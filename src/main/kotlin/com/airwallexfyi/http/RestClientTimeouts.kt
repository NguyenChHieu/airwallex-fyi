package com.airwallexfyi.http

import java.time.Duration
import org.springframework.http.client.SimpleClientHttpRequestFactory

object RestClientTimeouts {
    fun requestFactory(): SimpleClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        }

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 45L
}
