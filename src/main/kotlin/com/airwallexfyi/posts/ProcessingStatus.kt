package com.airwallexfyi.posts

enum class ProcessingStatus {
    DISCOVERED,
    SEEDED,
    BASELINED,
    SUMMARY_READY,
    SUMMARY_FAILED,
    APPROVAL_NEEDED,
}
