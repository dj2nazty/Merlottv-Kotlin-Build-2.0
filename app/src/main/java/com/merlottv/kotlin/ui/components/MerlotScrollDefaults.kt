package com.merlottv.kotlin.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Custom scroll defaults inspired by NuvioTV's spring-based scroll animation.
 * Uses spring physics for natural, weighted scrolling and centers items at
 * 42% of the viewport for a more cinematic browsing feel.
 */
@OptIn(ExperimentalFoundationApi::class)
object MerlotScrollDefaults {

    /**
     * Spring-based scroll spec that centers items at 42% of the viewport.
     * - dampingRatio 0.95 = nearly critical damping (smooth settle, no bounce)
     * - stiffness 180 = responsive but not snappy
     */
    val smoothScrollSpec = object : BringIntoViewSpec {
        @Suppress("DEPRECATION")
        override val scrollAnimationSpec: AnimationSpec<Float> = spring(
            dampingRatio = 0.95f,
            stiffness = 180f
        )

        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float
        ): Float {
            if (containerSize <= 0f || size <= 0f) return 0f
            val itemCenter = offset + size / 2f
            val viewportTarget = containerSize * 0.42f
            return itemCenter - viewportTarget
        }
    }
}
