// Host-run unit tests for the closure (door / trunk / liftgate) path of the
// Tesla DashKit CarState mapper (repo/bridge/car/cars/tesla.h). Built by
// CMakeLists.txt when not configuring for Android:
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

// The latch / liftgate signals as defined in bus_1_tesla_vehicle.dbc.
const char* kVehicleDbc =
    "BO_ 258 VCLEFT_doorStatus: 8 VEH\n"
    " SG_ VCLEFT_frontLatchStatus: 0|4@1+ (1,0) [0|0] \"\" X\n"
    " SG_ VCLEFT_rearLatchStatus: 4|4@1+ (1,0) [0|0] \"\" X\n"
    "\n"
    "BO_ 259 VCRIGHT_doorStatus: 8 VEH\n"
    " SG_ VCRIGHT_frontLatchStatus: 0|4@1+ (1,0) [0|0] \"\" X\n"
    " SG_ VCRIGHT_rearLatchStatus: 4|4@1+ (1,0) [0|0] \"\" X\n"
    " SG_ VCRIGHT_trunkLatchStatus: 56|4@1+ (1,0) [0|0] \"\" X\n"
    "\n"
    "BO_ 322 VCLEFT_liftgateStatus: 8 VEH\n"
    " SG_ VCLEFT_liftgateStatusIndex M : 0|2@1+ (1,0) [0|2] \"\" X\n"
    " SG_ VCLEFT_liftgatePosition m1 : 21|7@1- (1,46) [-5|95] \"deg\" X\n"
    " SG_ VCLEFT_liftgateState m0 : 3|4@1+ (1,0) [0|12] \"\" X\n"
    "\n"
    "BO_ 737 VCFRONT_status: 8 VEH\n"
    " SG_ VCFRONT_statusIndex M : 0|3@1+ (1,0) [0|6] \"\" X\n"
    " SG_ VCFRONT_frunkLatchStatus m0 : 3|4@1+ (1,0) [0|8] \"\" X\n";

enum Latch { SNA = 0, OPENED = 1, CLOSED = 2, CLOSING = 3, OPENING = 4, AJAR = 5, TIMEOUT = 6, DEFAULT = 7, FAULT = 8 };

// Writes `value` into little-endian (Intel) bits [start, start+len) of an 8-byte frame.
void setBits(std::string& frame, int start, int len, uint64_t value) {
    for (int i = 0; i < len; i++) {
        int bit = start + i;
        uint8_t& byte = reinterpret_cast<uint8_t&>(frame[bit / 8]);
        if ((value >> i) & 1) byte |= static_cast<uint8_t>(1u << (bit % 8));
        else byte &= static_cast<uint8_t>(~(1u << (bit % 8)));
    }
}

std::string leftDoors(int front, int rear) {
    std::string f(8, '\0');
    setBits(f, 0, 4, front);
    setBits(f, 4, 4, rear);
    return f;
}

std::string rightDoors(int front, int rear, int trunk) {
    std::string f(8, '\0');
    setBits(f, 0, 4, front);
    setBits(f, 4, 4, rear);
    setBits(f, 56, 4, trunk);
    return f;
}

std::string liftgateMux0(int state) {
    std::string f(8, '\0');
    setBits(f, 0, 2, 0);
    setBits(f, 3, 4, state);
    return f;
}

// Raw 7-bit two's complement; physical angle = raw + 46.
std::string liftgateMux1(int raw) {
    std::string f(8, '\0');
    setBits(f, 0, 2, 1);
    setBits(f, 21, 7, static_cast<uint64_t>(raw) & 0x7F);
    return f;
}

std::string frontStatus(int mux, int frunk) {
    std::string f(8, '\0');
    setBits(f, 0, 3, mux);
    setBits(f, 3, 4, frunk);
    return f;
}

struct Harness {
    CANParsers cp;
    TeslaDashKitMapper mapper;
    CarState cs;

    Harness() {
        cp.addBus(1, kVehicleDbc);
        cp.buildCache();
    }

    void feed(uint32_t address, const std::string& data, int bus = 1) {
        cp.updateFrame(bus, address, reinterpret_cast<const uint8_t*>(data.data()), data.size());
        mapper.update(cp, cs);
    }
};

void doors_default_closed_before_any_frame() {
    Harness h;
    h.feed(0x405, std::string(8, '\0'));  // unrelated frame
    CHECK(h.cs.doorOpenFL == 0 && h.cs.doorOpenFR == 0 && h.cs.doorOpenRL == 0 && h.cs.doorOpenRR == 0);
    CHECK(h.cs.trunkOpen == 0 && h.cs.trunkPosition == 0 && h.cs.frunkOpen == 0);
}

