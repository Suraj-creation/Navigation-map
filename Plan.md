# Android AR Navigation — End-to-End Engineering Architecture & Development Plan

## 1. Executive Summary & Product Architecture

The **AR Navigation** application is a native Android application engineered in **Kotlin** and **Jetpack Compose (Material 3)**. It provides pedestrian navigation by combining:
1. **Google Maps Platform** (Maps SDK for Android, Places SDK for Android, Routes API / Navigation infrastructure) for discovery, exploration, and geographic routing.
2. **ARCore Geospatial API & Device Sensors** (Visual Positioning Service - VPS, GNSS/GPS, Accelerometer, Gyroscope, Magnetometer) for continuous 6-DOF spatial positioning and real-world anchored guidance.
3. **Assistive 3D Augmented Reality UI Layer** that projects world-space directional arrows, proximity ribbons, and destination beacons directly into the device's camera stream.
4. **Resilient Fallback Navigation** that seamlessly transitions between True Geospatial AR, AR + GPS heading overlay, and conventional 2D Google Map navigation whenever environmental conditions or tracking fidelity change.

---

### Non-Coupled Architectural Paradigm

A key architectural mandate is **strict decoupling**: ARCore must **not** be driven directly by Google Maps, and Google Maps must **not** be driven by ARCore. Instead, a centralized, reactive **Navigation State Machine** acts as the single source of truth for both the 2D Map UI and the 3D AR Engine.

```
                         ┌───────────────────────────┐
                         │           USER            │
                         └─────────────┬─────────────┘
                                       │
                                       ▼
                         ┌───────────────────────────┐
                         │    AR Navigation App      │
                         └─────────────┬─────────────┘
                                       │
            ┌──────────────────────────┼──────────────────────────┐
            ▼                          ▼                          ▼
 ┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
 │   Maps SDK for Android│   │ Navigation Engine /  │   │  ARCore Geospatial   │
 │   & Places SDK       │   │ Routes API Client    │   │  & Sensor Fusion     │
 └──────────┬───────────┘   └──────────┬───────────┘   └──────────┬───────────┘
            │                          │                          │
            └──────────────────────────┼──────────────────────────┘
                                       │
                                       ▼
                       ┌───────────────────────────────┐
                       │    Central NavigationState    │
                       │ (StateFlow / Single Source)   │
                       └───────┬───────────────┬───────┘
                               │               │
                     ┌─────────┴───────┐       └─────────┐
                     ▼                                   ▼
          ┌─────────────────────┐             ┌─────────────────────┐
          │     Map View UI     │             │ AR Guidance Engine  │
          │   (Fallback / 2D)   │             │   (World Spatial)   │
          └─────────────────────┘             └──────────┬──────────┘
                                                         │
                                                         ▼
                                              ┌─────────────────────┐
                                              │ Live Camera Preview │
                                              │  + 3D AR Overlay    │
                                              └─────────────────────┘
```

---

## 2. Directory & Module Blueprint

The codebase strictly adheres to **Clean Architecture** (Data → Domain → Presentation) and prevents SDK coupling from leaking into UI composables:

