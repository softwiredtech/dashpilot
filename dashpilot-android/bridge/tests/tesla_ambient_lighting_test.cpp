#include "common/vehicle_decoder.h"
#include <cassert>
#include <cstring>

int main() {
    const uint8_t candidate[] = {0xA5, 0x12, 0x34, 0x56, 0xE4, 0xAD, 0xFF, 0x5A};
    for (const auto* type : {"dashkit", "tesla_juniper"}) {
        VehicleDecoder decoder({}, {}, type);
        auto& state = decoder.state();
        assert(!state.ambientLightingAvailable);
        for (size_t len = 0; len < 7; ++len) {
            decoder.updateFrame(1, 0x679, candidate, len);
            decoder.updateMapper();
            assert(!state.ambientLightingAvailable);
        }
        for (int bus : {0, 2, 5}) {
            decoder.updateFrame(bus, 0x679, candidate, 8);
            assert(!state.ambientLightingAvailable);
        }
        decoder.updateFrame(1, 0x678, candidate, 8);
        decoder.updateFrame(1, 0x80000679, candidate, 8);
        assert(!state.ambientLightingAvailable);
        decoder.updateFrame(1, 0x679, candidate, 8);
        decoder.updateMapper();
        assert(state.ambientLightingAvailable);
        assert(state.ambientLightingRed == 18 && state.ambientLightingGreen == 52);
        assert(state.ambientLightingBlue == 86 && state.ambientLightingBrightness == 100);
        assert(state.ambientLightingEnableEffectRaw == 165 && state.ambientLightingSectionMaskRaw == 53);
        std::strcpy(state.vin, "5YJ3E7EB1MF123456");
        state.tpmsPressureFL = 2.5;
        state.tpmsTempRR = 31;
        double values[CarState::FIELD_COUNT] = {};
        state.toArray(values);
        assert(values[39] == 2.5 && values[46] == 31);
        assert(values[47] == 1 && values[48] == 18 && values[49] == 52 && values[50] == 86);
        assert(values[51] == 100 && values[52] == 165 && values[53] == 53);
        assert(std::memcmp(values + CarState::VIN_OFFSET, state.vin, 17) == 0);
        // Truncation must preserve the entire previous update, including mask.
        decoder.updateFrame(1, 0x679, candidate, 6);
        decoder.updateMapper();
        assert(state.ambientLightingSectionMaskRaw == 53 && state.ambientLightingRed == 18);
        // Latest section-specific update supplies the global accent; no labels.
        const uint8_t white[] = {0, 255, 255, 255, 0, 8, 0};
        decoder.updateFrame(1, 0x679, white, 7);
        decoder.updateMapper();
        assert(state.ambientLightingRed == 255 && state.ambientLightingBrightness == 0);
        assert(state.ambientLightingSectionMaskRaw == 1 && state.ambientLightingEnableEffectRaw == 0);
        const uint8_t black[] = {255, 0, 0, 0, 255, 0, 0};
        decoder.updateFrame(1, 0x679, black, 7);
        assert(state.ambientLightingAvailable && state.ambientLightingBlue == 0);
        assert(state.ambientLightingBrightness == 127); // retain masked raw value, don't clamp
        for (int mask = 0; mask < 64; ++mask) {
            uint8_t data[] = {0, 1, 2, 3, 0, static_cast<uint8_t>(mask << 3),
                             static_cast<uint8_t>(mask >> 5)};
            decoder.updateFrame(1, 0x679, data, 7);
            assert(state.ambientLightingSectionMaskRaw == mask);
        }
    }
    VehicleDecoder other({}, {}, "tesla_party");
    other.updateFrame(1, 0x679, candidate, 8);
    assert(!other.state().ambientLightingAvailable);
}
