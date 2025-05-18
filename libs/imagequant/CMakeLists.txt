cmake_minimum_required(VERSION 3.22)
project(pngquant_bridge LANGUAGES C CXX)

set(CMAKE_C_STANDARD 99)

# --- 1. zlib（NDK 自带） ---
find_library(ZLIB_LIB z)          # libz.so

# --- 2. libpng ---
add_subdirectory(external/libpng) # 会生成 libpng_static.a
# libpng 自己就会寻找 zlib

# --- 3. libimagequant ---
file(GLOB LIQ_SRC
     "external/libimagequant/*.c")
add_library(imagequant STATIC ${LIQ_SRC})
target_include_directories(imagequant PUBLIC
        external/libimagequant)

# --- 4. 你的 JNI Bridge ---
add_library(pngquant_bridge SHARED
    PngQuantBridge.cpp)

target_include_directories(pngquant_bridge PRIVATE
    external/libimagequant
    external/libpng)

target_link_libraries(pngquant_bridge
    imagequant          # libimagequant 静态库
    png_static          # libpng 静态库
    ${ZLIB_LIB}         # zlib.so
    log)                # __android_log_print