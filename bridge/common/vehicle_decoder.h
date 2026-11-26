#pragma once

#include "car/can_parsers.h"
#include "car/can_subscriptions.h"
#include "car/car_state.h"
#include "car/car_state_mapper.h"
#include "car/cars/tesla.h"
#include <vector>
#include <memory>
#include <string>

inline std::unique_ptr<CarStateMapper> createMapper(const std::string& vehicleType) {
    if (vehicleType == "dashkit") return std::make_unique<TeslaDashKitMapper>();
    if (vehicleType == "tesla_juniper") return std::make_unique<TeslaJuniperMapper>();
    return nullptr;
}

class VehicleDecoder {
public:
    VehicleDecoder(const std::vector<std::string>& dbcContents,
                   const std::vector<int>& busIndices,
                   const std::string& vehicleType) {
        for (size_t i = 0; i < dbcContents.size(); i++) {
            parsers_.addBus(busIndices[i], dbcContents[i]);
        }
        parsers_.buildCache();
        mapper_ = createMapper(vehicleType);
        vehicleType_ = vehicleType;
    }

    // The CAN filter payload a DashKit needs for this vehicle (see
    // car/can_subscriptions.h); empty if the vehicle type has no mapper.
    // Probes with a throwaway mapper so the live one keeps its state.
    std::vector<uint8_t> canFilterPayload() const {
        auto probe = createMapper(vehicleType_);
        if (!probe) return {};
        return encodeCanFilter(probeCanSubscriptions(parsers_, *probe));
    }

    void updateFrame(int bus, uint32_t address, const uint8_t* data, size_t len) {
        parsers_.updateFrame(bus, address, data, len);
    }

    void updateMapper() {
        if (mapper_) mapper_->update(parsers_, state_);
    }

    CarState& state() { return state_; }

private:
    CANParsers parsers_;
    std::unique_ptr<CarStateMapper> mapper_;
    std::string vehicleType_;
    CarState state_;
};
