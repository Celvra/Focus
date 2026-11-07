package fan.x0.focus.data.plugin

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class PluginDatabase(@Provided context: Context) : SQLiteOpenHelper(context, "plugins.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE plugins(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                version TEXT NOT NULL,
                version_code INTEGER NOT NULL,
                api_version INTEGER NOT NULL,
                main_class TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                source TEXT NOT NULL,
                installed_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                manifest_json TEXT NOT NULL,
                capabilities_json TEXT NOT NULL DEFAULT '[]',
                last_error TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
