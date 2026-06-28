package com.airwallexfyi.digests

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

object DigestDeliveryStatus {
    const val DRY_RUN: String = "DRY_RUN"
    const val SKIPPED: String = "SKIPPED"
    const val SENT: String = "SENT"
    const val FAILED: String = "FAILED"
    const val SKIPPED_DUPLICATE: String = "SKIPPED_DUPLICATE"
}

object DigestMessageType {
    const val DIGEST: String = "DIGEST"
    const val NO_CHANGES: String = "NO_CHANGES"
}

@Table("digest_deliveries")
data class DigestDeliveryRecord(
    @Id @Column("id") private val recordId: UUID = UUID.randomUUID(),
    @Column("subscriber_channel_id") val subscriberChannelId: UUID,
    @Column("local_date") val localDate: LocalDate,
    @Column("message_type") val messageType: String,
    @Column("status") val status: String,
    @Column("recipient") val recipient: String,
    @Column("channel") val channel: String,
    @Column("payload_preview") val payloadPreview: String? = null,
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
