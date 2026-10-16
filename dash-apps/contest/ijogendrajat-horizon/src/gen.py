#!/usr/bin/env python3
"""Generates horizon.rml, the Horizon dashboard scene.

The scene is plain Rive markup; this script only exists because the lane
markings are ~1,500 keyframes of perspective maths that nobody should type by
hand. Run it, then build with the Rive CLI:

    python3 gen.py && rive . --once
"""
import math
import os
import random

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "horizon.rml")

# ---------------------------------------------------------------- ids

_next = {}


def nid(client=3):
    _next[client] = _next.get(client, 100) + 1
    return f"{client}:{_next[client]}"


# DashViewModel property ids, as declared in viewmodel.rml
P = {
    "egoSpeed": "1:10", "gear": "1:11", "egoSteeringAngle": "1:12",
    "leftBlinker": "1:13", "rightBlinker": "1:14", "anyDoorOpen": "1:15",
    "buckleStatus": "1:16", "odometer": "1:17", "accSetSpeed": "1:18",
    "fusedSpeedLimit": "1:19", "leftBlindSpot": "1:20", "rightBlindSpot": "1:21",
    "trafficLightColor": "1:24", "stopLineDist": "1:25",
    "nominalEnergyRemaining": "1:26", "fullPackEnergy": "1:27", "energyBuffer": "1:28",
    "powerLevel": "1:29", "packVoltage": "1:30", "packTMin": "1:34", "packTMax": "1:35",
    "currentTime": "1:36", "phoneBattery": "1:37", "speedCameraDistance": "1:40",
    "adasOn": "1:50", "changingLane": "1:51", "experimentalMode": "1:53",
    "isImperial": "1:55", "darkMode": "1:56", "showPhoneBattery": "1:57",
    "showCarBattery": "1:58", "showOdometer": "1:59", "alwaysOnBlindSpotMonitor": "1:60",
}

# property keys
OPACITY, ROT, X, Y, SX, SY = 18, 15, 13, 14, 16, 17
TEXT, TRIM_END, DISPLAY, ANIM_SPEED = 268, 115, 596, 199

C = {}  # converter name -> id
conv_xml = []


def bind(prop, key, conv=None):
    c = f' converterId="{C[conv]}"' if conv else ""
    return f'<DataBindContext sourcePathIds="1:1-{P[prop]}" propertyKey="{key}"{c}/>'


# ---------------------------------------------------------------- converters

def conv(name, xml_fn):
    i = nid(2)
    C[name] = i
    conv_xml.append(xml_fn(i))


def ranger(name, i0, i1, o0, o1, clamp=True):
    cl = ' clampLower="true" clampUpper="true"' if clamp else ""
    conv(name, lambda i: f'<DataConverterRangeMapper minInput="{i0}" maxInput="{i1}" '
                         f'minOutput="{o0}" maxOutput="{o1}"{cl} name="{name}" id="{i}"/>')


def group(name, *members):
    items = "".join(f'<DataConverterGroupItem converterId="{C[m]}"/>' for m in members)
    conv(name, lambda i: f'<DataConverterGroup name="{name}" id="{i}">{items}</DataConverterGroup>')


OPS = {"+": 0, "-": 1, "*": 2, "/": 3, "%": 4}
FNS = {"min(": 0, "max(": 1, "floor(": 4, "sqrt(": 5, "pow(": 6}


def formula(name, *toks):
    """Infix tokens: 'in', numbers, operators, '(', ')', 'min(' ... ',', ('vm', prop)."""
    out = []
    for t in toks:
        if t == "in":
            out.append("<FormulaTokenInput/>")
        elif isinstance(t, tuple):
            cv = f' converterId="{C[t[2]]}"' if len(t) > 2 else ""
            out.append(f'<FormulaTokenValue><DataBindContext sourcePathIds="1:1-{P[t[1]]}" '
                       f'propertyKey="777"{cv}/></FormulaTokenValue>')
        elif isinstance(t, (int, float)):
            out.append(f'<FormulaTokenValue operationValue="{t}"/>')
        elif t in OPS:
            out.append(f'<FormulaTokenOperation operationType="{OPS[t]}"/>')
        elif t in FNS:
            out.append(f'<FormulaTokenFunction functionType="{FNS[t]}"/>')
        elif t == "(":
            out.append("<FormulaTokenParenthesisOpen/>")
        elif t == ")":
            out.append("<FormulaTokenParenthesisClose/>")
        elif t == ",":
            out.append("<FormulaTokenArgumentSeparator/>")
        else:
            raise ValueError(t)
    body = "".join(out)
    conv(name, lambda i: f'<DataConverterFormula name="{name}" id="{i}">{body}</DataConverterFormula>')


def smooth(name, seconds):
    conv(name, lambda i: f'<DataConverterInterpolator duration="{seconds}" name="{name}" id="{i}"/>')


conv("Integer", lambda i: f'<DataConverterToString decimals="0" round="true" name="Integer" id="{i}"/>')
conv("BoolText", lambda i: f'<DataConverterToString name="Bool To Text" id="{i}"/>')
conv("TextNum", lambda i: f'<DataConverterToNumber name="Text To Number" id="{i}"/>')
conv("Pad2", lambda i: f'<DataConverterStringPad length="2" text="0" padType="start" name="Pad 2" id="{i}"/>')
ranger("Clamp", 0, 1, 0, 1)
ranger("NotClamp", 0, 1, 1, 0)
ranger("Is2", 1, 2, 0, 1)
group("Bool", "BoolText", "TextNum")
group("NotBool", "BoolText", "TextNum", "NotClamp")
ranger("DimUnlessOn", 0, 1, 0.35, 1)
group("CruiseDim", "BoolText", "TextNum", "DimUnlessOn")

