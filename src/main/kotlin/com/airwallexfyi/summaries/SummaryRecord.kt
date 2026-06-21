package com.airwallexfyi.summaries

import com.airwallexfyi.posts.SourceType
import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

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

    companion object {
        fun from(
            postId: UUID,
            summary: StructuredSummary,
            model: String,
            promptVersion: String,
            objectMapper: ObjectMapper,
            now: Instant = Instant.now(),
        ): SummaryRecord = SummaryRecord(
            postId = postId,
            headline = summary.headline,
            summaryJson = objectMapper.writeValueAsString(summary.toJsonMap()),
            whyItMatters = summary.whyItMatters,
            tagsJson = objectMapper.writeValueAsString(summary.tags),
            model = model,
            promptVersion = promptVersion,
            createdAt = now,
            updatedAt = now,
        )
    }
}

fun SummaryRecord.toStructuredSummary(objectMapper: ObjectMapper): StructuredSummary {
    val payload: SummaryPayload = objectMapper.readValue(summaryJson)
    return StructuredSummary.validated(
        headline = payload.headline,
        bullets = payload.bullets,
        whyItMatters = payload.why_it_matters,
        tags = payload.tags,
        sourceType = SourceType.valueOf(payload.source_type),
    )
}

private fun StructuredSummary.toJsonMap(): Map<String, Any> = mapOf(
    "headline" to headline,
    "bullets" to bullets,
    "why_it_matters" to whyItMatters,
    "tags" to tags,
    "source_type" to sourceType.name,
)

private data class SummaryPayload(
    val headline: String,
    val bullets: List<String>,
    val why_it_matters: String,
    val tags: List<String>,
    val source_type: String,
)
