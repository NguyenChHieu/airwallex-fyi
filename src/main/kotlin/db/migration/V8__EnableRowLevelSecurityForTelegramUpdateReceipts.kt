package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V8__EnableRowLevelSecurityForTelegramUpdateReceipts : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val databaseProduct = context.connection.metaData.databaseProductName.lowercase()
        if (!databaseProduct.contains("postgresql")) return

        context.connection.createStatement().use { statement ->
            statement.execute("ALTER TABLE IF EXISTS public.telegram_update_receipts ENABLE ROW LEVEL SECURITY")
        }
    }
}
