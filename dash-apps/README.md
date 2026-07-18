# dash-apps

Dash apps are the sandboxed applications running inside the Dashpilot android app.
They can be either web apps running in the `WebDashView` Composable, or Rive apps running in `RiveDashView`.

## Performance requirements

Dash-apps render live driving data - even one second of stale speed is unacceptable. Web
dash-apps are benchmarked by an automated performance gate before merging.

**Which apps are gated** is declared in each app's `manifest.json`:

- No `perf` field, or `"perf": { "gate": "standard" }` - the app is benchmarked in CI.
  This is the default: new themes are gated automatically.
- `"perf": { "gate": "exempt", "reason": "..." }` - skipped, with the reason shown in the
  CI summary. Exemption is for apps the harness cannot measure fairly (currently
  `web-vanilla`, whose WASM/3D renderer does its work in WebGL/canvas — invisible to the
  jsdom tripwire, so the gate would pass trivially); it is not an escape hatch for slow apps.
- `"perf": { "root": "dist" }` - benchmark a built subdirectory (used by `web-expo`).

**The gate:** your app must define `window.onCarStateUpdate` and read values from
`window.NativeCarState`. CI replays a deterministic 20 s drive at 25 Hz in jsdom (no
browser) and enforces the budgets in
[`_perf-harness/lib/budgets.json`](./_perf-harness/lib/budgets.json) - each budget and
default harness knob documents its own rationale.

The gate is a regression **tripwire**, not a device-fidelity measurement: it budgets the
CPU cost and DOM-mutation churn of each `onCarStateUpdate` tick in Node. Layout, paint,
and canvas work are invisible to it - a canvas- or CSS-animation-heavy regression will
pass, and budgets are absolute CI-runner time, not head-unit time. The budgets are
percentiles and means, so a single isolated stall (one multi-hundred-millisecond tick in
an otherwise-fast run) also passes - there is deliberately no per-tick max, because a
lone tick preempted by a busy CI runner would make the gate flaky. It exists to catch
the failure mode that actually occurred: per-tick work exploding (e.g. rebuilding the
DOM wholesale on every update).

### Key performance metrics

When the benchmark runs, it evaluates the app against these four metrics:

* **`tickCpuP95Ms` (Worst-Case Update Response Time)**: The maximum time (95th percentile) the app spends running its JavaScript code the instant a new telemetry update arrives. Spikes here directly cause visual stutters.
* **`mutationsPerTickP95` (Screen Rebuild Overhead)**: The number of separate parts of the webpage changed during an update. A low number means efficient, surgical DOM updates; a high number (like 50+) means the app is rewriting the DOM from scratch.
* **`meanProcessCpuPerTickMs` (Average Overall Workload)**: The average processor effort spent per update. This captures delayed background tasks, animations, or framework rendering work that runs between update ticks.
* **`steadyFrames` (Clean Updates Analyzed)**: The count of healthy frames analyzed after the initial 2-second "warm-up" period. A minimum count is required to ensure the test gathered enough data to be statistically reliable.

## Perf harness tuning map

- Change pass/fail thresholds or default harness knobs: [`_perf-harness/lib/budgets.json`](./_perf-harness/lib/budgets.json)
- Change which apps are gated or exempt: each app's `manifest.json` `perf` block
- Change target-selection rules for changed files: [`.github/workflows/dashapp-perf.yml`](../.github/workflows/dashapp-perf.yml) (inline bash step)
- Change the simulated drive data: [`_perf-harness/lib/scenario.js`](./_perf-harness/lib/scenario.js)
- Change the jsdom driver / `NativeCarState` injection: [`_perf-harness/lib/drive.js`](./_perf-harness/lib/drive.js)
- Change the exposed getter list: [`_perf-harness/lib/getters.js`](./_perf-harness/lib/getters.js)
- Change CLI behavior or validation: [`_perf-harness/run.js`](./_perf-harness/run.js)
- Change CI trigger behavior: [`.github/workflows/dashapp-perf.yml`](../.github/workflows/dashapp-perf.yml)
- Develop an app outside the phone: `cd _perf-harness && npm run dev -- ../web-retro`

Run the identical check locally while developing:

```bash
cd dash-apps/_perf-harness
npm ci
node run.js ../web-yourapp
```

Set `PERF_DURATION=<ms>` to override the simulated drive length (must be a positive
number of milliseconds); when unset, the harness uses `defaultDurationMs` from
[`budgets.json`](./_perf-harness/lib/budgets.json). Set `PERF_CONTRACT_TIMEOUT=<ms>`
to shorten how long the harness waits for the app to define `window.onCarStateUpdate`
(default 10000) — useful when iterating on an app that fails the contract.

> For apps whose manifest declares `"root"` (e.g. `web-expo` with `"root": "dist"`),
> run the app's build/export command first. The harness benchmarks the built
> subdirectory, not the source tree.

`web-expo` is the one current app with build-specific CI logic before benchmarking,
so if another app needs a pre-benchmark build/export step, start in
`.github/workflows/dashapp-perf.yml`.

For `web-expo` specifically:

```bash
cd ../web-expo
npm ci
npx expo export --platform web

cd ../_perf-harness
node run.js ../web-expo/dist --app-name web-expo
```
