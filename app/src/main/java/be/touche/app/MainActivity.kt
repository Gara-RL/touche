package be.touche.app

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import be.touche.app.data.Store
import be.touche.app.nfc.NfcReader
import be.touche.app.nfc.NfcState
import be.touche.app.nfc.ReadResult
import be.touche.app.nfc.ShareState
import be.touche.app.nfc.ToucheHceService
import be.touche.app.ui.ToucheApp
import be.touche.app.ui.ToucheTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfc: NfcAdapter? = null
    private var readerOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        nfc = NfcAdapter.getDefaultAdapter(this)
        enableEdgeToEdge()
        setContent { ToucheTheme { ToucheApp() } }

        // Allume / éteint le lecteur NFC selon le bouton "Scanner", uniquement quand l'app est à l'écran
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                NfcState.scanning.collect { on -> if (on) startReader() else stopReader() }
            }
        }
        // Quand "Montrer ma carte" est actif, on demande à Android de nous donner la priorité NFC
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                ShareState.activeUntil.collect { preferHce(ShareState.isActive) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopReader()
        preferHce(false)
    }

    private fun startReader() {
        val adapter = nfc ?: return
        if (readerOn) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(this, this, flags, Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)
        })
        readerOn = true
    }

    private fun stopReader() {
        if (!readerOn) return
        runCatching { nfc?.disableReaderMode(this) }
        readerOn = false
    }

    private fun preferHce(on: Boolean) {
        val adapter = nfc ?: return
        runCatching {
            val ce = CardEmulation.getInstance(adapter)
            if (on) ce.setPreferredService(this, ComponentName(this, ToucheHceService::class.java))
            else ce.unsetPreferredService(this)
        }
    }

    /** Appelé par Android (hors fil principal) quand un tag ou un téléphone est détecté. */
    override fun onTagDiscovered(tag: Tag) {
        when (val r = NfcReader.read(tag, Store.activeProfile(), Store.sendBack.value)) {
            is ReadResult.Success -> {
                Store.addEncounter(r.encounter)
                NfcState.setScanning(false)
                if (r.sentBack) NfcState.message("Échange réussi : ta carte a aussi été envoyée")
            }
            is ReadResult.Error -> NfcState.message(r.message)
        }
    }
}
