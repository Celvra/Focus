package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class ApprovalStore(private val database: AssistantDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private val stepsSerializer = ListSerializer(ApprovalStep.serializer())

    suspend fun create(title: String, summary: String, steps: List<ApprovalStep>): ApprovalRequest =
        withContext(Dispatchers.IO) {
            val request = ApprovalRequest(
                id = UUID.randomUUID().toString(),
                title = title.take(80),
                summary = summary.take(500),
                steps = steps,
                status = ApprovalStatus.Pending,
                createdAt = System.currentTimeMillis(),
            )
            val values = ContentValues().apply {
                put("id", request.id)
                put("title", request.title)
                put("summary", request.summary)
                put("steps", json.encodeToString(stepsSerializer, steps))
                put("status", "pending")
                put("created_at", request.createdAt)
            }
            database.writableDatabase.insertOrThrow("approvals", null, values)
            request
        }

    suspend fun decide(id: String, status: ApprovalStatus, result: String?): ApprovalRequest? =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("status", status.name.lowercase())
                put("decided_at", System.currentTimeMillis())
                put("result", result)
            }
            database.writableDatabase.update("approvals", values, "id = ?", arrayOf(id))
            get(id)
        }

    suspend fun get(id: String): ApprovalRequest? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, title, summary, steps, status, created_at, result FROM approvals WHERE id = ?",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toApproval() else null }
    }

    suspend fun pending(): List<ApprovalRequest> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, title, summary, steps, status, created_at, result FROM approvals " +
                "WHERE status = 'pending' ORDER BY created_at DESC",
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toApproval()) }
        }
    }

    private fun Cursor.toApproval(): ApprovalRequest {
        val steps = runCatching {
            json.decodeFromString(stepsSerializer, getString(3))
        }.getOrDefault(emptyList())
        return ApprovalRequest(
            id = getString(0),
            title = getString(1),
            summary = getString(2),
            steps = steps,
            status = when (getString(4)) {
                "approved" -> ApprovalStatus.Approved
                "rejected" -> ApprovalStatus.Rejected
                "expired" -> ApprovalStatus.Expired
                else -> ApprovalStatus.Pending
            },
            createdAt = getLong(5),
            result = if (isNull(6)) null else getString(6),
        )
    }
}
