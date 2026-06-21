package com.airwallexfyi.notifications

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("notification_attempts")
data class NotificationAttemptRecord(
    @Id @Column("id") private val recordId: UUID = UUID.randomUUID(),
    @Column("post_id") val postId: UUID,
    @Column("channel") val channel: String,
    @Column("recipient") val recipient: String,
    @Column("status") val status: String,
    @Column("provider_message_id") val providerMessageId: String? = null,
    @Column("error_message") val errorMessage: String? = null,
    @Column("attempted_at") val attemptedAt: Instant = Instant.now(),
    @Column("sent_at") val sentAt: Instant? = null,
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Column("updated_at") val updatedAt: Instant = Instant.now(),
    @Transient private val newRecord: Boolean = true,
) : Persistable<UUID> {
    override fun getId(): UUID = recordId

    fun identifier(): UUID = recordId

    override fun isNew(): Boolean = newRecord
}
