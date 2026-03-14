package com.merlottv.kotlin.core.di

import android.content.Context
import androidx.room.Room
import com.merlottv.kotlin.data.local.FavoritesDataStore
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.data.local.WatchProgressDataStore
import com.merlottv.kotlin.data.local.db.EpgDao
import com.merlottv.kotlin.data.local.db.MerlotDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFavoritesDataStore(@ApplicationContext context: Context): FavoritesDataStore {
        return FavoritesDataStore(context)
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideWatchProgressDataStore(@ApplicationContext context: Context): WatchProgressDataStore {
        return WatchProgressDataStore(context)
    }

    @Provides
    @Singleton
    fun provideProfileDataStore(@ApplicationContext context: Context): ProfileDataStore {
        return ProfileDataStore(context)
    }

    @Provides
    @Singleton
    fun provideMerlotDatabase(@ApplicationContext context: Context): MerlotDatabase {
        return Room.databaseBuilder(context, MerlotDatabase::class.java, "merlot_epg.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideEpgDao(db: MerlotDatabase): EpgDao {
        return db.epgDao()
    }
}
