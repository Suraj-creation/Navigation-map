package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.ui.theme.*
import kotlin.math.*

/**
 * World-space AR directional overlay rendered on top of camera feed.
 */
@Composable
fun ARWorldSpaceOverlay(
    guidance: ARGuidance?,
    currentManeuver: NavigationManeuver?,
    modifier: Modifier = Modifier
) {
    if (guidance == null) return

    val infiniteTransition = rememberInfiniteTransition(label = "ar_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow_offset"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val centerX = screenWidth / 2f
        val centerY = screenHeight * 0.58f

        // Camera horizontal FOV approx 60 degrees (+-30 degrees from center)
        val fovHalfAngle = 30f
        val relativeAngle = guidance.relativeAngleDegrees

        val isTargetInFov = abs(relativeAngle) <= fovHalfAngle

        // Screen X position mapped from relative angle
        val targetX = if (isTargetInFov) {
            centerX + (relativeAngle / fovHalfAngle) * (screenWidth * 0.42f)
        } else {
            if (relativeAngle < 0) screenWidth * 0.08f else screenWidth * 0.92f
        }

        // Perspective Y based on distance and device pitch
        val pitchOffset = (guidance.pitchDegrees.coerceIn(-30f, 30f) / 30f) * (screenHeight * 0.15f)
        val targetY = (centerY - pitchOffset).coerceIn(screenHeight * 0.25f, screenHeight * 0.85f)

        // 1. Ground Path Ribbon
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPathRibbon(
                ribbonPoints = guidance.ribbonPoints,
                centerX = centerX,
                bottomY = screenHeight * 0.95f,
                targetX = targetX,
                targetY = targetY,
                flowOffset = flowOffset,
                confidence = guidance.confidence
            )
        }

        // 2. Destination Beacon (if visible)
        if (guidance.isDestinationBeaconVisible) {
            DestinationBeaconCanvas(
                x = targetX,
                y = targetY - 60f,
                distanceMeters = guidance.destinationBeaconDistance,
                pulseAlpha = pulseAlpha
            )
        }

        // 3. Floating 3D Directional Chevrons & Arrow
        if (isTargetInFov) {
            FloatingARArrowCanvas(
                x = targetX,
                y = targetY,
                angleDegrees = relativeAngle,
                rollDegrees = guidance.rollDegrees,
                maneuverType = currentManeuver?.type ?: ManeuverType.STRAIGHT,
                distanceMeters = guidance.distanceMeters,
                pulseAlpha = pulseAlpha
            )
        } else {
            // Peripheral edge indicator pointing towards hidden target
            PeripheralTurnIndicator(
                isTurnLeft = relativeAngle < 0,
                angleOffset = abs(relativeAngle),
                pulseAlpha = pulseAlpha,
                modifier = Modifier.align(
                    if (relativeAngle < 0) Alignment.CenterStart else Alignment.CenterEnd
                )
            )
        }
    }
}

/**
 * Draws animated glowing ground ribbon indicating walking path.
 */
private fun DrawScope.drawPathRibbon(
    ribbonPoints: List<LatLngPoint>,
    centerX: Float,
    bottomY: Float,
    targetX: Float,
    targetY: Float,
    flowOffset: Float,
    confidence: ARConfidence
) {
    val ribbonColor = when (confidence) {
        ARConfidence.HIGH -> CyanGlow
        ARConfidence.MEDIUM -> TealAccent
        ARConfidence.LOW -> AmberBeacon
        ARConfidence.UNAVAILABLE -> RedAlert
    }

    val path = Path().apply {
        moveTo(centerX - 40f, bottomY)
        quadraticBezierTo(
            centerX,
            (bottomY + targetY) / 2f,
            targetX - 15f,
            targetY
        )
        lineTo(targetX + 15f, targetY)
        quadraticBezierTo(
            centerX,
            (bottomY + targetY) / 2f,
            centerX + 40f,
            bottomY
        )
        close()
    }

    // Gradient fill representing glowing light stream
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                ribbonColor.copy(alpha = 0.05f),
                ribbonColor.copy(alpha = 0.45f),
                ribbonColor.copy(alpha = 0.75f)
            ),
            startY = targetY,
            endY = bottomY
        )
    )

    // Animated chevron rings on the path
    val ringCount = 5
    for (i in 0 until ringCount) {
        val t = ((i.toFloat() / ringCount) + (flowOffset / 300f)) % 1f
        val ringY = bottomY - (bottomY - targetY) * t
        val ringWidth = 70f * (1f - t * 0.65f)
        val ringX = centerX + (targetX - centerX) * t

        drawOval(
            color = Color.White.copy(alpha = (1f - t) * 0.8f),
            topLeft = Offset(ringX - ringWidth / 2f, ringY - 4f),
            size = androidx.compose.ui.geometry.Size(ringWidth, 8f),
            style = Stroke(width = 3f)
        )
    }
}

