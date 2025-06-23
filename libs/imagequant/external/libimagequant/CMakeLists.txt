cmake_minimum_required(VERSION 2.6)
file(READ version.txt VERSION)

project(imagequant C)

if(${CMAKE_SYSTEM_PROCESSOR} STREQUAL ARM64)
  option(BUILD_WITH_SSE "Use SSE" OFF)
else()
  option(BUILD_WITH_SSE "Use SSE" ON)
endif()

if(BUILD_WITH_SSE)
  add_definitions(-DUSE_SSE=1)
endif()

find_package(OpenMP)
if(OPENMP_FOUND)
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${OpenMP_C_FLAGS}")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
endif()

include_directories(${CMAKE_SOURCE_DIR})

if(CMAKE_COMPILER_IS_GNUCC)
  add_compile_options("-std=c99")
endif()

add_library(imagequant SHARED 
  libimagequant.c
  blur.c
  mediancut.c
  mempool.c
  nearest.c
  pam.c
  kmeans.c
)

add_library(imagequant_a STATIC
  libimagequant.c
  blur.c
  mediancut.c
  mempool.c
  nearest.c
  pam.c
  kmeans.c
)
set_target_properties(imagequant PROPERTIES SOVERSION 0
  VERSION 0.0)

set(PREFIX ${CMAKE_INSTALL_PREFIX})
configure_file(imagequant.pc.in imagequant.pc @ONLY)

install(TARGETS imagequant        LIBRARY           DESTINATION ${LIB_INSTALL_DIR})
install(FILES                     libimagequant.h   DESTINATION include)
install(FILES ${CMAKE_BINARY_DIR}/libimagequant_a.a DESTINATION ${LIB_INSTALL_DIR} RENAME libimagequant.a)
install(FILES ${CMAKE_BINARY_DIR}/imagequant.pc     DESTINATION ${LIB_INSTALL_DIR}/pkgconfig)
