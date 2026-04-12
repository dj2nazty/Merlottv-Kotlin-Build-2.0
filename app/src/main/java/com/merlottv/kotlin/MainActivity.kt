package com.merlottv.kotlin

import android.app.Activity
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.merlottv.kotlin.ui.components.SidebarNavigation
import com.merlottv.kotlin.ui.components.WeatherAlertTicker
import com.merlottv.kotlin.ui.navigation.MerlotNavHost
import com.merlottv.kotlin.ui.navigation.Screen
import com.merlottv.kotlin.ui.theme.MerlotColors
import com.merlottv.kotlin.ui.components.VideoSplashScreen
import com.merlottv.kotlin.ui.theme.MerlotTVTheme
import com.merlottv.kotlin.ui.viewmodels.AlertsViewModel
import com.merlottv.kotlin.ui.viewmodels.AutoUpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Intercept key dispatch to catch Compose's internal focus-search crash:
     * "LayoutCoordinate operations are only valid when isAttached is true"
     *
     * This is a known Compose bug with LazyColumn/LazyRow + D-pad navigation —
     * the focus system tries to measure an item that was scrolled off-screen and
     * already detached. Catching it here lets the UI recover gracefully instead
     * of killing the app.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("isAttached") == true) {
                android.util.Log.w("MerlotTV", "Suppressed Compose focus crash", e)
                true // Consume the event — UI recovers on next frame
            } else {
                throw e
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MerlotTVTheme {
                MerlotApp()
            }
        }
    }

    /**
     * Stop all audio when the app goes to the background (Home button, task switcher,
     * or screen off). This prevents audio from continuing to play after leaving the app.
     */
    override fun onStop() {
        super.onStop()
        // Abandon audio focus — tells the system we're done producing audio
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocus(null)
        // Mute the stream temporarily so any lingering player buffer is silent
        // (ExoPlayer may still have a few frames queued)
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                0
            )
        } catch (_: Exception) {}
    }

    /**
     * Restore audio when the app comes back to the foreground.
     */
    override fun onRestart() {
        super.onRestart()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                0
            )
        } catch (_: Exception) {}
    }
}

