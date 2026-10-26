// Ribbon finish pass, over the native instrument rendered to a texture.
// p is in artboard points (1080 tall), so the layout numbers from ribbon_fx.luau line up.
// Every effect is driven by the car: parked and idle, nothing moves.
struct U {
    a: vec4<f32>,   // w, h, time, renderQuality (1-3)
    b: vec4<f32>,   // speed fill 0-1, speed trail 0-1, power fill 0-1, regen fill 0-1
    c: vec4<f32>,   // red, green, yellow (smoothed 0-1), over the limit 0-1
    d: vec4<f32>,   // left lamp 0-1, right lamp 0-1, seconds since left lit, seconds since right lit
    e: vec4<f32>,   // left gauge centre x, y, ribbon inner radius, outer radius
    f: vec4<f32>,   // outline radius, angle of the 0 end, angle span (radians), speed km/h
    g: vec4<f32>,   // car centre x, y, car top, car bottom
    h: vec4<f32>,   // car half width, left blind spot 0-2, right blind spot 0-2, start-up fade 0-1
    i: vec4<f32>,   // energy phase left, energy phase right, road scroll (points), -
    j: vec4<f32>,   // left rear fan x, y, boil over the limit 0-1, -
    k: vec4<f32>,   // liquid: surface tilt left, right (-1..1, sloshing), ripple left, right
    l: vec4<f32>,   // fit: design offset x, y on screen, design scale, -
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
            let knee = clamp((l - 0.38) / 0.30, 0.0, 1.0);          // soft knee: only real highlights glow
            c = c + s * knee * knee * w;
            wsum = wsum + w;
        }
    }
    return c / wsum;
}

