package com.merlottv.kotlin

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.merlottv.kotlin.ui.components.SidebarNavigation
import com.merlottv.kotlin.ui.navigation.MerlotNavHost
import com.merlottv.kotlin.ui.navigation.Screen
import com.merlottv.kotlin.ui.theme.MerlotColors
import com.merlottv.kotlin.ui.theme.MerlotTVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MerlotTVTheme {
                MerlotApp()
            }
        }
    }
}

@Composable
fun MerlotApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isLiveTvFullscreen by remember { mutableStateOf(false) }
    var sidebarVisible by remember { mutableStateOf(false) }
    val sidebarFocusRequester = remember { FocusRequester() }
    var showExitDialog by remember { mutableStateOf(false) }

    // Always start at ProfilePicker — it auto-redirects to Home if a profile is already set
    // This avoids creating a duplicate ProfileDataStore outside of Hilt
    val startDestination = Screen.ProfilePicker.route

    // Hide sidebar on player screen, profile picker, or when live TV is fullscreen
    val showSidebar = currentRoute != Screen.Player.route &&
        currentRoute != Screen.ProfilePicker.route &&
        !isLiveTvFullscreen

    // Determine if we're on a "root" screen (sidebar destination, not a detail/player)
    val isRootScreen = Screen.sidebarItems.any { it.route == currentRoute } || currentRoute == Screen.Home.route

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
                .onPreviewKeyEvent { event ->
                    // D-pad Left at the content edge → show sidebar and give it focus
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionLeft &&
                        showSidebar && !sidebarVisible
                    ) {
                        sidebarVisible = true
                        try { sidebarFocusRequester.requestFocus() } catch (_: Exception) {}
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
                onLiveTvFullscreenChanged = { isLiveTvFullscreen = it }
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
    }
}

@Composable
private fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val noFocusRequester = remember { FocusRequester() }

    // Auto-focus "No" button (safer default)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try { noFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Full-screen semi-transparent backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
                    true
                } else false
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
