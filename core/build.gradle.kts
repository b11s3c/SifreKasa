plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
    // libsodium tabanlı CryptoEngine uygulaması hem bu modülün testlerinde (lazysodium-java)
    // hem de :app içinde (lazysodium-android) aynı kaynaktan derlenir.
    sourceSets["test"].kotlin.srcDir("src/sodium/kotlin")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("com.google.zxing:core:3.5.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.goterl:lazysodium-java:5.1.4")
    testImplementation("net.java.dev.jna:jna:5.19.1")
    // Yalnızca testlerde: libsodium çıktısını bağımsız uygulamalarla çapraz doğrulamak için.
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.87")
    testImplementation("com.google.crypto.tink:tink:1.24.0")
}

// Gerçek bir dışa aktarım dosyasını bağımsız kütüphanelerle açmayı denemek için:
//   ./gradlew :core:test --tests '*ExternalFileInteropTest*' -Pskx.file=/yol/dosya.skx -Pskx.password=parola
tasks.test {
    listOf("skx.file", "skx.password").forEach { name ->
        (findProperty(name) as String?)?.let { systemProperty(name, it) }
    }
    if (hasProperty("skx.file")) outputs.upToDateWhen { false }
}
