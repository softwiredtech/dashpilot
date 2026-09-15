package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scripted 90 second drive that loops forever: pull away, cruise at 50, accelerate onto a
 * 120 road with ADAS engaged, brake for a red light with regen, park and open a door.
 */
class DemoDataSource : IDataSource {

    private val _incoming = MutableSharedFlow<CarState>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingMessages: Flow<CarState> = _incoming

    private var scope: CoroutineScope? = null

    override fun connect(address: String) {
        disconnect()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch {
            val start = System.currentTimeMillis()
            var odometerKm = 48213f
            var lastT = 0f
            while (isActive) {
                val t = ((System.currentTimeMillis() - start) / 1000f) % LOOP_SECONDS
                val speed = speedAt(t)
                val accel = (speed - speedAt(t - 0.5f)) / 0.5f
                val dt = if (t >= lastT) t - lastT else 0f
                lastT = t
                odometerKm += speed / 3600f * dt

                val powerKw = (speed * 0.12f + accel * 4.5f).coerceIn(-60f, 250f)
                val adas = t in 30f..70f
                val red = t in 70f..80f

                _incoming.tryEmit(
                    CarState(
                        egoSteeringAngle = 12f * sin(t * 0.7f),
                        egoSpeed = speed,
                        leftBlinker = blink(t, 12f, 17f),
                        rightBlinker = blink(t, 45f, 50f),
                        gear = if (t < 2f || t >= 85f) 1f else 4f,
                        adasOn = adas,
                        leftBlindSpot = window(t, 14f, 19f),
                        rightBlindSpot = window(t, 47f, 52f),
                        fusedSpeedLimit = if (t < 25f || t >= 65f) 50f else 120f,
                        stopLineDist = if (red) ((80f - t) * 6f).coerceAtLeast(0f) else 0f,
                        trafficLightColor = when {
                            red -> 1f
                            t in 80f..83f -> 2f
                            else -> 0f
                        },
                        laneDepartureWarning = window(t, 58f, 60f),
                        sideCollisionWarning = 0f,
                        anyDoorOpen = window(t, 86f, 89f),
                        buckleStatus = if (t < 2f) 0f else 1f,
                        accSetSpeed = if (adas) 120f else 0f,
                        fullPackEnergy = 78f,
                        nominalEnergyRemaining = 52f - t * 0.02f,
                        energyBuffer = 3f,
                        maxRegenPower = 60f,
                        maxDischargePower = 250f,
                        packVoltage = 380f,
                        packCurrent = powerKw * 1000f / 380f,
                        packTMin = 22f + 0.5f * cos(t * 0.1f),
                        packTMax = 27f + 0.5f * cos(t * 0.1f),
                        odometer = odometerKm,
                        acTemp = 21f,
                        acTempRight = 21.5f,
                        hvacFanLevel = 3f,
                        hvacPowerState = 1f,
                        hvacAcMode = 1f,
                        vin = "5YJ3DEMO000000000"
                    )
                )
                delay(TICK_MS)
            }
        }
    }

    override fun disconnect() {
        scope?.cancel()
        scope = null
    }

    private fun speedAt(t: Float): Float = when {
        t < 0f -> 0f
        t < 10f -> ease(t / 10f) * 50f
        t < 25f -> 50f
        t < 40f -> 50f + ease((t - 25f) / 15f) * 70f
        t < 60f -> 120f
        t < 80f -> 120f * (1f - ease((t - 60f) / 20f))
        else -> 0f
    }

    private fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun window(t: Float, from: Float, to: Float) = if (t in from..to) 1f else 0f

    private fun blink(t: Float, from: Float, to: Float) =
        if (t in from..to && ((t * 2.5f).toInt() % 2 == 0)) 1f else 0f

    private companion object {
        const val LOOP_SECONDS = 90f
        const val TICK_MS = 40L
    }
}
