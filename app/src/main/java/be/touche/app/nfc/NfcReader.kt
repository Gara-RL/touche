package be.touche.app.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import be.touche.app.data.Encounter
import be.touche.app.data.Field
import be.touche.app.data.FieldType
import be.touche.app.data.Payload
import be.touche.app.data.Profile
import be.touche.app.data.VCard
import be.touche.app.data.Via

/** Résultat d'un scan. */
sealed class ReadResult {
    data class Success(val encounter: Encounter, val sentBack: Boolean) : ReadResult()
    data class Error(val message: String) : ReadResult()
}

/**
 * Mode "Scanner" : lit un téléphone Touché, un autre téléphone (vCard) ou une carte/sticker NFC.
 * Appelé sur un fil d'exécution NFC (pas sur l'écran) : on peut bloquer ici.
 */
object NfcReader {

    fun read(tag: Tag, myProfile: Profile?, sendBack: Boolean): ReadResult {
        // 1) Téléphone en émulation (Touché) ou tag Type 4 : on parle directement le protocole
        IsoDep.get(tag)?.let { iso ->
            try {
                iso.connect()
                iso.timeout = 3000
                val message = readType4(iso)
                if (message != null) {
                    val theirs = parse(message, Via.NFC)
                        ?: return ReadResult.Error("Carte illisible")
                    var sent = false
                    // L'autre a l'app ? Je lui renvoie ma carte dans la foulée.
                    if (theirs.second && sendBack && myProfile != null && myProfile.sharedFields.isNotEmpty()) {
                        sent = push(iso, Payload.encode(myProfile))
                    }
                    val e = if (sent) theirs.first.copy(via = Via.NFC_ECHANGE) else theirs.first
                    return ReadResult.Success(e, sent)
                }
            } catch (e: Exception) {
                // on tente la méthode classique juste après
            } finally {
                runCatching { iso.close() }
            }
        }

        // 2) Tag NFC classique (sticker, carte de visite NFC...) lu par Android
        Ndef.get(tag)?.let { ndef ->
            val message = ndef.cachedNdefMessage ?: runCatching {
                ndef.connect(); ndef.ndefMessage
            }.getOrNull().also { runCatching { ndef.close() } }
            if (message != null) {
                parse(message, Via.NFC)?.let { return ReadResult.Success(it.first, false) }
            }
        }
        return ReadResult.Error("Rien de lisible. Garde les téléphones dos à dos 2 secondes.")
    }

    /** Lecture d'un tag Type 4 : SELECT appli -> SELECT CC -> lecture CC -> SELECT NDEF -> lecture NDEF. */
    private fun readType4(iso: IsoDep): NdefMessage? {
        if (!Protocol.isOk(iso.transceive(Protocol.selectAid()))) return null
        if (!Protocol.isOk(iso.transceive(Protocol.selectFile(Protocol.CC_FILE_ID)))) return null
        val cc = iso.transceive(Protocol.readBinary(0, 15))
        if (!Protocol.isOk(cc) || cc.size < 17) return null
        val mle = ((cc[3].toInt() and 0xFF) shl 8 or (cc[4].toInt() and 0xFF)).coerceIn(1, 0xFF)
        val fileId = byteArrayOf(cc[9], cc[10])

        if (!Protocol.isOk(iso.transceive(Protocol.selectFile(fileId)))) return null
        val lenResp = iso.transceive(Protocol.readBinary(0, 2))
        if (!Protocol.isOk(lenResp) || lenResp.size < 4) return null
        val nlen = (lenResp[0].toInt() and 0xFF) shl 8 or (lenResp[1].toInt() and 0xFF)
        if (nlen == 0) return null

        val out = java.io.ByteArrayOutputStream()
        var offset = 2
        while (out.size() < nlen) {
            val n = minOf(mle, nlen - out.size())
            val r = iso.transceive(Protocol.readBinary(offset, n))
            if (!Protocol.isOk(r) || r.size <= 2) return null
            out.write(r, 0, r.size - 2)
            offset += r.size - 2
        }
        return NdefMessage(out.toByteArray().copyOf(nlen))
    }

    private fun push(iso: IsoDep, payload: ByteArray): Boolean = runCatching {
        Protocol.pushCommands(payload).all { Protocol.isOk(iso.transceive(it)) }
    }.getOrDefault(false)

    /**
     * Transforme un message NDEF en rencontre.
     * Retourne (rencontre, true si ça vient de l'app Touché).
     */
    fun parse(message: NdefMessage, via: Via): Pair<Encounter, Boolean>? {
        val records = message.records
        // Priorité au profil Touché (plus complet que la vCard)
        records.firstOrNull { isToucheRecord(it) }?.let { r ->
            Payload.decode(r.payload, via)?.let { return it to true }
        }
        records.firstOrNull { r ->
            r.tnf == NdefRecord.TNF_MIME_MEDIA && String(r.type, Charsets.US_ASCII).lowercase().let {
                it == "text/vcard" || it == "text/x-vcard"
            }
        }?.let { r ->
            VCard.parse(String(r.payload, Charsets.UTF_8), via)?.let { return it to false }
        }
        // Un simple lien (ex. carte de visite NFC qui ouvre une page web)
        records.firstNotNullOfOrNull { r ->
            r.toUri()?.takeIf { it.scheme in setOf("http", "https", "tel", "mailto") }
        }?.let { uri ->
            return Encounter(
                name = uri.host ?: "Lien",
                profileTitle = "Lien",
                fields = listOf(Field(FieldType.WEBSITE.key, "Lien", uri.toString())),
                via = via,
            ) to false
        }
        return null
    }

    private fun isToucheRecord(r: NdefRecord): Boolean =
        r.tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
            String(r.type, Charsets.US_ASCII).equals("${Protocol.EXT_DOMAIN}:${Protocol.EXT_TYPE}", ignoreCase = true)
}
