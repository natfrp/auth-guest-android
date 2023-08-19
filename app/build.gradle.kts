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
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

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
            include("arm64-v8a", "x86")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("net.nicbell.material-lists:listitem:0.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // QRCode Scan
    implementation("androidx.camera:camera-mlkit-vision:1.3.0-beta02")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    val cameraxVersion = "1.2.3"
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