package com.merlottv.kotlin.ui.screens.tvguide

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
    viewModel: TvGuideViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val channelListState = rememberLazyListState()
    val programListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

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

    LaunchedEffect(uiState.channels) {
        if (uiState.channels.isNotEmpty()) {
            val nowOffsetPx = ((currentTimeMs - timelineStartMs) / 60_000f * PIXELS_PER_MINUTE).toInt()
            val scrollTarget = (nowOffsetPx - 200).coerceAtLeast(0)
            scrollState.scrollTo(scrollTarget)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            scope.launch {
                                val target = channelListState.firstVisibleItemIndex + 1
                                channelListState.animateScrollToItem(target)
                                programListState.animateScrollToItem(target)
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            scope.launch {
                                val target = (channelListState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                channelListState.animateScrollToItem(target)
                                programListState.animateScrollToItem(target)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        when {
            uiState.isLoading -> LoadingState(uiState.loadingMessage, Modifier.align(Alignment.Center))
            uiState.error != null -> ErrorState(uiState.error, viewModel, Modifier.align(Alignment.Center))
            uiState.channels.isEmpty() -> EmptyState(viewModel, Modifier.align(Alignment.Center))
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    GuideHeader(uiState)
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.width(CHANNEL_COL_WIDTH.dp).fillMaxHeight()) {
                            Box(modifier = Modifier.height(TIMELINE_HEIGHT.dp).fillMaxWidth().background(MerlotColors.Surface))
                            LazyColumn(state = channelListState, modifier = Modifier.fillMaxHeight()) {
                                items(uiState.channels, key = { it.id }) { channel ->
                                    ChannelCell(channel)
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Column {
                                TimelineHeader(scrollState, timelineStartMs, timelineEndMs)
                                Box(modifier = Modifier.weight(1f)) {
                                    LazyColumn(
                                        state = programListState,
                                        modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)
                                    ) {
                                        items(uiState.channels, key = { it.id }) { channel ->
                                            EpgChannelRow(
                                                channel = channel,
                                                currentTimeMs = currentTimeMs,
                                                timelineStartMs = timelineStartMs,
                                                onProgramSelected = { viewModel.selectProgram(it) }
                                            )
                                        }
                                    }
                                    NowIndicator(currentTimeMs, timelineStartMs, scrollState)
                                }
                            }
                        }
                    }
                }
                uiState.selectedProgram?.let { program ->
                    ProgramDetailDialog(program) { viewModel.selectProgram(null) }
                }
            }
        }
    }
}

@Composable
private fun GuideHeader(uiState: TvGuideUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("TV Guide", color = MerlotColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(12.dp))
        Text("${uiState.channels.size} channels", color = MerlotColors.TextMuted, fontSize = 12.sp)
        if (uiState.isSyncing) {
            Spacer(modifier = Modifier.width(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MerlotColors.Accent, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Updating...", color = MerlotColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TimelineHeader(scrollState: ScrollState, startMs: Long, endMs: Long) {
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

@Composable
private fun NowIndicator(currentTimeMs: Long, timelineStartMs: Long, scrollState: ScrollState) {
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

@Composable
private fun ChannelCell(channel: EpgChannel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .border(0.5.dp, MerlotColors.Border)
            .background(MerlotColors.Surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.icon.isNotEmpty()) {
            AsyncImage(
                model = channel.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = channel.name,
            color = MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpgChannelRow(
    channel: EpgChannel,
    currentTimeMs: Long,
    timelineStartMs: Long,
    onProgramSelected: (EpgEntry) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(modifier = Modifier.height(ROW_HEIGHT.dp)) {
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
                var isFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .fillMaxHeight()
                        .alpha(if (isPast) 0.4f else 1f)
                        .border(
                            width = if (isFocused) 2.dp else 0.5.dp,
                            color = if (isFocused) MerlotColors.Accent else MerlotColors.Border
                        )
                        .background(
                            when {
                                isAiring -> MerlotColors.AccentAlpha10
                                else -> MerlotColors.Surface2
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onProgramSelected(program) }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                onProgramSelected(program)
                                true
                            } else false
                        }
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

@Composable
private fun ProgramDetailDialog(program: EpgEntry, onDismiss: () -> Unit) {
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
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MerlotColors.Accent,
                        contentColor = MerlotColors.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End).focusable()
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LoadingState(message: String, modifier: Modifier) {
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
private fun ErrorState(error: String?, viewModel: TvGuideViewModel, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(error ?: "Unknown error", color = MerlotColors.Danger, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.retry() },
            colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.focusable()
        ) { Text("Retry", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun EmptyState(viewModel: TvGuideViewModel, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No EPG data available", color = MerlotColors.TextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.retry() },
            colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.focusable()
        ) { Text("Retry", fontWeight = FontWeight.Bold) }
    }
}
