# OmniCam AI

Application Android native Kotlin / Jetpack Compose, CameraX et Camera2. Projet autonome dans le dépôt `Vwexp-ytb/Apk-app`.

## Interface

- Écran caméra sobre avec grand aperçu, déclencheur, galerie, vidéo et accès aux paramètres.
- Écrans séparés Paramètres, Capteurs et IA.
- Mode Photo, Pro, HDR, Nuit et Portrait.
- Mise au point tactile, grille, flash/lampe et zoom CameraX lorsqu’il est disponible.

## Fonctions implémentées dans le code

- **Capture photo** : JPEG vers `Pictures/OmniCam`.
- **HDR multi-images** : capture en trois expositions (−2 EV, EV courant, +2 EV lorsque la plage du téléphone le permet), puis fusion tonale. La fusion est volontairement plafonnée à environ 8 MP pour limiter la mémoire sur les capteurs 50/108/200 MP.
- **Nuit multi-images** : quatre captures avec exposition standard, moyennées pour réduire une partie du bruit. Ce pipeline ne fait pas encore d’alignement optique ; les sujets mobiles peuvent produire des images fantômes.
- **Portrait** : segmentation du sujet sur l’appareil avec ML Kit Selfie Segmentation et flou d’arrière-plan simplifié ; la photo originale est conservée si le traitement échoue.
- **RAW / DNG** : écran Camera2 dédié qui ouvre un module arrière exposant `RAW_SENSOR`, enregistre un DNG avec `DngCreator` et permet de régler ISO, temps de pose et distance de mise au point. Les valeurs acceptées dépendent du pilote ; certaines combinaisons de flux peuvent être refusées.
- **Vidéo** : enregistrement HD sans audio vers `Movies/OmniCam`. Le microphone n’est pas demandé.
- **IA** : classification de scène locale par ML Kit après capture.
- **Capteurs** : identifiants, niveau Camera2, taille de capteur, tailles JPEG/RAW annoncées, focales, ISO, plage d’exposition, autofocus, flash et stabilisation lorsque les informations sont exposées.
- **Tests JVM** : tests pour le calcul des niveaux d’exposition HDR et le mapping des curseurs.

## Limites à connaître

- Le HDR et le mode Nuit sont des fusions simples, pas un pipeline professionnel avec alignement multi-échelle, déghosting, estimation de mouvement et calibration de couleurs.
- Le flou Portrait est un effet de segmentation et de réduction de résolution de fond, pas un mode portrait optique natif du fabricant.
- L’écran RAW sélectionne parmi les identifiants arrière exposés par Camera2 qui annoncent RAW. **La sélection arbitraire de chaque sous-capteur physique d’un module logique n’est pas garantie** : elle exige que le constructeur expose et autorise ce flux.
- CameraX choisit les flux de photo standards compatibles ; un bouton 200 MP ou une taille annoncée par Camera2 ne garantit pas que le fabricant autorise cette résolution dans la combinaison aperçu/capture. Le pipeline HDR/Nuit est réduit en résolution pour éviter les erreurs mémoire.
- La vidéo est muette pour le moment. La retouche générative n’est pas implémentée.
- Une compilation réussie ne prouve pas le fonctionnement sur tous les téléphones. Les tests sur appareils réels, la stabilité thermique, le comportement en faible lumière, les combinaisons de flux, la politique de confidentialité, la signature Release et la validation Play Console restent requis avant une publication commerciale.

## Build et tests

Le workflow GitHub Actions `.github/workflows/build-omnicam.yml` exécute les tests JVM puis `gradle :app:assembleDebug --no-daemon` avec JDK 17. L’artefact de test s’appelle `OmniCamAI-debug`.

L’APK Debug n’est pas une version Release signée. Pour publier, configurez une clé de signature protégée dans GitHub Actions Secrets et ajoutez les vérifications de conformité et de confidentialité nécessaires.
