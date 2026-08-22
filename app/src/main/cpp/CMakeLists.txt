cmake_minimum_required(VERSION 3.22.1)

project(autojs6_r2_fd_flags LANGUAGES CXX)

add_library(
    autojs6_r2_fd_flags
    SHARED
    r2_file_descriptor_open_flags.cpp
)

target_compile_features(autojs6_r2_fd_flags PRIVATE cxx_std_17)
target_compile_options(
    autojs6_r2_fd_flags
    PRIVATE
    -Wall
    -Wextra
    -Werror
    -fvisibility=hidden
)
