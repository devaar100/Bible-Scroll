package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.net.FirestoreApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

/** What happened to one "+1". */
enum class SendOutcome {
    /** The server added it. */
    COUNTED,

    /** Offline, timeout, rate limit or server error: try again later. */
    RETRY,

    /** The server refused (round closed, not open yet, or no such counter). Retrying won't help. */
    REJECTED,
}

/**
 * The Community vote counter in Cloud Firestore: community_votes/{YYYY-MM_ministryId}.count. The only write is an
 * increment transform of exactly 1 on an existing document; the request carries the month and ministry id and
 * nothing else (no auth, no key, no device or user id). Firestore sees the phone's IP address, as any server does.
 */
class CommunityCounter(
    private val api: FirestoreApi,
    private val projectId: String,
    private val io: CoroutineDispatcher,
) {
    private val root = "projects/$projectId/databases/(default)/documents"
    private val base = "https://firestore.googleapis.com/v1/$root"

    suspend fun addVote(month: String, ministryId: String): SendOutcome = withContext(io) {
        val body = FirestoreJson.incrementBody(root, docId(month, ministryId))
        try {
            val response = api.commit("$base:commit", body)
            response.errorBody()?.close()
            response.body()?.close()
            classify(response.code())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SendOutcome.RETRY
        }
    }

    /** Counts by ministry id for the given month's ministries; null if they couldn't be read. Missing docs count 0. */
    suspend fun totals(month: String, ministryIds: List<String>): Map<String, Long>? = withContext(io) {
        if (ministryIds.isEmpty()) return@withContext emptyMap()
        val names = ministryIds.map { "$root/community_votes/${docId(month, it)}" }
        try {
            val response = api.batchGet("$base:batchGet", FirestoreJson.batchGetBody(names))
            val text = response.body()?.use { it.string() }
            response.errorBody()?.close()
            if (!response.isSuccessful || text == null) return@withContext null
            val counts = FirestoreJson.parseBatchGet(text)
            ministryIds.associateWith { counts[docId(month, it)] ?: 0L }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private val MONTH = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")
        private val ID = Regex("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$")

        /** "2026-12_river". Checked strictly: ids come from a downloaded file and end up in a URL path. */
        fun docId(month: String, ministryId: String): String {
            require(MONTH.matches(month)) { "Not a month: $month" }
            require(ID.matches(ministryId)) { "Not a ministry id: $ministryId" }
            return "${month}_$ministryId"
        }

        fun classify(httpCode: Int): SendOutcome = when {
            httpCode in 200..299 -> SendOutcome.COUNTED
            httpCode == 408 || httpCode == 429 || httpCode >= 500 -> SendOutcome.RETRY
            else -> SendOutcome.REJECTED // 400 FAILED_PRECONDITION, 403 PERMISSION_DENIED, 404 NOT_FOUND
        }
    }
}

/** Firestore REST JSON, built and read by hand (no Firebase SDK). */
object FirestoreJson {
    private val parser = Json { ignoreUnknownKeys = true }

    /** A commit with one write: count += 1 on an existing document. */
    fun incrementBody(root: String, docId: String): JsonObject = buildJsonObject {
        putJsonArray("writes") {
            add(buildJsonObject {
                putJsonObject("transform") {
                    put("document", "$root/community_votes/$docId")
                    putJsonArray("fieldTransforms") {
                        add(buildJsonObject {
                            put("fieldPath", "count")
                            putJsonObject("increment") { put("integerValue", "1") }
                        })
                    }
                }
                putJsonObject("currentDocument") { put("exists", true) } // never creates a document
            })
        }
    }

    fun batchGetBody(names: List<String>): JsonObject = buildJsonObject {
        putJsonArray("documents") { names.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
    }

    /**
     * batchGet's response: a JSON array of {"found": {name, fields}} or {"missing": name}. Returns count by doc id
     * for found documents. count is an int64 sent as a string ("integerValue": "12").
     */
    fun parseBatchGet(text: String): Map<String, Long> {
        val items = parser.parseToJsonElement(text) as? JsonArray ?: throw SerializationException("batchGet: expected an array")
        return items.mapNotNull { item ->
            val found = (item as? JsonObject)?.get("found") as? JsonObject ?: return@mapNotNull null
            val name = found["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val count = (found["fields"] as? JsonObject)?.get("count")?.jsonObject?.get("integerValue")
                ?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            name.substringAfterLast('/') to count
        }.toMap()
    }
}
