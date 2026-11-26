import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Sürüm imzası: anahtar deposunun yeri ve parolaları depoya girmeyen keystore.properties dosyasından okunur
// (örnek: keystore.properties.example). Dosya yoksa sürüm derlemesi imzasız üretilir.
val keystoreProperties = Properties().apply {
    val file = (findProperty("keystoreProperties") as String?)?.let(::file) ?: rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "dev.sifrekasa"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.sifrekasa"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Hata ayıklanamaz, küçültülmüş ve optimize edilmiş: günlük kullanım için olan derleme budur.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // libsodium'un sağladığı dört mimari. 32 bit ARM gerekli: bazı Android 14+ telefonlar (ör. Android Go)
            // 32 bit çalışır. JNA'nın artık kullanılmayan mimarileri (armeabi, mips) dışarıda kalır.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")

    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.19.1@aar")

    testImplementation("junit:junit:4.13.2")
}
