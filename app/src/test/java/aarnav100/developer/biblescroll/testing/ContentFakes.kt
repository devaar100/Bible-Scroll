package aarnav100.developer.biblescroll.testing

import aarnav100.developer.biblescroll.core.content.ContentStore
import aarnav100.developer.biblescroll.core.content.Sha256
import aarnav100.developer.biblescroll.core.content.StoredBook
import aarnav100.developer.biblescroll.core.content.StudyRow
import aarnav100.developer.biblescroll.core.content.VerseKey
import aarnav100.developer.biblescroll.core.content.model.BookFile
import aarnav100.developer.biblescroll.core.content.model.Citation
import aarnav100.developer.biblescroll.core.content.model.CrossReference
import aarnav100.developer.biblescroll.core.content.model.CrossReferenceSource
import aarnav100.developer.biblescroll.core.content.model.DiscoverEntry
import aarnav100.developer.biblescroll.core.content.model.DiscoverFile
import aarnav100.developer.biblescroll.core.content.model.DiscoverVerse
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

/* Small fakes for app-side tests of study features (the core module's own fakes aren't visible here). */

val ContentJson = Json { ignoreUnknownKeys = true }

fun sampleStudy(id: String, meaning: String = "God loves the world. He gave his Son.", xrefs: List<String> = listOf("JHN.14.6")) = Study(
    id = id, contentVersion = "0123456789ab",
    originalLanguage = OriginalLanguage(Language.GREEK, TextBasis.CRITICAL,
        listOf(Word("ἠγάπησεν", "ἀγαπάω", "ēgapēsen", "G0025", "loved", "V-AAI-3S"))),
    crossReferences = xrefs.map { CrossReference(it, CrossReferenceSource.OPEN_BIBLE) },
    meaning = meaning, historicalContext = "h", lifeInBibleTimes = "l", theology = "t", didYouKnow = "d", applyIt = "a",
    citations = listOf(Citation("STEP Bible", "CC BY 4.0", "https://stepbible.github.io/STEPBible-Data/")),
    reviewed = false, model = "claude-opus-5-5",
)

class FakeContent {
    val store = FakeStudyStore()
    val api = FakeStudyApi()
    val network = FakeNetworkMonitor()

    /** Publish a book on the fake CDN (manifest + gz file); optionally a discover list too. */
    fun publish(code: String, bookId: Int, vararg studies: Study, discover: List<String> = emptyList()) {
        val raw = ContentJson.encodeToString(BookFile.serializer(), BookFile(1, code, 2, studies.toList())).toByteArray()
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
        val sha = Sha256.hex(gz)
        val url = "https://cdn.test/studies/v${sha.take(12)}/$code.json.gz"
        val discoverBytes = ContentJson.encodeToString(DiscoverFile.serializer(), DiscoverFile(2, discover.map { DiscoverVerse(it, "trust") })).toByteArray()
        val manifest = Manifest(1, 2, "release00000",
            (api.manifest?.books.orEmpty().filter { it.bookId != bookId }) +
                ManifestBook(code, bookId, url, sha.take(12), studies.size, 0, sha, gz.size.toLong()),
            DiscoverEntry("https://cdn.test/discover.json", discover.size, Sha256.hex(discoverBytes), discoverBytes.size.toLong()))
        api.manifest = manifest
        api.files[url] = gz
        api.files["https://cdn.test/manifest.json"] = ContentJson.encodeToString(Manifest.serializer(), manifest).toByteArray()
        api.files["https://cdn.test/discover.json"] = discoverBytes
    }
}

class FakeStudyStore : ContentStore {
    val studies = MutableStateFlow<Map<VerseKey, StudyRow>>(emptyMap())
    val books = MutableStateFlow<Map<Int, StoredBook>>(emptyMap())
    override fun observeStudyJson(key: VerseKey): Flow<String?> = studies.map { it[key]?.json }
    override fun observeBook(bookId: Int): Flow<StoredBook?> = books.map { it[bookId] }
    override fun observeBooks(): Flow<List<StoredBook>> = books.map { it.values.sortedBy { b -> b.bookId } }
    override suspend fun books(): List<StoredBook> = books.value.values.sortedBy { it.bookId }
    override suspend fun replaceBook(book: StoredBook, studies: List<Pair<VerseKey, StudyRow>>) {
        this.studies.value = this.studies.value.filterKeys { it.bookId != book.bookId } + studies
        books.value = books.value + (book.bookId to book)
    }
    override suspend fun removeBook(bookId: Int) {
        studies.value = studies.value.filterKeys { it.bookId != bookId }
        books.value = books.value - bookId
    }
}

class FakeStudyApi : ContentApi {
    var manifest: Manifest? = null
    val files = mutableMapOf<String, ByteArray>()
    var offline = false
    val downloads = mutableListOf<String>()
    override suspend fun manifest(): Manifest {
        if (offline) throw IOException("offline")
        return manifest ?: throw IOException("404")
    }
    override suspend fun download(url: String): ResponseBody {
        if (offline) throw IOException("offline")
        downloads += url
        return (files[url] ?: throw IOException("404 $url")).toResponseBody()
    }
}

class FakeNetworkMonitor(online: Boolean = true, unmetered: Boolean = true) : NetworkMonitor {
    val onlineState = MutableStateFlow(online)
    val unmeteredState = MutableStateFlow(unmetered)
    override val isOnline: Flow<Boolean> = onlineState
    override val isUnmetered: Flow<Boolean> = unmeteredState
}
