package com.lumiposture.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telemetry_samples",
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"])],
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val elapsedSeconds: Int,
    val ergonomicScore: Int?,
    val blinkRateBpm: Float?,
    val distanceCm: Float?,
    val neckAngleDeg: Float?,
    val ambientLux: Float?,
    val noiseDb: Float?,
    val isViolationActive: Boolean,
    val screenBrightnessPct: Int? = null,
)
