plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.v.island"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.v.island"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { aidl = true }
}

dependencies {
    // The only dependency in the project: the microphone-access toggle needs a
    // shell UID, and Shizuku is the no-root way to get one.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
