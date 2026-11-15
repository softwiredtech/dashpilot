-- v3 GPU layer. Renders the native Instrument artboard (bound to DashPilot's live view model) into a
-- texture every frame and finishes it in lensfx.wgsl: bloom, a spark at the head of the power fill,
-- redline heat, the start-up ripple and the light at the top of the gauge sweep.
-- It also runs the start-up, like a real car's ignition: the glass wakes and its edge traces out from
-- the centre, the gold dip ignites, the crimson glow blooms, every tell-tale flashes once as a bulb
-- check, then the full gauge sweep (bar to the redline and back, the speed racing 0 to 200 and back,
-- both rails up and down), and the labels settle in. The sweep writes view-model numbers that the
-- native vectors are bound to, so the text stays sharp.
-- If this script cannot run, nothing here draws and the native copy in Dash stays visible.
type Fx = {
    ui: Input<Artboard<Data.DashViewModel>>,
    inst: Artboard<Data.DashViewModel>?,
    vm: ViewModel?,
    full: Canvas?, small: Canvas?, out: GPUCanvas?,
    pipe: GPUPipeline?, group: GPUBindGroup?, ubo: GPUBuffer?, buf: buffer,
    samp: GPUSampler?, img: ImageSampler,
    w: number, h: number, k: number, t: number, ready: boolean, scale: number, sizedW: number,
    pow: number, regen: number, red: number, green: number, yellow: number, lOn: number, rOn: number,
    over: number, introDone: boolean, sweep: number, scaleSeen: number, peak: number, introT: number,
    display: number, shown: boolean, restored: boolean,
}

local MAXTEX = 8192                      -- the largest texture side we ask for
local K = 2280 / 2172                    -- v3.py maps target-2 pixels onto the artboard with this scale
local OY = (1080 - 724 * K) / 2
local GLOW_X, GLOW_Y = 1086 * K, OY + 365 * K

local function num(vm: ViewModel, name: string): number
    local p = vm:getNumber(name)
    if p then return p.value end
    return 0
end

local function setNum(vm: ViewModel, name: string, v: number)
    local p = vm:getNumber(name)
    if p then p.value = v end
end

local function ease(cur: number, target: number, rate: number, dt: number): number
    local nextValue = cur + (target - cur) * (1 - math.exp(-rate * dt))
    return math.abs(nextValue - target) < 0.0001 and target or nextValue    -- a held value truly settles
end

local function smoother(x: number): number                                    -- zero speed and acceleration at both ends
    local z = math.clamp(x, 0, 1)
    return z * z * z * (z * (z * 6 - 15) + 10)
end

local function easeOut(x: number): number
    local c = math.clamp(x, 0, 1)
    return 1 - (1 - c) ^ 3
end


local INTRO = { 'introMain', 'introEdge', 'introDip', 'introSide', 'introBars', 'introScale' }

-- the start-up: one overlapping event, about 1.85 s. The glass and its edge wake, the gold dip ignites from
-- the centre, the crimson field fades up in place, then the bar sweeps to the redline and back while the
-- rails follow. Speed stays the live value throughout. A car that is already moving skips straight to the end.
local S = 1.25                                                    -- stretch over the base 1.48 s choreography
local function intro(self: Fx, vm: ViewModel)
    if self.introDone then return end
    local T = self.t / S
    if math.abs(num(vm, 'egoSpeed')) > 0.5 then
        T = 1.48
        local live = math.clamp(num(vm, 'powerLevel') / 100, -1, 1)
        self.pow, self.regen = math.max(live, 0), math.max(-live, 0)
        self.display = live
        setNum(vm, 'displayPowerPct', live * 100)
    end
    self.introT = T
    setNum(vm, 'introMain', easeOut(T / 0.24))
    setNum(vm, 'introEdge', easeOut((T - 0.04) / 0.42))
    setNum(vm, 'introDip', easeOut((T - 0.12) / 0.30))
    setNum(vm, 'introSide', easeOut((T - 0.20) / 0.32))
    setNum(vm, 'introBars', easeOut((T - 0.28) / 0.24))
    setNum(vm, 'introScale', easeOut((T - 0.20) / 0.30))
    setNum(vm, 'introTell', 0)
    local up = smoother((T - 0.34) / 0.54)
    local down = smoother((T - 0.88) / 0.60)
    self.sweep = up * (1 - down)
    setNum(vm, 'introSweep', self.sweep)
    if T >= 1.48 then
        self.introDone = true
        self.sweep = 0
        for _, n in INTRO do setNum(vm, n, 1) end
        setNum(vm, 'introSweep', 0)
    end
end

function init(self: Fx, context: Context): boolean
    if self.ready then return true end
    local vm = context:viewModel()
    if vm == nil then return true end          -- init runs twice; the view model arrives the second time
    local sh = context:shader('lensfx')
    if sh == nil then
        print('v3_fx: shader missing')
        return false
    end
    self.vm = vm
    self.inst = self.ui:instance(vm)
    self.full, self.small = context:canvas(), context:canvas()
    local out = context:gpuCanvas()
    self.out = out
    self.samp = GPUSampler.new({ min = 'linear', mag = 'linear', wrapU = 'clamp-to-edge', wrapV = 'clamp-to-edge' })
    self.ubo = GPUBuffer.new({ size = 96, usage = 'uniform' })
    self.pipe = GPUPipeline.new({ vertex = sh, fragment = sh, vertexLayout = {}, colorTargets = { { format = out.format } } })
    self.ready = true                          -- the native copy stays fully revealed until a GPU frame exists
    return true
end

-- resize can arrive before init has the view model, so sizing is applied lazily from draw
local function applySize(self: Fx)
    if self.w < 1 or self.h < 1 then return end
    self.k = math.min(math.max(self.scale, 0.01), MAXTEX / self.w)
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
    local dt = math.max(seconds, 0)
    local inst, vm = self.inst, self.vm
    if vm == nil or not self.shown then
        if inst then inst:advance(seconds) end
        return true
    end
    intro(self, vm)
    local power, speed, limit = num(vm, 'powerLevel'), num(vm, 'egoSpeed'), num(vm, 'fusedSpeedLimit')
    -- power: filtered once here; the native fill, its head and the shader all read this one value
    local previous = self.pow - self.regen
    local shown = ease(previous, math.clamp(power / 100, -1, 1), 20, dt)
    self.pow, self.regen = math.max(shown, 0), math.max(-shown, 0)
    local display = shown
    if not self.introDone then
        local release = smoother((self.introT - 0.88) / 0.60)       -- the sweep descends straight into live power
        display = self.sweep + shown * release
    end
    self.display = display
    setNum(vm, 'displayPowerPct', display * 100)
    self.over = ease(self.over, (limit > 0) and math.clamp((speed - limit) / 15, 0, 1) or 0, 12, dt)
    local function fade(cur: number, target: number): number
        return ease(cur, target, target > cur and 12 or 15, dt)
    end
    local light = num(vm, 'trafficLightColor')
    self.red = fade(self.red, light == 1 and 1 or 0)
    self.green = fade(self.green, light == 2 and 1 or 0)
    self.yellow = fade(self.yellow, light == 3 and 1 or 0)
    local lb, rb = num(vm, 'leftBlinker'), num(vm, 'rightBlinker')
    self.lOn = ease(self.lOn, lb == 2 and 1 or 0, 60, dt)
    self.rOn = ease(self.rOn, rb == 2 and 1 or 0, 60, dt)
    if inst then inst:advance(seconds) end   -- after the writes, so this frame shows them
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
    local first = not self.shown
    if first then
        -- the first GPU frame: start the ignition from dark (unless the car is already moving or the
        -- GPU took too long, then adopt the finished live state with no replay)
        if self.t < 0.10 and math.abs(num(vm, 'egoSpeed')) <= 0.5 then
            for _, n in INTRO do setNum(vm, n, 0) end
        else
            self.introDone = true
            local live = math.clamp(num(vm, 'powerLevel') / 100, -1, 1)
            self.pow, self.regen = math.max(live, 0), math.max(-live, 0)
            self.display = live
            setNum(vm, 'displayPowerPct', live * 100)
            for _, n in INTRO do setNum(vm, n, 1) end
        end
        setNum(vm, 'introSweep', 0)
        setNum(vm, 'introTell', 0)
        inst:advance(0)
        self.t = 0
    end
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
    local fillv = math.max(self.display, 0)
    local v = self.buf
    local function put(i: number, x: number) buffer.writef32(v, i * 4, x) end
    put(0, self.w); put(1, self.h); put(2, self.t); put(3, q)
    put(4, fillv); put(5, math.max(-self.display, 0)); put(6, smoother((fillv - 0.80) / 0.12)); put(7, self.over)
    put(8, self.red); put(9, self.green); put(10, self.yellow); put(11, self.introDone and 99 or self.introT)
    put(12, self.lOn); put(13, self.rOn); put(14, self.sweep); put(15, self.peak)
    put(16, K); put(17, OY); put(18, GLOW_X); put(19, GLOW_Y)
    put(20, ox); put(21, oy); put(22, s); put(23, 0)
    ubo:write(v)
    local pass = out:beginRenderPass({ color = { { loadOp = 'clear', storeOp = 'store', clearColor = { 0, 0, 0, 1 } } } })
    pass:setPipeline(pipe); pass:setBindGroup(0, g); pass:draw(3); pass:finish()
    if first then
        self.shown = true                      -- the prepared frame is out: now hide the native copy
        local fx = vm:getBoolean('fxActive')
        if fx then fx.value = true end
    end
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
        pipe = nil, group = nil, ubo = nil, buf = buffer.create(96),
        samp = nil, img = ImageSampler('clamp', 'clamp', 'bilinear'),
        w = 0, h = 0, k = 1, t = 0, ready = false, scale = 1, sizedW = 0,
        pow = 0, regen = 0, red = 0, green = 0, yellow = 0, lOn = 0, rOn = 0,
        over = 0, introDone = false, sweep = 0, scaleSeen = 0, peak = 0, introT = -1,
        display = 0, shown = false, restored = false,
        init = init, resize = resize, advance = advance, draw = draw,
    }
end
