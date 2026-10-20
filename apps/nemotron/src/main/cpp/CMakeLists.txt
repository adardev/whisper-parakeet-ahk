cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_NO_AVX ON CACHE BOOL "" FORCE)
set(WHISPER_NO_AVX2 ON CACHE BOOL "" FORCE)
set(WHISPER_NO_FMA ON CACHE BOOL "" FORCE)
set(WHISPER_NO_F16C ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper_cpp ${CMAKE_CURRENT_BINARY_DIR}/whisper_cpp)

add_library(whisper_jni SHARED whisper_jni.c)

target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper_cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper_cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper_cpp/ggml/include
)

target_link_libraries(whisper_jni
    whisper
    android
    log
)
