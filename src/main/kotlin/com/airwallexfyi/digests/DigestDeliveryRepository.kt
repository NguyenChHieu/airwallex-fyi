package com.airwallexfyi.digests

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface DigestDeliveryRepository : CrudRepository<DigestDeliveryRecord, UUID> {
    fun findBySubscriberChannelIdAndLocalDate(
        subscriberChannelId: UUID,
        localDate: LocalDate,
    ): DigestDeliveryRecord?

    @Query(
        """
        SELECT *
        FROM digest_deliveries
        WHERE subscriber_channel_id = :subscriberChannelId
          AND status IN ('SENT', 'DRY_RUN')
        ORDER BY attempted_at DESC
        LIMIT 1
        """,
    )
    fun findMostRecentSuccessfulDelivery(subscriberChannelId: UUID): DigestDeliveryRecord?

    @Modifying
    @Query(
        """
        UPDATE digest_deliveries
        SET status = :status,
            provider_message_id = :providerMessageId,
            error_message = :errorMessage,
            sent_at = :sentAt,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    fun updateAfterAttempt(
        id: UUID,
        status: String,
        providerMessageId: String?,
        errorMessage: String?,
        sentAt: Instant?,
        updatedAt: Instant,
    ): Int
}
