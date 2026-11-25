package com.lumiposture.app.data.repository

import com.lumiposture.app.data.local.dao.SessionDao
import com.lumiposture.app.data.local.dao.SessionWithDetails
import com.lumiposture.app.data.local.entity.AiInsightEntity
import com.lumiposture.app.data.local.entity.StudySessionEntity
import com.lumiposture.app.data.local.entity.TelemetrySampleEntity
import com.lumiposture.app.domain.model.AiInsight
import com.lumiposture.app.domain.model.SessionReport
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.StudySessionSummary
import com.lumiposture.app.domain.model.TelemetrySamplePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val json: Json,
) : SessionRepository {

    override fun observeAllSessions(): Flow<List<StudySessionSummary>> {
        return sessionDao.observeAllSessionsWithInsight().map { items ->
            items.map { item ->
                val e = item.session
                StudySessionSummary(
                    id = e.id,
                    startTimeMillis = e.startTimeMillis,
                    endTimeMillis = e.endTimeMillis,
                    durationSeconds = e.durationSeconds,
                    studyMode = parseStudyMode(e.studyMode),
                    overallScore = e.overallScore,
                    avgBlinkRate = e.avgBlinkRate,
                    avgDistanceCm = e.avgDistanceCm,
                    avgNeckAngleDeg = e.avgNeckAngleDeg,
                    avgLux = e.avgLux,
                    avgNoiseDb = e.avgNoiseDb,
                    totalWarningsTriggered = e.totalWarningsTriggered,
                    statusLabel = item.insight?.statusLabel,
                    isGeneratedByCloudAi = item.insight?.isGeneratedByCloudAi,
                )
            }
        }
    }

    override fun observeSessionReport(sessionId: Long): Flow<SessionReport?> {
        return sessionDao.observeSessionDetail(sessionId).map { it?.toDomain() }
    }

    override suspend fun getSessionReport(sessionId: Long): SessionReport? {
        return sessionDao.getSessionDetail(sessionId)?.toDomain()
    }

    override suspend fun saveCompletedSession(
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
    ): Long {
        val sessionId = sessionDao.insertSession(
            StudySessionEntity(
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                durationSeconds = durationSeconds,
                studyMode = studyMode.apiValue,
                overallScore = overallScore,
                avgBlinkRate = avgBlinkRate,
                avgDistanceCm = avgDistanceCm,
                avgNeckAngleDeg = avgNeckAngleDeg,
                avgLux = avgLux,
                avgNoiseDb = avgNoiseDb,
                totalWarningsTriggered = totalWarningsTriggered,
            ),
        )
        if (samples.isNotEmpty()) {
            sessionDao.insertSamples(
                samples.map { point ->
                    TelemetrySampleEntity(
                        sessionId = sessionId,
                        elapsedSeconds = point.elapsedSeconds,
                        ergonomicScore = point.ergonomicScore,
                        blinkRateBpm = point.blinkRateBpm,
                        distanceCm = point.distanceCm,
                        neckAngleDeg = point.neckAngleDeg,
                        ambientLux = point.ambientLux,
                        noiseDb = point.noiseDb,
                        isViolationActive = point.isViolationActive,
                    )
                },
            )
        }
        upsertInsightEntity(sessionId, insight)
        return sessionId
    }

    override suspend fun updateSessionInsight(sessionId: Long, insight: AiInsight) {
        upsertInsightEntity(sessionId, insight)
    }

    override suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSession(sessionId)
    }

    override suspend fun clearAllSessions() {
        sessionDao.clearAllSessions()
    }

    private suspend fun upsertInsightEntity(sessionId: Long, insight: AiInsight) {
        sessionDao.upsertInsight(
            AiInsightEntity(
                sessionId = sessionId,
                statusLabel = insight.statusLabel,
                executiveSummary = insight.executiveSummary,
                primaryRiskFactor = insight.primaryRiskFactor,
                correlationsJson = json.encodeToString(insight.sensorCorrelations),
                recommendationsJson = json.encodeToString(insight.actionableRecommendations),
                isGeneratedByCloudAi = insight.isGeneratedByCloudAi,
                createdAtMillis = insight.createdAtMillis,
            ),
        )
    }

    private fun SessionWithDetails.toDomain(): SessionReport {
        val s = session
        val domainInsight = insight?.let { i ->
            AiInsight(
                statusLabel = i.statusLabel,
                executiveSummary = i.executiveSummary,
                primaryRiskFactor = i.primaryRiskFactor,
                sensorCorrelations = runCatching {
                    json.decodeFromString<List<String>>(i.correlationsJson)
                }.getOrDefault(emptyList()),
                actionableRecommendations = runCatching {
                    json.decodeFromString<List<String>>(i.recommendationsJson)
                }.getOrDefault(emptyList()),
                isGeneratedByCloudAi = i.isGeneratedByCloudAi,
                createdAtMillis = i.createdAtMillis,
            )
        }
        return SessionReport(
            session = StudySessionSummary(
                id = s.id,
                startTimeMillis = s.startTimeMillis,
                endTimeMillis = s.endTimeMillis,
                durationSeconds = s.durationSeconds,
                studyMode = parseStudyMode(s.studyMode),
                overallScore = s.overallScore,
                avgBlinkRate = s.avgBlinkRate,
                avgDistanceCm = s.avgDistanceCm,
                avgNeckAngleDeg = s.avgNeckAngleDeg,
                avgLux = s.avgLux,
                avgNoiseDb = s.avgNoiseDb,
                totalWarningsTriggered = s.totalWarningsTriggered,
                statusLabel = domainInsight?.statusLabel,
                isGeneratedByCloudAi = domainInsight?.isGeneratedByCloudAi,
            ),
            samples = samples
                .asSequence()
                .sortedBy { it.elapsedSeconds }
                .map { sample ->
                    TelemetrySamplePoint(
                        elapsedSeconds = sample.elapsedSeconds,
                        ergonomicScore = sample.ergonomicScore,
                        blinkRateBpm = sample.blinkRateBpm,
                        distanceCm = sample.distanceCm,
                        neckAngleDeg = sample.neckAngleDeg,
                        ambientLux = sample.ambientLux,
                        noiseDb = sample.noiseDb,
                        isViolationActive = sample.isViolationActive,
                    )
                }
                .toList(),
            insight = domainInsight,
        )
    }

    private fun parseStudyMode(raw: String): StudyMode {
        return StudyMode.entries.firstOrNull { it.apiValue == raw } ?: StudyMode.HANDHELD
    }
}
