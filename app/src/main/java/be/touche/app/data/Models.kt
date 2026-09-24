package be.touche.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Types de champs connus. `key` sert pour la vCard et les icônes. */
enum class FieldType(val key: String, val label: String, val hint: String) {
    NAME("name", "Nom", "Prénom Nom"),
    PHONE("phone", "Téléphone", "+32 470 00 00 00"),
    EMAIL("email", "E-mail", "toi@exemple.be"),
    ORG("org", "Entreprise / équipe", "Nom de l'entreprise"),
    TITLE("title", "Fonction", "Menuisier, joueur, ..."),
    WEBSITE("website", "Site web", "https://..."),
    INSTAGRAM("instagram", "Instagram", "@pseudo"),
    TIKTOK("tiktok", "TikTok", "@pseudo"),
    SNAPCHAT("snapchat", "Snapchat", "pseudo"),
    DISCORD("discord", "Discord", "pseudo"),
    LINKEDIN("linkedin", "LinkedIn", "lien du profil"),
    NOTE("note", "Petit mot", "Un message pour la personne");

    companion object {
        fun fromKey(key: String): FieldType? = entries.firstOrNull { it.key == key }
    }
}

/** Un champ d'un profil. `shared` = la personne a coché qu'il peut être partagé. */
data class Field(
    val key: String,
    val label: String,
    val value: String,
    val shared: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("k", key).put("l", label).put("v", value).put("s", shared)

    companion object {
        fun fromJson(o: JSONObject) = Field(
            key = o.optString("k"),
            label = o.optString("l"),
            value = o.optString("v"),
            shared = o.optBoolean("s", true),
        )
    }
}

/** Un profil contextuel : Pro, Esport, Perso... */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val emoji: String,
    val fields: List<Field>,
) {
    /** Uniquement les champs remplis ET cochés "partager". */
    val sharedFields: List<Field> get() = fields.filter { it.shared && it.value.isNotBlank() }

    val displayName: String
        get() = fields.firstOrNull { it.key == FieldType.NAME.key }?.value?.takeIf { it.isNotBlank() } ?: "Sans nom"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("emoji", emoji)
        .put("fields", JSONArray().apply { fields.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject) = Profile(
            id = o.optString("id", UUID.randomUUID().toString()),
            title = o.optString("title"),
            emoji = o.optString("emoji", "👤"),
            fields = o.optJSONArray("fields").toFieldList(),
        )

        fun blank(title: String, emoji: String, keys: List<FieldType>) = Profile(
            title = title,
            emoji = emoji,
            fields = keys.map { Field(it.key, it.label, "") },
        )
    }
}

enum class Via { NFC, NFC_ECHANGE, QR }

/** Une rencontre = une carte reçue, avec la date et une note perso. */
data class Encounter(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val name: String,
    val profileTitle: String,
    val fields: List<Field>,
    val via: Via,
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("ts", timestamp).put("name", name).put("pt", profileTitle)
        .put("via", via.name).put("note", note)
        .put("fields", JSONArray().apply { fields.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject) = Encounter(
            id = o.optString("id"),
            timestamp = o.optLong("ts"),
            name = o.optString("name"),
            profileTitle = o.optString("pt"),
            fields = o.optJSONArray("fields").toFieldList(),
            via = runCatching { Via.valueOf(o.optString("via")) }.getOrDefault(Via.NFC),
            note = o.optString("note"),
        )
    }
}

/**
 * Ce qui voyage réellement entre deux téléphones : seulement les champs partagés.
 * Format JSON compact et versionné pour pouvoir évoluer sans casser les anciennes versions.
 */
object Payload {
    const val VERSION = 1

    fun encode(profile: Profile): ByteArray = JSONObject()
        .put("v", VERSION)
        .put("app", "touche")
        .put("pt", profile.title)
        .put("f", JSONArray().apply {
            profile.sharedFields.forEach { put(JSONObject().put("k", it.key).put("l", it.label).put("v", it.value)) }
        })
        .toString().toByteArray(Charsets.UTF_8)

    /** Retourne null si les données ne viennent pas de Touché ou sont illisibles. */
    fun decode(bytes: ByteArray, via: Via): Encounter? {
        val o = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return null
        if (o.optString("app") != "touche") return null
        val fields = o.optJSONArray("f").toFieldList()
        return Encounter(
            name = fields.firstOrNull { it.key == FieldType.NAME.key }?.value ?: "Inconnu",
            profileTitle = o.optString("pt"),
            fields = fields.filter { it.key != FieldType.NAME.key },
            via = via,
        )
    }
}

private fun JSONArray?.toFieldList(): List<Field> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { Field.fromJson(it) } }
}
