#pragma once

#include <array>
#include <cstdint>
#include <string>

// Assembles the vehicle VIN from muxed CAN frames carried by the DashKit BLE
// CAN stream.
//
// Frame contract (from bus_1_tesla_vehicle.dbc, message VIN_info / 0x405):
//   DLC 8, byte 0 = mux selector:
//     mux 0x10 -> VIN chars 1-3   in bytes 5-7  (bytes 1-4 must be zero)
//     mux 0x11 -> VIN chars 4-10  in bytes 1-7
//     mux 0x12 -> VIN chars 11-17 in bytes 1-7
//
// Segments may arrive in any order and may duplicate; a frame that doesn't
// match this contract is ignored. Segments are validated as raw ASCII and as
// legal VIN characters (digits, A-Z minus I/O/Q). The completed VIN stays
// readable until reset().
//
// Raw bytes are read directly on purpose: VIN_B405/VIN_C405 are 56-bit signals,
// wider than double's 53-bit mantissa, so decoding them through the normal
// signal path would silently corrupt the top byte(s).
class VinAssembler {
public:
    static constexpr int kVinBus = 1;
    static constexpr uint32_t kVinCanId = 0x405;
    static constexpr size_t kFrameLen = 8;
    static constexpr size_t kVinLen = 17;
    static constexpr uint8_t kMuxA = 0x10;
    static constexpr uint8_t kMuxB = 0x11;
    static constexpr uint8_t kMuxC = 0x12;

    enum class State { Waiting, Invalid, Ready };

    // Feeds one raw frame; returns the assembly state after processing it.
    // Called from VehicleDecoder::updateFrame(), so no extra decode pass is
    // needed. Not synchronized, same threading model as CANParsers.
    State onFrame(int bus, uint32_t address, const uint8_t* data, size_t len) {
        if (!completed_.empty()) return State::Ready;
        if (bus != kVinBus || address != kVinCanId || len != kFrameLen) {
            return State::Waiting;
        }

        int index;
        size_t offset;
        switch (data[0]) {
            case kMuxA: index = 0; offset = 5; break;
            case kMuxB: index = 1; offset = 1; break;
            case kMuxC: index = 2; offset = 1; break;
            default: return State::Waiting;
        }
        if (index == 0) {
            // Mux A carries only 3 real chars; bytes 1-4 must be zero.
            for (size_t i = 1; i < offset; i++) {
                if (data[i] != 0x00) return State::Invalid;
            }
        }
        for (size_t i = offset; i < kFrameLen; i++) {
            if (!isVinChar(data[i])) return State::Invalid;
        }

        segments_[index].assign(reinterpret_cast<const char*>(data) + offset,
                                kFrameLen - offset);
        for (const auto& segment : segments_) {
            if (segment.empty()) return State::Waiting;
        }
        completed_ = segments_[0] + segments_[1] + segments_[2];
        return State::Ready;
    }

    bool ready() const { return !completed_.empty(); }

    const std::string& vin() const { return completed_; }

    void reset() {
        for (auto& segment : segments_) segment.clear();
        completed_.clear();
    }

private:
    // ISO 3779: digits and letters except I, O, Q.
    static bool isVinChar(uint8_t c) {
        if (c >= '0' && c <= '9') return true;
        if (c >= 'A' && c <= 'Z') return c != 'I' && c != 'O' && c != 'Q';
        return false;
    }

    std::array<std::string, 3> segments_;
    std::string completed_;
};
