package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V5__EnableRowLevelSecurity : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val databaseProduct = context.connection.metaData.databaseProductName.lowercase()
        if (!databaseProduct.contains("postgresql")) return

        context.connection.createStatement().use { statement ->
            TABLES.forEach { table ->
                statement.addBatch("ALTER TABLE IF EXISTS public.$table ENABLE ROW LEVEL SECURITY")
            }
            statement.executeBatch()
        }
    }

    private companion object {
        val TABLES = listOf(
            "posts",
            "summaries",
            "notification_attempts",
            "subscribers",
            "subscriber_channels",
            "digest_deliveries",
            "digest_delivery_posts",
            "app_state",
        )
    }
}
