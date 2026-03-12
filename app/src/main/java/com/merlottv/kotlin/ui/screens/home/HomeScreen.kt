package com.merlottv.kotlin.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MerlotColors.Accent
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // Hero section
                    uiState.featuredItem?.let { featured ->
                        item {
                            HeroSection(
                                meta = featured,
                                onClick = { onNavigateToDetail(featured.type, featured.id) }
                            )
                        }
                    }

                    // Catalog rows
                    items(uiState.catalogRows) { row ->
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
private fun HeroSection(meta: MetaPreview, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = meta.poster,
            contentDescription = meta.name,
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
                            MerlotColors.Background.copy(alpha = 0.9f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                text = meta.name,
                color = MerlotColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item ->
                PosterCard(meta = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun PosterCard(meta: MetaPreview, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = meta.poster,
            contentDescription = meta.name,
            modifier = Modifier
                .width(130.dp)
                .height(195.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MerlotColors.Surface2),
            contentScale = ContentScale.Crop
        )
        Text(
            text = meta.name,
            color = MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (meta.imdbRating.isNotEmpty()) {
            Text(
                text = "\u2B50 ${meta.imdbRating}",
                color = MerlotColors.TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
