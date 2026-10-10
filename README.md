# Xread PDF

Scanner, lire, annoter, signer et partager des PDF — famille Xread.

| Plateforme | Dossier | Build GitHub Actions | Artefact |
|---|---|---|---|
| Android (Kotlin / Compose) | [`android/`](android/) | « Build APK » | `Xread-PDF-apk` → `app-debug.apk` |
| iOS (SwiftUI / PDFKit) | [`ios/`](ios/) | « Build iOS » | `Xread-PDF-ipa` → `XreadPDF-non-signee.ipa` |

Chaque workflow ne se lance que si son dossier change. Les deux apps partagent le même modèle d'édition (coordonnées en points PDF, tracés lissés, format des signatures) et la même identité visuelle (icône « Fusée Partage »).
