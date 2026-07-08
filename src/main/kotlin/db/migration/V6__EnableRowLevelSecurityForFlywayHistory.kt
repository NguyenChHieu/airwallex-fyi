package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V6__EnableRowLevelSecurityForFlywayHistory : BaseJavaMigration() {
    override fun migrate(context: Context) {
        // Flyway holds locks around its schema-history table while migrations run.
        // Altering that same table from inside Flyway can hit statement timeouts on
        // Supabase. Production was fixed manually; this marker lets Flyway record
        // the resolved state without blocking application startup.
    }
}
