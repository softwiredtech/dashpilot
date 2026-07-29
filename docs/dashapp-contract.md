# Dash-app bridge contract

This document describes the contract between a web dash-app and the DashPilot
native host. A dash-app is an HTML page loaded into a WebView; the host pushes
live vehicle state to it. If you are adding a new app, read
[`adding-a-dash-app.md`](./adding-a-dash-app.md) first for the folder layout and
registration steps — this document covers what your `index.html` actually does
at runtime.

## Two bridge mechanisms

Android and iOS deliver state to the WebView through **different mechanisms**.
A cross-platform app must wire up both.

### Android — `window.NativeCarState` + `window.onCarStateUpdate`

Android injects a synchronous Java object as `window.NativeCarState` via
`WebView.addJavascriptInterface` (`WebDashView.kt:96`). The object exposes 39
getter methods (see the reference table below). On each state update the host
calls:

```javascript
window.onCarStateUpdate && window.onCarStateUpdate()
```

Your app defines `window.onCarStateUpdate`, reads the getters, and renders.

### iOS — `window.receiveMessage(car)`

iOS does **not** inject a `NativeCarState` object. Instead it serializes the
full state to a flat JSON object and calls (`WebDashView.swift:135`):

```javascript
window.receiveMessage && window.receiveMessage(car)
```

Your app defines `window.receiveMessage`, accepts the `car` object, and renders.

### The dual-platform pattern

Apps that work on both platforms follow this structure (see `web-retro` for the
canonical example):

```javascript
// The single render entry point — called by both platforms.
function receiveMessage(car) {
  // ...read car.egoSpeed, car.darkMode, etc., and update the DOM
}

// iOS calls this directly.
window.receiveMessage = receiveMessage;

// Android calls this; it reads from NativeCarState and forwards to receiveMessage.
window.onCarStateUpdate = function () {
  receiveMessage({
    egoSpeed:   NativeCarState.getEgoSpeed(),
    isImperial: NativeCarState.isImperial(),
    darkMode:   NativeCarState.isDarkMode(),
    // ...all the getters your app uses, keyed by the iOS JSON key
  });
};
```

**iOS field names differ from Android getter names.** Android getters are
prefixed (`getEgoSpeed`, `isAdasOn`); iOS JSON keys are not (`egoSpeed`,
`adasOn`) — with one exception: the imperial flag is sent as `isImperial`,
prefix and all. Build the object you pass to `receiveMessage` with the iOS
JSON keys from the reference table, so the handler sees identical shapes on
both platforms.

## Update lifecycle

- **Android** samples car state at **40 ms** intervals (~25 Hz) —
  `DashKitDataSource` / `CommaDataSource` apply `.sample(40)` — and calls
  `onCarStateUpdate` on every emission.
- **iOS** does not sample: every data-source emission is forwarded, but through
  an `AsyncStream` with `bufferingNewest(1)`, so if the WebView falls behind
  only the **latest** state is delivered (stale ticks are dropped natively).
- On both platforms, updates only start after the page finishes loading
  (`onPageFinished` / `didFinish`). Both hosts guard the call
  (`window.onCarStateUpdate && ...`), so any tick that fires before your
  handler is defined is **silently dropped** — define the handler in a script
  that runs during page load, not after an async import resolves.
- **No guarantee of one tick per frame.** On Android especially, multiple
  updates can queue between paints; see "Coalescing" below.

### Coalescing

The 25 Hz cadence is faster than most devices paint (60 Hz = 16.7 ms), so you
will often receive more ticks than frames. Writing to the DOM on every tick
wastes work. The recommended pattern (used by `web-retro`, `web-ambient`, and
`web-analog`) is to stash the latest state and defer the render to
`requestAnimationFrame`:

```javascript
let pendingCarState = null;
let renderFrame = 0;

function receiveMessage(car) {
  pendingCarState = car;
  if (renderFrame) return;          // already scheduled
  renderFrame = requestAnimationFrame(() => {
    renderFrame = 0;
    const next = pendingCarState;
    pendingCarState = null;
    renderCarState(next);           // DOM writes here
  });
}

window.receiveMessage = receiveMessage;
```

This collapses N queued ticks into one paint. The perf harness specifically
measures `updateToPaintP95Ms` — coalescing is how you keep it under budget.

## Reference: getters and JSON fields

