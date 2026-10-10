# Xread PDF

Scanner, lire, annoter, signer et partager des PDF — famille Xread.

| Plateforme | Dossier | Build GitHub Actions | Artefact |
|---|---|---|---|
| Android (Kotlin / Compose) | [`android/`](android/) | « Build APK » | `Xread-PDF-apk` → `app-debug.apk` |
| **iPhone / iPad — web app** | [`web/`](web/) | « Web app (iPhone) » | **https://toxletox74-prog.github.io/Xread-pdf/** (Safari → Partager → Sur l'écran d'accueil) |
| iOS natif (SwiftUI / PDFKit) | [`ios/`](ios/) | « Build iOS » | `Xread-PDF-ipa` → `XreadPDF-non-signee.ipa` (installation signée requise) |

Chaque workflow ne se lance que si son dossier change. Sur iPhone, la web app est la solution gratuite et sans expiration ; la version native iOS reste prête si un compte Apple Developer est pris un jour. Les deux apps partagent le même modèle d'édition (coordonnées en points PDF, tracés lissés, format des signatures) et la même identité visuelle (icône « Fusée Partage »).
