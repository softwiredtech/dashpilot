import React from "react";
import { View, Image, StyleSheet } from "react-native";

interface Props {
  angle: number;
  adasOn: boolean;
}

// eslint-disable-next-line @typescript-eslint/no-var-requires
const steeringWheelImg = require("../assets/steering-wheel.svg");

export function SteeringWheel({ angle, adasOn }: Props) {
  return (
    <View style={styles.container}>
      <Image
        source={steeringWheelImg}
        style={[
          styles.image,
          { transform: [{ rotate: `${angle}deg` }] },
          { tintColor: adasOn ? "#42A5F5" : "#888" },
        ]}
        resizeMode="contain"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
  },
  image: {
    width: 56,
    height: 56,
  },
});
