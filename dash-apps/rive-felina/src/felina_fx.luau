-- Felina GPU layer. Renders the native Instrument artboard (bound to DashPilot's live view
-- model) into a texture every frame and finishes it in nightglass.wgsl: bloom, glass, a lens over the
-- hub, and light that comes from the car's data (traffic light, blinker lamps, regen, assist).
-- If this script cannot run, nothing here draws and the native copy in Dash stays visible.
type Fx = {
    ui: Input<Artboard<Data.DashViewModel>>,
    inst: Artboard<Data.DashViewModel>?,
    vm: ViewModel?,
    full: Canvas?, small: Canvas?, out: GPUCanvas?,
    pipe: GPUPipeline?, group: GPUBindGroup?, ubo: GPUBuffer?, buf: buffer,
    samp: GPUSampler?, img: ImageSampler,
    w: number, h: number, k: number, t: number, ready: boolean, scale: number, sizedW: number,
    -- smoothed light state
    red: number, green: number, yellow: number, regen: number, assist: number, steer: number,
    lPrev: number, rPrev: number, lT: number, rT: number, lOn: number, rOn: number,
    needle: number, trail: number, gearPrev: number, engT: number, lane: number, introDone: boolean, sweep: number, glass: number,
}

local MAXW = 2400        -- cap the texture width; phones report 3x scale
local DIAL_Y, DIAL_R, HUB_R = 542, 440, 282
local BATT_X, BATT_Y, BATT_R = 348, 582, 200
local SAT_X, SAT_Y, SAT_R = 348, 582, 268      -- left satellite; the right one mirrors from the right edge
-- those are full-size positions (2280 wide). Below 2236 the Gauges artboard (2140 x 944, from x 48, y 70)
-- is shrunk by scaleDown and centred in Main, so the shader's positions follow it through px() and py()
local GW, GH, GX, GY = 2140, 944, 48, 70

local function num(vm: ViewModel, name: string): number
    local p = vm:getNumber(name)
    if p then return p.value end
    return 0
end

local function flag(vm: ViewModel, name: string): number
    local p = vm:getBoolean(name)
    if p and p.value then return 1 end
    return 0
end

-- the dial's own mapping: 0 km/h at 7:30 (-135 deg), 160 at 4:30 (+135 deg)
local function gaugeAngle(v: number): number
    return math.rad(-135 + math.clamp(v, 0, 160) / 160 * 270)
end

local function easeOut(x: number): number
    local c = math.clamp(x, 0, 1)
    return 1 - (1 - c) ^ 3
end

local function easeInOut(x: number): number
    local c = math.clamp(x, 0, 1)
    return c < 0.5 and 4 * c ^ 3 or 1 - (-2 * c + 2) ^ 3 / 2
end

local function setNum(vm: ViewModel, name: string, v: number)
    local p = vm:getNumber(name)
    if p then p.value = v end
end

-- start-up: the centre dial pushes in and fades up, the side dials a beat later, the bars last;
-- readouts count up from zero while the needle sweeps the whole scale and settles back
local function intro(self: Fx, vm: ViewModel)
    if self.introDone then return end
    local T = self.t - 0.2
    local up = easeInOut((T - 0.25) / 0.65)
    local down = easeInOut((T - 1.0) / 0.75)
    self.sweep = math.rad(270) * (up - down)
    self.glass = easeOut(T / 0.7)
    setNum(vm, 'introMain', self.glass)
    setNum(vm, 'introSide', easeOut((T - 0.12) / 0.7))
    setNum(vm, 'introBars', easeOut((T - 0.3) / 0.6))
    setNum(vm, 'introCount', easeOut((T - 0.2) / 1.2))
    setNum(vm, 'introSweep', self.sweep)
    if T > 1.9 then
        self.introDone = true
        self.sweep = 0
        self.glass = 1
        setNum(vm, 'introSweep', 0)
    end
end

local function ease(cur: number, target: number, rate: number, dt: number): number
    return cur + (target - cur) * (1 - math.exp(-rate * dt))
end

function init(self: Fx, context: Context): boolean
    local vm = context:viewModel()
    if vm == nil then return true end          -- init runs twice; the view model arrives the second time
    local sh = context:shader('nightglass')
    if sh == nil then
        print('felina_fx: shader missing')
        return false
    end
    self.vm = vm
    self.inst = self.ui:instance(vm)
    self.full, self.small = context:canvas(), context:canvas()
    local out = context:gpuCanvas()
    self.out = out
    self.samp = GPUSampler.new({ min = 'linear', mag = 'linear', wrapU = 'clamp-to-edge', wrapV = 'clamp-to-edge' })
    self.ubo = GPUBuffer.new({ size = 128, usage = 'uniform' })
    self.pipe = GPUPipeline.new({ vertex = sh, fragment = sh, vertexLayout = {}, colorTargets = { { format = out.format } } })
    local fx = vm:getBoolean('fxActive')
    if fx then fx.value = true end             -- hides the native copy underneath
    for _, n in { 'introMain', 'introSide', 'introBars', 'introCount' } do setNum(vm, n, 0) end
    self.ready = true
    return true
end

