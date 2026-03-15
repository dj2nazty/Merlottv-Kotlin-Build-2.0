package com.merlottv.kotlin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.merlottv.kotlin.ui.theme.MerlotColors

/**
 * Custom chip composable that replaces Material3 FilterChip to eliminate
 * the default teal focus state layer that bleeds through from Material3's
 * theme primary color. Fully controlled styling — no Material3 internals.
 */
@Composable
fun MerlotChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedContainerColor: Color = MerlotColors.Accent,
    containerColor: Color = MerlotColors.Surface2,
    focusedContainerColor: Color = Color(0xFF555555),
    borderColor: Color = MerlotColors.Border,
    focusedBorderColor: Color = Color(0xFF888888),
    selectedBorderColor: Color = MerlotColors.Accent,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        selected -> selectedContainerColor
        isFocused -> focusedContainerColor
        else -> containerColor
    }

    val bdrColor = when {
        selected -> selectedBorderColor
        isFocused -> focusedBorderColor
        else -> borderColor
    }

    val bdrWidth = if (isFocused || selected) 2.dp else 1.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(bgColor, shape)
            .border(BorderStroke(bdrWidth, bdrColor), shape)
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        label()
    }
}
