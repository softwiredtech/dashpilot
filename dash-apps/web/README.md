# adasviz

A 3D ADAS visualization engine built with Bevy, compiled to WebAssembly.

---

## Integration

The engine reads data from a global variable you set on `window`:

```js
window.__ADAS_DATA_JSON__ = JSON.stringify(worldData);
```

You must update this variable every time you want the scene to change — the engine polls it on every render frame.

> **Important:** `window.__ADAS_DATA_JSON__` must contain the **world data object directly** — not wrapped in a message envelope. 

### Minimal example

```js
function sendFrame(data) {
  window.__ADAS_DATA_JSON__ = JSON.stringify({
    ego_speed: 15.0,              // m/s
    ego_steering_angle: 0.0,      // radians
    adas_on: true,                // toggles lane fill overlay
    gear: 1,                      // 0 = drive, 1 = park, 2 = reverse
    lane: { ... },
    objects: [ ... ],
    traffic_lights: [ ... ],
    speed_limits: [ ... ],
    stop_signs: [ ... ],
    road_signs: [ ... ],
    pedestrians: [ ... ],
    events: [ ... ],              // optional, defaults to []
    stop_line_dist: null,         // optional
  });
}
```

---

## Coordinate System

All positions are **relative to the ego vehicle**, which is rendered at the origin. Units are **meters**.

- `dx` — longitudinal axis, positive = forward
- `dy` — lateral axis, positive = left

---

## Data Model Reference

### World data object

The object set on `window.__ADAS_DATA_JSON__` has the following fields:

```
ego_speed            f32            (required)  Ego vehicle speed in m/s — drives the lane animation
ego_steering_angle   f32            (required)  Steering wheel angle in radians
adas_on              bool           (required)  Enables/disables the lane fill overlay
gear                 u32            (required)  0 = reverse, 1 = drive
lane                 Lane           (required)  Lane geometry (polynomial + width)
objects              Vehicle[]      (required)  Surrounding vehicles
traffic_lights       TrafficLight[] (required)  Traffic lights
speed_limits         SpeedLimit[]   (required)  Speed limit signs
stop_signs           StopSign[]     (required)  Stop signs
road_signs           RoadSign[]     (required)  Directional road signs
pedestrians          Pedestrian[]   (required)  Pedestrians
events               Event[]        (optional)  ADAS warning events, defaults to []
stop_line_dist       f32 | null     (optional)  Distance to stop line in meters, null = hide
```

---

### `Lane`

Describes the detected lane using a **cubic polynomial**. The lateral offset from the center of the lane at longitudinal distance `x` is:

```
offset(x) = c0 + c1·x + c2·x² + c3·x³
```

Fields:

```
laneWidth    f32    Lane width in meters (e.g. 3.5)
viewRange    f32    How far ahead the lane extends in meters (e.g. 100.0)
lanePolyC0   f32    Constant offset (lateral offset at x=0)
lanePolyC1   f32    Linear term (heading offset)
lanePolyC2   f32    Quadratic term (curvature)
lanePolyC3   f32    Cubic term (curvature rate of change)
```

**Example — slight left curve:**

```json
"lane": {
  "laneWidth": 3.7,
  "viewRange": 80.0,
  "lanePolyC0": 0.0,
  "lanePolyC1": 0.0,
  "lanePolyC2": 0.0005,
  "lanePolyC3": 0.0
}
```

**Rendered elements:**
- Left and right lane boundary lines (0.1 m wide)
- Center fill overlay (visible when `adas_on: true`), animated at ego speed

---

### `Vehicle`

```
id              u32             Unique object ID — must be stable across frames
Dx              f32             Longitudinal distance (meters, forward)
Dy              f32             Lateral distance (meters, left)
VxRel           f32             Relative longitudinal velocity (m/s)
vehicle_class   VehicleClass    Object type (see below)
heading         f32             Rotation in radians
```

**`VehicleClass` values:**

```
"Car"          →  Audi sedan
"Truck"        →  Truck
"Motorcycle"   →  Motorcycle
"Bicycle"      →  BMX bike
"Pedestrian"   →  Human figure
"Ipso"         →  Bus
"Unknown"      →  Generic box
"EgoCar"       →  Tesla Model 3
```

> **Note:** IDs must remain stable across frames. Objects not seen for **2 seconds** are automatically removed from the scene.

**Example:**

```json
"objects": [
  { "id": 42, "Dx": 25.0, "Dy": -1.5, "VxRel": -3.0, "vehicle_class": "Car", "heading": 0.0 },
  { "id": 43, "Dx": 60.0, "Dy": 0.0,  "VxRel": 0.0,  "vehicle_class": "Truck", "heading": 0.05 }
]
```

---

### `TrafficLight`

```
id       u32                 Unique ID
Dx       f32                 Longitudinal distance (meters)
Dy       f32                 Lateral distance (meters)
state    TrafficLightState   Light color: "Red", "Yellow", or "Green"
```

**Example:**

```json
"traffic_lights": [
  { "id": 1, "Dx": 45.0, "Dy": 0.0, "state": "Red" }
]
```

---

### `SpeedLimit`

