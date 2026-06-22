package com.airwallexfyi.subscribers

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

object SubscriberStatus {
    const val ACTIVE: String = "ACTIVE"
    const val INACTIVE: String = "INACTIVE"
}

@Table("subscribers")
data class SubscriberRecord(
    @Id @Column("id") private val recordId: UUID = UUID.randomUUID(),
    @Column("display_name") val displayName: String,
    @Column("status") val status: String = SubscriberStatus.ACTIVE,
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Column("updated_at") val updatedAt: Instant = Instant.now(),
    @Transient private val newRecord: Boolean = true,
) : Persistable<UUID> {
    override fun getId(): UUID = recordId

    fun identifier(): UUID = recordId

    override fun isNew(): Boolean = newRecord
}
