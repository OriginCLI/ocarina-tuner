cmake_minimum_required(VERSION 3.22.1)
project(ocarina_engine CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(ocarina_engine SHARED
    PitchEngine.cpp
    jni_bridge.cpp)

# Always optimise the DSP, even in debug builds, so timing in the debug overlay is real.
target_compile_options(ocarina_engine PRIVATE -O3 -ffast-math -Wall)
target_link_libraries(ocarina_engine oboe::oboe log)
