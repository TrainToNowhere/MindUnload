cmake_minimum_required(VERSION 3.22)
project(planner_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)

# Vendored whisper.cpp (v1.8.2) — GPU/vendor backends were stripped, only the CPU
# backend is built. See app/src/main/cpp/whisper.cpp/LICENSE.
set(WHISPER_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Optimize in EVERY build type, debug included. Unoptimized ggml is not "a bit slower":
# measured on a Galaxy S22 Ultra, 3.8 seconds of speech took 180 s at -O0 versus 6.7 s
# at -O3 — a debug build looks like the app has frozen. This is vendored, stable
# third-party code; stepping through it unoptimized buys nothing.
# Must be set before the targets below are defined, ggml's included.
set(CMAKE_C_FLAGS_DEBUG "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

add_library(whisper_jni SHARED
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    whisper_jni.c
)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
)

target_compile_definitions(whisper_jni PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper_jni PRIVATE WHISPER_VERSION="1.8.2")

# armv8.2 fp16 arithmetic roughly halves inference time on the Snapdragon/Exynos
# class of chips this app targets; every 64-bit Android device since 2018 has it.
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisper_jni PRIVATE -march=armv8.2-a+fp16)
    target_compile_options(ggml-cpu PRIVATE -march=armv8.2-a+fp16)
endif ()

target_compile_options(whisper_jni PRIVATE -O3 -ffunction-sections -fdata-sections)
target_link_options(whisper_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)

find_library(LOG_LIB log)
target_link_libraries(whisper_jni ${LOG_LIB} android ggml)
