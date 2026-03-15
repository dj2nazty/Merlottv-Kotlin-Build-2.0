@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.sports

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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.merlottv.kotlin.ui.components.MerlotChip
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun TeamDetailScreen(
    league: String,
    teamId: String,
    onBack: () -> Unit = {},
    viewModel: TeamDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(league, teamId) { viewModel.load(league, teamId) }

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
            uiState.error != null && uiState.teamInfo == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "Error", color = MerlotColors.TextMuted, fontSize = 14.sp)
                }
            }
            else -> {
                val teamInfo = uiState.teamInfo
                Spacer(Modifier.height(8.dp))

                // ─── Team Header ───
                if (teamInfo != null) {
                    TeamHeader(teamInfo)
                    Spacer(Modifier.height(10.dp))
                }

                // ─── Sub-tab chips ───
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TeamDetailTab.entries.toList()) { tab ->
                        TeamChip(
                            label = tab.title,
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                // ─── Content ───
                when (uiState.selectedTab) {
                    TeamDetailTab.Roster -> RosterContent(uiState.roster)
                    TeamDetailTab.Schedule -> ScheduleContent(uiState.schedule)
                }
            }
        }
    }
}

@Composable
private fun TeamHeader(team: SportTeamInfo) {
    val teamColor = try {
        if (team.color.isNotEmpty()) Color(android.graphics.Color.parseColor("#${team.color}"))
        else MerlotColors.Accent
    } catch (_: Exception) { MerlotColors.Accent }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MerlotColors.Surface)
            .padding(16.dp)
    ) {
        // Team color bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(teamColor)
        )
        Spacer(Modifier.width(12.dp))

        if (team.logo.isNotEmpty()) {
            AsyncImage(
                model = team.logo,
                contentDescription = team.name,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                team.displayName.ifEmpty { team.name },
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MerlotColors.TextPrimary
            )
            if (team.record.isNotEmpty()) {
                Text(team.record, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MerlotColors.TextMuted)
            }
            if (team.standingSummary.isNotEmpty()) {
                Text(team.standingSummary, fontSize = 11.sp, color = MerlotColors.Accent)
            }
        }

        // Venue info
        if (team.venue.name.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End) {
                Text(team.venue.name, fontSize = 11.sp, color = MerlotColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                val location = listOfNotNull(
                    team.venue.city.ifEmpty { null },
                    team.venue.state.ifEmpty { null }
                ).joinToString(", ")
                if (location.isNotEmpty()) {
                    Text(location, fontSize = 9.sp, color = MerlotColors.TextMuted)
                }
                if (team.venue.capacity > 0) {
                    Text("Capacity: ${String.format("%,d", team.venue.capacity)}", fontSize = 9.sp, color = MerlotColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun TeamChip(label: String, selected: Boolean, onClick: () -> Unit) {
    MerlotChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MerlotColors.Black else MerlotColors.TextPrimary)
        }
    )
}

// ─── Roster ───────────────────────────────────────────────────────────

@Composable
private fun RosterContent(roster: List<SportPlayer>) {
    if (roster.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Roster not available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Header
        item(key = "roster_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MerlotColors.Surface2, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("#", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(32.dp))
                Text("Player", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.weight(1f))
                Text("POS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Text("AGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                Text("HT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                Text("WT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MerlotColors.TextMuted, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
            }
        }

        items(roster, key = { it.id }) { player ->
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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(player.jersey, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MerlotColors.Accent, modifier = Modifier.width(32.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (player.headshot.isNotEmpty()) {
                        AsyncImage(
                            model = player.headshot,
                            contentDescription = player.name,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        player.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MerlotColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(player.position, fontSize = 11.sp, color = MerlotColors.TextMuted, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Text(if (player.age > 0) "${player.age}" else "-", fontSize = 11.sp, color = MerlotColors.TextMuted, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                Text(player.height.ifEmpty { "-" }, fontSize = 11.sp, color = MerlotColors.TextMuted, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                Text(player.weight.ifEmpty { "-" }, fontSize = 11.sp, color = MerlotColors.TextMuted, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

// ─── Schedule ─────────────────────────────────────────────────────────

@Composable
private fun ScheduleContent(schedule: List<SportScheduleEvent>) {
    if (schedule.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Schedule not available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(schedule, key = { it.eventId.ifEmpty { schedule.indexOf(it).toString() } }) { event ->
            ScheduleRow(event)
        }
    }
}

@Composable
private fun ScheduleRow(event: SportScheduleEvent) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) MerlotColors.Hover else MerlotColors.Surface)
            .then(
                if (isFocused) Modifier.border(1.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                else Modifier.border(1.dp, MerlotColors.Border, RoundedCornerShape(8.dp))
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Date
        val dateDisplay = try {
            if (event.date.length >= 10) event.date.substring(0, 10) else event.date
        } catch (_: Exception) { event.date }

        Text(
            dateDisplay,
            fontSize = 10.sp,
            color = MerlotColors.TextMuted,
            modifier = Modifier.width(80.dp)
        )

        // Home/Away indicator
        Text(
            if (event.homeAway == "home") "vs" else "@",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.Accent,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )

        // Opponent
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (event.opponent.logo.isNotEmpty()) {
                AsyncImage(
                    model = event.opponent.logo,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                event.opponent.displayName.ifEmpty { event.opponent.name },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MerlotColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Score or broadcast
        if (event.score.isNotEmpty()) {
            val isWin = event.score.startsWith("W")
            Text(
                event.score,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWin) MerlotColors.Success else MerlotColors.Danger,
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.End
            )
        } else if (event.broadcasts.isNotEmpty()) {
            Text(
                event.broadcasts.first(),
                fontSize = 10.sp,
                color = MerlotColors.Accent,
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.End
            )
        }
    }
}
