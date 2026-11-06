#pragma once

#include "car_state.h"
#include <cstdint>

// Candidate UI_ambientLightingCtrls layout; pending vehicle validation.
// Latest complete update wins for the dashboard accent, regardless of section.
// Byte 0 and the section mask deliberately remain uninterpreted.
inline void updateTeslaAmbientLighting(CarState& state, int bus, uint32_t address,
                                       const uint8_t* data, size_t len) {
    if (bus != 1 || address != 0x679 || len < 7 || !data) return;
    state.ambientLightingRed = data[1];
    state.ambientLightingGreen = data[2];
    state.ambientLightingBlue = data[3];
    state.ambientLightingBrightness = data[4] & 0x7F;
    state.ambientLightingEnableEffectRaw = data[0];
    state.ambientLightingSectionMaskRaw = ((data[5] >> 3) | (data[6] << 5)) & 0x3F;
    state.ambientLightingAvailable = 1;
}
