package com.airwallexfyi.http

import com.airwallexfyi.sources.RestClientAirwallexHttpClient
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RestClientTimeoutsTest {
    private lateinit var server: HttpServer

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/destination")
            exchange.sendResponseHeaders(301, -1)
            exchange.close()
        }
        server.createContext("/destination") { exchange ->
            val body = "destination content".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `follows a 301 redirect to its destination instead of returning the redirect stub body`() {
        val client = RestClientAirwallexHttpClient()
        val baseUrl = "http://localhost:${server.address.port}"

        val body = client.fetchText("$baseUrl/redirect")

        assertThat(body).isEqualTo("destination content")
    }
}
