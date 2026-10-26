-- Ribbon GPU layer. Renders the native Instrument artboard (bound to DashPilot's live view model)
-- into a texture every frame and finishes it in ribbonglass.wgsl: neon bloom, glossy ribbons with
-- energy flowing in them, a white-hot head on each fill, streetlights sliding over the car, lane
-- marks passing at the car's speed, blind-spot radar, and light from the traffic light and blinkers.
-- Everything moves only because the car moves: parked, the screen is still.
-- If this script cannot run, nothing here draws and the native copy in Dash stays visible.
type Fx = {
    ui: Input<Artboard<Data.DashViewModel>>,
    inst: Artboard<Data.DashViewModel>?,
    vm: ViewModel?,
    full: Canvas?, small: Canvas?, out: GPUCanvas?,
    pipe: GPUPipeline?, group: GPUBindGroup?, ubo: GPUBuffer?, buf: buffer,
    samp: GPUSampler?, img: ImageSampler,
    w: number, h: number, k: number, t: number, ready: boolean, scale: number, sizedW: number,
    -- smoothed state
    spd: number, trail: number, pow: number, regen: number, over: number,
    red: number, green: number, yellow: number, lbs: number, rbs: number,
    lPrev: number, rPrev: number, lT: number, rT: number, lOn: number, rOn: number,
    flowL: number, flowR: number, road: number, glass: number, assist: number,
    -- liquid: each ribbon's surface tilts with how fast its level moves, swings back and settles
    prevL: number, prevR: number, velL: number, velR: number,
    tiltL: number, tiltR: number, tvL: number, tvR: number, ripL: number, ripR: number,
    -- shown levels (the start-up sweep on top of the data) and the boil when over the limit
    showL: number, showR: number, boil: number, bootOff: boolean,
    -- punch and glow on the big numbers: smoothed rate of change, pulse, last values
    rS: number, rP: number, pS: number, pP: number, lastSp: number, lastKw: number, lastSpI: number, lastKwI: number, pgInit: boolean,
}

local MAXW = 2400
-- layout, in artboard points (ribbon.py lays the instrument out on 2280 x 1080)
local GX, GY, R_IN, R_OUT, R_LINE = 651.85, 506.4, 296.1, 400.05, 404.05   -- the outline sits on the ribbon rim      -- left gauge; the right one mirrors
local A_BOT, A_SPAN = math.rad(171), math.rad(189)
local CAR_X, CAR_Y, CAR_TOP, CAR_BOT, CAR_HW = 1140, 496, 344, 648, 70   -- car drawn at 0.54
local FAN_X, FAN_Y = 1072, 620                                                -- left rear fan; the right mirrors

local function num(vm: ViewModel, name: string): number
    local p = vm:getNumber(name)
    if p then return p.value end
    return 0
end

local function ease(cur: number, target: number, rate: number, dt: number): number
    return cur + (target - cur) * (1 - math.exp(-rate * dt))
end

function init(self: Fx, context: Context): boolean
    local vm = context:viewModel()
    if vm == nil then return true end          -- init runs twice; the view model arrives the second time
    local sh = context:shader('ribbonglass')
    if sh == nil then
        print('ribbon_fx: shader missing')
        return false
    end
    self.vm = vm
    self.inst = self.ui:instance(vm)
    self.full, self.small = context:canvas(), context:canvas()
    local out = context:gpuCanvas()
    self.out = out
    self.samp = GPUSampler.new({ min = 'linear', mag = 'linear', wrapU = 'clamp-to-edge', wrapV = 'clamp-to-edge' })
    self.ubo = GPUBuffer.new({ size = 192, usage = 'uniform' })
    self.pipe = GPUPipeline.new({ vertex = sh, fragment = sh, vertexLayout = {}, colorTargets = { { format = out.format } } })
    local fx = vm:getBoolean('fxActive')
    if fx then fx.value = true end             -- hides the native copy underneath
    self.ready = true
    return true
end

-- resize can arrive before init has the view model, so sizing is applied lazily from draw
local function applySize(self: Fx)
    if self.w < 1 or self.h < 1 then return end
    self.k = math.min(self.scale, MAXW / self.w)
    local W, H = math.floor(self.w * self.k), math.floor(self.h * self.k)
    local full, small, out = self.full, self.small, self.out
    if full == nil or small == nil or out == nil then return end
    full:resize(W, H)
    small:resize(math.floor(W / 4), math.floor(H / 4))
    out:resize(W, H)
    self.group = nil
    self.sizedW = W
end

function resize(self: Fx, size: Vector, scale: number)
    self.w, self.h, self.scale = size.x, size.y, scale
    self.sizedW = 0
end

function advance(self: Fx, seconds: number): boolean
    self.t += seconds
    local dt = math.min(seconds, 1 / 20)
    local inst, vm = self.inst, self.vm
    if inst then inst:advance(seconds) end
    if vm == nil then return true end
    local speed, limit = num(vm, 'egoSpeed'), num(vm, 'fusedSpeedLimit')
    local gear = num(vm, 'gear')
    -- the fills, as fractions of their ribbons; the trail is a slower copy that lights acceleration
    self.spd = ease(self.spd, math.clamp(speed / 200, 0, 1), 9, dt)
    self.trail = ease(self.trail, self.spd, 2.5, dt)
    -- the power ribbon reads the same kW as its number: pack volts x amps, 0-100 kW full scale
    local kw = num(vm, 'packVoltage') * num(vm, 'packCurrent') / 1000
    self.pow = ease(self.pow, math.clamp(kw / 100, 0, 1), 6, dt)
    -- punch and glow on the two big numbers: they warm up while accelerating, cool while braking or in regen,
    -- and pulse a little whenever the shown value changes. The view-model numbers fade tinted copies in (ribbon.py).
    if not self.pgInit then
        self.pgInit = true
        self.lastSp, self.lastKw = speed, kw
        self.lastSpI, self.lastKwI = math.floor(speed + 0.5), math.floor(kw + 0.5)
    end
    if dt > 0 then
        self.rS = ease(self.rS, (speed - self.lastSp) / dt, 6, dt)
        self.rP = ease(self.rP, (kw - self.lastKw) / dt, 6, dt)
    end
    self.lastSp, self.lastKw = speed, kw
    local si, ki = math.floor(speed + 0.5), math.floor(kw + 0.5)
    if si ~= self.lastSpI then self.pS = 1 end
    if ki ~= self.lastKwI then self.pP = 1 end
    self.lastSpI, self.lastKwI = si, ki
    self.pS = ease(self.pS, 0, 9, dt)
    self.pP = ease(self.pP, 0, 9, dt)
    local function put(name: string, v: number)
        local pr = vm:getNumber(name)
        if pr then pr.value = v end
    end
    put('spdHot', math.clamp(self.rS / 26, 0, 1))
    put('spdCool', math.clamp(-self.rS / 30, 0, 1))
    put('spdPunch', 1 + 0.025 * self.pS)
    put('kwHot', kw > 0 and math.clamp(self.rP / 90, 0, 1) or 0)
    put('kwCool', math.max(math.clamp(-kw / 12, 0, 1), math.clamp(-self.rP / 90, 0, 1)))
    put('kwPunch', 1 + 0.02 * self.pP)
    self.regen = ease(self.regen, math.clamp(-kw / 100, 0, 1), 6, dt)
    self.over = ease(self.over, (limit > 0) and math.clamp((speed - limit) / 15, 0, 1) or 0, 4, dt)
    -- lights come in over ~250 ms and leave over ~200 ms
    local function fade(cur: number, target: number): number
        return ease(cur, target, target > cur and 12 or 15, dt)
    end
    local light = num(vm, 'trafficLightColor')
    self.red = fade(self.red, light == 1 and 1 or 0)
    self.green = fade(self.green, light == 2 and 1 or 0)
    self.yellow = fade(self.yellow, light == 3 and 1 or 0)
    self.lbs = fade(self.lbs, num(vm, 'leftBlindSpot'))
    self.rbs = fade(self.rbs, num(vm, 'rightBlindSpot'))
    -- blinkers: remember when the real lamp lights, so the light runs in sync with it
    local lb, rb = num(vm, 'leftBlinker'), num(vm, 'rightBlinker')
    if lb == 2 and self.lPrev ~= 2 then self.lT = self.t end
    if rb == 2 and self.rPrev ~= 2 then self.rT = self.t end
    self.lPrev, self.rPrev = lb, rb
    self.lOn = ease(self.lOn, lb == 2 and 1 or 0, lb == 2 and 30 or 6, dt)
    self.rOn = ease(self.rOn, rb == 2 and 1 or 0, rb == 2 and 30 or 6, dt)
    -- motion phases: energy runs up the ribbons with speed and power (down the power ribbon in regen),
    -- the road and the streetlights pass at the car's speed, backwards in R
    local dir = gear == 2 and -1 or 1
    -- (no wrap: the bubbles ride this phase, and wrapping it made them jump)
    self.flowL = self.flowL + self.spd * 0.9 * dt
    self.flowR = self.flowR + (self.pow - self.regen) * 0.8 * dt
    self.road = (self.road + speed * 2.6 * dir * dt) % 2160
    self.glass = ease(self.glass, 1, 2.5, dt)
    local a = vm:getBoolean('adasOn')
    self.assist = ease(self.assist, (a and a.value) and 1 or 0, 6, dt)
    -- start-up: both ribbons pour full in 0.9 s, hold, then drain to the real reading by 2 s
    local bt = self.t - 0.35
    local boot = 0
    if bt > 0 and bt < 0.9 then
        local x = bt / 0.9
        boot = 1 - (1 - x) ^ 3
    elseif bt >= 0.9 and bt < 1.15 then
        boot = 1
    elseif bt >= 1.15 and bt < 2.0 then
        local x = (bt - 1.15) / 0.85
        boot = 1 - x * x * (3 - 2 * x)
    end
    if self.t < 0.35 then boot = 0 end
    -- only on a parked car: moving at any point cancels it and the ribbons jump to the live levels
    if speed > 1 then self.bootOff = true end
    if self.bootOff then boot = 0 end
    self.showL = math.max(self.spd, boot)
    self.showR = math.max(math.max(self.pow, self.regen), boot)
    -- over the limit the speed liquid boils: eases in over ~0.4 s, settles over ~0.8 s
    self.boil = ease(self.boil, self.over, self.over > self.boil and 6 or 3, dt)
    -- liquid slosh: a damped spring pulls each surface toward a tilt set by how fast the level moves
    local levR = self.showR
    if dt > 0 then
        self.velL = ease(self.velL, (self.showL - self.prevL) / dt, 8, dt)
        self.velR = ease(self.velR, (levR - self.prevR) / dt, 8, dt)
    end
    self.prevL, self.prevR = self.showL, levR
    local function slosh(tilt: number, tv: number, vel: number): (number, number)
        local target = math.clamp(vel * 11, -1.2, 1.2)
        tv += (-(tilt - target) * 55 - tv * 3.2) * dt
        return tilt + tv * dt, tv
    end
    self.tiltL, self.tvL = slosh(self.tiltL, self.tvL, self.velL)
    self.tiltR, self.tvR = slosh(self.tiltR, self.tvR, self.velR)
    -- ripples: the road shakes the surface a little while moving, level changes stir it more
    local road = math.clamp(speed / 120, 0, 1) * 0.35
    self.ripL = ease(self.ripL, road + math.min(math.abs(self.velL) * 9, 1) + self.boil * 1.4, 3, dt)
    self.ripR = ease(self.ripR, road + math.min(math.abs(self.velR) * 9, 1), 3, dt)
    return true
end

-- the instrument keeps its 2280 x 1080 design: scaled to fit the screen and centred (contain)
local DW, DH = 2280, 1080
local function fit(self: Fx): (number, number, number)
    local s = math.min(self.w / DW, self.h / DH)
    return s, (self.w - DW * s) / 2, (self.h - DH * s) / 2
end

local function paint(canvas: Canvas, inst: Artboard<Data.DashViewModel>, k: number, s: number, ox: number, oy: number)
    local r = canvas:beginFrame({ clearColor = Color.rgba(0, 0, 0, 255) })
    r:save()
    local m = Mat2D.identity()
    m.xx, m.yy = k * s, k * s
    m.tx, m.ty = k * ox, k * oy
    r:transform(m)
    inst:draw(r)
    r:restore()
    canvas:endFrame()
end

function draw(self: Fx, renderer: Renderer)
    local full, small, out, pipe, ubo, samp, inst, vm =
        self.full, self.small, self.out, self.pipe, self.ubo, self.samp, self.inst, self.vm
    if not self.ready or full == nil or small == nil or out == nil or pipe == nil or ubo == nil
        or samp == nil or inst == nil or vm == nil or self.w < 1 then
        return
    end
    if self.sizedW == 0 then applySize(self) end
    if self.sizedW == 0 then return end
    local s, ox, oy = fit(self)
    paint(full, inst, self.k, s, ox, oy)
    paint(small, inst, self.k / 4, s, ox, oy)
    local g: GPUBindGroup
    local cached = self.group
    if cached ~= nil then
        g = cached
    else
        g = GPUBindGroup.new({ layout = pipe:getBindGroupLayout(0),
            textures = { { slot = 0, view = full.image:view() }, { slot = 1, view = small.image:view() } },
            samplers = { { slot = 2, sampler = samp } }, ubos = { { slot = 3, buffer = ubo } } })
        self.group = g
    end
    local q = num(vm, 'renderQuality')
    if q < 1 then q = 3 end
    local v = self.buf
    local function put(i: number, x: number) buffer.writef32(v, i * 4, x) end
    put(0, self.w); put(1, self.h); put(2, self.t); put(3, q)
    local bootR = math.max(self.showR - math.max(self.pow, self.regen), 0)
    put(4, self.showL); put(5, math.min(self.trail, self.showL)); put(6, self.pow + bootR); put(7, self.regen)
    put(8, self.red); put(9, self.green); put(10, self.yellow); put(11, self.over)
    put(12, self.lOn); put(13, self.rOn); put(14, self.t - self.lT); put(15, self.t - self.rT)
    put(16, GX); put(17, GY); put(18, R_IN); put(19, R_OUT)
    put(20, R_LINE); put(21, A_BOT); put(22, A_SPAN); put(23, num(vm, 'egoSpeed'))
    put(24, CAR_X); put(25, CAR_Y); put(26, CAR_TOP); put(27, CAR_BOT)
    put(28, CAR_HW); put(29, self.lbs); put(30, self.rbs); put(31, self.glass)
    put(32, self.flowL); put(33, self.flowR); put(34, self.road); put(35, self.assist)
    put(36, FAN_X); put(37, FAN_Y); put(38, self.boil); put(39, 0)
    put(40, self.tiltL); put(41, self.tiltR); put(42, self.ripL); put(43, self.ripR)
    put(44, ox); put(45, oy); put(46, s); put(47, 0)
    ubo:write(v)
    local pass = out:beginRenderPass({ color = { { loadOp = 'clear', storeOp = 'store', clearColor = { 0, 0, 0, 1 } } } })
    pass:setPipeline(pipe); pass:setBindGroup(0, g); pass:draw(3); pass:finish()
    renderer:save()
    local m = Mat2D.identity()
    m.xx, m.yy = 1 / self.k, 1 / self.k
    renderer:transform(m)
    renderer:drawImage(out.image, self.img, 'srcOver', 1)
    renderer:restore()
end

return function(context: Context): Layout<Fx>
    return {
        ui = late(), inst = nil, vm = nil, full = nil, small = nil, out = nil,
        pipe = nil, group = nil, ubo = nil, buf = buffer.create(192),
        samp = nil, img = ImageSampler('clamp', 'clamp', 'bilinear'),
        w = 0, h = 0, k = 1, t = 0, ready = false, scale = 1, sizedW = 0,
        spd = 0, trail = 0, pow = 0, regen = 0, over = 0,
        red = 0, green = 0, yellow = 0, lbs = 0, rbs = 0,
        lPrev = 0, rPrev = 0, lT = -10, rT = -10, lOn = 0, rOn = 0,
        flowL = 0, flowR = 0, road = 0, glass = 0, assist = 0,
        showL = 0, showR = 0, boil = 0, bootOff = false,
        rS = 0, rP = 0, pS = 0, pP = 0, lastSp = 0, lastKw = 0, lastSpI = 0, lastKwI = 0, pgInit = false,
        prevL = 0, prevR = 0, velL = 0, velR = 0, tiltL = 0, tiltR = 0, tvL = 0, tvR = 0, ripL = 0, ripR = 0,
        init = init, resize = resize, advance = advance, draw = draw,
    }
end
