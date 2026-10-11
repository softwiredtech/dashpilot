# DashState

Everything a dash app can read. The host sends a fresh snapshot at ~25 Hz.

## Getting it

| | Web, Android | Web, iOS | Rive (Android) |
|---|---|---|---|
| delivery | host calls `window.onCarStateUpdate()`, you read `window.NativeCarState.getX()` / `isX()` | host calls `window.receiveMessage(state)` with a plain object | host writes numbers into the `MainViewModel` view model |

Support both web paths with one adapter:

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

Use the key names below in both cases. Android getters are `get<Key>()`, or `is<Key>()` for booleans (`isAdasOn`, `isDarkMode`, …); `isImperial()` keeps its name.

## Driving

| key | type | meaning |
|---|---|---|
| `egoSpeed` | number | speed, already in the user's unit. Display as-is |
| `gear` | 0–4 | 0 unknown, 1 P, 2 R, 3 N, 4 D |
| `egoSteeringAngle` | degrees | steering wheel angle, negative = left |
| `leftBlinker`, `rightBlinker` | 0–2 | 0 off, 1 blinking (lamp dark), 2 blinking (lamp lit) |
| `anyDoorOpen` | 0/1 | |
| `buckleStatus` | 0/1 | 1 = driver buckled |
| `odometer` | number | km, or miles when `isImperial` |

## ADAS

| key | type | meaning |
|---|---|---|
| `adasOn` | bool | cruise / lane keeping engaged |
| `accSetSpeed` | number | cruise set speed, same unit as `egoSpeed` |
| `fusedSpeedLimit` | number | posted limit, user's unit. 0 = unknown |
| `leftBlindSpot`, `rightBlindSpot` | 0–2 | 0 clear, 1 vehicle, 2 vehicle + urgent |
| `laneDepartureWarning` | 0–4 | 1 left, 2 right, 3 left severe, 4 right severe |
| `sideCollisionWarning` | 0–3 | 1 left, 2 right, 3 both. **iOS only** |
| `trafficLightColor` | 0–3 | 0 none, 1 red, 2 green, 3 yellow |
| `stopLineDist` | meters | distance to stop line, 0 = none |
| `changingLane` | bool | lane change in progress |
| `madsActive`, `experimentalMode` | bool | openpilot states |
| `selfdriveActive` | bool | openpilot engaged. **iOS only** |
| `speedCameraDistance` | int, meters | -1 = none nearby. **Android only** |

## Battery

| key | type | meaning |
|---|---|---|
| `nominalEnergyRemaining`, `fullPackEnergy`, `energyBuffer` | kWh | state of charge = `(remaining - buffer) / (full - buffer)` |
| `powerLevel` | -100…100 | % of max regen (negative) or discharge (positive) |
| `packVoltage` | V | |
| `packCurrent` | A | positive = discharging |
| `maxRegenPower`, `maxDischargePower` | kW | current limits |
| `packTMin`, `packTMax` | °C, or °F when `isImperial` | |

## Phone & settings

| key | type | meaning |
|---|---|---|
| `currentTime` | ms since epoch | |
| `phoneBattery` | 0–100 | -1 = unknown |
| `isImperial` | bool | label speeds mph instead of km/h |
| `darkMode` | bool | |
| `darkModeBackgroundGray` | 0–255 | background gray the user picked for dark mode |
| `showPhoneBattery`, `showCarBattery`, `showOdometer` | bool | user wants these visible |
| `alwaysOnBlindSpotMonitor` | bool | show blind spots even without blinker |
| `renderQuality` | 1–3 | hint for expensive effects |
| `dataSourceType` | string | `"dashkit"`, `"comma"`, `"websocket"`, `"demo"` |

Missing or not-yet-received values are `0` / `false`. Treat 0 as "unknown" for speed limit, stop line and energy fields.

## Rive

The file needs a view model named `MainViewModel` (default instance) with these **number** properties:

| property | from |
|---|---|
| `speed` | `egoSpeed` |
| `speedLimit` | `fusedSpeedLimit` |
| `gear` | `gear` |
| `steeringAngle` | `egoSteeringAngle` |
| `blinkerLeft`, `blinkerRight` | `leftBlinker`, `rightBlinker` |
| `leftBlindspot`, `rightBlindspot` | `leftBlindSpot`, `rightBlindSpot` |
| `trafficLightColor` | `trafficLightColor` |
| `stopDist`, `trafficLightDist` | `stopLineDist` |

Same encodings as above. Missing properties are ignored.

## Saving settings (web only)

| | Android | iOS |
|---|---|---|
| load | `NativeCarState.getAppData()` → string, `""` if none | `window.__DASHPILOT_APP_DATA__` |
| save | `NativeCarState.setAppData(string)` | `webkit.messageHandlers.appData.postMessage(string)` |
| suspend swipe while dragging | `NativeCarState.setEditing(bool)` | `webkit.messageHandlers.editing.postMessage(bool)` |
