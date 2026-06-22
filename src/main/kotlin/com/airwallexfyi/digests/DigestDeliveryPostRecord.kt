package com.airwallexfyi.digests

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("digest_delivery_posts")
data class DigestDeliveryPostRecord(
    @Id @Column("id") private val recordId: UUID = UUID.randomUUID(),
    @Column("digest_delivery_id") val digestDeliveryId: UUID,
    @Column("post_id") val postId: UUID,
    @Column("summary_id") val summaryId: UUID,
    @Column("display_order") val displayOrder: Int,
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Transient private val newRecord: Boolean = true,
) : Persistable<UUID> {
    override fun getId(): UUID = recordId

    fun identifier(): UUID = recordId

    override fun isNew(): Boolean = newRecord
}