/**
 * Floating 3D holographic directional arrow with banking and depth scaling.
 */
@Composable
private fun FloatingARArrowCanvas(
    x: Float,
    y: Float,
    angleDegrees: Float,
    rollDegrees: Float,
    maneuverType: ManeuverType,
    distanceMeters: Double,
    pulseAlpha: Float
) {
    // Dynamic scale: larger when closer (12m -> scale 1.3, 80m -> scale 0.85)
    val baseScale = (1.4f - (distanceMeters.toFloat() / 100f) * 0.5f).coerceIn(0.75f, 1.4f)
    val bankingAngle = (-angleDegrees * 0.6f) - (rollDegrees * 0.4f)

    Canvas(
        modifier = Modifier
            .offset(x = (x - 60).dp, y = (y - 60).dp)
            .size(120.dp)
            .rotate(bankingAngle)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)

        // Outer aura ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CyanGlow.copy(alpha = pulseAlpha * 0.6f),
                    Color.Transparent
                ),
                center = center,
                radius = 55f * baseScale
            ),
            radius = 55f * baseScale,
            center = center
        )

        // Draw dynamic maneuver arrow shape
        val arrowPath = Path().apply {
            when (maneuverType) {
                ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT -> {
                    moveTo(center.x - 20f, center.y + 25f)
                    lineTo(center.x + 5f, center.y + 25f)
                    lineTo(center.x + 5f, center.y + 5f)
                    lineTo(center.x + 28f, center.y + 5f)
                    lineTo(center.x + 5f, center.y - 22f)
                    lineTo(center.x - 18f, center.y + 5f)
                    lineTo(center.x - 5f, center.y + 5f)
                    lineTo(center.x - 5f, center.y + 15f)
                    lineTo(center.x - 20f, center.y + 15f)
                    close()
                }
                ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT -> {
                    moveTo(center.x + 20f, center.y + 25f)
                    lineTo(center.x - 5f, center.y + 25f)
                    lineTo(center.x - 5f, center.y + 5f)
                    lineTo(center.x - 28f, center.y + 5f)
                    lineTo(center.x - 5f, center.y - 22f)
                    lineTo(center.x + 18f, center.y + 5f)
                    lineTo(center.x + 5f, center.y + 5f)
                    lineTo(center.x + 5f, center.y + 15f)
                    lineTo(center.x + 20f, center.y + 15f)
                    close()
                }
                else -> {
                    // Straight forward chevron
                    moveTo(center.x, center.y - 32f)
                    lineTo(center.x + 26f, center.y + 10f)
                    lineTo(center.x + 10f, center.y + 10f)
                    lineTo(center.x + 10f, center.y + 28f)
                    lineTo(center.x - 10f, center.y + 28f)
                    lineTo(center.x - 10f, center.y + 10f)
                    lineTo(center.x - 26f, center.y + 10f)
                    close()
                }
            }
        }

        // Draw arrow body with gradient and clean crisp outline
        drawPath(
            path = arrowPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, CyanGlow, TealAccent)
            )
        )
        drawPath(
            path = arrowPath,
            color = SpaceNavyDark,
            style = Stroke(width = 3f)
        )

        // Pulsing inner center dot
        drawCircle(
            color = AmberBeacon,
            radius = 5f * baseScale,
            center = center
        )
    }
}