The table below lists every state value the bridge exposes. Android getter
names and iOS JSON keys are both shown; apps using the dual-platform pattern
only deal with the unprefixed keys inside `receiveMessage`.

**Type** maps to the Android `@JavascriptInterface` return type. In JS every
value arrives as-is (numbers, booleans). **Default** is the bridge's startup
value before the first real update arrives — useful for initial render and for
the harness test fixtures (see `_perf-harness/lib/getters.js`).

### Driving

| Android getter | iOS JSON key | Type | Default | Notes |
|---|---|---|---|---|
| `getEgoSpeed` | `egoSpeed` | Float | 0 | km/h or mph per `isImperial` (host-converted) — see "Units" |
| `getEgoSteeringAngle` | `egoSteeringAngle` | Float | 0 | Steering angle in degrees |
| `getGear` | `gear` | Float | 0 | `1=P`, `2=R`, `3=N`, `4=D`; `0`=invalid, `7`=SNA (apps typically fall back to `D`) |
| `isAdasOn` | `adasOn` | Boolean | false | Full self-driving engaged |
| `isMadsActive` | `madsActive` | Boolean | false | sunnypilot MADS — steering/lateral assist engaged independently of cruise |
| `isExperimentalMode` | `experimentalMode` | Boolean | false | openpilot experimental mode |
| `isChangingLane` | `changingLane` | Boolean | false | Lane change in progress |

### Blinkers and blind spot

| Android getter | iOS JSON key | Type | Default | Notes |
|---|---|---|---|---|
| `getLeftBlinker` | `leftBlinker` | Float | 0 | `>0` = active |
| `getRightBlinker` | `rightBlinker` | Float | 0 | `>0` = active |
| `getLeftBlindSpot` | `leftBlindSpot` | Float | 0 | `>0` = vehicle detected |
| `getRightBlindSpot` | `rightBlindSpot` | Float | 0 | `>0` = vehicle detected |
| `isAlwaysOnBlindSpotMonitor` | `alwaysOnBlindSpotMonitor` | Boolean | true | User setting — whether the app should show blind-spot indicators even without a blinker |

### Speed and navigation

| Android getter | iOS JSON key | Type | Default | Notes |
|---|---|---|---|---|
| `getFusedSpeedLimit` | `fusedSpeedLimit` | Float | 0 | Speed limit; `0` = no limit known. Host-converted to mph when imperial **and** the phone's region posts signs in km — see "Units" |
| `getAccSetSpeed` | `accSetSpeed` | Float | 0 | km/h or mph per `isImperial` (host-converted) — see "Units" |
| `getStopLineDist` | `stopLineDist` | Float | 0 | Meters to the stop line; `>0` = active |
| `getTrafficLightColor` | `trafficLightColor` | Float | 0 | `0=None`, `1=Red`, `2=Green`, `3=Yellow` |
| `getLaneDepartureWarning` | `laneDepartureWarning` | Float | 0 | `>0` = warning active |
| `getSpeedCameraDistance` | `speedCameraDistance` | Int | -1 | Meters to the nearest speed camera; **Android-only** (not in iOS JSON); `-1` = no camera |

iOS-only JSON keys (no Android getter yet):

| iOS JSON key | Type | Notes |
|---|---|---|
| `selfdriveActive` | Boolean | openpilot self-drive engaged (distinct from `adasOn`) |
| `sideCollisionWarning` | Float | `>0` = warning active |

### Vehicle body

| Android getter | iOS JSON key | Type | Default | Notes |
|---|---|---|---|---|
| `getAnyDoorOpen` | `anyDoorOpen` | Float | 0 | `>0` = any door open |
| `getBuckleStatus` | `buckleStatus` | Float | 0 | `0` = unbuckled, `>0` = buckled |
| `getOdometer` | `odometer` | Float | 0 | km or miles per `isImperial` (pre-converted) |

### Battery and energy

