package com.airwallexfyi.monitor

data class MonitorRunResult(
    val status: String,
    val sitemapFetched: Boolean,
    val discoveredCount: Int,
    val seededCount: Int,
    val newCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val sampleUrls: MonitorRunSampleUrls = MonitorRunSampleUrls(),
    val sampleErrors: List<MonitorRunError> = emptyList(),
    val externalCallsTriggered: Boolean = false,
    val message: String,
)

data class MonitorRunSampleUrls(
    val seeded: List<String> = emptyList(),
    val new: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
)

data class MonitorRunError(
    val url: String?,
    val reason: String,
)

object MonitorRunStatus {
    const val COMPLETED = "completed"
    const val PARTIAL_FAILURE = "partial_failure"
    const val FAILED = "failed"
}