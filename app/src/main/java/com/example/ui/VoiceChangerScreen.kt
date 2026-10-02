package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.audio.VoiceEffect
import com.example.ui.components.AppsLauncherSection
import com.example.ui.components.LiveMicSection
import com.example.ui.components.VisualizerWave
import com.example.ui.components.VoicePresetCard
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChangerScreen(
    viewModel: VoiceChangerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPitchSlider by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Voice Changer",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = "Voice Changer",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runThreeSecondDiagnostic() },
                        modifier = Modifier.testTag("diagnostic_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Run 3-Second Audio Test"
                        )
                    }
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.testTag("info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help & Information"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Permission Card if not granted
                if (!uiState.hasMicPermission) {
                    item {
                        PermissionRequiredCard(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }
                }

                // Mode Tabs: Record & Transform vs Realtime Live Mic vs Apps
                item {
                    val selectedIndex = when (uiState.activeMode) {
                        ActiveMode.RECORD_AND_TRANSFORM -> 0
                        ActiveMode.REALTIME_MIC -> 1
                        ActiveMode.APPS -> 2
                    }
                    TabRow(
                        selectedTabIndex = selectedIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        indicator = {}
                    ) {
                        Tab(
                            selected = uiState.activeMode == ActiveMode.RECORD_AND_TRANSFORM,
                            onClick = { viewModel.setMode(ActiveMode.RECORD_AND_TRANSFORM) },
                            modifier = Modifier
                                .testTag("tab_record_mode")
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.activeMode == ActiveMode.RECORD_AND_TRANSFORM) {
                                        MaterialTheme.colorScheme.primary
                                    } else Color.Transparent
                                ),
                            text = {
                                Text(
                                    text = "Record",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.activeMode == ActiveMode.RECORD_AND_TRANSFORM) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        Tab(
                            selected = uiState.activeMode == ActiveMode.REALTIME_MIC,
                            onClick = { viewModel.setMode(ActiveMode.REALTIME_MIC) },
                            modifier = Modifier
                                .testTag("tab_realtime_mode")
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.activeMode == ActiveMode.REALTIME_MIC) {
                                        MaterialTheme.colorScheme.primary
                                    } else Color.Transparent
                                ),
                            text = {
                                Text(
                                    text = "Live Mic",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.activeMode == ActiveMode.REALTIME_MIC) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        Tab(
                            selected = uiState.activeMode == ActiveMode.APPS,
                            onClick = { viewModel.setMode(ActiveMode.APPS) },
                            modifier = Modifier
                                .testTag("tab_apps_mode")
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.activeMode == ActiveMode.APPS) {
                                        MaterialTheme.colorScheme.primary
                                    } else Color.Transparent
                                ),
                            text = {
                                Text(
                                    text = "Apps",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.activeMode == ActiveMode.APPS) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                if (uiState.activeMode == ActiveMode.APPS) {
                    item {
                        AppsLauncherSection(
                            apps = uiState.targetApps,
                            onLaunchApp = { viewModel.launchApp(context, it) }
                        )
                    }
                } else {
                    // Voice Presets Section Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Voice Presets",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            TextButton(
                                onClick = { showPitchSlider = !showPitchSlider },
                                modifier = Modifier.testTag("fine_tune_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Fine Tune Pitch",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showPitchSlider) "Hide Fine Tune" else "Fine Tune",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // Fine Tune Pitch Slider
                    if (showPitchSlider) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Pitch Fine Adjustment",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        val semi = uiState.finePitchSemitones
                                        Text(
                                            text = if (semi > 0) "+$semi semitones" else "$semi semitones",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }

                                    Slider(
                                        value = uiState.finePitchSemitones.toFloat(),
                                        onValueChange = { viewModel.setFinePitch(it.toInt()) },
                                        valueRange = -6f..6f,
                                        steps = 11,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pitch_slider"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Presets Cards List (Natural Female & Young Boy highlighted)
                    VoiceEffect.values().forEach { effect ->
                        item(key = effect.id) {
                            VoicePresetCard(
                                effect = effect,
                                isSelected = uiState.selectedEffect == effect,
                                onSelect = { viewModel.selectEffect(effect) }
                            )
                        }
                    }

                    // Mode Specific Content
                    if (uiState.activeMode == ActiveMode.RECORD_AND_TRANSFORM) {
                        item {
                            RecordingStudioCard(
                                uiState = uiState,
                                onToggleRecord = {
                                    if (!uiState.hasMicPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleRecording()
                                    }
                                },
                                onTogglePlay = { viewModel.togglePlayback() },
                                onToggleAudition = { viewModel.toggleAuditionOriginal(it) },
                                onSeek = { viewModel.seekPlayback(it) },
                                onDiscard = { viewModel.discardRecording() },
                                onShare = { viewModel.shareActiveVoice(context) }
                            )
                        }
                    } else if (uiState.activeMode == ActiveMode.REALTIME_MIC) {
                        // Real-Time Mic Mode
                        item {
                            LiveMicSection(
                                isLiveActive = uiState.isLiveActive,
                                amplitude = uiState.amplitude,
                                selectedEffect = uiState.selectedEffect,
                                onToggleLive = {
                                    if (!uiState.hasMicPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleLiveMic()
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showInfoDialog) {
        VoiceChangerInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
private fun RecordingStudioCard(
    uiState: VoiceChangerUiState,
    onToggleRecord: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleAudition: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onDiscard: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Waveform visualizer
            VisualizerWave(
                amplitude = uiState.amplitude,
                isActive = uiState.isRecording || uiState.isPlaying,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timer & Status Text
            val timerText = when {
                uiState.isRecording -> formatDuration(uiState.recordingDurationMs)
                uiState.hasRecording -> "${formatDuration(uiState.playbackPositionMs)} / ${formatDuration(uiState.playbackDurationMs)}"
                else -> "00:00"
            }

            Text(
                text = timerText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.isProcessing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Transforming voice...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = when {
                        uiState.isRecording -> "Recording in progress... Speak clearly."
                        uiState.isPlaying -> "Playing: ${uiState.activeAudioName ?: uiState.selectedEffect.displayName}"
                        uiState.hasRecording -> if (uiState.isAuditioningOriginal) "Ready: Original Voice (Raw Mic)" else "Ready: ${uiState.activeAudioName ?: uiState.selectedEffect.displayName}"
                        else -> "Tap microphone to record voice"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.hasRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                uiState.diagnostic?.let { diag ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${diag.sampleRate}Hz | ${diag.channelCount}ch Mono | ${diag.audioEncoding}\nDuration: ${String.format(Locale.US, "%.2f", diag.durationMs / 1000.0)}s | Samples: ${diag.sampleCount} | Min: ${diag.minSample} / Max: ${diag.maxSample} | RMS: ${String.format(Locale.US, "%.1f", diag.rmsPercent)}% | Distinct: ${diag.distinctValuesCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (diag.isValidVoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Original vs Transformed Audition Toggle
            if (uiState.hasRecording && !uiState.isRecording) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (uiState.isAuditioningOriginal) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onToggleAudition(true) }
                            .testTag("audition_original_tab")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (uiState.isAuditioningOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Original Voice",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (uiState.isAuditioningOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (!uiState.isAuditioningOriginal) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onToggleAudition(false) }
                            .testTag("audition_transformed_tab")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (!uiState.isAuditioningOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Transformed",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (!uiState.isAuditioningOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Playback progress bar
            if (uiState.hasRecording && uiState.playbackDurationMs > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val progress = (uiState.playbackPositionMs.toFloat() / uiState.playbackDurationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.hasRecording && !uiState.isRecording) {
                    // Discard / New Recording
                    FilledTonalButton(
                        onClick = onDiscard,
                        modifier = Modifier
                            .testTag("discard_button")
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "New Record",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Play / Pause Audio
                    Button(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .testTag("play_button")
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val playLabel = if (uiState.isPlaying) "Pause" else if (uiState.isAuditioningOriginal) "Play Original" else "Play Transformed"
                        Text(
                            text = playLabel,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Share Audio
                    FilledTonalButton(
                        onClick = onShare,
                        modifier = Modifier
                            .testTag("share_button")
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                } else {
                    // Big Recording FAB with pulsing ring when active
                    RecordActionButton(
                        isRecording = uiState.isRecording,
                        onClick = onToggleRecord
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordActionButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(68.dp)
                .testTag("record_button"),
            shape = CircleShape,
            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Permission Needed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Microphone Access Required",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Grant permission to record and change your voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.testTag("grant_permission_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Allow", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VoiceChangerInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Voice Changer Guide",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "• Natural Female: Shift pitch upward (+34%) with feminine formant filtering around 2.9 kHz for natural vocal tract resonance.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Young Boy: Clean youthful vocal timbre (+22% pitch) with treble clarity.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Record & Transform: Tap record, speak into your mic, and tap stop. The audio is instantly transformed into the chosen voice and played back with high fidelity.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Real-Time Mic: Direct live monitoring. Connect headphones to avoid audio feedback loop.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Android Platform Audio Policy: The Android OS strictly isolates the hardware microphone per process. Standard Android does not allow apps to inject audio directly into third-party game voice chats (such as Yalla Ludo) without external hardware loopback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
