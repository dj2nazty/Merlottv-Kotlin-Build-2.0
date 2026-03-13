package com.merlottv.kotlin.ui.screens.profiles

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.UserProfile
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun ProfilePickerScreen(
    onProfileSelected: (String) -> Unit,
    viewModel: ProfilePickerViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background),
        contentAlignment = Alignment.Center
    ) {
        if (showCreateDialog) {
            CreateProfileDialog(
                profileCount = profiles.size,
                onConfirm = { name, colorIndex ->
                    viewModel.createAndSelectProfile(name, colorIndex, onProfileSelected)
                    showCreateDialog = false
                },
                onDismiss = { showCreateDialog = false }
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Who's Watching?",
                    color = MerlotColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            onClick = {
                                viewModel.selectProfile(profile.id)
                                onProfileSelected(profile.id)
                            }
                        )
                    }

                    if (profiles.size < 6) {
                        item(key = "add") {
                            AddProfileCard(
                                onClick = { showCreateDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(
    profileCount: Int,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var profileName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableIntStateOf(profileCount % ProfileDataStore.AVATAR_COLORS.size) }

    // Voice input launcher
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) profileName = spoken
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .background(MerlotColors.Surface, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Create Profile",
                color = MerlotColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            var closeFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .onFocusChanged { closeFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            onDismiss(); true
                        } else false
                    }
            ) {
                Icon(
                    Icons.Default.Close, "Close",
                    tint = if (closeFocused) MerlotColors.Accent else MerlotColors.TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Preview avatar
        val avatarColor = ProfileDataStore.AVATAR_COLORS.getOrElse(selectedColor) { 0xFF00E5FF.toInt() }
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(avatarColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (profileName.isNotEmpty()) profileName.take(1).uppercase() else "?",
                color = MerlotColors.Black,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name input — standard OutlinedTextField (works natively with Android TV keyboard + voice)
        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            placeholder = { Text("Enter profile name", color = MerlotColors.TextMuted, fontSize = 14.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MerlotColors.TextPrimary,
                unfocusedTextColor = MerlotColors.TextPrimary,
                cursorColor = MerlotColors.Accent,
                focusedBorderColor = MerlotColors.Accent,
                unfocusedBorderColor = MerlotColors.Border
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            trailingIcon = {
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say profile name")
                    }
                    try { voiceLauncher.launch(intent) } catch (_: Exception) {}
                }) {
                    Icon(painter = painterResource(com.merlottv.kotlin.R.drawable.ic_mic), "Voice input", tint = MerlotColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Color picker
        Text("Choose Color", color = MerlotColors.TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileDataStore.AVATAR_COLORS.forEachIndexed { index, color ->
                var colorFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .onFocusChanged { colorFocused = it.isFocused }
                        .focusable()
                        .then(
                            if (colorFocused) Modifier.border(2.dp, MerlotColors.White, CircleShape)
                            else Modifier
                        )
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                                selectedColor = index; true
                            } else false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (index == selectedColor) {
                        Icon(Icons.Default.Check, null, tint = MerlotColors.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Confirm button
        var confirmFocused by remember { mutableStateOf(false) }
        Button(
            onClick = {
                val name = profileName.ifBlank { "Profile ${profileCount + 1}" }
                onConfirm(name, selectedColor)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (confirmFocused) MerlotColors.Accent else MerlotColors.Accent.copy(alpha = 0.9f),
                contentColor = MerlotColors.Black
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onFocusChanged { confirmFocused = it.isFocused }
                .focusable()
                .then(
                    if (confirmFocused) Modifier.border(2.dp, MerlotColors.White, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                        val name = profileName.ifBlank { "Profile ${profileCount + 1}" }
                        onConfirm(name, selectedColor)
                        true
                    } else false
                }
        ) {
            Text(
                "Create Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ProfileCard(profile: UserProfile, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val avatarColor = ProfileDataStore.AVATAR_COLORS.getOrElse(profile.colorIndex) { 0xFF00E5FF.toInt() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
    ) {
        Box(
            modifier = Modifier
                .size(if (isFocused) 110.dp else 100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isFocused) Color(avatarColor)
                    else Color(avatarColor).copy(alpha = 0.7f)
                )
                .then(
                    if (isFocused) Modifier.border(3.dp, MerlotColors.Accent, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.name.take(1).uppercase(),
                color = MerlotColors.Black,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = profile.name,
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
    ) {
        Box(
            modifier = Modifier
                .size(if (isFocused) 110.dp else 100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MerlotColors.Surface2)
                .then(
                    if (isFocused) Modifier.border(3.dp, MerlotColors.Accent, RoundedCornerShape(16.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Profile",
                tint = if (isFocused) MerlotColors.Accent else MerlotColors.TextMuted,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Add Profile",
            color = if (isFocused) MerlotColors.Accent else MerlotColors.TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
