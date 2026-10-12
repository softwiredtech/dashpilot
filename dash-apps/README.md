# dash-apps

Dashboards that run inside the Dashpilot app, fed live car data.

| folder | |
|---|---|
| `web-*/` | web dashboards: `index.html`, `manifest.json`, `preview.jpg` |
| `rive/` | Rive dashboards (`.riv`) |

## Getting data

The app sends a new state about 25 times a second.

| | delivery |
|---|---|
| Web, Android | calls `window.onCarStateUpdate()`; read values with `window.NativeCarState.getX()` / `isX()` |
| Web, iOS | calls `window.receiveMessage(state)` with a plain object |
| Rive (Android only) | writes numbers into the `MainViewModel` view model, see [Rive](#rive) |

A web app should handle both:

```js
const KEYS = ["egoSpeed", "gear", "leftBlinker", "rightBlinker", "isImperial"]; // the keys you use

function fromAndroid() {
  const n = window.NativeCarState, s = {};
  for (const k of KEYS) {
    const cap = k[0].toUpperCase() + k.slice(1);
    const m = k === "isImperial" ? k : n["get" + cap] ? "get" + cap : "is" + cap;
    s[k] = n[m]?.();
  }
  return s;
}

window.onCarStateUpdate = () => render(fromAndroid());
window.receiveMessage = (state) => render(state);
```

## DashState

What a dash app receives: the [CarState](#carstate) fields plus these phone and settings fields, all in one flat object.

| key | type | meaning |
|---|---|---|
| `currentTime` | ms since epoch | |
| `phoneBattery` | 0–100 | -1 = unknown |
| `isImperial` | bool | user prefers miles / °F |
| `darkMode` | bool | |
| `darkModeBackgroundGray` | 0–255 | background gray picked for dark mode |
| `showPhoneBattery`, `showCarBattery`, `showOdometer` | bool | user wants these visible |
| `alwaysOnBlindSpotMonitor` | bool | show blind spots even without a blinker |
| `renderQuality` | 1–3 | hint for expensive effects |
| `dataSourceType` | string | `"dashkit"`, `"comma"`, `"websocket"`, `"demo"` |
| `speedCameraDistance` | meters | -1 = none nearby. **Android only** |

## CarState

Values not received yet are `0` / `false`. For speed limit, stop line and energy fields, 0 means unknown.

**Driving**

| key | type | meaning |
|---|---|---|
| `egoSpeed` | number | speed as the car displays it. Show as-is |
| `gear` | 0–4 | 0 unknown, 1 P, 2 R, 3 N, 4 D |
| `egoSteeringAngle` | degrees | steering wheel angle, negative = left |
| `leftBlinker`, `rightBlinker` | 0–2 | 0 off, 1 blinking (lamp dark), 2 blinking (lamp lit) |
| `anyDoorOpen` | 0/1 | |
| `buckleStatus` | 0/1 | 1 = driver buckled |
| `odometer` | number | km, or miles when `isImperial` |

**ADAS**

| key | type | meaning |
|---|---|---|
| `adasOn` | bool | cruise / lane keeping engaged |
| `accSetSpeed` | number | cruise set speed, same unit as `egoSpeed` |
| `fusedSpeedLimit` | number | posted limit, in the user's unit |
| `leftBlindSpot`, `rightBlindSpot` | 0–2 | 0 clear, 1 vehicle, 2 vehicle + urgent |
| `laneDepartureWarning` | 0–4 | 1 left, 2 right, 3 left severe, 4 right severe |
| `sideCollisionWarning` | 0–3 | 1 left, 2 right, 3 both. **iOS only** |
| `trafficLightColor` | 0–3 | 0 none, 1 red, 2 green, 3 yellow |
| `stopLineDist` | meters | distance to the stop line |
| `changingLane` | bool | lane change in progress |
| `madsActive`, `experimentalMode` | bool | openpilot states |
| `selfdriveActive` | bool | openpilot engaged. **iOS only** |

**Battery**

| key | type | meaning |
|---|---|---|
| `nominalEnergyRemaining`, `fullPackEnergy`, `energyBuffer` | kWh | charge = `(remaining - buffer) / (full - buffer)` |
| `powerLevel` | -100…100 | % of max regen (negative) or max discharge (positive) |
| `packVoltage` | V | |
| `packCurrent` | A | positive = discharging |
| `maxRegenPower`, `maxDischargePower` | kW | current limits |
| `packTMin`, `packTMax` | °C, or °F when `isImperial` | |

The app's CarState also has climate fields and the VIN; dash apps don't receive those yet.

## Rive

The file needs a view model named `MainViewModel` with a default instance and these **number** properties. Missing ones are skipped.

| property | value |
|---|---|
| `speed` | `egoSpeed` |
| `speedLimit` | `fusedSpeedLimit` |
| `gear` | `gear` |
| `steeringAngle` | `egoSteeringAngle` |
| `blinkerLeft`, `blinkerRight` | `leftBlinker`, `rightBlinker` |
| `leftBlindspot`, `rightBlindspot` | `leftBlindSpot`, `rightBlindSpot` |
| `trafficLightColor` | `trafficLightColor` |
| `stopDist`, `trafficLightDist` | `stopLineDist` |

The Android runtime is rive-android 11.7. Debug builds can load any `.riv` from the dashboard picker.

## Saving settings

Web apps get one string of storage, usually JSON.

| | Android | iOS |
|---|---|---|
| load | `NativeCarState.getAppData()`, `""` if none | `window.__DASHPILOT_APP_DATA__` |
| save | `NativeCarState.setAppData(string)` | `webkit.messageHandlers.appData.postMessage(string)` |
| pause the swipe-to-next-dashboard gesture while you drag | `NativeCarState.setEditing(bool)` | `webkit.messageHandlers.editing.postMessage(bool)` |

## Adding a web app to the apps

1. `dash-apps/web-<id>/` with `index.html`, `preview.jpg` (landscape, ~2:1) and a `manifest.json`. `settings` lists the display settings your app uses, so the app only offers those:
   ```json
   { "$schema": "../../docs/dashapp-manifest.schema.json", "manifestVersion": 1, "id": "<id>", "settings": ["useImperial", "darkMode"] }
   ```
2. Add `web-<id>` to `DEFAULT_FOLDERS` in `scripts/sync-to-android.sh` and `scripts/sync-to-ios.sh`.
3. Android: an entry in `availableDashboards` (`datamodel/dash/DashboardConfig.kt`) and a `dashboard_name_<id>` string.
4. iOS: an entry in `availableDashboards` (`Models/DashboardConfig.swift`), `<id>` in `localApps` in `UI/WebDashView.swift`, and a `preview_<id>.imageset` in `Assets.xcassets` (copy an existing one).

`node scripts/validate-manifests.js` checks the manifests; CI runs it too.
