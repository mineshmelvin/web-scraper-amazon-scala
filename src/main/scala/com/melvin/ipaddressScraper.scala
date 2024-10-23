package com.melvin

import org.jsoup.{Connection, Jsoup}

import java.io.PrintWriter
import scala.io.Source
import java.io.File
import java.net.{HttpURLConnection, URL}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object ipaddressScraper extends App {
  private def fetchContent(url: String): String = {
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]

    // Set the request method and add a User-Agent to mimic a browser
    connection.setRequestMethod("GET")
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3")

    val source = Source.fromInputStream(connection.getInputStream)

    try {
      source.mkString
    } finally {
      source.close() // Ensure the stream is closed
      connection.disconnect() // Disconnect the connection
    }
  }

  private def extractIPsAndPorts(content: String): List[String] = {
    // Regex to match IP address and port (e.g., 192.168.1.1:8080)
    val ipPortRegex: Regex = """\b(?:\d{1,3}\.){3}\d{1,3}:\d{1,5}\b""".r

    // Find all matches and return them as a list
    ipPortRegex.findAllIn(content).toList
  }

  private def writeToFile(content: List[String], filePath: String): Unit = {
    val writer = new PrintWriter(new File(filePath))
    try {
      content.foreach(line => writer.println(line))
    } finally {
      writer.close() // Ensure the writer is closed
    }
  }

  private def isValidProxy(proxy: String): Boolean = {
    val proxyParts = proxy.split(":")
    if (proxyParts.length != 2) {
      println(s"Invalid proxy format: $proxy")
      return false
    }

    val proxyHost = proxyParts(0)
    val proxyPort = proxyParts(1).toInt
    //System.setProperty("http.proxyHost", "182.16.78.222")
    //System.setProperty("http.proxyPort", "80")
    Try {
      Jsoup.connect("http://httpbin.org/ip")
        .timeout(5000)
        .proxy(proxyHost, proxyPort)
        .ignoreContentType(true)
        .get()
    } match {
      case Success(doc) =>
        println("Valid proxy: " + proxy)
        true
      case Failure(exception) =>
        exception match {
          case e: java.net.SocketTimeoutException =>
            println(s"Timeout while using proxy: $proxy - ${e.getMessage}")
          case e: java.net.UnknownHostException =>
            println(s"Unknown host for proxy: $proxy - ${e.getMessage}")
          case e: org.jsoup.HttpStatusException =>
            println(s"HTTP error with proxy: $proxy - Status: ${e.getStatusCode}, ${e.getMessage}")
          case _ =>
            println(s"Failed to connect using proxy: $proxy - ${exception.getMessage}")
        }
        false
    }
  }


  val url = "https://spys.me/proxy.txt"
  val filePath = "src\\main\\resources\\ipaddress.txt" // Path to the file where the content will be saved
  try {
    val content = fetchContent(url)
    val ipPortList = extractIPsAndPorts(content)
    //val validIPs = ipPortList.filter(isValidProxy)
    writeToFile(ipPortList, filePath)
    println(s"Content successfully written to $filePath")
  } catch {
    case e: Exception => println(s"Error: ${e.getMessage}")
  }
}
