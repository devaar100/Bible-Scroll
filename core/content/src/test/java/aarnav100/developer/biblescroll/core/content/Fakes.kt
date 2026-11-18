package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.BookFile
import aarnav100.developer.biblescroll.core.content.model.Citation
import aarnav100.developer.biblescroll.core.content.model.CrossReference
import aarnav100.developer.biblescroll.core.content.model.CrossReferenceSource
import aarnav100.developer.biblescroll.core.content.model.DiscoverEntry
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.model.ManifestBook
import aarnav100.developer.biblescroll.core.content.model.OriginalLanguage
import aarnav100.developer.biblescroll.core.content.model.Study
import aarnav100.developer.biblescroll.core.content.model.TextBasis
import aarnav100.developer.biblescroll.core.content.model.Word
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import aarnav100.developer.biblescroll.core.content.net.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

val TestJson = Json { ignoreUnknownKeys = true }

fun study(id: String, reviewed: Boolean = false) = Study(
    id = id, contentVersion = "0123456789ab",
    originalLanguage = OriginalLanguage(Language.GREEK, TextBasis.CRITICAL,
        listOf(Word("ἠγάπησεν", "ἀγαπάω", "ēgapēsen", "G0025", "loved", "V-AAI-3S"))),
    crossReferences = listOf(CrossReference("ROM.5.8", CrossReferenceSource.OPEN_BIBLE)),
    meaning = "m", historicalContext = "h", lifeInBibleTimes = "l", theology = "t", didYouKnow = "d", applyIt = "a",
    citations = listOf(Citation("STEP Bible", "CC BY 4.0", "https://stepbible.github.io/STEPBible-Data/")),
    reviewed = reviewed, model = "claude-opus-5-5",
)

/** A gzipped book file and its manifest entry (hash and size computed from the real bytes). */
fun bookFile(code: String, bookId: Int, vararg verses: Study, schemaVersion: Int = 2): Pair<ByteArray, ManifestBook> {
    val raw = TestJson.encodeToString(BookFile.serializer(), BookFile(1, code, schemaVersion, verses.toList())).toByteArray()
    val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
    val sha = Sha256.hex(gz)
    return gz to ManifestBook(code, bookId, "https://cdn.test/studies/v${sha.take(12)}/$code.json.gz",
        sha.take(12), verses.size, verses.count { it.reviewed }, sha, gz.size.toLong())
}

fun manifest(vararg books: ManifestBook, formatVersion: Int = 1, schemaVersion: Int = 2) = Manifest(
    formatVersion, schemaVersion, "release00000", books.toList(), DiscoverEntry("discover.json", 0, "0".repeat(64), 1),
)

private val emptyManifest = manifest()

class FakeContentStore : ContentStore {
    val studies = MutableStateFlow<Map<VerseKey, StudyRow>>(emptyMap())
    val booksState = MutableStateFlow<Map<Int, StoredBook>>(emptyMap())
    var replaceCalls = 0

    override fun observeStudyJson(key: VerseKey): Flow<String?> = studies.map { it[key]?.json }
    override fun observeBook(bookId: Int): Flow<StoredBook?> = booksState.map { it[bookId] }
    override fun observeBooks(): Flow<List<StoredBook>> = booksState.map { it.values.sortedBy { b -> b.bookId } }
    override suspend fun books(): List<StoredBook> = booksState.value.values.sortedBy { it.bookId }

    override suspend fun replaceBook(book: StoredBook, studies: List<Pair<VerseKey, StudyRow>>) {
        replaceCalls++
        this.studies.value = this.studies.value.filterKeys { it.bookId != book.bookId } + studies
        booksState.value = booksState.value + (book.bookId to book)
    }

    override suspend fun removeBook(bookId: Int) {
        studies.value = studies.value.filterKeys { it.bookId != bookId }
        booksState.value = booksState.value - bookId
    }
}

class FakeNetwork(initiallyOnline: Boolean, initiallyUnmetered: Boolean = initiallyOnline) : NetworkMonitor {
    val online = MutableStateFlow(initiallyOnline)
    val unmetered = MutableStateFlow(initiallyUnmetered)
    override val isOnline: Flow<Boolean> = online
    override val isUnmetered: Flow<Boolean> = unmetered
}

/** Serves a manifest and book files from memory; can be told to fail. */
class FakeContentApi : ContentApi {
    var manifest: Manifest = emptyManifest
    var manifestError: Exception? = null
    val files = mutableMapOf<String, ByteArray>()
    var downloadError: Exception? = null
    var manifestCalls = 0
    val downloads = mutableListOf<String>()

    override suspend fun manifest(): Manifest {
        manifestCalls++
        manifestError?.let { throw it }
        return manifest
    }

    override suspend fun download(url: String): ResponseBody {
        downloads += url
        downloadError?.let { throw it }
        return (files[url] ?: throw IOException("404 $url")).toResponseBody()
    }
}
