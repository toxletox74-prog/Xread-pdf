package com.csa.xreadpdf.ui

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.csa.xreadpdf.BuildConfig
import com.csa.xreadpdf.Library
import com.csa.xreadpdf.PdfDoc
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Actions de la bibliothèque fournies par l'écran principal. */
class LibraryActions(
    val onScan: () -> Unit,
    val onOpenExternal: () -> Unit,
    val onImages: () -> Unit,
    val onSignatures: () -> Unit,
    val onSettings: () -> Unit,
    val onOpen: (File) -> Unit,
    val onEdit: (File) -> Unit,
    val onSign: (File) -> Unit,
    val onShare: (File) -> Unit,
    val onShareMany: (List<File>) -> Unit,
    val onSaveAs: (File) -> Unit,
    val onPrint: (File) -> Unit,
    val onTools: (File) -> Unit,
    val onRename: (File, String) -> Unit,
    val onDelete: (File) -> Unit,
    val onDeleteMany: (List<File>) -> Unit,
    val onToggleFavorite: (File) -> Unit,
    val onMoveToFolder: (List<File>, String?) -> Unit,
    val onCreateFolder: (String) -> Boolean,
    val onDeleteFolder: (String) -> Unit,
    val onMerge: (List<File>) -> Unit,
)

private const val FILTER_ALL = "all"
private const val FILTER_FAV = "fav"
private const val FILTER_RECENT = "recent"
private const val FOLDER_PREFIX = "folder:"

private enum class Sort(val label: String) { DATE("Date de modification"), NAME("Nom"), SIZE("Taille") }

