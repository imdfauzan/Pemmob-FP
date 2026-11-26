package com.lumiposture.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiposture.app.data.local.AppDatabase
import com.lumiposture.app.data.local.MIGRATION_1_2
import com.lumiposture.app.data.repository.SessionRepositoryImpl
import com.lumiposture.app.domain.engine.LocalRuleBasedInsightEngine
import com.lumiposture.app.domain.engine.SessionAggregator
import com.lumiposture.app.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DatabaseStabilizationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migrationPreservesLegacyHistoryAndForeignKeys() = runBlocking {
        val name = "migration-stabilization-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE study_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startTimeMillis INTEGER NOT NULL, endTimeMillis INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, studyMode TEXT NOT NULL, overallScore INTEGER NOT NULL, avgBlinkRate REAL NOT NULL, avgDistanceCm REAL NOT NULL, avgNeckAngleDeg REAL NOT NULL, avgLux REAL NOT NULL, avgNoiseDb REAL NOT NULL, totalWarningsTriggered INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE telemetry_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, elapsedSeconds INTEGER NOT NULL, ergonomicScore INTEGER NOT NULL, blinkRateBpm REAL NOT NULL, distanceCm REAL NOT NULL, neckAngleDeg REAL NOT NULL, ambientLux REAL NOT NULL, noiseDb REAL NOT NULL, isViolationActive INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES study_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_telemetry_samples_sessionId ON telemetry_samples(sessionId)")
            db.execSQL("CREATE TABLE ai_session_insights (sessionId INTEGER NOT NULL PRIMARY KEY, statusLabel TEXT NOT NULL, executiveSummary TEXT NOT NULL, primaryRiskFactor TEXT NOT NULL, correlationsJson TEXT NOT NULL, recommendationsJson TEXT NOT NULL, isGeneratedByCloudAi INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES study_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("INSERT INTO study_sessions VALUES (42, 1000, 21000, 20, 'HANDHELD', 100, 15, 30, 10, 200, 36, 0)")
            db.execSQL("INSERT INTO telemetry_samples VALUES (1, 42, 0, 100, 15, 30, 10, 200, 36, 0)")
            db.execSQL("INSERT INTO ai_session_insights VALUES (42, 'Optimal', 'legacy', 'legacy', '[]', '[]', 0, 21000)")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val detail = database.sessionDao().getSessionDetail(42)!!
            assertEquals(1, detail.session.dataVersion)
            assertEquals(1, detail.samples.size)
            assertEquals("legacy", detail.insight?.executiveSummary)
            assertEquals(100, detail.session.overallScore)
            val exposed = SessionRepositoryImpl(database.sessionDao(), Json, database).getSessionReport(42)!!
            assertNull(exposed.session.overallScore)
            assertTrue(exposed.samples.isEmpty())
            database.sessionDao().deleteSession(42)
            database.openHelper.readableDatabase.query("SELECT count(*) FROM telemetry_samples").use {
                it.moveToFirst(); assertEquals(0, it.getInt(0))
            }
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun nullableMetricsPersistAfterDatabaseReopen() = runBlocking {
        val name = "persistence-stabilization-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var database = open()
        try {
            val repo = SessionRepositoryImpl(database.sessionDao(), Json, database)
            val points = listOf(TelemetrySamplePoint(0, null, null, null, null, null, null, false))
            val aggregate = SessionAggregator().aggregate(2, StudyMode.HANDHELD, null, points, null)
            val id = repo.saveCompletedSession(1000, 3000, 2, StudyMode.HANDHELD, null,
                null, null, null, null, null, 0, points, LocalRuleBasedInsightEngine().generate(aggregate))
            database.close()
            database = open()
            val report = SessionRepositoryImpl(database.sessionDao(), Json, database).getSessionReport(id)!!
            assertNull(report.session.overallScore)
            assertNull(report.samples.single().ambientLux)
            assertNull(report.samples.single().noiseDb)
            assertEquals("Data belum cukup", report.insight?.statusLabel)
            assertEquals(1, database.openHelper.readableDatabase.query("SELECT * FROM study_sessions").use { it.count })
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
