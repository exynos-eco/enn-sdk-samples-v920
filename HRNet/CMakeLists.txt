set(CMAKE_C_COMPILER aarch64-poky-linux-gcc)
set(CMAKE_CXX_COMPILER aarch64-poky-linux-g++)
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)

cmake_minimum_required(VERSION 3.16)
project(kittv2_linux)

set(CMAKE_EXPORT_COMPILE_COMMANDS on)
set(top_dir ${CMAKE_SOURCE_DIR})

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -Wall -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions -frtti")
set(ANDROID_STL "gnustl_shared")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/build/bin)

# OpenCV
set(OpenCV_DIR "/opt/opencv-arm-4.1.0/lib/cmake/opencv4")
find_package(OpenCV REQUIRED)
include_directories(${OpenCV_INCLUDE_DIRS})

# GStreamer
find_package(PkgConfig REQUIRED)
pkg_check_modules(GST REQUIRED gstreamer-1.0 gstreamer-app-1.0)
include_directories(${GST_INCLUDE_DIRS})

add_library(enn_public_api_cpp_ SHARED IMPORTED)
set_target_properties(enn_public_api_cpp_ PROPERTIES IMPORTED_LOCATION ${top_dir}/lib64_linux/libenn_public_api_cpp.so)

set(target_name enn_sample_hrnet)
set(PROJECT_SOURCES
        enn_sample_hrnet.cc
)
add_executable(${target_name} ${PROJECT_SOURCES})

target_link_libraries(${target_name} enn_public_api_cpp_ enn_user enn_engine cutils dmabufheap enn_common_utils
                       enn_model enn_user_driver_cpu enn_user_driver_unified enn_jsoncpp ion ${OpenCV_LIBS} ${GST_LIBRARIES})
