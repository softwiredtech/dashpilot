#import bevy_pbr::forward_io::VertexOutput
#import bevy_pbr::mesh_view_bindings::globals

@group(2) @binding(0) var<uniform> material_color: vec4<f32>;
@group(2) @binding(1) var<uniform> params: vec4<f32>;  // x = alpha

// End lane distance
const FAR: f32 = 50.0;
// Lane width
const WIDTH: f32 = 1.8;
// Lane depth offset
const DEPTH: f32 = 3.5;
// Lane corner roundness
const ROUND: f32 = 0.5;
// Softness of the edges
const SOFTNESS: f32 = 1.0;

// Pulse animation parameters
const PULSE_SPEED: f32 = 2.0;
const PULSE_AMPLITUDE: f32 = 0.3;
const PULSE_FREQUENCY: f32 = 0.2;

// Arrow point slope (0 = flat)
const ARROW_SLOPE: f32 = 0.5;
// Arrow scroll speed (radians per second)
const ARROW_SPEED: f32 = 4.0;
// Arrow frequency (cycles per unit distance)
const ARROW_FREQUENCY: f32 = 1.0;
// Arrow thickness (0 = no arrow, 2 = full arror)
const ARROW_THICKNESS: f32 = 0.2;
// Between arrow opacity (0 = transparent, 1 = opaque)
const ARROW_OPACITY: f32 = 0.5;

fn antialias(d: f32) -> f32 {
    let dxy = vec2<f32>(dpdx(d), dpdy(d));
    return smoothstep(-SOFTNESS, SOFTNESS, d / max(length(dxy), 0.0001));
}

@fragment
fn fragment(
    mesh: VertexOutput,
) -> @location(0) vec4<f32> {
    
    // XY distance to lane corners
    let corner = ROUND + vec2<f32>(abs(mesh.world_position.x) - WIDTH, -DEPTH - mesh.world_position.z);
    // Pulse waves
    let wave = sin(corner.y * PULSE_FREQUENCY - globals.time * PULSE_SPEED);
    let pulse = wave * PULSE_AMPLITUDE + 1.0 - PULSE_AMPLITUDE;

    let arrow_d = ARROW_THICKNESS - 1.0 - sin((corner.y - ARROW_SLOPE*abs(mesh.world_position.x) - globals.time * ARROW_SPEED) * ARROW_FREQUENCY);
    let arrow = mix(ARROW_OPACITY, 1.0, antialias(arrow_d));
    
    var color = material_color;

    // Distance to lane edges
    let d = ROUND - (length(max(corner, vec2<f32>(0.0))) + min(max(corner.x, corner.y), 0.0));
    let aa = antialias(d);

    // Blend with pulse and antialiasing
    color.a = color.a * aa * pulse * arrow * smoothstep(-FAR, 0.0, corner.y);
    return color;
}