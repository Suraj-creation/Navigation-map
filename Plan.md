# Android AR Navigation — Master Architecture & Delivery Plan

_Last verified against official Google/ARCore documentation: 2026-09-11. Google Maps Platform and ARCore APIs change frequently — re-verify version numbers against `developers.google.com` and the ARCore GitHub releases page before pinning a build._

## 1. Product Intent

The app has exactly one job: turn a destination into an embodied walk, expressed through two synchronized views of the same navigation session.

**MAP MODE** — Google Map, current position, destination, route, next maneuver, distance, ETA, recalculation.
**AR MODE** — live camera, world-space directional arrow, route ribbon, floating maneuver card, destination beacon, AR confidence, recenter, graceful fallback.

Both modes render the same `NavigationSessionState`. Neither invents its own truth. At every moment the user should be able to answer: where am I, where am I going, what's next, where is it physically, is AR reliable right now, what happens if it isn't, have I arrived.

## 2. Non-Negotiable Architecture Principle

Banned: `GPS → compass heading → rotate a 2D screen icon`. That is a fallback tier, not the product.

Required: `Google route/maneuver coordinate → ARCore Geospatial pose (Earth/GeospatialPose) → geospatial anchor → camera-relative world transform → 3D arrow`. The arrow's position comes from an anchor in the ARCore world, not from `bearing − heading` projected onto the screen.

## 3. Current Implementation Status — Honest Audit

This project already has real Kotlin source (not a blank scaffold). Status below reflects what the code actually does today, audited file-by-file, not what earlier checklists assumed.

| Area | File(s) | Status | Notes |
|---|---|---|---|
| Central navigation state machine | `core/navigation/NavigationEngine.kt`, `domain/model/NavigationModels.kt` | **IMPLEMENTED FOR REAL** | Single `NavigationSessionState` StateFlow drives both Map and AR UI — the architectural mandate in §12 is correctly satisfied. `NavigationState.ERROR` exists but nothing ever transitions into it. |
| Geo math | `core/navigation/GeoUtils.kt` | **IMPLEMENTED FOR REAL** | Haversine distance/bearing, point-to-polyline distance — correct. |
| Location layer | `core/location/LocationProvider.kt`, `MockLocationEngine.kt` | **IMPLEMENTED FOR REAL** | Clean `LocationEngine` interface; ViewModel switches source via a developer-mode flag. Simulation does not leak into the production path. |
| Device orientation | `core/sensors/DeviceOrientationSensor.kt` | **IMPLEMENTED FOR REAL** | Rotation-vector with accelerometer/magnetometer fallback — valid as the Tier-2 fallback sensor source. |
| ARCore session | `core/ar/ARSessionManager.kt` | **PARTIALLY IMPLEMENTED — dead code** | Uses the correct real API surface (`Session`, `Config.GeospatialMode.ENABLED`, `Earth`, `checkVpsAvailabilityAsync`, `ArCoreApk.checkAvailability`), but `createSession()` / `resume()` / `updateFrame(Frame)` are never invoked from anywhere in the app. No per-frame update loop exists, so in real usage Earth tracking state never leaves its default and geospatial pose is never actually produced. |
| AR guidance math | `core/ar/ARNavigationEngine.kt`, `ui/components/ARVisualElements.kt` | **SIMULATION-GRADE, NOT WORLD-SPACE** | Computes `targetBearing − deviceHeading`, projects through FOV into screen X/Y, draws a 2D canvas chevron. `geospatialPose` is only consulted to pick a heading source — it is never used to place a world anchor. This is precisely the architecture §2/§21/§37 forbid as the final design. |
| AR camera surface | `ui/ar/ARCameraScreen.kt` | **WRONG OWNER FOR AR** | Binds CameraX (`Preview` + `DEFAULT_BACK_CAMERA`) to a `PreviewView`. ARCore must own the camera device directly (it renders its own passthrough texture); CameraX and an active ARCore `Session` cannot both own the camera. No conflict is visible today only because the ARCore session is never resumed. |
| Places search | `data/places/GooglePlacesDataSource.kt` | **PARTIALLY IMPLEMENTED** | Real `PlacesClient` + `findAutocompletePredictions` for text, but every returned prediction is given one hardcoded lat/lng (26.9688, 94.2205) instead of a real `FetchPlaceRequest` lookup. Also currently orphaned — the search flow doesn't call it. |
| Routing | `data/repository/NavigationRepositoryImpl.kt` | **SIMULATION ONLY** | 5 hardcoded `curatedDestinations`; routes are produced by a synthetic 4-waypoint interpolator (`buildRealisticWalkingRoute()`). No HTTP call to Routes API despite Retrofit/OkHttp/Moshi already being dependencies. |
| Persistence | `data/local/AppDatabase.kt`, DAO, entity | **IMPLEMENTED FOR REAL** | Room, recent destinations — simple and correct. |
| Permissions | `core/permissions/PermissionHandler.kt` | **PARTIALLY IMPLEMENTED** | Boolean checks only. `PermissionRationaleDialog` does not exist as a file; the system permission dialog fires immediately with no "why" screen (violates §15). |
| Map UI / dev mode / dialogs | `ui/home/HomeScreen.kt`, `ui/navigation/MapNavigationScreen.kt`, `NavigationViewModel.kt`, `ui/components/DeveloperModePanel.kt`, `NavigationDialogs.kt` | **IMPLEMENTED FOR REAL** | Correctly wired to the shared state machine; dev-mode playback (play/pause/speed/off-route injection) works against the real engine, not a separate fake UI. |
| AR entry gate | — | **NOT IMPLEMENTED** | Nothing checks ARCore/geospatial/VPS support before switching to the AR screen (the 14-step sequence in §11 below). |

