cmake_minimum_required(VERSION 3.22)
project(dashkit-connect LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)

# Shares the DBC parser and per-vehicle CarState mappers with the comma
# bridge; only the sources needed to decode frames are compiled here, so
# this library has no zmq / capnp dependency.
set(BRIDGE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../bridge")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=0x4000")

add_library(dashkitdecoder SHARED
        ${BRIDGE_ROOT}/dbc/dbc.cc
        ${BRIDGE_ROOT}/dbc/dbcfile.cc
        src/main/cpp/dashkit_decoder_jni.cpp
)

target_compile_options(dashkitdecoder PRIVATE -Wall -Wextra)
target_include_directories(dashkitdecoder PRIVATE ${BRIDGE_ROOT})
target_link_libraries(dashkitdecoder PRIVATE android log)
