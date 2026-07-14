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
  `web-vanilla`, whose WASM/3D renderer runs on software rasterization in CI); it is not
  an escape hatch for slow apps.
- `"perf": { "root": "dist" }` - benchmark a built subdirectory (used by `web-expo`).

**The gate:** your app must define `window.onCarStateUpdate` and read values from
`window.NativeCarState`. CI replays a deterministic 20 s drive at 25 Hz in Chromium
throttled 6x (Pixel 3 XL-class hardware, landscape dashboard viewport) and enforces the
budgets in [`_perf-harness/lib/budgets.json`](./_perf-harness/lib/budgets.json) - each
budget and default harness knob documents its own rationale.

## Perf harness tuning map

- Change pass/fail thresholds or default harness knobs: [`_perf-harness/lib/budgets.json`](./_perf-harness/lib/budgets.json)
- Change which apps are gated or exempt: each app's `manifest.json` `perf` block
- Change target-selection rules for changed files: [`.github/workflows/dashapp-perf.yml`](../.github/workflows/dashapp-perf.yml) (inline bash step)
- Change the simulated drive data: [`_perf-harness/lib/scenario.js`](./_perf-harness/lib/scenario.js)
- Change the browser timing shim / `NativeCarState` injection: [`_perf-harness/lib/shim.js`](./_perf-harness/lib/shim.js)
- Change the exposed getter list: [`_perf-harness/lib/getters.js`](./_perf-harness/lib/getters.js)
- Change CLI behavior or validation: [`_perf-harness/run.js`](./_perf-harness/run.js)
- Change CI trigger behavior: [`.github/workflows/dashapp-perf.yml`](../.github/workflows/dashapp-perf.yml)

Run the identical check locally while developing:

```bash
cd dash-apps/_perf-harness
npm ci && npx playwright install chromium
node run.js ../web-yourapp
```

Set `PERF_DURATION=<ms>` to override the simulated drive length (must be a positive
number of milliseconds); when unset, the harness uses `defaultDurationMs` from
[`budgets.json`](./_perf-harness/lib/budgets.json).

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
