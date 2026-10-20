# Audit des licences et dépendances — OmniCam AI

État : **à vérifier avant toute diffusion publique**. Cette liste est l'inventaire des dépendances directes déclarées dans `app/build.gradle.kts`, pas une certification de licence. La CI conserve aussi la sortie Gradle `releaseRuntimeClasspath` pour examiner les dépendances transitives.

## Dépendances directes déclarées

| Composant | Version déclarée | Contrôle avant publication |
|---|---|---|
| Android Gradle Plugin | 8.6.1 | Vérifier licence et version supportée |
| Kotlin Android / Compose compiler plugin | 2.0.20 | Vérifier licences des composants et du compilateur |
| Compose BOM | 2024.10.01 | Examiner les versions Compose résolues |
| AndroidX Core KTX | 1.15.0 | Confirmer notice de licence applicable |
| Activity Compose | 1.9.3 | Confirmer notice de licence applicable |
| Lifecycle Runtime KTX / Compose | 2.8.7 | Confirmer notice de licence applicable |
| Jetpack Compose UI / Material 3 / Material Icons Extended | via BOM | Vérifier les licences des modules effectivement résolus |
| CameraX Core / Camera2 / Lifecycle / View / Video | 1.4.1 | Confirmer licences et notices |
| AndroidX ExifInterface | 1.3.7 | Confirmer notice de licence applicable |
| ML Kit Image Labeling | 17.0.9 | Examiner les conditions Google ML Kit et les dépendances transitives |
| ML Kit Selfie Segmentation | 16.0.0-beta6 | Examiner les conditions de la bibliothèque bêta et sa suitability pour une release |
| JUnit | 4.13.2 | Vérifier les conditions de distribution des outils de test |
| AndroidX Test Runner | 1.6.2 | Vérifier les conditions de distribution des outils de test |
| AndroidX Test Ext JUnit | 1.2.1 | Vérifier les conditions de distribution des outils de test |

## Procédure d'audit requise

1. Récupérer l'artefact `dependencies-release.txt` de la dernière CI verte et archiver le résultat avec la version candidate.
2. Produire un inventaire complet incluant les dépendances transitives, les plugins Gradle et les GitHub Actions utilisés.
3. Vérifier la licence et les conditions d'utilisation à la source officielle de chaque composant ; relever les notices, attributions, obligations de redistribution et restrictions applicables.
4. Vérifier spécialement les bibliothèques ML Kit, les composants bêta, les modèles embarqués et les conditions de traitement des données.
5. Inclure les notices requises dans l'application ou dans un écran « Licences », selon les obligations applicables.
6. Enregistrer date, version auditée, URL des licences et personne ayant approuvé l'audit.
7. Répéter cet audit après chaque mise à jour de dépendance.

Ne pas conclure qu'une dépendance est autorisée à être redistribuée sur la seule base de son nom ou de son dépôt GitHub. Cet audit doit être terminé avant la diffusion commerciale.
