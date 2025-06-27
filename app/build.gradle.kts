

plugins {
    id("com.android.application")
}

android {
    namespace = "com.fartopblu.adrenoturbomode"
    compileSdk = 34



    defaultConfig {
        applicationId = "com.fartopblu.adrenoturbomode"
        minSdk = 25
        targetSdk = 25
        versionCode = 11
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                // cppFlags += "-std=c++11"
            }
        }
        ndk {
            abiFilters.addAll(arrayOf("arm64-v8a"))
        }
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        prefab = true
        viewBinding = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {

    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}