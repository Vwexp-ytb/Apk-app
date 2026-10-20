# OmniCam AI

Application Android native Kotlin/Jetpack Compose, indépendante de l'archive OpenCam.

## Fonctionnalités implémentées
- Aperçu caméra CameraX et demande d'autorisation à l'exécution.
- Capture JPEG dans `Pictures/OmniCam`.
- Bascule caméra avant/arrière.
- Contrôle de zoom appliqué via CameraX lorsque le rapport est supporté par la caméra active.
- Modes PHOTO (priorité à la latence), PRO et NUIT (priorité à la qualité de capture CameraX, sans prétendre effectuer un traitement nocturne multi-image).
- Grille de composition et commande lampe torche arrière.
- Ouverture de la galerie système.
- Étiquetage d'image sur l'appareil après capture avec ML Kit Image Labeling : catégories et scores de confiance estimés.

## Stack
- Kotlin, Jetpack Compose, Material 3
- CameraX 1.4.1
- ML Kit Image Labeling (modèle embarqué)
- Java 17, Gradle 8.7, Android Gradle Plugin 8.6.1

## Compilation
Ouvrir le dossier `OmniCamAI` dans Android Studio avec JDK 17, puis lancer `gradle :app:assembleDebug`. Le workflow GitHub Actions `.github/workflows/build-omnicam.yml` compile le projet et publie l'APK debug comme artefact.

## Limites techniques importantes
Le matériel Android varie selon le fabricant et le modèle. Le zoom CameraX ne garantit pas le basculement explicite entre chaque module physique. La résolution maximale, dont 200 MP, dépend des identifiants de caméra exposés par le fabricant, des flux autorisés, de la version Android et du pipeline Camera2/CameraX ; elle n'est pas garantie par ce MVP.

HDR multi-image, fusion de bracketing, mode nuit computationnel, segmentation/détourage portrait, RAW/DNG, réglages manuels ISO/temps d'exposition/mise au point/balance des blancs, vidéo, galerie intégrée, retouche IA et synchronisation multi-capteurs ne sont pas encore implémentés. L'étiquetage ML Kit est une classification de scène basique, pas un générateur d'image ni un outil de retouche.

## Avant publication Play Store
- Valider sur une matrice réelle d'appareils et de versions Android, notamment les modules ultra-grand-angle/téléobjectif et les capteurs haute résolution.
- Ajouter des tests unitaires/instrumentés, tests de rotation, de permissions, de cycle de vie, de mémoire et de performance thermique.
- Configurer la signature release sécurisée via secrets CI, la version de production, les règles de confidentialité et la fiche Play Store.
- Vérifier les politiques de données, la déclaration de sécurité des données et les permissions minimales.
