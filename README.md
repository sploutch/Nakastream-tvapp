# Nakastream TV

Application Android TV pour [Nakastream](https://naka.cx), qui n'existe qu'en PWA (site installable) et n'a pas d'APK officiel.

L'APK affiche le site en plein écran dans une WebView et le rend utilisable avec la télécommande :

- **Curseur** déplacé avec les flèches, **OK** pour cliquer (le clavier de la TV s'ouvre sur les champs texte)
- **Défilement** en poussant le curseur contre le bord de l'écran, ou d'un écran entier avec **CH+ / CH−**
- **Retour** : quitte le plein écran, ferme l'onglet ouvert, ou revient à la page précédente (deux appuis pour quitter)
- **Touches média** : lecture/pause, avance et retour rapides
- **Nouveaux onglets** (pubs, liens `target=_blank`) ouverts par-dessus la page, qui reste chargée en dessous
- Session et cookies conservés entre deux lancements

## Compiler

Prérequis : Windows, Android SDK (build-tools 36, plateforme android-36) et JDK 21. Les chemins sont définis en haut de `build.ps1`.

```powershell
powershell -ExecutionPolicy Bypass -File .\make-icons.ps1   # icône et bannière TV depuis logo.png
powershell -ExecutionPolicy Bypass -File .\build.ps1        # produit Nakastream-TV.apk
```

Le script compile sans Gradle (aapt2, javac, d8, zipalign, apksigner). Au premier lancement, il génère une clé de signature dans `nakastream.keystore`. Ce fichier n'est pas versionné : garde-le, car il faut la même clé pour installer une mise à jour par-dessus l'ancienne version.

## Installer sur la TV

Via l'application Downloader, utilisez le code : 7096031

## Structure

| Fichier | Rôle |
|---|---|
| `app/src/.../MainActivity.java` | WebView, curseur, touches de la télécommande, onglets |
| `app/src/.../CursorView.java` | Dessin du curseur |
| `app/AndroidManifest.xml` | Déclaration de l'application pour le lanceur Android TV |
| `build.ps1` / `make-icons.ps1` | Compilation et génération des icônes |
