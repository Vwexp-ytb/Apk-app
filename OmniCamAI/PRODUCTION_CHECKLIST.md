# Checklist de validation OmniCam AI avant publication

Ce document sépare ce que la CI peut valider de ce qui exige une clé de signature, une URL publique, un compte éditeur ou un téléphone réel.

## Automatisé par GitHub Actions

- [ ] Les tests JVM passent sur le commit candidat.
- [ ] `lintDebug` passe ; chaque avertissement doit être trié avant la release.
- [ ] `assembleDebug` passe.
- [ ] `assembleRelease` passe en candidat non signé.
- [ ] L'inventaire des dépendances Release est archivé et examiné.
- [ ] Une Release avec secrets configurés passe les tests, compile l'APK signé et `apksigner verify`.
- [ ] L'APK de la Release a un `versionCode` supérieur à celui déjà installé et un `versionName` cohérent.

## Signature et mise à jour — à tester manuellement

1. Créer le keystore de production sur un poste sûr, protéger son mot de passe et en faire au moins deux sauvegardes sécurisées.
2. Ajouter les quatre secrets documentés dans le README du dépôt GitHub.
3. Publier une première version Release et conserver l'APK ainsi que l'empreinte du certificat.
4. Installer cette version sur un téléphone de test et enregistrer quelques préférences ainsi qu'une photo.
5. Publier une version avec un `versionCode` supérieur, sans changer `applicationId` ni le keystore.
6. Installer la nouvelle APK par-dessus l'ancienne.
7. Vérifier que l'installation réussit, que les préférences sont conservées et que la photo reste dans la bibliothèque média.
8. Installer volontairement un APK signé avec une autre clé et confirmer qu'Android refuse la mise à jour (contrôle de sécurité).
9. Vérifier une installation propre, l'annulation, le stockage presque plein et le redémarrage.
10. Ne jamais mettre le keystore ou ses mots de passe dans Git.

## Matrice d'appareils à remplir

Tester au minimum trois catégories physiques et plusieurs versions Android, en notant fabricant, modèle, version Android, résultats et bugs :

| Catégorie | Appareil / Android | Photo / flash / autofocus | HDR / nuit / portrait | RAW / vidéo | Mémoire / chauffe | Résultat |
|---|---|---|---|---|---|---|
| Android récent, capteur haut de gamme | À renseigner | À tester | À tester | À tester | À mesurer | En attente |
| Android milieu de gamme | À renseigner | À tester | À tester | À tester | À mesurer | En attente |
| Android ancien au minimum supporté | À renseigner | À tester | À tester | À tester | À mesurer | En attente |
| Caméra avant et arrière / multi-capteur | À renseigner | À tester | À tester | À tester | À mesurer | En attente |

Pour chaque appareil, tester refus/révocation de permission, rotation, mise en arrière-plan, interruption par un appel, fermeture forcée, manque d'espace, capture prolongée, résolution maximale, options incompatibles et erreurs du pilote. Les capacités Camera2 affichées par l'écran Capteurs servent à générer le profil de compatibilité partageable.

## Confidentialité et distribution

- [ ] Remplir les champs responsable/contact dans `PRIVACY_POLICY.md`.
- [ ] Publier la politique à une URL publique stable ; un fichier dans ce dépôt privé ne suffit pas.
- [ ] Vérifier le manifeste fusionné et le binaire final, notamment les permissions transitives et les services tiers.
- [ ] Examiner les licences de toutes les dépendances directes et transitives, puis inclure les notices requises.
- [ ] Remplir la déclaration Google Play Data safety selon les SDK réellement livrés et leurs pratiques.
- [ ] Choisir la classification d'âge et répondre au questionnaire de contenu.
- [ ] Préparer fiche Store, icône, captures d'écran réelles, coordonnées de support et notes de version.
- [ ] Tester le canal bêta avant la diffusion générale.

## Fonctionnalités désormais présentes dans le code (à valider sur appareils)

- Préférences de capture conservées entre les lancements.
- Retardateur photo désactivé / 3 s / 10 s.
- Rafale séquentielle de 3 ou 5 photos JPEG en mode Photo.
- Fusion Nuit utilisant un trimmed mean par canal pour limiter l'influence des valeurs extrêmes ; ce n'est pas un alignement optique ni une suppression complète du ghosting.
- Galerie OmniCam avec partage et suppression confirmée pour les fichiers reconnus.
- Rapport de compatibilité exportable contenant le modèle et les capacités Camera2 annoncées.

## Bloquants connus / non automatisables ici

- La clé de signature et les secrets GitHub doivent être créés par le propriétaire.
- Le responsable légal, le contact et l'URL publique de confidentialité ne sont pas fournis.
- La conformité Play Console ne peut pas être déclarée avant l'audit des dépendances et le compte éditeur.
- Aucun téléphone physique n'est connecté à cette CI ; aucun résultat de test matériel n'est donc supposé.
- La retouche générative IA et le vérificateur de mise à jour intégré ne sont pas implémentés.
