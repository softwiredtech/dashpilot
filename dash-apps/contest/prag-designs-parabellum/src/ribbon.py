"""DashPilot v2 "Ribbon": two hatched ribbons frame a top-down car. Magenta palette, white sedan.

Built from the approved Codex frame (codex/c3.png palette, codex/c1.png car). Coordinates are
measured on that 1536 x 1024 frame and mapped onto the 2280 x 1080 phone artboard by P():
scaled 1.05, the two gauges pushed DX further out so the wide screen breathes.
Draw order: the FIRST sibling draws on TOP; node()/stack() take paint order, bottom first.
"""
import math
import os
import re
from gen import (nid, bind, fill, glow, grad_fill, stroke, grad_stroke, trim, shape, node, ellipse, rect, poly,
                 text, SM, fx_layer, X, Y, OPACITY, TEXT, TRIM_END, SCALE_X, SCALE_Y)

W, H = 2280, 1080
S, DX = 1.05, 170
EXO, EXO_B = "2:902", "2:903"
WHITE, SOFT, DIM = "FFF4F1F7", "FFD8D4DC", "FF55525A"
MAGENTA, OUTLINE = "FFE51FD0", "FFEE63E4"
TRACK, HATCH = "FF17181B", "FF0B0C0E"     # empty track: same stripes, muted so it recedes behind the liquid
AMBER, RED = "FFFFC21A", "FFFF3B30"
FILL = [("FFD91CFB", 0), ("FFFD4CBD", 0.25), ("FFFE3BCF", 0.6), ("FFFC00FB", 1)]   # violet tip, pink body, magenta top
REGEN = [("FF9C8CFF", 0), ("FFC9BEFF", 1)]
# left gauge, measured on the frame: centre, outline radius, ribbon radii, the angles of its two ends
GC, R_LINE, R_OUT, R_IN = (465, 480), 400, 381, 282
A_BOT, A_TOP = 171, 360


def P(x, y, side=0):
    return 1140 + (x - 768) * S + side * DX, 540 + (y - 512) * S


def arc(c, r, a0, a1, n=96):
    """Points on a circle from a0 to a1 (degrees, clockwise from 12 o'clock)."""
    return [(c[0] + r * math.sin(math.radians(a)), c[1] - r * math.cos(math.radians(a)))
            for a in (a0 + (a1 - a0) * i / n for i in range(n + 1))]


def rim_path(c, r, a_bot, a_top, rho=9.0, tick=21.0, n=14):
    """The rim as ONE path: end tick, rounded corner, the arc, rounded corner, end tick. Same shape at all four ends."""
    def at(a, rr):
        ar = math.radians(a)
        return (c[0] + rr * math.sin(ar), c[1] - rr * math.cos(ar))
    def frame(a):
        ar = math.radians(a)
        return (math.sin(ar), -math.cos(ar)), (math.cos(ar), math.sin(ar))       # outward, along the rim (growing angle)
    dd = math.degrees(rho / r)
    pts = []
    nb, tb = frame(a_bot)
    cb = at(a_bot, r)
    ctr = (cb[0] + tb[0] * rho + nb[0] * rho, cb[1] + tb[1] * rho + nb[1] * rho)
    pts.append((cb[0] + nb[0] * tick, cb[1] + nb[1] * tick))
    for i in range(n + 1):
        s_ = math.radians(90 - 90 * i / n)
        pts.append((ctr[0] + rho * (-nb[0] * math.cos(s_) - tb[0] * math.sin(s_)), ctr[1] + rho * (-nb[1] * math.cos(s_) - tb[1] * math.sin(s_))))
    pts += arc(c, r, a_bot + dd, a_top - dd, 96)
    nt, tt_ = frame(a_top)
    ct = at(a_top, r)
    ut = (-tt_[0], -tt_[1])                                                        # into the rim at the top end
    ctr = (ct[0] + ut[0] * rho + nt[0] * rho, ct[1] + ut[1] * rho + nt[1] * rho)
    for i in range(n + 1):
        s_ = math.radians(90 * i / n)
        pts.append((ctr[0] + rho * (-nt[0] * math.cos(s_) - ut[0] * math.sin(s_)), ctr[1] + rho * (-nt[1] * math.cos(s_) - ut[1] * math.sin(s_))))
    pts.append((ct[0] + nt[0] * tick, ct[1] + nt[1] * tick))
    return pts


