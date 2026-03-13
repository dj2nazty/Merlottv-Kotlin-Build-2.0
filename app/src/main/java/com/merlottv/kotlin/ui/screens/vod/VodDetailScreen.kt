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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.domain.model.Video
import com.merlottv.kotlin.ui.theme.MerlotColors

/** Grey color for focused buttons throughout the app */
private val FocusedButtonGrey = Color(0xFF666666)
private val FocusedButtonGreyLight = Color(0xFF777777)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VodDetailScreen(
    type: String,
    id: String,
    onBack: () -> Unit,
    onPlay: (streamUrl: String, title: String, contentId: String, poster: String, contentType: String) -> Unit,
    onNavigateToDetail: ((String, String) -> Unit)? = null,
    viewModel: VodDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playButtonFocusRequester = remember { FocusRequester() }

    // Auto-navigate to player when stream is selected
    LaunchedEffect(uiState.autoPlayTriggered, uiState.selectedStreamUrl) {
        if (uiState.autoPlayTriggered && uiState.selectedStreamUrl != null) {
            val meta = uiState.meta
            val episode = uiState.selectedEpisode
            val contentId = if (episode != null) episode.id else (meta?.id ?: id)
            onPlay(
                uiState.selectedStreamUrl!!,
                uiState.selectedStreamTitle ?: "",
                contentId,
                meta?.poster ?: "",
                type
            )
            viewModel.clearPlayback()
        }
    }

    // Default focus on Play button when meta loads
    LaunchedEffect(uiState.meta) {
        if (uiState.meta != null) {
            try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
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
                val isSeries = meta.videos.isNotEmpty() && uiState.seasons.isNotEmpty()

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
                        // Back button — grey on focus
                        var backFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .onFocusChanged { backFocused = it.isFocused }
                                .then(
                                    if (backFocused) Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(FocusedButtonGrey)
                                    else Modifier
                                )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (backFocused) MerlotColors.White else MerlotColors.White)
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
                                if (isSeries) {
                                    Text("${uiState.seasons.size} Seasons", color = MerlotColors.TextMuted, fontSize = 12.sp)
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

                            // Watch progress bar (if previously watched)
                            if (uiState.watchProgressPercent > 0f && !isSeries) {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MerlotColors.Surface2)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(uiState.watchProgressPercent)
                                                .height(4.dp)
                                                .background(MerlotColors.Accent)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val remainMs = (uiState.watchDuration - uiState.watchPosition).coerceAtLeast(0)
                                    val remainMin = remainMs / 60_000
                                    val remainText = if (remainMin >= 60) {
                                        "${remainMin / 60}h ${remainMin % 60}m remaining"
                                    } else {
                                        "${remainMin}m remaining"
                                    }
                                    Text(
                                        text = "${(uiState.watchProgressPercent * 100).toInt()}% watched \u2022 $remainText",
                                        color = MerlotColors.TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Action buttons — Play/Resume/Restart for movies (series uses episode play)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isSeries) {
                                    val hasProgress = uiState.watchProgressPercent > 0f

                                    if (hasProgress) {
                                        // Resume button — primary action
                                        var resumeFocused by remember { mutableStateOf(false) }
                                        Button(
                                            onClick = { viewModel.playBestStream() },
                                            enabled = !uiState.isLoadingStreams,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (resumeFocused) FocusedButtonGrey else MerlotColors.Accent,
                                                contentColor = if (resumeFocused) MerlotColors.White else MerlotColors.Black
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .focusRequester(playButtonFocusRequester)
                                                .onFocusChanged { resumeFocused = it.isFocused }
                                                .focusable()
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown &&
                                                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                                    ) {
                                                        viewModel.playBestStream()
                                                        true
                                                    } else false
                                                }
                                        ) {
                                            if (uiState.isLoadingStreams) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MerlotColors.Black, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Finding stream...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            } else {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("RESUME", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }

                                        // Restart button
                                        var restartFocused by remember { mutableStateOf(false) }
                                        Button(
                                            onClick = { viewModel.playFromStart() },
                                            enabled = !uiState.isLoadingStreams,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (restartFocused) FocusedButtonGrey else MerlotColors.Surface2,
                                                contentColor = if (restartFocused) MerlotColors.White else MerlotColors.TextPrimary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .onFocusChanged { restartFocused = it.isFocused }
                                                .focusable()
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown &&
                                                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                                    ) {
                                                        viewModel.playFromStart()
                                                        true
                                                    } else false
                                                }
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("RESTART", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        // Play button — no previous progress
                                        var playFocused by remember { mutableStateOf(false) }
                                        Button(
                                            onClick = { viewModel.playBestStream() },
                                            enabled = !uiState.isLoadingStreams,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (playFocused) FocusedButtonGrey else MerlotColors.Accent,
                                                contentColor = if (playFocused) MerlotColors.White else MerlotColors.Black
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .focusRequester(playButtonFocusRequester)
                                                .onFocusChanged { playFocused = it.isFocused }
                                                .focusable()
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown &&
                                                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                                    ) {
                                                        viewModel.playBestStream()
                                                        true
                                                    } else false
                                                }
                                        ) {
                                            if (uiState.isLoadingStreams) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MerlotColors.Black, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Finding stream...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            } else {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("PLAY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

                                // Like This button
                                var likeFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { viewModel.loadSimilarContent() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (likeFocused) FocusedButtonGrey else MerlotColors.Surface2,
                                        contentColor = if (likeFocused) MerlotColors.White else MerlotColors.TextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .then(if (isSeries) Modifier.focusRequester(playButtonFocusRequester) else Modifier)
                                        .onFocusChanged { likeFocused = it.isFocused }
                                        .focusable()
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown &&
                                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                            ) {
                                                viewModel.loadSimilarContent()
                                                true
                                            } else false
                                        }
                                ) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("LIKE THIS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                // Favorite button — grey on focus
                                var favFocused by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { viewModel.toggleFavorite() },
                                    modifier = Modifier
                                        .onFocusChanged { favFocused = it.isFocused }
                                        .then(
                                            if (favFocused) Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(FocusedButtonGrey)
                                            else Modifier
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = when {
                                            favFocused -> MerlotColors.White
                                            uiState.isFavorite -> MerlotColors.Accent
                                            else -> MerlotColors.TextMuted
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // =============== SEASON/EPISODE BROWSER (for series) ===============
                    if (isSeries) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Season selector tabs
                        Text(
                            text = "Seasons",
                            color = MerlotColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.seasons) { season ->
                                val isSelected = uiState.selectedSeason == season
                                var isFocused by remember { mutableStateOf(false) }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectSeason(season) },
                                    label = { Text("Season $season", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MerlotColors.Accent,
                                        selectedLabelColor = MerlotColors.Black,
                                        containerColor = if (isFocused) FocusedButtonGrey else MerlotColors.Surface2,
                                        labelColor = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary
                                    ),
                                    border = if (isFocused && !isSelected) FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = false,
                                        borderColor = FocusedButtonGreyLight,
                                        borderWidth = 2.dp
                                    ) else FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    modifier = Modifier
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable()
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown &&
                                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                            ) {
                                                viewModel.selectSeason(season)
                                                true
                                            } else false
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Episode list
                        Text(
                            text = "Episodes (${uiState.episodesForSeason.size})",
                            color = MerlotColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.episodesForSeason.forEach { episode ->
                            EpisodeCard(
                                episode = episode,
                                isSelected = uiState.selectedEpisode?.id == episode.id,
                                isLoadingStreams = uiState.isLoadingStreams && uiState.selectedEpisode?.id == episode.id,
                                onPlay = { viewModel.playEpisode(episode) }
                            )
                        }
                    }

                    // Streams section (for movies or after episode selection)
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
                                            if (streamFocused) FocusedButtonGrey.copy(alpha = 0.3f)
                                            else MerlotColors.Surface2
                                        )
                                        .then(
                                            if (streamFocused) Modifier.border(2.dp, FocusedButtonGreyLight, RoundedCornerShape(8.dp))
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
                                            color = if (streamFocused) MerlotColors.White else MerlotColors.TextPrimary,
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
                                    // Stream Play button — grey on focus
                                    var streamPlayFocused by remember { mutableStateOf(false) }
                                    Button(
                                        onClick = { viewModel.playStream(stream) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (streamPlayFocused) FocusedButtonGrey else MerlotColors.Accent,
                                            contentColor = if (streamPlayFocused) MerlotColors.White else MerlotColors.Black
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .onFocusChanged { streamPlayFocused = it.isFocused }
                                    ) {
                                        Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // =============== SIMILAR CONTENT ("LIKE THIS") ===============
                    if (uiState.similarItems.isNotEmpty() || uiState.isLoadingSimilar) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "More Like This",
                            color = MerlotColors.Accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (uiState.isLoadingSimilar) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MerlotColors.Accent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Finding similar content...", color = MerlotColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                        if (uiState.similarItems.isNotEmpty()) {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.similarItems, key = { it.id }) { item ->
                                    SimilarPosterCard(
                                        meta = item,
                                        onClick = {
                                            onNavigateToDetail?.invoke(item.type, item.id)
                                        }
                                    )
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

@Composable
private fun SimilarPosterCard(meta: MetaPreview, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(120.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
    ) {
        Box {
            AsyncImage(
                model = meta.poster,
                contentDescription = meta.name,
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Surface2)
                    .then(
                        if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
            if (meta.imdbRating.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MerlotColors.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "\u2B50 ${meta.imdbRating}",
                        color = MerlotColors.Warn,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = meta.name,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: Video,
    isSelected: Boolean,
    isLoadingStreams: Boolean,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected && isFocused -> FocusedButtonGrey.copy(alpha = 0.4f)
                    isFocused -> FocusedButtonGrey.copy(alpha = 0.3f)
                    isSelected -> MerlotColors.Accent.copy(alpha = 0.1f)
                    else -> MerlotColors.Surface2
                }
            )
            .then(
                if (isFocused) Modifier.border(2.dp, FocusedButtonGreyLight, RoundedCornerShape(10.dp))
                else if (isSelected) Modifier.border(1.dp, MerlotColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onPlay()
                    true
                } else false
            }
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Episode thumbnail
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MerlotColors.Surface2),
            contentAlignment = Alignment.Center
        ) {
            if (episode.thumbnail.isNotEmpty()) {
                AsyncImage(
                    model = episode.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // Play overlay icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MerlotColors.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingStreams) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MerlotColors.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MerlotColors.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Episode info
        Column(modifier = Modifier.weight(1f)) {
            // Episode number + title
            Text(
                text = "E${episode.episode}. ${episode.title}",
                color = when {
                    isFocused -> MerlotColors.White
                    isSelected -> MerlotColors.Accent
                    else -> MerlotColors.TextPrimary
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Runtime / release date
            val infoItems = mutableListOf<String>()
            if (episode.released.isNotEmpty()) {
                // Show just the date part (first 10 chars of ISO date)
                val dateStr = episode.released.take(10)
                infoItems.add(dateStr)
            }
            if (infoItems.isNotEmpty()) {
                Text(
                    text = infoItems.joinToString(" • "),
                    color = MerlotColors.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Overview/description
            if (episode.overview.isNotEmpty()) {
                Text(
                    text = episode.overview,
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
