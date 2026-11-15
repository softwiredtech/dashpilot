"""Felina in Rive, on the real DashViewModel.

Three instruments, each a fixed layout box with hand-placed art inside a Node:
energy well (left), speed dial (centre), assist well (right). Top/bottom bars and the
blinker / blind-spot edge light are layouts. Every live value is a data binding.

RML draw order: the FIRST sibling draws on TOP. stack() takes paint order (bottom
first) and reverses it, so read every list bottom-up.
"""
import math

_id = [0]


def nid():
    _id[0] += 1
    return f"7:{_id[0]}"


VM = {
    "speed": "1:10", "gear": "1:11", "steer": "1:12", "lblink": "1:13", "rblink": "1:14", "door": "1:15",
    "buckle": "1:16", "odo": "1:17", "set": "1:18", "limit": "1:19", "lbs": "1:20", "rbs": "1:21",
    "light": "1:24", "stop": "1:25", "energy": "1:26", "power": "1:29", "volt": "1:30", "amps": "1:31",
    "tmin": "1:34", "tmax": "1:35", "time": "1:36", "phone": "1:37", "adas": "1:50", "lane": "1:51",
    "imperial": "1:55", "showPhone": "1:57", "showCar": "1:58", "showOdo": "1:59", "source": "1:70", "fx": "1:80", "laneScroll": "1:81", "introMain": "1:82", "introSide": "1:83", "introBars": "1:84", "introCount": "1:85", "introSweep": "1:86", "fxWidth": "1:87",
}
X, Y, ROT, SCALE_X, SCALE_Y, OPACITY, TEXT, TRIM_START, TRIM_END = 13, 14, 15, 16, 17, 18, 268, 114, 115

# palette: warm ink on black, one orange, signal colours only for signals
INK = "FFF6EFE4"      # warm white
INK2 = "FFA49E95"     # engraved grey
INK3 = "FF5C5852"     # dim engraving
TRACK = "FF2B2825"
ORANGE = "FFFF8A1F"
RED, AMBER, GREEN, BLUE = "FFFF4436", "FFFFB020", "FF39D46E", "FF5AA9FF"
REG, MED = "2:900", "2:901"   # Inter Regular / SemiBold


def bind(prop, key, conv=None):
    c = f' converterId="{conv}"' if conv else ""
    return f'<DataBindContext sourcePathIds="1:1-{VM[prop]}" propertyKey="{key}"{c}/>'


def stack(children):
    return "".join(reversed([c for c in children if c]))


# ---------------------------------------------------------------- paint + geometry helpers
def solid(argb):
    return f'<SolidColor colorValue="{argb}" name="C"/>'


def glow(strength, ox=0, oy=0, inner=False):
    i = ' inner="true"' if inner else ""
    return f'<Feather strength="{strength}" offsetX="{ox}" offsetY="{oy}"{i} name="Glow"/>'


def fill(argb, extra=""):
    return f'<Fill name="F">{solid(argb)}{extra}</Fill>'


def grad_fill(kind, stops, extra="", sx=0, sy=0, ex=0, ey=0):
    s = "".join(f'<GradientStop colorValue="{c}" position="{p}"/>' for c, p in stops)
    xy = f' startX="{sx}" startY="{sy}" endX="{ex}" endY="{ey}"' if kind == "LinearGradient" else ""
    return f'<Fill name="F"><{kind}{xy} name="G">{s}</{kind}>{extra}</Fill>'


def stroke(argb, w, extra="", cap="butt"):
    return f'<Stroke thickness="{w}" cap="{cap}" name="S">{solid(argb)}{extra}</Stroke>'


def grad_stroke(w, stops, sx, sy, ex, ey, extra="", cap="butt"):
    st = "".join(f'<GradientStop colorValue="{c}" position="{p}"/>' for c, p in stops)
    return (f'<Stroke thickness="{w}" cap="{cap}" name="S"><LinearGradient startX="{sx}" startY="{sy}" endX="{ex}" endY="{ey}" name="G">'
            f'{st}</LinearGradient>{extra}</Stroke>')


def rim_light(name, d, w, hot, cx, cy, sides=True, bottom=True, blur=14):
    """Light catching a machined edge: hot at the sides and the bottom, dark at the top."""
    r = d / 2
    out = []
    if sides:
        st = [(hot, 0), ("00" + hot[2:], 0.32), ("00" + hot[2:], 0.68), (hot, 1)]
        out += [shape(f"{name} Side Glow", ellipse(d) + grad_stroke(w * 3, st, -r, 0, r, 0, glow(blur)), cx, cy),
                shape(f"{name} Side", ellipse(d) + grad_stroke(w, st, -r, 0, r, 0), cx, cy)]
    if bottom:
        st = [("00" + hot[2:], 0), ("00" + hot[2:], 0.55), (hot, 1)]
        out += [shape(f"{name} Low Glow", ellipse(d) + grad_stroke(w * 3, st, 0, -r, 0, r, glow(blur)), cx, cy),
                shape(f"{name} Low", ellipse(d) + grad_stroke(w, st, 0, -r, 0, r), cx, cy)]
    return out


def trim(start=0.0, end=1.0, binds=""):
    return f'<TrimPath start="{start}" end="{end}" name="Trim">{binds}</TrimPath>'


def shape(name, body, x=0, y=0, rot=0.0, binds=""):
    return f'<Shape x="{x:.2f}" y="{y:.2f}" rotation="{rot:.6f}" name="{name}">{binds}{body}</Shape>'


def node(name, kids, x=0, y=0, rot=0.0, binds=""):
    return f'<Node x="{x:.2f}" y="{y:.2f}" rotation="{rot:.6f}" name="{name}">{binds}{stack(kids)}</Node>'


def ellipse(d):
    return f'<Ellipse width="{d}" height="{d}" name="P"/>'


