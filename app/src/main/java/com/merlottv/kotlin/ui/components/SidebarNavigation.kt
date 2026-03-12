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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.merlottv.kotlin.ui.navigation.Screen
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun SidebarNavigation(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isExpanded by remember { mutableStateOf(false) }

    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 200.dp else 72.dp,
        animationSpec = tween(250),
        label = "sidebarWidth"
    )

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
        // Logo area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isExpanded) {
                Text(
                    text = "MERLOT TV",
                    color = MerlotColors.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    text = "M",
                    color = MerlotColors.Accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
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
}

@Composable
private fun SidebarItem(
    screen: Screen,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Single focus state source — no MutableInteractionSource split
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && isFocused -> MerlotColors.AccentAlpha20
            isFocused -> MerlotColors.Hover
            isSelected -> MerlotColors.AccentAlpha10
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "bgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected || isFocused -> MerlotColors.Accent
            else -> MerlotColors.TextMuted
        },
        animationSpec = tween(150),
        label = "iconColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> MerlotColors.Accent
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
            .focusable()
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.title,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

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
