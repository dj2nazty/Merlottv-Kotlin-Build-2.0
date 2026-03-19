package com.merlottv.kotlin.ui.screens.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.merlottv.kotlin.domain.model.AirQuality
import com.merlottv.kotlin.domain.model.DayForecast
import com.merlottv.kotlin.domain.model.HourForecast
import com.merlottv.kotlin.domain.model.MarineData
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─── Sun Arc Progress ────────────────────────────────────────────────────────

@Composable
fun SunArcCard(
    sunrise: String,
    sunset: String,
    localTime: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val progress = calculateSunProgress(sunrise, sunset, localTime)

    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.8f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SUN POSITION", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val w = size.width
            val h = size.height
            val arcPadding = 20f
            val arcRect = Size(w - arcPadding * 2, (h - 15f) * 2)
            val arcTopLeft = Offset(arcPadding, 15f)

            // Background arc
            drawArc(
                color = Color(0xFF2A2A3A),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRect,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // Progress arc (golden)
            if (progress > 0f) {
                drawArc(
                    color = Color(0xFFFFB800),
                    startAngle = 180f,
                    sweepAngle = 180f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRect,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }

            // Sun dot
            val angle = PI + PI * progress.coerceIn(0f, 1f)
            val rx = arcRect.width / 2f
            val ry = arcRect.height / 2f
            val cx = arcTopLeft.x + rx + rx * cos(angle).toFloat()
            val cy = arcTopLeft.y + ry + ry * sin(angle).toFloat()
            drawCircle(color = Color(0xFFFFB800), radius = 8f, center = Offset(cx, cy))
            drawCircle(color = Color(0xFFFFDD44), radius = 5f, center = Offset(cx, cy))

            // Horizon line
            drawLine(
                color = Color(0xFF3A3A4A),
                start = Offset(arcPadding, h - 5f),
                end = Offset(w - arcPadding, h - 5f),
                strokeWidth = 1f
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("☀ Sunrise", color = MerlotColors.TextMuted, fontSize = 9.sp)
                Text(sunrise, color = MerlotColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Sunset 🌙", color = MerlotColors.TextMuted, fontSize = 9.sp)
                Text(sunset, color = MerlotColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun calculateSunProgress(sunrise: String, sunset: String, localTime: String): Float {
    try {
        val sunriseMin = parseTimeToMinutes(sunrise)
        val sunsetMin = parseTimeToMinutes(sunset)
        val nowMin = parseLocalTimeToMinutes(localTime)
        if (sunriseMin < 0 || sunsetMin < 0 || nowMin < 0) return 0.5f
        val totalDaylight = sunsetMin - sunriseMin
        if (totalDaylight <= 0) return 0.5f
        return ((nowMin - sunriseMin).toFloat() / totalDaylight).coerceIn(0f, 1f)
    } catch (_: Exception) { return 0.5f }
}

private fun parseTimeToMinutes(time: String): Int {
    // "06:30 AM" or "7:45 PM"
    val parts = time.trim().uppercase().split(" ")
    if (parts.size != 2) return -1
    val timeParts = parts[0].split(":")
    if (timeParts.size != 2) return -1
    var hour = timeParts[0].toIntOrNull() ?: return -1
    val min = timeParts[1].toIntOrNull() ?: return -1
    if (parts[1] == "PM" && hour != 12) hour += 12
    if (parts[1] == "AM" && hour == 12) hour = 0
    return hour * 60 + min
}

private fun parseLocalTimeToMinutes(localTime: String): Int {
    // "2026-03-19 14:30"
    val parts = localTime.split(" ")
    if (parts.size < 2) return -1
    val timeParts = parts[1].split(":")
    if (timeParts.size < 2) return -1
    val hour = timeParts[0].toIntOrNull() ?: return -1
    val min = timeParts[1].toIntOrNull() ?: return -1
    return hour * 60 + min
}

// ─── UV Index Gauge ──────────────────────────────────────────────────────────

@Composable
fun UvIndexGauge(
    uvIndex: Double,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val level = when {
        uvIndex <= 2 -> "Low"
        uvIndex <= 5 -> "Moderate"
        uvIndex <= 7 -> "High"
        uvIndex <= 10 -> "Very High"
        else -> "Extreme"
    }
    val color = when {
        uvIndex <= 2 -> Color(0xFF4CAF50)
        uvIndex <= 5 -> Color(0xFFFFEB3B)
        uvIndex <= 7 -> Color(0xFFFF9800)
        uvIndex <= 10 -> Color(0xFFFF5722)
        else -> Color(0xFF9C27B0)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.8f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("UV INDEX", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Canvas(modifier = Modifier.size(70.dp)) {
            val stroke = 8f
            val padding = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)

            // Background arc
            drawArc(
                color = Color(0xFF2A2A3A),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(padding, padding),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // UV progress arc
            val progress = (uvIndex / 11.0).coerceIn(0.0, 1.0)
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = (270f * progress).toFloat(),
                useCenter = false,
                topLeft = Offset(padding, padding),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Text("${uvIndex.toInt()}", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(level, color = MerlotColors.TextMuted, fontSize = 10.sp)
    }
}

// ─── Air Quality Card ────────────────────────────────────────────────────────

@Composable
fun AirQualityCard(
    aq: AirQuality,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val label = when (aq.usEpaIndex) {
        1 -> "Good"
        2 -> "Moderate"
        3 -> "Unhealthy (Sensitive)"
        4 -> "Unhealthy"
        5 -> "Very Unhealthy"
        6 -> "Hazardous"
        else -> "Unknown"
    }
    val color = when (aq.usEpaIndex) {
        1 -> Color(0xFF4CAF50)
        2 -> Color(0xFFFFEB3B)
        3 -> Color(0xFFFF9800)
        4 -> Color(0xFFFF5722)
        5 -> Color(0xFF9C27B0)
        6 -> Color(0xFF7B1FA2)
        else -> Color(0xFF888888)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.8f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AIR QUALITY", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // AQI bar
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val w = size.width
            val h = size.height
            // Background
            drawRoundRect(color = Color(0xFF2A2A3A), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            // Gradient sections
            val sections = listOf(Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFF7B1FA2))
            val sectionWidth = w / 6f
            sections.forEachIndexed { i, c ->
                drawRect(color = c, topLeft = Offset(i * sectionWidth, 0f), size = Size(sectionWidth, h))
            }
            // Indicator
            val pos = ((aq.usEpaIndex - 1).coerceIn(0, 5).toFloat() / 5f * (w - 4f)) + 2f
            drawCircle(color = Color.White, radius = 6f, center = Offset(pos, h / 2f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AqiItem("PM2.5", String.format("%.1f", aq.pm25))
            AqiItem("PM10", String.format("%.1f", aq.pm10))
            AqiItem("O3", String.format("%.0f", aq.o3))
            AqiItem("NO2", String.format("%.0f", aq.no2))
        }
    }
}

@Composable
private fun AqiItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(label, color = MerlotColors.TextMuted, fontSize = 9.sp)
    }
}

// ─── 7-Day Temperature Graph ─────────────────────────────────────────────────

@Composable
fun WeeklyTempGraph(
    forecast: List<DayForecast>,
    modifier: Modifier = Modifier
) {
    if (forecast.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.8f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(16.dp)
    ) {
        Text("TEMPERATURE TREND", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val w = size.width
            val h = size.height
            val padding = 30f
            val graphW = w - padding * 2
            val graphH = h - 30f

            val allTemps = forecast.flatMap { listOf(it.maxTempF, it.minTempF) }
            val minTemp = (allTemps.minOrNull() ?: 0.0) - 3
            val maxTemp = (allTemps.maxOrNull() ?: 100.0) + 3
            val tempRange = maxTemp - minTemp

            fun tempToY(temp: Double): Float = (graphH - ((temp - minTemp) / tempRange * graphH)).toFloat() + 10f
            fun indexToX(i: Int): Float = padding + (i.toFloat() / (forecast.size - 1).coerceAtLeast(1)) * graphW

            // Grid lines
            for (i in 0..3) {
                val y = 10f + graphH * i / 3f
                drawLine(Color(0xFF2A2A3A), Offset(padding, y), Offset(w - padding, y), strokeWidth = 0.5f)
            }

            // High temp line
            val highPath = Path()
            forecast.forEachIndexed { i, day ->
                val x = indexToX(i)
                val y = tempToY(day.maxTempF)
                if (i == 0) highPath.moveTo(x, y) else highPath.lineTo(x, y)
            }
            drawPath(highPath, Color(0xFFFF6B35), style = Stroke(width = 3f, cap = StrokeCap.Round))

            // Low temp line
            val lowPath = Path()
            forecast.forEachIndexed { i, day ->
                val x = indexToX(i)
                val y = tempToY(day.minTempF)
                if (i == 0) lowPath.moveTo(x, y) else lowPath.lineTo(x, y)
            }
            drawPath(lowPath, Color(0xFF4FC3F7), style = Stroke(width = 3f, cap = StrokeCap.Round))

            // Dots and labels
            forecast.forEachIndexed { i, day ->
                val x = indexToX(i)
                // High dot
                drawCircle(Color(0xFFFF6B35), 4f, Offset(x, tempToY(day.maxTempF)))
                // Low dot
                drawCircle(Color(0xFF4FC3F7), 4f, Offset(x, tempToY(day.minTempF)))
                // Day label
                drawContext.canvas.nativeCanvas.drawText(
                    if (i == 0) "Today" else day.dayOfWeek,
                    x,
                    h - 2f,
                    android.graphics.Paint().apply {
                        color = 0xFF888899.toInt()
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(8.dp)) { drawCircle(Color(0xFFFF6B35)) }
                Spacer(Modifier.width(4.dp))
                Text("High", color = MerlotColors.TextMuted, fontSize = 10.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(8.dp)) { drawCircle(Color(0xFF4FC3F7)) }
                Spacer(Modifier.width(4.dp))
                Text("Low", color = MerlotColors.TextMuted, fontSize = 10.sp)
            }
        }
    }
}

// ─── Hourly Temperature Graph ────────────────────────────────────────────────

@Composable
fun HourlyTempGraph(
    hourly: List<HourForecast>,
    modifier: Modifier = Modifier
) {
    if (hourly.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.85f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(16.dp)
    ) {
        Text("HOURLY TEMPERATURE", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val w = size.width
            val h = size.height
            val padding = 25f
            val graphW = w - padding * 2
            val graphH = h - 30f

            val temps = hourly.map { it.tempF }
            val minT = (temps.minOrNull() ?: 0.0) - 2
            val maxT = (temps.maxOrNull() ?: 100.0) + 2
            val range = maxT - minT

            fun tY(t: Double): Float = (graphH - ((t - minT) / range * graphH)).toFloat() + 10f
            fun iX(i: Int): Float = padding + (i.toFloat() / (hourly.size - 1).coerceAtLeast(1)) * graphW

            // Grid
            for (g in 0..3) {
                val y = 10f + graphH * g / 3f
                drawLine(Color(0xFF2A2A3A), Offset(padding, y), Offset(w - padding, y), 0.5f)
            }

            // Fill area under curve
            val fillPath = Path().apply {
                moveTo(iX(0), tY(temps[0]))
                for (i in 1 until hourly.size) lineTo(iX(i), tY(temps[i]))
                lineTo(iX(hourly.size - 1), graphH + 10f)
                lineTo(iX(0), graphH + 10f)
                close()
            }
            drawPath(fillPath, Color(0x18FF6B35))

            // Line
            val linePath = Path()
            hourly.forEachIndexed { i, hr ->
                val x = iX(i)
                val y = tY(hr.tempF)
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            drawPath(linePath, Color(0xFFFF6B35), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

            // Time labels every 3 hours
            for (i in hourly.indices step 3) {
                drawContext.canvas.nativeCanvas.drawText(
                    hourly[i].displayTime,
                    iX(i),
                    h - 2f,
                    android.graphics.Paint().apply {
                        color = 0xFF888899.toInt()
                        textSize = 18f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }

            // Dots every 3 hours
            for (i in hourly.indices step 3) {
                drawCircle(Color(0xFFFF6B35), 3f, Offset(iX(i), tY(hourly[i].tempF)))
            }
        }
    }
}

// ─── Wind Compass ────────────────────────────────────────────────────────────

@Composable
fun WindCompass(
    windDegree: Int,
    windMph: Double,
    gustMph: Double,
    windDir: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.8f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WIND", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(6.dp))

        Canvas(modifier = Modifier.size(65.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = cx - 8f

            // Outer circle
            drawCircle(Color(0xFF2A2A3A), r, Offset(cx, cy), style = Stroke(2f))

            // Cardinal direction ticks
            val directions = listOf("N", "E", "S", "W")
            directions.forEachIndexed { i, dir ->
                val angle = (i * 90.0 - 90.0) * PI / 180.0
                val tickStart = Offset(cx + (r - 6f) * cos(angle).toFloat(), cy + (r - 6f) * sin(angle).toFloat())
                val tickEnd = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat())
                drawLine(Color(0xFF666680), tickStart, tickEnd, 1.5f)
            }

            // Wind arrow
            val arrowAngle = (windDegree.toDouble() - 90.0) * PI / 180.0
            val arrowLen = r - 12f
            val tipX = cx + arrowLen * cos(arrowAngle).toFloat()
            val tipY = cy + arrowLen * sin(arrowAngle).toFloat()
            drawLine(MerlotColors.Accent, Offset(cx, cy), Offset(tipX, tipY), 3f, cap = StrokeCap.Round)
            // Arrowhead
            drawCircle(MerlotColors.Accent, 4f, Offset(tipX, tipY))
            // Center dot
            drawCircle(Color(0xFF444460), 4f, Offset(cx, cy))
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("${windMph.toInt()} mph $windDir", color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (gustMph > 0) {
            Text("Gusts ${gustMph.toInt()} mph", color = MerlotColors.TextMuted, fontSize = 10.sp)
        }
    }
}

// ─── Marine / Wave Card ──────────────────────────────────────────────────────

@Composable
fun MarineCard(
    marine: MarineData,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.8f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(16.dp)
    ) {
        Text("WAVE CONDITIONS", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Wave animation
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val w = size.width
            val h = size.height
            val wavePath = Path()
            val amplitude = (marine.waveHeightFt * 5f).coerceIn(3.0, 15.0).toFloat()

            wavePath.moveTo(0f, h / 2f)
            var x = 0f
            while (x <= w) {
                val y = h / 2f + amplitude * sin(x * 0.03f).toFloat()
                wavePath.lineTo(x, y)
                x += 2f
            }
            wavePath.lineTo(w, h)
            wavePath.lineTo(0f, h)
            wavePath.close()
            drawPath(wavePath, Color(0xFF1565C0).copy(alpha = 0.3f))

            // Second wave layer
            val wave2 = Path()
            wave2.moveTo(0f, h / 2f + 5f)
            x = 0f
            while (x <= w) {
                val y = h / 2f + 5f + amplitude * 0.7f * sin(x * 0.025f + 1.5f).toFloat()
                wave2.lineTo(x, y)
                x += 2f
            }
            wave2.lineTo(w, h)
            wave2.lineTo(0f, h)
            wave2.close()
            drawPath(wave2, Color(0xFF1976D2).copy(alpha = 0.2f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MarineItem("Waves", String.format("%.1f ft", marine.waveHeightFt))
            MarineItem("Period", String.format("%.0fs", marine.wavePeriodSec))
            MarineItem("Swell", String.format("%.1f ft", marine.swellHeightFt))
            if (marine.windWaveHeightFt > 0) {
                MarineItem("Wind Wave", String.format("%.1f ft", marine.windWaveHeightFt))
            }
        }
    }
}

@Composable
private fun MarineItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(label, color = MerlotColors.TextMuted, fontSize = 9.sp)
    }
}

// ─── Clothing Suggestion ─────────────────────────────────────────────────────

fun getClothingSuggestion(tempF: Double, windMph: Double, chanceOfRain: Int, uvIndex: Double, condition: String): String {
    val suggestions = mutableListOf<String>()
    val c = condition.lowercase()

    when {
        tempF <= 20 -> suggestions.add("Heavy winter coat, gloves & hat")
        tempF <= 32 -> suggestions.add("Winter jacket & layers")
        tempF <= 45 -> suggestions.add("Warm jacket")
        tempF <= 55 -> suggestions.add("Light jacket or sweater")
        tempF <= 68 -> suggestions.add("Long sleeves")
        tempF <= 80 -> suggestions.add("T-shirt weather")
        else -> suggestions.add("Light & breathable clothing")
    }

    if (windMph >= 25) suggestions.add("Windbreaker recommended")
    else if (windMph >= 15 && tempF < 50) suggestions.add("Wind chill — extra layer")

    if (chanceOfRain >= 50) suggestions.add("Bring an umbrella")
    else if (chanceOfRain >= 30) suggestions.add("Rain possible — umbrella handy")

    if (c.contains("snow") || c.contains("blizzard")) suggestions.add("Waterproof boots")

    if (uvIndex >= 8) suggestions.add("Sunscreen SPF 50+")
    else if (uvIndex >= 6) suggestions.add("Sunscreen recommended")
    else if (uvIndex >= 3) suggestions.add("Sunglasses")

    return suggestions.joinToString("  •  ")
}

// ─── Moon Phase Emoji ────────────────────────────────────────────────────────

fun moonPhaseEmoji(phase: String): String {
    val p = phase.lowercase()
    return when {
        p.contains("new moon") -> "🌑"
        p.contains("waxing crescent") -> "🌒"
        p.contains("first quarter") -> "🌓"
        p.contains("waxing gibbous") -> "🌔"
        p.contains("full moon") -> "🌕"
        p.contains("waning gibbous") -> "🌖"
        p.contains("last quarter") || p.contains("third quarter") -> "🌗"
        p.contains("waning crescent") -> "🌘"
        else -> "🌙"
    }
}
