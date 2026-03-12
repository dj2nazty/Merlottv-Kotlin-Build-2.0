package com.merlottv.kotlin.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
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
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun FavoritesScreen(
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .padding(16.dp)
    ) {
        Text(
            text = "Favorites",
            color = MerlotColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (uiState.favoriteChannelIds.isEmpty() && uiState.favoriteVodIds.isEmpty()) {
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
                    Text(
                        "No favorites yet",
                        color = MerlotColors.TextMuted,
                        fontSize = 14.sp
                    )
                    Text(
                        "Add channels or movies to your favorites",
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            if (uiState.favoriteChannelIds.isNotEmpty()) {
                Text(
                    "Favorite Channels (${uiState.favoriteChannelIds.size})",
                    color = MerlotColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                uiState.favoriteChannelIds.forEach { id ->
                    Text(
                        text = id,
                        color = MerlotColors.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MerlotColors.Surface2)
                            .padding(12.dp)
                    )
                }
            }

            if (uiState.favoriteVodIds.isNotEmpty()) {
                Text(
                    "Favorite Movies & Series (${uiState.favoriteVodIds.size})",
                    color = MerlotColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                uiState.favoriteVodIds.forEach { id ->
                    Text(
                        text = id,
                        color = MerlotColors.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MerlotColors.Surface2)
                            .padding(12.dp)
                            .clickable { onNavigateToDetail("movie", id) }
                    )
                }
            }
        }
    }
}
