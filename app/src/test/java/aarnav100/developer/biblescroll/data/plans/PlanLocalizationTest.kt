package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.core.content.model.DevotionalLocale
import aarnav100.developer.biblescroll.core.content.model.DevotionalSeries
import aarnav100.developer.biblescroll.testing.FakePlanCatalogDao
import aarnav100.developer.biblescroll.testing.MapAssets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plans and devotionals in the app's language, falling back to English field by field. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanLocalizationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val plan = """
        {"id":"john","contentVersion":"abc123abc123","title":"John in 3 days","shortDescription":"Meet Jesus",
         "description":"About John","category":"gospels-and-acts","status":"auto_checked","origin":"structural",
         "credits":"By Bible Scroll","estimatedMinutesPerDay":5,"difficulty":"easy",
         "days":[{"dayNumber":1,"title":"The Word","intro":"In the beginning","passages":[{"book":"JHN","startChapter":1,"startVerse":1,"endChapter":1,"endVerse":5}]},
                 {"dayNumber":2,"passages":[{"book":"JHN","startChapter":2,"startVerse":1,"endChapter":2,"endVerse":5}]}],
         "i18n":{"es":{"title":"Juan en 3 días","shortDescription":"Conoce a Jesús","description":"Sobre Juan",
                       "days":[{"dayNumber":1,"title":"La Palabra"}]}}}
    """.trimIndent()

    private val index = """
        {"formatVersion":1,"startHere":[],"categories":[{"id":"gospels-and-acts","title":"Gospels and Acts","plans":["john"],
         "i18n":{"es":{"title":"Evangelios y Hechos"}}}],"plans":[]}
    """.trimIndent()

    @Test
    fun localizedReplacesTranslatedFieldsAndKeepsTheRest() {
        val file = json.decodeFromString<PlanFile>(plan)
        val es = file.localized("es")
        assertEquals("Juan en 3 días", es.title)
        assertEquals("Sobre Juan", es.description)
        assertEquals("By Bible Scroll", es.credits) // not translated: English
        assertEquals("La Palabra", es.days[0].title)
        assertEquals("In the beginning", es.days[0].intro) // day 1 intro not translated
        assertEquals(file.days[0].passages, es.days[0].passages) // Scripture references untouched
        assertEquals(file, file.localized("de")) // no German: unchanged
    }

    @Test
    fun startedPlanTitleComesFromItsOwnCopy() {
        assertEquals("Juan en 3 días", localizedPlanTitle(plan, "John in 3 days", "es"))
        assertEquals("John in 3 days", localizedPlanTitle(plan, "John in 3 days", "en"))
        assertEquals("My plan", localizedPlanTitle("not json", "My plan", "es"))
    }

    @Test
    fun catalogueIsStoredInTheAppLanguageAndReimportedWhenItChanges() = runTest {
        val dao = FakePlanCatalogDao()
        var lang = "es"
        val assets = MapAssets(mutableMapOf("plans/index.json" to index, "plans/john.json" to plan))
        val importer = PlanImporter(assets, dao, json, 1, UnconfinedTestDispatcher()) { lang }

        assertFalse(importer.importIfNeeded().skipped)
        var row = dao.observeCategoryItems().first().single()
        assertEquals("Juan en 3 días", row.title)
        assertEquals("Conoce a Jesús", row.shortDescription)
        assertEquals("Evangelios y Hechos", row.categoryTitle)
        assertTrue(importer.importIfNeeded().skipped) // same language: no work

        lang = "en"
        assertFalse(importer.importIfNeeded().skipped) // language changed: re-import
        row = dao.observeCategoryItems().first().single()
        assertEquals("John in 3 days", row.title)
        assertEquals("Gospels and Acts", row.categoryTitle)
        assertTrue("full JSON (with translations) is kept", dao.plan("john")!!.json.contains("\"i18n\""))
    }

    @Test
    fun devotionalLocalizedKeepsScriptureAndFallsBackPerField() {
        val d = Devotional(
            id = "devotional-11-18", contentVersion = "v1", date = "11-18", dayOfYear = 322, title = "Light", theme = "Guidance",
            series = DevotionalSeries("Lamp", "Ordinary", 1, 5), coverStyle = "dawn", verses = emptyList(),
            context = "c", reflect = "r", applyIt = "a", prayer = "p",
            i18n = mapOf("es" to DevotionalLocale(title = "Luz", seriesTitle = "Lámpara", reflect = "reflexión")),
        )
        val es = d.localized("es")
        assertEquals("Luz", es.title)
        assertEquals("Lámpara", es.series.title)
        assertEquals(1, es.series.day)
        assertEquals("reflexión", es.reflect)
        assertEquals("c", es.context) // not translated
        assertEquals(d.verses, es.verses)
        assertEquals(d, d.localized("en"))
    }
}
