package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

// Synthetic drive so the dashboards run with nothing connected. Port of the
// iOS DemoDataSource: cruise -> gentle turn -> cruise -> red light stop ->
// green -> back to cruise, 90 s per lap.
class DemoDataSource : IDataSource {

    private val _incoming = MutableSharedFlow<CarState>(replay = 1)
    override val incomingMessages: Flow<CarState> = _incoming

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun connect(address: String) {
        job?.cancel()
        job = scope.launch {
            var elapsed = 0.0
            val dt = 0.04
            while (isActive) {
                _incoming.tryEmit(frame(elapsed))
                elapsed += dt
                delay((dt * 1000).toLong())
            }
        }
    }

    override fun disconnect() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val CYCLE = 90.0
        const val TURN_START = 25.0
        const val TURN_END = 33.0
        const val CRUISE2_END = 50.0
        const val APPROACH_END = 68.0
        const val STOPPED_END = 75.0
        const val GO_END = 78.0

        fun cruiseSpeed(t: Double): Float = (70 + 20 * sin(t * 0.02)).toFloat()

        fun frame(t: Double): CarState {
            val cycleIndex = (t / CYCLE).toInt()
            val phase = t % CYCLE
            val turningLeft = cycleIndex % 2 == 0
            val limits = floatArrayOf(50f, 90f, 130f)

            var speed: Float
            var steering: Float
            var light = 0f
            var stopDist = 0f
            var blinkLeft = 0f
            var blinkRight = 0f

            when {
                phase < TURN_START -> {
                    speed = cruiseSpeed(t)
                    steering = (sin(t * 0.3) * 3).toFloat()
                }
                phase < TURN_END -> {
                    val progress = (phase - TURN_START) / (TURN_END - TURN_START)
                    val ease = sin(progress * PI).toFloat()
                    val direction = if (turningLeft) -1f else 1f
                    steering = direction * ease * 28f
                    speed = cruiseSpeed(t) * (1f - 0.5f * ease)
                    val blink = if (phase % 1.0 < 0.5) 1f else 0f
                    blinkLeft = if (turningLeft) blink else 0f
                    blinkRight = if (turningLeft) 0f else blink
                }
                phase < CRUISE2_END -> {
                    speed = cruiseSpeed(t)
                    steering = (sin(t * 0.3) * 3).toFloat()
                }
                phase < APPROACH_END -> {
                    val progress = (phase - CRUISE2_END) / (APPROACH_END - CRUISE2_END)
                    val startSpeed = cruiseSpeed(t - (phase - CRUISE2_END))
                    speed = startSpeed * (1 - progress).toFloat()
                    steering = 0f
                    light = 1f
                    stopDist = (45 * (1 - progress)).toFloat()
                }
                phase < STOPPED_END -> {
                    speed = 0f
                    steering = 0f
                    light = 1f
                }
                phase < GO_END -> {
                    val progress = (phase - STOPPED_END) / (GO_END - STOPPED_END)
                    speed = cruiseSpeed(t) * progress.toFloat() * 0.3f
                    steering = 0f
                    light = 2f
                }
                else -> {
                    val progress = (phase - GO_END) / (CYCLE - GO_END)
                    speed = cruiseSpeed(t) * (0.3f + 0.7f * progress.toFloat())
                    steering = 0f
                }
            }

            return CarState(
                egoSpeed = speed,
                egoSteeringAngle = steering,
                leftBlinker = blinkLeft,
                rightBlinker = blinkRight,
                gear = 4f,
                adasOn = true,
                leftBlindSpot = if (t % 23 < 3) 1f else 0f,
                rightBlindSpot = if (t % 29 < 3) 1f else 0f,
                fusedSpeedLimit = limits[cycleIndex % limits.size],
                stopLineDist = stopDist,
                trafficLightColor = light,
                buckleStatus = 1f,
                accSetSpeed = cruiseSpeed(t) + 5f,
                fullPackEnergy = 75f,
                nominalEnergyRemaining = max(10.0, 60 - t * 0.01).toFloat(),
                energyBuffer = 1.5f,
                maxRegenPower = 60f,
                maxDischargePower = 300f,
                packVoltage = (380 + sin(t * 0.2) * 5).toFloat(),
                packCurrent = speed * 0.4f,
                packTMin = 22f,
                packTMax = 28f,
                odometer = (42000 + t * 0.01).toFloat(),
                acTemp = 22f,
                selfdriveActive = true,
                madsActive = true,
                vin = "5YJ3E7EB1MF123456",
            )
        }
    }
}
