package com.airwallexfyi.http

import java.net.http.HttpClient
import java.time.Duration
import org.springframework.http.client.JdkClientHttpRequestFactory

object RestClientTimeouts {
    fun requestFactory(readTimeout: Duration = DEFAULT_READ_TIMEOUT): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(readTimeout)
        }
    }

    val GEMINI_READ_TIMEOUT: Duration = Duration.ofSeconds(120)

    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(45)
}
