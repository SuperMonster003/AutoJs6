cmake_minimum_required(VERSION 3.10.2)

project(autojs6_node LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE OFF CACHE BOOL "Enable direct Node embedded lifecycle calls in the experimental probe")
set(AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION OFF CACHE BOOL "Enable experimental embedded Node script execution")
set(AUTOJS6_NODE_RUNTIME_SLOT "node24_5" CACHE STRING "Build-only Embedded Node runtime slot")
set(AUTOJS6_NODE_INCLUDE_DIR "" CACHE PATH "Build-selected Node include/node directory")
set(AUTOJS6_NODE_LIBRARY_ROOT "" CACHE PATH "Build-selected Node jniLibs root")

if ("${AUTOJS6_NODE_INCLUDE_DIR}" STREQUAL "")
    set(NODE_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/node-v24.5.0/include/node")
else ()
    set(NODE_INCLUDE_DIR "${AUTOJS6_NODE_INCLUDE_DIR}")
endif ()

if ("${AUTOJS6_NODE_LIBRARY_ROOT}" STREQUAL "")
    set(NODE_LIBRARY_PATH "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libnode.so")
else ()
    set(NODE_LIBRARY_PATH "${AUTOJS6_NODE_LIBRARY_ROOT}/${ANDROID_ABI}/libnode.so")
endif ()

if (NOT EXISTS "${NODE_INCLUDE_DIR}/node.h")
    message(FATAL_ERROR "Node.js headers are missing for runtime slot ${AUTOJS6_NODE_RUNTIME_SLOT}: ${NODE_INCLUDE_DIR}")
endif ()

if (NOT EXISTS "${NODE_LIBRARY_PATH}")
    message(FATAL_ERROR "libnode.so is missing for runtime slot ${AUTOJS6_NODE_RUNTIME_SLOT}, ABI ${ANDROID_ABI}: ${NODE_LIBRARY_PATH}")
endif ()

add_library(node SHARED IMPORTED GLOBAL)
set_target_properties(node PROPERTIES IMPORTED_LOCATION "${NODE_LIBRARY_PATH}")

add_library(
        autojs6-node
        SHARED
        node_bridge.cpp
        node_bridge_sources.cpp
        node_bridge_validation.cpp
        node_bridge_core.cpp
        node_bridge_payload.cpp
        node_bridge_lifecycle.cpp
        node_bridge_jni.cpp
        node_bridge/embedded_inline_probe_payload.cpp
        node_bridge/embedded_probe_metadata.cpp
        node_bridge/embedded_probe_payload_utils.cpp
        node_bridge/embedded_probe_sources.cpp
)

target_include_directories(
        autojs6-node
        PRIVATE
        "${NODE_INCLUDE_DIR}"
)

target_compile_definitions(
        autojs6-node
        PRIVATE
        AUTOJS6_NODE_ANDROID_ABI="${ANDROID_ABI}"
        AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE=$<BOOL:${AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE}>
        AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=$<BOOL:${AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION}>
        AUTOJS6_NODE_BRIDGE_LINKS_LIBNODE=0
)

if (ANDROID_ABI STREQUAL "x86_64" AND CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    target_compile_options(autojs6-node PRIVATE -O0)
endif ()

target_link_libraries(
        autojs6-node
        PRIVATE
        log
        dl
)
