package com.lumiposture.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.lumiposture.app.data.local.entity.AiInsightEntity
import com.lumiposture.app.data.local.entity.StudySessionEntity
import com.lumiposture.app.data.local.entity.TelemetrySampleEntity
import kotlinx.coroutines.flow.Flow

data class StudySessionWithInsight(
    @Embedded val session: StudySessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val insight: AiInsightEntity?,
)

data class SessionWithDetails(
    @Embedded val session: StudySessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val samples: List<TelemetrySampleEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val insight: AiInsightEntity?,
)

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<TelemetrySampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInsight(insight: AiInsightEntity)

    @Transaction
    @Query("SELECT * FROM study_sessions ORDER BY startTimeMillis DESC")
    fun observeAllSessionsWithInsight(): Flow<List<StudySessionWithInsight>>

    @Transaction
    @Query("SELECT * FROM study_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionDetail(sessionId: Long): Flow<SessionWithDetails?>

    @Transaction
    @Query("SELECT * FROM study_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionDetail(sessionId: Long): SessionWithDetails?

    @Query("DELETE FROM study_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM study_sessions")
    suspend fun clearAllSessions()
}