**The one architectural conclusion that matters:** the app has a correct central state machine and a correct fallback-tier *shape*, but AR mode currently runs entirely on Tier-2 (sensor-fused screen overlay) while presenting itself as Tier-1. The highest-value engineering work is wiring the already-mostly-correct `ARSessionManager` into an actual frame loop and moving `ARNavigationEngine` from screen-space to anchor-based world-space — not adding new features.

## 4. Verified SDK Reference

| Component | Installed in this repo (`gradle/libs.versions.toml`) | Current per official docs (2026-09-11) | Note |
|---|---|---|---|
| Maps SDK for Android | `com.google.android.gms:play-services-maps:19.1.0` | `20.0.0` | Minor bump available. |
| Maps Compose | `com.google.maps.android:maps-compose:6.5.3` | `8.3.0` | Current release bundles `maps-compose-utils`/`widgets` and drops the need to separately declare `play-services-maps`. Re-test map rendering if bumped. |
| Places SDK for Android | `com.google.android.libraries.places:places:3.5.0` | ~`4.2.0`–`5.1.1` (exact latest unconfirmed — verify at `mvnrepository.com/artifact/com.google.android.libraries.places/places` before pinning) | Standardize on "(New)" methods: `FetchPlaceRequest` / `PlacesClient.fetchPlace()`, `fetchResolvedPhotoUri()` (legacy `fetchPhoto()` is deprecated). |
| ARCore | `com.google.ar:core:1.48.0` | `1.56.0` | Latest bumps `targetSdkVersion` to API 37 — treat as a breaking change requiring its own test phase, not a drive-by bump alongside other work. |
| Play Services Location | `com.google.android.gms:play-services-location:21.3.0` | current | No action needed. |
| **Navigation SDK for Android** | not present | `com.google.android.libraries.navigation` — **gated behind Mobility Services; requires a Google Sales enrollment, not a public Maven dependency** | **Not available to this prototype.** Do not architect around it. Build turn-by-turn logic on top of Routes API polylines inside our own `NavigationEngine` instead — the domain model in §6 already stays SDK-agnostic for exactly this reason. |
| Routes API | not called yet (used only as design intent) | REST endpoint `routes.googleapis.com`, current supported replacement for the legacy Directions API (migration completed) | Billed per request. Called from the app over HTTPS via the existing Retrofit/Moshi stack — not an Android SDK. |
| Geospatial API surface | — | `Session.Config.GeospatialMode.ENABLED`, `Earth.getCameraGeospatialPose()` → `GeospatialPose` (lat/lng/altitude/heading), `Earth.getTrackingState()`, `Session.checkVpsAvailabilityAsync()` → `VpsAvailabilityFuture`, `Earth.createAnchor(...)` (+ `resolveAnchorOnTerrainAsync`/`resolveAnchorOnRooftopAsync`), `ArCoreApk.checkAvailability()` / `checkAvailabilityAsync()` | Confirmed current class/method names — matches what `ARSessionManager.kt` already calls. The gap is wiring, not API correctness. |

