#import bevy_pbr::forward_io::VertexOutput

const PI: f32 = 3.14159265359;

// Ripple strength (0 = no ripple, 1 = full ripple)
const RIPPLE_AMPLITUDE: f32 = 0.3;
// Ripple frequency (radians per unit distance)
const RIPPLE_FREQUENCY: f32 = 180.0;
// Ripple proximity shift (radians per unit of distance)
const RIPPLE_OFFSET: f32 = 10.0;

const dot_size: f32 = 0.5;
const max_display_distance: f32 = 0.9; // Only show targets within 90% of max range

@group(2) @binding(0) var<uniform> base_color: vec4<f32>;
@group(2) @binding(1) var<uniform> target_count: vec4<u32>;
@group(2) @binding(2) var<uniform> targets: array<vec4<f32>, 16>;

@fragment
fn fragment(mesh: VertexOutput) -> @location(0) vec4<f32> {
    // Normalize UV coordinates to the plane
    let uv = mesh.world_position.xz / 12.5;
    var color = vec4<f32>(0.0);

    // Visual parameters
    let leng = length(uv);

    //Skip outside radius
    if (leng > dot_size)
    {
        discard;
    }

    for (var i: u32 = 0u; i < target_count[0]; i = i + 1u)
    {
        // Get target data
        let t: vec4<f32> = targets[i];
        let normalized_distance: f32 = t.y; // 0.0 = close, 1.0 = far
        
        // Skip targets that are too far away
        if (normalized_distance > max_display_distance) {
            continue;
        }

        // Convert normalized angle to radians 
        let angle: f32 = t.x * 2.0 * PI;
        // Calculate direction vector for target position
        let dir: vec2<f32> = vec2<f32>(-cos(angle), sin(angle));
        
        // Arc direction fade with distance
        let arc: f32 = smoothstep(leng * normalized_distance / max_display_distance, leng, dot(uv, dir));
        // Ripple waves
        let ripple: f32 = cos(leng * RIPPLE_FREQUENCY - normalized_distance * RIPPLE_OFFSET * PI) * RIPPLE_AMPLITUDE + 1.0 - RIPPLE_AMPLITUDE;
        // Rim fade at the edges
        let rim: f32 = smoothstep(1.0, 0.9, leng / dot_size);
        // Fade out the target as it gets further away
        let fade: f32 = smoothstep(1.0, 0.5, normalized_distance / max_display_distance);
        color = max(color, base_color * vec4<f32>(1.0, 1.0, 1.0, fade * arc * ripple * rim));
    }
    return color;
}