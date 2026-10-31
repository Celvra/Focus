package fan.x0.focus.data.assistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class AssistantDatabase(@Provided context: Context) : SQLiteOpenHelper(context, "assistant.db", null, 11) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                archived INTEGER NOT NULL DEFAULT 0,
                provider_id TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                reasoning TEXT NOT NULL DEFAULT '',
                mode TEXT NOT NULL DEFAULT 'build',
                plan_json TEXT,
                plan_status TEXT NOT NULL DEFAULT 'none',
                plan_request_id TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL DEFAULT '',
                reasoning TEXT NOT NULL DEFAULT '',
                tool_calls TEXT,
                tool_call_id TEXT,
                name TEXT,
                created_at INTEGER NOT NULL,
                attachments TEXT,
                label TEXT,
                reply_to_id INTEGER,
                stats_json TEXT,
                reaction TEXT,
                elapsed_ms INTEGER NOT NULL DEFAULT 0,
                diff_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id, id)")
        db.execSQL(
            """
            CREATE TABLE goals(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                detail TEXT,
                status TEXT NOT NULL,
                due_at INTEGER,
                progress INTEGER NOT NULL DEFAULT 0,
                note TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                icon TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE memories(
                id TEXT PRIMARY KEY,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE approvals(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                steps TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                decided_at INTEGER,
                result TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE updates(
                id TEXT PRIMARY KEY,
                headline TEXT NOT NULL,
                body TEXT NOT NULL,
                reason TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                icon TEXT NOT NULL DEFAULT '',
                article_id TEXT,
                created_at INTEGER NOT NULL,
                feedback INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE ideas(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT '',
                steps TEXT NOT NULL DEFAULT '[]',
                category TEXT NOT NULL DEFAULT '',
                icon TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                dismissed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE reminders(
                id TEXT PRIMARY KEY,
                text TEXT NOT NULL,
                at INTEGER NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                goal_id TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE profile(
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE conversation_todos(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id TEXT NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                priority TEXT NOT NULL DEFAULT 'medium',
                position INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_todos_conversation ON conversation_todos(conversation_id, position)",
        )
        db.execSQL(
            """
            CREATE TABLE file_index(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                root TEXT NOT NULL DEFAULT '',
                path TEXT NOT NULL,
                size INTEGER NOT NULL DEFAULT 0,
                modified_at INTEGER NOT NULL DEFAULT 0,
                content_hash TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                language TEXT,
                summary TEXT,
                indexed_at INTEGER NOT NULL,
                UNIQUE(root, path)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_file_index_path ON file_index(path)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN provider_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE conversations ADD COLUMN model TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE conversations ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN attachments TEXT")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE messages ADD COLUMN label TEXT")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE goals ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE goals ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS updates(
                    id TEXT PRIMARY KEY,
                    headline TEXT NOT NULL,
                    body TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT '',
                    icon TEXT NOT NULL DEFAULT '',
                    article_id TEXT,
                    created_at INTEGER NOT NULL,
                    feedback INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ideas(
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    detail TEXT NOT NULL DEFAULT '',
                    steps TEXT NOT NULL DEFAULT '[]',
                    category TEXT NOT NULL DEFAULT '',
                    icon TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    dismissed INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_id INTEGER")
            db.execSQL("ALTER TABLE messages ADD COLUMN stats_json TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN reaction TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN mode TEXT NOT NULL DEFAULT 'build'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN plan_json TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN plan_status TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN plan_request_id TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_todos(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    priority TEXT NOT NULL DEFAULT 'medium',
                    position INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_todos_conversation ON conversation_todos(conversation_id, position)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS file_index(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    root TEXT NOT NULL DEFAULT '',
                    path TEXT NOT NULL,
                    size INTEGER NOT NULL DEFAULT 0,
                    modified_at INTEGER NOT NULL DEFAULT 0,
                    content_hash TEXT NOT NULL DEFAULT '',
                    content TEXT NOT NULL DEFAULT '',
                    language TEXT,
                    summary TEXT,
                    indexed_at INTEGER NOT NULL,
                    UNIQUE(root, path)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_file_index_path ON file_index(path)")
        }
        if (oldVersion < 8) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS file_index(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    root TEXT NOT NULL DEFAULT '',
                    path TEXT NOT NULL,
                    size INTEGER NOT NULL DEFAULT 0,
                    modified_at INTEGER NOT NULL DEFAULT 0,
                    content_hash TEXT NOT NULL DEFAULT '',
                    content TEXT NOT NULL DEFAULT '',
                    language TEXT,
                    summary TEXT,
                    indexed_at INTEGER NOT NULL,
                    UNIQUE(root, path)
                )
                """.trimIndent(),
            )
            if (!hasColumn(db, "file_index", "content")) {
                db.execSQL("ALTER TABLE file_index ADD COLUMN content TEXT NOT NULL DEFAULT ''")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_file_index_path ON file_index(path)")
        }
        if (oldVersion < 9) {
            if (!hasTable(db, "file_index")) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS file_index(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        root TEXT NOT NULL DEFAULT '',
                        path TEXT NOT NULL,
                        size INTEGER NOT NULL DEFAULT 0,
                        modified_at INTEGER NOT NULL DEFAULT 0,
                        content_hash TEXT NOT NULL DEFAULT '',
                        content TEXT NOT NULL DEFAULT '',
                        language TEXT,
                        summary TEXT,
                        indexed_at INTEGER NOT NULL,
                        UNIQUE(root, path)
                    )
                    """.trimIndent(),
                )
            } else if (!hasColumn(db, "file_index", "content")) {
                db.execSQL("ALTER TABLE file_index ADD COLUMN content TEXT NOT NULL DEFAULT ''")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_file_index_path ON file_index(path)")
        }
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE messages ADD COLUMN elapsed_ms INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 11) {
            db.execSQL("ALTER TABLE messages ADD COLUMN diff_json TEXT")
        }
    }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return true
            }
            false
        }
}
