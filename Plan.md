# Android AR Navigation — Master Architecture & Delivery Plan

_Last verified against official docs/repos: 2026-09-11. Re-verify version numbers and free-tier terms before pinning a build — pricing pages and OSS project status change without notice._

## 1. Product Intent

The app has exactly one job: turn a destination into an embodied walk, expressed through two synchronized views of the same navigation session.

**MAP MODE** — map, current position, destination, route, next maneuver, distance, ETA, recalculation.
**AR MODE** — live camera, world-space directional arrow, route ribbon, floating maneuver card, destination beacon, AR confidence, recenter, graceful fallback.

Both modes render the same `NavigationSessionState`. Neither invents its own truth. At every moment the user should be able to answer: where am I, where am I going, what's next, where is it physically, is AR reliable right now, what happens if it isn't, have I arrived.

## 2. Non-Negotiable Architecture Principle

Banned: `GPS → compass heading → rotate a 2D screen icon`. That is a fallback tier, not the product.

Required: `route/maneuver coordinate → ARCore Geospatial pose (Earth/GeospatialPose) → geospatial anchor → camera-relative world transform → 3D arrow`. The arrow's position comes from an anchor in the ARCore world, not from `bearing − heading` projected onto the screen. **This principle is unaffected by the map/search/routing stack decision below** — ARCore Geospatial is the AR substrate regardless of who provides the map tiles or the route.

## 3. Stack Decision — Provider-Neutral Architecture

Google Maps Platform (Maps SDK, Places SDK, Routes API) is pay-as-you-go. This project replaces it with a free/open-source stack for map rendering, search, and routing, while **keeping ARCore Geospatial** — confirmed free and billing-independent from Google Maps Platform (a Cloud project is only a quota formality for ARCore; there is no per-request charge). The AR layer in §8–§9 does not change.

The one architectural rule that makes this swap safe: nothing in the UI or `NavigationEngine` may depend on a specific map/search/routing vendor's types. Three interfaces sit between them:

```
                     NavigationSessionState
                             │
                  ┌──────────┴──────────┐
                  │                     │
               MAP MODE               AR MODE
                  │                     │
            MapProvider              ARCore Geospatial
         (MapLibre Native)          (unchanged, §8–§9)
                  │                     │
                  └──────────┬──────────┘
                             │
                      same navigation truth

  PlaceSearchProvider ──▶ Destination ──▶ RoutingProvider ──▶ RouteInfo ──▶ NavigationEngine
     (Geoapify)                              (Valhalla)
```

```kotlin
interface MapProvider          // renders map, camera, markers, polyline, location puck
interface PlaceSearchProvider {
    suspend fun autocomplete(query: String): List<PlaceSuggestion>
    suspend fun resolve(suggestion: PlaceSuggestion): Destination   // real lat/lng, not a stub
}
interface RoutingProvider {
    suspend fun calculateRoute(origin: Coordinate, destination: Coordinate): RouteInfo
}
```

`NavigationEngine` stays exactly as it is today — it consumes `RouteInfo`/`Destination` domain models, never a vendor SDK type, so swapping the provider underneath never touches it. This was already the right instinct in the existing domain layer (§6); the provider interfaces just make the boundary explicit and swappable (e.g. a `ValhallaRoutingProvider` today, an `OsrmRoutingProvider` or self-hosted variant later, without touching `NavigationEngine` or any Composable).

**Corrections to verify against the originally proposed stack** (research findings, 2026-09-11):

- **Valhalla has no endpoint the Valhalla project itself hosts for free.** The closest real option is the community-run **FOSSGIS public instance** (`https://valhalla.openstreetmap.de`) — no API key, full planet graph, but fair-use limited (~1 req/sec/user, 100 req/sec total) and asks clients to send an `X-Client-Id` header identifying the app. Treat it as a Phase 1 bootstrap, not a permanent production dependency — plan to self-host (Docker) once usage is non-trivial.
- **OSRM's public demo (`router.project-osrm.org`) is explicitly non-commercial/demo-only** per its own usage policy — 1 req/sec, no uptime guarantee, "access may be withdrawn at any time." It's fine for local development against real OSM data, but must not be treated as a fallback *production* routing provider. If OSRM is wanted long-term, self-host it.
- **MapLibre Navigation SDK for Android is not deprecated, but it is immature** — an active fork mid-rewrite to Kotlin Multiplatform, still shipping pre-release versions (e.g. `5.0.0-pre14`). Do not take a dependency on it. This app's own `NavigationEngine` already owns maneuver/progress/off-route logic and should keep doing so — exactly per the user's own instinct not to outsource this layer.
- **MapLibre Native Android is confirmed solid**: `org.maplibre.gl:android-sdk:13.4.1` on Maven Central, actively maintained, no API key required by the SDK itself (only the tile source needs one, if any).
- **Geoapify confirmed**: free plan is 3,000 credits/day, no credit card required, 5 req/sec, commercial use allowed. Different endpoints consume credits at different rates (map tiles are metered per-tile) — see §19.
- **Nominatim's public instance is fair-use only** (1 req/sec, no bulk/production use per OSM Foundation policy) — not viable as a production search backend; Geoapify is the right Phase 1 `PlaceSearchProvider`, with self-hosted Nominatim as a later option only.

