# dashkit-connect

Android library for talking to a [DashKit](https://github.com/softwiredtech/dashpilot) over Bluetooth LE. It owns the whole connection lifecycle (scan, bond, reconnect, keepalive) and exposes the DashKit's services as small Kotlin APIs:

| Package | What it gives you |
| --- | --- |
| `com.softwiredtech.dashkitconnect` | `DashKitBleManager` (connection), `CarState` (decoded vehicle state), `DashKitDiscovery` (one-shot "is a DashKit nearby?" scan), `VehicleControl` (Tesla control opcodes), `ConnectionStatus`, `DashKitTelemetry` |
| `...dashkitconnect.can` | `DashKitCarStateSource` (decoded `CarState` stream), `DashKitCanSource` (raw CAN frames), `DashKitDecoder`, `DashKitVehicleProfile` |
| `...dashkitconnect.tesla` | `TeslaStatusSource` / `TeslaStatus` (car + key-enrollment state), `TeslaClient` (enrollment commands), `TeslaVehicleScanner` |
| `...dashkitconnect.ota` | `FirmwareUpdateManager` (version check + download + BLE upload), `DashKitOtaUpdate`, `DashKitFirmwareVersion`, `FirmwareUpdateRepository`, `SemVer` |

No Firebase or Compose dependencies. It pulls in `kotlinx-coroutines-android` (Flows are part of the public API) and OkHttp (firmware downloads). CAN decoding runs in a small bundled native library (`libdashkitdecoder.so`, built from the shared `bridge/dbc` and `bridge/car` sources) with the Tesla DBC shipped as an asset, so consumers of the AAR need no NDK.

## Adding it to your app

Inside this repository the DashPilot app depends on it as `implementation(project(":dashkit-connect"))`.

For an external app, publish it to your local Maven repository and depend on the artifact:

```
./gradlew :dashkit-connect:publishToMavenLocal
```

```kotlin
repositories { mavenLocal() }
dependencies { implementation("com.softwiredtech:dashkit-connect:0.1.0") }
```

Requirements: `minSdk 29`, Kotlin 2.x.

### Permissions

The library manifest declares `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` and `INTERNET` (plus the legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` for API 30 and below). Your app must still request the runtime permissions before connecting:

- API 31+: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`
- API 29–30: `ACCESS_FINE_LOCATION` (required by the platform for BLE scanning; declare it in your own manifest)

## Connecting

```kotlin
val manager = DashKitBleManager(context)

lifecycleScope.launch {
    manager.connectionState.collect { status ->
        when (status) {
            ConnectionStatus.Connected -> ...
            ConnectionStatus.Connecting -> ...
            ConnectionStatus.Disconnected -> ...
            is ConnectionStatus.Error -> showError(status.message)
        }
    }
}

manager.connect()      // scans for "DashKit", bonds if needed, discovers services
...
manager.disconnect()   // user-initiated; suppresses auto-reconnect
```

Things the manager does for you that your app must not duplicate:

- Retries the scan+connect cycle three times before reporting an error, staying under Android's 5-scans-per-30-seconds throttle.
- Auto-reconnects after an unexpected drop (typically a DashKit reboot).
- Recovers from a stale bond: after three quick drops it removes the OS bond and pairs again.
- Sends a keepalive ping every 15 s while connected. The firmware drops any phone that stops pinging for 60 s so the single connection slot is freed. Call `onAppBackgrounded()` / `onAppForegrounded()` from your lifecycle so a backgrounded app releases the slot.

A DashKit accepts exactly one connection at a time. New phones can only pair while the DashKit has no bonds, or during a pairing window opened by an already-paired phone via `VehicleControl.sendEnterPairing(manager)`.

### Deciding what to do at launch

```kotlin
val found = DashKitDiscovery.find(context, timeoutMs = 3_000)
when {
    found == null -> // no DashKit advertising nearby
    found.bonded  -> manager.connect()
    else          -> // show your pairing flow, then manager.connect()
}
```

## Reading vehicle state

`DashKitCarStateSource` decodes the CAN stream into `CarState` (speed, gear, blinkers, doors, battery pack, odometer, VIN, ...) and emits a new value for every BLE notification, around 25 Hz.

```kotlin
val source = DashKitCarStateSource(context, manager)
source.start()      // builds the decoder in the background; survives reconnects
manager.connect()

lifecycleScope.launch {
    source.carState.collect { state -> speedLabel.text = "${state.egoSpeed} km/h" }
}
...
source.stop()
```

Values are metric. Sample or conflate the flow if your UI cannot keep up. To decode a vehicle other than a Tesla, pass a `DashKitVehicleProfile` pointing at your own DBC assets and a mapper type known to the native decoder.

### Raw CAN frames

If you want the frames themselves, `DashKitCanSource` delivers every BLE notification as a batch of `RawCanFrame`s and leaves decoding to you.

```kotlin
val canSource = DashKitCanSource(manager) { frames ->
    for (f in frames) handle(f.bus, f.address, f.data)
}
canSource.start()
manager.connect()
```

The callback runs on the Bluetooth binder thread.

## Vehicle control

```kotlin
VehicleControl.send(manager, VehicleControl.CMD_CLOSURE, VehicleControl.CLOSURE_REAR_TRUNK)
VehicleControl.sendRearFanToggle(manager)
VehicleControl.sendClimateKeep(manager, enabled = true)
```

All commands return `true` if the write was dispatched, not when the car acted on it. Opcodes are documented in `VehicleControl.kt` and mirror the firmware's `vehicle_control.h`.

## Tesla key status and enrollment

```kotlin
val tesla = TeslaStatusSource(manager)
tesla.start()
tesla.status.collect { st -> st.linkState /* EnrolledConnected, Staged, PairingWindow, ... */ }

TeslaClient.sendStart(manager)  // begin key enrollment for the staged car
```

## Firmware updates

```kotlin
val updater = FirmwareUpdateManager(manager)
updater.start()                   // reads the installed version over BLE
updater.checkForUpdate()          // suspend; compares against the release manifest
(updater.check.value as? FirmwareUpdateManager.Check.Available)?.let { updater.install(it.manifest) }
updater.otaState.collect { /* Uploading(progress) -> Rebooting */ }
updater.dispose()
```

Pass `manifestUrl` to `FirmwareUpdateManager` to serve firmware from your own host. During an OTA the manager suspends keepalive pings automatically.

## Crash reporting

`DashKitBleManager` logs connection breadcrumbs and failures to logcat. To forward them to your crash reporter, implement `DashKitTelemetry`:

```kotlin
object CrashlyticsTelemetry : DashKitTelemetry {
    override fun log(message: String) = FirebaseCrashlytics.getInstance().log(message)
    override fun recordException(throwable: Throwable) = FirebaseCrashlytics.getInstance().recordException(throwable)
}

val manager = DashKitBleManager(context, CrashlyticsTelemetry)
```

## GATT layout

| Service | Characteristic | Purpose |
| --- | --- | --- |
| `CADA0000` | `CADA0001` | CAN frame stream (notify) |
| `CADA0000` | `CADA0004` | Vehicle control commands (write, encrypted) |
| `CADA0000` | `CADA0005` | Firmware version (read) |
| `CADA0100` | `CADA0101` / `0102` / `0103` | OTA control / data / status |
| `CADA0200` | `CADA0201` / `0202` | Tesla app channel: command / status |

Full UUIDs use the suffix `-CA00-B1E0-B0D6-C000AA0100A1`.
