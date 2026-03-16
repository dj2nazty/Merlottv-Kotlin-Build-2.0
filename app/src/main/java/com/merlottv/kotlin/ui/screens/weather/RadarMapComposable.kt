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
    #legend {
        position: absolute;
        bottom: 40px;
        right: 10px;
        background: rgba(0,0,0,0.85);
        padding: 6px 8px;
        border-radius: 6px;
        font-family: sans-serif;
        font-size: 9px;
        color: #aaa;
        z-index: 1000;
        display: flex;
        flex-direction: column;
        gap: 2px;
    }
    .legend-row { display: flex; align-items: center; gap: 5px; }
    .legend-swatch {
        width: 14px; height: 8px; border-radius: 2px;
        display: inline-block; flex-shrink: 0;
    }
</style>
</head>
<body>
<div id="map"></div>
<div id="timestamp"></div>
<div id="framecount"></div>
<div id="legend">
    <div class="legend-row"><span class="legend-swatch" style="background:#007800"></span>Light</div>
    <div class="legend-row"><span class="legend-swatch" style="background:#00C800"></span>Moderate</div>
    <div class="legend-row"><span class="legend-swatch" style="background:#FFFF00"></span>Heavy</div>
    <div class="legend-row"><span class="legend-swatch" style="background:#FF8C00"></span>Very Heavy</div>
    <div class="legend-row"><span class="legend-swatch" style="background:#FF0000"></span>Intense</div>
    <div class="legend-row"><span class="legend-swatch" style="background:#FF00FF"></span>Extreme</div>
</div>
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

    // ─── Traditional NEXRAD color remap ────────────────────────────────
    // RainViewer free tier only provides "Universal Blue" (scheme 2).
    // We fetch the raw tiles, then recolor every pixel to the classic
    // NEXRAD green→yellow→red→magenta palette via a Canvas GridLayer.

    // Traditional NEXRAD dBZ color scale (green→yellow→orange→red→magenta→white)
    var nexradColors = [
        // [minBrightness, maxBrightness, r, g, b]  — maps pixel intensity bands
        [10,  30,   0, 120,   0],   // dark green  — very light rain
        [30,  60,   0, 200,   0],   // green       — light rain
        [60,  90,  50, 255,   0],   // yellow-green
        [90, 115, 255, 255,   0],   // yellow      — moderate rain
        [115,140, 255, 200,   0],   // gold
        [140,165, 255, 140,   0],   // orange      — heavy rain
        [165,190, 255,  60,   0],   // dark orange
        [190,210, 255,   0,   0],   // red         — very heavy rain
        [210,230, 200,   0,   0],   // dark red
        [230,245, 255,   0, 255],   // magenta     — extreme
        [245,256, 255, 255, 255]    // white       — hail / intense
    ];

    function remapColor(r, g, b, a) {
        if (a < 10) return [0, 0, 0, 0]; // transparent stays transparent
        // Compute intensity from the source pixel (weighted luminance)
        var intensity = 0.299 * r + 0.587 * g + 0.114 * b;
        // Also consider max channel for saturated colors
        var maxC = Math.max(r, g, b);
        var bright = Math.max(intensity, maxC * 0.8);
        for (var i = 0; i < nexradColors.length; i++) {
            var c = nexradColors[i];
            if (bright >= c[0] && bright < c[1]) {
                // Interpolate within the band for smooth gradient
                var t = (bright - c[0]) / (c[1] - c[0]);
                var nextIdx = Math.min(i + 1, nexradColors.length - 1);
                var n = nexradColors[nextIdx];
                var nr = Math.round(c[2] + t * (n[2] - c[2]));
                var ng = Math.round(c[3] + t * (n[3] - c[3]));
                var nb = Math.round(c[4] + t * (n[4] - c[4]));
                return [nr, ng, nb, Math.min(a, 220)];
            }
        }
        return [r, g, b, a]; // fallback
    }

    // Custom Canvas GridLayer that fetches RainViewer tiles and recolors them
    var NexradRadarLayer = L.GridLayer.extend({
        options: { tileSize: 256, opacity: 0, zIndex: 10 },

        initialize: function(tileUrl, options) {
            this._radarUrl = tileUrl;
            L.setOptions(this, options);
        },

        createTile: function(coords, done) {
            var tile = document.createElement('canvas');
            var size = this.getTileSize();
            tile.width = size.x;
            tile.height = size.y;

            var img = new Image();
            img.crossOrigin = 'anonymous';
            img.onload = function() {
                var ctx = tile.getContext('2d');
                ctx.drawImage(img, 0, 0, size.x, size.y);
                var imageData = ctx.getImageData(0, 0, size.x, size.y);
                var data = imageData.data;
                for (var p = 0; p < data.length; p += 4) {
                    var result = remapColor(data[p], data[p+1], data[p+2], data[p+3]);
                    data[p]   = result[0];
                    data[p+1] = result[1];
                    data[p+2] = result[2];
                    data[p+3] = result[3];
                }
                ctx.putImageData(imageData, 0, 0);
                done(null, tile);
            };
            img.onerror = function() { done(null, tile); };
            img.src = this._radarUrl
                .replace('{z}', coords.z)
                .replace('{x}', coords.x)
                .replace('{y}', coords.y);
            return tile;
        }
    });

    // Pre-create all radar tile layers with NEXRAD recoloring
    for (var i = 0; i < frames.length; i++) {
        var url = frames[i].host + frames[i].path + '/512/{z}/{x}/{y}/2/1_1.png';
        var layer = new NexradRadarLayer(url, {
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
        radarLayers[idx].setOpacity(0.85);
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
