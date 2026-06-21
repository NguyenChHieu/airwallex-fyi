package com.airwallexfyi.posts

enum class ProcessingStatus {
    DISCOVERED,
    SEEDED,
    SUMMARY_READY,
    ALERT_SENT,
    DRY_RUN_READY,
    SUMMARY_FAILED,
    ALERT_FAILED,
    APPROVAL_NEEDED,
}
