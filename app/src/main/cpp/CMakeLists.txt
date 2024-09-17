# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.

project("sonorstream")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ReceivingSide.cpp
        native-lib.cpp
        StreamEngine.cpp
        # to be renamed to .h files and removed (renamed to cpp for fixing header linker bug)
        ClientCommThread.h
        SocketInit.h
        OboeMicRecorder.h
        CircularQueue.h)

add_library(mpg123 STATIC IMPORTED)
set_target_properties(mpg123 PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/libs/${ANDROID_ABI}/libmpg123.so)

include_directories(/include)
include_directories(/mpg123/src/libmpg123)
# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.
find_package (oboe REQUIRED CONFIG)

find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
# Specify the libraries which our native library is dependent on, including Oboe

target_link_libraries(native-lib oboe::oboe ${log-lib} OpenSLES mpg123)