def rect(w, h, x=0, y=0, r=0):
    rr = " ".join(f'cornerRadius{c}="{r}"' for c in ("TL", "TR", "BL", "BR")) if r else ""
    return f'<Rectangle x="{x:.2f}" y="{y:.2f}" width="{w}" height="{h}" originX="0.5" originY="0.5" {rr} name="P"/>'


def poly(points, closed=True):
    v = "".join(f'<StraightVertex x="{px:.2f}" y="{py:.2f}"/>' for px, py in points)
    return f'<PointsPath isClosed="{"true" if closed else "false"}" name="P">{v}</PointsPath>'


def text(name, runs, x, y, size, color=INK, font=MED, w=600, h=None, align="center", ox=0.5, spacing=0, binds="", glow_px=0):
    """Free-placed text. runs: (text, bind_xml) or (text, bind_xml, size, color, font)."""
    styles, rs = {}, []
    for r in runs:
        t, b = r[0], r[1]
        key = (r[2] if len(r) > 2 else size, r[3] if len(r) > 3 else color, r[4] if len(r) > 4 else font)
        if key not in styles:
            styles[key] = nid()
        rs.append(f'<TextValueRun styleId="{styles[key]}" text="{t}" name="Run">{b}</TextValueRun>')
    paints = "".join(f'<TextStylePaint fontSize="{s}" letterSpacing="{spacing}" fontAssetId="{f}" name="S" id="{i}">'
                     f'{fill(c, glow(glow_px) if glow_px else "")}<TextStyleFeature tag="1953396077" featureValue="1" name="tnum"/></TextStylePaint>'
                     for (s, c, f), i in styles.items())
    h = h or int(size * 1.35)
    t = (f'<Text x="{x:.2f}" y="{y:.2f}" sizingValue="fixed" width="{w}" height="{h}" originX="{ox}" originY="0.5" '
         f'alignValue="{align}" verticalAlignValue="middle" wrapValue="noWrap" name="{name}" id="{nid()}">'
         f'{paints}{"".join(rs)}</Text>')
    return node(name + " Holder", [t], 0, 0, 0, binds) if binds else t


def caps(name, t, x, y, size=17, color=INK2, spacing=3.5, binds="", align="center", ox=0.5, w=300):
    return text(name, [(t, "")], x, y, size, color, MED, w=w, align=align, ox=ox, spacing=spacing, binds=binds)


def polar(cx, cy, r, a):
    return cx + r * math.sin(a), cy - r * math.cos(a)


def box(name, w, h, kids, push=None):
    """push = (centre x, centre y, intro property): the art scales up from 93% around its own centre and fades in."""
    sid = nid()
    art = stack(kids)
    if push:
        px, py, prop = push
        art = (f'<Node x="{px}" y="{py}" name="{name} Push">'
               f'{bind(prop, SCALE_X, "8:130")}{bind(prop, SCALE_Y, "8:130")}{bind(prop, OPACITY)}'
               f'<Node x="{-px}" y="{-py}" name="{name} Push Origin">{art}</Node></Node>')
    return (f'<LayoutComponent width="{w}" height="{h}" styleId="{sid}" name="{name}" id="{nid()}">'
            f'<LayoutComponentStyle layoutWidthScaleType="fixed" layoutHeightScaleType="fixed" name="S" id="{sid}"/>'
            f'<Node name="{name} Art">{art}</Node></LayoutComponent>')   # bare Shapes in a layout get stretched


# ---------------------------------------------------------------- centre: the speed dial
SW0 = math.radians(225)          # 0 km/h sits at 7:30, the scale runs 270 degrees clockwise
SWEEP = math.radians(270)


