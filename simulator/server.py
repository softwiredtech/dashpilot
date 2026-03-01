import argparse
from dataclasses import dataclass, asdict, field
from typing import List
import asyncio
import websockets
import json
import functools
import time
from data_model import SpeedLimitSign, RoadSign, StopSign, TrafficLight, Vehicle, Lane, Pedestrian
from dynamic_sim import DynamicSim
from replay import ReplayTool
import socket

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip

@dataclass
class ADASWorld:
    lane: Lane = field(default_factory=lambda: Lane(4, 50, 0, 0, 0, 0))
    objects: List[Vehicle] = field(default_factory=list)
    traffic_lights: List[TrafficLight] = field(default_factory=list)
    speed_limits: List[SpeedLimitSign] = field(default_factory=list)
    stop_signs: List[StopSign] = field(default_factory=list)
    road_signs: List[RoadSign] = field(default_factory=list)
    pedestrians: List[Pedestrian] = field(default_factory=list)
    events: List = field(default_factory=list)
    ego_steering_angle: float = 0
    ego_speed: float = 5.0
    adas_on: bool = True

# Simulation parameters
DURATION = 60.0  # seconds
DT = 0.016    # time step (60 FPS)
NUM_FRAMES = int(DURATION / DT)
dynamic_sim = DynamicSim()

import math
STEERING_AMPLITUDE = math.radians(10)  # max steering angle (±10°)
STEERING_FREQUENCY = 0.5               # Hz (cycles per second)

# WebSocket server to emit vehicle data
async def websocket_server(websocket, websocket_dt: float, path="/", route_path=None):
    try:
        last_send_time = 0
        websocket_frame = 0
        world: ADASWorld = ADASWorld()
        replayTool = ReplayTool(route_path) if route_path != None else None

        if replayTool:
            while (msg:= replayTool.get_next_message()):
                try:
                    await websocket.send(msg)
                except websockets.exceptions.ConnectionClosed:
                    print(f"WebSocket connection closed for client {websocket.remote_address}")
                    break
                
                await asyncio.sleep(DT)
        else:
            for physics_frame in range(NUM_FRAMES):
                simulation_time = physics_frame * DT
                current_time = time.time()
                vehicles = dynamic_sim.get_sim_frame_at(DT)

                # TODO: Decision about ADASWorld model
                # Should we separate out objects here, or just have them in one array, and the app resolve the concrete objects?
                world.objects = [x for x in vehicles if isinstance(x, Vehicle)]
                world.road_signs = [x for x in vehicles if isinstance(x, RoadSign)]
                world.speed_limits = [x for x in vehicles if isinstance(x, SpeedLimitSign)]
                world.traffic_lights = [x for x in vehicles if isinstance(x, TrafficLight)]
                world.stop_signs = [x for x in vehicles if isinstance(x, StopSign)]
                world.ego_steering_angle = (
                    STEERING_AMPLITUDE
                    * math.sin(2.0 * math.pi * STEERING_FREQUENCY * simulation_time)
                )

                try:
                    if simulation_time - last_send_time >= websocket_dt:
                        message_data = {
                            "frame": websocket_frame,
                            "world": asdict(world),
                            "timestamp": current_time,
                            "simulation_time": simulation_time
                        }
                        await websocket.send(json.dumps(message_data))
                        last_send_time = simulation_time
                        websocket_frame += 1

                except websockets.exceptions.ConnectionClosed:
                    print(f"WebSocket connection closed for client {websocket.remote_address}")
                    break
                
                await asyncio.sleep(DT)

    finally:
        await websocket.close()

# Run the WebSocket server
async def main(scenario: int, port: int, websocket_fps: float, route_path: str):
    websocket_dt = 1.0 / websocket_fps

    handler = functools.partial(websocket_server, websocket_dt=websocket_dt, route_path=route_path)
    server = await websockets.serve(handler, "0.0.0.0", port)
    local_ip = get_local_ip()
    print(f"Running websocket server on ws://{local_ip}:{port}")
    await server.wait_closed()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenario", type=int, default=2, help="Scenario number to run (default: 0)")
    parser.add_argument("--fps", type=float, default=30.0, help="WebSocket FPS (default: 30.0)")
    parser.add_argument("--route", type=str, default=None, help="Path to route.jsonl")
    parser.add_argument("--port", type=int, default=8080, help="Port number for websocket connection")
    args = parser.parse_args()
    print(f"Selected scenario: {args.scenario}")
    asyncio.run(main(args.scenario, args.port, args.fps, args.route))
