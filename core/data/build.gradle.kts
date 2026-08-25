plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.reference.implementation.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }

}

dependencies {

    // 1. Domain Layer Dependency
    implementation(project(":core:domain"))

    // 2. Android Core Extensions
    implementation(libs.androidx.core.ktx) // KEEP!!!!

    // 3. DataStore (user preferences)
    implementation(libs.androidx.datastore.preferences)

    // 4. Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.jetbrains.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // 5. Asynchronous Concurrency
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // -------------------------------------------------------------------------
    // Unit Test Dependencies (:core:data)
    // -------------------------------------------------------------------------

    // Standard Kotlin & JUnit 4 Test Runners
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))

    // Kotlin Coroutines Virtual Time & Dispatcher Control (StandardTestDispatcher, runTest)
    testImplementation(libs.kotlinx.coroutines.test)

    // Flow Assertion Framework (awaitItem, awaitComplete, etc.)
    testImplementation(libs.turbine)

    // MockWebServer for Retrofit/OkHttp Network Contract & HTTP 401 Testing
    testImplementation(libs.okhttp.mockwebserver)

    // MockK for Mocking Non-Network Components (DAOs, Storage Interfaces)
    testImplementation(libs.mockk)

    // Assertion Extensions (Google Truth or Kotlin Test extension functions)
    testImplementation(libs.google.truth)


    androidTestImplementation(libs.androidx.junit) // KEEP!!!! (Optional depending on testing strategy)


}