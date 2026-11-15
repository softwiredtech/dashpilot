// v3 finish pass, over the native instrument rendered to a texture.
// p is in artboard points (1080 tall). The bar geometry is the same as v3.py's, in target-2 pixels:
// tx = p.x / K, ty = (p.y - OY) / K. Calm when driving; the start-up gets the show.
struct U {
    a: vec4<f32>,   // w, h, time, renderQuality (1-3)
    b: vec4<f32>,   // power fill 0-1 (incl. the start-up sweep), regen 0-1, redline heat 0-1, over the limit 0-1
    c: vec4<f32>,   // red, green, yellow (smoothed 0-1), seconds into the start-up (99 = done)
    d: vec4<f32>,   // left lamp, right lamp, sweep 0-1, light at the top of the sweep 0-1
    e: vec4<f32>,   // K, OY, glow centre x, y
    f: vec4<f32>,   // fit: design offset x, y on screen, design scale, -
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

fn tap(uv: vec2<f32>) -> vec3<f32> {
    return textureSampleLevel(fullTex, samp, clamp(uv, vec2<f32>(0.0), vec2<f32>(1.0)), 0.0).rgb;
}

fn bloom(uv: vec2<f32>, res: vec2<f32>, radius: f32, knee0: f32) -> vec3<f32> {
    var sum = vec3<f32>(0.0);
    var weight = 0.0;
    let duv = vec2<f32>(radius) / res;                 // radius in artboard points
    for (var j = -1; j <= 1; j = j + 1) {
        for (var i = -1; i <= 1; i = i + 1) {
            let o = vec2<f32>(f32(i), f32(j));
            let w = select(1.0, 2.0, i == 0) * select(1.0, 2.0, j == 0);
            let s = textureSampleLevel(smallTex, samp, uv + o * duv, 0.0).rgb;
            let hi = max(s.r, max(s.g, s.b));
            let lo = min(s.r, min(s.g, s.b));
            let chroma = (hi - lo) / max(hi, 0.001);
            let emitter = smoothstep(0.18, 0.45, chroma);   // coloured light blooms, white text does not
            let knee = smoothstep(knee0, knee0 + 0.25, hi);
            sum += s * emitter * knee * w;
            weight += w;
        }
    }
    return sum / weight;
}

fn sq(x: f32) -> f32 { return x * x; }

fn barArc(x: f32) -> f32 {
    let a = 144.0 / (821.5 * 821.5);
    let z = a * (x - 1083.5);
    return (z * sqrt(1.0 + z * z) + log(z + sqrt(1.0 + z * z))) / (2.0 * a);   // asinh, spelled out
}

fn barX(f: f32) -> f32 {
    let wanted = mix(barArc(262.0), barArc(1905.0), clamp(f, 0.0, 1.0));
    var x = mix(262.0, 1905.0, clamp(f, 0.0, 1.0));
    for (var i = 0; i < 3; i = i + 1) {
        let slope = 144.0 * (x - 1083.5) / (821.5 * 821.5);
        x = x - (barArc(x) - wanted) / sqrt(1.0 + slope * slope);
    }
    return clamp(x, 262.0, 1905.0);
}

fn baseY(x: f32) -> f32 {                          // the bar's baseline, target px
    let v = (x - 1083.5) / (1905.0 - 1083.5);
    return 136.0 + 72.0 * v * v;
}

@fragment
fn fs(in: VOut) -> @location(0) vec4<f32> {
    let res = u.a.xy;
    let t = u.a.z;
    let q = u.a.w;
    let uv = in.uv;
    let p = (uv * res - u.f.xy) / u.f.z;          // design points (2280 x 1080); the screen may be wider or narrower
    let K = u.e.x;
    let OY = u.e.y;
    let tp = vec2<f32>(p.x / K, (p.y - OY) / K);   // target-2 pixels
    let it = u.c.w;                                // seconds into the start-up
    // the glass, approximated: light from the start-up and the moments stays inside it
    let lv = pow(abs(tp.x - 1086.0) / 1062.0, 4.0) + pow(abs(tp.y - 356.5) / 280.0, 2.2);
    let inLens = 1.0 - smoothstep(0.82, 1.0, lv);

    let base = tap(uv);
    var col = base;

    // --- bloom: the glow, the dip and the fill spill light; at the top of the sweep, more
    if (q > 1.5) {
        var b = bloom(uv, res, 6.0, 0.40) * 0.18;
        if (q > 2.5) {
            b += bloom(uv, res, 18.0, 0.40) * 0.10;
        }
        col += b * inLens;
    }

    // --- the power bar
    let fillv = u.b.x;
    let hx = barX(fillv);
    let hy = baseY(hx) - 21.0;
    let hd = tp - vec2<f32>(hx, hy);
    let on = smoothstep(0.004, 0.02, fillv);
    // the head: a tight light along the bar's tangent, on the native sliver
    let slope = 144.0 * (hx - 1083.5) / (821.5 * 821.5);
    let tangent = normalize(vec2<f32>(1.0, slope));
    let normal = vec2<f32>(-tangent.y, tangent.x);
    let local = vec2<f32>(dot(hd, tangent), dot(hd, normal));
    let core = exp(-0.5 * (sq(local.x / 2.5) + sq(local.y / 11.0)));
    let halo = exp(-0.5 * (sq(local.x / 15.0) + sq(local.y / 20.0)));
    col += (vec3<f32>(1.0, 0.8353, 0.4863) * core * 0.32 + vec3<f32>(1.0, 0.3882, 0.0706) * halo * 0.08) * on * inLens;
    // redline heat: when the fill reaches 7-8 the red block glows, and a red haze rises off it
    let byv = baseY(clamp(tp.x, 1576.4, 1905.0));
    let heat = u.b.z;
    let above = max(byv - 42.0 - tp.y, 0.0);
    let redSoft = smoothstep(1564.4, 1592.4, tp.x) * (1.0 - smoothstep(1889.0, 1917.0, tp.x));
    let outside = 1.0 - smoothstep(byv - 46.0, byv - 40.0, tp.y);
    col += vec3<f32>(0.78, 0.06, 0.03) * redSoft * exp(-above / 18.0) * outside * heat * 0.06 * inLens;
    // regen: a cool light at the left end
    let rd = tp - vec2<f32>(230.0, baseY(230.0) - 21.0);
    col = col + vec3<f32>(0.35, 0.85, 0.78) * exp(-dot(rd, rd) / (2.0 * 50.0 * 50.0)) * u.b.y * 0.18 * inLens;

    let gc = u.e.zw;
    let gd = (p - gc) / vec2<f32>(1.0, 0.62);
    let gr = length(gd);

    // --- over the limit: the glow deepens to red
    col = col + vec3<f32>(0.55, 0.02, 0.02) * u.b.w * exp(-gr * gr / (2.0 * 300.0 * 300.0)) * 0.12 * inLens;

    // --- blinkers: amber light along the glass on that side, with the lamp
    let amber = vec3<f32>(1.0, 0.62, 0.12);
    col = col + amber * (u.d.x * exp(-p.x / 140.0) + u.d.y * exp(-(2280.0 - p.x) / 140.0)) * 0.22 * inLens;

    // --- finish: keep the native type and colour; soft-limit only the light this pass added
    let added = max(col - base, vec3<f32>(0.0));
    let light = added / (vec3<f32>(1.0) + added);
    let finished = base + (vec3<f32>(1.0) - clamp(base, vec3<f32>(0.0), vec3<f32>(1.0))) * light * inLens;
    return vec4<f32>(clamp(finished, vec3<f32>(0.0), vec3<f32>(1.0)), 1.0);
}