def gauge(name, side, labels, fills, extra):
    """One ribbon gauge. side -1 = left as measured, +1 = mirrored to the right."""
    m = (lambda p: p) if side < 0 else (lambda p: (W - p[0], p[1]))
    c = P(*GC, -1)
    r_out, r_in = R_OUT * S, R_IN * S
    r_line = r_out + 4                               # the outline is the ribbon's own rim: no gap, one piece
    r_mid, band = (r_out + r_in) / 2, r_out - r_in
    k = []
    # outline, outside the ribbon with a dark gap, and a notch at every label
    line = [m(p) for p in arc(c, r_line, A_BOT, A_TOP)]          # the fills keep their own gradient range
    ys = [p[1] for p in line]
    rim = [m(p) for p in rim_path(c, r_line, A_BOT, A_TOP)]      # the rim: one stroke, the end ticks are part of it
    rys = [p[1] for p in rim]
    k.append(shape(f"{name} Outline", poly(rim, False) + grad_stroke(6, [("FF9B2BF2", 0), (OUTLINE, 0.5), ("FFFF3FD8", 1)], 0, max(rys), 0, min(rys), cap="round")))
    # the ribbon body: outer arc to a slanted top cut, inner arc back to a slanted bottom cut
    body = [m(p) for p in arc(c, r_out, A_BOT, A_TOP) + arc(c, r_in, A_TOP - 8, A_BOT + 5)]
    clip_id = nid()
    k.append(f'<Shape x="0" y="0" rotation="0" name="{name} Track" id="{clip_id}">{poly(body)}{fill(TRACK)}</Shape>')
    clip = f'<ClippingShape sourceId="{clip_id}" name="Clip"/>'
    # diagonal hatching in the unlit part
    bx = [p[0] for p in body]
    by = [p[1] for p in body]
    x0, x1, y0, y1 = min(bx) - 60, max(bx) + 60, min(by) - 60, max(by) + 60
    hatch = []
    tilt = math.radians(-32 * side)                  # stripes lean the same way the ribbon runs on each side
    for i, hx in enumerate(range(int(x0 - (y1 - y0) * 0.7), int(x1 + (y1 - y0) * 0.7), 17)):
        hatch.append(f'<Rectangle x="{hx:.1f}" y="{(y0 + y1) / 2:.1f}" width="6" height="{(y1 - y0) * 1.3:.1f}" '
                     f'originX="0.5" originY="0.5" rotation="{tilt:.4f}" name="H{i}"/>')
    k.append(f'<Shape x="0" y="0" rotation="0" name="{name} Hatch">{"".join(hatch)}{fill(HATCH)}{clip}</Shape>')
    # live fills along the ribbon's centre line, clipped to the ribbon so the ends stay cut
    mid = poly([m(p) for p in arc(c, r_mid, A_BOT - 2, A_TOP + 1)], False)
    for i, (stops, b) in enumerate(fills):
        k.append(shape(f"{name} Glow {i}", mid + grad_stroke(band * 0.9, [(s[0][:2].replace("FF", "66") + s[0][2:], s[1]) for s in stops],
                                                            0, max(ys), 0, min(ys), trim(0, 0, b) + glow(26))).replace("</Shape>", clip + "</Shape>"))
        k.append(f'<Shape x="0" y="0" rotation="0" name="{name} Fill {i}">{mid}'
                 f'{grad_stroke(band + 8, stops, 0, max(ys), 0, min(ys), trim(0, 0, b))}{clip}</Shape>')
    # notches and numbers
    for i, lab in enumerate(labels):
        a = A_BOT + (A_TOP - A_BOT) * i / (len(labels) - 1)
        nx, ny = arc(c, r_line + 13, a, a, 1)[0]                    # ticks stand just outside the rim
        tx, ty = arc(c, r_line + 50, a, a, 1)[0]
        nx, ny = m((nx, ny))
        tx, ty = m((tx, ty))
        if 0 < i < len(labels) - 1:                                   # the two end ticks are part of the rim path
            k.append(shape(f"{name} Notch {i}", rect(7, 22) + fill(OUTLINE), nx, ny, math.radians(a if side < 0 else 360 - a)))
        k.append(text(f"{name} Num {i}", [(lab, "")], tx, ty, 30, WHITE, EXO, w=90))
    return node(name, k + extra)


