plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.fp2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fp2"
        minSdk = 24
        targetSdk = 35
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // 🟢 ML Kit OCR
    implementation("com.google.mlkit:text-recognition:16.0.0-beta5")          // 英文、拉丁字母
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0-beta5") // 中文 OCR

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
