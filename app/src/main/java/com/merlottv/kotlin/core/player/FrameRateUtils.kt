package com.merlottv.kotlin.core.player

import android.app.Activity
import android.media.MediaExtractor
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Auto frame rate matching utility.
 * Detects video frame rate and switches the display refresh rate to match
 * for judder-free playback (ported from NuvioTV pattern).
 */
object FrameRateUtils {

    private const val TAG = "FrameRateUtils"
    private const val SWITCH_TIMEOUT_MS = 4000L
    private const val REFRESH_MATCH_TOLERANCE_HZ = 0.08f
    private const val NTSC_FILM_FPS = 24000f / 1001f  // 23.976
    private const val CINEMA_24_FPS = 24f
    private const val MIN_VALID_FPS = 10f
    private const val MAX_VALID_FPS = 120f
    private const val SWITCH_POLL_INTERVAL_MS = 60L
    private const val SWITCH_STABLE_POLLS = 2
    private const val MAX_PROBE_FRAMES = 350

    /** Frame rate matching mode */
    enum class Mode {
        OFF,        // No frame rate matching
        START,      // Match on playback start only
        START_STOP  // Match on start, restore original on stop
    }

    data class FrameRateDetection(
        val raw: Float,
        val snapped: Float,
        val videoWidth: Int? = null,
        val videoHeight: Int? = null
    )

    data class DisplayModeSwitchResult(
        val appliedMode: Display.Mode
    )

    private var originalModeId: Int? = null

    // ── Standard cinema rates for snapping ──────────────────────────────

    private val STANDARD_RATES = listOf(
        NTSC_FILM_FPS, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f
    )

    private fun snapToStandardRate(fps: Float): Float {
        if (fps < MIN_VALID_FPS || fps > MAX_VALID_FPS) return fps
        return STANDARD_RATES.minByOrNull { abs(it - fps) }
            ?.takeIf { abs(it - fps) < 1.0f }
            ?: fps
    }

    // ── Frame rate detection via MediaExtractor ─────────────────────────

    /**
     * Detect video frame rate from a stream URI using MediaExtractor.
     * Analyzes frame timestamps to determine actual frame rate.
     */
    suspend fun detectFrameRate(uri: Uri): FrameRateDetection? = withContext(Dispatchers.IO) {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(uri.toString())

            // Find video track
            var videoTrack = -1
            var width: Int? = null
            var height: Int? = null
            var trackFps: Float? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrack = i
                    width = try { format.getInteger(android.media.MediaFormat.KEY_WIDTH) } catch (_: Exception) { null }
                    height = try { format.getInteger(android.media.MediaFormat.KEY_HEIGHT) } catch (_: Exception) { null }
                    trackFps = try { format.getFloat(android.media.MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { null }
                    break
                }
            }

            if (videoTrack < 0) {
                extractor.release()
                return@withContext null
            }

            // If container reports frame rate, use it (with snapping)
            if (trackFps != null && trackFps in MIN_VALID_FPS..MAX_VALID_FPS) {
                extractor.release()
                val snapped = snapToStandardRate(trackFps)
                Log.d(TAG, "Container reports ${trackFps}fps → snapped to ${snapped}fps")
                return@withContext FrameRateDetection(trackFps, snapped, width, height)
            }

            // Probe frame timestamps for duration-based detection
            extractor.selectTrack(videoTrack)
            val timestamps = mutableListOf<Long>()
            var framesRead = 0

            while (framesRead < MAX_PROBE_FRAMES) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break
                timestamps.add(sampleTime)
                framesRead++
                if (!extractor.advance()) break
            }

            extractor.release()

            if (timestamps.size < 10) return@withContext null

            // Skip first 3 frames (may have irregular timing), analyze the rest
            val durations = mutableListOf<Long>()
            for (i in 4 until timestamps.size) {
                val dur = timestamps[i] - timestamps[i - 1]
                if (dur > 0) durations.add(dur)
            }

            if (durations.isEmpty()) return@withContext null

            // Median frame duration (more robust than mean)
            val sorted = durations.sorted()
            val median = sorted[sorted.size / 2]
            val fps = 1_000_000f / median

            if (fps < MIN_VALID_FPS || fps > MAX_VALID_FPS) return@withContext null