| Android getter | iOS JSON key | Type | Default | Notes |
|---|---|---|---|---|
| `getFullPackEnergy` | `fullPackEnergy` | Float | 0 | Total pack capacity |
| `getNominalEnergyRemaining` | `nominalEnergyRemaining` | Float | 0 | Usable remaining energy |
| `getEnergyBuffer` | `energyBuffer` | Float | 0 | Reserve energy below zero (buffer) |
| `getMaxRegenPower` | `maxRegenPower` | Float | 0 | Max regen power |
| `getMaxDischargePower` | `maxDischargePower` | Float | 0 | Max discharge power |
| `getPackVoltage` | `packVoltage` | Float | 0 | Pack voltage |
| `getPackCurrent` | `packCurrent` | Float | 0 | Pack current (signed: `+` = discharging, `-` = charging) |
| `getPackTMin` | `packTMin` | Float | 0 | Min pack temperature, °C or °F per `isImperial` |
| `getPackTMax` | `packTMax` | Float | 0 | Max pack temperature, °C or °F per `isImperial` |

### Display settings

These reflect the user's in-app settings (stored as `DisplaySettings` on the
host). They exist so your app can respect the user's preferences without
re-implementing them.

| Android getter | iOS JSON key | Type | Default | Notes |
|---|---|---|---|---|
| `isImperial` | `isImperial` | Boolean | false | `true` = user wants imperial units (mph, miles, °F). See "Units" for which fields the host converts. |
| `isDarkMode` | `darkMode` | Boolean | false | User-selected theme. Apply `data-theme` on `:root` (see "Dark mode"). |
| `getRenderQuality` | `renderQuality` | Int | 3 | `1–5`. Hint for the app — e.g. drop shadows, particle effects, sub-pixel detail. |
| `getDarkModeBackgroundGray` | `darkModeBackgroundGray` | Int | 0 | `0–255`. Used by apps that render a custom dark-mode background (e.g. `web-vanilla` passes it to its 3D renderer). |
| `getShowPhoneBattery` | `showPhoneBattery` | Boolean | true | User wants to see phone battery — your app should hide its phone-battery UI if `false`. |
| `getShowCarBattery` | `showCarBattery` | Boolean | true | Same, for car battery / BMS readouts. |
| `getShowOdometer` | `showOdometer` | Boolean | true | Same, for odometer. |
| `isAlwaysOnBlindSpotMonitor` | `alwaysOnBlindSpotMonitor` | Boolean | true | Whether the user wants blind-spot indicators visible without a blinker active. See "Blinkers and blind spot" above. |

### Device

| Android getter | iOS JSON key | Type | Default | Notes |
|---|---|---|---|---|
| `getPhoneBattery` | `phoneBattery` | Int | -1 | 0–100 percent; `-1` = unknown |
| `getCurrentTime` | `currentTime` | Long | 0 | Epoch milliseconds (same as `Date.now()` would return) |

## Units and imperial conversion

`isImperial()` / the `isImperial` field reflects the user's in-app unit
preference. What that means per field differs — three groups:

**Host-converted when `isImperial` is true** (`CarState.toImperial()`, both
platforms):

- `egoSpeed`, `accSetSpeed` — km/h → mph
- `odometer` — km → miles
- `packTMin`, `packTMax` — °C → °F
- `fusedSpeedLimit` — km/h → mph, but **only when the phone's region posts
  speed-limit signs in km/h**. In mile-sign countries (US, GB, MM, LR) the
  limit is assumed to already be mph and is passed through unchanged.

**Not affected by `isImperial`** — always in the car's cluster-unit:
Traffic-light and blinkers are based on CAN signals that don't carry a unit,
and gear is an abstract index. Pass them through as-is.

**Not affected by `isImperial`** — physical SI values independent of any unit
preference: steering angle (degrees), distances (`stopLineDist`,
`speedCameraDistance`, meters), voltage, current, energy, and power.

Apps should display values as-is (no re-multiplication needed) and toggle unit
labels by reading `isImperial` — e.g. `MPH` vs `KMH`, `mi` vs `km`, `°F` vs
`°C`.

## Safe-area CSS

The host WebView does **not** apply `env(safe-area-inset-*)` reliably; apps
that need to avoid display cutouts (notch, rounded corners) compute their own
insets from `window.visualViewport` and expose them as CSS variables. The
canonical pattern (`web-retro`, `web-ambient`, `web-analog`):

```css
:root {
  --safe-top: 0px;
  --safe-right: 0px;
  --safe-bottom: 0px;
  --safe-left: 0px;
}

.viewport {
  padding-top:    max(env(safe-area-inset-top),    var(--safe-top));
  padding-right:  max(env(safe-area-inset-right),  var(--safe-right));
  padding-bottom: max(env(safe-area-inset-bottom), var(--safe-bottom));
  padding-left:   max(env(safe-area-inset-left),   var(--safe-left));
}
```

