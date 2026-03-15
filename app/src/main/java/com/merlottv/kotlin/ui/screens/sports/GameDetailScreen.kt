@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.*
import com.merlottv.kotlin.ui.components.MerlotChip
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun GameDetailScreen(
    league: String,
    eventId: String,
    onBack: () -> Unit = {},
    viewModel: GameDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(league, eventId) { viewModel.load(league, eventId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBack(); true
                } else false
            }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        // Back button
        var backFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(32.dp)
                .onFocusChanged { backFocused = it.isFocused }
                .focusable()
                .then(
                    if (backFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(6.dp))
                    else Modifier
                )
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = if (backFocused) MerlotColors.Accent else MerlotColors.TextMuted)
        }

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "Error", color = MerlotColors.TextMuted, fontSize = 14.sp)
                }
            }
            uiState.summary != null -> {
                val summary = uiState.summary!!
                Spacer(Modifier.height(8.dp))

                // ─── Game Header ───
                GameHeader(summary)
                Spacer(Modifier.height(10.dp))

                // ─── Sub-tab chips ───
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GameDetailTab.entries.toList()) { tab ->
                        GameDetailChip(
                            label = tab.title,
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                // ─── Content ───
                when (uiState.selectedTab) {
                    GameDetailTab.BoxScore -> BoxScoreContent(summary.boxScore, summary.homeTeam, summary.awayTeam)
                    GameDetailTab.PlayByPlay -> PlayByPlayContent(summary.plays)
                    GameDetailTab.TeamStats -> TeamStatsContent(summary.teamStats, summary.homeTeam, summary.awayTeam)
                }
            }
        }
    }
}

@Composable
private fun GameHeader(summary: SportGameSummary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MerlotColors.Surface)
            .padding(16.dp)
    ) {
        // Away team
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            if (summary.awayTeam.logo.isNotEmpty()) {
                AsyncImage(
                    model = summary.awayTeam.logo, contentDescription = null,
                    modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit
                )
            }
            Text(summary.awayTeam.abbreviation.ifEmpty { summary.awayTeam.name }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextPrimary)
            if (summary.awayTeam.record.isNotEmpty()) {
                Text(summary.awayTeam.record, fontSize = 10.sp, color = MerlotColors.TextMuted)
            }
        }

        // Score
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(summary.awayScore, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MerlotColors.TextPrimary)
                Text(" — ", fontSize = 24.sp, color = MerlotColors.TextMuted)
                Text(summary.homeScore, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MerlotColors.TextPrimary)
            }
            Text(summary.statusText, fontSize = 11.sp, color = MerlotColors.Accent)
            if (summary.venue.isNotEmpty()) {
                Text(summary.venue, fontSize = 9.sp, color = MerlotColors.TextMuted)
            }
        }

        // Home team
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            if (summary.homeTeam.logo.isNotEmpty()) {
                AsyncImage(
                    model = summary.homeTeam.logo, contentDescription = null,
                    modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit
                )
            }
            Text(summary.homeTeam.abbreviation.ifEmpty { summary.homeTeam.name }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextPrimary)
            if (summary.homeTeam.record.isNotEmpty()) {
                Text(summary.homeTeam.record, fontSize = 10.sp, color = MerlotColors.TextMuted)
            }
        }
    }
}

@Composable
private fun GameDetailChip(label: String, selected: Boolean, onClick: () -> Unit) {
    MerlotChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MerlotColors.Black else MerlotColors.TextPrimary)
        }
    )
}

// ─── Box Score ────────────────────────────────────────────────────────

@Composable
private fun BoxScoreContent(
    boxScore: List<SportBoxScoreSection>,
    homeTeam: SportTeamRef,
    awayTeam: SportTeamRef
) {
    if (boxScore.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Box score not available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        boxScore.forEach { section ->
            item(key = "box_${section.title}") {
                Text(section.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MerlotColors.Accent)
                Spacer(Modifier.height(4.dp))

                // Away team stats
                if (section.awayRows.isNotEmpty()) {
                    Text("${awayTeam.abbreviation}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextPrimary)
                    StatTable(headers = section.headers, rows = section.awayRows)
                    Spacer(Modifier.height(6.dp))
                }

                // Home team stats
                if (section.homeRows.isNotEmpty()) {
                    Text("${homeTeam.abbreviation}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextPrimary)
                    StatTable(headers = section.headers, rows = section.homeRows)
                }
            }
        }
    }
}

@Composable
private fun StatTable(headers: List<String>, rows: List<List<String>>) {
    Column {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MerlotColors.Surface2, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            headers.forEachIndexed { i, header ->
                Text(
                    header,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.TextMuted,
                    modifier = if (i == 0) Modifier.weight(1f) else Modifier.width(44.dp),
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Data rows
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                row.forEachIndexed { i, value ->
                    Text(
                        value,
                        fontSize = 10.sp,
                        color = MerlotColors.TextPrimary,
                        modifier = if (i == 0) Modifier.weight(1f) else Modifier.width(44.dp),
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Play-by-Play ─────────────────────────────────────────────────────

@Composable
private fun PlayByPlayContent(plays: List<SportPlay>) {
    if (plays.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Play-by-play not available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    var currentPeriod = -1
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(plays, key = { it.id.ifEmpty { plays.indexOf(it).toString() } }) { play ->
            // Period header
            if (play.period != currentPeriod) {
                currentPeriod = play.period
                Text(
                    play.periodText.ifEmpty { "Period ${play.period}" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.Accent,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (play.isScoring) MerlotColors.Accent.copy(alpha = 0.1f) else MerlotColors.Surface
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (play.clock.isNotEmpty()) {
                    Text(
                        play.clock,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MerlotColors.TextMuted,
                        modifier = Modifier.width(48.dp)
                    )
                }
                Text(
                    play.description,
                    fontSize = 11.sp,
                    color = if (play.isScoring) MerlotColors.Accent else MerlotColors.TextPrimary,
                    fontWeight = if (play.isScoring) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── Team Stats Comparison ────────────────────────────────────────────

@Composable
private fun TeamStatsContent(
    stats: List<SportStatComparison>,
    homeTeam: SportTeamRef,
    awayTeam: SportTeamRef
) {
    if (stats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Team stats not available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Header
        item(key = "stats_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MerlotColors.Surface2, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    awayTeam.abbreviation,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.TextPrimary,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Stat",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    homeTeam.abbreviation,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.TextPrimary,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        items(stats, key = { it.label }) { stat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    stat.awayValue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MerlotColors.TextPrimary,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    stat.label,
                    fontSize = 11.sp,
                    color = MerlotColors.TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    stat.homeValue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MerlotColors.TextPrimary,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