```
app/src/main/java/com/example/
│
├── core/
│   ├── ar/
│   │   ├── ARConfidence.kt                // HIGH, MEDIUM, LOW, UNAVAILABLE
│   │   ├── ARNavigationEngine.kt          // 3D world pose math, ribbon math, arrow transforms
│   │   ├── ARSessionManager.kt            // ARCore lifecycle, Earth mode configuration
│   │   └── VpsAvailabilityChecker.kt      // Geospatial VPS query & outdoor checks
│   ├── location/
│   │   ├── LocationProvider.kt            // FusedLocationProviderClient wrapper
│   │   └── MockLocationEngine.kt          // Developer route simulation & step playback
│   ├── navigation/
│   │   ├── NavigationEngine.kt            // Route tracking, maneuver proximity, bearing delta
│   │   ├── NavigationState.kt             // Central State Machine enum & state payload
│   │   └── RouteCalculator.kt             // Walking route processing, polyline decoders
│   ├── permissions/
│   │   └── PermissionHandler.kt           // ACCESS_FINE_LOCATION, CAMERA onboarding rationale
│   └── sensors/
│       ├── DeviceOrientationSensor.kt     // Accelerometer + Magnetometer + Gyroscope fusion
│       └── SensorFusionFilter.kt          // Low-pass & complementary bearing stabilization
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt                 // Room database for saved places and history
│   │   ├── RecentDestinationDao.kt        // DAO for recent destinations
│   │   └── RecentDestinationEntity.kt     // Persisted place metadata & coordinates
│   ├── maps/
│   │   └── GoogleMapsDataSource.kt        // Map style, camera bounds, markers
│   ├── places/
│   │   ├── GooglePlacesDataSource.kt      // Autocomplete, Place search, Place details
│   │   └── PlacesRepositoryImpl.kt        // Repository implementation
│   └── repository/
│       ├── NavigationRepository.kt        // Navigation repository interface
│       └── NavigationRepositoryImpl.kt    // Routes fetching, demo presets, route caching
│
├── domain/
│   ├── model/
│   │   ├── ARGuidance.kt                  // Bearing, distance, arrow angle, ribbon coordinates
│   │   ├── Destination.kt                 // Place ID, name, coordinates, category, distance
│   │   ├── Maneuver.kt                    // Turn instruction, type, location, bearing, distance
│   │   ├── RouteInfo.kt                   // Overview polyline, distance m, duration s, steps
│   │   └── VpsStatus.kt                   // VPS availability status result
│   └── repository/
│       ├── PlacesRepository.kt            // Domain contract for destination discovery
│       └── RouteRepository.kt             // Domain contract for walking route generation
│
├── ui/
│   ├── ar/
│   │   ├── ARCameraScreen.kt              // Fullscreen camera viewfinder + floating guidance
│   │   ├── ARConfidenceBadge.kt           // Accuracy/confidence status chip
│   │   ├── ARDirectionArrow.kt            // 3D perspective world directional arrow
│   │   ├── ARNavigationRibbon.kt          // Upcoming 50-100m path ribbon projection
│   │   ├── ARDestinationBeacon.kt         // Spatially anchored arrival beacon with pulse
│   │   └── ARViewModel.kt                 // Bridges NavigationEngine and AR Guidance
│   ├── explore/
│   │   ├── ExploreNearbyChips.kt          // Culture, Food, Nature, Heritage filters
│   │   └── PlaceCard.kt                   // Selected place preview sheet
│   ├── home/
│   │   ├── HomeScreen.kt                  // Google Map base screen with search & presets
│   │   └── HomeViewModel.kt               // Map state, search query, location sync
│   ├── navigation/
│   │   ├── DestinationDetailScreen.kt     // Route overview, ETA, walking distance, "Start"
│   │   ├── MapNavigationScreen.kt         // Conventional turn-by-turn fallback screen
│   │   ├── NavigationHeader.kt            // Floating maneuver banner (Turn in 40m)
│   │   └── NavigationViewModel.kt         // Central navigation state holder
│   ├── components/
│   │   ├── DeveloperModePanel.kt          // Simulation controls: Play/Pause, Step, Speed
│   │   ├── OffRouteAlertBanner.kt         // "Off route - Recalculating" feedback
│   │   └── PermissionRationaleDialog.kt   // Explicit explanation for Fine Location & Camera
│   └── theme/
│       ├── Color.kt                       // M3 Dark/Light palettes with Cyan/Teal AR accents
│       ├── Shape.kt                       // Rounded corner hierarchy
│       ├── Theme.kt                       // Central MaterialTheme definition
│       └── Type.kt                        // Clear sans-serif hierarchy for legible HUD
│
└── MainActivity.kt                        // Single-activity Jetpack Compose root
```

---

## 3. Detailed Navigation State Machine

A deterministic state machine guarantees that both the 2D Map UI and the 3D AR UI always reflect the exact same state without drift:

```
┌────────┐
│  IDLE  │
└───┬────┘
    │ User selects Destination
    ▼
┌───────────┐
│ PREPARING │ ── (Fetch Route from Routes / Demo Engine)
└─────┬─────┘
      │
      ▼
┌─────────────┐
│ ROUTE_READY │ ── (Route preview on 2D map with walking ETA)
└─────┬───────┘
      │ User taps "Start Walking" or "See the Way"
      ▼
┌────────────┐
│ NAVIGATING │ ◄─────────────────────────────────────────────┐
└─────┬──────┘                                               │
      │ distanceToManeuver < 50m                             │ Maneuver completed
      ▼                                                      │
┌──────────────────────┐                                     │
│ APPROACHING_MANEUVER │                                     │
└─────┬────────────────┘                                     │
      │ distanceToManeuver < 12m                             │
      ▼                                                      │
┌──────────────┐                                             │
│ MANEUVER_NOW │ ────────────────────────────────────────────┘
└─────┬────────┘
      │ distanceFromRoute > 35m (Threshold violated)
      ▼
┌───────────┐
│ OFF_ROUTE │
└─────┬─────┘
      │ Trigger recalculate
      ▼
┌───────────────┐
│ RECALCULATING │ ── (Obtain new polyline) ──► ROUTE_READY ──► NAVIGATING
└───────────────┘
      │
      │ distanceToDestination < 15m
      ▼
┌─────────┐
│ ARRIVED │ ── (Destination beacon pulses, show Arrival Sheet & Explore)
└─────────┘
```

