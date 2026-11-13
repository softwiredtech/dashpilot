#include "common/vehicle_decoder.h"
#include <cassert>
#include <fstream>
#include <iterator>

static void check(const char* dbcPath, const char* profile) {
    std::ifstream dbc(dbcPath);
    assert(dbc);
    const std::string content{std::istreambuf_iterator<char>(dbc), {}};
    VehicleDecoder decoder({content, content}, {0, 1}, profile);
    const auto& state = decoder.state();
    const uint8_t payload[] = {0xA5, 0x12, 0x34, 0x56, 0xE4, 0xAD, 0xFF, 0x5A};
    const auto feed = [&](int bus, uint32_t id, const uint8_t* data, size_t length) {
        decoder.updateFrame(bus, id, data, length);
        decoder.updateMapper();
    };
    feed(0, 0x679, payload, 8);
    feed(1, 0x678, payload, 8);
    feed(1, 0x679, payload, 6);
    assert(!state.ambientLightingAvailable);
    feed(1, 0x679, payload, 7);
    assert(state.ambientLightingAvailable && state.ambientLightingColor == 0x123456);
    assert(state.ambientLightingBrightness == 100);
    const uint8_t black[8] = {};
    feed(1, 0x679, black, 6);
    assert(state.ambientLightingColor == 0x123456 && state.ambientLightingBrightness == 100);
    feed(1, 0x679, black, 8);
    assert(state.ambientLightingAvailable && state.ambientLightingColor == 0 && state.ambientLightingBrightness == 0);
}

int main(int argc, char** argv) {
    if (argc != 3) return 1;
    check(argv[1], "dashkit");
    check(argv[2], "tesla_juniper");
}
