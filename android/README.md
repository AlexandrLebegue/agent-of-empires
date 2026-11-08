# AoE Companion (Android)

Application Android native pour piloter une instance Agent of Empires (AoE).
Elle reprend les écrans principaux du dashboard web pour un usage quotidien sur
téléphone.

## Prérequis

- Android 11+ (`minSdk 30`)
- Une instance AoE accessible sur le réseau local, avec `aoe serve`
- Le jeton de `~/.config/agent-of-empires/serve.token` (ou un serveur `--no-auth`)

## Compilation et installation

```sh
cd android
ANDROID_HOME=/home/dev/android-sdk \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
./gradlew assembleDebug
```

APK : `android/app/build/outputs/apk/debug/app-debug.apk`

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Tests unitaires :

```sh
cd android
ANDROID_HOME=/home/dev/android-sdk \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
./gradlew testDebugUnitTest
```

## Configuration

Au premier lancement, l'écran de connexion demande :

- **URL du serveur** : par défaut `http://192.168.1.60:8080`
- **Jeton** : contenu de `serve.token`

Le bouton **Tester** interroge `GET /api/about` et affiche la version, le mode
d'authentification et le profil servi. La configuration est conservée dans
DataStore. Le bouton **Se déconnecter** efface le jeton et ramène à cet écran.

Le HTTP en clair est activé pour le réseau local. Le jeton n'est jamais écrit
dans les logs de l'application.

## Couverture fonctionnelle

### Connexion et robustesse

- Test de connexion, persistance URL/jeton
- Gestion 401 avec retour à l'écran de connexion
- Serveur injoignable sans crash, bandeaux d'erreur et reprises
- WebSocket avec backoff, repli polling si `/api/sessions/status/ws` est absent
- Rafraîchissement au retour au premier plan
- Thème clair/sombre/système

### Sessions

- Accueil avec en-tête type Reddit (logo cliquable + recherche pilule fixe)
- Liste par projets sous forme de cartes repliables avec état agrégé
- Favoris regroupés dans une carte dédiée en tête
- Gestion séparée des groupes AoE (création, renommage, suppression)
- Filtres Actives/Favoris/Toutes/Archivées/Corbeille
- Recherche locale et recherche conversationnelle (`/api/sessions/search`)
- Compteurs actives/en attente/non lues, indicateur non-lu
- Création de session : dossier, projets récents, agent, modèle, effort, vue
  structurée/terminal, YOLO, groupe, worktree et branche, progression, confiance
  des hooks
- Actions : renommer, pin, favori, archive, snooze, couleur, mute, non-lu,
  stop/start/restart, corbeille/restauration avec confirmation et annulation,
  déplacement entre groupes
- Rollback optimiste en cas d'échec
- Groupes : création, renommage, suppression, déplacement de sessions
- Ordre manuel des sessions (`/api/reorder`) et ordre des workspaces
  (`PUT /api/workspace-ordering`)

### Session structurée (ACP)

- Historique chargé par pages via `/acp/replay?view=rows`, live WS à partir du dernier seq
- Transcript live `/sessions/{id}/acp/ws` avec repli terminal
- Markdown minimal, blocs d'outils repliables, diffs compactes, sélection/copie
- Prompt streaming, file d'attente (envoi, édition, suppression, vidage)
- Pièces jointes : collage d'image (`paste-image`), composer multi-lignes
- Modèle, effort, modes de l'agent, changement d'agent ACP
- Approbations Accepter/Toujours/Refuser
- Bascule vue structurée/terminal pour une même session

### Diff et fichiers

- Liste des fichiers, navigation précédent/suivant, diff complet
- Base de diff par dépôt (`PATCH /diff-base`)
- Vue patch ou fichier complet (`/api/sessions/{id}/file`), sélection/copie

### Terminal

- Vue `/sessions/{id}/live-ws`, envoi de texte par `/send`
- Sélection et copie du contenu
- Bascule vers/depuis la vue structurée

### Gestion du serveur

- MCP : liste, création, édition, suppression, conflits, conservation/retrait
- Skills : liste, détail, création, édition, suppression, adoption
- Plugins : liste, état, activation/désactivation, mises à jour disponibles
- Profils : liste, création, renommage, profil par défaut, suppression
- Sécurité : appareils connectés, révocation, passkeys
- Serveur : health, logs, mise à jour, about

### Notifications

- Service de premier plan maintenant le statut des sessions
- Alertes Waiting (approbation), Error, Idle
- Déduplication avec dwell/cooldown et plage silencieuse
- Mute par session
- Notification actionnable : Approuver, Ouvrir, Répondre (RemoteInput)
- Autorisation Android demandée au premier lancement

## Limites connues

- Le modèle affiché dans la liste provient d'un cache local alimenté par ACP ;
  `/api/sessions` ne l'expose pas.
- Certaines routes récentes n'existent pas sur les anciens daemons (1.20) :
  `/api/sessions/creation-trust`, `/api/reorder`, `/api/sessions/{id}/favorite`,
  CRUD des groupes, `/api/groups/collapse`, `/api/passkeys`,
  `/api/system/logs`. L'app affiche alors un message ou utilise un repli.
- Les notifications utilisent le WebSocket local et un service Android, pas Web
  Push. Sans redémarrage automatique au boot, aucune alerte n'arrive après un
  redémarrage du téléphone tant que l'app n'est pas ouverte.
- Pas de WebAuthn/passkey depuis l'app, pas d'import/fork de session, sandbox,
  multi-repo, commentaires de diff, file de prompts avec pièces jointes
  binaires, cartes d'outils spécialisées, agents d'arrière-plan, sons, ni
  gestion des workspaces/projets/templates.

## Architecture

- Kotlin + Jetpack Compose Material3, module `:app`
- OkHttp pour HTTP et WebSocket
- DataStore Preferences pour la configuration et les états locaux
- ViewModel + StateFlow
- `kotlinx.serialization` non utilisé ; parsing via `org.json`
- Aucune dépendance Hilt, Room ou équivalent
