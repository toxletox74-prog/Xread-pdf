# Xread PDF — iOS

Version iPhone / iPad (SwiftUI, PDFKit, VisionKit) de Xread PDF, mêmes fonctions que la version Android.

## Fonctions
- **Scanner** : scanner de documents d'Apple (détection des bords, recadrage, multi-pages) → PDF dans « Mes PDF ».
- **Visionneuse** : défilement continu, zoom, indicateur de page, barre Modifier / Signer / Partager.
- **Éditeur** : stylo, surligneur, texte (« Date du jour », « Lu et approuvé »), gomme, sélection (déplacer, agrandir, modifier, supprimer), pages (pivoter, déplacer, supprimer), annuler / rétablir, zoom à 2 doigts. Enregistrer : remplacer l'original ou créer une copie « - modifié ».
- **Signatures** : dessinées une fois au doigt, enregistrées en vectoriel (même format JSON que la version Android), posées sur n'importe quelle page.
- **Bibliothèque** : grille de miniatures, recherche, renommer, supprimer, partager. Les PDF sont aussi visibles dans l'app **Fichiers** (Sur mon iPhone › Xread PDF).
- **Ouvrir avec Xread PDF** depuis Fichiers, Mail, etc.

## Compilation (GitHub Actions)
Le workflow « Build iOS » se lance à chaque modification de `ios/` :
- **export-check** : vérifie l'export PDF (positions, rotations, CropBox) sur macOS ;
- **ipa** : produit l'artefact **Xread-PDF-ipa** → `XreadPDF-non-signee.ipa` ;
- **simulateur** : lance l'app sur un iPhone simulé, fait un parcours complet au doigt (test `UITests/FlowTests.swift`) et publie les captures sur la branche `ios-captures`.

Le projet Xcode est généré à partir de `project.yml` avec [XcodeGen](https://github.com/yonaskolb/XcodeGen) :
```
brew install xcodegen
cd ios && xcodegen generate && open XreadPDF.xcodeproj
```

## Installer sur un iPhone
Une IPA doit être signée par Apple pour s'installer. Deux possibilités :
1. **Sans abonnement** : [Sideloadly](https://sideloadly.io) (Windows ou Mac) installe `XreadPDF-non-signee.ipa` avec votre identifiant Apple. L'app expire au bout de 7 jours (il suffit de la réinstaller).
2. **Avec le programme Apple Developer** (99 $/an) : signature permanente, distribution par TestFlight. Le workflow peut alors signer et envoyer l'app automatiquement (certificat et profil à ajouter en secrets GitHub).

## Différences avec Android
- Le texte ajouté est écrit en Helvetica, police intégrée au PDF : tous les caractères passent (pas de « ? »).
- À l'enregistrement, chaque page est redessinée (texte d'origine conservé et sélectionnable). Les liens et champs de formulaire d'origine sont figés dans la page.
- Minimum : iOS 16.
