package com.airwallexfyi.notifications

import java.util.UUID
import org.springframework.data.repository.CrudRepository

interface NotificationAttemptRepository : CrudRepository<NotificationAttemptRecord, UUID> {
    fun findByPostIdAndChannelAndRecipient(postId: UUID, channel: String, recipient: String): NotificationAttemptRecord?
}