def speed_dial():
    W, H = 940, 940
    cx, cy, R = 470, 470, 440     # R = outer rim
    hub = 282
    k = []
    # body: warm haze around a machined bezel, the face recessed inside it
    k += [shape("Haze", ellipse(2 * R + 120) + grad_fill("RadialGradient", [("00000000", 0.55), ("22FF7A1A", 0.82), ("00000000", 1)]), cx, cy),
          shape("Dial Shadow", ellipse(2 * R + 40) + fill("FF000000", glow(50, 0, 24)), cx, cy),
          shape("Bezel", ellipse(2 * R - 18) + grad_stroke(22, [("FF3B3732", 0), ("FF1C1A18", 0.5), ("FF0E0D0C", 1)], 0, -R, 0, R), cx, cy),
          shape("Dial Face", ellipse(2 * R - 40) + grad_fill("RadialGradient", [("FF0F0E0D", 0), ("FF090908", 0.7), ("FF040404", 1)]), cx, cy),
          shape("Face Inner Shadow", ellipse(2 * R - 40) + fill("CC000000", glow(28, 0, 10, inner=True)), cx, cy)]
    k += rim_light("Bezel Outer", 2 * R, 2.5, "FFFF8A1F", cx, cy)
    k += rim_light("Bezel Inner", 2 * R - 40, 1.5, "CCFFB066", cx, cy, bottom=False, blur=8)
    k.append(shape("Bezel Top Sheen", ellipse(2 * R - 4) + stroke("40FFFFFF", 1.2, trim(0.9, 1.1)), cx, cy))
    # scale track and graduations
    # the track covers the scale only (0 to 160, 270 deg); the open bottom is left clear for P R N D
    k.append(shape("Scale Track", ellipse(2 * (R - 66)) + stroke(TRACK, 1.5, trim(0, SWEEP / (2 * math.pi))), cx, cy, SW0))
    for i in range(0, 81):
        v = i * 2
        a = SW0 + v / 160 * SWEEP
        if v % 20 == 0:
            L, Wd, c = 30, 4, "FFFFE2BC"
            k.append(shape(f"Tick Glow {v}", rect(10, L + 6, 0, -(R - 30 - L / 2)) + fill("66FF9A3C", glow(10)), cx, cy, a))
        elif v % 10 == 0:
            L, Wd, c = 19, 2.5, "E6F6EFE4"
        else:
            L, Wd, c = 10, 1.5, "80F6EFE4"
        k.append(shape(f"Tick {v}", rect(Wd, L, 0, -(R - 30 - L / 2)) + fill(c), cx, cy, a))
        if v % 20 == 0:
            tx, ty = polar(cx, cy, R - 108, a)
            k.append(text(f"Num {v}", [(str(v), "")], tx, ty, 40, INK, REG, w=120))
    # over-limit: the scale between the limit and the needle glows red
    over = trim(0, 0.5, bind("limit", TRIM_START, "8:61") + bind("speed", TRIM_END, "8:61"))
    k.append(node("Over Limit Arc", [
        shape("Over Glow", ellipse(2 * (R - 45)) + stroke("99FF3A2E", 26, over + glow(16)), 0, 0, SW0),
        shape("Over Line", ellipse(2 * (R - 45)) + stroke("CCFF4436", 6, over), 0, 0, SW0),
    ], cx, cy, 0, bind("speed", OPACITY, "8:73")))
    # limit index: a red triangle on the rim at the posted limit
    tri = poly([(-11, -(R - 2)), (11, -(R - 2)), (0, -(R - 24))])
    k.append(node("Limit Index", [shape("Limit Tri", tri + fill(RED))], cx, cy, SW0, bind("limit", ROT, "8:62")))
    # cruise set: a pale blue chevron just inside the track
    chev = poly([(-9, -(R - 70)), (9, -(R - 70)), (0, -(R - 86))])
    k.append(node("Set Pivot", [node("Set Index", [shape("Set Tri", chev + fill(BLUE))], 0, 0, 0, bind("adas", OPACITY, "2:41"))],
                  cx, cy, SW0, bind("set", ROT, "8:62")))
    # needle: a hot wedge of light from the hub rim through the scale
    tip, base = -(R - 22), -(hub - 6)
    wedge = poly([(-1.5, tip), (1.5, tip), (6, base), (-6, base)])
    k.append(node("Needle", [
        shape("Needle Bloom", rect(40, abs(tip - base), 0, (tip + base) / 2, 20) + fill("88FF7A1A", glow(34))),
        shape("Needle Glow", wedge + fill("CCFF8A1F", glow(10))),
        shape("Needle Blade", wedge + grad_fill("LinearGradient", [("FFFFE7A8", 0), ("FFFF9A2E", 0.45), ("FFFF6A10", 1)], sx=0, sy=tip, ex=0, ey=base)),
    ], cx, cy, SW0, bind("speed", ROT, "8:137")))
    # park: the scale rests under a dark veil
    k.append(node("Park Veil", [shape("Veil", ellipse(2 * 364) + stroke("B3000000", 148))], cx, cy, 0, bind("gear", OPACITY, "8:100")))
    # reverse: white reverse light rises along the bottom of the bezel
    rev_trim = trim(0.36, 0.64)
    k.append(node("Reverse Light", [
        shape("Rev Glow", ellipse(2 * R) + stroke("88FFFFFF", 16, rev_trim + glow(20), "round")),
        shape("Rev Line", ellipse(2 * R) + stroke("EEFFFFFF", 3, rev_trim, "round")),
    ], cx, cy, 0, bind("gear", OPACITY, "8:102")))
    # the raised hub: second plane, casting onto the face
    k += [shape("Hub Shadow", ellipse(2 * hub + 30) + fill("FF000000", glow(40, 0, 30)), cx, cy),
          shape("Hub", ellipse(2 * hub) + grad_fill("RadialGradient", [("FF161513", 0), ("FF0C0B0A", 0.8), ("FF080807", 1)]), cx, cy),
          shape("Hub Rim", ellipse(2 * hub) + stroke("FF3A332C", 2), cx, cy),
          shape("Hub Sheen", ellipse(2 * hub - 4) + grad_fill("LinearGradient", [("14FFFFFF", 0), ("00FFFFFF", 0.45)], sx=0, sy=-hub, ex=0, ey=hub), cx, cy),
          shape("Hub Specular", ellipse(2 * hub - 10) + stroke("38FFFFFF", 1.5, trim(0.88, 1.12)), cx, cy)]
    k += rim_light("Hub Lip", 2 * hub, 2, "DDFF8A1F", cx, cy, bottom=False, blur=12)
    # moments: the instrument changes light with the road and the assist state
    k.append(node("Assist Light", [shape("Assist Lip", ellipse(2 * hub) + stroke("CC5AA9FF", 2, glow(6)))], cx, cy, 0, bind("adas", OPACITY, "8:72")))
    k.append(node("Red Wash", [shape("Red Rim", ellipse(2 * R) + stroke("DDFF3A2E", 3, glow(8)))], cx, cy, 0, bind("light", OPACITY, "8:70")))
    k.append(node("Green Wash", [shape("Green Rim", ellipse(2 * R) + stroke("DD39D46E", 3, glow(8)))], cx, cy, 0, bind("light", OPACITY, "8:71")))
    # hub contents
    k.append(node("Limit Mark", [
        shape("Limit Ring", ellipse(70) + stroke(RED, 2.5), cx, cy - 205),
        text("Limit Value", [("80", bind("limit", TEXT, "2:1"))], cx, cy - 205, 28, "FFF2ECE4", MED, w=80),
        shape("Limit Stem", rect(2, 26, cx, cy - 258) + fill("99FF4436")),
    ], 0, 0, 0, bind("limit", OPACITY, "2:5")))
    k.append(node("Speed Group", [
        text("Speed Bloom", [("88", bind("speed", TEXT, "2:1"))], cx, cy - 6, 290, "66FFB070", MED, w=560, h=330, glow_px=26),
        text("Speed", [("88", bind("speed", TEXT, "2:1"))], cx, cy - 6, 290, "FFFFF7EC", MED, w=560, h=330),
        node("Units", [caps("Unit Km", "KM/H", cx - 18, cy + 172, 26, INK2, 6, bind("imperial", OPACITY, "2:43")),
                       caps("Unit Mi", "MPH", cx - 18, cy + 172, 26, INK2, 6, bind("imperial", OPACITY, "2:41"))]),
    ], 0, 0, 0, bind("gear", OPACITY, "8:101")))
    k.append(node("Park Group", [
        text("Park P", [("P", "")], cx, cy - 16, 250, "FFF6EFE4", MED, w=400, h=300),
        caps("Parked", "PARKED", cx, cy + 128, 22, INK2, 8),
    ], 0, 0, 0, bind("gear", OPACITY, "8:100")))
    k.append(caps("Reverse Label", "REVERSE", cx, cy + 228, 22, "FFF2F2F2", 8, bind("gear", OPACITY, "8:102")))
    k.append(text("Over By", [("+", ""), ("8", bind("speed", TEXT, "8:21"))], cx + 68, cy + 172, 28, RED, MED, w=90,
                  align="left", ox=0, binds=bind("speed", OPACITY, "2:4")))
    k.append(text("Set Speed", [("SET ", "", 22, "FF7FA6CF", MED), ("85", bind("set", TEXT, "2:1"))], cx, cy + 228, 24,
                  "FFB9D4F0", MED, w=220, spacing=4, binds=bind("adas", OPACITY, "2:41")))
    # P R N D carry the numbers' circle on through the open bottom of the scale, P on the left;
    # upright like the numbers, the lit letter with an orange dot just outside it
    for i, (g, conv) in enumerate((("P", "2:11"), ("R", "2:12"), ("N", "2:13"), ("D", "2:14"))):
        a = math.radians(207 - i * 18)          # 207, 189, 171, 153 deg: 18 deg clear of 0 and 160
        gx, gy = polar(cx, cy, R - 108, a)
        dx, dy = polar(cx, cy, R - 66, a)       # the dot sits on the scale track's line
        k.append(text(f"Gear {g} Dim", [(g, "")], gx, gy, 40, INK3, MED, w=70))
        k.append(node(f"Gear {g} On", [
            text(f"Gear {g} Lit", [(g, "")], gx, gy, 40, INK, MED, w=70),
            shape(f"Gear {g} Dot", ellipse(10) + fill(ORANGE, ""), dx, dy),
            shape(f"Gear {g} Dot Glow", ellipse(18) + fill("88FF8A1F", glow(8)), dx, dy),
        ], 0, 0, 0, bind("gear", OPACITY, conv)))
    # turn signals: a green arrow in each top corner beside the dial, lit in step with the real lamp
    # (fast attack, a short bulb afterglow); a faint outline stays when it is off
    arrow = [(-30, 0), (-4, -24), (-4, -11), (28, -11), (28, 11), (-4, 11), (-4, 24)]
    for side, prop in ((-1, "lblink"), (1, "rblink")):
        pts = [(side * -x * 1.3, y * 1.3) for x, y in arrow]   # the left arrow points left, the right one right
        ax, ay = cx + side * 372, cy - 368
        nm = "Left" if side < 0 else "Right"
        k.append(shape(f"Turn {nm} Off", poly(pts) + fill("1FF6EFE4"), ax, ay))
        k.append(node(f"Turn {nm} Afterglow", [shape(f"Turn {nm} Halo", poly(pts) + fill("8839D46E", glow(18)))],
                      ax, ay, 0, bind(prop, OPACITY, "8:69")))
        k.append(node(f"Turn {nm} On", [shape(f"Turn {nm} Lamp", poly(pts) + fill(GREEN))], ax, ay, 0, bind(prop, OPACITY, "8:68")))
    return box("Speed Instrument", W, H, k, (cx, cy, "introMain"))