def arrow(name, x, y, d, b):
    s = 1 if d > 0 else -1
    pts = [(-s * 30, -9), (s * 2, -9), (s * 2, -22), (s * 30, 0), (s * 2, 22), (s * 2, 9), (-s * 30, 9)]
    return [shape(f"{name} Dim", poly(pts) + fill("FF302C36"), x, y),
            node(name, [shape(f"{name} Glow", poly(pts) + fill("9935D879", glow(14))), shape(f"{name} Lit", poly(pts) + fill("FF35D879"))], x, y, 0, b)]


def neighbour(name, x, y, prop, b_amber, b_red, side):
    """A car in the next lane while that side's blind spot reports one: the same sedan, smaller than ours,
    softly tinted amber (red when urgent), sliding up from behind as it arrives."""
    def car(color, nm):
        img = (f'<Image assetId="9:21" x="0" y="0" scaleX="0.30" scaleY="0.30" blendModeValue="screen" name="{nm} Image" id="{nid()}"/>')
        tint = f'<Shape x="0" y="0" rotation="0" blendModeValue="multiply" name="{nm} Tint">{rect(100, 180, 0, 0, 20)}{fill(color)}</Shape>'
        return [shape(f"{nm} Halo", rect(56, 120, 0, 0, 26) + fill("40" + color[2:], glow(22))), img, tint]
    warn = node(f"{name} Warn", [shape(f"{name} Warn Glow", poly([(0, -19), (20, 15), (-20, 15)]) + fill("66FF3B30", glow(10))),
                                  shape(f"{name} Warn Sign", poly([(0, -19), (20, 15), (-20, 15)]) + fill(RED)),
                                  text(f"{name} Warn Mark", [("!", "")], 0, 3, 30, "FFFFF4F2", EXO_B, w=30)], side * 70, -62)
    return [node(f"{name} Slide", [node(f"{name} Amber", car("FFFFB547", f"{name} A"), 0, 0, 0, b_amber),
                                   node(f"{name} Red", car("FFFF5B4D", f"{name} R") + [warn], 0, 0, 0, b_red)],
                 0, 0, 0, bind(prop, Y, "8:104")).replace('<Node ', f'<Node opacity="0.9" ', 1).replace(' y="0.00"', f' y="{y:.2f}"', 1).replace(' x="0.00"', f' x="{x:.2f}"', 1)]


def fan(name, x, y, side, b_amber, b_red):
    """A quarter fan of dashes off the car's rear corner: dim always, amber or red when something is there."""
    def dashes(color, nm):
        k = []
        for i in range(11):
            a = math.radians((200 + i * 7.5) if side < 0 else (160 - i * 7.5))
            px, py = 50 * math.sin(a), -50 * math.cos(a)
            k.append(shape(f"{nm} {i}", rect(5, 34, 0, 0, 2) + fill(color), px, py, a))
        return k
    return [node(f"{name} Dim", dashes("FF2C2A30", f"{name} Dim"), x, y),
            node(f"{name} Amber", [node(f"{name} Amber Glow", [shape(f"{name} Halo", ellipse(150) + fill("55FFC21A", glow(40)))])] + dashes(AMBER, f"{name} A"), x, y, 0, b_amber),
            node(f"{name} Red", [shape(f"{name} Red Halo", ellipse(150) + fill("66FF3B30", glow(40)))] + dashes(RED, f"{name} R"), x, y, 0, b_red)]