            val snapped = snapToStandardRate(fps)
            Log.d(TAG, "Probed ${timestamps.size} frames: median duration ${median}µs → ${fps}fps → snapped ${snapped}fps")

            FrameRateDetection(fps, snapped, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Frame rate detection failed", e)
            null
        }
    }

    // ── Display mode switching ──────────────────────────────────────────

    /**
     * Match the display refresh rate to the given video frame rate.
     * Returns the applied display mode, or null if no switch was needed/possible.
     */
    suspend fun matchFrameRate(
        activity: Activity,
        targetFps: Float
    ): DisplayModeSwitchResult? = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@withContext null

        val window = activity.window ?: return@withContext null
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        } ?: return@withContext null

        val currentMode = display.mode
        val modes = display.supportedModes

        // Save original mode for later restoration
        if (originalModeId == null) {
            originalModeId = currentMode.modeId
            Log.d(TAG, "Saved original mode: ${currentMode.modeId} (${currentMode.refreshRate}Hz)")
        }

        // Find best matching mode
        val bestMode = pickBestMode(modes.toList(), targetFps, currentMode)
        if (bestMode == null || bestMode.modeId == currentMode.modeId) {
            Log.d(TAG, "No mode switch needed (current ${currentMode.refreshRate}Hz matches ${targetFps}fps)")
            return@withContext null
        }

        Log.d(TAG, "Switching: ${currentMode.refreshRate}Hz → ${bestMode.refreshRate}Hz for ${targetFps}fps content")

        // Apply the mode switch
        val layoutParams = window.attributes
        layoutParams.preferredDisplayModeId = bestMode.modeId
        window.attributes = layoutParams

        // Wait for mode to stabilize
        val result = waitForModeSwitch(display, bestMode.modeId)
        if (result != null) {
            Log.d(TAG, "Mode switch complete: ${result.appliedMode.refreshRate}Hz")
        } else {
            Log.w(TAG, "Mode switch timed out, may not have applied")
        }

        result ?: DisplayModeSwitchResult(bestMode)
    }

    /**
     * Restore the original display mode (call when playback stops).
     */
    suspend fun restoreOriginalMode(activity: Activity) = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@withContext

        val savedMode = originalModeId ?: return@withContext
        originalModeId = null

        val window = activity.window ?: return@withContext
        val layoutParams = window.attributes
        layoutParams.preferredDisplayModeId = savedMode
        window.attributes = layoutParams
        Log.d(TAG, "Restored original display mode: $savedMode")
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun matchesTargetRefresh(refreshRate: Float, target: Float): Boolean {
        val tolerance = max(REFRESH_MATCH_TOLERANCE_HZ, target * 0.003f)
        return abs(refreshRate - target) <= tolerance
    }

    private fun pickBestMode(
        modes: List<Display.Mode>,
        targetFps: Float,
        currentMode: Display.Mode
    ): Display.Mode? {
        // Priority: exact match > 2x match > 2.5x match (3:2 pulldown)
        val candidates = mutableListOf<Pair<Display.Mode, Int>>() // mode to priority

        for (mode in modes) {
            // Must match current resolution
            if (mode.physicalWidth != currentMode.physicalWidth ||
                mode.physicalHeight != currentMode.physicalHeight) continue

            val refresh = mode.refreshRate
            when {
                matchesTargetRefresh(refresh, targetFps) -> candidates.add(mode to 0)         // Exact
                matchesTargetRefresh(refresh, targetFps * 2) -> candidates.add(mode to 1)     // 2x
                matchesTargetRefresh(refresh, targetFps * 2.5f) -> candidates.add(mode to 2)  // 2.5x pulldown
            }
        }

        return candidates.minByOrNull { it.second }?.first
    }

    private suspend fun waitForModeSwitch(
        display: Display,
        targetModeId: Int
    ): DisplayModeSwitchResult? {
        var stableCount = 0
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < SWITCH_TIMEOUT_MS) {
            delay(SWITCH_POLL_INTERVAL_MS)
            val current = display.mode
            if (current.modeId == targetModeId) {
                stableCount++
                if (stableCount >= SWITCH_STABLE_POLLS) {
                    return DisplayModeSwitchResult(current)
                }
            } else {
                stableCount = 0
            }
        }

        return null
    }
}
