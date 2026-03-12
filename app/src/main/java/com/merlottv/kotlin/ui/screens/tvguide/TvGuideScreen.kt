package com.merlottv.kotlin.ui.screens.tvguide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.ui.theme.MerlotColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                                val target = (channelListState.firstVisibleItemIndex + 1)
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
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        uiState.loadingMessage,
                        color = MerlotColors.TextMuted,
                        fontSize = 13.sp
                    )
                    Text(
                        "This may take a moment for large EPG sources",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        uiState.error ?: "Unknown error",
                        color = MerlotColors.Danger,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.retry() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MerlotColors.Accent,
                            contentColor = MerlotColors.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.focusable()
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
            uiState.channels.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No EPG data available",
                        color = MerlotColors.TextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.retry() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MerlotColors.Accent,
                            contentColor = MerlotColors.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.focusable()
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TV Guide",
                            color = MerlotColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${uiState.channels.size} channels",
                            color = MerlotColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    // Time header + EPG grid
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Channel names column
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier
                                .width(160.dp)
                                .fillMaxHeight()
                        ) {
                            items(uiState.channels, key = { it.id }) { channel ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .border(0.5.dp, MerlotColors.Border)
                                        .background(MerlotColors.Surface)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (channel.icon.isNotEmpty()) {
                                        AsyncImage(
                                            model = channel.icon,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(4.dp))
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
                        }

                        // Programs grid (horizontally scrollable)
                        LazyColumn(
                            state = programListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(scrollState)
                        ) {
                            items(uiState.channels, key = { it.id }) { channel ->
                                EpgChannelRow(channel = channel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgChannelRow(channel: EpgChannel) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val now = System.currentTimeMillis()

    Row(
        modifier = Modifier
            .height(56.dp)
    ) {
        val programs = channel.programs
            .filter { it.endTime > now - 3600000 * 3 }
            .sortedBy { it.startTime }
            .take(12)

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
                Text(
                    text = "No program info",
                    color = MerlotColors.TextMuted,
                    fontSize = 10.sp
                )
            }
        } else {
            programs.forEach { program ->
                val durationMinutes = ((program.endTime - program.startTime) / 60000).coerceAtLeast(1)
                val widthDp = (durationMinutes * 3).coerceIn(80, 600).toInt()
                val isCurrentlyAiring = program.startTime <= now && program.endTime >= now

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .fillMaxHeight()
                        .border(0.5.dp, MerlotColors.Border)
                        .background(
                            if (isCurrentlyAiring) MerlotColors.AccentAlpha10
                            else MerlotColors.Surface2
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .focusable()
                ) {
                    Column {
                        Text(
                            text = program.title,
                            color = if (isCurrentlyAiring) MerlotColors.Accent else MerlotColors.TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${timeFormat.format(Date(program.startTime))} - ${timeFormat.format(Date(program.endTime))}",
                            color = MerlotColors.TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
