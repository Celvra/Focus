package fan.x0.focus.data.sources

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class SourcesDatabase(@Provided context: Context) : SQLiteOpenHelper(context, "sources.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE feeds(
                id TEXT PRIMARY KEY,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                added_at INTEGER NOT NULL,
                last_fetch_at INTEGER,
                error TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE articles(
                id TEXT PRIMARY KEY,
                feed_id TEXT NOT NULL,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                author TEXT,
                summary TEXT,
                content TEXT NOT NULL,
                published_at INTEGER,
                fetched_at INTEGER NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_articles_feed ON articles(feed_id, published_at DESC)")
        db.execSQL("CREATE INDEX idx_articles_time ON articles(published_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun query(sql: String, vararg args: String): Cursor = readableDatabase.rawQuery(sql, args)

    fun <T> read(block: (SQLiteDatabase) -> T): T = block(readableDatabase)

    fun <T> write(block: (SQLiteDatabase) -> T): T = block(writableDatabase)

}
