"""Monte Carlo: a sports-car lens cluster, drawn from a face-on reference.

Every position is measured on the reference (2172 x 724) and mapped onto the 2280 x 1080 artboard by P():
scale 2280 / 2172, centred vertically. On top of it: a near-black cool
face with ONE crimson glow behind the speed, thin luminous graphics, a faint hex grid that fades out,
narrow peripheral gauges, a quiet bottom tab, the power fill / track / redline as one ribbon.
Type: Saira Italic, variable, pushed to 125 % width for the extended oblique look of the target.
Draw order: the FIRST sibling draws on TOP; node()/stack() take paint order, bottom first.
"""
import math
import os
import re
from gen import (nid, bind, fill, glow, grad_fill, stroke, grad_stroke, trim, shape, node, rect, poly, ellipse,
                 SM, OPACITY, TEXT, TRIM_START, TRIM_END, SCALE_X, SCALE_Y, VM, fx_layer)

VM.update({"introDip": "1:87", "introEdge": "1:88", "introTell": "1:89", "introScale": "1:90", "displayPower": "1:91"})


def fade(prop, kids, name):
    """A group that the start-up fades in through one view-model number (1 = finished)."""
    return node(name, kids, 0, 0, 0, bind(prop, OPACITY))

W, H = 2280, 1080
K = 2280 / 2172
OY = (1080 - 724 * K) / 2
SAIRA = "2:907"
WHITE, LIT, UNLIT = "FFF6F8F5", "FFEEF0ED", "FF25262C"
FILL = [("FFFFC531", 0), ("FFF8901A", 0.3), ("FFF36E00", 0.5), ("FFD8380A", 0.75), ("FFC52800", 1)]


def P(x, y):
    return x * K, OY + y * K


def S(v):
    return v * K


def radial(stops, r):
    s = "".join(f'<GradientStop colorValue="{c}" position="{p}"/>' for c, p in stops)
    return f'<Fill name="F"><RadialGradient startX="0" startY="0" endX="{r}" endY="0" name="G">{s}</RadialGradient></Fill>'


def T(name, runs, x, y, size, color=WHITE, w=600, wght=700, wdth=125, align="center", ox=0.5, spacing=0, binds="", sx=1.0):
    """Saira Italic text at a width and weight. runs: (text, bind) or (text, bind, size)."""
    styles, rs = {}, []
    for r in runs:
        sz = r[2] if len(r) > 2 else size
        if sz not in styles:
            styles[sz] = nid()
        rs.append(f'<TextValueRun styleId="{styles[sz]}" text="{r[0]}" name="Run">{r[1]}</TextValueRun>')
    paints = "".join(
        f'<TextStylePaint fontSize="{sz:.1f}" letterSpacing="{spacing}" fontAssetId="{SAIRA}" familyName="Saira" styleName="Bold Italic" name="S" id="{i}">'
        f'{fill(color)}<TextStyleAxis tag="2003265652" axisValue="{wght}" name="Weight"/>'
        f'<TextStyleAxis tag="2003072104" axisValue="{wdth}" name="Width"/>'
        f'<TextStyleFeature tag="1953396077" featureValue="1" name="tnum"/></TextStylePaint>'
        for sz, i in styles.items())
    hh = int(size * 1.5)
    if sx != 1.0:                                   # stretch wide about the text's own centre, still vector
        inner = T(name, runs, 0, 0, size, color, w, wght, wdth, align, ox, spacing)
        holder = f'<Node x="{x:.2f}" y="{y:.2f}" scaleX="{sx}" name="{name} Wide">{inner}</Node>'
        return node(name + " Holder", [holder], 0, 0, 0, binds) if binds else holder
    t = (f'<Text x="{x:.2f}" y="{y:.2f}" sizingValue="fixed" width="{w}" height="{hh}" originX="{ox}" originY="0.5" '
         f'alignValue="{align}" verticalAlignValue="middle" wrapValue="noWrap" name="{name}" id="{nid()}">{paints}{"".join(rs)}</Text>')
    return node(name + " Holder", [t], 0, 0, 0, binds) if binds else t


def catmull(pts, n=12):
    out = []
    for i in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, len(pts) - 1)]
        for j in range(n):
            t = j / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * (2 * p1[a] + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t2
                                    + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t3) for a in (0, 1)))
    out.append(pts[-1])
    return out


# ---------------------------------------------------------------- the lens (measured on target 2, symmetric about x 1086)
TOP_L = [(25, 372), (30, 300), (40, 268), (80, 225), (150, 187), (300, 144), (600, 100), (1086, 78)]
BOT_L = [(25, 400), (33, 500), (40, 523), (80, 565), (150, 593), (300, 616), (600, 633), (1086, 635)]


