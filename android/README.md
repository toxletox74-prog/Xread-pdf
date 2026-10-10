# Xread PDF — Android

App Android (Kotlin / Jetpack Compose) de la famille Xread : scanner des documents en PDF, les consulter, les annoter, les signer et les partager.

## Fonctions
- **Scanner** : appareil photo avec détection automatique des bords, recadrage, filtres, multi-pages, import depuis la galerie (scanner Google ML Kit) → PDF enregistré dans « Mes PDF » et ouvert directement.
- **Visionneuse** : défilement vertical des pages, zoom par pincement (2 doigts), indicateur de page, bouton pour réinitialiser le zoom.
- **Éditeur** (bouton « Modifier ») :
  - Stylo (4 couleurs, 3 épaisseurs), surligneur (4 couleurs), texte (couleur, 3 tailles, raccourcis « Date du jour » et « Lu et approuvé »), gomme.
  - Sélection : déplacer un texte ou une signature, l'agrandir avec la poignée, le modifier ou le supprimer.
  - Pages : pivoter, déplacer, supprimer (bouton grille dans la barre de navigation).
  - Annuler / Rétablir, zoom à 2 doigts.
  - Enregistrer : remplacer l'original ou créer une copie « - modifié ». Le contenu d'origine reste vectoriel, les annotations sont ajoutées par-dessus (PdfBox-Android).
- **Signatures** : dessinées au doigt une fois (noir ou bleu), enregistrées en vectoriel, puis posées sur n'importe quelle page via « Signer ». Gestion dans « Mes signatures ».
- **Bibliothèque « Mes PDF »** : grille de miniatures, recherche, date, taille, renommer, supprimer, modifier, signer.
- **Partage** : bouton Partager (mail, WhatsApp, Drive…) depuis la liste ou la visionneuse.
- **Ouvrir un PDF externe** : icône dossier, « Ouvrir avec Xread PDF » depuis un gestionnaire de fichiers, ou « Partager vers Xread PDF » ; bouton Enregistrer pour l'ajouter à « Mes PDF ».

## Compilation
1. Ouvrir le dossier `android` dans Android Studio (Ladybug ou plus récent) et laisser la synchronisation Gradle se faire (le wrapper Gradle 8.9 est téléchargé automatiquement).
2. Run ▶ sur un appareil, ou Build > Build APK(s).

### Sans Android Studio (GitHub Actions)
1. Onglet **Actions** → workflow « Build APK » (se lance à chaque push, ou bouton *Run workflow*).
2. Une fois terminé (~5 min) : télécharger l'artefact **Xread-PDF-apk** → `app-debug.apk`, à installer sur le téléphone (autoriser les sources inconnues).

## Prérequis appareil
- Android 8.0+ (minSdk 26).
- Services Google Play : le module de scan est téléchargé au premier usage. Aucune permission caméra à demander, le scanner Google la gère lui-même.

## Limites connues
- Les PDF protégés par mot de passe ne s'ouvrent pas (limite du moteur PdfRenderer d'Android).
- Le texte ajouté est écrit en Helvetica (police standard PDF) : les caractères hors alphabet latin occidental sont remplacés par « ? ».

## Structure
```
app/src/main/java/com/csa/xreadpdf/
  MainActivity.kt      — entrée, scanner, intents Ouvrir/Partager vers
  MainViewModel.kt     — état, navigation, actions
  PdfRepository.kt     — stockage, copie, renommage, partage (FileProvider)
  PdfDoc.kt            — rendu des pages (PdfRenderer, thread-safe)
  editor/Model.kt         — annotations (points PDF), lissage des tracés
  editor/EditorSession.kt — état d'édition, outils, annuler/rétablir, pages
  editor/PdfExporter.kt   — écriture du PDF modifié (PdfBox-Android)
  editor/SignatureStore.kt — signatures (JSON vectoriel)
  ui/LibraryScreen.kt  — accueil « Mes PDF »
  ui/ViewerScreen.kt   — visionneuse + zoom + barre d'actions
  ui/EditorScreen.kt   — éditeur (outils, page, organisation des pages)
  ui/SignatureUi.kt    — pad de signature, « Mes signatures »
  ui/Theme.kt          — thème Material 3 aux couleurs de l'icône
```