```javascript
function updateSafeInsets() {
  const vv = window.visualViewport;
  if (!vv) return;
  const w = window.innerWidth, h = window.innerHeight;
  document.documentElement.style.setProperty('--safe-top',    `${Math.max(0, vv.offsetTop)}px`);
  document.documentElement.style.setProperty('--safe-right',  `${Math.max(0, w - vv.width - vv.offsetLeft)}px`);
  document.documentElement.style.setProperty('--safe-bottom', `${Math.max(0, h - vv.height - vv.offsetTop)}px`);
  document.documentElement.style.setProperty('--safe-left',   `${Math.max(0, vv.offsetLeft)}px`);
}

window.addEventListener('resize', updateSafeInsets);
window.visualViewport?.addEventListener('resize', updateSafeInsets);
updateSafeInsets();
```

The `max(env(...), var(--safe-*))` casing lets the WebView's native insets win
when they're larger (true hardware cutouts) and falls back to the JS-computed
values otherwise. Call `updateSafeInsets` on load and on every
`visualViewport` resize.

## Dark mode

Apps implement theming by setting `data-theme="dark"` or `"light"` on
`document.documentElement` (`:root`). There is no framework — each app provides
its own `[data-theme="light"]`/`[data-theme="dark"]` CSS overrides.

Read `isDarkMode` / `darkMode` at the **top** of your update handler, before
any DOM writes, so the theme is correct for the rest of the render:

```javascript
function receiveMessage(car) {
  document.documentElement.setAttribute('data-theme', car.darkMode ? 'dark' : 'light');
  // ...rest of render
}
```

`renderQuality` and `darkModeBackgroundGray` are optional tuning hints for apps
that want to match the host's dark-mode aesthetic (e.g. `web-vanilla` feeds
`darkModeBackgroundGray` into its 3D renderer's background color).

## Performance contract

Web dash-apps are benchmarked by an automated harness before they can merge.
Your app must:

1. Define `window.onCarStateUpdate` (used by the harness — it simulates the
   Android `NativeCarState` path). The harness fails the run if the handler
   isn't defined within 10 s of page load (`contractTimeoutMs` in
   `_perf-harness/lib/budgets.json`).
2. Stay within the per-run budgets below. The harness replays a 60 s
   deterministic drive at 25 Hz on 6× CPU-throttled Chromium (Pixel 3 XL-class,
   landscape dashboard viewport).

Key budgets (each has a rationale note in `budgets.json`):

| Budget | Value | What it gates |
|---|---|---|
| `injectionLagP95Ms` | 50 | Scheduled-vs-actual fire time (stale-data symptom) |
| `injectionLagMaxMs` | 200 | Single stall = visible freeze of a driving instrument |
| `updateToPaintP95Ms` | 100 | Handler start → next painted frame |
| `longestTaskMs` | 150 | One long task blocks every queued update behind it |
| `heapGrowthMb` | 10 | Growth over a 60 s run — surfaces leaks that pass short tests |
| `warmupMs` | 5000 | Excluded from gating (font load, JIT warmup) |

Run the identical check locally (`dash-apps/README.md` has the full guide):

```bash
cd dash-apps/_perf-harness
npm ci && npx playwright install chromium
node run.js ../web-yourapp
```

**The harness only exercises the `NativeCarState` / `onCarStateUpdate` path.**
It does not test the iOS `receiveMessage` path. Defining both is still required
for cross-platform support; only the Android-facing half is perf-gated.

## Reference implementations

- `dash-apps/web-retro/index.html` — canonical vanilla JS, dual-platform. Start
  here. Demonstrates the `receiveMessage` + `onCarStateUpdate` pair, safe-area
  handling, dark mode, `requestAnimationFrame` coalescing, and segment-display
  rendering.
- `dash-apps/web-ambient/index.html` — vanilla JS, dual-platform, simpler layout.
- `dash-apps/web-analog/scripts/dashboard-runtime.js` — same pattern with the
  update handler in an external script rather than inline.
- `dash-apps/web-expo/hooks/useCarState.ts` — React/TypeScript hook
  encapsulating the dual-platform bridge. Good template for component-based
  apps.

When in doubt, copy from `web-retro` — it is the maintained reference.