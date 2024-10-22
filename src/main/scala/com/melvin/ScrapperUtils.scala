package com.melvin

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.{File, PrintWriter}
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.util.{Failure, Success, Try}

object ScrapperUtils {
  private var iter = 0
  private var totalProductsFound = 0

  /**
   * Fetches a search document from a given search url
   * @param url The search results URL from which we get a list of product documents
   * @return document of searched products
   */
  def fetchPage(url: String): Option[Document] = {
    //Fetch HTML content of the page
    Try(Jsoup.connect(url)
      .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.37")
      .timeout(5000)
      .get()) match {
      case Success(doc) => Some(doc)
      case Failure(exception) =>
        println(s"Error fetching the page: ${exception.getMessage}")
        None
    }
  }


  /**
   * Extracts product links from a search document
   * @param doc the document from which we can extract product links
   * @return Sequence of product links
   */
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

  /**
   * Extracts data from a given url
   * @param url the product url from which we can scrape the data
   * @return Map of product data in key value pair
   */
  def extractProductData(url: String): Option[Map[String, String]] = { //Option[Map[String, String]]
    // Fetch and scrape each product page
    fetchPage(url) match {
      case Some(doc) =>
        //println(s"Scraping product page: $url")
        println(s"Scrapping $iter of $totalProductsFound")
        // Extract specific product information
        val fullInfo = Map(
          "title" -> doc.select("#productTitle").text(),
          "price" -> doc.select("#corePriceDisplay_desktop_feature_div .a-price-whole").first().text(),
          "reviews" -> doc.select("#acrCustomerReviewText.a-size-base").first().text(),
          "soldBy" -> doc.select("#sellerProfileTriggerId").first().text(), // Adjust selector based on actual page structure
          "isAmazonChoice" -> {if(doc.select("#acBadge_feature_div .ac-badge-text-secondary ac-orange").text().toLowerCase().contains("choice")) "Yes" else "No"},
          "freeDelivery" -> {if(doc.select("#mir-layout-DELIVERY_BLOCK").text().toLowerCase().contains("free delivery")) "Yes" else "No"}, //".a-size-small.a-color-success" // Adjust based on actual page structure
          "bestSeller" -> {if(doc.select("#zeitgeistBadge_feature_div").text().toLowerCase().contains("best seller")) "Yes" else "No"},
          "boxContents" -> doc.select("#witb-content-list span.a-list-item").text(),
          "productDescription" -> doc.select("#productDescription").text(),
          "features" -> doc.select("#productDetails_feature_div").text(),
          "stars" -> doc.select("#acrPopover span.a-size-base.a-color-base").first().text(),
          "socialProof" -> doc.select("#social-proofing-faceout-title-tk_bought").text(),
          "ratingsHistogram" -> doc.select("#cm_cr_dp_d_rating_histogram").text(),
          "availability" -> doc.select("#availability").text(),
          "url" -> url
        )

        iter+=1
        // Return the resulting list
        Some(fullInfo)

      case None =>
        println(s"Failed to fetch product page: $url")
        None
    }
  }

  /**
   * Writes the data to a csv file (can use custom delimiter)
   * @param fileName path of the output file to which data will be written
   * @param data data to be written
   * @param separator separator or delimiter to use while writing data
   * @param headersList List of headers to be included in the output
   */
  def writeToCSV(fileName: String, data: Seq[Map[String, String]], separator: String, headersList: List[String]): Unit = {
    val writer = new PrintWriter(new File(fileName))

    try{
      // Extract headers from the first Map
      //val headers = data.flatMap(_.keys).distinct
      val headers = headersList

      // Write headers
      writer.println(s"sep=$separator\n" + headers.mkString(separator))
      //writer.println(headers.mkString(separator))

      // Write data
      data.foreach{ row =>
        val values = headers.map(header => row.getOrElse(header, "").replace(separator, " "))
        writer.println(values.mkString(separator))
        //println(values.mkString(separator))
      }
    } finally {
      writer.close()
    }
    println(s"Data written to $fileName")
  }
}