---

## 4. ARCore Geospatial API & Sensor Fusion Pipeline

### 4.1. The World-Space Projection Transformation

Instead of rotating a 2D arrow icon based solely on phone yaw (which jitters and feels flat), the application transforms spatial coordinates through real-world poses:

$$\text{Target Route Coordinate} (\text{Lat}, \text{Lng}, \text{Alt}) \xrightarrow{\text{WGS-84 to ECEF}} \text{Geospatial Earth Pose} \xrightarrow{\text{Camera 6-DOF Pose}} \text{Camera World Space} (X, Y, Z)$$

* **Camera World Space ($X, Y, Z$)**:
  * $+X$: Right of device.
  * $+Y$: Up (towards sky).
  * $-Z$: Forward in physical space (direction camera lens is aimed).
* **Relative Direction**:
  $$\theta_{\text{relative}} = \text{Bearing}_{\text{target}} - \text{Heading}_{\text{device}}$$
* In 3D rendering, this translates into a world-anchored transformation: the arrow tilts into the physical street, indicating forward perspective, banking into upcoming turns, and locking its position against the physical street geometry.

### 4.2. Selective Dynamic Anchoring

To ensure 60 FPS performance and avoid ARCore anchor bloat:
1. **Current Maneuver Anchor**: Placed at the coordinate of the immediate upcoming turn.
2. **Next Maneuver Anchor**: Cached and pre-calculated for smooth transition.
3. **50–100m Ribbon Anchor Strip**: 5 to 8 intermediate splines along the upcoming walking path segment, refreshed as the user advances.
4. **Destination Beacon Anchor**: Anchored at the target destination when within 300m, rendering a vertical light column and pulsing badge.

### 4.3. AR Confidence Matrix

| Confidence Level | Tracking Criteria | UX Presentation |
|---|---|---|
| **HIGH** | ARCore TrackingState == TRACKING, VPS available, Horizontal Accuracy < 5m, Heading Accuracy < 5° | Glowing Cyan AR Arrow, Full 3D Ribbon, "AR Navigation Active" |
| **MEDIUM** | GPS Horizontal Accuracy < 12m, Heading stabilized, VPS calibrating | Amber indicator: "AR positioning stabilizing — hold phone upright" |
| **LOW** | GPS Horizontal Accuracy > 15m, High magnetic interference | Warning Banner: "Low positioning accuracy — use map for best guidance" |
| **UNAVAILABLE** | ARCore unsupported, Camera denied, or Pitch/Roll invalid | Automatic fallback to Camera Directional Overlay or 2D Map Navigation |

---

## 5. Graceful Fallback Hierarchy

The system never strands the user if AR tracking is hindered (e.g. indoors, low light, or missing ARCore):

1. **Tier 1: Full Geospatial AR**: Live ARCore VPS session with 3D world-anchored geometry and path ribbon.
2. **Tier 2: Sensor-Fused Camera Overlay**: Live camera feed with sensor-fused (GPS + Gyroscope + Magnetometer) floating directional HUD.
3. **Tier 3: Conventional Map Navigation**: Full-screen Google Map with route polyline, user location puck, and floating turn banners.

---

## 6. Developer Mode & Realistic Simulation Engine

To allow seamless end-to-end testing and demonstrations without physically walking 1–2 km:
* **Built-in Developer Mode Toggle** in top app bar.
* **4 Built-in Curated Demo Routes**:
  1. *Kamalabari Satra, Majuli (Cultural Heritage Walk)* — Majuli, Assam.
  2. *Historic City Center Walk* — Urban high-density simulation with 4 turns.
  3. *Riverside Nature Trail* — Open sky trail demonstrating VPS outdoor guidance.
  4. *Artisan Market Route* — Short 250m pedestrian alleyway with tight maneuvers.
