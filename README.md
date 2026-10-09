# Xread PDF

App Android (Kotlin / Jetpack Compose) de la famille Xread : scanner des documents en PDF, les consulter et les partager.

## Fonctions
- **Scanner** : appareil photo avec détection automatique des bords, recadrage, filtres, multi-pages, import depuis la galerie (scanner Google ML Kit) → PDF enregistré dans « Mes PDF » et ouvert directement.
- **Visionneuse** : défilement vertical des pages, zoom par pincement (2 doigts), indicateur de page, bouton pour réinitialiser le zoom.
- **Bibliothèque « Mes PDF »** : miniatures, date, taille, renommer, supprimer.
- **Partage** : bouton Partager (mail, WhatsApp, Drive…) depuis la liste ou la visionneuse.
- **Ouvrir un PDF externe** : icône dossier, « Ouvrir avec Xread PDF » depuis un gestionnaire de fichiers, ou « Partager vers Xread PDF » ; bouton Enregistrer pour l'ajouter à « Mes PDF ».

## Compilation
1. Ouvrir le dossier `XreadPdf` dans Android Studio (Ladybug ou plus récent) et laisser la synchronisation Gradle se faire (le wrapper Gradle 8.9 est téléchargé automatiquement).
2. Run ▶ sur un appareil, ou Build > Build APK(s).

### Sans Android Studio (GitHub Actions)
1. Créer un dépôt GitHub (privé) et y pousser le contenu du dossier `XreadPdf`.
2. Onglet **Actions** → workflow « Build APK » (se lance à chaque push, ou bouton *Run workflow*).
3. Une fois terminé (~5 min) : télécharger l'artefact **Xread-PDF-apk** → `app-debug.apk`, à installer sur le téléphone (autoriser les sources inconnues).

## Prérequis appareil
- Android 8.0+ (minSdk 26).
- Services Google Play : le module de scan est téléchargé au premier usage. Aucune permission caméra à demander, le scanner Google la gère lui-même.

## Limites connues
- Les PDF protégés par mot de passe ne s'ouvrent pas (limite du moteur PdfRenderer d'Android).

## Structure
```
app/src/main/java/com/csa/xreadpdf/
  MainActivity.kt      — entrée, scanner, intents Ouvrir/Partager vers
  MainViewModel.kt     — état, navigation, actions
  PdfRepository.kt     — stockage, copie, renommage, partage (FileProvider)
  PdfDoc.kt            — rendu des pages (PdfRenderer, thread-safe)
  ui/LibraryScreen.kt  — liste « Mes PDF »
  ui/ViewerScreen.kt   — visionneuse + zoom
  ui/Theme.kt          — thème Material 3 (couleurs dynamiques Android 12+)
```
