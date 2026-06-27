package com.airwallexfyi.subscribers

import java.time.Instant
import java.util.UUID
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface SubscriberChannelRepository : CrudRepository<SubscriberChannelRecord, UUID> {
    fun findByChannelAndRecipient(channel: String, recipient: String): SubscriberChannelRecord?

    fun findByChannelAndStatusOrderByCreatedAtAsc(channel: String, status: String): List<SubscriberChannelRecord>

    @Modifying
    @Query(
        """
        UPDATE subscriber_channels
        SET status = :status, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    fun updateStatus(id: UUID, status: String, updatedAt: Instant): Int
}
