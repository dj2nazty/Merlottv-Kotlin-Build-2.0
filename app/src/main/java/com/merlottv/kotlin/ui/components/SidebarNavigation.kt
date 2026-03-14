package com.merlottv.kotlin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.merlottv.kotlin.R
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.UserProfile
import com.merlottv.kotlin.ui.navigation.Screen
import com.merlottv.kotlin.ui.screens.profiles.ProfilePickerViewModel
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun SidebarNavigation(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }

    // Profile data for the M logo picker
    val profileVm: ProfilePickerViewModel = hiltViewModel()
    val profiles by profileVm.profiles.collectAsState()
    val activeProfileId by profileVm.activeProfileId.collectAsState()

    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 200.dp else 72.dp,
        animationSpec = tween(250),
        label = "sidebarWidth"
    )

    Box {
        Column(
            modifier = modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(MerlotColors.SidebarCollapsed)
                .border(
                    width = 1.dp,
                    color = MerlotColors.Border,
                    shape = RoundedCornerShape(0.dp)
                )
                .onFocusChanged { isExpanded = it.hasFocus }
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Logo area — CLICKABLE to open profile picker
            var logoFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (logoFocused) Color(0xFF666666).copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .border(
                        width = if (logoFocused) 2.dp else 0.dp,
                        color = if (logoFocused) MerlotColors.Accent else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .onFocusChanged { logoFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter -> {
                                    showProfilePicker = true
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .focusable()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isExpanded) {
                    val activeProfile = profiles.find { it.id == activeProfileId }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (activeProfile != null) {
                            ProfileAvatar(profile = activeProfile, size = 24)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeProfile.name,
                                color = MerlotColors.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "MERLOT TV",
                                color = MerlotColors.Accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                } else {
                    val activeProfile = profiles.find { it.id == activeProfileId }
                    if (activeProfile != null && activeProfile.avatarUrl.isNotEmpty()) {
                        ProfileAvatar(profile = activeProfile, size = 28)
                    } else {
                        Text(
                            text = "M",
                            color = MerlotColors.Accent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation items
            Screen.sidebarItems.forEachIndexed { index, screen ->
                SidebarItem(
                    screen = screen,
                    isSelected = currentRoute == screen.route,
                    isExpanded = isExpanded,
                    onClick = { onNavigate(screen) },
                    modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
                )
            }
        }

        // Profile picker overlay — rendered OVER everything
        if (showProfilePicker) {
            ProfilePickerOverlay(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelectProfile = { profileId ->
                    profileVm.selectProfile(profileId)
                    showProfilePicker = false
                },
                onDismiss = { showProfilePicker = false }
            )
        }
    }
}

// ─── Profile Picker Overlay ─────────────────────────────────────────────────

@Composable
private fun ProfilePickerOverlay(
    profiles: List<UserProfile>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Focus requester for the first profile row — auto-focus on open
    val firstProfileFocus = remember { FocusRequester() }

    // Auto-focus the first profile when overlay opens
    LaunchedEffect(Unit) {
        try {
            firstProfileFocus.requestFocus()
        } catch (_: Exception) {}
    }

    // Full-screen semi-transparent backdrop
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
        contentAlignment = Alignment.TopStart
    ) {
        // Profile picker card
        Column(
            modifier = Modifier
                .padding(start = 80.dp, top = 32.dp)
                .width(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Surface2)
                .border(1.dp, MerlotColors.Border, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Switch Profile",
                color = MerlotColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            profiles.forEachIndexed { index, profile ->
                val isActive = profile.id == activeProfileId
                ProfileRow(
                    profile = profile,
                    isActive = isActive,
                    onSelect = { onSelectProfile(profile.id) },
                    modifier = if (index == 0) Modifier.focusRequester(firstProfileFocus) else Modifier
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Press BACK to close",
                color = MerlotColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: UserProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isActive && isFocused -> MerlotColors.AccentAlpha20
                    isFocused -> Color(0xFF666666).copy(alpha = 0.3f)
                    isActive -> MerlotColors.AccentAlpha10
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MerlotColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            onSelect()
                            true
                        }
                        Key.Back -> {
                            // Let Back bubble up to the overlay backdrop
                            false
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(profile = profile, size = 32)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = profile.name,
            color = if (isActive) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Active",
                tint = MerlotColors.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    profile: UserProfile,
    size: Int
) {
    if (profile.avatarUrl.isNotEmpty()) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = profile.name,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
        )
    } else {
        val colorInt = ProfileDataStore.AVATAR_COLORS.getOrElse(profile.colorIndex) {
            ProfileDataStore.AVATAR_COLORS[0]
        }
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Color(colorInt)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = (size / 2).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Sidebar Item ───────────────────────────────────────────────────────────

@Composable
private fun SidebarItem(
    screen: Screen,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusedGrey = Color(0xFF666666)

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && isFocused -> focusedGrey.copy(alpha = 0.4f)
            isFocused -> focusedGrey.copy(alpha = 0.3f)
            isSelected -> MerlotColors.AccentAlpha10
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "bgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> MerlotColors.Accent
            isFocused -> MerlotColors.White
            else -> MerlotColors.TextMuted
        },
        animationSpec = tween(150),
        label = "iconColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> focusedGrey
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "borderColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (screen is Screen.SpaceX) {
            Icon(
                painter = painterResource(R.drawable.ic_spacex_x),
                contentDescription = screen.title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = screen.icon,
                contentDescription = screen.title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = screen.title,
                color = iconColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
