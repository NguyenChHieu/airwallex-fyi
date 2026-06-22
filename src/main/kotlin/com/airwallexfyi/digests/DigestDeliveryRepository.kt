package com.airwallexfyi.digests

import java.time.LocalDate
import java.util.UUID
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
}