# 1 - min(1, sqrt((in - k)^2)): 1 when the input equals k
for k in range(5):
    formula(f"Eq{k}", 1, "-", "min(", 1, ",", "sqrt(", "pow(", "in", "-", k, ",", 2, ")", ")", ")")

# 0..1 as the speed passes limit+2 .. limit+5, and 0 while the limit is unknown
formula("OverLimit",
        "min(", 1, ",", "max(", 0, ",", "(", "in", "-", ("vm", "fusedSpeedLimit"), "-", 2, ")", "/", 3, ")", ")",
        "*", "min(", 1, ",", ("vm", "fusedSpeedLimit"), ")")
group("OverLimitNot", "OverLimit", "NotClamp")

# Lane markings: one loop is one dash period, ~12 m of road
conv("RoadSpeed", lambda i: f'<DataConverterRangeMapper minInput="0" maxInput="200" minOutput="0" '
                            f'maxOutput="5" clampLower="true" name="Road Speed" id="{i}"/>')

# Steering tilts the horizon like an attitude indicator
ranger("TiltMap", -270, 270, 0.07, -0.07)
smooth("Smooth300", 0.3)
group("Tilt", "TiltMap", "Smooth300")

# Horizon power meter
smooth("Smooth250", 0.25)
ranger("PowerMap", 0, 100, 0, 1)
group("Power", "PowerMap", "Smooth250")
ranger("RegenMap", -100, 0, 1, 0)
group("Regen", "RegenMap", "Smooth250")

# Stop line depth: 1 world unit ~ 12 m, the screen bottom is z = 1
formula("StopY", 480, "/", "max(", 0.8, ",", "in", "/", 12, "+", 1, ")")
formula("StopScale", 1, "/", "max(", 0.8, ",", "in", "/", 12, "+", 1, ")")
ranger("StopFar", 120, 220, 1, 0)

# Blind spot: shown with the blinker on that side, or always when the user asked
formula("BlindLeft", "min(", 1, ",", "in", ")", "*", "min(", 1, ",", ("vm", "leftBlinker"), ")")
formula("BlindRight", "min(", 1, ",", "in", ")", "*", "min(", 1, ",", ("vm", "rightBlinker"), ")")

# Battery: (remaining - buffer) / (full - buffer) * 100
formula("Soc", "max(", 0, ",", "min(", 100, ",",
        "(", "in", "-", ("vm", "energyBuffer"), ")", "/",
        "max(", 0.01, ",", ("vm", "fullPackEnergy"), "-", ("vm", "energyBuffer"), ")", "*", 100, ")", ")")
group("SocText", "Soc", "Integer")
ranger("SocWidthMap", 0, 100, 0, 240)
group("SocWidth", "Soc", "SocWidthMap")
ranger("SocLowMap", 15, 25, 1, 0)
group("SocLow", "Soc", "SocLowMap")

formula("Hours", "floor(", "in", "/", 3600, ")")
group("HoursText", "Hours", "Integer", "Pad2")
formula("Minutes", "floor(", "(", "in", "%", 3600, ")", "/", 60, ")")
group("MinutesText", "Minutes", "Integer", "Pad2")
ranger("PhoneKnown", -1, 0, 0, 1)

# ---------------------------------------------------------------- drawing helpers

FONT_REG, FONT_SEMI = "2:900", "2:901"
TNUM = '<TextStyleFeature tag="1953396077" featureValue="1" name="tnum"/>'


def solid(c):
    return f'<SolidColor colorValue="{c}" name="C"/>'


def fill(c, feather=None):
    if feather:
        return (f'<Fill fillRule="clockwise" name="F">{solid(c)}'
                f'<Feather strength="{feather}" name="Fe"/></Fill>')
    return f'<Fill name="F">{solid(c)}</Fill>'


def lgrad(x0, y0, x1, y1, stops):
    s = "".join(f'<GradientStop colorValue="{c}" position="{p}"/>' for p, c in stops)
    return f'<LinearGradient startX="{x0}" startY="{y0}" endX="{x1}" endY="{y1}" name="G">{s}</LinearGradient>'


def rgrad(r, stops):
    s = "".join(f'<GradientStop colorValue="{c}" position="{p}"/>' for p, c in stops)
    return f'<RadialGradient startX="0" startY="0" endX="{r}" endY="0" name="G">{s}</RadialGradient>'


def stroke(c, w, cap="round", inner=""):
    return f'<Stroke thickness="{w}" cap="{cap}" join="round" name="S">{solid(c)}{inner}</Stroke>'


def poly(pts, radius=0, closed=True):
    if closed:
        area = sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]))
        if area < 0:
            pts = pts[::-1]
    v = "".join(f'<StraightVertex x="{x:.2f}" y="{y:.2f}" radius="{radius}"/>' for x, y in pts)
    return f'<PointsPath isClosed="{"true" if closed else "false"}" name="P">{v}</PointsPath>'


