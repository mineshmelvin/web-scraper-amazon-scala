package com.melvin

import com.melvin.PersistantProxyScraper.httpClient
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.{File, PrintWriter}
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.io.Source
import scala.util.{Failure, Random, Success, Try}

object PersistantProxyScraperUtils {
  private val ipAddressesFilePath = "C:\\Users\\mines\\workspace\\projects\\training\\amazon-web-scraper\\src\\main\\resources\\ipaddress.txt"
  val proxyIPaddresses: List[String] = readIPAddresses(ipAddressesFilePath)
  var totalProductsFound = 0
  var iter = 0
  def getHttpClient(maxRetries: Int = 3): Option[CloseableHttpClient] = {
    var attempt = 0
    var client: Option[CloseableHttpClient] = None

    // Continue rotating through proxies until we get a valid client or exhaust the retries
    while (attempt < maxRetries && client.isEmpty) {
      val randomProxyIP: String = proxyIPaddresses(Random.nextInt(proxyIPaddresses.length)) // Randomly pick a proxy
      val proxyParts = randomProxyIP.split(":")
      val proxyHost = proxyParts(0)
      val proxyPort = proxyParts(1).toInt

      println(s"Trying with proxy: $proxyHost:$proxyPort (Attempt ${attempt + 1})")

      // Try to create an HttpClient with the chosen proxy
      Try {
        val proxy = new HttpHost(proxyHost, proxyPort)
        val requestConfig = RequestConfig.custom()
          .setProxy(proxy)
          .setConnectTimeout(10000) // Optional: You can customize timeout here
          .setSocketTimeout(10000)
          .build()

        HttpClients.custom()
          .setDefaultRequestConfig(requestConfig)
          .build()
      } match {
        case Success(httpClient) =>
          println(s"Successfully created HttpClient using proxy $proxyHost:$proxyPort")
          client = Some(httpClient)
        case Failure(exception) =>
          println(s"Failed to create HttpClient using proxy $proxyHost:$proxyPort: ${exception.getMessage}")
          attempt += 1 // Increment attempt and try another proxy
      }
    }

    if (client.isEmpty) {
      println("Failed to create HttpClient with any proxy.")
    }
    client // Return the working HttpClient, or None if all attempts failed
  }

  private def readIPAddresses(filePath: String): List[String] = {
    val bufferedSource = Source.fromFile(filePath)
    val ipList = bufferedSource.getLines().toList // Read each line and store in a list
    bufferedSource.close() // Close the file after reading
    ipList
  }

  // Fetch the content of a page using the HttpClient
  def fetchPage(url: String, httpClient: Option[CloseableHttpClient], attempt: Int = 0): Option[Document] = {
    val request = new HttpGet(url)

    httpClient match {
      case Some(client) =>
        try {
          val response: CloseableHttpResponse = client.execute(request)
          try {
            val entity = response.getEntity
            if (entity != null) {
              val content = EntityUtils.toString(entity)
              println(s"INSIDE FETCH PAGE; content: $content")
              if (content.contains("Bad Request")) {
                //client.close()
                fetchPage(url, httpClient, attempt + 1)
              } else if (content.contains("<head></head>")) {
                //client.close()
                fetchPage(url, httpClient, attempt + 1)
              } else if (content.contains("500 Internal Server Error")) {
                //client.close()
                fetchPage(url, httpClient, attempt + 1)
              } else if (content.contains("503 - Service Unavailable Error")) {
                //client.close()
                fetchPage(url, httpClient, attempt + 1)
              } else if (content.contains("400 ERROR")) {
                //client.close()
                fetchPage(url, httpClient, attempt + 1)
              }
              parseHtmlContent(content)
            } else None
          } finally {
            response.close()
          }
        } catch {
          case e: Exception =>
            println(s"Error fetching the page with this proxy: ${e.getMessage}")

            // Optional: Implement a backoff strategy
            Thread.sleep(2000) // Wait for 2 seconds before trying the next proxy

            // Close the current client and try the next proxy
            //client.close()
            fetchPage(url, getHttpClient(), attempt + 1) // Retry with the next proxy
        }
      case None =>
        println(s"No valid HttpClient available.")
        None
    }
  }


