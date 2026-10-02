package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.YallaLudoDiagnostics

@Composable
fun YallaLudoDiagnosticsDialog(
    diagnostics: YallaLudoDiagnostics,
    onDismiss: () -> Unit,
    onLaunchYallaLudo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Yalla Ludo Audio Test",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Check items
                DiagnosticItem(
                    title = "Microphone Permission",
                    status = if (diagnostics.hasMicPermission) "Granted" else "Missing",
                    isSuccess = diagnostics.hasMicPermission
                )

                DiagnosticItem(
                    title = "Yalla Ludo Installation",
                    status = if (diagnostics.isInstalled) "Detected (${diagnostics.installedPackageName})" else "Not Installed",
                    isSuccess = diagnostics.isInstalled
                )

                DiagnosticItem(
                    title = "Mic Preemption / Conflict",
                    status = if (diagnostics.isMicInConflict) "Contended by other app" else "Clean (No Conflict)",
                    isSuccess = !diagnostics.isMicInConflict,
                    isWarning = diagnostics.isMicInConflict
                )

                DiagnosticItem(
                    title = "Direct In-App Virtual Mic Injection",
                    status = "Not Supported by Android OS",
                    isSuccess = false,
                    isBlocked = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Section 2: Detailed Architectural Explanation
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Android Security Architecture Constraint",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = diagnostics.injectionRestrictionReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Section 3: Legitimate Supported Solutions
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Officially Supported Workarounds",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Live Speaker Passthrough: Turn on Real-Time Voice Changer in speaker mode so Yalla Ludo or your external mic picks up the transformed voice.\n" +
                                    "2. Voice Notes / Clips: Share transformed clips directly into Yalla Ludo chats using the Share button.\n" +
                                    "3. External Audio Splitter: Connect a hardware 3.5mm TRRS splitter or virtual audio cable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (diagnostics.isInstalled) {
                    Button(
                        onClick = {
                            onDismiss()
                            onLaunchYallaLudo()
                        },
                        modifier = Modifier.testTag("dialog_open_yalla_button")
                    ) {
                        Text("Open Yalla Ludo")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dialog_close_button")
                ) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun DiagnosticItem(
    title: String,
    status: String,
    isSuccess: Boolean,
    isWarning: Boolean = false,
    isBlocked: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isBlocked -> MaterialTheme.colorScheme.error
                    isWarning -> Color(0xFFF59E0B)
                    isSuccess -> Color(0xFF10B981)
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }

        Icon(
            imageVector = when {
                isBlocked -> Icons.Default.Close
                isWarning -> Icons.Default.Warning
                isSuccess -> Icons.Default.CheckCircle
                else -> Icons.Default.Close
            },
            contentDescription = null,
            tint = when {
                isBlocked -> MaterialTheme.colorScheme.error
                isWarning -> Color(0xFFF59E0B)
                isSuccess -> Color(0xFF10B981)
                else -> MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(20.dp)
        )
    }
}
