package com.csa.xreadpdf.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Fenêtre affichée après un scan : nom du PDF et destination
 * (« Mes PDF » dans l'app, ou un emplacement au choix : téléphone, carte SD, Drive…).
 */
@Composable
fun ScanSaveDialog(
    defaultName: String,
    initialToFolder: Boolean,
    initialKeepCopy: Boolean,
    onSaveInApp: (String) -> Unit,
    onChooseLocation: (name: String, keepCopy: Boolean) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember { mutableStateOf(TextFieldValue(defaultName, TextRange(0, defaultName.length))) }
    var toFolder by remember { mutableStateOf(initialToFolder) }
    var keepCopy by remember { mutableStateOf(initialKeepCopy) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val validName = name.text.trim()

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
        title = { Text("Enregistrer le scan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nom du document") },
                    suffix = { Text(".pdf") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Où l'enregistrer ?", style = MaterialTheme.typography.labelLarge)
                DestinationOption(
                    selected = !toFolder,
                    icon = Icons.Filled.PictureAsPdf,
                    title = "Mes PDF",
                    hint = "Dans l'application",
                    onClick = { toFolder = false },
                )
                DestinationOption(
                    selected = toFolder,
                    icon = Icons.Filled.Folder,
                    title = "Choisir l'emplacement…",
                    hint = "Téléchargements, Documents, carte SD, Drive…",
                    onClick = { toFolder = true },
                )
                if (toFolder) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = keepCopy, role = Role.Checkbox, onValueChange = { keepCopy = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = keepCopy, onCheckedChange = null)
                        Text(
                            "Garder aussi une copie dans Mes PDF",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validName.isNotEmpty(),
                onClick = { if (toFolder) onChooseLocation(validName, keepCopy) else onSaveInApp(validName) },
            ) { Text(if (toFolder) "Choisir…" else "Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = { if (confirmDiscard) onDiscard() else confirmDiscard = true }) {
                Text(
                    if (confirmDiscard) "Confirmer la suppression" else "Supprimer",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun DestinationOption(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
