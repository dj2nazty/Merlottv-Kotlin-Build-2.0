package com.merlottv.kotlin.ui.screens.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isFullscreen && uiState.selectedChannel != null) {
        FullscreenPlayer(
            viewModel = viewModel,
            uiState = uiState
        )
    } else {
        ChannelListView(
            viewModel = viewModel,
            uiState = uiState
        )
    }
}

@Composable
private fun FullscreenPlayer(
    viewModel: LiveTvViewModel,
    uiState: LiveTvUiState
) {
    val focusRequester = remember { FocusRequester() }

    // Auto-hide overlay after 5 seconds
    LaunchedEffect(uiState.showOverlay) {
        if (uiState.showOverlay) {
            delay(5000)
            viewModel.hideOverlay()
        }
    }

    // Request focus for D-pad input
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            viewModel.channelUp()
                            true
                        }
                        Key.DirectionDown -> {
                            viewModel.channelDown()
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            viewModel.toggleOverlay()
                            true
                        }
                        Key.Back -> {
                            if (uiState.showOverlay) {
                                viewModel.hideOverlay()
                            } else {
                                viewModel.exitFullscreen()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Full-screen video player
        AndroidView(
            factory = { context ->
                androidx.media3.ui.PlayerView(context).apply {
                    useController = false  // We handle our own overlay
                    player = viewModel.player
                }
            },
            update = { playerView ->
                playerView.player = viewModel.player
            },
            modifier = Modifier.fillMaxSize()
        )

        // Transparent EPG Info Overlay (slides up from bottom)
        AnimatedVisibility(
            visible = uiState.showOverlay,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ChannelInfoOverlay(uiState = uiState)
        }

        // Channel switch indicator (top-right, brief)
        AnimatedVisibility(
            visible = uiState.showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            uiState.selectedChannel?.let { ch ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MerlotColors.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CH ${ch.number}",
                        color = MerlotColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelInfoOverlay(uiState: LiveTvUiState) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val channel = uiState.selectedChannel ?: return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MerlotColors.Transparent,
                        MerlotColors.Black.copy(alpha = 0.85f),
                        MerlotColors.Black.copy(alpha = 0.95f)
                    )
                )
            )
            .padding(top = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Channel name + number row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = MerlotColors.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "CH ${channel.number} • ${channel.group}",
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Resolution/Quality badge
                if (uiState.videoResolution.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MerlotColors.Accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Hd,
                            contentDescription = null,
                            tint = MerlotColors.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.videoResolution,
                            color = MerlotColors.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Show quality label
                        val qualityLabel = getQualityLabel(uiState.videoResolution)
                        if (qualityLabel.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = qualityLabel,
                                color = MerlotColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current program
            uiState.currentProgram?.let { program ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MerlotColors.AccentAlpha10)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = MerlotColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NOW",
                            color = MerlotColors.Accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = program.title,
                            color = MerlotColors.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (program.description.isNotEmpty()) {
                            Text(
                                text = program.description,
                                color = MerlotColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${timeFormat.format(Date(program.startTime))} - ${timeFormat.format(Date(program.endTime))}",
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp
                        )
                        // Progress bar
                        val now = System.currentTimeMillis()
                        val progress = ((now - program.startTime).toFloat() / (program.endTime - program.startTime).toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(80.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MerlotColors.Surface2)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(MerlotColors.Accent)
                            )
                        }
                    }
                }
            }

            // Next program
            uiState.nextProgram?.let { program ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MerlotColors.TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEXT: ",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${program.title} (${timeFormat.format(Date(program.startTime))})",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // D-pad hint
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\u25B2 \u25BC Channel  \u25CF Info  \u25C0 Back",
                    color = MerlotColors.TextMuted.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun getQualityLabel(resolution: String): String {
    val parts = resolution.split("x")
    if (parts.size != 2) return ""
    val height = parts[1].toIntOrNull() ?: return ""
    return when {
        height >= 2160 -> "4K UHD"
        height >= 1440 -> "2K QHD"
        height >= 1080 -> "Full HD"
        height >= 720 -> "HD"
        height >= 480 -> "SD"
        else -> "LQ"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelListView(
    viewModel: LiveTvViewModel,
    uiState: LiveTvUiState
) {
    val listState = rememberLazyListState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        // Channel sidebar
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MerlotColors.Surface)
                .border(width = 1.dp, color = MerlotColors.Border)
        ) {
            // Search
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchChanged(it) },
                placeholder = { Text("Search channels...", color = MerlotColors.TextMuted, fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MerlotColors.TextPrimary,
                    unfocusedTextColor = MerlotColors.TextPrimary,
                    cursorColor = MerlotColors.Accent,
                    focusedBorderColor = MerlotColors.Accent,
                    unfocusedBorderColor = MerlotColors.Border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                shape = RoundedCornerShape(8.dp)
            )

            // Group selector
            var groupExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = groupExpanded,
                onExpandedChange = { groupExpanded = it },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = uiState.selectedGroup ?: "All Groups (${uiState.totalChannels})",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = MerlotColors.TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MerlotColors.Accent,
                        unfocusedBorderColor = MerlotColors.Border
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(
                    expanded = groupExpanded,
                    onDismissRequest = { groupExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Groups (${uiState.totalChannels})", fontSize = 11.sp) },
                        onClick = { viewModel.onGroupSelected(null); groupExpanded = false }
                    )
                    uiState.groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group, fontSize = 11.sp) },
                            onClick = { viewModel.onGroupSelected(group); groupExpanded = false }
                        )
                    }
                }
            }

            // Channel list
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MerlotColors.Accent, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.filteredChannels) { channel ->
                        ChannelItem(
                            channel = channel,
                            isSelected = channel.id == uiState.selectedChannel?.id,
                            isFavorite = uiState.favoriteIds.contains(channel.id),
                            onClick = { viewModel.onChannelSelected(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) }
                        )
                    }
                }
            }
        }

        // Player preview area (when not fullscreen, shows idle or small preview)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MerlotColors.Black)
        ) {
            if (uiState.selectedChannel != null) {
                AndroidView(
                    factory = { context ->
                        androidx.media3.ui.PlayerView(context).apply {
                            useController = true
                        }
                    },
                    update = { playerView ->
                        playerView.player = viewModel.player
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Channel info at bottom
                uiState.selectedChannel?.let { ch ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2.copy(alpha = 0.9f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (ch.logoUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ch.logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ch.name, color = MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(ch.group, color = MerlotColors.TextMuted, fontSize = 10.sp)
                        }
                        Text(
                            "Press Enter for fullscreen",
                            color = MerlotColors.TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            } else {
                // Idle state
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = MerlotColors.TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("MERLOT TV", color = MerlotColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Select a channel from the list to start watching",
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .focusable()
            .background(if (isSelected) MerlotColors.AccentAlpha10 else MerlotColors.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MerlotColors.Surface2)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MerlotColors.Surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(2).uppercase(),
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isSelected) MerlotColors.Accent else MerlotColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.group.isNotEmpty()) {
                Text(
                    text = channel.group,
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MerlotColors.Accent else MerlotColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "${channel.number}",
            color = MerlotColors.TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.width(24.dp)
        )
    }
}