def lens_pts():
    top = catmull(TOP_L + [(2172 - x, y) for x, y in TOP_L[::-1][1:]])
    bot = catmull(BOT_L + [(2172 - x, y) for x, y in BOT_L[::-1][1:]])
    return [P(x, y) for x, y in top + bot[::-1]]


def lens():
    pts = lens_pts()
    k = [fade("introMain", [shape("Lens Face", poly(pts) + grad_fill("LinearGradient", [("FF070A10", 0), ("FF04060A", 0.5), ("FF030407", 1)], sx=0, sy=P(0, 78)[1], ex=0, ey=P(0, 635)[1])),
         shape("Lens Inner", poly(pts) + fill("CC000000", glow(S(30), 0, 0, inner=True)))], "Lens Body"),
         shape("Lens Top Edge", poly(pts[:len(pts) // 2], False) + grad_stroke(1.5, [("00303A44", 0), ("40303A44", 0.3), ("55394651", 0.5), ("40303A44", 0.7), ("00303A44", 1)], 0, 0, W, 0,
                                                                              trim(0, 1, bind("introEdge", TRIM_START, "9:100") + bind("introEdge", TRIM_END, "9:101"))))]
    return node("Lens", k)


# ---------------------------------------------------------------- the power ribbon
BX0, BX1, BMID = 262.0, 1905.0, 1083.5


def base_y(x):                                   # the baseline the fill block sits on (target px)
    u = (x - BMID) / (BX1 - BMID)
    return 136 + 72 * u * u


def bx(v):
    return BX0 + (BX1 - BX0) * v / 10


def ribbon(x0, x1, dy=0.0, n=90):
    return [P(x0 + (x1 - x0) * i / n, base_y(x0 + (x1 - x0) * i / n) + dy) for i in range(n + 1)]


def hexes(cx, cy, size, rx, ry, rings, color, alpha, falloff, width=1.0):
    buckets = [[] for _ in range(rings)]
    seen = set()
    wv = math.sqrt(3) * size
    for r_ in range(-int(ry / (1.5 * size)) - 2, int(ry / (1.5 * size)) + 3):
        for c_ in range(-int(rx / wv) - 2, int(rx / wv) + 3):
            hx = cx + (c_ + (0.5 if r_ % 2 else 0)) * wv
            hy = cy + r_ * 1.5 * size
            d = math.hypot((hx - cx) / rx, (hy - cy) / ry)
            if d > 1:
                continue
            vertices = [(hx + size * math.cos(math.radians(30 + 60 * j)), hy + size * math.sin(math.radians(30 + 60 * j))) for j in range(6)]
            for j in range(6):                       # every wall once, so neighbours share it
                a, z = vertices[j], vertices[(j + 1) % 6]
                key = tuple(sorted(((round(a[0], 3), round(a[1], 3)), (round(z[0], 3), round(z[1], 3)))))
                if key in seen:
                    continue
                seen.add(key)
                ed = math.hypot(((a[0] + z[0]) / 2 - cx) / rx, ((a[1] + z[1]) / 2 - cy) / ry)
                if ed <= 1:
                    buckets[min(rings - 1, int(ed * rings))].append(poly([a, z], False))
    out = []
    for b, paths in enumerate(buckets):
        if paths:
            a = int(255 * alpha * (1 - b / rings) ** falloff)
            out.append(f'<Shape x="0" y="0" rotation="0" name="Hex {b}">{"".join(paths)}{stroke(f"{a:02X}" + color[2:], width)}</Shape>')
    return out


PATTERN = os.environ.get("V3_PATTERN", "carbon")   # the texture inside the speed glow: carbon weave (hex was the original)


def faded(segs, cx, cy, rx, ry, rings, color, alpha, falloff, width, name, closed=False):
    """Bucket line pieces by their distance from the glow centre so the texture fades out the same way the hex grid does."""
    buckets = [[] for _ in range(rings)]
    for pts in segs:
        mx, my = sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts)
        d = math.hypot((mx - cx) / rx, (my - cy) / ry)
        if d <= 1:
            buckets[min(rings - 1, int(d * rings))].append(poly(pts, closed))
    out = []
    for b, paths in enumerate(buckets):
        if paths:
            a = int(255 * alpha * (1 - b / rings) ** falloff)
            out.append(f'<Shape x="0" y="0" rotation="0" name="{name} {b}">{"".join(paths)}{stroke(f"{a:02X}" + color[2:], width)}</Shape>')
    return out


