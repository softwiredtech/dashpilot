# dash-apps

Dash apps are the sandboxed applications running inside the Dashpilot android app.
They can be either web apps running in the `WebDashView` Composable, or Rive apps running in `RiveDashView`.

## Performance requirements

Dash-apps render live driving data - even one second of stale speed is unacceptable. Web
dash-apps are benchmarked by an automated performance gate before merging.

**Which apps are gated** is declared in the CI matrix at
[`.github/workflows/dashapp-perf.yml`](../.github/workflows/dashapp-perf.yml) — the matrix
is the single source of truth. Currently gated: web-retro, web-analog, web-ambient. Not
gated: web-vanilla (WASM/Bevy 3D renderer — invisible to the jsdom tripwire) and web-expo
(React Native's bridge — not the `window.onCarStateUpdate` contract the gate replays). Adding
a new dash-app to the matrix is a one-line edit in the workflow file.

**The gate:** your app must define `window.onCarStateUpdate` and read values from `window.NativeCarState`. CI replays a deterministic 6 s drive (150 frames at 25 Hz) in
jsdom (no browser) and enforces one per-tick budget documented inline in
[`_perf-gate/gate.js`](./_perf-gate/gate.js). The gate fails when a budget is broken **three
ticks in a row** (after a 2-second warmup):

| budget | default | catches |
|---|---|---|
| child updates per parent | `max 6` | per-child update loops — apps that write attributes/text to many siblings sharing one parent every tick (the actual web-retro regression: `tape.cells.forEach(c => c.className = next)` = 8 hits on one parent); in-place single-element updates cost 1-2 |

The gate is a regression **tripwire**, not a device-fidelity measurement: it budgets the
DOM-mutation churn of each `onCarStateUpdate` tick in Node. Layout,
paint, and canvas work are invisible — a canvas- or CSS-animation-heavy regression will
pass, and the budget is an absolute jsdom record count, not head-unit time. The verdict is a
consecutive-streak (3-in-a-row) check on per-parent mutation concentration, so a single isolated stall
(one multi-hundred-mutation tick in an otherwise-clean run) does not fail the gate —
deliberately no per-tick max, because a lone tick preempted by a busy CI runner would make
the gate flaky. The streak is stricter than a p95 on clustered regressions (three bad
ticks back-to-back trips immediately) and looser on scattered spikes. It
exists to catch the failure mode that actually occurred: per-child update loops rewriting attributes on every sibling, every tick (e.g. web-retro's blind-spot tape).

**What this gate does not catch:** a wholesale `el.innerHTML = "..."` rebuild fires ONE batched MutationObserver record regardless of child count, so neither per-parent concentration nor a total record count sees it. If a wholesale-rebuild regression ever occurs, count `addedNodes.length + removedNodes.length` inside childList records — out of scope until it shows up. Also invisible: synchronous CPU cost (the previous CPU budget was removed — only a synthetic fixture ever tripped it).

`window.NativeCarState` is injected as a `Proxy` whose methods read a shared state object
the gate mutates per tick. State keys are derived from method name by convention
(`getEgoSpeed`→`egoSpeed`, `isAdasOn`→`adasOn`); the one exception (`isImperial`→`useImperial`)
lives in `gate.js`. Apps reading an unknown method get `undefined`, so their own `readNative`
fallback runs. No getter list to keep in sync with `CarStateBridge.kt` — the gate drifts with
the Kotlin naming convention automatically.

## Run the gate locally

```bash
cd dash-apps/_perf-gate
npm ci
node gate.js ../web-yourapp
```

> Exit codes: `0` PASS, `1` budget violation (a `why:` line names the failing metric),
> `2` APP_ERROR (missing `window.onCarStateUpdate` contract within 10 s, or an uncaught
> error the app tried to swallow), `3` INFRA (bad args / no `index.html`).

## Tuning map

- Change pass/fail thresholds: edit the `BUDGET_PER_PARENT` constant near the top of [`_perf-gate/gate.js`](./_perf-gate/gate.js) (it has a rationale comment).
- Change the simulated drive data: edit `buildScenario()` in [`_perf-gate/gate.js`](./_perf-gate/gate.js).
- Change `NativeCarState` injection or the key-derivation special cases: edit `keyFromMethod()` / `beforeParse` in [`_perf-gate/gate.js`](./_perf-gate/gate.js).
- Change the CI matrix (which apps run on every PR): edit `matrix.include` in [`.github/workflows/dashapp-perf.yml`](../.github/workflows/dashapp-perf.yml).
