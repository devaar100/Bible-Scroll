package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.data.user.UserDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The real SQL: status filtering in the catalogue, and the days-done count / completion in user.db. */
@RunWith(AndroidJUnit4::class)
class PlanDaoTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val catalogDb = Room.inMemoryDatabaseBuilder(context, PlanCatalogDatabase::class.java).build()
    private val userDb = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build()

    @After
    fun close() {
        catalogDb.close()
        userDb.close()
    }

    private fun plan(id: String, status: String) = PlanEntity(id, "T $id", "S", "c", status, 2, 5, "easy", "v", "{}")

    @Test
    fun catalogueHidesUnpublishedPlans() = runBlocking {
        val dao = catalogDb.catalogDao()
        dao.replaceAll(
            listOf(plan("a", "reviewed"), plan("b", "auto_checked"), plan("c", "flagged")),
            listOf(PlanCategoryEntity("c1", "Cat", 1)),
            listOf(PlanCategoryItemEntity("c1", "c", 0), PlanCategoryItemEntity("c1", "b", 1), PlanCategoryItemEntity("c1", "a", 2)),
            "fp",
        )
        assertEquals(listOf("b", "a"), dao.observeCategoryItems().first().map { it.id })
        assertNull(dao.plan("c"))
        assertEquals("fp", dao.meta(PlanImporter.FINGERPRINT_KEY))
    }

    @Test
    fun progressCountsDaysAndCompletes() = runBlocking {
        val dao = userDb.planProgressDao()
        dao.insert(PlanEnrollmentEntity("e1", "a", "A", "v", "{}", 2, 5, "2026-11-18", 480, null, 1, 1))
        dao.setDayCompleted("e1", 1, true, 10)
        assertEquals(1, dao.observeEnrollments().first().single().daysDone)
        assertEquals(listOf("e1"), dao.activeWithReminders().map { it.id })
        dao.setDayCompleted("e1", 2, true, 20)
        val row = dao.observeEnrollments().first().single()
        assertEquals(20L, row.completedAt)
        assertEquals(emptyList<EnrollmentRow>(), dao.activeWithReminders())
        assertNull(dao.activeEnrollment("a"))
    }
}
