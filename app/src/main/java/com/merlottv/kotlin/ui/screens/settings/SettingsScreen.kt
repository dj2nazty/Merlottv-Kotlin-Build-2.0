package com.merlottv.kotlin.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.ui.theme.MerlotColors

/** Grey color for focused buttons/rows throughout the app */
private val FocusedGrey = Color(0xFF666666)

/**
 * Helper modifier that adds an accent border + background tint when focused via D-pad.
 */
@Composable
private fun Modifier.dpadFocusable(
    onClick: () -> Unit = {}
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .then(
            if (isFocused) Modifier
                .border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                .background(MerlotColors.Accent.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(8.dp))
        )
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter)
            ) {
                onClick()
                true
            } else false
        }
}

/**
 * TV-friendly button: Surface2 background when unfocused, accent border when focused via D-pad.
 * Consistent look matching the VOD card selection style.
 */
@Composable
private fun DpadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MerlotColors.Surface2,
            contentColor = if (isFocused) MerlotColors.White else MerlotColors.TextPrimary,
            disabledContainerColor = MerlotColors.Surface2.copy(alpha = 0.5f),
            disabledContentColor = MerlotColors.TextMuted
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                else Modifier
            ),
        content = content
    )
}

/**
 * TV-friendly text field: shows accent border on D-pad focus but does NOT open keyboard.
 * Keyboard only opens when user explicitly presses Enter/OK (D-pad center).
 * Press Back to dismiss keyboard and return to navigation mode.
 */
