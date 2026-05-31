plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    // IMPORTANT FOR KOTLINX SERIALIZATION!
    // ADD THIS LINE (without the version)
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.reference.implementation.messages"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.reference.implementation.messages"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
        buildConfig = true // Enables BuildConfig generation
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // The journey of remote/data/domain/UI layer dependencies start here
    // Retrofit
    implementation(libs.retrofit)
    // Official Kotlin Serialization Converter
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.jetbrains.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // Logging HTTP raw network traffic in Logcat
    implementation(libs.logging.interceptor)
    // Jetpack Compose Navigation
    implementation(libs.androidx.navigation.compose)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}