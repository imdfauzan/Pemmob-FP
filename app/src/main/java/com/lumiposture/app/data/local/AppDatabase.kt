package com.lumiposture.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lumiposture.app.data.local.dao.SessionDao
import com.lumiposture.app.data.local.entity.AiInsightEntity
import com.lumiposture.app.data.local.entity.StudySessionEntity
import com.lumiposture.app.data.local.entity.TelemetrySampleEntity

@Database(
    entities = [
        StudySessionEntity::class,
        TelemetrySampleEntity::class,
        AiInsightEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
