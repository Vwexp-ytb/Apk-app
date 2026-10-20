# OmniCam AI

Application Android native Kotlin / Jetpack Compose, CameraX et Camera2. Projet autonome dans le dépôt `Vwexp-ytb/Apk-app`.

## Interface

- Écran caméra sobre avec grand aperçu sans titre superposé, déclencheur, galerie intégrée, vidéo et accès rapide au flash et aux paramètres.
- Écrans séparés Paramètres, Capteurs et IA.
- Mode Photo, Pro, HDR, Nuit et Portrait.
- Mise au point tactile, grille, flash/lampe et zoom CameraX lorsqu’il est disponible.

## Fonctions implémentées dans le code

- **Capture photo** : JPEG/JPG ou PNG vers `Pictures/OmniCam`; le choix DNG ouvre le flux Camera2 RAW. L’écran Pro permet de demander la résolution maximale, HD ou 4K avec repli selon les tailles disponibles.
- **HDR multi-images** : capture en trois expositions (−2 EV, EV courant, +2 EV lorsque la plage du téléphone le permet), puis fusion tonale. La fusion est volontairement plafonnée à environ 8 MP pour limiter la mémoire sur les capteurs 50/108/200 MP.
- **Nuit multi-images** : quatre captures avec exposition standard, moyennées pour réduire une partie du bruit. Ce pipeline ne fait pas encore d’alignement optique ; les sujets mobiles peuvent produire des images fantômes.
- **Portrait** : segmentation du sujet sur l’appareil avec ML Kit Selfie Segmentation et fond légèrement pixelisé plutôt qu’un flou lisse ; la photo originale est conservée si le traitement échoue. Le panneau dédié permet d’afficher/masquer les réglages, choisir la taille des cellules (force), la forme (carrée, arrondie ou diamant), l’adoucissement du masque et le lissage visuel. Le rendu traite l’image par lignes pour réduire les allocations mémoire.
- **Mode Pro** : commandes Camera2 pour ISO et temps de pose en aperçu, température de couleur approximative réglable en kelvins, choix du format JPEG/JPG/PNG/DNG et résolution demandée. La disponibilité et le rendu des commandes manuelles dépendent du pilote du téléphone.
- **Autofocus** : autofocus continu pendant l’aperçu, mise au point par toucher et action rapide de mise au point/mesure au centre.
- **RAW / DNG** : écran Camera2 dédié qui ouvre un module arrière exposant `RAW_SENSOR`, enregistre un DNG avec `DngCreator` et permet de régler ISO, temps de pose et distance de mise au point. Les valeurs acceptées dépendent du pilote ; certaines combinaisons de flux peuvent être refusées.
- **Vidéo Pro** : choix de la qualité parmi les résolutions que Camera2 annonce et des fréquences d’images parmi les plages FPS exposées par le capteur ; la caméra peut appliquer un repli si le pilote ne permet pas la combinaison. Enregistrement sans audio vers `Movies/OmniCam`; le microphone n’est pas demandé.
- **Galerie intégrée** : affiche directement la dernière image dans l’application et évite d’ouvrir un sélecteur d’application externe.
- **IA désactivable** : l’analyse de scène ML Kit après capture peut être activée ou désactivée dans les paramètres.
- **IA** : classification de scène locale par ML Kit après capture.
- **Objectifs / capteurs** : sélecteur des identifiants caméra que Camera2 expose à l’application, avec facing, focale et plus grande taille JPEG annoncée ; écran de capacités détaillées (niveau Camera2, capteur, tailles JPEG/RAW, ISO, exposition, autofocus, flash et stabilisation). CameraX demande la résolution photo la plus élevée disponible, avec repli automatique si le fabricant refuse la combinaison de flux.
- **Tests JVM** : tests pour le calcul des niveaux d’exposition HDR et le mapping des curseurs.

## Limites à connaître

