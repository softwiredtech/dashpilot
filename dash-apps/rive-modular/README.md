# rive-modular

A Rive dash-app authored as text with the [Rive CLI](https://rive.app/docs/cli/overview) and
[Rive Markup Language](https://rive.app/docs/runtimes/advanced-topic/rml). No editor file is
involved: the `.rml` sources in this folder compile straight to `dist/dashboard_modular.riv`,
which the Android app loads through `RiveDashView` (dashboard id `modular`).

The layout is five tiles around a fixed speed cluster. Every tile is a slot that can show any
of the widgets below; tapping a tile cycles it to the next widget, and the Android app persists
the choice. The default layout needs no configuration.

```
┌──────────┐ ┌──────────────┐ ┌─────┐ ┌─────┐
│  slot1   │ │              │ │     │ │slot4│
├──────────┤ │ speed ring   │ │slot3│ ├─────┤
│  slot2   │ │ gear, mode   │ │     │ │slot5│
└──────────┘ └──────────────┘ └─────┘ └─────┘
```

## Widgets

| index | widget | reads |
|---|---|---|
| 0 | Battery | `nominalEnergyRemaining`, `energyBuffer`, `fullPackEnergy` |
| 1 | Range | usable energy × `rangeFactor` |
| 2 | Power | `packVoltage` × `packCurrent`, regen/discharge bar against `maxRegenPower` / `maxDischargePower` |
| 3 | Pack temp | `packTMax`, `packTMin` |
| 4 | Odometer | `odometer` |
| 5 | Speed limit | `speedLimit` |
| 6 | Cruise | `accSetSpeed`, `adasOn` |
| 7 | Climate | `acTemp`, `hvacFanLevel` |
| 8 | Clock | `clock`, `phoneBattery` |
| 9 | Traffic light | `trafficLightColor`, `stopDist` |

Default layout: slot1 Battery, slot2 Power, slot3 Range, slot4 Pack temp, slot5 Speed limit.

The center cluster is always on: speed ring (0–200 over three quarters of the circle), speed,
unit, gear letter (P/R/N/D from the Tesla `DI_gear` encoding), ENGAGED/MANUAL from `adasOn`,
blinker arrows, blind-spot glows on the artboard edges, and warning chips for door open,
seat belt, lane departure and side collision.

## View model contract

The artboard binds `MainViewModel`. The host sets these by name (all numbers unless noted):

- car state: `speed`, `speedLimit`, `gear`, `steeringAngle`, `blinkerLeft`, `blinkerRight`,
  `leftBlindspot`, `rightBlindspot`, `stopDist`, `trafficLightColor`, `accSetSpeed`,
  `laneDeparture`, `sideCollision`, `anyDoorOpen`, `buckleStatus`, `adasOn`, `selfdriveActive`,
  `experimentalMode`, `madsActive`, `changingLane` (flags are 0/1 numbers, not booleans, so
  they can drive opacity directly)
- vehicle bus: `fullPackEnergy`, `nominalEnergyRemaining`, `energyBuffer`, `maxRegenPower`,
  `maxDischargePower`, `packVoltage`, `packCurrent`, `packTMin`, `packTMax`, `odometer`,
  `acTemp`, `acTempRight`, `hvacFanLevel`, `hvacPowerState`, `hvacAcMode`
- host: `phoneBattery`, `rangeFactor` (km or mi per kWh), strings `speedUnit`, `distUnit`,
  `tempUnit`, `clock`
- layout: `slot1` … `slot5`, the widget index per tile. Written by the file itself on tap, so
  the host can read them back to persist.

Unit conversion happens in the host: the file only formats what it is given.

## Files

- `scene.rml` – the `Dash` artboard: layout, speed cluster, slot stacks, tap listeners
- `widgets.rml` – one component artboard per widget, each bound to `MainViewModel`
- `data.rml` – the view model, default instance, fonts and every data converter
- `rive.yaml` – project config; output goes to `dist/`
- `inter-*.ttf` – Inter (SIL OFL), embedded into the `.riv`

### Runtime compatibility

The Android app runs rive-android 11.7.0, which predates `LayoutParticipant`. Text placed in a
layout with a participant renders fine in the CLI preview but piles up at the box origin on the
device, so every text here sits in its own `LayoutComponent` (fill width, fixed height) with
`sizingValue="fixed"` and `alignValue` doing the horizontal placement. The runtime resizes the
text to that box. Keep to that pattern until the runtime is upgraded, and always check a change
on a phone, not only in the CLI window.

### How the slots work

A slot is a `LayoutComponent` with `layoutTypeValue="stack"` holding one wrapper per widget.
Each wrapper's `displayValue` is bound to the slot's number through a `NotEqK` formula
converter, so exactly one nested widget artboard is laid out and drawn. The widget artboards
carry no data of their own: they inherit the host's `MainViewModel` instance, so their binds
use the same absolute paths as the main scene. The tap listener reads the slot number through
the `NextKind` converter (`(n + 1) % 10`) and writes it back.

## Build

```bash
curl -fsSL https://releases.rive.app/cli/install.sh | sh   # once
cd dash-apps/rive-modular
rive . --once                      # writes dist/dashboard_modular.riv
rive .                             # live preview window, rebuilds on save
rive . --screenshot=build/x.png --advance=1 --data=speed=120 --data=slot1=6
./scripts/sync-to-android.sh       # copies the .riv into res/raw and the preview
```

Commit `dist/dashboard_modular.riv` together with the `.rml` change that produced it, and
re-render `preview.jpg` when the default look changes:

```bash
rive . --screenshot=build/preview.png --advance=1 --data=speed=72 --data=adasOn=1 --data=packCurrent=-40
sips -s format jpeg -s formatOptions 88 build/preview.png --out preview.jpg
```

`AGENTS.md` is the Rive CLI's own guidance for coding agents; keep it next to the sources.
