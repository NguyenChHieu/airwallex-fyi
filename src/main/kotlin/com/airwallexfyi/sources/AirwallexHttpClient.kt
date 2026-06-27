package com.airwallexfyi.sources

import com.airwallexfyi.http.RestClientTimeouts
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

interface AirwallexHttpClient {
    fun fetchText(url: String): String
}

@Component
class RestClientAirwallexHttpClient : AirwallexHttpClient {
    private val restClient = RestClient.builder()
        .requestFactory(RestClientTimeouts.requestFactory())
        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
        .build()

    override fun fetchText(url: String): String =
        restClient.get()
            .uri(url)
            .retrieve()
            .body(String::class.java)
            ?: error("Empty response body from $url")

    private companion object {
        const val USER_AGENT = "airwallex-fyi/0.1 (+public sitemap/article monitor)"
    }
}
