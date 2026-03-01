from dataclasses import dataclass
from enum import Enum

class TrafficLightState(Enum):
    Red = "Red"
    Yellow = "Yellow"
    Green = "Green"

class RoadSignType(Enum):
    TurnRight = "TurnRight"
    TurnLeft = "TurnLeft"
    Straight = "Straight"
    Stop = "Stop"

class VehicleClass(Enum):
    Car = "Car"
    Truck = "Truck"
    Motorcycle = "Motorcycle"
    Bicycle = "Bicycle"

@dataclass
class SceneObject:
    id: int
    Dx: float       # Forward distance from ego (m)
    Dy: float       # Lateral distance from ego (m)
    heading: float   # Heading (radians)
    visible: bool

@dataclass
class Vehicle(SceneObject):
    VxRel: float    # Relative velocity (m/s)
    vehicle_class: str

@dataclass
class SpeedLimitSign(SceneObject):
    speed_limit: int # km/hn"

@dataclass
class TrafficLight(SceneObject):
    state: str

@dataclass
class StopSign(SceneObject):
    pass

@dataclass
class Pedestrian(SceneObject):
    pass

@dataclass
class RoadSign(SceneObject):
    sign_type: str

@dataclass
class Lane:
    laneWidth: int
    viewRange: int
    lanePolyC0: float
    lanePolyC1: float
    lanePolyC2: float
    lanePolyC3: float