* **Simulation Controller**:
  * Play / Pause / Reset walking simulation.
  * Speed slider: 1x (normal walk ~1.4 m/s), 2x (jogging), 5x (fast test).
  * Triggers all states: Straight walking $\rightarrow$ Approaching turn ($<50\text{m}$) $\rightarrow$ Maneuver now ($<12\text{m}$) $\rightarrow$ Post-turn straight $\rightarrow$ Off-route drift injection $\rightarrow$ Destination arrival ($<15\text{m}$).

---

## 7. API Configuration, Secrets & Security

* Follows **`android-secret-management`** skill strictly.
* Keys are kept in `.env` (git-ignored) and templated in `.env.example`:
  * `MAPS_API_KEY`: For Google Maps SDK for Android & Places SDK.
  * `ROUTES_API_KEY`: For Google Routes API requests.
* Injected into `BuildConfig` and `AndroidManifest.xml` via the Secrets Gradle Plugin.
* App displays a non-blocking configuration prompt if keys are placeholder values, gracefully falling back to the rich offline simulation and mock provider so the demo is 100% operational immediately.

---

## 8. Implementation Phases & Roadmap

### **Phase 1: Project Foundations & Infrastructure Setup**
- [x] Platform metadata sync (`metadata.json` updated with app name and description).
- [x] App resource name sync (`strings.xml` and `settings.gradle.kts` updated).
- [x] Unique `applicationId` set (`com.aistudio.arnav.wkqz`).
- [x] Custom high-tech AR Navigation launcher icon generated and configured.
- [ ] Dependencies configured in `gradle/libs.versions.toml` and `app/build.gradle.kts`:
  - Google Play Services Location (`play-services-location`)
  - CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`)
  - Navigation Compose (`navigation-compose`)
  - Room & Coroutines
- [ ] Android Manifest permissions declared (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`, `INTERNET`, `VIBRATE`).

### **Phase 2: Domain Layer & Navigation State Machine**
- [ ] Implement `NavigationState` sealed class / enum and state payload.
- [ ] Implement domain models: `Destination`, `Maneuver`, `RouteInfo`, `ARGuidance`, `ARConfidence`.
- [ ] Build `NavigationEngine` with bearing math, distance calculations, and turn triggering logic.
- [ ] Implement `MockLocationEngine` for Developer Mode playback.

### **Phase 3: Data Layer & Persistence**
- [ ] Room database setup for destination caching and recent history (`AppDatabase`, `RecentDestinationDao`).
- [ ] Demo destinations catalog and Routes provider.
- [ ] Places & Routes repository abstractions.

### **Phase 4: Sensor Fusion & AR Core Calculations**
- [ ] `DeviceOrientationSensor` using `SensorManager` (Accelerometer + Magnetometer + Gyroscope).
- [ ] Bearing calculations: Relative angle $\theta = \text{Target Bearing} - \text{Device Heading}$.
- [ ] Screen projection math for floating directional arrows and perspective ribbons.
- [ ] AR Confidence evaluator based on sensor accuracy and location provider precision.

### **Phase 5: Camera & AR Guidance UI Layer**
- [ ] CameraX live viewfinder composable (`ARCameraScreen`).
- [ ] World-space floating directional 3D arrow with responsive turn banking.
- [ ] Dynamic perspective path ribbon (50-100m upcoming segment).
- [ ] Floating Maneuver HUD (`TURN RIGHT in 23m` $\rightarrow$ `TURN NOW`).
- [ ] Recenter button & calibration prompt (`Point camera toward surroundings`).
- [ ] Destination beacon visualizer with pulsing distance tag.

### **Phase 6: Conventional Map View & Explore Screen**
- [ ] Interactive 2D map composable with route polyline and live location puck.
- [ ] Destination discovery sheet with nearby categories ([Culture], [Food], [Nature], [Heritage]).
- [ ] Destination detail card with walking route summary and "See the Way" (AR) CTA.
- [ ] Arrival celebration sheet with place summary and "Explore Destination" actions.

### **Phase 7: Developer Mode & Simulation Controls**
- [ ] Collapsible Developer Mode banner with playback controls.
- [ ] Route progress slider and speed multiplier.
- [ ] Drift injection button to test "Off Route" detection and recalculation.

### **Phase 8: Verification & Compilation**
- [ ] Compile verification via `compile_applet`.
- [ ] Unit & Robolectric test coverage for `NavigationEngine` state transitions and bearing calculations.
- [ ] Edge-to-edge styling, contrast, and accessibility verification.
