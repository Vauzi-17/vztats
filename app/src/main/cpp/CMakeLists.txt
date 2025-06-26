# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("adrenoturboswitch")

add_subdirectory( adrenotools )
add_subdirectory( sds_library )


add_library(adrenoturboswitch SHARED
        main.cpp )




target_link_libraries(adrenoturboswitch
        sds_library
        android
        log
        adrenotools)
