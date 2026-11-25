plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.hori.browser"
    // Minor SDK release: Android 17 = API 37.0 (package platforms;android-37.0).
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "dev.hori.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // GeckoView ships arm64, armeabi-v7a and x86_64; hori-core matches.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug keys for now; a real release keystore comes before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 has built-in Kotlin support; match the stdlib pulled by Compose.
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            // UniFFI-generated Kotlin bindings (tools/build-core.ps1 regenerates them).
            java.srcDir("src/main/kotlin")
            // Cross-compiled libhori_core.so per ABI.
            jniLibs.srcDir("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:161.0.20261118180411")

    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")

    // UniFFI Kotlin bindings call the Rust core through JNA.
    implementation("net.java.dev.jna:jna:5.19.1@aar")
}