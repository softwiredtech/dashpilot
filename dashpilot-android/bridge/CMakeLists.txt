cmake_minimum_required(VERSION 3.22)
project(dashpilot-android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)

set(BRIDGE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../bridge")

if(ANDROID)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=0x4000")

add_library(bridge SHARED
        ${BRIDGE_ROOT}/dbc/dbc.cc
        ${BRIDGE_ROOT}/dbc/dbcfile.cc
        src/main/cpp/jni_bridge.cpp
)

target_compile_options(bridge PRIVATE -Wall -Wextra)

target_include_directories(bridge PRIVATE
        ${BRIDGE_ROOT}
        ${BRIDGE_ROOT}/dbc
)

target_link_libraries(bridge
        PRIVATE
        android
        log
)
else()
# Host-side unit tests for the native decode logic (not built for Android).
# Build & run: cmake -B build-host -S . && cmake --build build-host && ctest --test-dir build-host
enable_testing()
add_executable(tesla_vin_mapper_test
        tests/tesla_vin_mapper_test.cpp
        ${BRIDGE_ROOT}/dbc/dbc.cc
        ${BRIDGE_ROOT}/dbc/dbcfile.cc
)
target_include_directories(tesla_vin_mapper_test PRIVATE ${BRIDGE_ROOT})
target_compile_options(tesla_vin_mapper_test PRIVATE -Wall -Wextra)
add_test(NAME tesla_vin_mapper COMMAND tesla_vin_mapper_test)

add_executable(tesla_closure_mapper_test
        tests/tesla_closure_mapper_test.cpp
        ${BRIDGE_ROOT}/dbc/dbc.cc
        ${BRIDGE_ROOT}/dbc/dbcfile.cc
)
target_include_directories(tesla_closure_mapper_test PRIVATE ${BRIDGE_ROOT})
target_compile_options(tesla_closure_mapper_test PRIVATE -Wall -Wextra)
add_test(NAME tesla_closure_mapper COMMAND tesla_closure_mapper_test)

add_executable(tesla_ambient_lighting_test
        tests/tesla_ambient_lighting_test.cpp
        ${BRIDGE_ROOT}/dbc/dbc.cc
        ${BRIDGE_ROOT}/dbc/dbcfile.cc
)
target_include_directories(tesla_ambient_lighting_test PRIVATE ${BRIDGE_ROOT})
target_compile_options(tesla_ambient_lighting_test PRIVATE -Wall -Wextra)
set(TESLA_DBC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/assets/vehicles/tesla")
add_test(NAME tesla_ambient_lighting COMMAND tesla_ambient_lighting_test
        "${TESLA_DBC_DIR}/bus_1_tesla_vehicle.dbc"
        "${TESLA_DBC_DIR}/new_gen_juniper.dbc")
add_executable(can_subscriptions_test
        tests/can_subscriptions_test.cpp
        ${BRIDGE_ROOT}/dbc/dbc.cc
        ${BRIDGE_ROOT}/dbc/dbcfile.cc
)
target_include_directories(can_subscriptions_test PRIVATE ${BRIDGE_ROOT})
target_compile_options(can_subscriptions_test PRIVATE -Wall -Wextra)
# Runs against the DBC files the Android app ships.
target_compile_definitions(can_subscriptions_test PRIVATE
        DBC_DIR="${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/assets/vehicles/tesla")
add_test(NAME can_subscriptions COMMAND can_subscriptions_test)
endif()
