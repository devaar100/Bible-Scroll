package aarnav100.developer.biblescroll.data.user

import aarnav100.developer.biblescroll.data.backup.BackupDao
import aarnav100.developer.biblescroll.data.community.CommunityVoteDao
import aarnav100.developer.biblescroll.data.community.CommunityVoteEntity
import aarnav100.developer.biblescroll.data.devotionals.DevotionalProgressDao
import aarnav100.developer.biblescroll.data.devotionals.DevotionalProgressEntity
import aarnav100.developer.biblescroll.data.plans.CustomPlanDao
import aarnav100.developer.biblescroll.data.plans.CustomPlanEntity
import aarnav100.developer.biblescroll.data.plans.PlanDayProgressEntity
import aarnav100.developer.biblescroll.data.plans.PlanEnrollmentEntity
import aarnav100.developer.biblescroll.data.plans.PlanProgressDao
import aarnav100.developer.biblescroll.data.streak.StreakDao
import aarnav100.developer.biblescroll.data.streak.StreakDayEntity
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/** The user's own data. Schema changes need a real migration (exported schemas in app/schemas). */
@Database(
    entities = [
        VerseMarkEntity::class, ReadingPositionEntity::class, ChapterReadEntity::class, NoteEntity::class,
        StreakDayEntity::class, PlanEnrollmentEntity::class, PlanDayProgressEntity::class,
        CustomPlanEntity::class, DevotionalProgressEntity::class, CommunityVoteEntity::class,
    ],
    version = UserDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds chapter_reads
        // 2 -> 3 is manual: MIGRATION_2_3 backfills created_at.
        AutoMigration(from = 3, to = 4), // adds verse_marks.studied and study_saved (default 0)
        AutoMigration(from = 4, to = 5), // adds verse_marks.liked_at/bookmarked_at/studied_at/study_saved_at (null)
        AutoMigration(from = 5, to = 6), // adds streak_days (H2)
        AutoMigration(from = 6, to = 7), // adds plan_enrollments and plan_day_progress (H8-H10)
        AutoMigration(from = 7, to = 8), // adds custom_plans (H13/H14)
        AutoMigration(from = 8, to = 9), // adds devotional_progress (H16)
        AutoMigration(from = 9, to = 10), // adds community_votes (C1)
    ],
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun streakDao(): StreakDao
    abstract fun planProgressDao(): PlanProgressDao
    abstract fun customPlanDao(): CustomPlanDao
    abstract fun devotionalProgressDao(): DevotionalProgressDao
    abstract fun communityVoteDao(): CommunityVoteDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val FILE_NAME = "user.db"

        /**
         * Bump with every schema change, and add the new table or column to data/backup/BackupFormat.kt:
         * ExportCoversSchemaTest fails until export covers the latest exported schema.
         */
        const val VERSION = 10
    }
}
