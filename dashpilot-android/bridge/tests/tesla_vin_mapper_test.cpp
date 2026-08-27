// Host-run unit tests for the VIN path of the Tesla CarState mappers
// (repo/bridge/car/cars/tesla.h). Built by CMakeLists.txt when not configuring
// for Android:
//   cmake -B build-host -S . && cmake --build build-host && ctest --test-dir build-host

#include "car/cars/tesla.h"

#include <cstdio>
#include <cstring>
#include <string>

static int failures = 0;

#define CHECK(cond)                                                          \
    do {                                                                     \
        if (!(cond)) {                                                       \
            std::printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);      \
            failures++;                                                      \
        }                                                                    \
    } while (0)

namespace {

const char* kVin = "5YJ3E7EB1MF123456";

// VIN_info as defined in bus_1_tesla_vehicle.dbc.
const char* kVehicleDbc =
    "BO_ 1029 VIN_info: 8 VEH\n"
    " SG_ VIN_infoIndex M : 0|8@1+ (1,0) [0|255] \"\" X\n"
    " SG_ VIN_B405 m17 : 8|56@1+ (1,0) [0|7.2057594038E+016] \"\" X\n"
    " SG_ VIN_C405 m18 : 8|56@1+ (1,0) [0|7.2057594038E+016] \"\" X\n"
    " SG_ VIN_A405 m16 : 8|56@1+ (1,0) [0|7.2057594038E+016] \"\" X\n";

// Builds an 8-byte VIN_info frame: byte 0 = mux, text written at offset, rest zero.
std::string vinFrame(uint8_t mux, const std::string& text, size_t offset) {
    std::string frame(8, '\0');
    frame[0] = static_cast<char>(mux);
    std::memcpy(&frame[offset], text.data(), text.size());
    return frame;
}

std::string frameA() { return vinFrame(0x10, std::string(kVin, 0, 3), 5); }
std::string frameB() { return vinFrame(0x11, std::string(kVin, 3, 7), 1); }
std::string frameC() { return vinFrame(0x12, std::string(kVin, 10, 7), 1); }

struct Harness {
    CANParsers cp;
    TeslaDashKitMapper mapper;
    CarState cs;

    Harness() {
        cp.addBus(1, kVehicleDbc);
        cp.buildCache();
    }

    void feed(const std::string& data, int bus = 1, uint32_t address = 0x405) {
        cp.updateFrame(bus, address, reinterpret_cast<const uint8_t*>(data.data()),
                       data.size());
        mapper.update(cp, cs);
    }

    std::string vin() const { return cs.vin; }
};

void assembles_out_of_order_frames_with_duplicates() {
    Harness h;
    h.feed(frameC());
    CHECK(h.vin().empty());
    h.feed(frameA());
    h.feed(frameA()); // duplicate is tolerated
    CHECK(h.vin().empty());
    h.feed(frameB());
    CHECK(h.vin() == kVin);
}

void completion_persists_across_further_frames() {
    Harness h;
    h.feed(frameB());
    h.feed(frameC());
    h.feed(frameA());
    CHECK(h.vin() == kVin);
    h.feed(vinFrame(0x11, "JUNKJUN", 1));
    CHECK(h.vin() == kVin);
}

void rejects_illegal_vin_characters_until_retransmission() {
    const char* illegal[] = {"I", "O", "Q", "a", "\x00", "\xC4"};
    for (const char* c : illegal) {
        Harness h;
        std::string bad = frameC();
        bad[1] = c[0];
        h.feed(frameA());
        h.feed(frameB());
        h.feed(bad);
        CHECK(h.vin().empty());

        // A valid retransmission of the segment completes the VIN.
        h.feed(frameC());
        CHECK(h.vin() == kVin);
    }
}

void rejects_zero_padding_violation_on_mux_a() {
    Harness h;
    std::string frame = frameA();
    frame[2] = 'X'; // bytes 1-4 must be zero for mux A
    h.feed(frame);
    h.feed(frameB());
    h.feed(frameC());
    CHECK(h.vin().empty());
    h.feed(frameA());
    CHECK(h.vin() == kVin);
}

void ignores_frames_outside_the_vin_contract() {
    Harness h;
    h.feed(frameA(), 0, 0x405);   // wrong bus
    h.feed(frameB(), 1, 0x404);   // wrong CAN id
    h.feed(vinFrame(0x20, "JUNKJUN", 1)); // unknown mux
    std::string short_frame = frameC();
    short_frame.resize(6); // truncated DLC
    h.feed(short_frame);
    CHECK(h.vin().empty());
}

void vin_round_trips_through_the_double_array() {
    Harness h;
    h.feed(frameA());
    h.feed(frameB());
    h.feed(frameC());

    double out[CarState::FIELD_COUNT];
    h.cs.toArray(out);
    char decoded[CarState::VIN_DOUBLE_COUNT * sizeof(double) + 1] = {};
    std::memcpy(decoded, out + 31, CarState::VIN_DOUBLE_COUNT * sizeof(double));
    CHECK(std::string(decoded) == kVin);
}

void empty_vin_encodes_as_zero_doubles() {
    CarState cs;
    double out[CarState::FIELD_COUNT];
    cs.toArray(out);
    for (size_t i = 31; i < CarState::FIELD_COUNT; i++) {
        CHECK(out[i] == 0.0);
    }
}

} // namespace

int main() {
    assembles_out_of_order_frames_with_duplicates();
    completion_persists_across_further_frames();
    rejects_illegal_vin_characters_until_retransmission();
    rejects_zero_padding_violation_on_mux_a();
    ignores_frames_outside_the_vin_contract();
    vin_round_trips_through_the_double_array();
    empty_vin_encodes_as_zero_doubles();

    if (failures > 0) {
        std::printf("%d check(s) failed\n", failures);
        return 1;
    }
    std::printf("all tesla_vin_mapper tests passed\n");
    return 0;
}
