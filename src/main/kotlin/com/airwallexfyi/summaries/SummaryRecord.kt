package com.airwallexfyi.summaries

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("summaries")
data class SummaryRecord(
    @Id @Column("id") private val recordId: UUID = UUID.randomUUID(),
    @Column("post_id") val postId: UUID,
    @Column("headline") val headline: String,
    @Column("summary_json") val summaryJson: String,
    @Column("why_it_matters") val whyItMatters: String? = null,
    @Column("tags_json") val tagsJson: String? = null,
    @Column("model") val model: String? = null,
    @Column("prompt_version") val promptVersion: String? = null,
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Column("updated_at") val updatedAt: Instant = Instant.now(),
    @Transient private val newRecord: Boolean = true,
) : Persistable<UUID> {
    override fun getId(): UUID = recordId

    fun identifier(): UUID = recordId

    override fun isNew(): Boolean = newRecord
}