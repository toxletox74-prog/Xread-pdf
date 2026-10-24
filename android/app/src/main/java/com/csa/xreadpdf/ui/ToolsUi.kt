package com.csa.xreadpdf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.csa.xreadpdf.MainViewModel
import com.csa.xreadpdf.tools.CompressLevel
import com.csa.xreadpdf.tools.FieldKind
import com.csa.xreadpdf.tools.FormField
import com.csa.xreadpdf.tools.ImageFormat
import com.csa.xreadpdf.tools.NumberPosition
import com.csa.xreadpdf.tools.PdfTools
import com.csa.xreadpdf.tools.parsePageRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class ToolDialog { EXTRACT, SPLIT, BLANK, INSERT_PDF, COMPRESS, NUMBERS, WATERMARK, PROTECT, EXPORT }

/** Actions qui demandent un sélecteur système, fournies par l'activité. */
class ToolLaunchers(
    val pickPdfToInsert: (after: Int) -> Unit,
    val exportImages: (ImageFormat) -> Unit,
    val scanMore: () -> Unit,
    val print: () -> Unit,
    val fillForm: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheet(vm: MainViewModel, file: File, launchers: ToolLaunchers, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val count by produceState<Int?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { PdfTools.pageCount(context, file) }.getOrDefault(0) }
    }
    var dialog by remember { mutableStateOf<ToolDialog?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Outils PDF", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Le résultat est enregistré comme nouveau document : l'original reste intact.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                }
            }
            item { Section("Pages") }
            item { ToolRow(Icons.Filled.ContentCut, "Extraire des pages", "Garder seulement certaines pages") { dialog = ToolDialog.EXTRACT } }
            item { ToolRow(Icons.Filled.CallSplit, "Découper", "Un fichier par page ou par plage") { dialog = ToolDialog.SPLIT } }
            item { ToolRow(Icons.Filled.CropPortrait, "Insérer une page blanche", null) { dialog = ToolDialog.BLANK } }
            item { ToolRow(Icons.Filled.PostAdd, "Insérer un autre PDF", "Ou fusionner à la fin") { dialog = ToolDialog.INSERT_PDF } }
            item { ToolRow(Icons.Filled.DocumentScanner, "Ajouter des pages scannées", "À la fin de ce document") { onDismiss(); launchers.scanMore() } }
            item { Section("Contenu") }
            item { ToolRow(Icons.Filled.TextSnippet, "Rendre le texte cherchable (OCR)", "Reconnaît le texte d'un scan, sur l'appareil") { onDismiss(); vm.ocr(file) } }
            item { ToolRow(Icons.Filled.EditNote, "Remplir le formulaire", "Champs interactifs du PDF") { onDismiss(); launchers.fillForm() } }
            item { ToolRow(Icons.Filled.FormatListNumbered, "Numéros de page", null) { dialog = ToolDialog.NUMBERS } }
            item { ToolRow(Icons.Filled.BrandingWatermark, "Filigrane", "Texte en diagonale sur chaque page") { dialog = ToolDialog.WATERMARK } }
            item { Section("Fichier") }
            item { ToolRow(Icons.Filled.Compress, "Compresser", "Alléger pour l'envoi par mail") { dialog = ToolDialog.COMPRESS } }
            item { ToolRow(Icons.Filled.Image, "Exporter en images", "Une image JPG ou PNG par page") { dialog = ToolDialog.EXPORT } }
            item { ToolRow(Icons.Filled.Lock, "Protéger par mot de passe", null) { dialog = ToolDialog.PROTECT } }
            item { ToolRow(Icons.Filled.Print, "Imprimer", null) { onDismiss(); launchers.print() } }
        }
    }

    val n = count ?: 0
    fun done() { dialog = null; onDismiss() }
    when (dialog) {
        ToolDialog.EXTRACT -> PagesDialog("Extraire des pages", "Pages à garder, ex. 1-3, 5", n, split = false, onDismiss = { dialog = null }) { groups ->
            done(); vm.extractPages(file, groups.flatten().distinct().sorted())
        }
        ToolDialog.SPLIT -> PagesDialog("Découper", "Plages, ex. 1-2, 3-5, 6-", n, split = true, onDismiss = { dialog = null }) { groups ->
            done(); vm.splitPdf(file, groups)
        }
        ToolDialog.BLANK -> PositionDialog("Insérer une page blanche", n, onDismiss = { dialog = null }) { after -> done(); vm.insertBlank(file, after) }
        ToolDialog.INSERT_PDF -> PositionDialog("Insérer un autre PDF", n, onDismiss = { dialog = null }) { after -> done(); launchers.pickPdfToInsert(after) }
        ToolDialog.COMPRESS -> ChoiceDialog("Compresser", CompressLevel.entries.toList(), CompressLevel.MEDIUM, { it.label }, "Compresser", onDismiss = { dialog = null }) {
            done(); vm.compress(file, it)
        }
        ToolDialog.NUMBERS -> ChoiceDialog("Numéros de page", NumberPosition.entries.toList(), NumberPosition.BOTTOM_CENTER, { it.label }, "Ajouter", onDismiss = { dialog = null }) {
            done(); vm.pageNumbers(file, it)
        }
        ToolDialog.EXPORT -> ChoiceDialog("Exporter en images", ImageFormat.entries.toList(), ImageFormat.JPG, { it.label }, "Choisir le dossier…", onDismiss = { dialog = null }) {
            done(); launchers.exportImages(it)
        }
        ToolDialog.WATERMARK -> TextAskDialog("Filigrane", "Texte", "CONFIDENTIEL", "Ajouter", onDismiss = { dialog = null }) { done(); vm.watermark(file, it) }
        ToolDialog.PROTECT -> NewPasswordDialog(onDismiss = { dialog = null }) { done(); vm.protect(file, it) }
        null -> Unit
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, hint: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------- Boîtes de dialogue

@Composable
private fun PagesDialog(
    title: String,
    hint: String,
    count: Int,
    split: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<List<Int>>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var eachPage by remember { mutableStateOf(split) }
    val groups = if (split && eachPage) (0 until count).map { listOf(it) } else parsePageRanges(text, count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Le document compte $count page${if (count > 1) "s" else ""}.", style = MaterialTheme.typography.bodyMedium)
                if (split) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = eachPage, onCheckedChange = { eachPage = it })
                        Spacer(Modifier.width(12.dp))
                        Text("Un fichier par page", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!(split && eachPage)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text(hint) },
                        isError = text.isNotBlank() && groups == null,
                        supportingText = { if (text.isNotBlank() && groups == null) Text("Saisie invalide (pages 1 à $count)") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = count > 0 && !groups.isNullOrEmpty(), onClick = { groups?.let(onConfirm) }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun PositionDialog(title: String, count: Int, onDismiss: () -> Unit, onConfirm: (after: Int) -> Unit) {
    var text by remember(count) { mutableStateOf("$count") }
    val n = text.toIntOrNull()?.takeIf { it in 0..count }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(5) },
                    singleLine = true,
                    label = { Text("Après la page n°") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = n == null,
                )
                Text(
                    "0 = au début, $count = à la fin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = n != null && count > 0, onClick = { n?.let { onConfirm(it - 1) } }) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    initial: T,
    label: (T) -> String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit,
) {
    var choice by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == o, role = Role.RadioButton) { choice = o }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == o, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(o), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun TextAskDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text(label) }) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun NewPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val tooShort = a.isNotEmpty() && a.length < 4
    val mismatch = b.isNotEmpty() && a != b
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text("Protéger par mot de passe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = a, onValueChange = { a = it }, singleLine = true, label = { Text("Mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = tooShort,
                    supportingText = { if (tooShort) Text("4 caractères minimum") },
                )
                OutlinedTextField(
                    value = b, onValueChange = { b = it }, singleLine = true, label = { Text("Confirmer") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = mismatch,
                    supportingText = { if (mismatch) Text("Les mots de passe diffèrent") },
                )
                Text(
                    "Sans ce mot de passe, le PDF ne pourra plus être ouvert : notez-le.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = a.length >= 4 && a == b, onClick = { onConfirm(a) }) { Text("Protéger") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------- Formulaire

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(vm: MainViewModel, file: File, onDismiss: () -> Unit) {
    val fields by produceState<List<FormField>?>(null, file) { value = vm.formFields(file) }
    val values = remember(file) { mutableStateMapOf<String, String>() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Remplir le formulaire", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val f = fields
            if (!f.isNullOrEmpty()) {
                Button(onClick = {
                    val changed = values.filter { (k, v) -> f.firstOrNull { it.name == k }?.value != v }
                    onDismiss()
                    if (changed.isEmpty()) vm.message("Aucun champ modifié") else vm.fillForm(file, changed)
                }) { Text("Enregistrer") }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).navigationBarsPadding()) {
            val f = fields
            when {
                f == null -> CircularProgressIndicator(Modifier.align(Alignment.Center).padding(32.dp))
                f.isEmpty() -> Text(
                    "Ce PDF ne contient pas de champs de formulaire.\nPour écrire dessus, utilisez Modifier › Texte.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(f, key = { it.name }) { field ->
                        val v = values[field.name] ?: field.value
                        FieldEditor(field, v) { values[field.name] = it }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditor(field: FormField, value: String, onChange: (String) -> Unit) {
    when (field.kind) {
        FieldKind.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label) },
            singleLine = !field.multiline,
            minLines = if (field.multiline) 3 else 1,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldKind.CHECK -> Row(
            Modifier.fillMaxWidth().selectable(selected = value == "true", role = Role.Checkbox) { onChange(if (value == "true") "false" else "true") },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = value == "true", onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(field.label, style = MaterialTheme.typography.bodyLarge)
        }
        FieldKind.RADIO, FieldKind.CHOICE -> {
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(field.label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    field.options.forEach { o ->
                        DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false })
                    }
                }
            }
        }
    }
}

/** Icône de l'outil « Images → PDF » (partagée avec la bibliothèque). */
val ImagesToPdfIcon: ImageVector get() = Icons.Filled.AddPhotoAlternate
