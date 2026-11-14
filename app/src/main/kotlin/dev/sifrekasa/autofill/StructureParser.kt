package dev.sifrekasa.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** Ekrandaki oturum açma formundan çıkarılanlar. */
class ParsedForm(
    val packageName: String,
    /** Yapının bildirdiği alan adı; yoksa ya da birden fazla farklı alan adı varsa null. */
    val webDomain: String?,
    val usernameId: AutofillId?,
    val passwordIds: List<AutofillId>,
) {
    val allIds: List<AutofillId> get() = listOfNotNull(usernameId) + passwordIds
}

/** AssistStructure içinde kullanıcı adı ve şifre alanlarını bulur. Alanların içeriği okunmaz, saklanmaz. */
object StructureParser {
    private enum class Kind { USERNAME, PASSWORD, TEXT }
    private class Field(val id: AutofillId, val kind: Kind)

    private val usernameWords = listOf(
        "user", "email", "e-mail", "mail", "login", "account", "phone",
        "kullanici", "kullanıcı", "eposta", "e-posta", "hesap", "telefon", "kimlik",
    )

    fun parse(structure: AssistStructure): ParsedForm? {
        val fields = ArrayList<Field>()
        val domains = LinkedHashSet<String>()
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, fields, domains)
        }
        val passwords = fields.filter { it.kind == Kind.PASSWORD }
        val firstPassword = fields.indexOfFirst { it.kind == Kind.PASSWORD }
        // Kullanıcı adı: şifreden önceki açıkça kullanıcı adı olan alan; yoksa şifreden hemen önceki metin alanı.
        val before = if (firstPassword >= 0) fields.subList(0, firstPassword) else fields
        val username = before.lastOrNull { it.kind == Kind.USERNAME }
            ?: (if (firstPassword >= 0) before.lastOrNull { it.kind == Kind.TEXT } else null)
        if (passwords.isEmpty() && username == null) return null
        return ParsedForm(
            packageName = structure.activityComponent.packageName,
            webDomain = domains.singleOrNull(),
            usernameId = username?.id,
            passwordIds = passwords.map { it.id },
        )
    }

    private fun walk(node: AssistStructure.ViewNode, fields: MutableList<Field>, domains: MutableSet<String>) {
        node.webDomain?.takeIf { it.isNotBlank() }?.let { domains.add(it.lowercase()) }
        classify(node)?.let { kind -> node.autofillId?.let { fields.add(Field(it, kind)) } }
        for (i in 0 until node.childCount) walk(node.getChildAt(i), fields, domains)
    }

    private fun classify(node: AssistStructure.ViewNode): Kind? {
        if (node.autofillId == null || node.visibility != View.VISIBLE) return null
        val html = node.htmlInfo
        val htmlAttributes = html?.attributes?.associate { it.first.lowercase() to it.second.orEmpty().lowercase() }.orEmpty()
        val isHtmlInput = html?.tag.equals("input", ignoreCase = true)
        val htmlType = htmlAttributes["type"].orEmpty()
        if (isHtmlInput && htmlType in setOf("hidden", "submit", "button", "checkbox", "radio", "file", "image", "reset")) return null
        val isText = node.autofillType == View.AUTOFILL_TYPE_TEXT || isHtmlInput ||
            node.className?.contains("EditText") == true
        if (!isText) return null

        val hints = node.autofillHints.orEmpty().map { it.lowercase() }
        val autocomplete = htmlAttributes["autocomplete"].orEmpty()
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val klass = inputType and InputType.TYPE_MASK_CLASS

        val password = hints.any { "password" in it } || "password" in autocomplete || htmlType == "password" ||
            (klass == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            )) ||
            (klass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        if (password) return Kind.PASSWORD

        val described = listOfNotNull(node.idEntry, node.hint, htmlAttributes["name"], htmlAttributes["id"], htmlAttributes["placeholder"])
            .joinToString(" ").lowercase()
        val username = hints.any { h -> h == "username" || "email" in h || h == "phone" } ||
            autocomplete in setOf("username", "email") || htmlType == "email" ||
            (klass == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            )) ||
            usernameWords.any { it in described }
        return if (username) Kind.USERNAME else Kind.TEXT
    }
}
