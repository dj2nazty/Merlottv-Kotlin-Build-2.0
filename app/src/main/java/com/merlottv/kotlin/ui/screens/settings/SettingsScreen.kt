package com.merlottv.kotlin.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.merlottv.kotlin.ui.theme.MerlotColors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var playlistInput by remember { mutableStateOf(uiState.playlistUrl) }
    var torboxInput by remember { mutableStateOf(uiState.torboxKey) }
    var addonInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            color = MerlotColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Playlist section
        SettingsSection(title = "Playlist / M3U", icon = { Icon(Icons.Default.List, null, tint = MerlotColors.Accent) }) {
            OutlinedTextField(
                value = playlistInput,
                onValueChange = { playlistInput = it },
                label = { Text("Playlist URL", color = MerlotColors.TextMuted) },
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.savePlaylistUrl(playlistInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Playlist", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stremio Addons section
        SettingsSection(title = "Stremio Addons", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
            Text("Default addons are built-in and cannot be removed.", color = MerlotColors.TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))

            uiState.addons.forEach { addon ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MerlotColors.Surface2, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(addon.name, color = MerlotColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(addon.url, color = MerlotColors.TextMuted, fontSize = 9.sp, maxLines = 1)
                    }
                    if (addon.isDefault) {
                        Text("built-in", color = MerlotColors.Accent, fontSize = 9.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addonInput,
                    onValueChange = { addonInput = it },
                    placeholder = { Text("https://addon.example.com/manifest.json", color = MerlotColors.TextMuted, fontSize = 10.sp) },
                    colors = settingsFieldColors(),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.addAddon(addonInput); addonInput = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Torbox section
        SettingsSection(title = "Torbox", icon = { Icon(Icons.Default.Settings, null, tint = MerlotColors.Accent) }) {
            OutlinedTextField(
                value = torboxInput,
                onValueChange = { torboxInput = it },
                label = { Text("Torbox API Key", color = MerlotColors.TextMuted) },
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.saveTorboxKey(torboxInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MerlotColors.Accent, contentColor = MerlotColors.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About section
        SettingsSection(title = "About", icon = { Icon(Icons.Default.Info, null, tint = MerlotColors.Accent) }) {
            Text("Merlot TV", color = MerlotColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Kotlin Build 2.0", color = MerlotColors.Accent, fontSize = 12.sp)
            Text("Version 2.0.0", color = MerlotColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MerlotColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = MerlotColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MerlotColors.TextPrimary,
    unfocusedTextColor = MerlotColors.TextPrimary,
    cursorColor = MerlotColors.Accent,
    focusedBorderColor = MerlotColors.Accent,
    unfocusedBorderColor = MerlotColors.Border
)
