# OnyxBridge CMakeLists.txt
# Builds libonyxbridge.so for all target Android ABIs.
#
# ABIs handled by the Gradle externalNativeBuild integration or by direct
# cmake invocation in CI:
#   arm64-v8a, armeabi-v7a, x86, x86_64

cmake_minimum_required(VERSION 3.18.1)

project(onyxbridge LANGUAGES CXX)

# ---------------------------------------------------------------------- #
# C++ Standard                                                           #
# ---------------------------------------------------------------------- #
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ---------------------------------------------------------------------- #
# Sources                                                                #
# ---------------------------------------------------------------------- #
set(ONYXBRIDGE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/onyxbridge.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/permission_manager.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/toast_helper.cpp
)

set(ONYXBRIDGE_HEADERS
    ${CMAKE_CURRENT_SOURCE_DIR}/onyxbridge.h
    ${CMAKE_CURRENT_SOURCE_DIR}/permission_manager.h
    ${CMAKE_CURRENT_SOURCE_DIR}/toast_helper.h
)

add_library(onyxbridge SHARED ${ONYXBRIDGE_SOURCES} ${ONYXBRIDGE_HEADERS})

target_include_directories(onyxbridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# ---------------------------------------------------------------------- #
# Android platform libraries                                            #
# ---------------------------------------------------------------------- #
find_library(log-lib     log)
find_library(android-lib android)

target_link_libraries(onyxbridge
    ${log-lib}
    ${android-lib}
)

# ---------------------------------------------------------------------- #
# Compile flags                                                          #
# ---------------------------------------------------------------------- #
target_compile_options(onyxbridge PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

# Strip debug symbols and dead-code-eliminate in release builds
target_link_options(onyxbridge PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(onyxbridge PRIVATE -O2 -DNDEBUG)
    target_link_options(onyxbridge PRIVATE -Wl,-s)
endif()

# ---------------------------------------------------------------------- #
# Library name                                                           #
# ---------------------------------------------------------------------- #
set_target_properties(onyxbridge PROPERTIES
    OUTPUT_NAME "onyxbridge"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)
