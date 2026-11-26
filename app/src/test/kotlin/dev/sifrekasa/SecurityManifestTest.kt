package dev.sifrekasa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Güvenlik ve Google Play politika değişmezlerinin (bkz. docs/project/MEMORY.md §3)
 * yapılandırma dosyalarında korunduğunu doğrulayan gerileme testleri.
 */
class SecurityManifestTest {

    private fun appFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Dosya bulunamadı: $relativePath (cwd=${File(".").absolutePath})")
    }

    @Test
    fun `manifest enforces no backup and minimal permissions`() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()

        assertTrue("allowBackup false olmalı", manifest.contains("""android:allowBackup="false""""))
        assertTrue("fullBackupContent false olmalı", manifest.contains("""android:fullBackupContent="false""""))
        assertFalse("QUERY_ALL_PACKAGES asla eklenmemeli", manifest.contains("QUERY_ALL_PACKAGES"))

        val declaredPermissions = Regex("""<uses-permission\s+android:name="([^"]+)"\s*/>""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(
            setOf("android.permission.INTERNET", "android.permission.CAMERA"),
            declaredPermissions,
        )
    }

    @Test
    fun `manifest queries stay synchronized with privileged_apps json`() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        val privilegedJson = appFile("src/main/assets/privileged_apps.json").readText()

        val queriedPackages = Regex("""<package\s+android:name="([^"]+)"\s*/>""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toSet()

        val privilegedPackages = Regex(""""package_name"\s*:\s*"([^"]+)"""")
            .findAll(privilegedJson)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("privileged_apps.json boş olmamalı", privilegedPackages.isNotEmpty())
        assertEquals(
            "Manifest <queries> listesi privileged_apps.json ile birebir uyumlu olmalı",
            privilegedPackages,
            queriedPackages,
        )
    }

    @Test
    fun `data extraction rules exclude cloud backup and device transfer`() {
        val rules = appFile("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(rules.contains("<cloud-backup>"))
        assertTrue(rules.contains("<device-transfer>"))
        assertTrue(rules.contains("""<exclude domain="root" path="." />"""))
    }
}
