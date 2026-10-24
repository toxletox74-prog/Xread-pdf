package com.csa.xreadpdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.csa.xreadpdf.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appLock: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    StatusBarIcons(lightIcons = false)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                title = { Text("Réglages") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingCard(Icons.Filled.Fingerprint, "Verrouiller l'application", "Empreinte, visage ou code de l'appareil à l'ouverture (et après 1 min en arrière-plan).") {
                Switch(checked = appLock, onCheckedChange = onToggleLock)
            }
            SettingCard(Icons.Filled.Backup, "Sauvegarder Mes PDF", "Un fichier ZIP avec tous vos PDF, signatures, dossiers et favoris, à garder sur Drive, le NAS ou une carte SD.", onClick = onBackup)
            SettingCard(Icons.Filled.Restore, "Restaurer une sauvegarde", "Ajoute les PDF d'une sauvegarde (rien n'est écrasé).", onClick = onRestore)
            SettingCard(Icons.Filled.Info, "Xread PDF ${BuildConfig.VERSION_NAME}", "Tout est traité sur votre téléphone : aucun document n'est envoyé sur Internet.")
        }
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    text: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    }
}

/** Écran affiché tant que l'app est verrouillée. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brand.Night), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            BrandMark(84.dp)
            Spacer(Modifier.size(20.dp))
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Brand.Sun, modifier = Modifier.size(28.dp))
            Text(
                "Xread PDF est verrouillé",
                style = MaterialTheme.typography.titleLarge,
                color = Brand.Cream,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Déverrouiller")
            }
        }
    }
}

@Suppress("unused")
private val Transparent = Color.Transparent
