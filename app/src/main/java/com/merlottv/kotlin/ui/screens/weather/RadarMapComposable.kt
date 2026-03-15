package com.merlottv.kotlin.ui.screens.weather

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.merlottv.kotlin.domain.model.RadarFrame
import com.merlottv.kotlin.ui.theme.MerlotColors

/**
 * Animated radar map using WebView + Leaflet.js + RainViewer tile layers.
 * Shows a proper interactive map with dark base tiles and animated radar overlay.
 */
@Composable
fun RadarMapComposable(
    frames: List<RadarFrame>,
    currentIndex: Int,
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    if (frames.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(if (isFullscreen) 600.dp else 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Surface2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No radar data available",
                color = MerlotColors.TextMuted,
                fontSize = 14.sp
            )
        }
        return
    }

    val shape = RoundedCornerShape(if (isFullscreen) 0.dp else 12.dp)
    var isFocused by remember { mutableStateOf(false) }

    // Build the Leaflet HTML with RainViewer radar frames
    val radarHtml = remember(frames, lat, lon) {
        buildRadarHtml(frames, lat, lon)
    }

    // Keep a reference to the WebView for updating the frame
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Update radar frame in WebView when index changes
    if (webViewRef != null && frames.isNotEmpty()) {
        val safeIndex = currentIndex.coerceIn(0, frames.size - 1)
        webViewRef?.evaluateJavascript("showFrame($safeIndex);", null)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.height(300.dp))
            .clip(shape)
            .background(Color(0xFF0A0A1A), shape)
            .then(
                if (!isFullscreen) Modifier.border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) MerlotColors.Accent else MerlotColors.Border,
                    shape = shape
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onToggleFullscreen()
                    true
                } else false
            }
    ) {
        // WebView with Leaflet map
        AndroidView(
            factory = { context ->
                @SuppressLint("SetJavaScriptEnabled")
                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(0xFF0A0A1A.toInt())
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()

                    // Disable scrolling / interaction — just a display
                    setOnTouchListener { _, _ -> true }
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                }
                webViewRef = webView
                webView.loadDataWithBaseURL(
                    "https://rainviewer.com",
                    radarHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
                webView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Fullscreen hint (top-right) — only when NOT fullscreen
        if (!isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isFocused) "Press ENTER for fullscreen" else "RADAR",
                    color = if (isFocused) MerlotColors.Accent else MerlotColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Back hint in fullscreen
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Press BACK to exit fullscreen",
                    color = MerlotColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }

    // Cleanup WebView on dispose
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

/**
 * Builds the HTML page with Leaflet.js + CartoDB dark tiles + RainViewer radar overlay.
 * All radar frames are pre-loaded as tile layers; showFrame(index) toggles visibility.
 */
private fun buildRadarHtml(frames: List<RadarFrame>, lat: Double, lon: Double): String {
    // Build JS array of frame paths
    val framesJs = frames.joinToString(",\n        ") { frame ->
        """{ path: "${frame.path}", host: "${frame.host}", time: ${frame.time} }"""
    }

    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
    * { margin: 0; padding: 0; }
    html, body { width: 100%; height: 100%; overflow: hidden; background: #0A0A1A; }
    #map { width: 100%; height: 100%; background: #0A0A1A; }
    .leaflet-control-zoom, .leaflet-control-attribution { display: none !important; }
    #timestamp {
        position: absolute;
        bottom: 10px;
        left: 10px;
        background: rgba(0,0,0,0.8);
        color: #fff;
        padding: 4px 10px;
        border-radius: 6px;
        font-family: sans-serif;
        font-size: 13px;
        z-index: 1000;
    }
    #framecount {
        position: absolute;
        bottom: 10px;
        right: 10px;
        background: rgba(0,0,0,0.8);
        color: #6B6B80;
        padding: 4px 8px;
        border-radius: 6px;
        font-family: sans-serif;
        font-size: 11px;
        z-index: 1000;
    }
</style>
</head>
<body>
<div id="map"></div>
<div id="timestamp"></div>
<div id="framecount"></div>
<script>
    var map = L.map('map', {
        center: [$lat, $lon],
        zoom: 7,
        zoomControl: false,
        attributionControl: false,
        dragging: false,
        touchZoom: false,
        scrollWheelZoom: false,
        doubleClickZoom: false,
        boxZoom: false,
        keyboard: false
    });

    // Dark base map (CartoDB Dark Matter — free, no key)
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        subdomains: 'abcd',
        maxZoom: 19
    }).addTo(map);

    // Radar frames
    var frames = [
        $framesJs
    ];

    var radarLayers = [];
    var currentFrame = 0;

    // Pre-create all radar tile layers
    for (var i = 0; i < frames.length; i++) {
        var layer = L.tileLayer(frames[i].host + frames[i].path + '/512/{z}/{x}/{y}/2/1_1.png', {
            opacity: 0,
            zIndex: 10 + i
        });
        layer.addTo(map);
        radarLayers.push(layer);
    }

    function showFrame(idx) {
        if (idx < 0 || idx >= radarLayers.length) return;
        // Hide previous
        if (currentFrame >= 0 && currentFrame < radarLayers.length) {
            radarLayers[currentFrame].setOpacity(0);
        }
        // Show new
        radarLayers[idx].setOpacity(0.7);
        currentFrame = idx;

        // Update timestamp
        var ts = frames[idx].time * 1000;
        var d = new Date(ts);
        var h = d.getHours();
        var m = d.getMinutes();
        var ampm = h >= 12 ? 'PM' : 'AM';
        h = h % 12;
        if (h === 0) h = 12;
        var mStr = m < 10 ? '0' + m : m;
        document.getElementById('timestamp').textContent = h + ':' + mStr + ' ' + ampm;
        document.getElementById('framecount').textContent = (idx + 1) + '/' + frames.length;
    }

    // Show first frame
    if (frames.length > 0) {
        showFrame(0);
    }
</script>
</body>
</html>
""".trimIndent()
}
