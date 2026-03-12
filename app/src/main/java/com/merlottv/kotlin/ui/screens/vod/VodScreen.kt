@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        // Sub-tabs: Home, Movies, Series
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Home", "Movies", "Series").forEach { tab ->
                FilterChip(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    label = { Text(tab, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MerlotColors.Accent,
                        selectedLabelColor = MerlotColors.Black,
                        containerColor = MerlotColors.Surface2,
                        labelColor = MerlotColors.TextPrimary
                    )
                )
            }
        }

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.items) { item ->
                        VodCard(item = item, onClick = { onNavigateToDetail(item.type, item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VodCard(item: MetaPreview, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.name,
            modifier = Modifier
                .width(130.dp)
                .height(195.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MerlotColors.Surface2),
            contentScale = ContentScale.Crop
        )
        Text(
            text = item.name,
            color = MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (item.imdbRating.isNotEmpty()) {
            Text(
                text = "\u2B50 ${item.imdbRating}",
                color = MerlotColors.Warn,
                fontSize = 10.sp
            )
        }
    }
}
