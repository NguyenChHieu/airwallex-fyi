package com.airwallexfyi.articles

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class RichTextFlattener {
    fun flatten(node: JsonNode?): String {
        if (node == null || node.isNull || node.isMissingNode) return ""

        val builder = StringBuilder()
        appendNode(node, builder)
        return builder.toString()
            .lineSequence()
            .map { line -> line.trim().replace(INLINE_WHITESPACE, " ") }
            .filter { line -> line.isNotBlank() }
            .joinToString("\n")
    }

    private fun appendNode(node: JsonNode, builder: StringBuilder) {
        val nodeType = node.get("nodeType")?.asText()
        if (nodeType == "text") {
            appendInlineText(builder, node.get("value")?.asText().orEmpty())
            return
        }

        val block = nodeType in BLOCK_NODE_TYPES
        if (block) ensureNewline(builder)

        val content = node.get("content")
        if (content != null && content.isArray) {
            for (child in content) {
                appendNode(child, builder)
            }
        }

        if (block) ensureNewline(builder)
    }

    private fun appendInlineText(builder: StringBuilder, value: String) {
        val text = value.trim()
        if (text.isBlank()) return
        if (builder.isNotEmpty() && !builder.last().isWhitespace()) {
            builder.append(' ')
        }
        builder.append(text)
    }

    private fun ensureNewline(builder: StringBuilder) {
        if (builder.isNotEmpty() && builder.last() != '\n') {
            builder.append('\n')
        }
    }

    private companion object {
        val INLINE_WHITESPACE = Regex("[ \\t\\x0B\\f\\r]+")
        val BLOCK_NODE_TYPES = setOf(
            "paragraph",
            "heading-1",
            "heading-2",
            "heading-3",
            "heading-4",
            "heading-5",
            "heading-6",
            "list-item",
        )
    }
}