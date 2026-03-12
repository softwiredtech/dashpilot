import { StatusBar } from "expo-status-bar";
import { StyleSheet, View } from "react-native";
import { useCarState } from "./hooks/useCarState";
import { SpeedGauge } from "./components/SpeedGauge";
import { GearIndicator } from "./components/GearIndicator";
import { SteeringWheel } from "./components/SteeringWheel";
import { SpeedLimitSign } from "./components/SpeedLimitSign";
import { BlinkerIndicator } from "./components/BlinkerIndicator";
import { BlindSpotWarning } from "./components/BlindSpotWarning";
import { TrafficLight } from "./components/TrafficLight";
import { StopLineBar } from "./components/StopLineBar";

export default function App() {
  const car = useCarState();

  return (
    <View style={styles.root}>
      <StatusBar style="light" hidden />

      {/* Blind spot edge warnings */}
      <BlindSpotWarning side="left" active={car.leftBlindSpot > 0} />
      <BlindSpotWarning side="right" active={car.rightBlindSpot > 0} />

      {/* Top row: blinkers */}
      <View style={styles.topRow}>
        <BlinkerIndicator side="left" value={car.leftBlinker} />
        <BlinkerIndicator side="right" value={car.rightBlinker} />
      </View>

      {/* Main content */}
      <View style={styles.mainRow}>
        {/* Left column: steering wheel + gear */}
        <View style={styles.sideColumn}>
          <SteeringWheel angle={car.egoSteeringAngle} adasOn={car.adasOn} />
          <GearIndicator gear={car.gear} />
        </View>

        {/* Center: speed gauge */}
        <View style={styles.centerColumn}>
          <SpeedGauge speed={car.egoSpeed} />
        </View>

        {/* Right column: speed limit, traffic light, stop line */}
        <View style={styles.sideColumn}>
          <SpeedLimitSign limit={car.fusedSpeedLimit} />
          <TrafficLight color={car.trafficLightColor} />
          <StopLineBar distance={car.stopLineDist} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: "#0D0D0D",
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  topRow: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 40,
    paddingTop: 8,
  },
  mainRow: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
  },
  sideColumn: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
  },
  centerColumn: {
    alignItems: "center",
    justifyContent: "center",
  },
});
