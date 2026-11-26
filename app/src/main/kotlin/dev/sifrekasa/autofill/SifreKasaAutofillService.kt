package dev.sifrekasa.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.Presentations
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import dev.sifrekasa.R
import dev.sifrekasa.security.PrivilegedApps

/**
 * Telefondaki uygulama ve tarayıcılarda oturum açma formlarını doldurur.
 *
 * Servis kasaya hiç dokunmaz ve yanıtına hiçbir gizli veri koymaz: yalnızca "ŞifreKasa ile doldur"
 * seçeneğini döner. Kullanıcı ona dokununca [AutofillPickerActivity] açılır; kilit açma, kayıt seçimi
 * ve değerlerin forma verilmesi orada, kullanıcının gözü önünde olur.
 */
class SifreKasaAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val form = structure?.let(StructureParser::parse)
        if (form == null || form.packageName == packageName) {
            callback.onSuccess(null)
            return
        }
        // Alan adına yalnızca bilinen, imzası doğrulanmış bir tarayıcı bildiriyorsa güvenilir.
        val domain = form.webDomain?.takeIf { PrivilegedApps.isTrusted(this, form.packageName) }

        val intent = Intent(this, AutofillPickerActivity::class.java)
            .putExtra(AutofillPickerActivity.EXTRA_PACKAGE, form.packageName)
            .putExtra(AutofillPickerActivity.EXTRA_DOMAIN, domain)
            .putExtra(AutofillPickerActivity.EXTRA_USERNAME_ID, form.usernameId)
            .putParcelableArrayListExtra(AutofillPickerActivity.EXTRA_PASSWORD_IDS, ArrayList(form.passwordIds))
        // Sistem sonucu bu PendingIntent üzerinden beklediği için değiştirilebilir olmalı; hedef açıkça belirtilmiştir.
        val pending = PendingIntent.getActivity(
            this, request.id, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
        )
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_text, getString(R.string.autofill_prompt))
        }
        // Kimlik doğrulama veri kümesi düzeyindedir: seçim ekranının döndürdüğü veri kümesi doğrudan
        // forma uygulanır. (Yanıt düzeyinde doğrulamada sistem tek bir veri kümesini kabul etmez.)
        // Buradaki alanlar değer taşımaz; yalnızca hangi alanlar için öneri gösterileceğini belirtir.
        val placeholder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Dataset.Builder(Presentations.Builder().setMenuPresentation(presentation).build()).apply {
                form.allIds.forEach { setField(it, Field.Builder().build()) }
            }
        } else {
            @Suppress("DEPRECATION")
            Dataset.Builder(presentation).apply {
                form.allIds.forEach { setValue(it, null) }
            }
        }
        placeholder.setAuthentication(pending.intentSender)
        val response = FillResponse.Builder().addDataset(placeholder.build()).build()
        callback.onSuccess(response)
    }

    /** Yeni şifre kaydetme teklifi yapılmadığı için (SaveInfo yok) sistem bunu çağırmaz. */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) = callback.onSuccess()
}