def rect(w, h, x=0, y=0, r=0, ox=0.5, oy=0.5):
    return (f'<Rectangle x="{x}" y="{y}" width="{w}" height="{h}" originX="{ox}" originY="{oy}" '
            f'cornerRadiusTL="{r}" name="P"/>')


def ellipse(w, h, x=0, y=0):
    return f'<Ellipse x="{x}" y="{y}" width="{w}" height="{h}" name="P"/>'


def shape(name, *parts, x=0, y=0, binds="", sid=None, extra=""):
    i = f' id="{sid}"' if sid else ""
    return f'<Shape x="{x}" y="{y}"{extra} name="{name}"{i}>{binds}{"".join(parts)}</Shape>'


def node(name, *kids, x=0, y=0, binds="", extra="", nid_=None):
    i = f' id="{nid_}"' if nid_ else ""
    return f'<Node x="{x}" y="{y}"{extra} name="{name}"{i}>{binds}{"".join(kids)}</Node>'


def text(name, runs, styles, x=0, y=0, w=400, h=100, align="center", ox=0.5, oy=0.5,
         binds="", layout=False):
    """styles: [(size, font, color)]; runs: [(style_index, text, bindxml)]."""
    sids = [nid() for _ in styles]
    st = "".join(
        f'<TextStylePaint fontSize="{s}" fontAssetId="{f}" familyName="Inter" '
        f'styleName="{"SemiBold" if f == FONT_SEMI else "Regular"}" name="S{n}" id="{sids[n]}">'
        f'{fill(c)}{TNUM}</TextStylePaint>'
        for n, (s, f, c) in enumerate(styles))
    rn = "".join(f'<TextValueRun styleId="{sids[si]}" text="{t}" name="R">{b}</TextValueRun>'
                 for si, t, b in runs)
    pos = "" if layout else f' x="{x}" y="{y}" originX="{ox}" originY="{oy}"'
    return (f'<Text{pos} sizingValue="fixed" width="{w}" height="{h}" alignValue="{align}" '
            f'verticalAlignValue="middle" wrapValue="noWrap" name="{name}" id="{nid()}">'
            f'{binds}{st}{rn}</Text>')


def lstyle(**kw):
    """A LayoutComponentStyle; *Units are filled in for every inset given."""
    i = nid()
    attrs = []
    for k, v in kw.items():
        attrs.append(f'{k}="{v}"')
        if k.startswith(("padding", "position", "gap", "margin")) and k not in ("positionTypeValue",):
            attrs.append(f'{k}UnitsValue="points"')
    return i, f'<LayoutComponentStyle {" ".join(attrs)} name="S" id="{i}"/>'


def box(name, *kids, w=100, h=100, style=None, binds="", style_binds="", paint="", clip="false"):
    style = dict(style or {})
    style.setdefault("layoutWidthScaleType", "fixed")
    style.setdefault("layoutHeightScaleType", "fixed")
    sid, sxml = lstyle(**style)
    if style_binds:
        sxml = sxml.replace("/>", f">{style_binds}</LayoutComponentStyle>", 1)
    return (f'<LayoutComponent clip="{clip}" width="{w}" height="{h}" styleId="{sid}" name="{name}" id="{nid()}">'
            f'{binds}{sxml}{paint}{"".join(kids)}</LayoutComponent>')


def gated(name, prop, conv_name, make):
    """Blind spot art, shown with the blinker on that side or always if the user asked.

    Formulas cannot read a boolean, so the OR is two copies: one gated by the blinker,
    one by alwaysOnBlindSpotMonitor. When both apply they overlap exactly."""
    return node(name,
                node("With Blinker", make(), binds=bind(prop, OPACITY, conv_name)),
                node("Always On", node("Present", make(), binds=bind(prop, OPACITY, "Clamp")),
                     binds=bind("alwaysOnBlindSpotMonitor", OPACITY, "Bool")))


# ---------------------------------------------------------------- palette

WHITE = "FFF4F6FA"
DIM = "FF8A93A6"
FAINT = "FF4A5366"
AMBER = "FFFFB020"
RED = "FFFF453A"
GREEN = "FF30D158"
BLUE = "FF3D8BFF"

# ---------------------------------------------------------------- the road (component artboard)

RW, RH = 3200, 700          # road artboard: 3200 wide, horizon at y=0, 700 down
CX = RW / 2
NEAR = 480.0                # screen distance from horizon to the bottom edge: z = 1
LANE = 320.0                # ego lane half-width at z = 1
EDGE = 960.0                # road edge offset at z = 1
DASHES, PERIOD, DASH_LEN, Z0 = 15, 1.0, 0.34, 0.6
FRAMES, STEP = 60, 3
COS = NEAR / math.hypot(LANE, NEAR)
THETA = math.atan2(LANE, NEAR)


def along(z):
    """Distance along a lane divider from the vanishing point to depth z."""
    return (NEAR / z) / COS


