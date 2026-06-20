package com.airwallexfyi.posts

import java.util.UUID
import org.springframework.data.repository.CrudRepository

interface PostRepository : CrudRepository<PostRecord, UUID> {
    fun findByUrl(url: String): PostRecord?

    fun findTop20ByOrderByDiscoveredAtDesc(): List<PostRecord>
}