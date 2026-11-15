# Halo

**Category:** Rive  
**Author:** [Dillon Blair (@dillonblair)](https://github.com/dillonblair)  
**File:** `Halo.riv` — 2,743,483 bytes (2.74 MB)

Halo combines the depth of a physical instrument cluster with clear digital information. Recessed glass, metallic bezels and layered lighting frame a large, always-white speed display. Its illuminated rings communicate vehicle state: blue for engaged driver assistance, green for turn signals and red for blind-spot warnings. This entry is the original Halo design, built as a signed, self-contained Rive dashboard on DashPilot's `DashViewModel`.

## Features

- Large white central speed reading and a sweeping, segmented speed arc. The 0–9 scale represents 0–90 MPH or 0–180 KM/H, not engine RPM. Amber begins at seven and progresses toward red through eight and nine.
- A blue perimeter and speed outline indicate engaged driver assistance through the official `adasOn` flag.
- Green left/right sections of the center ring follow the vehicle's turn-signal phases; hazards illuminate both sides.
- Independent red blind-spot perimeter halves flash once on arrival, then remain illuminated until that side clears.
- A posted-limit sign briefly enlarges on reaching the limit and gains a restrained red outline in five-MPH excess intervals.
- Battery charge, pack temperature, power, gear, clock and odometer. Unknown readings use a dash where the host's defaults allow that distinction.
- A green-light reminder when an observed red-to-green transition is followed by two seconds stopped in Drive, where traffic-light data is available.
- Automatic 0–60 MPH timing. The latest completed result appears in green beside the clock in Drive or Park.
- Six palettes selected by horizontal swipe, rendered dimming by vertical drag and Settings by a roughly 650 ms hold. The gesture guide is replayed manually.
- A landscape phone-first layout with manual notch-side controls. Optional manual display profiles and an iPad aspect-ratio preview are included; this is not automatic hardware detection.

## Run in DashPilot

1. Save `Halo.riv` to the phone.
2. Select **Demo** as the data source.
3. Open **Settings → Developer → Load Rive File** and select `Halo.riv`.
4. Use landscape orientation. Hold the dashboard for Settings and select the appropriate notch side.
5. For live driving data, select the connected vehicle source in DashPilot.

All visual/font assets are embedded; the dashboard makes no network requests. It consumes telemetry supplied by DashPilot and does not connect to or control the vehicle itself. Preferences and launch results are session-only. Dimming affects the rendered dashboard, not the phone's hardware brightness. Local Rive exports retain a Rive watermark.

## Implementation and source

Main artboard: `Halo`. Default state machine: `Cluster`. Default view model: `DashViewModel`.

The material face and center glass are embedded images. Readings, segments, signals and warning lighting are native Rive elements driven by embedded scripts. The center-glass shader in the archive’s `material-source/` was used to render an image; no live per-frame WGSL finish pass is shipped in this entry. The preview is a native Rive tooling capture with sample telemetry, not a DashPilot screen recording.

Editable RML, Luau, generator code, fonts and material assets are in `Halo-source.zip`. Extract it to obtain `source/` and `material-source/`; see `source/README.md` for rebuilding and native checks. Physical-device frame rate is not claimed.

## Assets and credits

Original code, vector geometry and project-created material artwork are contributed under the **MIT license**, included as `LICENSE`. Font software retains its own **SIL Open Font License 1.1**, with notices included below; it is not relicensed as MIT.

| Asset | Source / creator | License |
| --- | --- | --- |
| Instrument geometry, ticks, turn-signal rings, warning halos, sign graphics and UI | Halo project, Dillon Blair; developed with ChatGPT coding assistance | MIT (`LICENSE`) |
| `source/plate.png` (embedded material plate) | AI-assisted material artwork created during Halo's design process with OpenAI ChatGPT image generation, using the project's dashboard mockups as references | Contributed with this entry under MIT (`LICENSE`) |
| `source/center-depth.png` (embedded center-glass material) | Rendered from the Halo WGSL/Luau authoring project in `material-source/` | MIT (`LICENSE`) |
| `source/Cluster.ttf` — Michroma | The Michroma Project Authors; [source](https://github.com/googlefonts/Michroma-font) | SIL OFL 1.1 (`Michroma-OFL.txt`) |
| `source/Body.ttf` — Barlow | The Barlow Project Authors; [source](https://github.com/jpt/barlow) | SIL OFL 1.1 (`Barlow-OFL.txt`) |
| `preview.jpg` | Native Rive screenshot of this dashboard with sample telemetry | MIT (`LICENSE`), subject to the embedded font notices |

AI tools assisted the code and material artwork; the design direction, feature decisions and iterative evaluation were led by Dillon Blair. No external image/font downloads, audio files, 3D car models or model assets are required at runtime.

Submitted file SHA-256: `3d036a237c6d100337fa67ac5719a7895b229cb2489ed3cbb16519a43833979d`.
