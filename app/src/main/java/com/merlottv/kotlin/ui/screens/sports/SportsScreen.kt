@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.*
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun SportsScreen(
    viewModel: SportsViewModel = hiltViewModel(),
    onNavigateToGame: (league: String, eventId: String) -> Unit = { _, _ -> },
    onNavigateToTeam: (league: String, teamId: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

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
                "Sports",
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
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = if (refreshFocused) MerlotColors.Accent else MerlotColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Sub-tab chips: Scores | Standings | Teams ───
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SportsTab.entries.toList()) { tab ->
                SportChip(
                    label = tab.title,
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── League filter chips ───
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SportsLeague.entries.toList()) { league ->
                SportChip(
                    label = league.displayName,
                    selected = uiState.selectedLeague == league,
                    onClick = { viewModel.selectLeague(league) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ─── Content Area ───
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        color = MerlotColors.Accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null && uiState.scores.isEmpty() && uiState.standings.isEmpty() && uiState.teams.isEmpty() -> {
                    Text(
                        uiState.error ?: "Error",
                        color = MerlotColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    when (uiState.selectedTab) {
                        SportsTab.Scores -> ScoresList(
                            scores = uiState.scores,
                            onGameClick = { score ->
                                onNavigateToGame(score.league.name.lowercase(), score.eventId)
                            }
                        )
                        SportsTab.Standings -> StandingsList(
                            standings = uiState.standings,
                            onTeamClick = { teamId ->
                                onNavigateToTeam(uiState.selectedLeague.name.lowercase(), teamId)
                            }
                        )
                        SportsTab.Teams -> TeamsGrid(
                            teams = uiState.teams,
                            onTeamClick = { team ->
                                onNavigateToTeam(uiState.selectedLeague.name.lowercase(), team.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── Reusable Filter Chip ─────────────────────────────────────────────

@Composable
private fun SportChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (isFocused) Color(0xFF555555) else MerlotColors.Surface2,
            labelColor = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
            iconColor = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
            selectedContainerColor = MerlotColors.Accent,
            selectedLabelColor = MerlotColors.Black,
            selectedLeadingIconColor = MerlotColors.Black,
            selectedTrailingIconColor = MerlotColors.Black
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = if (isFocused) MerlotColors.Accent else MerlotColors.Border,
            selectedBorderColor = MerlotColors.Accent,
            borderWidth = if (isFocused) 2.dp else 1.dp,
            selectedBorderWidth = 1.dp,
            enabled = true,
            selected = selected
        ),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
    )
}

// ─── Scores List ──────────────────────────────────────────────────────

@Composable
private fun ScoresList(
    scores: List<SportScore>,
    onGameClick: (SportScore) -> Unit
) {
    if (scores.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No games today", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(scores, key = { it.eventId }) { score ->
            ScoreCard(score = score, onClick = { onGameClick(score) })
        }
    }
}

@Composable
private fun ScoreCard(score: SportScore, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) MerlotColors.Hover else MerlotColors.Surface)
            .then(
                if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, MerlotColors.Border, RoundedCornerShape(10.dp))
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Live indicator
        if (score.isLive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MerlotColors.Success)
            )
            Spacer(Modifier.width(8.dp))
        }

        // Away team
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(80.dp)
        ) {
            if (score.awayTeam.logo.isNotEmpty()) {
                AsyncImage(
                    model = score.awayTeam.logo,
                    contentDescription = score.awayTeam.name,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                score.awayTeam.abbreviation.ifEmpty { score.awayTeam.name },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MerlotColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (score.awayTeam.record.isNotEmpty()) {
                Text(score.awayTeam.record, fontSize = 9.sp, color = MerlotColors.TextMuted)
            }
        }

        // Score
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    score.awayScore,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (score.awayTeam.isWinner) MerlotColors.Accent else MerlotColors.TextPrimary
                )
                Text(
                    " — ",
                    fontSize = 18.sp,
                    color = MerlotColors.TextMuted
                )
                Text(
                    score.homeScore,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (score.homeTeam.isWinner) MerlotColors.Accent else MerlotColors.TextPrimary
                )
            }
            // Status text
            Text(
                score.statusText,
                fontSize = 10.sp,
                fontWeight = if (score.isLive) FontWeight.Bold else FontWeight.Normal,
                color = if (score.isLive) MerlotColors.Success else MerlotColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }

        // Home team
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(80.dp)
        ) {
            if (score.homeTeam.logo.isNotEmpty()) {
                AsyncImage(
                    model = score.homeTeam.logo,
                    contentDescription = score.homeTeam.name,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                score.homeTeam.abbreviation.ifEmpty { score.homeTeam.name },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MerlotColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (score.homeTeam.record.isNotEmpty()) {
                Text(score.homeTeam.record, fontSize = 9.sp, color = MerlotColors.TextMuted)
            }
        }

        // Broadcast info
        if (score.broadcasts.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(60.dp)
            ) {
                Text(
                    score.broadcasts.firstOrNull() ?: "",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MerlotColors.Accent,
                    maxLines = 1
                )
                if (score.venue.isNotEmpty()) {
                    Text(
                        score.venue,
                        fontSize = 8.sp,
                        color = MerlotColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Standings List ───────────────────────────────────────────────────

@Composable
private fun StandingsList(
    standings: List<SportConference>,
    onTeamClick: (teamId: String) -> Unit
) {
    if (standings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No standings available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        standings.forEach { conference ->
            conference.divisions.forEach { division ->
                // Division header
                item(key = "header_${conference.id}_${division.name}") {
                    Text(
                        division.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MerlotColors.Accent,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    // Table header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Team", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.weight(1f))
                        Text("W", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                        Text("L", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                        Text("PCT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                        Text("STRK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                    }
                }

                // Team rows
                items(division.entries, key = { "standings_${it.team.id}" }) { entry ->
                    StandingsRow(entry = entry, onClick = { onTeamClick(entry.team.id) })
                }
            }
        }
    }
}

@Composable
private fun StandingsRow(entry: SportStandingsEntry, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFocused) MerlotColors.Hover else MerlotColors.Surface)
            .then(
                if (isFocused) Modifier.border(1.dp, MerlotColors.Accent, RoundedCornerShape(2.dp))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Team logo + name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (entry.team.logo.isNotEmpty()) {
                AsyncImage(
                    model = entry.team.logo,
                    contentDescription = entry.team.name,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                entry.team.displayName.ifEmpty { entry.team.name },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MerlotColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("${entry.wins}", fontSize = 12.sp, color = MerlotColors.TextPrimary, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        Text("${entry.losses}", fontSize = 12.sp, color = MerlotColors.TextPrimary, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        Text(entry.winPct, fontSize = 12.sp, color = MerlotColors.TextPrimary, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        Text(entry.streak, fontSize = 12.sp, color = MerlotColors.TextMuted, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
    }
}

// ─── Teams Grid ───────────────────────────────────────────────────────

@Composable
private fun TeamsGrid(
    teams: List<SportTeamInfo>,
    onTeamClick: (SportTeamInfo) -> Unit
) {
    if (teams.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No teams found", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(teams, key = { it.id }) { team ->
            TeamCard(team = team, onClick = { onTeamClick(team) })
        }
    }
}

@Composable
private fun TeamCard(team: SportTeamInfo, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val teamColor = try {
        if (team.color.isNotEmpty()) Color(android.graphics.Color.parseColor("#${team.color}"))
        else MerlotColors.Surface
    } catch (_: Exception) { MerlotColors.Surface }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) MerlotColors.Hover else MerlotColors.Surface)
            .then(
                if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, MerlotColors.Border, RoundedCornerShape(10.dp))
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .padding(16.dp)
    ) {
        // Team color accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(teamColor)
        )
        Spacer(Modifier.height(8.dp))

        if (team.logo.isNotEmpty()) {
            AsyncImage(
                model = team.logo,
                contentDescription = team.name,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(6.dp))
        }

        Text(
            team.displayName.ifEmpty { team.name },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (team.record.isNotEmpty()) {
            Text(
                team.record,
                fontSize = 10.sp,
                color = MerlotColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }

        if (team.standingSummary.isNotEmpty()) {
            Text(
                team.standingSummary,
                fontSize = 9.sp,
                color = MerlotColors.Accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
