@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.vod

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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import com.merlottv.kotlin.ui.components.MerlotChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.ui.components.CardTrailerPreview
import com.merlottv.kotlin.ui.theme.MerlotColors
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

@Composable
fun VodScreen(
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: VodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteVodIds.collectAsState()
    val firstCardFocusRequester = remember { FocusRequester() }

    // Focus restoration: track the last focused item ID across navigation
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // Restore focus to the previously selected item, or default to first card
    LaunchedEffect(uiState.filteredSections.isNotEmpty()) {
        if (uiState.filteredSections.isNotEmpty()) {
            delay(300)
            val restored = lastFocusedItemId?.let { id ->
                focusRequesters[id]?.let { requester ->
                    try { requester.requestFocus(); true } catch (_: Exception) { false }
                }
            } ?: false
            if (!restored) {
                try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VOD",
                color = MerlotColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(16.dp))

            val tabs = listOf("All", "Movies", "Series")
            tabs.forEach { tab ->
                val isSelected = uiState.selectedTab == tab

                MerlotChip(
                    selected = isSelected,
                    onClick = { viewModel.onTabSelected(tab) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary
                            when (tab) {
                                "Movies" -> Icon(Icons.Default.Movie, null, modifier = Modifier.size(14.dp), tint = tint)
                                "Series" -> Icon(Icons.Default.Tv, null, modifier = Modifier.size(14.dp), tint = tint)

                                else -> {}
                            }
                            if (tab != "All") Spacer(modifier = Modifier.width(4.dp))
                            Text(tab, fontSize = 12.sp, color = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!uiState.isLoading && uiState.filteredSections.isNotEmpty()) {
                Text(
                    text = "${uiState.filteredSections.size} categories",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Genre & Year filter chip rows (only on Movies/Series tabs)
        if (uiState.selectedTab != "All" && (uiState.availableGenres.isNotEmpty() || uiState.availableYears.isNotEmpty())) {
            // Genre filter row
            if (uiState.availableGenres.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                    Text(
                        "Genre",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(uiState.availableGenres) { genre ->
                            MerlotChip(
                                selected = genre == uiState.selectedGenre,
                                onClick = { viewModel.onGenreSelected(genre) },
                                label = {
                                    Text(
                                        genre,
                                        fontSize = 11.sp,
                                        color = if (genre == uiState.selectedGenre) MerlotColors.Black else MerlotColors.TextPrimary
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Year filter row
            if (uiState.availableYears.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                    Text(
                        "Year",
                        color = MerlotColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(uiState.availableYears) { year ->
                            MerlotChip(
                                selected = year == uiState.selectedYear,
                                onClick = { viewModel.onYearSelected(year) },
                                label = {
                                    Text(
                                        year,
                                        fontSize = 11.sp,
                                        color = if (year == uiState.selectedYear) MerlotColors.Black else MerlotColors.TextPrimary
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MerlotColors.Accent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading catalogs...", color = MerlotColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
            uiState.isFilterLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MerlotColors.Accent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading...", color = MerlotColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "Failed to load", color = MerlotColors.Danger, fontSize = 13.sp)
                }
            }
            uiState.filteredSections.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No content available", color = MerlotColors.TextMuted, fontSize = 14.sp)
                }
            }
            else -> {
                val isFilterActive = uiState.selectedGenre != null || uiState.selectedYear != null

                if (isFilterActive) {
                    // Grid layout for genre/year filtered results
                    val allItems = uiState.filteredSections.flatMap { it.items }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(allItems, key = { it.id }) { item ->
                            val itemFocusRequester = remember {
                                focusRequesters.getOrPut(item.id) { FocusRequester() }
                            }
                            val isFirst = item == allItems.firstOrNull()
                            VodCard(
                                item = item,
                                onClick = {
                                    lastFocusedItemId = item.id
                                    onNavigateToDetail(item.type, item.id)
                                },
                                onLongClick = { viewModel.toggleFavorite(item) },
                                isFavorite = item.id in favoriteIds,
                                focusRequester = if (isFirst) firstCardFocusRequester else itemFocusRequester,
                                onFocused = { lastFocusedItemId = item.id }
                            )
                        }
                    }
                } else {
                    // Normal horizontal row layout for unfiltered catalogs
                    val catalogListState = rememberLazyListState()
                    LazyColumn(
                        state = catalogListState,
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            uiState.filteredSections,
                            key = { "${it.addonName}_${it.catalogId}_${it.type}_${it.title}" }
                        ) { section ->
                            val isFirst = section == uiState.filteredSections.first()
                            CatalogSectionRow(
                                section = section,
                                onItemClick = { item ->
                                    lastFocusedItemId = item.id
                                    onNavigateToDetail(item.type, item.id)
                                },
                                onItemLongClick = { item ->
                                    viewModel.toggleFavorite(item)
                                },
                                favoriteIds = favoriteIds,
                                firstCardFocusRequester = if (isFirst) firstCardFocusRequester else null,
                                focusRequesters = focusRequesters,
                                onItemFocused = { itemId -> lastFocusedItemId = itemId }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Focus debounce delay — prevents rapid focus fires during fast D-pad navigation */
private const val FOCUS_DEBOUNCE_MS = 140L
/** Number of items to prefetch ahead of visible items */
private const val POSTER_PREFETCH_DISTANCE = 8

@Composable
private fun CatalogSectionRow(
    section: CatalogSection,
    onItemClick: (MetaPreview) -> Unit,
    onItemLongClick: (MetaPreview) -> Unit = {},
    favoriteIds: Set<String> = emptySet(),
    firstCardFocusRequester: FocusRequester? = null,
    focusRequesters: MutableMap<String, FocusRequester> = mutableMapOf(),
    onItemFocused: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        // Section header with brand logo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand logo (streaming platform logo)
            val logoUrl = section.brandLogo.ifEmpty { section.addonLogo }
            if (logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = section.title,
                color = MerlotColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (section.type == "movie") MerlotColors.Accent.copy(alpha = 0.15f)
                        else MerlotColors.Success.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (section.type == "movie") "MOVIE" else "SERIES",
                    color = if (section.type == "movie") MerlotColors.Accent else MerlotColors.Success,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${section.items.size} titles",
                color = MerlotColors.TextMuted,
                fontSize = 10.sp
            )
        }

        // Horizontal poster row
        val lazyRowState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // Image prefetching: preload posters 8 items ahead of visible items
        LaunchedEffect(lazyRowState) {
            snapshotFlow {
                lazyRowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }.distinctUntilChanged().collect { lastVisibleIndex ->
                val endIndex = (lastVisibleIndex + POSTER_PREFETCH_DISTANCE)
                    .coerceAtMost(section.items.size - 1)
                for (i in (lastVisibleIndex + 1)..endIndex) {
                    val posterUrl = section.items[i].poster
                    if (posterUrl.isNotEmpty()) {
                        val request = ImageRequest.Builder(context)
                            .data(posterUrl)
                            .size(130, 195)
                            .build()
                        context.imageLoader.enqueue(request)
                    }
                }
            }
        }

        LazyRow(
            state = lazyRowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(section.items, key = { it.id }) { item ->
                val isFirst = firstCardFocusRequester != null && section.items.firstOrNull()?.id == item.id
                val itemFocusRequester = remember(item.id) {
                    if (isFirst && firstCardFocusRequester != null) {
                        focusRequesters[item.id] = firstCardFocusRequester
                        firstCardFocusRequester
                    } else {
                        focusRequesters.getOrPut(item.id) { FocusRequester() }
                    }
                }
                val itemIndex = remember(item.id, section.items.size) {
                    section.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                }

                VodCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    isFavorite = item.id in favoriteIds,
                    focusRequester = itemFocusRequester,
                    onFocused = { onItemFocused(item.id) },
                    onLeftPress = if (itemIndex > 0) {
                        {
                            val prevId = section.items.getOrNull(itemIndex - 1)?.id
                            if (prevId != null) {
                                // Just move focus — Compose's BringIntoView scrolls
                                // only enough to reveal the one card, not the whole row.
                                focusRequesters[prevId]?.let {
                                    try { it.requestFocus() } catch (_: Exception) {}
                                }
                            }
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun VodCard(
    item: MetaPreview,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isFavorite: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onLeftPress: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var isTrailerPlaying by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }
    var showHeartOverlay by remember { mutableStateOf(false) }
    var heartIsFilled by remember { mutableStateOf(false) }

    // Focus debounce — prevents rapid focus fires during fast D-pad navigation
    var focusEventId by remember { mutableIntStateOf(0) }
    var isCardFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focusEventId, isCardFocused) {
        if (focusEventId == 0 || !isCardFocused) return@LaunchedEffect
        val targetEventId = focusEventId
        delay(FOCUS_DEBOUNCE_MS)
        if (!isCardFocused || focusEventId != targetEventId) return@LaunchedEffect
        onFocused()
    }

    // Scale animation with spring physics (more natural than tween)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "cardScale"
    )

    // Width: expands to landscape hero when trailer plays, normal poster otherwise
    val cardWidth by animateDpAsState(
        targetValue = when {
            isTrailerPlaying -> 280.dp  // Landscape hero width for trailer
            isFocused -> 140.dp
            else -> 130.dp
        },
        animationSpec = tween(durationMillis = 350),
        label = "cardWidth"
    )

    // Height: switches to 16:9 landscape when trailer plays, 3:2 poster otherwise
    val cardHeight by animateDpAsState(
        targetValue = when {
            isTrailerPlaying -> 158.dp  // 280 * 9/16 = 157.5 ≈ 158dp (16:9)
            isFocused -> 210.dp         // 140 * 1.5
            else -> 195.dp              // 130 * 1.5
        },
        animationSpec = tween(durationMillis = 350),
        label = "cardHeight"
    )

    // Auto-hide heart overlay after 1.5 seconds
    if (showHeartOverlay) {
        LaunchedEffect(showHeartOverlay) {
            delay(1500)
            showHeartOverlay = false
        }
    }

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged {
                isFocused = it.isFocused
                isCardFocused = it.isFocused
                if (it.isFocused) focusEventId++
            }
            .onPreviewKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter
                when {
                    // Intercept Left to scroll back in row instead of opening sidebar
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && onLeftPress != null -> {
                        onLeftPress.invoke()
                        true // consume — prevents bubble to MainActivity's onKeyEvent
                    }
                    event.type == KeyEventType.KeyDown && isSelectKey -> {
                        if (pressStartTime == 0L) pressStartTime = System.currentTimeMillis()
                        false
                    }
                    event.type == KeyEventType.KeyUp && isSelectKey -> {
                        val held = System.currentTimeMillis() - pressStartTime
                        pressStartTime = 0L
                        if (held >= 600) {
                            heartIsFilled = !isFavorite
                            showHeartOverlay = true
                            onLongClick()
                            true
                        } else {
                            onClick()
                            true
                        }
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        Box {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MerlotColors.Surface2)
                    .then(
                        if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
                contentScale = ContentScale.Crop
            )

            // Inline trailer preview (plays after 2s focus, expands to landscape hero)
            CardTrailerPreview(
                isFocused = isFocused,
                contentId = item.id,
                contentType = item.type,
                title = item.name,
                onTrailerStateChanged = { playing -> isTrailerPlaying = playing },
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(8.dp))
            )

            // Rating badge
            if (item.imdbRating.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MerlotColors.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "\u2B50 ${item.imdbRating}",
                        color = MerlotColors.Warn,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Favorite heart overlay (shows after long-press) — animated entrance/exit
            if (showHeartOverlay) {
                val heartScale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                    label = "heartScale"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                        }
                        .clip(RoundedCornerShape(32.dp))
                        .background(MerlotColors.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (heartIsFilled) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (heartIsFilled) "Added to favorites" else "Removed from favorites",
                        tint = if (heartIsFilled) Color(0xFFFF4081) else MerlotColors.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Small heart badge when favorited (always visible)
            if (isFavorite && !showHeartOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MerlotColors.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorited",
                        tint = Color(0xFFFF4081),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Focus overlay with title
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(MerlotColors.Transparent, MerlotColors.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = item.name,
                        color = MerlotColors.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = item.name,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
