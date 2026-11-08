package com.softwiredtech.dashpilot.js

import android.webkit.JavascriptInterface
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.datamodel.dash.DisplaySettings

class CarStateBridge(
    private val loadAppData: () -> String = { "" },
    private val storeAppData: (String) -> Unit = {},
    // Raised while a dash-app's own editor is open, so the host can suspend
    // gestures of its own (the dashboard carousel) for the duration.
    private val onEditingChange: (Boolean) -> Unit = {},
) {

    @Volatile private var egoSteeringAngle: Float = 0f
    @Volatile private var egoSpeed: Float = 0f
    @Volatile private var leftBlinker: Float = 0f
    @Volatile private var rightBlinker: Float = 0f
    @Volatile private var gear: Float = 0f
    @Volatile private var adasOn: Boolean = false
    @Volatile private var leftBlindSpot: Float = 0f
    @Volatile private var rightBlindSpot: Float = 0f
    @Volatile private var fusedSpeedLimit: Float = 0f
    @Volatile private var stopLineDist: Float = 0f
    @Volatile private var trafficLightColor: Float = 0f
    @Volatile private var buckleStatus: Float = 0f
    @Volatile private var anyDoorOpen: Float = 0f
    @Volatile private var doorOpenFL: Float = 0f
    @Volatile private var doorOpenFR: Float = 0f
    @Volatile private var doorOpenRL: Float = 0f
    @Volatile private var doorOpenRR: Float = 0f
    @Volatile private var trunkOpen: Float = 0f
    @Volatile private var trunkPosition: Float = 0f
    @Volatile private var frunkOpen: Float = 0f
    @Volatile private var accSetSpeed: Float = 0f
    @Volatile private var fullPackEnergy: Float = 0f
    @Volatile private var nominalEnergyRemaining: Float = 0f
    @Volatile private var energyBuffer: Float = 0f
    @Volatile private var maxRegenPower: Float = 0f
    @Volatile private var maxDischargePower: Float = 0f
    @Volatile private var packVoltage: Float = 0f
    @Volatile private var packCurrent: Float = 0f
    @Volatile private var powerLevel: Float = 0f
    @Volatile private var packTMin: Float = 0f
    @Volatile private var packTMax: Float = 0f
    @Volatile private var odometer: Float = 0f
    @Volatile private var experimentalMode: Boolean = false
    @Volatile private var madsActive: Boolean = false
    @Volatile private var phoneBattery: Int = -1
    @Volatile private var currentTime: Long = 0L
    @Volatile private var showPhoneBattery: Boolean = true
    @Volatile private var showCarBattery: Boolean = true
    @Volatile private var showOdometer: Boolean = true
    @Volatile private var useImperial: Boolean = false
    @Volatile private var darkMode: Boolean = false
    @Volatile private var alwaysOnBlindSpotMonitor: Boolean = true
    @Volatile private var renderQuality: Int = 3
    @Volatile private var darkModeBackgroundGray: Int = 0
    @Volatile private var changingLane: Boolean = false
    @Volatile private var tpmsPressureFL: Float = -1f
    @Volatile private var tpmsPressureFR: Float = -1f
    @Volatile private var tpmsPressureRL: Float = -1f
    @Volatile private var tpmsPressureRR: Float = -1f
    @Volatile private var tpmsTempFL: Float = -100f
    @Volatile private var tpmsTempFR: Float = -100f
    @Volatile private var tpmsTempRL: Float = -100f
    @Volatile private var tpmsTempRR: Float = -100f
    @Volatile private var tpmsBattFL: Float = -1f
    @Volatile private var tpmsBattFR: Float = -1f
    @Volatile private var tpmsBattRL: Float = -1f
    @Volatile private var tpmsBattRR: Float = -1f
    @Volatile private var speedCameraDistance: Int = -1
    @Volatile private var dataSourceType: String = ""

    fun update(state: CarState) {
        egoSteeringAngle = state.egoSteeringAngle
        egoSpeed = state.egoSpeed
        leftBlinker = state.leftBlinker
        rightBlinker = state.rightBlinker
        gear = state.gear
        adasOn = state.adasOn
        leftBlindSpot = state.leftBlindSpot
        rightBlindSpot = state.rightBlindSpot
        fusedSpeedLimit = state.fusedSpeedLimit
        stopLineDist = state.stopLineDist
        trafficLightColor = state.trafficLightColor
        buckleStatus = state.buckleStatus
        anyDoorOpen = state.anyDoorOpen
        doorOpenFL = state.doorOpenFL
        doorOpenFR = state.doorOpenFR
        doorOpenRL = state.doorOpenRL
        doorOpenRR = state.doorOpenRR
        trunkOpen = state.trunkOpen
        trunkPosition = state.trunkPosition
        frunkOpen = state.frunkOpen
        accSetSpeed = state.accSetSpeed
        fullPackEnergy = state.fullPackEnergy
        nominalEnergyRemaining = state.nominalEnergyRemaining
        energyBuffer = state.energyBuffer
        maxRegenPower = state.maxRegenPower
        maxDischargePower = state.maxDischargePower
        packVoltage = state.packVoltage
        packCurrent = state.packCurrent
        powerLevel = state.powerLevel
        packTMin = state.packTMin
        packTMax = state.packTMax
        odometer = state.odometer
        experimentalMode = state.experimentalMode
        madsActive = state.madsActive
        changingLane = state.changingLane
        tpmsPressureFL = state.tpmsPressureFL
        tpmsPressureFR = state.tpmsPressureFR
        tpmsPressureRL = state.tpmsPressureRL
        tpmsPressureRR = state.tpmsPressureRR
        tpmsTempFL = state.tpmsTempFL
        tpmsTempFR = state.tpmsTempFR
        tpmsTempRL = state.tpmsTempRL
        tpmsTempRR = state.tpmsTempRR
        tpmsBattFL = state.tpmsBattFL
        tpmsBattFR = state.tpmsBattFR
        tpmsBattRL = state.tpmsBattRL
        tpmsBattRR = state.tpmsBattRR
    }

    @JavascriptInterface fun getEgoSteeringAngle(): Float = egoSteeringAngle
    @JavascriptInterface fun getEgoSpeed(): Float = egoSpeed
    @JavascriptInterface fun getLeftBlinker(): Float = leftBlinker
    @JavascriptInterface fun getRightBlinker(): Float = rightBlinker
    @JavascriptInterface fun getGear(): Float = gear
    @JavascriptInterface fun isAdasOn(): Boolean = adasOn
    @JavascriptInterface fun getLeftBlindSpot(): Float = leftBlindSpot
    @JavascriptInterface fun getRightBlindSpot(): Float = rightBlindSpot
    @JavascriptInterface fun getFusedSpeedLimit(): Float = fusedSpeedLimit
    @JavascriptInterface fun getStopLineDist(): Float = stopLineDist
    @JavascriptInterface fun getTrafficLightColor(): Float = trafficLightColor
    @JavascriptInterface fun getBuckleStatus(): Float = buckleStatus
    @JavascriptInterface fun getAnyDoorOpen(): Float = anyDoorOpen
    @JavascriptInterface fun getDoorOpenFL(): Float = doorOpenFL
    @JavascriptInterface fun getDoorOpenFR(): Float = doorOpenFR
    @JavascriptInterface fun getDoorOpenRL(): Float = doorOpenRL
    @JavascriptInterface fun getDoorOpenRR(): Float = doorOpenRR
    @JavascriptInterface fun getTrunkOpen(): Float = trunkOpen
    @JavascriptInterface fun getTrunkPosition(): Float = trunkPosition
    @JavascriptInterface fun getFrunkOpen(): Float = frunkOpen
    @JavascriptInterface fun getAccSetSpeed(): Float = accSetSpeed
    @JavascriptInterface fun getFullPackEnergy(): Float = fullPackEnergy
    @JavascriptInterface fun getNominalEnergyRemaining(): Float = nominalEnergyRemaining
    @JavascriptInterface fun getEnergyBuffer(): Float = energyBuffer
    @JavascriptInterface fun getMaxRegenPower(): Float = maxRegenPower
    @JavascriptInterface fun getMaxDischargePower(): Float = maxDischargePower
    @JavascriptInterface fun getPackVoltage(): Float = packVoltage
    @JavascriptInterface fun getPackCurrent(): Float = packCurrent
    @JavascriptInterface fun getPowerLevel(): Float = powerLevel
    @JavascriptInterface fun getPackTMin(): Float = packTMin
    @JavascriptInterface fun getPackTMax(): Float = packTMax
    @JavascriptInterface fun getOdometer(): Float = odometer
    @JavascriptInterface fun isExperimentalMode(): Boolean = experimentalMode
    @JavascriptInterface fun isMadsActive(): Boolean = madsActive
    @JavascriptInterface fun getPhoneBattery(): Int = phoneBattery
    @JavascriptInterface fun getCurrentTime(): Long = currentTime
    @JavascriptInterface fun getShowPhoneBattery(): Boolean = showPhoneBattery
    @JavascriptInterface fun getShowCarBattery(): Boolean = showCarBattery
    @JavascriptInterface fun getShowOdometer(): Boolean = showOdometer
    @JavascriptInterface fun isImperial(): Boolean = useImperial
    @JavascriptInterface fun isDarkMode(): Boolean = darkMode
    @JavascriptInterface fun isAlwaysOnBlindSpotMonitor(): Boolean = alwaysOnBlindSpotMonitor
    @JavascriptInterface fun getRenderQuality(): Int = renderQuality
    @JavascriptInterface fun getDarkModeBackgroundGray(): Int = darkModeBackgroundGray
    @JavascriptInterface fun isChangingLane(): Boolean = changingLane
    @JavascriptInterface fun getTpmsPressureFL(): Float = tpmsPressureFL
    @JavascriptInterface fun getTpmsPressureFR(): Float = tpmsPressureFR
    @JavascriptInterface fun getTpmsPressureRL(): Float = tpmsPressureRL
    @JavascriptInterface fun getTpmsPressureRR(): Float = tpmsPressureRR
    @JavascriptInterface fun getTpmsTempFL(): Float = tpmsTempFL
    @JavascriptInterface fun getTpmsTempFR(): Float = tpmsTempFR
    @JavascriptInterface fun getTpmsTempRL(): Float = tpmsTempRL
    @JavascriptInterface fun getTpmsTempRR(): Float = tpmsTempRR
    @JavascriptInterface fun getTpmsBattFL(): Float = tpmsBattFL
    @JavascriptInterface fun getTpmsBattFR(): Float = tpmsBattFR
    @JavascriptInterface fun getTpmsBattRL(): Float = tpmsBattRL
    @JavascriptInterface fun getTpmsBattRR(): Float = tpmsBattRR
    @JavascriptInterface fun getSpeedCameraDistance(): Int = speedCameraDistance

    fun updateSpeedCameraDistance(distanceMeters: Int) {
        speedCameraDistance = distanceMeters
    }

    fun updateDataSourceType(type: String) {
        dataSourceType = type
    }

    @JavascriptInterface fun getDataSourceType(): String = dataSourceType

    @JavascriptInterface fun getAppData(): String = loadAppData()

    @JavascriptInterface fun setAppData(json: String) = storeAppData(json)

    @JavascriptInterface fun setEditing(editing: Boolean) = onEditingChange(editing)

    fun updatePhoneBattery(level: Int) {
        phoneBattery = level
    }

    fun updateCurrentTime(time: Long) {
        currentTime = time
    }

    fun updateDisplaySettings(settings: DisplaySettings) {
        showPhoneBattery = settings.showPhoneBattery
        showCarBattery = settings.showCarBattery
        showOdometer = settings.showOdometer
        useImperial = settings.useImperial
        darkMode = settings.darkMode
        alwaysOnBlindSpotMonitor = settings.alwaysOnBlindSpotMonitor
        renderQuality = settings.renderQuality
        darkModeBackgroundGray = settings.darkModeBackgroundGray
    }
}
