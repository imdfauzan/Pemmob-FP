package com.lumiposture.app.data.repository

import com.lumiposture.app.domain.model.AiInsight
import com.lumiposture.app.domain.model.SessionReport
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.StudySessionSummary
import com.lumiposture.app.domain.model.TelemetrySamplePoint
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeAllSessions(): Flow<List<StudySessionSummary>>
    fun observeSessionReport(sessionId: Long): Flow<SessionReport?>
    suspend fun getSessionReport(sessionId: Long): SessionReport?
    suspend fun saveCompletedSession(
        startTimeMillis: Long,
        endTimeMillis: Long,
        durationSeconds: Int,
        studyMode: StudyMode,
        overallScore: Int,
        avgBlinkRate: Float,
        avgDistanceCm: Float,
        avgNeckAngleDeg: Float,
        avgLux: Float,
        avgNoiseDb: Float,
        totalWarningsTriggered: Int,
        samples: List<TelemetrySamplePoint>,
        insight: AiInsight,
    ): Long

    suspend fun updateSessionInsight(sessionId: Long, insight: AiInsight)
    suspend fun deleteSession(sessionId: Long)
    suspend fun clearAllSessions()
}
