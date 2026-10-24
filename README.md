<p align="center">
  <img src="docs/apercu-social.png" alt="Xread PDF — scanner, modifier, signer et partager des PDF" width="100%">
</p>

<h1 align="center">Xread PDF</h1>

<p align="center">
  <b>Scanner, lire, annoter, signer et partager des PDF</b> — sur Android, iPhone et iPad.<br>
  Gratuit, sans compte, sans publicité. Vos documents restent sur votre appareil.
</p>

<p align="center">
  <a href="https://toxletox74-prog.github.io/Xread-pdf/"><img alt="Ouvrir la web app" src="https://img.shields.io/badge/iPhone%20%2F%20web-ouvrir%20l'app-FF5A4E?style=for-the-badge"></a>
  <a href="https://github.com/toxletox74-prog/Xread-pdf/releases/latest/download/Xread-PDF.apk"><img alt="Télécharger l'APK Android" src="https://img.shields.io/badge/Android-t%C3%A9l%C3%A9charger%20l'APK-4C8DFF?style=for-the-badge"></a>
  <a href="https://github.com/toxletox74-prog/Xread-pdf/releases/latest"><img alt="Dernière version" src="https://img.shields.io/github/v/release/toxletox74-prog/Xread-pdf?style=for-the-badge&label=version&color=2A1E3D"></a>
</p>

---

## Fonctions

| | |
|---|---|
| 📷 **Scanner** | Photo d'un document, recadrage des 4 coins avec redressement de la perspective, filtre « Document » noir et blanc, plusieurs pages en un PDF. |
| ✍️ **Signer** | Dessinez votre signature une fois, posez-la sur n'importe quel PDF, déplacez-la et ajustez sa taille. |
| 🖊️ **Annoter** | Stylo, surligneur, texte (« Date du jour », « Lu et approuvé »), gomme, annuler / rétablir, zoom. |
| 📄 **Organiser** | Pivoter, déplacer, supprimer des pages. Enregistrer par-dessus l'original ou en copie. |
| 💾 **Enregistrer où vous voulez** | Après un scan, choisissez le nom et l'emplacement : « Mes PDF » dans l'app, ou Téléchargements, Documents, carte SD, Drive… « Enregistrer sous… » pour tout PDF (Android). |
| 📤 **Partager** | Mail, WhatsApp, Drive, AirDrop… directement depuis l'app. |
| 🔒 **Local** | Aucun serveur : les PDF ne quittent pas votre téléphone, sauf si vous les partagez. |

Le contenu d'origine reste net : le texte du PDF reste sélectionnable, les annotations sont ajoutées par-dessus.

## Aperçu

<p align="center">
  <img src="docs/captures/accueil.png" width="24%" alt="Accueil">
  <img src="docs/captures/editeur.png" width="24%" alt="Éditeur et signature">
  <img src="docs/captures/scanner.png" width="24%" alt="Scanner avec recadrage">
  <img src="docs/captures/pages.png" width="24%" alt="Organisation des pages">
</p>

## Installer

### iPhone / iPad (gratuit, sans expiration)
1. Ouvrir **https://toxletox74-prog.github.io/Xread-pdf/** dans **Safari**.
2. Toucher **Partager** puis **Sur l'écran d'accueil**.

L'app s'ouvre en plein écran et fonctionne hors ligne.

### Android
Télécharger **[Xread-PDF.apk](https://github.com/toxletox74-prog/Xread-pdf/releases/latest/download/Xread-PDF.apk)** puis l'ouvrir sur le téléphone (autoriser l'installation depuis cette source). Android 8.0 ou plus récent.

## Code source

| Plateforme | Dossier | Technologies |
|---|---|---|
| Android | [`android/`](android/) | Kotlin, Jetpack Compose, PdfRenderer, PdfBox-Android, ML Kit Document Scanner |
| iPhone / web | [`web/`](web/) | PWA sans build : Preact, pdf.js, pdf-lib, IndexedDB, service worker |
| iOS natif | [`ios/`](ios/) | SwiftUI, PDFKit, VisionKit (nécessite une signature Apple pour être installé) |

Chaque plateforme est compilée et testée par GitHub Actions : export PDF vérifié sur les 4 orientations de page, parcours complet testé sur émulateur Android, simulateur iPhone et navigateur mobile.

Les trois versions partagent le même modèle d'édition (coordonnées en points PDF, tracés lissés, format des signatures) et la même identité visuelle.

---

<p align="center">Xread PDF fait partie de la famille <b>Xread</b> — © Cédric Sarda</p>
