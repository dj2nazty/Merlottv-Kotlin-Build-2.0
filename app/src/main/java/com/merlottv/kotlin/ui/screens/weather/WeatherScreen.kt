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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.onKeyEvent
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
import com.merlottv.kotlin.domain.model.HourForecast
import com.merlottv.kotlin.domain.model.WeatherAlert
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

    // Handle BACK from day detail
    BackHandler(enabled = uiState.selectedDayIndex >= 0) {
        viewModel.dismissDayDetail()
    }

    // Handle BACK from alert detail
    BackHandler(enabled = uiState.selectedAlertIndex >= 0) {
        viewModel.dismissAlertDetail()
    }

    // Handle BACK from ZIP dialog
    BackHandler(enabled = uiState.showZipDialog) {
        viewModel.dismissZipDialog()
    }

    // Day detail overlay (full screen)
    if (uiState.selectedDayIndex >= 0 && uiState.selectedDayIndex < uiState.forecast.size) {
        DayDetailOverlay(
            day = uiState.forecast[uiState.selectedDayIndex],
            onDismiss = { viewModel.dismissDayDetail() }
        )
        return
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
                lat = uiState.currentWeather?.lat ?: 0.0,
                lon = uiState.currentWeather?.lon ?: 0.0,
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
    ) {
        // Animated weather background
        if (uiState.currentWeather != null) {
            WeatherAnimatedBackground(
                condition = uiState.currentWeather?.condition ?: "",
                isDay = uiState.currentWeather?.isDay ?: true
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MerlotColors.Background))
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

                    // Weather alerts banner
                    if (uiState.alerts.isNotEmpty()) {
                        item {
                            AlertBanner(
                                alerts = uiState.alerts,
                                onAlertClick = { index -> viewModel.selectAlert(index) }
                            )
                        }
                        if (uiState.selectedAlertIndex in uiState.alerts.indices) {
                            item {
                                AlertDetailCard(
                                    alert = uiState.alerts[uiState.selectedAlertIndex],
                                    onDismiss = { viewModel.dismissAlertDetail() }
                                )
                            }
                        }
                    }

                    // Clothing suggestion
                    uiState.currentWeather?.let { weather ->
                        val todayForecast = uiState.forecast.firstOrNull()
                        val suggestion = getClothingSuggestion(
                            tempF = weather.tempF,
                            windMph = weather.windMph,
                            chanceOfRain = todayForecast?.chanceOfRain ?: 0,
                            uvIndex = weather.uvIndex,
                            condition = weather.condition
                        )
                        item {
                            val chipShape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(chipShape)
                                    .background(MerlotColors.Accent.copy(alpha = 0.1f), chipShape)
                                    .border(1.dp, MerlotColors.Accent.copy(alpha = 0.3f), chipShape)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("👕  $suggestion", color = MerlotColors.TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }

                    // Current conditions
                    uiState.currentWeather?.let { weather ->
                        item { CurrentConditionsCard(weather = weather) }
                    }

                    // Row: Sun Arc + UV Gauge + Wind Compass
                    uiState.currentWeather?.let { weather ->
                        val todayForecast = uiState.forecast.firstOrNull()
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SunArcCard(
                                    sunrise = todayForecast?.sunrise ?: "",
                                    sunset = todayForecast?.sunset ?: "",
                                    localTime = weather.localTime,
                                    modifier = Modifier.weight(1f)
                                )
                                UvIndexGauge(
                                    uvIndex = weather.uvIndex,
                                    modifier = Modifier.weight(0.6f)
                                )
                                WindCompass(
                                    windDegree = weather.windDegree,
                                    windMph = weather.windMph,
                                    gustMph = weather.windGustMph,
                                    windDir = weather.windDir,
                                    modifier = Modifier.weight(0.6f)
                                )
                            }
                        }
                    }

                    // Air Quality
                    uiState.currentWeather?.airQuality?.let { aq ->
                        item { AirQualityCard(aq = aq, modifier = Modifier.fillMaxWidth()) }
                    }

                    // 7-day forecast
                    if (uiState.forecast.isNotEmpty()) {
                        item {
                            ForecastSection(
                                forecast = uiState.forecast,
                                selectedIndex = uiState.selectedDayIndex,
                                onDayClick = { index -> viewModel.selectDay(index) }
                            )
                        }
                    }

                    // Weekly Temperature Graph
                    if (uiState.forecast.size >= 2) {
                        item { WeeklyTempGraph(forecast = uiState.forecast, modifier = Modifier.fillMaxWidth()) }
                    }

                    // Moon phase (from today's forecast)
                    uiState.forecast.firstOrNull()?.let { today ->
                        if (today.moonPhase.isNotEmpty()) {
                            item {
                                val moonShape = RoundedCornerShape(12.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(moonShape)
                                        .background(MerlotColors.Surface.copy(alpha = 0.8f), moonShape)
                                        .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), moonShape)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(moonPhaseEmoji(today.moonPhase), fontSize = 28.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("MOON PHASE", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        Text(today.moonPhase, color = MerlotColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${today.moonIllumination}%", color = MerlotColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("Illumination", color = MerlotColors.TextMuted, fontSize = 9.sp)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        if (today.moonrise.isNotEmpty()) Text("Rise ${today.moonrise}", color = MerlotColors.TextMuted, fontSize = 11.sp)
                                        if (today.moonset.isNotEmpty()) Text("Set ${today.moonset}", color = MerlotColors.TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Marine / Wave data
                    uiState.marineData?.let { marine ->
                        if (marine.waveHeightFt > 0) {
                            item { MarineCard(marine = marine, modifier = Modifier.fillMaxWidth()) }
                        }
                    }

                    // Radar map
                    if (uiState.currentWeather != null) {
                        item {
                            Column {
                                Text("RADAR", color = MerlotColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                RadarMapComposable(
                                    frames = uiState.radarFrames,
                                    currentIndex = uiState.radarAnimIndex,
                                    lat = uiState.currentWeather?.lat ?: 0.0,
                                    lon = uiState.currentWeather?.lon ?: 0.0,
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
    } // outer background Box
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
            .background(MerlotColors.Surface.copy(alpha = 0.8f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
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
private fun ForecastSection(
    forecast: List<DayForecast>,
    selectedIndex: Int,
    onDayClick: (Int) -> Unit
) {
    val focusRequesters = remember(forecast.size) { List(forecast.size) { FocusRequester() } }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "7-DAY FORECAST",
                color = MerlotColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Select a day for details",
                color = MerlotColors.TextMuted,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Use Row (not LazyRow) so all 7 cards always fit on screen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            forecast.forEachIndexed { index, day ->
                ForecastDayCard(
                    day = day,
                    isSelected = index == selectedIndex,
                    itemIndex = index,
                    totalItems = forecast.size,
                    focusRequester = focusRequesters[index],
                    onFocusLeft = {
                        if (index > 0) {
                            try { focusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                        }
                    },
                    onFocusRight = {
                        if (index < forecast.size - 1) {
                            try { focusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                        }
                    },
                    onClick = { onDayClick(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ForecastDayCard(
    day: DayForecast,
    isSelected: Boolean = false,
    itemIndex: Int = 0,
    totalItems: Int = 7,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusLeft: () -> Unit = {},
    onFocusRight: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    var isFocused by remember { mutableStateOf(false) }
    val isToday = itemIndex == 0

    val bgColor = when {
        isSelected -> Color(0xFF1A2A3A)
        isFocused -> Color(0xFF252535)
        isToday -> Color(0xFF1A1A2E)
        else -> MerlotColors.Surface.copy(alpha = 0.85f)
    }

    val borderColor = when {
        isSelected -> MerlotColors.Accent
        isFocused -> MerlotColors.Accent
        isToday -> Color(0xFF2A4A5A)
        else -> MerlotColors.Border
    }

    Column(
        modifier = modifier
            .height(110.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> { onClick(); true }
                        Key.DirectionLeft -> {
                            if (itemIndex > 0) { onFocusLeft(); true }
                            else false // let it bubble to open sidebar
                        }
                        Key.DirectionRight -> {
                            if (itemIndex < totalItems - 1) { onFocusRight(); true }
                            else true // consume at end
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Day label — "TODAY" for first item
        Text(
            if (isToday) "TODAY" else day.dayOfWeek,
            color = when {
                isToday -> MerlotColors.Accent
                isFocused -> MerlotColors.Accent
                else -> MerlotColors.TextPrimary
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (isToday) 1.sp else 0.sp
        )

        // Icon + temps row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = day.conditionIconUrl,
                contentDescription = day.condition,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${day.maxTempF.toInt()}°",
                    color = MerlotColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${day.minTempF.toInt()}°",
                    color = MerlotColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Bottom info row — rain/snow/wind
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (day.chanceOfRain > 0) {
                Icon(Icons.Default.WaterDrop, null, modifier = Modifier.size(9.dp), tint = Color(0xFF4FC3F7))
                Text(" ${day.chanceOfRain}%", color = Color(0xFF4FC3F7), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (day.chanceOfSnow > 0) {
                Text("❄${day.chanceOfSnow}%", color = Color(0xFFB3E5FC), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(Icons.Default.Air, null, modifier = Modifier.size(9.dp), tint = MerlotColors.TextMuted)
            Text(" ${day.maxWindMph.toInt()}", color = MerlotColors.TextMuted, fontSize = 10.sp)
        }
    }
}

// ─── Day Detail Overlay (Full Screen) ────────────────────────────────────────

@Composable
private fun DayDetailOverlay(
    day: DayForecast,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xF0101020)).padding(20.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with prominent HIGH / LOW
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = day.conditionIconUrl, contentDescription = day.condition,
                        modifier = Modifier.size(64.dp), contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${day.dayOfWeek} — ${day.date}", color = MerlotColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(day.condition, color = MerlotColors.TextPrimary, fontSize = 14.sp)
                    }
                    // High / Low prominently displayed
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HIGH", color = Color(0xFFFF6B35), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("${day.maxTempF.toInt()}°", color = Color(0xFFFF6B35), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LOW", color = Color(0xFF4FC3F7), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("${day.minTempF.toInt()}°", color = Color(0xFF4FC3F7), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Detail cards row
            item {
                val cs = RoundedCornerShape(10.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailMini("Wind", "${day.maxWindMph.toInt()} mph", MerlotColors.Accent, Modifier.weight(1f), cs)
                    DetailMini("Humidity", "${day.avgHumidity}%", MerlotColors.Accent, Modifier.weight(1f), cs)
                    DetailMini("Rain", "${day.chanceOfRain}%", Color(0xFF4FC3F7), Modifier.weight(1f), cs)
                    DetailMini("UV", "${day.uvIndex.toInt()}", Color(0xFFFFEB3B), Modifier.weight(1f), cs)
                    DetailMini("Visibility", "${day.avgVisibilityMiles.toInt()} mi", MerlotColors.TextMuted, Modifier.weight(1f), cs)
                    if (day.chanceOfSnow > 0) DetailMini("Snow", "${day.chanceOfSnow}%", Color(0xFFB3E5FC), Modifier.weight(1f), cs)
                    if (day.totalPrecipIn > 0) DetailMini("Precip", "${String.format("%.2f", day.totalPrecipIn)}\"", Color(0xFF4FC3F7), Modifier.weight(1f), cs)
                }
            }

            // Sunrise/Sunset + Moon row
            item {
                val barShape = RoundedCornerShape(10.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sun info
                    Row(
                        modifier = Modifier.weight(1f).clip(barShape).background(MerlotColors.Surface.copy(alpha = 0.85f), barShape)
                            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), barShape).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (day.sunrise.isNotEmpty()) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("☀", fontSize = 16.sp); Text(day.sunrise, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium); Text("Sunrise", color = MerlotColors.TextMuted, fontSize = 9.sp) } }
                        if (day.sunset.isNotEmpty()) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🌙", fontSize = 16.sp); Text(day.sunset, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium); Text("Sunset", color = MerlotColors.TextMuted, fontSize = 9.sp) } }
                    }
                    // Moon info
                    if (day.moonPhase.isNotEmpty()) {
                        Row(
                            modifier = Modifier.weight(1f).clip(barShape).background(MerlotColors.Surface.copy(alpha = 0.85f), barShape)
                                .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), barShape).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(moonPhaseEmoji(day.moonPhase), fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(day.moonPhase, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${day.moonIllumination}% illuminated", color = MerlotColors.TextMuted, fontSize = 10.sp)
                            }
                            Column {
                                if (day.moonrise.isNotEmpty()) Text("Rise ${day.moonrise}", color = MerlotColors.TextMuted, fontSize = 10.sp)
                                if (day.moonset.isNotEmpty()) Text("Set ${day.moonset}", color = MerlotColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Hourly temperature graph
            if (day.hourly.isNotEmpty()) {
                item { HourlyTempGraph(hourly = day.hourly, modifier = Modifier.fillMaxWidth()) }
            }

            // Full scrollable hourly cards (all 24 hours)
            if (day.hourly.isNotEmpty()) {
                item {
                    Column {
                        Text("HOURLY FORECAST", color = MerlotColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(day.hourly) { index, hour ->
                                HourlyCard(hour = hour, itemIndex = index, totalItems = day.hourly.size)
                            }
                        }
                    }
                }
            }

            // Back hint
            item {
                Text("Press BACK to return", color = MerlotColors.TextMuted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DetailMini(label: String, value: String, accentColor: Color, modifier: Modifier, shape: RoundedCornerShape) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(MerlotColors.Surface.copy(alpha = 0.85f), shape)
            .border(1.dp, MerlotColors.Border.copy(alpha = 0.5f), shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MerlotColors.TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun HourlyCard(hour: HourForecast, itemIndex: Int = 0, totalItems: Int = 24) {
    val shape = RoundedCornerShape(8.dp)
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(70.dp)
            .clip(shape)
            .background(
                if (isFocused) Color(0xFF252535) else MerlotColors.Surface2,
                shape
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MerlotColors.Accent else MerlotColors.Border,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (itemIndex > 0) false // let focus system handle
                            else false // bubble up
                        }
                        Key.DirectionRight -> {
                            if (itemIndex < totalItems - 1) false
                            else true // consume at end
                        }
                        else -> false
                    }
                } else false
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time
        Text(
            hour.displayTime,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Icon
        AsyncImage(
            model = hour.conditionIconUrl,
            contentDescription = hour.condition,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Temp
        Text(
            "${hour.tempF.toInt()}°",
            color = MerlotColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        // Rain chance
        if (hour.chanceOfRain > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${hour.chanceOfRain}%",
                color = Color(0xFF4FC3F7),
                fontSize = 10.sp
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

// ─── NWS Weather Alerts ─────────────────────────────────────────────────

private fun alertSeverityColor(severity: String): Color = when (severity) {
    "Extreme" -> Color(0xFFCC0000)
    "Severe" -> Color(0xFFDD2200)
    "Moderate" -> Color(0xFFCC6600)
    "Minor" -> Color(0xFFCCAA00)
    else -> Color(0xFF888888)
}

private fun alertSeverityTextColor(severity: String): Color = when (severity) {
    "Minor" -> Color(0xFF1A1A1A)
    else -> Color.White
}

@Composable
private fun AlertBanner(
    alerts: List<WeatherAlert>,
    onAlertClick: (Int) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val worstSeverity = alerts.firstOrNull()?.severity ?: "Unknown"
    val bgColor = alertSeverityColor(worstSeverity)
    val textColor = alertSeverityTextColor(worstSeverity)

    // Build scrolling text from all alerts
    val alertText = alerts.joinToString("  \u2022  ") { alert ->
        "\u26A0 ${alert.event}: ${alert.headline}"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = 0.9f))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MerlotColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onAlertClick(0)
                    true
                } else false
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "\u26A0 ${alerts.size} ALERT${if (alerts.size > 1) "S" else ""}",
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Scrolling alert text using marquee-like effect
            Box(modifier = Modifier.weight(1f)) {
                // Use basicMarquee on API 33+ or just scrolling text
                Text(
                    text = alertText,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isFocused) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ENTER for details",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AlertDetailCard(
    alert: WeatherAlert,
    onDismiss: () -> Unit
) {
    val bgColor = alertSeverityColor(alert.severity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MerlotColors.Surface2)
            .border(1.dp, bgColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Severity badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bgColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = alert.severity.uppercase(),
                    color = alertSeverityTextColor(alert.severity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = alert.event,
                color = MerlotColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sender
        Text(
            text = alert.senderName,
            color = MerlotColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Headline
        Text(
            text = alert.headline,
            color = MerlotColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = alert.description.trim(),
            color = MerlotColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        // Instruction (if available)
        if (!alert.instruction.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "PRECAUTIONS",
                        color = bgColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (alert.instruction ?: "").trim(),
                        color = MerlotColors.TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Area + timing info
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Areas", color = MerlotColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = alert.areaDesc,
                    color = MerlotColors.TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 3
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Press BACK to close",
            color = MerlotColors.TextMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
