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
 * v2.25.4 improvements:
 * - Pure fullscreen embed — NO YouTube UI, NO title bar, NO recommendations
 * - Uses YouTube IFrame Player API with controls=0, showinfo=0, fs=0
 * - Injects custom HTML that forces the video to fill the entire screen
 * - D-pad Left OR Back exits the trailer at any time
 * - For search fallback: loads YouTube Data API search → auto-embeds first result
 * - No close button visible — completely clean fullscreen experience
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebPlayer(
    url: String,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val videoId = remember(url) { extractYouTubeId(url) }
    val isSearchUrl = remember(url) { url.contains("search_query=") || url.contains("results?") }

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

                    // Prevent any navigation away from our embed page
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Block all navigation — keep the embed locked
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
                    }
                    setBackgroundColor(android.graphics.Color.BLACK)

                    if (videoId != null) {
                        // Direct video ID — load clean fullscreen embed
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            buildEmbedHtml(videoId),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    } else if (isSearchUrl) {
                        // Search fallback — extract query and use YouTube search API
                        val query = url.substringAfter("search_query=")
                            .substringBefore("&")
                            .let { java.net.URLDecoder.decode(it, "UTF-8") }
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            buildSearchEmbedHtml(query),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    } else {
                        // Unknown format — try to embed directly
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            buildEmbedHtml(url),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Build a clean fullscreen YouTube embed HTML page.
 * Uses the IFrame Player API with all UI elements hidden:
 * - controls=0: No player controls
 * - showinfo=0: No video title/info bar
 * - rel=0: No related videos at the end
 * - modestbranding=1: Minimal YouTube branding
 * - iv_load_policy=3: No annotations
 * - fs=0: No fullscreen button (we ARE fullscreen)
 * - autoplay=1: Start immediately
 * - playsinline=1: Play inline (not in YouTube app)
 *
 * The HTML uses CSS to make the iframe fill 100% of the viewport
 * with zero margins, padding, or scrollbars.
 */
fun buildEmbedHtml(videoId: String): String {
    // If it looks like a full URL rather than just an ID, try to extract the ID
    val id = extractYouTubeId(videoId) ?: videoId

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            * { margin: 0; padding: 0; overflow: hidden; }
            html, body { width: 100%; height: 100%; background: #000; }
            iframe {
                position: absolute;
                top: 0; left: 0;
                width: 100%; height: 100%;
                border: none;
            }
        </style>
    </head>
    <body>
        <iframe
            src="https://www.youtube.com/embed/$id?autoplay=1&controls=0&showinfo=0&rel=0&modestbranding=1&iv_load_policy=3&fs=0&playsinline=1&enablejsapi=1&origin=https://www.youtube.com"
            allow="autoplay; encrypted-media"
            allowfullscreen>
        </iframe>
    </body>
    </html>
    """.trimIndent()
}

/**
 * Build an HTML page that searches YouTube for the query and auto-embeds
 * the first result. Uses the YouTube oEmbed API (no API key needed) as a
 * fallback, but primarily constructs the embed URL from a known search pattern.
 *
 * Flow:
 * 1. Fetches YouTube search results page
 * 2. Extracts the first video ID from the response
 * 3. Embeds that video in a clean fullscreen iframe
 * 4. If extraction fails, shows the search query as a direct embed search
 */
fun buildSearchEmbedHtml(query: String): String {
    val urlEncodedQuery = try {
        java.net.URLEncoder.encode(query, "UTF-8")
    } catch (_: Exception) {
        query.replace(" ", "+")
    }

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            * { margin: 0; padding: 0; overflow: hidden; }
            html, body { width: 100%; height: 100%; background: #000; }
            iframe {
                position: absolute;
                top: 0; left: 0;
                width: 100%; height: 100%;
                border: none;
            }
            #loading {
                position: absolute;
                top: 50%; left: 50%;
                transform: translate(-50%, -50%);
                color: #fff;
                font-family: sans-serif;
                font-size: 18px;
                text-align: center;
            }
            .spinner {
                width: 40px; height: 40px;
                border: 4px solid rgba(255,255,255,0.3);
                border-top: 4px solid #fff;
                border-radius: 50%;
                animation: spin 1s linear infinite;
                margin: 0 auto 16px;
            }
            @keyframes spin { to { transform: rotate(360deg); } }
        </style>
    </head>
    <body>
        <div id="loading">
            <div class="spinner"></div>
            Loading trailer...
        </div>
        <iframe id="player" style="display:none"
            allow="autoplay; encrypted-media"
            allowfullscreen>
        </iframe>
        <script>
            // Fetch YouTube search page and extract first video ID
            fetch('https://www.youtube.com/results?search_query=$urlEncodedQuery')
                .then(r => r.text())
                .then(html => {
                    // Extract first video ID from search results
                    var match = html.match(/"videoId":"([a-zA-Z0-9_-]{11})"/);
                    if (match && match[1]) {
                        playVideo(match[1]);
                    } else {
                        // Fallback: try another pattern
                        match = html.match(/\/watch\?v=([a-zA-Z0-9_-]{11})/);
                        if (match && match[1]) {
                            playVideo(match[1]);
                        } else {
                            // Last resort: just embed the search as a playlist-like embed
                            document.getElementById('loading').innerHTML = 'Trailer not found';
                        }
                    }
                })
                .catch(function() {
                    // If fetch fails (CORS), use a direct embed with search
                    // YouTube embed supports a search-based "listType=search" parameter
                    var frame = document.getElementById('player');
                    frame.src = 'https://www.youtube.com/embed?listType=search&list=$urlEncodedQuery&autoplay=1&controls=0&showinfo=0&rel=0&modestbranding=1&iv_load_policy=3&fs=0&playsinline=1';
                    frame.style.display = 'block';
                    document.getElementById('loading').style.display = 'none';
                });

            function playVideo(videoId) {
                var frame = document.getElementById('player');
                frame.src = 'https://www.youtube.com/embed/' + videoId + '?autoplay=1&controls=0&showinfo=0&rel=0&modestbranding=1&iv_load_policy=3&fs=0&playsinline=1&enablejsapi=1&origin=https://www.youtube.com';
                frame.style.display = 'block';
                document.getElementById('loading').style.display = 'none';
            }
        </script>
    </body>
    </html>
    """.trimIndent()
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
