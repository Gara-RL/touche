package be.touche.app.data

/**
 * vCard 3.0 : le format "carte de contact" que tous les téléphones comprennent
 * (Android, iPhone, Contacts Google...). C'est ce qui rend l'app universelle.
 */
object VCard {

    fun build(profile: Profile): String {
        val f = profile.sharedFields.associateBy { it.key }
        fun v(t: FieldType) = f[t.key]?.value?.trim().orEmpty()

        val name = v(FieldType.NAME).ifBlank { "Contact Touché" }
        val parts = name.split(" ", limit = 2)
        val first = parts.getOrElse(0) { "" }
        val last = parts.getOrElse(1) { "" }

        val notes = mutableListOf<String>()
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("N:${esc(last)};${esc(first)};;;")
            appendLine("FN:${esc(name)}")
            v(FieldType.PHONE).takeIf { it.isNotEmpty() }?.let { appendLine("TEL;TYPE=CELL:${esc(it)}") }
            v(FieldType.EMAIL).takeIf { it.isNotEmpty() }?.let { appendLine("EMAIL:${esc(it)}") }
            v(FieldType.ORG).takeIf { it.isNotEmpty() }?.let { appendLine("ORG:${esc(it)}") }
            v(FieldType.TITLE).takeIf { it.isNotEmpty() }?.let { appendLine("TITLE:${esc(it)}") }
            v(FieldType.WEBSITE).takeIf { it.isNotEmpty() }?.let { appendLine("URL:${esc(it)}") }
            for (t in listOf(FieldType.INSTAGRAM, FieldType.TIKTOK, FieldType.SNAPCHAT, FieldType.LINKEDIN)) {
                val value = v(t)
                if (value.isNotEmpty()) {
                    val url = socialUrl(t, value)
                    appendLine("X-SOCIALPROFILE;TYPE=${t.key}:${esc(url)}")
                    notes += "${t.label} : $value"
                }
            }
            v(FieldType.DISCORD).takeIf { it.isNotEmpty() }?.let { notes += "Discord : $it" }
            v(FieldType.NOTE).takeIf { it.isNotEmpty() }?.let { notes += it }
            // Les réseaux sont aussi mis dans la note : certaines apps Contacts ignorent X-SOCIALPROFILE
            if (notes.isNotEmpty()) appendLine("NOTE:${esc(notes.joinToString("\n"))}")
            appendLine("END:VCARD")
        }.replace("\n", "\r\n")
    }

    /** Lit une vCard reçue d'un téléphone ou d'une carte NFC qui n'a pas l'app. */
    fun parse(text: String, via: Via): Encounter? {
        if (!text.contains("BEGIN:VCARD", ignoreCase = true)) return null
        // "Déplie" les lignes coupées (une ligne qui commence par un espace continue la précédente)
        val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").lines()
        var name = ""
        val fields = mutableListOf<Field>()
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val prop = line.substring(0, idx).substringBefore(';').uppercase()
            val value = unesc(line.substring(idx + 1)).trim()
            if (value.isEmpty()) continue
            when (prop) {
                "FN" -> name = value
                "TEL" -> fields += Field(FieldType.PHONE.key, FieldType.PHONE.label, value)
                "EMAIL" -> fields += Field(FieldType.EMAIL.key, FieldType.EMAIL.label, value)
                "ORG" -> fields += Field(FieldType.ORG.key, FieldType.ORG.label, value.trimEnd(';'))
                "TITLE" -> fields += Field(FieldType.TITLE.key, FieldType.TITLE.label, value)
                "URL" -> fields += Field(FieldType.WEBSITE.key, FieldType.WEBSITE.label, value)
                "NOTE" -> fields += Field(FieldType.NOTE.key, FieldType.NOTE.label, value)
            }
        }
        if (name.isEmpty() && fields.isEmpty()) return null
        return Encounter(name = name.ifEmpty { "Inconnu" }, profileTitle = "vCard", fields = fields, via = via)
    }

    fun socialUrl(t: FieldType, raw: String): String {
        if (raw.startsWith("http")) return raw
        val handle = raw.removePrefix("@")
        return when (t) {
            FieldType.INSTAGRAM -> "https://instagram.com/$handle"
            FieldType.TIKTOK -> "https://www.tiktok.com/@$handle"
            FieldType.SNAPCHAT -> "https://www.snapchat.com/add/$handle"
            FieldType.LINKEDIN -> "https://www.linkedin.com/in/$handle"
            else -> raw
        }
    }

    private fun esc(s: String) = s
        .replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun unesc(s: String) = s
        .replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