# ---------------------------------------------------------------- wells
SW_, SH_ = 600, 680             # satellite box; the dial sits a little low, tucked beside the big one
SCX, SCY, SR = 300, 380, 268    # satellite centre and outer rim


def sat_face(name):
    """Same machined language as the speed dial, smaller."""
    cx, cy, R = SCX, SCY, SR
    k = [shape(f"{name} Shadow", ellipse(2 * R + 30) + fill("FF000000", glow(40, 0, 20)), cx, cy),
         shape(f"{name} Bezel", ellipse(2 * R - 14) + grad_stroke(16, [("FF3B3732", 0), ("FF1C1A18", 0.5), ("FF0E0D0C", 1)], 0, -R, 0, R), cx, cy),
         shape(f"{name} Face", ellipse(2 * R - 30) + grad_fill("RadialGradient", [("FF0F0E0D", 0), ("FF090908", 0.7), ("FF040404", 1)]), cx, cy),
         shape(f"{name} Inner Shadow", ellipse(2 * R - 30) + fill("CC000000", glow(24, 0, 8, inner=True)), cx, cy)]
    k += rim_light(f"{name} Rim", 2 * R, 1.6, "70FF8A1F", cx, cy, blur=10)
    k += rim_light(f"{name} Inner Rim", 2 * R - 30, 1.0, "40FFB066", cx, cy, bottom=False, blur=6)
    return k


def regen_rim():
    st = [("FF39D46E", 0), ("0039D46E", 0.5), ("FF39D46E", 1)]
    d = 2 * SR
    return node("Regen Light", [
        shape("Regen Rim Glow", ellipse(d) + grad_stroke(12, st, -SR, 0, SR, 0, glow(22)), SCX, SCY),
        shape("Regen Rim", ellipse(d) + grad_stroke(2.5, st, -SR, 0, SR, 0), SCX, SCY),
    ], 0, 0, 0, bind("power", OPACITY, "8:75"))


