#pragma once

#include <map>
#include <unordered_map>
#include <vector>
#include <string>
#include <cstring>
#include "dbc/dbcfile.h"

class CANParsers {
public:
    ~CANParsers() {
        for (auto& [_, dbc] : busDbcFiles_) {
            delete dbc;
        }
    }

    void addBus(int busIndex, const std::string& dbcContent) {
        busDbcFiles_[busIndex] = new DBCFile("bus_" + std::to_string(busIndex), dbcContent);
    }

    // Pre-resolve all (msgName, sigName) pairs per bus to avoid expensive runtime lookups.
    // Must be called once after all addBus() calls.
    void buildCache() {
        busSignalCache_.clear();
        for (const auto& [busIdx, dbc] : busDbcFiles_) {
            auto& cache = busSignalCache_[busIdx & 3];
            for (const auto& [addr, msg] : dbc->getMessages()) {
                for (const auto* sig : msg.getSignals()) {
                    std::string key = msg.name + "::" + sig->name;
                    cache[key] = { msg.address, sig };
                }
            }
        }
    }

    void updateFrame(int bus, uint32_t address, const uint8_t* data, size_t len) {
        uint64_t key = frameKey(bus, address);
        auto& stored = latestFrames_[key];
        stored.resize(len);
        std::memcpy(stored.data(), data, len);
    }

    double get(int bus, const std::string& msgName, const std::string& sigName) const {
        int maskedBus = bus & 3;
        auto busIt = busSignalCache_.find(maskedBus);
        if (busIt == busSignalCache_.end()) return 0.0;

        auto it = busIt->second.find(msgName + "::" + sigName);
        if (it == busIt->second.end()) return 0.0;

        const auto& cached = it->second;
        auto frameIt = latestFrames_.find(frameKey(maskedBus, cached.address));
        if (frameIt == latestFrames_.end()) return 0.0;

        double value = 0.0;
        cached.signal->getValue(frameIt->second.data(), frameIt->second.size(), &value);
        return value;
    }

private:
    struct CachedSignal {
        uint32_t address;
        const cabana::Signal* signal;
    };

    static inline uint64_t frameKey(int bus, uint32_t address) {
        return (static_cast<uint64_t>(bus & 3) << 32) | address;
    }

    std::map<int, DBCFile*> busDbcFiles_;
    std::map<uint64_t, std::vector<uint8_t>> latestFrames_;
    std::map<int, std::unordered_map<std::string, CachedSignal>> busSignalCache_;
};
