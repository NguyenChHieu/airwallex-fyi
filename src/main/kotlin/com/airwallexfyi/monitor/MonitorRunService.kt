package com.airwallexfyi.monitor

import org.springframework.stereotype.Service

@Service
class MonitorRunService {
    fun runOnce(): MonitorRunResult = MonitorRunResult(
        status = "stubbed",
        message = "Phase 1 run-once stub completed; no Airwallex, OpenAI, or Twilio calls were made.",
        externalCallsTriggered = false,
    )
}

data class MonitorRunResult(
    val status: String,
    val message: String,
    val externalCallsTriggered: Boolean,
)