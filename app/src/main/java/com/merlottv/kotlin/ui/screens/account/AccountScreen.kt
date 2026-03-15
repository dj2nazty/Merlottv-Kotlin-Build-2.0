package com.merlottv.kotlin.ui.screens.account

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.merlottv.kotlin.ui.theme.MerlotColors

private const val LINK_BASE_URL = "https://dj2nazty.github.io/merlottv-link"

@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MerlotColors.Background)
            .padding(24.dp)
    ) {
        when (uiState.mode) {
            AccountMode.SIGNED_OUT -> SignedOutView(
                onSignIn = viewModel::showSignIn,
                onSignUp = viewModel::showSignUp,
                onDeviceCode = viewModel::startDeviceCodeFlow
            )
            AccountMode.SIGN_IN -> SignInView(
                state = uiState,
                onEmailChange = viewModel::updateEmail,
                onPasswordChange = viewModel::updatePassword,
                onSubmit = viewModel::signIn,
                onBack = viewModel::goBack
            )
            AccountMode.SIGN_UP -> SignUpView(
                state = uiState,
                onEmailChange = viewModel::updateEmail,
                onPasswordChange = viewModel::updatePassword,
                onConfirmPasswordChange = viewModel::updateConfirmPassword,
                onSubmit = viewModel::signUp,
                onBack = viewModel::goBack
            )
            AccountMode.DEVICE_CODE -> DeviceCodeView(
                state = uiState,
                onBack = viewModel::goBack
            )
            AccountMode.DEVICE_CODE_PASSWORD -> DeviceCodePasswordView(
                state = uiState,
                onPasswordChange = viewModel::updatePassword,
                onSubmit = viewModel::signInWithLinkedEmail,
                onBack = viewModel::goBack
            )
            AccountMode.SIGNED_IN -> SignedInView(
                state = uiState,
                onSignOut = viewModel::signOut
            )
        }
    }
}

@Composable
private fun SignedOutView(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onDeviceCode: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MerlotColors.Accent
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "MerlotTV Account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sign in to sync favorites, watch history, and settings across all your devices",
            fontSize = 14.sp,
            color = MerlotColors.TextMuted,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        AccountActionButton(
            text = "Sign In",
            icon = Icons.Default.Login,
            onClick = onSignIn,
            isPrimary = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        AccountActionButton(
            text = "Create Account",
            icon = Icons.Default.PersonAdd,
            onClick = onSignUp,
            isPrimary = false
        )
        Spacer(modifier = Modifier.height(12.dp))
        AccountActionButton(
            text = "Scan QR Code",
            icon = Icons.Default.QrCode2,
            onClick = onDeviceCode,
            isPrimary = false
        )
    }
}

@Composable
private fun SignInView(
    state: AccountUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign In",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))

        AccountTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email",
            icon = Icons.Default.Email
        )
        Spacer(modifier = Modifier.height(12.dp))
        AccountTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Password",
            icon = Icons.Default.Lock,
            isPassword = true,
            onSubmit = onSubmit
        )

        if (state.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = state.error, fontSize = 13.sp, color = MerlotColors.Danger)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountActionButton(
                text = "Back",
                icon = Icons.Default.ArrowBack,
                onClick = onBack,
                isPrimary = false,
                modifier = Modifier.width(140.dp)
            )
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = MerlotColors.Accent, strokeWidth = 3.dp)
            } else {
                AccountActionButton(
                    text = "Sign In",
                    icon = Icons.Default.Login,
                    onClick = onSubmit,
                    isPrimary = true,
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}

@Composable
private fun SignUpView(
    state: AccountUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Create Account",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))

        AccountTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email",
            icon = Icons.Default.Email
        )
        Spacer(modifier = Modifier.height(12.dp))
        AccountTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Password",
            icon = Icons.Default.Lock,
            isPassword = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        AccountTextField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Confirm Password",
            icon = Icons.Default.Lock,
            isPassword = true,
            onSubmit = onSubmit
        )

        if (state.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = state.error, fontSize = 13.sp, color = MerlotColors.Danger)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountActionButton(
                text = "Back",
                icon = Icons.Default.ArrowBack,
                onClick = onBack,
                isPrimary = false,
                modifier = Modifier.width(140.dp)
            )
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = MerlotColors.Accent, strokeWidth = 3.dp)
            } else {
                AccountActionButton(
                    text = "Create",
                    icon = Icons.Default.PersonAdd,
                    onClick = onSubmit,
                    isPrimary = true,
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}

// ───── QR Code Device Login ─────

@Composable
private fun DeviceCodeView(
    state: AccountUiState,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isLoading || state.deviceCode == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MerlotColors.Accent,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Generating code...",
                fontSize = 16.sp,
                color = MerlotColors.TextMuted
            )
        } else {
            // Title
            Text(
                text = "Scan to Sign In",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MerlotColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // QR Code
                val qrUrl = "$LINK_BASE_URL?code=${state.deviceCode}"
                val qrBitmap = remember(qrUrl) { generateQrBitmap(qrUrl, 280) }
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR code for login",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Instructions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "1. Scan the QR code with your phone",
                        fontSize = 14.sp,
                        color = MerlotColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "2. Sign in with your MerlotTV account",
                        fontSize = 14.sp,
                        color = MerlotColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "3. Your TV will connect automatically",
                        fontSize = 14.sp,
                        color = MerlotColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Code display
                    Text(
                        text = "Or enter this code:",
                        fontSize = 12.sp,
                        color = MerlotColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val displayCode = state.deviceCode.let {
                        if (it.length == 6) "${it.substring(0, 3)}-${it.substring(3)}" else it
                    }
                    Text(
                        text = displayCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MerlotColors.Accent,
                        letterSpacing = 4.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Countdown
                    val minutes = state.deviceCodeExpiresIn / 60
                    val seconds = state.deviceCodeExpiresIn % 60
                    val timeColor = if (state.deviceCodeExpiresIn < 60) MerlotColors.Danger else MerlotColors.TextMuted
                    Text(
                        text = "Expires in ${minutes}:${"%02d".format(seconds)}",
                        fontSize = 13.sp,
                        color = timeColor
                    )
                }
            }

            if (state.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = state.error, fontSize = 13.sp, color = MerlotColors.Danger)
            }

            Spacer(modifier = Modifier.height(20.dp))
            AccountActionButton(
                text = "Cancel",
                icon = Icons.Default.ArrowBack,
                onClick = onBack,
                isPrimary = false
            )
        }
    }
}

