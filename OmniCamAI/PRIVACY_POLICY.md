# Politique de confidentialité — OmniCam AI

**Dernière mise à jour : 20 octobre 2026**

> Avant toute publication publique, le responsable de l’application doit renseigner son identité, un moyen de contact valide, une adresse publique permanente pour cette politique et confirmer chaque déclaration ci-dessous en examinant le binaire Release et ses dépendances.

## Données traitées

OmniCam AI demande l’autorisation **Caméra** pour afficher l’aperçu, prendre des photos et enregistrer des vidéos. L’application n’enregistre pas de vidéo avec audio dans la version actuelle.

Les photos et vidéos prises par l’utilisateur sont enregistrées dans la bibliothèque multimédia Android, sous `Pictures/OmniCam` et `Movies/OmniCam`. Selon le format et le chemin de capture, les fichiers peuvent contenir des métadonnées intégrées (par exemple date, orientation et caractéristiques de l’image).

Les réglages de l’application sont enregistrés localement dans les préférences privées de l’application.

## Traitement par intelligence artificielle

L’analyse de scène et la segmentation du mode Portrait s’appuient sur les bibliothèques ML Kit intégrées au projet. Les traitements prévus par ces fonctionnalités sont exécutés sur l’appareil. L’utilisateur peut désactiver l’analyse de scène dans les paramètres.

L’application ne déclare actuellement pas la permission Android `INTERNET` et le code du projet ne configure pas de service de télémétrie. Cette affirmation doit être vérifiée à nouveau sur le manifeste fusionné et le binaire Release avant chaque publication, y compris après toute mise à jour de dépendance.

## Partage, accès et suppression

Les médias OmniCam sont visibles dans la bibliothèque multimédia du téléphone. L’utilisateur peut les ouvrir, partager ou supprimer. Lorsqu’il choisit de partager un média, l’application destinataire reçoit l’accès au fichier sélectionné ; les informations intégrées au fichier peuvent être transmises avec celui-ci.

La suppression des médias se fait depuis la galerie OmniCam ou depuis les outils de gestion des médias du système. La désinstallation supprime les préférences privées de l’application, mais ne garantit pas la suppression des photos et vidéos déjà enregistrées dans la bibliothèque multimédia.

## Services tiers et transfert de données

Aucun compte utilisateur ni service serveur n’est configuré dans le code actuel. Les bibliothèques Android, Google ML Kit et leurs dépendances doivent être examinées avant publication pour confirmer les conditions applicables, les données de diagnostic éventuelles et les obligations de divulgation. Ne pas publier cette politique tant que cet audit n’est pas terminé.

## Enfants, sécurité et contact

OmniCam AI n’a pas encore fait l’objet d’une classification d’âge ou d’un audit juridique. Les parents ou tuteurs doivent superviser l’utilisation par des mineurs selon le contexte.

**Responsable / éditeur : À renseigner avant publication.**  
**Contact confidentialité : À renseigner avant publication.**  
**Politique publique (URL permanente) : À publier avant la soumission à une boutique.**

Cette documentation est un point de départ technique et ne constitue pas un avis juridique. Les déclarations Google Play Data safety et les informations de confidentialité doivent correspondre au comportement réel du binaire publié.