## 5. System Architecture

```
app/src/main/java/com/example/
├── core/
│   ├── ar/            ARSessionManager (needs lifecycle wiring), ARNavigationEngine (needs world-space rewrite), ARConfidence
│   ├── location/       LocationProvider, MockLocationEngine
│   ├── navigation/     NavigationEngine, NavigationState, GeoUtils
│   ├── permissions/     PermissionHandler (+ missing PermissionRationaleDialog)
│   └── sensors/        DeviceOrientationSensor
├── data/
│   ├── local/           AppDatabase, RecentDestinationDao/Entity
│   ├── places/          GooglePlacesDataSource (needs real fetchPlace coordinates + wiring into search)
│   └── repository/      NavigationRepository, NavigationRepositoryImpl (needs real Routes API call)
├── domain/
│   └── model/           ARModels, NavigationModels
├── ui/
│   ├── ar/               ARCameraScreen (needs ARCore-owned camera, not CameraX)
│   ├── components/      ARVisualElements (needs world-space input), DeveloperModePanel, NavigationDialogs
│   ├── home/             HomeScreen, HomeViewModel
│   ├── navigation/       MapNavigationScreen, NavigationViewModel
│   └── theme/            Color, Theme, Type
└── MainActivity.kt
```

Domain models (`Destination`, `RouteInfo`, `Maneuver`, `ARGuidance`, `ARConfidence`, `NavigationState`) stay SDK-agnostic on purpose — this is what lets Navigation SDK stay absent without touching the UI layer, and is what will absorb a Routes API-backed repository without other layers noticing.

## 6. Central Navigation State Machine

Already correctly implemented — keep this design, just close the gap on `ERROR`:

```
IDLE → PREPARING → ROUTE_READY → NAVIGATING → APPROACHING_MANEUVER → MANEUVER_NOW
                                        ↓                                    │
                                   OFF_ROUTE → RECALCULATING → ROUTE_READY ◄──┘
                                                                              │
                                                                          ARRIVED
```

`ERROR` needs real triggers wired: Routes API HTTP failure, Places fetch failure, no network, and ARCore unsupported at the AR entry gate should all set `NavigationState.ERROR` with a reason string, rather than leaving the union type dead.

## 7. Critical Fix #1 — AR Camera Ownership

CameraX and an active ARCore `Session` cannot both own the camera device. `ARCameraScreen.kt` must stop using `CameraX Preview` for the AR screen. ARCore renders its own camera passthrough texture; the standard pattern is a `GLSurfaceView` (or Compose `AndroidView` wrapping one) that:
1. Binds the GL context to the ARCore session's camera texture (`session.setCameraTextureName(textureId)`).
2. Calls `session.update()` once per rendered frame to get the latest `Frame`/`Camera` pose, texture, and (once enabled) `Earth`/geospatial pose.
3. Draws the camera background texture, then draws AR content (arrow/ribbon/beacon) transformed by the frame's view/projection matrices.

CameraX has no remaining purpose once this is done — there is no other camera consumer in the app.

## 8. Critical Fix #2 — Wire the World-Space AR Pipeline

`ARSessionManager` already has the right calls; they just need a lifecycle and a frame loop:

1. **Lifecycle**: create the session on entering AR mode (after the entry gate in §11 passes), `resume()` on `ON_RESUME`, `pause()` on `ON_PAUSE`, `close()` on leaving AR mode — bind this to the Compose screen's lifecycle, not `Activity` `onCreate`/`onDestroy`.
2. **Frame loop**: each `GLSurfaceView.Renderer.onDrawFrame` (or `Choreographer` callback), call `session.update()` → get `Frame` → `frame.camera` → `earth.getCameraGeospatialPose()` when `earth.getTrackingState() == TRACKING`.
3. **Anchors instead of angles**: for the current maneuver location and the destination, call `earth.createAnchor(lat, lng, altitude, quaternion)` once per target (not per frame) and cache the `Anchor`. Re-create only when the maneuver changes.
4. **Render transform**: each frame, take `anchor.pose` and the current `camera.pose`, compute the anchor's position in camera space (`camera.pose.inverse().compose(anchor.pose)`), and use that to place/scale the 3D arrow — this replaces `targetBearing − deviceHeading` entirely for Tier-1 (HIGH/MEDIUM confidence). The existing screen-space bearing math in `ARNavigationEngine`/`ARVisualElements` becomes the Tier-2 fallback renderer only, used when Earth tracking is not `TRACKING` or confidence is `LOW`.

