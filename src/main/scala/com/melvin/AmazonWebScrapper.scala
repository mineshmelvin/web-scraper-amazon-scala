package com.melvin

import com.melvin.ScrapperUtils.{extractProductData, writeToCSV, extractProductLinks, fetchPage}
/**
 * @author ${minesh.melvin}
 */
object AmazonWebScrapper extends App {
  private val searchUrl = "https://www.amazon.in/s?k=home+decor"
  println(s"Scraping Amazon search page: $searchUrl")

  //println(s"Headers are: " + getHeaders(searchUrl))

  // Step 1: Fetch the search page and extract product links
  fetchPage(searchUrl) match {
    case Some(doc) =>
      val productLinks = extractProductLinks(doc).toList

      // Step 2: Scrape data from each product
      val scrapedData = productLinks.flatMap(link => extractProductData(link))
      //val scrapedData = extractProductData(productLinks).toSeq
      writeToCSV("scraped_data.csv", scrapedData, ",")

    case None =>
      println("Failed the scrape the search results page.")
  }
}