## 4. Current Implementation Status — Honest Audit

This project already has real Kotlin source (not a blank scaffold). Status reflects what the code actually does today.

| Area | File(s) | Status | Notes |
|---|---|---|---|
| Central navigation state machine | `core/navigation/NavigationEngine.kt`, `domain/model/NavigationModels.kt` | **IMPLEMENTED FOR REAL** | Single `NavigationSessionState` StateFlow drives both Map and AR UI. `NavigationState.ERROR` exists but nothing ever transitions into it. Nothing here needs to change for the stack swap — this is exactly the layer that stays vendor-agnostic. |
| Geo math | `core/navigation/GeoUtils.kt` | **IMPLEMENTED FOR REAL** | Haversine distance/bearing, point-to-polyline distance — correct, vendor-agnostic. |
| Location layer | `core/location/LocationProvider.kt`, `MockLocationEngine.kt` | **IMPLEMENTED FOR REAL** | Clean `LocationEngine` interface; unaffected by the stack swap (Android GNSS stays Android GNSS). |
| Device orientation | `core/sensors/DeviceOrientationSensor.kt` | **IMPLEMENTED FOR REAL** | Tier-2 fallback sensor source — unaffected. |
| ARCore session | `core/ar/ARSessionManager.kt` | **PARTIALLY IMPLEMENTED — dead code** | Correct real API surface but never actually started (`createSession()`/`resume()`/`updateFrame(Frame)` unused). Unaffected by the stack swap — see §8/§9. |
| AR guidance math | `core/ar/ARNavigationEngine.kt`, `ui/components/ARVisualElements.kt` | **SIMULATION-GRADE, NOT WORLD-SPACE** | Screen-space `bearing − heading` math, not anchor-based. Unaffected by the stack swap — see §8/§9. |
| AR camera surface | `ui/ar/ARCameraScreen.kt` | **WRONG OWNER FOR AR** | CameraX conflicts with ARCore owning the camera. Unaffected by the stack swap — see §8. |
| Map rendering | (currently Google Maps Compose, not yet in this repo's file list as such) | **TO BE REPLACED** | Any Google Maps Compose usage becomes a `MapLibreMapProvider` implementing `MapProvider` (§10). |
| Places search | `data/places/GooglePlacesDataSource.kt` | **TO BE REPLACED** | Currently: real `PlacesClient` autocomplete text, but a hardcoded lat/lng stub for every result, and orphaned from the search flow. Replace entirely with a `GeoapifyPlaceSearchProvider` (§11) rather than fixing the Google implementation — no reason to keep a second, paid search backend around. |
| Routing | `data/repository/NavigationRepositoryImpl.kt` | **SIMULATION ONLY → TO BE REPLACED** | 5 hardcoded destinations, synthetic 4-waypoint route interpolator, no real routing HTTP call. Replace with a `ValhallaRoutingProvider` (§12); keep the synthetic generator scoped explicitly to Developer Mode. |
| Persistence | `data/local/AppDatabase.kt`, DAO, entity | **IMPLEMENTED FOR REAL** | Room, recent destinations — unaffected. |
| Permissions | `core/permissions/PermissionHandler.kt` | **PARTIALLY IMPLEMENTED** | No rationale dialog yet (§14) — unaffected by the stack swap. |
| Map UI / dev mode / dialogs | `ui/home/HomeScreen.kt`, `ui/navigation/MapNavigationScreen.kt`, `NavigationViewModel.kt`, `ui/components/DeveloperModePanel.kt`, `NavigationDialogs.kt` | **IMPLEMENTED FOR REAL** | Correctly wired to the shared state machine — only the map widget inside `HomeScreen`/`MapNavigationScreen` needs to swap from Google Maps Compose to MapLibre; the surrounding logic is untouched. |
| AR entry gate | — | **NOT IMPLEMENTED** | Unaffected by the stack swap — see §13. |

**The one conclusion that matters:** the stack swap (Google Maps/Places/Routes → MapLibre/Geoapify/Valhalla) is entirely a `data/` layer change behind the three provider interfaces in §3. The AR gap (dead ARCore session, screen-space arrow math, CameraX/ARCore conflict) is a completely separate, unaffected problem and remains the highest-value engineering work regardless of which map/routing vendor is underneath.

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
│   ├── map/             MapLibreMapProvider : MapProvider              (replaces Google Maps Compose)
│   ├── search/           GeoapifyPlaceSearchProvider : PlaceSearchProvider (replaces GooglePlacesDataSource)
│   └── repository/      ValhallaRoutingProvider : RoutingProvider, NavigationRepositoryImpl (replaces Google Routes usage)
├── domain/
│   ├── model/           ARModels, NavigationModels
│   └── provider/        MapProvider, PlaceSearchProvider, RoutingProvider   (new — the swap boundary from §3)
├── ui/
│   ├── ar/               ARCameraScreen (needs ARCore-owned camera, not CameraX)
│   ├── components/      ARVisualElements (needs world-space input), DeveloperModePanel, NavigationDialogs
│   ├── home/             HomeScreen, HomeViewModel
│   ├── navigation/       MapNavigationScreen, NavigationViewModel
│   └── theme/            Color, Theme, Type
└── MainActivity.kt
```

## 6. Central Navigation State Machine

Already correctly implemented — keep this design, just close the gap on `ERROR`:

```
IDLE → PREPARING → ROUTE_READY → NAVIGATING → APPROACHING_MANEUVER → MANEUVER_NOW
                                        ↓                                    │
                                   OFF_ROUTE → RECALCULATING → ROUTE_READY ◄──┘
                                                                              │
                                                                          ARRIVED
```

`ERROR` needs real triggers wired: `RoutingProvider` HTTP failure, `PlaceSearchProvider` resolve failure, no network, and ARCore unsupported at the AR entry gate should all set `NavigationState.ERROR` with a reason string.

## 7. Critical Fix #1 — AR Camera Ownership

CameraX and an active ARCore `Session` cannot both own the camera device. `ARCameraScreen.kt` must stop using `CameraX Preview` for the AR screen. ARCore renders its own camera passthrough texture; the standard pattern is a `GLSurfaceView` (or Compose `AndroidView` wrapping one) that:
1. Binds the GL context to the ARCore session's camera texture (`session.setCameraTextureName(textureId)`).
2. Calls `session.update()` once per rendered frame to get the latest `Frame`/`Camera` pose, texture, and (once enabled) `Earth`/geospatial pose.
3. Draws the camera background texture, then draws AR content (arrow/ribbon/beacon) transformed by the frame's view/projection matrices.

CameraX has no remaining purpose once this is done — there is no other camera consumer in the app.

## 8. Critical Fix #2 — Wire the World-Space AR Pipeline

`ARSessionManager` already has the right calls; they just need a lifecycle and a frame loop:

1. **Lifecycle**: create the session on entering AR mode (after the entry gate in §13 passes), `resume()` on `ON_RESUME`, `pause()` on `ON_PAUSE`, `close()` on leaving AR mode — bind this to the Compose screen's lifecycle.
2. **Frame loop**: each `GLSurfaceView.Renderer.onDrawFrame`, call `session.update()` → `Frame` → `frame.camera` → `earth.getCameraGeospatialPose()` when `earth.getTrackingState() == TRACKING`.
3. **Anchors instead of angles**: for the current maneuver location and the destination, call `earth.createAnchor(lat, lng, altitude, quaternion)` once per target and cache the `Anchor`. Re-create only when the maneuver changes.
4. **Render transform**: each frame, take `anchor.pose` and `camera.pose`, compute the anchor's position in camera space, and use that to place/scale the 3D arrow — replacing `targetBearing − deviceHeading` entirely for Tier-1 (HIGH/MEDIUM confidence). The existing screen-space bearing math becomes the Tier-2 fallback renderer only.

## 9. Map Rendering — `MapProvider` (MapLibre Native)

- Dependency: `org.maplibre.gl:android-sdk:13.4.1` (Maven Central, actively maintained, no API key needed for the SDK itself).
- Tile source for Phase 1: Geoapify's OSM-based vector/raster tile endpoint (metered per-tile against the same Geoapify credit pool as search — see §19).
- `MapLibreMapProvider` implements `MapProvider`: current-location puck, destination marker, route polyline, camera control, recenter/zoom — same responsibilities the old Google Maps Compose widget had, same place in `HomeScreen`/`MapNavigationScreen`.
- Do not use Google's raster satellite tile endpoints (`mt1.google.com/vt/...`) — undocumented, still part of Google Maps Platform's access terms, and defeats the point of leaving it.

## 10. Search — `PlaceSearchProvider` (Geoapify)

- `GeoapifyPlaceSearchProvider` implements `autocomplete()` (Geoapify Geocoding Autocomplete) and `resolve()` (Geoapify Place Details / forward geocode) — `resolve()` must return the **real** coordinate, closing the exact bug the current `GooglePlacesDataSource` has (hardcoded lat/lng stub).
- One debounced autocomplete call per pause in typing, never per keystroke (§19).
- Self-hosted Nominatim is the only viable long-term free alternative if Geoapify's quota becomes limiting — its public instance is fair-use-only and not an option for real traffic.

## 11. Routing — `RoutingProvider` (Valhalla primary)

`ValhallaRoutingProvider` calls the FOSSGIS public Valhalla instance (`https://valhalla.openstreetmap.de`) with a pedestrian costing profile, parses route geometry + maneuvers into `RouteInfo`/`Maneuver`, and sends an `X-Client-Id` header identifying the app per that instance's fair-use ask. Call it once per `PREPARING`/`RECALCULATING` transition, never on a GPS tick — same discipline the old Routes API plan already specified.

`OsrmRoutingProvider` may exist as a second implementation for local development against self-hosted OSRM, but the public OSRM demo server must not be wired into any path a real user hits — it is explicitly demo-only per its own usage policy.

Both are swappable behind `RoutingProvider`; `NavigationEngine` never sees either type directly.

## 12. AR Entry Gate

`enterArMode()` does not currently exist as a gate — it should, in this order, before switching to the AR screen:

1. Camera permission granted.
2. Precise (`ACCESS_FINE_LOCATION`) permission granted.
3. Location services enabled on device.
4. `ArCoreApk.checkAvailability(context)` → supported/installed.
5. Geospatial mode supported on this session/device.
6. `session.checkVpsAvailabilityAsync(lat, lng)` result available (may be `UNAVAILABLE` — proceed, just at lower confidence).
7. Create session → enable `GeospatialMode.ENABLED` → `resume()`.

Any failure short-circuits straight to the map-navigation fallback with the matching error copy from §16 — never a blank or crashed AR screen.

## 13. Permission UX

Build the missing `PermissionRationaleDialog` — shown before the system prompt, not after a denial:

- **Precise location**: "AR navigation uses your precise position to place navigation guidance in the world around you."
- **Camera**: "Your camera is used to show navigation guidance over your surroundings."

## 14. AR Confidence Matrix

| Confidence | Criteria | Presentation |
|---|---|---|
| HIGH | `Earth.getTrackingState() == TRACKING`, VPS available, horizontal accuracy < 5m, heading accuracy < 5° | World-space arrow + ribbon, "AR Navigation" |
| MEDIUM | GPS accuracy < 12m, heading stabilized, VPS still resolving | "AR positioning stabilizing" |
| LOW | GPS accuracy > 15m or high magnetic interference | Tier-2 sensor-fused overlay, "Use map for best accuracy" |
| UNAVAILABLE | ARCore unsupported, camera denied, or entry gate failed | Automatic fallback to Tier-3 map navigation |

## 15. Fallback Hierarchy

1. **Tier 1 — True Geospatial AR**: anchor-based world-space rendering (§8).
2. **Tier 2 — Sensor-fused overlay**: today's screen-space bearing math, kept intentionally as the degraded renderer.
3. **Tier 3 — Map navigation**: `MapNavigationScreen` (now on MapLibre), always available, never blocked by AR failure.

## 16. Error States

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

## 17. Developer Mode & Simulation

Already correctly implemented against the real state machine — no changes needed. `MockLocationEngine` + `DeveloperModePanel` drive the same `NavigationEngine` a real GPS feed would, including off-route drift injection. Curated demo routes stay hardcoded here on purpose — they never call `RoutingProvider`.

## 18. Accessibility

Textual turn instructions and distance must always be present alongside AR visuals (never AR-only); sufficient contrast on floating cards; semantic labels on touch targets; Tier-3 map fallback is itself the accessibility fallback for anyone who can't/doesn't want AR.

## 19. Billing & Quota Safety (Free Stack)

No Google Maps Platform billing account is required anywhere in this stack. Real constraints instead:

- **Geoapify**: 3,000 credits/day free, no credit card, 5 req/sec. Map tiles are metered per-tile — aggressive pan/zoom burns quota fast. Debounce search input; never fetch tiles or autocomplete on every frame/keystroke.
- **Valhalla (FOSSGIS public instance)**: fair-use only, ~1 req/sec/user, 100 req/sec total, send `X-Client-Id`. Call once per route calculation, never per GPS tick; cache the result; only recalculate on `OFF_ROUTE`.
- **OSRM public demo**: not usable in any user-facing path — 1 req/sec, demo-only, "may be withdrawn at any time." Local dev only, against a self-hosted instance if used at all in CI/testing.
- **Nominatim public instance**: 1 req/sec, no bulk/production use — do not point real user traffic at it.
- **ARCore Geospatial/VPS**: confirmed free; a Google Cloud project is a quota-gate formality, not a billing trigger under normal prototype usage.

## 20. Staged Migration Roadmap

- [x] Dependencies declared, Manifest permissions, single-activity Compose shell, Room persistence, dev-mode simulation, `NavigationEngine`/`GeoUtils`/`MockLocationEngine`.
- **Phase 1 — Swap the provider layer (this is new work from this session):**
  - [ ] Define `MapProvider`/`PlaceSearchProvider`/`RoutingProvider` interfaces in `domain/provider/`.
  - [ ] Add `MapLibreMapProvider` (MapLibre + Geoapify tiles), replace Google Maps Compose usage in `HomeScreen`/`MapNavigationScreen`.
  - [ ] Add `GeoapifyPlaceSearchProvider` with real `resolve()` coordinates, replace `GooglePlacesDataSource`.
  - [ ] Add `ValhallaRoutingProvider` (FOSSGIS instance + `X-Client-Id`), replace synthetic routing in the production path of `NavigationRepositoryImpl`; keep synthetic routes scoped to Developer Mode only.
  - [ ] Remove Google Maps/Places/Routes dependencies once the above are wired and tested.
- **Phase 2 — AR fixes (unaffected by Phase 1, can run in parallel):**
  - [ ] Fix camera ownership — replace CameraX in `ARCameraScreen` with an ARCore-owned GL surface (§7).
  - [ ] Wire ARCore session lifecycle + frame loop (§8, steps 1–2).
  - [ ] Rewrite `ARNavigationEngine` guidance math to anchor-based world-space (§8, steps 3–4); demote bearing math to the Tier-2 renderer.
  - [ ] Add the AR entry gate (§12) and `PermissionRationaleDialog` (§13).
  - [ ] Trigger `NavigationState.ERROR` from real failures (network, ARCore unsupported, route/search failure).
- **Phase 3 — Reduce dependency on hosted free tiers:**
  - [ ] Self-host Valhalla (Docker) once usage exceeds FOSSGIS fair-use comfort margin.
- **Phase 4 — Full self-hosted geographic stack** (only if this becomes a real product, not a prototype):
  - [ ] Self-hosted tile server + Nominatim + Valhalla, all fed from an OSM extract. ARCore remains the only external geospatial dependency.
- [ ] Physical-device test pass (bright/low-light outdoor, urban canyon, VPS unavailable, permission denial, ARCore unsupported device).

## 21. Acceptance Criteria (current status)

- [x] Builds and launches without crashing.
- [x] Map navigation works end-to-end against the real state machine (map widget itself still needs the MapLibre swap).
- [x] Camera permission flow works (no rationale screen yet).
- [ ] Map renders via MapLibre + Geoapify tiles, not Google Maps Compose.
- [ ] Search returns real, selectable coordinates via Geoapify.
- [ ] Route comes from Valhalla, not a synthetic generator, on the production path.
- [ ] ARCore session actually initializes and produces `GeospatialPose` in a live app run.
- [ ] VPS availability reflected in real AR confidence.
- [ ] At least one AR guidance object is rendered from a geospatial anchor, not screen-space bearing math.
- [ ] AR entry gate blocks entry on real capability checks.
- [ ] Off-route recalculation calls a real routing provider.
- [ ] No unrestricted API key committed (`.env` git-ignored — confirmed); no Google Maps Platform key required at all.
- [ ] Verified on a real ARCore-capable device.

## 22. Known Limitations

- FOSSGIS's public Valhalla instance and Geoapify's free tier are fair-use/rate-limited, not guaranteed-uptime production infrastructure — fine for a prototype, plan to self-host before real user traffic.
- MapLibre Navigation SDK for Android is deliberately not used (pre-release, immature) — all turn-by-turn logic stays in this project's own `NavigationEngine`.
- ARCore Geospatial accuracy depends on VPS coverage and outdoor conditions the emulator cannot reproduce — physical-device testing is mandatory before claiming Tier-1 works.
- Exact Geoapify per-endpoint credit costs beyond the general free-tier terms were not independently re-derived from the raw pricing table — re-check before relying on a specific budget calculation.