- La sélection d’objectif concerne les identifiants caméra que le fabricant expose séparément à Android. Les sous-capteurs physiques d’une caméra logique peuvent rester inaccessibles individuellement.
- Le HDR et le mode Nuit sont des fusions simples, pas un pipeline professionnel avec alignement multi-échelle, déghosting, estimation de mouvement et calibration de couleurs.
- L’effet Portrait est une segmentation et une pixelisation légère du fond, pas un mode portrait optique natif du fabricant.
- L’écran RAW sélectionne parmi les identifiants arrière exposés par Camera2 qui annoncent RAW. **La sélection arbitraire de chaque sous-capteur physique d’un module logique n’est pas garantie** : elle exige que le constructeur expose et autorise ce flux.
- CameraX choisit les flux de photo standards compatibles ; un bouton 200 MP ou une taille annoncée par Camera2 ne garantit pas que le fabricant autorise cette résolution dans la combinaison aperçu/capture. Le pipeline HDR/Nuit est réduit en résolution pour éviter les erreurs mémoire.
- La vidéo est muette pour le moment. La retouche générative n’est pas implémentée.
- Une compilation réussie ne prouve pas le fonctionnement sur tous les téléphones. Les tests sur appareils réels, la stabilité thermique, le comportement en faible lumière, les combinaisons de flux, la politique de confidentialité, la signature Release et la validation Play Console restent requis avant une publication commerciale.

## Build et tests

Le workflow GitHub Actions `.github/workflows/build-omnicam.yml` exécute les tests JVM puis `gradle :app:assembleDebug --no-daemon` avec JDK 17. L’artefact de test s’appelle `OmniCamAI-debug`.

L’APK Debug n’est pas une version Release signée. Pour publier, configurez une clé de signature protégée dans GitHub Actions Secrets et ajoutez les vérifications de conformité et de confidentialité nécessaires.


## Générer et installer une nouvelle version

Le workflow de publication `.github/workflows/release-omnicam.yml` produit un **APK Release signé** quand un tag `vX.Y.Z` est poussé (ex. `v0.2.0`). Il exécute d'abord les tests JVM, puis publie l'APK dans les Releases GitHub et comme artefact Actions. Le numéro de version Android (`versionCode`) augmente à chaque exécution du workflow.

### Configuration obligatoire une seule fois

1. Créer une clé de signature Android/keystore dédiée à OmniCam AI et la sauvegarder dans plusieurs emplacements sécurisés hors du dépôt. **Ne jamais committer le keystore ni son mot de passe.** Si cette clé est perdue, Android ne permettra généralement pas d'installer les futures versions par-dessus l'application signée avec cette clé.
2. Dans **GitHub → Settings → Secrets and variables → Actions → New repository secret**, ajouter les secrets suivants :
   - `ANDROID_KEYSTORE_BASE64` : contenu Base64 du fichier keystore (sur macOS/Linux : `base64 -w 0 omnicam-release.jks`; sur macOS, utiliser `base64 omnicam-release.jks | tr -d '\\n'`).
   - `ANDROID_KEYSTORE_PASSWORD` : mot de passe du keystore.
   - `ANDROID_KEY_ALIAS` : alias de la clé.
   - `ANDROID_KEY_PASSWORD` : mot de passe de la clé.
3. Garder ces secrets privés. Ne pas les mettre dans le code, le README, les issues ou les logs.

### Publier une mise à jour

1. Vérifier les changements et les tests.
2. Créer et pousser un tag de version, par exemple : `git tag v0.2.0 && git push origin v0.2.0`.
3. Ouvrir l'onglet **Actions** et attendre que « Build and publish OmniCam AI release » soit vert.
4. Ouvrir **Releases** et télécharger `OmniCamAI-0.2.0.apk`.
5. Installer l'APK sur le téléphone. Android peut demander d'autoriser l'installation depuis la source utilisée. Installer par-dessus l'application existante conserve normalement ses données si le package et la clé de signature sont identiques.

**Important :** le dépôt est actuellement privé. Les Releases et artefacts ne sont donc pas des liens de téléchargement publics. Cette livraison prépare des mises à jour signées téléchargeables par les personnes qui ont accès au dépôt ; elle **n'ajoute pas encore un bouton de mise à jour automatique dans l'application**. Pour un vérificateur de mise à jour intégré, il faudra héberger publiquement et durablement un manifeste de version et l'APK (ou utiliser Google Play In-App Updates). Android exigera toujours le consentement de l'utilisateur pour installer un APK hors Play Store.

