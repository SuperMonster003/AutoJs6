cmake_minimum_required(VERSION 3.22.1)

project(RapidOcr)

# OnnxRuntime

include(${CMAKE_CURRENT_SOURCE_DIR}/../onnxruntime-shared/OnnxRuntimeWrapper.cmake)
find_package(OnnxRuntime REQUIRED)
if (OnnxRuntime_FOUND)
    message(STATUS "OnnxRuntime_LIBS: ${OnnxRuntime_LIBS}")
    message(STATUS "OnnxRuntime_INCLUDE_DIRS: ${OnnxRuntime_INCLUDE_DIRS}")
else ()
    message(FATAL_ERROR "onnxruntime Not Found!")
endif (OnnxRuntime_FOUND)

## opencv 库

set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/../../sdk/native/jni")
find_package(OpenCV REQUIRED)
if (OpenCV_FOUND)
    message(STATUS "OpenCV_LIBS: ${OpenCV_LIBS}")
    message(STATUS "OpenCV_INCLUDE_DIRS: ${OpenCV_INCLUDE_DIRS}")
else ()
    message(FATAL_ERROR "opencv Not Found!")
endif (OpenCV_FOUND)

# openmp

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fopenmp")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fopenmp")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fopenmp")

if (DEFINED ANDROID_NDK_MAJOR AND ${ANDROID_NDK_MAJOR} GREATER 20)
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-openmp")
endif ()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fomit-frame-pointer -fstrict-aliasing -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fomit-frame-pointer -fstrict-aliasing -ffast-math")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")

# disable rtti and exceptions
# set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-rtti -fno-exceptions")

include_directories(include)
file(GLOB OCR_SRC src/*.cpp)
set(OCR_COMPILE_CODE ${OCR_SRC})

add_library(RapidOcr SHARED ${OCR_COMPILE_CODE})

find_library( # Sets the name of the path variable.
        log-lib
        log)

find_library(
        android-lib
        android
)

target_link_libraries(
        RapidOcr
        ${OnnxRuntime_LIBS}
        ${OpenCV_LIBS}
        android
        z
        ${log-lib}
        ${android-lib}
        jnigraphics)
