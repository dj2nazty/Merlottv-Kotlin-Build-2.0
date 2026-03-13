package com.merlottv.kotlin.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import com.merlottv.kotlin.data.local.WatchProgressItem
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun HomeScreen(
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        when {
            uiState.isLoading && uiState.catalogRows.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading catalogs...", color = MerlotColors.TextMuted, fontSize = 12.sp)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // Hero carousel — auto-rotating top movies
                    if (uiState.featuredItems.isNotEmpty()) {
                        item(key = "hero_carousel") {
                            HeroCarousel(
                                items = uiState.featuredItems,
                                onItemClick = { meta -> onNavigateToDetail(meta.type, meta.id) }
                            )
                        }
                    }

                    // Continue Watching row (if any)
                    if (uiState.continueWatching.isNotEmpty()) {
                        item(key = "continue_watching") {
                            ContinueWatchingRow(
                                items = uiState.continueWatching,
                                onItemClick = { item ->
                                    onNavigateToDetail(item.type, item.id)
                                }
                            )
                        }
                    }

                    // Catalog rows
                    items(uiState.catalogRows, key = { it.title }) { row ->
                        CatalogRowSection(
                            title = row.title,
                            items = row.items,
                            onItemClick = { item -> onNavigateToDetail(item.type, item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<WatchProgressItem>,
    onItemClick: (WatchProgressItem) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MerlotColors.Accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Continue Watching",
                color = MerlotColors.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { item ->
                ContinueWatchingCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(item: WatchProgressItem, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(160.dp)
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
                model = item.poster,
                contentDescription = item.title,
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Surface2)
                    .then(
                        if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
                contentScale = ContentScale.Crop
            )

            // Progress bar at bottom of thumbnail
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(MerlotColors.Surface2)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.progressPercent)
                        .height(3.dp)
                        .background(MerlotColors.Accent)
                )
            }

            // Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MerlotColors.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MerlotColors.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = item.title,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(item.progressPercent * 100).toInt()}% watched",
                color = MerlotColors.TextMuted,
                fontSize = 9.sp
            )
            val remainingMs = (item.duration - item.position).coerceAtLeast(0)
            if (remainingMs > 0) {
                val remainingMin = remainingMs / 60_000
                val remainingText = if (remainingMin >= 60) {
                    "${remainingMin / 60}h ${remainingMin % 60}m left"
                } else {
                    "${remainingMin}m left"
                }
                Text(
                    text = remainingText,
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun HeroCarousel(
    items: List<MetaPreview>,
    onItemClick: (MetaPreview) -> Unit
) {
    if (items.isEmpty()) return
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFocused by remember { mutableStateOf(false) }

    // Auto-rotate every 5 seconds (pause when focused)
    LaunchedEffect(currentIndex, isFocused) {
        if (!isFocused) {
            delay(5000)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    val currentItem = items[currentIndex]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            onItemClick(currentItem); true
                        }
                        Key.DirectionLeft -> {
                            currentIndex = if (currentIndex > 0) currentIndex - 1 else items.size - 1
                            true
                        }
                        Key.DirectionRight -> {
                            currentIndex = (currentIndex + 1) % items.size
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .then(
                if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(12.dp))
                else Modifier
            )
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "hero_carousel"
        ) { index ->
            val meta = items[index]
            // Use landscape background image (high-res) with poster as fallback
            val heroImage = meta.background.ifEmpty { meta.poster }
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = heroImage,
                    contentDescription = meta.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MerlotColors.Transparent,
                                    MerlotColors.Background.copy(alpha = 0.85f)
                                ),
                                startY = 100f
                            )
                        )
                )
                // Also add a left-edge gradient for better text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MerlotColors.Background.copy(alpha = 0.6f),
                                    MerlotColors.Transparent
                                ),
                                endX = 600f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 20.dp, end = 120.dp)
                ) {
                    // Use logo image for title if available, otherwise text
                    if (meta.logo.isNotEmpty()) {
                        AsyncImage(
                            model = meta.logo,
                            contentDescription = meta.name,
                            modifier = Modifier
                                .height(48.dp)
                                .padding(bottom = 6.dp),
                            contentScale = ContentScale.FillHeight
                        )
                    } else {
                        Text(
                            text = meta.name,
                            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (meta.description.isNotEmpty()) {
                        Text(
                            text = meta.description,
                            color = MerlotColors.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Dot indicators at bottom-right
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentIndex) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index == currentIndex) MerlotColors.Accent
                            else MerlotColors.TextMuted.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

@Composable
private fun CatalogRowSection(
    title: String,
    items: List<MetaPreview>,
    onItemClick: (MetaPreview) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            color = MerlotColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(meta = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun PosterCard(meta: MetaPreview, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(130.dp)
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
                    .width(130.dp)
                    .height(195.dp)
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
