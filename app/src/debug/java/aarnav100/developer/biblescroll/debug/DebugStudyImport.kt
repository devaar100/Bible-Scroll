package aarnav100.developer.biblescroll.debug

import aarnav100.developer.biblescroll.core.content.BookCodes
import aarnav100.developer.biblescroll.core.content.BookSource
import aarnav100.developer.biblescroll.core.content.ContentStore
import aarnav100.developer.biblescroll.core.content.Sha256
import aarnav100.developer.biblescroll.core.content.StoredBook
import aarnav100.developer.biblescroll.core.content.StudyRow
import aarnav100.developer.biblescroll.core.content.VerseKey
import aarnav100.developer.biblescroll.core.content.model.Study
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject

/*
 * DEBUG BUILDS ONLY (src/debug). Test B8/D2 with pipeline studies before anything is published:
 *
 *   adb shell mkdir -p /sdcard/Android/data/aarnav100.developer.biblescroll/files/debug-studies
 *   adb push pipeline/out/studies/JHN.1.12.json /sdcard/Android/data/aarnav100.developer.biblescroll/files/debug-studies/
 *   adb shell am broadcast -a aarnav100.developer.biblescroll.debug.IMPORT_STUDIES -p aarnav100.developer.biblescroll
 *
 * Every written study (auto_checked, flagged or reviewed; flagged ones too, so you can read them) is converted
 * the way pipeline publish.public_study does and stored by book. An imported book REPLACES that book's stored
 * studies; Settings → Storage → Remove downloaded studies puts things back.
 */

/** pipeline/out/studies/<id>.json (schema/study.json v3) -> the app's published Study, or null if not written. */
object PipelineStudy {
    private val PROSE = listOf("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt")

    fun toAppStudy(text: String, json: Json): Study? {
        val src = json.parseToJsonElement(text).jsonObject
        val meta = src["meta"]?.jsonObject ?: return null
        val model = meta["model"]?.jsonPrimitive?.contentOrNull ?: return null
        if (PROSE.any { src[it]?.jsonPrimitive?.contentOrNull.isNullOrBlank() }) return null
        val public: JsonObject = buildJsonObject {
            for (key in listOf("id", "contentVersion", "originalLanguage", "crossReferences", "citations", *PROSE.toTypedArray())) {
                put(key, src.getValue(key))
            }
            put("reviewed", JsonPrimitive(src["status"]?.jsonPrimitive?.contentOrNull == "reviewed"))
            put("model", JsonPrimitive(model))
            meta["promptVersion"]?.jsonPrimitive?.contentOrNull?.let { put("promptVersion", JsonPrimitive(it)) }
        }
        return json.decodeFromJsonElement(Study.serializer(), public)
    }
}

data class DebugImportResult(val imported: List<String>, val skipped: List<String>, val books: List<String>)

class DebugStudyImporter @Inject constructor(private val store: ContentStore, private val json: Json) {
    suspend fun importFrom(dir: File): DebugImportResult {
        val imported = mutableListOf<Study>()
        val skipped = mutableListOf<String>()
        dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.forEach { file ->
            val study = try {
                PipelineStudy.toAppStudy(file.readText(), json)
            } catch (e: Exception) {
                null
            }
            if (study == null || BookCodes.parse(study.id) == null) skipped += file.name else imported += study
        }
        val byBook = imported.groupBy { BookCodes.parse(it.id)!!.bookId }
        for ((bookId, studies) in byBook) {
            val rows = studies.map { s ->
                val key = BookCodes.parse(s.id)!!
                VerseKey(key.bookId, key.chapter, key.verse) to StudyRow(s.contentVersion, json.encodeToString(Study.serializer(), s))
            }
            val digest = Sha256.hex(rows.joinToString { it.second.json }.toByteArray())
            store.replaceBook(
                StoredBook(bookId, BookCodes.code(bookId), "debug-${digest.take(6)}", digest, BookSource.DOWNLOADED,
                    studies.size, studies.count { it.reviewed }, rows.sumOf { it.second.json.length.toLong() }),
                rows,
            )
        }
        return DebugImportResult(imported.map { it.id }, skipped, byBook.keys.sorted().map(BookCodes::code))
    }
}

@AndroidEntryPoint
class DebugStudyImportReceiver : BroadcastReceiver() {
    @Inject lateinit var importer: DebugStudyImporter

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val dir = File(context.getExternalFilesDir(null), "debug-studies")
        scope.launch {
            try {
                val result = importer.importFrom(dir)
                val summary = "Imported ${result.imported.size} studies into ${result.books}; skipped ${result.skipped.size} " +
                    "(not written or unreadable): ${result.skipped.take(10)}"
                Log.i(TAG, summary)
                pending.resultData = summary // shown by `adb shell am broadcast`
            } catch (e: Exception) {
                Log.e(TAG, "Debug study import failed", e)
                pending.resultData = "Failed: ${e.message}"
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugStudyImport"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