## 9. Routing Strategy — Routes API, Not Navigation SDK

Navigation SDK access is not realistic for this project (§4). The routing path is:

`Destination selected → Routes API computeRoutes (single HTTP call via existing Retrofit/Moshi client) → cached RouteInfo (polyline + maneuvers) → NavigationEngine tracks progress locally against that cached route using GeoUtils`

Only call Routes API again on `OFF_ROUTE → RECALCULATING`, never on a GPS tick. `NavigationRepositoryImpl.buildRealisticWalkingRoute()` should be renamed/scoped explicitly to Developer Mode's curated demo routes; the production path needs an actual `RoutesApiClient` (Retrofit interface + Moshi models for the `routes.googleapis.com:computeRoutes` request/response) added to `data/repository`.

## 10. Places — Real Coordinates

Wire `GooglePlacesDataSource` into the actual search flow, and after a user picks an autocomplete prediction, call `PlacesClient.fetchPlace()` with a `FetchPlaceRequest` for `Place.Field.LAT_LNG`/`NAME`/`ADDRESS` instead of returning the hardcoded coordinate. Keep one `AutocompleteSessionToken` per search session for correct Places billing.

## 11. AR Entry Gate

`enterArMode()` does not currently exist as a gate — it should, in this order, before switching to the AR screen:

1. Camera permission granted.
2. Precise (`ACCESS_FINE_LOCATION`) permission granted.
3. Location services enabled on device.
4. `ArCoreApk.checkAvailability(context)` → supported/installed.
5. Geospatial mode supported on this session/device.
6. `session.checkVpsAvailabilityAsync(lat, lng)` result available (may be `UNAVAILABLE` — proceed, just at lower confidence).
7. Create session → enable `GeospatialMode.ENABLED` → `resume()`.

Any failure short-circuits straight to the map-navigation fallback with the matching error copy from §15 — never a blank or crashed AR screen.

## 12. Permission UX

Build the missing `PermissionRationaleDialog` — shown before the system prompt, not after a denial:

- **Precise location**: "AR navigation uses your precise position to place navigation guidance in the world around you."
- **Camera**: "Your camera is used to show navigation guidance over your surroundings."

## 13. AR Confidence Matrix

| Confidence | Criteria | Presentation |
|---|---|---|
| HIGH | `Earth.getTrackingState() == TRACKING`, VPS available, horizontal accuracy < 5m, heading accuracy < 5° | World-space arrow + ribbon, "AR Navigation" |
| MEDIUM | GPS accuracy < 12m, heading stabilized, VPS still resolving | "AR positioning stabilizing" |
| LOW | GPS accuracy > 15m or high magnetic interference | Tier-2 sensor-fused overlay, "Use map for best accuracy" |
| UNAVAILABLE | ARCore unsupported, camera denied, or entry gate failed | Automatic fallback to Tier-3 map navigation |

## 14. Fallback Hierarchy

1. **Tier 1 — True Geospatial AR**: anchor-based world-space rendering (§8).
2. **Tier 2 — Sensor-fused overlay**: today's screen-space bearing math, kept intentionally as the degraded renderer.
3. **Tier 3 — Map navigation**: `MapNavigationScreen`, always available, never blocked by AR failure.

## 15. Error States

