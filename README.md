# Touché — version 0.1 (Android)

Échange tes infos en collant deux téléphones. Tu choisis le profil (Pro, Esport, Perso) et tu décides, info par info, ce qui part.

## Installer l'app sans Android Studio

Suis le guide **INSTALLER-SUR-MON-TELEPHONE.md** : GitHub compile l'app pour toi et tu installes le fichier APK directement sur ton téléphone.

## Lancer l'app avec Android Studio (pour développer)

1. Installe **Android Studio** (gratuit) : https://developer.android.com/studio
2. `File > Open` puis choisis le dossier `Touche`. Laisse Android Studio synchroniser. La première fois, ça télécharge Gradle et les librairies (quelques minutes).
3. Sur ton téléphone : `Paramètres > À propos > Numéro de build`, tape 7 fois dessus pour activer le **mode développeur**. Active ensuite le **débogage USB** dans les options développeur.
4. Branche le téléphone en USB et clique sur ▶ **Run**.
5. Pour tester l'échange, installe l'app sur **deux téléphones Android** (le tien et celui d'un pote).

> Si Android Studio propose de mettre à jour le plugin Gradle ou Kotlin, tu peux accepter.

## Comment ça marche

| Téléphone A | Téléphone B | Résultat |
|---|---|---|
| **Montrer ma carte** | **Scanner** (avec Touché) | B reçoit la carte de A **et** A reçoit celle de B, en un seul contact |
| **Montrer ma carte** | Android **sans** l'app | B voit « Ajouter le contact » (vCard) |
| **Montrer ma carte** | iPhone avec une app de lecture NFC | L'iPhone lit la vCard |
| QR code | iPhone ou n'importe quel téléphone | Scan avec l'appareil photo, puis ajout du contact |
| **Scanner** | Carte ou sticker NFC (Popl, lien...) | Enregistré dans Rencontres |

Conseils pour le test : dos contre dos, les deux écrans **allumés et déverrouillés**, et tu gardes 1 à 2 secondes. Sur la plupart des téléphones, l'antenne NFC est au milieu du dos, près de l'appareil photo.

## Vie privée

- Le téléphone ne répond **que** quand tu appuies sur « Montrer ma carte », et seulement pendant 90 secondes. Personne ne peut te lire à ton insu.
- Les infos marquées d'un cadenas ne quittent jamais le téléphone.
- Pas de compte, pas de serveur : tout reste sur le téléphone.

## Organisation du code

```
app/src/main/java/be/touche/app/
├── MainActivity.kt          Lance l'app, allume/éteint le lecteur NFC
├── data/
│   ├── Models.kt            Profil, champ, rencontre, format échangé (JSON)
│   ├── VCard.kt             Création/lecture de vCard (compatibilité universelle)
│   └── Store.kt             Sauvegarde locale (fichiers JSON)
├── nfc/
│   ├── Protocol.kt          Le protocole : tag Type 4 + commande PUSH pour l'échange
│   ├── ToucheHceService.kt  Le téléphone se fait passer pour une carte NFC
│   ├── NfcReader.kt         Mode Scanner : lit l'autre et lui renvoie ta carte
│   └── NfcState.kt          État du scan, disponibilité du NFC
└── ui/                      Écrans (Jetpack Compose) : Partager, Profils, Rencontres, QR
```

## Limites connues de la v0.1

- Pour partager en NFC, il faut un Android avec HCE (presque tous depuis 2015). L'**iPhone** peut recevoir, mais pour envoyer il doit passer par le QR code (restriction d'Apple).
- Un iPhone **sans** app ne réagit pas toujours à une vCard lue en NFC. La V1 ajoutera un lien web lisible par tous.
- Pas encore de lieu de rencontre, de rappels, de mode Événement ni de cartes qui se mettent à jour : c'est prévu dans la feuille de route du dossier.

## Prochaines étapes

1. Tester sur 2 à 3 téléphones différents (Samsung, Pixel, Xiaomi...) et noter ce qui coince.
2. Ajouter le lieu et le rappel de relance dans Rencontres.
3. Ajouter le lien web (page de carte) pour les iPhone sans app.
4. Faire tester par 12 personnes pendant 14 jours (obligatoire pour publier sur Google Play avec un compte perso).
