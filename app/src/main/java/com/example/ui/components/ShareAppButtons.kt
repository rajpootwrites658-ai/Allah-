package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.sharing.ShareTarget

@Composable
fun ShareAppButtons(
    isSharing: Boolean,
    onShareTo: (ShareTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share Voice Note",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Standard Audio Intent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShareAppItem(
                    label = "Yalla Ludo",
                    icon = Icons.Default.Casino,
                    bgColor = Color(0xFFEAB308),
                    iconTint = Color.Black,
                    testTag = "share_yalla_ludo",
                    enabled = !isSharing,
                    onClick = { onShareTo(ShareTarget.YALLA_LUDO) }
                )

                ShareAppItem(
                    label = "WhatsApp",
                    icon = Icons.Default.Chat,
                    bgColor = Color(0xFF25D366),
                    iconTint = Color.White,
                    testTag = "share_whatsapp",
                    enabled = !isSharing,
                    onClick = { onShareTo(ShareTarget.WHATSAPP) }
                )

                ShareAppItem(
                    label = "Instagram",
                    icon = Icons.Default.CameraAlt,
                    bgColor = Color(0xFFE1306C),
                    iconTint = Color.White,
                    testTag = "share_instagram",
                    enabled = !isSharing,
                    onClick = { onShareTo(ShareTarget.INSTAGRAM) }
                )

                ShareAppItem(
                    label = "Messenger",
                    icon = Icons.Default.Forum,
                    bgColor = Color(0xFF0084FF),
                    iconTint = Color.White,
                    testTag = "share_messenger",
                    enabled = !isSharing,
                    onClick = { onShareTo(ShareTarget.FACEBOOK_MESSENGER) }
                )

                ShareAppItem(
                    label = "More",
                    icon = Icons.Default.Share,
                    bgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    testTag = "share_general",
                    enabled = !isSharing,
                    onClick = { onShareTo(ShareTarget.ALL_APPS) }
                )
            }
        }
    }
}

@Composable
private fun ShareAppItem(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    iconTint: Color,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            modifier = Modifier.size(48.dp),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
