# Vanilla v2

**Category:** web

Vanilla v2 rebuilds the Vanilla dashboard around a real-time 3D scene written in plain WebGL 2, with no libraries and no build step. Your Tesla (a legacy or 2025 Highland Model 3, or a legacy or 2025 Juniper Model Y; long-press anywhere to switch, tap the car to change its paint) drives a road that curves with the steering wheel. The car leans and steers its front wheels, and its wheels spin with your speed. The scene reacts to the car's data:

- **Blind spots:** cars slide into the next lane when a blind spot is flagged. They're a random Model 3, Model Y (legacy or 2025) or Cybertruck, and turn red when the warning is urgent.
- **Driver assist:** a green highlight with forward-flowing chevrons fills your lane while assist is on.
- **Traffic lights:** a lit signal, with its stop line, appears at the distance the car reports.
- **Park:** the camera swings to a front three-quarter studio view with a soft floor reflection.
- **Reverse:** the camera switches to a top-down view.
- **Dark mode:** turns the scene to night with headlight beams on the road.

Around the 3D scene sits Vanilla's instrument layer: speed, gear, power/regen bar, turn signals, cruise set speed, speed limit, battery and odometer. Open `index.html?demo=cycle` in a browser for a 34-second loop that runs through all of it: it starts in Park showing each of the cars, then cycles through the paints while driving.

## Credits

| Asset | Source | License |
|---|---|---|
| `model_y.glb` (Tesla Model Y) | Adapted for this dashboard from a third-party OBJ model: converted to glTF, interior and hidden geometry removed, materials and tail lights reworked. | |
| `model_3.glb` (Tesla Model 3) | Adapted for this dashboard from a third-party FBX model: converted to glTF, interior removed, glass made opaque, underbody panels added in code. | |
| `model_3_2025.glb` (2025 Tesla Model 3 "Highland") | Adapted for this dashboard from a third-party OBJ model: converted to glTF, converted to the dashboard's axes, size and node layout (body plus four wheels), triangle strips turned into triangles, glass made opaque, materials reworked. | |
| `model_y_2025.glb` (2025 Tesla Model Y "Juniper") | ["2025 Tesla Model Y"](https://sketchfab.com/3d-models/2025-tesla-model-y-619601e7800d418da5922c4fa7833f74) by [BloxBloger](https://sketchfab.com/BloxBloger). Changes: converted to the dashboard's axes and node layout (body plus four wheels), glass made opaque, materials and lights reworked. | [CC BY-NC 4.0](http://creativecommons.org/licenses/by-nc/4.0/) (non-commercial) |
| `cybertruck.glb` (Tesla Cybertruck) | ["Tesla Cybertruck"](https://sketchfab.com/3d-models/tesla-cybertruck-3cce737ec2ed4850aa921e92d6853143) by [VTX_car](https://sketchfab.com/VTX_car), adapted for this dashboard: interior removed, glass and materials reworked. This adapted file is shared under the same license. | [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) (non-commercial, share-alike) |
| `fonts/inter-*.ttf` (Inter) | [Inter](https://rsms.me/inter/) by Rasmus Andersson, copied from `web-vanilla` | SIL Open Font License 1.1 |
| `left.png`, `right.png`, `seat-belt.png`, `car-door.svg`, `steering-wheel.svg`, `experimental.png` | The original Vanilla dashboard in this repo (`dash-apps/web-vanilla`) | This repo's MIT license |
| 3D scene, road, traffic light, studio floor, shaders | Written for this dashboard | This repo's MIT license |

"Tesla", "Model Y", "Model 3" and "Cybertruck" are trademarks of Tesla, Inc. This project is not affiliated with or endorsed by Tesla.