def centre():
    k = []
    # top of the centre column: clock, then the traffic light, then the limit sign, all on the centre line
    cx, cy = W / 2, 70
    k.append(text("Clock", [("07", bind("time", TEXT, "2:28")), (":", ""), ("25", bind("time", TEXT, "2:30"))], cx, cy, 34, "B3F4F1F7", EXO_B, w=200))   # quiet: a glance, not a headline
    ty = 130
    k.append(shape("Light Pill", rect(120, 36, 0, 0, 18) + fill("FF121116") + stroke("FF2A2830", 1.5), cx, ty))
    for i, (c, conv) in enumerate(((RED, "2:11"), ("FFFFC53D", "2:13"), ("FF34D27A", "2:12"))):
        jx = cx + (i - 1) * 38
        k.append(shape(f"Light Socket {i}", ellipse(18) + fill("FF24222A"), jx, ty))
        k.append(node(f"Light On {i}", [shape(f"Light Glow {i}", ellipse(32) + fill("CC" + c[2:], glow(7))), shape(f"Light {i}", ellipse(24) + fill(c))],
                      jx, ty, 0, bind("light", OPACITY, conv)))
    lx, ly = P(768, 207)
    k.append(node("Limit", [shape("Limit Disc", ellipse(92) + fill("FFFFFFFF") + stroke(RED, 12)),
                            text("Limit Num", [("80", bind("limit", TEXT, "2:1"))], 0, 1, 40, "FF111111", EXO_B, w=90)],
                  lx, ly, 0, bind("limit", OPACITY, "2:5")))
    k += arrow("Blink L", *P(571, 205), -1, bind("lblink", OPACITY, "8:68"))
    k += arrow("Blink R", *P(965, 205), 1, bind("rblink", OPACITY, "8:68"))
    # the car and its blind-spot fans
    carx, cary = P(768, 470)
    k.append(shape("Car Floor", ellipse(260) + fill("1AFFFFFF", glow(60)), carx, cary + 14))
    k.append(f'<Image assetId="9:21" x="{carx:.1f}" y="{cary:.1f}" scaleX="0.54" scaleY="0.54" blendModeValue="screen" name="Car" id="{nid()}"/>')
    k += [node("Blind L Lane", neighbour("Blind L", 0, 0, "lbs", bind("lbs", OPACITY, "8:105"), bind("lbs", OPACITY, "8:106"), -1), W / 2 - 245, 600)]
    k += [node("Blind R Lane", neighbour("Blind R", 0, 0, "rbs", bind("rbs", OPACITY, "8:105"), bind("rbs", OPACITY, "8:107"), 1), W / 2 + 245, 600)]
    # driver assist: a blue steering wheel and ASSIST ON under the car while it is engaged
    ay = 728
    # a real steering wheel: rim, hub and three spokes (the old ring and bar read as a minus button)
    wx = -118
    blue = "FF64CFFF"
    k.append(node("Assist", [shape("Assist Glow", ellipse(40) + fill("3364CFFF", glow(10)), wx, 0),
                             shape("Assist Rim", ellipse(34) + stroke(blue, 3.5), wx, 0),
                             shape("Assist Hub", ellipse(9) + fill(blue), wx, 2),
                             shape("Assist Spoke L", rect(11, 3.5, 0, 0, 1.5) + fill(blue), wx - 10, 2),
                             shape("Assist Spoke R", rect(11, 3.5, 0, 0, 1.5) + fill(blue), wx + 10, 2),
                             shape("Assist Spoke D", rect(3.5, 11, 0, 0, 1.5) + fill(blue), wx, 10),
                             text("Assist Text", [("ASSIST ON", "")], 16, 0, 34, blue, EXO, w=260, spacing=3)],
                  W / 2, ay, 0, bind("adas", OPACITY, "2:41")))
    # battery: a small ribbon of its own, cut at the same slant as the big ribbons' ends. Same rim, same
    # liquid: a bright surface line at the level (slanted like the cuts), a gloss along the top, quarter ticks
    # on the rim, and the liquid turns red when the charge runs low
    bx, by, bw, bh, sk = W / 2 + 18, P(768, 762)[1], 236, 26, 12     # group centred on the car
    cell = [(bx - bw / 2 + sk, by - bh / 2), (bx + bw / 2 + sk, by - bh / 2), (bx + bw / 2 - sk, by + bh / 2), (bx - bw / 2 - sk, by + bh / 2)]
    bid = nid()
    k.append(f'<Shape x="0" y="0" rotation="0" name="Battery Track" id="{bid}">{poly(cell)}{fill(TRACK)}</Shape>')
    bclip = f'<ClippingShape sourceId="{bid}" name="Clip"/>'
    stripes = "".join(f'<Rectangle x="{hx}" y="{by:.1f}" width="4" height="60" originX="0.5" originY="0.5" rotation="0.56" name="H{i}"/>'
                      for i, hx in enumerate(range(int(bx - bw / 2 - 40), int(bx + bw / 2 + 40), 11)))
    k.append(f'<Shape x="0" y="0" rotation="0" name="Battery Hatch">{stripes}{fill(HATCH)}{bclip}</Shape>')
    full = bw + 2 * sk
    bx0 = bx - bw / 2 - sk                                           # the cell's bottom-left corner: origin of the level nodes
    liquid = (f'<Shape x="0" y="0" rotation="0" name="Battery Liquid">{rect(full, bh, full / 2, 0)}'
              f'{grad_fill("LinearGradient", [(FILL[0][0], 0), (FILL[1][0], 0.45), (FILL[3][0], 1)], sx=0, sy=0, ex=full, ey=0)}{bclip}</Shape>')
    low = (f'<Shape x="0" y="0" rotation="0" name="Battery Low">{rect(full, bh, full / 2, 0)}'
           f'{grad_fill("LinearGradient", [("FFFF453A", 0), ("FFFF7A5C", 1)], sx=0, sy=0, ex=full, ey=0)}{bclip}</Shape>')
    # the liquid is always full; the empty part is a slanted cover that slides over it, so the fill ends on the same cut as the shape
    k.append(node("Battery Level", [liquid, node("Battery Low Tint", [low], 0, 0, 0, bind("energy", OPACITY, "9:20"))], bx0, by))
    k.append(shape("Battery Gloss", poly([(bx - bw / 2 + sk - 2, by - bh / 2 + 4), (bx + bw / 2 + sk - 4, by - bh / 2 + 4)], False)
                   + stroke("59FFFFFF", 2)).replace("</Shape>", bclip + "</Shape>"))
    # the empty part: a slanted cover that slides over the full liquid. Only filled shapes here: an open stroked line or a
    # clip whose source moves turns the Android GPU layer off or black (found on the phone 26 Oct)
    cover = [(sk, -bh / 2), (full + 40, -bh / 2), (full + 40, bh / 2), (-sk, bh / 2)]
    def cut_band(off, w):                                            # a bar parallel to the cut, off px to its right
        return [(sk + off, -bh / 2), (sk + off + w, -bh / 2), (-sk + off + w, bh / 2), (-sk + off, bh / 2)]
    lean = bh * math.tan(0.56)                                       # the hatch slant of the bar
    stripes = "".join(poly([(b + lean, -bh / 2), (b + lean + 4, -bh / 2), (b + 4, bh / 2), (b, bh / 2)])
                      for b in range(0, int(full + 40), 11))         # every stripe starts right of the cut
    empty = shape("Battery Empty", poly(cover) + fill(TRACK)).replace("</Shape>", bclip + "</Shape>")
    ehatch = shape("Battery Empty Hatch", stripes + fill(HATCH)).replace("</Shape>", bclip + "</Shape>")
    sline = shape("Battery Surface Glow", poly(cut_band(-4, 8)) + fill("66FF91EA")).replace("</Shape>", bclip + "</Shape>")
    sedge = shape("Battery Surface", poly(cut_band(-1.5, 3)) + fill("F2FFF0FC")).replace("</Shape>", bclip + "</Shape>")
    k.append(node("Battery Empty Offset", [node("Battery Empty Pos", [empty, ehatch, sline, sedge], 0, 0, 0, bind("energy", X, "9:70"))], bx0, by))
    # the rim: the ribbons' magenta, so the bar reads as part of the same set; ticks at 25, 50 and 75 %
    k.append(shape("Battery Rim", poly(cell) + grad_stroke(2, [("FF9B2BF2", 0), (OUTLINE, 0.5), ("FFFF3FD8", 1)], bx - bw / 2, 0, bx + bw / 2, 0)))
    k.append(text("Charge", [("69", bind("energy", TEXT, "2:17")), ("%", "", 26, SOFT, EXO_B)], bx + bw / 2 + 74, by, 40, WHITE, EXO_B, w=150))
    k.append(text("Battery Label", [("BATTERY", "")], bx - bw / 2 - 100, by, 28, "FFAAA3B5", EXO_B, w=170, spacing=2))
    # gear: P R N D as round chips; the engaged one fills with the ribbon's pink and glows
    gy = P(768, 868)[1]
    for i, (g_, conv) in enumerate((("P", "2:11"), ("R", "2:12"), ("N", "2:13"), ("D", "2:14"))):
        gx = W / 2 + (i - 1.5) * 74
        k.append(shape(f"Chip {g_}", ellipse(58) + fill("FF111015") + stroke("FF3A3740", 2), gx, gy))
        k.append(text(f"Gear {g_} Dim", [(g_, "")], gx, gy, 34, "FF8F8A99", EXO_B, w=60))   # Chakra Petch caps sit centred as set
        k.append(node(f"Gear {g_} On", [shape(f"Chip {g_} Glow", ellipse(66) + fill("88E51FD0", glow(22))),
                                         shape(f"Chip {g_} Lit", ellipse(58) + grad_fill("LinearGradient", [(FILL[3][0], 0), (FILL[1][0], 1)], sx=0, sy=-29, ex=0, ey=29)),
                                         shape(f"Chip {g_} Rim", ellipse(58) + stroke("66FFFFFF", 1.5)),
                                         text(f"Gear {g_}", [(g_, "")], 0, 0, 36, WHITE, EXO_B, w=60)],
                      gx, gy, 0, bind("gear", OPACITY, conv)))
    return node("Centre", k)


