@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.ui.theme.MerlotColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VodDetailScreen(
    type: String,
    id: String,
    onBack: () -> Unit,
    onPlay: (streamUrl: String, title: String, contentId: String, poster: String, contentType: String) -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Auto-navigate to player when stream is selected
    LaunchedEffect(uiState.autoPlayTriggered, uiState.selectedStreamUrl) {
        if (uiState.autoPlayTriggered && uiState.selectedStreamUrl != null) {
            val meta = uiState.meta
            onPlay(
                uiState.selectedStreamUrl!!,
                uiState.selectedStreamTitle ?: "",
                meta?.id ?: id,
                meta?.poster ?: "",
                type
            )
            viewModel.clearPlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MerlotColors.Accent
                )
            }
            uiState.meta != null -> {
                val meta = uiState.meta!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Background image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        AsyncImage(
                            model = meta.background.ifEmpty { meta.poster },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            MerlotColors.Transparent,
                                            MerlotColors.Background
                                        )
                                    )
                                )
                        )
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MerlotColors.White)
                        }
                    }

                    // Content
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Poster
                        AsyncImage(
                            model = meta.poster,
                            contentDescription = null,
                            modifier = Modifier
                                .width(120.dp)
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meta.name,
                                color = MerlotColors.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (meta.imdbRating.isNotEmpty()) {
                                    Text("\u2B50 ${meta.imdbRating}", color = MerlotColors.Warn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                if (meta.year.isNotEmpty()) {
                                    Text(meta.year, color = MerlotColors.TextMuted, fontSize = 12.sp)
                                }
                                if (meta.runtime.isNotEmpty()) {
                                    Text(meta.runtime, color = MerlotColors.TextMuted, fontSize = 12.sp)
                                }
                            }

                            // Genres
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                meta.genres.forEach { genre ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(genre, fontSize = 10.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MerlotColors.Surface2,
                                            labelColor = MerlotColors.TextPrimary
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, MerlotColors.Border
                                        )
                                    )
                                }
                            }

                            if (meta.description.isNotEmpty()) {
                                Text(
                                    text = meta.description,
                                    color = MerlotColors.TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                    lineHeight = 18.sp
                                )
                            }

                            // Cast
                            if (meta.cast.isNotEmpty()) {
                                Text(
                                    text = "Cast: ${meta.cast.take(5).joinToString(", ")}",
                                    color = MerlotColors.TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.playBestStream() },
                                    enabled = !uiState.isLoadingStreams,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MerlotColors.Accent,
                                        contentColor = MerlotColors.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (uiState.isLoadingStreams) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MerlotColors.Black,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Finding stream...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("PLAY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                IconButton(onClick = { viewModel.toggleFavorite() }) {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (uiState.isFavorite) MerlotColors.Accent else MerlotColors.TextMuted
                                    )
                                }
                            }
                        }
                    }

                    // Streams section
                    if (uiState.streams.isNotEmpty()) {
                        Text(
                            text = "Available Streams (${uiState.streams.size})",
                            color = MerlotColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                        uiState.streams.forEach { stream ->
                            val streamUrl = stream.url.ifEmpty { stream.externalUrl }
                            if (streamUrl.isNotEmpty()) {
                                var streamFocused by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (streamFocused) MerlotColors.Accent.copy(alpha = 0.15f)
                                            else MerlotColors.Surface2
                                        )
                                        .then(
                                            if (streamFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                                            else Modifier
                                        )
                                        .onFocusChanged { streamFocused = it.isFocused }
                                        .focusable()
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown &&
                                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                            ) {
                                                viewModel.playStream(stream)
                                                true
                                            } else false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stream.name.ifEmpty { stream.addonName },
                                            color = if (streamFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (stream.title.isNotEmpty()) {
                                            Text(
                                                text = stream.title,
                                                color = MerlotColors.TextMuted,
                                                fontSize = 10.sp,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.playStream(stream) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MerlotColors.Accent,
                                            contentColor = MerlotColors.Black
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