def energy_well():
    """Left satellite: battery on the top half, power on the bottom half, numbers in the middle."""
    k = sat_face("Energy")
    k.append(regen_rim())
    cx, cy, R = SCX, SCY, 200
    BA = math.radians(75)                       # each half sweeps 150 degrees, leaving gaps at 3 and 9
    # top half: battery, 0 at 8 o'clock to 100 at 4 o'clock... up and over
    k.append(shape("Charge Track", ellipse(2 * R) + stroke(TRACK, 3, trim(0, 150 / 360)), cx, cy, -BA))
    k.append(shape("Charge Glow", ellipse(2 * R) + stroke("8839D46E", 14, trim(0, 0.3, bind("energy", TRIM_END, "8:134")) + glow(14), "round"), cx, cy, -BA))
    k.append(shape("Charge Arc", ellipse(2 * R) + stroke(GREEN, 4, trim(0, 0.3, bind("energy", TRIM_END, "8:134")), "round"), cx, cy, -BA))
    k.append(node("Charge Knob", [shape("Knob", ellipse(12) + fill(INK), 0, -R)], cx, cy, 0, bind("energy", ROT, "8:135")))
    for v in range(0, 101, 5):
        a = -BA + v / 100 * 2 * BA
        L, c = (16, "E6F6EFE4") if v % 20 == 0 else (8, "70F6EFE4")
        k.append(shape(f"Charge Tick {v}", rect(2.5 if v % 20 == 0 else 2, L, 0, -(R - 14 - L / 2)) + fill(c), cx, cy, a))
        if v % 20 == 0:
            tx, ty = polar(cx, cy, R - 46, a)
            k.append(text(f"Charge Num {v}", [(str(v), "")], tx, ty, 19, INK2, REG, w=70))
    # bottom half: power, zero at 6 o'clock, regen to the left, power to the right
    k.append(shape("Power Track", ellipse(2 * R) + stroke(TRACK, 3, trim(0, 150 / 360)), cx, cy, math.pi - BA))
    k.append(shape("Power Fill Glow", ellipse(2 * R) + stroke("88FF8A1F", 12, trim(0, 0, bind("power", TRIM_END, "8:95")) + glow(12), "round"), cx, cy, math.pi, ) .replace('rotation="3.141593"', 'rotation="3.141593" scaleX="-1"'))
    k.append(shape("Power Fill", ellipse(2 * R) + stroke(ORANGE, 4, trim(0, 0, bind("power", TRIM_END, "8:95")), "round"), cx, cy, math.pi).replace('rotation="3.141593"', 'rotation="3.141593" scaleX="-1"'))
    k.append(shape("Regen Fill Glow", ellipse(2 * R) + stroke("8839D46E", 12, trim(0, 0, bind("power", TRIM_END, "8:97")) + glow(12), "round"), cx, cy, math.pi))
    k.append(shape("Regen Fill", ellipse(2 * R) + stroke(GREEN, 4, trim(0, 0, bind("power", TRIM_END, "8:97")), "round"), cx, cy, math.pi))
    for i, v in enumerate(range(-100, 101, 10)):
        a = math.pi - v / 100 * BA
        major = v % 50 == 0
        L = 16 if major else 8
        k.append(shape(f"Power Tick {v}", rect(2.5 if major else 2, L, 0, -(R - 14 - L / 2)) + fill("E6F6EFE4" if major else "70F6EFE4"), cx, cy, a))
    blade = R - 26
    k.append(node("Power Needle", [
        shape("PN Glow", rect(10, 54, 0, -(blade - 27), 5) + fill("99FF7A1A", glow(10))),
        shape("PN", rect(4, 54, 0, -(blade - 27), 2) + fill(ORANGE)),
    ], cx, cy, math.pi, bind("power", ROT, "8:99")))
    # the middle: the two readouts
    k.append(text("Charge", [("69", bind("energy", TEXT, "8:133")), ("%", "", 30, INK2, REG)], cx, cy - 44, 72, INK, MED, w=320))
    k.append(caps("Charge Label", "BATTERY", cx, cy + 4, 14))
    k.append(text("Kilowatts", [("31", bind("amps", TEXT, "8:136")), (" kW", "", 22, INK2, REG)], cx, cy + 56, 38, INK, MED, w=320))
    rx, ry = polar(cx, cy, R - 50, math.pi + BA - 0.12)      # at the two ends of the power arc
    px, py = polar(cx, cy, R - 50, math.pi - BA + 0.12)
    k.append(caps("Regen L", "REGEN", rx + 8, ry, 13, "FF6FCF95", 2.5, w=110))
    k.append(caps("Power L", "POWER", px - 8, py, 13, "FFFFA860", 2.5, w=110))
    return box("Energy Instrument", SW_, SH_, k, (SCX, SCY, "introSide"))


def outline_car(name, color, glow_color=None):
    # top-down outline, nose up: body, raked windscreen, glass roof, rear window, mirrors
    body = rect(64, 126, 0, 0, 24)
    screen = poly([(-24, -30), (24, -30), (20, -10), (-20, -10)])
    roof = rect(40, 38, 0, 12, 8)
    rear = poly([(-20, 34), (20, 34), (23, 46), (-23, 46)])
    hood = poly([(-14, -58), (-10, -40), (10, -40), (14, -58)], closed=False)
    mirrors = rect(8, 6, -36, -16, 2) + rect(8, 6, 36, -16, 2)
    kids = [shape(f"{name} Body", body + stroke(color, 2.5)),
            shape(f"{name} Glass", screen + roof + rear + stroke(color, 1.6)),
            shape(f"{name} Hood", hood + stroke(color, 1.2)),
            shape(f"{name} Mirrors", mirrors + fill(color))]
    if glow_color:
        kids.insert(0, shape(f"{name} Halo", body + stroke(glow_color, 10, glow(14))))
    return kids


