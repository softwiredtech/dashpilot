# Adding a new dash-app

This is the checklist for shipping a new web dashboard named `mytheme`. Every
step below is required; skipping one produces a runtime warning or a build
failure in a place you will not be looking at first.

## 1. App directory

Create `dash-apps/web-mytheme/` with three files:

```
dash-apps/web-mytheme/
  index.html      # the dashboard
  manifest.json   # app declaration (see below)
  preview.jpg     # landscape preview screenshot (~2:1, e.g. 2280x1080)
```

Minimal `manifest.json`:

```json
{
  "$schema": "../../docs/dashapp-manifest.schema.json",
  "manifestVersion": 1,
  "id": "mytheme",
  "settings": []
}
```

- `id` must be lowercase, digits, and hyphens only (`^[a-z0-9-]+$`).
- `id` must match the folder suffix: `web-` + `id` = `web-mytheme`.
- `settings` entries must be chosen from the schema enum (see
  `docs/dashapp-manifest.schema.json`).
- `preview.jpg` is required by both sync scripts. Either missing file fails the
  script.

Validate before committing:

```
node scripts/validate-manifests.js
```

CI runs the same script on every push (`android-build.yml`), so a bad manifest
fails the build either way — running it locally just fails faster.

## 2. Android sync list

`scripts/sync-to-android.sh` — add `web-mytheme` to the `DEFAULT_FOLDERS` array:

```diff
- DEFAULT_FOLDERS=(web-vanilla web-retro web-ambient web-analog)
+ DEFAULT_FOLDERS=(web-vanilla web-retro web-ambient web-analog web-mytheme)
```

`PREVIEW_FOLDERS` (next line) derives from `DEFAULT_FOLDERS`, so the preview
JPEG copy is automatic: the preview loop copies
`dash-apps/web-mytheme/preview.jpg` into
`dashpilot-android/app/src/main/res/drawable/preview_mytheme.jpg`.

## 3. Android registration

`dashpilot-android/app/src/main/java/com/softwiredtech/dashpilot/datamodel/dash/DashboardConfig.kt`
— add an entry to `availableDashboards`:

```kotlin
add(
    DashboardConfig(
        id = "mytheme",
        nameRes = R.string.dashboard_name_mytheme,
        url = "${LOCAL_ASSET_BASE_URL}web-mytheme/index.html",
        type = DashboardType.WEB,
        screenshotRes = R.drawable.preview_mytheme,
    )
)
```

The `id` must match the `id` field in `manifest.json`.

## 4. Android string resource

`dashpilot-android/app/src/main/res/values/strings.xml` — add the display name:

```xml
<string name="dashboard_name_mytheme">My Theme</string>
```

The `name` attribute must match the `R.string.dashboard_name_mytheme` reference
used in step 3.

## 5. iOS sync list

`scripts/sync-to-ios.sh` — add `web-mytheme` to `DEFAULT_FOLDERS`:

```diff
- DEFAULT_FOLDERS=(web-vanilla web-retro web-ambient web-analog)
+ DEFAULT_FOLDERS=(web-vanilla web-retro web-ambient web-analog web-mytheme)
```

The preview loop copies `preview.jpg` into
`preview_mytheme.imageset/preview_mytheme.jpg` — the imageset directory must
pre-exist (see step 7).

## 6. iOS registration

`dashpilot-ios/dashpilot/Models/DashboardConfig.swift` — add an entry to
`availableDashboards`:

```swift
DashboardConfig(
    id: "mytheme",
    name: "My Theme",
    url: "mytheme",
    type: .web,
    screenshotName: "preview_mytheme"
),
```

- `id` must match the `id` field in `manifest.json`.
- `url` is the folder name **without the `web-` prefix** — `WebDashView`
  resolves it to the bundled `web-<url>/` folder.

## 7. iOS imageset (Xcode asset catalog)

Create the imageset directory:

```
dashpilot-ios/dashpilot/Assets.xcassets/preview_mytheme.imageset/
  Contents.json
  preview_mytheme.jpg
```

`Contents.json` — copy from an existing imageset and update the filename:

```json
{
  "images" : [
    {
      "filename" : "preview_mytheme.jpg",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

The sync script copies `dash-apps/web-mytheme/preview.jpg` into this directory
(overwriting `preview_mytheme.jpg` on each run). The imageset **must pre-exist**
or `sync-to-ios.sh` fails.

## Performance gate

New web dash-apps are benchmarked by CI by default — no `perf` field (or
`"perf": { "gate": "standard" }`) means the app is gated the moment it exists.
Your `index.html` must define `window.onCarStateUpdate` and read values from
`window.NativeCarState`, and must pass the budgets in
`dash-apps/_perf-harness/lib/budgets.json`. See "Performance requirements" in
`dash-apps/README.md` for details, and run the identical check locally:

```
cd dash-apps/_perf-harness
npm ci && npx playwright install chromium
node run.js ../web-mytheme
```

## Local verification

Run both sync scripts to catch missing files or bad paths before CI sees them:

```
./scripts/sync-to-android.sh web-mytheme
./scripts/sync-to-ios.sh web-mytheme
```

Then build each platform to confirm the dashboard appears in the selector.

## App types

### Web app

Use `DashboardType.WEB` / `DashboardType.web`. Must have a `manifest.json` and
a three-file directory (step 1). This is the standard path.

### Rive app

Use `DashboardType.RIVE` / `DashboardType.rive`. Does **not** use a manifest.
Place a `.riv` file and `preview.jpg` in `dash-apps/rive/`. Add the app to the
registration lists (steps 3/6), skipping only the `DEFAULT_FOLDERS` sync lists:

- The Android string resource (step 4) is **still required** — `nameRes` is
  mandatory on `DashboardConfig`.
- The Android preview copies automatically (`rive` is in `PREVIEW_FOLDERS`,
  producing `preview_rive.jpg`). There is no iOS rive preview sync — create the
  imageset (step 7) and copy the JPEG in manually.

No production Rive app is registered today (only the debug-build `dev_rive`
loader), so treat this path as unexercised and expect to fill gaps.

## Notes

- `web-expo` is a special case: it is not registered on either platform and is
  excluded from both `DEFAULT_FOLDERS` lists, because it needs a build step
  (`npx expo export --platform web`) before it can be served. Only its preview
  ships, via `PREVIEW_FOLDERS` on Android. A normal production app goes in both
  `DEFAULT_FOLDERS` lists.
- Once `docs/dashapp-contract.md` exists, it documents the bridge API your
  `index.html` can call.
