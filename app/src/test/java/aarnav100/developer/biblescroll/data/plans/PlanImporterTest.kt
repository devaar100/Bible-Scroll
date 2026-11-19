package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.testing.DirectoryAssets
import aarnav100.developer.biblescroll.testing.FakePlanCatalogDao
import aarnav100.developer.biblescroll.testing.MapAssets
import aarnav100.developer.biblescroll.testing.planIndex
import aarnav100.developer.biblescroll.testing.planJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlanImporterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val dao = FakePlanCatalogDao()

    private fun assets() = MapAssets(
        mutableMapOf(
            "plans/index.json" to planIndex(listOf("john"), mapOf("gospels-and-acts" to listOf("john", "mark", "luke"), "letters" to listOf("draft"))),
            "plans/john.json" to planJson("john", status = "reviewed"),
            "plans/mark.json" to planJson("mark"),
            "plans/luke.json" to planJson("luke", status = "flagged"),
            "plans/draft.json" to planJson("draft", status = "generated"),
        ),
    )

    private fun importer(assets: MapAssets, version: Long = 1) =
        PlanImporter(assets, dao, json, version, UnconfinedTestDispatcher())

    @Test
    fun firstLaunchImportsOnlyReviewedAndAutoChecked() = runTest {
        val result = importer(assets()).importIfNeeded()
        assertEquals(2, result.imported)
        assertEquals(listOf("luke", "draft"), result.hidden)
        assertEquals(setOf("john", "mark"), dao.plans.value.keys)
        val rows = dao.observeCategoryItems().first()
        assertEquals(listOf("start-here" to "john", "gospels-and-acts" to "john", "gospels-and-acts" to "mark"), rows.map { it.categoryId to it.id })
        assertFalse(dao.categories.value.any { it.id == "letters" }) // nothing publishable left in it
    }

    @Test
    fun secondLaunchWithTheSameAppAndBundleIsANoOp() = runTest {
        val assets = assets()
        importer(assets).importIfNeeded()
        assertTrue(importer(assets).importIfNeeded().skipped)
    }

    @Test
    fun anAppUpdateReimportsAndPicksUpChanges() = runTest {
        val assets = assets()
        importer(assets, version = 1).importIfNeeded()
        assets.files["plans/luke.json"] = planJson("luke", status = "reviewed")
        // Same version, files changed but index unchanged: still skipped (the index fingerprint is the signal) ...
        assertTrue(importer(assets, version = 1).importIfNeeded().skipped)
        // ... an update (new versionCode) always re-imports.
        val result = importer(assets, version = 2).importIfNeeded()
        assertFalse(result.skipped)
        assertTrue("luke" in dao.plans.value)
    }

    @Test
    fun aChangedIndexReimportsAndDropsPlansThatWentAway() = runTest {
        val assets = assets()
        importer(assets).importIfNeeded()
        assets.files["plans/index.json"] = planIndex(emptyList(), mapOf("gospels-and-acts" to listOf("mark")))
        importer(assets).importIfNeeded()
        assertEquals(setOf("mark"), dao.plans.value.keys)
        assertEquals(listOf("gospels-and-acts"), dao.categories.value.map { it.id })
    }

    @Test
    fun brokenOrMissingFilesAreReportedNotImported() = runTest {
        val assets = assets()
        assets.files["plans/mark.json"] = "{ not json"
        assets.files.remove("plans/john.json")
        val result = importer(assets).importIfNeeded()
        assertEquals(listOf("john", "mark"), result.broken)
        assertEquals(0, result.imported)
    }

    @Test
    fun noBundleMeansNothingToDo() = runTest {
        assertTrue(importer(MapAssets(mutableMapOf())).importIfNeeded().skipped)
    }

    @Test
    fun theRealBundledPlansAllParseAndShow() = runTest {
        val real = PlanImporter(DirectoryAssets(File("src/main/assets")), dao, json, 1, UnconfinedTestDispatcher())
        val result = real.importIfNeeded()
        assertTrue(result.broken.isEmpty())
        assertTrue("expected 25+ plans, got ${result.imported}", result.imported >= 25)
        val first = dao.observeCategoryItems().first().first()
        assertEquals(PlanImporter.START_HERE, first.categoryId)
        dao.plans.value.values.forEach { plan ->
            val file = json.decodeFromString<PlanFile>(plan.json)
            assertTrue(file.days.all { d -> d.passages.all { it.bookId != null } })
        }
    }
}
