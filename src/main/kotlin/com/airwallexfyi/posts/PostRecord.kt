package com.airwallexfyi.posts

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("posts")
data class PostRecord(
    @Id @Column("id") private val recordId: UUID = UUID.randomUUID(),
    @Column("url") val url: String,
    @Column("source_type") val sourceType: String,
    @Column("title") val title: String? = null,
    @Column("description") val description: String? = null,
    @Column("author") val author: String? = null,
    @Column("published_at") val publishedAt: Instant? = null,
    @Column("sitemap_lastmod") val sitemapLastmod: Instant? = null,
    @Column("discovered_at") val discoveredAt: Instant = Instant.now(),
    @Column("content_hash") val contentHash: String? = null,
    @Column("article_body") val articleBody: String? = null,
    @Column("processing_status") val processingStatus: String = "DISCOVERED",
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Column("updated_at") val updatedAt: Instant = Instant.now(),
    @Transient private val newRecord: Boolean = true,
) : Persistable<UUID> {
    override fun getId(): UUID = recordId

    fun identifier(): UUID = recordId

    override fun isNew(): Boolean = newRecord
}