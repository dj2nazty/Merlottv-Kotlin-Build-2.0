@file:Suppress("SetJavaScriptEnabled")

package com.merlottv.kotlin.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Fullscreen in-app YouTube player using WebView.
 * Handles all YouTube URL formats and embeds the video directly.
 * Press Back or tap Close to dismiss.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebPlayer(
    url: String,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val embedUrl = remember(url) { buildEmbedUrl(url) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss(); true
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
                    webViewClient = WebViewClient()
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
                    }
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadUrl(embedUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close button
        var closeFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .onFocusChanged { closeFocused = it.isFocused }
                .focusable()
                .then(
                    if (closeFocused) Modifier.border(2.dp, Color.White, CircleShape)
                    else Modifier
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        onDismiss(); true
                    } else false
                }
        ) {
            Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Convert a YouTube URL into an embeddable URL.
 * Handles: youtu.be/ID, youtube.com/watch?v=ID, youtube.com/live/ID,
 * youtube.com/results?search_query=..., vnd.youtube: intents,
 * and falls back to loading the URL directly if no ID can be extracted.
 */
fun buildEmbedUrl(url: String): String {
    val videoId = extractYouTubeId(url)
    return if (videoId != null) {
        "https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&modestbranding=1&playsinline=1"
    } else {
        // Convert vnd.youtube: scheme URLs to web URLs
        if (url.startsWith("vnd.youtube://results")) {
            url.replace("vnd.youtube://results", "https://www.youtube.com/results")
        } else if (url.startsWith("vnd.youtube:")) {
            val id = url.removePrefix("vnd.youtube:")
            "https://www.youtube.com/embed/$id?autoplay=1&rel=0&modestbranding=1&playsinline=1"
        } else {
            url
        }
    }
}

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
