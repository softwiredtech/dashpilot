#pragma once

#include "car/can_parsers.h"
#include "car/car_state.h"
#include "car/car_state_mapper.h"
#include "car/vin_assembler.h"
#include "car/cars/tesla.h"
#include "msgq/ipc.h"
#include <vector>
#include <memory>
#include <string>

struct SubSocketGroup {
    std::vector<SubSocket*> sockets;
};

inline std::unique_ptr<CarStateMapper> createMapper(const std::string& vehicleType) {
    if (vehicleType == "tesla_party") return std::make_unique<TeslaCommaPartyMapper>();
    if (vehicleType == "tesla_extra") return std::make_unique<TeslaCommaExtraMapper>();
    if (vehicleType == "dashkit") return std::make_unique<TeslaDashKitMapper>();
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
    }

    void updateFrame(int bus, uint32_t address, const uint8_t* data, size_t len) {
        parsers_.updateFrame(bus, address, data, len);
        vin_.onFrame(bus, address, data, len);
    }

    void updateMapper() {
        if (mapper_) mapper_->update(parsers_, state_);
    }

    CarState& state() { return state_; }

    // Vehicle VIN assembled from the CAN stream (empty until complete).
    bool vinReady() const { return vin_.ready(); }
    const std::string& vin() const { return vin_.vin(); }
    void resetVin() { vin_.reset(); }

private:
    CANParsers parsers_;
    std::unique_ptr<CarStateMapper> mapper_;
    CarState state_;
    VinAssembler vin_;
};