fn hash(p: vec2<f32>) -> f32 {
    var p3 = fract(vec3<f32>(p.xyx) * 0.1031);
    p3 = p3 + dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// the liquid's own colour, by height on the screen: the same stops the native ribbon uses
fn liquidColour(y: f32, regen: f32) -> vec3<f32> {
    let g = clamp((942.0 - y) / 877.0, 0.0, 1.0);
    var c = mix(vec3<f32>(0.851, 0.110, 0.984), vec3<f32>(0.992, 0.298, 0.741), smoothstep(0.0, 0.25, g));
    c = mix(c, vec3<f32>(0.996, 0.231, 0.812), smoothstep(0.25, 0.6, g));
    c = mix(c, vec3<f32>(0.988, 0.0, 0.984), smoothstep(0.6, 1.0, g));
    let lav = mix(vec3<f32>(0.612, 0.549, 1.0), vec3<f32>(0.788, 0.745, 1.0), g);
    return mix(c, lav, regen);
}

fn sq(x: f32) -> f32 { return x * x; }

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
    // geometry works in design points (2280 x 1080); the screen may be wider or narrower
    let sres = vec2<f32>(2280.0, 1080.0);
    let p = (uv * res - u.l.xy) / u.l.z;

    // --- lens: colour fringes grow toward the screen edges, nothing at the centre
    let ed = uv - 0.5;
    let fr = vec2<f32>(0.0, 0.0);                          // no colour fringes: clean type and scale
    let base = tap(uv);
    var col = vec3<f32>(tap(uv + fr).r, base.g, tap(uv - fr).b);
    let lum = dot(base, vec3<f32>(0.299, 0.587, 0.114));
    let bg = 1.0 - smoothstep(0.025, 0.07, lum);            // 1 on the black ground, 0 on anything drawn

    // --- bloom: neon, three radii
    if (q > 1.5) {
        let b = bloom(uv, res, 0.8) * 0.29 + bloom(uv, res, 2.0) * 0.25 + bloom(uv, res, 4.5) * 0.17;
        col = col + b;
    }

    // --- the two ribbons
    for (var s = 0; s < 2; s = s + 1) {
        let right = s == 1;
        let qp = select(p, vec2<f32>(sres.x - p.x, p.y), right);
        let d = qp - u.e.xy;
        let r = length(d);
        var ang = atan2(d.x, -d.y);
        if (ang < 0.0) { ang = ang + 6.2831853; }
        let tt = (ang - u.f.y) / u.f.z;                       // 0 at the zero end, 1 at the top
        let ttu = select(tt, tt + 6.2831853 / u.f.z, tt < -0.5);   // unwrapped across 12 o'clock
        let rin = u.e.z;
        let rout = u.e.w;
        let band = rout - rin;
        let rmid = (rin + rout) * 0.5;
        // the liquid sits inside the tube: 4 px in from both walls and both end caps
        let inner = clamp((rout - r) / (rout - rin), 0.0, 1.0);          // 0 at the outer edge, 1 at the inner
        let t0 = inner * 0.0872665 / u.f.z;                               // slanted zero cut: +5 deg at the inner edge
        let t1 = 1.0 - inner * 0.1396263 / u.f.z;                         // slanted top cut: -8 deg at the inner edge
        let inBand = smoothstep(rin, rin + 2.0, r) * (1.0 - smoothstep(rout - 2.0, rout, r)) * step(t0, tt) * step(tt, t1);
        let fillv = select(u.b.x, max(u.b.z, u.b.w), right);
        let shown = step(0.003, fillv);
        let arcLen = u.f.z * rmid;
        // --- the liquid: its surface tilts across the ribbon as it sloshes, and ripples
        let wv = clamp((r - rmid) / (band * 0.5), -1.0, 1.0);            // -1 inner edge .. 1 outer edge
        let tilt = select(u.k.x, u.k.y, right);
        let rip = select(u.k.z, u.k.w, right);
        let surfPx = tilt * wv * band * 0.42
                   + rip * (sin(wv * 3.4 + t * 6.3) * 4.5 + sin(wv * 7.9 - t * 9.1) * 2.0);
        let ts = fillv + surfPx / arcLen * shown;
        let depthPx = (ts - tt) * arcLen;                                 // how far below the surface
        let liquid = inBand * smoothstep(-1.2, 1.2, depthPx) * shown;
        let regenMix = select(0.0, step(u.b.z, u.b.w), right);
        var lc = liquidColour(p.y, regenMix);
        // body: darker toward the inner wall, a glossy line along the outer third, brighter just under the surface
        lc = lc * (1.0 - smoothstep(rmid, rin, r) * 0.30);
        lc = lc + vec3<f32>(1.0, 0.92, 1.0) * ring(r, rmid + band * 0.24, 6.0) * 0.14;
        lc = lc + vec3<f32>(1.0, 0.85, 1.0) * exp(-max(depthPx, 0.0) / 26.0) * 0.18;
        // slow light moving through the liquid, at the rate the level is fed
        let phase = select(u.i.x, u.i.y, right);
        let sf = fract(tt * 9.0 - phase * 9.0);
        lc = lc + vec3<f32>(1.0, 0.78, 1.0) * smoothstep(0.0, 0.035, sf) * (1.0 - smoothstep(0.035, 0.28, sf)) * 0.0;
        // bubbles (shading after the day 15 liquid battery the boss approved): each a lit sphere, a slightly
        // lighter disc that brightens toward its edge from inside, a soft glint on the side facing the surface,
        // antialiased. They always rise at their own steady pace with a small sideways wobble, more of them
        // drifting with the flow when the level moves, and they end at the surface.
        let along = tt * arcLen;
        let cellSz = 34.0;
        let cj = floor((wv + 1.0) * 2.5);
        // over the limit the speed liquid boils: more bubbles, rising twice as fast, shaking
        let boil = select(u.j.z, 0.0, right);
        // in regen the power liquid runs backwards: its bubbles sink toward the zero end
        let sink = select(1.0, -1.0, right && u.b.w > u.b.z && u.b.w > 0.01);
        let rise = phase * arcLen * 1.6 + sink * t * (34.0 + 26.0 * hash(vec2<f32>(cj, 3.0 + f32(s)))) * (1.0 + boil * 1.6);
        let cid = floor((along - rise) / cellSz);
        let hsh = hash(vec2<f32>(cid, cj + f32(s) * 13.0));
        if (hsh > 0.74 - boil * 0.30) {                                         // the first bubble amount (boss)                                         // a few bubbles, not foam
            let wobble = sin(t * (5.0 + boil * 9.0) + hsh * 40.0) * (2.4 + boil * 3.0);
            let bpos = vec2<f32>((cid + 0.5 + (hash(vec2<f32>(cid, cj + 5.0)) - 0.5) * 0.6) * cellSz + rise,
                                 ((cj + 0.5) / 2.5 - 1.0) * band * 0.5 + wobble);
            let bp = vec2<f32>(along, r - rmid) - bpos;               // x toward the surface, y toward the outer edge
            let br = 2.0 + hash(vec2<f32>(cid, cj + 9.0)) * 4.5;
            let bd = length(bp) - br;
            let cover = 1.0 - smoothstep(-0.8, 0.8, bd);
            let rimIn = smoothstep(-br * 0.55, 0.0, bd);
            let glint = exp(-length(bp - vec2<f32>(br * 0.35, -br * 0.35)) / max(br * 0.18, 0.6));
            let lit = mix(lc * 1.08 + vec3<f32>(0.06), vec3<f32>(1.0), clamp(rimIn * 0.5 + glint * 0.8, 0.0, 1.0));
            lc = mix(lc, lit, cover * smoothstep(3.0, 18.0, depthPx));
        }
        // regen swirl: lavender streaks curl down the power liquid at the rate it charges
        if (right) {
            let sw = sin(along * 0.040 + wv * 5.5 + u.i.y * 40.0 + t * 1.5);
            lc = lc + vec3<f32>(0.86, 0.82, 1.0) * pow(max(sw, 0.0), 8.0) * smoothstep(0.0, 0.15, u.b.w) * regenMix * smoothstep(2.0, 20.0, depthPx) * 0.0;
        }
        // boiling: the surface throws up a hot froth
        let froth = boil * exp(-max(-depthPx, 0.0) / 6.0) * (1.0 - liquid) * inBand * shown
                  * smoothstep(0.55, 0.95, hash(vec2<f32>(floor(along / 5.0), floor(t * 14.0))));
        col = col + vec3<f32>(1.0, 0.75, 0.80) * froth * 0.6;
        // the surface: a bright meniscus line and a little foam above it
        let men = exp(-depthPx * depthPx / 3.0) * inBand * shown;
        col = mix(col, lc, liquid);
        col = col + vec3<f32>(1.0, 0.95, 1.0) * men * 0.72;
        col = col + vec3<f32>(1.0, 0.70, 0.95) * exp(-max(-depthPx, 0.0) / 9.0) * (1.0 - liquid) * inBand * shown * 0.18;
        // light off the liquid onto the ribbon's edges and a halo at the surface
        let inShape = step(t0, ttu) * step(ttu, t1) * smoothstep(rin - 8.0, rin, r) * (1.0 - smoothstep(rout, rout + 8.0, r));
        col = col + liquidColour(p.y, regenMix) * (ring(r, rout + 2.0, 5.0) + ring(r, rin - 2.0, 5.0))   // glow hugs the rim
                  * step(tt, ts) * step(0.0, tt) * shown * 0.20 * inShape;
        let ha = u.f.y + fillv * u.f.z;
        let hpt = u.e.xy + rmid * vec2<f32>(sin(ha), -cos(ha));
        let hv = qp - hpt;
        col = col + liquidColour(p.y, regenMix) * exp(-dot(hv, hv) / (2.0 * 60.0 * 60.0)) * 0.20 * shown * inShape;
        let lit = liquid;
        let pastCut = 1.0 - step(t0, ttu) * step(ttu, t1);
        let nearEnd = step(-0.20, ttu) * step(ttu, 1.20);
        let nearBand = smoothstep(rin - 60.0, rin - 30.0, r) * (1.0 - smoothstep(rout + 30.0, rout + 60.0, r));
        let spill = pastCut * nearEnd * nearBand;

        if (!right) {
            // acceleration: the stretch gained in the last moments burns brighter
            let tr = u.b.y;
            if (fillv > tr + 0.003) {
                let w = clamp((tt - tr) / (fillv - tr), 0.0, 1.0);
                col = col + vec3<f32>(1.0, 0.62, 1.0) * lit * step(tr, tt) * w * w * 0.30;
            }
            // over the limit the speed ribbon runs hot red
            col = mix(col, col * vec3<f32>(1.25, 0.32, 0.40) + vec3<f32>(0.22, 0.0, 0.02), lit * u.c.w * 0.75);
        } else {
            // regen: a lavender aura along the outside of the power ribbon
            col = col + vec3<f32>(0.62, 0.52, 1.0) * u.b.w * ring(r, rout + 16.0, 24.0) * step(0.0, tt) * step(tt, 1.0) * 0.20;
        }

        // blinker: a comet of amber runs down that side's outline, lit with the real lamp
        let on = select(u.d.x, u.d.y, right);
        let since = select(u.d.z, u.d.w, right);
        let cHead = 1.0 - min(since / 0.32, 1.0);
        let tail = clamp((tt - cHead) / 0.40, 0.0, 1.0);
        let onLine = ring(r, u.f.x, 4.5) + ring(r, u.f.x, 20.0) * 0.45;
        col = col + vec3<f32>(0.21, 0.85, 0.47) * onLine * step(cHead - 0.01, tt) * step(tt, 1.0) * (1.0 - tail) * on * exp(-since * 1.8) * 0.95;
        col = mix(col, base, spill);                              // nothing glows past the ribbon's cut ends
    }

    // --- the car: a light under it that follows power
    let cx = u.g.x;
    let ctop = u.g.z;
    let cbot = u.g.w;
    let hw = u.h.x;
    let gd = vec2<f32>((p.x - cx) / (hw * 1.7), (p.y - (ctop + cbot) * 0.5) / ((cbot - ctop) * 0.62));
    let under = exp(-dot(gd, gd) * 2.2) * bg;
    // (the light under the car is off: it tinted the car's glass magenta)

    // --- lane marks pass beside the car at its speed (backwards in R), fading at both ends
    let ly = fract((p.y - u.i.z) / 64.0);
    let dash = smoothstep(0.0, 0.04, ly) * (1.0 - smoothstep(0.42, 0.46, ly));
    let lx = min(abs(p.x - (cx - 178.0)), abs(p.x - (cx + 178.0)));
    let laneV = smoothstep(250.0, 380.0, p.y) * (1.0 - smoothstep(620.0, 700.0, p.y));   // stop above the assist row
    let laneC = mix(vec3<f32>(0.92, 0.70, 1.0), vec3<f32>(0.39, 0.81, 1.0), u.i.w);   // blue while assist is on
    col = col + laneC * exp(-lx * lx / 3.5) * dash * laneV * bg * mix(0.30, 0.55, u.i.w);

    // --- blind spots: radar pings sweep out of the car's rear corner, amber, red when urgent
    for (var s = 0; s < 2; s = s + 1) {
        let right = s == 1;
        let fo = select(u.j.xy, vec2<f32>(sres.x - u.j.x, u.j.y), right);
        let act = select(u.h.y, u.h.z, right);
        if (act > 9.0) {                                       // off: the native car in the next lane says it
            let fd = p - fo;
            let fl = max(length(fd), 0.001);
            let dir = select(vec2<f32>(-0.80, 0.60), vec2<f32>(0.80, 0.60), right);
            let cone = smoothstep(0.70, 0.93, dot(fd / fl, dir));
            let c1 = select(vec3<f32>(1.0, 0.76, 0.10), vec3<f32>(1.0, 0.23, 0.19), act > 1.5);
            for (var k = 0; k < 2; k = k + 1) {
                let ph = fract(t * 1.1 + f32(k) * 0.5);
                col = col + c1 * ring(fl, 64.0 + ph * 120.0, 3.0 + ph * 7.0) * (1.0 - ph) * cone * min(act, 1.0) * 0.75;
            }
        }
    }

    // --- the traffic light washes in from the top; over the limit the sign burns red
    let tint = u.c.x * vec3<f32>(1.0, 0.15, 0.12) + u.c.y * vec3<f32>(0.20, 1.0, 0.45) + u.c.z * vec3<f32>(1.0, 0.75, 0.15);
    // the traffic light lives in its housing now; no wash over the screen
    let sd = length(p - vec2<f32>(sres.x * 0.5, 219.75));
    col = col + vec3<f32>(1.0, 0.18, 0.15) * ring(sd, 54.0, 12.0) * u.c.w * 0.38;

    // --- finish: vignette, and the start-up fade
    let vq = (uv - 0.5) * vec2<f32>(1.6, 1.0);
    col = col * mix(0.82, 1.0, smoothstep(1.3, 0.35, length(vq)));
    col = col * 0.93;                                           // overall a touch less bright on the phone
    col = col * u.h.w;
    return vec4<f32>(max(col, vec3<f32>(0.0)), 1.0);
}