/**
 * Destination vertical landmark beacon.
 */
@Composable
private fun DestinationBeaconCanvas(
    x: Float,
    y: Float,
    distanceMeters: Double,
    pulseAlpha: Float
) {
    Canvas(
        modifier = Modifier
            .offset(x = (x - 40).dp, y = (y - 120).dp)
            .size(80.dp, 160.dp)
    ) {
        val centerX = size.width / 2f
        val bottomY = size.height

        // Vertical holographic light pillar
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, AmberBeacon.copy(alpha = pulseAlpha), AmberBeacon),
                startY = 0f,
                endY = bottomY
            ),
            start = Offset(centerX, 0f),
            end = Offset(centerX, bottomY),
            strokeWidth = 6f
        )

        // Floating rings around beacon
        drawOval(
            color = AmberBeacon.copy(alpha = pulseAlpha),
            topLeft = Offset(centerX - 24f, 40f),
            size = androidx.compose.ui.geometry.Size(48f, 16f),
            style = Stroke(width = 3f)
        )

        // Pinhead Icon
        drawCircle(
            color = AmberBeacon,
            radius = 12f,
            center = Offset(centerX, 20f)
        )
        drawCircle(
            color = Color.White,
            radius = 6f,
            center = Offset(centerX, 20f)
        )
    }
}

/**
 * Peripheral turn guide when target is outside phone camera FOV.
 */
@Composable
private fun PeripheralTurnIndicator(
    isTurnLeft: Boolean,
    angleOffset: Float,
    pulseAlpha: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        color = SpaceNavySurface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanGlow.copy(alpha = pulseAlpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isTurnLeft) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Turn Left",
                    tint = CyanGlow,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = if (isTurnLeft) "Turn Left" else "Turn Right",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "${angleOffset.roundToInt()}° off view",
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
            }
            if (!isTurnLeft) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Turn Right",
                    tint = CyanGlow,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Top AR Maneuver HUD Banner with large distance countdown & turn typography.
 */
@Composable
fun ARNavigationTopHUD(
    sessionState: NavigationSessionState,
    confidence: ARConfidence,
    onConfidenceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maneuver = sessionState.currentManeuver
    val isManeuverNow = sessionState.navigationState == NavigationState.MANEUVER_NOW

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ar_top_hud"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SpaceNavyDark.copy(alpha = 0.94f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isManeuverNow) AmberBeacon else SpaceNavyBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Maneuver Icon + Distance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isManeuverNow) AmberBeacon else CyanPrimaryDark),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (maneuver?.type) {
                            ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
                            ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
                            ManeuverType.DESTINATION -> Icons.Default.Place
                            else -> Icons.Default.Straight
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = maneuver?.instruction,
                            tint = if (isManeuverNow) SpaceNavyDark else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isManeuverNow) "NOW" else "in ${sessionState.distanceToManeuverMeters.roundToInt()} m",
                            color = if (isManeuverNow) AmberBeacon else TextPrimaryDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = maneuver?.instruction ?: "Proceed along path",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }

                // AR Confidence status chip
                ARConfidenceChip(
                    confidence = confidence,
                    onClick = onConfidenceClick
                )
            }

            if (!maneuver?.secondaryInstruction.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = maneuver!!.secondaryInstruction,
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 58.dp)
                )
            }
        }
    }
}

/**
 * AR Confidence Pill Chip.
 */
@Composable
fun ARConfidenceChip(
    confidence: ARConfidence,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, color, icon) = when (confidence) {
        ARConfidence.HIGH -> Triple("VPS Live", GreenConfidence, Icons.Default.CheckCircle)
        ARConfidence.MEDIUM -> Triple("Stabilizing", AmberBeacon, Icons.Default.GpsFixed)
        ARConfidence.LOW -> Triple("Sensors Calibrating", RedAlert, Icons.Default.Warning)
        ARConfidence.UNAVAILABLE -> Triple("Sensor Fallback", TextMutedDark, Icons.Default.SensorsOff)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("ar_confidence_chip"),
        color = SpaceNavySurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
