import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Compile the matching upstream wrapper locally for compatibility with Kotlin 1.8.
val zxingCppNative by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val unpackZxingCppNative = tasks.register<Sync>("unpackZxingCppNative") {
    from(zxingCppNative.elements.map { files -> files.map { zipTree(it.asFile) } })
    include("jni/**/*.so")
    eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("generated/zxingcpp/jniLibs"))
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        debug {
            if (providers.gradleProperty("qrTrial").orNull == "true") {
                applicationIdSuffix = ".qrtrial"
            }
        }
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
    sourceSets.getByName("main") {
        jniLibs.srcDir(layout.buildDirectory.dir("generated/zxingcpp/jniLibs"))
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

tasks.named("preBuild").configure { dependsOn(unpackZxingCppNative) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("net.nicbell.material-lists:listitem:0.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // QRCode Scan
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    add(zxingCppNative.name, "io.github.zxing-cpp:android:3.1.1@aar")

    // TOTP
    implementation("commons-codec:commons-codec:1.16.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.11.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