def tinted_car(name, argb):
    """The same glossy car, tinted: multiply turns white paint into the warning colour, black stays black."""
    halo = shape(f"{name} Halo", rect(70, 150, 0, 0, 30) + fill("66" + argb[2:], glow(26)))
    img = (f'<Image assetId="9:20" x="0" y="0" scaleX="0.46" scaleY="0.46" blendModeValue="screen" name="{name} Image" id="{nid()}"/>')
    tint = f'<Shape x="0" y="0" rotation="0" blendModeValue="multiply" name="{name} Tint">{rect(90, 175, 0, 0, 20)}{fill(argb)}</Shape>'
    return [halo, img, tint]


def ego_car():
    """Our car: a glossy studio render (fx2/car.png, ours), screened onto the face so its black drops out."""
    img = (f'<Image assetId="9:20" x="0" y="0" scaleX="0.5" scaleY="0.5" blendModeValue="screen" name="Ego Image" id="{nid()}"/>')
    halo = shape("Ego Floor Light", ellipse(150) + fill("22FFF2E0", glow(40)), 0, 10)
    beams = node("Reverse Beams", [
        shape("Beam L Soft", poly([(-28, 74), (-12, 74), (-2, 190), (-66, 190)]) + fill("CCFFFFFF", glow(22))),
        shape("Beam R Soft", poly([(12, 74), (28, 74), (66, 190), (2, 190)]) + fill("CCFFFFFF", glow(22))),
        shape("Beam L Core", poly([(-24, 76), (-16, 76), (-14, 150), (-40, 150)])
              + grad_fill("LinearGradient", [("CCFFFFFF", 0), ("00FFFFFF", 1)], sx=0, sy=76, ex=0, ey=150)),
        shape("Beam R Core", poly([(16, 76), (24, 76), (40, 150), (14, 150)])
              + grad_fill("LinearGradient", [("CCFFFFFF", 0), ("00FFFFFF", 1)], sx=0, sy=76, ex=0, ey=150)),
    ], 0, 0, 0, bind("gear", OPACITY, "8:102"))
    return [beams, halo, img]


def assist_well():
    k = sat_face("Assist")
    cx, cy = SCX, SCY
    # lane marks: fine dotted lines (decoration; the car does not report lanes)
    # they scroll past at the car's speed (the GPU script writes laneScroll, 0..18 px); soft ends hide the wrap
    marks = [node(f"Lane {s} {j}", [shape(f"Lane Mark {s} {j}", rect(2, 8, cx + s * 78, cy - 226 + j * 18) + fill("66F6EFE4"))],
                  0, 0, 0, bind("laneScroll", OPACITY, f"8:{110 + j}")) for s in (-1, 1) for j in range(18)]
    k.append(node("Lane Marks", marks, 0, 0, 0, bind("laneScroll", Y)))
    for side, prop, urgent in ((-1, "lbs", "8:106"), (1, "rbs", "8:107")):
        x = cx + side * 150
        k.append(node(f"Blind {side} Slide", [
            node(f"Blind {side}", tinted_car(f"Blind Car {side}", "FFFFB020"), x, cy - 50, 0, bind(prop, OPACITY, "8:105")),
            node(f"Urgent {side}", tinted_car(f"Urgent Car {side}", "FFFF4436"), x, cy - 50, 0, bind(prop, OPACITY, urgent)),
        ], 0, 0, 0, bind(prop, Y, "8:104")))
    k.append(node("Ego", ego_car(), cx, cy - 70, 0, bind("steer", ROT, "8:67")))
    k.append(node("Assist On", [shape("Assist Dot", ellipse(12) + fill(BLUE), cx - 82, cy + 108),
                                shape("Assist Dot Glow", ellipse(22) + fill("885AA9FF", glow(8)), cx - 82, cy + 108),
                                caps("Assist On Text", "ASSIST ON", cx + 14, cy + 108, 18, "FFD6E6F7", 4)],
                  0, 0, 0, bind("adas", OPACITY, "2:41")))
    k.append(caps("Assist Off", "MANUAL", cx, cy + 108, 18, INK3, 4, bind("adas", OPACITY, "2:43")))
    k.append(caps("Lane Change", "CHANGING LANE", cx, cy + 140, 14, AMBER, 3, bind("lane", OPACITY, "2:41")))
    # traffic light jewels + the distance to the stop line
    for i, (c, conv, nm) in enumerate(((RED, "2:11", "Red"), ("FFFFC53D", "2:13", "Yellow"), (GREEN, "2:12", "Green"))):
        lx = cx - 40 + i * 40          # centred in the dial
        k.append(shape(f"{nm} Socket", ellipse(26) + fill("FF1A1917") + stroke("FF2E2B27", 1.5), lx, cy + 172))
        k.append(node(f"{nm} On", [shape(f"{nm} Jewel Glow", ellipse(36) + fill("AA" + c[2:], glow(12)), lx, cy + 172),
                                   shape(f"{nm} Jewel", ellipse(20) + fill(c), lx, cy + 172)], 0, 0, 0, bind("light", OPACITY, conv)))
    k.append(node("Stop", [text("Stop Dist", [("42", bind("stop", TEXT, "8:24")), (" m", "", 16, INK2, REG)], cx, cy + 208, 24, INK, MED,
                                w=160)], 0, 0, 0, bind("stop", OPACITY, "2:5")))   # under the lights
    return box("Assist Instrument", SW_, SH_, k, (SCX, SCY, "introSide"))


