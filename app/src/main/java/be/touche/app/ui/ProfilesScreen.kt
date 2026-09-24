package be.touche.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import be.touche.app.data.Field
import be.touche.app.data.FieldType
import be.touche.app.data.Profile
import be.touche.app.data.Store

@Composable
fun ProfilesScreen(modifier: Modifier = Modifier, onEdit: (String) -> Unit) {
    val profiles by Store.profiles.collectAsState()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Mes profils",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text("Une carte différente selon la personne en face.", style = MaterialTheme.typography.bodyMedium)
        }
        items(profiles, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(p.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text("${p.emoji}  ${p.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${p.sharedFields.size} info(s) partagée(s) · ${p.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val p = Profile.blank("Nouveau", "⭐", listOf(FieldType.NAME, FieldType.PHONE, FieldType.EMAIL))
                    Store.upsertProfile(p)
                    onEdit(p.id)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Nouveau profil")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditor(modifier: Modifier = Modifier, profileId: String, onDone: () -> Unit) {
    val original = remember(profileId) { Store.profiles.value.firstOrNull { it.id == profileId } }
    if (original == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    var title by remember { mutableStateOf(original.title) }
    var emoji by remember { mutableStateOf(original.emoji) }
    var fields by remember { mutableStateOf(original.fields) }
    var addMenu by remember { mutableStateOf(false) }

    fun save() {
        Store.upsertProfile(original.copy(title = title.ifBlank { "Profil" }, emoji = emoji.ifBlank { "👤" }, fields = fields))
        onDone()
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Modifier le profil") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            actions = {
                IconButton(onClick = { Store.deleteProfile(original.id); onDone() }) {
                    Icon(Icons.Default.Delete, "Supprimer le profil")
                }
            },
        )
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Icône") }, modifier = Modifier.width(90.dp), singleLine = true)
                    OutlinedTextField(title, { title = it }, label = { Text("Nom du profil") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text(
                    "Le cadenas = l'info reste privée, elle n'est pas envoyée.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(fields.size) { i ->
                val f = fields[i]
                FieldRow(
                    field = f,
                    onChange = { nf -> fields = fields.toMutableList().also { it[i] = nf } },
                    onRemove = { fields = fields.toMutableList().also { it.removeAt(i) } },
                )
            }
            item {
                Row {
                    OutlinedButton(onClick = { addMenu = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter une info")
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        FieldType.entries.filter { t -> fields.none { it.key == t.key } }.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = { fields = fields + Field(t.key, t.label, ""); addMenu = false },
                            )
                        }
                    }
                }
            }
        }
        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Enregistrer") }
    }
}

@Composable
private fun FieldRow(field: Field, onChange: (Field) -> Unit, onRemove: () -> Unit) {
    val type = FieldType.fromKey(field.key)
    val keyboard = when (type) {
        FieldType.PHONE -> KeyboardType.Phone
        FieldType.EMAIL -> KeyboardType.Email
        FieldType.WEBSITE, FieldType.LINKEDIN -> KeyboardType.Uri
        else -> KeyboardType.Text
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = field.value,
            onValueChange = { onChange(field.copy(value = it)) },
            label = { Text(field.label) },
            placeholder = { Text(type?.hint ?: "") },
            singleLine = type != FieldType.NOTE,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.weight(1f),
            trailingIcon = if (type != FieldType.NAME) {
                { IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Retirer") } }
            } else null,
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (field.shared) Icons.Default.Share else Icons.Default.Lock,
                contentDescription = if (field.shared) "Partagé" else "Privé",
                tint = if (field.shared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Switch(checked = field.shared, onCheckedChange = { onChange(field.copy(shared = it)) })
        }
    }
}
