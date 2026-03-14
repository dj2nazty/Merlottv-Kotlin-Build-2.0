@file:Suppress("SetJavaScriptEnabled")

package com.merlottv.kotlin.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Fullscreen in-app YouTube player using WebView.
 *
 * v2.26.1 fix: YouTube error 152-4 (embed blocked from WebView).
 * YouTube's July 2025 update blocked iframe embeds loaded via loadDataWithBaseURL()
 * because WebViews don't send proper HTTP Referer headers from injected HTML.
 *
 * FIX: Load the YouTube embed URL directly via loadUrl() instead of injecting
 * custom HTML. This sends proper HTTP headers that YouTube accepts.
 * The embed URL params hide all YouTube UI (controls=0, showinfo=0, etc.).
 *
 * For search fallback: We load YouTube's mobile search page, let the user's
 * query auto-play the first result via YouTube's own UI, then hide controls.
 *
 * D-pad Left OR Back exits the trailer at any time.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebPlayer(
    url: String,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val videoId = remember(url) { extractYouTubeId(url) }
    val isSearchUrl = remember(url) {
        url.contains("search_query=") || url.contains("results?")
    }

    // Build the final URL to load
    val embedUrl = remember(url, videoId, isSearchUrl) {
        when {
            videoId != null -> {
                // Direct embed URL — YouTube accepts this from WebView loadUrl()
                "https://www.youtube.com/embed/$videoId?autoplay=1&controls=0&showinfo=0&rel=0&modestbranding=1&iv_load_policy=3&fs=0&playsinline=1"
            }
            isSearchUrl -> {
                // Search fallback: extract query and build search embed
                val query = url.substringAfter("search_query=")
                    .substringBefore("&")
                    .let { try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it } }
                val encoded = try { java.net.URLEncoder.encode(query, "UTF-8") } catch (_: Exception) { query.replace(" ", "+") }
                // Use YouTube's embed search playlist feature
                "https://www.youtube.com/embed?listType=search&list=$encoded&autoplay=1&controls=0&showinfo=0&rel=0&modestbranding=1&iv_load_policy=3&fs=0&playsinline=1"
            }
            else -> {
                // Unknown format — try to extract ID and embed, or load directly
                val extractedId = extractYouTubeId(url)
                if (extractedId != null) {
                    "https://www.youtube.com/embed/$extractedId?autoplay=1&controls=0&showinfo=0&rel=0&modestbranding=1&iv_load_policy=3&fs=0&playsinline=1"
                } else {
                    // Last resort — load as-is
                    url
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.DirectionLeft -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val requestUrl = request?.url?.toString() ?: return true
                            // Allow YouTube embed URLs to load normally
                            if (requestUrl.contains("youtube.com/embed") ||
                                requestUrl.contains("youtube.com/watch") ||
                                requestUrl.contains("googlevideo.com") ||
                                requestUrl.contains("youtube.com/api") ||
                                requestUrl.contains("youtube.com/iframe") ||
                                requestUrl.contains("accounts.google.com") ||
                                requestUrl.contains("googleapis.com")) {
                                return false // Allow these URLs
                            }
                            // Block navigation to other pages
                            return true
                        }
                    }
                    webChromeClient = WebChromeClient()

                    settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // Set a proper user agent so YouTube treats us as a real browser
                        userAgentString = "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }
                    setBackgroundColor(android.graphics.Color.BLACK)

                    // Load the embed URL directly — proper HTTP headers are sent
                    loadUrl(embedUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Extract a YouTube video ID from various URL formats.
 * Handles: youtu.be/ID, youtube.com/watch?v=ID, youtube.com/live/ID,
 * youtube.com/embed/ID, vnd.youtube:ID
 */
fun extractYouTubeId(url: String): String? {
    // vnd.youtube:VIDEO_ID (app intent scheme)
    if (url.startsWith("vnd.youtube:") && !url.contains("//")) {
        return url.removePrefix("vnd.youtube:").takeIf { it.isNotEmpty() && !it.contains("/") }
    }
    // youtu.be/VIDEO_ID
    if (url.contains("youtu.be/")) {
        return url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").takeIf { it.isNotEmpty() }
    }
    // youtube.com/live/VIDEO_ID
    if (url.contains("/live/")) {
        return url.substringAfter("/live/").substringBefore("?").substringBefore("&").takeIf { it.isNotEmpty() }
    }
    // youtube.com/watch?v=VIDEO_ID
    if (url.contains("v=")) {
        return url.substringAfter("v=").substringBefore("&").substringBefore("#").takeIf { it.isNotEmpty() }
    }
    // youtube.com/embed/VIDEO_ID
    if (url.contains("/embed/")) {
        return url.substringAfter("/embed/").substringBefore("?").substringBefore("&").takeIf { it.isNotEmpty() }
    }
    return null
}
