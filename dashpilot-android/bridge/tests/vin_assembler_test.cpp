// Host-run unit tests for VinAssembler (repo/bridge/car/vin_assembler.h).
// Built by CMakeLists.txt when not configuring for Android:
//   cmake -B build-host -S . && cmake --build build-host && ctest --test-dir build-host

#include "car/vin_assembler.h"

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

using State = VinAssembler::State;

// Builds an 8-byte frame: byte 0 = mux, text written at offset, rest zero.
std::string vinFrame(uint8_t mux, const char* text, size_t offset) {
    std::string frame(VinAssembler::kFrameLen, '\0');
    frame[0] = static_cast<char>(mux);
    std::memcpy(frame.data() + offset, text, std::strlen(text));
    return frame;
}

std::string frameA() { return vinFrame(0x10, std::string(kVin, 0, 3).c_str(), 5); }
std::string frameB() { return vinFrame(0x11, std::string(kVin, 3, 7).c_str(), 1); }
std::string frameC() { return vinFrame(0x12, std::string(kVin, 10, 7).c_str(), 1); }

State feed(VinAssembler& a, int bus, uint32_t address, const std::string& data) {
    return a.onFrame(bus, address, reinterpret_cast<const uint8_t*>(data.data()),
                     data.size());
}

void assembles_out_of_order_frames_with_duplicates() {
    VinAssembler a;
    CHECK(feed(a, 1, 0x405, frameC()) == State::Waiting);
    CHECK(feed(a, 1, 0x405, frameA()) == State::Waiting);
    CHECK(feed(a, 1, 0x405, frameA()) == State::Waiting); // duplicate is tolerated
    CHECK(feed(a, 1, 0x405, frameB()) == State::Ready);
    CHECK(a.vin() == kVin);
}

void completion_persists_until_reset() {
    VinAssembler a;
    feed(a, 1, 0x405, frameB());
    feed(a, 1, 0x405, frameC());
    CHECK(feed(a, 1, 0x405, frameA()) == State::Ready);
    // Subsequent unrelated frames keep reporting the completed VIN.
    CHECK(feed(a, 1, 0x405, vinFrame(0x13, "junk", 1)) == State::Ready);
    CHECK(a.vin() == kVin);

    a.reset();
    CHECK(!a.ready());
    CHECK(a.vin().empty());
    CHECK(feed(a, 1, 0x405, frameC()) == State::Waiting);
    CHECK(a.vin().empty());
}

void rejects_illegal_vin_characters() {
    const char* illegal[] = {"I", "O", "Q", "a", "\xC3\x84"};
    for (const char* c : illegal) {
        VinAssembler a;
        std::string frame = frameC();
        frame.replace(1, std::strlen(c), c);
        feed(a, 1, 0x405, frameA());
        feed(a, 1, 0x405, frameB());
        CHECK(feed(a, 1, 0x405, frame) == State::Invalid);
        CHECK(!a.ready());

        // Invalid isn't latched: a valid retransmission of the segment completes.
        CHECK(feed(a, 1, 0x405, frameC()) == State::Ready);
        CHECK(a.vin() == kVin);
    }
}

void rejects_zero_padding_violation_on_mux_a() {
    VinAssembler a;
    std::string frame = frameA();
    frame[2] = 'X'; // bytes 1-4 must be zero for mux A
    CHECK(feed(a, 1, 0x405, frame) == State::Invalid);
    CHECK(!a.ready());
}

void ignores_frames_outside_the_vin_contract() {
    VinAssembler a;
    // Wrong bus and wrong CAN id.
    CHECK(feed(a, 0, 0x405, frameA()) == State::Waiting);
    CHECK(feed(a, 1, 0x404, frameA()) == State::Waiting);
    // Wrong DLC.
    std::string short_frame = frameA();
    short_frame.resize(6);
    CHECK(feed(a, 1, 0x405, short_frame) == State::Waiting);
    // Unknown mux.
    CHECK(feed(a, 1, 0x405, vinFrame(0x20, "junk", 1)) == State::Waiting);
    CHECK(!a.ready());
}

void assembles_in_order_from_single_frames() {
    VinAssembler a;
    CHECK(feed(a, 1, 0x405, frameA()) == State::Waiting);
    CHECK(feed(a, 1, 0x405, frameB()) == State::Waiting);
    CHECK(feed(a, 1, 0x405, frameC()) == State::Ready);
    CHECK(a.vin() == kVin);
}

} // namespace

int main() {
    assembles_out_of_order_frames_with_duplicates();
    completion_persists_until_reset();
    rejects_illegal_vin_characters();
    rejects_zero_padding_violation_on_mux_a();
    ignores_frames_outside_the_vin_contract();
    assembles_in_order_from_single_frames();

    if (failures > 0) {
        std::printf("%d check(s) failed\n", failures);
        return 1;
    }
    std::printf("all vin_assembler tests passed\n");
    return 0;
}
