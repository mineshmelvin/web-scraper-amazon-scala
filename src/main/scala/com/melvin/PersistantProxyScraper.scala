package com.melvin

import com.melvin.PersistantProxyScraperUtils.{closeHttpClient, extractProductData, extractProductLinks, fetchPage, getHttpClient, writeToCSV}
import org.apache.http.impl.client.CloseableHttpClient
import org.jsoup.nodes.Document
import scala.util.{Failure, Success, Try}

object PersistantProxyScraper extends App {
  private val searchKeywords = "home decor" // args
  private val searchKeywordsFormatted = searchKeywords.replace(" ", "+")
  private val searchUrl = s"https://www.amazon.in/s?k=$searchKeywordsFormatted"
  println(s"Scraping Amazon search page: $searchUrl")

  private val headersToScrapList = List("title", "price", "bestSeller", "stars", "reviews", "socialProof", "soldBy",
    "isAmazonChoice", "freeDelivery", "availability", "boxContents", "productDescription", "features", "ratingsHistogram")

  var httpClient: Option[CloseableHttpClient] = getHttpClient()
  private var searchPageDocument: Option[Document] = None
  // Fetch page content
  Try {
    fetchPage(searchUrl, httpClient)
  } match {
    case Success(value) =>
      searchPageDocument = value
    case Failure(exception) =>
      println(exception)
      fetchPage(searchUrl, httpClient)
  }

  searchPageDocument match {
    case Some(doc) =>
      println(doc)
      val productLinks = extractProductLinks(doc).toList
      val scrapedData = productLinks.flatMap(link => extractProductData(link, httpClient))
      // Write to file
      writeToCSV("output.txt", scrapedData, ",", headersToScrapList)
    case None => println("Failed to fetch the page.")
  }
  closeHttpClient(httpClient) // Always close the client
}

/**
 * searchPageDocument match {
 * case Some(html) =>
 * //Parse the html
 * //val parsedDoc = parseHtmlContent(html)
 * parsedDoc.foreach { doc =>
 * // Extract required data (like product titles, reviews, etc.)
 * println(doc.toString)
 * val productLinks = extractProductLinks(doc).toList
 * val requiredData = Seq(extractProductData(doc))
 * // Write to file
 * writeToCSV("output.txt", requiredData, ",", headersToScrapList)
 * }
 */