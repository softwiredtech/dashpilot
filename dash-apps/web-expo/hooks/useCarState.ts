import { useState, useEffect, useCallback, useRef } from "react";

export interface CarState {
  egoSteeringAngle: number;
  egoSpeed: number;
  leftBlinker: number;
  rightBlinker: number;
  gear: number;
  adasOn: boolean;
  leftBlindSpot: number;
  rightBlindSpot: number;
  fusedSpeedLimit: number;
  stopLineDist: number;
  trafficLightColor: number;
}

declare global {
  interface Window {
    NativeCarState?: {
      getEgoSteeringAngle(): number;
      getEgoSpeed(): number;
      getLeftBlinker(): number;
      getRightBlinker(): number;
      getGear(): number;
      isAdasOn(): boolean;
      getLeftBlindSpot(): number;
      getRightBlindSpot(): number;
      getFusedSpeedLimit(): number;
      getStopLineDist(): number;
      getTrafficLightColor(): number;
    };
    onCarStateUpdate?: () => void;
    receiveMessage?: (car: CarState) => void;
  }
}

const DEFAULT_STATE: CarState = {
  egoSteeringAngle: 0,
  egoSpeed: 0,
  leftBlinker: 0,
  rightBlinker: 0,
  gear: 4,
  adasOn: false,
  leftBlindSpot: 0,
  rightBlindSpot: 0,
  fusedSpeedLimit: 0,
  stopLineDist: 0,
  trafficLightColor: 0,
};

export function useCarState(): CarState {
  const [state, setState] = useState<CarState>(DEFAULT_STATE);
  const stateRef = useRef(state);

  const readNative = useCallback(() => {
    const n = window.NativeCarState;
    if (!n) return;
    const next: CarState = {
      egoSteeringAngle: n.getEgoSteeringAngle(),
      egoSpeed: n.getEgoSpeed(),
      leftBlinker: n.getLeftBlinker(),
      rightBlinker: n.getRightBlinker(),
      gear: n.getGear(),
      adasOn: n.isAdasOn(),
      leftBlindSpot: n.getLeftBlindSpot(),
      rightBlindSpot: n.getRightBlindSpot(),
      fusedSpeedLimit: n.getFusedSpeedLimit(),
      stopLineDist: n.getStopLineDist(),
      trafficLightColor: n.getTrafficLightColor(),
    };
    stateRef.current = next;
    setState(next);
  }, []);

  useEffect(() => {
    // Register the bridge callbacks
    window.onCarStateUpdate = readNative;
    window.receiveMessage = (car) => {
      stateRef.current = car;
      setState(car);
    };

    // If running outside WebView, simulate demo data
    if (typeof window.NativeCarState === "undefined") {
      let t = 0;
      const interval = setInterval(() => {
        t += 0.05;
        const demo: CarState = {
          egoSteeringAngle: Math.sin(t * 0.5) * 45,
          egoSpeed: 60 + Math.sin(t * 0.3) * 20,
          leftBlinker: Math.sin(t * 3) > 0.5 ? 2 : 0,
          rightBlinker: 0,
          gear: 4,
          adasOn: true,
          leftBlindSpot: Math.sin(t * 0.2) > 0.8 ? 1 : 0,
          rightBlindSpot: 0,
          fusedSpeedLimit: 80,
          stopLineDist: Math.max(0, 50 - ((t * 5) % 60)),
          trafficLightColor: Math.floor(t / 3) % 3 === 0 ? 1 : Math.floor(t / 3) % 3 === 1 ? 2 : 3,
        };
        stateRef.current = demo;
        setState(demo);
      }, 50);
      return () => {
        clearInterval(interval);
        window.onCarStateUpdate = undefined;
        window.receiveMessage = undefined;
      };
    }

    return () => {
      window.onCarStateUpdate = undefined;
      window.receiveMessage = undefined;
    };
  }, [readNative]);

  return state;
}
