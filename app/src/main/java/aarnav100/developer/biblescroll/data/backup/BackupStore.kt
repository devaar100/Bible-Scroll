package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.community.CommunityVoteEntity
import aarnav100.developer.biblescroll.data.devotionals.DevotionalProgressEntity
import aarnav100.developer.biblescroll.data.plans.CustomPlanEntity
import aarnav100.developer.biblescroll.data.plans.PlanDayProgressEntity
import aarnav100.developer.biblescroll.data.plans.PlanEnrollmentEntity
import aarnav100.developer.biblescroll.data.streak.StreakDayEntity
import aarnav100.developer.biblescroll.data.user.ChapterReadEntity
import aarnav100.developer.biblescroll.data.user.NoteEntity
import aarnav100.developer.biblescroll.data.user.ReadingPositionEntity
import aarnav100.developer.biblescroll.data.user.UserDatabase
import aarnav100.developer.biblescroll.data.user.VerseMarkEntity
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import javax.inject.Inject

/** Whole-table reads and bulk upserts for export and import. Nothing here deletes. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM verse_marks") suspend fun verseMarks(): List<VerseMarkEntity>
    @Query("SELECT * FROM reading_positions") suspend fun readingPositions(): List<ReadingPositionEntity>
    @Query("SELECT * FROM chapter_reads") suspend fun chapterReads(): List<ChapterReadEntity>
    @Query("SELECT * FROM notes") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM streak_days") suspend fun streakDays(): List<StreakDayEntity>
    @Query("SELECT * FROM plan_enrollments") suspend fun planEnrollments(): List<PlanEnrollmentEntity>
    @Query("SELECT * FROM plan_day_progress") suspend fun planDayProgress(): List<PlanDayProgressEntity>
    @Query("SELECT * FROM custom_plans") suspend fun customPlans(): List<CustomPlanEntity>
    @Query("SELECT * FROM devotional_progress") suspend fun devotionalProgress(): List<DevotionalProgressEntity>
    @Query("SELECT * FROM community_votes") suspend fun communityVotes(): List<CommunityVoteEntity>

    @Upsert suspend fun upsertVerseMarks(rows: List<VerseMarkEntity>)
    @Upsert suspend fun upsertReadingPositions(rows: List<ReadingPositionEntity>)
    @Upsert suspend fun upsertChapterReads(rows: List<ChapterReadEntity>)
    @Upsert suspend fun upsertNotes(rows: List<NoteEntity>)
    @Upsert suspend fun upsertStreakDays(rows: List<StreakDayEntity>)
    @Upsert suspend fun upsertPlanEnrollments(rows: List<PlanEnrollmentEntity>)
    @Upsert suspend fun upsertPlanDayProgress(rows: List<PlanDayProgressEntity>)
    @Upsert suspend fun upsertCustomPlans(rows: List<CustomPlanEntity>)
    @Upsert suspend fun upsertDevotionalProgress(rows: List<DevotionalProgressEntity>)
    @Upsert suspend fun upsertCommunityVotes(rows: List<CommunityVoteEntity>)
}

/** The user database as backup rows. Abstracted so merge and import are testable without Room. */
interface BackupStore {
    suspend fun readAll(): BackupTables

    /**
     * In one transaction: reads every table, passes it to [merge], and upserts the rows [merge] returns.
     * If anything throws, nothing is written.
     */
    suspend fun mergeInTransaction(merge: (existing: BackupTables) -> BackupTables)
}

class RoomBackupStore @Inject constructor(private val db: UserDatabase) : BackupStore {
    private val dao get() = db.backupDao()

    override suspend fun readAll(): BackupTables = db.withTransaction { read() }

    override suspend fun mergeInTransaction(merge: (existing: BackupTables) -> BackupTables) {
        db.withTransaction {
            val write = merge(read())
            dao.upsertVerseMarks(write.verseMarks.map { it.toEntity() })
            dao.upsertReadingPositions(write.readingPositions.map { it.toEntity() })
            dao.upsertChapterReads(write.chapterReads.map { it.toEntity() })
            dao.upsertNotes(write.notes.map { it.toEntity() })
            dao.upsertStreakDays(write.streakDays.map { it.toEntity() })
            dao.upsertPlanEnrollments(write.planEnrollments.map { it.toEntity() })
            dao.upsertPlanDayProgress(write.planDayProgress.map { it.toEntity() })
            dao.upsertCustomPlans(write.customPlans.map { it.toEntity() })
            dao.upsertDevotionalProgress(write.devotionalProgress.map { it.toEntity() })
            dao.upsertCommunityVotes(write.communityVotes.map { it.toEntity() })
        }
    }

    private suspend fun read() = BackupTables(
        verseMarks = dao.verseMarks().map { it.toRow() },
        readingPositions = dao.readingPositions().map { it.toRow() },
        chapterReads = dao.chapterReads().map { it.toRow() },
        notes = dao.notes().map { it.toRow() },
        streakDays = dao.streakDays().map { it.toRow() },
        planEnrollments = dao.planEnrollments().map { it.toRow() },
        planDayProgress = dao.planDayProgress().map { it.toRow() },
        customPlans = dao.customPlans().map { it.toRow() },
        devotionalProgress = dao.devotionalProgress().map { it.toRow() },
        communityVotes = dao.communityVotes().map { it.toRow() },
    )
}
