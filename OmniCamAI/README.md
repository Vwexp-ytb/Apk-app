# OmniCam AI

Projet Android natif indépendant, initialisé à partir du cahier des charges du dépôt parent. Cette application ne dépend pas de l’archive OpenCam.

## Stack
- Kotlin, Jetpack Compose, Material 3
- CameraX pour l’aperçu et la capture photo
- Java 17, Gradle 8.7, Android Gradle Plugin 8.6.1

## État actuel — MVP initial
- Aperçu caméra en direct avec permission runtime
- Capture JPEG enregistrée dans `Pictures/OmniCam`
- Bascule caméra avant/arrière
- Contrôle flash, grille de composition et sélecteur de modes
- Interface sombre adaptée à une expérience caméra premium

## Compilation
Ouvrir le dossier `OmniCamAI` dans Android Studio avec JDK 17, puis lancer la tâche `app:assembleDebug`.

Le workflow GitHub Actions `.github/workflows/build-omnicam.yml` compile ce projet et publie l’APK debug en artefact.

## Roadmap
1. Stabiliser la capture et ajouter une galerie intégrée
2. Exposer les capacités matérielles (ISO, temps d’exposition, mise au point, balance des blancs)
3. Vérifier le support RAW/DNG par appareil et ajouter l’export quand disponible
4. Ajouter un pipeline HDR multi-images puis un mode nuit
5. Ajouter assistance de composition et outils de retouche

## Limites connues
Les libellés HDR+ et IA sont pour l’instant des indicateurs d’interface : aucun moteur de fusion multi-frame ni modèle IA n’est encore embarqué. Les modes Nuit, Portrait et Pro sont des points d’entrée visuels, pas encore des pipelines de capture spécialisés.