@Composable
private fun DpadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = !isEditing,
        placeholder = { Text(placeholder, color = MerlotColors.TextMuted, fontSize = 12.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MerlotColors.TextPrimary,
            unfocusedTextColor = MerlotColors.TextPrimary,
            cursorColor = MerlotColors.Accent,
            focusedBorderColor = if (isEditing) MerlotColors.Accent else MerlotColors.Accent,
            unfocusedBorderColor = MerlotColors.Border
        ),
        modifier = modifier
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && isEditing) {
                    isEditing = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        // Enter/OK while NOT editing → activate editing mode + show keyboard
                        !isEditing && (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                            isEditing = true
                            keyboardController?.show()
                            true
                        }
                        // Back while editing → deactivate editing mode + hide keyboard
                        isEditing && event.key == Key.Back -> {
                            isEditing = false
                            keyboardController?.hide()
                            true
                        }
                        // D-pad navigation while NOT editing → pass through for normal D-pad nav
                        !isEditing && (event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                            event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                            false // Let D-pad navigate away
                        }
                        else -> false
                    }
                } else false
            },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
        trailingIcon = trailingIcon
    )
}

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
        item(key = "title") {
            Text("Settings", color = MerlotColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // ═══ About ═══
        item(key = "about") {
            SettingsSection(title = "About", icon = { Icon(Icons.Default.Info, null, tint = MerlotColors.Accent) }) {
                Text("Merlot TV", color = MerlotColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Kotlin Build 2.0", color = MerlotColors.Accent, fontSize = 12.sp)
                Text("Version ${uiState.appVersion} (Build ${com.merlottv.kotlin.BuildConfig.VERSION_CODE})", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))
                if (uiState.updateAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MerlotColors.Accent.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Update Available!", color = MerlotColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Version ${uiState.latestVersion}", color = MerlotColors.TextMuted, fontSize = 11.sp)
                        }
                        if (uiState.updateUrl.isNotEmpty()) {
                            DpadButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateUrl))) }
                            ) { Text("Download", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                DpadButton(
                    onClick = { viewModel.checkForUpdate() },
                    enabled = !uiState.isCheckingUpdate
                ) {
                    if (uiState.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MerlotColors.Accent, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp)); Text("Checking...", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp)); Text("Check for Updates", fontSize = 11.sp)
                    }
                }
            }
        }

        // ═══ Speed Test ═══
        item(key = "speed_test") {
            SettingsSection(title = "Internet Speed Test", icon = { Icon(Icons.Default.Refresh, null, tint = MerlotColors.Accent) }) {
                if (uiState.isRunningSpeedTest) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = MerlotColors.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
                        Row(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface2, RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("↓ Download", color = MerlotColors.TextMuted, fontSize = 10.sp); Text(uiState.downloadSpeed, color = MerlotColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("↑ Upload", color = MerlotColors.TextMuted, fontSize = 10.sp); Text(uiState.uploadSpeed, color = MerlotColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (uiState.speedTestError.isNotEmpty()) { Text("Error: ${uiState.speedTestError}", color = MerlotColors.Danger, fontSize = 11.sp); Spacer(modifier = Modifier.height(8.dp)) }
                    DpadButton(onClick = { viewModel.runSpeedTest() }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Run Speed Test", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ═══ Live TV Buffer ═══
        item(key = "buffer") {
            SettingsSection(
                title = "Live TV Buffer",
                icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }
            ) {
                Text(
                    "Adjust the minimum buffer before playback starts. Lower = faster start but more prone to buffering on slow networks. Higher = more stable but slower start.",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Current value display
                val bufferMs = uiState.bufferDurationMs
                val bufferSec = bufferMs / 1000f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format("%.1f seconds", bufferSec),
                        color = MerlotColors.Accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${bufferMs}ms",
                    color = MerlotColors.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // D-pad controlled slider bar
                // Focus the row, then Left/Right adjusts by 100ms, OK confirms
                var sliderFocused by remember { mutableStateOf(false) }
                val steps = 27 // (3000 - 300) / 100 = 27 steps
                val currentStep = ((bufferMs - 300) / 100).coerceIn(0, steps)
                val progress = currentStep.toFloat() / steps

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { sliderFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        // Decrease by 100ms
                                        val newMs = (bufferMs - 100).coerceAtLeast(300)
                                        if (newMs != bufferMs) viewModel.setBufferDuration(newMs)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        // Increase by 100ms
                                        val newMs = (bufferMs + 100).coerceAtMost(3000)
                                        if (newMs != bufferMs) viewModel.setBufferDuration(newMs)
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter -> {
                                        // Confirm — save is already done on each adjustment
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .then(
                            if (sliderFocused) Modifier
                                .border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                                .background(MerlotColors.Accent.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            else Modifier
                                .border(2.dp, Color.Transparent, RoundedCornerShape(8.dp))
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Labels: 0.3s on left, 3.0s on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.3s", color = MerlotColors.TextMuted, fontSize = 10.sp)
                        Text(
                            if (sliderFocused) "◀  Use Left/Right to adjust  ▶"
                            else "Select to adjust",
                            color = if (sliderFocused) MerlotColors.Accent else MerlotColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (sliderFocused) FontWeight.Bold else FontWeight.Normal
                        )
                        Text("3.0s", color = MerlotColors.TextMuted, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    // Track bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MerlotColors.Surface2)
                    ) {
                        // Filled portion
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (sliderFocused) MerlotColors.Accent
                                    else MerlotColors.Accent.copy(alpha = 0.6f)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Step indicator dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 0..steps) {
                            if (i % 5 == 0 || i == steps) { // Show tick marks at 0.5s intervals
                                val ms = 300 + i * 100
                                Box(
                                    modifier = Modifier
                                        .size(if (i == currentStep) 6.dp else 3.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == currentStep) MerlotColors.Accent
                                            else if (i <= currentStep) MerlotColors.Accent.copy(alpha = 0.4f)
                                            else MerlotColors.TextMuted.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Default: 0.8s (800ms). Takes effect on next channel change.",
                    color = MerlotColors.TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        // ═══ Weather Alerts ═══
        item(key = "weather_alerts") {
            SettingsSection(
                title = "Weather Alerts",
                icon = { Text("⚠", fontSize = 18.sp) }
            ) {
                Text(
                    "Show scrolling NWS weather alert ticker on Live TV and VOD screens when active alerts exist for your area.",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                        .dpadFocusable(onClick = { viewModel.toggleWeatherAlerts(!uiState.weatherAlertsEnabled) })
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Alert Ticker on Live TV & VOD",
                            color = MerlotColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (uiState.weatherAlertsEnabled) "Enabled — alerts scroll at top of screen"
                            else "Disabled — alerts only show on Weather screen",
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = uiState.weatherAlertsEnabled,
                        onCheckedChange = { viewModel.toggleWeatherAlerts(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MerlotColors.Accent,
                            checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // ═══ Profiles ═══
        item(key = "profiles") {
            SettingsSection(title = "Profiles", icon = { Icon(Icons.Default.Person, null, tint = MerlotColors.Accent) }) {
                Text("Each profile has its own favorites and watch history.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.profiles.forEach { profile ->
                    val isActive = profile.id == uiState.activeProfileId
                    val avatarColor = ProfileDataStore.AVATAR_COLORS.getOrElse(profile.colorIndex) { 0xFF00E5FF.toInt() }
                    Row(modifier = Modifier.fillMaxWidth().background(if (isActive) MerlotColors.Surface2 else MerlotColors.Surface, RoundedCornerShape(8.dp)).dpadFocusable(onClick = { viewModel.switchProfile(profile.id) }).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(avatarColor)), contentAlignment = Alignment.Center) { Text(profile.name.take(1).uppercase(), color = MerlotColors.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(profile.name, color = MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); if (isActive) Text("Active", color = MerlotColors.Accent, fontSize = 9.sp) }
                        if (isActive) Icon(Icons.Default.Check, null, tint = MerlotColors.Accent, modifier = Modifier.size(18.dp))
                        if (!profile.isDefault) IconButton(onClick = { viewModel.removeProfile(profile.id) }) { Icon(Icons.Default.Delete, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (uiState.profiles.size < 6) {
                    Spacer(modifier = Modifier.height(8.dp))
                    var newProfileName by remember { mutableStateOf("") }
                    var selectedColor by remember { mutableIntStateOf(0) }

                    // Voice input launcher for profile name
                    val profileVoiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                            if (!spoken.isNullOrBlank()) newProfileName = spoken
                        }
                    }
                    val profileSpeechIntent = remember {
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say profile name")
                        }
                    }
                    val profilePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) { try { profileVoiceLauncher.launch(profileSpeechIntent) } catch (_: Exception) {} }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DpadTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            placeholder = "Profile name",
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        try { profileVoiceLauncher.launch(profileSpeechIntent) } catch (_: Exception) {}
                                    } else {
                                        profilePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }) {
                                    Icon(painter = painterResource(com.merlottv.kotlin.R.drawable.ic_mic), "Voice input", tint = MerlotColors.Accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        DpadButton(onClick = { viewModel.addProfile(newProfileName, selectedColor); newProfileName = ""; selectedColor = (selectedColor + 1) % ProfileDataStore.AVATAR_COLORS.size }, enabled = newProfileName.isNotBlank()) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileDataStore.AVATAR_COLORS.forEachIndexed { index, color ->
                            var colorFocused by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(color)).onFocusChanged { colorFocused = it.isFocused }.focusable().then(if (colorFocused) Modifier.border(2.dp, MerlotColors.White, CircleShape) else Modifier).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) { selectedColor = index; true } else false }, contentAlignment = Alignment.Center) {
                                if (index == selectedColor) Icon(Icons.Default.Check, null, tint = MerlotColors.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // ═══ Playlists / M3U ═══
        item(key = "playlists") {
            SettingsSection(title = "Playlists / M3U", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                Text("Add multiple playlists. All enabled playlists load in Live TV.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.playlists.forEachIndexed { index, playlist ->
                    Row(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface2, RoundedCornerShape(8.dp)).dpadFocusable().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(playlist.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(playlist.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1) }
                        Switch(checked = playlist.enabled, onCheckedChange = { viewModel.togglePlaylist(index) }, colors = SwitchDefaults.colors(checkedThumbColor = MerlotColors.Accent, checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)), modifier = Modifier.height(24.dp))
                        IconButton(onClick = { viewModel.removePlaylist(index) }) { Icon(Icons.Default.Close, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                var playlistName by remember { mutableStateOf("") }
                var playlistUrl by remember { mutableStateOf("") }

                // Voice input launcher for playlist fields
                var voiceTarget by remember { mutableStateOf("") }
                val playlistVoiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                        if (!spoken.isNullOrBlank()) {
                            when (voiceTarget) {
                                "name" -> playlistName = spoken
                                "url" -> playlistUrl = spoken
                            }
                        }
                    }
                }
                val playlistPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, if (voiceTarget == "name") "Say playlist name" else "Say playlist URL")
                        }
                        try { playlistVoiceLauncher.launch(intent) } catch (_: Exception) {}
                    }
                }

                fun launchVoice(target: String, prompt: String) {
                    voiceTarget = target
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        try { playlistVoiceLauncher.launch(intent) } catch (_: Exception) {}
                    } else {
                        playlistPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                DpadTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = "Playlist name",
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { launchVoice("name", "Say playlist name") }) {
                            Icon(painter = painterResource(com.merlottv.kotlin.R.drawable.ic_mic), "Voice", tint = MerlotColors.Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DpadTextField(
                        value = playlistUrl,
                        onValueChange = { playlistUrl = it },
                        placeholder = "https://playlist-url.m3u",
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { launchVoice("url", "Say playlist URL") }) {
                                Icon(painter = painterResource(com.merlottv.kotlin.R.drawable.ic_mic), "Voice", tint = MerlotColors.Accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DpadButton(onClick = { viewModel.addPlaylist(playlistName, playlistUrl); playlistName = ""; playlistUrl = "" }, enabled = playlistUrl.isNotBlank()) { Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }

        // ═══ EPG Sources ═══
        item(key = "epg") {
            SettingsSection(title = "EPG Sources", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                Text("Default sources are always loaded. Add custom EPG sources below.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.defaultEpgSources.forEach { source ->
                    Row(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface2, RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(source.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(source.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1) }
                        Text("built-in", color = MerlotColors.Accent, fontSize = 9.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                uiState.customEpgSources.forEachIndexed { index, source ->
                    Row(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface2, RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(source.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(source.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1) }
                        IconButton(onClick = { viewModel.removeEpgSource(index) }) { Icon(Icons.Default.Close, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp)) }
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
                            placeholder = "Source name",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DpadTextField(
                            value = epgUrl,
                            onValueChange = { epgUrl = it },
                            placeholder = "https://epg-source.xml",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    DpadButton(onClick = { viewModel.addEpgSource(epgName, epgUrl); epgName = ""; epgUrl = "" }, enabled = epgUrl.isNotBlank()) { Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }

        // ═══ Backup Stream Sources ═══
        item(key = "backup_sources") {
            val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""; viewModel.importBackupFile(content) }
            }
            SettingsSection(title = "Backup Stream Sources", icon = { Icon(Icons.Default.Refresh, null, tint = MerlotColors.Accent) }) {
                Text("Load backup M3U playlists. When a live stream fails, the app automatically searches these for a working alternative.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.backupSources.forEachIndexed { index, source ->
                    Row(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface2, RoundedCornerShape(8.dp)).dpadFocusable().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(source.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(source.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1) }
                        Switch(checked = source.enabled, onCheckedChange = { viewModel.toggleBackupSource(index) }, colors = SwitchDefaults.colors(checkedThumbColor = MerlotColors.Accent, checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)), modifier = Modifier.height(24.dp))
                        IconButton(onClick = { viewModel.removeBackupSource(index) }) { Icon(Icons.Default.Close, null, tint = MerlotColors.TextMuted, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                DpadButton(onClick = { filePickerLauncher.launch(arrayOf("text/*")) }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Import from File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                var backupName by remember { mutableStateOf("") }
                var backupUrl by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DpadTextField(
                            value = backupName,
                            onValueChange = { backupName = it },
                            placeholder = "Source name",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DpadTextField(
                            value = backupUrl,
                            onValueChange = { backupUrl = it },
                            placeholder = "https://backup-server.com/get.php?username=...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    DpadButton(onClick = { viewModel.addBackupSource(backupName, backupUrl); backupName = ""; backupUrl = "" }, enabled = backupUrl.isNotBlank()) { Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }

        // ═══ Stremio Addons ═══
        item(key = "addons") {
            SettingsSection(title = "Stremio Addons", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                Text("Default addons are built-in and cannot be removed.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.addons.forEach { addon ->
                    Row(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface2, RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(addon.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(addon.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1) }
                        if (addon.isDefault) Text("built-in", color = MerlotColors.Accent, fontSize = 9.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                var addonInput by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DpadTextField(
                        value = addonInput,
                        onValueChange = { addonInput = it },
                        placeholder = "https://addon.example.com/manifest.json",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DpadButton(onClick = { viewModel.addAddon(addonInput); addonInput = "" }) { Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }

        // ═══ Torbox ═══
        item(key = "torbox") {
            SettingsSection(title = "Torbox", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
                var torboxInput by remember { mutableStateOf(uiState.torboxKey) }
                DpadTextField(
                    value = torboxInput,
                    onValueChange = { torboxInput = it },
                    placeholder = "Torbox API Key",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                DpadButton(onClick = { viewModel.saveTorboxKey(torboxInput) }) { Text("Save Key", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }

        // ═══ Live TV Category Order ═══
        if (uiState.categoryOrder.isNotEmpty()) {
            item(key = "category_order") {
                var movingIndex by remember { mutableStateOf(-1) }  // -1 = not moving

                SettingsSection(title = "Live TV Categories", icon = { Icon(Icons.Default.List, null, tint = MerlotColors.Accent) }) {
                    Text(
                        text = if (movingIndex >= 0) "⬆ ⬇  Use D-pad Up/Down to move, press OK to confirm"
                               else "Select a category and press OK to reorder it",
                        color = if (movingIndex >= 0) MerlotColors.Accent else MerlotColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (movingIndex >= 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.categoryOrder.forEachIndexed { index, category ->
                        val isMoving = movingIndex == index
                        var isFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isMoving) MerlotColors.Accent.copy(alpha = 0.15f)
                                    else MerlotColors.Surface2,
                                    RoundedCornerShape(8.dp)
                                )
                                .then(
                                    if (isMoving) Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                                    else if (isFocused) Modifier.border(2.dp, FocusedGrey, RoundedCornerShape(8.dp))
                                    else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(8.dp))
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionCenter, Key.Enter -> {
                                                if (isMoving) {
                                                    movingIndex = -1  // Confirm position
                                                } else {
                                                    movingIndex = index  // Start moving this category
                                                }
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                if (isMoving && index > 0) {
                                                    viewModel.moveCategoryUp(index)
                                                    movingIndex = index - 1
                                                    true
                                                } else false
                                            }
                                            Key.DirectionDown -> {
                                                if (isMoving && index < uiState.categoryOrder.size - 1) {
                                                    viewModel.moveCategoryDown(index)
                                                    movingIndex = index + 1
                                                    true
                                                } else false
                                            }
                                            Key.Back -> {
                                                if (isMoving) {
                                                    movingIndex = -1  // Cancel
                                                    true
                                                } else false
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                color = if (isMoving) MerlotColors.Accent else MerlotColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isMoving) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = category,
                                color = if (isMoving) MerlotColors.Accent else MerlotColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (isMoving) {
                                Text("▲▼", color = MerlotColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    Icons.Default.KeyboardArrowUp, null,
                                    tint = MerlotColors.TextMuted.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown, null,
                                    tint = MerlotColors.TextMuted.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DpadButton(onClick = { movingIndex = -1; viewModel.saveCategoryOrder() }) {
                            Text("Save Order", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        DpadButton(onClick = { movingIndex = -1; viewModel.resetCategoryOrder() }) {
                            Text("Reset", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item(key = "spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SettingsSection(title: String, icon: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) { icon(); Spacer(modifier = Modifier.width(8.dp)); Text(title, color = MerlotColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        content()
    }
}