@Composable
fun MerlotApp() {
    var showSplash by remember { mutableStateOf(true) }
    var splashTimerDone by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Minimum 6 seconds for the splash video
    LaunchedEffect(Unit) {
        delay(6000)
        splashTimerDone = true
    }

    // Hard timeout: force-dismiss splash after 12 seconds no matter what
    // This prevents infinite splash if navigation gets stuck
    LaunchedEffect(Unit) {
        delay(12000)
        if (showSplash) {
            showSplash = false
        }
    }

    // Dismiss splash when timer is done AND we've navigated past ProfilePicker
    // Also dismiss if we're still on ProfilePicker (first-time user needs to pick a profile)
    LaunchedEffect(splashTimerDone, currentRoute) {
        if (splashTimerDone && currentRoute != null) {
            if (currentRoute == Screen.Home.route) {
                showSplash = false
            } else if (currentRoute == Screen.ProfilePicker.route) {
                // First-time user — dismiss splash so they can pick a profile
                delay(500) // short extra delay for smooth transition
                showSplash = false
            }
        }
    }
    var isLiveTvFullscreen by remember { mutableStateOf(false) }
    var sidebarVisible by remember { mutableStateOf(false) }
    val sidebarFocusRequester = remember { FocusRequester() }
    var showExitDialog by remember { mutableStateOf(false) }

    // Global weather alerts
    val alertsViewModel: AlertsViewModel = hiltViewModel()
    val activeAlerts by alertsViewModel.activeAlerts.collectAsState()
    val showAlertBanner by alertsViewModel.showBanner.collectAsState()
    val alertsEnabled by alertsViewModel.alertsEnabled.collectAsState()

    // Auto-update — checks GitHub on launch, downloads in background
    val autoUpdateViewModel: AutoUpdateViewModel = hiltViewModel()
    val updateState by autoUpdateViewModel.state.collectAsState()

    // Always start at ProfilePicker — it auto-redirects to Home if a profile is already set
    // This avoids creating a duplicate ProfileDataStore outside of Hilt
    val startDestination = Screen.ProfilePicker.route

    // Hide sidebar on player screen, profile picker, or when live TV is fullscreen
    val showSidebar = currentRoute != Screen.Player.route &&
        currentRoute != Screen.ProfilePicker.route &&
        !isLiveTvFullscreen

    // Determine if we're on a "root" screen (sidebar destination, not a detail/player)
    // Match exact route OR route with query params (e.g. "vod?platform=...")
    // but NOT sub-routes (e.g. "vod_detail/..." should NOT match "vod")
    val isRootScreen = Screen.sidebarItems.any { screen ->
        currentRoute == screen.route ||
        currentRoute?.startsWith("${screen.route}?") == true
    } || currentRoute == Screen.Home.route

    // Intercept system back button — never close app, show exit dialog on root screens
    BackHandler(enabled = isRootScreen && !showExitDialog) {
        showExitDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    // Bubble phase: open sidebar when Left is not consumed by any child.
                    // Only allow sidebar to open on ROOT screens (Home, VOD list, Favorites, etc.)
                    // NOT on detail screens, player, or other sub-screens — prevents
                    // Left D-pad from hijacking navigation on those screens.
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionLeft &&
                        showSidebar && !sidebarVisible && isRootScreen
                    ) {
                        sidebarVisible = true
                        true
                    } else {
                        false
                    }
                }
        ) {
            if (showSidebar && sidebarVisible) {
                SidebarNavigation(
                    currentRoute = currentRoute,
                    focusRequester = sidebarFocusRequester,
                    onNavigate = { screen ->
                        sidebarVisible = false
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onDismiss = { sidebarVisible = false }
                )
            }

            MerlotNavHost(
                navController = navController,
                modifier = Modifier.weight(1f),
                startDestination = startDestination,
                onLiveTvFullscreenChanged = { isLiveTvFullscreen = it },
                onOpenSidebar = { sidebarVisible = true }
            )
        }

        // Exit confirmation dialog
        if (showExitDialog) {
            ExitConfirmationDialog(
                onConfirm = {
                    showExitDialog = false
                    // Close the app
                    (navController.context as? Activity)?.finish()
                },
                onDismiss = { showExitDialog = false }
            )
        }

        // Global weather alert ticker — shows on Live TV and Player (VOD) screens
        val showTickerOnScreen = currentRoute == Screen.LiveTv.route ||
            currentRoute == Screen.Player.route
        WeatherAlertTicker(
            alerts = activeAlerts,
            visible = showAlertBanner && showTickerOnScreen && alertsEnabled,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Auto-update dialog — shows after download completes
        if (!showSplash && updateState.updateReady && !updateState.dismissed) {
            UpdateReadyDialog(
                version = updateState.latestVersion,
                onInstall = { autoUpdateViewModel.installNow() },
                onDismiss = { autoUpdateViewModel.dismiss() }
            )
        }

        // Subtle download progress indicator (top bar while downloading)
        if (!showSplash && updateState.isDownloading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "Downloading update… ${updateState.downloadProgress}%",
                    color = MerlotColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Video splash overlay — covers everything while app loads underneath
        if (showSplash) {
            VideoSplashScreen(onFinished = { /* controlled by MerlotApp LaunchedEffects */ })
        }
    }
}

@Composable
private fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val noFocusRequester = remember { FocusRequester() }

    // Auto-focus "No" button (safer default) — delay to ensure layout is ready
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { noFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Full-screen semi-transparent backdrop — block all input from reaching content behind
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* consume clicks on backdrop */ }
            .onPreviewKeyEvent { event ->
                // Capture phase: handle Back to dismiss, let D-pad through for button focus
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
                    return@onPreviewKeyEvent true
                }
                // Let D-pad LEFT/RIGHT/UP/DOWN through so focus can move between buttons
                if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                    event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter || event.key == Key.Tab) {
                    return@onPreviewKeyEvent false // let Compose focus system handle it
                }
                // Consume everything else so keys don't leak to content behind
                event.type == KeyEventType.KeyDown
            },
        contentAlignment = Alignment.Center
    ) {
        // Dialog card
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MerlotColors.Surface2)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Exit MerlotTV?",
                    color = MerlotColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Are you sure you want to close the app?",
                    color = MerlotColors.TextMuted,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExitDialogButton(
                        text = "Yes",
                        onClick = onConfirm,
                        isPrimary = false,
                        buttonColor = MerlotColors.Danger
                    )
                    ExitDialogButton(
                        text = "No",
                        onClick = onDismiss,
                        isPrimary = true,
                        focusRequester = noFocusRequester
                    )
                }
            }
        }
    }
}

@Composable
private fun ExitDialogButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    buttonColor: Color? = null,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        buttonColor != null && isFocused -> buttonColor
        buttonColor != null -> buttonColor.copy(alpha = 0.3f)
        isPrimary && isFocused -> MerlotColors.Accent
        isPrimary -> MerlotColors.AccentDark
        isFocused -> MerlotColors.Surface2
        else -> MerlotColors.Surface
    }
    val textColor = when {
        buttonColor != null && isFocused -> Color.White
        buttonColor != null -> buttonColor
        isPrimary && isFocused -> Color.Black
        isPrimary -> MerlotColors.TextPrimary
        isFocused -> MerlotColors.Accent
        else -> MerlotColors.TextPrimary
    }
    val borderColor = if (isFocused) MerlotColors.Accent else MerlotColors.Border

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(44.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun UpdateReadyDialog(
    version: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val installFocusRequester = remember { FocusRequester() }

    // Auto-focus "Install Now" button — delay to ensure layout is ready
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { installFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Full-screen semi-transparent backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* consume backdrop clicks */ }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                    event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter || event.key == Key.Tab) {
                    return@onPreviewKeyEvent false
                }
                event.type == KeyEventType.KeyDown
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MerlotColors.Surface2)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Update Available",
                    color = MerlotColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Merlot TV v$version is ready to install",
                    color = MerlotColors.TextMuted,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Current: v${com.merlottv.kotlin.BuildConfig.VERSION_NAME}",
                    color = MerlotColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExitDialogButton(
                        text = "Install Now",
                        onClick = onInstall,
                        isPrimary = true,
                        focusRequester = installFocusRequester
                    )
                    ExitDialogButton(
                        text = "Later",
                        onClick = onDismiss,
                        isPrimary = false
                    )
                }
            }
        }
    }
}
