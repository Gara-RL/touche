package be.touche.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * Stockage 100 % local (aucun compte, aucun serveur en V1).
 * Tout est dans des fichiers JSON privés de l'app.
 * Partagé entre l'écran et le service NFC (même processus).
 */
object Store {
    private lateinit var dir: File
    private lateinit var prefs: android.content.SharedPreferences

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _encounters = MutableStateFlow<List<Encounter>>(emptyList())
    val encounters: StateFlow<List<Encounter>> = _encounters.asStateFlow()

    private val _activeProfileId = MutableStateFlow("")
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _sendBack = MutableStateFlow(true)
    /** En mode "Scanner", renvoyer aussi ma carte à l'autre téléphone ? */
    val sendBack: StateFlow<Boolean> = _sendBack.asStateFlow()

    /** Prévient l'écran dès qu'une carte arrive (depuis le lecteur OU le service NFC). */
    private val _received = MutableSharedFlow<Encounter>(extraBufferCapacity = 8)
    val received: SharedFlow<Encounter> = _received.asSharedFlow()

    @Volatile private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        dir = context.applicationContext.filesDir
        prefs = context.applicationContext.getSharedPreferences("touche", Context.MODE_PRIVATE)
        _profiles.value = readList(File(dir, "profiles.json")) { Profile.fromJson(it) }
            .ifEmpty { defaultProfiles().also { saveProfiles(it) } }
        _encounters.value = readList(File(dir, "encounters.json")) { Encounter.fromJson(it) }
        _activeProfileId.value = prefs.getString("active", null) ?: _profiles.value.first().id
        _sendBack.value = prefs.getBoolean("sendBack", true)
        initialized = true
    }

    fun activeProfile(): Profile? =
        _profiles.value.firstOrNull { it.id == _activeProfileId.value } ?: _profiles.value.firstOrNull()

    fun setActive(id: String) {
        _activeProfileId.value = id
        prefs.edit().putString("active", id).apply()
    }

    fun setSendBack(value: Boolean) {
        _sendBack.value = value
        prefs.edit().putBoolean("sendBack", value).apply()
    }

    @Synchronized
    fun upsertProfile(p: Profile) {
        val list = _profiles.value.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list += p
        saveProfiles(list)
    }

    @Synchronized
    fun deleteProfile(id: String) {
        val list = _profiles.value.filterNot { it.id == id }
        if (list.isEmpty()) return // on garde toujours au moins un profil
        saveProfiles(list)
        if (_activeProfileId.value == id) setActive(list.first().id)
    }

    @Synchronized
    fun addEncounter(e: Encounter) {
        saveEncounters(listOf(e) + _encounters.value)
        _received.tryEmit(e)
    }

    @Synchronized
    fun updateEncounter(e: Encounter) {
        saveEncounters(_encounters.value.map { if (it.id == e.id) e else it })
    }

    @Synchronized
    fun deleteEncounter(id: String) {
        saveEncounters(_encounters.value.filterNot { it.id == id })
    }

    private fun saveProfiles(list: List<Profile>) {
        _profiles.value = list
        writeList(File(dir, "profiles.json"), list.map { it.toJson() })
    }

    private fun saveEncounters(list: List<Encounter>) {
        _encounters.value = list
        writeList(File(dir, "encounters.json"), list.map { it.toJson() })
    }

    private fun <T> readList(file: File, parse: (org.json.JSONObject) -> T): List<T> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(parse) }
    }.getOrDefault(emptyList())

    private fun writeList(file: File, items: List<org.json.JSONObject>) {
        // Écriture atomique : on écrit dans un fichier temporaire puis on renomme
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray().apply { items.forEach { put(it) } }.toString())
        tmp.renameTo(file)
    }

    private fun defaultProfiles() = listOf(
        Profile.blank(
            "Pro", "🛠️",
            listOf(FieldType.NAME, FieldType.PHONE, FieldType.EMAIL, FieldType.ORG, FieldType.TITLE, FieldType.WEBSITE, FieldType.LINKEDIN),
        ),
        Profile.blank(
            "Esport", "🎮",
            listOf(FieldType.NAME, FieldType.DISCORD, FieldType.ORG, FieldType.TIKTOK, FieldType.INSTAGRAM),
        ),
        Profile.blank(
            "Perso", "😎",
            listOf(FieldType.NAME, FieldType.PHONE, FieldType.INSTAGRAM, FieldType.SNAPCHAT, FieldType.TIKTOK),
        ),
    )
}
