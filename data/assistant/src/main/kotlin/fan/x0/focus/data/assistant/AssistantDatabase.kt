package fan.x0.focus.data.assistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class AssistantDatabase(@Provided context: Context) : SQLiteOpenHelper(context, "assistant.db", null, 22) {
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
                plan_request_id TEXT NOT NULL DEFAULT '',
                kind TEXT NOT NULL DEFAULT 'chat',
                parent_id TEXT NOT NULL DEFAULT '',
                origin TEXT NOT NULL DEFAULT 'user',
                task_status TEXT NOT NULL DEFAULT 'none',
                worker_profile TEXT NOT NULL DEFAULT 'standard',
                task_note TEXT NOT NULL DEFAULT '',
                result_seen INTEGER NOT NULL DEFAULT 1
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
            CREATE TABLE compactions(
                conversation_id TEXT PRIMARY KEY,
                up_to_id INTEGER NOT NULL DEFAULT 0,
                summary TEXT NOT NULL,
                tokens_before INTEGER NOT NULL DEFAULT 0,
                tokens_after INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
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
        db.execSQL(
            """
            CREATE TABLE agent_groups(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                topic TEXT NOT NULL DEFAULT '',
                avatar_preset INTEGER NOT NULL DEFAULT 0,
                avatar_palette INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_message_at INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE crew_members(
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                name TEXT NOT NULL,
                duty TEXT NOT NULL DEFAULT '',
                avatar_preset INTEGER NOT NULL DEFAULT 0,
                avatar_palette INTEGER NOT NULL DEFAULT 0,
                avatar_image TEXT NOT NULL DEFAULT '',
                position INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                provider_id TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                reasoning TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_crew_members_group ON crew_members(group_id, active, position)")
        db.execSQL(
            """
            CREATE TABLE crew_messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id TEXT NOT NULL,
                author_type TEXT NOT NULL,
                author_id TEXT NOT NULL DEFAULT '',
                author_name TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                reply_to_id INTEGER,
                reactions TEXT,
                kind TEXT NOT NULL DEFAULT 'text',
                created_at INTEGER NOT NULL,
                reasoning TEXT NOT NULL DEFAULT '',
                elapsed_ms INTEGER NOT NULL DEFAULT 0,
                tools TEXT,
                attachments TEXT,
                stats_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_crew_messages_group ON crew_messages(group_id, id)")
        db.execSQL(
            """
            CREATE TABLE crew_runs(
                group_id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                last_user_message_id INTEGER NOT NULL DEFAULT 0,
                focus TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE im_accounts(
                id TEXT PRIMARY KEY,
                channel TEXT NOT NULL,
                name TEXT NOT NULL,
                config_json TEXT NOT NULL DEFAULT '{}',
                enabled INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL DEFAULT 'Disabled',
                status_detail TEXT NOT NULL DEFAULT '',
                platform_user_id TEXT NOT NULL DEFAULT '',
                owner_id TEXT NOT NULL DEFAULT '',
                cursor TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE im_chats(
                id TEXT PRIMARY KEY,
                account_id TEXT NOT NULL,
                external_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                conversation_id TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                send_thinking INTEGER NOT NULL DEFAULT 1,
                send_tools INTEGER NOT NULL DEFAULT 1,
                platform_context TEXT NOT NULL DEFAULT '',
                last_message_at INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(account_id, external_id, kind)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_im_chats_account ON im_chats(account_id, last_message_at)")
        db.execSQL("CREATE INDEX idx_im_chats_conversation ON im_chats(conversation_id)")
        db.execSQL(
            """
            CREATE TABLE im_seen(
                account_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                seen_at INTEGER NOT NULL,
                PRIMARY KEY(account_id, message_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_im_seen_time ON im_seen(seen_at)")
        db.execSQL(
            """
            CREATE TABLE agent_events(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                kind TEXT NOT NULL,
                target TEXT NOT NULL,
                thread_key TEXT NOT NULL DEFAULT '',
                payload TEXT NOT NULL DEFAULT '{}',
                dedupe_key TEXT,
                status TEXT NOT NULL DEFAULT 'pending',
                attempts INTEGER NOT NULL DEFAULT 0,
                due_at INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                processed_at INTEGER,
                error TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX idx_agent_events_dedupe ON agent_events(dedupe_key)")
        db.execSQL("CREATE INDEX idx_agent_events_pending ON agent_events(status, due_at, id)")
        db.execSQL(
            """
            CREATE TABLE thread_subscriptions(
                thread_key TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                reason TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                PRIMARY KEY(thread_key, conversation_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_thread_subscriptions_conversation ON thread_subscriptions(conversation_id)")
        db.execSQL(
            """
            CREATE TABLE turn_journal(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id TEXT NOT NULL,
                turn_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'running',
                tool_calls TEXT NOT NULL DEFAULT '',
                repeated_calls INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT '',
                started_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_turn_journal_conversation ON turn_journal(conversation_id, id)")
        db.execSQL(
            """
            CREATE TABLE agent_triggers(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                prompt TEXT NOT NULL,
                schedule TEXT NOT NULL DEFAULT '',
                timezone TEXT NOT NULL DEFAULT '',
                profile TEXT NOT NULL DEFAULT 'standard',
                target TEXT NOT NULL DEFAULT 'side',
                enabled INTEGER NOT NULL DEFAULT 1,
                webhook_token TEXT NOT NULL DEFAULT '',
                next_run INTEGER NOT NULL DEFAULT 0,
                last_run INTEGER NOT NULL DEFAULT 0,
                last_status TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_agent_triggers_due ON agent_triggers(enabled, next_run)")
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
        if (oldVersion < 12) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS compactions(
                    conversation_id TEXT PRIMARY KEY,
                    up_to_id INTEGER NOT NULL DEFAULT 0,
                    summary TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 13) {
            if (!hasColumn(db, "compactions", "tokens_before")) {
                db.execSQL("ALTER TABLE compactions ADD COLUMN tokens_before INTEGER NOT NULL DEFAULT 0")
            }
            if (!hasColumn(db, "compactions", "tokens_after")) {
                db.execSQL("ALTER TABLE compactions ADD COLUMN tokens_after INTEGER NOT NULL DEFAULT 0")
            }
        }
        if (oldVersion < 14) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agent_groups(
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    topic TEXT NOT NULL DEFAULT '',
                    avatar_preset INTEGER NOT NULL DEFAULT 0,
                    avatar_palette INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_message_at INTEGER NOT NULL DEFAULT 0,
                    archived INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS crew_members(
                    id TEXT PRIMARY KEY,
                    group_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    duty TEXT NOT NULL DEFAULT '',
                    avatar_preset INTEGER NOT NULL DEFAULT 0,
                    avatar_palette INTEGER NOT NULL DEFAULT 0,
                    avatar_image TEXT NOT NULL DEFAULT '',
                    position INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_crew_members_group ON crew_members(group_id, active, position)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS crew_messages(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id TEXT NOT NULL,
                    author_type TEXT NOT NULL,
                    author_id TEXT NOT NULL DEFAULT '',
                    author_name TEXT NOT NULL DEFAULT '',
                    content TEXT NOT NULL DEFAULT '',
                    reply_to_id INTEGER,
                    reactions TEXT,
                    kind TEXT NOT NULL DEFAULT 'text',
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_crew_messages_group ON crew_messages(group_id, id)")
        }
        if (oldVersion < 15) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS crew_runs(
                    group_id TEXT PRIMARY KEY,
                    started_at INTEGER NOT NULL,
                    last_user_message_id INTEGER NOT NULL DEFAULT 0,
                    focus TEXT NOT NULL DEFAULT '',
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 16) {
            db.execSQL("ALTER TABLE crew_members ADD COLUMN provider_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE crew_members ADD COLUMN model TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE crew_members ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE crew_messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE crew_messages ADD COLUMN elapsed_ms INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE crew_messages ADD COLUMN tools TEXT")
            db.execSQL("ALTER TABLE crew_messages ADD COLUMN attachments TEXT")
        }
        if (oldVersion < 17) {
            db.execSQL("ALTER TABLE crew_messages ADD COLUMN stats_json TEXT")
        }
        if (oldVersion < 18) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS im_accounts(
                    id TEXT PRIMARY KEY,
                    channel TEXT NOT NULL,
                    name TEXT NOT NULL,
                    config_json TEXT NOT NULL DEFAULT '{}',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    status TEXT NOT NULL DEFAULT 'Disabled',
                    status_detail TEXT NOT NULL DEFAULT '',
                    platform_user_id TEXT NOT NULL DEFAULT '',
                    owner_id TEXT NOT NULL DEFAULT '',
                    cursor TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS im_chats(
                    id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL,
                    external_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL DEFAULT '',
                    conversation_id TEXT,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    platform_context TEXT NOT NULL DEFAULT '',
                    last_message_at INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    UNIQUE(account_id, external_id, kind)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_im_chats_account ON im_chats(account_id, last_message_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_im_chats_conversation ON im_chats(conversation_id)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS im_seen(
                    account_id TEXT NOT NULL,
                    message_id TEXT NOT NULL,
                    seen_at INTEGER NOT NULL,
                    PRIMARY KEY(account_id, message_id)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_im_seen_time ON im_seen(seen_at)")
        }
        if (oldVersion < 19) {
            db.execSQL("ALTER TABLE im_chats ADD COLUMN send_thinking INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE im_chats ADD COLUMN send_tools INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 20) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN kind TEXT NOT NULL DEFAULT 'chat'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN parent_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE conversations ADD COLUMN origin TEXT NOT NULL DEFAULT 'user'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN task_status TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN worker_profile TEXT NOT NULL DEFAULT 'standard'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN task_note TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE conversations ADD COLUMN result_seen INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "UPDATE conversations SET kind = 'side', task_status = 'done', result_seen = 1 " +
                    "WHERE kind = 'chat' AND id NOT IN ('main', 'router')",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agent_events(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    target TEXT NOT NULL,
                    thread_key TEXT NOT NULL DEFAULT '',
                    payload TEXT NOT NULL DEFAULT '{}',
                    dedupe_key TEXT,
                    status TEXT NOT NULL DEFAULT 'pending',
                    attempts INTEGER NOT NULL DEFAULT 0,
                    due_at INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    processed_at INTEGER,
                    error TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_events_dedupe ON agent_events(dedupe_key)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_agent_events_pending ON agent_events(status, due_at, id)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS thread_subscriptions(
                    thread_key TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    reason TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY(thread_key, conversation_id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_thread_subscriptions_conversation " +
                    "ON thread_subscriptions(conversation_id)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS turn_journal(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id TEXT NOT NULL,
                    turn_id INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'running',
                    tool_calls TEXT NOT NULL DEFAULT '',
                    repeated_calls INTEGER NOT NULL DEFAULT 0,
                    note TEXT NOT NULL DEFAULT '',
                    started_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_turn_journal_conversation " +
                    "ON turn_journal(conversation_id, id)",
            )
        }
        if (oldVersion < 21) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agent_triggers(
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    schedule TEXT NOT NULL DEFAULT '',
                    timezone TEXT NOT NULL DEFAULT '',
                    profile TEXT NOT NULL DEFAULT 'standard',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    webhook_token TEXT NOT NULL DEFAULT '',
                    next_run INTEGER NOT NULL DEFAULT 0,
                    last_run INTEGER NOT NULL DEFAULT 0,
                    last_status TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_agent_triggers_due ON agent_triggers(enabled, next_run)",
            )
        }
        if (oldVersion < 22) {
            db.execSQL("ALTER TABLE agent_triggers ADD COLUMN target TEXT NOT NULL DEFAULT 'side'")
            db.execSQL("UPDATE conversations SET kind = 'side' WHERE kind = 'chat'")
            db.execSQL("DELETE FROM messages WHERE conversation_id = 'router'")
            db.execSQL("DELETE FROM conversation_todos WHERE conversation_id = 'router'")
            db.execSQL("DELETE FROM turn_journal WHERE conversation_id = 'router'")
            db.execSQL("DELETE FROM conversations WHERE id = 'router' OR kind = 'router'")
            db.execSQL("DELETE FROM agent_events")
            db.execSQL("DROP TABLE IF EXISTS thread_subscriptions")
            db.execSQL("UPDATE conversations SET kind = 'side' WHERE kind NOT IN ('main', 'side')")
            db.execSQL("UPDATE conversations SET kind = 'main' WHERE id = 'main'")
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
