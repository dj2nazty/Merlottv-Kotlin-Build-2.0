package com.merlottv.kotlin.ui.screens.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated weather background using Compose Canvas particles.
 * Maps the WeatherAPI condition string to a visual particle effect.
 */

// ─── Weather type classification ─────────────────────────────────────────────

private enum class WeatherType {
    SUNNY, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, RAIN, HEAVY_RAIN,
    THUNDERSTORM, SNOW, BLIZZARD, FOG, WIND
}

private fun classifyCondition(condition: String, isDay: Boolean): WeatherType {
    val c = condition.lowercase()
    return when {
        c.contains("thunder") || c.contains("lightning") -> WeatherType.THUNDERSTORM
        c.contains("blizzard") -> WeatherType.BLIZZARD
        c.contains("heavy rain") || c.contains("torrential") || c.contains("heavy shower") -> WeatherType.HEAVY_RAIN
        c.contains("rain") || c.contains("drizzle") || c.contains("shower") || c.contains("sleet") -> WeatherType.RAIN
        c.contains("heavy snow") || c.contains("blowing snow") -> WeatherType.BLIZZARD
        c.contains("snow") || c.contains("ice") || c.contains("freezing") || c.contains("flurries") -> WeatherType.SNOW
        c.contains("fog") || c.contains("mist") || c.contains("haze") -> WeatherType.FOG
        c.contains("overcast") -> WeatherType.CLOUDY
        c.contains("cloudy") || c.contains("cloud") -> WeatherType.PARTLY_CLOUDY
        c.contains("sunny") || c.contains("clear") -> if (isDay) WeatherType.SUNNY else WeatherType.CLEAR_NIGHT
        c.contains("wind") -> WeatherType.WIND
        else -> if (isDay) WeatherType.PARTLY_CLOUDY else WeatherType.CLEAR_NIGHT
    }
}

// ─── Particle data ───────────────────────────────────────────────────────────

private data class Particle(
    val x: Float,       // 0-1 normalized
    val y: Float,       // 0-1 normalized
    val speed: Float,   // pixels per frame multiplier
    val size: Float,    // radius or length
    val alpha: Float,   // 0-1
    val drift: Float    // horizontal drift for snow/rain angle
)

private fun generateParticles(count: Int, seed: Int = 42): List<Particle> {
    val rng = Random(seed)
    return List(count) {
        Particle(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            speed = 0.3f + rng.nextFloat() * 0.7f,
            size = 1f + rng.nextFloat() * 3f,
            alpha = 0.2f + rng.nextFloat() * 0.6f,
            drift = -0.2f + rng.nextFloat() * 0.4f
        )
    }
}

// ─── Main composable ─────────────────────────────────────────────────────────

