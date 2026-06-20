package com.airwallexfyi.notifications

import java.util.UUID
import org.springframework.data.repository.CrudRepository

interface NotificationAttemptRepository : CrudRepository<NotificationAttemptRecord, UUID> {
    fun findByPostId(postId: UUID): List<NotificationAttemptRecord>
}