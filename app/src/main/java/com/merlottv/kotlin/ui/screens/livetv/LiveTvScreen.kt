package com.merlottv.kotlin.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.domain.model.Channel
import com.merlottv.kotlin.ui.theme.MerlotColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        // Channel sidebar
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MerlotColors.Surface)
                .border(width = 1.dp, color = MerlotColors.Border)
        ) {
            // Search
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchChanged(it) },
                placeholder = { Text("Search channels...", color = MerlotColors.TextMuted, fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MerlotColors.TextPrimary,
                    unfocusedTextColor = MerlotColors.TextPrimary,
                    cursorColor = MerlotColors.Accent,
                    focusedBorderColor = MerlotColors.Accent,
                    unfocusedBorderColor = MerlotColors.Border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                shape = RoundedCornerShape(8.dp)
            )

            // Group selector
            var groupExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = groupExpanded,
                onExpandedChange = { groupExpanded = it },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = uiState.selectedGroup ?: "All Groups (${uiState.totalChannels})",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = MerlotColors.TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MerlotColors.Accent,
                        unfocusedBorderColor = MerlotColors.Border
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(
                    expanded = groupExpanded,
                    onDismissRequest = { groupExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Groups (${uiState.totalChannels})", fontSize = 11.sp) },
                        onClick = { viewModel.onGroupSelected(null); groupExpanded = false }
                    )
                    uiState.groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group, fontSize = 11.sp) },
                            onClick = { viewModel.onGroupSelected(group); groupExpanded = false }
                        )
                    }
                }
            }

            // Channel list
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MerlotColors.Accent, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.filteredChannels) { channel ->
                        ChannelItem(
                            channel = channel,
                            isSelected = channel.id == uiState.selectedChannel?.id,
                            isFavorite = uiState.favoriteIds.contains(channel.id),
                            onClick = { viewModel.onChannelSelected(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) }
                        )
                    }
                }
            }
        }

        // Player area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MerlotColors.Black)
        ) {
            if (uiState.selectedChannel != null) {
                AndroidView(
                    factory = { context ->
                        androidx.media3.ui.PlayerView(context).apply {
                            useController = true
                        }
                    },
                    update = { playerView ->
                        playerView.player = viewModel.player
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Channel info overlay at bottom
                uiState.selectedChannel?.let { ch ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2.copy(alpha = 0.9f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (ch.logoUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ch.logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ch.name, color = MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(ch.group, color = MerlotColors.TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            } else {
                // Idle state
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = MerlotColors.TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("MERLOT TV", color = MerlotColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Select a channel from the list to start watching",
                        color = MerlotColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isSelected) MerlotColors.AccentAlpha10 else MerlotColors.Transparent)
            .then(
                if (isSelected) Modifier.border(
                    width = 0.dp,
                    color = MerlotColors.Transparent
                ) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MerlotColors.Surface2)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MerlotColors.Surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(2).uppercase(),
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isSelected) MerlotColors.Accent else MerlotColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.group.isNotEmpty()) {
                Text(
                    text = channel.group,
                    color = MerlotColors.TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MerlotColors.Accent else MerlotColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "${channel.number}",
            color = MerlotColors.TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.width(24.dp)
        )
    }
}
