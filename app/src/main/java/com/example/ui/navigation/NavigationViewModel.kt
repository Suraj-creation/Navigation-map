package com.example.ui.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ar.ARNavigationEngine
import com.example.core.ar.ARSessionManager
import com.example.core.location.FusedLocationEngine
import com.example.core.location.MockLocationEngine
import com.example.core.navigation.NavigationEngine
import com.example.core.sensors.DeviceOrientationSensor
import com.example.data.local.AppDatabase
import com.example.data.repository.NavigationRepository
import com.example.data.repository.NavigationRepositoryImpl
import com.example.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository: NavigationRepository = NavigationRepositoryImpl(database)

    // Engines
    val navigationEngine = NavigationEngine()
    val arNavigationEngine = ARNavigationEngine()
    val arSessionManager = ARSessionManager(application)
    val orientationSensor = DeviceOrientationSensor(application)

    val fusedLocationEngine = FusedLocationEngine(application)
    val mockLocationEngine = MockLocationEngine()

    // Simulation toggle state
    private val _isDeveloperMode = MutableStateFlow(false)
    val isDeveloperMode: StateFlow<Boolean> = _isDeveloperMode.asStateFlow()

    // UI Exploration State
    private val _curatedDestinations = MutableStateFlow<List<Destination>>(emptyList())
    val curatedDestinations: StateFlow<List<Destination>> = _curatedDestinations.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedDestination = MutableStateFlow<Destination?>(null)
    val selectedDestination: StateFlow<Destination?> = _selectedDestination.asStateFlow()

    private val _isArModeActive = MutableStateFlow(false)
    val isArModeActive: StateFlow<Boolean> = _isArModeActive.asStateFlow()

    // Navigation Session State from NavigationEngine
    val sessionState: StateFlow<NavigationSessionState> = navigationEngine.sessionState

    // AR Guidance State
    val arGuidance: StateFlow<ARGuidance?> = arNavigationEngine.guidanceFlow
    val arConfidence: StateFlow<ARConfidence> = arNavigationEngine.confidenceFlow
    val orientation = orientationSensor.orientationFlow

    val recentDestinations = repository.getRecentDestinations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadDestinations()
        setupSensorsAndLocationTracking()
    }

    private fun loadDestinations() {
        viewModelScope.launch {
            _curatedDestinations.value = repository.getCuratedDestinations()
        }
    }

    private fun setupSensorsAndLocationTracking() {
        orientationSensor.start()
        fusedLocationEngine.start()

        // Active location observer (either Mock engine when simulating, or Fused GPS)
        viewModelScope.launch {
            combine(
                mockLocationEngine.locationFlow,
                fusedLocationEngine.locationFlow,
                _isDeveloperMode,
                orientationSensor.orientationFlow
            ) { mockLoc, realLoc, isSim, orient ->
                val activeLoc = if (isSim) mockLoc else realLoc
                navigationEngine.updateLocation(activeLoc.point, orient.azimuthDegrees)

                // Push update to AR Navigation Engine
                val state = navigationEngine.sessionState.value
                arNavigationEngine.update(
                    currentLocation = activeLoc.point,
                    orientation = orient,
                    currentManeuver = state.currentManeuver,
                    nextManeuver = state.nextManeuver,
                    destination = state.activeDestination,
                    routePolyline = state.currentRoute?.polylinePoints ?: emptyList(),
                    geospatialPose = arSessionManager.geospatialPose.value,
                    vpsStatus = arSessionManager.vpsStatus.value
                )
            }.collect()
        }
    }

    fun selectDestination(destination: Destination) {
        _selectedDestination.value = destination
        viewModelScope.launch {
            repository.saveRecentDestination(destination)
            val startPoint = if (_isDeveloperMode.value) {
                mockLocationEngine.locationFlow.value.point
            } else {
                fusedLocationEngine.locationFlow.value.point
            }

            val route = repository.getWalkingRoute(startPoint, destination)
            navigationEngine.prepareRoute(destination, route)
            if (_isDeveloperMode.value) {
                mockLocationEngine.setRoute(route)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _curatedDestinations.value = repository.searchDestinations(query)
        }
    }

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = category
        viewModelScope.launch {
            val all = repository.getCuratedDestinations()
            _curatedDestinations.value = if (category == "All") {
                all
            } else {
                all.filter { it.category.equals(category, ignoreCase = true) }
            }
        }
    }

    fun startNavigation() {
        navigationEngine.startNavigation()
        val currentRoute = sessionState.value.currentRoute
        if (_isDeveloperMode.value && currentRoute != null) {
            mockLocationEngine.setRoute(currentRoute)
            mockLocationEngine.start()
        }
    }

    fun enterArMode() {
        _isArModeActive.value = true
        val dest = sessionState.value.activeDestination
        if (dest != null) {
            arSessionManager.checkVpsAvailability(dest.latitude, dest.longitude)
        }
    }

    fun exitArMode() {
        _isArModeActive.value = false
    }

    fun recenterAR() {
        arNavigationEngine.recenter()
    }

    fun toggleDeveloperMode() {
        val newMode = !_isDeveloperMode.value
        _isDeveloperMode.value = newMode
        if (newMode) {
            val route = sessionState.value.currentRoute
            if (route != null) {
                mockLocationEngine.setRoute(route)
            }
        } else {
            mockLocationEngine.stop()
        }
    }

    fun setSimulationSpeed(multiplier: Float) {
        mockLocationEngine.speedMultiplier = multiplier
    }

    fun toggleSimulationPlayback() {
        if (mockLocationEngine.isPlaying.value) {
            mockLocationEngine.stop()
        } else {
            mockLocationEngine.start()
        }
    }

    fun stepSimulationOnce() {
        mockLocationEngine.stepOnce(8.0)
    }

    fun resetSimulation() {
        mockLocationEngine.reset()
    }

    fun injectSimulationDrift() {
        mockLocationEngine.injectOffRouteDrift(45.0)
    }

    fun recalculateOffRoute() {
        viewModelScope.launch {
            val state = sessionState.value
            val dest = state.activeDestination ?: return@launch
            val newRoute = repository.getRecalculatedRoute(state.currentPosition, dest)
            navigationEngine.triggerRecalculation(newRoute)
            if (_isDeveloperMode.value) {
                mockLocationEngine.setRoute(newRoute)
                mockLocationEngine.start()
            }
        }
    }

    fun endNavigation() {
        mockLocationEngine.stop()
        navigationEngine.stopNavigation()
        _isArModeActive.value = false
        _selectedDestination.value = null
    }

    override fun onCleared() {
        super.onCleared()
        orientationSensor.stop()
        fusedLocationEngine.stop()
        mockLocationEngine.stop()
        arSessionManager.destroy()
    }
}
