package be.touche.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import be.touche.app.data.Store
import be.touche.app.nfc.NfcState
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { HOME("Partager"), PROFILES("Profils"), ENCOUNTERS("Rencontres") }

@Composable
fun ToucheApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openEncounter by rememberSaveable { mutableStateOf<String?>(null) }
    var editProfile by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    // Carte reçue (en scannant OU pendant que je montre ma carte) -> vibration + message
    LaunchedEffect(Unit) {
        Store.received.collect { e ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            launch {
                val r = snackbar.showSnackbar("Carte reçue : ${e.name}", actionLabel = "Voir")
                if (r == SnackbarResult.ActionPerformed) {
                    tab = Tab.ENCOUNTERS.ordinal
                    openEncounter = e.id
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        NfcState.messages.collect { launch { snackbar.showSnackbar(it) } }
    }

    BackHandler(enabled = openEncounter != null || editProfile != null) {
        openEncounter = null
        editProfile = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal; openEncounter = null; editProfile = null },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.HOME -> Icons.Default.Nfc
                                    Tab.PROFILES -> Icons.Default.Person
                                    Tab.ENCOUNTERS -> Icons.Default.Contacts
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (Tab.entries[tab]) {
            Tab.HOME -> HomeScreen(m)
            Tab.PROFILES -> {
                val id = editProfile
                if (id == null) ProfilesScreen(m, onEdit = { editProfile = it })
                else ProfileEditor(m, profileId = id, onDone = { editProfile = null })
            }
            Tab.ENCOUNTERS -> {
                val id = openEncounter
                if (id == null) EncountersScreen(m, onOpen = { openEncounter = it })
                else EncounterDetail(m, encounterId = id, onBack = { openEncounter = null })
            }
        }
    }
}
