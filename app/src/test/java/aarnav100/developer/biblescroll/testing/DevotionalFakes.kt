package aarnav100.developer.biblescroll.testing

import aarnav100.developer.biblescroll.core.content.BookSource
import aarnav100.developer.biblescroll.core.content.DevotionalDownloader
import aarnav100.developer.biblescroll.core.content.DevotionalImporter
import aarnav100.developer.biblescroll.core.content.DevotionalRepository
import aarnav100.developer.biblescroll.core.content.DevotionalStore
import aarnav100.developer.biblescroll.core.content.StoredMonth
import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.core.content.model.DevotionalPassage
import aarnav100.developer.biblescroll.core.content.model.DevotionalSeries
import aarnav100.developer.biblescroll.data.devotionals.DevotionalProgressDao
import aarnav100.developer.biblescroll.data.devotionals.DevotionalProgressEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File

class FakeDevotionalStoreApp : DevotionalStore {
    val months = MutableStateFlow<Map<String, StoredMonth>>(emptyMap())
    val rows = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    override fun observeJson(date: String): Flow<String?> = rows.map { it[date]?.second }
    override fun observeMonthJson(month: String): Flow<List<String>> = rows.map { r -> r.filterValues { it.first == month }.toSortedMap().values.map { it.second } }
    override fun observeMonth(month: String): Flow<StoredMonth?> = months.map { it[month] }
    override suspend fun months() = months.value.values.toList()
    override suspend fun replaceMonth(month: StoredMonth, devotionals: List<Triple<String, String, String>>) {
        rows.update { r -> r.filterValues { it.first != month.month } + devotionals.associate { (d, _, j) -> d to (month.month to j) } }
        months.update { it + (month.month to month) }
    }
    override suspend fun removeMonth(month: String) {
        rows.update { r -> r.filterValues { it.first != month } }
        months.update { it - month }
    }

    /** Puts [devotionals] on the "device" as if their month had been imported. */
    fun put(vararg devotionals: Devotional) {
        devotionals.groupBy { it.date.take(2) }.forEach { (month, days) ->
            months.update { it + (month to StoredMonth(month, "v", "s", BookSource.BUNDLED, days.size, 1, 1)) }
            rows.update { r -> r + days.associate { it.date to (month to ContentJson.encodeToString(Devotional.serializer(), it)) } }
        }
    }
}

class FakeDevotionalProgressDao : DevotionalProgressDao {
    val rows = MutableStateFlow<Map<String, DevotionalProgressEntity>>(emptyMap())
    override fun observeYear(year: String) = rows.map { r -> r.keys.filter { it.startsWith("$year-") } }
    override suspend fun insertIfAbsent(row: DevotionalProgressEntity): Long {
        if (row.readFor in rows.value) return -1
        rows.update { it + (row.readFor to row) }
        return 1
    }
}

fun sampleDevotional(date: String, prayer: String? = "Lord, help me.", style: String = "dawn") = Devotional(
    id = "devotional-$date", contentVersion = "cv$date", date = date, dayOfYear = 1, title = "Title $date", theme = "Theme $date",
    series = DevotionalSeries("Waiting well", "Before Advent", 3, 12), coverStyle = style,
    verses = listOf(DevotionalPassage("JHN", 3, 15, 3, 17), DevotionalPassage("PSA", 119, 105, 119, 105)),
    context = "Context text", reflect = "Reflect text", applyIt = "Apply text", prayer = prayer,
)

/** A content repository over [store]; offline by default, so tests never download. */
fun devotionalContent(store: FakeDevotionalStoreApp, dispatcher: CoroutineDispatcher, scope: CoroutineScope, online: Boolean = false) =
    DevotionalRepository(
        store,
        DevotionalDownloader(FakeStudyApi(), store, DevotionalImporter(store, ContentJson), File(System.getProperty("java.io.tmpdir")), scope, dispatcher, "https://cdn.test/"),
        FakeNetworkMonitor(online), ContentJson, dispatcher,
    )
