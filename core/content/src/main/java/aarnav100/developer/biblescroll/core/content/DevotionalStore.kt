package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.db.DevotionalDao
import aarnav100.developer.biblescroll.core.content.db.DevotionalEntity
import aarnav100.developer.biblescroll.core.content.db.DevotionalMonthEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A month file on the device. */
data class StoredMonth(
    val month: String,
    val contentVersion: String,
    val sha256: String,
    val source: BookSource,
    val count: Int,
    val storedBytes: Long,
    val importedAt: Long,
)

/** Devotional content on the device (content.db). An interface so tests can use memory. */
interface DevotionalStore {
    fun observeJson(date: String): Flow<String?>
    fun observeMonthJson(month: String): Flow<List<String>>
    fun observeMonth(month: String): Flow<StoredMonth?>
    suspend fun months(): List<StoredMonth>
    suspend fun replaceMonth(month: StoredMonth, devotionals: List<Triple<String, String, String>>)
    suspend fun removeMonth(month: String)
}

class RoomDevotionalStore @Inject constructor(private val dao: DevotionalDao) : DevotionalStore {
    override fun observeJson(date: String) = dao.observeJson(date).distinctUntilChanged()
    override fun observeMonthJson(month: String) = dao.observeMonthJson(month).distinctUntilChanged()
    override fun observeMonth(month: String) = dao.observeMonth(month).map { it?.toModel() }.distinctUntilChanged()
    override suspend fun months() = dao.months().map { it.toModel() }

    /** [devotionals] = (date, contentVersion, json). */
    override suspend fun replaceMonth(month: StoredMonth, devotionals: List<Triple<String, String, String>>) =
        dao.replaceMonth(
            DevotionalMonthEntity(month.month, month.contentVersion, month.sha256, month.source.name, month.count, month.storedBytes, month.importedAt),
            devotionals.map { (date, version, json) -> DevotionalEntity(date, month.month, version, json) },
        )

    override suspend fun removeMonth(month: String) = dao.removeMonth(month)

    private fun DevotionalMonthEntity.toModel() = StoredMonth(
        month, contentVersion, sha256, BookSource.valueOf(source), count, storedBytes, importedAt,
    )
}
