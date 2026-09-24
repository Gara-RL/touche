package be.touche.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import be.touche.app.data.Encounter
import be.touche.app.data.Field
import be.touche.app.data.FieldType
import be.touche.app.data.Store
import be.touche.app.data.VCard
import be.touche.app.data.Via
import java.text.DateFormat
import java.util.Date

@Composable
fun EncountersScreen(modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val encounters by Store.encounters.collectAsState()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Rencontres",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (encounters.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Aucune carte reçue pour l'instant. Scanne quelqu'un depuis l'onglet Partager !")
            }
        }
        items(encounters, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(e.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${formatDate(e.timestamp)} · ${e.profileTitle}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (e.note.isNotBlank()) {
                            Text("📝 ${e.note}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    if (e.via == Via.NFC_ECHANGE) Icon(Icons.Default.SwapHoriz, "Échange dans les deux sens")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterDetail(modifier: Modifier = Modifier, encounterId: String, onBack: () -> Unit) {
    val encounters by Store.encounters.collectAsState()
    val e = encounters.firstOrNull { it.id == encounterId }
    if (e == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    var note by remember(e.id) { mutableStateOf(e.note) }

    // La note est enregistrée automatiquement quand on quitte l'écran
    androidx.compose.runtime.DisposableEffect(e.id) {
        onDispose {
            Store.encounters.value.firstOrNull { it.id == e.id }?.let {
                if (it.note != note) Store.updateEncounter(it.copy(note = note))
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(e.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            actions = {
                IconButton(onClick = { Store.deleteEncounter(e.id); onBack() }) {
                    Icon(Icons.Default.Delete, "Supprimer")
                }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val how = when (e.via) {
                Via.NFC_ECHANGE -> "Échange NFC dans les deux sens"
                Via.NFC -> "Reçu par NFC"
                Via.QR -> "Reçu par QR code"
            }
            Text("${formatDate(e.timestamp)} · ${e.profileTitle} · $how", style = MaterialTheme.typography.bodySmall)

            e.fields.forEach { f -> FieldLine(f) { openField(context, f) } }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ma note (où, quoi, à relancer...)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = { runCatching { context.startActivity(addToContactsIntent(e, note)) } },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Icon(Icons.Default.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Ajouter à mes contacts")
        }
    }
}

@Composable
private fun FieldLine(f: Field, onClick: () -> Unit) {
    val clickable = f.key != FieldType.NOTE.key && f.key != FieldType.ORG.key && f.key != FieldType.TITLE.key
    Column(if (clickable) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(f.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            f.value,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (clickable) TextDecoration.Underline else null,
        )
    }
}

private fun formatDate(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

/** Tape sur une info : appeler, écrire, ouvrir le profil Instagram... */
private fun openField(context: Context, f: Field) {
    val type = FieldType.fromKey(f.key) ?: return
    val uri = when (type) {
        FieldType.PHONE -> Uri.parse("tel:${f.value.replace(" ", "")}")
        FieldType.EMAIL -> Uri.parse("mailto:${f.value}")
        FieldType.INSTAGRAM, FieldType.TIKTOK, FieldType.SNAPCHAT, FieldType.LINKEDIN ->
            Uri.parse(VCard.socialUrl(type, f.value))
        FieldType.WEBSITE -> Uri.parse(if (f.value.startsWith("http")) f.value else "https://${f.value}")
        FieldType.DISCORD -> {
            // Discord n'a pas de lien de profil par pseudo : on copie le pseudo
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Discord", f.value))
            return
        }
        else -> return
    }
    val action = if (type == FieldType.PHONE) Intent.ACTION_DIAL else Intent.ACTION_VIEW
    runCatching { context.startActivity(Intent(action, uri)) }
}

private fun addToContactsIntent(e: Encounter, note: String): Intent {
    fun v(t: FieldType) = e.fields.firstOrNull { it.key == t.key }?.value
    val extraNotes = buildList {
        e.fields.filter {
            it.key in setOf(FieldType.INSTAGRAM.key, FieldType.TIKTOK.key, FieldType.SNAPCHAT.key,
                FieldType.DISCORD.key, FieldType.LINKEDIN.key, FieldType.WEBSITE.key, FieldType.NOTE.key)
        }.forEach { add("${it.label} : ${it.value}") }
        if (note.isNotBlank()) add("Ma note : $note")
        add("Ajouté avec Touché le ${formatDate(e.timestamp)}")
    }
    return Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.NAME, e.name)
        v(FieldType.PHONE)?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        v(FieldType.EMAIL)?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        v(FieldType.ORG)?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        v(FieldType.TITLE)?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        putExtra(ContactsContract.Intents.Insert.NOTES, extraNotes.joinToString("\n"))
    }
}