def pieces(a, b, step):
    """Cut a straight line into short pieces, so each piece fades by its own distance."""
    n = max(1, int(math.hypot(b[0] - a[0], b[1] - a[1]) / step))
    return [[(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n), (a[0] + (b[0] - a[0]) * (i + 1) / n, a[1] + (b[1] - a[1]) * (i + 1) / n)] for i in range(n)]


def texture(cx, cy, rx, ry, color):
    if PATTERN == "hex":
        return hexes(cx, cy, S(19), rx, ry, 64, color, 0.38, 1.15, S(1.10))
    if PATTERN == "none":
        return []
    segs = []
    if PATTERN == "carbon":                                    # twill weave: short diagonals, alternating direction per cell
        c = S(13)
        for j in range(-int(ry / c) - 1, int(ry / c) + 2):
            for i in range(-int(rx / c) - 1, int(rx / c) + 2):
                x, y = cx + i * c, cy + j * c
                if (i + j) % 2:
                    segs += [[(x + c * 0.15, y + c * 0.85), (x + c * 0.85, y + c * 0.15)]]
                else:
                    segs += [[(x + c * 0.15, y + c * 0.15), (x + c * 0.85, y + c * 0.85)]]
        return faded(segs, cx, cy, rx, ry, 48, color, 0.30, 1.1, S(1.4), "Weave")
    if PATTERN == "rings":                                     # concentric lens rings around the number
        for k in range(1, 40):
            r = k * S(11)
            pts = [(cx + r * (rx / ry) * math.cos(t * math.pi / 48), cy + r * math.sin(t * math.pi / 48)) for t in range(97)]
            segs += [pts[i:i + 3] for i in range(0, 96, 2)]
        return faded(segs, cx, cy, rx, ry, 48, color, 0.30, 1.2, S(1.1), "Ring")
    if PATTERN == "streaks":                                   # pinstripes at the numerals' italic slant, like motion
        sl = math.tan(math.radians(14))
        g = S(15)
        for i in range(-int((rx + ry) / g) - 2, int((rx + ry) / g) + 3):
            x0 = cx + i * g
            segs += pieces((x0 - ry * sl, cy + ry), (x0 + ry * sl, cy - ry), S(12))
        return faded(segs, cx, cy, rx, ry, 48, color, 0.28, 1.1, S(1.2), "Streak")
    if PATTERN == "dots":                                      # dot matrix: tiny squares with round corners, like an LED field
        g = S(17)
        out_b = [[] for _ in range(48)]
        for j in range(-int(ry / g) - 1, int(ry / g) + 2):
            for i in range(-int(rx / g) - 1, int(rx / g) + 2):
                x, y = cx + i * g + (g / 2 if j % 2 else 0), cy + j * g
                d = math.hypot((x - cx) / rx, (y - cy) / ry)
                if d <= 1:
                    out_b[min(47, int(d * 48))].append(rect(S(2.8), S(2.8), x, y, S(1.4)))
        out = []
        for b, paths in enumerate(out_b):
            if paths:
                a = int(255 * 0.50 * (1 - b / 48) ** 1.1)
                out.append(f'<Shape x="0" y="0" rotation="0" name="Dot {b}">{"".join(paths)}{fill(f"{a:02X}" + color[2:])}</Shape>')
        return out
    raise SystemExit("unknown V3_PATTERN " + PATTERN)


