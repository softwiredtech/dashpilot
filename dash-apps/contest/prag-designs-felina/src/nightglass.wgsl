// Felina finish pass, over the native instrument rendered to a texture.
// Units: p is in artboard points (1080 tall), so the layout numbers from felina_fx.luau line up.
struct U {
    a: vec4<f32>,   // w, h, time, renderQuality (1-3)
    b: vec4<f32>,   // speed, steer (deg, smoothed), over-limit (speed - limit, -1 = unknown), assist 0-1
    c: vec4<f32>,   // red, green, yellow (smoothed 0-1), regen 0-1
    d: vec4<f32>,   // left lamp 0-1, right lamp 0-1, seconds since left lit, seconds since right lit
    e: vec4<f32>,   // dial centre x, y, outer radius, hub radius
    f: vec4<f32>,   // battery arc centre x, y, radius, charge 0-1
    g: vec4<f32>,   // needle angle, trailing angle, limit angle (9 = none), seconds since start
    h: vec4<f32>,   // start-up fade 0-1 (the glass arrives with the dials), left satellite x, satellite y, satellite radius
};
@group(0) @binding(0) var fullTex: texture_2d<f32>;
@group(0) @binding(1) var smallTex: texture_2d<f32>;
@group(0) @binding(2) var samp: sampler;
@group(0) @binding(3) var<uniform> u: U;

struct VOut { @builtin(position) pos: vec4<f32>, @location(0) uv: vec2<f32> };

@vertex
fn vs(@builtin(vertex_index) i: u32) -> VOut {
    var p = array<vec2<f32>, 3>(vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0));
    var o: VOut;
    o.pos = vec4<f32>(p[i], 0.0, 1.0);
    o.uv = vec2<f32>(p[i].x * 0.5 + 0.5, 1.0 - (p[i].y * 0.5 + 0.5));
    return o;
}

fn hash(p: vec2<f32>) -> f32 {
    var p3 = fract(vec3<f32>(p.xyx) * 0.1031);
    p3 = p3 + dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

fn tap(uv: vec2<f32>) -> vec3<f32> {
    return textureSampleLevel(fullTex, samp, clamp(uv, vec2<f32>(0.0), vec2<f32>(1.0)), 0.0).rgb;
}

// soft wide blur of the quarter-size copy: the light that spills out of bright things
fn bloom(uv: vec2<f32>, res: vec2<f32>, k: f32) -> vec3<f32> {
    let px = 4.0 / res;
    var c = vec3<f32>(0.0);
    var wsum = 0.0;
    for (var j = -3; j <= 3; j = j + 1) {
        for (var i = -3; i <= 3; i = i + 1) {
            let o = vec2<f32>(f32(i), f32(j));
            let w = exp(-dot(o, o) / 6.0);
            let s = textureSampleLevel(smallTex, samp, uv + o * px * k, 0.0).rgb;
            let l = max(s.r, max(s.g, s.b));
            let knee = clamp((l - 0.42) / 0.30, 0.0, 1.0);          // soft knee: only real highlights glow
            c = c + s * knee * knee * w;
            wsum = wsum + w;
        }
    }
    return c / wsum;
}

fn vnoise(p: vec2<f32>) -> f32 {
    let i = floor(p);
    let f = fract(p);
    let w = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2<f32>(1.0, 0.0)), w.x),
               mix(hash(i + vec2<f32>(0.0, 1.0)), hash(i + vec2<f32>(1.0, 1.0)), w.x), w.y);
}

// angle of a point around the dial: 0 at 12 o'clock, clockwise positive, same as the needle
fn dialAngle(d: vec2<f32>) -> f32 { return atan2(d.x, -d.y); }

fn ring(r: f32, at: f32, width: f32) -> f32 {
    let x = (r - at) / width;
    return exp(-x * x);
}

