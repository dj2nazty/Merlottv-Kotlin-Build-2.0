@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.merlottv.kotlin.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.merlottv.kotlin.ui.components.MerlotChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    // Build the full tab list for FocusRequester indexing
    val builtInTabs = listOf("All", "Movies", "Series", "Channels")
    val customTabNames = uiState.customLists.keys.toList()
    val allTabs = builtInTabs + customTabNames + listOf("+") // "+" is the add button
    val tabFocusRequesters = remember(allTabs.size) { List(allTabs.size) { FocusRequester() } }

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
                .horizontalScroll(rememberScrollState())
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

            // Render all tabs with D-pad Left/Right navigation
            allTabs.forEachIndexed { index, tab ->
                val isSelected = when (tab) {
                    "+" -> false
                    else -> uiState.selectedTab == tab
                }
                val isCustom = customTabNames.contains(tab)

                MerlotChip(
                    selected = isSelected,
                    onClick = {
                        when (tab) {
                            "+" -> viewModel.showCreateListDialog()
                            else -> viewModel.selectTab(tab)
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary
                            when (tab) {
                                "Movies" -> Icon(Icons.Default.Movie, null, modifier = Modifier.height(14.dp), tint = tint)
                                "Series" -> Icon(Icons.Default.Tv, null, modifier = Modifier.height(14.dp), tint = tint)
                                "Channels" -> Icon(Icons.Default.LiveTv, null, modifier = Modifier.height(14.dp), tint = tint)
                                "+" -> Icon(Icons.Default.Add, contentDescription = "Create list", modifier = Modifier.height(14.dp), tint = MerlotColors.TextPrimary)
                                "All" -> {}
                                else -> {
                                    Icon(Icons.Default.List, null, modifier = Modifier.height(14.dp), tint = tint)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                            if (tab != "+" && tab != "All" && !isCustom) Spacer(modifier = Modifier.width(4.dp))
                            if (tab != "+") {
                                Text(tab, fontSize = 12.sp, color = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary)
                            }
                        }
                    },
                    modifier = Modifier
                        .focusRequester(tabFocusRequesters[index])
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (index > 0) {
                                            try { tabFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                            true
                                        } else true // consume on first tab — block sidebar
                                    }
                                    Key.DirectionRight -> {
                                        if (index < allTabs.size - 1) {
                                            try { tabFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                        }
                                        true // always consume right
                                    }
                                    // Menu key on custom list tab → rename dialog
                                    Key.Menu -> {
                                        if (isCustom) {
                                            viewModel.showRenameDialog(tab)
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }

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

        // Edit/Delete buttons for custom lists (shown when a custom list is selected)
        val isCustomList = uiState.customLists.containsKey(uiState.selectedTab)
        if (isCustomList) {
            val renameFocusRequester = remember { FocusRequester() }
            val deleteFocusRequester = remember { FocusRequester() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Rename button
                run {
                    var isFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) FocusedGrey else MerlotColors.Surface2)
                            .focusRequester(renameFocusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter -> {
                                            viewModel.showRenameDialog(uiState.selectedTab)
                                            true
                                        }
                                        Key.DirectionLeft -> true // first button — consume to block sidebar
                                        Key.DirectionRight -> {
                                            deleteFocusRequester.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, null, tint = MerlotColors.Accent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rename", color = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary, fontSize = 11.sp)
                    }
                }
                // Delete button
                run {
                    var isFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) Color(0xFF8B0000) else MerlotColors.Surface2)
                            .focusRequester(deleteFocusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter -> {
                                            viewModel.deleteList(uiState.selectedTab)
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            renameFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> true // last button — consume to block
                                        else -> false
                                    }
                                } else false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete List", color = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
                val isCustom = uiState.customLists.containsKey(uiState.selectedTab)
                val items = uiState.filteredVodMetas
                if (items.isEmpty() && !isCustom && uiState.favoriteVodIds.isEmpty()) {
                    EmptyState("No favorites yet", "Add movies or shows to your favorites")
                } else if (items.isEmpty() && isCustom) {
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
                                isWatched = uiState.watchedVodIds.contains(meta.id),
                                onClick = { onNavigateToDetail(meta.type, meta.id) },
                                onMenuPress = { viewModel.showItemMenu(meta) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ═══ Create list dialog ═══
    if (uiState.showCreateListDialog) {
        DialogOverlay(
            title = "Create New List",
            textValue = uiState.newListName,
            onTextChange = { viewModel.updateNewListName(it) },
            textLabel = "List name",
            confirmText = "Create",
            onConfirm = { viewModel.createList(uiState.newListName) },
            onDismiss = { viewModel.hideCreateListDialog() }
        )
    }

    // ═══ Rename list dialog ═══
    if (uiState.showRenameDialog) {
        DialogOverlay(
            title = "Rename \"${uiState.renameListTarget}\"",
            textValue = uiState.renameListName,
            onTextChange = { viewModel.updateRenameListName(it) },
            textLabel = "New name",
            confirmText = "Rename",
            onConfirm = { viewModel.confirmRename() },
            onDismiss = { viewModel.hideRenameDialog() }
        )
    }

    // ═══ Item context menu (Menu button on poster) ═══
    if (uiState.showItemMenu && uiState.itemMenuTarget != null) {
        val target = uiState.itemMenuTarget!!
        val isWatched = uiState.watchedVodIds.contains(target.id)
        val currentTabIsCustom = uiState.customLists.containsKey(uiState.selectedTab)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Menu)) {
                        if (uiState.showAddToListMenu) {
                            viewModel.hideAddToListSubmenu()
                        } else {
                            viewModel.hideItemMenu()
                        }
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.94f))
                    .border(1.dp, Color(0xFF888888), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = target.name.ifEmpty { target.id },
                    color = MerlotColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (!uiState.showAddToListMenu) {
                    // Main menu options
                    MenuButton(
                        icon = if (isWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        text = if (isWatched) "Mark as Unwatched" else "Mark as Watched",
                        onClick = {
                            viewModel.toggleWatched(target.id)
                            viewModel.hideItemMenu()
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (currentTabIsCustom) {
                        MenuButton(
                            icon = Icons.Default.RemoveCircleOutline,
                            text = "Remove from \"${uiState.selectedTab}\"",
                            onClick = {
                                viewModel.removeFromList(uiState.selectedTab, target.id)
                                viewModel.hideItemMenu()
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    MenuButton(
                        icon = Icons.Default.Close,
                        text = "Remove from Favorites",
                        tint = Color(0xFFFF6B6B),
                        onClick = {
                            viewModel.removeFavorite(target.id)
                            viewModel.hideItemMenu()
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (uiState.customLists.isNotEmpty()) {
                        MenuButton(
                            icon = Icons.Default.PlaylistAdd,
                            text = "Add to List...",
                            onClick = { viewModel.showAddToListSubmenu() }
                        )
                    }
                } else {
                    // Add to list submenu — show each custom list
                    Text(
                        "Add to List",
                        color = MerlotColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    uiState.customLists.keys.forEach { listName ->
                        val alreadyInList = uiState.customLists[listName]?.contains(target.id) == true
                        MenuButton(
                            icon = if (alreadyInList) Icons.Default.Check else Icons.Default.List,
                            text = if (alreadyInList) "$listName (already added)" else listName,
                            tint = if (alreadyInList) MerlotColors.TextMuted else MerlotColors.Accent,
                            onClick = {
                                if (!alreadyInList) {
                                    viewModel.addToList(listName, target.id)
                                }
                                viewModel.hideItemMenu()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    } // end outer Box
}

// ═══ Reusable dialog for Create / Rename ═══
@Composable
private fun DialogOverlay(
    title: String,
    textValue: String,
    onTextChange: (String) -> Unit,
    textLabel: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val textFieldFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }

    // Auto-focus the text field when dialog opens
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try { textFieldFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
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
                text = title,
                color = MerlotColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = textValue,
                onValueChange = onTextChange,
                label = { Text(textLabel, color = MerlotColors.TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MerlotColors.White,
                    unfocusedTextColor = MerlotColors.TextPrimary,
                    focusedBorderColor = MerlotColors.Accent,
                    unfocusedBorderColor = Color(0xFF888888),
                    cursorColor = MerlotColors.Accent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFieldFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    cancelFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionLeft, Key.DirectionRight -> true // consume to stay in text field
                                else -> false
                            }
                        } else false
                    }
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
                            .focusRequester(cancelFocusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter -> {
                                            onDismiss()
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            textFieldFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            confirmFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionLeft -> true // consume — block sidebar
                                        Key.DirectionDown -> true // consume — nothing below
                                        else -> false
                                    }
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

                // Confirm button
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
                            .focusRequester(confirmFocusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter -> {
                                            onConfirm()
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            textFieldFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            cancelFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> true // consume — last button
                                        Key.DirectionDown -> true // consume — nothing below
                                        else -> false
                                    }
                                } else false
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = confirmText,
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

// ═══ Menu button for item context menu ═══
@Composable
private fun MenuButton(
    icon: ImageVector,
    text: String,
    tint: Color = MerlotColors.Accent,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) FocusedGrey else MerlotColors.Surface2)
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
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
private fun FavoritePosterCard(
    meta: FavoriteVodMeta,
    isWatched: Boolean,
    onClick: () -> Unit,
    onMenuPress: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            onClick()
                            true
                        }
                        Key.Menu -> {
                            onMenuPress()
                            true
                        }
                        else -> false
                    }
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
                contentScale = ContentScale.Crop,
                alpha = if (isWatched) 0.5f else 1f
            )

            // Watched badge
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = MerlotColors.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

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

            // "Menu" hint on focus
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MerlotColors.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "☰ Menu",
                        color = MerlotColors.TextMuted,
                        fontSize = 7.sp
                    )
                }
            }
        }

        Text(
            text = meta.name,
            color = if (isFocused) MerlotColors.Accent else if (isWatched) MerlotColors.TextMuted else MerlotColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
