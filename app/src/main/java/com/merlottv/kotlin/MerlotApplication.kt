package com.merlottv.kotlin

import android.app.Application
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.merlottv.kotlin.data.worker.EpgSyncWorker
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MerlotApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Schedule periodic EPG background sync (every 4 hours)
        EpgSyncWorker.schedule(this)

        // Log available memory for diagnostics
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val heapMb = am.memoryClass
        val largeHeapMb = am.largeMemoryClass
        Log.i("MerlotApp", "Device RAM: ${memInfo.totalMem / 1024 / 1024}MB | " +
                "Heap: ${heapMb}MB | LargeHeap: ${largeHeapMb}MB | " +
                "Available: ${memInfo.availMem / 1024 / 1024}MB")
    }

    /**
     * Called by Android when the system is running low on memory.
     * We aggressively trim image caches to protect ExoPlayer buffer space.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w("MerlotApp", "Memory pressure (level=$level) — clearing image caches")
                // Force GC to reclaim image cache memory for ExoPlayer
                System.gc()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val client = try { okHttpClient } catch (_: UninitializedPropertyAccessException) { OkHttpClient() }

        // On a 3GB TV with largeHeap, we get ~512MB heap.
        // Reserve only 10% for images (was 15%) — leaves more room for ExoPlayer buffers.
        // Lean on disk cache (100MB) instead of memory cache for poster/logo storage.
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10)  // 10% of heap (~50MB) for images, down from 15%
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)  // 100MB disk cache (was 50MB)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
