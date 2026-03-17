#import bevy_pbr::forward_io::VertexOutput
#import bevy_pbr::mesh_view_bindings::globals

@group(2) @binding(0) var<uniform> material_color: vec4<f32>;
@group(2) @binding(1) var<uniform> params: vec4<f32>;
// x = emissive_strength, y = animation_speed, z = phase_offset

const SOFTNESS: f32 = 1.0;
const PULSE_AMPLITUDE: f32 = 0.5;
const PULSE_FREQUENCY: f32 = 1.0;

@fragment
fn fragment(
    mesh: VertexOutput,
) -> @location(0) vec4<f32> {
    let emissive_strength = params.x;
    let animation_speed = params.y;
    let phase_offset = params.z;
    
    var base_color = material_color;
    base_color = base_color * (1.0 + emissive_strength);

    let phase = mesh.world_position.z * PULSE_FREQUENCY 
                - (globals.time * animation_speed + phase_offset);
    
    let d = sin(phase);
    let dxy = vec2<f32>(dpdx(d), dpdy(d));
    let aa = smoothstep(-SOFTNESS, SOFTNESS, d / max(length(dxy), 0.0001));
    
    base_color.a = base_color.a * aa;
    
    return base_color;
}