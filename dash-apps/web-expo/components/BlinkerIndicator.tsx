import React from "react";
import { Image, StyleSheet } from "react-native";

interface Props {
  side: "left" | "right";
  value: number; // 0=off, 1=blinking, 2=solid
}

const leftImg = require("../assets/left.png");
const rightImg = require("../assets/right.png");

export function BlinkerIndicator({ side, value }: Props) {
  const opacity = value === 2 ? 1 : value === 1 ? 0.3 : 0;

  return (
    <Image
      source={side === "left" ? leftImg : rightImg}
      style={[styles.image, { opacity }]}
      resizeMode="contain"
    />
  );
}

const styles = StyleSheet.create({
  image: {
    width: 24,
    height: 24,
  },
});