def power():
    k = []
    TH = S(42)                                               # ribbon thickness
    mid = poly(ribbon(BX0, BX1, -21), False)                 # the ribbon's centre line, 21 above the baseline
    k.append(shape("Ribbon Track", mid + stroke("FF0B0A0E", TH)))
    # regen: the same ribbon carried a short way left of 0
    rg = poly(ribbon(BX0, 200, -21, 12), False)
    k.append(shape("Regen Fill", rg + stroke("FF71BDB2", TH, trim(0, 0, bind("displayPower", TRIM_END, "9:132")))))
    # redline 8..10: the same ribbon, red, a fine hex texture inside
    upper, lower = [], []
    for i in range(61):
        x = bx(8) + (BX1 - bx(8)) * i / 60
        slope = 144 * (x - BMID) / (BX1 - BMID) ** 2
        inv = 1 / math.sqrt(1 + slope * slope)
        nx_, ny_ = -slope * inv, inv
        y = base_y(x) - 21
        upper.append(P(x - 21 * nx_, y - 21 * ny_))
        lower.append(P(x + 21 * nx_, y + 21 * ny_))
    rz = upper[::-1] + lower
    rid = nid()
    k.append(f'<Shape x="0" y="0" rotation="0" name="Redline" id="{rid}">{poly(rz)}'
             f'{grad_fill("LinearGradient", [("2EB20B08", 0), ("4DDA170C", 1)], sx=P(bx(8), 0)[0], sy=0, ex=P(BX1, 0)[0], ey=0)}</Shape>')
    hz = "".join(hexes(*P(bx(9), base_y(bx(9)) - 21), S(11), S(200), S(40), 1, "FFFF5335", 0.20, 0, S(1.0)))
    k.append(hz.replace("</Shape>", f'<ClippingShape sourceId="{rid}" name="Clip"/></Shape>'))
    m0, m1 = P(bx(8), base_y(bx(8)) - 46), P(bx(8), base_y(bx(8)) + 4)
    k.append(shape("Redline Mark", poly([m0, m1], False) + stroke("FFFF2A1A", S(4))))
    # the zone is marked, not filled: a thin red edge along its top; the live fill turns it red when it gets there
    k.append(shape("Redline Edge", poly(upper, False) + stroke("CCFF2A1A", S(2.5))))
    # the live fill, a soft bloom, and a bright sliver at its head
    k.append(shape("Fill", mid + grad_stroke(TH, FILL, P(BX0, 0)[0], 0, P(BX1, 0)[0], 0, trim(0, 0, bind("displayPower", TRIM_END, "9:130")))))
    k.append(shape("Fill Head", mid + stroke("FFFFBB52", TH + S(2), trim(0, 0, bind("displayPower", TRIM_START, "9:131") + bind("displayPower", TRIM_END, "9:130")))))
    # the baseline: a thin luminous line under the ribbon
    base = poly(ribbon(BX0 - 4, BX1 + 4, 0), False)
    k.append(shape("Baseline", base + grad_stroke(1.6, [("88B8B4BA", 0), ("B0D8D4DA", 0.5), ("88B8B4BA", 0.85), ("CCFF3A2A", 1)], P(BX0, 0)[0], 0, P(BX1, 0)[0], 0)))
    # ticks hang under the baseline, numbers under the majors
    for i in range(21):
        v = i / 2
        x = bx(v)
        y = base_y(x)
        slope = 2 * 72 * (x - BMID) / (BX1 - BMID) ** 2
        tilt = math.atan(slope)
        major = i % 2 == 0
        L = 22 if major else 10
        px, py = P(x, y)
        k.append(shape(f"Tick {i}", rect(S(3.7) if major else S(2.2), S(L), 0, S(L) / 2, 1) + fill(WHITE if major else "CCD8D8DC"), px, py + 1, tilt))
        if major:
            nx, ny = P(x + 2, y + 50)
            k.append(T(f"Scale {i // 2}", [(str(i // 2), "")], nx, ny, S(40), WHITE, w=140, wght=660, sx=1.15,
                       binds=bind("introScale", OPACITY)))
    cx, cy = P(1086, 230)
    k.append(T("Scale Caption", [("x10 % POWER", "")], cx, cy, S(19), WHITE, w=300, wght=700, spacing=1, binds=bind("introBars", OPACITY)))
    return node("Power", k, 0, 0, 0, bind("introMain", OPACITY))


# ---------------------------------------------------------------- centre: glow, hex, speed, divider, dip
def cubic_points(a, b, c, d, n=48):
    return [tuple((1 - t) ** 3 * a[k] + 3 * (1 - t) ** 2 * t * b[k] + 3 * (1 - t) * t * t * c[k] + t ** 3 * d[k] for k in (0, 1))
            for t in (i / n for i in range(n + 1))]


def divider():
    """Wing line at y 430, a shoulder with flat tangents at both ends, the dip floor exactly flat at y 520."""
    left = [(25, 432), (600, 430), (650, 430)]
    left += cubic_points((650, 430), (735, 430), (825, 520), (880, 520))[1:]
    right = [(2172 - x, y) for x, y in left[::-1]]
    return [P(x, y) for x, y in left + [(1292, 520)] + right[1:]]


def centre():
    k = []
    lid = nid()
    k.append(f'<Shape x="0" y="0" rotation="0" name="Lens Clip" id="{lid}">{poly(lens_pts())}</Shape>')
    clip = f'<ClippingShape sourceId="{lid}" name="Clip"/>'

    def clipped(s):
        i = s.rindex("</Shape>")
        return s[:i] + clip + s[i:]

    def oval(name, cx, cy, rx, ry, stops, binds=""):
        sh = clipped(shape(name, ellipse(2 * rx) + radial(stops, rx)))
        return f'<Node x="{cx:.2f}" y="{cy:.2f}" scaleY="{ry / rx:.4f}" name="{name} Holder">{binds}{sh}</Node>'

    gx, gy = P(1086, 365)
    glow_k = []
    k0 = k
    k = glow_k
    # one crimson field behind the speed: dark red outside, a hotter heart behind the numerals
    k.append(oval("Glow Outer", gx, gy + S(40), S(600), S(320), [("80550607", 0), ("40550607", 0.5), ("00550607", 1)]))
    # red light creeping in at the lower corners of the glass
    for sx_ in (-1, 1):
        cx_, cy_ = P(1086 + sx_ * 1000, 560)
        k.append(oval(f"Edge Red {sx_}", cx_, cy_, S(260), S(170), [("40600808", 0), ("1A600808", 0.5), ("00600808", 1)]))
    k.append(oval("Glow Core", gx, gy, S(340), S(225), [("D9B8140C", 0), ("A6930D0A", 0.36), ("38500608", 0.72), ("00500608", 1)]))
    k.append(oval("Glow Heart", gx, gy - S(5), S(170), S(110), [("33F53C14", 0), ("12E72A10", 0.6), ("00E72A10", 1)]))
    # the faint hex grid inside the glow
    hx = texture(gx, gy + S(10), S(410), S(215), "FFFF4B24")
    k += [clipped(h) for h in hx]
    k = k0
    k.append(f'<Node x="{gx:.2f}" y="{gy:.2f}" name="Bloom">{bind("introSide", OPACITY)}'
             f'{node("Bloom Origin", glow_k, -gx, -gy)}</Node>')
    # the divider and its lit dip
    dv = divider()
    dip0, dip1 = P(880, 520)[0], P(1292, 520)[0]
    k.append(shape("Divider", poly(dv, False) + grad_stroke(S(4.0), [("00303038", 0), ("B0303038", 0.1), ("C0383038", 0.28), ("FFC0442A", 0.36),
                                                                  ("FFD8502C", 0.5), ("FFC0442A", 0.64), ("C0383038", 0.72), ("B0303038", 0.9), ("00303038", 1)],
                                                                  0, 0, W, 0, trim(0, 1, bind("introDip", TRIM_START, "9:100") + bind("introDip", TRIM_END, "9:101")))))
    dy = P(0, 520)[1]
    dip_k = []
    k0 = k
    k = dip_k
    k.append(oval("Dip Spill", W / 2, dy - S(32), S(285), S(95), [("78FF9A30", 0), ("30FF7A20", 0.5), ("00FF6020", 1)]))
    k.append(shape("Dip Halo Wide", poly([(dip0, dy), (dip1, dy)], False) + grad_stroke(S(26), [("00FF9A2B", 0), ("40FF9A2B", 0.2), ("90FFA52B", 0.5), ("40FF9A2B", 0.8), ("00FF9A2B", 1)], dip0, 0, dip1, 0, glow(S(7)))))
    k.append(shape("Dip Halo", poly([(dip0, dy), (dip1, dy)], False) + grad_stroke(S(8), [("00FFB52B", 0), ("99FFB52B", 0.15), ("E6FFB52B", 0.5), ("99FFB52B", 0.85), ("00FFB52B", 1)], dip0, 0, dip1, 0, glow(S(2)))))
    k.append(shape("Dip Core", poly([(dip0, dy), (dip1, dy)], False) + grad_stroke(S(4.2), [("00FFE6A1", 0), ("CCFFE6A1", 0.14), ("FFFFFBEA", 0.5), ("CCFFE6A1", 0.86), ("00FFE6A1", 1)], dip0, 0, dip1, 0)))
    k = k0
    k.append(node("Dip", dip_k, 0, 0, 0, bind("introDip", OPACITY)))
    # the speed (it races the sweep), its unit, gear, mode
    sx, sy = P(1090, 362)
    k.append(T("Speed", [("86", bind("speed", TEXT, "2:1"))], sx, sy, S(156), WHITE, w=760, wght=640, sx=1.18, binds=bind("introSide", OPACITY)))
    labels = []
    k0 = k
    k = labels
    ux, uy = P(1086, 463)
    k.append(T("Speed Unit", [("KM/H", "")], ux, uy, S(26), WHITE, w=200, wght=700, sx=1.12))
    gx2, gy2 = P(490, 368)
    for g_, conv in (("P", "2:11"), ("R", "2:12"), ("N", "2:13"), ("D", "2:14")):
        k.append(T(f"Gear {g_}", [(g_, "")], gx2, gy2, S(48), WHITE, w=140, wght=820, sx=1.15, binds=bind("gear", OPACITY, conv)))
    mx, my = P(1632, 368)
    # the outgoing word goes at once (state gate); only the incoming word fades in, so they never overlap
    k.append(node("Assist State Gate", [T("Mode Assist", [("ASSIST", "")], mx, my, S(35), WHITE, w=340, wght=720, sx=1.12, binds=bind("adas", OPACITY, "9:145"))], 0, 0, 0, bind("adas", OPACITY, "2:41")))
    k.append(node("Manual State Gate", [T("Mode Manual", [("MANUAL", "")], mx, my, S(35), "FFB8B6BC", w=340, wght=680, sx=1.12, binds=bind("adas", OPACITY, "9:146"))], 0, 0, 0, bind("adas", OPACITY, "2:43")))
    # posted limit: a quiet figure and caption between the speed and the mode
    lx, ly = P(1412, 318)
    k.append(node("Limit", [T("Limit Num", [("80", bind("limit", TEXT, "2:1"))], lx, ly, S(30), WHITE, w=120, wght=700),
                            T("Limit Cap", [("LIMIT", "")], lx, ly + S(28), S(12), "FFB8B6BC", w=120, wght=700, spacing=1)],
                  0, 0, 0, bind("limit", OPACITY, "2:5")))
    k = k0
    k.append(node("Labels", labels, 0, 0, 0, bind("introBars", OPACITY)))
    # traffic light: a small native lamp under the limit, not a wash over the speed field
    tlx, tly = P(1412, 390)
    for nm, c, conv in (("Red", "FF3B30", "2:11"), ("Green", "32D583", "2:12"), ("Yellow", "FFC247", "2:13")):
        lamp = [shape(nm + " Signal Spill", ellipse(S(32)) + fill("26" + c, glow(S(9)))), shape(nm + " Signal", ellipse(S(12)) + fill("FF" + c))]
        k.append(node(nm + " Traffic", lamp, tlx, tly, 0, bind("light", OPACITY, conv)))
    # turn telltales: hidden unless the lamp is lit (and lit once together at start-up, as a bulb check)
    for nm, x, s, prop in (("Blink L", 500, -1, "lblink"), ("Blink R", 1672, 1, "rblink")):
        pts = [(-s * 13, -6), (s * 1, -6), (s * 1, -14), (s * 15, 0), (s * 1, 14), (s * 1, 6), (-s * 13, 6)]
        pts = [(S(a), S(b)) for a, b in pts]
        cx2, cy2 = P(x, 311)
        lit_k = [shape(f"{nm} Glow", poly(pts) + fill("88FFB020", glow(S(8)))), shape(f"{nm} Lit", poly(pts) + fill("FFFFB020"))]
        k.append(node(nm, lit_k, cx2, cy2, 0, bind(prop, OPACITY, "8:68")))
    return node("Centre", k)


# ---------------------------------------------------------------- side gauges (target left side; the right mirrors)
def rail(off):
    """One rail of the left gauge, offset along the true normal so the two rails stay parallel round the turn."""
    p = cubic_points((57, 312), (70, 380), (85, 455), (104, 518))
    p += cubic_points((104, 518), (114, 551), (133, 557), (160, 557))[1:]
    p += [(186, 557)]
    out = []
    for i, (x, y) in enumerate(p):
        a, b = p[max(0, i - 1)], p[min(len(p) - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(dx, dy) or 1
        out.append((x + off * dy / ln, y - off * dx / ln))
    return out


def along(pts, k):
    seg = [math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    total = sum(seg)
    out, acc, j = [], 0.0, 0
    for i in range(k + 1):
        want = total * i / k
        while j < len(seg) - 1 and acc + seg[j] < want:
            acc += seg[j]
            j += 1
        f = (want - acc) / seg[j] if seg[j] else 0
        (x0, y0), (x1, y1) = pts[j], pts[j + 1]
        out.append((x0 + (x1 - x0) * f, y0 + (y1 - y0) * f))
    return out


def gauge(name, side, prop, convs, top_lbl, bot_lbl, icon, red_at):
    m = (lambda p: P(*p)) if side < 0 else (lambda p: P(2172 - p[0], p[1]))
    k = []
    off_k, data_k, sweep_k = [], [], []
    N = 8
    for lane, off in (("Outer", 0.0), ("Inner", -14.0)):
        pts = rail(-off)[::-1]                        # bottom first
        marks = along(pts, N * 10)
        for i in range(N):
            seg = [m(p) for p in marks[i * 10: i * 10 + 9]]        # a short gap between segments
            red = (red_at == "bottom" and i == 0) or (red_at == "top" and i == N - 1)
            lit = "FFFF3025" if red else LIT
            off_k.append(shape(f"{name} {lane} {i} Off", poly(seg, False) + stroke(UNLIT, S(5), "", "round")))
            data_k.append(shape(f"{name} {lane} {i}", poly(seg, False) + stroke(lit, S(5), "", "round"), 0, 0, 0, bind(prop, OPACITY, convs[i])))
            sweep_k.append(shape(f"{name} {lane} {i} Sweep", poly(seg, False) + stroke(lit, S(5), "", "round"), 0, 0, 0, bind("introSweep", OPACITY, f"9:{90 + i}")))
    k.append(node(f"{name} Rails", off_k, 0, 0, 0, bind("introMain", OPACITY)))
    k.append(node(f"{name} Live", data_k, 0, 0, 0, bind("introBars", OPACITY)))
    k.append(node(f"{name} Sweep", sweep_k))
    tx, ty = m((86, 300))
    bx_, by_ = m((165, 515))
    k.append(T(f"{name} Top", [(top_lbl, "")], tx, ty, S(18), WHITE, w=60, wght=760))
    k.append(T(f"{name} Bottom", [(bot_lbl, "")], bx_, by_, S(20), WHITE, w=60, wght=760))
    ix, iy = m((132, 390))
    k.append(node(f"{name} Icon", icon, ix, iy))
    return node(name, [k[0], k[1], k[2], node(f"{name} Marks", k[3:], 0, 0, 0, bind("introBars", OPACITY))])


def temp_icon():
    s = S(1)
    return [shape("Therm Stem", rect(6 * s, 26 * s, 0, -6 * s, 3 * s) + stroke(WHITE, 2.2 * s)),
            shape("Therm Bulb", rect(12 * s, 12 * s, 0, 10 * s, 6 * s) + fill(WHITE)),
            shape("Therm Marks", poly([(4 * s, -14 * s), (10 * s, -14 * s)], False) + poly([(4 * s, -8 * s), (10 * s, -8 * s)], False) + stroke(WHITE, 2 * s)),
            shape("Therm Waves", poly([(-16 * s, 20 * s), (-10 * s, 18 * s), (-4 * s, 20 * s), (2 * s, 18 * s), (8 * s, 20 * s), (14 * s, 18 * s)], False) + stroke(WHITE, 2 * s))]


def battery_icon():
    s = S(1)
    return [shape("Bat Body", rect(30 * s, 20 * s, 0, 0, 3 * s) + stroke(WHITE, 2.2 * s)),
            shape("Bat Tips", rect(5 * s, 4 * s, -8 * s, -12 * s, 1) + fill(WHITE) + ""),
            shape("Bat Tip R", rect(5 * s, 4 * s, 8 * s, -12 * s, 1) + fill(WHITE)),
            shape("Bat Plus", poly([(-11 * s, 0), (-5 * s, 0)], False) + poly([(-8 * s, -3 * s), (-8 * s, 3 * s)], False) + poly([(5 * s, 0), (11 * s, 0)], False) + stroke(WHITE, 1.8 * s))]


# ---------------------------------------------------------------- the bottom tab
def tab():
    L = [(475, 628), (545, 576), (1086, 571)]
    pts = catmull([(475, 628), (548, 576), (800, 572), (1086, 571), (1372, 572), (1624, 576), (1697, 628)], 8)
    pts = [P(x, y) for x, y in pts]
    k = [shape("Tab Fill", poly(pts) + fill("40080C12")),
         shape("Tab Line", poly(pts, False) + grad_stroke(1.4, [("40394149", 0), ("90606670", 0.5), ("40394149", 1)], 0, 0, W, 0))]
    for nm, x, runs in (("Clock", 608, [("07", bind("time", TEXT, "2:28")), (":", ""), ("25", bind("time", TEXT, "2:30"))]),
                        ("Odo", 1085, [("042180", bind("odo", TEXT, "2:1")), (" KM", "", S(13))]),
                        ("Charge", 1572, [("69", bind("energy", TEXT, "2:17")), ("%", "", S(14))])):
        cx, cy = P(x, 608)
        k.append(T(nm, runs, cx, cy, S(27), "FFE4E8EA", w=320, wght=640, sx=1.12))
    return node("Tab", k, 0, 0, 0, bind("introBars", OPACITY))


# ---------------------------------------------------------------- blind spots light the lens tip on that side
def blind():
    """Blind spots light the lens tip on that side: a soft amber glow, red when urgent (it replaces amber).
    A radial fill clipped to the glass; a feathered tip polygon does not render in Rive."""
    k = []
    lid = nid()
    k.append(f'<Shape x="0" y="0" rotation="0" name="Blind Clip" id="{lid}">{poly(lens_pts())}</Shape>')
    clip = f'<ClippingShape sourceId="{lid}" name="Clip"/>'

    def tip_glow(name, cx, cy, c, a):
        r = S(210)
        sh = shape(name, ellipse(2 * r) + radial([(a + c, 0), (a[:1] + "0" + c if False else "40" + c, 0.45), ("00" + c, 1)], r))
        i = sh.rindex("</Shape>")
        return f'<Node x="{cx:.2f}" y="{cy:.2f}" scaleX="0.55" name="{name} Holder">{sh[:i] + clip + sh[i:]}</Node>'

    for nm, side, prop, urg in (("Blind L", -1, "lbs", "9:143"), ("Blind R", 1, "rbs", "9:144")):
        cx, cy = P(1086 + side * 1050, 395)
        k.append(node(nm, [tip_glow(f"{nm} Amber", cx, cy, "FFB020", "AA")], 0, 0, 0, bind(prop, OPACITY, "9:142")))
        k.append(node(f"{nm} Urgent", [tip_glow(f"{nm} Red", cx, cy, "FF3025", "CC")], 0, 0, 0, bind(prop, OPACITY, urg)))
    return node("Blind Spots", k)


def instrument():
    temp = gauge("Temp", -1, "tmax", [f"9:{60 + i}" for i in range(8)], "H", "C", temp_icon(), "top")
    batt = gauge("Battery", 1, "energy", [f"9:{50 + i}" for i in range(8)], "F", "E", battery_icon(), "bottom")
    return node("Instrument", [lens(), centre(), power(), temp, batt, tab(), blind()])


def scene():
    # the native copy lives in its own 2280 x 1080 artboard, shown through a nested leaf with fit contain:
    # centred on a 19.5:9 phone, shrunk as a whole on a 4:3 tablet. Hidden once the GPU layer runs.
    sid = nid()
    native = (f'<LayoutComponent width="100" height="100" styleId="{sid}" name="Native Slot" id="{nid()}">{bind("fx", OPACITY, "2:43")}'
              f'<LayoutComponentStyle positionTypeValue="absolute" layoutWidthScaleType="fixed" layoutHeightScaleType="fixed" '
              f'widthUnitsValue="percent" heightUnitsValue="percent" positionLeft="0" positionLeftUnitsValue="points" '
              f'positionTop="0" positionTopUnitsValue="points" name="S" id="{sid}"/>'
              f'<NestedArtboardLeaf artboardId="6:21" fit="1" alignmentX="0" alignmentY="0" name="Native Scene" id="{nid()}"/>'
              f'</LayoutComponent>')
    native_ab = f'''    <Artboard defaultStateMachineId="6:25" viewModelId="1:1" styleId="6:22" clip="true"
              x="0" y="1300" width="{W}" height="{H}" name="Native" id="6:21">
        <LayoutComponentStyle name="Native Style" id="6:22"/>
        {instrument()}
        {SM.format(s=" N", a="6:24", n="Native", m="6:25", l="6:26", st="6:27")}
    </Artboard>'''
    dash = f'''    <Artboard defaultStateMachineId="5:1" viewModelId="1:1" viewModelInstanceId="1:2" styleId="3:2"
              clip="true" width="{W}" height="{H}" name="Dash" id="3:1">
        <LayoutComponentStyle name="Dash Style" id="3:2"/>
        <Fill name="Background"><SolidColor colorValue="FF000000" name="C"/></Fill>
        {fx_layer()}
        {native}
        {SM.format(s="", a="4:1", n="Dash", m="5:1", l="5:10", st="5:11")}
    </Artboard>'''
    inst = f'''    <Artboard defaultStateMachineId="6:5" viewModelId="1:1" styleId="6:2" clip="true"
              x="2600" y="0" width="{W}" height="{H}" name="Instrument" id="6:1">
        <LayoutComponentStyle name="Instrument Style" id="6:2"/>
        <Fill name="Background"><SolidColor colorValue="FF000000" name="C"/></Fill>
        {instrument()}
        {SM.format(s=" I", a="6:4", n="Instrument", m="6:5", l="6:6", st="6:7")}
    </Artboard>'''
    return f'''<Rive version="1" kind="fragment">
    <ScriptAsset file="v3_fx.luau" name="v3_fx" id="0:79"/>
{dash}
{inst}
{native_ab}
</Rive>
'''


def merged(doc):
    parts = []
    for f in ("viewmodel.part", "converters.part", "mine.part", "v3.part"):
        s = open(f).read()
        s = re.sub(r"<Rive[^>]*>", "", s, count=1)
        parts.append(s[:s.rindex("</Rive>")])
    return doc.replace("</Rive>", "\n".join(parts) + "\n</Rive>")


if __name__ == "__main__":
    open("scene.rml", "w").write(merged(scene()))
    print("scene.rml written")
