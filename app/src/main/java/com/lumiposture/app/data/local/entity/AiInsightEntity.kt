package com.lumiposture.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_session_insights",
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AiInsightEntity(
    @PrimaryKey
    val sessionId: Long,
    val statusLabel: String,
    val executiveSummary: String,
    val primaryRiskFactor: String,
    val correlationsJson: String,
    val recommendationsJson: String,
    val isGeneratedByCloudAi: Boolean,
    val createdAtMillis: Long,
)
