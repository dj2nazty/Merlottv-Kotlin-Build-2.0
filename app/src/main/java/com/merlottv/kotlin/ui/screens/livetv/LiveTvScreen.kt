package com.merlottv.kotlin.ui.screens.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
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

    // Request focus for D-pad input — with safety delay
    LaunchedEffect(Unit) {
        delay(100)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
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
                        Key.DirectionLeft -> {
                            // D-pad Left → exit fullscreen and show categories
                            viewModel.exitFullscreen()
                            viewModel.showCategories()
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
                    useController = false
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

        // Failover indicator (top-center)
        AnimatedVisibility(
            visible = uiState.isFailingOver || uiState.failoverMessage.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.isFailingOver) {
                    CircularProgressIndicator(
                        color = MerlotColors.Accent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    text = uiState.failoverMessage,
                    color = MerlotColors.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
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

@Composable
private fun ChannelListView(
    viewModel: LiveTvViewModel,
    uiState: LiveTvUiState
) {
    val listState = rememberLazyListState()
    val categoryFocusRequester = remember { FocusRequester() }
    val channelFocusRequester = remember { FocusRequester() }

    // Request focus on categories when they become visible — with safety delay
    LaunchedEffect(uiState.showCategories) {
        if (uiState.showCategories) {
            delay(150)
            try { categoryFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Black)
    ) {
        // Full-screen player as background
        if (uiState.selectedChannel != null) {
            AndroidView(
                factory = { context ->
                    androidx.media3.ui.PlayerView(context).apply {
                        useController = false
                    }
                },
                update = { playerView ->
                    playerView.player = viewModel.player
                },
                modifier = Modifier.fillMaxSize()
            )
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

        // Category sidebar — slides in from left
        AnimatedVisibility(
            visible = uiState.showCategories,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(MerlotColors.Black.copy(alpha = 0.88f))
                    .padding(vertical = 8.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            if (uiState.selectedGroup != null) {
                                try { channelFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                            true
                        } else false
                    }
            ) {
                // Header
                Text(
                    text = "Categories",
                    color = MerlotColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Search
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchChanged(it) },
                    placeholder = { Text("Search...", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MerlotColors.TextMuted, modifier = Modifier.size(14.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MerlotColors.TextPrimary,
                        unfocusedTextColor = MerlotColors.TextPrimary,
                        cursorColor = MerlotColors.Accent,
                        focusedBorderColor = MerlotColors.Accent,
                        unfocusedBorderColor = MerlotColors.Border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Category list (vertical)
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MerlotColors.Accent, modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        item(key = "all_channels") {
                            CategoryItem(
                                label = "All Channels (${uiState.totalChannels})",
                                isSelected = uiState.selectedGroup == null,
                                onClick = { viewModel.onGroupSelected(null) },
                                focusRequester = categoryFocusRequester
                            )
                        }
                        items(uiState.groups, key = { it }) { group ->
                            CategoryItem(
                                label = group,
                                isSelected = uiState.selectedGroup == group,
                                onClick = { viewModel.onGroupSelected(group) }
                            )
                        }
                    }
                }
            }
        }

        // Channel list — semi-transparent overlay, shows when categories are hidden
        AnimatedVisibility(
            visible = !uiState.showCategories && !uiState.isLoading,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(MerlotColors.Black.copy(alpha = 0.75f))
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            viewModel.showCategories()
                            true
                        } else if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            viewModel.showCategories()
                            true
                        } else false
                    }
            ) {
                // Group name header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Black.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.selectedGroup ?: "All Channels",
                        color = MerlotColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${uiState.filteredChannels.size} ch",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp
                    )
                }

                // Channel list — use itemsIndexed for O(1) index access
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(uiState.filteredChannels, key = { _, ch -> ch.id }) { index, channel ->
                        ChannelItem(
                            channel = channel,
                            isSelected = channel.id == uiState.selectedChannel?.id,
                            isFavorite = uiState.favoriteIds.contains(channel.id),
                            onClick = { viewModel.onChannelSelected(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                            focusRequester = if (index == 0) channelFocusRequester else null
                        )
                    }
                }

                // Hint bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Black.copy(alpha = 0.9f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "\u25C0 Categories  \u25B6 Hide  \u25CF Select",
                        color = MerlotColors.TextMuted.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Bottom bar showing current channel info
        if (uiState.selectedChannel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ch = uiState.selectedChannel!!
                if (ch.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ch.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(ch.name, color = MerlotColors.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("CH ${ch.number} • ${ch.group}", color = MerlotColors.TextMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFF666666).copy(alpha = 0.4f)
                    isSelected -> MerlotColors.Accent.copy(alpha = 0.15f)
                    else -> MerlotColors.Transparent
                }
            )
            .then(
                if (isFocused) Modifier.border(1.5.dp, Color(0xFF888888), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = when {
                isFocused -> MerlotColors.White
                isSelected -> MerlotColors.Accent
                else -> MerlotColors.White
            },
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .background(
                when {
                    isFocused -> Color(0xFF666666).copy(alpha = 0.4f)
                    isSelected -> MerlotColors.Accent.copy(alpha = 0.12f)
                    else -> MerlotColors.Transparent
                }
            )
            .then(
                if (isFocused) Modifier.border(1.dp, Color(0xFF888888), RoundedCornerShape(4.dp))
                else Modifier
            )
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
