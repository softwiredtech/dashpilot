import React from "react";
import { View, Text, StyleSheet } from "react-native";

interface Props {
  active: boolean;
}

export function AdasStatus({ active }: Props) {
  return (
    <View style={[styles.pill, active ? styles.active : styles.inactive]}>
      <Text style={[styles.label, active ? styles.activeLabel : styles.inactiveLabel]}>
        ADAS {active ? "ON" : "OFF"}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    paddingHorizontal: 14,
    paddingVertical: 5,
    borderRadius: 14,
    borderWidth: 1,
  },
  active: {
    backgroundColor: "rgba(0, 230, 118, 0.15)",
    borderColor: "#00E676",
  },
  inactive: {
    backgroundColor: "rgba(255,255,255,0.05)",
    borderColor: "#444",
  },
  label: {
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 1,
  },
  activeLabel: {
    color: "#00E676",
  },
  inactiveLabel: {
    color: "#666",
  },
});
