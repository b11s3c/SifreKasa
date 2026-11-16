# libsodium bağlayıcısı (Lazysodium) JNA üzerinden yansımayla çalışır: yerel yöntem adları ve
# imzaları korunmalıdır, yoksa Argon2id / XChaCha20 çağrıları çalışma zamanında bulunamaz.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.goterl.lazysodium.** { *; }
-dontwarn java.awt.**

# Tink'in bu uygulamada kullanılmayan isteğe bağlı parçalarının bağımlılıkları.
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
