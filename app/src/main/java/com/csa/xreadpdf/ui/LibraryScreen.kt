package com.csa.xreadpdf.ui

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.csa.xreadpdf.PdfDoc
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(
    files: List<File>,
    signatureCount: Int,
    snackbar: SnackbarHostState,
    onScan: () -> Unit,
    onOpenExternal: () -> Unit,
    onSignatures: () -> Unit,
    onOpen: (File) -> Unit,
    onEdit: (File) -> Unit,
    onSign: (File) -> Unit,
    onShare: (File) -> Unit,
    onRename: (File, String) -> Unit,
    onDelete: (File) -> Unit,
) {
    StatusBarIcons(lightIcons = true)
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(files, query) {
        if (query.isBlank()) files else files.filter { it.nameWithoutExtension.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(
                    modifier = Modifier.bleed(Gutter),
                    signatureCount = signatureCount,
                    onScan = onScan,
                    onOpenExternal = onOpenExternal,
                    onSignatures = onSignatures,
                )
            }
            if (files.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Rechercher un document") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mes documents", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "${shown.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (files.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            } else if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Aucun document ne correspond à « $query ».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(shown, key = { it.absolutePath }) { file ->
                DocCard(
                    file = file,
                    onOpen = { onOpen(file) },
                    onEdit = { onEdit(file) },
                    onSign = { onSign(file) },
                    onShare = { onShare(file) },
                    onRename = { renaming = file },
                    onDelete = { deleting = file },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    renaming?.let { file ->
        RenameDialog(
            initial = file.nameWithoutExtension,
            onDismiss = { renaming = null },
            onConfirm = { onRename(file, it); renaming = null },
        )
    }
    deleting?.let { file ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Supprimer ce PDF ?") },
            text = { Text("« ${file.nameWithoutExtension} » sera définitivement supprimé.") },
            confirmButton = {
                TextButton(onClick = { onDelete(file); deleting = null }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

private val Gutter = 20.dp

/** Déborde de [gutter] de chaque côté (en-tête pleine largeur dans une grille à marges). */
private fun Modifier.bleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = (gutter * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun Header(
    modifier: Modifier,
    signatureCount: Int,
    onScan: () -> Unit,
    onOpenExternal: () -> Unit,
    onSignatures: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brand.Night)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Xread PDF", style = MaterialTheme.typography.headlineSmall, color = Brand.Cream)
                Text(
                    "Scanner · Modifier · Signer · Partager",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Cream.copy(alpha = 0.72f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Scanner", "Appareil photo", Icons.Filled.DocumentScanner, Brand.Coral, onScan, Modifier.weight(1f))
            ActionTile("Ouvrir", "Un PDF", Icons.Filled.FolderOpen, Brand.Sky, onOpenExternal, Modifier.weight(1f))
            ActionTile(
                "Signatures",
                if (signatureCount == 0) "À créer" else "$signatureCount enregistrée${if (signatureCount > 1) "s" else ""}",
                Icons.Filled.Gesture, Brand.Sun, onSignatures, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    hint: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp), color = color) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = Brand.Ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(96.dp)
        Text(
            "Rien ici… pour l'instant",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            "Scannez un document ou ouvrez un PDF pour le lire, l'annoter et le signer.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DocCard(
    file: File,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onSign: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val modified = file.lastModified()
    val date = remember(modified) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(modified)) }
    val size = remember(modified) { Formatter.formatShortFileSize(context, file.length()) }
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            PdfThumbnail(file, Modifier.fillMaxWidth().aspectRatio(0.78f))
            Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.nameWithoutExtension,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$date · $size",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuEntry("Modifier", Icons.Filled.Edit) { menu = false; onEdit() }
                        MenuEntry("Signer", Icons.Filled.Gesture) { menu = false; onSign() }
                        MenuEntry("Partager", Icons.Filled.Share) { menu = false; onShare() }
                        MenuEntry("Renommer", Icons.Filled.DriveFileRenameOutline) { menu = false; onRename() }
                        MenuEntry("Supprimer", Icons.Filled.Delete, danger = true) { menu = false; onDelete() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick,
    )
}

@Composable
private fun PdfThumbnail(file: File, modifier: Modifier = Modifier) {
    val widthPx = with(LocalDensity.current) { 180.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, file.absolutePath, file.lastModified()) {
        value = PdfDoc.thumbnail(file, widthPx)?.asImageBitmap()
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                b, contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
        title = { Text("Renommer") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nom du document") },
            )
        },
        confirmButton = {
            TextButton(enabled = text.text.isNotBlank(), onClick = { onConfirm(text.text.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
