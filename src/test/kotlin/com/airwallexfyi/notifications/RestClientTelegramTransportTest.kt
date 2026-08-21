package com.airwallexfyi.notifications

import com.airwallexfyi.config.AppProperties
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class RestClientTelegramTransportTest {
    private lateinit var server: HttpServer
    private val slept = CopyOnWriteArrayList<Duration>()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    }

    @AfterEach
    fun stop() {
        server.stop(0)
        slept.clear()
    }

    @Test
    fun `retries once after a 429 with retry_after and then succeeds`() {
        val attempt = AtomicInteger(0)
        server.createContext("/bot123/sendMessage") { exchange ->
            if (attempt.getAndIncrement() == 0) {
                val body = """{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 1","parameters":{"retry_after":1}}"""
                    .toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(429, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                val body = """{"ok":true,"result":{"message_id":42}}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        val transport = transport()

        val response = transport.sendMessage(botToken = "123", chatId = "999", body = "hello")

        assertThat(response.messageId).isEqualTo("42")
        assertThat(attempt.get()).isEqualTo(2)
        assertThat(slept).containsExactly(Duration.ofSeconds(1))
    }

    @Test
    fun `gives up after the max retry attempts and reports rate limiting`() {
        server.createContext("/bot123/sendMessage") { exchange ->
            val body = """{"ok":false,"error_code":429,"description":"Too Many Requests","parameters":{"retry_after":1}}"""
                .toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(429, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val transport = transport()

        assertThatThrownBy { transport.sendMessage(botToken = "123", chatId = "999", body = "hello") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("rate limited (429)")
        // 3 attempts total, so only 2 waits between them.
        assertThat(slept).hasSize(2)
    }

    @Test
    fun `caps the wait at the configured maximum even if Telegram asks for longer`() {
        val attempt = AtomicInteger(0)
        server.createContext("/bot123/sendMessage") { exchange ->
            if (attempt.getAndIncrement() == 0) {
                val body = """{"ok":false,"error_code":429,"description":"slow down","parameters":{"retry_after":9999}}"""
                    .toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(429, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                val body = """{"ok":true,"result":{"message_id":7}}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        val transport = transport()

        transport.sendMessage(botToken = "123", chatId = "999", body = "hello")

        assertThat(slept.single()).isEqualTo(Duration.ofSeconds(60))
    }

    private fun transport(): RestClientTelegramTransport = RestClientTelegramTransport(
        objectMapper = ObjectMapper(),
        properties = AppProperties(telegram = AppProperties.Telegram(sendsPerSecond = 1000.0)),
        baseUrl = "http://localhost:${server.address.port}",
        sleeper = { duration -> slept += duration },
    )
}
