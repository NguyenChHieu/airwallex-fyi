package com.airwallexfyi.summaries

import java.util.UUID
import org.springframework.data.repository.CrudRepository

interface SummaryRepository : CrudRepository<SummaryRecord, UUID> {
    fun findByPostId(postId: UUID): SummaryRecord?
}