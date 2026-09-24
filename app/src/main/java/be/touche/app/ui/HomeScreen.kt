package be.touche.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.touche.app.data.Profile
import be.touche.app.data.Store
import be.touche.app.data.VCard
import be.touche.app.nfc.NfcAvailability
import be.touche.app.nfc.NfcState
import be.touche.app.nfc.ShareState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profiles by Store.profiles.collectAsState()
    val activeId by Store.activeProfileId.collectAsState()
    val sendBack by Store.sendBack.collectAsState()
    val scanning by NfcState.scanning.collectAsState()
    val activeUntil by ShareState.activeUntil.collectAsState()
    val profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull() ?: return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showQr by remember { mutableStateOf(false) }
    var availability by remember { mutableStateOf(NfcState.availability(context)) }

    val sharing = activeUntil > now

    // Petite horloge pour le compte à rebours, et re-vérification du NFC (si activé dans les réglages)
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            availability = NfcState.availability(context)
            delay(1000)
        }
    }
    // Si je change de profil pendant le partage, la carte montrée suit
    LaunchedEffect(profile) {
        if (ShareState.isActive) ShareState.start(profile)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Touché", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        NfcBanner(availability) { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }

        // Choix du profil
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            profiles.forEach { p ->
                FilterChip(
                    selected = p.id == profile.id,
                    onClick = { Store.setActive(p.id) },
                    label = { Text("${p.emoji} ${p.title}") },
                )
            }
        }

        CardPreview(profile)

        val empty = profile.sharedFields.isEmpty()
        if (empty) {
            Text(
                "Ton profil « ${profile.title} » est vide. Remplis-le dans l'onglet Profils.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ---- Mode 1 : montrer ma carte ----
        if (sharing) {
            ActiveBox(
                title = "Ta carte est prête",
                text = "Colle ton téléphone dos à dos avec l'autre. Il peut te lire avec Touché ou directement (Android).\n" +
                    "Arrêt automatique dans ${((activeUntil - now) / 1000).coerceAtLeast(0)} s",
                onStop = { ShareState.stop() },
            )
        } else {
            Button(
                onClick = { NfcState.setScanning(false); ShareState.start(profile) },
                enabled = !empty && availability == NfcAvailability.OK,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Nfc, null)
                Spacer(Modifier.width(8.dp))
                Text("Montrer ma carte")
            }
        }

        // ---- Mode 2 : scanner l'autre ----
        if (scanning) {
            ActiveBox(
                title = "Scan en cours…",
                text = "Approche le dos de ton téléphone de l'autre téléphone ou d'une carte NFC.",
                onStop = { NfcState.setScanning(false) },
            )
        } else {
            OutlinedButton(
                onClick = { ShareState.stop(); NfcState.setScanning(true) },
                enabled = availability == NfcAvailability.OK || availability == NfcAvailability.NO_HCE,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Sensors, null)
                Spacer(Modifier.width(8.dp))
                Text("Scanner une carte")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Échange automatique", fontWeight = FontWeight.SemiBold)
                Text(
                    "Quand je scanne quelqu'un qui a Touché, il reçoit aussi ma carte.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = sendBack, onCheckedChange = { Store.setSendBack(it) })
        }

        // ---- Secours : QR code (iPhone, pas de NFC) ----
        TextButton(onClick = { showQr = true }, enabled = !empty, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.QrCode, null)
            Spacer(Modifier.width(8.dp))
            Text("Afficher mon QR code (iPhone, pas de NFC)")
        }
    }

    if (showQr) QrDialog(profile) { showQr = false }
}

@Composable
private fun NfcBanner(a: NfcAvailability, openSettings: () -> Unit) {
    val text = when (a) {
        NfcAvailability.OK -> return
        NfcAvailability.DISABLED -> "Le NFC est désactivé."
        NfcAvailability.NO_HCE -> "Ton téléphone peut scanner, mais pas montrer sa carte en NFC. Utilise le QR code pour partager."
        NfcAvailability.NONE -> "Pas de NFC sur ce téléphone : utilise le QR code."
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            if (a == NfcAvailability.DISABLED) TextButton(onClick = openSettings) { Text("Activer") }
        }
    }
}

@Composable
private fun CardPreview(profile: Profile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${profile.emoji}  ${profile.title}", style = MaterialTheme.typography.labelLarge)
            Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val shown = profile.sharedFields.filter { it.key != "name" }
            if (shown.isEmpty()) Text("Aucune info partagée pour l'instant", style = MaterialTheme.typography.bodyMedium)
            shown.forEach { f ->
                Row {
                    Text("${f.label} : ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(f.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val hidden = profile.fields.count { !it.shared && it.value.isNotBlank() }
            if (hidden > 0) {
                Text("🔒 $hidden info(s) masquée(s)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ActiveBox(title: String, text: String, onStop: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
    )
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp).scale(pulse)) {}
                Icon(Icons.Default.Nfc, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onStop) { Text("Arrêter") }
        }
    }
}

@Composable
private fun QrDialog(profile: Profile, onDismiss: () -> Unit) {
    val bitmap = remember(profile) { Qr.bitmap(VCard.build(profile)).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("${profile.emoji} ${profile.title}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap, contentDescription = "QR code de ta carte", modifier = Modifier.size(260.dp))
                Spacer(Modifier.height(8.dp))
                Text("L'autre scanne avec son appareil photo et ajoute ton contact.")
            }
        },
    )
}
