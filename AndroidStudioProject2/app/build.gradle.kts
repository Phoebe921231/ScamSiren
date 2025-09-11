import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}

val props = Properties().apply {
    val lp = rootProject.file("local.properties")
    if (lp.exists()) FileInputStream(lp).use { load(it) }
}
val urlscanKey: String = props.getProperty("URLSCAN_API_KEY", "")
val baseUrlDebug: String = props.getProperty("BASE_URL_DEBUG", "http://192.168.56.1:5000")
val baseUrlRelease: String = props.getProperty("BASE_URL_RELEASE", "https://example.com")

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

    buildFeatures { buildConfig = true }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"${baseUrlDebug}\"")
            buildConfigField("String", "URLSCAN_API_KEY", "\"${urlscanKey}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL", "\"${baseUrlRelease}\"")
            buildConfigField("String", "URLSCAN_API_KEY", "\"${urlscanKey}\"")
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
    implementation("com.google.mlkit:text-recognition:16.0.0-beta5")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0-beta5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