@Composable
fun LibraryScreen(
    files: List<File>,
    library: Library,
    signatureCount: Int,
    snackbar: SnackbarHostState,
    a: LibraryActions,
) {
    StatusBarIcons(lightIcons = true)
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<List<File>?>(null) }
    var moving by remember { mutableStateOf<List<File>?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var sort by rememberSaveable { mutableStateOf(Sort.DATE) }
    var sortMenu by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()

    // Filtre de dossier devenu invalide (dossier supprimé)
    if (filter.startsWith(FOLDER_PREFIX) && filter.removePrefix(FOLDER_PREFIX) !in library.folders) filter = FILTER_ALL

    val shown = remember(files, library, query, filter, sort) {
        var l = files
        if (query.isNotBlank()) l = l.filter { it.nameWithoutExtension.contains(query.trim(), ignoreCase = true) }
        l = when {
            filter == FILTER_FAV -> l.filter { library.of(it).favorite }
            filter == FILTER_RECENT -> l.filter { library.of(it).lastOpened > 0 }.sortedByDescending { library.of(it).lastOpened }.take(20)
            filter.startsWith(FOLDER_PREFIX) -> l.filter { library.of(it).folder == filter.removePrefix(FOLDER_PREFIX) }
            else -> l
        }
        if (filter != FILTER_RECENT) {
            l = when (sort) {
                Sort.DATE -> l.sortedByDescending { it.lastModified() }
                Sort.NAME -> l.sortedBy { it.nameWithoutExtension.lowercase() }
                Sort.SIZE -> l.sortedByDescending { it.length() }
            }
        }
        l
    }
    val selectedFiles = files.filter { it.absolutePath in selected }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selecting) {
                SelectionBar(
                    count = selectedFiles.size,
                    onClose = { selected.clear() },
                    onMerge = { a.onMerge(selectedFiles); selected.clear() },
                    onMove = { moving = selectedFiles },
                    onShare = { a.onShareMany(selectedFiles) },
                    onDelete = { deleting = selectedFiles },
                )
            }
        },
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
                    a = a,
                )
            }
            if (files.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            item { Chip("Tous", null, filter == FILTER_ALL) { filter = FILTER_ALL } }
                            item { Chip("Favoris", Icons.Filled.Star, filter == FILTER_FAV) { filter = FILTER_FAV } }
                            item { Chip("Récents", Icons.Filled.History, filter == FILTER_RECENT) { filter = FILTER_RECENT } }
                            items(library.folders) { f ->
                                val key = FOLDER_PREFIX + f
                                Chip(f, Icons.Filled.Folder, filter == key) { filter = if (filter == key) FILTER_ALL else key }
                            }
                            item {
                                IconButton(onClick = { newFolder = true }) {
                                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nouveau dossier", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val title = when {
                                filter == FILTER_FAV -> "Favoris"
                                filter == FILTER_RECENT -> "Récemment ouverts"
                                filter.startsWith(FOLDER_PREFIX) -> filter.removePrefix(FOLDER_PREFIX)
                                else -> "Mes documents"
                            }
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "${shown.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (filter.startsWith(FOLDER_PREFIX)) {
                                IconButton(onClick = { deleteFolder = filter.removePrefix(FOLDER_PREFIX) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer le dossier", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (filter != FILTER_RECENT) {
                                Box {
                                    IconButton(onClick = { sortMenu = true }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Trier")
                                    }
                                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                        Sort.entries.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(s.label) },
                                                leadingIcon = { RadioButton(selected = sort == s, onClick = null) },
                                                onClick = { sort = s; sortMenu = false },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!selecting && shown.size > 1) {
                            Text(
                                "Astuce : appui long sur un document pour en sélectionner plusieurs (fusionner, déplacer…).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (files.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            } else if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        when {
                            query.isNotBlank() -> "Aucun document ne correspond à « $query »."
                            filter == FILTER_FAV -> "Aucun favori. Menu ⋮ d'un document › Ajouter aux favoris."
                            filter == FILTER_RECENT -> "Aucun document ouvert récemment."
                            else -> "Ce dossier est vide. Menu ⋮ d'un document › Déplacer vers un dossier."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(shown, key = { it.absolutePath }) { file ->
                val path = file.absolutePath
                DocCard(
                    file = file,
                    favorite = library.of(file).favorite,
                    folder = library.of(file).folder,
                    selecting = selecting,
                    selected = path in selected,
                    onOpen = { if (selecting) { if (path in selected) selected.remove(path) else selected.add(path) } else a.onOpen(file) },
                    onLongPress = { if (path !in selected) selected.add(path) },
                    onEdit = { a.onEdit(file) },
                    onSign = { a.onSign(file) },
                    onShare = { a.onShare(file) },
                    onSaveAs = { a.onSaveAs(file) },
                    onPrint = { a.onPrint(file) },
                    onTools = { a.onTools(file) },
                    onFavorite = { a.onToggleFavorite(file) },
                    onMove = { moving = listOf(file) },
                    onRename = { renaming = file },
                    onDelete = { deleting = listOf(file) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    renaming?.let { file ->
        RenameDialog(
            initial = file.nameWithoutExtension,
            onDismiss = { renaming = null },
            onConfirm = { a.onRename(file, it); renaming = null },
        )
    }
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(if (list.size > 1) "Supprimer ${list.size} PDF ?" else "Supprimer ce PDF ?") },
            text = {
                Text(
                    if (list.size > 1) "Ces documents seront définitivement supprimés."
                    else "« ${list.first().nameWithoutExtension} » sera définitivement supprimé."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (list.size == 1) a.onDelete(list.first()) else a.onDeleteMany(list)
                    selected.clear()
                    deleting = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
    moving?.let { list ->
        MoveDialog(
            folders = library.folders,
            current = list.map { library.of(it).folder }.distinct().singleOrNull(),
            onCreate = a.onCreateFolder,
            onDismiss = { moving = null },
            onConfirm = { folder -> a.onMoveToFolder(list, folder); selected.clear(); moving = null },
        )
    }
    if (newFolder) {
        TextAskDialog("Nouveau dossier", "Nom du dossier", "", "Créer", onDismiss = { newFolder = false }) { name ->
            if (a.onCreateFolder(name)) filter = FOLDER_PREFIX + name.trim()
            newFolder = false
        }
    }
    deleteFolder?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            title = { Text("Supprimer le dossier « $f » ?") },
            text = { Text("Les documents qu'il contient ne sont pas supprimés : ils restent dans Mes documents.") },
            confirmButton = {
                TextButton(onClick = { a.onDeleteFolder(f); filter = FILTER_ALL; deleteFolder = null }) {
                    Text("Supprimer le dossier", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFolder = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Chip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = CircleShape,
    )
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onMerge: () -> Unit, onMove: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Surface(color = Brand.Night, contentColor = Brand.Cream, shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Annuler la sélection", tint = Brand.Cream) }
            Text("$count sélectionné${if (count > 1) "s" else ""}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onMerge, enabled = count >= 2) {
                Icon(Icons.Filled.MergeType, contentDescription = "Fusionner", tint = if (count >= 2) Brand.Sun else Brand.Cream.copy(alpha = 0.35f))
            }
            IconButton(onClick = onMove) { Icon(Icons.Filled.DriveFileMove, contentDescription = "Déplacer vers un dossier", tint = Brand.Cream) }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Partager", tint = Brand.Cream) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Brand.Coral) }
        }
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
private fun Header(modifier: Modifier, signatureCount: Int, a: LibraryActions) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brand.Night)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Xread PDF", style = MaterialTheme.typography.headlineSmall, color = Brand.Cream)
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.Ink,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Sun)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "Scanner · Modifier · Signer · Partager",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Cream.copy(alpha = 0.72f),
                )
            }
            IconButton(onClick = a.onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = Brand.Cream)
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Scanner", "Appareil photo", Icons.Filled.DocumentScanner, Brand.Coral, a.onScan, Modifier.weight(1f))
                ActionTile("Ouvrir", "Un PDF", Icons.Filled.FolderOpen, Brand.Sky, a.onOpenExternal, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Images", "Photos → PDF", ImagesToPdfIcon, Color(0xFF7CD992), a.onImages, Modifier.weight(1f))
                ActionTile(
                    "Signatures",
                    if (signatureCount == 0) "À créer" else "$signatureCount enregistrée${if (signatureCount > 1) "s" else ""}",
                    Icons.Filled.Gesture, Brand.Sun, a.onSignatures, Modifier.weight(1f),
                )
            }
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
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = color) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
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
            "Scannez un document, transformez des photos en PDF ou ouvrez un PDF pour le lire, l'annoter et le signer.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocCard(
    file: File,
    favorite: Boolean,
    folder: String?,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
    onSign: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onPrint: () -> Unit,
    onTools: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val modified = file.lastModified()
    val date = remember(modified) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(modified)) }
    val size = remember(modified) { Formatter.formatShortFileSize(context, file.length()) }
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                PdfThumbnail(file, Modifier.fillMaxWidth().aspectRatio(0.78f))
                if (favorite) {
                    Icon(
                        Icons.Filled.Star, contentDescription = "Favori", tint = Brand.Sun,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp),
                    )
                }
                if (selecting) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                            .clip(CircleShape).background(Color.White),
                    )
                }
            }
            Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.nameWithoutExtension,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(date, size, folder).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!selecting) {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Actions")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuEntry("Modifier", Icons.Filled.Edit) { menu = false; onEdit() }
                            MenuEntry("Signer", Icons.Filled.Gesture) { menu = false; onSign() }
                            MenuEntry("Outils PDF…", Icons.Filled.Build) { menu = false; onTools() }
                            MenuEntry("Partager", Icons.Filled.Share) { menu = false; onShare() }
                            MenuEntry("Imprimer", Icons.Filled.Print) { menu = false; onPrint() }
                            MenuEntry("Enregistrer sous…", Icons.Filled.SaveAs) { menu = false; onSaveAs() }
                            HorizontalDivider()
                            MenuEntry(if (favorite) "Retirer des favoris" else "Ajouter aux favoris", if (favorite) Icons.Filled.StarBorder else Icons.Filled.Star) { menu = false; onFavorite() }
                            MenuEntry("Déplacer vers un dossier…", Icons.Filled.DriveFileMove) { menu = false; onMove() }
                            MenuEntry("Renommer", Icons.Filled.DriveFileRenameOutline) { menu = false; onRename() }
                            MenuEntry("Supprimer", Icons.Filled.Delete, danger = true) { menu = false; onDelete() }
                        }
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

@Composable
private fun MoveDialog(
    folders: List<String>,
    current: String?,
    onCreate: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var choice by remember { mutableStateOf(current) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
        title = { Text("Déplacer vers…") },
        text = {
            Column {
                (listOf<String?>(null) + folders).forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == f, role = Role.RadioButton) { choice = f }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == f, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Icon(if (f == null) Icons.Filled.PictureAsPdf else Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(f ?: "Aucun dossier", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("Nouveau dossier") },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(enabled = newName.isNotBlank(), onClick = {
                        val n = newName.trim()
                        if (onCreate(n) || n in folders) { choice = n; newName = "" }
                    }) { Icon(Icons.Filled.Add, contentDescription = "Créer le dossier") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice) }) { Text("Déplacer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