def lane_dashes(side):
    """side -1 left, +1 right. Returns (node xml, animation keyed objects)."""
    shapes, keyed = [], []
    for k in range(DASHES):
        sid = nid(6)
        shapes.append(shape(f"Dash {k}", rect(16, 1, ox=0.5, oy=0), fill("E6E9EEF5"), sid=sid,
                            y=along(Z0 + (k + 1) * PERIOD)))
        props = {"y": [], "scaleX": [], "scaleY": []}
        for f in range(0, FRAMES + 1, STEP):
            z = Z0 + (k + 1 - f / FRAMES) * PERIOD
            near, far = along(z), along(z + DASH_LEN)
            props["y"].append((f, far))
            props["scaleX"].append((f, 1 / z))
            props["scaleY"].append((f, near - far))
        kp = "".join(
            f'<KeyedProperty property="{p}">' +
            "".join(f'<KeyFrameDouble frame="{f}" value="{v:.3f}" interpolationType="linear"/>' for f, v in kfs) +
            "</KeyedProperty>" for p, kfs in props.items())
        keyed.append(f'<KeyedObject objectId="{sid}">{kp}</KeyedObject>')
    grp = node(f"Divider {'Left' if side < 0 else 'Right'}", *shapes, x=CX, y=0,
               extra=f' rotation="{-side * THETA:.6f}"')
    return grp, keyed


def road_artboard():
    left, kl = lane_dashes(-1)
    right, kr = lane_dashes(1)
    scale = RH / NEAR
    edge_l = shape("Edge Left", poly([(CX - 0.6, 0), (CX + 0.6, 0),
                                      (CX - EDGE * scale + 13, RH), (CX - EDGE * scale - 13, RH)]),
                   fill("B39AA3B5"))
    edge_r = shape("Edge Right", poly([(CX - 0.6, 0), (CX + 0.6, 0),
                                       (CX + EDGE * scale + 13, RH), (CX + EDGE * scale - 13, RH)]),
                   fill("B39AA3B5"))
    surface = shape("Asphalt", poly([(CX - 2, 0), (CX + 2, 0),
                                     (CX + EDGE * scale + 80, RH), (CX - EDGE * scale - 80, RH)]),
                    f'<Fill name="F">{lgrad(0, 0, 0, NEAR, [(0, "FF121722"), (1, "FF1A202B")])}</Fill>')
    anim_id = nid(6)
    anim = (f'<LinearAnimation loopValue="loop" fps="60" duration="{FRAMES}" name="Flow" id="{anim_id}">'
            + "".join(kl + kr) + "</LinearAnimation>")
    sid = nid(6)
    ab_id = nid(6)
    xml = (f'<Artboard isComponent="true" clip="true" x="0" y="1400" width="{RW}" height="{RH}" '
           f'styleId="{sid}" name="Road" id="{ab_id}">'
           f'<LayoutComponentStyle name="Road Style" id="{sid}"/>'
           f'{left}{right}{edge_l}{edge_r}{surface}{anim}</Artboard>'
           f'<ComponentAsset artboardId="{ab_id}" name="Road"/>')
    return xml, ab_id, anim_id


# ---------------------------------------------------------------- cars

def car(name, body, binds="", x=0, y=0, scale=1.0, tail=True, blink=True, outline=None):
    """A rear view of a car, 100 units wide, centred on its rear bumper."""
    parts = []
    if blink:
        for side, prop in ((-1, "leftBlinker"), (1, "rightBlinker")):
            parts.append(shape(f"Blink {prop}", rect(24, 8, x=side * 35, y=2, r=3),
                               fill(AMBER), binds=bind(prop, OPACITY, "Is2")))
            parts.append(shape(f"Blink Glow {prop}", ellipse(46, 26, x=side * 35, y=2),
                               fill("CCFFB020", feather=10), binds=bind(prop, OPACITY, "Is2")))
    if tail:
        parts.append(shape("Tail Lights", rect(24, 8, x=-35, y=2, r=3), rect(24, 8, x=35, y=2, r=3),
                           fill("FFE0242B")))
        parts.append(shape("Plate", rect(26, 9, y=14, r=2), fill("FFC9CED6")))
    parts.append(shape("Rear Window", poly([(-30, -31), (30, -31), (37, -13), (-37, -13)], radius=5),
                       fill("FF0B0F16")))
    parts.append(shape("Body",
                       poly([(-36, -36), (36, -36), (44, -12), (50, -8), (50, 18), (-50, 18), (-50, -8), (-44, -12)],
                            radius=8),
                       fill(body), *( [stroke(outline, 2)] if outline else [])))
    parts.append(shape("Tyres", rect(16, 14, x=-38, y=22, r=4), rect(16, 14, x=38, y=22, r=4), fill("FF05070A")))
    parts.append(shape("Shadow", ellipse(124, 26, y=26), fill("AA000000", feather=12)))
    return node(name, *parts, x=x, y=y, binds=binds, extra=f' scaleX="{scale}" scaleY="{scale}"')


# ---------------------------------------------------------------- the stage (centred, 1440 x 1080)

SW, SH, HZ = 1440, 1080, 600   # stage size; horizon height
MID = SW / 2


