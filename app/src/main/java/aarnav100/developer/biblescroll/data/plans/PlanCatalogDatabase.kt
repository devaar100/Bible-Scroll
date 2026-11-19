package aarnav100.developer.biblescroll.data.plans

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * The plan catalogue: a copy of assets/plans/, rebuilt on first launch and whenever the app version or the
 * bundled index changes. It holds no user data (that's in user.db), so a schema change may simply wipe it.
 */

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "short_description") val shortDescription: String,
    val category: String,
    val status: String,
    val days: Int,
    @ColumnInfo(name = "minutes_per_day") val minutesPerDay: Int,
    val difficulty: String,
    @ColumnInfo(name = "content_version") val contentVersion: String,
    /** The plan file as shipped (PlanFile JSON), for the detail page and for enrolling. */
    val json: String,
)

@Entity(tableName = "plan_categories", primaryKeys = ["id"])
data class PlanCategoryEntity(val id: String, val title: String, val position: Int)

/** Plans in a category, in index order. category "start-here" = the featured row. */
@Entity(tableName = "plan_category_items", primaryKeys = ["category_id", "plan_id"])
data class PlanCategoryItemEntity(
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    val position: Int,
)

@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(@PrimaryKey val key: String, val value: String)

data class CategoryItemRow(
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "category_title") val categoryTitle: String,
    @ColumnInfo(name = "category_position") val categoryPosition: Int,
    val id: String,
    val title: String,
    @ColumnInfo(name = "short_description") val shortDescription: String,
    val category: String,
    val days: Int,
    @ColumnInfo(name = "minutes_per_day") val minutesPerDay: Int,
    val difficulty: String,
)

@Dao
interface PlanCatalogDao {
    @Query(
        "SELECT c.id AS category_id, c.title AS category_title, c.position AS category_position, p.id, p.title, " +
            "p.short_description, p.category, p.days, p.minutes_per_day, p.difficulty " +
            "FROM plan_category_items i JOIN plan_categories c ON c.id = i.category_id JOIN plans p ON p.id = i.plan_id " +
            "WHERE p.status IN ('reviewed', 'auto_checked') ORDER BY c.position, i.position",
    )
    fun observeCategoryItems(): Flow<List<CategoryItemRow>>

    @Query("SELECT * FROM plans WHERE id = :id AND status IN ('reviewed', 'auto_checked')")
    fun observePlan(id: String): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE id = :id AND status IN ('reviewed', 'auto_checked')")
    suspend fun plan(id: String): PlanEntity?

    @Query("SELECT value FROM catalog_meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Upsert suspend fun putMeta(meta: CatalogMetaEntity)

    @Query("DELETE FROM plans") suspend fun clearPlans()
    @Query("DELETE FROM plan_categories") suspend fun clearCategories()
    @Query("DELETE FROM plan_category_items") suspend fun clearItems()
    @Insert suspend fun insertPlans(plans: List<PlanEntity>)
    @Insert suspend fun insertCategories(categories: List<PlanCategoryEntity>)
    @Insert suspend fun insertItems(items: List<PlanCategoryItemEntity>)

    /** Replaces the whole catalogue in one transaction, so readers never see half an import. */
    @Transaction
    suspend fun replaceAll(
        plans: List<PlanEntity>,
        categories: List<PlanCategoryEntity>,
        items: List<PlanCategoryItemEntity>,
        fingerprint: String,
    ) {
        clearItems()
        clearCategories()
        clearPlans()
        insertPlans(plans)
        insertCategories(categories)
        insertItems(items)
        putMeta(CatalogMetaEntity(PlanImporter.FINGERPRINT_KEY, fingerprint))
    }
}

@Database(
    entities = [PlanEntity::class, PlanCategoryEntity::class, PlanCategoryItemEntity::class, CatalogMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PlanCatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): PlanCatalogDao

    companion object {
        const val FILE_NAME = "plans.db"
    }
}
