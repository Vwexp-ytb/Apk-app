# OmniCam AI

Application Android native Kotlin / Jetpack Compose, CameraX et inspection Camera2. Projet autonome dans ce dépôt.

## Interface

- Écran caméra minimaliste : grand aperçu, déclencheur central, accès galerie, sélection rapide du zoom accepté par l’objectif et modes Photo / Pro / Nuit / Portrait.
- Écran **Paramètres** séparé : flash/lampe, grille de composition, caméra avant/arrière, accès aux capacités des capteurs et à la galerie. L’aperçu n’est plus encombré de tous les réglages.
- Écran **Capteurs** dédié avec capacités exposées par Camera2.
- Écran **IA** dédié affichant le résultat de la dernière analyse et l’état des fonctions disponibles.
- Mise au point tactile sur l’aperçu et correction EV réellement appliquée en mode Pro lorsque le pilote l’expose.

## Fonctions actuellement implémentées

- Aperçu caméra, demande runtime de permission et bascule avant/arrière.
- Capture JPEG vers `Pictures/OmniCam`.
- Zoom CameraX 0,5× / 1× / 2× uniquement si le rapport est accepté par la caméra active.
- Lampe torche sur la caméra arrière lorsque prise en charge.
- Grille de composition activable.
- Mode Pro : capture privilégiant la qualité et correction EV si disponible.
- Mode Nuit : capture privilégiant la qualité. **Ce n’est pas encore un mode nuit computationnel multi-images.**
- Rapport Camera2 : identifiants caméra, orientation, niveau matériel, dimensions déclarées, plus grande taille JPEG annoncée, tailles RAW annoncées, focales, flash, autofocus, plages ISO/exposition, stabilisation optique, tailles JPEG et identifiants physiques lorsqu’exposés.
- Analyse de scène locale après capture via ML Kit Image Labeling ; aucun envoi de photo vers un serveur n’est effectué par cette analyse.
- Ouverture de la galerie système.

## Limites importantes — pas encore prêt pour publication commerciale

- Le mode Portrait est pour le moment un choix d’interface, sans segmentation du sujet ni flou d’arrière-plan calculé.
- HDR multi-images, bracketing et fusion, mode nuit computationnel, capture RAW/DNG, sélection explicite de chaque caméra physique, commandes manuelles ISO/temps de pose/mise au point, vidéo, détection avancée de flou et retouche générative ne sont pas encore implémentés.
- L’inspection d’un capteur 200 MP ou d’un ultra-grand-angle n’assure pas qu’une application tierce puisse exploiter toute sa résolution ni sélectionner ce module individuellement. Cela dépend du HAL et des flux autorisés par le fabricant.
- Le niveau de zoom CameraX ne garantit pas le passage explicite à un objectif physique. Les niveaux indisponibles sont refusés plutôt que simulés.
- Les résultats de compilation ne remplacent pas les essais sur appareils physiques de différents fabricants. Aucun état de publication ne doit être déclaré avant les tests fonctionnels, de sécurité, de chauffe, de permissions et de cycle de vie.

## Compilation et APK

Ouvrir `OmniCamAI` dans Android Studio avec JDK 17 ou utiliser GitHub Actions, workflow `.github/workflows/build-omnicam.yml`. La tâche CI est `gradle :app:assembleDebug --no-daemon` et l’artefact de test est nommé `OmniCamAI-debug`.

Pour publier, il reste à ajouter une configuration release signée avec une clé protégée (GitHub Actions Secrets), des tests automatisés et sur appareil, la politique de confidentialité et la validation Play Console.
