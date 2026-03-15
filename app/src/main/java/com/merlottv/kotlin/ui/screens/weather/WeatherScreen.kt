@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.weather

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.CurrentWeather
import com.merlottv.kotlin.domain.model.DayForecast
import com.merlottv.kotlin.ui.components.MerlotChip
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Lazy-load on first visibility
    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    // Handle BACK from fullscreen radar
    BackHandler(enabled = uiState.showFullscreenRadar) {
        viewModel.dismissFullscreenRadar()
    }

    // Handle BACK from ZIP dialog
    BackHandler(enabled = uiState.showZipDialog) {
        viewModel.dismissZipDialog()
    }

    // Fullscreen radar overlay
    if (uiState.showFullscreenRadar && uiState.currentWeather != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            RadarMapComposable(
                frames = uiState.radarFrames,
                currentIndex = uiState.radarAnimIndex,
                lat = uiState.currentWeather!!.lat,
                lon = uiState.currentWeather!!.lon,
                isFullscreen = true,
                onToggleFullscreen = { viewModel.dismissFullscreenRadar() }
            )
        }
        return
    }

    // ZIP code dialog overlay
    if (uiState.showZipDialog) {
        ZipCodeDialog(
            currentZip = uiState.zipCode,
            onApply = { viewModel.changeZipCode(it) },
            onDismiss = { viewModel.dismissZipDialog() }
        )
        return
    }

    // Main weather layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        when {
            uiState.isLoading && !uiState.hasLoadedOnce -> {
                // Initial loading state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MerlotColors.Accent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Loading Weather...",
                        color = MerlotColors.TextPrimary,
                        fontSize = 16.sp
                    )
                }
            }
            uiState.error != null && uiState.currentWeather == null -> {
                // Error state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MerlotColors.TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        uiState.error ?: "Unknown error",
                        color = MerlotColors.Danger,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MerlotChip(
                        selected = false,
                        onClick = { viewModel.refresh() },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp), tint = MerlotColors.TextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 12.sp, color = MerlotColors.TextPrimary)
                            }
                        }
                    )
                }
            }
            else -> {
                // Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    item {
                        WeatherHeader(
                            zipCode = uiState.zipCode,
                            isLoading = uiState.isLoading,
                            onChangeZip = { viewModel.toggleZipDialog() },
                            onRefresh = { viewModel.refresh() }
                        )
                    }

                    // Current conditions
                    uiState.currentWeather?.let { weather ->
                        item {
                            CurrentConditionsCard(weather = weather)
                        }
                    }

                    // 7-day forecast
                    if (uiState.forecast.isNotEmpty()) {
                        item {
                            ForecastSection(forecast = uiState.forecast)
                        }
                    }

                    // Radar map
                    if (uiState.currentWeather != null) {
                        item {
                            Column {
                                Text(
                                    "RADAR",
                                    color = MerlotColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                RadarMapComposable(
                                    frames = uiState.radarFrames,
                                    currentIndex = uiState.radarAnimIndex,
                                    lat = uiState.currentWeather!!.lat,
                                    lon = uiState.currentWeather!!.lon,
                                    isFullscreen = false,
                                    onToggleFullscreen = { viewModel.toggleFullscreenRadar() }
                                )
                            }
                        }
                    }

                    // Bottom spacing
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun WeatherHeader(
    zipCode: String,
    isLoading: Boolean,
    onChangeZip: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Cloud,
            contentDescription = null,
            tint = MerlotColors.Accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Weather",
            color = MerlotColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        // ZIP code chip
        MerlotChip(
            selected = false,
            onClick = onChangeZip,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = MerlotColors.Accent)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(zipCode, fontSize = 12.sp, color = MerlotColors.TextPrimary)
                }
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Refresh button
        IconButton(onClick = onRefresh) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MerlotColors.Accent,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MerlotColors.TextMuted
                )
            }
        }
    }
}

// ─── Current Conditions Card ─────────────────────────────────────────────────

