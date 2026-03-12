import React from "react";
import { View, Text, StyleSheet } from "react-native";

interface Props {
  distance: number; // meters
  maxDistance?: number;
}

export function StopLineBar({ distance, maxDistance = 100 }: Props) {
  if (distance <= 0 || distance >= 127) return null;

  const ratio = Math.min(distance / maxDistance, 1);
  // Color: green when far, yellow when mid, red when close
  const color =
    ratio > 0.5 ? "#00E676" : ratio > 0.2 ? "#FDD835" : "#E53935";

  return (
    <View style={styles.container}>
      <View style={styles.barBg}>
        <View style={[styles.barFill, { width: `${ratio * 100}%`, backgroundColor: color }]} />
      </View>
      <Text style={[styles.label, { color }]}>{Math.round(distance)}m</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    width: 160,
  },
  barBg: {
    flex: 1,
    height: 4,
    backgroundColor: "#333",
    borderRadius: 2,
    overflow: "hidden",
  },
  barFill: {
    height: "100%",
    borderRadius: 2,
  },
  label: {
    fontSize: 12,
    fontWeight: "600",
    fontVariant: ["tabular-nums"],
    minWidth: 36,
    textAlign: "right",
  },
});
