package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.VoiceEffect

@Composable
fun VoicePresetCard(
    effect: VoiceEffect,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (effect) {
        VoiceEffect.ORIGINAL -> Icons.Default.Mic
        VoiceEffect.NATURAL_FEMALE -> Icons.Default.Face
        VoiceEffect.YOUNG_BOY -> Icons.Default.ChildCare
        VoiceEffect.DEEP_MAN -> Icons.Default.RecordVoiceOver
        VoiceEffect.CHIPMUNK -> Icons.Default.MusicNote
        VoiceEffect.ROBOT -> Icons.Default.SmartToy
    }

    val badgeText = when (effect) {
        VoiceEffect.NATURAL_FEMALE -> "Natural"
        VoiceEffect.YOUNG_BOY -> "Clear"
        VoiceEffect.DEEP_MAN -> "Deep"
        VoiceEffect.CHIPMUNK -> "High"
        VoiceEffect.ROBOT -> "FX"
        VoiceEffect.ORIGINAL -> "Clean"
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preset_${effect.id}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon bubble
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = effect.displayName,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = effect.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        }
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = effect.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Pitch indicator
            val pitchDelta = ((effect.pitchFactor - 1.0f) * 100).toInt()
            val pitchStr = when {
                pitchDelta > 0 -> "+$pitchDelta%"
                pitchDelta < 0 -> "$pitchDelta%"
                else -> "1.0x"
            }

            Text(
                text = pitchStr,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
