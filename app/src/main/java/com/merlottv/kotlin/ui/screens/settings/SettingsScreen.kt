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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import kotlinx.coroutines.launch
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.ui.components.MerlotChip
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
    var selectedTab by rememberSaveable { mutableStateOf("General") }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll to top when tab changes
    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        // Tab row with focus requesters for each tab
        val tabs = listOf("General", "Playback", "Sources", "Addons", "Advanced")
        val tabFocusRequesters = remember { tabs.map { FocusRequester() } }

        // Auto-focus General tab when Settings opens
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(200)
            try { tabFocusRequesters[0].requestFocus() } catch (_: Exception) {}
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", color = MerlotColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.width(16.dp))

            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTab == tab
                MerlotChip(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    modifier = Modifier
                        .focusRequester(tabFocusRequesters[index])
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (index > 0) {
                                            try { tabFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                                            true // Consume — don't let sidebar steal focus
                                        } else true // On first tab, consume Left to block sidebar
                                    }
                                    Key.DirectionRight -> {
                                        if (index < tabs.size - 1) {
                                            try { tabFocusRequesters[index + 1].requestFocus() } catch (_: Exception) {}
                                        }
                                        true // Consume to prevent focus from leaving tabs
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary
                            when (tab) {
                                "General" -> Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = tint)
                                "Playback" -> Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp), tint = tint)
                                "Sources" -> Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp), tint = tint)
                                "Addons" -> Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = tint)
                                "Advanced" -> Icon(Icons.Default.List, null, modifier = Modifier.size(14.dp), tint = tint)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tab, fontSize = 12.sp, color = if (isSelected) MerlotColors.Black else MerlotColors.TextPrimary)
                        }
                    }
                )
            }
        }

        // Content area — focusProperties redirects Up to the currently selected tab
        val selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        // Focus anchor at top — scrolls to top when focused, D-pad Up goes to selected tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .onFocusChanged { if (it.isFocused) coroutineScope.launch { scrollState.animateScrollTo(0) } }
                .focusProperties { up = tabFocusRequesters[selectedTabIndex] }
                .focusable()
        )

        // ═══ About ═══ [General]
        if (selectedTab == "General") {
            SettingsSection(title = "About", icon = { Icon(Icons.Default.Info, null, tint = MerlotColors.Accent) }) {
                Text("Merlot TV", color = MerlotColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Kotlin Build 2.0", color = MerlotColors.Accent, fontSize = 12.sp)
                Text("Version ${uiState.appVersion} (Build ${com.merlottv.kotlin.BuildConfig.VERSION_CODE})", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                // Release notes button + panel
                DpadButton(onClick = { viewModel.toggleReleaseNotes() }) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.showReleaseNotes) "Hide Release Notes" else "View Release Notes", fontSize = 11.sp)
                }
                if (uiState.showReleaseNotes) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("Release Notes — v${uiState.appVersion}", color = MerlotColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.isFetchingReleaseNotes) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MerlotColors.Accent, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching from GitHub...", color = MerlotColors.TextMuted, fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                uiState.releaseNotes,
                                color = MerlotColors.TextPrimary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.updateAvailable) {
                    val downloadFocus = remember { FocusRequester() }
                    // Auto-focus the Download button when update becomes available
                    LaunchedEffect(uiState.updateAvailable) {
                        if (uiState.updateAvailable && uiState.updateUrl.isNotEmpty()) {
                            try { downloadFocus.requestFocus() } catch (_: Exception) {}
                        }
                    }
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
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateUrl))) },
                                modifier = Modifier.focusRequester(downloadFocus)
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

        // ═══ Speed Test ═══ [General]
        if (selectedTab == "General") {
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

        // ═══ Live TV Buffer ═══ [Playback]
        if (selectedTab == "Playback") {
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

        // ═══ Buffer Automatic Backup Scan ═══ [Playback]
        if (selectedTab == "Playback") {
            SettingsSection(
                title = "Buffer Automatic Backup Scan",
                icon = { Text("\uD83D\uDD04", fontSize = 18.sp) }
            ) {
                Text(
                    "When enabled, the app automatically searches backup M3U sources for a better stream after repeated buffering. Keeps your channels playing without manual intervention.",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                        .dpadFocusable(onClick = { viewModel.toggleBufferAutoBackupScan(!uiState.bufferAutoBackupScan) })
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Buffer Automatic Backup Scan",
                            color = MerlotColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (uiState.bufferAutoBackupScan) "Enabled — auto-switches to backup stream after 2 rebuffers"
                            else "Disabled — uses original stream behavior",
                            color = if (uiState.bufferAutoBackupScan) MerlotColors.Accent else MerlotColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = uiState.bufferAutoBackupScan,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MerlotColors.Accent,
                            checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(24.dp).focusable(false)
                    )
                }
            }
        }

        // ═══ Home & VOD Categories ═══ [Playback]
        if (selectedTab == "Playback") {
            SettingsSection(title = "Home & VOD Categories", icon = { Icon(Icons.Default.Tune, null, tint = MerlotColors.Accent) }) {
                Text(
                    "Customize which catalog rows appear on the Home and VOD screens, reorder them, or hide ones you don't want.",
                    color = MerlotColors.TextMuted, fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                DpadButton(onClick = { viewModel.openVodCategorySystem() }) {
                    Text("Manage Home & VOD Categories", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ═══ Bitrate Checker ═══ [Playback]
        if (selectedTab == "Playback") {
            SettingsSection(
                title = "Bitrate Checker",
                icon = { Text("📊", fontSize = 18.sp) }
            ) {
                Text(
                    "Show real-time video/audio bitrate, codec, and network throughput in the Live TV Quick Menu.",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                        .dpadFocusable(onClick = { viewModel.toggleBitrateChecker(!uiState.bitrateCheckerEnabled) })
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Bitrate Checker",
                            color = MerlotColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (uiState.bitrateCheckerEnabled) "Enabled — showing in Quick Menu"
                            else "Disabled",
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = uiState.bitrateCheckerEnabled,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MerlotColors.Accent,
                            checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(24.dp).focusable(false)
                    )
                }
            }
        }

        // ═══ Player Settings ═══ [Playback]
        if (selectedTab == "Playback") {
            SettingsSection(
                title = "Player Settings",
                icon = { Text("\uD83C\uDFAC", fontSize = 18.sp) }
            ) {
                // Frame Rate Matching
                Text(
                    "Auto frame rate matching detects video frame rate and switches your display refresh rate to match, eliminating judder on 24fps cinema content.",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val frameRateModes = listOf("off" to "Off", "start" to "Match on Start", "start_stop" to "Match & Restore")
                frameRateModes.forEach { (mode, label) ->
                    val isSelected = uiState.frameRateMatching == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MerlotColors.Accent.copy(alpha = 0.15f) else MerlotColors.Surface2,
                                RoundedCornerShape(8.dp)
                            )
                            .dpadFocusable(onClick = { viewModel.setFrameRateMatching(mode) })
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = if (isSelected) MerlotColors.Accent else MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (mode) {
                                    "off" -> "No frame rate switching"
                                    "start" -> "Switch display on playback start"
                                    else -> "Switch on start, restore original on stop"
                                },
                                color = MerlotColors.TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = MerlotColors.Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Next Episode Auto-Play
                Text(
                    "Auto-play next episode when the current one nears completion. Shows a countdown card before switching.",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                        .dpadFocusable(onClick = { viewModel.toggleNextEpisodeAutoPlay(!uiState.nextEpisodeAutoPlay) })
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Next Episode Auto-Play",
                            color = MerlotColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (uiState.nextEpisodeAutoPlay) "Enabled — shows countdown card at ${uiState.nextEpisodeThresholdPercent}%"
                            else "Disabled",
                            color = MerlotColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = uiState.nextEpisodeAutoPlay,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MerlotColors.Accent,
                            checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(24.dp).focusable(false)
                    )
                }

                // Threshold control (only when auto-play is enabled)
                if (uiState.nextEpisodeAutoPlay) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val thresholdPercent = uiState.nextEpisodeThresholdPercent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (thresholdPercent > 85) viewModel.setNextEpisodeThreshold(thresholdPercent - 1)
                                            true // consume — don't open sidebar
                                        }
                                        Key.DirectionRight -> {
                                            if (thresholdPercent < 99) viewModel.setNextEpisodeThreshold(thresholdPercent + 1)
                                            true // consume
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .dpadFocusable(onClick = {})
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trigger at:", color = MerlotColors.TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("◀", color = MerlotColors.Accent, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${thresholdPercent}%",
                            color = MerlotColors.Accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("▶", color = MerlotColors.Accent, fontSize = 14.sp)
                    }
                }

            }
        }

        // ═══ Weather Alerts ═══ [General]
        if (selectedTab == "General") {
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
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MerlotColors.Accent,
                            checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(24.dp).focusable(false)
                    )
                }
            }
        }

        // ═══ Profiles ═══ [General]
        if (selectedTab == "General") {
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

        // ═══ Playlists / M3U ═══ [Sources]
        if (selectedTab == "Sources") {
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

        // ═══ EPG Sources ═══ [Sources]
        if (selectedTab == "Sources") {
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

        // ═══ Backup Stream Sources ═══ [Sources]
        if (selectedTab == "Sources") {
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

        // ═══ Torbox ═══ [Sources]
        if (selectedTab == "Sources") {
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

        // ═══ Stremio Addons ═══ [Addons]
        if (selectedTab == "Addons") {
            SettingsSection(title = "Stremio Addons", icon = { Icon(Icons.Default.Add, null, tint = MerlotColors.Accent) }) {
                Text("Addons extend Merlot TV with additional streaming sources. Default addons are built-in and cannot be removed.", color = MerlotColors.TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.addons.forEach { addon ->
                    val isEnabled = !uiState.disabledAddons.contains(addon.url)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isEnabled) MerlotColors.Surface2 else MerlotColors.Surface2.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .dpadFocusable(onClick = { viewModel.toggleAddonEnabled(addon.url, isEnabled) })
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    addon.name,
                                    color = if (isEnabled) MerlotColors.TextPrimary else MerlotColors.TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (addon.version.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("v${addon.version}", color = MerlotColors.TextMuted, fontSize = 9.sp)
                                }
                                if (addon.isDefault) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("built-in", color = MerlotColors.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (addon.description.isNotEmpty()) {
                                Text(addon.description, color = MerlotColors.TextMuted, fontSize = 10.sp, maxLines = 2)
                            }
                            Text(addon.url, color = MerlotColors.TextMuted.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (!addon.isDefault) {
                            IconButton(onClick = { viewModel.removeAddon(addon.url) }) {
                                Icon(Icons.Default.Delete, "Remove addon", tint = MerlotColors.TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MerlotColors.Accent,
                                checkedTrackColor = MerlotColors.Accent.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(24.dp).focusable(false)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Add Custom Addon", color = MerlotColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                var addonInput by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DpadTextField(
                        value = addonInput,
                        onValueChange = { addonInput = it },
                        placeholder = "https://addon.example.com/manifest.json",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DpadButton(onClick = { viewModel.addAddon(addonInput); addonInput = "" }, enabled = addonInput.isNotBlank()) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // ═══ VOD Category System ═══ [moved to Playback tab below]

        // ═══ Live TV Category Order ═══ [Advanced]
        if (selectedTab == "Advanced" && uiState.categoryOrder.isNotEmpty()) {
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
                                .focusable()
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
                    // Explicit Left/Right focus handling between buttons prevents
                    // the sidebar from hijacking Left on the Reset button
                    val saveOrderFocus = remember { FocusRequester() }
                    val resetFocus = remember { FocusRequester() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DpadButton(
                            onClick = { movingIndex = -1; viewModel.saveCategoryOrder() },
                            modifier = Modifier.focusRequester(saveOrderFocus)
                        ) {
                            Text("Save Order", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        DpadButton(
                            onClick = { movingIndex = -1; viewModel.resetCategoryOrder() },
                            modifier = Modifier
                                .focusRequester(resetFocus)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionLeft
                                    ) {
                                        try { saveOrderFocus.requestFocus() } catch (_: Exception) {}
                                        true
                                    } else false
                                }
                        ) {
                            Text("Reset", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

        Spacer(modifier = Modifier.height(16.dp))
        }  // end scrollable Column
    }  // end outer Column

    // ═══ VOD Category System Overlay ═══
    if (uiState.showVodCategorySystem) {
        VodCategorySystemOverlay(
            uiState = uiState,
            onClose = { viewModel.closeVodCategorySystem() },
            onSave = { viewModel.saveVodCategorySystem() },
            onTabChange = { viewModel.setActiveCategoryTab(it) },
            onToggleHome = { viewModel.toggleHomeCategoryVisible(it) },
            onToggleVod = { viewModel.toggleVodCategoryVisible(it) },
            onMoveHomeUp = { viewModel.moveHomeCategoryUp(it) },
            onMoveHomeDown = { viewModel.moveHomeCategoryDown(it) },
            onMoveVodUp = { viewModel.moveVodCategoryUp(it) },
            onMoveVodDown = { viewModel.moveVodCategoryDown(it) },
            onResetHome = { viewModel.resetHomeCategoryOrder() },
            onResetVod = { viewModel.resetVodCategoryOrder() }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(MerlotColors.Surface, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = MerlotColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

// ═══ VOD Category System Overlay ═══
@Composable
private fun VodCategorySystemOverlay(
    uiState: SettingsUiState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onTabChange: (String) -> Unit,
    onToggleHome: (String) -> Unit,
    onToggleVod: (String) -> Unit,
    onMoveHomeUp: (Int) -> Unit,
    onMoveHomeDown: (Int) -> Unit,
    onMoveVodUp: (Int) -> Unit,
    onMoveVodDown: (Int) -> Unit,
    onResetHome: () -> Unit,
    onResetVod: () -> Unit
) {
    val isHome = uiState.activeCategoryTab == "Home"
    val items = if (isHome) uiState.homeCategoryItems else uiState.vodCategoryItems
    var movingIndex by remember { mutableStateOf(-1) }

    // Reset moving index on tab change
    LaunchedEffect(uiState.activeCategoryTab) { movingIndex = -1 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    if (movingIndex >= 0) {
                        movingIndex = -1
                        true
                    } else {
                        onClose()
                        true
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Hoisted focus requesters so tabs and header can reference them
            val saveFocus = remember { FocusRequester() }
            val closeFocus = remember { FocusRequester() }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tune, null, tint = MerlotColors.Accent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Home & VOD Categories",
                    color = MerlotColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                var saveBtnFocused by remember { mutableStateOf(false) }
                var closeBtnFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .focusRequester(saveFocus)
                        .onFocusChanged { saveBtnFocused = it.isFocused }
                        .border(
                            2.dp,
                            if (saveBtnFocused) MerlotColors.Accent else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionCenter, Key.Enter -> {
                                        movingIndex = -1; onSave()
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        try { closeFocus.requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionDown -> false
                                    Key.DirectionLeft -> true
                                    else -> false
                                }
                            } else false
                        }
                        .focusable()
                        .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp), tint = if (saveBtnFocused) MerlotColors.White else MerlotColors.TextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (saveBtnFocused) MerlotColors.White else MerlotColors.TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .focusRequester(closeFocus)
                        .onFocusChanged { closeBtnFocused = it.isFocused }
                        .border(
                            2.dp,
                            if (closeBtnFocused) MerlotColors.Accent else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionCenter, Key.Enter -> {
                                        onClose()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        try { saveFocus.requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionDown -> false
                                    Key.DirectionRight -> true
                                    else -> false
                                }
                            } else false
                        }
                        .focusable()
                        .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = if (closeBtnFocused) MerlotColors.White else MerlotColors.TextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (closeBtnFocused) MerlotColors.White else MerlotColors.TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab chips: Home / VOD
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val homeFocus = remember { FocusRequester() }
                val vodFocus = remember { FocusRequester() }

                // Auto-focus Home tab when overlay opens
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(200)
                    try { homeFocus.requestFocus() } catch (_: Exception) {}
                }
                listOf("Home" to homeFocus, "VOD" to vodFocus).forEach { (tab, focusReq) ->
                    val selected = uiState.activeCategoryTab == tab
                    val otherFocus = if (tab == "Home") vodFocus else homeFocus
                    var chipFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) MerlotColors.Accent
                                else if (chipFocused) FocusedGrey
                                else MerlotColors.Surface2,
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                2.dp,
                                if (chipFocused && !selected) FocusedGrey else Color.Transparent,
                                RoundedCornerShape(20.dp)
                            )
                            .focusRequester(focusReq)
                            .onFocusChanged { chipFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter -> {
                                            movingIndex = -1
                                            onTabChange(tab)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            try { saveFocus.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            if (tab == "VOD") {
                                                try { homeFocus.requestFocus() } catch (_: Exception) {}
                                            }
                                            true // Always consume Left to block sidebar
                                        }
                                        Key.DirectionRight -> {
                                            if (tab == "Home") {
                                                try { vodFocus.requestFocus() } catch (_: Exception) {}
                                            }
                                            true // Consume Right to stay in tabs
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$tab Categories",
                            color = if (selected) MerlotColors.White else MerlotColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode toggle + instructions row
            var reorderMode by remember { mutableStateOf(false) }
            // Reset reorder mode on tab change
            LaunchedEffect(uiState.activeCategoryTab) { reorderMode = false; movingIndex = -1 }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode toggle button
                DpadButton(onClick = {
                    movingIndex = -1
                    reorderMode = !reorderMode
                }) {
                    Icon(
                        if (reorderMode) Icons.Default.Check else Icons.Default.List,
                        null, modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (reorderMode) "Done Reordering" else "Reorder Mode",
                        fontWeight = FontWeight.Bold, fontSize = 11.sp
                    )
                }

                // Instructions
                Text(
                    text = when {
                        movingIndex >= 0 -> "▲▼ D-pad Up/Down to move, OK to confirm"
                        reorderMode -> "Press OK to pick up a row, then move it"
                        else -> "Press OK to toggle on/off"
                    },
                    color = when {
                        movingIndex >= 0 -> MerlotColors.Accent
                        reorderMode -> Color(0xFFFF9800)
                        else -> MerlotColors.TextMuted
                    },
                    fontSize = 11.sp,
                    fontWeight = if (movingIndex >= 0 || reorderMode) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoadingVodCategories) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MerlotColors.Accent, modifier = Modifier.size(24.dp))
                }
            } else {
                // Track which row index has focus for reorder moves
                var focusedIndex by remember { mutableStateOf(-1) }
                // FocusRequesters for each row so we can restore focus after reorder
                val focusRequesters = remember(items.size) {
                    List(items.size) { FocusRequester() }
                }

                val lazyState = rememberLazyListState()

                // After a reorder move, re-focus the moved item AND scroll to keep it visible
                LaunchedEffect(movingIndex) {
                    if (movingIndex >= 0 && movingIndex < focusRequesters.size) {
                        kotlinx.coroutines.delay(50)
                        try {
                            lazyState.animateScrollToItem(movingIndex)
                            focusRequesters[movingIndex].requestFocus()
                        } catch (_: Exception) {}
                    }
                }

                LazyColumn(
                    state = lazyState,
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            // Handle reorder moves at the LazyColumn level
                            if (event.type == KeyEventType.KeyDown && movingIndex >= 0) {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        if (movingIndex > 0) {
                                            if (isHome) onMoveHomeUp(movingIndex) else onMoveVodUp(movingIndex)
                                            movingIndex -= 1
                                        }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        if (movingIndex < items.size - 1) {
                                            if (isHome) onMoveHomeDown(movingIndex) else onMoveVodDown(movingIndex)
                                            movingIndex += 1
                                        }
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter -> {
                                        movingIndex = -1 // Drop / confirm position
                                        true
                                    }
                                    Key.Back -> {
                                        movingIndex = -1
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                        val isMoving = movingIndex == index
                        var isFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isMoving -> MerlotColors.Accent.copy(alpha = 0.15f)
                                        reorderMode && isFocused -> Color(0xFFFF9800).copy(alpha = 0.1f)
                                        !item.enabled -> MerlotColors.Surface2.copy(alpha = 0.4f)
                                        else -> MerlotColors.Surface2
                                    },
                                    RoundedCornerShape(8.dp)
                                )
                                .then(
                                    when {
                                        isMoving -> Modifier.border(2.dp, MerlotColors.Accent, RoundedCornerShape(8.dp))
                                        reorderMode && isFocused -> Modifier.border(2.dp, Color(0xFFFF9800), RoundedCornerShape(8.dp))
                                        isFocused -> Modifier.border(2.dp, FocusedGrey, RoundedCornerShape(8.dp))
                                        else -> Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(8.dp))
                                    }
                                )
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (it.isFocused) focusedIndex = index
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && movingIndex < 0) {
                                        when (event.key) {
                                            Key.DirectionCenter, Key.Enter -> {
                                                if (reorderMode) {
                                                    movingIndex = index // Pick up this item
                                                } else {
                                                    // Toggle visibility
                                                    if (isHome) onToggleHome(item.key) else onToggleVod(item.key)
                                                }
                                                true
                                            }
                                            Key.DirectionLeft -> true // Block left to prevent sidebar
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Row number
                            Text(
                                text = "${index + 1}.",
                                color = if (isMoving) MerlotColors.Accent else MerlotColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isMoving) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(28.dp)
                            )
                            // Reorder indicator
                            if (reorderMode) {
                                if (isMoving) {
                                    Text("▲▼", color = MerlotColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.KeyboardArrowUp, null, tint = Color(0xFFFF9800).copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFFF9800).copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            // Title
                            Text(
                                text = item.title,
                                color = when {
                                    isMoving -> MerlotColors.Accent
                                    !item.enabled -> MerlotColors.TextMuted
                                    else -> MerlotColors.TextPrimary
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Visual indicator — not focusable, not interactive
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 20.dp)
                                    .background(
                                        if (item.enabled) MerlotColors.Accent.copy(alpha = 0.3f) else MerlotColors.Surface2,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = if (item.enabled) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(16.dp)
                                        .background(
                                            if (item.enabled) MerlotColors.Accent else MerlotColors.TextMuted,
                                            CircleShape
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom buttons: Save, Close, Reset — all reachable by scrolling down past the list
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var saveBtnFocused2 by remember { mutableStateOf(false) }
                    var closeBtnFocused2 by remember { mutableStateOf(false) }
                    var resetBtnFocused by remember { mutableStateOf(false) }

                    DpadButton(
                        onClick = { movingIndex = -1; onSave() },
                        modifier = Modifier
                            .onFocusChanged { saveBtnFocused2 = it.isFocused }
                            .border(
                                2.dp,
                                if (saveBtnFocused2) MerlotColors.Accent else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    DpadButton(
                        onClick = { onClose() },
                        modifier = Modifier
                            .onFocusChanged { closeBtnFocused2 = it.isFocused }
                            .border(
                                2.dp,
                                if (closeBtnFocused2) MerlotColors.Accent else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    DpadButton(
                        onClick = { movingIndex = -1; reorderMode = false; if (isHome) onResetHome() else onResetVod() },
                        modifier = Modifier
                            .onFocusChanged { resetBtnFocused = it.isFocused }
                            .border(
                                2.dp,
                                if (resetBtnFocused) Color(0xFFFF9800) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset to Default", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