@Composable
private fun CurrentConditionsCard(weather: CurrentWeather) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MerlotColors.Surface, shape)
            .border(1.dp, MerlotColors.Border, shape)
            .padding(20.dp)
    ) {
        Text(
            "CURRENT CONDITIONS",
            color = MerlotColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Weather icon
            AsyncImage(
                model = weather.conditionIconUrl,
                contentDescription = weather.condition,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    weather.condition,
                    color = MerlotColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Feels like ${weather.feelsLikeF.toInt()}°F",
                    color = MerlotColors.TextMuted,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Big temperature
            Text(
                "${weather.tempF.toInt()}°F",
                color = MerlotColors.TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detail rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WeatherDetailItem(
                icon = { Icon(Icons.Default.Air, null, modifier = Modifier.size(16.dp), tint = MerlotColors.Accent) },
                label = "Wind",
                value = "${weather.windMph.toInt()} mph ${weather.windDir}"
            )
            WeatherDetailItem(
                icon = { Icon(Icons.Default.WaterDrop, null, modifier = Modifier.size(16.dp), tint = MerlotColors.Accent) },
                label = "Humidity",
                value = "${weather.humidity}%"
            )
            WeatherDetailItem(
                icon = { Text("UV", color = MerlotColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                label = "UV Index",
                value = "${weather.uvIndex.toInt()}"
            )
            WeatherDetailItem(
                icon = { Text("👁", fontSize = 14.sp) },
                label = "Visibility",
                value = "${weather.visibilityMiles.toInt()} mi"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Location and time
        Text(
            "${weather.locationName}, ${weather.region} — ${formatLocalTime(weather.localTime)}",
            color = MerlotColors.TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun WeatherDetailItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(label, color = MerlotColors.TextMuted, fontSize = 10.sp)
    }
}

// ─── 7-Day Forecast ──────────────────────────────────────────────────────────

@Composable
private fun ForecastSection(forecast: List<DayForecast>) {
    Column {
        Text(
            "7-DAY FORECAST",
            color = MerlotColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(forecast) { day ->
                ForecastDayCard(day = day)
            }
        }
    }
}

@Composable
private fun ForecastDayCard(day: DayForecast) {
    val shape = RoundedCornerShape(10.dp)
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(shape)
            .background(
                if (isFocused) Color(0xFF252535) else MerlotColors.Surface,
                shape
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MerlotColors.Accent else MerlotColors.Border,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Day of week
        Text(
            day.dayOfWeek,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Weather icon
        AsyncImage(
            model = day.conditionIconUrl,
            contentDescription = day.condition,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(6.dp))

        // High temp
        Text(
            "${day.maxTempF.toInt()}°",
            color = MerlotColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // Low temp
        Text(
            "${day.minTempF.toInt()}°",
            color = MerlotColors.TextMuted,
            fontSize = 14.sp
        )

        // Rain chance
        if (day.chanceOfRain > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, null, modifier = Modifier.size(10.dp), tint = Color(0xFF4FC3F7))
                Text(
                    " ${day.chanceOfRain}%",
                    color = Color(0xFF4FC3F7),
                    fontSize = 11.sp
                )
            }
        }

        // Snow chance
        if (day.chanceOfSnow > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "❄ ${day.chanceOfSnow}%",
                color = Color(0xFFB3E5FC),
                fontSize = 11.sp
            )
        }
    }
}

// ─── ZIP Code Dialog ─────────────────────────────────────────────────────────

@Composable
private fun ZipCodeDialog(
    currentZip: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var zipText by remember { mutableStateOf(currentZip) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        val dialogShape = RoundedCornerShape(16.dp)

        Column(
            modifier = Modifier
                .width(350.dp)
                .clip(dialogShape)
                .background(MerlotColors.Surface, dialogShape)
                .border(1.dp, MerlotColors.Border, dialogShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Change Location",
                color = MerlotColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Enter a ZIP code to change your weather location",
                color = MerlotColors.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ZIP code input field
            val inputShape = RoundedCornerShape(8.dp)
            var inputFocused by remember { mutableStateOf(false) }

            BasicTextField(
                value = zipText,
                onValueChange = { if (it.length <= 10) zipText = it },
                textStyle = TextStyle(
                    color = MerlotColors.TextPrimary,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(MerlotColors.Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onApply(zipText) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(inputShape)
                    .background(MerlotColors.Surface2, inputShape)
                    .border(
                        width = if (inputFocused) 2.dp else 1.dp,
                        color = if (inputFocused) MerlotColors.Accent else MerlotColors.Border,
                        shape = inputShape
                    )
                    .onFocusChanged { inputFocused = it.isFocused }
                    .focusRequester(focusRequester)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            onApply(zipText)
                            true
                        } else false
                    }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MerlotChip(
                    selected = false,
                    onClick = onDismiss,
                    label = { Text("Cancel", fontSize = 13.sp, color = MerlotColors.TextPrimary) },
                    modifier = Modifier.weight(1f)
                )
                MerlotChip(
                    selected = true,
                    onClick = { onApply(zipText) },
                    label = { Text("Apply", fontSize = 13.sp, color = MerlotColors.Black, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

// ─── Utilities ───────────────────────────────────────────────────────────────

private fun formatLocalTime(localTime: String): String {
    // Input: "2026-03-15 15:42"
    return try {
        val parts = localTime.split(" ")
        if (parts.size >= 2) {
            val timeParts = parts[1].split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1]
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            "$displayHour:$minute $amPm"
        } else localTime
    } catch (_: Exception) { localTime }
}