| State | Copy | Fallback |
|---|---|---|
| `NO_LOCATION` | "Location unavailable." | Retry, else Tier 3 |
| `NO_CAMERA` | "Camera access is required for AR navigation." | Tier 3 |
| `ARCORE_UNAVAILABLE` | "AR navigation isn't supported on this device." | Tier 3 |
| `VPS_UNAVAILABLE` | "Visual positioning isn't currently available." | Tier 2 |
| `GEOPOSE_UNAVAILABLE` | "Unable to establish spatial position." | Tier 2 |
| `ROUTE_FAILED` | "Unable to calculate a walking route." | Retry with backoff |
| `NETWORK_ERROR` | "Check your connection and try again." | Retry |
| `OFF_ROUTE` | "You're off route. Recalculating…" | `RECALCULATING` state |

## 16. Developer Mode & Simulation

Already correctly implemented against the real state machine — no changes needed. `MockLocationEngine` + `DeveloperModePanel` drive the same `NavigationEngine` a real GPS feed would, including off-route drift injection.

## 17. Billing & Quota Safety

- Routes API and Places Autocomplete/Details are billed per call. Cache route + place results; never call on a GPS tick; recalculate only on `OFF_ROUTE`.
- Required GCP APIs to enable: Maps SDK for Android, Places API (New), Routes API.
- Recommend a Cloud Billing budget alert during development; `.env`/`.env.example` already isolate the key from source control via the Secrets Gradle Plugin.

## 18. Accessibility

Textual turn instructions and distance must always be present alongside AR visuals (never AR-only); sufficient contrast on floating cards; semantic labels on touch targets; Tier-3 map fallback is itself the accessibility fallback for anyone who can't/doesn't want AR.

## 19. Implementation Roadmap

- [x] Dependencies declared (`libs.versions.toml`), Manifest permissions, single-activity Compose shell.
- [x] Domain models, `NavigationEngine`, `GeoUtils`, `MockLocationEngine`.
- [x] Room persistence, dev-mode simulation UI, map navigation UI.
- [ ] **Fix camera ownership** — replace CameraX in `ARCameraScreen` with an ARCore-owned GL surface (§7).
- [ ] **Wire ARCore session lifecycle + frame loop** (§8, step 1–2).
- [ ] **Rewrite `ARNavigationEngine` guidance math to anchor-based world-space** (§8, step 3–4); demote current bearing math to the Tier-2 renderer.
- [ ] **Add the AR entry gate** (§11) — gate the AR screen, don't just open it.
- [ ] **Add `PermissionRationaleDialog`** (§12).
- [ ] **Add a `RoutesApiClient`** and wire `NavigationRepositoryImpl` to it for the production path (§9); keep synthetic routes scoped to Developer Mode.
- [ ] **Fix `GooglePlacesDataSource`** to fetch real coordinates and wire it into the search flow (§10).
- [ ] Trigger `NavigationState.ERROR` from real failures (network, ARCore unsupported, route failure).
- [ ] Physical-device test pass (bright/low-light outdoor, urban canyon, VPS unavailable, permission denial, ARCore unsupported device).

## 20. Acceptance Criteria (current status)

- [x] Builds and launches without crashing.
- [x] Google Map loads; current location displays after permission.
- [x] Walking route can be prepared and previewed (synthetic today).
- [x] Map navigation works end-to-end against the real state machine.
- [x] Camera permission flow works (no rationale screen yet).
- [ ] Places search returns real, selectable coordinates.
- [ ] Route comes from Routes API, not a synthetic generator, on the production path.
- [ ] ARCore session actually initializes and produces `GeospatialPose` in a live app run.
- [ ] VPS availability reflected in real AR confidence (API is called; not yet reached at runtime since the session never starts).
- [ ] At least one AR guidance object is rendered from a geospatial anchor, not screen-space bearing math.
- [ ] AR entry gate blocks entry on real capability checks.
- [ ] Off-route recalculation calls a real routing API.
- [ ] No unrestricted API key committed (`.env` git-ignored — confirmed).
- [ ] Verified on a real ARCore-capable device.

## 21. Known Limitations

- Navigation SDK for Android is architecturally out of scope for this prototype (access-gated); all "turn-by-turn" logic is our own, on top of Routes API polylines.
- ARCore Geospatial accuracy depends on VPS coverage and outdoor conditions the emulator cannot reproduce — physical-device testing is mandatory before claiming Tier-1 works.
- Exact latest Places SDK version was not fully confirmed against Maven at authoring time — re-check before bumping from 3.5.0.
