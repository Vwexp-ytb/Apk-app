# OmniCam AI

Application Android native Kotlin / Jetpack Compose avec CameraX et inspection Camera2. Projet autonome dans ce dépôt.

## Fonctions présentes dans le code
- Aperçu caméra, autorisation runtime et bascule avant/arrière.
- Capture JPEG vers `Pictures/OmniCam`.
- Grille de composition, lampe torche quand elle est disponible et boutons de zoom CameraX 0,5× / 1× / 2× si le rapport est accepté par l'objectif actif.
- Mise au point et mesure d'exposition par toucher sur l'aperçu.
- Mode Pro avec réglage réel de la compensation d'exposition EV lorsque CameraX l'expose.
- Rapport matériel Camera2 par identifiant de caméra : orientation avant/arrière, niveau matériel, dimensions de capteur déclarées, plus grande taille JPEG annoncée, tailles RAW annoncées, focales, flash, modes autofocus, plage ISO/exposition, stabilisation optique, tailles JPEG et identifiants de caméras physiques.
- Ouverture de la galerie système.
- Classification d'image locale avec ML Kit après la capture.

## Compilation et APK
Ouvrir `OmniCamAI` dans Android Studio avec JDK 17 ou utiliser le workflow GitHub Actions `.github/workflows/build-omnicam.yml`. Le workflow lance `gradle :app:assembleDebug --no-daemon` et publie l'APK debug comme artefact `OmniCamAI-debug`.

## Limites connues — ne pas considérer comme prêt à publier
- Le rapport de capacités reflète les formats annoncés par le HAL Camera2 ; il ne garantit pas que chaque format se combine avec chaque flux.
- La liste de caméras logiques et les identifiants physiques exposés par le constructeur peuvent différer des modules visibles dans l'application caméra d'origine.
- Le zoom CameraX ne sélectionne pas explicitement chaque objectif physique. Les paliers 0,5×/1×/2× sont désactivés s'ils ne sont pas acceptés par le rapport de zoom de l'objectif actif.
- Le fait qu'un capteur annonce 200 MP ne garantit pas qu'un flux JPEG 200 MP soit accessible à une application tierce. La résolution maximale affichée est celle exposée par Camera2.
- Les fonctions HDR multi-images, nuit computationnelle, fusion de bracketing, portrait avec segmentation, RAW/DNG exporté, commandes manuelles ISO/temps de pose/focus, vidéo, retouche générative, détection de flou et galerie intégrée ne sont pas encore implémentées. La classification ML Kit n'est pas une retouche générative.
- Aucun test sur appareil physique ne peut être remplacé par la compilation CI. Tester plusieurs constructeurs, objectifs et versions Android avant la publication.

## Préparation production restante
Tests unitaires et instrumentés, tests de permissions/cycle de vie/rotation, gestion de mémoire et de chauffe, accessibilité, politique de confidentialité et déclaration Play Console, version de production et signature release stockée en secrets CI, validation des licences et test sur appareils réels.
