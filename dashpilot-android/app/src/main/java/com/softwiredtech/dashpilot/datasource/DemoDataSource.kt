package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class DemoDataSource : IDataSource {

    private val _incoming = MutableSharedFlow<CarState>(replay = 1)
    override val incomingMessages: Flow<CarState> = _incoming
    private var job: Job? = null

    override fun connect(address: String) {
        job = CoroutineScope(Dispatchers.Default).launch {
            var elapsed = 0.0
            while (isActive) {
                _incoming.emit(frame(elapsed))
                elapsed += STEP_S
                delay((STEP_S * 1000).toLong())
            }
        }
    }

    override fun disconnect() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val STEP_S = 0.1
        const val CYCLE = 90.0
        const val TURN_START = 25.0
        const val TURN_END = 33.0
        const val CRUISE2_END = 50.0
        const val APPROACH_END = 68.0
        const val STOPPED_END = 75.0
        const val GO_END = 78.0
        val LIMITS = floatArrayOf(50f, 90f, 130f)

        fun cruise(t: Double): Float = (70 + 20 * sin(t * 0.02)).toFloat()

        fun frame(t: Double): CarState {
            val lap = (t / CYCLE).toInt()
            val phase = t % CYCLE
            val turningLeft = lap % 2 == 0
            val blink = if (t % 1.0 < 0.5) 2f else 1f
            val wiggle = (sin(t * 0.3) * 15).toFloat()

            var speed: Float
            var steering = 0f
            var left = 0f
            var right = 0f
            var light = 0f
            var stopLine = 0f
            var braking = false

            when {
                phase < TURN_START || (phase >= TURN_END && phase < CRUISE2_END) -> {
                    speed = cruise(t)
                    steering = wiggle
                }
                phase < TURN_END -> {
                    val ease = sin((phase - TURN_START) / (TURN_END - TURN_START) * PI)
                    steering = ((if (turningLeft) -1 else 1) * ease * 28).toFloat()
                    speed = cruise(t) * (1 - 0.5 * ease).toFloat()
                    if (turningLeft) left = blink else right = blink
                }
                phase < APPROACH_END -> {
                    val progress = (phase - CRUISE2_END) / (APPROACH_END - CRUISE2_END)
                    speed = cruise(t - (phase - CRUISE2_END)) * (1 - progress).toFloat()
                    light = 1f
                    stopLine = (45 * (1 - progress)).toFloat()
                    braking = true
                }
                phase < STOPPED_END -> {
                    speed = 0f
                    light = 1f
                }
                phase < GO_END -> {
                    speed = cruise(t) * ((phase - STOPPED_END) / (GO_END - STOPPED_END)).toFloat() * 0.3f
                    light = 2f
                }
                else -> {
                    speed = cruise(t) * (0.3 + 0.7 * (phase - GO_END) / (CYCLE - GO_END)).toFloat()
                }
            }

            val packVoltage = (380 + sin(t * 0.2) * 5).toFloat()
            val packCurrent = if (braking) -speed * 1.2f else speed * 0.4f
            val kw = packVoltage * packCurrent / 1000
            val maxRegen = 60f
            val maxDischarge = 300f

            return CarState(
                egoSteeringAngle = steering,
                egoSpeed = speed,
                leftBlinker = left,
                rightBlinker = right,
                gear = 4f,
                adasOn = true,
                leftBlindSpot = if (t % 23 < 3) 1f else 0f,
                rightBlindSpot = if (t % 29 < 3) 1f else 0f,
                fusedSpeedLimit = LIMITS[lap % LIMITS.size],
                stopLineDist = stopLine,
                trafficLightColor = light,
                buckleStatus = 1f,
                accSetSpeed = cruise(t) + 5,
                fullPackEnergy = 75f,
                nominalEnergyRemaining = max(10.0, 60 - t * 0.01).toFloat(),
                energyBuffer = 1.5f,
                maxRegenPower = maxRegen,
                maxDischargePower = maxDischarge,
                packVoltage = packVoltage,
                packCurrent = packCurrent,
                packTMin = 22f,
                packTMax = 28f,
                odometer = (42000 + t * 0.01).toFloat(),
                acTemp = 22f,
                acTempRight = 21f,
                hvacFanLevel = 11f,
                hvacPowerState = 1f,
                powerLevel = max(-1f, min(1f, kw / if (kw >= 0) maxDischarge else maxRegen)) * 100,
                selfdriveActive = true,
                madsActive = true,
            )
        }
    }
}
