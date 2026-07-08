package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V6__EnableRowLevelSecurityForFlywayHistory : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val databaseProduct = context.connection.metaData.databaseProductName.lowercase()
        if (!databaseProduct.contains("postgresql")) return

        context.connection.createStatement().use { statement ->
            statement.execute("ALTER TABLE IF EXISTS public.flyway_schema_history ENABLE ROW LEVEL SECURITY")
        }
    }
}
