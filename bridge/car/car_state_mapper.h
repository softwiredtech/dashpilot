#pragma once

#include "car/car_state.h"
#include "car/can_parsers.h"

// Base class for per-vehicle CarState mapping.
// Each vehicle implements update() with programmatic control over how
// CAN signals map to CarState fields — inspired by opendbc's carstate.py pattern.
class CarStateMapper {
public:
    virtual ~CarStateMapper() = default;
    virtual void update(const CANParsers& parsers, CarState& state) = 0;
};