# ---------------------------------------------------------------- bars and edges (layouts)
def lc(name, kids="", w=None, h=None, direction=None, align=None, gap=0, pad=None, binds=""):
    sid = nid()
    st = [f'layoutWidthScaleType="{"fixed" if w is not None else "fill"}"',
          f'layoutHeightScaleType="{"fixed" if h is not None else "fill"}"']
    if direction:
        st.append(f'flexDirectionValue="{direction}"')
    if align:
        st.append(f'layoutAlignmentType="{align}"')
    if gap:
        k = "Horizontal" if direction == "row" else "Vertical"
        st.append(f'gap{k}="{gap}" gap{k}UnitsValue="points"')
    if pad:
        for side, v in zip(("Top", "Right", "Bottom", "Left"), pad):
            st.append(f'padding{side}="{v}" padding{side}UnitsValue="points"')
    size = (f' width="{w}"' if w is not None else "") + (f' height="{h}"' if h is not None else "")
    return (f'<LayoutComponent{size} styleId="{sid}" name="{name}" id="{nid()}">{binds}'
            f'<LayoutComponentStyle {" ".join(st)} name="S" id="{sid}"/>{kids}</LayoutComponent>')


def bar_text(name, runs, w, size, color, align, binds=""):
    t = text(name, runs, w / 2 if align == "center" else (0 if align == "left" else w), 24, size, color, MED, w=w,
             h=48, align=align, ox=0.5 if align == "center" else (0 if align == "left" else 1))
    return lc(name + " Box", f'<Node name="{name} Art">{t}</Node>', w, 48, binds=binds)


def top_bar():
    clock = bar_text("Clock", [("07", bind("time", TEXT, "2:28")), (":", ""), ("25", bind("time", TEXT, "2:30"))], 260, 38, INK, "left")
    door = bar_text("Door", [("DOOR OPEN", "")], 220, 18, AMBER, "center", bind("door", OPACITY, "2:5"))
    belt = bar_text("Belt", [("SEATBELT", "")], 200, 18, RED, "center", bind("buckle", OPACITY, "2:7"))
    warn = lc("Warnings", door + belt, 440, 48, "row", "center", 16)
    source = bar_text("Source", [("demo", bind("source", TEXT))], 120, 20, INK3, "right")
    phone = bar_text("Phone", [("82", bind("phone", TEXT, "2:1")), ("%", "", 24, INK2, REG)], 120, 34, INK, "right",
                     bind("showPhone", OPACITY, "2:41"))
    right = lc("Status", source + phone, 260, 48, "row", "centerRight", 12)
    return lc("Top Bar", clock + warn + right, None, 48, "row", "spaceBetweenCenter", binds=bind("introBars", OPACITY))


def bottom_bar():
    odo = bar_text("Odo", [("ODO  ", "", 16, INK3, MED), ("42180", bind("odo", TEXT, "2:1")), (" km", "", 18, INK2, REG)],
                   360, 26, INK, "left", bind("showOdo", OPACITY, "2:41"))
    volt = bar_text("Volt", [("396", bind("volt", TEXT, "2:1")), (" V", "", 18, INK2, REG)], 200, 26, INK, "right")
    return lc("Bottom Bar", odo + volt, None, 48, "row", "spaceBetweenCenter", binds=bind("introBars", OPACITY))


def edges():
    def strip(name, side, w, color, prop, conv):
        a, b = (color, "00" + color[2:]) if side == "left" else ("00" + color[2:], color)
        g = (f'<Fill name="F"><LinearGradient startX="0" startY="0" endX="{w}" endY="0" name="G">'
             f'<GradientStop colorValue="{a}" position="0"/><GradientStop colorValue="{b}" position="1"/></LinearGradient></Fill>')
        sid = nid()
        S = side.capitalize()
        return (f'<LayoutComponent width="{w}" height="2400" styleId="{sid}" name="{name}" id="{nid()}">{bind(prop, OPACITY, conv)}'
                f'<LayoutComponentStyle positionTypeValue="absolute" layoutWidthScaleType="fixed" layoutHeightScaleType="fixed" '
                f'position{S}="0" position{S}UnitsValue="points" positionTop="0" positionTopUnitsValue="points" name="S" id="{sid}"/>'
                f'{g}</LayoutComponent>')
    return (strip("Blind L Urgent Edge", "left", 80, "CCFF4436", "lbs", "2:33") + strip("Blind L Edge", "left", 44, "CCFFB020", "lbs", "2:5")
            + strip("Blind R Urgent Edge", "right", 80, "CCFF4436", "rbs", "2:34") + strip("Blind R Edge", "right", 44, "CCFFB020", "rbs", "2:5")
            # blinker = the real lamp phase: a fast attack layer plus a slow afterglow, like a bulb
            + strip("Blink L Afterglow", "left", 220, "55FF9A20", "lblink", "8:69") + strip("Blink L Edge", "left", 110, "AAFFA020", "lblink", "8:68")
            + strip("Blink R Afterglow", "right", 220, "55FF9A20", "rblink", "8:69") + strip("Blink R Edge", "right", 110, "AAFFA020", "rblink", "8:68"))


SM = """
        <LinearAnimation duration="1" name="Idle{s}" id="{a}"/>
        <StateMachine name="{n}" id="{m}">
            <StateMachineLayer name="Idle" id="{l}">
                <EntryState x="0" y="0"><StateTransition stateToId="{st}"/></EntryState>
                <AnimationState x="200" y="0" animationId="{a}" id="{st}"/>
                <AnyState x="400" y="0"/>
                <ExitState x="500" y="0"/>
            </StateMachineLayer>
        </StateMachine>"""


GW, GH = 2140, 944       # the three gauges at full size; the Main box between the bars is 944 tall


