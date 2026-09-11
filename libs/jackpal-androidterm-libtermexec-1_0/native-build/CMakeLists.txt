cmake_minimum_required(VERSION 3.22)
project(autojs6_jackpal LANGUAGES CXX)

if(NOT DEFINED JACKPAL_SOURCE)
    message(FATAL_ERROR "Set JACKPAL_SOURCE to the locked upstream checkout")
endif()
set(CMAKE_CXX_STANDARD 98)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_library(jackpal-androidterm5 SHARED
    ${JACKPAL_SOURCE}/term/src/main/jni/common.cpp
    ${JACKPAL_SOURCE}/term/src/main/jni/fileCompat.cpp
    ${JACKPAL_SOURCE}/term/src/main/jni/termExec.cpp)
add_library(jackpal-termexec2 SHARED ${JACKPAL_SOURCE}/libtermexec/src/main/jni/process.cpp)
# Old NDK headers supplied these declarations transitively; keep upstream sources intact.
target_compile_options(jackpal-termexec2 PRIVATE -include string.h)
foreach(target jackpal-androidterm5 jackpal-termexec2)
    target_compile_options(${target} PRIVATE -fno-exceptions -fno-rtti)
    target_link_options(${target} PRIVATE
        -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,--build-id=sha1)
    target_link_libraries(${target} PRIVATE log)
endforeach()
