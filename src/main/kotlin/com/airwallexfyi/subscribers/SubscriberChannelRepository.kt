package com.airwallexfyi.subscribers

import java.util.UUID
import org.springframework.data.repository.CrudRepository

interface SubscriberChannelRepository : CrudRepository<SubscriberChannelRecord, UUID> {
    fun findByChannelAndRecipient(channel: String, recipient: String): SubscriberChannelRecord?

    fun findByChannelAndStatusOrderByCreatedAtAsc(channel: String, status: String): List<SubscriberChannelRecord>
}
