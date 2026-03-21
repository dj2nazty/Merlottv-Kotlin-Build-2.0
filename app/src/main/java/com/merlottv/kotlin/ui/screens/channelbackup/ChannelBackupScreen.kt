package com.merlottv.kotlin.ui.screens.channelbackup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.BackupStream
import com.merlottv.kotlin.domain.model.BackupTvChannel
import com.merlottv.kotlin.ui.components.MerlotChip
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun ChannelBackupScreen(
    viewModel: ChannelBackupViewModel = hiltViewModel(),
    onStreamSelected: (streamUrl: String, channelName: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    // Track which channel's stream picker is open
    var selectedChannel by remember { mutableStateOf<BackupTvChannel?>(null) }

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
                "Channel Backup",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MerlotColors.TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            // Channel count badge
            if (uiState.filteredChannels.isNotEmpty()) {
                Text(
                    "${uiState.filteredChannels.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MerlotColors.Accent,
                    modifier = Modifier
                        .background(MerlotColors.AccentAlpha10, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
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

        // ─── Genre filter chips ───
        if (uiState.genres.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(uiState.genres) { index, genre ->
                    GenreChip(
                        label = genre,
                        selected = uiState.selectedGenre == genre,
                        onClick = { viewModel.selectGenre(genre) },
                        itemIndex = index
                    )
                }
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
                uiState.error != null && uiState.filteredChannels.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            uiState.error ?: "Error",
                            color = MerlotColors.TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        var retryFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent),
                            modifier = Modifier
                                .onFocusChanged { retryFocused = it.isFocused }
                                .focusable()
                                .then(
                                    if (retryFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                        ) {
                            Text("Retry", color = MerlotColors.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                uiState.filteredChannels.isEmpty() -> {
                    Text(
                        "No channels found",
                        color = MerlotColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ChannelGrid(
                        channels = uiState.filteredChannels,
                        onChannelClick = { channel -> selectedChannel = channel }
                    )
                }
            }
        }
    }

    // ─── Stream Picker Dialog ───
    selectedChannel?.let { channel ->
        StreamPickerDialog(
            channel = channel,
            onStreamSelected = { stream ->
                selectedChannel = null
                onStreamSelected(stream.url, channel.name)
            },
            onDismiss = { selectedChannel = null }
        )
    }
}

// ─── Stream Picker Dialog ─────────────────────────────────────────────

@Composable
private fun StreamPickerDialog(
    channel: BackupTvChannel,
    onStreamSelected: (BackupStream) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        onDismiss(); true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MerlotColors.Surface)
                    .border(1.dp, MerlotColors.Border, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                // Header with channel logo and name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (channel.logoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MerlotColors.TextPrimary
                        )
                        Text(
                            "${channel.streams.size} streams available",
                            fontSize = 12.sp,
                            color = MerlotColors.TextMuted
                        )
                    }
                    // Close button
                    var closeFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .onFocusChanged { closeFocused = it.isFocused }
                            .focusable()
                            .then(
                                if (closeFocused) Modifier.border(2.dp, MerlotColors.Accent, CircleShape)
                                else Modifier
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (closeFocused) MerlotColors.Accent else MerlotColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Divider
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MerlotColors.Border)
                )

                Spacer(Modifier.height(12.dp))

                // Stream list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(channel.streams.size) { index ->
                        val stream = channel.streams[index]
                        StreamItem(
                            stream = stream,
                            streamIndex = index + 1,
                            onClick = { onStreamSelected(stream) },
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstFocusRequester)
                            } else Modifier
                        )
                    }
                }
            }
        }
    }

    // Auto-focus first stream item when dialog opens
    LaunchedEffect(Unit) {
        try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
    }
}

// ─── Stream Item ──────────────────────────────────────────────────────

@Composable
private fun StreamItem(
    stream: BackupStream,
    streamIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) MerlotColors.Hover else MerlotColors.Surface2)
            .then(
                if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                else Modifier.border(1.dp, MerlotColors.Border, RoundedCornerShape(8.dp))
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
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Play icon
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = if (isFocused) MerlotColors.Accent else MerlotColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(10.dp))

        // Stream info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Stream $streamIndex",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary
            )
            // Show provider description if available
            val details = buildList {
                if (stream.description.isNotEmpty()) add(stream.description)
                // Show URL domain as hint
                val domain = try {
                    java.net.URI(stream.url).host ?: ""
                } catch (_: Exception) { "" }
                if (domain.isNotEmpty()) add(domain)
            }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    fontSize = 10.sp,
                    color = MerlotColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quality badge
        if (stream.name.isNotEmpty()) {
            Text(
                stream.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (stream.name.equals("HD", ignoreCase = true)) MerlotColors.Accent
                else MerlotColors.TextMuted,
                modifier = Modifier
                    .background(
                        if (stream.name.equals("HD", ignoreCase = true)) MerlotColors.AccentAlpha10
                        else MerlotColors.Surface,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ─── Channel Grid ─────────────────────────────────────────────────────

@Composable
private fun ChannelGrid(
    channels: List<BackupTvChannel>,
    onChannelClick: (BackupTvChannel) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelCard(channel = channel, onClick = { onChannelClick(channel) })
        }
    }
}

// ─── Channel Card ─────────────────────────────────────────────────────

@Composable
private fun ChannelCard(channel: BackupTvChannel, onClick: () -> Unit) {
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

        // Genre badge
        Text(
            channel.genre,
            fontSize = 9.sp,
            color = MerlotColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        // Stream count indicator
        if (channel.streams.size > 1) {
            Text(
                "${channel.streams.size} streams",
                fontSize = 8.sp,
                color = MerlotColors.Accent,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Genre Filter Chip ────────────────────────────────────────────────

@Composable
private fun GenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    itemIndex: Int = 0
) {
    Box(
        modifier = Modifier.onKeyEvent { event ->
            // Consume LEFT after focus move to prevent sidebar opening
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
