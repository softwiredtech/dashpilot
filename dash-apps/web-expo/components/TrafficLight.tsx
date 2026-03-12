import React from "react";
import { View, StyleSheet } from "react-native";

interface Props {
  color: number; // 0=none, 1=red, 2=green, 3=yellow
}

const LIGHT_COLORS: Record<number, { red: string; yellow: string; green: string }> = {
  0: { red: "#331111", yellow: "#332B11", green: "#113311" },
  1: { red: "#E53935", yellow: "#332B11", green: "#113311" },
  2: { red: "#331111", yellow: "#332B11", green: "#00E676" },
  3: { red: "#331111", yellow: "#FDD835", green: "#113311" },
};

export function TrafficLight({ color }: Props) {
  if (color === 0) return null;
  const colors = LIGHT_COLORS[color] ?? LIGHT_COLORS[0];

  return (
    <View style={styles.housing}>
      <View style={[styles.light, { backgroundColor: colors.red }]} />
      <View style={[styles.light, { backgroundColor: colors.yellow }]} />
      <View style={[styles.light, { backgroundColor: colors.green }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  housing: {
    backgroundColor: "#222",
    borderRadius: 10,
    padding: 4,
    gap: 3,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#333",
  },
  light: {
    width: 14,
    height: 14,
    borderRadius: 7,
  },
});