@Composable
private fun DeviceCodePasswordView(
    state: AccountUiState,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCode2,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MerlotColors.Success
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Device Linked!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.Success
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter password for:",
            fontSize = 14.sp,
            color = MerlotColors.TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.linkedEmail ?: state.email,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.Accent
        )
        Spacer(modifier = Modifier.height(20.dp))

        AccountTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Password",
            icon = Icons.Default.Lock,
            isPassword = true,
            onSubmit = onSubmit
        )

        if (state.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = state.error, fontSize = 13.sp, color = MerlotColors.Danger)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountActionButton(
                text = "Back",
                icon = Icons.Default.ArrowBack,
                onClick = onBack,
                isPrimary = false,
                modifier = Modifier.width(140.dp)
            )
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = MerlotColors.Accent, strokeWidth = 3.dp)
            } else {
                AccountActionButton(
                    text = "Sign In",
                    icon = Icons.Default.Login,
                    onClick = onSubmit,
                    isPrimary = true,
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}

@Composable
private fun SignedInView(
    state: AccountUiState,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MerlotColors.AccentAlpha20)
                .border(2.dp, MerlotColors.Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (state.userEmail?.firstOrNull() ?: 'U').uppercase(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MerlotColors.Accent
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Signed In",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MerlotColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.userEmail ?: "",
            fontSize = 16.sp,
            color = MerlotColors.Accent
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MerlotColors.Success.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Sync Active",
                fontSize = 12.sp,
                color = MerlotColors.Success,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your favorites, watch history, and settings sync across all your MerlotTV devices",
            fontSize = 13.sp,
            color = MerlotColors.TextMuted,
            modifier = Modifier.padding(horizontal = 40.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        AccountActionButton(
            text = "Sign Out",
            icon = Icons.Default.Logout,
            onClick = onSignOut,
            isPrimary = false,
            buttonColor = MerlotColors.Danger
        )
    }
}

// ───── QR Code Generation ─────

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

// ───── Shared components ─────

@Composable
private fun AccountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    onSubmit: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = MerlotColors.TextMuted)
        },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .width(320.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && onSubmit != null) {
                    onSubmit()
                    true
                } else false
            },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MerlotColors.TextPrimary,
            unfocusedTextColor = MerlotColors.TextPrimary,
            focusedBorderColor = MerlotColors.Accent,
            unfocusedBorderColor = MerlotColors.Border,
            cursorColor = MerlotColors.Accent,
            focusedLabelColor = MerlotColors.Accent,
            unfocusedLabelColor = MerlotColors.TextMuted,
            focusedContainerColor = MerlotColors.Surface,
            unfocusedContainerColor = MerlotColors.Surface
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun AccountActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    buttonColor: Color? = null
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
        isPrimary -> Color.Black
        isFocused -> MerlotColors.Accent
        else -> MerlotColors.TextPrimary
    }
    val borderColor = when {
        isFocused -> MerlotColors.Accent
        buttonColor != null -> buttonColor.copy(alpha = 0.5f)
        else -> MerlotColors.Border
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .then(if (modifier == Modifier) Modifier.width(260.dp) else Modifier)
            .height(48.dp)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = textColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = textColor, fontWeight = FontWeight.Medium)
    }
}