def speed_cluster():
    big = [(300, FONT_SEMI, WHITE)]
    amber = [(300, FONT_SEMI, AMBER)]
    run = [(0, "72", bind("egoSpeed", TEXT, "Integer"))]
    speed = node("Speed",
                 text("Speed Over", run, amber, x=MID, y=322, w=760, h=300,
                      binds=bind("egoSpeed", OPACITY, "OverLimit")),
                 text("Speed", run, big, x=MID, y=322, w=760, h=300),
                 shape("Glow", ellipse(1000, 1000), f'<Fill name="F">{rgrad(500, [(0, "502B3A5E"), (0.5, "202B3A5E"), (1, "002B3A5E")])}</Fill>',
                       x=MID, y=330))
    unit = node("Unit",
                text("Kmh", [(0, "km/h", "")], [(40, FONT_REG, DIM)], x=MID, y=488, w=300, h=56,
                     binds=bind("isImperial", OPACITY, "NotBool")),
                text("Mph", [(0, "mph", "")], [(40, FONT_REG, DIM)], x=MID, y=488, w=300, h=56,
                     binds=bind("isImperial", OPACITY, "Bool")))
    cruise = node("Cruise",
                  node("Pill",
                       text("Set", [(0, "SET ", ""), (1, "75", bind("accSetSpeed", TEXT, "Integer"))],
                            [(28, FONT_SEMI, "FFBFD7FF"), (34, FONT_SEMI, WHITE)], x=0, y=0, w=200, h=50),
                       shape("Pill Bg", rect(170, 50, r=25), fill("FF16305C"), stroke("FF3D8BFF", 2)),
                       binds=bind("adasOn", OPACITY, "CruiseDim")),
                  x=MID, y=548, binds=bind("accSetSpeed", OPACITY, "Clamp"))

    # Gear: one big letter, left of the speed
    letters = []
    for n, (ch, col) in enumerate([("–", FAINT), ("P", WHITE), ("R", AMBER), ("N", WHITE), ("D", GREEN)]):
        letters.append(text(f"Gear {ch}", [(0, ch, "")], [(132, FONT_SEMI, col)], x=0, y=-4, w=180, h=170,
                            binds=bind("gear", OPACITY, f"Eq{n}")))
    gear = node("Gear", *letters,
                shape("Frame", rect(176, 176, r=40), fill("E60E1320"), stroke("FF2A3346", 3)),
                x=MID - 430, y=322)

    # Speed limit: round sign (metric), US-style rectangle (imperial)
    eu = node("Limit EU",
              text("Limit", [(0, "80", bind("fusedSpeedLimit", TEXT, "Integer"))], [(74, FONT_SEMI, "FF0B0D12")],
                   w=170, h=110),
              shape("Sign", ellipse(186, 186), fill("FFF7F7F7"), stroke("FFE5322D", 20)),
              binds=bind("isImperial", OPACITY, "NotBool"))
    us = node("Limit US",
              text("Caption", [(0, "SPEED", "")], [(25, FONT_SEMI, "FF0B0D12")], y=-62, w=150, h=34),
              text("Caption2", [(0, "LIMIT", "")], [(25, FONT_SEMI, "FF0B0D12")], y=-32, w=150, h=34),
              text("Limit", [(0, "50", bind("fusedSpeedLimit", TEXT, "Integer"))], [(80, FONT_SEMI, "FF0B0D12")],
                   y=30, w=150, h=96),
              shape("Inner", rect(136, 186, r=10), stroke("FF0B0D12", 5)),
              shape("Sign", rect(150, 200, r=16), fill("FFF7F7F7")),
              binds=bind("isImperial", OPACITY, "Bool"))
    limit = node("Speed Limit", eu, us, x=MID + 430, y=322, binds=bind("fusedSpeedLimit", OPACITY, "Clamp"))

    # Blinkers: chevrons at the far ends of the speed row
    def chevrons(side, prop):
        pts = [(-34, -46), (2, -46), (40, 0), (2, 46), (-34, 46), (4, 0)]
        lit = [shape(f"Lit {n}", poly([(x * side + side * n * 44, y) for x, y in pts], radius=4), fill(GREEN))
               for n in range(2)]
        dim = [shape(f"Dim {n}", poly([(x * side + side * n * 44, y) for x, y in pts], radius=4), fill("FF1A2130"))
               for n in range(2)]
        glow = shape("Glow", ellipse(240, 170, x=side * 22), fill("8030D158", feather=40))
        return node(f"Blinker {prop}",
                    node("Lit", glow, *lit, binds=bind(prop, OPACITY, "Is2")),
                    *dim, x=MID + side * 600, y=322)

    return node("Cluster", speed, unit, cruise, gear, limit,
                chevrons(-1, "leftBlinker"), chevrons(1, "rightBlinker"))


