cmake_minimum_required(VERSION 3.22.1)
project(releaseowner)

add_library(releaseowner SHARED releaseowner.cpp)

target_link_libraries(releaseowner
    log
)
