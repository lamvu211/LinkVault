package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit
) {
    // Dynamic theme colors mirroring MaterialTheme
    val colorScheme = MaterialTheme.colorScheme
    val NaturalBg = colorScheme.background
    val NaturalText = colorScheme.onBackground
    val NaturalPrimary = colorScheme.primary
    val NaturalSecondary = colorScheme.secondary
    val NaturalTertiary = colorScheme.tertiary
    val NaturalBorder = colorScheme.outline

    var showChooser by remember { mutableStateOf(false) }
    var showCustomInput by remember { mutableStateOf(false) }
    var customEmailInput by remember { mutableStateOf("") }
    var customEmailError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NaturalBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().testTag("login_screen_container")
        ) {
            // Elegant Graphic Logo Bubble
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primaryContainer)
                    .border(1.dp, NaturalBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = NaturalPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Heading
            Text(
                text = "LinkVault",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NaturalText,
                modifier = Modifier.testTag("login_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle description
            Text(
                text = "Keep your shared social feeds and URLs organized in one safe space.",
                fontSize = 14.sp,
                color = NaturalTertiary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Google Sign In Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { showChooser = true }
                    .testTag("google_login_button"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, NaturalBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Styled dynamic G logo
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            color = Color(0xFF4285F4),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Sign in with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3C4043)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Secure Google accounts authentication required to access LinkVault features.",
                fontSize = 11.sp,
                color = NaturalTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Account Chooser Sheet Dialogue
        if (showChooser) {
            Dialog(
                onDismissRequest = { showChooser = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showChooser = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f)
                            .clickable(enabled = false, onClick = {}) // block clicks
                            .testTag("auth_chooser_container"),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        colors = CardDefaults.cardColors(containerColor = NaturalBg),
                        border = BorderStroke(1.dp, NaturalBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header drag point
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(NaturalSecondary.copy(alpha = 0.2f))
                                    .align(Alignment.CenterHorizontally)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Choose an account",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NaturalText
                                )
                                Text(
                                    text = "to continue to LinkVault",
                                    fontSize = 12.sp,
                                    color = NaturalTertiary
                                )
                            }

                            Divider(color = NaturalBorder.copy(alpha = 0.5f))

                            // Device accounts listing
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Account Option 1: lamvu211@gmail.com
                                DeviceAccountItem(
                                    email = "lamvu211@gmail.com",
                                    name = "Lam Vu",
                                    onSelect = {
                                        showChooser = false
                                        onLoginSuccess("lamvu211@gmail.com")
                                    }
                                )

                                // Account Option 2: testuser@gmail.com
                                DeviceAccountItem(
                                    email = "testuser@gmail.com",
                                    name = "Test User",
                                    onSelect = {
                                        showChooser = false
                                        onLoginSuccess("testuser@gmail.com")
                                    }
                                )

                                // Custom Account Switcher Trigger
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showCustomInput = true }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(NaturalBorder.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", fontSize = 18.sp, color = NaturalPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "Use another Google account",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = NaturalPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Dialog for custom gmail typing
                    if (showCustomInput) {
                        Dialog(onDismissRequest = { showCustomInput = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, NaturalBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Add Google Account",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NaturalText
                                    )

                                    OutlinedTextField(
                                        value = customEmailInput,
                                        onValueChange = {
                                            customEmailInput = it
                                            customEmailError = ""
                                        },
                                        placeholder = { Text("username@gmail.com") },
                                        label = { Text("Google Email") },
                                        isError = customEmailError.isNotEmpty(),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_email_textfield"),
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            val mail = customEmailInput.trim()
                                            if (mail.endsWith("@gmail.com") && mail.length > 10) {
                                                showCustomInput = false
                                                showChooser = false
                                                onLoginSuccess(mail)
                                            } else {
                                                customEmailError = "Please enter a valid Google account (@gmail.com)"
                                            }
                                        }),
                                        leadingIcon = {
                                            Icon(Icons.Default.Email, contentDescription = null, tint = NaturalSecondary)
                                        }
                                    )

                                    if (customEmailError.isNotEmpty()) {
                                        Text(customEmailError, color = Color.Red, fontSize = 11.sp)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { showCustomInput = false }) {
                                            Text("Cancel", color = NaturalTertiary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val mail = customEmailInput.trim()
                                                if (mail.endsWith("@gmail.com") && mail.length > 10) {
                                                    showCustomInput = false
                                                    showChooser = false
                                                    onLoginSuccess(mail)
                                                } else {
                                                    customEmailError = "Please enter a valid Google account (@gmail.com)"
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Sign In", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceAccountItem(
    email: String,
    name: String,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val initials = if (name.isNotEmpty()) name.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2) else "G"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Column(modifier = Modifier.weight(1.0f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )
            Text(
                text = email,
                fontSize = 12.sp,
                color = colors.tertiary
            )
        }
    }
}
