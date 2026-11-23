#pragma once

#include <cstdint>
#include <utility>
#include <vector>

#include "car/can_parsers.h"
#include "car/car_state.h"
#include "car/car_state_mapper.h"

// CAN subscriptions for a DashKit.
//
// The DashKit forwards a frame to the phone only if its (bus, id) is on the
// list the app writes to the CAN filter characteristic (CADA0002). The list is
// not maintained by hand: it is whatever the vehicle's CarStateMapper reads,
// found by running a fresh mapper once against the parsers and recording every
// message it asks for. A new signal in a mapper is therefore subscribed
// automatically on both platforms.
//
// The probe runs against whatever the parsers hold, usually nothing, so it only
// finds a message whose read actually executes then. A read that depends on a
// signal of the same message is found (it is asked for either way). A read that
// only happens once ANOTHER message has some value would be missed: keep reads
// unconditional, or condition them on the message they belong to.
//
// DashKit firmware that receives the whole bus delivers any listed id; older
// firmware delivers the listed ids its hardware filters accept, which is what it
// sent before.

// (bus, address) of every message `mapper` reads, sorted by bus then address.
// Pass a newly created mapper: one that has already seen frames may skip
// messages it is done with (the VIN, for instance).
inline std::vector<std::pair<int, uint32_t>> probeCanSubscriptions(const CANParsers& parsers,
                                                                   CarStateMapper& mapper) {
    CarState scratch;
    // Ends the recording even if the mapper throws.
    struct Recording {
        const CANParsers& p;
        explicit Recording(const CANParsers& parsers) : p(parsers) { p.beginRecording(); }
        ~Recording() { p.endRecording(); }
    } recording(parsers);
    mapper.update(parsers, scratch);
    return parsers.endRecording();
}

// The DashKit CAN filter wire format: per bus [bus : 1][count : 1][id : LE32]...
// Limited to what one filter write may carry: 512 bytes, the largest attribute
// value BLE allows (about 125 ids). Firmware before whole-bus receive takes 256
// bytes (about 60 ids) and rejects a longer write, which leaves its default set.
// An empty result means "do not write a filter" (the DashKit then forwards its
// default set), which is also what a list that does not fit comes to. The
// callers log it.
inline constexpr size_t kCanFilterMaxBytes = 512;

inline std::vector<uint8_t> encodeCanFilter(const std::vector<std::pair<int, uint32_t>>& subs) {
    std::vector<uint8_t> out;
    size_t i = 0;
    while (i < subs.size()) {
        int bus = subs[i].first;
        size_t n = 0;
        while (i + n < subs.size() && subs[i + n].first == bus && n < 255) n++;
        if (bus < 0 || bus > 255) return {};
        out.push_back(static_cast<uint8_t>(bus));
        out.push_back(static_cast<uint8_t>(n));
        for (size_t k = 0; k < n; k++) {
            uint32_t id = subs[i + k].second;
            for (int b = 0; b < 4; b++) out.push_back(static_cast<uint8_t>(id >> (8 * b)));
        }
        i += n;
    }
    if (out.size() > kCanFilterMaxBytes) return {};
    return out;
}
