package com.csa.xreadpdf.ui

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.csa.xreadpdf.PdfDoc
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    files: List<File>,
    snackbar: SnackbarHostState,
    onScan: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpen: (File) -> Unit,
    onShare: (File) -> Unit,
    onRename: (File, String) -> Unit,
    onDelete: (File) -> Unit,
) {
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xread PDF") },
                actions = {
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Ouvrir un PDF")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                text = { Text("Scanner") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (files.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    FileRow(
                        file = file,
                        onOpen = { onOpen(file) },
                        onShare = { onShare(file) },
                        onRename = { renaming = file },
                        onDelete = { deleting = file },
                    )
                    HorizontalDivider()
                }
            }
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
            title = { Text("Supprimer ?") },
            text = { Text("« ${file.nameWithoutExtension} » sera définitivement supprimé.") },
            confirmButton = {
                TextButton(onClick = { onDelete(file); deleting = null }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.PictureAsPdf, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            "Aucun PDF pour l'instant",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Touchez « Scanner » pour numériser un document, ou l'icône dossier pour ouvrir un PDF.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FileRow(
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val date = remember(file.lastModified()) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified()))
    }
    val size = remember(file.length()) { Formatter.formatShortFileSize(context, file.length()) }
    var menu by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { PdfThumbnail(file, Modifier.size(48.dp, 64.dp)) },
        headlineContent = { Text(file.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("$date · $size") },
        trailingContent = {
            Row {
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Partager") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Plus") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Renommer") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PdfThumbnail(file: File, modifier: Modifier = Modifier) {
    val widthPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, file.absolutePath, file.lastModified()) {
        value = PdfDoc.thumbnail(file, widthPx)?.asImageBitmap()
    }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
