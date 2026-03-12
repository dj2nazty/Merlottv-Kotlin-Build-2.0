package com.merlottv.kotlin.ui.screens.tvguide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.merlottv.kotlin.domain.model.EpgChannel
import com.merlottv.kotlin.domain.model.EpgEntry
import com.merlottv.kotlin.ui.theme.MerlotColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvGuideScreen(
    viewModel: TvGuideViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                    Text(
                        "Loading TV Guide...",
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            uiState.channels.isEmpty() -> {
                Text(
                    "No EPG data available",
                    color = MerlotColors.TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Text(
                        text = "TV Guide",
                        color = MerlotColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )

                    // Time header + EPG grid
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Channel names column
                        LazyColumn(
                            modifier = Modifier
                                .width(160.dp)
                                .fillMaxHeight()
                        ) {
                            items(uiState.channels) { channel ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .border(0.5.dp, MerlotColors.Border)
                                        .background(MerlotColors.Surface)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
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
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(scrollState)
                        ) {
                            items(uiState.channels) { channel ->
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
        channel.programs
            .filter { it.endTime > now - 3600000 * 3 } // Show programs within 3 hours of now
            .take(12) // Limit displayed programs
            .forEach { program ->
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