```
id            u32   Unique ID
Dx            f32   Longitudinal distance (meters)
Dy            f32   Lateral distance (meters)
speed_limit   u32   Speed limit value (km/h)
```

**Example:**

```json
"speed_limits": [
  { "id": 10, "Dx": 80.0, "Dy": 3.0, "speed_limit": 50 }
]
```

---

### `StopSign`

```
id   u32   Unique ID
Dx   f32   Longitudinal distance (meters)
Dy   f32   Lateral distance (meters)
```

**Example:**

```json
"stop_signs": [
  { "id": 20, "Dx": 35.0, "Dy": 2.0 }
]
```

---

### `RoadSign`

Directional overhead sign with an arrow (e.g. turn guidance).

```
id          u32            Unique ID
Dx          f32            Longitudinal distance (meters)
Dy          f32            Lateral distance (meters)
sign_type   RoadSignType   Sign category
heading     f32            Rotation in radians (defaults to 0.0)
```

**`RoadSignType` values:**

```
"TurnRight"   →  3.0 × 5.0 m
"TurnLeft"    →  3.0 × 5.0 m
"Straight"    →  2.5 × 6.0 m
"Stop"        →  3.0 × 1.5 m
```

**Example:**

```json
"road_signs": [
  { "id": 30, "Dx": 70.0, "Dy": 0.0, "sign_type": "TurnRight", "heading": 0.0 }
]
```

---

### `Pedestrian`

```
id        u32   Unique ID
Dx        f32   Longitudinal distance (meters)
Dy        f32   Lateral distance (meters)
heading   f32   Facing direction in radians
```

**Example:**

```json
"pedestrians": [
  { "id": 100, "Dx": 20.0, "Dy": 4.0, "heading": 1.57 }
]
```

### `stop_line_dist`

A single number (or `null`) representing the distance in meters to an upcoming stop line.

- Valid range: `0.0` – `127.0` meters
- A white line is drawn across the lane at the given distance
- Set to `null` or omit to hide the stop line

**Example:**

```json
"stop_line_dist": 18.5
```

---

## Complete Example

```js
window.__ADAS_DATA_JSON__ = JSON.stringify({
  "ego_speed": 13.9,
  "ego_steering_angle": 0.03,
  "adas_on": true,
  "gear": 1,
  "lane": {
    "laneWidth": 3.6,
    "viewRange": 100.0,
    "lanePolyC0": 0.0,
    "lanePolyC1": 0.0,
    "lanePolyC2": 0.0003,
    "lanePolyC3": 0.0
  },
  "objects": [
    { "id": 1, "Dx": 20.0, "Dy": 0.0,  "VxRel": -2.0, "vehicle_class": "Car",   "heading": 0.0 },
    { "id": 2, "Dx": 55.0, "Dy": -3.8, "VxRel": 1.0,  "vehicle_class": "Truck", "heading": 0.0 }
  ],
  "traffic_lights": [
    { "id": 10, "Dx": 90.0, "Dy": 0.0, "state": "Green" }
  ],
  "speed_limits": [
    { "id": 20, "Dx": 120.0, "Dy": 3.0, "speed_limit": 70 }
  ],
  "stop_signs": [],
  "road_signs": [],
  "pedestrians": [
    { "id": 50, "Dx": 25.0, "Dy": 5.0, "heading": 0.0 }
  ],
  "events": [
    { "event_type": "CollisionWarning", "target_id": 1, "severity": 0.7 }
  ],
  "stop_line_dist": null
});
```

---

## Object Lifecycle

Objects are tracked by `id` across frames. If an object disappears from the data, it is removed after a timeout:

```
Vehicle         2.0 s
Pedestrian      1.0 s
Traffic light   2.0 s
Lane            1.0 s
Stop sign       1.0 s
Road sign       1.0 s
Stop line       2.0 s
```

The engine supports up to **20 vehicles** simultaneously.

---

## ADAS Toggle

Setting `adas_on: false` hides the lane fill overlay but keeps lane boundary lines visible. All other objects remain rendered regardless of this flag.

---

## Shaders

The engine uses three custom WGSL shaders. They are purely data-driven — you don't call them directly.

### `lane_material.wgsl` — Lane boundary lines

Always active when `laneWidth > 0` and `viewRange > 0`. Renders dashed lane lines that scroll backward at `ego_speed`, creating the sensation of forward movement. At `ego_speed = 0` the lines appear static. Antialiased with screen-space derivatives.

### `lane_fill_material.wgsl` — Lane fill overlay

Active only when `adas_on: true`. Renders a green semi-transparent fill (`RGB 0.2, 0.9, 0.6`, alpha `0.6`) between the lane boundaries with three layered effects: a rounded shape that fades out at ~50 m, a sinusoidal brightness pulse traveling forward, and scrolling arrow chevrons pointing in the direction of travel.

### `proximity_indicator.wgsl` — ADAS warning arcs

Active when `events` are present. Renders a circular disk around the ego vehicle with directional arcs pointing toward threatening vehicles (up to **16 targets**). Each target gets a directional fade, concentric ripple rings that compress with proximity, and a distance-based intensity falloff (hidden beyond 90% of display range).

---