plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pico.spatial.sample.hotlinechamber"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pico.spatial.sample.hotlinechamber"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
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
    androidResources {
        noCompress.add(".glb")
        noCompress.add(".ktx")
        noCompress.add(".usdz")
        noCompress.add(".bundle")
    }
}

dependencies {
    // Spatial SDK 6.1.x（版本由 BOM 统一对齐）
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.foundation)
    implementation(libs.spatial.tracking)
    implementation(libs.spatial.sense)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.design)

    // AndroidX / Compose（版本与 6.1.9 传递依赖基线对齐）
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

// Spatial SDK 自带裁剪过的 Compose 运行时：排除标准 Compose 件避免类冲突
configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
