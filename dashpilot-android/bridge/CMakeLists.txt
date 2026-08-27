cmake_minimum_required(VERSION 3.22)
project(dashpilot-android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)

set(MSGQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../bridge")

if(ANDROID)
set(ZMQ_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/zmq")
set(CAPNP_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/capnp")
set(GENERATED_CEREAL_FILES "${CMAKE_CURRENT_SOURCE_DIR}/../../bridge/cereal/gen")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=0x4000")
file(GLOB_RECURSE GENERATED_CAPNP_SOURCES
        "${GENERATED_CEREAL_FILES}/*.c++"
)

file(GLOB_RECURSE GENERATED_CAPNP_HEADERS
        "${GENERATED_CEREAL_FILES}/*.h"
)

# Cap'n Proto
add_library(capnp STATIC IMPORTED)
set_target_properties(capnp PROPERTIES
        IMPORTED_LOCATION ${CAPNP_ROOT}/lib/${ANDROID_ABI}/libcapnp.a
        INTERFACE_INCLUDE_DIRECTORIES ${MSGQ_ROOT}/include
)

add_library(kj STATIC IMPORTED)
set_target_properties(kj PROPERTIES
        IMPORTED_LOCATION ${CAPNP_ROOT}/lib/${ANDROID_ABI}/libkj.a
        INTERFACE_INCLUDE_DIRECTORIES ${MSGQ_ROOT}/include
)

# Import ZeroMQ static library
add_library(zmq STATIC IMPORTED)
set_target_properties(zmq PROPERTIES
        IMPORTED_LOCATION
        ${ZMQ_ROOT}/lib/${ANDROID_ABI}/libzmq.a
        INTERFACE_INCLUDE_DIRECTORIES
        ${MSGQ_ROOT}/include/zmq
)

set(MSGQ_SOURCES
        ${MSGQ_ROOT}/msgq/ipc.cc
        ${MSGQ_ROOT}/msgq/event.cc
        ${MSGQ_ROOT}/msgq/impl_msgq.cc
        ${MSGQ_ROOT}/msgq/impl_zmq.cc
        ${MSGQ_ROOT}/msgq/impl_fake.cc
        ${MSGQ_ROOT}/msgq/msgq.cc
        ${MSGQ_ROOT}/dbc/dbc.cc
        ${MSGQ_ROOT}/dbc/dbcfile.cc
        ${GENERATED_CAPNP_SOURCES}
        src/main/cpp/jni_bridge.cpp
)

add_library(bridge SHARED ${MSGQ_SOURCES})

target_compile_definitions(bridge PRIVATE MSGQ_ANDROID)
target_compile_options(bridge PRIVATE -Wall -Wextra)

target_include_directories(bridge PRIVATE
        ${MSGQ_ROOT}
        ${MSGQ_ROOT}/msgq
        ${MSGQ_ROOT}/dbc
        "${CMAKE_CURRENT_SOURCE_DIR}/../../bridge/capnp/gen"
)

target_link_libraries(bridge
        PRIVATE
        capnp
        kj
        zmq
        android
        log
)
else()
# Host-side unit tests for the native decode logic (not built for Android).
# Build & run: cmake -B build-host -S . && cmake --build build-host && ctest --test-dir build-host
enable_testing()
add_executable(tesla_vin_mapper_test
        tests/tesla_vin_mapper_test.cpp
        ${MSGQ_ROOT}/dbc/dbc.cc
        ${MSGQ_ROOT}/dbc/dbcfile.cc
)
target_include_directories(tesla_vin_mapper_test PRIVATE ${MSGQ_ROOT})
target_compile_options(tesla_vin_mapper_test PRIVATE -Wall -Wextra)
add_test(NAME tesla_vin_mapper COMMAND tesla_vin_mapper_test)
endif()
