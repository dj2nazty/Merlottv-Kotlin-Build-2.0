package com.merlottv.kotlin.ui.screens.tvguide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PIXELS_PER_MINUTE = 3f
private const val CHANNEL_COL_WIDTH = 160
private const val ROW_HEIGHT = 56
private const val TIMELINE_HEIGHT = 32

private fun roundToHalfHour(timeMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMs
    val min = cal.get(Calendar.MINUTE)
    cal.set(Calendar.MINUTE, if (min < 30) 0 else 30)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
fun TvGuideScreen(
    viewModel: TvGuideViewModel = hiltViewModel(),
    onChannelSelected: (Channel) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val channelListState = rememberLazyListState()
    val channelPanelListState = rememberLazyListState()
    val programListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Update time every 60 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val timelineStartMs = remember(currentTimeMs) {
        roundToHalfHour(currentTimeMs - 60 * 60_000L)
    }
    val timelineEndMs = remember(timelineStartMs) {
        timelineStartMs + 6 * 60 * 60_000L
    }

    // Auto-scroll timeline to current time on first load
    LaunchedEffect(uiState.guideChannels) {
        if (uiState.guideChannels.isNotEmpty()) {
            val nowOffsetPx = ((currentTimeMs - timelineStartMs) / 60_000f * PIXELS_PER_MINUTE).toInt()
            val scrollTarget = (nowOffsetPx - 200).coerceAtLeast(0)
            scrollState.scrollTo(scrollTarget)
        }
    }

    // Sync LazyColumn scroll when selectedIndex changes (ViewModel-driven)
    val selectedIndex = uiState.selectedIndex
    LaunchedEffect(selectedIndex) {
        if (uiState.guideChannels.isNotEmpty()) {
            channelListState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
            programListState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
            // Also sync channel panel if visible
            if (uiState.showChannelPanel) {
                channelPanelListState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
            }
        }
    }

    // Scroll channel panel to selected channel when it opens
    LaunchedEffect(uiState.showChannelPanel) {
        if (uiState.showChannelPanel && uiState.guideChannels.isNotEmpty()) {
            channelPanelListState.scrollToItem((selectedIndex - 3).coerceAtLeast(0))
        }
    }

    // Scroll timeline when ViewModel requests (driven by LEFT/RIGHT D-pad)
    LaunchedEffect(uiState.scrollRequest) {
        if (uiState.scrollRequest != 0) {
            val delta = if (uiState.scrollRequest > 0) 200 else -200
            scrollState.animateScrollTo((scrollState.value + delta).coerceAtLeast(0))
        }
    }

    // Report timeline scroll position to ViewModel
    LaunchedEffect(scrollState.value) {
        viewModel.updateTimelineAtStart(scrollState.value <= 10)
    }

    // Request focus on load
    LaunchedEffect(Unit) {
        delay(200)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Guard against rapid Left presses opening both panels at once
    var channelPanelOpenTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Category sidebar is open — let it handle its own keys
                if (uiState.showCategoryPicker) {
                    false
                }
                // Channel panel is open — handle its navigation
                else if (uiState.showChannelPanel) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            viewModel.navigate(1)
                            true
                        }
                        Key.DirectionUp -> {
                            viewModel.navigate(-1)
                            true
                        }
                        Key.DirectionLeft -> {
                            // Step 2: open category sidebar (only if panel has been open > 300ms)
                            if (System.currentTimeMillis() - channelPanelOpenTime > 300) {
                                viewModel.showCategoryPicker()
                            }
                            true
                        }
                        Key.DirectionRight, Key.Back -> {
                            // Go back to EPG grid
                            viewModel.hideChannelPanel()
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            val channel = viewModel.getSelectedChannel()
                            if (channel != null) {
                                viewModel.saveChannelForPlayback(channel)
                                onChannelSelected(channel)
                            }
                            true
                        }
                        else -> false
                    }
                }
                // Default EPG grid navigation
                else {
                    when (event.key) {
                        Key.DirectionDown -> {
                            viewModel.navigate(1)
                            true
                        }
                        Key.DirectionUp -> {
                            viewModel.navigate(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.scrollTimeline(1)
                            true
                        }
                        Key.DirectionLeft -> {
                            if (uiState.timelineAtStart) {
                                // Step 1: open channel panel
                                channelPanelOpenTime = System.currentTimeMillis()
                                viewModel.showChannelPanel()
                            } else {
                                viewModel.scrollTimeline(-1)
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            val channel = viewModel.getSelectedChannel()
                            if (channel != null) {
                                viewModel.saveChannelForPlayback(channel)
                                onChannelSelected(channel)
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        when {
            uiState.isLoading -> {
                GuideLoadingState(uiState.loadingMessage, Modifier.align(Alignment.Center))
            }
            uiState.error != null -> {
                GuideErrorState(uiState.error, viewModel, Modifier.align(Alignment.Center))
            }
            uiState.guideChannels.isEmpty() -> {
                GuideEmptyState(viewModel, Modifier.align(Alignment.Center))
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with category selector
                    GuideHeader(
                        uiState = uiState,
                        onCategoryClick = { viewModel.toggleCategoryPicker() }
                    )

                    // EPG Grid
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left column — channel names with highlight
                        Column(
                            modifier = Modifier
                                .width(CHANNEL_COL_WIDTH.dp)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(TIMELINE_HEIGHT.dp)
                                    .fillMaxWidth()
                                    .background(MerlotColors.Surface)
                            )
                            LazyColumn(
                                state = channelListState,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                itemsIndexed(
                                    uiState.guideChannels,
                                    key = { index, ch -> "${index}_${ch.id}" }
                                ) { index, channel ->
                                    GuideChannelCell(
                                        channel = channel,
                                        epgChannel = uiState.epgChannels.getOrNull(index),
                                        isHighlighted = index == selectedIndex,
                                        onClick = {
                                            viewModel.saveChannelForPlayback(channel)
                                            onChannelSelected(channel)
                                        }
                                    )
                                }
                            }
                        }

                        // Right column — timeline + program rows
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Column {
                                GuideTimelineHeader(scrollState, timelineStartMs, timelineEndMs)
                                Box(modifier = Modifier.weight(1f)) {
                                    LazyColumn(
                                        state = programListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(scrollState)
                                    ) {
                                        itemsIndexed(
                                            uiState.epgChannels,
                                            key = { index, ch -> "${index}_${ch.id}" }
                                        ) { index, channel ->
                                            GuideChannelRow(
                                                channel = channel,
                                                currentTimeMs = currentTimeMs,
                                                timelineStartMs = timelineStartMs,
                                                isHighlighted = index == selectedIndex,
                                                onProgramSelected = { viewModel.selectProgram(it) }
                                            )
                                        }
                                    }
                                    GuideNowIndicator(currentTimeMs, timelineStartMs, scrollState)
                                }
                            }
                        }
                    }
                }

                // Step 1: Channel panel — slides in from left, shows channels in current category
                GuideChannelPanel(
                    visible = uiState.showChannelPanel,
                    channels = uiState.guideChannels,
                    epgChannels = uiState.epgChannels,
                    selectedIndex = selectedIndex,
                    categoryName = uiState.selectedGroup ?: "All Channels",
                    onChannelClick = { channel ->
                        viewModel.saveChannelForPlayback(channel)
                        onChannelSelected(channel)
                    },
                    channelListState = channelPanelListState
                )

                // Step 2: Category sidebar — slides in over channel panel
                GuideCategorySidebar(
                    visible = uiState.showCategoryPicker,
                    groups = uiState.groups,
                    selectedGroup = uiState.selectedGroup,
                    onGroupSelected = { group ->
                        viewModel.setGroup(group)
                        viewModel.hideCategoryPicker()
                        // Keep channel panel open to show filtered channels
                    },
                    onDismiss = {
                        viewModel.hideCategoryPicker()
                        // Go back to channel panel
                    }
                )

                // Program detail dialog
                uiState.selectedProgram?.let { program ->
                    GuideProgramDetailDialog(program) { viewModel.selectProgram(null) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Header
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideHeader(
    uiState: TvGuideUiState,
    onCategoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("TV Guide", color = MerlotColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "${uiState.guideChannels.size} channels",
            color = MerlotColors.TextMuted,
            fontSize = 12.sp
        )

        // Category selector button
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MerlotColors.Surface2)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(6.dp))
                .clickable { onCategoryClick() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "\u25BE ${uiState.selectedGroup ?: "All Channels"}",
                color = MerlotColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (uiState.isSyncing) {
            Spacer(modifier = Modifier.width(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = MerlotColors.Accent,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Updating...", color = MerlotColors.TextMuted, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Press OK to watch",
            color = MerlotColors.TextMuted,
            fontSize = 10.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Timeline Header
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideTimelineHeader(scrollState: ScrollState, startMs: Long, endMs: Long) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .height(TIMELINE_HEIGHT.dp)
            .horizontalScroll(scrollState)
            .background(MerlotColors.Surface)
    ) {
        var time = startMs
        while (time < endMs) {
            val blockWidthDp = (30 * PIXELS_PER_MINUTE).toInt()
            Box(
                modifier = Modifier
                    .width(blockWidthDp.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, MerlotColors.Border)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = timeFormat.format(Date(time)),
                    color = MerlotColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            time += 30 * 60_000L
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Now Indicator (red vertical line)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideNowIndicator(currentTimeMs: Long, timelineStartMs: Long, scrollState: ScrollState) {
    val density = LocalDensity.current
    val nowOffsetPx = with(density) {
        ((currentTimeMs - timelineStartMs) / 60_000f * PIXELS_PER_MINUTE).dp.roundToPx()
    }
    val visibleOffset = nowOffsetPx - scrollState.value
    if (visibleOffset > 0) {
        Box(
            modifier = Modifier
                .offset { IntOffset(visibleOffset, 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(MerlotColors.Accent)
                .zIndex(10f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Channel Cell (left column — with D-pad highlight)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideChannelCell(
    channel: Channel,
    epgChannel: EpgChannel?,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val logoUrl = channel.logoUrl.ifEmpty { epgChannel?.icon ?: "" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .border(
                width = if (isHighlighted) 2.dp else 0.5.dp,
                color = if (isHighlighted) MerlotColors.Accent else MerlotColors.Border
            )
            .background(
                if (isHighlighted) MerlotColors.Accent.copy(alpha = 0.25f)
                else MerlotColors.Surface
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (logoUrl.isNotEmpty()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = channel.name,
            color = if (isHighlighted) MerlotColors.White else MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Channel Program Row (right column — with D-pad highlight border)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideChannelRow(
    channel: EpgChannel,
    currentTimeMs: Long,
    timelineStartMs: Long,
    isHighlighted: Boolean = false,
    onProgramSelected: (EpgEntry) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .height(ROW_HEIGHT.dp)
            .then(
                if (isHighlighted) Modifier.border(2.dp, MerlotColors.Accent)
                else Modifier
            )
    ) {
        val programs = channel.programs
            .filter { it.endTime > timelineStartMs }
            .sortedBy { it.startTime }

        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, MerlotColors.Border)
                    .background(MerlotColors.Surface2)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("No program info", color = MerlotColors.TextMuted, fontSize = 10.sp)
            }
        } else {
            programs.forEach { program ->
                val durationMin = ((program.endTime - program.startTime) / 60_000L).coerceAtLeast(1)
                val widthDp = (durationMin * PIXELS_PER_MINUTE).coerceIn(80f, 600f).toInt()
                val isAiring = program.startTime <= currentTimeMs && program.endTime >= currentTimeMs
                val isPast = program.endTime < currentTimeMs

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .fillMaxHeight()
                        .alpha(if (isPast) 0.4f else 1f)
                        .border(0.5.dp, MerlotColors.Border)
                        .background(
                            when {
                                isAiring -> MerlotColors.AccentAlpha10
                                else -> MerlotColors.Surface2
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clickable { onProgramSelected(program) }
                ) {
                    Column {
                        Text(
                            text = program.title,
                            color = if (isAiring) MerlotColors.Accent else MerlotColors.TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = timeFormat.format(Date(program.startTime)) + " - " + timeFormat.format(Date(program.endTime)),
                            color = MerlotColors.TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Channel Panel — Step 1 slide-in (channels in current category)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideChannelPanel(
    visible: Boolean,
    channels: List<Channel>,
    epgChannels: List<EpgChannel>,
    selectedIndex: Int,
    categoryName: String,
    onChannelClick: (Channel) -> Unit,
    channelListState: LazyListState
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = Modifier
            .zIndex(5f)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(MerlotColors.Black.copy(alpha = 0.92f))
        ) {
            // Header — shows current category
            Text(
                text = categoryName,
                color = MerlotColors.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = "${channels.size} channels",
                color = MerlotColors.TextPrimary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(MerlotColors.Border)
            )

            // Channel list
            LazyColumn(
                state = channelListState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    channels,
                    key = { index, ch -> "panel_${index}_${ch.id}" }
                ) { index, channel ->
                    val logoUrl = channel.logoUrl.ifEmpty {
                        epgChannels.getOrNull(index)?.icon ?: ""
                    }
                    val isHighlighted = index == selectedIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(
                                width = if (isHighlighted) 2.dp else 0.dp,
                                color = if (isHighlighted) MerlotColors.Accent else Color.Transparent
                            )
                            .background(
                                if (isHighlighted) MerlotColors.Accent.copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .clickable { onChannelClick(channel) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (logoUrl.isNotEmpty()) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = channel.name,
                            color = if (isHighlighted) MerlotColors.White else MerlotColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Category Sidebar — Step 2 slide-in (category list)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideCategorySidebar(
    visible: Boolean,
    groups: List<String>,
    selectedGroup: String?,
    onGroupSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val allItems = remember(groups) {
        listOf<String?>(null, "\u2605 Favorites") + groups.map { it }
    }
    // Default to the currently selected category
    val defaultIndex = remember(selectedGroup, allItems) {
        val idx = allItems.indexOf(selectedGroup)
        if (idx >= 0) idx else 0
    }
    var highlightedIndex by remember(visible) { mutableIntStateOf(defaultIndex) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sidebarFocusRequester = remember { FocusRequester() }

    // Auto-focus and scroll to current category when sidebar slides in
    LaunchedEffect(visible) {
        if (visible) {
            delay(150)
            try { sidebarFocusRequester.requestFocus() } catch (_: Exception) {}
            // Scroll to show the highlighted category
            listState.animateScrollToItem((defaultIndex - 3).coerceAtLeast(0))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = Modifier
            .zIndex(10f)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(MerlotColors.Black.copy(alpha = 0.92f))
                .focusRequester(sidebarFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (highlightedIndex < allItems.size - 1) {
                                highlightedIndex++
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (highlightedIndex - 3).coerceAtLeast(0)
                                    )
                                }
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (highlightedIndex > 0) {
                                highlightedIndex--
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (highlightedIndex - 3).coerceAtLeast(0)
                                    )
                                }
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            val item = allItems.getOrNull(highlightedIndex)
                            onGroupSelected(item)
                            true
                        }
                        Key.Back, Key.DirectionRight, Key.DirectionLeft -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            // Header
            Text(
                "Select Category",
                color = MerlotColors.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MerlotColors.Border)
            )

            // Category list
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(allItems) { index, item ->
                    val label = item ?: "All Channels"
                    val isSelected = item == selectedGroup
                    val isHighlighted = index == highlightedIndex

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGroupSelected(item) }
                            .border(
                                width = if (isHighlighted) 2.dp else 0.dp,
                                color = if (isHighlighted) MerlotColors.Accent else Color.Transparent
                            )
                            .background(
                                when {
                                    isHighlighted -> MerlotColors.Accent.copy(alpha = 0.25f)
                                    isSelected -> MerlotColors.Surface2
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            label,
                            color = when {
                                isHighlighted -> MerlotColors.White
                                isSelected -> MerlotColors.Accent
                                else -> MerlotColors.TextPrimary
                            },
                            fontSize = 13.sp,
                            fontWeight = if (isSelected || isHighlighted) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Program Detail Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideProgramDetailDialog(program: EpgEntry, onDismiss: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Surface)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = program.title,
                    color = MerlotColors.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = timeFormat.format(Date(program.startTime)) + " - " + timeFormat.format(Date(program.endTime)),
                    color = MerlotColors.TextPrimary,
                    fontSize = 13.sp
                )
                if (program.category.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = program.category,
                        color = MerlotColors.AccentDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (program.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = program.description,
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                var closeFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (closeFocused) MerlotColors.Accent else MerlotColors.Surface2,
                        contentColor = if (closeFocused) MerlotColors.Black else MerlotColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End).onFocusChanged { closeFocused = it.isFocused }.focusable()
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Loading / Error / Empty States
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideLoadingState(message: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MerlotColors.Accent)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = MerlotColors.TextMuted, fontSize = 13.sp)
        Text(
            "This may take a moment for large EPG sources",
            color = MerlotColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun GuideErrorState(error: String?, viewModel: TvGuideViewModel, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(error ?: "Unknown error", color = MerlotColors.Danger, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))
        var retryFocused by remember { mutableStateOf(false) }
        Button(
            onClick = { viewModel.retry() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (retryFocused) MerlotColors.Accent else MerlotColors.Surface2,
                contentColor = if (retryFocused) MerlotColors.Black else MerlotColors.TextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.onFocusChanged { retryFocused = it.isFocused }.focusable()
        ) { Text("Retry", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun GuideEmptyState(viewModel: TvGuideViewModel, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No channels available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        Text(
            "Add a playlist in Settings to see channels here",
            color = MerlotColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        var retryFocused by remember { mutableStateOf(false) }
        Button(
            onClick = { viewModel.retry() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (retryFocused) MerlotColors.Accent else MerlotColors.Surface2,
                contentColor = if (retryFocused) MerlotColors.Black else MerlotColors.TextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.onFocusChanged { retryFocused = it.isFocused }.focusable()
        ) { Text("Retry", fontWeight = FontWeight.Bold) }
    }
}
