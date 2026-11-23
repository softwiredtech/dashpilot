// Host-run unit tests for DashKit CAN subscription discovery
// (repo/bridge/car/can_subscriptions.h): the (bus, id) list the app writes to
// the DashKit's CAN filter is whatever the vehicle's mapper reads. Runs against
// the DBC files the apps ship. Built by CMakeLists.txt when not configuring for
// Android:
//   cmake -B build-host -S . && cmake --build build-host && ctest --test-dir build-host

#include "car/can_subscriptions.h"
#include "car/cars/tesla.h"

#include <algorithm>
#include <cstdio>
#include <fstream>
#include <map>
#include <regex>
#include <sstream>
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

using Subs = std::vector<std::pair<int, uint32_t>>;

std::string readFile(const std::string& path) {
    std::ifstream f(path);
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

// Message name -> id, straight from the DBC text.
std::map<std::string, uint32_t> messageIds(const std::string& dbc) {
    std::map<std::string, uint32_t> ids;
    std::regex bo(R"(BO_ (\d+) (\w+):)");
    for (auto it = std::sregex_iterator(dbc.begin(), dbc.end(), bo); it != std::sregex_iterator(); ++it) {
        ids[(*it)[2]] = static_cast<uint32_t>(std::stoul((*it)[1]));
    }
    return ids;
}

struct Rig {
    std::string dbc;
    std::map<std::string, uint32_t> ids;
    CANParsers cp;

    explicit Rig(const std::string& file) : dbc(readFile(std::string(DBC_DIR) + "/" + file)), ids(messageIds(dbc)) {
        CHECK(!dbc.empty());
        // Both DashKit buses share one DBC, as in config_dashkit.json / config_juniper.json.
        cp.addBus(0, dbc);
        cp.addBus(1, dbc);
        cp.buildCache();
    }

    bool has(const Subs& subs, int bus, const std::string& message) const {
        auto it = ids.find(message);
        if (it == ids.end()) return false;
        return std::find(subs.begin(), subs.end(), std::make_pair(bus, it->second)) != subs.end();
    }
};

void dashkit_mapper_subscribes_to_what_it_reads() {
    Rig rig("bus_1_tesla_vehicle.dbc");
    TeslaDashKitMapper mapper;
    Subs subs = probeCanSubscriptions(rig.cp, mapper);

    // Chassis bus.
    for (const char* m : {"SCCM_steeringAngleSensor", "DI_systemStatus", "DI_speed", "DAS_status",
                          "DAS_status2", "DAS_road"}) {
        CHECK(rig.has(subs, 0, m));
    }
    // Vehicle bus.
    for (const char* m : {"VCLEFT_lightStatus", "VCRIGHT_lightStatus", "VCFRONT_status",
                          "VCLEFT_switchStatus", "UI_hvacRequest", "BMS_energyStatus",
                          "BMS_powerAvailable", "BMS_hvBusStatus", "BMS_bmbMinMax", "DI_odometerStatus",
                          "VIN_info", "VCSEC_TPMSData", "VCLEFT_doorStatus", "VCRIGHT_doorStatus",
                          "VCLEFT_liftgateStatus", "UI_ambientLightingCtrls"}) {
        CHECK(rig.has(subs, 1, m));
    }
    // Nothing else: a message the mapper stops reading leaves the list too.
    CHECK(subs.size() == 22);
    // Messages only read once a frame has arrived are still found on an empty
    // parser (muxed TPMS and liftgate, the VIN, ambient lighting).
    CHECK(rig.has(subs, 1, "VCSEC_TPMSData") && rig.has(subs, 1, "VIN_info"));
    // Per bus: the chassis copy of a vehicle-bus message is not subscribed.
    CHECK(!rig.has(subs, 0, "VCFRONT_status") && !rig.has(subs, 1, "DI_systemStatus"));
    CHECK(std::is_sorted(subs.begin(), subs.end()));
}

void juniper_mapper_has_its_own_list() {
    Rig rig("new_gen_juniper.dbc");
    TeslaJuniperMapper mapper;
    Subs subs = probeCanSubscriptions(rig.cp, mapper);
    CHECK(rig.has(subs, 0, "DI_systemStatus") && rig.has(subs, 0, "UI_hvacRequest"));
    CHECK(rig.has(subs, 1, "SCCM_steeringAngleSensor") && rig.has(subs, 1, "VIN_info"));
    CHECK(!rig.has(subs, 1, "UI_hvacRequest"));          // chassis bus only on Juniper
    CHECK(subs.size() == 17);
    CHECK(!encodeCanFilter(subs).empty());
}

void probing_does_not_disturb_decoding() {
    Rig rig("bus_1_tesla_vehicle.dbc");
    TeslaDashKitMapper live;
    CarState cs;
    const uint8_t odo[8] = {0x10, 0x27, 0, 0, 0, 0, 0, 0};          // raw 10000 * 0.001 km
    rig.cp.updateFrame(1, rig.ids.at("DI_odometerStatus"), odo, sizeof(odo));
    live.update(rig.cp, cs);
    CHECK(cs.odometer == 10.0);

    TeslaDashKitMapper probe;
    Subs first = probeCanSubscriptions(rig.cp, probe);
    live.update(rig.cp, cs);
    CHECK(cs.odometer == 10.0);                                     // live state untouched

    // Recording stops with the probe: later reads are not remembered, and a
    // second probe reports the same list.
    live.update(rig.cp, cs);
    CHECK(rig.cp.endRecording().empty());
    TeslaDashKitMapper probe2;
    CHECK(probeCanSubscriptions(rig.cp, probe2) == first);
}

void filter_wire_format() {
    // Per bus: [bus][count][id LE32]...
    Subs subs = {{0, 0x118}, {0, 0x129}, {1, 0x405}};
    std::vector<uint8_t> want = {0, 2, 0x18, 0x01, 0, 0, 0x29, 0x01, 0, 0, 1, 1, 0x05, 0x04, 0, 0};
    CHECK(encodeCanFilter(subs) == want);
    CHECK(encodeCanFilter({}).empty());

    // The largest list one write can carry: 127 ids on one bus (2 + 127 * 4 = 510 bytes).
    Subs many;
    for (uint32_t i = 0; i < 127; i++) many.emplace_back(1, 0x100 + i);
    CHECK(encodeCanFilter(many).size() == 510);
    // One more does not fit: no partial list, the DashKit keeps its default.
    many.emplace_back(1, 0x200);
    CHECK(encodeCanFilter(many).empty());
    CHECK(encodeCanFilter({{300, 0x100}}).empty());                 // bus does not fit in a byte
}

}  // namespace

int main() {
    dashkit_mapper_subscribes_to_what_it_reads();
    juniper_mapper_has_its_own_list();
    probing_does_not_disturb_decoding();
    filter_wire_format();

    if (failures) {
        std::printf("%d check(s) failed\n", failures);
        return 1;
    }
    std::printf("can_subscriptions_test: all checks passed\n");
    return 0;
}
