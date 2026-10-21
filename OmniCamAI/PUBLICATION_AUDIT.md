# Audit de publication et nettoyage — OmniCam AI

Date de revue : 21 octobre 2026  
Révision analysée : `c673bfed30b5c58f34150f4f56f1e3cabf12ec4b` (branche `main`).

Ce document distingue les constats vérifiés dans le dépôt des validations qui exigent un appareil réel, les informations de l’éditeur ou un audit de distribution. Une compilation verte ne suffit pas à déclarer l’application prête pour une boutique.

## État constaté

- Les tests unitaires JVM, `lintDebug`, `assembleDebug` et `assembleRelease` non signé ont réussi dans le workflow du 21 octobre 2026.
- Les tests instrumentés API 26 et API 35 étaient encore en cours au moment de cette revue. Vérifier leurs résultats avant de déclarer la CI entièrement verte.
- La version Release candidate générée par la CI est **non signée** ; elle n’est pas publiable telle quelle.
- La politique de confidentialité contient encore des champs « À renseigner » et n’a pas d’URL publique permanente.
- Le projet possède deux workflows de build : `build-omnicam.yml` pour OmniCam AI et `build.yml` pour l’ancien projet OpenCam extrait de `OpenCam.zip`. Ils ne construisent pas la même application : ne pas supprimer le second sans confirmer que l’ancien projet n’est plus maintenu.
- `MainActivity.kt` concentre l’essentiel de l’interface et de la logique caméra dans un fichier d’environ 960 lignes. Il faut le découper progressivement, avec tests à chaque étape, plutôt qu’une réécriture globale.

## Bloquants avant publication — P0

- [ ] Renseigner l’identité de l’éditeur et un contact confidentialité ; publier la politique sur une URL stable et accessible sans connexion.
- [ ] Compléter la fiche de sécurité des données de la boutique selon le comportement réel des bibliothèques ML Kit et du binaire Release.
- [ ] Configurer les secrets de signature Android dans GitHub Actions, protéger la clé et vérifier la signature de l’APK/AAB final.
- [ ] Produire et tester un **Android App Bundle (AAB)** signé pour Google Play ; l’APK Release candidate actuel est explicitement non signé.
- [ ] Vérifier le nom de package définitif, la version `versionCode` / `versionName`, les règles de mise à jour et la conservation de la même clé de signature.
- [ ] Confirmer l’état final des tests instrumentés sur API 26 et API 35 ; ajouter au moins un appareil physique récent et un appareil d’entrée de gamme aux tests de recette.
- [ ] Tester manuellement les permissions, l’arrêt/reprise de l’application, les rotations, les interruptions caméra (appel entrant/autre application), le stockage presque plein et le refus d’autorisation.
- [ ] Confirmer les exigences de niveau API cible de Google Play au moment de la soumission.

## Qualité fonctionnelle et caméra — P1

- [ ] Vérifier les combinaisons réellement supportées par le matériel : caméra sélectionnée, flash, zoom, ISO/exposition manuels, balance des blancs, vidéo UHD/FPS, RAW/DNG. Les capacités annoncées par Camera2 ne garantissent pas que chaque combinaison de flux fonctionne simultanément.
- [ ] Afficher les contrôles manuels uniquement quand les plages correspondantes sont disponibles. Si le matériel refuse une option, rétablir l’auto-exposition/balance des blancs et afficher un message utile.
- [ ] Tester le cycle de vie de la session Camera2 RAW : changement d’objectif, fermeture pendant une capture, destruction de l’activité, image RAW sans résultat de capture et libération de `ImageReader`/thread.
- [ ] Vérifier que les rafales interrompues n’abandonnent pas de fichiers vides ou partiels ; envisager de supprimer le fichier créé si une capture échoue avant l’écriture complète.
- [ ] Tester la galerie sur Android 10+ et Android 8/9 : autorisations, éléments supprimés depuis une autre application, partage sans application compatible, vidéos, EXIF absent ou corrompu.
- [ ] Valider l’orientation EXIF sur les quatre rotations et les orientations miroir, ainsi que la correspondance du masque de segmentation et de l’image source.
- [ ] Faire des essais portrait sur cheveux, lunettes, faible lumière, plusieurs personnes et arrière-plans complexes. Vérifier le rendu des formes carré/arrondi/diamant et la mémoire sur les images haute résolution.
- [ ] Décider explicitement si l’analyse de scène doit être désactivée par défaut pour la confidentialité et l’autonomie ; expliquer ce qui est analysé et à quel moment.
- [ ] Vérifier que la capture « silencieuse » respecte les exigences et comportements du système et des appareils ciblés ; ne pas promettre une absence universelle de son, certains appareils imposant le son d’obturateur.
- [ ] Vérifier la sémantique des réglages : une option doit avoir un effet visible, être persistée si nécessaire et être masquée si elle n’est pas réellement prise en charge.

