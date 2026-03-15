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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun VodScreen(
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: VodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val firstCardFocusRequester = remember { FocusRequester() }

    // Request focus on first content card when content loads
    LaunchedEffect(uiState.filteredSections.isNotEmpty()) {
        if (uiState.filteredSections.isNotEmpty()) {
            delay(300)
            try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
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
                var isFocused by remember { mutableStateOf(false) }

                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.onTabSelected(tab) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (tab) {
                                "Movies" -> Icon(Icons.Default.Movie, null, modifier = Modifier.size(14.dp))
                                "Series" -> Icon(Icons.Default.Tv, null, modifier = Modifier.size(14.dp))
                                else -> {}
                            }
                            if (tab != "All") Spacer(modifier = Modifier.width(4.dp))
                            Text(tab, fontSize = 12.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (isFocused) Color(0xFF555555) else MerlotColors.Surface2,
                        labelColor = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
                        iconColor = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
                        selectedContainerColor = MerlotColors.Accent,
                        selectedLabelColor = MerlotColors.Black,
                        selectedLeadingIconColor = MerlotColors.Black,
                        selectedTrailingIconColor = MerlotColors.Black
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isFocused) Color(0xFF888888) else MerlotColors.Border,
                        selectedBorderColor = MerlotColors.Accent,
                        borderWidth = if (isFocused) 2.dp else 1.dp,
                        selectedBorderWidth = 1.dp,
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
                                viewModel.onTabSelected(tab)
                                true
                            } else false
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
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(
                        uiState.filteredSections,
                        key = { "${it.addonName}_${it.catalogId}_${it.type}" }
                    ) { section ->
                        val isFirst = section == uiState.filteredSections.first()
                        CatalogSectionRow(
                            section = section,
                            onItemClick = { item ->
                                onNavigateToDetail(item.type, item.id)
                            },
                            firstCardFocusRequester = if (isFirst) firstCardFocusRequester else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionRow(
    section: CatalogSection,
    onItemClick: (MetaPreview) -> Unit,
    firstCardFocusRequester: FocusRequester? = null
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
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(section.items, key = { it.id }) { item ->
                val isFirst = firstCardFocusRequester != null && item == section.items.first()
                VodCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    focusRequester = if (isFirst) firstCardFocusRequester else null
                )
            }
        }
    }
}

@Composable
private fun VodCard(
    item: MetaPreview,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(130.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
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
    ) {
        Box {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
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