void left_frame_maps_front_and_rear_left() {
    Harness h;
    h.feed(0x102, leftDoors(OPENED, CLOSED));
    CHECK(h.cs.doorOpenFL == 1 && h.cs.doorOpenRL == 0);
    CHECK(h.cs.doorOpenFR == 0 && h.cs.doorOpenRR == 0);
    h.feed(0x102, leftDoors(CLOSED, AJAR));
    CHECK(h.cs.doorOpenFL == 0 && h.cs.doorOpenRL == 1);
}

void right_frame_maps_front_rear_right_and_trunk() {
    Harness h;
    h.feed(0x103, rightDoors(CLOSED, OPENING, CLOSED));
    CHECK(h.cs.doorOpenFR == 0 && h.cs.doorOpenRR == 1 && h.cs.trunkOpen == 0);
    h.feed(0x103, rightDoors(CLOSING, CLOSED, OPENED));
    CHECK(h.cs.doorOpenFR == 1 && h.cs.doorOpenRR == 0 && h.cs.trunkOpen == 1);
    CHECK(h.cs.doorOpenFL == 0 && h.cs.doorOpenRL == 0);
}

void unknown_and_fault_latch_states_read_closed() {
    const int notOpen[] = {SNA, CLOSED, TIMEOUT, DEFAULT, FAULT, 9, 15};
    for (int s : notOpen) {
        Harness h;
        h.feed(0x102, leftDoors(s, s));
        h.feed(0x103, rightDoors(s, s, s));
        CHECK(h.cs.doorOpenFL == 0 && h.cs.doorOpenRL == 0);
        CHECK(h.cs.doorOpenFR == 0 && h.cs.doorOpenRR == 0 && h.cs.trunkOpen == 0);
    }
    const int open[] = {OPENED, CLOSING, OPENING, AJAR};
    for (int s : open) {
        Harness h;
        h.feed(0x102, leftDoors(s, CLOSED));
        CHECK(h.cs.doorOpenFL == 1);
    }
}

void liftgate_angle_only_from_mux_1_and_clamped_at_zero() {
    Harness h;
    h.feed(0x142, liftgateMux1(14));   // 14 + 46 = 60 deg
    CHECK(h.cs.trunkPosition == 60);
    h.feed(0x142, liftgateMux0(5));    // PLG_STATE_CLOSED on mux 0 keeps the last angle
    CHECK(h.cs.trunkPosition == 60);
    h.feed(0x142, liftgateMux1(-2));   // -2 + 46 = 44 deg (negative raw)
    CHECK(h.cs.trunkPosition == 44);
    h.feed(0x142, liftgateMux1(-48));  // -48 + 46 = -2 deg: closed reads slightly negative
    CHECK(h.cs.trunkPosition == 0);
}

void frunk_latch_only_from_vcfront_mux_0() {
    Harness h;
    h.feed(0x2E1, frontStatus(0, OPENED));
    CHECK(h.cs.frunkOpen == 1);
    h.feed(0x2E1, frontStatus(1, CLOSED));  // another mux: those bits mean something else, keep the last value
    CHECK(h.cs.frunkOpen == 1);
    h.feed(0x2E1, frontStatus(0, CLOSED));
    CHECK(h.cs.frunkOpen == 0);
}

void closures_round_trip_through_the_double_array() {
    Harness h;
    h.feed(0x102, leftDoors(OPENED, CLOSED));
    h.feed(0x103, rightDoors(CLOSED, AJAR, OPENED));
    h.feed(0x142, liftgateMux1(14));
    h.feed(0x2E1, frontStatus(0, AJAR));

    double out[CarState::FIELD_COUNT];
    h.cs.toArray(out);
    CHECK(CarState::DOOR_OFFSET == 47 && CarState::FIELD_COUNT == 54);
    CHECK(out[CarState::DOOR_OFFSET + 0] == 1);   // FL
    CHECK(out[CarState::DOOR_OFFSET + 1] == 0);   // FR
    CHECK(out[CarState::DOOR_OFFSET + 2] == 0);   // RL
    CHECK(out[CarState::DOOR_OFFSET + 3] == 1);   // RR
    CHECK(out[CarState::DOOR_OFFSET + 4] == 1);   // trunk open
    CHECK(out[CarState::DOOR_OFFSET + 5] == 60);  // trunk angle
    CHECK(out[CarState::DOOR_OFFSET + 6] == 1);   // frunk open
}

}  // namespace

int main() {
    doors_default_closed_before_any_frame();
    left_frame_maps_front_and_rear_left();
    right_frame_maps_front_rear_right_and_trunk();
    unknown_and_fault_latch_states_read_closed();
    liftgate_angle_only_from_mux_1_and_clamped_at_zero();
    frunk_latch_only_from_vcfront_mux_0();
    closures_round_trip_through_the_double_array();

    if (failures) {
        std::printf("%d check(s) failed\n", failures);
        return 1;
    }
    std::printf("tesla_closure_mapper_test: all checks passed\n");
    return 0;
}