## Sécurité, confidentialité et conformité — P1

- [ ] Contrôler le manifeste fusionné du binaire final, pas seulement le manifeste source, après ajout ou mise à jour de dépendances.
- [ ] Confirmer les permissions minimales. La permission `WRITE_EXTERNAL_STORAGE` est limitée à API 28 ; vérifier les parcours MediaStore et les besoins de lecture sur chaque version Android prise en charge.
- [ ] Examiner les licences et obligations de toutes les dépendances transitives. `THIRD_PARTY_AUDIT.md` décrit une procédure, mais ne constitue pas une vérification juridique effective.
- [ ] Vérifier l’absence de secrets, clés privées, logs sensibles, identifiants et données de test dans le dépôt et dans les artefacts.
- [ ] Définir la politique de conservation des photos, des métadonnées EXIF et des fichiers temporaires ; tester l’annulation et l’échec d’écriture.
- [ ] Faire correspondre la politique de confidentialité, la fiche boutique, les captures d’écran et les fonctionnalités réellement présentes dans le binaire.

## Expérience utilisateur et stabilité — P2

- [ ] Ajouter un état de chargement et des erreurs compréhensibles pendant l’initialisation caméra, l’analyse IA, l’écriture de média et l’export RAW.
- [ ] Éviter que les réglages persistés invalides (objectif absent, résolution/FPS non disponibles) ne provoquent un écran incohérent au démarrage.
- [ ] Ajouter des tests unitaires aux transformations pures : choix des résolutions/FPS, conversion des curseurs, validation des valeurs et calculs d’exposition.
- [ ] Ajouter des tests instrumentés pour l’ouverture/fermeture de l’écran, les permissions refusées et les transitions photo/vidéo.
- [ ] Mesurer le temps de démarrage, la consommation mémoire sur photos haute résolution et la chauffe lors de rafales/vidéos.
- [ ] Vérifier l’accessibilité : tailles de cible, descriptions d’icônes, contraste, taille de texte et navigation avec TalkBack.
- [ ] Fournir des captures d’écran réelles et à jour, une description de boutique, une classification d’âge et des instructions d’assistance.

## Nettoyage du code : décisions prudentes

- Les imports réellement inutilisés identifiés dans `MainActivity.kt` peuvent être supprimés sans changer le comportement.
- La logique de rendu portrait contenait un calcul de forme qui ne s’appliquait qu’aux pixels déjà classés comme arrière-plan et mélangeait alors l’image source avec la mosaïque. Le calcul doit appliquer la forme au fond mosaïqué, puis recomposer le sujet, sans toucher au masque de segmentation.
- Ne pas supprimer `CameraCapabilities.kt`, `CameraMath.kt`, `PhotoBurst.kt`, `MediaGallery.kt`, `PortraitProcessor.kt` ou `RawCaptureActivity.kt` : ces fichiers portent des responsabilités distinctes et sont appelés par les parcours existants.
- Ne pas supprimer le workflow OpenCam tant que l’ancien projet `OpenCam.zip` doit rester constructible.
- Prochaine étape de refactorisation recommandée : extraire les préférences, les fonctions de sélection des caméras et la gestion de capture de `MainActivity.kt` une par une, en conservant les signatures et en validant la CI après chaque extraction.

## Critères de sortie

La publication est prête uniquement quand : lint et tests JVM réussissent ; les tests instrumentés réussissent ; un AAB signé est généré et vérifié ; la recette physique est terminée ; la politique et les déclarations boutique sont complètes ; les métadonnées de version et la procédure de mise à jour sont validées.
