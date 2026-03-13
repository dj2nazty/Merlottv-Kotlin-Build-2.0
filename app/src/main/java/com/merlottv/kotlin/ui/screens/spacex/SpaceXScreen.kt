@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.spacex

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.LaunchStatus
import com.merlottv.kotlin.domain.model.SpaceXLaunch
import com.merlottv.kotlin.ui.theme.MerlotColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun SpaceXScreen(
    viewModel: SpaceXViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Lazy load — only fetch data when this screen is first composed
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        // ─── Header ───
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "SpaceX Launches",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MerlotColors.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            // Refresh button
            var refreshFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier
                    .size(36.dp)
                    .onFocusChanged { refreshFocused = it.isFocused }
                    .focusable()
                    .then(
                        if (refreshFocused) Modifier.border(2.dp, MerlotColors.Accent, CircleShape)
                        else Modifier
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)
                        ) {
                            viewModel.refresh(); true
                        } else false
                    }
            ) {
                Icon(Icons.Default.Refresh, "Refresh", tint = MerlotColors.TextPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Countdown Hero Card ───
        uiState.nextLaunch?.let { nextLaunch ->
            CountdownHeroCard(
                launch = nextLaunch,
                countdownText = uiState.countdownText,
                onWatchLive = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        // ─── Sub-Tab Chips ───
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SpaceXTab.entries.toList()) { tab ->
                var chipFocused by remember { mutableStateOf(false) }
                FilterChip(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    label = {
                        Text(
                            tab.title,
                            fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MerlotColors.Accent.copy(alpha = 0.2f),
                        selectedLabelColor = MerlotColors.Accent,
                        containerColor = Color(0xFF2A2A2A),
                        labelColor = MerlotColors.TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (chipFocused) MerlotColors.Accent else Color.Transparent,
                        selectedBorderColor = if (chipFocused) MerlotColors.Accent else MerlotColors.Accent.copy(alpha = 0.5f),
                        borderWidth = if (chipFocused) 2.dp else 1.dp,
                        selectedBorderWidth = if (chipFocused) 2.dp else 1.dp,
                        enabled = true,
                        selected = uiState.selectedTab == tab
                    ),
                    modifier = Modifier
                        .onFocusChanged { chipFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) {
                                viewModel.selectTab(tab); true
                            } else false
                        }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ─── Content ───
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                }
            }
            uiState.error != null && (
                (uiState.selectedTab == SpaceXTab.Upcoming && uiState.upcomingLaunches.isEmpty()) ||
                (uiState.selectedTab == SpaceXTab.Past && uiState.pastLaunches.isEmpty())
            ) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "Unknown error", color = MerlotColors.TextMuted, fontSize = 16.sp)
                }
            }
            else -> {
                when (uiState.selectedTab) {
                    SpaceXTab.Upcoming -> LaunchList(
                        launches = uiState.upcomingLaunches,
                        onWatchLive = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                    SpaceXTab.Past -> LaunchList(
                        launches = uiState.pastLaunches,
                        onWatchLive = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

// ─── Countdown Hero Card ────────────────────────────────────────────────────

@Composable
private fun CountdownHeroCard(
    launch: SpaceXLaunch,
    countdownText: String,
    onWatchLive: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            // Background image
            if (launch.imageUrl != null) {
                AsyncImage(
                    model = launch.imageUrl,
                    contentDescription = launch.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                )
                // Gradient overlay for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            }

            // Content overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row — status badge + label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "NEXT LAUNCH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 2.sp
                    )
                    StatusBadge(launch.status)
                }

                // Center — countdown
                Column {
                    Text(
                        countdownText.ifEmpty { "Calculating..." },
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        launch.missionName ?: launch.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${launch.rocketName}  •  ${launch.padLocation ?: "Unknown Pad"}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bottom row — watch live button
                if (launch.videoUrls.isNotEmpty() || launch.webcastLive) {
                    var btnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val url = launch.videoUrls.firstOrNull() ?: "https://www.youtube.com/@SpaceX/live"
                            onWatchLive(url)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (launch.webcastLive) Color(0xFFE53935) else MerlotColors.Accent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .onFocusChanged { btnFocused = it.isFocused }
                            .focusable()
                            .then(
                                if (btnFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                ) {
                                    val url = launch.videoUrls.firstOrNull() ?: "https://www.youtube.com/@SpaceX/live"
                                    onWatchLive(url)
                                    true
                                } else false
                            }
                    ) {
                        Icon(Icons.Default.PlayArrow, "Watch", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (launch.webcastLive) "WATCH LIVE" else "Watch Stream",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Launch List ────────────────────────────────────────────────────────────

@Composable
private fun LaunchList(
    launches: List<SpaceXLaunch>,
    onWatchLive: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(launches, key = { it.id }) { launch ->
            LaunchCard(launch = launch, onWatchLive = onWatchLive)
        }
    }
}

@Composable
private fun LaunchCard(
    launch: SpaceXLaunch,
    onWatchLive: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF2A2A3E) else Color(0xFF1E1E2E)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    val url = launch.videoUrls.firstOrNull()
                    if (url != null) {
                        onWatchLive(url)
                        true
                    } else false
                } else false
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mission image
            if (launch.imageUrl != null) {
                AsyncImage(
                    model = launch.imageUrl,
                    contentDescription = launch.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
            }

            // Launch info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        launch.missionName ?: launch.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MerlotColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    StatusBadge(launch.status)
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    launch.rocketName,
                    fontSize = 13.sp,
                    color = MerlotColors.Accent,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(2.dp))

                // Date/time
                Text(
                    formatLaunchDate(launch.netUtc, launch.netEpochMs),
                    fontSize = 12.sp,
                    color = MerlotColors.TextMuted
                )

                // Location + orbit
                val locationOrbit = buildString {
                    launch.padLocation?.let { append(it) }
                    launch.orbit?.let {
                        if (isNotEmpty()) append("  •  ")
                        append(it)
                    }
                }
                if (locationOrbit.isNotEmpty()) {
                    Text(
                        locationOrbit,
                        fontSize = 12.sp,
                        color = MerlotColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Mission description (truncated)
                launch.missionDescription?.let { desc ->
                    if (desc.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            desc,
                            fontSize = 11.sp,
                            color = MerlotColors.TextMuted.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Watch button for launches with video
            if (launch.videoUrls.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                var btnFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { onWatchLive(launch.videoUrls.first()) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (launch.webcastLive) Color(0xFFE53935) else MerlotColors.Accent.copy(alpha = 0.2f)
                        )
                        .onFocusChanged { btnFocused = it.isFocused }
                        .focusable()
                        .then(
                            if (btnFocused) Modifier.border(2.dp, MerlotColors.Accent, CircleShape)
                            else Modifier
                        )
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) {
                                onWatchLive(launch.videoUrls.first()); true
                            } else false
                        }
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        "Watch",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─── Status Badge ───────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: LaunchStatus) {
    val (bgColor, textColor) = when (status) {
        LaunchStatus.Go -> Color(0xFF4CAF50) to Color.White
        LaunchStatus.InFlight -> Color(0xFFE53935) to Color.White
        LaunchStatus.Success -> Color(0xFF2E7D32) to Color.White
        LaunchStatus.Failure -> Color(0xFFC62828) to Color.White
        LaunchStatus.PartialFailure -> Color(0xFFE65100) to Color.White
        LaunchStatus.TBD -> Color(0xFFFFA726).copy(alpha = 0.8f) to Color.Black
        LaunchStatus.TBC -> Color(0xFFFFB74D).copy(alpha = 0.7f) to Color.Black
        LaunchStatus.Hold -> Color(0xFF757575) to Color.White
        LaunchStatus.Unknown -> Color(0xFF424242) to Color.White
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        modifier = Modifier.height(20.dp)
    ) {
        Text(
            status.display,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

private fun formatLaunchDate(utcString: String, epochMs: Long): String {
    return try {
        val date = Date(epochMs)
        val formatter = SimpleDateFormat("EEE, MMM d yyyy  •  h:mm a z", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        formatter.format(date)
    } catch (_: Exception) {
        utcString
    }
}
