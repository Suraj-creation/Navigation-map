package com.example.ui.ar

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.model.ARConfidence
import com.example.domain.model.NavigationState
import com.example.ui.components.*
import com.example.ui.navigation.NavigationViewModel
import com.example.ui.theme.*

@Composable
fun ARCameraScreen(
    viewModel: NavigationViewModel,
    onSwitchToMapView: () -> Unit,
    onExitNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionState by viewModel.sessionState.collectAsState()
    val arGuidance by viewModel.arGuidance.collectAsState()
    val arConfidence by viewModel.arConfidence.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val isSimPlaying by viewModel.mockLocationEngine.isPlaying.collectAsState()

    var showTelemetryDialog by remember { mutableStateOf(false) }
    var showDevPanel by remember { mutableStateOf(false) }

    // Check arrival state
    if (sessionState.navigationState == NavigationState.ARRIVED && sessionState.activeDestination != null) {
        ArrivalDialog(
            destination = sessionState.activeDestination!!,
            onDismiss = onExitNavigation
        )
    }

    if (showTelemetryDialog) {
        ARConfidenceDetailDialog(
            confidence = arConfidence,
            vpsStatus = viewModel.arSessionManager.vpsStatus.collectAsState().value,
            geospatialPose = viewModel.arSessionManager.geospatialPose.collectAsState().value,
            onRecenter = { viewModel.recenterAR() },
            onDismiss = { showTelemetryDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("ar_camera_screen")
    ) {
        // 1. CameraX Live Viewport
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    } catch (e: Exception) {
                        // Camera binding exception fallback
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle dark gradient vignette for contrast with HUD elements
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // 2. World-Space Directional Canvas (3D Arrow, Ribbon, Beacon)
        ARWorldSpaceOverlay(
            guidance = arGuidance,
            currentManeuver = sessionState.currentManeuver,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top HUD: Maneuver distance countdown & instruction
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            ARNavigationTopHUD(
                sessionState = sessionState,
                confidence = arConfidence,
                onConfidenceClick = { showTelemetryDialog = true }
            )

            // Off route alert banner
            if (sessionState.navigationState == NavigationState.OFF_ROUTE) {
                OffRouteBanner(
                    distanceMeters = sessionState.offRouteDistanceMeters,
                    onRecalculate = { viewModel.recalculateOffRoute() }
                )
            }
        }

        // 4. Quick Developer Floating Trigger Button
        IconButton(
            onClick = {
                if (!isDeveloperMode) viewModel.toggleDeveloperMode()
                showDevPanel = !showDevPanel
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(SpaceNavyDark.copy(alpha = 0.85f))
                .testTag("dev_mode_toggle_fab")
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = "Walking Simulator",
                tint = if (isDeveloperMode) CyanGlow else TextSecondaryDark,
                modifier = Modifier.size(24.dp)
            )
        }

        // 5. Developer Mode Panel Overlay
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

        // 6. Bottom Navigation Controls
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            color = SpaceNavyDark.copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, SpaceNavyBorder)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Trip info summary
                Column {
                    Text(
                        text = "ETA ${sessionState.etaSeconds / 60} min",
                        color = CyanGlow,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${sessionState.totalRemainingDistanceMeters.toInt()} m remaining",
                        color = TextSecondaryDark,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Recenter button
                    IconButton(
                        onClick = { viewModel.recenterAR() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SpaceNavySurfaceVariant)
                            .testTag("ar_recenter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Recenter AR",
                            tint = TextPrimaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Switch to 2D Map View button
                    Button(
                        onClick = onSwitchToMapView,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceNavySurfaceVariant),
                        modifier = Modifier.testTag("switch_to_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "2D Map View",
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Map",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Exit navigation button
                    IconButton(
                        onClick = onExitNavigation,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(RedAlert.copy(alpha = 0.2f))
                            .testTag("exit_navigation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Navigation",
                            tint = RedAlert,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
