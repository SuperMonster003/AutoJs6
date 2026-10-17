cmake_minimum_required(VERSION 3.22)
project(autojs6_file_ops LANGUAGES C)
add_library(autojs6_file_ops SHARED privileged_file_ops.c)
target_compile_options(autojs6_file_ops PRIVATE -Wall -Wextra -Werror)
target_link_options(autojs6_file_ops PRIVATE
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
