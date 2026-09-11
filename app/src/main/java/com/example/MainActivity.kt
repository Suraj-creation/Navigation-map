package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.permissions.PermissionHandler
import com.example.domain.model.NavigationState
import com.example.ui.ar.ARCameraScreen
import com.example.ui.home.HomeScreen
import com.example.ui.navigation.MapNavigationScreen
import com.example.ui.navigation.NavigationViewModel
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpaceNavyDark
import com.example.ui.theme.SpaceNavySurface
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

enum class AppScreen {
    HOME,
    MAP_NAV,
    AR_NAV
}

class MainActivity : ComponentActivity() {

    private val viewModel: NavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: NavigationViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val sessionState by viewModel.sessionState.collectAsState()
    val isArActive by viewModel.isArModeActive.collectAsState()

    var hasCameraPerm by remember { mutableStateOf(PermissionHandler.hasCameraPermission(context)) }
    var hasLocationPerm by remember { mutableStateOf(PermissionHandler.hasLocationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPerm = results[android.Manifest.permission.CAMERA] ?: false
        hasLocationPerm = results[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm || !hasLocationPerm) {
            permissionLauncher.launch(PermissionHandler.REQUIRED_PERMISSIONS)
        }
    }

    // Keep screen state synced with navigation engine
    LaunchedEffect(sessionState.navigationState, isArActive) {
        if (sessionState.navigationState == NavigationState.IDLE) {
            currentScreen = AppScreen.HOME
        } else if (isArActive) {
            currentScreen = AppScreen.AR_NAV
        } else if (sessionState.navigationState != NavigationState.IDLE) {
            currentScreen = AppScreen.MAP_NAV
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SpaceNavyDark)) {
        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToRoute = {
                        if (!hasCameraPerm || !hasLocationPerm) {
                            permissionLauncher.launch(PermissionHandler.REQUIRED_PERMISSIONS)
                        }
                        currentScreen = AppScreen.MAP_NAV
                    }
                )
            }
            AppScreen.MAP_NAV -> {
                MapNavigationScreen(
                    viewModel = viewModel,
                    onLaunchAR = {
                        if (hasCameraPerm) {
                            viewModel.enterArMode()
                            currentScreen = AppScreen.AR_NAV
                        } else {
                            permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
                        }
                    },
                    onExitNavigation = {
                        viewModel.endNavigation()
                        currentScreen = AppScreen.HOME
                    }
                )
            }
            AppScreen.AR_NAV -> {
                if (hasCameraPerm) {
                    ARCameraScreen(
                        viewModel = viewModel,
                        onSwitchToMapView = {
                            viewModel.exitArMode()
                            currentScreen = AppScreen.MAP_NAV
                        },
                        onExitNavigation = {
                            viewModel.endNavigation()
                            currentScreen = AppScreen.HOME
                        }
                    )
                } else {
                    // Fallback camera permission prompt
                    CameraPermissionFallback(
                        onRequestPermission = {
                            permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
                        },
                        onBackToMap = {
                            viewModel.exitArMode()
                            currentScreen = AppScreen.MAP_NAV
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPermissionFallback(
    onRequestPermission: () -> Unit,
    onBackToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        color = SpaceNavyDark
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Camera Permission",
                tint = CyanGlow,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Camera Access Required",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Geospatial AR overlay projects navigation chevrons and real-time guidance directly onto your live camera view.",
                color = TextSecondaryDark,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) {
                Text("Grant Camera Permission", color = SpaceNavyDark, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onBackToMap) {
                Text("Back to 2D Map", color = TextSecondaryDark)
            }
        }
    }
}
