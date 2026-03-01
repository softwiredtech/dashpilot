package com.softwiredtech.pilotboard.datamodel

data class WorldMessage(
    val frame: Int,
    val world: World,
    val timestamp: Long,
    val simulation_time: Long
)

data class World(
    val ego_steering_angle: Float = 0f,
    val ego_speed: Float = 0f,
    val leftBlinker: Float = 0f,
    val rightBlinker: Float = 0f,
    val gear: Float = 0f,
    val adas_on: Boolean = false,
    val leftBlindSpot: Float = 0f,
    val rightBlindSpot: Float = 0f,
    val fusedSpeedLimit: Float = 0f,
    val stopLineDist: Float = 0f,
    val trafficLightColor: Float = 0f,
    val lane: Lane = Lane(),
    val objects: List<Any> = emptyList(),
    val traffic_lights: List<Any> = emptyList(),
    val speed_limits: List<Any> = emptyList(),
    val stop_signs: List<Any> = emptyList(),
    val road_signs: List<Any> = emptyList(),
    val pedestrians: List<Any> = emptyList(),
    val events: List<Any> = emptyList()
)

data class Lane(
    val laneWidth: Float = 0f,
    val viewRange: Float = 0f,
    val lanePolyC0: Float = 0f,
    val lanePolyC1: Float = 0f,
    val lanePolyC2: Float = 0f,
    val lanePolyC3: Float = 0f
)
