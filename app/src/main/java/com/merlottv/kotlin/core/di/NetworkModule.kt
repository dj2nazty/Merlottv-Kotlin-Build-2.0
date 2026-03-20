package com.merlottv.kotlin.core.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        // 50 MB disk cache for HTTP responses (M3U playlists, EPG data, API calls, search results)
        // NuvioTV uses 50MB — dramatically speeds up repeat loads and search caching
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 50L * 1024 * 1024) // 50 MB

        return OkHttpClient.Builder()
            .cache(cache)
            // 30 idle connections (warm for failover), 2-min keep-alive (free sockets faster)
            .connectionPool(ConnectionPool(30, 2, TimeUnit.MINUTES))
            // Tighter timeouts — fail fast on dead hosts instead of blocking UI
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            // Prefer HTTP/2 for multiplexed requests (multiple M3U downloads over one connection)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            // HTTP logging disabled — even BASIC level adds logcat I/O overhead per request.
            // With 30+ catalog requests + playlist downloads, this accumulates significantly.
            // Re-enable for debugging: HttpLoggingInterceptor.Level.BASIC
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://v3-cinemeta.strem.io/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
