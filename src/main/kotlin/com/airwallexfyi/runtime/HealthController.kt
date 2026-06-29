package com.airwallexfyi.runtime

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(status = "ok")
}

data class HealthResponse(
    val status: String,
)
