import React, { useEffect, useRef, useState, useCallback } from "react";
import { View, Text, StyleSheet, Platform } from "react-native";
import Svg, { Path } from "react-native-svg";

interface Props {
  speed: number;
  maxSpeed?: number;
}

const START_ANGLE = 150;
const SWEEP_ANGLE = 240;
const CX = 120;
const CY = 120;
const R = 100;
const LERP_FACTOR = 0.12;

function toXY(deg: number) {
  return {
    x: CX + R * Math.cos((deg * Math.PI) / 180),
    y: CY + R * Math.sin((deg * Math.PI) / 180),
  };
}

// Pre-compute background arc and ticks (static)
const bgStart = toXY(START_ANGLE);
const bgEnd = toXY(START_ANGLE + SWEEP_ANGLE);
const BG_PATH = `M ${bgStart.x} ${bgStart.y} A ${R} ${R} 0 1 1 ${bgEnd.x} ${bgEnd.y}`;

const TICKS: string[] = [];
const NUM_TICKS = 9;
for (let i = 0; i <= NUM_TICKS; i++) {
  const angle = START_ANGLE + (SWEEP_ANGLE * i) / NUM_TICKS;
  const rad = (angle * Math.PI) / 180;
  const inner = { x: CX + 82 * Math.cos(rad), y: CY + 82 * Math.sin(rad) };
  const outer = { x: CX + 92 * Math.cos(rad), y: CY + 92 * Math.sin(rad) };
  TICKS.push(`M ${inner.x} ${inner.y} L ${outer.x} ${outer.y}`);
}

function buildArcPath(ratio: number): string {
  if (ratio <= 0.001) return "";
  const endAngle = START_ANGLE + SWEEP_ANGLE * ratio;
  const valStart = toXY(START_ANGLE);
  const valEnd = toXY(endAngle);
  const largeArc = endAngle - START_ANGLE > 180 ? 1 : 0;
  return `M ${valStart.x} ${valStart.y} A ${R} ${R} 0 ${largeArc} 1 ${valEnd.x} ${valEnd.y}`;
}

export function SpeedGauge({ speed, maxSpeed = 180 }: Props) {
  const [displaySpeed, setDisplaySpeed] = useState(speed);
  const currentRef = useRef(speed);
  const targetRef = useRef(speed);
  const rafRef = useRef<number>(0);

  const animate = useCallback(() => {
    const current = currentRef.current;
    const target = targetRef.current;
    const diff = target - current;

    if (Math.abs(diff) < 0.3) {
      currentRef.current = target;
      setDisplaySpeed(target);
      return;
    }

    const next = current + diff * LERP_FACTOR;
    currentRef.current = next;
    setDisplaySpeed(next);
    rafRef.current = requestAnimationFrame(animate);
  }, []);

  useEffect(() => {
    targetRef.current = speed;
    cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(rafRef.current);
  }, [speed, animate]);

  const rounded = Math.round(displaySpeed);
  const ratio = Math.min(Math.max(displaySpeed / maxSpeed, 0), 1);
  const valPath = buildArcPath(ratio);

  return (
    <View style={styles.container}>
      <Svg width={240} height={240} viewBox="0 0 240 240">
        {TICKS.map((d, i) => (
          <Path key={i} d={d} stroke="#555" strokeWidth={2} />
        ))}
        <Path d={BG_PATH} stroke="#333" strokeWidth={8} fill="none" strokeLinecap="round" />
        {valPath ? (
          <Path d={valPath} stroke="#00E676" strokeWidth={8} fill="none" strokeLinecap="round" />
        ) : null}
      </Svg>
      <View style={styles.textOverlay}>
        <Text style={styles.speedValue}>{rounded}</Text>
        <Text style={styles.speedUnit}>km/h</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: 240,
    height: 240,
    alignItems: "center",
    justifyContent: "center",
  },
  textOverlay: {
    position: "absolute",
    alignItems: "center",
    justifyContent: "center",
    width: 160,
  },
  speedValue: {
    fontSize: 64,
    fontWeight: "200",
    color: "#FFFFFF",
    textAlign: "center",
    fontVariant: ["tabular-nums"],
  },
  speedUnit: {
    fontSize: 14,
    color: "#888",
    marginTop: -8,
  },
});
