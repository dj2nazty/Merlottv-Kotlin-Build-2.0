package com.merlottv.kotlin.core.di

import android.content.Context
import com.merlottv.kotlin.data.local.FavoritesDataStore
import com.merlottv.kotlin.data.local.SettingsDataStore
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
}
