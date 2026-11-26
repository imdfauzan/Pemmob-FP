package com.lumiposture.app.di

import android.content.Context
import androidx.room.Room
import com.lumiposture.app.data.local.AppDatabase
import com.lumiposture.app.data.local.MIGRATION_1_2
import com.lumiposture.app.data.local.dao.SessionDao
import com.lumiposture.app.data.repository.SessionRepository
import com.lumiposture.app.data.repository.SessionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lumiposture.db",
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    @Singleton
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
