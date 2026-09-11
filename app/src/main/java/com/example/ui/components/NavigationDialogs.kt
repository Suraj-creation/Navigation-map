package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.ar.GeospatialPoseData
import com.example.core.ar.VpsStatus
import com.example.domain.model.ARConfidence
import com.example.domain.model.Destination
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ArrivalDialog(
    destination: Destination,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("arrival_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceNavySurface),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GreenConfidence)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(GreenConfidence.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Destination Reached",
                        tint = GreenConfidence,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Text(
                    text = "You Have Arrived!",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = destination.name,
                    color = CyanGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = destination.address,
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                if (destination.description.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SpaceNavyDark)
                    ) {
                        Text(
                            text = destination.description,
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("arrival_finish_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text(
                        text = "Complete Trip",
                        color = SpaceNavyDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OffRouteBanner(
    distanceMeters: Double,
    onRecalculate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("off_route_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RedAlert),
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceNavyDark)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Off Route Warning",
                    tint = SpaceNavyDark,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Off Route (${distanceMeters.roundToInt()}m away)",
                        color = SpaceNavyDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Re-aligning path to destination...",
                        color = SpaceNavyDark.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }

            Button(
                onClick = onRecalculate,
                colors = ButtonDefaults.buttonColors(containerColor = SpaceNavyDark),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("recalculate_button")
            ) {
                Text(
                    text = "Reroute",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ARConfidenceDetailDialog(
    confidence: ARConfidence,
    vpsStatus: VpsStatus,
    geospatialPose: GeospatialPoseData,
    onRecenter: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceNavySurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SpaceNavyBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AR & VPS Telemetry",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryDark)
                    }
                }

                // Confidence Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceNavyDark)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tracking Confidence", color = TextSecondaryDark, fontSize = 13.sp)
                    ARConfidenceChip(confidence = confidence, onClick = {})
                }

                // Details list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryRow("VPS Localization", vpsStatus.name)
                    TelemetryRow("Geospatial Tracking", if (geospatialPose.isTracking) "ACTIVE" else "INITIALIZING")
                    TelemetryRow("Horizontal Accuracy", if (geospatialPose.isTracking) "${geospatialPose.horizontalAccuracyMeters.roundToInt()} m" else "Fused GPS (~4m)")
                    TelemetryRow("Heading Accuracy", if (geospatialPose.isTracking) "${geospatialPose.headingAccuracyDegrees.roundToInt()}°" else "Compass Sensor (~5°)")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onRecenter()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Recenter", tint = CyanGlow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recalibrate", color = CyanGlow, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Done", color = SpaceNavyDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMutedDark, fontSize = 13.sp)
        Text(value, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