def tilted_world(road_id, anim_id):
    """Everything that sits on the ground plane; rotated by the steering angle."""
    scale = RH / NEAR
    random.seed(7)
    stars = "".join(
        shape("Star", ellipse(s, s), fill(f"{a:02X}DDE6FF"),
              x=round(random.uniform(-1300, 1300)), y=round(random.uniform(-640, -90)))
        for s, a in ((random.choice([2, 2, 3, 4]), random.randint(40, 150)) for _ in range(70)))

    # Blind spot cars, in the next lane, alongside you
    def blind_car(side, prop, conv_name):
        z = 1.42
        x, y = side * 2 * LANE / z, NEAR / z

        def make():
            return node("Car",
                        car("Urgent", RED, x=x, y=y, scale=2.9 / z, tail=True, blink=False, outline="FFFFB0A8",
                            binds=bind(prop, OPACITY, "Is2")),
                        car("Vehicle", AMBER, x=x, y=y, scale=2.9 / z, tail=True, blink=False, outline="FFFFE2A0"),
                        shape("Lane Glow", poly([(side * LANE / 6, 40), (side * 2 * LANE * 1.3, NEAR * 1.3 + 40),
                                                 (side * 0.95 * LANE * 1.3, NEAR * 1.3 + 40)]),
                              fill("55FFB020", feather=40)))
        return gated(f"Blind {prop}", prop, conv_name, make)

    # Blinker: the lane divider on that side lights up
    def divider_glow(side, prop):
        return shape(f"Divider Glow {prop}",
                     poly([(0, 0), (side * (LANE * scale + 38), RH), (side * (LANE * scale - 38), RH)]),
                     fill("9930D158", feather=18), binds=bind(prop, OPACITY, "Is2"))

    stop = node("Stop Line",
                node("Depth",
                     shape("Red", rect(2 * LANE - 40, 30, r=6), fill(RED), binds=bind("trafficLightColor", OPACITY, "Eq1")),
                     shape("Line", rect(2 * LANE - 40, 30, r=6), fill("FFF4F6FA")),
                     binds=bind("stopLineDist", Y, "StopY") + bind("stopLineDist", SX, "StopScale")
                     + bind("stopLineDist", SY, "StopScale")),
                binds=bind("stopLineDist", OPACITY, "Clamp"))

    horizon = node("Horizon Meter",
                   shape("Power", poly([(0, 0), (1000, 0)], closed=False),
                         stroke("FFFFE7C2", 8, inner=f'<TrimPath start="0" end="0.3" name="T">{bind("powerLevel", TRIM_END, "Power")}</TrimPath>')),
                   shape("Power Glow", poly([(0, 0), (1000, 0)], closed=False),
                         stroke("CCFFB35C", 30, inner=f'<TrimPath start="0" end="0.3" name="T">{bind("powerLevel", TRIM_END, "Power")}</TrimPath><Feather strength="14" name="Fe"/>')),
                   shape("Regen", poly([(0, 0), (-1000, 0)], closed=False),
                         stroke("FF5BF08A", 8, inner=f'<TrimPath start="0" end="0" name="T">{bind("powerLevel", TRIM_END, "Regen")}</TrimPath>')),
                   shape("Regen Glow", poly([(0, 0), (-1000, 0)], closed=False),
                         stroke("CC30D158", 30, inner=f'<TrimPath start="0" end="0" name="T">{bind("powerLevel", TRIM_END, "Regen")}</TrimPath><Feather strength="14" name="Fe"/>')),
                   shape("Line", poly([(-2400, 0), (2400, 0)], closed=False), stroke("FF34405A", 2, cap="butt")),
                   shape("Tick", rect(4, 22, r=2), fill("FF56627D")))

    fog = shape("Fog", rect(6000, 170, y=0, ox=0.5, oy=0),
                f'<Fill name="F">{lgrad(0, 0, 0, 170, [(0, "FF1D2740"), (0.35, "991D2740"), (1, "001D2740")])}</Fill>')

    carpet = shape("Assist Carpet",
                   poly([(-LANE * 30 / NEAR, 30), (LANE * 30 / NEAR, 30), (LANE * scale - 24, RH), (-LANE * scale + 24, RH)]),
                   f'<Fill name="F">{lgrad(0, 30, 0, NEAR, [(0, "003D8BFF"), (0.55, "553D8BFF"), (1, "993D8BFF")])}</Fill>',
                   binds=bind("adasOn", OPACITY, "Bool"))
    carpet = node("Assist", carpet.replace("3D8BFF", "FF8A3D").replace('name="Assist Carpet"', 'name="Experimental"').replace(
        bind("adasOn", OPACITY, "Bool"), bind("experimentalMode", OPACITY, "Bool")),
        carpet.replace(bind("adasOn", OPACITY, "Bool"), bind("experimentalMode", OPACITY, "NotBool")),
        binds=bind("adasOn", OPACITY, "Bool"))

    road = (f'<NestedArtboard artboardId="{road_id}" x="{-RW / 2}" y="0" name="Road">'
            f'<NestedSimpleAnimation animationId="{anim_id}" isPlaying="true" speed="1" name="Flow">'
            f'{bind("egoSpeed", ANIM_SPEED, "RoadSpeed")}</NestedSimpleAnimation></NestedArtboard>')

    lights = node("Sky Light",
                  *[shape(n, rect(6000, 380, ox=0.5, oy=1),
                          f'<Fill name="F">{lgrad(0, -380, 0, 0, [(0, "00" + c[2:]), (1, "77" + c[2:])])}</Fill>',
                          binds=bind("trafficLightColor", OPACITY, f"Eq{k}"))
                    for n, c, k in (("Red", RED, 1), ("Green", GREEN, 2), ("Yellow", AMBER, 3))])

    ground = shape("Ground", rect(7000, 3000, ox=0.5, oy=0),
                   f'<Fill name="F">{lgrad(0, 0, 0, NEAR, [(0, "FF161D2B"), (0.2, "FF0B0F17"), (1, "FF07090D")])}</Fill>')
    haze = shape("Horizon Haze", rect(7000, 260, ox=0.5, oy=1),
                 f'<Fill name="F">{lgrad(0, -260, 0, 0, [(0, "00334670"), (1, "66334670")])}</Fill>')

    return node("World",
                blind_car(-1, "leftBlindSpot", "BlindLeft"), blind_car(1, "rightBlindSpot", "BlindRight"),
                stop, horizon, fog, divider_glow(-1, "leftBlinker"), divider_glow(1, "rightBlinker"),
                carpet, road, ground, lights, haze, stars,
                x=MID, y=HZ, binds=bind("egoSteeringAngle", ROT, "Tilt"))


