# Installer Touché sur ton téléphone, sans Android Studio

GitHub (gratuit) compile l'app pour toi en ligne. Ensuite, tu télécharges le fichier `Touche.apk` sur ton téléphone et tu l'installes. Compte environ 10 minutes la première fois.

## Étape 1 : créer le projet sur GitHub (sur ton ordi)

1. Va sur **https://github.com** et crée un compte gratuit (ou connecte-toi).
2. En haut à droite, clique sur **+**, puis sur **New repository**.
   - Nom : `touche`
   - Coche **Public**, c'est le plus simple pour télécharger l'APK depuis ton téléphone.
   - Clique sur **Create repository**.
3. Sur la page qui s'affiche, clique sur le lien **uploading an existing file**.
4. Dézippe `Touche-v0.1.zip` sur ton ordi et ouvre le dossier `Touche`.
   Sélectionne **tout ce qu'il y a dedans** (`app`, `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, les fichiers `.md`...) et fais-le **glisser** dans la page GitHub.
5. Attends que tous les fichiers soient chargés, puis clique sur **Commit changes** en bas.

## Étape 2 : ajouter la « machine à compiler »

Le dossier `.github` est caché sur ton ordi. On le crée donc à la main :

1. Dans ton projet GitHub : **Add file**, puis **Create new file**.
2. Dans le nom du fichier, tape exactement : `.github/workflows/build.yml`
3. Colle dedans le contenu du fichier `build.yml` ci-dessous.
4. Clique sur **Commit changes**.

```yaml
name: Construire l'APK

on:
  push:
    branches: [main, master]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.11.1"

      - name: Compiler l'app
        run: gradle assembleDebug --stacktrace

      - name: Renommer l'APK
        run: cp app/build/outputs/apk/debug/app-debug.apk Touche.apk

      - name: Publier l'APK (téléchargeable depuis le téléphone)
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v0.1.${{ github.run_number }}
          name: Touché v0.1.${{ github.run_number }}
          body: "Télécharge **Touche.apk** ci-dessous depuis ton téléphone Android, puis ouvre-le pour l'installer."
          files: Touche.apk
          make_latest: true
```

## Étape 3 : attendre la compilation (environ 5 minutes)

- Clique sur l'onglet **Actions** de ton projet. Tu vois « Construire l'APK » tourner (rond jaune).
- **Coche verte** : c'est prêt.
- **Croix rouge** : clique dessus, puis sur « build », copie le message d'erreur (surtout les lignes qui contiennent `e:` ou `error`) et envoie-le-moi. Je corrige et tu n'auras qu'à relancer.

## Étape 4 : installer sur ton téléphone

1. Sur ton **téléphone**, ouvre `https://github.com/TON-PSEUDO/touche/releases`.
2. Touche **Touche.apk** pour le télécharger.
3. Ouvre le fichier. Android te demande d'autoriser l'installation d'applications inconnues pour ton navigateur : accepte, c'est normal pour une app qui ne vient pas du Play Store.
4. Si Play Protect affiche un avertissement, touche **Plus de détails**, puis **Installer quand même**. L'app est à toi, elle n'est simplement pas encore sur le Play Store.

C'est fait. Fais pareil sur le téléphone d'un pote pour tester l'échange.

## Les mises à jour

Quand je te donne une nouvelle version d'un fichier, remplace-le sur GitHub (ouvre le fichier, crayon ✏️, colle, **Commit**). GitHub recompile tout seul, et la nouvelle version apparaît dans **Releases**. Tu l'installes par-dessus l'ancienne, sans rien perdre.
