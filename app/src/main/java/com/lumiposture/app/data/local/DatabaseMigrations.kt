package com.lumiposture.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE study_sessions_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            startTimeMillis INTEGER NOT NULL, endTimeMillis INTEGER NOT NULL, durationSeconds INTEGER NOT NULL,
            studyMode TEXT NOT NULL, overallScore INTEGER, avgBlinkRate REAL, avgDistanceCm REAL,
            avgNeckAngleDeg REAL, avgLux REAL, avgNoiseDb REAL, totalWarningsTriggered INTEGER NOT NULL, dataVersion INTEGER NOT NULL)""")
        db.execSQL("INSERT INTO study_sessions_new SELECT *, 1 FROM study_sessions")
        // Preserve children before replacing the parent; do not rely on FK rewriting during ALTER TABLE.
        db.execSQL("CREATE TEMP TABLE telemetry_backup AS SELECT * FROM telemetry_samples")
        db.execSQL("CREATE TEMP TABLE insights_backup AS SELECT * FROM ai_session_insights")
        db.execSQL("DROP TABLE ai_session_insights")
        db.execSQL("DROP TABLE telemetry_samples")
        db.execSQL("DROP TABLE study_sessions")
        db.execSQL("ALTER TABLE study_sessions_new RENAME TO study_sessions")
        db.execSQL("""CREATE TABLE telemetry_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            sessionId INTEGER NOT NULL, elapsedSeconds INTEGER NOT NULL, ergonomicScore INTEGER,
            blinkRateBpm REAL, distanceCm REAL, neckAngleDeg REAL, ambientLux REAL, noiseDb REAL,
            isViolationActive INTEGER NOT NULL, screenBrightnessPct INTEGER, FOREIGN KEY(sessionId) REFERENCES study_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("INSERT INTO telemetry_samples SELECT *, NULL FROM telemetry_backup")
        db.execSQL("""CREATE TABLE ai_session_insights (sessionId INTEGER NOT NULL PRIMARY KEY,
            statusLabel TEXT NOT NULL, executiveSummary TEXT NOT NULL, primaryRiskFactor TEXT NOT NULL,
            correlationsJson TEXT NOT NULL, recommendationsJson TEXT NOT NULL, isGeneratedByCloudAi INTEGER NOT NULL,
            createdAtMillis INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES study_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("INSERT INTO ai_session_insights SELECT * FROM insights_backup")
        db.execSQL("DROP TABLE telemetry_backup")
        db.execSQL("DROP TABLE insights_backup")
        db.execSQL("CREATE INDEX index_telemetry_samples_sessionId ON telemetry_samples(sessionId)")
    }
}