def gauges():
    """The three gauges in their own artboard. Main shows it through a NestedArtboardLeaf with fit
    scaleDown: full size from 2236 wide up, shrunk as a whole below that (1440 = 4:3 tablet).
    A plain nested artboard reads the parent's DashViewModel, so every bind still works."""
    row = lc("Gauges Row", energy_well() + speed_dial() + assist_well(), None, None, "row", "spaceBetweenCenter")
    return f'''    <Artboard defaultStateMachineId="6:25" viewModelId="1:1" styleId="6:22" clip="false"
              x="0" y="1300" width="{GW}" height="{GH}" name="Gauges" id="6:21">
        <LayoutComponentStyle name="Gauges Style" id="6:22"/>
        {row}
        {SM.format(s=" G", a="6:24", n="Gauges", m="6:25", l="6:26", st="6:27")}
    </Artboard>'''


def content():
    leaf = f'<NestedArtboardLeaf artboardId="6:21" fit="6" alignmentX="0" alignmentY="0" name="Gauges" id="{nid()}"/>'
    # absolute 100% box: the leaf's own 2140 width must not set Main's minimum, or Root overflows on a tablet
    sid = nid()
    slot = (f'<LayoutComponent width="100" height="100" styleId="{sid}" name="Gauges Slot" id="{nid()}">'
            f'<LayoutComponentStyle positionTypeValue="absolute" layoutWidthScaleType="fixed" layoutHeightScaleType="fixed" '
            f'widthUnitsValue="percent" heightUnitsValue="percent" positionLeft="0" positionLeftUnitsValue="points" '
            f'positionTop="0" positionTopUnitsValue="points" name="S" id="{sid}"/>{leaf}</LayoutComponent>')
    main = lc("Main", slot)
    return lc("Root", top_bar() + main + bottom_bar(), None, None, "column", pad=(22, 48, 18, 48))


def fallback_edges():
    sid = nid()
    return (f'<LayoutComponent styleId="{sid}" name="Native Edges" id="{nid()}">{bind("fx", OPACITY, "2:43")}'
            f'<LayoutComponentStyle positionTypeValue="absolute" layoutWidthScaleType="fill" layoutHeightScaleType="fill" '
            f'positionLeft="0" positionLeftUnitsValue="points" positionTop="0" positionTopUnitsValue="points" name="S" id="{sid}"/>'
            f'{edges()}</LayoutComponent>')


def fx_layer():
    """Full-size GPU layer. If the runtime cannot run scripts it draws nothing and the native copy stays.
    Absolute box pinned to all four edges, so it follows the artboard as DashPilot stretches it."""
    sid = nid()
    return (f'<LayoutComponent width="100" height="100" styleId="{sid}" name="GPU Layer" id="{nid()}">'
            f'<LayoutComponentStyle positionTypeValue="absolute" layoutWidthScaleType="fixed" layoutHeightScaleType="fixed" '
            f'widthUnitsValue="percent" heightUnitsValue="percent" '
            f'positionLeft="0" positionLeftUnitsValue="points" positionTop="0" positionTopUnitsValue="points" name="S" id="{sid}"/>'
            f'<ScriptedLayout scriptAssetId="0:79" name="Felina FX" id="{nid()}">'
            f'<ScriptInputArtboard artboardId="6:1" name="ui" id="{nid()}"/></ScriptedLayout></LayoutComponent>')


def scene():
    # Dash: what DashPilot loads. Native instrument (hidden once the GPU layer runs) + the GPU layer on top.
    native = content().replace('<LayoutComponent styleId=', '<LayoutComponent styleId=', 1)
    native = native.replace('name="Root" id=', 'name="Root" id=', 1)
    root_hide = bind("fx", OPACITY, "2:43")
    native = native.replace('<LayoutComponentStyle layoutWidthScaleType="fill"', root_hide + '<LayoutComponentStyle layoutWidthScaleType="fill"', 1)
    dash = f'''    <Artboard defaultStateMachineId="5:1" viewModelId="1:1" viewModelInstanceId="1:2" styleId="3:2"
              clip="true" width="2280" height="1080" name="Dash" id="3:1">
        <LayoutComponentStyle name="Dash Style" id="3:2"/>
        <Fill name="Background"><SolidColor colorValue="FF000000" name="C"/></Fill>
        {fx_layer()}
        {fallback_edges()}
        {native}
        {SM.format(s="", a="4:1", n="Dash", m="5:1", l="5:10", st="5:11")}
    </Artboard>'''
    # Instrument: the same instrument, drawn by the GPU layer into a texture with the live view model.
    inst = f'''    <Artboard defaultStateMachineId="6:5" viewModelId="1:1" styleId="6:2" clip="true"
              x="2600" y="0" width="2280" height="1080" name="Instrument" id="6:1">
        {bind("fxWidth", 7)}
        <LayoutComponentStyle name="Instrument Style" id="6:2"/>
        <Fill name="Background"><SolidColor colorValue="FF000000" name="C"/></Fill>
        {edges()}
        {content()}
        {SM.format(s=" I", a="6:4", n="Instrument", m="6:5", l="6:6", st="6:7")}
    </Artboard>'''
    return f'''<Rive version="1" kind="fragment">
    <ScriptAsset file="felina_fx.luau" name="felina_fx" id="0:79"/>
    <ImageAsset file="car.png" name="Car" id="9:20"/>
{dash}
{inst}
{gauges()}
</Rive>
'''


def merged(doc):
    """One .rml only: a project split over several .rml files publishes script signatures the web runtime rejects."""
    import re
    parts = []
    for f in ("viewmodel.part", "converters.part", "mine.part"):
        s = open(f).read()
        s = re.sub(r"<Rive[^>]*>", "", s, count=1)
        parts.append(s[:s.rindex("</Rive>")])
    return doc.replace("</Rive>", "\n".join(parts) + "\n</Rive>")


if __name__ == "__main__":
    open("scene.rml", "w").write(merged(scene()))
    print("scene.rml written,", _id[0], "ids")