-- resize can arrive before init has the view model, so sizing is applied lazily from draw
local function applySize(self: Fx)
    if self.w < 1 or self.h < 1 then return end
    self.k = math.min(self.scale, MAXW / self.w)
    local W, H = math.floor(self.w * self.k), math.floor(self.h * self.k)
    local full, small, out, inst = self.full, self.small, self.out, self.inst
    if full == nil or small == nil or out == nil then return end
    full:resize(W, H)
    small:resize(math.floor(W / 4), math.floor(H / 4))
    out:resize(W, H)
    -- setting inst.width does not lay the instance out again; its Root width is bound to fxWidth instead
    local vm = self.vm
    if vm then setNum(vm, 'fxWidth', self.w) end
    if inst then inst:advance(0) end
    self.group = nil                           -- textures changed size: rebuild the bind group
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
    intro(self, vm)
    local light = num(vm, 'trafficLightColor')
    -- lights come in over ~250 ms and leave over ~200 ms (exponential ease-out)
    local function fade(cur: number, target: number): number
        return ease(cur, target, target > cur and 12 or 15, dt)
    end
    self.red = fade(self.red, light == 1 and 1 or 0)
    self.green = fade(self.green, light == 2 and 1 or 0)
    self.yellow = fade(self.yellow, light == 3 and 1 or 0)
    self.regen = fade(self.regen, math.clamp(-num(vm, 'powerLevel') / 100, 0, 1))
    self.assist = fade(self.assist, flag(vm, 'adasOn'))
    self.steer = ease(self.steer, num(vm, 'egoSteeringAngle'), 6, dt)
    -- blinkers: remember when the real lamp lights, so a sweep starts in sync with it
    local lb, rb = num(vm, 'leftBlinker'), num(vm, 'rightBlinker')
    if lb == 2 and self.lPrev ~= 2 then self.lT = self.t end
    if rb == 2 and self.rPrev ~= 2 then self.rT = self.t end
    self.lPrev, self.rPrev = lb, rb
    self.lOn = ease(self.lOn, lb == 2 and 1 or 0, lb == 2 and 30 or 6, dt)   -- fast attack, slow decay
    self.rOn = ease(self.rOn, rb == 2 and 1 or 0, rb == 2 and 30 or 6, dt)
    -- needle angle (radians, 0 at 12 o'clock, clockwise): the trail is a slower copy of it
    local target = gaugeAngle(num(vm, 'egoSpeed')) + self.sweep
    self.needle = ease(self.needle, target, 18, dt)
    self.trail = ease(self.trail, self.needle, 3.5, dt)
    -- engage: shifting into D from anything else fires a pulse
    local gear = num(vm, 'gear')
    -- lane marks scroll past at the speed (backwards in R): ~2 px/s per km/h reads as motion without strobing
    local dir = gear == 2 and -1 or 1
    self.lane = (self.lane + num(vm, 'egoSpeed') * 2 * dir * dt) % 18
    local lp = vm:getNumber('laneScroll')
    if lp then lp.value = self.lane end
    if gear == 4 and self.gearPrev ~= 4 and self.gearPrev ~= 0 then self.engT = self.t end
    if gear ~= 0 then self.gearPrev = gear end
    return true
end

local function paint(canvas: Canvas, inst: Artboard<Data.DashViewModel>, k: number)
    local r = canvas:beginFrame({ clearColor = Color.rgba(0, 0, 0, 255) })
    r:save()
    local m = Mat2D.identity()
    m.xx, m.yy = k, k
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
    paint(full, inst, self.k)
    paint(small, inst, self.k / 4)
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
    local speed, limit = num(vm, 'egoSpeed'), num(vm, 'fusedSpeedLimit')
    local nom, fullE, bufE = num(vm, 'nominalEnergyRemaining'), num(vm, 'fullPackEnergy'), num(vm, 'energyBuffer')
    local charge = 0.69
    if fullE - bufE > 0.1 then charge = math.clamp((nom - bufE) / (fullE - bufE), 0, 1) end
    local q = num(vm, 'renderQuality')
    if q < 1 then q = 3 end
    local v = self.buf
    local function put(i: number, x: number) buffer.writef32(v, i * 4, x) end
    put(0, self.w); put(1, self.h); put(2, self.t); put(3, q)
    put(4, speed); put(5, self.steer); put(6, limit > 0 and (speed - limit) or -1); put(7, self.assist)
    put(8, self.red); put(9, self.green); put(10, self.yellow); put(11, self.regen)
    put(12, self.lOn); put(13, self.rOn); put(14, self.t - self.lT); put(15, self.t - self.rT)
    local s = math.min(1, (self.w - 2 * GX) / GW, (self.h - GY - 66) / GH)
    local x0 = self.w / 2 - GW * s / 2
    local y0 = GY + GH * (1 - s) / 2
    local function px(x: number): number return x0 + (x - GX) * s end
    local function py(y: number): number return y0 + (y - GY) * s end
    put(16, self.w / 2); put(17, py(DIAL_Y)); put(18, DIAL_R * s); put(19, HUB_R * s)
    put(20, px(BATT_X)); put(21, py(BATT_Y)); put(22, BATT_R * s); put(23, charge)
    put(24, self.needle); put(25, self.trail); put(26, limit > 0 and gaugeAngle(limit) or 9); put(27, self.t)
    put(28, self.glass); put(29, px(SAT_X)); put(30, py(SAT_Y)); put(31, SAT_R * s)
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
        pipe = nil, group = nil, ubo = nil, buf = buffer.create(128),
        samp = nil, img = ImageSampler('clamp', 'clamp', 'bilinear'),
        w = 0, h = 0, k = 1, t = 0, ready = false, scale = 1, sizedW = 0,
        red = 0, green = 0, yellow = 0, regen = 0, assist = 0, steer = 0,
        lPrev = 0, rPrev = 0, lT = -10, rT = -10, lOn = 0, rOn = 0,
        needle = math.rad(-135), trail = math.rad(-135), gearPrev = 0, engT = -10, lane = 0, introDone = false, sweep = 0, glass = 0,
        init = init, resize = resize, advance = advance, draw = draw,
    }
end
