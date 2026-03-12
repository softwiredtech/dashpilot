import React, { useEffect, useRef } from "react";
import { Animated, StyleSheet } from "react-native";

interface Props {
  side: "left" | "right";
  active: boolean;
}

export function BlindSpotWarning({ side, active }: Props) {
  const opacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: active ? 1 : 0,
      duration: active ? 150 : 600,
      useNativeDriver: true,
    }).start();
  }, [active, opacity]);

  return (
    <Animated.View
      style={[
        styles.strip,
        side === "left" ? styles.left : styles.right,
        { opacity },
      ]}
    />
  );
}

const styles = StyleSheet.create({
  strip: {
    position: "absolute",
    top: 0,
    bottom: 0,
    width: 6,
    backgroundColor: "#E53935",
    shadowColor: "#E53935",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.8,
    shadowRadius: 12,
  },
  left: {
    left: 0,
    borderTopRightRadius: 3,
    borderBottomRightRadius: 3,
  },
  right: {
    right: 0,
    borderTopLeftRadius: 3,
    borderBottomLeftRadius: 3,
  },
});
