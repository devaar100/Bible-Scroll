package aarnav100.developer.biblescroll.core.content.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Mirrors the published content format built by pipeline/ (schema/study.json, schema/book.json,
 * schema/manifest.json). The app only ever receives the published form: internal pipeline fields
 * (check reasons, reviewer names, status) are stripped before upload, so they aren't modelled here.
 * Unknown keys are ignored when decoding, so the pipeline can add fields without breaking older apps.
 */

/** One verse's study. Prose sections are plain text written with AI help and checked or reviewed. */
@Serializable
data class Study(
    /** USFM book code, chapter, verse, e.g. "JHN.3.16". */
    val id: String,
    val contentVersion: String,
    val originalLanguage: OriginalLanguage,
    val crossReferences: List<CrossReference>,
    val meaning: String,
    val historicalContext: String,
    val lifeInBibleTimes: String,
    val theology: String,
    val didYouKnow: String,
    val applyIt: String,
    val citations: List<Citation>,
    /** A person approved this study (otherwise it passed automatic checks only). */
    val reviewed: Boolean,
    /** Model that wrote the prose, for the "AI-assisted" label. */
    val model: String,
    /**
     * Hash of the study prompt (pipeline meta.promptVersion), sent with "Report a problem". Optional: published
     * book files don't carry it yet, and older apps ignore it if they do.
     */
    val promptVersion: String? = null,
)

@Serializable
data class OriginalLanguage(
    val language: Language,
    val textBasis: TextBasis,
    val words: List<Word>,
)

@Serializable
enum class Language {
    @SerialName("hebrew") HEBREW,
    @SerialName("aramaic") ARAMAIC,
    @SerialName("greek") GREEK,
}

@Serializable
enum class TextBasis {
    /** Leningrad Codex (Hebrew/Aramaic). */
    @SerialName("leningrad") LENINGRAD,
    /** NA27/28-based critical text (Greek). */
    @SerialName("critical") CRITICAL,
    /** The KJV's Greek (Scrivener TR), for verses absent from the critical text. */
    @SerialName("textus_receptus") TEXTUS_RECEPTUS,
}

@Serializable
data class Word(
    val surface: String,
    val lemma: String,
    val transliteration: String,
    /** Disambiguated Strong's, e.g. "H7225G" or "G0976". */
    val strongs: String,
    /** The source's English for this word in context (may be empty). */
    val gloss: String,
    val morphology: String,
)

@Serializable
data class CrossReference(
    /** "ROM.8.28" or an inclusive range "JHN.1.1-JHN.1.3". */
    val ref: String,
    val source: CrossReferenceSource,
    /** TSK: the word or phrase in this verse the reference annotates. */
    val note: String? = null,
)

@Serializable
enum class CrossReferenceSource {
    @SerialName("OpenBible") OPEN_BIBLE,
    @SerialName("TSK") TSK,
}

@Serializable
data class Citation(val source: String, val licence: String, val url: String)

/** One published book file (studies/v{contentVersion}/{BOOK}.json.gz, gunzipped). */
@Serializable
data class BookFile(
    val formatVersion: Int,
    /** USFM code, e.g. "JHN". */
    val book: String,
    val schemaVersion: Int,
    val verses: List<Study>,
)

/** manifest.json at the content root. */
@Serializable
data class Manifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    /** Hash of the whole release. */
    val release: String,
    val books: List<ManifestBook>,
    val discover: DiscoverEntry,
    /** Daily devotionals, one file per month. Absent in manifests published before devotionals existed. */
    val devotionals: ManifestDevotionals = ManifestDevotionals(),
    /** Community spotlight ministries (C1/C2). Absent until a ministries file is published. */
    val community: CommunityEntry? = null,
    /** Community donation history. Absent until a history file is published. */
    val history: HistoryEntry? = null,
)

@Serializable
data class ManifestBook(
    val book: String,
    /** The app's book id, 1-66. */
    val bookId: Int,
    /** Absolute URL (or relative to manifest.json). */
    val url: String,
    /** First 12 hex chars of [sha256]; changes whenever the file does. */
    val contentVersion: String,
    val verseCount: Int,
    val reviewedCount: Int,
    val sha256: String,
    val bytes: Long,
)

@Serializable
data class DiscoverEntry(val url: String, val count: Int, val sha256: String, val bytes: Long)

/** assets/studies/manifest.json: the bundled subset (url = asset file name). */
@Serializable
data class BundledManifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    val release: String,
    val books: List<ManifestBook>,
)

/** discover.json (format 2): curated Discover verses, in feed order, each with the theme its card is drawn in. */
@Serializable
data class DiscoverFile(val formatVersion: Int, val verses: List<DiscoverVerse>)

@Serializable
data class DiscoverVerse(
    /** Study id, e.g. "JER.17.8". */
    val id: String,
    /** The card's theme label as published, e.g. "Trust and guidance" (any text, 1-40 characters). Required: a card without one isn't shown. */
    val theme: String,
)
