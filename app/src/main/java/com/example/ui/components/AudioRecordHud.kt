package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RecordingState
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AudioRecordHud(
    recordingState: RecordingState,
    durationSeconds: Int,
    amplitude: Int,
    isPlaying: Boolean,
    hasRecordedAudio: Boolean,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onTogglePlayAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseColor by animateColorAsState(
        targetValue = when (recordingState) {
            RecordingState.RECORDING -> RoseError
            RecordingState.PAUSED -> AmberPending
            else -> CyanAccent
        },
        label = "pulseColor"
    )

    val formattedTime = String.format(
        Locale.US,
        "%02d:%02d",
        durationSeconds / 60,
        durationSeconds % 60
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(if (recordingState == RecordingState.RECORDING) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(pulseColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (recordingState) {
                            RecordingState.RECORDING -> "LIVE RECORDING SPEAKER..."
                            RecordingState.PAUSED -> "RECORDING PAUSED"
                            RecordingState.STOPPED -> "AUDIO CAPTURED"
                            RecordingState.IDLE -> "SPEAKER AUDIO RECORDER"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pulseColor,
                        letterSpacing = 1.sp
                    )
                }

                // Digital Timer
                Text(
                    text = formattedTime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }

            // Live Waveform equalizer simulation
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barCount = 24
                val normalizedAmp = (amplitude.toFloat() / 32768f).coerceIn(0.1f, 1f)
                for (i in 0 until barCount) {
                    val factor = kotlin.math.sin(i.toDouble() * 0.4 + (durationSeconds * 2)).toFloat().let {
                        (it + 1f) / 2f
                    }
                    val barHeight = when (recordingState) {
                        RecordingState.RECORDING -> (8 + factor * 20 * normalizedAmp).coerceIn(4f, 26f).dp
                        RecordingState.PAUSED -> 6.dp
                        else -> 4.dp
                    }
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (recordingState == RecordingState.RECORDING) {
                                    if (i % 2 == 0) CyanAccent else AmberPending
                                } else {
                                    ObsidianBorder
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (recordingState) {
                    RecordingState.IDLE -> {
                        Button(
                            onClick = onStartRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoseError,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Record Speaker Live", fontWeight = FontWeight.Bold)
                        }
                    }

                    RecordingState.RECORDING -> {
                        OutlinedButton(
                            onClick = onPauseRecording,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberPending),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pause_recording_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause")
                        }

                        Button(
                            onClick = onStopRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoseError,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_recording_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop & Keep", fontWeight = FontWeight.Bold)
                        }
                    }

                    RecordingState.PAUSED -> {
                        Button(
                            onClick = onResumeRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resume_recording_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume")
                        }

                        Button(
                            onClick = onStopRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoseError,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_recording_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Finish", fontWeight = FontWeight.Bold)
                        }
                    }

                    RecordingState.STOPPED -> {
                        if (hasRecordedAudio) {
                            OutlinedButton(
                                onClick = onTogglePlayAudio,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isPlaying) AmberPending else CyanAccent
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_audio_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Play Audio",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPlaying) "Pause Playback" else "Listen to Audio")
                            }

                            Button(
                                onClick = onStartRecording,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ObsidianBorder,
                                    contentColor = TextSecondary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("record_again_button")
                            ) {
                                Text("Retake")
                            }
                        }
                    }
                }
            }
        }
    }
}