# ---------------------------------------------------------------- HUD (full width, laid out)

def chip(name, label, color, display_bind):
    return box(name,
               text("L", [(0, label, "")], [(26, FONT_SEMI, color)], w=220, h=52, layout=True),
               w=220, h=52, style={"cornerRadiusTL": 26, "cornerRadiusTR": 26, "cornerRadiusBL": 26, "cornerRadiusBR": 26},
               style_binds=display_bind,
               paint=f'{fill("FF1B1418")}{stroke(color, 2)}')


def hud():
    clock = box("Clock",
                text("Clock", [(0, "07", bind("currentTime", TEXT, "HoursText")), (0, ":", ""),
                               (0, "25", bind("currentTime", TEXT, "MinutesText"))],
                     [(50, FONT_SEMI, WHITE)], w=150, h=64, align="left", layout=True),
                w=150, h=64)
    door = chip("Door Chip", "DOOR OPEN", AMBER, bind("anyDoorOpen", DISPLAY, "NotClamp"))
    belt = chip("Belt Chip", "SEATBELT", RED, bind("buckleStatus", DISPLAY, "Clamp"))
    cam = box("Camera Chip",
              text("L", [(0, "CAMERA ", ""), (1, "250", bind("speedCameraDistance", TEXT, "Integer")), (0, " m", "")],
                   [(26, FONT_SEMI, AMBER), (28, FONT_SEMI, WHITE)], w=240, h=52, layout=True),
              w=240, h=52, style={"cornerRadiusTL": 26, "cornerRadiusTR": 26, "cornerRadiusBL": 26, "cornerRadiusBR": 26},
              style_binds=bind("speedCameraDistance", DISPLAY, "NotClamp"),
              paint=f'{fill("FF1B1710")}{stroke(AMBER, 2)}')
    left = box("Top Left", clock, door, belt, cam, w=900, h=64,
               style={"layoutWidthScaleType": "fill", "flexBasis": 0, "flexBasisUnitsValue": "points", "flexDirectionValue": "row", "layoutAlignmentType": "centerLeft", "gapHorizontal": 18})

    lamps = []
    for k, (n, c) in enumerate((("Red", RED), ("Yellow", AMBER), ("Green", GREEN))):
        eq = {"Red": "Eq1", "Green": "Eq2", "Yellow": "Eq3"}[n]
        lamps.append(shape(f"{n} Lit", ellipse(30, 30, x=-44 + 44 * k), fill(c),
                           binds=bind("trafficLightColor", OPACITY, eq)))
        lamps.append(shape(f"{n} Off", ellipse(30, 30, x=-44 + 44 * k), fill("FF20262F")))
    light = box("Traffic Light", node("Lamps", *lamps, x=80, y=26), w=160, h=52,
                style={"cornerRadiusTL": 26, "cornerRadiusTR": 26, "cornerRadiusBL": 26, "cornerRadiusBR": 26},
                binds=bind("trafficLightColor", OPACITY, "Clamp"), paint=fill("E60B0E14"))

    phone = box("Phone",
                text("Phone", [(0, "PHONE ", ""), (1, "82", bind("phoneBattery", TEXT, "Integer")), (1, "%", "")],
                     [(24, FONT_SEMI, DIM), (36, FONT_SEMI, WHITE)], w=240, h=64, align="right", layout=True,
                     binds=bind("phoneBattery", OPACITY, "PhoneKnown")),
                w=240, h=64, style_binds=bind("showPhoneBattery", DISPLAY, "NotBool"))
    right = box("Top Right", phone, w=900, h=64,
                style={"layoutWidthScaleType": "fill", "flexBasis": 0, "flexBasisUnitsValue": "points", "flexDirectionValue": "row", "layoutAlignmentType": "centerRight"})
    top = box("Top Bar", left, light, right, w=100, h=64,
              style={"layoutWidthScaleType": "fill", "flexDirectionValue": "row",
                     "layoutAlignmentType": "spaceBetweenCenter"})

    soc = box("Battery",
              text("Soc", [(0, "69", bind("nominalEnergyRemaining", TEXT, "SocText")), (1, "%", "")],
                   [(46, FONT_SEMI, WHITE), (30, FONT_SEMI, DIM)], x=0, y=0, w=150, h=60, align="left", ox=0, oy=0),
              node("Bar",
                   shape("Low", rect(240, 10, r=5, ox=0, oy=0.5), fill(RED),
                         binds=bind("nominalEnergyRemaining", OPACITY, "SocLow")),
                   shape("Level", f'<Rectangle width="160" height="10" originX="0" originY="0.5" cornerRadiusTL="5" name="P">'
                                  f'{bind("nominalEnergyRemaining", 20, "SocWidth")}</Rectangle>', fill(GREEN)),
                   shape("Track", rect(240, 10, r=5, ox=0, oy=0.5), fill("FF1E2533")),
                   x=150, y=32),
              w=420, h=64, style_binds=bind("showCarBattery", DISPLAY, "NotBool"))
    def odo_text(unit, vis):
        return text(f"Odo {unit}", [(0, "ODO  ", ""), (1, "42180", bind("odometer", TEXT, "Integer")),
                                    (0, " " + unit, "")],
                    [(24, FONT_SEMI, DIM), (34, FONT_SEMI, "FFC9CED6")], w=360, h=64, align="right", layout=True,
                    binds=bind("isImperial", OPACITY, vis))
    odo = box("Odometer", odo_text("km", "NotBool"), odo_text("mi", "Bool"),
              w=360, h=64, style_binds=bind("showOdometer", DISPLAY, "NotBool"))
    bottom_left = box("Bottom Left", soc, w=600, h=64,
                      style={"layoutWidthScaleType": "fill", "flexBasis": 0, "flexBasisUnitsValue": "points", "flexDirectionValue": "row", "layoutAlignmentType": "centerLeft"})
    bottom_right = box("Bottom Right", odo, w=600, h=64,
                       style={"layoutWidthScaleType": "fill", "flexBasis": 0, "flexBasisUnitsValue": "points", "flexDirectionValue": "row", "layoutAlignmentType": "centerRight"})
    bottom = box("Bottom Bar", bottom_left, bottom_right, w=100, h=64,
                 style={"layoutWidthScaleType": "fill", "flexDirectionValue": "row",
                        "layoutAlignmentType": "spaceBetweenCenter"})
    return box("HUD", top, bottom, w=100, h=100,
               style={"layoutWidthScaleType": "fill", "layoutHeightScaleType": "fill", "flexDirectionValue": "column",
                      "layoutAlignmentType": "spaceBetweenCenter",
                      "paddingLeft": 64, "paddingRight": 64, "paddingTop": 34, "paddingBottom": 30})


