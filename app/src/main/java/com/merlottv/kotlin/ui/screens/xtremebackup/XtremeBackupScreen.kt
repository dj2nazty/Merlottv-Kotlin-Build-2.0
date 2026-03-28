package com.merlottv.kotlin.ui.screens.xtremebackup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.ui.components.MerlotChip
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun XtremeBackupScreen(
    viewModel: XtremeBackupViewModel = hiltViewModel(),
    onStreamSelected: (streamUrl: String, channelName: String, logoUrl: String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

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
                "Xtreme Backup",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MerlotColors.TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            if (uiState.channelCount > 0) {
                Text(
                    "${uiState.channelCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.Accent,
                    modifier = Modifier
                        .background(MerlotColors.AccentAlpha10, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))

            // Search field
            var searchFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text("Search channels...", fontSize = 12.sp, color = MerlotColors.TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MerlotColors.Accent,
                    unfocusedBorderColor = MerlotColors.Border,
                    focusedTextColor = MerlotColors.TextPrimary,
                    unfocusedTextColor = MerlotColors.TextPrimary
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(200.dp)
                    .height(40.dp)
                    .onFocusChanged { searchFocused = it.isFocused }
            )

            Spacer(Modifier.width(8.dp))

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

        // ─── Server picker chips ───
        if (uiState.servers.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(uiState.servers) { index, server ->
                    ServerChip(
                        label = server.name,
                        selected = uiState.selectedServerIndex == index,
                        onClick = { viewModel.selectServer(index) },
                        itemIndex = index
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ─── Group filter chips ───
        if (uiState.groups.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(uiState.groups) { index, group ->
                    GroupChip(
                        label = group,
                        selected = uiState.selectedGroup == group,
                        onClick = { viewModel.selectGroup(group) },
                        itemIndex = index
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ─── Content Area ───
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.servers.isEmpty() && uiState.hasLoadedOnce -> {
                    // No servers configured
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            "No Xtreme Backup servers configured",
                            color = MerlotColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "Go to Settings > Sources > Xtreme Backup Servers\nto add your Xtream Codes server credentials.",
                            color = MerlotColors.TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
                uiState.isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(
                            color = MerlotColors.Accent,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        val serverName = uiState.servers.getOrNull(uiState.selectedServerIndex)?.name ?: ""
                        Text(
                            "Loading channels from $serverName...",
                            color = MerlotColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
                uiState.error != null && uiState.filteredChannels.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            uiState.error ?: "Error",
                            color = MerlotColors.TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        var retryFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (retryFocused) MerlotColors.Accent else MerlotColors.Surface2,
                                contentColor = if (retryFocused) MerlotColors.Black else MerlotColors.TextPrimary
                            ),
                            modifier = Modifier
                                .onFocusChanged { retryFocused = it.isFocused }
                                .focusable()
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                uiState.filteredChannels.isEmpty() && uiState.hasLoadedOnce -> {
                    Text(
                        if (uiState.searchQuery.isNotBlank()) "No channels match \"${uiState.searchQuery}\""
                        else "No channels found",
                        color = MerlotColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ChannelGrid(
                        channels = uiState.filteredChannels,
                        onChannelClick = { channel ->
                            onStreamSelected(channel.streamUrl, channel.name, channel.logoUrl)
                        }
                    )
                }
            }
        }
    }
}

// ─── Channel Grid ─────────────────────────────────────────────────────

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(channels, key = { it.id + it.streamUrl }) { channel ->
            ChannelCard(channel = channel, onClick = { onChannelClick(channel) })
        }
    }
}

// ─── Channel Card ─────────────────────────────────────────────────────

@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

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
            .padding(12.dp)
    ) {
        // Channel logo
        if (channel.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            // Fallback: show initials
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    channel.name.take(2).uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.Accent
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Channel name
        Text(
            channel.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Group badge
        if (channel.group.isNotEmpty()) {
            Text(
                channel.group,
                fontSize = 9.sp,
                color = MerlotColors.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play icon hint on focus
        if (isFocused) {
            Spacer(Modifier.height(4.dp))
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MerlotColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─── Server Chip ──────────────────────────────────────────────────────

@Composable
private fun ServerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    itemIndex: Int = 0
) {
    Box(
        modifier = Modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionLeft && itemIndex > 0
            ) true else false
        }
    ) {
        MerlotChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MerlotColors.Black else MerlotColors.TextPrimary
                )
            }
        )
    }
}

// ─── Group Filter Chip ────────────────────────────────────────────────

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    itemIndex: Int = 0
) {
    Box(
        modifier = Modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionLeft && itemIndex > 0
            ) true else false
        }
    ) {
        MerlotChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MerlotColors.Black else MerlotColors.TextPrimary
                )
            }
        )
    }
}
