import math
import random
from typing import List
from data_model import SceneObject, Vehicle, RoadSign, RoadSignType, SpeedLimitSign, TrafficLight, TrafficLightState, StopSign, VehicleClass

def m_per_s(kmh: float) -> float:
    return kmh * 1000/3600

vehicle_classes = [v.value for v in VehicleClass]
def get_random_vehicle():
    return vehicle_classes[random.randint(0, len(vehicle_classes)-1)]

class DynamicSim:
    def __init__(self):
        self.entities: List[SceneObject] = []
        self.view_range = 50 # in meters
        self.max_entity_per_lane = 10
        self.lane_width = 3.5
        self.car_length = 5.5

        # Dynamic scenarios
        self.init_for_two_lanes_and_parking_cars()

    # What changes dynamically?
    # -> Number of cars in a lane
    # -> Type of car (currently only ego type, and "other" cars)
    # -> "Offroad" lane car heading
    # -> Distance between cars
    # -> the number on the speed limit sign
    # -> traffic light toggles between green and yellow
    # -> stop sign
    # -> road signs
    def init_for_two_lanes_and_parking_cars(self):
        self.left_lane_speed = 60 #km/h
        self.ego_speed = 50
    
        # place Ego vehicle first
        self.entities.append(Vehicle(id=0, Dx=0.0, Dy=0.0, VxRel=0.0, heading=0.0, visible=True, vehicle_class=VehicleClass.Car.value))

        entity_counter = 0
        self.initial_dx = self.view_range

        # TODO: make this changeable based on vehicle type
        car_length = 5.5

        dx_left, dx_right = self.initial_dx, self.initial_dx

        for _ in range(self.max_entity_per_lane):
            # Leave some random place between the cars
            dx_left -= car_length + random.uniform(3.0, 9.0)
            dx_right -= car_length + random.uniform(3.0, 9.0)

            # Left lane
            self.entities.append(Vehicle(id=entity_counter+1, Dx=dx_left, Dy=self.lane_width, VxRel=m_per_s(self.left_lane_speed), heading=(math.radians(180)), visible=random.randint(0,1) > 0, vehicle_class=get_random_vehicle()))

            # Don't place a car that overlaps with ego
            if math.fabs(dx_right) > 10.0:
                # Active lane 
                self.entities.append(
                    Vehicle(id=entity_counter+2, Dx=dx_right, Dy=0.0, VxRel=0.0, heading=0.0, visible=random.randint(0,1) > 0, vehicle_class=[VehicleClass.Car.value, VehicleClass.Truck.value][random.randint(0,1)])
                )

            # "Offroad" lane
            self.entities.append(
                Vehicle(id=entity_counter+3, Dx=dx_left, Dy=-self.lane_width - random.uniform(2,6), VxRel=m_per_s(self.ego_speed), heading=math.radians(random.randint(0,360)), visible=random.randint(0,1) > 0, vehicle_class=get_random_vehicle())
            )
            entity_counter += 4


        # Road sign for left lane
        self.entities.append(RoadSign(id=entity_counter, Dx=random.randint(-30,30), Dy=self.lane_width, sign_type=RoadSignType.Straight.value, heading=math.radians(180), visible=True))

        # Road sign for active lane
        self.entities.append(RoadSign(id=entity_counter+1, Dx=random.randint(-30,30), Dy=0.0, sign_type=RoadSignType.Straight.value, heading=0.0, visible=True))

        # Speed limit sign
        self.entities.append(SpeedLimitSign(id=entity_counter+2, Dx=0, Dy=-self.lane_width -2.0, heading=math.radians(180),speed_limit=60, visible=True))

        # Traffic light above active lane
        self.entities.append(TrafficLight(id=entity_counter+3, Dx=-40, Dy=0, heading=math.radians(0),state="Green", visible=True))

        # Stop sign
        self.entities.append(StopSign(id=entity_counter+4, Dx=-20, Dy=-self.lane_width -2.0, heading=math.radians(0), visible=True))

    # TOOO: Create dynami intersecation scenario
    def init_for_intersection(self):
        pass

    def get_sim_frame_at(self, time_step: float) -> List[SceneObject]:
        # Updates the SceneObjects given the current time_step, relative speed of object, and ego speed
        # If the object has a relative speed, use that, otherwise, for static objects (like signs, traffic lights), use ego speed
        # TODO: Should every object have a relative speed automatically filled with the ego speed if static?
        for i in range(len(self.entities)):
            entity = self.entities[i]
            if hasattr(entity, "VxRel"): 
                entity.Dx -= entity.VxRel*time_step
            else: 
                entity.Dx -= m_per_s(self.ego_speed) * time_step

            # Wrap around if out of view range
            if entity.Dx < -self.view_range:
                # TODO: prevent car overlap
                entity.Dx = self.initial_dx
                entity.visible = random.random() > 0.5

                if isinstance(entity, SpeedLimitSign):
                    entity.speed_limit = [40, 60, 70, 90][random.randint(0,3)]

                if isinstance(entity, TrafficLight):
                    entity.state = TrafficLightState.Yellow.value if entity.state == TrafficLightState.Green.value else TrafficLightState.Green.value

        # Get all visible scene objects
        return [x for x in self.entities if hasattr(x, "visible") and x.visible]
