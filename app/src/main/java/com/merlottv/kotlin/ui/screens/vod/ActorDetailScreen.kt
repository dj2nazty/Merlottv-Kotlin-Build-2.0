package com.merlottv.kotlin.ui.screens.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.TmdbFilmCredit
import kotlinx.coroutines.launch

@Composable
fun ActorDetailScreen(
    onBack: () -> Unit,
    onNavigateToDetail: ((String, String) -> Unit)? = null,
    viewModel: ActorDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBack()
                    true
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Actor name header
            Text(
                text = uiState.personName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Filmography",
                color = Color(0xFFAAAAAA),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6200EE))
                }
            } else if (uiState.filmography.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No filmography found", color = Color(0xFF888888), fontSize = 16.sp)
                }
            } else {
                FilmographyGrid(
                    films = uiState.filmography,
                    favoriteIds = favoriteIds,
                    onResolveAndNavigate = { film ->
                        viewModel.resolveImdbId(film)
                    },
                    onToggleFavorite = { film ->
                        viewModel.toggleFavorite(film)
                    },
                    onNavigateToDetail = onNavigateToDetail
                )
            }
        }
    }
}

@Composable
private fun FilmographyGrid(
    films: List<TmdbFilmCredit>,
    favoriteIds: Set<String>,
    onResolveAndNavigate: suspend (TmdbFilmCredit) -> String?,
    onToggleFavorite: (TmdbFilmCredit) -> Unit,
    onNavigateToDetail: ((String, String) -> Unit)?
) {
    val scope = rememberCoroutineScope()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(films, key = { "${it.id}_${it.type}" }) { film ->
            FilmCard(
                film = film,
                isFavorite = favoriteIds.contains(
                    film.imdbId.ifEmpty { "tmdb:${film.id}" }
                ),
                onClick = {
                    scope.launch {
                        val imdbId = onResolveAndNavigate(film)
                        if (imdbId != null && onNavigateToDetail != null) {
                            onNavigateToDetail(film.type, imdbId)
                        }
                    }
                },
                onLongPress = { onToggleFavorite(film) }
            )
        }
    }
}

@Composable
private fun FilmCard(
    film: TmdbFilmCredit,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableLongStateOf(0L) }

    val borderColor = when {
        isFavorite && isFocused -> Color(0xFFFF4081)
        isFocused -> Color(0xFF6200EE)
        isFavorite -> Color(0xFFFF4081).copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.DirectionCenter) -> {
                        pressStartTime = System.currentTimeMillis()
                        false
                    }
                    event.type == KeyEventType.KeyUp &&
                        (event.key == Key.Enter || event.key == Key.DirectionCenter) -> {
                        val duration = System.currentTimeMillis() - pressStartTime
                        if (duration > 600) {
                            onLongPress()
                        } else {
                            onClick()
                        }
                        pressStartTime = 0L
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        // Poster
        AsyncImage(
            model = film.posterUrl,
            contentDescription = film.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            contentScale = ContentScale.Crop
        )

        // Info
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = film.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (film.year.isNotEmpty()) {
                    Text(text = film.year, color = Color(0xFFAAAAAA), fontSize = 11.sp)
                }
                if (film.voteAverage.isNotEmpty()) {
                    Text(
                        text = film.voteAverage,
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (film.character.isNotEmpty()) {
                Text(
                    text = "as ${film.character}",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (isFavorite) {
                Text(
                    text = "Favorited",
                    color = Color(0xFFFF4081),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
