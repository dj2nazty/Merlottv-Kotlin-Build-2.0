package com.merlottv.kotlin.ui.components

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.merlottv.kotlin.data.trailer.TrailerService
import com.merlottv.kotlin.data.youtube.TmdbTrailerRepository
import com.merlottv.kotlin.data.youtube.YouTubeExtractor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CardTrailerEntryPoint {
    fun tmdbTrailerRepository(): TmdbTrailerRepository
    fun youTubeExtractor(): YouTubeExtractor
}

/**
 * Trailer preview overlay for poster/VOD cards.
 * Shows a muted inline trailer after the card has been focused for [focusDelayMs].
 * Only one trailer plays at a time (managed via the [globalTrailerKey] mechanism).
 *
 * Usage: Place inside a card's Box composable, conditionally shown when focused.
 *
 * @param isFocused Whether the parent card is currently focused
 * @param contentId Content IMDB ID (e.g. "tt1234567")
 * @param contentType "movie" or "series"
 * @param title Content title (for TMDB search fallback)
 * @param focusDelayMs Delay before starting trailer playback (default 2 seconds)
 */
@Composable
fun CardTrailerPreview(
    isFocused: Boolean,
    contentId: String,
    contentType: String,
    title: String,
    focusDelayMs: Long = 2000L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Resolve TrailerService via Hilt entry point
    val trailerService = remember {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                CardTrailerEntryPoint::class.java
            )
            TrailerService(entryPoint.tmdbTrailerRepository(), entryPoint.youTubeExtractor())
        } catch (e: Exception) {
            Log.w("CardTrailerPreview", "Failed to initialize TrailerService: ${e.message}")
            null
        }
    }

    var trailerResult by remember { mutableStateOf<TrailerService.TrailerStreamResult?>(null) }
    var shouldShowTrailer by remember { mutableStateOf(false) }

    // When focused for >2 seconds, start resolving trailer
    LaunchedEffect(isFocused, contentId) {
        if (!isFocused) {
            shouldShowTrailer = false
            trailerResult = null
            return@LaunchedEffect
        }

        // Wait for focus delay
        delay(focusDelayMs)

        // Still focused — resolve trailer
        if (trailerService == null || contentId.isEmpty()) return@LaunchedEffect

        val result = withContext(Dispatchers.IO) {
            try {
                // Extract IMDB ID from content ID (may be "tt1234567" or "tt1234567:1:3")
                val imdbId = contentId.split(":").firstOrNull()?.takeIf { it.startsWith("tt") }
                trailerService.getTrailerStream(
                    imdbId = imdbId,
                    title = title,
                    year = "", // Year not available in MetaPreview
                    type = contentType
                )
            } catch (e: Exception) {
                Log.w("CardTrailerPreview", "Trailer resolution failed: ${e.message}")
                null
            }
        }

        if (result != null) {
            trailerResult = result
            shouldShowTrailer = true
        }
    }

    // Fade in the trailer
    val trailerAlpha by animateFloatAsState(
        targetValue = if (shouldShowTrailer && trailerResult != null) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "trailerCardAlpha"
    )

    // Show inline trailer player when resolved
    if (shouldShowTrailer && trailerResult != null && trailerAlpha > 0f) {
        InlineTrailerPlayer(
            trailerResult = trailerResult!!,
            modifier = modifier
                .fillMaxSize()
                .alpha(trailerAlpha)
        )
    }
}
