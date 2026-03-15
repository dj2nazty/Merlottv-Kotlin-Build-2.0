@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.favorites

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import com.merlottv.kotlin.ui.components.MerlotChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.merlottv.kotlin.data.local.FavoriteVodMeta
import com.merlottv.kotlin.ui.theme.MerlotColors

private val FocusedGrey = Color(0xFF666666)

@Composable
fun FavoritesScreen(
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        // Header + Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Favorites",
                color = MerlotColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(16.dp))

            val builtInTabs = listOf("All", "Movies", "Series", "Channels")
            builtInTabs.forEach { tab ->
                val isSelected = uiState.selectedTab == tab

                MerlotChip(
                    selected = isSelected,
                    onClick = { viewModel.selectTab(tab) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary
                            when (tab) {
                                "Movies" -> Icon(Icons.Default.Movie, null, modifier = Modifier.height(14.dp), tint = tint)
                                "Series" -> Icon(Icons.Default.Tv, null, modifier = Modifier.height(14.dp), tint = tint)
                                "Channels" -> Icon(Icons.Default.LiveTv, null, modifier = Modifier.height(14.dp), tint = tint)
                                else -> {}
                            }
                            if (tab != "All") Spacer(modifier = Modifier.width(4.dp))
                            Text(tab, fontSize = 12.sp, color = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary)
                        }
                    }
                )
            }

            // Custom list tabs
            uiState.customLists.keys.forEach { listName ->
                val isSelected = uiState.selectedTab == listName

                MerlotChip(
                    selected = isSelected,
                    onClick = { viewModel.selectTab(listName) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary
                            Icon(Icons.Default.List, null, modifier = Modifier.height(14.dp), tint = tint)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(listName, fontSize = 12.sp, color = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary)
                        }
                    }
                )
            }

            // "+" button to create a new list
            MerlotChip(
                selected = false,
                onClick = { viewModel.showCreateListDialog() },
                label = {
                    Icon(Icons.Default.Add, contentDescription = "Create list", modifier = Modifier.height(14.dp), tint = MerlotColors.TextPrimary)
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Count
            val count = when (uiState.selectedTab) {
                "Channels" -> uiState.favoriteChannelIds.size
                else -> uiState.filteredVodMetas.size
            }
            if (count > 0) {
                Text(
                    text = "$count items",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Content
        when (uiState.selectedTab) {
            "Channels" -> {
                // Channel favorites
                if (uiState.favoriteChannelIds.isEmpty()) {
                    EmptyState("No favorite channels yet", "Add channels from Live TV")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(200.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.favoriteChannelIds.toList()) { channelId ->
                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isFocused) FocusedGrey.copy(alpha = 0.3f) else MerlotColors.Surface2
                                    )
                                    .then(
                                        if (isFocused) Modifier.border(2.dp, FocusedGrey, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .padding(12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = channelId,
                                    color = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // VOD favorites (All / Movies / Series / Custom lists)
                val isCustomList = uiState.customLists.containsKey(uiState.selectedTab)
                val items = uiState.filteredVodMetas
                if (items.isEmpty() && !isCustomList && uiState.favoriteVodIds.isEmpty()) {
                    EmptyState("No favorites yet", "Add movies or shows to your favorites")
                } else if (items.isEmpty() && isCustomList) {
                    EmptyState(
                        "\"${uiState.selectedTab}\" is empty",
                        "Add VOD items to this list"
                    )
                } else if (items.isEmpty()) {
                    EmptyState(
                        "No ${uiState.selectedTab.lowercase()} in favorites",
                        "Try a different tab"
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(130.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items, key = { it.id }) { meta ->
                            FavoritePosterCard(
                                meta = meta,
                                onClick = { onNavigateToDetail(meta.type, meta.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create list dialog overlay
    if (uiState.showCreateListDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        viewModel.hideCreateListDialog()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.92f))
                    .border(1.dp, Color(0xFF888888), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create New List",
                    color = MerlotColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.newListName,
                    onValueChange = { viewModel.updateNewListName(it) },
                    label = { Text("List name", color = MerlotColors.TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MerlotColors.White,
                        unfocusedTextColor = MerlotColors.TextPrimary,
                        focusedBorderColor = MerlotColors.Accent,
                        unfocusedBorderColor = Color(0xFF888888),
                        cursorColor = MerlotColors.Accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Cancel button
                    run {
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) FocusedGrey else MerlotColors.Surface2)
                                .then(
                                    if (isFocused) Modifier.border(2.dp, Color(0xFF888888), RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                    ) {
                                        viewModel.hideCreateListDialog()
                                        true
                                    } else false
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                color = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Create button
                    run {
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) FocusedGrey else MerlotColors.Accent)
                                .then(
                                    if (isFocused) Modifier.border(2.dp, Color(0xFF888888), RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                    ) {
                                        viewModel.createList(uiState.newListName)
                                        true
                                    } else false
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create",
                                color = if (isFocused) MerlotColors.White else MerlotColors.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
    } // end outer Box
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = MerlotColors.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(title, color = MerlotColors.TextMuted, fontSize = 14.sp)
            Text(subtitle, color = MerlotColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FavoritePosterCard(meta: FavoriteVodMeta, onClick: () -> Unit) {
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
                    onClick()
                    true
                } else false
            }
    ) {
        Box {
            AsyncImage(
                model = meta.poster.ifEmpty { null },
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

            // Rating badge
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

            // Type badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (meta.type == "movie") MerlotColors.Accent.copy(alpha = 0.8f)
                        else MerlotColors.Success.copy(alpha = 0.8f)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (meta.type == "movie") "MOVIE" else "SERIES",
                    color = MerlotColors.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Focus overlay with description
            if (isFocused && meta.description.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(MerlotColors.Transparent, MerlotColors.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = meta.description,
                        color = MerlotColors.White,
                        fontSize = 9.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        Text(
            text = meta.name,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