  // Parse the HTML content and extract required information using Jsoup
  private def parseHtmlContent(htmlContent: String): Option[org.jsoup.nodes.Document] = {
    try {
      Some(Jsoup.parse(htmlContent))
    } catch {
      case e: Exception =>
        println(s"Error parsing HTML content: ${e.getMessage}")
        None
    }
  }

  def extractProductLinks(doc: Document): Seq[String] = {
    // Extract product links from the search results page
    val productLinks = doc.select("a.a-link-normal.s-no-outline")
      .map(link => link.attr("href"))
      .filter(_.startsWith("/")) // Filter relative links
      .map(link => s"https://www.amazon.in$link") // Convert to absolute links
      .toSeq

    totalProductsFound = productLinks.size
    println(s"Found $totalProductsFound product links")
    productLinks
  }

  def extractProductData(url: String, httpClient: Option[CloseableHttpClient]): Option[Map[String, String]] = { //Option[Map[String, String]]
    // Fetch and scrape each product page
    fetchPage(url, httpClient) match {
      case Some(doc) =>
        //println(s"Scraping product page: $url")
        println(s"Scrapping $iter of $totalProductsFound")
        // Extract specific product information
        val fullInfo = Map(
          "title" -> doc.select("#productTitle").text(),
          "price" -> doc.select("#corePriceDisplay_desktop_feature_div .a-price-whole").first().text(),
          "reviews" -> doc.select("#acrCustomerReviewText.a-size-base").first().text(),
          "soldBy" -> doc.select("#sellerProfileTriggerId").first().text(), // Adjust selector based on actual page structure
          "isAmazonChoice" -> {
            if (doc.select("#acBadge_feature_div .ac-badge-text-secondary ac-orange").text().toLowerCase().contains("choice")) "Yes" else "No"
          },
          "freeDelivery" -> {
            if (doc.select("#mir-layout-DELIVERY_BLOCK").text().toLowerCase().contains("free delivery")) "Yes" else "No"
          }, //".a-size-small.a-color-success" // Adjust based on actual page structure
          "bestSeller" -> {
            if (doc.select("#zeitgeistBadge_feature_div").text().toLowerCase().contains("best seller")) "Yes" else "No"
          },
          "boxContents" -> doc.select("#witb-content-list span.a-list-item").text(),
          "productDescription" -> doc.select("#productDescription").text(),
          "features" -> doc.select("#productDetails_feature_div").text(),
          "stars" -> doc.select("#acrPopover span.a-size-base.a-color-base").first().text(),
          "socialProof" -> doc.select("#social-proofing-faceout-title-tk_bought").text(),
          "ratingsHistogram" -> doc.select("#cm_cr_dp_d_rating_histogram").text(),
          "availability" -> doc.select("#availability").text(),
          "url" -> url
        )

        iter += 1
        // Return the resulting list
        Some(fullInfo)

      case None =>
        println(s"Failed to fetch product page: $url")
        None
    }
  }

  def writeToCSV(fileName: String, data: Seq[Map[String, String]], separator: String, headersList: List[String]): Unit = {
    val writer = new PrintWriter(new File(fileName))

    try {
      // Extract headers from the first Map
      //val headers = data.flatMap(_.keys).distinct
      val headers = headersList

      // Write headers
      writer.println(s"sep=$separator\n" + headers.mkString(separator))
      //writer.println(headers.mkString(separator))

      // Write data
      data.foreach { row =>
        val values = headers.map(header => row.getOrElse(header, "").replace(separator, " "))
        writer.println(values.mkString(separator))
        //println(values.mkString(separator))
      }
    } finally {
      writer.close()
    }
    println(s"Data written to $fileName")
  }

  def closeHttpClient(clientOpt: Option[CloseableHttpClient]): Unit = {
    clientOpt match {
      case Some(client) =>
        try {
          client.close() // Safely close the HttpClient
          println("HttpClient closed successfully.")
        } catch {
          case e: Exception =>
            println(s"Error while closing HttpClient: ${e.getMessage}")
        }
      case None =>
        println("No HttpClient to close.")
    }
  }
}