def punch_text(name, prop, conv, size, color, w, x, y, hot, cool, scale):
    """A big number with punch and glow: the white number, a hot-pink copy and a cool lavender copy that fade in with
    effort (the GPU script writes the three numbers), all scaled together on every change. Still and white at rest."""
    def t(nm, col, glow_px=0, opac=None):
        return text(nm, [("86", bind(prop, TEXT, conv))], 0, 0, size, col, EXO_B, w=w, glow_px=glow_px,
                    binds=(bind(opac, OPACITY) if opac else ""))
    return node(name + " Punch", [t(name, color), t(name + " Hot", "FFFF86E6", 20, hot), t(name + " Cool", "FFB9A8FF", 20, cool)],
                x, y, 0, bind(scale, SCALE_X) + bind(scale, SCALE_Y))


def readouts(side):
    # speed leads: 200 px; power answers at 112 px, signed, so regen reads as a negative kW
    if side < 0:
        x, y = P(405, 462, -1)
        ux, uy = P(408, 557, -1)
        k = [punch_text("Speed", "speed", "2:1", 200, "FFF6F3F8", 520, x, y, "spdHot", "spdCool", "spdPunch"),
             text("Speed Unit", [("km/h", "")], ux, uy + 16, 42, "FFC9C5D0", EXO, w=200),
             node("Set", [text("Set Speed", [("CRUISE  ", "", 28, "FFBCA5F4", EXO_B), ("95", bind("set", TEXT, "2:1"))], ux, uy + 76, 34, "FFBCA5F4", EXO_B, w=300,
                               binds=bind("speed", OPACITY, "9:63")),
                          text("Set Stored", [("SET  ", "", 28, "FF8E8A96", EXO_B), ("95", bind("set", TEXT, "2:1"))], ux, uy + 76, 34, "FF8E8A96", EXO_B, w=300,
                               binds=bind("speed", OPACITY, "9:62"))],
                  0, 0, 0, bind("adas", OPACITY, "2:41"))]
    else:
        x, y = P(1150, 462, 1)
        ux, uy = P(1150, 557, 1)
        k = [punch_text("Kilowatts", "amps", "9:13", 112, "FFE2DDE8", 360, x, y + 10, "kwHot", "kwCool", "kwPunch"),
             text("Kw Unit", [("kW", "")], ux, uy - 20, 32, "FFB9B3C2", EXO, w=200),
             text("Regen Lit", [("REGEN", "")], ux, uy + 26, 30, "FFC7B7FF", EXO_B, w=200, spacing=2, binds=bind("amps", OPACITY, "9:16"))]
    return k


