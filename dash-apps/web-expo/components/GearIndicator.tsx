import React from "react";
import { View, Text, StyleSheet } from "react-native";

const GEARS = [
  { value: 1, label: "P" },
  { value: 2, label: "R" },
  { value: 3, label: "N" },
  { value: 4, label: "D" },
];

interface Props {
  gear: number;
}

export function GearIndicator({ gear }: Props) {
  return (
    <View style={styles.container}>
      {GEARS.map((g) => {
        const active = Math.round(gear) === g.value;
        return (
          <View key={g.value} style={[styles.pill, active && styles.activePill]}>
            <Text style={[styles.label, active && styles.activeLabel]}>{g.label}</Text>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    gap: 4,
    alignItems: "center",
  },
  pill: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
  },
  activePill: {
    backgroundColor: "rgba(255,255,255,0.15)",
  },
  label: {
    fontSize: 16,
    fontWeight: "600",
    color: "#555",
  },
  activeLabel: {
    color: "#FFFFFF",
  },
});
