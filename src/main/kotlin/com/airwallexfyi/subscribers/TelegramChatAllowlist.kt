package com.airwallexfyi.subscribers

object TelegramChatAllowlist {
    fun allows(configuredChatIds: String, chatId: String): Boolean {
        val allowedChatIds = configuredChatIds.toChatIdSet()
        return allowedChatIds.isEmpty() || chatId in allowedChatIds
    }

    private fun String.toChatIdSet(): Set<String> =
        trim()
            .takeIf { it.isNotBlank() }
            ?.split(CHAT_ID_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    private val CHAT_ID_SEPARATOR = Regex("[,\\s]+")
}