@fragment
fn fs(in: VOut) -> @location(0) vec4<f32> {
    let res = u.a.xy;
    let t = u.a.z;
    let q = u.a.w;
    let uv = in.uv;
    let p = uv * res;

    let c = u.e.xy;
    let R = u.e.z;
    let hub = u.e.w;
    let d = p - c;
    let r = length(d);

    let a = dialAngle(d);
    let needleA = u.g.x;
    let trailA = u.g.y;

    // --- over the limit, hot air rises off the scale between the limit mark and the needle
    let over = clamp(u.b.z / 20.0, 0.0, 1.0);
    let lo = min(u.g.z, needleA);
    let hi = max(u.g.z, needleA);
    let heatArc = smoothstep(lo - 0.04, lo + 0.04, a) * (1.0 - smoothstep(hi - 0.02, hi + 0.06, a)) * step(u.g.z, 8.0);
    let heatBand = exp(-pow((r - (R - 70.0)) / 75.0, 2.0));
    let heat = over * heatArc * heatBand * step(0.0, u.b.z);
    let hn = vec2<f32>(vnoise(p * 0.035 + vec2<f32>(0.0, t * 2.2)), vnoise(p * 0.035 + vec2<f32>(17.0, t * 2.6)));
    let shimmer = (hn - 0.5) * heat * 4.0;
    let ps = p + shimmer;
    let uvs = ps / res;

    // the speed stays crisp: no lens, straight sample
    var col = tap(uvs);

    // --- bloom: two radii, warm
    if (q > 1.5) {
        let b = bloom(uv, res, 0.8) * 0.35 + bloom(uv, res, 2.0) * 0.30 + bloom(uv, res, 4.5) * 0.20;
        col = col + b * vec3<f32>(1.04, 0.93, 0.82);
    }

    // --- dial glass: fresnel at the rim and a reflection that slides as the car steers
    let inDial = 1.0 - smoothstep(R - 10.0, R, r);
    let fres = smoothstep(R * 0.78, R, r) * inDial;
    let glass = u.h.x;
    col = col + vec3<f32>(0.9, 0.85, 0.8) * fres * 0.045 * glass;
    let streak = 0.0;
    // hub glass: a soft specular from the top left
    let hs = d - vec2<f32>(-0.32, -0.42) * hub;
    let inHub = 1.0 - smoothstep(hub - 3.0, hub, r);
    col = col + vec3<f32>(1.0) * exp(-dot(hs, hs) / (2.0 * pow(hub * 0.22, 2.0))) * 0.035 * inHub * glass;

    // --- the satellites are glass too: fresnel at the rim and a soft specular
    for (var sidx = 0; sidx < 2; sidx = sidx + 1) {
        let sx = select(u.h.y, res.x - u.h.y, sidx == 1);
        let sd = p - vec2<f32>(sx, u.h.z);
        let sr = length(sd);
        let sIn = 1.0 - smoothstep(u.h.w - 22.0, u.h.w - 12.0, sr);
        col = col + vec3<f32>(0.9, 0.85, 0.8) * smoothstep(u.h.w * 0.72, u.h.w - 14.0, sr) * sIn * 0.035 * glass;
        let sh = sd - vec2<f32>(-0.34, -0.46) * u.h.w;
        col = col + vec3<f32>(1.0) * exp(-dot(sh, sh) / (2.0 * pow(u.h.w * 0.20, 2.0))) * 0.045 * sIn * glass;
    }

    // --- the traffic light floods the glass from above
    let tint = u.c.x * vec3<f32>(1.0, 0.16, 0.08) + u.c.y * vec3<f32>(0.20, 1.0, 0.45) + u.c.z * vec3<f32>(1.0, 0.72, 0.15);
    col = col + tint * ring(r, R - 2.0, 9.0) * 0.22;

    // --- blinkers: when the real lamp lights, amber light sweeps in from that side of the screen
    // blinkers: when the real lamp lights, a comet of amber light runs from 12 o'clock down the bezel on that side
    let amber = vec3<f32>(1.0, 0.60, 0.12);
    let bez = ring(r, R - 2.0, 10.0) + ring(r, R + 4.0, 28.0) * 0.4;
    let lHead = -min(u.d.z / 0.30, 1.0) * 2.9;               // runs counter-clockwise, left
    let lTail = clamp((a - lHead) / 1.2, 0.0, 1.0) * step(a, 0.0) * step(lHead, a);
    col = col + amber * bez * (1.0 - lTail) * step(a, 0.02) * step(lHead - 0.02, a) * exp(-u.d.z * 2.4) * 0.45;
    let rHead = min(u.d.w / 0.30, 1.0) * 2.9;                 // clockwise, right
    let rTail = clamp((rHead - a) / 1.2, 0.0, 1.0) * step(0.0, a) * step(a, rHead);
    col = col + amber * bez * (1.0 - rTail) * step(-0.02, a) * step(a, rHead + 0.02) * exp(-u.d.w * 2.4) * 0.45;
    col = col + amber * (u.d.x * exp(-p.x / 120.0) + u.d.y * exp(-(res.x - p.x) / 120.0)) * 0.08;

    // --- regen: energy streams along the battery arc into the charge
    let bd = p - u.f.xy;
    let br = length(bd);
    let ba = atan2(bd.x, -bd.y);                         // 0 at the top, -pi/2 left, +pi/2 right
    let along = (ba + 1.3089969) / 2.6179939;            // battery arc sweeps -75..+75 deg
    let onArc = step(-1.3089969, ba) * step(ba, 1.3089969) * step(along, u.f.w);
    let arcBand = ring(br, u.f.z, 7.0);
    let green = vec3<f32>(0.30, 1.0, 0.55);
    var sparks = 0.0;
    for (var k = 0; k < 3; k = k + 1) {
        let lane = f32(k);
        let s = along * 34.0 + lane * 0.37 - t * (1.6 + lane * 0.5);
        let cell = floor(s);
        let jit = (hash(vec2<f32>(cell, lane)) - 0.5) * 12.0;
        let fx = fract(s) - 0.5;
        sparks = sparks + exp(-fx * fx * 90.0) * exp(-pow((br - u.f.z - jit) / 3.2, 2.0)) * step(0.55, hash(vec2<f32>(cell, lane + 7.0)));
    }
    col = col + green * u.c.w * onArc * (sparks * 0.8 + arcBand * 0.15);

    // --- assist: a cool light rises on the hub rim
    col = col + vec3<f32>(0.35, 0.62, 1.0) * u.b.w * ring(r, hub, 7.0) * 0.12;

    // --- over the limit: the heat glows, flickering as it rises
    col = col + vec3<f32>(1.0, 0.25, 0.08) * heat * 0.10;

    // --- needle light trail: a fading wedge between where the needle was and where it is
    let span = needleA - trailA;
    if (abs(span) > 0.004) {
        let w = clamp((a - trailA) / span, 0.0, 1.0);
        let inSpan = step(0.0, (a - trailA) * sign(span)) * step(0.0, (needleA - a) * sign(span));
        let band = smoothstep(hub + 4.0, hub + 30.0, r) * (1.0 - smoothstep(R - 50.0, R - 22.0, r));
        let strength = clamp(abs(span) * 2.5, 0.0, 1.0);
        col = col + vec3<f32>(1.0, 0.48, 0.12) * inSpan * band * pow(w, 2.2) * strength * 0.55;
    }


    // --- finish: vignette and a little grain, so the black reads as glass, not void
    let vq = (uv - 0.5) * vec2<f32>(1.7, 1.0);
    col = col * mix(0.86, 1.0, smoothstep(1.3, 0.4, length(vq)));
    return vec4<f32>(max(col, vec3<f32>(0.0)), 1.0);
}