def instrument(gpu=False):
    left = gauge("Speed", -1, [str(v) for v in range(0, 201, 40)],
                 [] if gpu else [(FILL, bind("speed", TRIM_END, "9:2"))], readouts(-1))
    cx_, cy_ = P(150, 868, -1)
    captions = [text("Power Caption", [("kW", "")], W - cx_, cy_, 24, "FFAAA3B5", EXO_B, w=160, spacing=2)]
    right = gauge("Power", 1, ["0", "25", "50", "75", "100"],
                  [] if gpu else [(REGEN, bind("amps", TRIM_END, "9:15")), (FILL, bind("amps", TRIM_END, "9:14"))], readouts(1))
    return node("Instrument", [left, right, centre()] + captions)


def scene():
    # Dash: what DashPilot loads. The GPU layer on top, the native instrument under it (hidden once the layer runs).
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
    # Instrument: the same instrument, drawn by the GPU layer into a texture with the live view model.
    inst = f'''    <Artboard defaultStateMachineId="6:5" viewModelId="1:1" styleId="6:2" clip="true"
              x="2600" y="0" width="{W}" height="{H}" name="Instrument" id="6:1">
        <LayoutComponentStyle name="Instrument Style" id="6:2"/>
        <Fill name="Background"><SolidColor colorValue="FF000000" name="C"/></Fill>
        {instrument(gpu=True)}
        {SM.format(s=" I", a="6:4", n="Instrument", m="6:5", l="6:6", st="6:7")}
    </Artboard>'''
    return f'''<Rive version="1" kind="fragment">
    <ScriptAsset file="ribbon_fx.luau" name="ribbon_fx" id="0:79"/>
    <ImageAsset file="car.png" name="White Sedan" id="9:21"/>
{dash}
{inst}
{native_ab}
</Rive>
'''


def merged(doc):
    parts = []
    for f in ("viewmodel.part", "converters.part", "mine.part", "ribbon.part"):
        s = open(f).read()
        s = re.sub(r"<Rive[^>]*>", "", s, count=1)
        parts.append(s[:s.rindex("</Rive>")])
    return doc.replace("</Rive>", "\n".join(parts) + "\n</Rive>")


if __name__ == "__main__":
    open("scene.rml", "w").write(merged(scene()))
    print("scene.rml written")
