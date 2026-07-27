package com.airwallexfyi.subscribers

import java.sql.Timestamp
import java.time.Instant
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class TelegramUpdateReceiptRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun tryClaim(updateId: Long, receivedAt: Instant): Boolean = try {
        jdbcTemplate.update(
            """
            INSERT INTO telegram_update_receipts (update_id, received_at)
            VALUES (?, ?)
            """.trimIndent(),
            updateId,
            Timestamp.from(receivedAt),
        )
        true
    } catch (_: DuplicateKeyException) {
        false
    }

    fun release(updateId: Long) {
        jdbcTemplate.update(
            "DELETE FROM telegram_update_receipts WHERE update_id = ?",
            updateId,
        )
    }

    fun findLatestUpdateId(): Long? =
        jdbcTemplate.queryForObject(
            "SELECT MAX(update_id) FROM telegram_update_receipts",
            Long::class.java,
        )
}
