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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.merlottv.kotlin.R
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.ui.components.CardTrailerPreview
import com.merlottv.kotlin.ui.theme.MerlotColors
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.BackHandler

@Composable
fun VodScreen(
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateToHome: () -> Unit = {},
    initialPlatformId: String = "",
    viewModel: VodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Back button: if viewing a genre tab, deselect it; if viewing a platform, go back to Home
    BackHandler(enabled = uiState.selectedGenreTab != null || uiState.selectedPlatformTab != null) {
        if (uiState.selectedGenreTab != null) {
            viewModel.onGenreTabSelected(null)
        } else {
            onNavigateToHome()
        }
    }
    val favoriteIds by viewModel.favoriteVodIds.collectAsState()

    // Auto-select platform tab if navigated from Home with a platform ID
    LaunchedEffect(initialPlatformId) {
        if (initialPlatformId.isNotEmpty()) {
            // Small delay to let ViewModel init finish
            delay(200)
            val tab = PLATFORM_TABS.find { it.id == initialPlatformId }
            if (tab != null) {
                viewModel.onPlatformTabSelected(tab)
            }
        }
    }
    val firstCardFocusRequester = remember { FocusRequester() }
    val firstPlatformItemFocusRequester = remember { FocusRequester() }

    // Focus restoration: track the last focused item ID across navigation
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequesters = remember { linkedMapOf<String, FocusRequester>() }

    // LRU cap: trim oldest entries when map grows too large (deferred to avoid detached-node crashes)
    LaunchedEffect(uiState.selectedTab, uiState.selectedGenre, uiState.selectedYear, uiState.selectedPlatformTab) {
        // Delay cleanup so Compose finishes disposing old nodes first
        kotlinx.coroutines.delay(500)
        focusRequesters.clear()
    }

    // Always focus the first content card when entering/returning to VOD
    var focusTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { focusTrigger++ }
    LaunchedEffect(focusTrigger, uiState.filteredSections.isNotEmpty()) {
        if (uiState.filteredSections.isNotEmpty() && uiState.selectedPlatformTab == null) {
            delay(300)
            try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Focus first item when platform/streaming service content loads
    LaunchedEffect(uiState.platformSections.isNotEmpty(), uiState.selectedPlatformTab) {
        if (uiState.selectedPlatformTab != null && uiState.platformSections.isNotEmpty()) {
            delay(400)
            try { firstPlatformItemFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Throttle rapid D-pad DOWN/UP to prevent Compose focus search crash on detached nodes
    var lastVerticalNavTime by remember { mutableStateOf(0L) }
    val verticalNavThrottleMs = 80L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastVerticalNavTime < verticalNavThrottleMs) {
                        true // Consume rapid repeat to prevent crash
                    } else {
                        lastVerticalNavTime = now
                        false // Allow normal processing
                    }
                } else {
                    false
                }
            }
    ) {
        // Tab row — hidden when a platform/streaming service tab is selected
        if (uiState.selectedPlatformTab == null) {
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

                // Main tabs: All, Movies, Series
                val tabs = listOf("All", "Movies", "Series")
                val mainTabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }
                tabs.forEachIndexed { index, tab ->
                    val isSelected = uiState.selectedTab == tab

                    Box(
                        modifier = Modifier
                            .focusRequester(mainTabFocusRequesters[index])
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (index > 0) {
                                                try { mainTabFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                                true
                                            } else false // let sidebar open
                                        }
                                        Key.DirectionRight -> {
                                            if (index < tabs.size - 1) {
                                                try { mainTabFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                                true
                                            } else true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
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
        }

        // Genre tab row — curated genre collections (below main tabs)
        if (uiState.selectedPlatformTab == null) {
            val genreTabs = GENRE_TABS
            val genreTabFocusRequesters = remember { List(genreTabs.size) { FocusRequester() } }
            LazyRow(
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                itemsIndexed(genreTabs) { index, tab ->
                    val isSelected = uiState.selectedGenreTab?.id == tab.id
                    Box(
                        modifier = Modifier
                            .focusRequester(genreTabFocusRequesters[index])
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (index > 0) {
                                                try { genreTabFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                                true
                                            } else false
                                        }
                                        Key.DirectionRight -> {
                                            if (index < genreTabs.size - 1) {
                                                try { genreTabFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                                true
                                            } else true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        MerlotChip(
                            selected = isSelected,
                            onClick = { viewModel.onGenreTabSelected(tab) },
                            label = {
                                Text(
                                    tab.name,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary
                                )
                            }
                        )
                    }
                }
            }
        }

        // Genre & Year filter chip rows
        if (uiState.selectedPlatformTab == null && uiState.selectedGenreTab == null && (uiState.availableGenres.isNotEmpty() || uiState.availableYears.isNotEmpty())) {
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
                    val genreFocusRequesters = remember(uiState.availableGenres.size) { List(uiState.availableGenres.size) { FocusRequester() } }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        itemsIndexed(uiState.availableGenres) { index, genre ->
                            Box(
                                modifier = Modifier
                                    .focusRequester(genreFocusRequesters[index])
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionLeft -> {
                                                    if (index > 0) {
                                                        try { genreFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                                        true
                                                    } else false
                                                }
                                                Key.DirectionRight -> {
                                                    if (index < uiState.availableGenres.size - 1) {
                                                        try { genreFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                                        true
                                                    } else true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            ) {
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
                    val yearFocusRequesters = remember(uiState.availableYears.size) { List(uiState.availableYears.size) { FocusRequester() } }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        itemsIndexed(uiState.availableYears) { index, year ->
                            Box(
                                modifier = Modifier
                                    .focusRequester(yearFocusRequesters[index])
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionLeft -> {
                                                    if (index > 0) {
                                                        try { yearFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                                        true
                                                    } else false
                                                }
                                                Key.DirectionRight -> {
                                                    if (index < uiState.availableYears.size - 1) {
                                                        try { yearFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                                        true
                                                    } else true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            ) {
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
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        when {
            // Platform loading MUST be checked before isLoading — user may click a
            // streaming service while the main VOD catalogs are still loading
            uiState.isPlatformLoading && uiState.selectedPlatformTab != null -> {
                // Pulsing streaming service icon animation
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = androidx.compose.animation.core.EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = androidx.compose.animation.core.EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(uiState.selectedPlatformTab!!.bgColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = uiState.selectedPlatformTab!!.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Loading ${uiState.selectedPlatformTab!!.name}...",
                            color = MerlotColors.TextMuted.copy(alpha = alpha),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            // Genre tab loading
            uiState.isGenreTabLoading && uiState.selectedGenreTab != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MerlotColors.Accent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Loading ${uiState.selectedGenreTab!!.name}...",
                            color = MerlotColors.TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            // Genre tab selected with content — show sections as horizontal rows
            uiState.selectedGenreTab != null && uiState.genreTabSections.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        uiState.genreTabSections,
                        key = { it.key },
                        contentType = { "catalog_row" }
                    ) { section ->
                        val isFirst = section == uiState.genreTabSections.first()
                        CatalogSectionRow(
                            section = section,
                            onItemClick = { item ->
                                lastFocusedItemId = item.id
                                onNavigateToDetail(item.type, item.id)
                            },
                            onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                            favoriteIds = favoriteIds,
                            inTheaterIds = uiState.inTheaterIds,
                            firstCardFocusRequester = if (isFirst) firstCardFocusRequester else null,
                            focusRequesters = focusRequesters,
                            onItemFocused = { itemId -> lastFocusedItemId = itemId }
                        )
                    }
                }
            }
            // Genre tab selected but empty
            uiState.selectedGenreTab != null && uiState.genreTabSections.isEmpty() && !uiState.isGenreTabLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No content found for ${uiState.selectedGenreTab?.name}",
                        color = MerlotColors.TextMuted, fontSize = 14.sp
                    )
                }
            }
            // Only show main catalog loading when NO platform tab is selected
            uiState.isLoading && uiState.selectedPlatformTab == null -> {
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
            // Platform tab selected with content — show header + search + grid
            uiState.selectedPlatformTab != null && uiState.platformSections.isNotEmpty() -> {
                val displayItems = uiState.filteredPlatformItems
                val allItems = uiState.platformSections.flatMap { it.items }
                Column {
                    // Header with back arrow, logo, name, count
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.onPlatformTabSelected(null) }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MerlotColors.TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(uiState.selectedPlatformTab!!.bgColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = uiState.selectedPlatformTab!!.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            uiState.selectedPlatformTab!!.name,
                            color = MerlotColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${displayItems.size} of ${allItems.size}",
                            color = MerlotColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    // TV-friendly search bar: only opens keyboard on center/enter press
                    val searchFieldFocusRequester = remember { FocusRequester() }
                    var isSearchEditing by remember { mutableStateOf(false) }
                    var isSearchBoxFocused by remember { mutableStateOf(false) }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val platformFirstItemFocusRequester = remember { FocusRequester() }

                    if (isSearchEditing) {
                        // Active editing mode — show real text field
                        OutlinedTextField(
                            value = uiState.platformSearchQuery,
                            onValueChange = { viewModel.onPlatformSearchQueryChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .height(48.dp)
                                .focusRequester(searchFieldFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                                        isSearchEditing = false
                                        keyboardController?.hide()
                                        // Return focus to first grid item
                                        try { platformFirstItemFocusRequester.requestFocus() } catch (_: Exception) {}
                                        true
                                    } else false
                                },
                            placeholder = {
                                Text(
                                    "Search ${uiState.selectedPlatformTab!!.name}...",
                                    color = MerlotColors.TextMuted,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MerlotColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.platformSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onPlatformSearchQueryChanged("") }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MerlotColors.TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MerlotColors.TextPrimary,
                                unfocusedTextColor = MerlotColors.TextPrimary,
                                cursorColor = MerlotColors.Accent,
                                focusedBorderColor = MerlotColors.Accent,
                                unfocusedBorderColor = MerlotColors.Surface2,
                                focusedContainerColor = MerlotColors.Surface,
                                unfocusedContainerColor = MerlotColors.Surface
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )
                        // Auto-focus the text field when entering edit mode
                        LaunchedEffect(Unit) {
                            try { searchFieldFocusRequester.requestFocus() } catch (_: Exception) {}
                            keyboardController?.show()
                        }
                    } else {
                        // D-pad friendly: looks like a search bar but is just a focusable Box
                        // Keyboard ONLY opens when center/enter is pressed — skip on D-pad up/down nav
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MerlotColors.Surface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSearchBoxFocused) MerlotColors.Accent else MerlotColors.Surface2,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusProperties {
                                    // Skip this search box during vertical D-pad navigation
                                    // Users can only focus it via explicit left/right navigation
                                    canFocus = false
                                }
                                .onFocusChanged { isSearchBoxFocused = it.isFocused }
                                .clickable {
                                    // Allow mouse/touch to open search
                                    isSearchEditing = true
                                }
                                .focusable(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MerlotColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.platformSearchQuery.ifEmpty {
                                        "Search ${uiState.selectedPlatformTab!!.name}..."
                                    },
                                    color = if (uiState.platformSearchQuery.isEmpty()) MerlotColors.TextMuted
                                            else MerlotColors.TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (uiState.platformSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onPlatformSearchQueryChanged("") }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MerlotColors.TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (displayItems.isEmpty() && uiState.platformSearchQuery.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No results for \"${uiState.platformSearchQuery}\"",
                                color = MerlotColors.TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayItems, key = { it.id }, contentType = { "vodcard" }) { item ->
                                val isFirstItem = item == displayItems.firstOrNull()
                                val itemFocusRequester = remember(item.id) {
                                    focusRequesters.getOrPut(item.id) { FocusRequester() }
                                }
                                VodCard(
                                    item = item,
                                    onClick = {
                                        lastFocusedItemId = item.id
                                        onNavigateToDetail(item.type, item.id)
                                    },
                                    onLongClick = { viewModel.toggleFavorite(item) },
                                    isFavorite = item.id in favoriteIds,
                                    focusRequester = if (isFirstItem) firstPlatformItemFocusRequester else itemFocusRequester,
                                    onFocused = { lastFocusedItemId = item.id }
                                )
                            }
                        }
                    }
                }
            }
            // Platform tab selected but no content found
            uiState.selectedPlatformTab != null && uiState.platformSections.isEmpty() && !uiState.isPlatformLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No ${uiState.selectedPlatformTab?.name} content found for ${uiState.selectedTab}",
                        color = MerlotColors.TextMuted, fontSize = 14.sp
                    )
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
                        items(allItems, key = { it.id }, contentType = { "vodcard" }) { item ->
                            val itemFocusRequester = remember(item.id) {
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
                                isInTheaters = item.id in uiState.inTheaterIds,
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
                            key = { "${it.addonName}_${it.catalogId}_${it.type}_${it.title}" },
                            contentType = { "catalog_row" }
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
                                inTheaterIds = uiState.inTheaterIds,
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
    inTheaterIds: Set<String> = emptySet(),
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
            // No logo — clean text-only section headers

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
            items(section.items, key = { it.id }, contentType = { "vodcard" }) { item ->
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
                    isInTheaters = item.id in inTheaterIds,
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
    isInTheaters: Boolean = false,
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

    // Lightweight tween scale — no spring overhead, smaller scale change
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "cardScale"
    )

    // Width: expands to landscape hero when trailer plays, normal poster otherwise
    val cardWidth by animateDpAsState(
        targetValue = when {
            isTrailerPlaying -> 280.dp
            else -> 130.dp  // Fixed size — no resize on focus
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardWidth"
    )

    // Height: switches to 16:9 landscape when trailer plays, fixed poster otherwise
    val cardHeight by animateDpAsState(
        targetValue = when {
            isTrailerPlaying -> 158.dp
            else -> 195.dp  // Fixed size — no resize on focus
        },
        animationSpec = tween(durationMillis = 200),
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

            // "In Theaters" badge
            if (isInTheaters) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.9f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "\uD83C\uDFAC IN THEATERS",
                        color = MerlotColors.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Favorite heart overlay (shows after long-press)
            if (showHeartOverlay) {
                val heartScale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 200),
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
            // Offset down when "In Theaters" badge is also shown
            if (isFavorite && !showHeartOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = if (isInTheaters) 22.dp else 4.dp)
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
