
cmake_minimum_required(VERSION 3.22)

project(DocDoomNative LANGUAGES C CXX)

include(ExternalProject)

# -----------------------------
# libjpeg-turbo
# -----------------------------

set(JPEG_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libjpeg-turbo-3.2.0")
set(JPEG_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo-build")

ExternalProject_Add(
    libjpeg_turbo_external
    SOURCE_DIR "${JPEG_SOURCE_DIR}"
    BINARY_DIR "${JPEG_BUILD_DIR}"
    CMAKE_ARGS
        "-DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}"
        "-DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}"
        "-DANDROID_ABI=${ANDROID_ABI}"
        "-DANDROID_PLATFORM=${ANDROID_PLATFORM}"
        "-DANDROID_NDK=${ANDROID_NDK}"
        "-DCMAKE_BUILD_TYPE=Release"
        "-DENABLE_SHARED=OFF"
        "-DENABLE_STATIC=ON"
        "-DWITH_TURBOJPEG=OFF"
        "-DWITH_TOOLS=OFF"
        "-DWITH_TESTS=OFF"
        "-DWITH_SIMD=OFF"
    BUILD_COMMAND
        "${CMAKE_COMMAND}" --build <BINARY_DIR> --config Release --target jpeg-static
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS "${JPEG_BUILD_DIR}/libjpeg.a"
)

add_library(docdoom_jpeg STATIC IMPORTED GLOBAL)

set_target_properties(docdoom_jpeg PROPERTIES
    IMPORTED_LOCATION "${JPEG_BUILD_DIR}/libjpeg.a"
    INTERFACE_INCLUDE_DIRECTORIES
        "${JPEG_SOURCE_DIR}/src;${JPEG_BUILD_DIR}"
)

add_dependencies(docdoom_jpeg libjpeg_turbo_external)


# -----------------------------
# zlib
# -----------------------------

set(ZLIB_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/zlib-1.3.1")
set(ZLIB_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/zlib-build")

ExternalProject_Add(
    zlib_external
    SOURCE_DIR "${ZLIB_SOURCE_DIR}"
    BINARY_DIR "${ZLIB_BUILD_DIR}"
    CMAKE_ARGS
        "-DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}"
        "-DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}"
        "-DANDROID_ABI=${ANDROID_ABI}"
        "-DANDROID_PLATFORM=${ANDROID_PLATFORM}"
        "-DANDROID_NDK=${ANDROID_NDK}"
        "-DCMAKE_BUILD_TYPE=Release"
        "-DBUILD_SHARED_LIBS=OFF"
    BUILD_COMMAND
        "${CMAKE_COMMAND}" --build <BINARY_DIR> --config Release --target zlibstatic
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS "${ZLIB_BUILD_DIR}/libz.a"
)

add_library(docdoom_zlib STATIC IMPORTED GLOBAL)

set_target_properties(docdoom_zlib PROPERTIES
    IMPORTED_LOCATION "${ZLIB_BUILD_DIR}/libz.a"
    INTERFACE_INCLUDE_DIRECTORIES
        "${ZLIB_BUILD_DIR};${ZLIB_SOURCE_DIR}"
)

add_dependencies(docdoom_zlib zlib_external)


# -----------------------------
# libpng
# -----------------------------

set(PNG_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libpng-1.6.50")
set(PNG_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/libpng-build")

ExternalProject_Add(
    libpng_external
    SOURCE_DIR "${PNG_SOURCE_DIR}"
    BINARY_DIR "${PNG_BUILD_DIR}"
    CMAKE_ARGS
        "-DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}"
        "-DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}"
        "-DANDROID_ABI=${ANDROID_ABI}"
        "-DANDROID_PLATFORM=${ANDROID_PLATFORM}"
        "-DANDROID_NDK=${ANDROID_NDK}"
        "-DCMAKE_BUILD_TYPE=Release"
        "-DPNG_SHARED=OFF"
        "-DPNG_STATIC=ON"
        "-DPNG_TESTS=OFF"
        "-DPNG_TOOLS=OFF"
        "-DZLIB_INCLUDE_DIR=${ZLIB_SOURCE_DIR}"
        "-DZLIB_LIBRARY=${ZLIB_BUILD_DIR}/libz.a"
    BUILD_COMMAND
        "${CMAKE_COMMAND}" --build <BINARY_DIR> --config Release --target png_static
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS "${PNG_BUILD_DIR}/libpng16.a"
)

add_library(docdoom_png STATIC IMPORTED GLOBAL)

set_target_properties(docdoom_png PROPERTIES
    IMPORTED_LOCATION "${PNG_BUILD_DIR}/libpng16.a"
    INTERFACE_INCLUDE_DIRECTORIES
        "${PNG_BUILD_DIR};${PNG_SOURCE_DIR};${ZLIB_BUILD_DIR};${ZLIB_SOURCE_DIR}"
)

add_dependencies(docdoom_png libpng_external)
add_dependencies(libpng_external zlib_external)


# -----------------------------
# DocDoom native library
# -----------------------------

add_library(docdoom_native SHARED native_image_compressor.cpp)

target_compile_features(docdoom_native PRIVATE cxx_std_17)

target_include_directories(docdoom_native PRIVATE
    "${JPEG_SOURCE_DIR}/src"
    "${JPEG_BUILD_DIR}"
    "${PNG_BUILD_DIR}"
    "${PNG_SOURCE_DIR}"
    "${ZLIB_BUILD_DIR}"
    "${ZLIB_SOURCE_DIR}"
)

# Ensure generated headers and libraries exist before native compilation.
add_dependencies(docdoom_native
    libjpeg_turbo_external
    zlib_external
    libpng_external
)

target_link_libraries(docdoom_native PRIVATE
    docdoom_jpeg
    docdoom_png
    docdoom_zlib
)
