package com.melvin

import com.melvin.ScrapperUtils.{extractProductData, writeToCSV, extractProductLinks, fetchPage}
/**
 * @author ${minesh.melvin}
 */
object AmazonWebScrapper extends App {
  private val searchKeywords = "home decor"         // args
  private val searchKeywordsFormatted = searchKeywords.replace(" ", "+")
  private val searchUrl = s"https://www.amazon.in/s?k=$searchKeywordsFormatted"
  println(s"Scraping Amazon search page: $searchUrl")

  private val headersToScrapList = List("title", "price", "bestSeller", "stars", "reviews", "socialProof", "soldBy",
    "isAmazonChoice", "freeDelivery", "availability", "boxContents", "productDescription", "features", "ratingsHistogram", "url")

  // Step 1: Fetch the search page and extract product links
  fetchPage(searchUrl) match {
    case Some(doc) =>
      val productLinks = extractProductLinks(doc).toList

      // Step 2: Scrape data from each product
      val scrapedData = productLinks.flatMap(link => extractProductData(link))
      //val scrapedData = extractProductData(productLinks).toSeq

      writeToCSV("scraped_data.csv", scrapedData, ",", headersToScrapList)

    case None =>
      println("Failed the scrape the search results page.")
  }
}