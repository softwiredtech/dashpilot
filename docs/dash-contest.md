# Dashpilot Dash App Contest

Design the dashboard people look at while they drive. Two categories, one winner each:

- **Web**: HTML/CSS/JS
- **Rive**: a single `.riv` file

Winners ship in the Dashpilot app with credit.

| | |
|---|---|
| Submissions open | TBD |
| Deadline | TBD |
| Winners announced | TBD |
| Prizes | TBD |

## The data

Everything your dash gets is in [dash-state.md](dash-state.md): speed, gear, blinkers, blind spots, traffic lights, cruise, battery and more, about 25 times a second.

## Try it

### In your browser

Web: add this as the first script in your `<head>` and open the page:

```html
<script src="https://cdn.jsdelivr.net/gh/softwiredtech/dashpilot@main/dash-apps/_sim/dashpilot-sim.js"></script>
```

It plays a 90-second loop (cruise, a turn with blinkers, a red light, blind-spot cars) and does nothing inside the real app, so you can leave it in. `?sim=ios` switches to the iOS delivery path; test both. Press `?` for keyboard controls (gear, blinkers, traffic light, imperial units, pause, seek), or drive it from the console: `DashpilotSim.set({ egoSpeed: 130 })`.

Rive: open [`dash-apps/_sim/rive.html`](../dash-apps/_sim/rive.html) and drop in your `.riv`. Properties the page can't find in `MainViewModel` are listed in the corner.

### In the app

<!-- TODO: confirm once the dev loader ships on both platforms -->
Settings → **Start Demo Mode**, then Dashboards → **Load dev dash** and enter your page's URL (any `http://` address your phone can reach, e.g. your laptop on the same Wi-Fi) or pick your `.riv`.

## Rules

**Both categories**
- Landscape. Must look right from 16:9 to 21:9 and on 4:3 tablets.
- Speed is the most legible thing on screen, readable in under a second.
- No full-screen flashing or large blinking areas.
- Works offline. No network requests at runtime.
- Original work, licensed MIT so we can ship it.

**Web**
- One folder: `index.html`, `manifest.json`, `preview.jpg` ([format](adding-a-dash-app.md#1-app-directory)). 10 MB max.
- Handles both delivery paths (Android `onCarStateUpdate`, iOS `receiveMessage`).
- Passes the performance gate: `node dash-apps/_perf-gate/gate.js <your-folder>`.
- No frameworks that need a build step at install time. Ship the built output.

**Rive**
- One `.riv`, 5 MB max, built against the `MainViewModel` contract in [dash-state.md](dash-state.md#rive).
- Must render on rive-android 11.7. Avoid `LayoutParticipant`: it is dropped on that runtime.

## Judging

| | weight |
|---|---|
| Readable at a glance while driving | 30% |
| Design and originality | 30% |
| Uses the data well (warnings, ADAS, battery) | 20% |
| Smooth on a mid-range phone, no glitches | 20% |

## Submit

A link to a public repo or a zip, plus a 15-second screen recording, to TBD.
