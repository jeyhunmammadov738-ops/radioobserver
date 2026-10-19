plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.radioobserver"
    compileSdk = 35

    defaultConfig {
        // Developer-approved permanent application ID (2026-10-19).
        // Deliberately different from namespace/source packages, which stay com.example.radioobserver;
        // a local build is NOT proof of store package-name availability.
        applicationId = "com.hjinlabs.radioobserver"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
