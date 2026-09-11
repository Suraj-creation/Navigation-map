package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.sensors.DeviceOrientation
import com.example.domain.model.NavigationSessionState
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun DeveloperModePanel(
    isPlaying: Boolean,
    speedMultiplier: Float,
    sessionState: NavigationSessionState,
    orientation: DeviceOrientation,
    onTogglePlay: () -> Unit,
    onStepOnce: () -> Unit,
    onReset: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onInjectDrift: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("developer_mode_panel"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SpaceNavySurface.copy(alpha = 0.95f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Simulation Mode",
                        tint = CyanGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "WALKING SIMULATOR",
                        color = CyanGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Developer Panel",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Real-time sensor readout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpaceNavyDark)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Heading", color = TextMutedDark, fontSize = 10.sp)
                    Text("${orientation.azimuthDegrees.roundToInt()}°", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pitch/Roll", color = TextMutedDark, fontSize = 10.sp)
                    Text("${orientation.pitchDegrees.roundToInt()}° / ${orientation.rollDegrees.roundToInt()}°", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Maneuver Dist", color = TextMutedDark, fontSize = 10.sp)
                    Text("${sessionState.distanceToManeuverMeters.roundToInt()}m", color = CyanGlow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) AmberBeacon else CyanPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("sim_play_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = SpaceNavyDark
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "Pause Walk" else "Auto Walk",
                        color = SpaceNavyDark,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onStepOnce,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("sim_step_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Step Forward",
                        tint = TextPrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Step +8m", color = TextPrimaryDark, fontSize = 12.sp)
                }

                IconButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("sim_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Route",
                        tint = TextSecondaryDark
                    )
                }
            }

            // Speed & Deviation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Selector
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1.0f, 2.0f, 4.0f).forEach { speed ->
                        FilterChip(
                            selected = speedMultiplier == speed,
                            onClick = { onSpeedChange(speed) },
                            label = { Text("${speed.toInt()}x", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimaryDark,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Off Route Injector
                Button(
                    onClick = onInjectDrift,
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("sim_drift_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.WrongLocation,
                        contentDescription = "Simulate Off Route",
                        tint = SpaceNavyDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simulate Off-Route", color = SpaceNavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
