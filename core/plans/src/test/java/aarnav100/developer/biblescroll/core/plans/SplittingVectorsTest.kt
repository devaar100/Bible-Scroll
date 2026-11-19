package aarnav100.developer.biblescroll.core.plans

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The app's splitter must match the pipeline's (pipeline/src/pipeline/plans/splitting.py) exactly.
 * Vectors come from `python -m pipeline plans vectors`; the pipeline's own tests check they're current.
 */
class SplittingVectorsTest {
    private val vectors: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("splitting_vectors.json")!!.readText(),
    ).jsonObject
    private val versesBin = File("../../app/src/main/assets/plans/verses.bin")
    private val data by lazy { VerseData.read(versesBin.inputStream()) }

    private fun ints(o: JsonObject, key: String) = o.getValue(key).jsonArray.map { it.jsonPrimitive.int }.toIntArray()

    @Test
    fun theVectorsWereMadeFromTheBundledVerseData() {
        val sha = MessageDigest.getInstance("SHA-256").digest(versesBin.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("verses.bin changed: regenerate vectors with `python -m pipeline plans vectors`", vectors.getValue("versesBinSha256").jsonPrimitive.content, sha)
    }

    @Test
    fun syntheticCasesMatchThePipeline() {
        val raw = vectors.getValue("raw").jsonArray
        assertEquals(40, raw.size)
        raw.forEachIndexed { n, element ->
            val c = element.jsonObject
            val words = ints(c, "words")
            val tiers = ints(c, "tiers")
            val days = c.getValue("days").jsonPrimitive.int
            val tolerance = c.getValue("tolerance").jsonPrimitive.double
            assertArrayEquals("case $n splitCuts", ints(c, "splitCuts"), PlanSplitter.splitCuts(words, tiers, days, tolerance))
            val plan = PlanSplitter.planCuts(words, tiers, days)
            assertArrayEquals("case $n planCuts", ints(c, "planCuts"), plan)
            assertEquals("case $n estimate", c.getValue("estimateMinutes").jsonPrimitive.int, PlanSplitter.estimateMinutes(PlanSplitter.dayWords(words, plan)))
        }
    }

    @Test
    fun realPlansMatchThePipelineDayForDay() {
        val codes = listOf(
            "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH", "2CH", "EZR",
            "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER", "LAM", "EZK", "DAN", "HOS", "JOL", "AMO",
            "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL",
            "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL", "1TH", "2TH", "1TI",
            "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN", "JUD", "REV",
        )
        val plans = vectors.getValue("plans").jsonArray
        assert(plans.size >= 15)
        for (element in plans) {
            val c = element.jsonObject
            val books = c.getValue("books").jsonArray.map { codes.indexOf(it.jsonPrimitive.content) + 1 }
            val days = c["days"]?.jsonPrimitive?.intOrNull ?: data.daysForMinutes(books, c.getValue("minutes").jsonPrimitive.int)
            val name = c.getValue("books").toString() + " " + (c["days"] ?: c["minutes"])
            assertEquals("$name days", c.getValue("expectedDays").jsonPrimitive.int, days)
            val plan = data.plan(books, days)
            assertEquals("$name minutes", c.getValue("minutesPerDay").jsonPrimitive.int, plan.minutesPerDay)
            val expected = c.getValue("passages").jsonArray.map { day ->
                day.jsonArray.map { p ->
                    val a = p.jsonArray
                    Passage(codes.indexOf(a[0].jsonPrimitive.content) + 1, a[1].jsonPrimitive.int, a[2].jsonPrimitive.int, a[3].jsonPrimitive.int, a[4].jsonPrimitive.int)
                }
            }
            assertEquals(name, expected, plan.days.map { it.passages })
        }
    }

    @Test
    fun verseDataLooksLikeTheKjv() {
        assertEquals(31_102, data.verseCount)
        assertEquals(150, data.chapterCount(19))
        assertEquals(21, data.chapterCount(43))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aDamagedFileFailsLoudly() {
        VerseData.read(versesBin.readBytes().copyOf(1000).inputStream())
    }
}
