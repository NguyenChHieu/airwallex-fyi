package com.airwallexfyi.articles

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.stereotype.Service

@Service
class ContentHashService {
    fun hash(title: String, description: String?, bodyText: String): String {
        val canonical = listOf(
            normalize(title),
            normalize(description.orEmpty()),
            normalize(bodyText),
        ).joinToString(separator = "\n---\n")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))

        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun normalize(value: String): String =
        value.trim().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}