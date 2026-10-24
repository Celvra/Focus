package fan.x0.focus.data.assistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class AssistantDatabase(@Provided context: Context) : SQLiteOpenHelper(context, "assistant.db", null, 6) {
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
                reasoning TEXT NOT NULL DEFAULT ''
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
                label TEXT
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
    }
}
