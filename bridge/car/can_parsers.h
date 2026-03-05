#pragma once

#include <map>
#include <vector>
#include <string>
#include <cstring>
#include "dbc/dbcfile.h"

// Provides signal value access across multiple CAN buses, inspired by opendbc's CANParser.
// Stores the latest raw frame data per (bus, address) and decodes signals on demand.
//
// Usage:
//   parsers.updateFrame(bus, address, data, len);
//
//   // Look up by message name (searches all buses):
//   double angle = parsers.get("SCCM_steeringAngleSensor", "SCCM_steeringAngle");
//
//   // Look up by bus + message name:
//   double angle = parsers.get(1, "SCCM_steeringAngleSensor", "SCCM_steeringAngle");
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

    void updateFrame(int bus, uint32_t address, const uint8_t* data, size_t len) {
        auto& stored = latestFrames_[address];
        stored.resize(len);
        std::memcpy(stored.data(), data, len);
    }

    double get(const std::string& msgName, const std::string& sigName) const {
        for (const auto& [busIdx, dbc] : busDbcFiles_) {
            auto* msg = dbc->msg(msgName);
            if (!msg) continue;

            if (latestFrames_.count( msg->address)) {
                return getSignalFromMsg(busIdx, msg, sigName);
            }
        }
        return 0.0;
    }

    double get(int bus, const std::string& msgName, const std::string& sigName) const {
        auto it = busDbcFiles_.find(bus);
        if (it == busDbcFiles_.end()) return 0.0;

        auto* msg = it->second->msg(msgName);
        if (!msg) return 0.0;

        return getSignalFromMsg(bus, msg, sigName);
    }

    double get(int bus, uint32_t address, const std::string& sigName) const {
        auto it = busDbcFiles_.find(bus);
        if (it == busDbcFiles_.end()) return 0.0;

        auto* msg = it->second->msg(address);
        if (!msg) return 0.0;

        return getSignalFromMsg(bus, msg, sigName);
    }

private:
    double getSignalFromMsg(int bus, const cabana::Msg* msg, const std::string& sigName) const {
        auto* sig = msg->sig(sigName);
        if (!sig) return 0.0;

        auto frameIt = latestFrames_.find(msg->address);
        if (frameIt == latestFrames_.end()) return 0.0;

        const auto& frameData = frameIt->second;
        double value = 0.0;
        sig->getValue(frameData.data(), frameData.size(), &value);
        return value;
    }

    std::map<int, DBCFile*> busDbcFiles_;
    std::map<uint32_t, std::vector<uint8_t>> latestFrames_;
};
