import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is configured through local.properties (gitignored) or the
// matching environment variables, so the keystore and its passwords never live
// in the repository. See README > Building a signed release.
//
// local.properties:
//   vztats.storeFile=C:/path/to/vztats-release.jks
//   vztats.storePassword=...
//   vztats.keyAlias=vztats
//   vztats.keyPassword=...
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingProp(name: String): String? =
    (localProps.getProperty("vztats.$name") ?: System.getenv("VZTATS_" + name.uppercase()))
        ?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProp("storeFile")
val hasReleaseSigning = releaseStoreFile != null &&
    rootProject.file(releaseStoreFile).exists() &&
    signingProp("storePassword") != null &&
    signingProp("keyAlias") != null &&
    signingProp("keyPassword") != null

android {
    namespace = "com.vauzi.vztats"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.vauzi.vztats"
        // targetSdk is intentionally kept at 25 (matching the original app): a
        // higher targetSdk moves the app into a stricter SELinux domain that is
        // denied read access to /sys/class/kgsl (GPU frequency). minSdk must be
        // <= targetSdk, and a lower minSdk also supports more old devices.
        minSdk = 25
        targetSdk = 25
        // First public pre-release.
        versionCode = 1
        versionName = "0.1"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    ndkVersion = "27.0.12077973"

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingProp("storePassword")
                keyAlias = signingProp("keyAlias")
                keyPassword = signingProp("keyPassword")
                // minSdk is 25 and the v2 scheme landed in API 24, so every
                // supported device can verify v2 and the legacy v1 JAR
                // signature is not needed.
                enableV2Signing = true
            }
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
            // Falls back to an unsigned build when no keystore is configured, so
            // the project still builds for anyone cloning it. Signed release
            // APKs are only produced on a machine that has the key.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            isDebuggable = true
        }
    }

    lint {
        // Play Store's "target a recent API level" rule. VZtats is sideload-only
        // and deliberately pinned to targetSdk 25 so it keeps SELinux read
        // access to /sys/class/kgsl (see defaultConfig above), so this rule is
        // not applicable and must not fail the release build.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// Versions are pinned to what is already resolved in the local Gradle cache so
// the project builds without pulling a Compose BOM or the Material Components
// (View) library over the network.
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.compose.runtime:runtime:1.9.4")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-graphics:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")

    // Shizuku — run shell-privileged commands (SurfaceFlinger FPS) without root.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
