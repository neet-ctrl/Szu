cmake_minimum_required(VERSION 3.22)
project("accu")

set(CMAKE_CXX_STANDARD 17)

add_compile_options(
    -fdata-sections
    -ffunction-sections
    -fno-exceptions
    -fno-rtti
    -fno-threadsafe-statics
)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-Os -flto -fvisibility=hidden -fvisibility-inlines-hidden)
    add_link_options(-flto -Wl,--gc-sections -Wl,--strip-all)
endif ()

find_package(boringssl REQUIRED CONFIG)

add_library(adb_pairing SHARED
    adb_pairing.cpp
)

target_link_libraries(adb_pairing
    log
    boringssl::crypto_static
)
