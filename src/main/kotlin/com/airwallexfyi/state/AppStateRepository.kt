package com.airwallexfyi.state

import java.sql.Timestamp
import java.time.Instant
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AppStateRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findValue(key: String): String? = jdbcTemplate.query(
        """
        SELECT state_value
        FROM app_state
        WHERE state_key = ?
        """.trimIndent(),
        { rs, _ -> rs.getString("state_value") },
        key,
    ).firstOrNull()

    fun putValue(key: String, value: String, now: Instant = Instant.now()) {
        val updatedAt = Timestamp.from(now)
        val updated = jdbcTemplate.update(
            """
            UPDATE app_state
            SET state_value = ?, updated_at = ?
            WHERE state_key = ?
            """.trimIndent(),
            value,
            updatedAt,
            key,
        )
        if (updated > 0) return

        try {
            jdbcTemplate.update(
                """
                INSERT INTO app_state (state_key, state_value, updated_at)
                VALUES (?, ?, ?)
                """.trimIndent(),
                key,
                value,
                updatedAt,
            )
        } catch (_: DuplicateKeyException) {
            putValue(key, value, now)
        }
    }
}
