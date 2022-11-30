# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("session_util")

# Compiles in C++17 mode
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

set(BUILD_SHARED_LIBS ON CACHE BOOL "")
add_subdirectory(../../../libsession-util libsession)

add_library( # Sets the name of the library.
        session_util

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        session_util.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Copy libsodium-internal.so into the library output dir manually because gradle invokes cmake in
# ways that doesn't properly do this:
get_target_property(sodium_lib_loc libsodium-internal IMPORTED_LOCATION)
add_custom_command(TARGET session_util POST_BUILD
    COMMAND
    ${CMAKE_COMMAND} -E copy ${sodium_lib_loc} ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        session_util
        PUBLIC
        libsession::config
        libsession::crypto
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
