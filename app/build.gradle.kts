import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.natfrp.authguest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.natfrp.authguest"
        minSdk = 26
        targetSdk = 33
        versionCode = 3
        versionName = "1.2"

        vectorDrawables {
            useSupportLibrary = true
        }
        archivesName.set("AuthWidget")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            ndk {
                abiFilters.clear()
                abiFilters.add("arm64-v8a")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {}
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            for (abiStr in abiFilters) {
                include(abiStr)
            }
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("net.nicbell.material-lists:listitem:0.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // QRCode Scan
    implementation("androidx.camera:camera-mlkit-vision:1.3.0-beta02")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    // It's safe to use 17.0.2 since we excluded vulnerable play-services-basement,
    // and keep vulnerabilities in barcode-scanning we need.
    implementation("com.google.mlkit:barcode-scanning:17.0.2") {
        exclude("com.google.android.gms", "play-services-base")
        exclude("com.google.android.gms", "play-services-basement")
        exclude("com.google.android.datatransport", "transport-api")
        exclude("com.google.android.datatransport", "transport-backend-cct")
        exclude("com.google.android.datatransport", "transport-runtime")
        exclude("com.google.firebase", "firebase-encoders-json")
        exclude("com.google.firebase", "firebase-encoders")
    }

    // TOTP
    implementation("commons-codec:commons-codec:1.16.0")
}