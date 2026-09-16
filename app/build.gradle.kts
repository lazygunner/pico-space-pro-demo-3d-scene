plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pico.spatial.sample.hotlinechamber"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pico.spatial.sample.hotlinechamber"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = libs.versions.spatialBom.get()
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters.add("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.4.3" }
}

dependencies {
    // Spatial SDK dependencies（全部已在本机 Gradle 缓存，无需额外下载）
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.foundation)
    implementation(libs.spatial.tracking)
    implementation(libs.spatial.sense)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.design)

    // AndroidX / Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

// Spatial SDK 要求：避免与自带的 Compose 运行时冲突
configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
