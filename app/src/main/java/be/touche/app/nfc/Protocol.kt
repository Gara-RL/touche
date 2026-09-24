package be.touche.app.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import be.touche.app.data.Payload
import be.touche.app.data.Profile
import be.touche.app.data.VCard

/**
 * Le protocole Touché, par-dessus la norme "NFC Forum Type 4 Tag".
 *
 * 1. Le téléphone qui "montre sa carte" imite un tag NFC contenant un message NDEF :
 *      - enregistrement 1 : vCard (text/vcard)  -> lisible par N'IMPORTE QUEL téléphone
 *      - enregistrement 2 : profil Touché (JSON) -> lu uniquement par l'app
 * 2. Le téléphone qui "scanne" lit ce tag. S'il voit l'enregistrement Touché, il sait que
 *    l'autre a l'app et lui RENVOIE sa propre carte avec une commande propriétaire (PUSH),
 *    découpée en morceaux. Résultat : échange dans les deux sens en un seul contact.
 */
object Protocol {
    /** AID standard NDEF Type 4 (ne pas changer : c'est lui que les iPhone/Android cherchent). */
    val NDEF_AID = hex("D2760000850101")
    val CC_FILE_ID = hex("E103")
    val NDEF_FILE_ID = hex("E104")

    const val EXT_DOMAIN = "be.touche"
    const val EXT_TYPE = "profile"

    /** Commande propriétaire PUSH : CLA=0x80, INS=0xE1, P1=flags (bit0 = dernier morceau), P2=n° du morceau. */
    const val PUSH_CLA: Byte = 0x80.toByte()
    const val PUSH_INS: Byte = 0xE1.toByte()
    const val PUSH_LAST: Byte = 0x01
    const val PUSH_CHUNK = 200

    /** Taille maximale de réponse annoncée (MLe). */
    const val MAX_LE = 0xF0

    val SW_OK = hex("9000")
    val SW_NOT_FOUND = hex("6A82")
    val SW_WRONG_PARAMS = hex("6B00")
    val SW_INS_NOT_SUPPORTED = hex("6D00")
    val SW_CONDITIONS = hex("6985")

    /** Message NDEF qui sera "affiché" par le téléphone émetteur. */
    fun buildNdef(profile: Profile): NdefMessage {
        val vcard = NdefRecord.createMime("text/vcard", VCard.build(profile).toByteArray(Charsets.UTF_8))
        val app = NdefRecord.createExternal(EXT_DOMAIN, EXT_TYPE, Payload.encode(profile))
        return NdefMessage(arrayOf(vcard, app))
    }

    /** Contenu du fichier NDEF d'un tag Type 4 : 2 octets de longueur (NLEN) + le message. */
    fun ndefFile(message: NdefMessage): ByteArray {
        val bytes = message.toByteArray()
        return byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes
    }

    /** Capability Container (15 octets) : décrit le fichier NDEF au lecteur. */
    fun capabilityContainer(ndefFileSize: Int): ByteArray {
        val max = ndefFileSize.coerceAtLeast(0x0F).coerceAtMost(0x7FFF)
        return byteArrayOf(
            0x00, 0x0F,                                   // CCLEN = 15
            0x20,                                         // version 2.0
            (MAX_LE shr 8).toByte(), MAX_LE.toByte(),     // MLe : taille max d'une lecture
            0x00, 0xFF.toByte(),                          // MLc : taille max d'une écriture
            0x04, 0x06,                                   // TLV "NDEF File Control", longueur 6
            NDEF_FILE_ID[0], NDEF_FILE_ID[1],             // identifiant du fichier NDEF
            (max shr 8).toByte(), max.toByte(),           // taille max du fichier NDEF
            0x00,                                         // lecture : libre
            0xFF.toByte(),                                // écriture : interdite
        )
    }

    // ---------- Commandes côté lecteur ----------

    fun selectAid(): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, NDEF_AID.size.toByte()) + NDEF_AID + byteArrayOf(0x00)

    fun selectFile(fileId: ByteArray): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02) + fileId

    fun readBinary(offset: Int, length: Int): ByteArray =
        byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), length.toByte())

    /** Découpe ma carte en commandes PUSH. */
    fun pushCommands(payload: ByteArray): List<ByteArray> {
        val chunks = payload.toList().chunked(PUSH_CHUNK).map { it.toByteArray() }
        require(chunks.size <= 255) { "Carte trop grande" }
        return chunks.mapIndexed { i, chunk ->
            val flags: Byte = if (i == chunks.lastIndex) PUSH_LAST else 0.toByte()
            byteArrayOf(PUSH_CLA, PUSH_INS, flags, i.toByte(), chunk.size.toByte()) + chunk
        }
    }

    fun isOk(response: ByteArray?): Boolean =
        response != null && response.size >= 2 &&
            response[response.size - 2] == 0x90.toByte() && response[response.size - 1] == 0x00.toByte()

    fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
