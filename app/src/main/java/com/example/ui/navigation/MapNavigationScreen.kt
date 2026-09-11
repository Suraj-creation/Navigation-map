package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.navigation.GeoUtils
import com.example.domain.model.*
import com.example.ui.components.ArrivalDialog
import com.example.ui.components.DeveloperModePanel
import com.example.ui.components.OffRouteBanner
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun MapNavigationScreen(
    viewModel: NavigationViewModel,
    onLaunchAR: () -> Unit,
    onExitNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val isSimPlaying by viewModel.mockLocationEngine.isPlaying.collectAsState()

    var showDevPanel by remember { mutableStateOf(false) }

    // Check arrival state
    if (sessionState.navigationState == NavigationState.ARRIVED && sessionState.activeDestination != null) {
        ArrivalDialog(
            destination = sessionState.activeDestination!!,
            onDismiss = onExitNavigation
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceNavyDark)
            .testTag("map_navigation_screen")
    ) {
        // 1. Dynamic Map Canvas
        MapNavigationCanvas(
            sessionState = sessionState,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Maneuver HUD Card
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            val maneuver = sessionState.currentManeuver
            val isManeuverNow = sessionState.navigationState == NavigationState.MANEUVER_NOW

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("map_nav_top_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SpaceNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isManeuverNow) AmberBeacon else SpaceNavyBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
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
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isManeuverNow) "NOW" else "in ${sessionState.distanceToManeuverMeters.roundToInt()} m",
                            color = if (isManeuverNow) AmberBeacon else CyanGlow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = maneuver?.instruction ?: "Continue along route",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            if (sessionState.navigationState == NavigationState.OFF_ROUTE) {
                OffRouteBanner(
                    distanceMeters = sessionState.offRouteDistanceMeters,
                    onRecalculate = { viewModel.recalculateOffRoute() }
                )
            }
        }

        // 3. Quick Walking Simulator Button
        IconButton(
            onClick = {
                if (!isDeveloperMode) viewModel.toggleDeveloperMode()
                showDevPanel = !showDevPanel
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(SpaceNavySurface)
                .testTag("map_dev_mode_button")
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = "Walking Simulator",
                tint = if (isDeveloperMode) CyanGlow else TextSecondaryDark
            )
        }

        // 4. Developer Panel
        AnimatedVisibility(
            visible = showDevPanel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            DeveloperModePanel(
                isPlaying = isSimPlaying,
                speedMultiplier = viewModel.mockLocationEngine.speedMultiplier,
                sessionState = sessionState,
                orientation = orientation,
                onTogglePlay = { viewModel.toggleSimulationPlayback() },
                onStepOnce = { viewModel.stepSimulationOnce() },
                onReset = { viewModel.resetSimulation() },
                onSpeedChange = { viewModel.setSimulationSpeed(it) },
                onInjectDrift = { viewModel.injectSimulationDrift() },
                onClose = { showDevPanel = false }
            )
        }

        // 5. Bottom Navigation Action Bar with Live AR Trigger
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(26.dp),
            color = SpaceNavySurface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SpaceNavyBorder),
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${sessionState.etaSeconds / 60} min (${sessionState.totalRemainingDistanceMeters.toInt()} m)",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Destination: ${sessionState.activeDestination?.name ?: "Selected Point"}",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onExitNavigation,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RedAlert.copy(alpha = 0.2f))
                            .testTag("map_exit_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Exit Navigation", tint = RedAlert)
                    }
                }

                // Primary "See the Way" Live AR Button
                Button(
                    onClick = onLaunchAR,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("launch_ar_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = "Open AR Walking Navigation",
                        tint = SpaceNavyDark,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "See the Way (Live AR)",
                        color = SpaceNavyDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * 2D Map Canvas showing road grid, walking polyline route, user location beacon, and destination pin.
 */
@Composable
private fun MapNavigationCanvas(
    sessionState: NavigationSessionState,
    modifier: Modifier = Modifier
) {
    val route = sessionState.currentRoute
    val userPos = sessionState.currentPosition
    val heading = sessionState.deviceHeadingDegrees

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Subtle road grid pattern
        val gridSpacing = 60f
        for (x in 0..(width / gridSpacing).toInt()) {
            drawLine(
                color = SpaceNavyBorder.copy(alpha = 0.35f),
                start = Offset(x * gridSpacing, 0f),
                end = Offset(x * gridSpacing, height),
                strokeWidth = 1f
            )
        }
        for (y in 0..(height / gridSpacing).toInt()) {
            drawLine(
                color = SpaceNavyBorder.copy(alpha = 0.35f),
                start = Offset(0f, y * gridSpacing),
                end = Offset(width, y * gridSpacing),
                strokeWidth = 1f
            )
        }

        if (route != null && route.polylinePoints.isNotEmpty()) {
            // Coordinate normalization relative to viewport bounds
            val minLat = route.polylinePoints.minOf { it.latitude }
            val maxLat = route.polylinePoints.maxOf { it.latitude }
            val minLng = route.polylinePoints.minOf { it.longitude }
            val maxLng = route.polylinePoints.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.001)

            fun mapToScreen(lat: Double, lng: Double): Offset {
                val normX = ((lng - minLng) / lngSpan).toFloat()
                val normY = (1f - ((lat - minLat) / latSpan).toFloat()) // Invert Y
                return Offset(
                    x = width * 0.15f + normX * (width * 0.70f),
                    y = height * 0.25f + normY * (height * 0.50f)
                )
            }

            // Draw route polyline
            val path = Path()
            route.polylinePoints.forEachIndexed { idx, pt ->
                val screenPt = mapToScreen(pt.latitude, pt.longitude)
                if (idx == 0) path.moveTo(screenPt.x, screenPt.y) else path.lineTo(screenPt.x, screenPt.y)
            }

            // Route casing & glowing body
            drawPath(path = path, color = SpaceNavyDark, style = Stroke(width = 14f))
            drawPath(path = path, color = CyanGlow, style = Stroke(width = 8f))

            // Draw Destination Pin
            val destPt = mapToScreen(route.destination.latitude, route.destination.longitude)
            drawCircle(color = AmberBeacon, radius = 16f, center = destPt)
            drawCircle(color = Color.White, radius = 7f, center = destPt)

            // Draw User Location Puck with Directional Cone
            val userScreenPt = mapToScreen(userPos.latitude, userPos.longitude)
            drawCircle(
                color = CyanGlow.copy(alpha = 0.3f),
                radius = 28f,
                center = userScreenPt
            )
            drawCircle(
                color = CyanGlow,
                radius = 12f,
                center = userScreenPt
            )
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = userScreenPt
            )
        }
    }
}
