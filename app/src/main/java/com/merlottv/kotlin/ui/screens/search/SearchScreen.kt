package com.merlottv.kotlin.ui.screens.search

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.MetaPreview
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun SearchScreen(
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Voice input launcher for search
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onQueryChanged(spoken)
            }
        }
    }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search movies and series")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try { voiceLauncher.launch(speechIntent) } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChanged(it) },
            placeholder = { Text("Search movies & series...", color = MerlotColors.TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MerlotColors.TextMuted) },
            trailingIcon = {
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        try { voiceLauncher.launch(speechIntent) } catch (_: Exception) {}
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Icon(painter = painterResource(com.merlottv.kotlin.R.drawable.ic_mic), "Voice search", tint = MerlotColors.Accent, modifier = Modifier.size(20.dp))
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MerlotColors.TextPrimary,
                unfocusedTextColor = MerlotColors.TextPrimary,
                cursorColor = MerlotColors.Accent,
                focusedBorderColor = MerlotColors.Accent,
                unfocusedBorderColor = MerlotColors.Border
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MerlotColors.Accent)
                }
            }
            uiState.results.isEmpty() && uiState.query.isNotEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results found", color = MerlotColors.TextMuted, fontSize = 14.sp)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    contentPadding = PaddingValues(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.results) { item ->
                        SearchResultCard(item = item, onClick = { onNavigateToDetail(item.type, item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: MetaPreview, onClick: () -> Unit) {
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