def edge_glow(side, prop, conv_name):
    pos = "positionLeft" if side < 0 else "positionRight"
    a, b = ("FF", "00") if side < 0 else ("00", "FF")

    def bar(label, w, color, conv):
        return box(f"Edge {prop} {label}", w=w, h=3000,
                   style={"positionTypeValue": "absolute", pos: 0, "positionTop": 0},
                   binds=bind(prop, OPACITY, conv),
                   paint=f'<Fill name="F">{lgrad(0, 0, w, 0, [(0, a + color[2:]), (1, b + color[2:])])}</Fill>')

    return gated(f"Edge {prop}", prop, conv_name,
                 lambda: node("Bars", bar("Urgent", 90, RED, "Is2"), bar("Vehicle", 54, AMBER, "Clamp")))


def main_artboard(road_id, anim_id):
    edges = edge_glow(-1, "leftBlindSpot", "BlindLeft") + edge_glow(1, "rightBlindSpot", "BlindRight")

    stage = box("Stage",
                car("Ego Car", "FF39414F", x=MID, y=968, scale=3.1, outline="FF59647A"),
                speed_cluster(),
                tilted_world(road_id, anim_id),
                w=SW, h=SH)
    stage_row = box("Stage Row", stage, w=100, h=100,
                    style={"layoutWidthScaleType": "fill", "layoutHeightScaleType": "fill", "flexDirectionValue": "row",
                           "layoutAlignmentType": "center"})
    sky = box("Sky", w=100, h=100,
              style={"layoutWidthScaleType": "fill", "layoutHeightScaleType": "fill"},
              paint=f'<Fill name="F">{lgrad(0, 0, 0, HZ, [(0, "FF020308"), (0.55, "FF0A1120"), (1, "FF22304E")])}</Fill>')

    root = box("Root", edges, hud(), stage_row, sky, w=2280, h=1080, clip="true",
               style={"layoutWidthScaleType": "fill", "layoutHeightScaleType": "fill",
                      "layoutTypeValue": "stack", "layoutAlignmentType": "center"})
    sid = nid()
    idle = nid(4)
    sm, layer, entry_to = nid(5), nid(5), nid(5)
    return (f'<Artboard defaultStateMachineId="{sm}" viewModelId="1:1" viewModelInstanceId="1:2" styleId="{sid}" '
            f'clip="true" x="0" y="0" width="2280" height="1080" name="Horizon" id="3:1">'
            f'<LayoutComponentStyle name="Horizon Style" id="{sid}"/>'
            f'{fill("FF020308")}'
            f'{root}'
            f'<StateMachine name="Dash" id="{sm}"><StateMachineLayer name="Idle" id="{layer}">'
            f'<EntryState x="0" y="0"><StateTransition stateToId="{entry_to}"/></EntryState>'
            f'<AnimationState x="200" y="0" animationId="{idle}" id="{entry_to}"/>'
            f'<AnyState x="400" y="0"/><ExitState x="500" y="0"/></StateMachineLayer></StateMachine>'
            f'<LinearAnimation duration="1" name="Idle" id="{idle}"/>'
            f'</Artboard>')


def build():
    road_xml, road_id, anim_id = road_artboard()
    main = main_artboard(road_id, anim_id)
    fonts = (f'<FontAsset file="fonts/inter-400.ttf" name="Inter Regular" id="{FONT_REG}"/>'
             f'<FontAsset file="fonts/inter-600.ttf" name="Inter SemiBold" id="{FONT_SEMI}"/>')
    doc = (f'<Rive version="1" kind="fragment">\n<!-- Generated by gen.py. Edit that, not this. -->\n'
           f'{fonts}\n' + "\n".join(conv_xml) + f'\n{main}\n{road_xml}\n</Rive>\n')
    with open(OUT, "w") as f:
        f.write(doc)


if __name__ == "__main__":
    build()
