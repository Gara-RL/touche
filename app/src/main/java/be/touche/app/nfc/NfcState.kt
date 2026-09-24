package be.touche.app.nfc

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NfcAvailability { OK, DISABLED, NO_HCE, NONE }

/** État partagé entre l'écran et l'activité pour le mode "Scanner". */
object NfcState {
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setScanning(on: Boolean) {
        _scanning.value = on
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    fun availability(context: Context): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.NONE
        if (!adapter.isEnabled) return NfcAvailability.DISABLED
        val hce = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        return if (hce) NfcAvailability.OK else NfcAvailability.NO_HCE
    }
}
