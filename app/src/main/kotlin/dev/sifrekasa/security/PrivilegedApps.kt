package dev.sifrekasa.security

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Başka siteler adına istekte bulunabilen, güvenilen uygulamalar (tarayıcılar ve Play Services).
 * Liste Google Password Manager'ın açık listesidir (assets/privileged_apps.json); uygulamayla birlikte
 * gelir, çalışma zamanında indirilmez.
 */
object PrivilegedApps {
    @Volatile
    private var cachedJson: String? = null

    @Volatile
    private var cachedFingerprints: Map<String, Set<String>>? = null

    /** `CallingAppInfo.getOrigin()` için ham JSON. */
    fun allowlistJson(context: Context): String =
        cachedJson ?: context.assets.open("privileged_apps.json").bufferedReader().use { it.readText() }.also { cachedJson = it }

    /**
     * [packageName] listede mi ve kurulu paketin imza sertifikası listedeki parmak izlerinden biriyle
     * eşleşiyor mu? Yalnızca paket adına güvenilmez: aynı adı taşıyan sahte bir uygulama reddedilir.
     */
    fun isTrusted(context: Context, packageName: String): Boolean {
        val expected = fingerprints(context)[packageName] ?: return false
        val signers = try {
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return false
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
        return signers.orEmpty().any { fingerprint(it.toByteArray()) in expected }
    }

    private fun fingerprints(context: Context): Map<String, Set<String>> {
        cachedFingerprints?.let { return it }
        val result = HashMap<String, MutableSet<String>>()
        val apps = JSONObject(allowlistJson(context)).getJSONArray("apps")
        for (i in 0 until apps.length()) {
            val info = apps.getJSONObject(i).optJSONObject("info") ?: continue
            val signatures = info.optJSONArray("signatures") ?: continue
            val set = result.getOrPut(info.optString("package_name")) { HashSet() }
            for (j in 0 until signatures.length()) {
                // Yalnızca yayın (release) imzaları; "userdebug" yapıları için listelenenler kabul edilmez.
                val signature = signatures.getJSONObject(j)
                if (signature.optString("build") == "release") set.add(signature.optString("cert_fingerprint_sha256").uppercase())
            }
        }
        return result.also { cachedFingerprints = it }
    }

    private fun fingerprint(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(":") { "%02X".format(it) }
}
