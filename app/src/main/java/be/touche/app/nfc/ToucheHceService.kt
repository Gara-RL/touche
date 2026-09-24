package be.touche.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import be.touche.app.data.Payload
import be.touche.app.data.Profile
import be.touche.app.data.Store
import be.touche.app.data.Via
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * État du mode "Montrer ma carte".
 * Confidentialité : le téléphone ne répond QUE si l'utilisateur a appuyé sur le bouton,
 * et seulement pendant un temps limité. Sinon, personne ne peut lire la carte à son insu.
 */
object ShareState {
    const val DURATION_MS = 90_000L

    @Volatile
    var ndefFile: ByteArray = ByteArray(0)
        private set

    @Volatile
    var cc: ByteArray = ByteArray(0)
        private set

    private val _activeUntil = MutableStateFlow(0L)
    val activeUntil: StateFlow<Long> = _activeUntil.asStateFlow()

    val isActive: Boolean get() = System.currentTimeMillis() < _activeUntil.value

    fun start(profile: Profile) {
        ndefFile = Protocol.ndefFile(Protocol.buildNdef(profile))
        cc = Protocol.capabilityContainer(ndefFile.size)
        _activeUntil.value = System.currentTimeMillis() + DURATION_MS
    }

    fun stop() {
        _activeUntil.value = 0L
    }
}

class ToucheHceService : HostApduService() {

    private enum class Selected { NONE, CC, NDEF }

    private var appSelected = false
    private var selected = Selected.NONE
    private val pushBuffer = ByteArrayOutputStream()
    private var expectedChunk = 0

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        if (apdu.size < 4) return Protocol.SW_WRONG_PARAMS
        if (!ShareState.isActive) return Protocol.SW_NOT_FOUND

        val cla = apdu[0]
        val ins = apdu[1]
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF

        return when {
            // SELECT par AID (0x04) ou par identifiant de fichier (0x00)
            ins == 0xA4.toByte() && p1 == 0x04 -> {
                val aid = data(apdu)
                if (aid.contentEquals(Protocol.NDEF_AID)) {
                    appSelected = true
                    selected = Selected.NONE
                    resetPush()
                    Protocol.SW_OK
                } else Protocol.SW_NOT_FOUND
            }
            ins == 0xA4.toByte() && p1 == 0x00 -> {
                if (!appSelected) return Protocol.SW_NOT_FOUND
                val id = data(apdu)
                selected = when {
                    id.contentEquals(Protocol.CC_FILE_ID) -> Selected.CC
                    id.contentEquals(Protocol.NDEF_FILE_ID) -> Selected.NDEF
                    else -> return Protocol.SW_NOT_FOUND
                }
                Protocol.SW_OK
            }
            // READ BINARY
            ins == 0xB0.toByte() -> {
                val file = when (selected) {
                    Selected.CC -> ShareState.cc
                    Selected.NDEF -> ShareState.ndefFile
                    Selected.NONE -> return Protocol.SW_CONDITIONS
                }
                val offset = (p1 shl 8) or p2
                val le = if (apdu.size >= 5) (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
                if (offset > file.size) return Protocol.SW_WRONG_PARAMS
                val end = minOf(file.size, offset + minOf(le, Protocol.MAX_LE))
                file.copyOfRange(offset, end) + Protocol.SW_OK
            }
            // PUSH : l'autre téléphone me renvoie SA carte
            cla == Protocol.PUSH_CLA && ins == Protocol.PUSH_INS -> handlePush(apdu, p1, p2)
            else -> Protocol.SW_INS_NOT_SUPPORTED
        }
    }

    private fun handlePush(apdu: ByteArray, flags: Int, index: Int): ByteArray {
        if (!appSelected) return Protocol.SW_CONDITIONS
        if (index == 0) resetPush()
        if (index != expectedChunk) {
            resetPush()
            return Protocol.SW_WRONG_PARAMS
        }
        val chunk = data(apdu)
        pushBuffer.write(chunk)
        expectedChunk++
        if (pushBuffer.size() > 32_000) {
            resetPush()
            return Protocol.SW_WRONG_PARAMS
        }
        if (flags and Protocol.PUSH_LAST.toInt() != 0) {
            val encounter = Payload.decode(pushBuffer.toByteArray(), Via.NFC_ECHANGE)
            resetPush()
            if (encounter == null) return Protocol.SW_WRONG_PARAMS
            Store.addEncounter(encounter)
        }
        return Protocol.SW_OK
    }

    private fun resetPush() {
        pushBuffer.reset()
        expectedChunk = 0
    }

    /** Extrait les données d'une APDU courte : CLA INS P1 P2 Lc [données] [Le]. */
    private fun data(apdu: ByteArray): ByteArray {
        if (apdu.size < 5) return ByteArray(0)
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return ByteArray(0)
        return apdu.copyOfRange(5, 5 + lc)
    }

    override fun onDeactivated(reason: Int) {
        appSelected = false
        selected = Selected.NONE
    }
}
