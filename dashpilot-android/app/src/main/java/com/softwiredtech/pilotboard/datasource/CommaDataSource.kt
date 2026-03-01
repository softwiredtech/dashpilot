package com.softwiredtech.pilotboard.datasource

import com.softwiredtech.pilotboard.datamodel.World
import com.softwiredtech.pilotboard.datamodel.WorldMessage
import com.softwiredtech.pilotboard.jni.CommaBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.Float

class CommaDataSource(dbcPath: String) : IDataSource {
    private val _incoming = MutableSharedFlow<WorldMessage>(replay = 1)
    override val incomingMessages: Flow<WorldMessage> = _incoming
    private val bridge = CommaBridge()
    private val ctx = bridge.nativeCreateContext()
    private val signalCount = 11
    private val reusableBuffer = DoubleArray(signalCount)

    private var subSocketHandle: Long = -1

    val signalIndexMap: Map<String, Int> = mapOf(
        "steeringAngle" to 0,
        "accState" to 1,
        "trafficLightColor" to 2,
        "stopLineDist" to 3,
        "blindSpotRearLeft" to 4,
        "blindSpotRearRight" to 5,
        "fusedSpeedLimit" to 6,
        "uiSpeed" to 7,
        "gear" to 8,
        "leftBlinkerBlinking" to 9,
        "rightBlinkerBlinking" to 10
    )

    init {
        bridge.loadDbcFile(dbcPath)
    }

    override fun connect(address: String) {
        subSocketHandle = bridge.nativeCreateSubSocket(ctx, "can", address)
        bridge.nativeStartReceiveLoop(
            subSocketHandle, reusableBuffer
        ) { values ->
            val steering = values[signalIndexMap.getValue("steeringAngle")].toFloat()
            val accState = values[signalIndexMap.getValue("accState")].toFloat()
            val trafficLightColor = values[signalIndexMap.getValue("trafficLightColor")].toFloat()
            val stopLineDist = values[signalIndexMap.getValue("stopLineDist")].toFloat()
            val leftBlindSpot = values[signalIndexMap.getValue("blindSpotRearLeft")].toFloat()
            val rightBlindSpot = values[signalIndexMap.getValue("blindSpotRearRight")].toFloat()
            val fusedSpeedLimit = values[signalIndexMap.getValue("fusedSpeedLimit")].toFloat()
            val uiSpeed = values[signalIndexMap.getValue("uiSpeed")].toFloat()
            val gear = values[signalIndexMap.getValue("gear")].toFloat()
            val leftBlinker = values[signalIndexMap.getValue("leftBlinkerBlinking")].toFloat()
            val rightBlinker = values[signalIndexMap.getValue("rightBlinkerBlinking")].toFloat()

            // If accState means ADAS enabled
            val adasOn = accState > 0f

            // Emit safely from non-suspend callback
            _incoming.tryEmit(
                WorldMessage(
                    frame = 0,
                    world = World(
                        ego_steering_angle = steering,
                        ego_speed = uiSpeed,
                        leftBlinker = leftBlinker,
                        rightBlinker = rightBlinker,
                        gear = gear,
                        adas_on = adasOn,
                        leftBlindSpot = leftBlindSpot,
                        rightBlindSpot = rightBlindSpot,
                        fusedSpeedLimit = fusedSpeedLimit,
                        stopLineDist = stopLineDist,
                        trafficLightColor = trafficLightColor
                    ),
                    timestamp = System.currentTimeMillis(),
                    simulation_time = 0
                )
            )
        }
    }

    override fun disconnect() {
        bridge.nativeStopReceiveLoop()
        bridge.nativeDeleteSubSocket(subSocketHandle)
        bridge.nativeDeleteContext(ctx)
    }
}
