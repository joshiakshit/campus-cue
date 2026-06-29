package com.campuscue.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshi.core.ui.components.AnimatedIconLoader
import com.joshi.core.ui.theme.AppShapes

@Composable
fun AccessCheckingScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedIconLoader(message = "Checking your access…")
        }
    }
}

@Suppress("LongMethod")
@Composable
fun AccessWaitingScreen(
    isRefreshing: Boolean,
    isRequestingSpecialAccess: Boolean,
    error: String?,
    specialAccessMessage: String?,
    onRefresh: () -> Unit,
    onRequestSpecialAccess: (referralName: String) -> Unit,
    onSignOut: () -> Unit,
) {
    var referralName by rememberSaveable { mutableStateOf("") }
    var showReferral by rememberSaveable { mutableStateOf(false) }
    val referralSubmitted = specialAccessMessage != null

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 44.dp)
                    .widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.HourglassTop,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Authorization in Process",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                if (referralSubmitted) {
                    "Your referral has been submitted.\nWe'll get back to you shortly."
                } else {
                    "Your OTP is verified. Access approval can\ntake up to 48 hours."
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                ErrorBanner(error)
            }

            if (referralSubmitted) {
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = AppShapes.large,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text(
                        "Check Status",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }

            AnimatedVisibility(visible = !referralSubmitted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = onRefresh,
                        enabled = !isRefreshing && !isRequestingSpecialAccess,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = AppShapes.large,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                "Check Status",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showReferral,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(20.dp))

                            OutlinedTextField(
                                value = referralName,
                                onValueChange = { referralName = it },
                                label = { Text("Referral name") },
                                placeholder = { Text("Someone already using CampusCue") },
                                singleLine = true,
                                enabled = !isRefreshing && !isRequestingSpecialAccess,
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.large,
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    ),
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = { onRequestSpecialAccess(referralName.trim()) },
                                enabled =
                                    !isRefreshing && !isRequestingSpecialAccess &&
                                        referralName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = AppShapes.large,
                            ) {
                                if (isRequestingSpecialAccess) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text(
                                        "Submit Referral",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = !referralSubmitted && !showReferral) {
                TextButton(onClick = { showReferral = true }) {
                    Text(
                        "Have a referral?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onSignOut,
                enabled = !isRefreshing && !isRequestingSpecialAccess,
            ) {
                Text(
                    "Sign Out",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
