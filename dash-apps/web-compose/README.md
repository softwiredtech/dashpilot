# web-compose

A dashboard the user assembles from widgets on a grid. The layout is a JSON
document (data only, no code); widgets are self-contained folders.

Run it in a browser with simulated data:

```
open "dash-apps/web-compose/index.html?demo"          # options: &lang=hu|de  &imperial  &light
```

## How it fits together

```
host.js      platform adapter: Android getters / iOS pushed JSON -> one flat `raw` object
signals.js   catalog of everything a widget can show (unit, range, precision)
layout.js    the layout document: normalize, clamp, migrate
registry.js  widget registry, prop / bind resolution
runtime.js   mounts the grid, runs the tick, isolates widget failures
widgets/     one folder per widget; _generated.{js,css} are built from them
layouts/     the default layout
```

A widget is a **form** (readout, bar, radial gauge, indicator), and the signal
it shows is a **bind** the user picks. New signals added to `signals.js` show up
in every widget whose bind accepts their kind; no widget changes needed.

One tick reads the host once, computes only the signals the layout uses, and
calls `update()` on widgets whose signals changed. An unchanged tick writes
nothing to the DOM.

## Editing on the device

Long-press the dashboard to open the editor (`?edit` opens it straight away in a
browser). It refuses to open above walking speed and closes itself — saving — if
the car pulls away while it is open.

- drag a widget to move it; drop it on another widget to swap the two, each
  taking the other's position and size
- drag the bottom-right corner to resize
- tap to select: Settings, Duplicate, Remove
- Settings > Type turns a widget into another one in place; what the new type
  can't use falls back to its defaults and returns on switching back
- **+ Add** lists every registered widget with its preview
- **Done** saves through the host, **Cancel** restores the layout as it was

Widgets never overlap: a move or resize that would collide is refused (a drop
onto another widget swaps their areas instead), and a new widget goes into the
first free space. The default layout fills the grid, so
adding one means removing one first.

The settings panel is generated from the widget descriptor — bind pickers list
every signal of an accepted kind, grouped by category, and each prop becomes the
control its type implies. A new widget folder is fully editable with no editor
changes.

Each orientation has its own layout: editing landscape leaves portrait alone.

## Adding a widget

Create `widgets/<type>/` with:

| file | |
|---|---|
| `descriptor.json` | type (= folder name), renderer, name, sizes, binds, props |
| `widget.js` | defines `function create(ctx)` returning `{ update(values), destroy() }` |
| `widget.css` | optional; scope rules under `.w-<type>` |
| `preview.svg` | palette thumbnail |

Then run `node scripts/build-compose-widgets.js`. Nothing else needs editing:
not `index.html`, not the registry, not the native apps.

`ctx` gives the widget:

- `el`: its root element, inside a size container (`@container` / `cq*` units work)
- `props`: descriptor defaults merged with what the layout stored
- `label(key)`, `unit(key)`, `format(key, value, precision)`, `range(key)` for each bind
- `t(key)`: UI strings

`update(values)` receives `{ <bindKey>: value }`, `undefined` meaning no data.
Write to the DOM only when the visible result changes: the perf gate fails
per-tick loops over siblings.

Descriptor rules the build script enforces:

- every prop has a `default`, so adding a prop never breaks an existing layout
- bind defaults must be known signals of an accepted kind
- names are `{ "en": ..., "hu": ..., "de": ... }`; `en` is required

## Compatibility

A stored layout outlives app versions in both directions:

- unknown widget type: kept and rendered as a placeholder
- unknown props / binds: kept untouched
- newer `schemaVersion`: not interpreted; the default layout is shown and the
  stored document is not overwritten
- a widget that throws is disabled in its own cell; the others keep running

Changing the document format means bumping `SCHEMA_VERSION` in `core/layout.js`
and appending a migration step. Never edit an existing step.

## Host contract

Data in: the same as every other web dash-app (`NativeCarState` +
`onCarStateUpdate()` on Android, `receiveMessage(json)` on iOS).

Optional host capabilities, with the fallback used until they exist:

| capability | Android | iOS | fallback |
|---|---|---|---|
| load layout | `NativeCarState.getComposeLayout()` | `window.__DASHPILOT_COMPOSE_LAYOUT__` injected before load | `localStorage` |
| save layout | `NativeCarState.saveComposeLayout(json)` | `webkit.messageHandlers.composeLayout` | `localStorage` |
| editor open | `NativeCarState.setComposeEditing(bool)` | `webkit.messageHandlers.composeEditing` | ignored |
| app language | `NativeCarState.getLocale()` | `locale` field in the pushed JSON | `navigator.language` |

The editor-open flag matters: both apps switch dashboards on a horizontal drag
across the dashboard, which would otherwise fire while a widget is being dragged
or resized. Both platforms suspend that carousel while the flag is raised, and
release it on Done, on Cancel, when the car pulls away, and when the view goes away.

Both platforms implement the layout capability (Android: `SharedPreferences`,
iOS: `UserDefaults`). The `localStorage` fallback is for browsers only — a
WKWebView served over a custom scheme has an opaque origin, where it throws.
The language capability is not implemented yet; `navigator.language` is used.

Units: like the other dash-apps, the host sends speed in the display unit and
converts odometer / temperatures / speed limit itself. The app only labels them.

## Tests

```
node scripts/build-compose-widgets.js --check          # descriptors valid, generated files current
cd dash-apps/_perf-gate && npm ci && node gate.js ../web-compose
```

Both run in CI (`.github/workflows/dashapp-perf.yml`).
