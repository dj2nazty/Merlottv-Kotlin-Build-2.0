package com.merlottv.kotlin.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.merlottv.kotlin.R
import kotlinx.coroutines.delay

/**
 * Full-screen video splash screen that plays merlot_launch.mp4 for a minimum
 * of 6 seconds before transitioning to the main app.
 */
@Composable
fun VideoSplashScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    // Auto-dismiss after 6 seconds minimum
    LaunchedEffect(Unit) {
        delay(6000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.merlot_launch}")
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.start()
                    }
                    setOnErrorListener { _, _, _ -> true } // Suppress errors
                    videoView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoView?.stopPlayback()
            } catch (_: Exception) {}
        }
    }
}
