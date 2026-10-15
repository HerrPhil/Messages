plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)

    // Hilt plugins to process annotations like @Module, @Provides, @Binds
    alias(libs.plugins.ksp)    // Applies KSP plugin
    alias(libs.plugins.hilt)   // Applies Hilt plugin
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

    buildFeatures {
        buildConfig = true // Added this to force generation of BuildConfig, for this library module
    }
    // THE ESSENTIAL ROBOLECTRIC HOOK:
    // This tells the Android Gradle Plugin to physically expose the simulated Android environment
    // variables to standard JUnit local unit tests running on the JVM.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true


            // THE RESOLUTION: Forces the mockable Android JAR to safely return
            // zero/null for any platform calls (like Log.d) invoked on foreign threads!
            isReturnDefaultValues = true

        }
    }

}

dependencies {

    // 1. Domain Layer Dependency
    implementation(project(":core:domain"))
    implementation(project(":core:database"))

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

    // 6. Hilt processing
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

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

    // Room Testing Configuration Engine
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.core)
    // THE RESOLUTION: Adds the simulated Android platform context to the local JVM!
    testImplementation(libs.robolectric) // Match your project's version baseline if using a catalog
    // Add this line if missing to ensure JUnit can resolve AndroidJUnit4
    testImplementation(libs.androidx.junit)
    // THE DRIVER HOOK: Supplies a self-contained SQLite engine to the JVM
    testImplementation(libs.room.runtime) // Ensure this matches your 2.8.5 catalog reference!
    // THE FIX: Feeds the missing C++/Java embedded SQLite binary files to your local JUnit runner!
    testImplementation(libs.androidx.sqlite.bundled)

    // -------------------------------------------------------------------------
    // Android Test Dependencies (:core:data)
    // -------------------------------------------------------------------------

    androidTestImplementation(libs.androidx.junit) // KEEP!!!! (Optional depending on testing strategy)

}