## Liste de contrôle avant mise en production

### Bloquants avant une première diffusion
- [ ] Configurer et sauvegarder la clé de signature de production, puis vérifier qu'une mise à jour signée s'installe par-dessus une version précédente sans perte de données.
- [ ] Définir la distribution : Google Play (recommandé pour les mises à jour), ou téléchargement direct depuis un hébergement fiable.
- [ ] Vérifier les droits d'accès, la politique de confidentialité et les déclarations Google Play Data safety. Documenter clairement que l'analyse d'image ML Kit se fait sur l'appareil et vérifier le comportement exact des dépendances.
- [ ] Tester installation propre, mise à jour, retour après échec, manque d'espace, rotation, reprise après fermeture forcée et redémarrage.
- [ ] Tester sur plusieurs appareils réels et versions Android : caméras avant/arrière, autorisations, flash, autofocus, zoom, photos, galerie, vidéo, températures et mémoire.
- [ ] Examiner les avertissements et exigences Play Console (target SDK, fiche Store, classification du contenu, coordonnées de support, captures d'écran, icône, déclaration publicitaire si applicable).
- [ ] Vérifier les licences et obligations des bibliothèques/dépendances et publier les mentions requises.
- [ ] Ne jamais distribuer une APK Debug comme version de production.

### Qualité photo/vidéo et robustesse
- [ ] Comparer le rendu sur appareils réels : exposition, balance des blancs, couleurs, HDR, nuit, portraits, scènes mobiles et faible luminosité.
- [ ] Ajouter alignement des images et déghosting pour HDR/Nuit ; tester les sujets mobiles et les mouvements de main.
- [ ] Valider chaque combinaison de résolution, FPS, format RAW/DNG et flux simultanés ; proposer des replis et messages explicites quand le matériel refuse une option.
- [ ] Améliorer la segmentation portrait sur cheveux, lunettes, contours fins, personnes multiples et arrière-plans complexes ; vérifier la cohérence réelle des options de forme/lissage.
- [ ] Finaliser l'intégration du RAW/DNG dans le flux de capture principal si l'expérience souhaitée est un seul écran caméra.
- [ ] Ajouter enregistrement vidéo avec audio uniquement après consentement clair, indicateur visible et gestion correcte de la permission microphone, si cette fonction est souhaitée.
- [ ] Mesurer mémoire, chauffe, batterie, latence, taille des photos et stabilité lors de longues sessions ; tester les capteurs haute résolution.
- [ ] Vérifier l'accessibilité : contraste, taille de texte, TalkBack, libellés des boutons et cibles tactiles.

### Sécurité, confidentialité et maintenance
- [ ] Ajouter un écran confidentialité et une adresse de contact ; expliquer les permissions et le stockage des médias.
- [ ] Vérifier qu'aucune image ni métadonnée n'est envoyée à un serveur sans information et consentement ; décider si les données EXIF/localisation sont conservées.
- [ ] Mettre en place suivi des crashs respectueux de la vie privée, journaux sans données sensibles et procédure de réponse aux bugs.
- [ ] Épingler/mettre à jour les dépendances, surveiller les avis de sécurité et automatiser les tests/builds sur chaque changement.
- [ ] Ajouter tests unitaires et tests instrumentés pour permissions, rotation, cycle de vie caméra, stockage, fusion HDR/Nuit et échec des traitements.
- [ ] Vérifier sauvegarde/restauration, migrations de préférences et compatibilité ascendante des paramètres.
- [ ] Préparer notes de version, versionnement cohérent, canal bêta, procédure de retour arrière et support utilisateur.

### Fonctions produit à envisager après stabilisation
- [ ] Retouche générative IA (non implémentée actuellement) avec indication claire des modifications générées.
- [ ] Galerie complète : parcourir, supprimer, partager, favoris et détails EXIF.
- [ ] Préréglages photo, histogramme, zébras, focus peaking et verrouillage exposition/mise au point.
- [ ] RAW+JPEG, contrôle du ratio, minuterie, rafale et retardateur.
- [ ] Tests de performance comparatifs et profils de compatibilité par modèle d'appareil.

Une compilation réussie ne remplace pas les essais sur appareils physiques, la validation de sécurité/confidentialité ni l'acceptation de la boutique.


## Travaux de mise en production ajoutés

- Les réglages caméra (mode, objectif, grille, flash, format, IA, ISO, exposition, température de couleur, vidéo et paramètres Portrait) sont maintenant conservés localement entre les lancements.
- L'écran **Confidentialité** décrit les permissions et traitements connus de cette version. Le fichier [PRIVACY_POLICY.md](PRIVACY_POLICY.md) reste un brouillon : il faut renseigner le responsable et le contact, vérifier le binaire/dépendances, puis publier la politique à une URL publique avant diffusion.
- L'écran **À propos** affiche la version, Android et le modèle du téléphone pour faciliter les rapports de compatibilité.
- La galerie OmniCam liste les fichiers dont le nom commence par `OmniCam`, permet de partager un média avec l'API Android et demande confirmation avant suppression. Elle ne cherche pas à parcourir toute la galerie personnelle.
- Une icône d'application dédiée est configurée. Le manifeste désactive le trafic HTTP non chiffré et n'ajoute pas la permission INTERNET.
- La CI exécute désormais les tests JVM, le lint, une compilation Debug, une compilation Release candidate non signée et génère un inventaire des dépendances.
- Le workflow de publication valide le keystore et l'alias, construit l'APK signé, vérifie sa signature puis publie l'APK dans une GitHub Release.

## Ce qui bloque encore la publication publique

1. Configurer les quatre secrets de signature GitHub Actions et tester l'installation par-dessus une version antérieure signée avec **la même clé**. Aucune clé privée n'est stockée dans le dépôt.
2. Remplacer les champs responsable/contact de la politique de confidentialité et publier son URL publique.
3. Examiner la sortie lint, l'inventaire des dépendances et les licences/transferts de données de chaque bibliothèque, puis remplir Google Play Data safety.
4. Fournir une adresse de support, préparer la fiche de boutique, la classification d'âge et les captures d'écran réelles.
5. Tester sur plusieurs téléphones physiques et versions Android. La CI Linux ne peut pas valider la chauffe, l'autofocus physique, les performances capteur, le comportement OEM ou la conservation effective des données lors d'une mise à jour.
6. Le workflow CI crée un candidat Release **non signé** uniquement pour vérifier que le variant compile. Seul le workflow de publication avec les secrets de production peut créer un APK distribuable signé.
7. La galerie ne peut partager/supprimer que les fichiers qu'Android rend accessibles et dont le nom commence par `OmniCam`. Les fichiers existants qui n'ont pas ce préfixe ne sont pas répertoriés.
8. Le canal de mise à jour automatique intégré à l'application n'est pas implémenté ; pour l'instant, il faut télécharger puis installer l'APK publié (ou distribuer l'application via Google Play).

Les workflows automatisés sont un filet de sécurité, pas une preuve de compatibilité universelle ni une certification juridique.


- **Retardateur** : désactivé, 3 secondes ou 10 secondes ; la sélection est conservée.
- **Rafale** : 3 ou 5 photos JPEG séquentielles en mode Photo, enregistrées dans MediaStore sans charger toutes les images en mémoire.
- **Galerie étendue** : liste les fichiers OmniCam, permet de les ouvrir, partager, supprimer après confirmation et consulter les principales métadonnées EXIF.
- **Profil de compatibilité** : l'écran Capteurs peut partager un rapport avec fabricant, modèle, version Android et capacités Camera2 annoncées.
- **Fusion Nuit** : la fusion utilise une moyenne tronquée des échantillons intermédiaires par canal pour réduire l'influence de valeurs aberrantes. Cela ne remplace pas l'alignement des images ni le déghosting optique.
- **Tests instrumentés** : un test vérifie le manifeste fusionné, la permission caméra, l'absence de permission réseau demandée et les métadonnées de version. Le workflow tente de l'exécuter sur des émulateurs API 26 et API 35.

Consulter aussi [la politique de confidentialité provisoire](PRIVACY_POLICY.md) et [la checklist de mise en production](PRODUCTION_CHECKLIST.md). Les champs responsable/contact, l'audit juridique et les essais sur téléphones physiques restent obligatoires.
