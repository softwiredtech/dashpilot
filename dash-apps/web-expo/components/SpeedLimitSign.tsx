import React from "react";
import { View, Text, StyleSheet } from "react-native";
import Svg, { Circle } from "react-native-svg";

interface Props {
  limit: number;
}

export function SpeedLimitSign({ limit }: Props) {
  if (limit <= 0 || limit === 31) return null;

  return (
    <View style={styles.container}>
      <Svg width={64} height={64} viewBox="0 0 64 64">
        <Circle cx={32} cy={32} r={29} stroke="#E53935" strokeWidth={5} fill="#1A1A1A" />
      </Svg>
      <View style={styles.textOverlay}>
        <Text style={styles.value}>{Math.round(limit)}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: 64,
    height: 64,
    alignItems: "center",
    justifyContent: "center",
  },
  textOverlay: {
    position: "absolute",
    alignItems: "center",
    justifyContent: "center",
  },
  value: {
    fontSize: 22,
    fontWeight: "700",
    color: "#FFFFFF",
  },
});
