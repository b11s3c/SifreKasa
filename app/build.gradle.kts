plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.sifrekasa"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.sifrekasa"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // :core testlerinde sınanan libsodium sarmalayıcısının aynısı (bkz. core/build.gradle.kts).
    sourceSets["main"].kotlin.directories.add("../core/src/sodium/kotlin")
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric 1.1.0 eski bir fragment sürümü getirir; o sürümde izin/sonuç başlatıcıları
    // "Can only use lower 16 bits for requestCode" hatasıyla çöker.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")

    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.19.1@aar")
}