@Composable
fun WeatherAnimatedBackground(
    condition: String,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val weatherType = remember(condition, isDay) { classifyCondition(condition, isDay) }

    val transition = rememberInfiniteTransition(label = "weather_bg")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Lightning flash for thunderstorms
    val flash by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flash"
    )

    val particles = remember(weatherType) {
        when (weatherType) {
            WeatherType.RAIN -> generateParticles(120, seed = 1)
            WeatherType.HEAVY_RAIN -> generateParticles(200, seed = 2)
            WeatherType.THUNDERSTORM -> generateParticles(180, seed = 3)
            WeatherType.SNOW -> generateParticles(80, seed = 4)
            WeatherType.BLIZZARD -> generateParticles(150, seed = 5)
            WeatherType.FOG -> generateParticles(20, seed = 6)
            WeatherType.SUNNY -> generateParticles(30, seed = 7)
            WeatherType.CLEAR_NIGHT -> generateParticles(60, seed = 8)
            WeatherType.PARTLY_CLOUDY -> generateParticles(15, seed = 9)
            WeatherType.CLOUDY -> generateParticles(12, seed = 10)
            WeatherType.WIND -> generateParticles(50, seed = 11)
        }
    }

    // Background gradient colors
    val gradientColors = remember(weatherType) {
        when (weatherType) {
            WeatherType.SUNNY -> listOf(Color(0xFF1A3A5C), Color(0xFF0D2240), Color(0xFF0A1628))
            WeatherType.CLEAR_NIGHT -> listOf(Color(0xFF0A0A20), Color(0xFF050518), Color(0xFF02020C))
            WeatherType.PARTLY_CLOUDY -> listOf(Color(0xFF1A2A40), Color(0xFF101828), Color(0xFF0A1018))
            WeatherType.CLOUDY -> listOf(Color(0xFF1A1A28), Color(0xFF121220), Color(0xFF0A0A14))
            WeatherType.RAIN -> listOf(Color(0xFF0C1A2A), Color(0xFF081420), Color(0xFF040A14))
            WeatherType.HEAVY_RAIN -> listOf(Color(0xFF080F1A), Color(0xFF050A14), Color(0xFF02060C))
            WeatherType.THUNDERSTORM -> listOf(Color(0xFF0A0A18), Color(0xFF060612), Color(0xFF02020A))
            WeatherType.SNOW -> listOf(Color(0xFF1A2030), Color(0xFF141828), Color(0xFF0E1220))
            WeatherType.BLIZZARD -> listOf(Color(0xFF1E2434), Color(0xFF161C2C), Color(0xFF101624))
            WeatherType.FOG -> listOf(Color(0xFF1A1A24), Color(0xFF14141E), Color(0xFF0E0E18))
            WeatherType.WIND -> listOf(Color(0xFF141E2C), Color(0xFF0E1620), Color(0xFF0A1018))
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Draw gradient background
        drawRect(
            brush = Brush.verticalGradient(gradientColors),
            size = size
        )

        when (weatherType) {
            WeatherType.SUNNY -> {
                // Warm sun glow at top-right
                val glowX = w * 0.8f
                val glowY = h * 0.1f
                val glowRadius = w * 0.3f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x30FFB800),
                            Color(0x18FF8C00),
                            Color(0x00000000)
                        ),
                        center = Offset(glowX, glowY),
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = Offset(glowX, glowY)
                )
                // Floating light particles
                particles.forEach { p ->
                    val px = ((p.x * w + time * p.speed * 0.3f + p.drift * time * 0.5f) % (w * 1.2f)) - w * 0.1f
                    val py = ((p.y * h + sin((time * 0.01f + p.x * 10f).toDouble()).toFloat() * 20f) % h)
                    drawCircle(
                        color = Color(0xFFFFCC44).copy(alpha = p.alpha * 0.4f),
                        radius = p.size * 1.5f,
                        center = Offset(px, py)
                    )
                }
            }

            WeatherType.CLEAR_NIGHT -> {
                // Twinkling stars
                particles.forEach { p ->
                    val twinkle = (sin((time * 0.02f * p.speed + p.x * 20f).toDouble()).toFloat() + 1f) / 2f
                    drawCircle(
                        color = Color.White.copy(alpha = p.alpha * twinkle * 0.7f),
                        radius = p.size * 0.6f,
                        center = Offset(p.x * w, p.y * h)
                    )
                }
                // Moon glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x20C0C0FF),
                            Color(0x08606080),
                            Color(0x00000000)
                        ),
                        center = Offset(w * 0.85f, h * 0.12f),
                        radius = w * 0.15f
                    ),
                    radius = w * 0.15f,
                    center = Offset(w * 0.85f, h * 0.12f)
                )
            }

            WeatherType.PARTLY_CLOUDY -> {
                // Drifting cloud blobs
                particles.forEach { p ->
                    val cx = ((p.x * w + time * p.speed * 0.2f) % (w * 1.4f)) - w * 0.2f
                    val cy = p.y * h * 0.6f + h * 0.05f
                    drawCircle(
                        color = Color(0xFFB0B8C8).copy(alpha = p.alpha * 0.08f),
                        radius = 30f + p.size * 25f,
                        center = Offset(cx, cy)
                    )
                }
            }

            WeatherType.CLOUDY -> {
                // Dense slow clouds
                particles.forEach { p ->
                    val cx = ((p.x * w + time * p.speed * 0.12f) % (w * 1.4f)) - w * 0.2f
                    val cy = p.y * h * 0.7f + h * 0.05f
                    drawCircle(
                        color = Color(0xFF808898).copy(alpha = p.alpha * 0.1f),
                        radius = 40f + p.size * 30f,
                        center = Offset(cx, cy)
                    )
                }
            }

            WeatherType.RAIN, WeatherType.HEAVY_RAIN -> {
                // Rain streaks
                val speedMult = if (weatherType == WeatherType.HEAVY_RAIN) 2.5f else 1.5f
                particles.forEach { p ->
                    val rainY = ((p.y * h + time * p.speed * speedMult) % (h * 1.1f)) - h * 0.05f
                    val rainX = p.x * w + p.drift * 40f
                    val streakLen = p.size * (if (weatherType == WeatherType.HEAVY_RAIN) 14f else 10f)
                    val angle = 0.15f + p.drift * 0.3f // slight angle
                    drawLine(
                        color = Color(0xFF7EB8E0).copy(alpha = p.alpha * 0.5f),
                        start = Offset(rainX, rainY),
                        end = Offset(rainX + angle * streakLen, rainY + streakLen),
                        strokeWidth = if (weatherType == WeatherType.HEAVY_RAIN) 1.8f else 1.2f
                    )
                }
            }

            WeatherType.THUNDERSTORM -> {
                // Rain (reuse rain logic)
                particles.forEach { p ->
                    val rainY = ((p.y * h + time * p.speed * 2.5f) % (h * 1.1f)) - h * 0.05f
                    val rainX = p.x * w + p.drift * 50f
                    val streakLen = p.size * 14f
                    drawLine(
                        color = Color(0xFF6090B0).copy(alpha = p.alpha * 0.4f),
                        start = Offset(rainX, rainY),
                        end = Offset(rainX + 0.2f * streakLen, rainY + streakLen),
                        strokeWidth = 1.5f
                    )
                }
                // Lightning flash — brief white overlay
                val flashPhase = flash % 1f
                if (flashPhase > 0.92f && flashPhase < 0.96f) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.15f),
                        size = size
                    )
                }
                if (flashPhase > 0.45f && flashPhase < 0.48f) {
                    drawRect(
                        color = Color(0xFFCCDDFF).copy(alpha = 0.08f),
                        size = size
                    )
                }
            }

            WeatherType.SNOW -> {
                // Snowflakes drifting
                particles.forEach { p ->
                    val snowY = ((p.y * h + time * p.speed * 0.4f) % (h * 1.1f)) - h * 0.05f
                    val drift = sin((time * 0.01f * p.speed + p.x * 15f).toDouble()).toFloat() * 30f
                    val snowX = p.x * w + drift
                    drawCircle(
                        color = Color.White.copy(alpha = p.alpha * 0.6f),
                        radius = p.size * 1.2f,
                        center = Offset(snowX, snowY)
                    )
                }
            }

            WeatherType.BLIZZARD -> {
                // Dense fast snow with wind
                particles.forEach { p ->
                    val snowY = ((p.y * h + time * p.speed * 0.8f) % (h * 1.1f)) - h * 0.05f
                    val windDrift = time * 0.6f * p.speed + sin((time * 0.02f + p.y * 10f).toDouble()).toFloat() * 40f
                    val snowX = ((p.x * w + windDrift) % (w * 1.2f)) - w * 0.1f
                    drawCircle(
                        color = Color.White.copy(alpha = p.alpha * 0.5f),
                        radius = p.size * 1.0f,
                        center = Offset(snowX, snowY)
                    )
                }
                // White fog overlay
                drawRect(
                    color = Color.White.copy(alpha = 0.04f),
                    size = size
                )
            }

            WeatherType.FOG -> {
                // Large translucent fog blobs drifting
                particles.forEach { p ->
                    val fogX = ((p.x * w + time * p.speed * 0.15f) % (w * 1.6f)) - w * 0.3f
                    val fogY = p.y * h
                    drawCircle(
                        color = Color(0xFFC0C0D0).copy(alpha = p.alpha * 0.06f),
                        radius = 50f + p.size * 40f,
                        center = Offset(fogX, fogY)
                    )
                }
                // Overall fog overlay
                drawRect(
                    color = Color(0xFFB0B0C0).copy(alpha = 0.05f),
                    size = size
                )
            }

            WeatherType.WIND -> {
                // Horizontal streaking particles
                particles.forEach { p ->
                    val windX = ((p.x * w + time * p.speed * 1.5f) % (w * 1.3f)) - w * 0.15f
                    val windY = p.y * h + sin((time * 0.01f + p.x * 8f).toDouble()).toFloat() * 10f
                    val streakLen = 15f + p.size * 12f
                    drawLine(
                        color = Color(0xFF90A0B0).copy(alpha = p.alpha * 0.3f),
                        start = Offset(windX, windY),
                        end = Offset(windX + streakLen, windY + p.drift * 3f),
                        strokeWidth = 0.8f
                    )
                }
            }
        }
    }
}
