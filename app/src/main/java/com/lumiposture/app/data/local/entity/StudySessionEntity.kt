package com.lumiposture.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Int,
    val studyMode: String,
    val overallScore: Int,
    val avgBlinkRate: Float,
    val avgDistanceCm: Float,
    val avgNeckAngleDeg: Float,
    val avgLux: Float,
    val avgNoiseDb: Float,
    val totalWarningsTriggered: Int,
)
