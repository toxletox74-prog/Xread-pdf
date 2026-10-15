# Xread PDF — web app (iPhone, iPad, et tout navigateur)

Version installable (PWA) de Xread PDF : **gratuite, sans compte développeur Apple, sans expiration.**

**Adresse :** https://toxletox74-prog.github.io/Xread-pdf/

## Installer sur l'iPhone
1. Ouvrir l'adresse dans **Safari**.
2. Toucher **Partager** puis **Sur l'écran d'accueil**.
3. L'icône Xread PDF apparaît : l'app s'ouvre en plein écran et fonctionne **hors ligne**.

## Fonctions
- **Scanner** : photo (ou galerie), ajustement des 4 coins avec redressement de la perspective, filtre « Document » (papier blanc, encre noire) ou « Couleur », multi-pages.
- **Visionneuse**, **éditeur** (stylo, surligneur, texte, gomme, sélection, pages, annuler / rétablir, zoom au pincement) et **signatures** : mêmes fonctions et même rendu PDF que les versions Android et iOS.
- **Partager** : feuille de partage iOS (Mail, Messages, Fichiers, AirDrop…).

## Où sont les PDF ?
Dans le stockage de l'app sur l'appareil (aucun envoi sur internet). Pour garder une copie ailleurs : **Partager → Enregistrer dans Fichiers**.

## Limites par rapport à une app native
- Pas de détection automatique des bords au scan (on ajuste les coins à la main).
- « Ouvrir avec Xread PDF » depuis une autre app n'existe pas pour les web apps sur iOS : on ouvre le PDF depuis l'app (tuile **Ouvrir**).
- Le texte ajouté est en Helvetica : les caractères hors alphabet latin deviennent « ? » (comme sur Android).

## Technique
Sans étape de compilation : Preact + htm, pdf.js (affichage), pdf-lib (écriture), IndexedDB (stockage), service worker (hors ligne).
Le workflow « Web app (iPhone) » teste l'export PDF puis publie le dossier `web/` sur la branche `gh-pages` à chaque modification.
