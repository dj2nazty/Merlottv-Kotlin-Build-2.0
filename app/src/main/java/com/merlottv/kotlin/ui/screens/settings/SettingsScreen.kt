package com.merlottv.kotlin.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item(key = "title") {
            Text(
                text = "Settings",
                color = MerlotColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ═══ About Section (FIRST — version + update check) ═══
        item(key = "about") {
            SettingsSection(title = "About", icon = { Icon(Icons.Default.Info, null, tint = MerlotColors.Accent) }) {
                Text("Merlot TV", color = MerlotColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Kotlin Build 2.0", color = MerlotColors.Accent, fontSize = 12.sp)
                Text("Version ${uiState.appVersion} (Build ${com.merlottv.kotlin.BuildConfig.VERSION_CODE})", color = MerlotColors.TextMuted, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.updateAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Accent.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Update Available!", color = MerlotColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Version ${uiState.latestVersion}", color = MerlotColors.TextMuted, fontSize = 11.sp)
                        }
                        if (uiState.updateUrl.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateUrl))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Download", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { viewModel.checkForUpdate() },
                    enabled = !uiState.isCheckingUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Surface2, contentColor = MerlotColors.TextPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.focusable()
                ) {
                    if (uiState.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MerlotColors.Accent, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Checking...", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check for Updates", fontSize = 11.sp)
                    }
                }
            }
        }

        // ═══ Speed Test Section (SECOND) ═══
        item(key = "speed_test") {
            SettingsSection(title = "Internet Speed Test", icon = { Icon(Icons.Default.Refresh, null, tint = MerlotColors.Accent) }) {
                if (uiState.isRunningSpeedTest) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = MerlotColors.Accent,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Running speed test...", color = MerlotColors.TextPrimary, fontSize = 12.sp)
                            if (uiState.downloadSpeed.isNotEmpty()) {
                                Text("↓ Download: ${uiState.downloadSpeed}", color = MerlotColors.Accent, fontSize = 11.sp)
                                Text("Testing upload...", color = MerlotColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                } else {
                    if (uiState.downloadSpeed.isNotEmpty() || uiState.uploadSpeed.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("↓ Download", color = MerlotColors.TextMuted, fontSize = 10.sp)
                                Text(uiState.downloadSpeed, color = MerlotColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("↑ Upload", color = MerlotColors.TextMuted, fontSize = 10.sp)
                                Text(uiState.uploadSpeed, color = MerlotColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (uiState.speedTestError.isNotEmpty()) {
                        Text("Error: ${uiState.speedTestError}", color = MerlotColors.Danger, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.runSpeedTest() },
                        colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.focusable()
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Speed Test", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ═══ Profiles Section ═══
        item(key = "profiles") {
            SettingsSection(title = "Profiles", icon = { Icon(Icons.Default.Person, null, tint = MerlotColors.Accent) }) {
                Text("Each profile has its own favorites and watch history.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                uiState.profiles.forEach { profile ->
                    val isActive = profile.id == uiState.activeProfileId
                    val avatarColor = ProfileDataStore.AVATAR_COLORS.getOrElse(profile.colorIndex) { 0xFF00E5FF.toInt() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isActive) MerlotColors.Surface2 else MerlotColors.Surface,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.switchProfile(profile.id) }
                            .focusable()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(avatarColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.name.take(1).uppercase(),
                                color = MerlotColors.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, color = MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (isActive) Text("Active", color = MerlotColors.Accent, fontSize = 9.sp)
                        }
                        if (isActive) {
                            Icon(Icons.Default.Check, null, tint = MerlotColors.Accent, modifier = Modifier.size(18.dp))
                        }
                        if (!profile.isDefault) {
                            IconButton(onClick = { viewModel.removeProfile(profile.id) }) {
                                Icon(Icons.Default.Delete, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (uiState.profiles.size < 6) {
                    Spacer(modifier = Modifier.height(8.dp))
                    var newProfileName by remember { mutableStateOf("") }
                    var selectedColor by remember { mutableIntStateOf(0) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DpadTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            placeholder = { Text("Profile name", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.addProfile(newProfileName, selectedColor)
                                newProfileName = ""
                                selectedColor = (selectedColor + 1) % ProfileDataStore.AVATAR_COLORS.size
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                            shape = RoundedCornerShape(8.dp),
                            enabled = newProfileName.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileDataStore.AVATAR_COLORS.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .clickable { selectedColor = index }
                                    .focusable(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (index == selectedColor) {
                                    Icon(Icons.Default.Check, null, tint = MerlotColors.Black, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══ Playlists Section ═══
        item(key = "playlists") {
            SettingsSection(title = "Playlists / M3U", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                Text("Add multiple playlists. All enabled playlists load in Live TV.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                uiState.playlists.forEachIndexed { index, playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(playlist.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1)
                        }
                        Switch(
                            checked = playlist.enabled,
                            onCheckedChange = { viewModel.togglePlaylist(index) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MerlotColors.Accent,
                                checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                        IconButton(onClick = { viewModel.removePlaylist(index) }) {
                            Icon(Icons.Default.Close, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                var playlistName by remember { mutableStateOf("") }
                var playlistUrl by remember { mutableStateOf("") }
                DpadTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Playlist name", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DpadTextField(
                        value = playlistUrl,
                        onValueChange = { playlistUrl = it },
                        placeholder = { Text("https://playlist-url.m3u", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.addPlaylist(playlistName, playlistUrl)
                            playlistName = ""
                            playlistUrl = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                        shape = RoundedCornerShape(8.dp),
                        enabled = playlistUrl.isNotBlank()
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // ═══ EPG Sources Section ═══
        item(key = "epg") {
            SettingsSection(title = "EPG Sources", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                Text("Default sources are always loaded. Add custom EPG sources below.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                uiState.defaultEpgSources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(source.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1)
                        }
                        Text("built-in", color = MerlotColors.Accent, fontSize = 9.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                uiState.customEpgSources.forEachIndexed { index, source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(source.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1)
                        }
                        IconButton(onClick = { viewModel.removeEpgSource(index) }) {
                            Icon(Icons.Default.Close, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                var epgName by remember { mutableStateOf("") }
                var epgUrl by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DpadTextField(
                            value = epgName,
                            onValueChange = { epgName = it },
                            placeholder = { Text("Source name", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DpadTextField(
                            value = epgUrl,
                            onValueChange = { epgUrl = it },
                            placeholder = { Text("https://epg-source.xml", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.addEpgSource(epgName, epgUrl)
                            epgName = ""
                            epgUrl = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                        shape = RoundedCornerShape(8.dp),
                        enabled = epgUrl.isNotBlank()
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // ═══ Stremio Addons Section ═══
        item(key = "addons") {
            SettingsSection(title = "Stremio Addons", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                Text("Default addons are built-in and cannot be removed.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                uiState.addons.forEach { addon ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(addon.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(addon.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1)
                        }
                        if (addon.isDefault) {
                            Text("built-in", color = MerlotColors.Accent, fontSize = 9.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                var addonInput by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DpadTextField(
                        value = addonInput,
                        onValueChange = { addonInput = it },
                        placeholder = { Text("https://addon.example.com/manifest.json", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.addAddon(addonInput); addonInput = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // ═══ Torbox Section ═══
        item(key = "torbox") {
            SettingsSection(title = "Torbox", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                var torboxInput by remember { mutableStateOf(uiState.torboxKey) }
                DpadTextField(
                    value = torboxInput,
                    onValueChange = { torboxInput = it },
                    label = { Text("Torbox API Key", color = MerlotColors.TextMuted) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveTorboxKey(torboxInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Bottom padding
        item(key = "spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MerlotColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = MerlotColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MerlotColors.TextPrimary,
    unfocusedTextColor = MerlotColors.TextPrimary,
    cursorColor = MerlotColors.Accent,
    focusedBorderColor = MerlotColors.Accent,
    unfocusedBorderColor = MerlotColors.Border
)

/**
 * D-pad friendly text field that only opens the keyboard when the user
 * explicitly presses the center/OK button. Navigating over it with
 * D-pad arrows will NOT trigger the soft keyboard.
 */
@Composable
private fun DpadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Use interaction source to detect click/press (center button on D-pad)
    val interactionSource = remember { MutableInteractionSource() }

    // When interaction source gets a press, enter editing mode
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                isEditing = true
            }
        }
    }

    // When entering editing mode, request focus on the actual text field
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    if (isEditing) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            label = label,
            colors = settingsFieldColors(),
            modifier = modifier
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    // Back button exits editing mode
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                        isEditing = false
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { state ->
                    if (!state.isFocused && !state.hasFocus) {
                        isEditing = false
                    }
                },
            singleLine = singleLine,
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
        )
    } else {
        // Read-only appearance — focusable box that looks like a text field
        OutlinedTextField(
            value = value,
            onValueChange = {},
            placeholder = placeholder,
            label = label,
            colors = settingsFieldColors(),
            modifier = modifier,
            singleLine = singleLine,
            readOnly = true,
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
            interactionSource = interactionSource
        )
    }
}
