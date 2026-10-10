******

### Historique des Versions

******

# v6.8.0

###### 2026/10/10

* `Astuce` Le plugin de sélection de couleur devient 3-Color Picker 2.0 avec un nouvel identifiant; installez-le et activez-le à nouveau. L'ancienne application peut rester installée, sans migration automatique des réglages. Le libellé du tiroir reste inchangé
* `Astuce` Compose UI, sa référence API, la complétion de types Ace Editor et le contenu Offline Docs sont fournis comme intégration locale nécessitant une compilation locale correspondante du host; le plugin ne figure pas dans l'index officiel et n'est pas publié officiellement
* `Astuce` Les dialogues indépendants nécessitent AutoJs6 6.8.0 / 5321 et Compose UI avec dialog-v1; TSX nécessite aussi TypeScript Engine 0.6.6. Les scripts hors UI nécessitent la permission de superposition de l'hôte
* `Astuce` Cette version utilise le contrat Compose UI V2 et nécessite AutoJs6 6.8.0 / 5322; TSX nécessite TypeScript Engine 0.6.7. Les nouveaux hôtes acceptent les composants existants des moteurs V1; les composants étendus nécessitent un moteur V2
* `Astuce` Les applications empaquetées doivent sélectionner OpenCV lors de l'empaquetage pour images et les autres fonctions OpenCV ; un runtime de même nom apporté par un plugin OCR n'est plus utilisé
* `Fonctionnalité` L'option Afficher l'icône du lanceur, activée par défaut dans les réglages des plugins officiels et tiers, masque et restaure le style d'icône initial (Root ou Shizuku autorisé en mode Root requis)
* `Fonctionnalité` Une option du guide d'installation contrôle les icônes uniquement pour les nouveaux plugins du lot, les affiche par défaut et ne modifie pas les plugins déjà installés
* `Fonctionnalité` La fonction "Générer du code" de l'inspecteur de disposition prend en charge les portées élément unique/ensemble, l'attente de détection/avec délai limite et la vérification immédiate, les destinataires d'actions et paramètres de texte, les styles Classique/Flow, les solutions alternatives et explications de localisation, avec aperçu des correspondances dans la disposition
* `Fonctionnalité` Actions liées aux noeuds inspectés par l'hôte, avec ajout de texte, défilement borné et retour du résultat et des changements de fenêtre
* `Fonctionnalité` OCR d'écran proposé uniquement si l'hôte signale un plugin OCR autorisé disponible, avec fusion en lignes bornées et coordonnées
* `Fonctionnalité` API de script ai.agent pour lancer et consulter les tâches, recevoir les événements, répondre, confirmer, annuler et exécuter en arrière-plan avec detached; result/context exposent les résultats et le contexte des scripts enregistrés par @agent, avec trois exemples
* `Fonctionnalité` Les options de ai.agent.run et ai.agent.create acceptent un booléen plan: avec true, Three Stove Agent passe en mode plan, où le modèle propose d'abord 1 à 8 étapes vérifiées via un événement input de kind plan avant l'exécution du premier outil; en cas d'omission, le réglage de mode plan du préréglage s'applique
* `Fonctionnalité` Three Stove Agent exécute le JavaScript généré après confirmation, arrête son script en cas d'annulation ou de délai dépassé, et renvoie les résultats structurés et la console; le script utilise les permissions de l'hôte, sans isolation des accès aux fichiers
* `Fonctionnalité` ai.agent: Outils MCP de serveurs locaux ou externes choisis, avec un niveau de risque par serveur et le groupe mcp désactivé par défaut
* `Fonctionnalité` Le relais de modèles Three Stove Agent accepte les images en entrée et dans les résultats des outils pour les modèles visuels compatibles (prise en charge par les plugins requise)
* `Fonctionnalité` Gestion de l'autorisation du réseau local Android 17 dans le volet, les paramètres et les connexions VS Code initiées par l'utilisateur, avec déclaration selon le targetSdk effectif
* `Fonctionnalité` Historique des arrêts de processus dans les options du développeur sous Android 11 et ultérieur, avec les motifs système récents de plusieurs processus, identification du limiteur de mémoire et rapports copiables (entrée : appui long sur l'icône de l'application dans la page "A propos de l'application et du développeur")
* `Fonctionnalité` Bouton MCP Server avec guides d'installation, d'activation, d'autorisation et de compatibilité; arrêt synchronisé avec la notification; paramètres conservés à la reconnexion; accès vérifié à la même page depuis le volet et le Centre de plugins. Restaure le serveur à l'ouverture d'AutoJs6 sauf arrêt par l'utilisateur en l'absence de l'hôte; aucun lancement au démarrage de l'appareil.
* `Fonctionnalité` Les capacités MCP ajoutent la recherche des applications Android, les positions dans un éditeur, une activation de l'accessibilité selon les stratégies configurées, une sortie Shell limitée et des descripteurs en lecture seule pour les grands résultats JSON; root utilise une autorisation shell.root et un réglage distincts sur le téléphone
* `Fonctionnalité` Moteur de plugin QuickJS Runtime indépendant pour JavaScript local à fichier unique et les modules ES, routé par la directive "quickjs"/"qjs" ou les marqueurs `.quickjs.js/.quickjs.mjs`, sans repli vers Rhino, Node.js ou Bun
* `Fonctionnalité` Propriété en lecture seule `device.pageSize` renvoyant la taille des pages mémoire du système en octets, pour identifier les environnements de 4 KB / 16 KB
* `Fonctionnalité` Moteur de plugin Bun Runtime indépendant pour les fichiers JavaScript et TypeScript locaux uniques, routés par la directive "bun" ou les marqueurs `.bun.js/.bun.ts/.bun.tsx`, sans repli vers Rhino ou Node.js
* `Fonctionnalité` La section Outils du tiroir latéral ajoute maintenant le plugin optionnel Sélecteur de couleur d'écran comme troisième outil, désactivé par défaut, après Bouton flottant et Position du pointeur; l'installation, l'activation, l'état d'exécution et la suppression sont actualisés automatiquement sans bloquer l'interface
* `Fonctionnalité` Module de contrat du plugin MCP Server (plugin-api/mcp-server-api): surface AIDL figee, client hote strict qui lie le plugin officiel via un bail de liaison dedie et le relie jusqu'a trois fois apres la mort du processus du plugin, courtier de capacites de l'hote restreint par grant qui achemine les requetes du plugin via le Node bridge provider, et enregistrement dans le Centre de plugins comme plugin desactive par defaut
* `Fonctionnalité` Gestionnaire pour l'entrée "Service d'accessibilité" du tiroir latéral (une boîte de dialogue, ouverte avec le bouton "Gestionnaire" de la boîte de description ou par un appui long sur l'entrée) : la section d'état affiche l'état du service, les paramètres système, l'instance dans le processus, la durée de fonctionnement, le nombre d'événements et le dernier événement, les fenêtres et la racine de la fenêtre active, le dernier paquet et la dernière activité, la configuration du service, la disponibilité de root et des paramètres sécurisés ainsi que le nombre d'écouteurs ; la section de contrôle propose démarrer, arrêter, redémarrer, vider le cache des nœuds, l'auto-vérification (compte les racines de fenêtre et les nœuds puis attend un événement) et un moniteur d'événements (liste en direct des derniers événements d'accessibilité avec leurs intervalles ; pause, effacer, changements d'état de fenêtre uniquement et copie) ; la section des paramètres contient les interrupteurs d'activation automatique via root et via les paramètres sécurisés ; le texte de diagnostic complet peut être copié en une fois ; les en-têtes état et contrôle portent des boutons pour actualiser et pour réduire ou augmenter le niveau de détail (état : 4 niveaux, champs de base par défaut, le titre affiche l'état du service comme "État [Opérationnel]" quand toutes les lignes sont masquées ; contrôle : 3 niveaux, les lignes démarrer / arrêter / redémarrer par défaut, ces trois boutons passent dans l'en-tête quand toutes les lignes sont masquées) et les niveaux choisis sont mémorisés
* `Fonctionnalité` Les lignes de permissions du gestionnaire accessibilité ouvrent les applications root candidates mises en cache avec ajout manuel, les instructions ADB pour les paramètres sécurisés et les autorisations ou réglages Shizuku; mode stable dans les réglages et ligne de statut avec conseils pour activer / désactiver
* `Fonctionnalité` Ajout des API `snapshot`/`capabilities` versionnées et des constantes de schéma v1/v2 à `mediainfo`; Rhino conserve les résultats `read` historiques, Node.js garde le format v1 de l'hôte par défaut, et le format v2 du plugin avec les métadonnées moteur courtes n'est exposé qu'après annonce explicite de la capacité
* `Fonctionnalité` Un clic sur un fichier dans une archive l’extrait dans un dossier temporaire privé pour l’exécuter ou l’ouvrir en conservant les suffixes du moteur; APK/APKS/XAPK/APKM/APKZ/AAB peuvent être consultés et extraits depuis les menus des fichiers et du dialogue d’information sans remplacer les boutons existants
* `Fonctionnalité` La capacité v1 d'informations de fichier hôte d'Explorer Action permet aux plugins de confiance en lecture seule d'ajouter un résumé localisé, borné et lié au SHA-256 à la boîte de dialogue d'informations APK existante; si le plugin est absent, désactivé, incompatible ou en échec, ses champs d'origine, les actions d'installation/manifeste et la gestion des erreurs restent inchangés
* `Fonctionnalité` Objet global `pangu` avec pangu.js 10.1.0 intégré, proposant `spaceText` pour espacer les textes mixtes CJK et `hasProperSpacing` pour les vérifier, avec déclarations TypeScript et documentation
* `Fonctionnalité` `pinyin` ajoute les lectures `customDictionary` limitées à chaque appel, finalise `compare` et `compact`, et expose le même fournisseur du plugin externe à Node.js via `autojs6:bridge.callAutoJs` avec la capacité explicite `pinyin`
* `Fonctionnalité` `pinyin4j` ajoute une matrice `readings` négociée par capacité pour toutes les lectures en Hanyu, Tongyong, Wade-Giles, MPS2, Yale et Gwoyeu Romatzyh; les anciens plugins se replient en toute sécurité uniquement pour Hanyu, tandis que le même fournisseur du plugin externe est exposé à Node.js via `autojs6:bridge.callAutoJs` avec la capacité explicite `pinyin4j`
* `Fonctionnalité` Le contrat de plugin OpenCC version 2 ajoute la découverte dynamique des types de conversion, des lots de 1024 textes maximum et des chaînes de 32 étapes maximum; les 16 méthodes composées passent de 3 allers-retours Binder au maximum à 1 avec un nouveau plugin, tandis que les anciens plugins utilisent automatiquement les appels d'origine par élément ou par étape
* `Fonctionnalité` Les options Image Quantization ajoutent `maxPixels` et `maxMemoryBytes` via `OPTIONS_API_VERSION>=4`, avec des limites par defaut de 16000000 pixels et 256 MiB de memoire de travail supplementaire; un depassement echoue avec des details de ressource types, les resultats indiquent le pic de memoire comptabilise et l'arret du script annule sa quantification native active
* `Fonctionnalité` `images.quantizeToFile` diffuse un PNG indexe directement sur le disque avec sa taille et ses mesures de qualite; les options Image Quantization prennent maintenant en charge `preserveAlpha`, les configurations Bitmap courantes, les entrees materielles et les entrees a large gamut normalisees en sRGB via `OPTIONS_API_VERSION>=3`
* `Fonctionnalité` `images.quantize` renvoie maintenant les octets PNG, la taille, la qualite obtenue et l'erreur de quantification; sans qualite minimale, le mode meilleur effort est utilise (`minQuality=0`), tandis qu'une limite inferieure explicite impossible a satisfaire leve `PngQuantBridge.QualityTooLowException` avec le code `PNG_QUANTIZATION_QUALITY_TOO_LOW`; l'API de resultat exige `OPTIONS_API_VERSION>=2`
* `Fonctionnalité` `images.save`, `images.saveImage`, `images.compress` et `images.compressToBytes` acceptent maintenant des options nommees de quantification PNG pour la taille de palette, la vitesse, les bornes de qualite independantes, le tramage et posterize; le `quality` numerique reste compatible et les options exigent un plugin Image Quantization declarant `OPTIONS_API_VERSION>=1`
* `Fonctionnalité` Exécution immédiate de TypeScript en mémoire via `engines.execScript`: les noms `.ts/.tsx/.mts/.cts` utilisent le compilateur indépendant avec des diagnostics et une remise en correspondance des piles Source Map équivalents aux fichiers, Rhino renvoie la valeur de la dernière expression et la page Log complète ainsi que le panneau Log de l'éditeur évaluent interactivement des extraits sur une ligne
* `Fonctionnalité` Les projets Node TypeScript adossés au compilateur peuvent désormais compiler à la demande les fichiers `.ts/.mts/.cts` créés pendant l'exécution: Node transfère la source via un PFD borné par le nombre d'octets et SHA-256, l'hôte réutilise la session de projet résidente, les diagnostics de types et la remise en correspondance des piles par Source Map sont conservés, et les sorties de chemin, liens symboliques et cibles ambiguës restent refusés
* `Fonctionnalité` Ace actualise désormais de façon incrémentale son service de langage TypeScript résident après un enregistrement et annule le travail obsolète avec un debounce d'inactivité de 450 ms et un délai de planification des diagnostics de 50 ms; sur l'appareil de validation, TS2322 a été actualisé 201 ms après l'enregistrement d'un projet de 20 fichiers sans bloquer la saisie
* `Fonctionnalité` Le contrat 5 de l'éditeur ajoute le renommage des symboles TypeScript dans tout le projet: Ace soumet des modifications bornées par fichier avec des identités exactes, tandis que l'hôte revalide un instantané propre, affiche un aperçu, fige le tampon actif et prépare, publie ou annule atomiquement tous les fichiers avant d'actualiser les diagnostics
* `Fonctionnalité` Le contrat d'éditeur 4 ajoute l'import automatique et les corrections orthographiques TypeScript autorisés par l'hôte pour le tampon actif: les requêtes ne peuvent contenir que des modifications UTF-16 bornées et ordonnées avec les SHA-256 exacts de la base et du résultat, tandis que l'hôte les rejoue indépendamment et refuse les actions en lecture seule, périmées, mal formées, chevauchantes ou altérées avant une autorisation unique
* `Fonctionnalité` Le contrat d'éditeur 3 ajoute la navigation vers les définitions TypeScript autorisée par le snapshot du projet: Ace peut demander des positions dans les sources du projet et les déclarations de dépendances gelées, tandis que l'hôte revérifie indépendamment la racine, le chemin relatif canonique, l'empreinte de l'inventaire des sources ou dépendances, le SHA-256 du contenu et la plage avant d'ouvrir et positionner la cible; les liens symboliques, snapshots périmés et contenus non enregistrés divergents sont refusés
* `Fonctionnalité` Le contrat d'éditeur 2 fournit désormais à Ace le même snapshot borné des sources du projet TypeScript que la compilation avant exécution, permettant des diagnostics inter-fichiers en direct pour les chemins d'import invalides tout en conservant la compatibilité avec les plugins d'éditeur de contrat 1
* `Fonctionnalité` Les instantanés de dépendances TypeScript rejettent désormais les signaux d'extensions natives (`.node`, `binding.gyp` et `gypfile`) et les hooks du cycle d'installation (`preinstall`, `install` et `postinstall`) avec des codes d'erreur stables et des conseils JavaScript pur/WASM; les scripts ordinaires du paquet restent des métadonnées inertes
* `Fonctionnalité` Alignement du contrat de dépendances TypeScript de l'hôte sur la révision 3 de la politique du resolver et ajout d'une couverture des manifestes de versions réelles de dayjs, lodash, zod, uuid et nanoid, garantissant la sélection des entrées CommonJS de Rhino et des erreurs ESM-only stables
* `Fonctionnalité` La compilation de projets TypeScript et Ace partagent maintenant une meme couche gelee de types `node_modules`: `types`/`typings`, `typesVersions` de TypeScript 6, declarations imbriquees et `@types` installes donnent la meme completion dayjs, le meme hover et le meme diagnostic strict TS2322 dans Rhino et Node
* `Fonctionnalité` Les capacités hôte de Lua Runtime fournissent désormais un stockage clé/valeur persistant isolé par fichier de script et Toast: `autojs.storage.get/put/remove/clear` utilise un espace de noms privé à l'application, borné et validé par écriture synchrone, tandis que `autojs.ui.toast` est distribué une seule fois sur le thread principal de l'hôte
* `Fonctionnalité` Les types de fichiers par défaut d'Ace LSP incluent désormais .mts/.cts et leurs variantes de déclaration, permettant à l'éditeur Ace externe de sélectionner le même profil d'exécution TypeScript Rhino/Node; les anciennes sélections par défaut sont migrées et les ensembles de suffixes personnalisés explicites restent inchangés
* `Fonctionnalité` APK Builder compile désormais les projets Rhino TypeScript lors de l'empaquetage ; les APK finaux ne contiennent que du JavaScript chiffré et des Source Maps facultatives sans source intégrée, jamais les sources TypeScript ni le compilateur ; les projets chiffrés utilisent une zone de staging AES-256-GCM à usage unique, le texte en clair restant uniquement en mémoire puis étant effacé après rechiffrement
* `Fonctionnalité` Environnement d'exécution de scripts Node.js 24.5.0, prenant en charge les scripts lancés avec la directive "nodejs" ou des extensions comme .node.js/.cjs/.mjs
* `Fonctionnalité` L'interopérabilité Java de Node.js utilise une liste blanche déclarative de l'hôte pour Math, UUID, Rect, Point, Color, Locale, Build, Build.VERSION et TimeUnit, avec `java.policy` et la lecture des champs statiques accessibles au plugin
* `Fonctionnalité` Prise en charge par l'hôte Node.js des sessions de lecture et de la bibliothèque media_store, avec pause, reprise, positionnement, état et nettoyage, ainsi que recherche, importation, renommage, exportation, analyse et suppression MediaStore; media.playback, media.library et media.library.mutate restent des capacités distinctes
* `Fonctionnalité` ESM, dynamic import(), le réseau et WebSocket, les modules réseau natifs, worker_threads, child_process et l'interopérabilité Java de Node.js sont des fonctions stables activées par défaut, tandis que les déclarations d'autorisation, listes d'autorisation, bacs à sable et budgets de ressources restent appliqués
* `Fonctionnalité` L'environnement d'exécution Node.js prend en charge CommonJS/ESM partiel/import() dynamique uniquement local/sources TypeScript légères (.ts/.mts/.cts)
* `Fonctionnalité` L'environnement d'exécution Node.js prend en charge un fournisseur à la demande de sources de modules chiffrés propre à chaque exécution, qui inspecte et déchiffre uniquement les candidats réellement sélectionnés par le résolveur de modules
* `Fonctionnalité` L'environnement d'exécution Node.js prend en charge des modules de liaison AutoJs6 contrôlés et adopte la politique de sécurité Safe Node Profile
* `Fonctionnalité` L'environnement d'exécution Node.js prend en charge un processus de service persistant et la réutilisation de la plateforme Node/V8 à l'échelle du processus, tout en créant un isolate et un Environment distincts pour chaque exécution de script
* `Fonctionnalité` L'environnement d'exécution Node.js persistant prend en charge le recyclage en cas d'inactivité ou de pression mémoire, l'arrêt explicite, ainsi que l'isolement et la reconstruction des processus défaillants
* `Fonctionnalité` Backend de plugin d'exécution Node.js externe avec mode `plugin` obligatoire et mode `pluginOptional` avec repli, sessions Binder persistantes et runtime à l'échelle du processus, validation de confiance/contrat/ABI et contre-pression sans file pour une seule exécution active
* `Fonctionnalité` Le backend du plugin d'exécution Node.js externe prend en charge le transport d'archive d'espace de travail v2 limité à la requête, avec mappage explicite des entrées, réécriture des sorties et manifeste tombstone de suppression, sans analyser le sandbox hôte
* `Fonctionnalité` Les builds de débogage de Node.js Runtime permettent d'activer explicitement, pour chaque exécution, une session Inspector/CDP limitée à l'hôte local, tandis que les builds release, l'écoute distante et l'attente bloquante restent interdits
* `Fonctionnalité` La fonction d'empaquetage prend en charge les projets de scripts Node.js et inclut automatiquement les bibliothèques d'exécution requises
* `Fonctionnalité` Client indépendant du plugin compilateur TypeScript avec validation du contrat d'archive AIDL/R2 figé, compilation Rhino de fichiers uniques UTF-8, diagnostics bornés et sortie Source Map obligatoire
* `Fonctionnalité` Les scripts .ts locaux à fichier unique et les scripts .tsx destinés à Rhino passent par le plugin compilateur indépendant avant leur exécution dans Rhino, conservent leur identité source UTF-8 et leur sortie Source Map, rejettent les erreurs de type avant l'exécution et ne se replient jamais sur le plugin Node.js ni sur du TypeScript brut; les fichiers de déclaration non pris en charge et un compilateur indisponible renvoient des erreurs localisées explicites
* `Fonctionnalité` Les scripts .ts et .tsx locaux à fichier unique avec la directive "ui" prennent en charge, après compilation, l'exécution dans une Activity Rhino avec des mises en page XML sous forme de chaîne et des mises en page TSX natives, des balises intrinsèques contrôlées, Fragment, des attributs scalaires, la recherche de vues, les callbacks de clic, la mise à jour du texte et la fermeture explicite de l'UI
* `Fonctionnalité` Les diagnostics du compilateur TypeScript sont exposés sous forme d'enregistrements ScriptCompilationException de l'hôte avec fichier source, ligne et colonne à base 1, gravité et code d'erreur TS; Console et le panneau de journal inférieur de l'éditeur affichent des emplacements propres et cliquables menant à la position source correspondante
* `Fonctionnalité` Les exceptions d'exécution TypeScript dans Rhino exploitent le Source Map v3 du compilateur, remappent les frames JavaScript générées vers le fichier .ts et la ligne d'origine sans inventer de colonne indisponible, et conservent des positions cliquables dans Console et le panneau de journal de l'éditeur
* `Fonctionnalité` Les scripts .mts/.cts locaux à fichier unique sont compilés en .mjs/.cjs par le profil Node du plugin compilateur TypeScript indépendant avant leur exécution par le plugin Node.js Runtime, avec conservation des noms de fichiers UTF-8 et contournement de l'ancien retrait TypeScript par expression régulière; les fichiers Node .ts uniques sont explicitement refusés tant que les métadonnées package de projet ne sont pas prises en charge
* `Fonctionnalité` Les échecs d'exécution Node TypeScript exploitent le Source Map v3 du compilateur pour remapper les frames V8 vers la ligne et la colonne exactes du fichier .mts/.cts d'origine, avec des positions remappées cliquables dans Console et le panneau de journal de l'éditeur
* `Fonctionnalité` Les projets TypeScript compilent désormais un instantané de projet borné comme un seul programme pour Rhino ou Node, en préservant les imports relatifs, les dépendances CommonJS circulaires, les fichiers JSON d'exécution, le contexte `type` du package, les diagnostics des modules importés et le mappage de pile Source Map par module; les métadonnées package restent strictes et les lifecycle scripts sont refusés
* `Fonctionnalité` Les résultats de compilation TypeScript sont mis en cache entre les exécutions par une clé sémantique couvrant le graphe source, les empreintes du compilateur et des déclarations, les révisions de profile et policy et les métadonnées package; les scripts inchangés évitent le prewarm du compilateur et la compilation Worker, tandis que les archives de sortie authentifiées sont revalidées avant réutilisation et que les entrées corrompues ou hors quota reviennent à la compilation normale
* `Fonctionnalité` Les projets TypeScript utilisent le meme snapshot exact et read-only de `node_modules` de la compilation a l'execution; Rhino charge CommonJS/JSON et les branches `require` des packages dual avec des erreurs ESM-only stables, tandis que Node recoit un workspace overlay prive et immuable et execute les packages ESM/CJS/dual sans reecrire les dependances
* `Fonctionnalité` Les tâches planifiées, tâches de diffusion, raccourcis et Tasker/Run Intents locaux partagent désormais la même préparation TypeScript que les lancements au premier plan; le JavaScript de pré-exécution Tasker reste ordonné avant la sortie Rhino/Node compilée et son préfixe est pris en compte dans le décalage des lignes Source Map, au lieu de contourner la compilation et d'exécuter le TypeScript brut
* `Fonctionnalité` Les dossiers de projet permettent de créer directement des tâches planifiées et de diffusion; au déclenchement, la tâche résout de nouveau le point d'entrée Rhino/Node.js/Python depuis project.json et restaure le répertoire de travail du projet ainsi que la configuration useFeatures
* `Fonctionnalité` Backend de plugin Python Runtime indépendant, prenant en charge l'exécution des fichiers .py locaux et des projets de scripts déclarés avec `type: python` via un processus de plugin CPython distinct, avec validation du composant fixe/de la signature/du protocole/de l'ABI, branchement à chaud et aucun repli vers JavaScript
* `Fonctionnalité` L'intégration de lancement Python couvre le gestionnaire de fichiers/l'éditeur/les Run Intent locaux/les Import Intent de fichier unique/les nouveaux raccourcis/les tâches planifiées/les tâches de diffusion et conserve un contexte strict de fichier unique ou de projet; les projets prennent en charge les imports internes, l'accès relatif aux fichiers et les API `autojs6.app/device/execution/project` en lecture seule
* `Fonctionnalité` L'exécution Python Runtime prend en charge des instantanés stdin facultatifs, bornés et préfournis via la clé ExecutionConfig `autojs6.python.stdinSnapshot`, avec admission stricte de ByteArray/texte UTF-8, négociation des capacités/tailles du fournisseur, transport PFD privé en lecture seule et nettoyage
* `Fonctionnalité` Le protocole Python Runtime 1.4 négocie par exécution des résultats JSON stricts explicites et des artefacts facultatifs, avec limites de nombre, chemin normalisé, taille par fichier/totale, références PFD exactes et SHA-256; l'hôte ne consomme que les résultats typés et n'en déduit jamais de stdout
* `Fonctionnalité` Le protocole Python Runtime 1.5 ajoute un broker synchrone de capacités Host limité à chaque exécution, avec vérification de l'UID fixe du plugin, JSON strict et borné, quotas d'appels, protections contre la répétition et l'utilisation hors cycle de vie; le premier lot complet à faible risque expose toast, presse-papiers, lancement d'applications ou d'URL, informations dynamiques batterie/écran/luminosité/volume, console Host à niveaux et notifications sensibles aux autorisations aux scripts Python
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 permet la lecture/écriture bornée de texte UTF-8, les contrôles d'existence/type et les listes directes triées dans la racine du projet courant ou le répertoire du script autonome; les chemins relatifs normalisés et les limites stables empêchent de sortir de la racine d'exécution
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 expose les dialogues bornés alert/confirm/prompt/select uniquement aux lancements de premier plan autorisés par l'utilisateur; les lancements en arrière-plan échouent avec INTERACTIVE_NOT_ALLOWED, et l'annulation ou le nettoyage terminal ferme l'interface de dialogue active
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 expose des métadonnées sûres du moteur courant sans chemin absolu, un auto-arrêt déterministe et jusqu'à 16 lancements enfants asynchrones et bornés de scripts Host non-Python; les sessions Python imbriquées échouent avec NESTED_PYTHON_NOT_ALLOWED tant que le provider reste mono-session
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 expose des actions d'accessibilité bornées de clic, clic long, pression temporisée, balayage, Retour et Accueil; les coordonnées et durées sont strictement validées, et un service d'accessibilité indisponible échoue de façon déterministe avec ACCESSIBILITY_UNAVAILABLE
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 expose des instantanés bornés de l'arbre d'accessibilité propres à chaque exécution et des requêtes selector composées avec AND, avec des références de node opaques pour les actions explicites de clic et de définition de texte; les instantanés, parcours, chaînes, nodes conservés et réponses ont des limites fixes, tandis que l'accessibilité indisponible, les parcours incomplets et les nodes obsolètes échouent avec des erreurs typées stables
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 expose les captures d'écran d'accessibilité Android 11+ sous forme d'octets PNG/JPEG propres à l'exécution, avec une seule image conservée, des blocs ordonnés de 32 KiB, une limite encodée de 4 MiB, des métadonnées SHA-256, une libération explicite et l'effacement des octets à la fin; aucune UI d'autorisation n'est ouverte, et l'accessibilité indisponible, l'échec de capture, les handles d'image obsolètes et les dépassements échouent avec des erreurs typées stables
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 recherche une couleur RGB bornée dans une nouvelle capture d'accessibilité Android 11+, avec une région facultative et un seuil par canal, puis renvoie la première coordonnée par lignes ou une absence déterministe sans transférer ni conserver les octets de l'image
* `Fonctionnalité` Le broker Host de Python Runtime 1.5 accepte un modèle PNG/JPEG propre à l'exécution par blocs ordonnés de 24 KiB, avec une limite encodée de 1 MiB et une vérification SHA-256, décode au plus 2048 pixels par côté et un million de pixels, puis recherche dans une nouvelle capture d'accessibilité Android 11+ selon un ordre déterministe par lignes avec les pixels opaques du modèle et un seuil RGB; les limites de cycle de vie, de région et de comparaisons échouent de façon fermée sans dépendance à OpenCV, tandis que l'intervalle obligatoire de 333 ms d'Android n'est retenté que pour l'erreur exacte d'intervalle trop court après 350 ms et dans le délai existant
* `Fonctionnalité` Les résultats JSON stricts explicites de Python Runtime sont écrits dans la console Host avec le préfixe `[result]` après la fin du script, tandis que les artefacts vérifiés sont publiés atomiquement dans un répertoire propre à l'exécution sous `.python-artifacts` dans le répertoire du projet ou du script et que leurs chemins absolus sont affichés; ce répertoire réservé est exclu des instantanés de projet ultérieurs
* `Fonctionnalité` Backend de plugin Lua Runtime indépendant pour exécuter les fichiers .lua locaux dans un processus de plugin PUC Lua 5.4.8 distinct, avec validation du composant fixe/de la signature/du protocole/de l'ABI, exécution bornée et aucun repli vers JavaScript
* `Fonctionnalité` Backends indépendants de plugins JVM Source Runtime pour Java et Kotlin, compilant et exécutant des scripts .java/.kt à fichier unique dans des processus isolés, avec package/import, sortie console en temps réel, sleep annulable, toast de l'hôte et négociation explicite des capacités
* `Fonctionnalité` Le périmètre de JVM Source Runtime couvre Java 8 à fichier unique (plugin Java, API minimale 24) et Kotlin 2.3 (plugin Kotlin, API minimale 26); cette version a uniquement été testée sur un appareil API 31/arm64-v8a et ne revendique pas les niveaux API non testés
* `Fonctionnalité` L'exécution explicite JVM Source Runtime prend en charge les arguments JSON bornés, l'accès au presse-papiers autorisé séparément, les paquets sources Java multifichiers et les classes d'entrée nommées, les positions sûres des exceptions et les observations d'exécution via Protocol 1.6 / Entry API 4; les plugins Protocol 1.1 / Entry API 2 restent compatibles avec l'exécution monofichier
* `Fonctionnalité` `ocr` sélectionne automatiquement le moteur disponible dans l'ordre ML Kit/Paddle/Rapid; `ocr.mode` indique le moteur choisi ou une chaîne vide, les modes explicites restent fixes, et `auto`, une chaîne vide ou une valeur nullish rétablissent la sélection automatique via `mode` ou `tap`
* `Fonctionnalité` Le système de plugins prend en charge des interfaces d'extension pour Node.js/ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/conversion de texte/informations multimédias et plus encore
* `Fonctionnalité` Le système de plugins fournit un transport TaggedWire versionné et des packs de protocole de développement v1 pour la compilation DEX et la génération de texte AI Provider, avec des applications de test de conformité dédiées et des scénarios hostiles
* `Fonctionnalité` Les plugins officiels AutoJs6 peuvent lire un instantané versionné et en lecture seule de l'apparence de l'hôte pour les couleurs du thème, la politique du mode sombre et la langue via le contrat de paramètres protégé par signature
* `Fonctionnalité` Le système de plugins prend en charge une interface d'extension pour la documentation hors ligne du projet
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge les extensions de plugin Explorer Action en lecture seule pour un seul fichier et charge dynamiquement leurs actions dans le menu supplémentaire de la page principale des fichiers
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge les actions principales Explorer Action du protocole v2 pour fournir l'affichage d'images, la lecture audio et la lecture vidéo via trois plugins indépendants
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge les transactions de sortie du protocole Explorer Action v3 avec des URI de contenu en écriture seule et soumises à des limites, la validation des sorties, la récupération après plantage et la publication atomique de fichiers dans le même répertoire
* `Fonctionnalité` Le protocole Explorer Action v4 du gestionnaire de fichiers accepte des cibles ordonnées fichier/dossier/mixtes et les sélections de même dossier parent, affiche la compression des plugins dans les menus de dossiers et une barre verticale de cinq actions, et fournit des sessions de fichiers de l'hôte pour les lectures contrôlées et les sorties atomiques voisines
* `Fonctionnalité` Le protocole Explorer Action v5 du gestionnaire de fichiers permet aux plugins fiables, limités à un fichier en lecture seule, de présenter les dossiers d'archives paginés dans la liste Explorer native, la barre de chemin, le thème et la pile Retour via des sessions Binder liées à l'UID
* `Fonctionnalité` Le protocole Explorer Action v6 du gestionnaire de fichiers prévisualise les documents, images, fichiers audio et vidéos lisibles avec les visionneuses existantes de l'hôte
* `Fonctionnalité` Les protocoles Explorer Action v7 à v10 du gestionnaire de fichiers ajoutent la relecture obligatoire des fichiers en attente, le remplacement atomique de cible, des transactions de sortie de dossier appartenant à l'hôte avec récupération après mort du client ou redémarrage, et l'extraction native du dossier courant ou des entrées sélectionnées avec progression bornée et annulation, permettant aux plugins d'archive de modifier un ZIP sélectionné et de publier une sortie vérifiée sans permission de stockage
* `Fonctionnalité` Le protocole Explorer Action v11 du gestionnaire de fichiers ajoute une récupération de mot de passe native et bornée lors de l'ouverture initiale ou de l'extraction; les nouvelles tentatives conservent le chemin et la sélection, puis effacent les tampons transitoires après chaque requête
* `Fonctionnalité` Le protocole Explorer Action v12 du gestionnaire de fichiers ajoute une source bornée de volumes voisins liée à chaque session d'archive; les plugins ouvrent uniquement les volumes ZIP et RAR approuvés par l'hôte via des ID opaques et des descripteurs en lecture seule, sans recevoir de répertoire ni de chemin du système de fichiers
* `Fonctionnalité` Le protocole Explorer Action v13 du gestionnaire de fichiers permet aux plugins compatibles de reconstruire le décodage des noms dans la même session en lecture seule; la barre de chemin native choisit un encodage ZIP tout en conservant le chemin interne disponible le plus profond et les éléments sélectionnés encore présents
* `Fonctionnalité` Le protocole Explorer Action v14 du gestionnaire de fichiers ajoute la correspondance bornée des suffixes composés; les plugins peuvent enregistrer exactement les premiers volumes `.zip.001` et `.7z.001` et réutiliser la source v12 liée à la session sans reconnaître des fichiers `.001` arbitraires ni recevoir le chemin du dossier
* `Fonctionnalité` Le protocole Explorer Action v15 du gestionnaire de fichiers ajoute des lots récupérables de sorties multi-fichiers; les plugins publient ensemble uniquement après vérification de tous les fichiers en attente, tandis que l'hôte journalise durablement le dossier parent et l'identité des fichiers et ne restaure après échec ou redémarrage que les membres dont l'identité correspond encore
* `Fonctionnalité` Le protocole Explorer Action v16 du gestionnaire de fichiers ajoute un transfert des sources vers la corbeille détenu par l'hôte et limité à la sélection originale exacte et à toutes les sorties validées; l'hôte persiste une copie récupérable avant le retrait sans accorder de chemin arbitraire ni de droit de suppression
* `Fonctionnalité` Le protocole Explorer Action v17 du gestionnaire de fichiers ajoute Ouvrir comme archive... en lecture seule uniquement lorsque l'action principale ne correspond pas; une session existante détecte la structure et peut indiquer le format réel dans la barre de chemin sans analyse de menu en arrière-plan ni nouvelle autorisation
* `Fonctionnalité` Le protocole Explorer Action v18 du gestionnaire de fichiers conserve la version précédente dans le stockage privé et durable de l'hôte avant un remplacement géré, n'expose que des métadonnées opaques et bornées et autorise une seule restauration si le fichier courant correspond encore exactement au remplacement validé; les changements externes ne sont jamais écrasés et les preuves de récupération interrompue sont conservées
* `Fonctionnalité` Le protocole Explorer Action v19 du gestionnaire de fichiers permet aux sessions d'archive inscriptibles d'exposer les actions natives Renommer et Supprimer pour les entrées compatibles; les réécritures utilisent un remplacement atomique vérifié appartenant à l'hôte, conservent la version précédente récupérable et reconstruisent la même session en préservant les chemins et sélections restants
* `Fonctionnalité` Le protocole Explorer Action v20 du gestionnaire de fichiers permet aux pages d'archive natives inscriptibles de créer des dossiers vides et d'ajouter une sélection mixte explicite de fichiers et d'arborescences complètes par une autorisation d'entrée figée sans chemin; les dossiers vides et les racines de même nom sont conservés avec une numérotation sûre, tandis qu'une source modifiée ou un élément défaillant annule tout le remplacement de l'archive
* `Fonctionnalité` Le protocole Explorer Action v21 du gestionnaire de fichiers fournit des lots durables de sources vers la Corbeille, détenus par l'hôte, avec historique opaque, résultats par élément et restauration au mieux entre les sessions; les noms existants ne sont jamais écrasés, restaurer les sources ne supprime jamais les archives créées et les preuves de récupération interrompue restent privées à l'hôte
* `Fonctionnalité` Le protocole Explorer Action v22 du gestionnaire de fichiers permet à une action en lecture seule sur un fichier de déclarer des suffixes bornés de fichiers associés; l'hôte fige uniquement les candidats exacts `<nom sélectionné><suffixe>` et les ouvre avec des descripteurs en lecture seule sans exposer la liste du dossier parent, les chemins ni des fichiers voisins arbitraires
* `Fonctionnalité` Le protocole Explorer Action v23 du gestionnaire de fichiers permet à une action de menu en lecture seule sur un seul répertoire de déclarer filesystemPath; l'hôte transmet alors uniquement le chemin canonique du répertoire sélectionné, sans créer de session de fichiers hôte ni accorder d'accès supplémentaire
* `Fonctionnalité` Le Centre des plugins propose une page de paramètres globaux et une page d'instructions
* `Fonctionnalité` La métadonnée de plugin `instruction` accepte des ressources d'instructions `@raw/...` localisées, et le résolveur partagé peut désormais lire les références `@string/...` et `@raw/...` locales ou qualifiées par un nom de paquet
* `Fonctionnalité` Le Centre des plugins prend en charge le montage automatique des plugins SDK
* `Fonctionnalité` Le Centre des plugins prend en charge le tri par priorité de plusieurs variantes
* `Fonctionnalité` Le Centre des plugins permet d'installer et de mettre à jour des plugins en ligne
* `Fonctionnalité` Le Centre des plugins permet d'ouvrir la page de lancement d'un plugin et de masquer des plugins
* `Fonctionnalité` Le Centre des plugins permet de détecter l'intersection des ABI disponibles et d'afficher des indications
* `Fonctionnalité` Le Centre des plugins résout les versions officielles d'APK Builder Template via la matrice de compatibilité de l'hôte, sélectionne l'artefact ABI exact de l'appareil avec repli sur universal, conserve le canal index de releases/tag existant et guide la désinstallation/réinstallation lorsque la version appariée est inférieure au plugin installé
* `Fonctionnalité` Les Runtime Kits APK Builder peuvent déclarer un intervalle fermé de correctifs hôte explicitement autorisé et vérifié; l'hôte exact de construction continue sans avertissement, les autres hôtes dans l'intervalle reçoivent un avertissement localisé, et les déclarations hors intervalle ou incorrectes sont bloquées avant le transfert du modèle
* `Fonctionnalité` Les options développeur ajoutent un commutateur R1 désactivé par défaut pour la construction distante expérimentale d'APK; l'état désactivé bloque la découverte du provider et l'ouverture de session, les échecs arrêtent la construction en cours sans repli local automatique, la sortie existante reste protégée par publication atomique, et les diagnostics du processus ne conservent que des champs enum et numériques bornés
* `Fonctionnalité` Le Centre des plugins prend en charge les plugins APK Builder Template, permettant d'installer et de mettre à jour à la demande les modèles nécessaires à l'empaquetage
* `Fonctionnalité` Un appui long sur le bouton flottant de l'onglet Plugins de la page d'accueil ouvre le Centre des plugins
* `Fonctionnalité` Assistants de configuration pour lancer l'un après l'autre l'assistant des permissions et l'assistant d'installation des plugins, proposés par un message de bienvenue sur la page d'accueil après le premier lancement (entrée : Paramètres > Configuration > Assistants de configuration)
* `Fonctionnalité` Assistant des permissions pour cocher les permissions du tiroir latéral de l'accueil et les demander l'une après l'autre, avec les profils intégrés "Standard" / "Minimal" / "Tous" et des profils personnalisés; les permissions accordées peuvent être masquées depuis le menu (entrées : Assistants de configuration / bouton du titre du groupe "Permissions" dans le tiroir latéral de l'accueil)
* `Fonctionnalité` Assistant d'installation des plugins pour cocher les plugins officiels par catégorie et les installer en une seule fois, avec les profils intégrés "Standard" / "Minimal" / "Tous" et des profils personnalisés pouvant être enregistrés, renommés et supprimés; les plugins installés peuvent être masqués depuis le menu (entrées : Assistants de configuration / menu du bouton flottant de l'onglet Plugins de la page d'accueil / menu "+" du Centre des plugins)
* `Fonctionnalité` L'assistant d'installation des plugins installe pendant le téléchargement, installe en silence via le plugin 3-Setup Installer lorsqu'une autorisation Shizuku / Root / Dhizuku est utilisable, et vérifie la permission d'installer des applis inconnues avant le téléchargement lorsque l'installateur du système est utilisé
* `Fonctionnalité` Méthode runtime.loadAar, pour charger dynamiquement des fichiers AAR
* `Fonctionnalité` Entrée explicite runtime.loadJarWithR8 avec sélection de composant exact désactivée par défaut, épinglage de l'identité de même signature, transport R8 borné de cinq artefacts, adoption d'un cache isolé et échec fermé sans repli D8/dx
* `Fonctionnalité` ui.useAndroidLayout prend en charge les widgets de mise en page Android natifs, include/merge, les références res des projets de scripts et les fichiers Drawable XML comme shape/selector/layer-list
* `Fonctionnalité` Les modules JavaScript (Rhino) prennent en charge les interfaces compatibles power/settings/work_manager et l'enveloppe AppInfo, et complètent certaines API Base64/Crypto/WebSocket/ByteString/automator/images/timers d'Auto.js Pro 9.3.11
* `Fonctionnalité` L'objet global ai de JavaScript (Rhino) route chaque requête locale et en ligne via une cible de plugin AI Provider; les options pour les développeurs ouvrent les réglages fiables de 3-Stone AI ou un guide exact d'installation/activation/mise à jour, tandis que l'hôte ne stocke plus de profils ni d'identifiants AI et n'inclut plus de transport HTTP de fournisseur
* `Fonctionnalité` L'API globale JavaScript (Rhino) `ai.catalog` expose un catalogue unifié des cibles locales et en ligne du plugin; omettre `plugin` et `target` sélectionne la cible par défaut déclarée du plugin officiel, `target` sélectionne une cible exacte et `plugin` un composant tiers exact; les réponses incluent reasoning, usage et durée complets, finish reason, profile et metadata de la cible résolue, et les erreurs stables ne changent jamais de route silencieusement
* `Fonctionnalité` L'API globale JavaScript (Rhino) `ai.session` crée une Conversation persistante de plugin AI provider, fixe la cible/le fournisseur/le modèle/les contrôles de génération et n'envoie que le nouveau prompt utilisateur aux tours `ask`/`chat`/`stream` suivants; une fin normale conserve le contexte, tandis qu'une annulation, un timeout, un échec de protocole ou une fermeture explicite libère toute la session
* `Fonctionnalité` Les routes AI provider `ai.ask`/`ai.chat`/`ai.stream` et la session persistante `ai.session` prennent en charge `structuredJson` et l'objet JSON `responseSchema`; un schema active le décodage natif contraint par JSON Schema de LiteRT-LM, la session conserve le même schema à chaque tour et le JSON final est validé strictement
* `Fonctionnalité` Les cibles locales AI Provider V2 exposent les profils backend explicites `cpu`, `gpu` et `npu` comme contrôles facultatifs de cible; la disponibilité dépend de l'appareil, un profil indisponible échoue sans repli CPU, une session fixe un backend, GPU exige OpenCL système et NPU reste indisponible faute de runtime EAP intégré
* `Fonctionnalité` Le module global yolo JavaScript (Rhino) fournit une route désactivée par défaut vers un composant précis de fournisseur YOLO, avec transport Binder/PFD versionné, chargement des modèles NCNN piloté par manifeste, détection d'objets, annulation et nettoyage des ressources explicites, sans repli silencieux vers un fournisseur
* `Fonctionnalité` Le module global tts JavaScript (Rhino) propose la synthèse vocale du système, le découpage intelligent des textes longs, des files logiques, la découverte des voix et des langues avec filtrage des voix hors ligne, des tâches observables et des événements audio, l'insertion de silences et la synthèse vers des fichiers avec publication atomique; il intègre aussi les délais d'expiration, la contre-pression, le nettoyage à l'exécution et la protection contre l'emploi d'un moteur de repli lorsqu'un moteur précis est demandé
* `Fonctionnalité` Méthode device.getMarketName, pour obtenir le "nom commercial" de l'appareil _[`issue #513`](http://issues.autojs6.com/513)_
* `Fonctionnalité` Les tâches planifiées prennent en charge les tâches mensuelles/annuelles/avec compte à rebours et les conditions de fin de répétition _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge la sélection multiple et les opérations groupées de copie/déplacement/suppression
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge la prévisualisation sécurisée des fichiers Markdown et HTML, avec rendu GFM, plusieurs thèmes, CSS personnalisé et chargement contrôlé des ressources du même répertoire
* `Fonctionnalité` Les aperçus Markdown et HTML prennent en charge le mode plein écran, avec un paramètre "Démarrer en mode plein écran" désactivé par défaut
* `Fonctionnalité` Le gestionnaire de fichiers propose des actions par icône tout à gauche pour l'aperçu Markdown/HTML, l'affichage des images et la lecture audio/vidéo; l'application peut servir de visionneuse multimédia système, avec lecture audio en arrière-plan et commandes de notification
* `Fonctionnalité` Le gestionnaire de fichiers reconnaît les livres électroniques `.epub` comme un type de document prévisualisable et les ouvre avec le plugin Readium EPUB Reader via le contrat Explorer Action; les livres ouverts directement depuis une archive disposent d'une limite dédiée de 256 MiB
* `Fonctionnalité` Barre de chemin épinglée en haut du gestionnaire de fichiers avec affichage du chemin actuel, mise en évidence du dossier actuel, accès direct aux dossiers parents, défilement automatique jusqu'à la fin des chemins longs, navigation horizontale, couleurs parent/actuel assorties aux libellés de catégorie/noms de fichiers et centrage optique des différentes écritures et chevrons de séparation selon le barycentre alpha des glyphes
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge les informations sur les images et l'affichage détaillé des données EXIF
* `Fonctionnalité` L'édition d'images et la conversion de formats sont assurées par le plugin Image Tools indépendant; lorsque le plugin est indisponible, seules les actions correspondantes sont masquées, tandis que l'affichage des images, les informations sur les images et les données EXIF restent disponibles
* `Fonctionnalité` La visionneuse d'images découvre automatiquement toutes les images du même répertoire et prend en charge le zoom par pincement synchronisé sur le point focal, la navigation par glissement et par boutons précédent/suivant de la barre d'outils, une barre d'informations compacte sur trois lignes pour la position/la résolution/la taille/le format/la date de modification/EXIF, le mode plein écran, un paramètre de démarrage en plein écran et une couleur d'arrière-plan configurable avec historique, tandis que l'ActionBar, les barres système et les icônes adaptent leurs couleurs et leur contraste à l'arrière-plan
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge les détails du package et l'affichage du manifeste pour les fichiers APK/APKS/XAPK/APKM/APKZ/AAB
* `Fonctionnalité` Le programme d'installation prend en charge les APK et les ensembles d'APK fractionnés compatibles des conteneurs APKS/XAPK/APKM/APKZ, avec sélection des fractions pour l'appareil et vérification de la cohérence des certificats de signature
* `Fonctionnalité` L'éditeur de code prend en charge le chargement d'Ace depuis un plugin externe, avec validation du signataire et de la confiance, vérification du contrat Editor API/de la version hôte/de la compatibilité ABI binaire, et repli automatique vers l'éditeur natif lorsque le plugin est indisponible
* `Fonctionnalité` L'éditeur de code prend en charge le branchement à chaud du plugin Ace externe à l'exécution et change automatiquement de moteur selon l'état d'installation ou d'activation du plugin
* `Fonctionnalité` L'éditeur Ace active désormais par défaut sa complétion intelligente TypeScript locale intégrée, avec complétion de l'API AutoJs6, aide aux signatures, informations au survol et diagnostics ; elle peut être désactivée dans les paramètres
* `Fonctionnalité` Les paramètres de l'éditeur Ace permettent de charger sélectivement les groupes de déclarations TypeScript AutoJs6 android/libraries/resources/main-app; les dépendances sont chargées automatiquement et tous les groupes facultatifs sont désactivés par défaut afin de réduire le temps de démarrage du service de langage et l'utilisation de la mémoire
* `Fonctionnalité` L'éditeur Ace prend en charge un panneau de raccourcis/l'historique des actions/les actions favorites/la sélection de texte sur mobile/le zoom par pincement et plus encore
* `Fonctionnalité` L'éditeur de code prend en charge le rechargement ou la conversion des fichiers avec un encodage sélectionné, un BOM facultatif, des indicateurs de risque d'encodage et l'annulation/rétablissement
* `Fonctionnalité` La barre d'informations de l'éditeur de code située sous la barre d'outils affiche le nom du fichier, la ligne et la colonne du curseur en temps réel ainsi que l'encodage du fichier ; un appui sur chacun des trois éléments permet respectivement d'afficher les détails du fichier, d'accéder à une ligne ou de modifier l'encodage
* `Fonctionnalité` La barre d'état système, l'ActionBar, les icônes et textes des boutons, les menus de débordement et imbriqués, les boîtes de dialogue intégrées à l'éditeur ainsi que le curseur, les poignées et les contrôles de sélection de l'éditeur natif suivent dynamiquement le thème actuel de l'éditeur; les icônes de la barre d'état conservent automatiquement un contraste clair/sombre pour un rendu visuel unifié du haut de l'écran jusqu'à la zone de code
* `Fonctionnalité` L'éditeur de code prend en charge un mode plein écran immersif, avec une préférence persistante accessible depuis le menu et les paramètres; un glissement depuis un bord affiche temporairement les barres système, tandis que Retour quitte d'abord le plein écran avant de quitter l'éditeur
* `Fonctionnalité` L'éditeur de code prend en charge un panneau de journal inférieur (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Fonctionnalité` Le panneau de journal inférieur de l'éditeur de code permet d'accéder au code source depuis les cadres de pile (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Fonctionnalité` Le panneau de journal inférieur de l'éditeur de code propose un bouton "Exécuter/Arrêter" pour démarrer et arrêter rapidement les scripts
* `Fonctionnalité` L'éditeur de code prend en charge une page de paramètres dédiée et une boîte de dialogue dans l'éditeur, avec aperçu des thèmes/configurations indépendantes des modes clair et sombre/polices/gouttière des numéros de ligne/retour automatique à la ligne et d'autres options d'affichage
* `Fonctionnalité` La page Paramètres propose des options d'éditeur comme "Afficher automatiquement le panneau de logs inférieur après l'exécution" et "Seuil de surlignage du code"
* `Fonctionnalité` La configuration des applications empaquetées permet d'afficher ou de masquer les raccourcis de lanceur "Journal" et "Paramètres" _[`issue #550`](http://issues.autojs6.com/550)_
* `Fonctionnalité` L'empaquetage d'applications prend en charge les compilations distantes à l'aide de plugins APK Builder Template
* `Fonctionnalité` Les options pour développeurs permettent de configurer un jeton d'accès GitHub afin d'augmenter les limites de requêtes de vérification des mises à jour et d'index des plugins
* `Fonctionnalité` L'intégration R8 JavaScript globale (Rhino) ajoute trois surcharges `runtime.loadJarWithR8` d'export vérifié et `runtime.retraceR8Stack` du protocole 1.1; mapping/seeds/usage/retrace metadata sont publiés atomiquement, retrace lie la provenance du mapping et un fournisseur absent ou invalide échoue sans repli D8/dx ou local
* `Fonctionnalité` Les paramètres de l'éditeur de code affichent désormais la matrice des langages Ace et des commutateurs sémantiques pour TypeScript/JavaScript, Python, Lua et Java; Kotlin reste visible en P2+ sans fournisseur, et les types de fichiers sont clairement une liste d'autorisation avant routage plutôt qu'une reclassification du langage
* `Fonctionnalité` Les boîtes de dialogue de mise à jour peuvent afficher dans l'application l'historique des versions du plugin correspondant
* `Fonctionnalité` `auto.registerEvent(name, listener, options?)` gagne une déduplication et un anti-rebond optionnels : `{ distinct: 'window' | 'source' | true | (event) => key, within?: 500 }` ignore les événements répétés de même clé dans la fenêtre (comme les plusieurs `WINDOW_STATE_CHANGED` d'une même transition de fenêtre), `{ debounce: ms }` ne délivre que le dernier événement d'une rafale une fois celle-ci calme pendant cette durée ; les deux sont désactivés par défaut
* `Fonctionnalité` Shizuku rejoint root et la permission des paramètres sécurisés comme moyen d'activer le service d'accessibilité sans intervention (utilisé lorsqu'aucun des deux autres n'est disponible, en écrivant le paramètre système via le service utilisateur Shizuku) : nouvel interrupteur "Activer le service d'accessibilité avec Shizuku automatiquement" (activé par défaut) dans la page des paramètres et dans la section paramètres du gestionnaire du service d'accessibilité, plus une ligne "Accès Shizuku" dans la section état du gestionnaire
* `Fonctionnalité` Nouvel indicateur `appWindowsFallback` de `auto.setFlags()` : tant que le système ne fournit pas encore la racine de la fenêtre active (juste après la liaison du service d'accessibilité ; sur Android 8.0 éventuellement jusqu'au prochain changement de fenêtre), les sélecteurs et `auto.root` / `auto.rootInActiveWindow` / `auto.windowRoots` se rabattent sur les racines des fenêtres d'application de la liste des fenêtres (fenêtre ayant le focus en premier) ; désactivé par défaut
* `Fonctionnalité` Nouvelles méthodes de `UiObject` : `contentInvalid()` / `contextClickable()` / `multiLine()` / `dismissable()` (dans le style de `clickable()` et des autres méthodes de propriétés booléennes, reflétant les accesseurs compat existants `isContentInvalid`, etc.)
* `Fonctionnalité` Nouvelles méthodes sur les collections de contrôles (les tableaux renvoyés par `find()` / `untilFind()` / `children()` / `siblings()` et consorts) : `at(i)` (négatif compte depuis la fin, null hors limites), `first()` / `last()` (null si vide), `nonNull()` (écarte les entrées null), `slice(start?, end?)` (règles d'index de `Array.prototype.slice`, le résultat conserve les méthodes de la collection), et les variantes par nœud `performActionEach(action, ...args)` / `clickEach()` / `longClickEach()` / `scrollForwardEach()` / `scrollBackwardEach()` / `setTextEach(text)` (un tableau de booléens, chaque nœud est tenté ; la sémantique de ET booléen des `performAction` / `click()` etc. existants est inchangée) ; `filter` / `map` / `forEach` / `some` / `every` / `indexOf` / `for...of` sont les opérations propres du tableau et continuent de fonctionner comme avant
* `Fonctionnalité` API de clic par coordonnées smartClickBounds / clickBoundsIfExists / clickBoundsAny, étapes Flow clickBounds avec attente ou délai et leurs alias
* `Fonctionnalité` Étapes chaînées clickIfExists / clickAny / findAny sur les instances Flow, branches facultatives whenPresent et boucles asynchrones bornées repeatUntil
* `Fonctionnalité` Nouvelle API d'attente asynchrone chaînable : `waitAsync(cond, timeout?, interval?)` (alias `wait.async` / `auto.wait` / `flow.wait`) renvoie un objet `Flow` au lieu de bloquer le script ; la condition peut être un sélecteur (chaîne / expression régulière / `UiSelector` / objet / tableau), une fonction, un `UiObject`, un `Flow` ou une `Promise` ; la chaîne peut se poursuivre par des actions de noeud (`click` / `longClick` / `setText` / `scrollForward`, etc.), des actions globales (`back` / `home` / `swipe`, etc.), `sleep` / `timeout` / `retry` / `scope` et les rappels `then` / `catch` (`else`) / `finally` / `map` / `filter` / `peek` / `run`, livre son résultat via `sync()` / `await()` / `toPromise()` et s'annule avec `cancel()` ; points d'entrée globaux `waitThenClick` (`clickWait`), `waitForStable`, `waitForStableThenClick` (`clickWhenStable`), `clickWhenStableAfter`, `waitForVisible`, `waitForHidden` (`waitForGone`), et dans l'espace de noms `flow` (`$flow`) aussi `waitUntil` / `waitWhile` / `waitForActivity` / `waitForPackage` / `waitThenLongClick` / `sleep` (`delay`) / `run` / `of` / `all` / `race` / `any` / `cancelAll` / `Flow` / `pending` ; les étapes bloquantes s'exécutent sur un pool de threads de travail propre au script (threads `flow-<id>-<n>`, une voie séquentielle par chaîne, de sorte que les chaînes indépendantes s'exécutent en parallèle) et les rappels sont renvoyés au thread de script qui a créé la chaîne (utilisable en mode UI, mais `sync()` est interdit sur le thread UI), et à la sortie du script toutes les chaînes en attente sont annulées et le pool arrêté ; les échecs sont rejetés avec un `FlowError` (un `Error` portant `code` / `step` / `selector` / `elapsed` / `attempts` / `reason`) dont le code est `TIMEOUT` / `ACTION_FAILED` / `CANCELLED` / `A11Y_UNAVAILABLE` / `INVALID_TARGET` ; `flow.defaults({ timeout, interval, stableFor, strictActions, trace })` définit les valeurs par défaut du script (une lecture sans argument) et `flow.trace(true)` journalise dans la console le début / la fin / la durée / le résultat de chaque étape ; les étapes pratiques `clickAfter(min, max?)` / `longClickAfter` / `clickWhenStableAfter` et `sleep(min, max?)` acceptent une chaîne `"±delta"` comme le `sleep` global ; le `wait()` synchrone est inchangé
* `Fonctionnalité` Outils d'automatisation de haut niveau à la fois sur le module `automator` et dans la portée globale : `smartClick(target, options?)` (remonte vers un ancêtre cliquable quand la cible ne l'est pas, se rabat sur un geste de tap quand rien ne l'est, et vérifie éventuellement une condition après le clic), `clickIfExists` / `clickAny` / `findAny`, `scrollUntil(target, options?)` (fait défiler un conteneur jusqu'à l'apparition de la cible, par actions d'accessibilité ou par gestes, un contenu inchangé à répétition valant fin de liste), `typeInto(target, text, options?)` (un sélecteur / un index / le champ ayant le focus ; remplacer ou ajouter ; vérification par relecture et validation), `dismissPopups(targets, options?)` (ferme une / toutes les popups, ou avec `watch: true` renvoie un garde d'arrière-plan piloté par les événements de changement de fenêtre), `collectList(container, item, options?)` (fait défiler et collecte les éléments, dédoublonnés par contenu), `launchAndWait` / `backUntil` / `backToApp`, `toggle(target, checked)` (ne clique que si l'état diffère, puis vérifie) et `retry(fn, options?)` ; Flow gagne les étapes correspondantes `.smartClick()` / `.scrollUntil()` / `.typeInto()` / `.dismissPopups()` / `.collectList()` / `.launchAndWait()` / `.backUntil()` / `.backToApp()` / `.toggle()` et les racines `flow.*` ; tout échec est un `FlowError` (`code` / `step` / `selector` / `reason`) ; `flow.defaults({ humanize: { offset, delay } })` donne aux étapes d'action de Flow et aux outils un décalage aléatoire du tap et une pause avant chaque action, à l'allure humaine
* `Fonctionnalité` Diagnostic d'accessibilité : `auto.explain(selector, root?)` comptabilise un sélecteur filtre par filtre (correspondances de chaque filtre seul et ce qui reste une fois les filtres précédents appliqués, le filtre qui ne laisse rien, les nœuds presque retenus qui passent toutes les étapes précédentes), le `toString()` du résultat étant le rapport texte ; `auto.dump(options?)` produit un instantané lisible des racines de fenêtre (ou de `root`) (texte indenté / JSON / XML façon uiautomator, avec `maxDepth`, `properties`, `visibleOnly` et `maxNodes`) ; `UiObject.toJSON()` (pour que `JSON.stringify(node)` fonctionne) et `UiObject.dumpSubtree(options?)` ; `auto.stats` conserve les durées de recherche / de requête par sondage / du sélecteur le plus lent / des étapes Flow du script (`reset()` les efface, `toString()` est le rapport texte) ; le générateur de code de l'inspecteur de mise en page gagne une option « Style de code » qui émet des extraits façon Flow (comme `waitThenClick(selector).sync()`)
* `Fonctionnalité` Attentes pilotées par les événements : `waitForIdle(quietFor? | { quietFor?, timeout?, eventTypes? })` renvoie `{ quietFor, elapsed, events }` dès qu'aucun événement d'état / de contenu / de liste de fenêtres n'est arrivé pendant `quietFor` ms, remplaçant le « dormir un moment puis chercher » ; `waitForEvent(type?, filter?, timeout?)` attend le prochain événement d'accessibilité (le type un nom, un tableau de noms ou `"*"` ; le filtre une fonction ou `{ packageName, className, text }` avec chaînes / regex) ; `waitForToast(text?, timeout?)` et `waitForNotification(filter?, timeout?)` attendent un toast d'une autre application ou une notification (filtres par sous-chaîne / regex / fonction / objet) ; les quatre existent comme racines Flow (`flow.waitForIdle(...)` etc.) et étapes chaînées (un `waitForIdle` chaîné laisse passer la valeur), et un dépassement du délai lève / rejette avec un `FlowError` (`code` `TIMEOUT`) ; `auto.setFlags(["eventAssistedPolling"])` fait réessayer les requêtes de sélecteur par sondage (`findOne(timeout)` / `untilFind()` et consorts) et les attentes Flow dès qu'un événement d'accessibilité arrive, l'intervalle de repli des requêtes passant à 200 ms tant que rien ne change
* `Fonctionnalité` Capacités de sélecteur et de nœud : la syntaxe de sélecteur en chaîne `select("text=Login clickable")` (`key=value` / `key~=regex` ou `key=/regex/flags` / `key` / `!key` séparés par des espaces, des guillemets autour d'une valeur contenant des espaces, une valeur sans guillemets contenant des virgules découpée en plusieurs arguments au besoin comme dans `bounds=0,0,100,200`, les clés étant celles du registre des sélecteurs ; `UiSelector.select(String)` ajoute les filtres et se renvoie lui-même, donc s'enchaîne avec les builders ; une chaîne mal formée lève une erreur avec la position du caractère, le terme fautif et des clés candidates ; `select()` sans argument reste l'action de nœud) ; `UiObject.isStale()` / `waitUntilGone(timeout?, interval?)` / `waitForStable(stableFor?, timeout?, interval?)` pour les vérifications d'état et les attentes au niveau du nœud (délai par défaut 10 s, intervalle 50 ms ; réveillées plus tôt par les événements d'accessibilité sous le flag `eventAssistedPolling` ; refusées sur le thread UI) ; `findIterator()` pour les recherches paresseuses (parcourues dans l'ordre de `find()` au fil de l'itération, le reste de l'arbre non parcouru après un `break` dans `for...of` ou `close()`, `collect()` pour les nœuds restants, compteurs `visited` / `produced`) ; des objets de filtre de fenêtre `{ type, packageName, title, displayId, active, focused, id, layer }` pour `auto.setWindowFilter(...)` et les nouveaux `auto.findWindows(options?)` / `auto.findWindowRoots(options?)` (noms de type tels que `application` / `inputMethod` / `system` / `accessibilityOverlay`, nom de package et titre en chaîne ou regex, les fenêtres antérieures à l'API 30 comptées sur l'écran par défaut)
* `Fonctionnalité` Robustesse (roadmap P5.5) : un `waitForEvent` / `waitForIdle` / `waitForToast` / `waitForNotification` en attente (forme synchrone ou Flow) se termine aussitôt par `A11Y_UNAVAILABLE` au lieu d'expirer lorsque le service d'accessibilité est détaché entre-temps ; les attentes Flow gagnent l'option `recoverService` (aussi par défaut via `flow.defaults({ recoverService: true })`) : une sonde qui rencontre une interruption du service attend alors silencieusement le service, seconde par seconde dans le délai restant (un service activé mais non lié est réactivé par les stratégies de démarrage, la page des paramètres n'est jamais ouverte et l'utilisateur n'est sollicité qu'une fois au plus), l'attente poursuit sa cible quand le service revient à temps et est sinon rejetée avec un `A11Y_UNAVAILABLE` portant `elapsed` / `attempts` ; lorsque la plateforme saute `onServiceConnected`, le registre adopte l'instance à partir de son premier événement et note le drapeau `adoptedByEvent` (`auto.state.adoptedByEvent`, une mention sur la ligne d'instance dans le processus du diagnostic du gestionnaire de services), et les préférences des informations du service (mode stable, observation des gestes) sont appliquées à partir de ce premier événement
* `Fonctionnalité` Exemples d'accessibilité (roadmap P6.4) : six scripts ajoutés au répertoire d'exemples intégré "无障碍服务" (attentes asynchrones et actions chaînées avec Flow, remplissage de formulaire et fermeture de fenêtres contextuelles avec la boîte à outils, défilement et collecte de listes avec la boîte à outils, sélecteurs textuels et itération paresseuse, diagnostic des sélecteurs et export de l'arbre des nœuds, attentes pilotées par les événements), chacun ciblant la fenêtre flottante du script lui-même, sans autre application
* `Fonctionnalité` Les trois boutons de la barre de titre de la console flottante (réduire, configuration de la position, fermer) sont fusionnés en un seul bouton "Plus" dont le menu propose Réduire, Redimensionner, Barre de saisie, Effacer, Copier, Paramètres et Fermer ; "Redimensionner" active un mode de redimensionnement (le contenu s'assombrit et affiche la taille actuelle, faites glisser le bord droit ou inférieur ou le coin inférieur droit pour redimensionner, touchez ailleurs pour quitter) ; la boîte de dialogue "Paramètres" permet de basculer le préfixe horaire, le format horaire (préréglages ou motif personnalisé), la coloration des journaux, la taille du texte, l'évitement de la barre d'état ainsi que l'opacité de la barre de titre et du contenu
* `Fonctionnalité` La console ajoute `setTimeVisible` / `setTimeFormat` / `setColorful` / `setAvoidStatusBar` / `setInputVisible` et les options `console.build` correspondantes (`timeVisible` / `timeFormat` / `colorful` / `avoidStatusBar` / `inputVisible`), reflétant les options de la boîte de dialogue des paramètres de la fenêtre flottante ; les menus "plus" de la page des journaux et du panneau de journaux de l'éditeur ajoutent "Paramètres" (préfixe horaire, format horaire, coloration des journaux, taille du texte) et un commutateur "Barre de saisie", chacun mémorisé séparément ; nouveaux exemples "Console/Paramètres d'heure et de coloration" et "Console/Paramètres indépendants de l'ordre"
* `Fonctionnalité` Restaure `console.rawInput(prompt?, ...args)` et `console.input(prompt?, ...args)` (abandonnés depuis 6.3.1) : ils bloquent le thread du script jusqu'à ce que l'utilisateur soumette une ligne dans la barre de saisie de la console, et `input` évalue la ligne comme une expression JavaScript ; la barre de saisie est masquée par défaut et n'apparaît que pendant qu'un script attend une saisie (la fenêtre flottante s'affiche et prend le focus si rien n'affiche la console, et remonte pour éviter le clavier), ou reste visible via `console.setInputVisible(true)` ou l'élément "Barre de saisie" du menu "plus" de chaque console ; le texte soumis est répété avec le préfixe `> ` et la saisie en attente est annulée à la fin du script ; nouvel exemple "Console/Saisie interactive"
* `Fonctionnalité` Le mode UI ajoute un élément `<globalconsole>` affichant la console globale, tandis que `<console>` affiche désormais la console du script lui-même (comme dans Auto.js 4) ; aucun des deux n'affiche de préfixe horaire par défaut ; `<console global="true">` et la forme à attribut nu `<console global>` équivalent à `<globalconsole>` (le XML de mise en page accepte les attributs nus, interprétés comme `"true"`) ; les attributs de l'élément couvrent les méthodes `console.setXxx` (`title` / `titleTextSize` / `titleTextColor` / `titleBackgroundColor` / `titleBackgroundTint` / `titleBackgroundAlpha` / `titleIconsTint` / `contentTextSize` / `contentTextColor` / `contentBackgroundColor` / `contentBackgroundTint` / `contentBackgroundAlpha` / `textSize` / `textColor` / `backgroundColor` / `backgroundTint` / `backgroundAlpha` / `timeVisible` / `timeFormat` / `colorful` / `inputVisible` et plus), et définir `title` affiche une barre de titre avec un menu "Plus" ; nouvel exemple "Mise en page/Élément console"
* `Fonctionnalité` Les abonnements du pont Node.js transmettent les événements des capteurs, WebSocket, interfaces, fenêtres flottantes et entrées via les callbacks existants, avec on/once/off, files bornées et compatibilité drainEvents
* `Fonctionnalité` Option idleExitMs pour arrêter le processus Node.js inactif et reconnecter le script suivant, avec diagnostic idleForMs et maintien en mémoire par défaut
* `Fonctionnalité` Entrée console native avec process.stdin / readline et messages JSON par exécution via autojs6:host; transaction postMessage v3 compatible avec les anciens hôtes
* `Fonctionnalité` Les sessions de capture Node.js utilisent le consentement Android et le service de premier plan existant, avec des handles d’image, la sauvegarde PNG/JPEG/WebP et la libération à l’arrêt ou à la fin du script
* `Fonctionnalité` Les handles d’image Node.js prennent en charge le découpage, le redimensionnement, les niveaux de gris, le seuillage et la recherche de modèles ou de couleurs via le moteur hôte, avec des images indépendantes et leur libération en fin d’exécution
* `Fonctionnalité` Node.js image.toBytes transfère les pixels PNG ou RGBA par descripteurs de fichiers vers des Buffer natifs, via JNI ou le pont fichier, et libère les pièces jointes après usage, expiration ou fin d’exécution
* `Fonctionnalité` Les API Node device exposent les informations actuelles du système, de l’écran, de la batterie et de la mémoire, le réglage de luminosité et le maintien temporaire de l’écran allumé; media règle le volume selon les autorisations Android
* `Fonctionnalité` autojs6:events observe les notifications Android, les Toast externes, les touches d’accessibilité et les événements écran ou batterie par callbacks, avec autorisations explicites et nettoyage automatique; events natif de Node reste compatible
* `Fonctionnalité` Node app prend en charge les services Android, les broadcasts et la recherche des applications installées; dialogs propose la saisie, les choix et les fenêtres de progression fermées avec le script, et keys expose les actions système d’accessibilité
* `Fonctionnalité` Node recorder prend en charge l’enregistrement AAC avec autorisation du microphone, notification, limite de durée et arrêt avec le script; ui.overlay permet la mise à jour des propriétés, le déplacement et les événements transmis
* `Fonctionnalité` Les scripts Node peuvent s’exécuter dans deux processus indépendants avec une file FIFO partagée et un routage des annulations et des entrées par exécution
* `Fonctionnalité` Les requêtes MediaInfo prennent en charge streamNumber à partir de 0, countGet et infoKind pour les unités, descriptions et noms lisibles; Rhino et Node conservent TEXT sur le premier flux par défaut et négocient les capacités du plugin
* `Fonctionnalité` Vérification de l'alignement ELF avant le chargement des plugins OpenCV et Image Quantization, avec une erreur claire si la taille des pages est incompatible
* `Fonctionnalité` Le Centre de plugins lit l'alignement dans l'index officiel et le manifest installé, et avertit avant d'installer ou d'activer des versions incompatibles sur les appareils de 16 KB
* `Fonctionnalité` Repli de capture MCP: image.captureScreen(options) accepte les options de recadrage, taille, format et qualité de la capture d'accessibilité et renvoie une image encodée à usage unique par descripteur de fichier. captureScreen() conserve son handle d'image; une autorisation MediaProjection absente ou révoquée produit une erreur de permission.
* `Fonctionnalité` `images.countPointsByColor(image, color, options?)` compte les pixels correspondant à une couleur via un masque OpenCV, et `images.getMeanColor(image, region?)` renvoie la couleur moyenne d'une image ou d'une région
* `Fonctionnalité` `images.readPixels` accepte un ImageWrapper en plus d'un chemin de fichier, et l'image transmise reste utilisable
* `Fonctionnalité` `images.matchTemplate` et `images.findPointByImage` acceptent l'option `scales` (un facteur ou une liste) pour rechercher des modèles capturés à d'autres résolutions; chaque correspondance expose désormais `width`, `height`, `scale`, `center` et `rect`, et `MatchingResult` gagne `size`, `isEmpty()`, `isNotEmpty()` et `filter()`
* `Fonctionnalité` Entrée "Exécution flottante" dans le menu de script de l'explorateur de fichiers, avec une barre flottante translucide pour démarrer / arrêter, modifier et consulter le journal du script, suivant le mode nuit et la couleur du thème
* `Fonctionnalité` Analyse de mise en page : appuyez longuement sur un attribut dans la boîte de dialogue d'informations du nœud pour en cocher plusieurs et les copier comme un sélecteur chaîné (id('x').text('y')) ou un sélecteur objet accepté par pickup() / detect() ; "Enregistrer la mise en page dans un fichier" écrit l'arbre de nœuds capturé dans <répertoire de travail>/layouts/<heure>.layout.json, et appuyer sur ce fichier dans l'explorateur de fichiers l'analyse hors ligne
* `Fonctionnalité` Intégration du plugin 3-Shell Terminal : l'entrée du menu latéral et la barre du projet ouvrent le terminal du plugin, "Ouvrir dans le terminal" du menu de répertoire est fourni par le plugin (1.1.0 ou ultérieur), le commutateur du menu latéral suit les sessions du plugin, et un plugin absent, désactivé ou incompatible renvoie vers le centre de plugins, ses réglages ou une invite de mise à jour
* `Fonctionnalité` Objet global de script terminal: gestion des sessions, exécution visible et événements de sortie, avec attente de sortie et codes de fin (nécessite le plugin 3-Shell Terminal) (voir Exemples > 终端)
* `Fonctionnalité` `images.matchFeatures` accepte une image ou un chemin pour chacun de ses deux arguments et détecte les caractéristiques à la volée; nouvelles options `ransacThreshold`, `minInliers` et `maxFeatures`, `ObjectFrame` expose `width`, `height`, `angle`, `bounds`, `points`, `matches` et `inliers`, et `ImageFeatures` expose `count` et `method`
* `Fonctionnalité` Contrat de compagnon d'accessibilité pour les plugins officiels: les compagnons qui publient une politique de service `follow-host` sont activés et désactivés avec le service d'accessibilité AutoJs6 via les stratégies root, paramètres sécurisés ou Shizuku configurées, et reçoivent la diffusion d'état du service hôte
* `Fonctionnalité` Exécution de projets par le plugin Bun Runtime: un script situé sous un répertoire contenant `project.json` ou `package.json` est empaqueté dans une archive d'espace de travail bornée (au plus 16384 entrées et 64 Mio, fichiers réguliers et répertoires uniquement) pour un plugin déclarant `SUPPORTS_WORKSPACE_ARCHIVE`, de sorte que les imports relatifs à l'intérieur du projet sont résolus; le fichier d'entrée porte la source actuelle de l'éditeur, les liens symboliques et fichiers spéciaux interrompent l'exécution, et les scripts isolés ou les anciens plugins conservent le chemin à source unique
* `Fonctionnalité` Instantané des informations de l'hôte pour le plugin Bun Runtime: quand le plugin déclare `SUPPORTS_HOST_INFO` et que l'autorisation `host_info` est active (par défaut), les requêtes `runScript` portent `hostInfoVersion = 1` et un Bundle `hostInfo` avec le nom du paquet hôte, `BuildConfig.VERSION_DATE` et la balise de langue préférée, de sorte que les scripts Bun lisent via la variable d'environnement `AUTOJS6_HOST_INFO_FILE` l'instantané hôte/plugin/exécution vérifié et écrit par le plugin; l'hôte n'envoie jamais sa propre version (le plugin la résout via PackageManager), l'autorisation est révocable à chaque exécution (`BunHostCapabilityGrants`) et les plugins sans cette capacité reçoivent exactement la requête précédente
* `Fonctionnalité` Pont de capacités pour les appels dynamiques du plugin Bun Runtime avec les deux premières capacités `ui.toast` / `device.info`: quand le plugin déclare `SUPPORTS_HOST_CAPABILITY_BRIDGE` et qu'au moins une des autorisations `ui_toast` / `device_info` est active (par défaut), les requêtes `runScript` portent `hostCapabilityBridgeVersion = 1`, un binder `IBunHostCapabilityBroker` lié à l'UID du plugin et à cette exécution, ainsi que les ID de capacité autorisés; les scripts Bun appellent `GET /v1/info` et `POST /v1/<capacité>` via le socket unix du plugin, l'hôte relaie le JSON sans l'interpréter (requêtes de 64 KiB, résultats de 256 KiB, 1024 appels par exécution, 4 en cours, 10 s chacun) et répond par `IBunHostCapabilityCallback`. `ui.toast` affiche au plus quatre toasts par exécution depuis le thread principal, `device.info` ne renvoie que des champs de `android.os.Build`; les deux interrupteurs se trouvent sur la page du plugin du Centre de plugins, et les plugins sans cette capacité reçoivent exactement la requête précédente
* `Fonctionnalité` Catégorie de paramètres "Corbeille et historique des versions": une sous-page de politiques de conservation permet de modifier l'historique des versions (jours de conservation, versions par fichier, taille par fichier, taille totale, taille maximale des fichiers suivis), la corbeille (jours de conservation, limite de taille) et les brouillons d'urgence (jours de conservation, limite de taille), où 0 signifie illimité et où toute modification déclenche immédiatement un nettoyage; plus un interrupteur "Effacer aussi l'historique des versions lors d'une suppression définitive"
* `Fonctionnalité` Entrées "Nettoyer maintenant" et "Paramètres" dans les menus des pages corbeille et historique des versions, et un appui long sur l'un des deux éléments du tiroir ouvre les paramètres des politiques de conservation
* `Fonctionnalité` La boîte de dialogue d'adresse du mode client peut scanner le code QR affiché par l'extension VSCode (1.1.0+) pour se connecter sans saisir l'adresse
* `Fonctionnalité` Protocole v2 de l'extension VSCode avec messages requête/réponse et méthodes de gestion de fichiers à distance (parcourir, lire, écrire, renommer et supprimer les fichiers de l'appareil, exécuter des scripts de l'appareil) pour l'extension VSCode 1.1.0+
* `Fonctionnalité` Page de liste de l'historique des versions: les entrées de l'éditeur et de l'explorateur ouvrent désormais une liste dédiée affichant l'heure, la taille, l'opération et l'encodage de chaque révision et marquant celle identique au fichier actuel; appui pour prévisualiser, restaurer ou supprimer, appui long pour la sélection multiple et la suppression, tri par heure ou par taille
* `Fonctionnalité` Vue des différences de l'historique des versions: comparez une révision avec le fichier actuel ou sélectionnez deux révisions pour les comparer; les lignes ajoutées et supprimées sont colorées ligne par ligne, les passages inchangés sont repliés et dépliables, et les lignes peuvent être recherchées par texte; un contenu trop volumineux n'affiche que les statistiques de lignes et de taille
* `Fonctionnalité` Instantanés manuels et épinglage dans l'historique des versions: le menu de l'éditeur propose "Créer un instantané" (libellé facultatif) et les instantanés sont épinglés par défaut; toute révision peut être épinglée, désépinglée ou renommée depuis la liste des révisions, les révisions épinglées sont exclues du nettoyage par jours / nombre / taille par fichier, et le nettoyage par taille globale supprime d'abord les révisions non épinglées
* `Fonctionnalité` Export de l'historique des versions: la boîte de dialogue des détails d'une révision propose "Exporter", qui, après le choix d'un dossier et la confirmation du nom de fichier (par défaut le nom d'origine suivi de l'horodatage de la révision), écrit la révision octet par octet en conservant l'encodage d'origine et le BOM; l'écrasement d'un fichier suivi conserve d'abord son contenu actuel
* `Fonctionnalité` Lorsque l'historique des versions est ouvert depuis l'éditeur, la boîte de dialogue des détails d'une révision propose "Restaurer et enregistrer": après confirmation de l'écrasement, la révision est chargée dans l'éditeur et enregistrée immédiatement sur le disque (l'option "Restaurer" par défaut ne modifie toujours que le contenu de l'éditeur)
* `Fonctionnalité` Sélection multiple dans la corbeille: un appui long sur un élément démarre la sélection, avec tout sélectionner / tout désélectionner, restauration par lot (vers le chemin d'origine de chaque élément ou vers un dossier choisi; en cas de conflit de noms, choisissez ignorer / écraser / renommer automatiquement pour tout le lot, et les éléments homonymes au sein du lot sont toujours renommés; la boîte de progression peut être interrompue et un résumé indique les nombres restaurés / ignorés / échoués) et suppression par lot de la corbeille
* `Fonctionnalité` Aperçu du contenu de la corbeille: la boîte de dialogue d'un élément propose "Aperçu", qui ouvre les fichiers texte dans l'éditeur en lecture seule, liste les fichiers d'un dossier mis à la corbeille (chemin relatif, taille, nombre de fichiers et de dossiers) et signale le contenu binaire au lieu de l'ouvrir
* `Fonctionnalité` Détails d'un élément de la corbeille: la boîte de dialogue de l'élément propose "Détails", qui affiche le nom, le type, le chemin d'origine, l'heure de mise à la corbeille, la taille, le SHA-256 et l'expiration prévue (avec les jours et heures restants) sous forme de texte sélectionnable, copiable dans le presse-papiers d'un appui
* `Fonctionnalité` Après le déplacement d'un fichier ou d'un dossier vers la corbeille depuis l'explorateur, la barre d'information propose "Annuler", qui restaure chaque élément à son chemin d'origine uniquement tant que ce chemin est libre (jamais d'écrasement); les éléments déjà retirés de la corbeille ou dont le chemin est occupé sont signalés comme ignorés dans le résumé
* `Fonctionnalité` La corbeille détecte les éléments défectueux dont le fichier de données est manquant : la liste les marque "Données manquantes" en couleur de mise en évidence, un appui ne propose que les détails et la suppression, et une nouvelle action de menu "Supprimer les éléments défectueux" les nettoie en une fois ; le nettoyage périodique ne fait que journaliser leur nombre et ne les supprime jamais automatiquement
* `Fonctionnalité` Connexion de l'extension VSCode : le mode client et le mode serveur reçoivent un dialogue "Gestionnaire" (bouton "Gestionnaire" de l'entrée du tiroir ou appui long) avec un état en direct (état de la connexion, adresse du serveur ou d'écoute, hôte distant, versions de l'extension et du protocole, durée de connexion, dernière activité, session de reconnexion, dernière erreur ; chaque ligne est copiable), des actions (connecter, déconnecter, reconnecter maintenant, arrêter la reconnexion, démarrer, arrêter, redémarrer, déconnecter tous les clients, copier l'adresse) et des interrupteurs (reconnexion automatique, nouvel essai au changement de réseau, restauration de la connexion ou démarrage du serveur au lancement, notification des changements de clients); le gestionnaire du mode serveur reçoit une section "Clients" listant chaque VSCode connecté, avec détails, déconnexion et reconnexion par client
* `Fonctionnalité` Connexion de l'extension VSCode : le mode client se reconnecte automatiquement après une déconnexion inattendue (plantage de l'extension, redémarrage de VSCode, incident réseau) avec un délai exponentiel, un nouvel essai immédiat dès qu'un réseau est disponible et une restauration au lancement de l'application ; les deux côtés annoncent les fermetures volontaires par un message bye, si bien qu'une déconnexion explicite de l'un ou l'autre n'est jamais suivie d'une reconnexion automatique
* `Fonctionnalité` Connexion de l'extension VSCode : confirmation d'appairage - un appareil inconnu se connectant en mode client doit d'abord être autorisé dans VSCode (mémorisation possible), tandis que les appareils ayant scanné le QR code sont approuvés automatiquement ; extension 1.1.0+ requise
* `Fonctionnalité` Mode serveur : découverte sur le réseau local (mDNS) - une fois activée, l'extension VSCode (1.1.0+) liste cet appareil dans son sélecteur de connexion LAN sans saisie d'adresse
* `Fonctionnalité` Fichiers distants VSCode : transfert par morceaux - les gros fichiers sont lus et écrits par morceaux de 1 MiB avec progression affichée dans VSCode ; extension 1.1.0+ requise; fs.list peut renvoyer le md5 des fichiers à la demande pour que l'extension compare les fichiers lors de la récupération des modifications du projet depuis l'appareil
* `Fonctionnalité` Gestionnaire du mode client : une entrée "Historique" dans la section de contrôle liste les adresses de serveur déjà utilisées ; appuyer pour se connecter, appui long pour supprimer, ou tout effacer
* `Fonctionnalité` Module de contrat du plugin Mail (plugin-api/mail-api): surface AIDL figee, client hote qui ouvre les sessions de compte via un bail de liaison dedie, les rouvre apres la mort du processus du plugin, reessaie une fois apres un rafraichissement du jeton XOAUTH2 et ecrit les pieces jointes sur disque en flux via des tubes, et enregistrement dans le Centre de plugins comme plugin active par defaut
* `Fonctionnalité` API de script `mail` du plugin Mail (feuille de route Mail P3.1 et P3.2) : `mail.connect(options ou alias)` avec les preselections de fournisseurs et un `tokenProvider` pour XOAUTH2, `MailClient` avec `test`, `send` (pieces jointes depuis des chemins du script via des descripteurs en lecture seule), `folders` / `folder(path)` / `createFolder` / `deleteFolder` / `renameFolder`, `fetch`, `search` avec un marqueur de repli cote client, `get` / `fetchBody`, `download` et `raw` ecrivant en flux dans le repertoire de travail avec `onProgress`, `setFlags` avec `markRead` / `markUnread` / `flag` / `unflag`, `move`, `copy`, `delete`, `expunge`, `append` et `close`, chacun avec une forme `Async` dont la Promise se resout sur le thread du script ; les messages resultants portent des champs `Date`, des adresses imprimables et des methodes liees `load` / `download`, les echecs sont des objets `MailError` avec `code` et `retryable`, et le global expose `mail.providers`, `mail.accounts`, `mail.setDefault` ainsi que le renvoi de chaque methode du client
* `Fonctionnalité` API de script `mail.watch` du plugin mail (feuille de route mail P5) : `client.watch(folder?, {mode?, pollIntervalMs?, fetchBody?, reconnect?})` et `mail.watch` renvoient un émetteur `MailWatch` dont les événements `message(message, watch)`, `mode`, `resync`, `error` et l'unique `close(reason)` arrivent sur le fil du script et le maintiennent en vie jusqu'à `stop()` ; le plugin surveille avec IMAP IDLE (repli sur l'interrogation périodique, POP3 par UIDL), l'hôte rejette les générations périmées et les livraisons hors d'ordre, la mort du plugin est signalée par `PLUGIN_UNAVAILABLE` puis suivie par défaut d'une nouvelle surveillance sur la session rouverte avec un `resync`, et fermer le client ou quitter le script arrête ses surveillances
* `Fonctionnalité` Contrat du plugin mail version 2 avec surveillances en arrière-plan (feuille de route mail P8) : les `IMailPlugin.openTrigger` / `listTriggers` ajoutés et les interfaces `IMailTrigger` / `IMailTriggerCallback` s'abonnent aux surveillances que le plugin maintient dans son propre service de premier plan, et la nouvelle tâche par intent « À l'arrivée d'un courriel » (`org.autojs.autojs6.action.MAIL_TRIGGER`, reçue derrière la permission de signature de l'hôte) lance un script pour chaque nouveau courriel de la surveillance choisie, avec un filtre facultatif sur l'expéditeur et le sujet et le document d'événement dans `engines.myEngine().execArgv.mail` ; les livraisons répétées sont ignorées et les rafales limitées à un lancement par tâche toutes les 3 s ; un plugin de version 1 ne voit jamais les nouveaux appels
* `Fonctionnalité` Module de contrat du plugin EPUB (plugin-api/epub-api) avec une surface AIDL figée pour l'extraction des livres (métadonnées, table des matières, ordre de lecture, texte des chapitres, ressources, recherche) et les sessions de lecture à jeton, l'enveloppe de découverte côté hôte du moteur `epub`, et l'enregistrement dans le Centre de plugins du paquet Readium EPUB Reader, dont les services Explorer Action et EPUB partagent une seule entrée
* `Fonctionnalité` Client hôte du plugin EPUB (core/plugin/epub, feuille de route du plugin P5.4) : `EpubPluginHost.openBook` valide le chemin, l'ouvre en lecture seule et remet un client de livre (métadonnées, table des matières, ordre de lecture, texte des chapitres paginé en texte brut ou Markdown, export de ressources via un tube tamponné, recherche, positions) sur un bail de liaison dédié avec surveillance de décès ; `openReader` ouvre une session de lecture, lit son jeton à usage unique et démarre lui-même l'Activity du lecteur du plugin (lancement en deux étapes), relaie les événements de session (open, progress, bookmark, error, close) avec contrôle de génération et de séquence, et transmet goTo, navigate, setPreferences, getBookmarks et getState ; chaque échec est traduit dans les codes d'erreur du contrat
* `Fonctionnalité` API de script `epub` du plugin Readium EPUB Reader (feuille de route du plugin P6.1) : `epub.open(chemin ou options)` renvoie un `EpubBook` avec `metadata`, `toc` (un arbre `{ title, href, children }`), `readingOrder`, `positions`, `text(target?, options?)` (un href, un index de l'ordre de lecture, une entrée de la table des matières ou le livre entier, paginé par `offset` / `maxChars` avec `format` text ou markdown), `textAll`, `cover` (une `Image` ou un fichier), `resource`, `search` et `close` ; la couche de commodité `epub.metadata` / `toc` / `readingOrder` / `text` / `cover` / `search` ouvre et ferme le livre à chaque appel, `epub.isAvailable()` sonde le plugin, chaque méthode a une forme `Async` dont la Promise se règle sur le thread du script, les chemins suivent le répertoire de travail du script et refusent les URI, les échecs sont des objets `EpubError` avec `code`, l'indication de plugin indisponible est localisée et la sortie du script ferme les livres restés ouverts
* `Fonctionnalité` Contrôle du lecteur dans l'API de script `epub` (feuille de route du plugin P6.2) : `epub.read(path, options?)` ouvre le lecteur du plugin Readium EPUB Reader et renvoie un émetteur d'événements `EpubReaderSession` dont les événements `open`, `progress`, `bookmark`, `error` et `close` arrivent sur le thread du script, avec `goTo(href | progression | locator)`, `next` / `prev` / `nextChapter` / `prevChapter`, `setPreferences(partial)`, `bookmarks()`, `close({ finish? })` et les propriétés `locator`, `progress`, `title`, `chapterTitle`, `href`, `positions`, `state`, `reason`, `isOpen` / `isClosed` ; la position de départ vient de `href`, `progression` ou `locator` avec des `preferences` facultatives, un script qui cesse de consommer ses événements reçoit `error(LIMIT_EXCEEDED)` et `close(overflow)`, et la sortie du script termine la session tandis que le lecteur reste ouvert pour l'utilisateur
* `Fonctionnalité` Exemples EPUB (feuille de route du plugin P6.3) : trois scripts ajoutés au nouveau répertoire d'exemples intégré "电子书" (EPUB 元数据与目录 : métadonnées, table des matières, ordre de lecture et nombre de positions d'un livre trouvé sous Download ou Books ; 导出章节文本 : export du texte d'un chapitre ou du livre entier en texte brut ou Markdown, export de la couverture et recherche plein texte ; 打开阅读器并监听进度 : une session de lecteur avec les écouteurs `open` / `progress` / `bookmark` / `error` / `close`, des préférences de départ, un changement de chapitre et `close()` qui laisse le lecteur à l'utilisateur) ; chaque script vérifie d'abord `epub.isAvailable()` et explique comment installer le plugin Readium EPUB Reader
* `Fonctionnalité` Version 2 du contrat EPUB avec surlignages et notes (feuille de route du plugin P9.4): le `IEpubBook.getAnnotations` ajouté (paginé, dans l'ordre de lecture) et l'événement `highlight` de la session du lecteur transportent les surlignages et notes du lecteur sous forme de document `annotations` (id, style, color, note, quote, titre du chapitre, locator, createdAt, updatedAt); l'hôte négocie la version à partir du `epubMaxContractVersion` du plugin et l'inscrit dans chaque requête, si bien que les plugins de version 1 ne voient jamais le nouvel appel; l'API de script `epub` gagne `book.annotations()` et `epub.annotations(path)` avec leurs formes `Async`, et `EpubReaderSession` émet `highlight({ action, id, style, color, note, quote, title, locator, createdAt, updatedAt })` avec `action` added, updated ou removed
* `Fonctionnalité` Contrat et relais hôte Three Stove Agent avec accès limité aux fonctions de l'appareil, quotas d'appels aux modèles et libération des ressources lors de la perte de connexion; le relais du modèle permet les appels natifs aux outils et leur continuation, avec contrôle du quota à chaque tour
* `Fonctionnalité` Enregistrement et exécution limitée des scripts Agent via project.json / en-têtes @agent, avec validation des paramètres, arrêt à expiration et résultats structurés
* `Fonctionnalité` Gestion de Three Stove Agent dans le centre de plugins avec aide pour l'installation, l'activation, les autorisations et la compatibilité; entrée facultative dans les Outils de l'assistant lorsque l'APK publié figure dans l'index officiel
* `Fonctionnalité` Les paramètres du plugin Three Stove Agent proposent des dossiers de scripts supplémentaires, validés, enregistrés et restaurés à la reconnexion par l'hôte; un dossier invalide conserve la configuration acceptée
* `Fonctionnalité` Module installer pour installer, mettre à jour et désinstaller des applications, avec opérations silencieuses Shizuku / Root et vérification des capacités pour Dhizuku, l'installation notification et l'option mode: 'persistent' de setDefault; Dhizuku reste soumis aux règles du propriétaire de l'appareil ou du profil (nécessite le plugin 3-Setup Installer) (voir Documentation du projet > [Installer](https://docs.autojs6.com/#/installer))
* `Fonctionnalité` Barre de recherche en haut des fenêtres d'analyse de la hiérarchie et des limites de mise en page, qui filtre les nœuds par mot-clé ou expression régulière sur text / desc / id / className : la fenêtre de hiérarchie ne conserve que les nœuds correspondants et leurs ancêtres en surlignant la correspondance, la fenêtre des limites ne trace que les nœuds correspondants, les correspondances peuvent être parcourues dans les deux sens, la barre se replie en une pastille déplaçable et la requête est conservée lors du passage d'une fenêtre à l'autre
* `Fonctionnalité` Dans la barre de recherche de l'analyse de mise en page, un appui sur le compteur de correspondances ouvre une liste de résultats (résumé du nœud, chemin, profondeur et limites ; appui pour sélectionner le nœud, appui long pour ses informations ; explication en l'absence de correspondance), et la barre conserve un historique des requêtes (enregistré à la validation ou à la navigation, 20 entrées au plus, affiché en liste déroulante quand le champ est vide, appui long pour supprimer, effaçable, conservé entre les sessions)
* `Fonctionnalité` Profils de filtrage dans la barre de recherche de l'analyse de mise en page : filtrer les nœuds par attributs booléens à trois états (14 attributs tels que clickable / scrollable / enabled), présence d'un id / text / desc, préréglages de classe (boutons / texte / champs de saisie / images / cases à cocher et interrupteurs / listes et défilement / conteneurs), plages depth / childCount / indexInParent, largeur et hauteur minimales, à l'écran seulement, noms de package et actions ; un profil par défaut intégré en lecture seule, des modifications appliquées immédiatement et enregistrables comme profils utilisateur (enregistrer sous / renommer / supprimer), le profil sélectionné étant mémorisé d'une session à l'autre
* `Fonctionnalité` Sélection de zone dans la fenêtre des limites de mise en page : choisissez « Sélectionner une zone » dans les profils de filtrage et tracez un rectangle pour ne garder que les nœuds entièrement contenus dedans ; la zone reste active en passant à la fenêtre de hiérarchie
* `Fonctionnalité` Mode champ dans la barre de recherche de l'analyse de mise en page : touchez le sélecteur de mode (Aa / k=v) pour saisir plusieurs conditions dans la syntaxe de chaîne des sélecteurs de script (par exemple text=OK clickable, id~=btn_.*, !enabled, minDepth=3) ; un aide-mémoire de la syntaxe s'affiche tant que le champ a le focus, les erreurs de syntaxe affichent la position, le terme et les noms candidats dans une bande d'indication, et le mode de requête est mémorisé d'une session à l'autre
* `Fonctionnalité` « Copier comme sélecteur » dans l'analyse de mise en page : touchez « Copier comme sélecteur » dans la boîte de dialogue des résultats ou appuyez longuement sur le compteur pour placer dans le presse-papiers le mot-clé (sous forme d'appel Contains / Match de la propriété text / desc / id / className touchée par la correspondance courante), les conditions de champ, le profil de filtrage et la zone sélectionnée en tant que sélecteur de script chaîné ; les conditions inexprimables sont ajoutées en commentaires
* `Fonctionnalité` Méthodes de sélecteur minDepth / maxDepth / minIndexInParent / maxIndexInParent (bornes incluses, comme minChildCount / maxChildCount), également utilisables dans la syntaxe de chaîne des sélecteurs (par exemple minDepth=3)
* `Fonctionnalité` Mode sélecteur dans la barre de recherche de l'analyse de mise en page : une fois le sélecteur de mode sur « $() », saisissez une expression de sélecteur chaînée comme dans un script (par exemple text("OK").clickable().depth(3) ou textMatch(/ok|annuler/i) ; le préfixe selector() / $() est facultatif) ; ses littéraux chaîne / nombre / booléen / regex atteignent le registre des sélecteurs avec la sémantique des scripts, une expression à moitié saisie affiche « Expression inachevée » au lieu d'une erreur, les erreurs de syntaxe affichent la position, l'appel et les noms de méthode candidats, et « Copier comme sélecteur » produit l'expression normalisée
* `Fonctionnalité` La page des modèles de génération de code permet l'importation locale, le téléchargement à la demande, la gestion et la comparaison de plusieurs versions, ainsi que la mise à jour des modèles officiels (Réglages > Service d'accessibilité > Modèles de génération de code)
* `Fonctionnalité` Bibliothèques de couleurs personnelles pour la couleur du thème : créer ou cloner une bibliothèque, ajouter des couleurs depuis la palette, une autre bibliothèque ou l'historique, puis renommer, modifier les valeurs, réordonner par glisser-déposer ou supprimer par lot ; les bibliothèques elles-mêmes peuvent aussi être réordonnées par glisser-déposer et supprimées par lot
* `Fonctionnalité` Bibliothèque de couleurs intelligente : chaque groupe prend sa couleur selon l'état actuel d'une source d'état, au choix le mode nuit, des plages horaires, le lever et coucher du soleil (emplacement d'après le fuseau horaire ou coordonnées saisies), le jour de la semaine ou jours ouvrés et week-end, les saisons, des niveaux de batterie, la charge, l'économiseur de batterie, le service d'accessibilité, des scripts en cours ou des états de script (changés avec colors.theme.state) ; les groupes changent dans l'ordre ou au hasard à chaque changement d'état, au démarrage de l'app, au démarrage d'un script, à intervalle régulier (premier changement maintenant, à la prochaine minute pleine ou à la prochaine heure pleine, au moins 5 secondes) ou à des heures quotidiennes ; les remplacements par date affichent un groupe choisi sur des périodes ou des fêtes (dont les fêtes du calendrier lunaire chinois comme la Fête du Printemps et la fête de la mi-automne, avec des jours avant ou après facultatifs) ; la rotation est suspendue tant que les pages de couleur du thème sont ouvertes
* `Fonctionnalité` Préréglages de bibliothèque de couleurs intelligente "Material jour et nuit" (19 familles Material, avec des nuances de jour et de nuit choisies selon le contraste avec les arrière-plans de fenêtre), "Couleur d'arrière-plan" et "Suivre le fond d'écran" (couleurs principale, secondaire et tertiaire du fond d'écran, Android 8.1 ou ultérieur, actualisées quand le fond d'écran change) ; cloner un préréglage, créer une bibliothèque ou la générer à partir d'une couleur de départ (couleur du thème, palette ou couleur du fond d'écran, avec une harmonie ou des nuances, couleurs de nuit remplies pour le mode nuit), puis régler dans l'éditeur la source d'état, la rotation des groupes, l'ordre et les couleurs de chaque groupe, l'éditeur de groupe pouvant remplir les autres états à partir du premier (annulable)
* `Fonctionnalité` Importation et exportation de bibliothèques de couleurs au format JSON d'AutoJs6 : exporter une bibliothèque depuis son menu d'appui long ou plusieurs avec "Exporter des bibliothèques de couleurs..." (les préréglages sous forme d'instantanés) ; l'importation présente les bibliothèques du fichier avec leur nombre de couleurs ou de groupes, crée toujours de nouvelles bibliothèques, numérote les noms en double et indique pourquoi un fichier ne peut pas être importé
* `Fonctionnalité` "Outils de couleur" dans les détails d'une couleur : harmonies complémentaire, analogue, triadique, complémentaire divisée et carrée, échelle de nuances du clair au foncé, contraste avec les arrière-plans de fenêtre de jour et de nuit (avec la couleur la plus proche atteignant 3:1 en cas d'insuffisance) et simulation de la protanopie, de la deutéranopie et de la tritanopie ; chaque couleur peut être appliquée comme couleur du thème, ajoutée à une bibliothèque de couleurs ou copiée
* `Fonctionnalité` Réglage "Couleurs dérivées" de la couleur du thème (désactivé par défaut, identique aux versions précédentes) : en mode Auto, la couleur d'accent des boutons flottants et la couleur sombre de la barre d'état sont dérivées de la couleur principale, et les plugins reçoivent les trois couleurs ; chaque état d'un groupe de bibliothèque de couleurs intelligente peut définir ses propres couleurs d'accent et sombre, prioritaires dans les deux modes
* `Fonctionnalité` Couleurs favorites : cochez "Favori" ou "Épinglée" dans les détails d'une couleur, ou appuyez longuement sur une couleur de bibliothèque ou un échantillon des outils de couleur ; la bibliothèque virtuelle "Favoris" se trie manuellement (par glisser), par date d'ajout, teinte ou luminosité avec les couleurs épinglées en tête, ajoute des couleurs depuis la palette et crée une bibliothèque de couleurs intelligente avec un groupe par favori ; les favoris servent aussi de couleurs de départ pour "Générer à partir d'une couleur de départ"
* `Fonctionnalité` Bibliothèque virtuelle "Fréquentes et recommandées" : favoris épinglés, autres favoris et couleurs de l'historique notées selon le nombre d'utilisations et leur récence (demi-vie de 14 jours), couleurs similaires fusionnées, 30 au plus et masquée tant qu'elle est vide ; les entrées s'appliquent comme elles avaient été choisies et peuvent devenir une bibliothèque de couleurs intelligente ; l'historique des couleurs compte désormais les utilisations et sa liste peut fusionner les couleurs similaires
* `Fonctionnalité` `colors.distance(colorA, colorB, algorithm?)` renvoie la distance de couleur sur l'échelle de seuil de chaque algorithme de détection, `colors.invert(color)` inverse le RGB en conservant l'alpha, `colors.blend(colorA, colorB, ratio?)` mélange deux couleurs et `colors.contrast(foreground, background)` calcule le rapport de contraste WCAG; les quatre méthodes sont aussi disponibles sur les instances Color
* `Fonctionnalité` Fonctions de couleur perceptuelles dans le module colors : toOklab / oklab et toOklch / oklch, ensureContrast (rapport WCAG, 4,5 par défaut), harmony, tones, derive (principale, accent et sombre), nightVariant, simulate (protanopie, deutéranopie, tritanopie) et extract (couleurs dominantes d'une image avec les options count, region et step) ; colors.distance, colors.isSimilar et images.detectColor acceptent l'algorithme oklab (voir Exemples > 图像与颜色)
* `Fonctionnalité` colors.theme contrôle la couleur du thème depuis les scripts : get, set (options accent et dark), reset, libraries, activate et deactivate (bibliothèques de couleurs intelligentes), next (change de groupe aussitôt), state (change la source "États du script") et current ; les modifications d'un script en moins de 500 ms sont fusionnées et notées "Script : nom" dans l'historique, et la page de couleur du thème propose "Annuler la modification du script" pour rétablir la sélection précédente, y compris une bibliothèque de couleurs intelligente active (voir Exemples > 图像与颜色)
* `Fonctionnalité` Un appui long sur TAB dans le panneau de raccourcis de l'éditeur Ace déclenche les suggestions intelligentes, qui affichent selon le contexte du curseur la complétion de code, les indications de paramètres ou la documentation du symbole, et passent à la surcharge suivante lorsque les indications de paramètres sont visibles; sur clavier physique, Ctrl+Space / Ctrl+Shift+Space / Ctrl+Q correspondent aux suggestions intelligentes / indications de paramètres / documentation du symbole
* `Fonctionnalité` Les paramètres de l'éditeur de code gagnent un groupe "Suggestions intelligentes" (moteur Ace uniquement): déclenchement de la complétion automatique (pendant la saisie d'identifiants / seulement après "." / désactivé), caractères minimum pour la complétion, délai de complétion, déclenchement des indications de paramètres (quand le curseur entre dans un appel / seulement après "(" ou "," / désactivé) et changement de surcharge avec les flèches; les modifications s'appliquent immédiatement et peuvent être enregistrées dans les actions favorites (nécessite le plugin Ace Editor 1.26.0)
* `Fonctionnalité` API de scripts compose / $compose avec 29 fabriques de noeuds, handles persistants, state/render/ref réactifs, mises à jour groupées, publications en file, thèmes et commandes de session Snackbar (nécessite le plugin Compose UI)
* `Fonctionnalité` compose.mount héberge les pages UI; compose.floaty propose aux scripts UI ou hors UI des fenêtres raw ou redimensionnables, position/taille en pixels, contrôle du toucher/focus et mises à jour depuis les workers via compose.post, avec libération des sessions à la fermeture ou à l'arrêt du script
* `Fonctionnalité` La catégorie Compose UI des exemples intégrés propose un compteur, la validation de formulaire, une liste de 1000 éléments, un HUD flottant et des thèmes, avec les prérequis de plugin et d'autorisations pour chaque script
* `Fonctionnalité` Référence API complète de Compose UI avec complétion de types Ace Editor et contenu local Offline Docs pour les composants, noeuds, états, modificateurs, thèmes, sessions et fenêtres flottantes
* `Fonctionnalité` Le Centre des plugins reconnaît et gère les plugins Compose UI installés en aperçu de développement, avec un hôte 6.8.0 / 5316 ou ultérieur
* `Fonctionnalité` TSX prend en charge `<compose.Column>`, `<compose:Text>`, les références aux fabriques de noeuds, les fragments, les emplacements et les rappels réactifs; un même arbre ne peut pas mélanger Compose et les anciens noeuds XML
* `Fonctionnalité` Les conteneurs XML `<compose>` et compose.attach intègrent des sessions Compose indépendantes dans les pages UI ou les fenêtres flottantes existantes; compose.AndroidView affiche une View Android existante ou renvoyée par une fabrique synchrone
* `Fonctionnalité` ui.inflate accepte les layouts XML TSX compilés et lie leurs rappels onClick
* `Fonctionnalité` compose.dialog renvoie une session modifiable et fermable pour les dialogues et panneaux modaux inférieurs dans les scripts UI ou ordinaires
* `Fonctionnalité` Composants Material 3 étendus: navigation et tiroirs, onglets, panneaux inférieurs et menus, sélecteurs de date et heure, pagination et grilles, puces, badges, boutons segmentés et flottants, recherche, infobulles et actualisation par glissement
* `Fonctionnalité` compose.memo réutilise les fragments de render dont les valeurs de dépendance et les états lus n'ont pas changé, de sorte qu'une mise à jour partielle d'une grande liste ne reconstruit que les lignes modifiées
* `Fonctionnalité` Les options de développement proposent "Autoriser Inspector pour les scripts Node.js", désactivé par défaut, pour les points d'arrêt et le profilage CPU/mémoire en Debug et Release avec un plugin Node.js Runtime compatible; DevTools se connecte via la redirection de ports ADB
* `Fonctionnalité` La fonction "Générer du code" de l'inspecteur de disposition prend en charge les plans de localisation relative qui identifient la cible par le text / desc / id d'un élément voisin et une relation de boussole fixe (par exemple l'icône d'une ligne de liste localisée par le titre de la ligne), en générant des appels filter et compass
* `Correction` Le filtre de recherche des plugins restait actif après la fermeture de la recherche ou un changement d'onglet
* `Correction` Un plantage de l'hôte lors du rafraîchissement des couleurs intelligentes au retour au premier plan ou à la recréation d'une page, quand les rappels des composants enregistrent de nouvelles cibles de thème
* `Correction` Le centre de plugins affichait Unknown pour les ABI des plugins officiels vérifiés sans code natif, et perdait les métadonnées de compatibilité native de l'index après un redémarrage
* `Correction` L'assistant d'installation des plugins attendait un délai après un échec ou une annulation lors d'installations successives via l'installateur système
* `Correction` Le centre de plugins sélectionnait un APK x86_64 sur un appareil x86 malgré la présence d'un APK x86
* `Correction` Échappement incomplet des guillemets, barres obliques inverses et caractères de contrôle dans "Copier en tant que sélecteur" de l'inspecteur de disposition et la représentation textuelle des sélecteurs
* `Correction` La génération de code de l'inspecteur de disposition ignorait la saisie de texte et certaines actions de recherche, appliquait les actions à tout le groupe pour une seule cible de liste, utilisait des appels obsolètes, abandonnait silencieusement Flow ou échouait sur les appuis longs, et localisait incorrectement les ID identiques entre paquets
* `Correction` Informations des paquets inaccessibles dans le gestionnaire de fichiers lorsque APK Inspector masque le bouton d'information
* `Correction` Les échecs des appels aux modèles en ligne d'Three Stove Agent perdaient les catégories d'erreurs réseau, d'authentification et de réponse (nécessite la prise en charge du plugin Provider)
* `Correction` La fusion des options d'outils d'un assistant n'active plus les groupes désactivés par défaut lorsque l'objet tools de base est vide
* `Correction` Les arbres compacts d'accessibilité abrégeaient les ID de ressources, empêchant les sélecteurs littéraux de retrouver les éléments affichés
* `Correction` Compatibilité des instantanés de fichiers des plugins, de l'installation des paquets Node.js et des opérations sur les fichiers des projets TypeScript sous Android 7/8.0
* `Correction` Écouteurs des demandes d'autorisation Shizuku restant enregistrés après leur destruction
* `Correction` Traductions manquantes des notifications des tâches Python de longue durée et textes anglais manquants des paramètres du compilateur DEX
* `Correction` La lecture de l'apparence par un plugin officiel pendant le démarrage pouvait faire échouer les préférences avant la disponibilité du contexte
* `Correction` Three Stove Agent poursuivait les observations et actions sur un écran éteint ou verrouillé au lieu de renvoyer SCREEN_LOCKED
* `Correction` Texte des toasts écrit dans les journaux de l'hôte à la demande des plugins via le relais de capacités
* `Correction` Agent rejetant a tort des references de noeuds et affichant un ancien nom d'application apres la fermeture de la fenetre de confirmation
* `Correction` Les actions sur les noeuds prennent en charge les interfaces profondes et distinguent les limites et capacités des conteneurs pour identifier une cible
* `Correction` L'activation du plugin YOLO depuis le centre des plugins échouait car la poignée de main de sondage exigeait que le plugin soit déjà activé; le sondage n'exige plus que l'autorisation et la poignée de main du composant exact, tandis que les points d'entrée à l'exécution exigent toujours l'enregistrement d'activation sauvegardé
* `Correction` MCP Server installé via adb pouvait apparaître activé dans le centre des plugins mais être considéré comme désactivé à l'exécution en l'absence de préférence enregistrée; les deux utilisent désormais l'état désactivé par défaut et conservent les choix explicites de l'utilisateur
* `Correction` Lignes vides superflues sous les états courts du gestionnaire accessibilité, tels que Non lié et Aucun
* `Correction` Les réponses Fetch contrôlées de Node.js transmises en ligne dépassant la limite de taille des transactions Binder étaient perdues silencieusement, laissant les scripts attendre une expiration du pont
* `Correction` La destruction du fournisseur WebSocket de Node.js annulait les connexions avant que les pairs puissent recevoir les trames de fermeture normale
* `Correction` Projets Node.js exécutables sans métadonnées de création APK, tels que host-events, non reconnus dans la liste des fichiers et la barre de projet
* `Correction` Les journaux de début et de fin des projets Node.js affichaient main.js sans chemin au lieu du chemin complet et de l'extension réelle du fichier d'entrée, tel que main.cjs
* `Correction` Le bouton de réglages du sélecteur de couleur ouvre la page principale du plugin, et le sous-titre MCP Server est masqué à l'arrêt
* `Correction` Fuite de descripteurs de fichiers lors du transfert des espaces de travail et du chargement des modules Node.js, ralentissant les scripts suivants et le redémarrage après des projets contenant de nombreux fichiers
* `Correction` La synchronisation des projets Node.js ne remplace plus les fichiers inchangés, évitant les lenteurs de fin de script et de réponse de l'application avec de nombreuses dépendances
* `Correction` L'exemple Flow de réservation retourne false au lieu de null si une ligne de médecin ne peut pas être analysée, évitant les erreurs de conversion booléenne
* `Correction` Les résultats des autorisations du plugin sont correctement transmis depuis l'écran principal, ce qui permet de terminer l'activation en attente après accord
* `Correction` L'option HTTP isInsecure / insecure pouvait laisser la vérification des certificats et des noms d'hôte désactivée pour les requêtes suivantes; elle ne s'applique désormais qu'à la requête en cours
* `Correction` La configuration de sécurité réseau pouvait bloquer les requêtes HTTP dans les versions de distribution de l'hôte et des applications empaquetées; la politique du trafic en clair correspond désormais aux versions de débogage tout en limitant les domaines de ressources GitHub à HTTPS
* `Correction` Les installations APK en arrière-plan ne pouvaient pas continuer lorsque le système bloquait la page de confirmation; l'état est désormais enregistré par session, avec reprise depuis une notification ou au retour dans l'application, même après un refus des notifications ou une recréation du processus
* `Correction` Les widgets de scripts pouvaient perdre leurs chemins de scripts et leurs associations de clic lors des mises à jour par lots si leurs identifiants comportaient plusieurs chiffres
* `Correction` La lecture binaire des exemples intégrés par le bridge utilise l'encodeur base64 Okio existant sous Android 7.0 / 7.1, sans appeler une méthode Java réservée à l'API 26, pour les ressources MCP des exemples
* `Correction` Les fichiers MCP et Shell fonctionnent sur Android 24 / 25 sans méthodes Java récentes; la suppression récursive retire les liens symboliques en préservant leurs cibles
* `Correction` Le contrôle CI du plugin éditeur exigeait la version 1 du contrat et rejetait les hôtes prenant en charge des versions compatibles plus récentes
* `Correction` Un plantage lors de l'utilisation successive de Paddle OCR et Rapid OCR dans la même application empaquetée
* `Correction` Les chemins MediaInfo et image acceptent les chemins absolus, les dossiers parents et les noms valides; les enregistrements suivent les mêmes droits Android avec un hôte mis à jour
* `Correction` L'exécution directe depuis le gestionnaire de fichiers ne respectait pas l'encodage UTF-8, UTF-16 ou UTF-32 indiqué par le BOM Unicode, si bien que des scripts exécutés normalement dans l'éditeur échouaient avec `Caractère non valide`
* `Correction` Les informations de téléchargement d'Android Studio n'étaient plus écrites automatiquement dans les données README communes après la migration des scrapers
* `Correction` Alignement de la numérotation des constructions distantes expérimentales à fichier unique sur le constructeur historique, et ajout d’un contrôle préalable de l’espace de travail à échec fermé fondé sur les tailles décompressées recoupées, une limite d’expansion du modèle vérifiée à la compilation et une réserve de 256 Mio
* `Correction` La WebView Ace pouvait afficher brièvement une page entièrement blanche avant l'application du thème hôte et du document initial, provoquant un flash marqué avec les thèmes sombres; la première présentation attend désormais des signaux explicites de thème appliqué, document soumis et rendu stable au lieu d'un délai fixe
* `Correction` Le sélecteur de fichiers WebView des scripts UI pouvait ne pas s'ouvrir lorsque le context de la racine de la mise en page perdait son association avec l'Activity
* `Correction` Les scripts d'interface utilisateur pouvaient se fermer de manière inattendue lors du passage en écran partagé ou du redimensionnement d'une fenêtre, car la recréation de l'Activity supprimait le script en cours
* `Correction` Les éditeurs natif et Ace pouvaient perdre la position du curseur et la sélection après la recréation de l'Activity, et les grands documents ouverts sans chemin de fichier pouvaient perdre leur contenu non enregistré
* `Correction` La zone de texte de l'éditeur pouvait se réduire à une hauteur nulle en mode paysage avec le clavier ouvert, et le champ de saisie du journal pouvait être masqué par le clavier
* `Correction` La recherche et l'état déplié du champ de recherche de la page principale pouvaient être perdus lorsque le passage en écran partagé recréait l'Activity
* `Correction` Ace pouvait revenir à l'ancien moteur lors de la première ouverture d'un fichier Lua lorsque la détection synchrone du runtime natif LuaLS retardait le premier affichage
* `Correction` L'accès simultané au stockage de polices partagé par l'hôte AutoJs6 et le plugin Ace pouvait déclencher une OverlappingFileLockException JVM et faire planter l'application pendant l'aperçu des polices
* `Correction` APK Builder préserve désormais la directive de mode d'exécution Rhino (dont `ui`) de l'entrée TypeScript/TSX dans le lanceur généré, afin que les projets UI compilés démarrent dans le bon mode Activity
* `Correction` Les demandes d'empaquetage APK avec Node.js intégré échouent désormais avant la préparation ou l'ouverture d'une session distante, avec des indications pour migrer vers le plugin Runtime externe ; l'analyse du mode d'exécution ne journalise plus les jetons source ni les détails d'exception
* `Correction` L’annulation d’une compilation APK distante avant ou pendant l’ouverture de la session pouvait être signalée comme un échec ; elle converge désormais vers un unique résultat terminal `STATUS_CANCELLED`, tout en préservant l’ancien artefact et en terminant le nettoyage
* `Correction` Sous Android 9 à 11, l’API moderne d’archive pouvait ne renvoyer aucun signataire pour un APK distant V2/V3 valide et le rejeter avant publication ; l’hôte n’utilise désormais la vue héritée des signataires validés qu’en repli de plateforme, tout en conservant la vérification cryptographique `apksig` obligatoire
* `Correction` Un APK distant cryptographiquement valide avec le paquet et la version demandés, mais un certificat de signature différent, pouvait réussir la validation de sortie de l’hôte ; l’hôte lie désormais l’unique signataire vérifié à l’identité de signature par défaut ou personnalisée choisie avant l’ouverture de la session Binder et rejette toute divergence avant de remplacer l’ancien artefact
* `Correction` Après la mort d'un processus de plug-in, plusieurs callbacks terminaux Binder pouvaient laisser une déliaison tardive annuler la liaison de service immédiatement recréée, bloquant la redécouverte du plug-in jusqu'au délai d'expiration de liaison de 60 secondes
* `Correction` Lorsque le commutateur développeur R1 était activé mais que le plug-in APK Builder installé déclarait explicitement `supportsRemoteBuild=false`, la découverte du fournisseur pouvait s'arrêter sans indication exploitable ; l'hôte le rejette désormais avant la préparation et explique comment installer une version compatible ou désactiver le commutateur
* `Correction` L'activation de la ligne de commande TypeScript dans la page Journal complète et le panneau Journal inférieur de l'éditeur permettait à un espaceur de barre de navigation en wrap-content d'occuper toute la hauteur restante et de réduire la liste des journaux à zéro ; la ligne reste désormais au-dessus d'une zone de journal visible et le FAB de la page Journal retrouve son décalage inférieur d'origine
* `Correction` Les actions Renommer et Supprimer des entrées d'archive ne respectaient pas les capacités de modification déclarées par la session active, ce qui pouvait exposer dans une session en lecture seule ou non compatible des opérations que le fournisseur ne pouvait pas terminer
* `Correction` Les actions Explorer du gestionnaire de fichiers pouvaient conserver un cache ou des références de ligne obsolètes après l'activation, la réautorisation ou la mise à jour d'un plugin, obligeant les fichiers reconnus à revenir au sélecteur d'applications système jusqu'au redémarrage d'AutoJs6
* `Correction` Les boutons vidéo du gestionnaire de fichiers pouvaient ouvrir le sélecteur d'applications système pour des formats déjà classés comme vidéo ; tous les types vidéo reconnus utilisent désormais d'abord le plugin officiel Video Player et, s'il est absent, désactivé, non autorisé, incompatible, indisponible ou impossible à lancer, l'hôte affiche des instructions de récupération avec l'option explicite "Ouvrir avec d'autres applications"
* `Correction` Les lignes M3U pouvaient afficher simultanément les actions plugin, lecture et édition; la lecture privilégie désormais 3-Amber, puis 3-Terra, avec des liens vers le Centre de plugins pour installer, activer ou autoriser un lecteur indisponible
* `Correction` Icônes de plugin redondantes pour les listes .m3u8/.pls/.wax/.wvx/.asx dans le gestionnaire de fichiers; conserver Lecture et Modifier ainsi que le lecteur associé
* `Correction` La consultation des informations APK pouvait échouer pour les APK ordinaires contenant de nombreuses ressources en raison de la limite du nombre d'entrées; leur manifeste est désormais lu directement avec une limite de taille, tandis que les conteneurs multi-APK conservent la validation du parcours
* `Correction` Le require Rhino CommonJS des applications empaquetées ne pouvait pas déchiffrer les modules JavaScript dépendants, ce qui faisait échouer avec "Cannot read file" les sorties TypeScript multifichiers compilées lors de l'empaquetage
* `Correction` Les handles d'interface Node.js pouvaient rester actifs après la destruction de leur Activity Android, autorisant des mises à jour tardives et conservant les ressources du fournisseur jusqu'à la fin de l'exécution
* `Correction` L'exécution via le plugin Node.js pouvait refuser de grands graphes de dépendances en clair et des espaces node_modules réels, car les budgets de sondage des modules et d'archivage de l'espace de travail étaient trop faibles
* `Correction` Les scripts Node.js vides ou simples démarraient lentement dans les répertoires de travail contenant de nombreux fichiers, car l'analyse inutile des modules lisait intégralement les fichiers candidats
* `Correction` Le processus Node.js persistant était arrêté à tort lorsque l'interface éphémère de lancement de script devenait masquée
* `Correction` La notification attendue de déconnexion Binder du processus Node.js persistant après l'arrêt propre d'une tâche interactive de longue durée dans une application empaquetée pouvait remplacer l'état stopped par failed
* `Correction` Node.js console.assert lançait à tort une exception AssertionError lors de l'échec d'une assertion au lieu d'écrire uniquement le message d'échec sur stderr
* `Correction` Le retour des résultats des scripts Node.js était retardé par l'attente de stabilisation du processus d'exécution
* `Correction` L'annulation d'une exécution Node.js active pouvait supprimer l'enregistrement de l'exécution avant la mise en quarantaine du processus défaillant, bloquant les attentes de fin et retardant la récupération
* `Correction` Le repli automatique après la perte d'une réponse Binder de l'environnement d'exécution Node.js externe pouvait exécuter deux fois le même script
* `Correction` Les exécutions de projets TypeScript Node.js via l'archive d'espace de travail pouvaient resynchroniser les sorties du compilateur réservées au runtime à côté des fichiers source, provoquant des conflits lors des exécutions suivantes
* `Correction` Les alias /data/user/0 et /data/data des répertoires privés Android n'étaient pas normalisés puis réécrits dans les requêtes du plugin, ce qui faisait considérer à tort un workingDirectory Node.js valide comme sortant de sandboxRoot
* `Correction` Les builds nodePlugin sans environnement d'exécution Node côté hôte excluaient à tort les bibliothèques JNI/AAR de toutes les ABI, entraînant un plantage lors de l'initialisation de Rhino Shell en raison de l'absence de libjackpal-termexec2.so
* `Correction` La collecte et la vérification des preuves Node.js R6 rejetaient les identités de signature APK signalées par Android Build Tools 37 car apksigner avait modifié le format de sortie de l'empreinte du certificat
* `Correction` L'exécution directe du projet depuis IntelliJ IDEA échouait lors de la vérification de l'alignement des pages des bibliothèques natives car la recherche des APK n'utilisait pas le répertoire des artefacts de la compilation en cours
* `Correction` Les builds de modules Android dans IntelliJ IDEA échouaient en raison d'une correspondance ambiguë de la tâche compileJava
* `Correction` Épuisement de la mémoire pendant la compilation Kotlin de plusieurs variantes
* `Correction` Avertissements de lecture SDK XML v4 avec AGP 9.1 et contrôles d'alignement natif des APK déclenchés par erreur lors de l'assemblage des tests unitaires JVM, avec les plugins de compilation partagés 1.8.3
* `Correction` Les builds de l'ensemble du projet dans IntelliJ IDEA échouaient après Clean car les AAR distribués de l'API Python Runtime étaient résolus avant leur génération et les fixtures de conformité n'implémentaient pas les méthodes AIDL ajoutées
* `Correction` Erreur Lint RemoveWorkManagerInitializer lors de la compilation due à un conflit entre l'initialiseur par défaut de WorkManager et l'initialisation à la demande
* `Correction` La compilation d'APK Builder Template échouait après `clean` car le répertoire Runtime Kit par défaut omettait le suffixe de variante `universal`
* `Correction` Les preuves soak Node.js R6 pouvaient rejeter un environnement d'exécution persistant sain lorsqu'Android signalait zéro octet actuellement alloué au tas natif
* `Correction` Les preuves d'APK empaqueté Node.js R6 étaient ignorées ou liées à arm64 car le collecteur ne préparait pas de modèle Inrt récent et ne transmettait pas l'ABI cible
* `Correction` Le lancement des projets Node.js ignorait node.timeoutMs dans project.json, interrompant au bout de 5 secondes environ les scripts configurés pour une attente plus longue
* `Correction` L'empaquetage d'applications entre ABI pouvait copier les bibliothèques natives du processus hôte installé dans le répertoire d'une autre ABI, amenant les applications empaquetées 32 bits à charger des bibliothèques natives shell 64 bits puis à planter
* `Correction` Lorsqu'un script TypeScript UI échouait à la compilation avant la création du moteur, l'écouteur d'exécution global pouvait déréférencer un moteur absent et faire planter l'hôte pendant le signalement des diagnostics
* `Correction` Les liens de cadres de pile et de diagnostics du compilateur traitaient les colonnes source à base 1 comme des décalages d'éditeur à base 0, déplaçant le curseur d'un caractère de trop
* `Correction` Le chemin hôte expérimental du compilateur DEX pouvait accepter des versions DEX incompatibles avec le minApi demandé, rejeter à tort la limite valide de 65 536 identifiants field/method et recréer un artefact partiel annulé lorsqu'un drain de pipe en attente démarrait tardivement
* `Correction` runtime.loadJar ne pouvait pas charger dynamiquement des fichiers de classe Java compilés pour une version plus récente
* `Correction` app.isInstalled/kill et les méthodes associées ne pouvaient pas utiliser correctement les arguments de nom de paquet _[`issue #495`](http://issues.autojs6.com/495)_
* `Correction` Les méthodes liées au shell pouvaient ne plus répondre sur les appareils sans accès Root
* `Correction` Les résultats de sélection multiple renvoyés par dialogs.multiChoice étaient convertis à tort en chaînes _[`issue #518`](http://issues.autojs6.com/518)_
* `Correction` L'application pouvait planter lorsqu'une boîte de dialogue de script était fermée pendant l'arrêt du Looper de son thread, car un callback de timer ne pouvait plus être planifié
* `Correction` Les scripts pouvaient rester actifs après l'annulation de minuteurs ou l'arrêt de threads de travail car des échéances de rappels obsolètes empêchaient Looper de se terminer
* `Correction` Le module selector pouvait choisir une mauvaise signature lors de l'invocation de méthodes surchargées par réflexion
* `Correction` La recherche de contrôles avec l'algorithme BFS renvoyait un résultat de plus que le maximum demandé, `find(0)` renvoyait un résultat au lieu d'une collection vide, et les nœuds déjà récupérés lorsque le maximum était atteint n'étaient pas recyclés
* `Correction` Les segments de boussole parent ou cliquable à plusieurs tronçons (comme `p4ppp12p` ou `k2k3`) passés à `UiObject.compass` ne comptaient que le premier tronçon
* `Correction` Le segment `k` de `UiObject.compass` renvoyait le nœud lui-même au lieu de `null` lorsqu'aucun nœud cliquable n'existait dans la plage
* `Correction` Le calcul de l'index parent du nœud `source` d'un rappel `auto.registerEvent` pouvait boucler indéfiniment ou lever une exception hors limites et faire planter le service d'accessibilité lorsque l'arbre des nœuds avait changé
* `Correction` Après le redémarrage du service d'accessibilité, `pickup` sans nœud racine et les sélecteurs créés avec `new UiSelector()` pouvaient continuer à renvoyer `null` ou une collection vide jusqu'à la création d'un nouveau runtime de script
* `Correction` `auto.stateListener` était un emplacement global unique, si bien que les écouteurs définis par plusieurs scripts s'écrasaient mutuellement et n'étaient pas supprimés à la fin du script
* `Correction` Les méthodes d'attente telles que `auto.waitFor` ne pouvaient pas être réveillées et pouvaient attendre jusqu'à l'expiration du délai lorsque le service d'accessibilité avait commencé à livrer des événements sans que le système n'appelle jamais `onServiceConnected`
* `Correction` L'arrêt du service d'accessibilité avec root ne faisait rien en silence tout en signalant un succès lorsque AutoJs6 était le seul service d'accessibilité activé, si bien que le service ne pouvait pas être arrêté ou semblait "redémarrer automatiquement" aussitôt
* `Correction` La création d'un écran de l'application réactivait automatiquement (via root ou les paramètres sécurisés) un service d'accessibilité que l'utilisateur avait explicitement arrêté ; désormais seul un service activé dans les paramètres système mais non en cours d'exécution est restauré, le reste étant démarré à la demande par les scripts, l'inspecteur de mise en page et les autres points d'entrée
* `Correction` `auto.start` et `auto.enable` réactivaient le service d'accessibilité via root ou les paramètres sécurisés même lorsqu'il était déjà en cours d'exécution, le redémarrant inutilement
* `Correction` Le redémarrage du service d'accessibilité le réactivait avant que le système n'ait détaché l'instance précédente, ce que certains systèmes (par ex. Android 8.0) fusionnaient en "toujours activé", de sorte qu'aucun redémarrage n'avait lieu
* `Correction` `auto.windowRoots` renvoyait un tableau contenant un nœud nul au lieu d'un tableau vide tant que le système n'avait pas encore déterminé la fenêtre active (par ex. juste après le rattachement du service d'accessibilité), si bien que l'appel de méthodes sur son élément levait une exception
* `Correction` `automator.isServiceRunning()` vérifiait seulement l'existence d'une instance du service d'accessibilité dans le processus, ce qui ne correspondait pas à la notion d'"en cours d'exécution" de `auto.isRunning()` et de l'interrupteur du tiroir latéral (activé dans les paramètres système et lié dans le processus)
* `Correction` Des points d'entrée tels que `auto()` levaient "activé mais non en cours d'exécution" lorsque le service d'accessibilité n'était pas activé, et "le service est désactivé" lorsqu'il était activé mais non en cours d'exécution, autrement dit les deux messages étaient inversés (la raison est désormais l'une de non activé / activé mais non lié / lié mais pas encore opérationnel)
* `Correction` `auto.services` renvoyait toujours un tableau vide sauf si l'activation automatique via root ou les paramètres sécurisés était configurée (la lecture de la liste des services activés n'a besoin ni de l'un ni de l'autre)
* `Correction` L'interrupteur du service d'accessibilité du tiroir latéral affichait comme activé un service "activé mais non en cours d'exécution" (il apparaît désormais désactivé avec le sous-titre "défaillant", et l'activer réactive le service)
* `Correction` "Inspecter les limites de la mise en page" / "Inspecter la hiérarchie des dispositions" du bouton flottant devaient être déclenchés deux fois lorsque le service d'accessibilité n'était pas activé mais pouvait être démarré automatiquement (root ou paramètres sécurisés) : le premier appui ne faisait que démarrer le service et le second inspectait (l'inspection se poursuit désormais juste après le démarrage du service, de même pour les tuiles des réglages rapides et les diffusions d'inspection de mise en page, et le démarrage ne bloque plus le thread principal)
* `Correction` Les rappels enregistrés avec `auto.registerEvent` et les écouteurs de touches de `events.observeKey()` étaient tous perdus dès que le service d'accessibilité redémarrait (y compris une nouvelle liaison après que le système l'ait délié) ; les abonnements vivent désormais sur un bus d'événements à l'échelle du processus et survivent à l'instance du service
* `Correction` Une régression de compatibilité de l'API de portée après la mise à jour du moteur Rhino provoquait le dysfonctionnement de certaines fonctions auxiliaires d'exécution
* `Correction` Le pont des modules Rhino traitait incorrectement null/undefined, Callable, les constructeurs, les prototypes, les realms et les valeurs de retour Kotlin, ce qui pouvait produire des objets inter-realm, perdre des callbacks ou provoquer des exceptions d'exécution
* `Correction` Les fonctions encapsulées dans un Proxy ne pouvaient pas être utilisées comme callbacks par les modules historiques tels que dialogs, events, timers, UI, selector et automator, et les arguments de callback de TimerThread pouvaient être transmis sous forme de tableau imbriqué
* `Correction` Les surcharges de compatibilité Callable et BaseFunction rendaient les types de paramètres ambigus lorsque des fonctions JavaScript ordinaires appelaient des méthodes Java telles que EventEmitter.on
* `Correction` Les callbacks EventEmitter.once pouvaient être invoqués plusieurs fois lors d'une émission récursive d'événement ou lorsqu'un callback levait une exception
* `Correction` Les actions globales telles que automator.home pouvaient lever une exception de pointeur nul lorsque l'initialisation du Looper auxiliaire était interrompue lors de la première utilisation, et les coordonnées Y des gestes pouvaient utiliser à tort le rapport de mise à l'échelle horizontal
* `Correction` La distribution des arguments, les valeurs de retour, les limites numériques, les coordonnées d'image et le cycle de vie des ressources temporaires de la migration initiale des modules JavaScript ne correspondaient pas à l'implémentation d'origine
* `Correction` L'application pouvait planter lors de la demande d'autorisation de capture d'écran sur Android 14+ en raison d'un démarrage anormal du service de premier plan de projection multimédia _[`issue #516`](http://issues.autojs6.com/516)_
* `Correction` Les appels OCR répétés depuis plusieurs threads ou fichiers pouvaient demander plusieurs fois l'autorisation de capture d'écran _[`issue #551`](http://issues.autojs6.com/551)_
* `Correction` Les erreurs de sélection de plugins OCR pouvaient nommer le mauvais moteur, omettre les ID de moteur/variantes requis et les plugins détectés, ou signaler à tort une installation manquante après un échec de détection
* `Correction` Le service du plugin Paddle OCR pouvait se déconnecter après une longue exécution sans pouvoir se rétablir automatiquement _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_
* `Correction` Le plugin Paddle OCR ne pouvait pas être réveillé automatiquement lorsqu'il n'était pas en cours d'exécution, entraînant l'échec des appels de fonctions ou de l'empaquetage de projets _[`issue #517`](http://issues.autojs6.com/517)_
* `Correction` Les appels incorrects à `images.resize(ImageWrapper, width, height)` pouvaient transmettre la hauteur à OpenCV comme valeur d'interpolation, tandis qu'un déroulement incompatible des exceptions natives pouvait faire planter l'application au lieu de signaler une exception de script
* `Correction` Les détails multimédias du gestionnaire de fichiers pouvaient rester sur "..." ou faire planter l'application lorsque la bibliothèque MediaInfo du plugin était indisponible ou qu'un appel Binder échouait, sans repli sur les métadonnées Android
* `Correction` Les boîtes de dialogue des détails vidéo du gestionnaire de fichiers affichaient à tort les champs de format et de métadonnées de l'audio intégré
* `Correction` Le document principal des aperçus Markdown et HTML était rejeté à tort par l'intercepteur de requêtes sur certaines versions de WebView et certains appareils physiques
* `Correction` Le zoom par pincement de la visionneuse d'images ne suivait pas fidèlement le mouvement des doigts, rendait difficile le retour à la taille d'origine lors de la réduction et pouvait conserver brièvement ou continuellement la couleur du thème dans l'ActionBar à la première ouverture
* `Correction` ImageViewer pouvait planter lors de sa destruction, car Glide tentait de démarrer le chargement d'une image pour une Activity déjà détruite
* `Correction` Le service d'accessibilité pouvait s'interrompre de manière inattendue lors d'opérations consécutives telles que back _[`issue #527`](http://issues.autojs6.com/527)_
* `Correction` L'utilisation de la mémoire pouvait continuer à augmenter après l'exécution répétée de scripts (tentative de correction) _[`issue #548`](http://issues.autojs6.com/548)_
* `Correction` Le chemin d'origine pouvait ne pas être conservé lors de l'exécution de scripts via des URI externes ou en arrière-plan, entraînant une résolution incorrecte des chemins relatifs _[`issue #545`](http://issues.autojs6.com/545)_
* `Correction` Tasker ne pouvait pas invoquer et exécuter correctement les scripts _[`issue #501`](http://issues.autojs6.com/501)_
* `Correction` L'onglet Docs de la page d'accueil pouvait faire planter l'application lorsque ViewPager recevait une séquence multitactile anormale avec un index de pointeur invalide
* `Correction` Plusieurs problèmes d'interaction de l'éditeur Ace concernant l'IME/la sélection de texte/le pliage du code/le zoom par pincement/la navigation dans la pile et d'autres scénarios
* `Correction` Les titres principaux des paramètres de l'éditeur de code, le panneau de fonctions fx, le tiroir de documentation épinglé à gauche et la barre d'outils de sélection de texte Ace pouvaient encore utiliser les couleurs de l'application AutoJs6 au lieu du thème actuel de l'éditeur, et les actions de ligne Ace pouvaient apparaître en double
* `Correction` En mode d'édition plein écran, le clavier virtuel pouvait recouvrir la barre de touches multifonctions et la zone de la barre de navigation sous le Snackbar d'indication du plein écran pouvait rester transparente
* `Correction` La barre latérale de documentation de l'éditeur de code pouvait n'afficher que l'arrière-plan du thème sans aucun contenu, tandis que les paramètres de l'éditeur ne disposaient pas de couleurs de rôles hiérarchiques stables dérivées de l'arrière-plan de l'éditeur, ce qui pouvait faire disparaître les couleurs des titres, sous-titres, catégories et contrôles après le défilement et le recyclage de la liste
* `Correction` Le clavier virtuel pouvait recouvrir la barre de touches multifonctions hors du mode plein écran
* `Correction` La barre d'informations de l'éditeur de code pouvait omettre les extensions de fichier ou afficher brièvement une position temporaire du curseur en fin de fichier à l'ouverture d'un document, et un espace clair pouvait apparaître entre la barre de touches multifonction et le clavier logiciel lorsque les thèmes de l'application et de l'éditeur avaient des luminosités différentes
* `Correction` L'éditeur Ace pouvait rester longtemps sans répondre et revenir à l'éditeur natif lorsqu'un fichier volumineux rechargé avec un encodage incompatible produisait une ligne de texte extrêmement longue
* `Correction` La désactivation ou la désinstallation du plugin Ace externe pendant l'utilisation de l'éditeur ou de son interface de paramètres pouvait entraîner la perte de modifications non enregistrées ou laisser l'interface dans un état invalide
* `Correction` Les icônes des boutons du Centre des plugins étaient trop éloignées de leurs libellés après la simplification de la disposition des lignes
* `Correction` Le Centre des plugins conservait les dates de septembre telles que `Sept 13, 2026` au lieu de les normaliser en `yyyy-MM-dd`
* `Correction` Le Centre des plugins pouvait afficher une ancienne icône distante au lieu de l'icône actuellement installée lorsqu'un plugin n'avait pas d'URL d'icône en ligne
* `Correction` Le changement de mode clair ou sombre dans le Centre des plugins pouvait afficher brièvement une icône installée du thème précédent; les deux variantes sont préparées en arrière-plan et le thème actuel est sélectionné dès le premier affichage
* `Correction` Le cache des icônes et l'état d'actualisation par glissement du Centre des plugins pouvaient devenir incohérents dans certains cas
* `Correction` La défaillance du processus d'un seul plugin local pouvait laisser la liste du Centre des plugins entièrement vide
* `Correction` Au retour vers le Centre des plugins depuis un autre écran ou l'arrière-plan, l'actualisation concurrente pouvait supprimer temporairement les plugins SDK hérités installés ou les remplacer par des entrées d'index désactivées, réordonner les lignes, réassocier les commutateurs et décaler la position de défilement
* `Correction` Les icônes des plugins installés dans l'onglet Plugins de la page d'accueil ne changeaient pas dynamiquement lors du basculement du mode nuit
* `Correction` Le Centre des plugins pouvait installer l'APK d'une mauvaise variante lorsqu'une version de plugin en contenait plusieurs
* `Correction` Les plugins APK Builder Template pouvaient apparaître en double ou signaler des mises à jour incorrectement
* `Correction` La vérification de l'intégrité de la documentation de projet hors ligne pouvait réutiliser un contenu obsolète en cache pendant la mise à jour du plugin
* `Correction` Le bouton Manifest et le contenu multiligne complet manquaient dans les détails des APK du gestionnaire de fichiers
* `Correction` Le gestionnaire de fichiers ignorait à tort la casse des noms d'entrées ZIP lors de l'analyse des APK, de sorte que des entrées ne différant que par la casse étaient considérées comme des doublons dans certains paquets valides, empêchant leur consultation ou leur installation
* `Correction` Les importations en cache depuis des URI externes faisaient confiance aux noms de fichiers fournis par le fournisseur de contenu, ce qui pouvait permettre une traversée de chemin ou l'écrasement de fichiers portant le même nom
* `Correction` La position de défilement de la liste du gestionnaire de fichiers était réinitialisée après la suppression de fichiers de la Corbeille
* `Correction` Les champs de recherche pouvaient conserver le curseur bleu par défaut au lieu de la couleur de premier plan adaptée au thème, et leurs indications pouvaient rester dans la langue de repli au lieu de suivre la langue actuelle de l'application
* `Correction` La zone tactile effective des interrupteurs du tiroir d'accueil était trop petite
* `Correction` Les interrupteurs du tiroir d'accueil pouvaient être tronqués et les boutons inférieurs masqués sur les écrans étroits comme 360x640 ou après le redimensionnement de la fenêtre
* `Correction` La fermeture ou la recréation de l'écran d'accueil pouvait faire planter l'application lorsque le nettoyage des écouteurs du tiroir accédait à un contexte libéré
* `Correction` Les noms de paquet et les informations d'Activity des applications récentes affichés dans le menu du bouton flottant étaient incorrects _[`issue #529`](http://issues.autojs6.com/529)_
* `Correction` La boîte de dialogue "Exécuter le script" du bouton flottant pouvait ne pas être restaurée après sa réduction (tentative de correction) _[`issue #500`](http://issues.autojs6.com/500)_
* `Correction` L'arrière-plan de l'écran de démarrage ne s'étendait pas dans la zone de découpe du bord court
* `Correction` Le gestionnaire de fichiers pouvait ne pas actualiser une nouvelle sortie voisine validée par Explorer Action ou afficher un dossier de sortie validé comme un fichier
* `Correction` Quitter une page native d'archive sous Android 7 pouvait faire planter l'application pendant le nettoyage du cache d'aperçu, car le chemin de nettoyage appelait une API File NIO plus récente
* `Correction` Le retour depuis un lecteur Explorer Action pouvait déclencher jusqu'à deux rebinding complets de la liste, provoquant de forts clignotements malgré la conservation du dossier et de la position de défilement
* `Correction` Un clic sur Fichier depuis un autre onglet d'accueil n'affichait pas l'animation de transition de ViewPager
* `Correction` Le retour depuis une page de plugin en paysage comme le lecteur vidéo reconstruisait toute l'interface principale suite au changement de configuration, faisant fortement clignoter le gestionnaire de fichiers, parfois deux fois de suite
* `Correction` Lors de la première ouverture de l'onglet Plugins de l'accueil ou du Centre de plugins autonome, ou du retour vers n'importe quel onglet d'accueil après avoir visité Plugins, les gestes immédiats pouvaient rester bloqués pendant environ une demi-seconde
* `Correction` Les animations de transition vers l'onglet Plugins de l'accueil et de tirage pour actualiser dans le Centre de plugins pouvaient se figer à mi-parcours pendant la découverte des métadonnées de services et la création à froid des lignes denses; le Centre de plugins autonome pouvait aussi restaurer le service d'accessibilité via root/settings pendant la création de l'Activity
* `Correction` L'icône puzzle Explorer Action des fichiers ZIP/JAR et autres fichiers gérés par un plugin remplissait toute la cellule d'action et paraissait surdimensionnée
* `Correction` Le gestionnaire de fichiers ne distinguait pas fiablement les sources et déclarations TypeScript `.ts/.mts` des vidéos MPEG-2 TS portant les mêmes suffixes, et ne traitait pas toujours `.d.ts/.d.mts/.d.cts` comme des déclarations
* `Correction` Les sources `.py/.ts/.java/.kt` prises en charge par des plugins n'affichaient pas systématiquement une icône de source exécutable et l'action Exécuter
* `Correction` Le lancement de fichiers `.ts/.tsx` via Intent et d'autres points d'entrée pouvait afficher une extension `.js` incorrecte dans la console hôte et perdre le chemin du fichier d'origine
* `Correction` Les projets de script minimaux sans métadonnées de packaging APK revenaient à `main.js` au lieu de lancer l'entrée `main` déclarée dans `project.json`
* `Correction` Les entrées d'activation pouvaient être mal résolues pour les variantes avec suffixe d'identifiant d'application, et le réveil pouvait être annoncé comme réussi avant que le service soit disponible
* `Correction` Appeler les accesseurs compat `getChild()` / `getParent()` etc. sur un `UiObject` doté d'un allocateur de nœuds récursait sans fin (l'allocateur rappelait les accesseurs de l'enveloppe elle-même) ; l'allocateur travaille désormais sur le nœud brut et enregistre par identité d'objet et non par égalité de nœuds (deux instances de la même vue ne sont plus libérées comme une seule). Il n'est toujours pas intégré aux runtimes de script : sous Android 13 les nœuds sont mis en pool par processus, une libération groupée à la sortie pourrait donc recycler une instance déjà réutilisée par un autre script
* `Correction` La condition de sélecteur `contextClickable()` apparaissait comme `checkable` dans la forme texte du sélecteur (`selector().toString()`, messages d'erreur)
* `Correction` La forme fonction globale `filter(fn)` échouait avec « Invalid arguments », et une expression régulière dans un tableau de sélecteur map (`pickup({text: [/regex/]})`) était rejetée
* `Correction` Les décimales dans la forme texte des sélecteurs (`selector().toString()`, messages d'erreur) apparaissaient comme `boundsLeft(0,5)` avec les langues d'interface dont le séparateur décimal est la virgule (français, russe, ...) ; le point est désormais utilisé partout
* `Correction` Les exceptions Java levées par la forme fonction globale des méthodes de sélecteur (`algorithm('xyz')`, `textMatch('(')`, ...) ne pouvaient pas être interceptées par le `try / catch` du script et terminaient le script ; elles sont désormais levées comme des erreurs de script interceptables
* `Correction` Avec l'indicateur `findOnUiThread` actif, une recherche envoyée au thread d'interface pouvait rester bloquée indéfiniment si son réveil était perdu (le thread d'interface terminait la recherche avant que le thread du script ne commence à attendre), et `findOne(timeout)` n'expirait jamais tant que le thread d'interface était bloqué ; l'attente est désormais bornée par le délai de la requête et ne peut plus manquer le réveil
* `Correction` `UiObject.clickBounds()` préparait un Looper sur le thread du script et appelait `quit()` une fois le geste terminé, ce qui quittait la boucle d'événements du thread du script lui-même : `setTimeout` et les autres rappels ne s'exécutaient plus ensuite et le script se terminait prématurément ; les nœuds issus d'une requête tapent désormais via le répartiteur de gestes du script (le Looper servant), les nœuds d'autres origines conservent l'ancien chemin
* `Correction` Le type de résultat `['compass', 'p']` de `pickup` / `detect` échouait toujours avec "type de résultat inconnu" parce que `compass` figurait avec un paramètre `String` dans la table de l'intercepteur alors que sa signature prend un `CharSequence` ; il fonctionne désormais
* `Correction` Lorsque l'inspecteur de mise en page générait du code pour un enfant d'un contrôle de collection sans action sélectionnée, le bloc `forEach` généré contenait l'instruction invalide `target.;` ; la ligne est désormais omise
* `Correction` Contraste insuffisant du texte et des commandes des fenêtres flottantes et des dialogues de l'analyse de mise en page avec certaines couleurs de thème ou en mode nuit
* `Correction` Parmi plusieurs surcharges de même nom sur un tableau de collection de contrôles, seule la dernière survivait (par exemple `performAction(action)` à côté de `performAction(action, ...args)`), si bien que l'appeler avec l'autre nombre d'arguments échouait pour paramètres non concordants ; toutes les surcharges sont désormais liées par nom
* `Correction` Depuis 6.7.0 Alpha13, une chaîne ordinaire passée à la famille de sélecteurs `contains` (`textContains` / `descContains` / `contentContains` / `idContains` / `classNameContains` / `packageNameContains`, etc.) était compilée comme motif d'expression régulière (`textContains(".")` correspondait à tout texte non vide, `textContains("1+1")` correspondait à "11", `textContains("(")` levait une erreur) ; elle redevient une sous-chaîne littérale, en cohérence avec `text(s)` / `textStartsWith` / `textEndsWith` ; pour un motif, passez une expression régulière (`textContains(/re/i)`) ou utilisez `textMatch` / `textMatches`, et une chaîne de la forme "/re/flags" n'est plus traitée comme expression régulière par `contains`
* `Correction` `requestFocus()` / `disableFocus()` des fenêtres flottantes (`floaty.rawWindow` / `floaty.window`) mettaient à jour la disposition de la fenêtre attachée directement sur le thread du script et levaient parfois `CalledFromWrongThreadException` ; ils s'exécutent désormais sur le thread UI (comme `setSize` / `setPosition`)
* `Correction` L'observateur de notifications d'accessibilité (derrière `events.observeToast()` et consorts) traitait chaque notification système comme un toast d'une autre application (sa vérification nommait la sous-classe propre à cette application, qui ne correspond jamais) : les notifications n'atteignaient jamais ses écouteurs et celles des autres applications étaient signalées comme des toasts ; il vérifie désormais `android.app.Notification` du framework
* `Correction` L'ordre d'appel de `console.setPosition` / `setSize` / `setGravity` et de `console.show()` influençait le résultat (par exemple `console.show(); console.setPosition(500, 400)` nécessitait un `sleep` entre les deux, et l'ordre inverse pouvait perdre le réglage) ; les réglages sont désormais enregistrés puis appliqués une fois la fenêtre attachée, l'ordre et le moment n'ont plus d'importance
* `Correction` Le déplacement de la console flottante par sa barre de titre échouait parfois (le geste de glissement était lié avant l'attachement de la fenêtre et entrait en concurrence avec les clics de boutons et la fermeture de la fenêtre) ; le glissement est désormais configuré après l'attachement, le déplacement est limité à l'écran et la position est enregistrée à la fin du glissement
* `Correction` La console flottante avait une marge de 12dp de chaque côté, si bien que `console.setPosition(0, 0)` n'atteignait pas le bord de l'écran ; la marge est supprimée, la coordonnée Y part par défaut du bas de la barre d'état (désactivable avec `setAvoidStatusBar(false)`), et la position comme la taille sont limitées à l'écran
* `Correction` Le controleur de session MCP Server pouvait publier l'instantane `starting` obtenu juste apres `openServer` par-dessus un etat `running` deja livre par le rappel du plugin, si bien que `status` ne signalait jamais l'ecouteur comme actif ; l'instantane obtenu n'est desormais publie que si aucun etat n'est arrive par le rappel pendant la requete
* `Correction` Complete name affiche le chemin du fichier source dans les détails et instantanés MediaInfo au lieu du cache privé ou du descripteur, sans modifier le nom de fichier de l'instantané
* `Correction` `accessibility.swipe` / `accessibility.gesture` du Node bridge (les gestes par coordonnees des plugins MCP Server et Node) repondaient `false` (annule) a chaque geste apres le premier sur un thread de dispatch du broker reutilise : l'automate de gestes sans handler preparait un Looper sur le thread appelant et le quittait une fois le premier geste termine, si bien que l'appel suivant sur ce thread revenait aussitot et son vrai resultat partait vers un handler mort ; le bridge remet desormais a l'automate le handler de la boucle principale, comme le broker Python de l'hote
* `Correction` Les politiques des plugins OpenCV et ImageQuant rejetaient les bibliothèques natives compilées avec le NDK r28c (28.2.13676358) pour incompatibilité de version du NDK, ce qui bloquait le plugin OpenCV v1.1.2 recompilé ; les compilations r26b et r28c sont désormais acceptées
* `Correction` Les chargeurs des plugins OpenCV et Image Quantization continuaient après un échec de protection du cache en lecture seule; les fichiers natifs sont désormais vérifiés avant le chargement pour assurer la compatibilité avec Android 17
* `Correction` runtime.loadDex sous Android 7.0 / 7.1 pouvait écraser le DEX en cache avec la sortie optimisée d'ART, provoquant un échec du chargement des classes
* `Correction` Échec des compilateurs Kotlin chargés dynamiquement en raison de restrictions sur les opérations atomiques Unsafe ou de l'absence du runtime de réflexion
* `Correction` Incompatibilité des anciennes API de réflexion des portées Rhino empêchant les bibliothèques externes d'enregistrer automatiquement les classes Java/Kotlin
* `Correction` Les captures du pont MediaProjection réessaient jusqu'à six fois si une image manque temporairement, pour permettre les captures consécutives après le renouvellement du flux sans demander à nouveau le consentement
* `Correction` Les matrices OpenCV derrière `images.imread`, `images.matToImage` et les images basées sur Mat n'étaient jamais libérées ou partageaient leur en-tête natif avec une source pouvant être récupérée par le ramasse-miettes, provoquant des fuites de mémoire native et des pointeurs pendants
* `Correction` Un script qui se terminait recyclait les images de tous les autres scripts en cours; les images sont désormais enregistrées et recyclées par environnement d'exécution de script
* `Correction` `colors.isSimilar` et `images.detectColor` avec l'algorithme `hs` correspondaient à n'importe quelle couleur, et `colors.rgb` / `colors.rgba` traitaient les composantes fractionnaires ou en pourcentage comme des entiers
* `Correction` `images.isEqual` renvoyait true pour des images différant sur un seul canal, `images.mse` saturait à `255`, et `images.ncc` / `images.isGrayscale` géraient mal les entrées en niveaux de gris ou RGBA
* `Correction` `images.invert` perdait le canal alpha, `ImageWrapper.saveTo` enregistrait les images basées sur Mat avec les canaux rouge et bleu inversés, et `images.clip` renvoyait silencieusement des résultats erronés pour des régions hors de l'image
* `Correction` Les images transmises par chemin à `images.clip`, `images.findCircles` et `images.readPixels` pouvaient être recyclées avant utilisation, et `images.findImage` / `images.matchTemplate` pouvaient lever une exception ou renvoyer de mauvaises correspondances pour des modèles proches du bord de l'image ou plus grands que la région de recherche
* `Correction` `images.load` applique un délai d'attente de 30 secondes et ferme sa connexion, et `images.saveBitmap`, la mise en correspondance de caractéristiques et la comparaison d'histogrammes libèrent leurs flux et matrices temporaires
* `Correction` La recherche de modèles renvoyait des coordonnées arrondies à un niveau grossier de la pyramide (jusqu'à plusieurs pixels d'écart) dès qu'une correspondance atteignait le seuil tôt, pouvait renvoyer deux fois la même position pour des candidats voisins, et les résultats n'étaient ni triés par similarité ni limités à `max`
* `Correction` `images.matchTemplate` levait une exception pour une image et un modèle au nombre de canaux différent, ne trouvait jamais un modèle de couleur unie, et `useTransparentMask` masquait seulement le canal alpha au lieu des pixels transparents; `MatchingResult.sortBy(compareFn)` échouait lorsque le comparateur renvoyait un nombre non entier
* `Correction` `images.matchFeatures` renvoyait un cadre de taille erronée lorsque les caractéristiques de l'objet étaient calculées avec un `scale` différent de 1, retriait les coins de sorte qu'un objet pivoté perdait la correspondance avec ses propres coins, pouvait renvoyer un cadre déformé issu d'une homographie peu fiable, dessinait l'image `drawMatches` à partir des matrices de descripteurs au lieu des images, et levait une erreur OpenCV pour des images sans points clés
* `Correction` `images.detectAndComputeFeatures` ne libérait pas ses matrices intermédiaires, et ORB ne trouvait aucun point clé dans les images de moins de 63 px et seulement quelques-uns dans les petits objets à cause de la bordure par défaut de 31 px
* `Correction` Le contrôleur de session MCP Server reliait le plugin environ une seconde après que l'utilisateur l'ait arrêté de force dans les paramètres système, ce qui le redémarrait contre cette décision; un plugin qui n'est plus disponible après la mort de son processus (arrêté, désactivé ou désinstallé) n'est plus relié, l'interrupteur du tiroir se ferme et son sous-titre affiche le guide d'activation comme pour une nouvelle installation
* `Correction` L'historique des versions enregistre désormais l'empreinte du contenu après l'enregistrement, de sorte que la prochaine sauvegarde d'un fichier renommé ou déplacé se rattache à son historique existant; le rattachement par empreinte n'a lieu que lorsque l'ancien chemin a disparu, ainsi une copie identique ne s'approprie plus l'historique du fichier d'origine
* `Correction` La corbeille et l'historique des versions résolvent la racine du stockage interne à partir du chemin réel de l'utilisateur courant au lieu d'un /storage/emulated/0 fixe, et fonctionnent donc dans les environnements multi-utilisateurs et les profils professionnels
* `Correction` Avant de restaurer une version sur le disque ou de restaurer un fichier depuis la corbeille avec écrasement, le contenu actuel de la cible est d'abord enregistré comme révision RESTORE_PRE; la restauration avec écrasement depuis la corbeille écrit dans un fichier temporaire puis remplace la cible, si bien qu'une interruption ou un plantage ne laisse plus de fichier à moitié écrit
* `Correction` Le renommage automatique lors d'un conflit de restauration depuis la corbeille détermine les dossiers d'après le type de l'élément, ainsi un dossier dont le nom contient un point (comme my.project) ne se voit plus retirer une extension
* `Correction` La boîte de dialogue de l'historique des versions liste toutes les révisions conservées (auparavant 20 au maximum), propose l'aperçu également depuis l'éditeur et conserve son mode après la suppression d'une révision et le rechargement de la liste
* `Correction` Le nettoyage des blobs orphelins de l'historique des versions protège les blobs de moins de 30 minutes, de sorte qu'un instantané récent dont la ligne en base est encore en attente n'est plus supprimé
* `Correction` Les fichiers supprimés sur le PC restaient sur l'appareil après l'enregistrement d'un projet depuis l'extension VSCode, et les fichiers obsolètes du répertoire de cache réapparaissaient _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `Correction` La réception d'une archive de projet depuis l'extension VSCode convertissait tout le tableau d'octets en chaîne pour le journal, ce qui pouvait provoquer un plantage OOM _[`issue #523`](http://issues.autojs6.com/523)_
* `Correction` Une seule connexion semi-morte de l'extension VSCode bloquait le thread d'écriture des journaux, de sorte que les nouvelles connexions ne recevaient aucun journal _[`issue #385`](http://issues.autojs6.com/385)_
* `Correction` L'historique des versions restait à l'ancien chemin après le renommage ou le déplacement d'un fichier ou d'un dossier dans l'explorateur; il suit désormais le nouveau chemin (les dossiers réécrivent tous les chemins suivis qu'ils contiennent), et lorsqu'un déplacement écrase un fichier suivi, les deux historiques sont fusionnés dans le chemin de destination
* `Correction` La restauration d'un seul élément de la corbeille affichait "terminé" même en cas d'échec, car la boîte de progression avalait l'exception; l'erreur réelle est désormais affichée
* `Correction` Les messages d'opération de l'explorateur affichés depuis le thread principal (comme "Déplacé vers la corbeille") apparaissaient deux fois
* `Correction` Lors de la restauration d'un dossier depuis la corbeille, les noms des entrées de l'archive sont validés par rapport au chemin canonique de la destination, de sorte que les entrées contenant ".." ou des chemins absolus ne peuvent plus être écrites hors de la destination (ces entrées sont ignorées et journalisées)
* `Correction` Les scripts envoyés par l'extension VSCode (TypeScript, Lua, Node.js, Bun, QuickJS, Python) étaient tous exécutés comme du JavaScript Rhino ; ils sont désormais dirigés vers le moteur correspondant au nom du fichier
* `Correction` Les dialogues de refus de handshake ou d'échec de connexion de l'extension VSCode pouvaient dysfonctionner une fois l'Activity détruite (changement de langue ou de mode sombre) ; ils se rabattent sur un toast
* `Correction` L'historique des adresses de serveur du mode client n'était écrit qu'en cas d'échec de la poignée de main et jamais lors d'une connexion réussie, de sorte que l'"Historique" du dialogue d'adresse restait vide
* `Correction` Lors du choix de l'APK d'une mise à jour, une version sans paquet pour la première ABI préférée pouvait sélectionner un paquet ultérieur de priorité inférieure dans la liste (comme le paquet universel) au lieu du paquet de l'ABI préférée suivante
* `Correction` L'installateur de paquets intégré pouvait rejeter les APK signés V2/V3 sur Android 9-11 avec "Base APK has no verifiable signer" car l'analyseur d'archive de la plateforme laissait les informations de signature vides ; la vue de signature d'archive obsolète est désormais consultée en secours
* `Correction` Les cinq plugins Three affichent des icônes transparentes dans le centre de plugins, adaptées au thème clair ou sombre de l'hôte, sans arrière-plan du lanceur
* `Correction` Les informations des paquets reconnaissent les ensembles APK fractionnes dans les ZIP ordinaires, y compris sans extension de paquet connue
* `Correction` L'écran de confirmation de 3-Setup Installer ne s'affichait pas sur Android 14 et versions ultérieures lors d'une installation lancée avec AutoJs6 au premier plan
* `Correction` La racine du manifeste et les UID partagés pouvaient être mal lus depuis des commentaires ou espaces de noms falsifiés; les attributs dupliqués, identifiants de ressources contradictoires et UID invérifiables arrêtent le traitement
* `Correction` Le Centre de plugins pouvait restaurer depuis le cache des plugins retirés ou renommés après une actualisation réussie
* `Correction` Absence du fond circulaire des icônes dans les détails du centre de plugins et bordures des icônes en cache non actualisées lors du changement de thème
* `Correction` Les couleurs Material choisies dans l'ancienne disposition ne pouvaient pas être retrouvées avec "Localiser la couleur du thème actuel" après un changement de disposition, et la page des couleurs Material n'affichait pas le résumé de la couleur actuelle
* `Correction` L'ouverture des détails d'un plugin juste après un démarrage à froid du centre de plugins pouvait faire clignoter la boîte de dialogue, car chaque actualisation de la liste la reconstruisait
* `Correction` La fermeture occasionnelle des scripts UI empaquetés après l'affichage d'une page vide au démarrage
* `Correction` Compose UI refuse de monter une autre page ou fenêtre flottante dans un rappel de rendu et conserve la page actuelle; les pages peuvent être remontées après une mise à jour du plugin
* `Correction` Les fabriques de composants traitaient le premier noeud comme un objet de propriétés lorsque plusieurs noeuds étaient passés en arguments
* `Correction` Les accesseurs des propriétés Compose conservent null au lieu de renvoyer undefined, y compris la progression par défaut et les dates DatePicker effacées
* `Correction` Avec Paddle OCR v3 et un plugin v4/v5/v6 activés en même temps, l'empaquetage choisissait ce dernier et Paddle OCR ne fonctionnait pas dans l'application empaquetée ; les applications empaquetées n'acceptent désormais que le plugin PP-OCRv3 et ne copient plus les modèles et bibliothèques natives v4-v6 qu'elles ne peuvent pas exécuter
* `Correction` L'empaquetage APK conservait silencieusement l'une de deux bibliothèques natives de même nom au contenu différent ; il s'arrête désormais en indiquant les deux sources
* `Amélioration` Le profil Standard inclut 3-Setup Installer; l'installation système des APK n'affiche plus le conseil recommandant ce plugin
* `Amélioration` Lecture des données de modèle Three Stove Agent sur Android 7-10 via les API publiques, avec conservation des positions de fichier et annulation des tubes bloqués
* `Amélioration` Les vérifications lint de l'hôte excluent par chemin les classes et ressources de compatibilité AutoJs4 et continuent à vérifier le reste de l'hôte
* `Amélioration` Le centre des plugins écrit un enregistrement d'activation explicite et exécute le sondage d'activation après avoir observé une nouvelle installation d'un plugin désactivé par défaut (YOLO, MCP Server) signé avec le certificat officiel de l'hôte; les signataires tiers de confiance ou autorisés par l'utilisateur, les installations via adb et les mises à jour exigent toujours une activation manuelle, et les consommateurs à l'exécution continuent de lire uniquement l'enregistrement sauvegardé
* `Amélioration` Les gestionnaires accessibilité, client et serveur partagent les boutons de déploiement et séparateurs; les réglages repliés sont accessibles dans une boîte de dialogue par une roue dentée, et les commandes client repliées incluent l'historique; les commandes serveur repliées proposent aussi Déconnecter tous les clients
* `Amélioration` La visibilité et le format de l'heure sont mémorisés séparément pour le panneau de journal, l'activité de journal, les éléments globalconsole et console et la console flottante; l'heure est affichée par défaut dans les deux premiers et masquée dans les trois autres
* `Amélioration` Les tâches et journaux de début/fin abrègent les répertoires connus avec $cwd, $sdcard, $sample, $files, $cache et $data tout en conservant les noms et extensions réels; les limites des composants et les séparateurs finaux sont respectés
* `Amélioration` L'entrée MCP Server du tiroir utilise l'icône du protocole MCP avec des traits légèrement épaissis, avec une taille et une teinte cohérentes avec les autres entrées
* `Amélioration` Les erreurs Flow conservent les méthodes, fichiers de script et numéros de ligne des tâches asynchrones dans flowStack et stack, affichés automatiquement par console.error
* `Amélioration` Le paramètre maxAttempts de Flow repeatUntil vaut 0 par défaut pour des tentatives illimitées dans le délai total
* `Amélioration` Comparaison des collections ordonnées de noeuds et des projections snapshot immuables, avec gestion des délais et de l'annulation des sous-flux
* `Amélioration` Autorisation du réseau local sous Android 17 intégrée au parcours activation et aux paramètres du plugin, sans page du lanceur; sans autorisation, le plugin reste désactivé et le démarrage automatique est silencieux
* `Amélioration` Explications de la protection avancée dans l'hôte et le compagnon, distinguant le mode global de la disponibilité réelle du service et du contrôle des paramètres sécurisés
* `Amélioration` Les applications empaquetées demandent un accès au stockage selon les besoins du projet; refus, retour des paramètres, restauration du lanceur et démarrage du système vérifient les droits effectifs
* `Amélioration` media.playMusic utilise un service musical de premier plan avec gestion du focus audio, commandes de notification, propriété de lecture du script et libération à sa fin
* `Amélioration` L'aide du service au premier plan explique les restrictions audio en arrière-plan d'Android 17 et comment redémarrer le service depuis l'application visible avant de relancer la lecture
* `Amélioration` Rendre explicite la délégation de lancement de l'Activity du plugin Sélecteur de couleur, avec des options de l'expéditeur liées à la visibilité de l'hôte sous Android 16 et versions ultérieures
* `Amélioration` Conseils de reprise des connexions LAN HTTP, WebSocket et du pont réseau contrôlé Node, conservant les erreurs originales et expliquant la reprise après autorisation; conversion des noms de permissions indépendante de la langue du système
* `Amélioration` Renommer le groupe du tiroir d'accueil "Connexion au PC" en "Connexions à distance" pour regrouper les modes client et serveur ainsi que MCP Server
* `Amélioration` Passage du compileSdk du projet à 37 (Android 17) et amélioration des builds de débogage et de la vérification lint pour la migration du SDK
* `Amélioration` Passage du targetSdk de l'application et des applications empaquetées (inrt) à 37 (Android 17); les applications empaquetées passent du targetSdk 29 à 37, la permission réseau local est demandée à la demande sur Android 17 et les applications empaquetées guident l'accès au stockage partagé si nécessaire
* `Amélioration` Unifier l'inspection des paquets à 262144 entrées et augmenter les budgets des paquets, analyses imbriquées, manifestes et métadonnées pour les grands fichiers APK/AAB/APKS
* `Amélioration` Les dialogues d'information partagent un composant réutilisable permettant d'insérer des entrées key:value à toute position, d'aligner automatiquement les clés et d'annuler les lectures asynchrones à la fermeture
* `Amélioration` Les informations sur les composants APK/APKS/XAPK/APKM/APKZ/AAB sont déplacées vers le dialogue "Plus d’informations" du menu en haut à droite, avec le même style que le dialogue principal et une mise à jour lors du chargement des métadonnées
* `Amélioration` Le résumé détaillé d'APK Inspector dans les dialogues d'information des paquets APK et autres formats se consulte depuis le menu en haut à droite, au-dessus de "Plus d'informations"
* `Amélioration` Le Centre des plugins vérifie les classes de service OCR avant le démarrage et signale rapidement les échecs d'activation, avec un délai de négociation de 5 secondes et l'annulation des liaisons abandonnées
* `Amélioration` Le module d'accessibilite du pont Node ajoute dump (instantanes de fenetre en texte, JSON ou XML limites a 400 noeuds, 32 niveaux et 256 KiB), explain (nombre de correspondances par filtre d'un descripteur de selecteur) et screenshot (capture d'ecran par accessibilite sur Android 11+ avec options d'echelle, de region, de format et de qualite, livree comme fichier de cache a usage unique ou fichier de l'espace de travail), et le module app ajoute currentWindow (paquet, activite et liste des fenetres en cours)
* `Amélioration` Le pont Node ajoute un module files (list, stat, read, write, mkdir, rename, delete) enracine dans le repertoire de travail : les chemins doivent etre des chemins relatifs normalises, les liens symboliques ne peuvent pas mener a l'exterieur, les lectures et ecritures sont limitees a 1 MiB de contenu UTF-8 ou base64, les listes a 2000 entrees, l'ecriture et la suppression exigent les jetons de capacite dedies files.write et files.delete, et le gestionnaire de fichiers de l'espace de travail se rafraichit apres les modifications
* `Amélioration` Le pont Node ajoute un module console (console.tail lit les entrees les plus recentes de la console globale avec lines jusqu'a 500, sinceId, level et un budget de 256 KiB), engines.execScript et execScriptFile acceptent les options captureConsole et waitMs (attendre la fin du script et collecter la sortie console de sa fenetre d'id, captureMode global-window), la console conserve au plus 4096 entrees de journal (les plus anciennes sont supprimees et la vue console se rafraichit par id d'entree), et ConsoleImpl expose addLogListener et removeLogListener
* `Amélioration` Le pont Node ajoute la semantique du processus hote pour engines.list, engines.stop(id) et engines.stopAll avec scope host (lister et arreter les executions du ScriptEngineService de l'hote sans changer la semantique existante de engines.all / stopAll), device.info (modele et version du systeme, taille / densite / orientation de l'ecran, batterie, version de l'hote, etat du service d'accessibilite), et les methodes en lecture seule app.listSamples / app.readSample (enumerer et lire les scripts d'exemple integres, chemins limites a la racine des exemples, 1 MiB par lecture)
* `Amélioration` La `console TypeScript` reste désormais repliée par défaut et ne s'affiche à la demande depuis le menu d'options de la page complète du journal ou du panneau de journal de l'éditeur que si le plugin de compilation est disponible, avec un état mémorisé séparément; pendant l'exécution d'un extrait, l'action verte Exécuter devient une action rouge Arrêter, la page complète du journal affiche le répertoire de travail réel et le menu du panneau regroupe aussi Copier, Envoyer et Exporter
* `Amélioration` Les messages de validation du runtime TypeScript TSX suivent désormais la langue de l'application dans les 10 langues prises en charge au lieu d'afficher de l'anglais codé en dur
* `Amélioration` L'aperçu des polices Ace affiche désormais au-dessus du clavier multifonction une indication de téléchargement tant que la police sélectionnée est indisponible, puis la masque après le téléchargement ou le passage à une police disponible
* `Amélioration` La boîte de dialogue Aller à la ligne de l'éditeur de code remplace désormais immédiatement une valeur supérieure à la dernière ligne du document par cette dernière ligne
* `Amélioration` Les corps des requêtes et réponses Fetch contrôlées de Node.js et les messages WebSocket sortants prennent en charge le transport par descripteur de fichier, réduisant le coût des gros transferts binaires via Binder
* `Amélioration` Suppression de l'indicateur de requête obsolète de stripping TypeScript hérité de Node.js Runtime ; le TypeScript brut reste réservé au compilateur et n'est jamais effacé par expressions régulières dans le runtime
* `Amélioration` Suppression côté hôte des options Gradle Node.js et des copies d'exemples, déclarations de types, assistant de projet et scripts de vérification; les valeurs par défaut du runtime et les ressources de développement appartiennent désormais au plugin Node.js Runtime
* `Amélioration` Les diagnostics du compilateur DEX conservent désormais, dans la limite existante, les détails INFO/WARNING/ERROR expurgés ainsi que les métadonnées facultatives d'origine logique, d'entrée d'archive et de position; les options pour développeurs affichent le dernier chemin de chargement JAR conservé uniquement en mémoire du processus et des indications localisées pour BUSY, le délai dépassé et le repli
* `Amélioration` La compilation TypeScript enregistre désormais, pour chaque requête et sans donnée sensible, la préparation, la recherche en cache, la liaison/préchauffe du compilateur, la compilation distante, le traitement de sortie, la durée totale, la taille de l'archive, le nombre de sources et la variation du tas de l'hôte, et décompose le temps du compilateur en intervalles avant ingress, ingress, inter-phases, parse, semantic, emit et après emit; les callbacks progress mal formés ou désordonnés sont ignorés et ces observations n'imposent aucun seuil de performance
* `Amélioration` Amélioration de la compatibilité des arguments des méthodes KeyCode _[`issue #492`](http://issues.autojs6.com/492)_
* `Amélioration` Les méthodes KeyCode permettent d'envoyer des commandes de touche avec l'autorisation Shizuku
* `Amélioration` Initialisation différée des composants non critiques pour améliorer les performances de démarrage de l'application
* `Amélioration` L'application n'affiche plus de boîtes de dialogue de demande de permission au démarrage; lorsque l'accès à tous les fichiers manque, la page d'accueil affiche un rappel permettant de l'accorder directement
* `Amélioration` Mise en cache des résultats de compilation des ressources Rhino intégrées et des métadonnées des fonctions d'extension de l'environnement d'exécution pour améliorer les performances d'initialisation des scripts
* `Amélioration` Synchronisation du moteur Rhino avec le projet amont, avec prise en charge de new.target et une meilleure compatibilité des portées, des TypedArray et du débogueur
* `Amélioration` Amélioration des performances de formatage des grands tableaux dans la console Rhino
* `Amélioration` Refonte de l'architecture d'extension des modules Kotlin avec des spécifications de montage déclaratives prenant en charge plusieurs alias et le contrôle indépendant du préfixe `$` pour chaque nom, liaison explicite des realms Rhino, séparation sémantique de tasks et work_manager, et davantage de tests de compatibilité et de régression sémantique
* `Amélioration` Réutilisation du processus Node.js persistant et de l'environnement d'exécution à l'échelle du processus pour améliorer les performances de démarrage à chaud des scripts
* `Amélioration` Mesure par phase du démarrage de Node.js et rapports fiables sur les performances à froid et avec le runtime chaud
* `Amélioration` Mise en cache de la résolution des bibliothèques natives intégrées de Node.js afin d'éviter la découverte répétée des plugins
* `Amélioration` Mise en cache des candidats autorisés de l'environnement d'exécution Node.js externe et des sessions Binder persistantes, avec invalidation d'état et reconnexion atomique afin de réduire le coût de découverte et de liaison
* `Amélioration` Suppression de l'analyse des modules chiffrés à l'échelle du projet dans le chemin d'exécution Node.js par défaut afin d'améliorer les performances de démarrage des scripts non triviaux et des grands projets
* `Amélioration` Ajout d'une régression réelle de bascule Rhino/Node sur plusieurs cycles d'exécution, couvrant la réutilisation/la récupération/l'arrêt propre de la session persistante du plugin externe
* `Amélioration` Ajout de tests soak du runtime persistant du plugin externe et de portes de preuves de production Node.js couvrant la matrice appareils/ABI, le cycle de vie du runtime, les mesures de ressources et la stabilité sur 8 h/24 h
* `Amélioration` La vérification native de Node.js résout et valide désormais les racines des sources et de la compilation du plugin d'exécution externe
* `Amélioration` Les portes d'intégration finale et de livraison Node.js appliquent une stratégie fail-closed lorsque des rapports requis, des preuves correspondant aux sources actuelles ou des artefacts d'acceptation en production sont absents ou périmés
* `Amélioration` Désactivation par défaut du stripping TypeScript historique fondé sur des expressions régulières pour les entrées Node.js brutes et dynamiques, avec sortie du compilateur obligatoire sauf activation explicite d'une option temporaire de compatibilité propre à la requête
* `Amélioration` Les specifiers import() TypeScript calculés pour Node.js sont mappés de .ts/.mts/.cts vers leurs modules .js/.mjs/.cjs précompilés dans le snapshot de compilation fermé, avec des erreurs stables pour les cibles absentes, ambiguës ou hors limites et sans fallback vers des sources TypeScript ajoutées pendant l'exécution
* `Amélioration` Les scripts Python autonomes ajoutent désormais dans la console une indication sûre lors d'un ModuleNotFoundError exact, orientant l'importation de modules voisins/locaux vers un projet Python explicite avec `project.json` et `type: python`, sans exposer de chemins privés
* `Amélioration` Les chunks de sortie Python Runtime sont écrits dans la console Host dès leur arrivée, avec un décodage UTF-8 incrémental indépendant pour stdout/stderr qui préserve les points de code répartis entre chunks et une limite de sortie de bout en bout de 16 MiB
* `Amélioration` Le project.json des projets Python accepte un timeout entier positif en millisecondes, de 5 minutes par défaut, plafonné à 30 minutes et toujours limité par le plugin Runtime
* `Amélioration` Le project.json des projets Python accepte une entrée de module explicite via "entryMode": "module" et un nom main pointé normalisé, permettant les imports relatifs au paquet avec la sémantique standard des modules Python
* `Amélioration` Porter les limites du workspace de projet Python à 64 MiB compressés, 8192 fichiers et 128 MiB extraits, puis comparer avant l'envoi les trois dimensions réelles du snapshot aux capacités du Runtime Provider afin de prendre en charge les dépendances Python pures locales au projet
* `Amélioration` Le project.json des projets Python accepte des instantanés stdin finis sous forme de texte intégré ou de fichier explicite relatif au projet, ce qui permet à sys.stdin.read() de consommer une entrée prédéfinie sans invite interactive
* `Amélioration` Modularisation sous forme de plugins des fonctions liées à ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/OpenCC/Pinyin/Pinyin4j/MediaInfo
* `Amélioration` Regroupement des requêtes de détails multimédias du gestionnaire de fichiers en un seul instantané MediaInfo afin de réduire les appels Binder et le temps d'attente
* `Amélioration` Limitation des replis d'affichage et de lecture externes aux accès URI en lecture seule
* `Amélioration` Unification des actions de plugins du gestionnaire de fichiers pour le clic par défaut, le bouton principal, la lecture depuis les détails multimédias et le menu overflow sous une politique explicite de la page principale des fichiers avec un repli unique et déterministe
* `Amélioration` Les archives utilisent des icônes de consultation et d’information, les actions à droite du chemin sont retirées pendant la consultation, l’extraction propose un choix de destination et les menus contextuels omettent le préfixe du nom du plugin
* `Amélioration` Explorer Action v7 permet aux plugins d'archives de rouvrir en lecture seule une sortie en attente masquée et de la vérifier avant sa publication tout en conservant la validation directe des clients v4
* `Amélioration` Explorer Action v12 vérifie l'UID appelant, l'identité du fichier ordinaire, la taille, la date de modification, l'état de lien symbolique et la durée de la session avant chaque ouverture de volume voisin, avec des limites fixes pour les noms, le catalogue et les requêtes d'ouverture
* `Amélioration` La sélection native d'archive n'affiche plus que Quitter et Extraire, intègre une progression bornée et l'annulation, et garde la barre de cinq actions lisible sur les écrans étroits en centrant les libellés sous les icônes
* `Amélioration` Conservation de la géométrie compacte historique de la barre de chemin, de la liste et de la barre de sélection à cinq actions, tout en gardant les libellés d'accessibilité, les rôles de bouton, l'état de sélection et les annonces TalkBack; le lancement du plugin valide aussi la taille des requêtes Explorer Action en mode fail-closed
* `Amélioration` Migration des fournisseurs de conformité AI et compilateur DEX du répertoire de production `plugins` vers le groupe de build dédié `test-apps`, en conservant la couverture de régression Binder hostile et en séparant la frontière de publication des plugins de production indépendants
* `Amélioration` Le chemin hôte expérimental du compilateur DEX reste désactivé par défaut et exige un composant explicite, avec épinglage de l'UID du composant exact et de l'ensemble complet des signataires, repli à répartition unique, provenance sémantique du cache, entrée de callbacks bornée, délais Binder isolés, validation structurelle du ZIP DEX, vidage borné de reliable pipe avec vérification des erreurs du pair à EOF, publication unique avec retour arrière protégée par les condensats source et destination, et arbitrage atomique entre les signaux de terminaison asynchrones et le point de commit de publication qui force le retour arrière si l'annulation, le délai dépassé ou Binder death l'emporte avant le commit
* `Amélioration` Le compilateur DEX R1.1 maintient l'expérimentation désactivée par défaut et permet aux JAR bruts de runtime.loadJar de préférer un provider de composant exact explicitement sélectionné et signé comme l'hôte, avec un seul repli vers D8/dx intégré en cas d'échec, aucun repli en cas d'annulation et le chargement AAR conservé sur le chemin intégré
* `Amélioration` Le compilateur DEX R1 est clos tout en gardant le chemin désactivé par défaut avec opt-in de l'exact component: R1.1 production 7/7, R1.2 automatisation 4/4, R1.3 matrice du vrai provider 7/7 et conditions de sortie 3/3. Le production routing API 34 a réussi 8/8; host DEX 16 suites/149 tests, wire 4/24, fake-provider 5/25 et plugin 48 tests ont réussi, avec lint à 0 erreur. La campagne canonique f3c2b1af-be93-41e7-b541-f167f90e5cc1 a validé API 24/25 via D8 CLI, API 26/28/34/36 via D8Command sur x86_64 et API 31 sur l'appareil QV arm64 multi-utilisateur; les campagnes antérieures échouées ou arrêtées volontairement sont conservées. L'annulation coopérative du dernier waiter et la récupération élargie restent des travaux R2 décochés
* `Amélioration` Les tests de conformité expérimentaux du compilateur DEX couvrent désormais le transport Binder Android réel et le stockage d'artefacts, les composants signés exacts, les scénarios hostiles de callback/sortie/délai, la validation DEX adaptée à l'API cible et l'exécution via DexClassLoader
* `Amélioration` La politique hôte pure de texte IA, désactivée par défaut, fournit une sélection de confiance déterministe, l'épinglage et la revalidation exacte de l'identité, l'intersection des capacités hôte/fournisseur, le rejet des candidats ambigus, l'isolation des identifiants gérés par le plugin, des diagnostics sans contenu sensible, l'arbitrage déterministe des courses annulation/terminal de session, la validation des outils et du JSON par descripteur liée à la requête, le contrôle agrégé longueur/SHA-256/quota du streaming, le rejet des clés JSON dupliquées après décodage, un `toString` résumé pour les modèles contenant des données sensibles et une clé de cache à séparation de domaine pour les collections de capacités
* `Amélioration` Le chemin hôte AI Provider V2 raccordé combine la découverte PackageManager par composant exact et la négociation metadata Binder avec la coordination paginée `listTargets`, la réinspection d'identité avant chaque dispatch/callback, les tokens de catalogue à usage unique, un plan de cible immuable sans identifiants, le transport session/PFD borné, les crédits de streaming, le coupe-circuit de processus, un propriétaire terminal unique et le cleanup idempotent pour les routes de plugin `ai.*` explicites
* `Amélioration` Modularisation du runtime WebView Ace/du bridge JavaScript/des services de langage intégrés/des ressources statiques sous forme de plugin installable séparément, afin de réduire le couplage avec l'APK hôte et de permettre des mises à jour indépendantes
* `Amélioration` Modularisation de la documentation hors ligne du projet sous forme de plugin, afin de réduire la taille de l'APK hôte et de permettre une installation et des mises à jour indépendantes
* `Amélioration` Déplacement de l'analyse de l'inventaire et de la vérification SHA-256 complète de la documentation de projet hors ligne au premier chargement vers un thread d'arrière-plan afin de ne pas bloquer l'interface
* `Amélioration` Migration de toute la chaîne d'outils AXML/ARSC/analyse/signature/stockage de clés/cryptographie nécessaire aux compilations APK vers le plugin APK Builder Template, avec une inspection APK légère dans l'hôte
* `Amélioration` La découverte du plugin APK Builder valide le SHA-256 du certificat installé avant toute connexion Binder, bloque les signatures non approuvées en affichant le package et l'empreinte observée, et conserve le parcours de signature officielle
* `Amélioration` Les applications empaquetées sont toujours construites par le plug-in APK Builder sur l'appareil ; le commutateur développeur de construction distante expérimentale et l'indicateur de compilation du plug-in sont supprimés
* `Amélioration` Déplace l'index de complétion AutoJs6 complet d'environ 1.3 MiB hors du chemin synchrone de première présentation d'Ace, le charge pendant une période d'inactivité après la première image de code visible et remplace sans interruption l'index initial; les cinq environnements Android 9-15 ont restauré 400 éléments globaux et 80 modules
* `Amélioration` La sélection du moteur de l'éditeur respecte les choix explicites de l'utilisateur et propose un accès au Centre de plugins lorsqu'Ace est indisponible
* `Amélioration` Le bouton "Exécuter" de l'éditeur de code devient "Arrêter" après avoir été pressé, ce qui permet d'arrêter rapidement les scripts
* `Amélioration` Réorganisation de la barre d'actions et de la hiérarchie des menus de l'éditeur de code, avec Journal comme action permanente et un meilleur emplacement pour Encodage du fichier et Mode lecture seule
* `Amélioration` Amélioration des interactions de l'éditeur de code pour les thèmes/polices/barres d'outils/mode lecture seule/pliage/menus de sélection et plus encore
* `Amélioration` Amélioration de la détection de l'encodage et de la sécurité d'enregistrement dans l'éditeur de code; les conversions d'encodage ne sont écrites sur le disque que lorsque l'utilisateur enregistre le fichier
* `Amélioration` Réduction supplémentaire de la hauteur et de l'espacement horizontal de la barre d'informations de l'éditeur de code afin de libérer davantage d'espace pour la zone de code
* `Amélioration` Le panneau de journal inférieur permet d'accéder au code source en cliquant sur les cadres de pile de la page de journal indépendante
* `Amélioration` Les changements de source de la documentation du projet prennent effet immédiatement
* `Amélioration` Le gestionnaire de fichiers décode et formate le fichier AndroidManifest.xml binaire complet en arrière-plan uniquement après un appui sur le bouton Manifest, avec de meilleures performances de défilement des grands textes et de zoom par pincement
* `Amélioration` Les détails des APK affichent le SDK maximal uniquement lorsqu'il est explicitement déclaré dans le manifeste et renomment l'élément Installation en Exception, uniquement visible lorsqu'une exception existe
* `Amélioration` Amélioration des aperçus Markdown du gestionnaire de fichiers avec le HTML brut couramment utilisé sur GitHub et nettoyé de manière sécurisée, les images distantes HTTPS, le défilement des tableaux sur écran étroit et la compatibilité avec les anciens commentaires masqués
* `Amélioration` Amélioration du style et des fonctions des boîtes de dialogue "Trier" et "Filtrer" du Centre des plugins
* `Amélioration` Le Centre des plugins affiche les variantes courantes par défaut et fournit une page distincte pour les autres variantes
* `Amélioration` Amélioration des journaux des modifications multilingues/du cache des langues/de la vérification officielle/du chargement des index et d'autres fonctions du Centre des plugins
* `Amélioration` Le Centre des plugins affiche d'abord un instantané local léger et l'index en cache, puis actualise en parallèle les métadonnées des plugins et l'index en ligne; les plugins défaillants restent désactivés et affichent l'erreur réelle ainsi que des conseils de récupération lors d'une tentative d'activation
* `Amélioration` Normalisation des ressources de chaînes du projet: traductions multilingues complètes/classement par fichier/tri par nom/ponctuation ASCII
* `Amélioration` Limitation des ressources linguistiques de l'APK aux 10 configurations régionales prises en charge par le projet afin de réduire la taille du paquet
* `Amélioration` Le système de compilation sélectionne dynamiquement les versions compatibles de Kotlin/KSP/AGP/R8 et réduit la pression mémoire au démarrage de Gradle
* `Amélioration` Suppression des avertissements de compilation Kotlin liés aux valeurs de retour Java nullables, aux expressions redondantes et aux API Android obsolètes, avec maintien de la compatibilité avec les anciennes versions de Kotlin
* `Amélioration` Le plugin IA officiel non publié est finalisé sous le nom 3-Stone AI et son contrat interprocessus est remplacé par `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, de nouveaux descripteurs Binder, le domaine schema AP et des fixtures de conformité renommées; aucun alias d'identité n'est conservé
* `Amélioration` La découverte paginée de cibles AI Provider V2 est implémentée directement: chaque cible porte des identités target/provider/model stables, locality, états de configuration et disponibilité, capacités, limites, contrôles et origines HTTPS autorisées; REMOTE/HYBRID et les identifiants gérés par le plugin sont pris en charge sans octets d'identification dans Binder, et aucun alias wire remplacé n'est conservé
* `Amélioration` L'actualisation des dossiers du gestionnaire de fichiers applique des différences minimales de liste, garde liées les lignes inchangées et conserve la position de défilement lors des actualisations sur place
* `Amélioration` Refactorisation du gestionnaire de fichiers autour de composants explicites pour la disposition des listes, la présentation des éléments, la barre de sélection, les archives, les capacités d'entrée, la réservation des sorties et la récupération, afin de réduire les classes de vue et de session surdimensionnées et de rendre la navigation, les limites de sécurité et les politiques d'action testables indépendamment
* `Amélioration` La liaison et l'amorçage du compilateur TypeScript s'effectuent désormais lors d'un préchauffage borné au démarrage plutôt que pendant la première compilation utilisateur; les sessions de projet revision 1 conservent une liaison active après l'ouverture de l'éditeur ou une compilation, tandis que l'expiration de l'inactivité initiale, la pression mémoire, les échecs et la fermeture de l'application reviennent sûrement à une compilation à froid
* `Amélioration` La suite de scrapers rétablit 9 tâches de version indépendantes et 5 tâches propres à l'hôte, plus 1 tâche de synchronisation entre dépôts; AutoJs6-Gradle-Platform-Versions exporte de manière centralisée les métadonnées JSON de la version stable d'Android Studio validées par les tables de versions
* `Amélioration` Mise à niveau de Gradle 9 Wrapper version 9.6.1 -> 9.7.1
* `Amélioration` Les données de compatibilité de la plateforme Gradle et leurs tâches de mise à jour ont été déplacées vers AutoJs6-Gradle-Platform-Versions; AutoJs6 consomme désormais directement la version 1.5.0 depuis Maven Local, sans surcharge locale des données, scrapers dupliqués ni ancienne dépendance à Puppeteer
* `Amélioration` Les aperçus HTML et Markdown sont délégués à des plugins Previewer dédiés; l'application principale conserve l'entrée et guide l'installation ou l'activation lorsque le plugin est absent, désactivé ou indisponible
* `Amélioration` Centralisation de tous les appels Pinyin via le plugin correspondant et suppression du dictionnaire intégré et de l'implémentation de conversion inutilisés
* `Amélioration` Le démarrage ou l'arrêt du service d'accessibilité via root ou les paramètres sécurisés n'écrit plus la valeur système `accessibility_enabled` (maintenue par le système lui-même à partir des services liés depuis Android 7.0), de sorte que l'arrêt de ce service ne laisse plus un 0 obsolète alors que d'autres services d'accessibilité restent activés
* `Amélioration` Les fenêtres flottantes telles que le bouton flottant avec ses boîtes de dialogue et l'inspecteur de mise en page suivent désormais les réglages de langue et de mode nuit d'AutoJs6 plutôt que ceux du système
* `Amélioration` Le menu "Plus" du bouton flottant s'affiche sans attendre les requêtes sur l'application au premier plan, charge les informations en arrière-plan et actualise uniquement les sous-titres concernés; le nom du paquet et l'Activity partagent une seule requête, et la fermeture du menu annule les chargements en cours et ignore les résultats tardifs
* `Amélioration` Le `source` de l'objet événement reçu par les rappels de `auto.registerEvent` est désormais résolu au premier accès (chaque événement parcourait auparavant la chaîne des parents de façon anticipée pour sa profondeur et son index), ce qui réduit le coût des rappels qui ne le lisent jamais ; sous l'API 33, un `source` non lu pendant le rappel est ensuite null
* `Amélioration` La résolution de la racine des fenêtres d'accessibilité n'a plus qu'une seule implémentation (`AccessibilityBridge` réécrit en Kotlin, ses trois chemins de code dupliqués unifiés dans `WindowRootPolicy` / `WindowRootProvider`, que l'inspecteur de mise en page utilise aussi pour ses fenêtres) : avec un filtre de fenêtres, `auto.root` passe à la fenêtre suivante lorsque la première fenêtre retenue n'a pas de racine (il renvoyait auparavant `null` immédiatement) ; `auto.windowRoots` et les actions sur les nœuds ne peuvent plus rencontrer de racine `null` ; le fait que `auto.rootInActiveWindow` ignore le filtre de fenêtres est désormais indiqué explicitement dans les sources
* `Amélioration` Le recyclage des nœuds d'accessibilité est désormais décidé en un seul endroit, `NodeLifecycle` / `NodeRecyclePolicy` (depuis Android 13 les nœuds n'ont plus besoin d'être recyclés, le `recycle()` sans effet n'est donc plus appelé pour chacun d'eux ; le recyclage en ligne autrefois dispersé dans les algorithmes de recherche, les actions de défilement, la racine en cache du service et l'allocateur passe entièrement par là, et un recyclage qui échoue est journalisé au lieu d'interrompre une recherche)
* `Amélioration` Les captures d'écran d'accessibilité (`automator.captureScreen()`) sont prises par le nouveau `A11yScreenshotter` : le tampon matériel d'une capture est désormais toujours fermé (seul le bitmap enveloppant était recyclé), et la nouvelle tentative sur l'erreur de limitation de fréquence du système est plafonnée à 60 essais (3 s), après quoi la promesse est résolue avec `null` comme pour tout autre échec (elle ne se résolvait jamais tant que le système continuait de limiter)
* `Amélioration` Les filtres de sélecteur, les algorithmes de recherche (DFS / BFS) et la navigation à la boussole (`compass`) travaillent désormais sur l'interface `UiNode`, indépendante d'Android (implémentée par `UiObject`), si bien que les trois sont couverts par des tests unitaires JVM sur un arbre de nœuds en mémoire ; l'API visible des scripts et le comportement sont inchangés
* `Amélioration` Les propriétés de sélecteur sont désormais décrites une seule fois dans le registre déclaratif `SelectorRegistry` : les méthodes builder de `UiSelector` en sont générées, les sélecteurs de type map (`pickup({...})`, etc.) sont analysés par lui plutôt que par réflexion, et les fonctions globales de sélecteur en sont exportées avec une liste blanche explicite (`toStringReadable` et les noms décorés des membres `internal` ne sont plus exposés comme globales) ; les arguments numériques des fonctions globales et des sélecteurs map sont uniformément arrondis en entiers (les sélecteurs map tronquaient auparavant)
* `Amélioration` Filtres de sélecteur dédupliqués : les propriétés de chaîne (text / desc / content / id / className / packageName et leurs variantes) partagent `StringPropertyFilter`, les métriques de limites (left / boundsLeft / minLeft / maxLeft, etc.) partagent `MetricsFilter`, les propriétés entières partagent `IntFilter` ; les filtres relatifs à l'écran (bounds / métriques / screenCenter / screenCoverage) sont évalués contre une taille d'écran injectable et couverts par des tests unitaires JVM ; un sélecteur évalue un instantané de sa liste de filtres, si bien qu'un sélecteur partagé entre threads est sûr ; l'API visible des scripts et le comportement sont inchangés
* `Amélioration` Les requêtes de sélecteur (`findOne(timeout)` / `untilFind` / `untilFindOne` / `exists` / `find(max)` et les obsolètes `findOne()` / `waitFor()`) s'exécutent comme un plan de sondage (`PollPolicy` : délai, intervalle, nombre, backoff) avec des valeurs par défaut inchangées (intervalle de 50 ms, un délai de 0 ou moins attend indéfiniment) ; les méthodes qui attendent sans limite portent la mention "peut bloquer indéfiniment" dans leur KDoc ; avec l'indicateur `findOnUiThread` actif, une recherche envoyée au thread d'interface attend au plus le délai de la requête et échoue avec une erreur lisible une fois celui-ci dépassé (`findOne(timeout)`), tandis que les requêtes sans limite (`untilFind`, etc.) attendent de manière interruptible, de sorte que l'arrêt du script y met fin
* `Amélioration` `UiObject` scindé : la table d'alias des types de résultat de `pickup` / `detect`, le sélecteur de résultats et l'intercepteur qui appelle les méthodes du nœud par leur nom passent dans `core/automator/pick` (`PickResultType` / `Picker` / `Detector` / `Invoker`), `summary()` / `toString()` / `isSimilar()` dans `UiObjectSummary` ; nouveau `UiObject.fingerprint()` (un hachage des limites, du texte, de la description, du nom de classe, du nombre d'enfants et des propriétés booléennes clés, sans visiter les frères ni les enfants) ; `indexInParent()` d'un nœud atteint via `parent()` est calculé à la première utilisation ; le générateur de code de l'inspecteur de mise en page (`CodeGenerator` / `ReadOnlyUiObject`) converti en Kotlin, ce dernier étant désormais une vue d'instantané implémentant directement `UiNode` ; l'API visible des scripts et le comportement sont inchangés
* `Amélioration` Réutilisation entre runtimes (roadmap P5.4) : les capacités de sélecteur de l'hôte Python (`selector.snapshot` / `selector.find` / `selector.click` / `selector.set_text`) et le module `accessibility` du Node Bridge (`findOne` / `findAll` / `click` / `longClick` / `setText` / `scrollForward` / `scrollBackward` / `findByText` / `clickText`) consomment désormais le cœur d'accessibilité du runtime de scripts (le parcours `UiNode`, les filtres de sélecteur, le fournisseur de racines de fenêtre, le cycle de vie des nœuds), les deux protocoles JSON restant identiques champ par champ ; les recherches du Node Bridge ne laissent plus fuir les nœuds visités en chemin sous Android 13 ; les captures d'écran d'accessibilité de l'hôte Python effectuent chaque tentative via le `A11yScreenshotter` partagé (en conservant la relance bornée et le délai de 4 secondes de l'hôte)
* `Amélioration` Chaque environnement d'exécution de script possède désormais sa propre console (ses propres entrées de journal et sa fenêtre flottante, chaque ligne étant transmise à la console globale) ; la fenêtre flottante n'affiche que la sortie du script courant (comme dans Auto.js 4) et relancer un script portant le même titre remplace la fenêtre flottante laissée par l'exécution précédente ; les entrées de journal conservent le contenu brut et un horodatage, chaque vue rendant le préfixe horaire à la demande (masqué par défaut dans la fenêtre flottante, activable dans les paramètres de la page des journaux) ; les entrées `console.tail` du Node bridge gagnent un champ `time` (millisecondes epoch) tandis que `text` conserve son préfixe horaire
* `Amélioration` Les exemples OCR demandent le consentement Android et reconnaissent de vraies images; les plugins OCR ou codes-barres indisponibles renvoient une erreur unavailable lisible, et les échecs de reconnaissance restent des erreurs
* `Amélioration` Les scripts Node prennent en charge un démarrage asynchrone négocié, libèrent les threads Binder pendant les longues exécutions et renvoient les modifications de l’espace de travail à la fin, avec compatibilité des anciens hôtes synchrones
* `Amélioration` Les noeuds JSON de `accessibility.dump` du Node bridge portent les indicateurs d'etat `scrollable`, `checkable`, `checked`, `editable`, `focused`, `selected` et `longClickable` en plus des sept champs du descripteur ; l'autorisation de session MCP Server inclut les actions globales `keys.notifications` / `quickSettings` / `powerDialog` / `lockScreen` pour l'outil `ui_press_key` du plugin, et un appel `keys.*` sans le service d'accessibilite repond desormais avec la categorie `unavailable` comme le module `accessibility` ; un `accessibility.swipe` de longueur nulle (points de depart et d'arrivee identiques) est envoye comme un appui de `durationMs`, dont dependent les formes par coordonnees de `ui_click` / `ui_long_click` de MCP
* `Amélioration` Alignement 16 KB pour les bibliothèques natives de l'hôte et du runtime-kit, avec jackpal JNI recompilé, libc++ du NDK r28.2, provenance et vérification de compilation partagée
* `Amélioration` Le constructeur APK signale les bibliothèques natives incompatibles dans la progression et les résultats, enregistre l'alignement dans les métadonnées locales et conserve l'empaquetage compressé
* `Amélioration` Les résumés de version du Centre de plugins normalisent aussi les chaînes date-heure ISO 8601 telles que `2026-09-11T14:30:00+08:00` en `yyyy-MM-dd`, en conservant la date calendaire du décalage horaire de la chaîne
* `Amélioration` La recherche multicolore (`images.findPointByColors`, `images.findPointsByColors`) préfiltre les candidats avec des masques OpenCV vectorisés, s'exécutant des dizaines de fois plus vite sur les images denses avec des résultats identiques
* `Amélioration` Les détecteurs de couleur exposent une méthode `distance` partagée par `colors.isSimilar`, `images.detectColor` et la recherche multicolore; l'algorithme `hs` utilise la distance circulaire de teinte et les noms d'algorithme inconnus lèvent une erreur explicite
* `Amélioration` L'état du capteur d'écran est lu sans verrou, et `images.releaseScreenCapturer` ne bloque plus les appels d'image concurrents pendant la libération
* `Amélioration` Les niveaux de pyramide de la recherche de modèles utilisent l'interpolation par aire, la recherche masquée conserve le coefficient de corrélation normalisé, et les petites valeurs de `max` affinent toujours plusieurs candidats afin de ne pas perdre la meilleure correspondance aux niveaux grossiers
* `Amélioration` Fenêtre de hiérarchie de mise en page : les lignes résument id / text / desc / clickable et d'autres indicateurs, le nœud sélectionné et tous ses ancêtres sont mis en évidence dans l'arbre et encadrés à l'écran, l'arbre défile horizontalement, l'élément de menu "Sélectionner le nœud parent" remonte la sélection niveau par niveau, et une astuce affichée une seule fois explique la sortie avec la touche volume bas
* `Amélioration` La correspondance de caractéristiques ne conserve que les inliers RANSAC et valide le quadrilatère projeté, corrige un matcher incompatible avec le type de descripteur au lieu de lever une exception, accepte un `scale` supérieur à 1 pour agrandir les petits objets, et ORB détecte jusqu'à 20000 points clés par défaut afin que les scènes très texturées n'évincent plus les points de la région de l'objet
* `Amélioration` Node bridge ajoute les méthodes en lecture seule app.listDocs / app.readDoc (énumérer et lire les ressources vérifiées de documentation hors ligne du AutoJs6-Plugin-Offline-Docs installé, chemins limités à la racine de la documentation, 1 Mio par lecture, available: false sans plugin éligible) afin que le plugin MCP Server puisse exposer des ressources autojs6://docs/
* `Amélioration` Les limites de rétention de l'historique des versions et de la corbeille (jours, versions, octets par fichier, octets totaux, taille de fichier suivie) traitent uniformément zéro ou une valeur négative comme illimité et partagent un seul objet de politique
* `Amélioration` L'historique des versions ignore un instantané dont le contenu est identique à la révision la plus récente stockée, évitant un stockage en double
* `Amélioration` Le déplacement vers la corbeille vérifie d'abord la limite de taille et, lorsque les éléments la dépassent, propose de déplacer quand même, de supprimer définitivement ou d'annuler; les entrées plus anciennes sont supprimées juste après le déplacement, et la limite de taille conserve toujours l'entrée la plus récente
* `Amélioration` Le nettoyage périodique applique la politique de conservation complète (jours, versions, taille par fichier et totale pour l'historique des versions; jours et taille pour la corbeille) et supprime les entrées de fichier sans révision; les limites des brouillons d'urgence traitent aussi 0 comme illimité
* `Amélioration` Les connexions de l'extension VSCode disposent d'un battement de coeur applicatif (ping/pong) et d'une limite de longueur de trame, libérant rapidement les connexions mortes et évitant les allocations énormes en cas de désynchronisation _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `Amélioration` Les fichiers enregistrés depuis l'extension VSCode peuvent conserver leur structure de répertoires relative dans le répertoire de travail _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `Amélioration` L'explorateur enregistre le contenu actuel d'un fichier comme révision OVERWRITE_PRE avant qu'une copie ou un déplacement ne le remplace; lorsqu'un dossier est écrasé, les fichiers suivis qu'il contient reçoivent des instantanés
* `Amélioration` Chaque élément de la liste de la corbeille affiche un compte à rebours avant expiration (dérivé des jours de conservation de la corbeille, masqué si illimité), et les éléments expirant sous 24 heures sont mis en évidence
* `Amélioration` Lorsqu'un élément de la corbeille ne peut être ni prévisualisé ni restauré parce que son fichier de données est manquant ou endommagé, un message localisé est affiché à la place du texte brut de l'exception
* `Amélioration` Lorsqu'un élément de la corbeille est restauré à un emplacement différent de son chemin d'origine, l'historique des versions du fichier (ou de tous les fichiers du dossier) le suit vers le nouveau chemin ; le déplacement d'un fichier vers la corbeille enregistre l'entrée d'historique des versions à laquelle il était lié à ce moment
* `Amélioration` Une archive de dossier endommagée dans la corbeille n'interrompt plus toute la restauration : les entrées lisibles sont restaurées normalement, les entrées endommagées sont enregistrées et ignorées (les fichiers à moitié écrits sont supprimés), le nombre d'entrées restaurées / en échec est indiqué à la fin et l'élément reste dans la corbeille
* `Amélioration` La liste des fichiers de l'historique des versions affiche les mêmes icônes de type de fichier que l'explorateur ; les listes de l'historique des versions et de la corbeille se rafraîchissent désormais de manière incrémentale par différence, si bien que les changements de statistiques / compte à rebours / sélection ne redessinent plus toute la liste
* `Amélioration` Connexion par scan de QR code : la caméra utilise la mise au point continue avec mesure de lumière et le décodage utilise TRY_HARDER, ce qui facilite nettement la visée d'un code affiché à l'écran
* `Amélioration` Dialogue d'adresse du mode client : "Scanner le code QR" passe de l'icône en fin de champ au menu "Plus" de la barre de titre ; la page de scan n'a plus de bouton de lampe (le code est affiché sur un écran, qui s'éclaire lui-même)
* `Amélioration` Gestionnaires des modes client et serveur : les sections d'état et de contrôle se développent et se replient par paliers comme dans le gestionnaire du service d'accessibilité (3 niveaux chacune, le niveau choisi est mémorisé ; entièrement repliée, le titre de l'état porte l'état de connexion et les actions principales passent dans l'en-tête de contrôle) ; le bouton "Copier" des trois gestionnaires passe dans le menu "Plus" de la barre de titre sous "Copier les informations", et le bouton "Système" du gestionnaire du service d'accessibilité de même sous "Paramètres système"
* `Amélioration` APK plus léger : suppression du chemin HTML vers Markdown désactivé et des dépendances inutilisées, JUnit n'entre plus dans le DEX de production via CommonMark et Spongy Castle, et le runtime Rhino n'embarque plus la bibliothèque de terminal JLine
* `Amélioration` La vérification des mises à jour appelle désormais directement la GitHub REST API (OkHttp + Gson) pour la dernière version et sa liste d'APK, et les notes de version de secours sont rendues localement au lieu de passer par le service de rendu Markdown de GitHub
* `Amélioration` L'écriture du journal global dans un fichier (console.setGlobalLogConfig / resetGlobalLogConfig et les classes globales LogConfigurator / Level / LogManager) utilise un enregistreur de fichiers tournant intégré à la place de Log4j ; les options documentées et le sous-ensemble PatternLayout (%d / %p / %c / %C / %m / %n / %t / %r avec modificateurs de largeur) sont conservés, le reste de l'API Log4j n'est plus disponible
* `Amélioration` Le moteur Rhino ignore desormais les methodes Java dont les types de parametres n'existent pas sur le systeme en cours (comme AppCompatTextView.setTextClassifier sur Android 7.x) au lieu de faire echouer la reflexion de toute la classe ; l'element text du mode UI utilise l'AppCompatTextView actuel sur Android 7.x et la bibliotheque heritee appcompat 1.0.2 n'est plus necessaire
* `Amélioration` L'application n'embarque que l'historique des versions en chinois simplifie et en anglais ; les autres langues sont recuperees en ligne et mises en cache, et hors ligne la copie en cache de la langue courante est affichee, puis la version anglaise embarquee
* `Amélioration` Les images PNG du répertoire de ressources sont converties en WebP sans perte (icônes du lanceur exclues) et quelques ressources inutilisées sont supprimées pour réduire la taille de l'APK
* `Amélioration` Réduction des avertissements Manifest, Java et Kotlin dans les applications de conformité des plugins et les tests de l'hôte, en conservant le conditionnement des bibliothèques natives et la compatibilité Android
* `Amélioration` Format compact et références nodeRef pour accessibility.dump, avec relocalisation des éléments et rejet des références périmées
* `Amélioration` accessibility.readScreenText renvoie du texte et des positions en quantité limitée à partir de captures et de plugins OCR autorisés
* `Amélioration` Les autorisations Three Stove Agent permettent les notifications et les réglages rapides; les autres actions de touches non autorisées restent refusées
* `Amélioration` La méthode respond des objets de tâche ai.agent accepte un tableau de 1 à 8 chaînes non vides pour répondre à une demande de revue de plan de Three Stove Agent en mode plan (événements input dont kind vaut plan); les réponses texte et booléennes restent inchangées
* `Amélioration` Lorsqu'un Provider rejette ou interrompt lui-même une requête (requête invalide, violation de protocole, problème de descripteur ou de quota, etc.), le courtier de modèles de Three Stove Agent signale l'étape par une raison fermée PROVIDER_* au lieu de tout réduire à un PROVIDER_FAILED sans raison; la raison dérive uniquement de AiErrorCode et ne contient aucun texte du Provider
* `Amélioration` Le courtier de modèles de Three Stove Agent réarme l'échéance avec le timeoutMs de la requête après chaque lot accepté de résultats d'outils natifs, de sorte que chaque tour de continuation dispose du délai complet et que le tour entier est limité par le budget de tâche du plugin et le plafond de tours; une attente sans soumission de résultats expire toujours à l'échéance initiale. Les tâches natives multi-tours partageaient auparavant l'échéance de la première requête et se terminaient par MODEL_TIMEOUT au bout de 5 minutes
* `Amélioration` Three Stove Agent est activé et désactivé uniquement dans le centre de plugins; le commutateur du volet est retiré et les scripts et l'interface se connectent à la demande; les installations officielles sont activées par défaut, tout en conservant la désactivation choisie par l'utilisateur
* `Amélioration` Renommer le lecteur vidéo 3-Ember Player en 3-Amber Player; adapter le guide d'installation, la priorité M3U et les icônes transparentes à son nouvel identifiant d'application
* `Amélioration` L'installateur de paquets est transfere au plugin 3-Setup Installer: les entrees d'installation du gestionnaire de fichiers, du Centre des plugins, d'une compilation terminee et de la mise a jour automatique confient le paquet a la boite de dialogue du plugin lorsqu'il est disponible et a l'installateur systeme sinon, avec une invitation a installer le plugin; l'hote n'enregistre plus l'entree systeme "ouvrir l'APK avec AutoJs6", et les conteneurs apks / xapk / apkm / apkz necessitent le plugin
* `Amélioration` L'historique d'installation distingue les installations lancées depuis AutoJs6 de celles des scripts installer, sans changer l'utilisation de l'API de script (nécessite une version de 3-Setup Installer avec historique d'installation)
* `Amélioration` installer et les entrées d'installation de l'hôte peuvent utiliser les profils du plugin par source/préfixe de paquet; les options explicites sont prioritaires et null réinitialise installer/installReason/packageSource hérités; la suppression exige une installation réussie et conserve les fichiers partagés avec des éléments conservés ou non réussis du lot (nécessite les profils de 3-Setup Installer)
* `Amélioration` Les options d'installation installer acceptent grantAllRequestedPermissions, requestUpdateOwnership, dexopt, installReason et packageSource selon les capacités du plugin choisi; leur omission conserve le comportement existant, tandis que false/none explicite nécessite la prise en charge
* `Amélioration` Le courtier de modèles de Three Stove Agent met en fusible un composant Provider lorsqu'un appel au modèle expire alors que sa transaction Binder synchrone n'est pas encore revenue ; le fusible durait jusqu'au redémarrage d'AutoJs6 et les tâches suivantes échouaient aussitôt avec RATE_LIMITED: FUSED. Il est désormais levé automatiquement une fois cette transaction revenue et l'exécution soldée, si bien que les tâches suivantes sont de nouveau admises sans redémarrage
* `Amélioration` Les lignes de l'analyse de hiérarchie perdent les barres de niveau colorées à gauche (le retrait est conservé) et placent le repère déplier / replier à côté du texte du nœud : chevron droit pour une ligne repliée, chevron bas pour une ligne dépliée et petit cercle creux pour une ligne sans descendants, les trois dans une même teinte légèrement plus foncée
* `Amélioration` La sélection multiple des attributs utilise un fond aux couleurs du thème sans modifier la hauteur des lignes; les boutons deviennent Paramètres / Retour / Copier, et le comportement de copie est mémorisé entre les sessions: Toujours demander (par défaut), Copier le sélecteur objet ou Copier le sélecteur chaîné
* `Amélioration` Symboles agrandis pour le mode de recherche, les filtres, les expressions régulières et la navigation dans la barre de recherche de l'analyse de mise en page; le bouton des expressions régulières utilise une icône ".*" simple
* `Amélioration` Les paramètres des comptes de courrier et le Centre de plugins reconnaissent le nouveau nom et le paquet de 3-Stamp Mail
* `Amélioration` Le Centre de plugins privilégie les icônes monochromes transparentes des plugins autonomes, suit le thème clair ou sombre de l'hôte et reste compatible avec les anciennes versions
* `Amélioration` Les icônes du catalogue officiel gardent la même source, taille et apparence avant et après installation, avec prise en charge des gris neutres
* `Amélioration` Intégration de la nouvelle identité de 3-Folio EPUB en conservant les API epub et les actions du gestionnaire de fichiers
* `Amélioration` L'assistant du centre de plugins utilise les nouvelles identités de 3-Maple Image, 3-Stack Archive et 3-Hanzi CC, en réunissant la lecture et la retouche d'images dans une seule recommandation
* `Amélioration` Le centre de plugins et son assistant affichent des fonds circulaires clairs et sombres distincts, avec des couleurs neutres fixes pour la série Three et un rendu identique avant et après installation
* `Amélioration` Les entrées d'arrière-plan de fenêtre clair et sombre de la bibliothèque de couleurs par défaut sont fusionnées en une seule entrée "Couleur d'arrière-plan" proposant Clair, Sombre, Automatique et Automatique (inversé) ; les sélections faites avant la mise à jour sont conservées
* `Amélioration` Le résumé de la couleur du thème indique la bibliothèque et le nom de la couleur (le groupe actuel pour les bibliothèques intelligentes), et "Localiser la couleur du thème actuel" retrouve les couleurs des bibliothèques personnelles et la bibliothèque intelligente active ; l'ancienne disposition affiche le nom de la bibliothèque intelligente active, et choisir une couleur dans celle-ci la désactive
* `Amélioration` La recherche sur la liste des bibliothèques de couleurs couvre aussi les couleurs des bibliothèques personnelles et les groupes des bibliothèques intelligentes (par nom ou par l'une de leurs couleurs) ; les résultats indiquent leur bibliothèque et s'appliquent ou s'activent d'un appui, et l'aide de recherche précise la portée
* `Amélioration` Les indications de paramètres de l'éditeur Ace prennent en charge les méthodes surchargées, avec affichage du numéro de surcharge, changement par appui et priorité à la documentation du paramètre courant
* `Amélioration` Le menu "Sauter" de l'éditeur gagne "Revenir au curseur précédent" (anciennement l'appui long sur TAB), enregistrable dans les actions favorites
* `Amélioration` Les indications de paramètres de l'éditeur Ace changent de surcharge avec Alt+Haut / Alt+Bas sur clavier physique, les touches Haut et Bas de la barre de touches (lorsque plusieurs surcharges sont affichées; désactivable dans les paramètres) et les boutons haut / bas en haut à droite de la fenêtre d'indication
* `Amélioration` Les indications de paramètres de l'éditeur Ace réapparaissent après un appui dans la zone d'édition (pas après un défilement), et ESC masque à la fois les indications de paramètres et la documentation du symbole
* `Amélioration` Les indications de paramètres de l'éditeur Ace prennent en charge les méthodes surchargées même lorsque le LSP est désactivé ou que le service sémantique est indisponible: l'index statique contient 146 surcharges déclarées (par exemple app.startActivity), les signatures ECMAScript intégrées gagnent des surcharges pour splice / replace / JSON.stringify / Array.from et d'autres, et les indications de paramètres Python / Lua conservent toutes les signatures
* `Amélioration` La documentation au survol de l'éditeur Ace peut être défilée et sélectionnée sur écran tactile, et les suggestions intelligentes attendent les indications de paramètres asynchrones dans les fichiers Python / Lua au lieu de retomber sur la complétion
* `Amélioration` La boîte de dialogue distingue toutes les "ABI prises en charge" des "ABI installées" dans les APK actuels, splits inclus; le manifeste du plugin est prioritaire, puis l'index et enfin INFO, pour éviter que des plugins comme Ace Editor affichent uniquement l'ABI du paquet installé
* `Amélioration` La saisie native Compose UI préserve la sélection et la composition IME, évite que le clavier masque le contenu et transmet une seule fois chaque action ou fermeture de Snackbar
* `Amélioration` ComposeError fournit des messages localisés et des codes stables avec le noeud, la propriété, la cause et la pile du script; chaque moteur accepte jusqu'à 8 sessions actives, refuse les opérations après fermeture et libère rappels, références et tâches en attente; la mise à jour, désinstallation ou désactivation du plugin ferme ses sessions actives avec l'erreur correspondante
* `Amélioration` cancelable=false désactive la fermeture par retour, clic extérieur et balayage; la fermeture explicite et la fin du script libèrent le dialogue sans remplacer les pages et sessions existantes
* `Amélioration` Préchauffage de Node.js en parallèle de la préparation du moteur hôte, de TypeScript et des fichiers pour réduire l'attente; mesure des étapes jusqu'à la première sortie pour les scripts interactifs et planifiés
* `Amélioration` Dans les applications empaquetées, le runtime du plugin OpenCV et l'OpenCV privé des plugins OCR sont indépendants : images et ocr fonctionnent ensemble même avec des versions d'OpenCV différentes, et l'OCR seul n'a plus besoin d'OpenCV (nécessite des versions de plugins OCR liant OpenCV de manière privée)
* `Amélioration` L'empaquetage APK utilise la libc++_shared.so la plus récente de l'hôte et des plugins, et s'arrête si une bibliothèque native a besoin de symboles du runtime C++ manquants
* `Dépendance` Android AXML modularise
* `Dépendance` Android ARSC Editor modularise
* `Dépendance` APK Signer modularise
* `Dépendance` APK Verifier modularise
* `Dépendance` ARSCLib modularise version 1.3.8
* `Dépendance` Image Quantization modularise version 2.17.0
* `Dépendance` OpenCV modularise version 4.8.0
* `Dépendance` Ajout de juniversalchardet version 2.0.4
* `Dépendance` Ajout de AndroidX AsyncLayoutInflater version 1.1.0
* `Dépendance` Suppression de Android Annotations version 4.8.0
* `Dépendance` Suppression du module local APK Parser
* `Dépendance` Suppression de Glide KSP version 5.0.5
* `Dépendance` Suppression de ICU4J version 77.1
* `Dépendance` Suppression de Jaredrummler Android Device Names version 2.1.1
* `Dépendance` Suppression de JCIP Annotations version 1.0
* `Dépendance` Suppression de Junit Jupiter version 5.13.4
* `Dépendance` Suppression de Retrofit2 Converter Gson version 3.0.0
* `Dépendance` Suppression de Retrofit2 Kotlin Coroutines Adapter version 0.9.2
* `Dépendance` Suppression de Tiny Sign version 0.9
* `Dépendance` Suppression de la déclaration redondante de AndroidX Room KTX version 2.8.1 dans le module app
* `Dépendance` Suppression de AndroidX MultiDex version 2.0.1
* `Dépendance` Suppression de MLKit Barcode Scanning version 17.3.0
* `Dépendance` Suppression de MLKit Text Recognition Chinese version 16.0.1
* `Dépendance` Suppression de OpenCC version 1.2.2
* `Dépendance` Suppression de Pinyin4j version 2.5.1
* `Dépendance` Suppression de Jieba Analysis version 1.0.3-SNAPSHOT (modifiée)
* `Dépendance` Suppression de la bibliothèque locale Rapid OCR
* `Dépendance` Suppression de Flexmark Java HTML to Markdown version 0.64.8
* `Dépendance` Suppression de la bibliothèque locale JavaMail for Android
* `Dépendance` Suppression de GitHub API version 1.306
* `Dépendance` Suppression de Jackson DataBind version 2.13.4.2
* `Dépendance` Suppression de Commons IO version 2.8.0
* `Dépendance` Suppression de Apache Commons Lang3 version 3.18.0
* `Dépendance` Suppression de Spongy Castle Prov version 1.58.0.0
* `Dépendance` Suppression de Android dx version 1.14
* `Dépendance` Suppression de Log4j version 1.2.17
* `Dépendance` Suppression de Android Logging Log4j version 1.0.3
* `Dépendance` Suppression de la bibliothèque locale AndroidX AppCompat 1.0.2
* `Dépendance` Ajout de ZXing Android Embedded version 4.3.0
* `Dépendance` Ajout de AndroidX Test Ext JUnit version 1.3.0
* `Dépendance` Ajout de Android Hidden API Bypass version 6.1
* `Dépendance` Modularisation de Package Archive Parser
* `Dépendance` Modularisation d'Installer API
* `Dépendance` Mise à niveau de AndroidX Lifecycle 2.6.2 -> 2.9.4
* `Dépendance` Mise à niveau de AndroidX SavedState 1.2.1 -> 1.3.2
* `Dépendance` Mise à niveau de AndroidX Emoji2 1.3.0 -> 1.4.0
* `Dépendance` Mise à niveau de AndroidX Window 1.0.0 -> 1.5.0
* `Dépendance` Modularisation de Compose UI API

# v6.7.0

###### 2026/03/14

* `Fonctionnalité` Fonction Centre des plugins, prenant en charge l'installation/la desinstallation/la mise a jour des plugins et autres operations (entree: bouton du tiroir d'accueil/onglet d'accueil)
* `Fonctionnalité` Fonction Historique des versions, prenant en charge l'affichage/la restauration des versions historiques des fichiers modifiables (entree: bouton du tiroir d'accueil/menu du gestionnaire de fichiers/menu de l'editeur de code)
* `Fonctionnalité` Fonction Corbeille, prenant en charge l'affichage/la restauration des fichiers/dossiers supprimes (entree: bouton du tiroir d'accueil)
* `Fonctionnalité` Plugin Paddle OCR (PP-OCRv5), pour la reconnaissance optique de caracteres
* `Fonctionnalité` Module cvt, pour la conversion d'unites de donnees (voir Documentation du projet > [Conversion d'unites](https://docs.autojs6.com/#/cvt))
* `Fonctionnalité` Module fmt, pour le formatage des donnees (voir Documentation du projet > [Formatage](https://docs.autojs6.com/#/fmt))
* `Fonctionnalité` Module zip, pour les operations de compression et de decompression de fichiers (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Zip](https://docs.autojs6.com/#/zip))
* `Fonctionnalité` Module mediainfo, pour afficher les informations detaillees des fichiers medias (voir Documentation du projet > [Informations media](https://docs.autojs6.com/#/mediainfo))
* `Fonctionnalité` Methode cvt.bytes, pour la conversion d'unites des donnees d'octets (voir Documentation du projet > [Conversion d'unites](https://docs.autojs6.com/#/cvt))
* `Fonctionnalité` Methode fmt.bytes, pour le formatage des donnees d'octets (voir Documentation du projet > [Formatage](https://docs.autojs6.com/#/fmt))
* `Fonctionnalité` Methode s13n.bytes, pour la standardisation des donnees d'octets (voir Documentation du projet > [Standardisation](https://docs.autojs6.com/#/s13n))
* `Fonctionnalité` Methode app.isDualInstalled, pour detecter si une application duale est installee (necessite les permissions Shizuku ou Root) _[`issue #450`](http://issues.autojs6.com/450)_
* `Fonctionnalité` Methode device.getSharedDeviceId, pour obtenir un identifiant d'appareil partage unifie entre applications _[`issue #455`](http://issues.autojs6.com/455)_
* `Fonctionnalité` Methode device.setPointerLocation et autres methodes de la serie Toggleable, pour definir ou obtenir l'option systeme de position du pointeur _[`issue #381`](http://issues.autojs6.com/381)_
* `Fonctionnalité` La methode dialogs.build prend en charge les parametres d'option textAllCaps/(positive/negative/neutral)TextAllCaps, pour controler si le texte des boutons est entierement en majuscules
* `Fonctionnalité` Methode images.loadAsync, pour recuperer de maniere asynchrone des ressources d'image reseau _[`issue #327`](http://issues.autojs6.com/327)_
* `Fonctionnalité` Methode ui.getNavigationBarHeight/propriete navigationBarHeight (getter), pour obtenir la hauteur de la barre de navigation _[`issue #456`](http://issues.autojs6.com/456)_
* `Fonctionnalité` Methode ui.getVisible(Status/Navigation)BarHeight/propriete visible(Status/Navigation)BarHeight (getter), pour obtenir rapidement la hauteur visible de la barre d'etat ou de navigation _[`issue #456`](http://issues.autojs6.com/456)_
* `Fonctionnalité` Methodes http.put/del(ete)/head, pour envoyer des requetes reseau avec differentes methodes HTTP
* `Fonctionnalité` Methodes telles que http.getAsync/postAsync/requestAsync, pour envoyer des requetes reseau de maniere asynchrone _[`issue #423`](http://issues.autojs6.com/423)_
* `Fonctionnalité` L'objet body obtenu via les methodes de requete du module http ajoute des methodes telles que stream/saveToFile/close _[`issue #452`](http://issues.autojs6.com/452)_
* `Fonctionnalité` Les methodes de requete du module http prennent en charge des parametres d'option de controle du cache (cacheBody/bodyCacheThresholdBytes)
* `Fonctionnalité` Les methodes de requete du module http prennent en charge des parametres d'option non securises (isInsecure/insecure), pour ignorer les exceptions liees aux certificats _[`issue #417`](http://issues.autojs6.com/417)_
* `Fonctionnalité` Les methodes de requete du module http prennent en charge l'option options.client, pour configurer OkHttpClient.Builder (comme followRedirects, etc.) _[`issue #454`](http://issues.autojs6.com/454)_
* `Fonctionnalité` Propriete auto.state (getter) et propriete shizuku.state (getter), pour obtenir l'etat du service d'accessibilite et du service Shizuku
* `Fonctionnalité` Methodes runtime.(set/is)JavaPrimitiveWrap, pour definir ou obtenir la strategie d'encapsulation des types primitifs Java _[`issue #435`](http://issues.autojs6.com/435)_
* `Fonctionnalité` Methodes autojs.(restart/exit), pour redemarrer ou quitter l'application AutoJs6, avec prise en charge de l'execution automatique du script specifie lors du redemarrage de l'application _[`issue #460`](http://issues.autojs6.com/460)_
* `Fonctionnalité` Methode UiObject#isShifted, pour detecter les changements de position des controles _[`issue #469`](http://issues.autojs6.com/469)_
* `Fonctionnalité` Methode UiObject#snapshot, pour capturer l'etat actuel d'un controle _[`issue #469`](http://issues.autojs6.com/469)_
* `Fonctionnalité` Methode engines.myEngine().getStartTime(), pour obtenir l'horodatage de demarrage du moteur de script _[`issue #449`](http://issues.autojs6.com/449)_
* `Fonctionnalité` Methode globale crash, pour tester la page de rapport de plantage
* `Fonctionnalité` Methode globale structuredClone, pour effectuer une copie profonde d'objets JavaScript (voir [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `Fonctionnalité` Les selecteurs de la serie equivalente (UiSelector#id/text/...) et de la serie contient (UiSelector#xxxContains) prennent en charge les parametres d'expression reguliere
* `Fonctionnalité` Les parametres d'expression reguliere des selecteurs prennent en charge les drapeaux (i, m, s, u)
* `Fonctionnalité` Les expressions regulieres prennent en charge la syntaxe de lookbehind _[`issue #464`](http://issues.autojs6.com/464)_
* `Fonctionnalité` Le gestionnaire de fichiers ajoute les elements de menu "Deplacer vers" et "Copier vers", avec prise en charge de l'abandon d'operation et de l'affichage de l'etat de progression
* `Fonctionnalité` L'editeur de code ajoute l'element de menu "Clavier multifonction" - "Parametres des symboles", avec prise en charge de l'edition des symboles et de la creation/importation/exportation de symboles par niveau de profil
* `Fonctionnalité` Le tiroir d'accueil ajoute l'outil "Position du pointeur"
* `Fonctionnalité` Le tiroir d'accueil ajoute l'interrupteur "Acces a tous les fichiers"
* `Fonctionnalité` Le tiroir d'accueil ajoute l'interrupteur "Afficher l'interface depuis l'arriere-plan" (pour les appareils [Xiaomi/Vivo])
* `Fonctionnalité` La page Parametres ajoute l'option "Encapsulation des types primitifs Java" _[`issue #435`](http://issues.autojs6.com/435)_
* `Fonctionnalité` La page Parametres ajoute l'option "Moteur de planification des taches", avec prise en charge de AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `Fonctionnalité` La page Parametres ajoute l'option "Icone du lanceur d'application", avec prise en charge des icones adaptatives/icones a fond transparent _[`issue #405`](http://issues.autojs6.com/405)_
* `Fonctionnalité` La page Parametres ajoute l'option "Strategie de redemarrage", pour definir si le bouton de redemarrage du tiroir d'accueil utilise la strategie de redemarrage rapide
* `Fonctionnalité` La page Parametres ajoute l'option "Delai de demande de permission de capture d'ecran", pour eviter que la fenetre de demande de permission masque l'ecran
* `Fonctionnalité` Les raccourcis du lanceur dans la page Parametres ajoutent l'option "Plugins", pour acceder a la page Centre des plugins via un raccourci
* `Fonctionnalité` Les applications empaquetees prennent en charge le demarrage automatique au boot et ajoutent un interrupteur sur la page de configuration
* `Fonctionnalité` Les applications empaquetees prennent en charge les raccourcis d'application (page des journaux/page des parametres)
* `Fonctionnalité` La page de configuration de l'empaquetage ajoute des boutons annuler/retablir/enregistrer
* `Fonctionnalité` La page de configuration de l'empaquetage ajoute un mecanisme de profils, prenant en charge la creation/modification/suppression/importation/exportation de profils _[`issue #483`](http://issues.autojs6.com/483)_
* `Fonctionnalité` Outil Scrapers (run-scrapers.mjs), pour mettre a jour automatiquement les donnees structurees des scripts de build Gradle/les donnees communes du README/les donnees de modele README, etc.
* `Fonctionnalité` Outil Gradle Wrapper switcher (gradle-wrapper-switcher), pour changer la version majeure locale de Gradle (comme Gradle 8.x et Gradle 9.x)
* `Correction` Erreur dans la restriction du nombre et des types de parametres de la methode notice _[`issue #462`](http://issues.autojs6.com/462)_
* `Correction` Probleme ou la propriete ui.R (getter) ne pouvait pas etre utilisee correctement
* `Correction` Probleme ou les exceptions generees dans les parametres de callback de la methode ui.post pouvaient echouer silencieusement
* `Correction` Probleme ou des methodes globales telles que isJavaClass/isJavaPackage etaient invalides
* `Correction` Probleme ou le parametre timeout de la methode timers.keepAlive ne fonctionnait pas
* `Correction` Probleme ou les methodes floaty.window/rawWindow ne pouvaient pas accepter de parametres de type chaine _[`issue #467`](http://issues.autojs6.com/467)_
* `Correction` Probleme ou floaty.rawWindow affichait certains controles de maniere anormale a cause du passage d'un contexte incorrect
* `Correction` Probleme ou la methode util.isString etait invalide
* `Correction` Probleme ou util.class[Name]/getClass[Name] pouvait renvoyer des resultats incorrects
* `Correction` Probleme ou util.java.array ne pouvait pas utiliser correctement des parametres de type tels que Boolean
* `Correction` Probleme ou l'objet renvoye par threads.disposable() pouvait etre encapsule de maniere inattendue lors de l'acces aux donnees _[`issue #435`](http://issues.autojs6.com/435)_
* `Correction` Probleme ou, lors de plusieurs appels a la methode console.build, les options de style de la fenetre flottante de journaux n'etaient pas correctement reinitialisees
* `Correction` Probleme ou les options (title/content)BackgroundColor de la methode console.build ecrasaient les options de transparence ou de teinte _[`issue #458`](http://issues.autojs6.com/458)_
* `Correction` Probleme ou des methodes comme console/toast pouvaient perdre les zeros finaux lors de l'affichage de valeurs Java Double
* `Correction` Probleme ou il etait impossible d'utiliser des methodes comme console/toast pour afficher le type de donnees BigInt
* `Correction` Probleme ou la methode toast pouvait provoquer une fuite memoire en conservant une reference forte a l'instance Toast
* `Correction` Probleme ou certains objets globaux pouvaient perdre des proprietes et methodes du prototype JavaScript
* `Correction` Probleme ou l'objet this pouvait pointer incorrectement lors de l'utilisation d'expressions JavaScript comme valeurs d'attributs en syntaxe XML
* `Correction` Probleme ou le parametre d'option linkify de la methode dialogs.build ne fonctionnait pas
* `Correction` Probleme ou la methode dialogs.build pouvait detruire les donnees d'origine du parametre objet
* `Correction` Calcul incorrect de la frequence d'images interne dans la methode setMaxFps du controle canvas
* `Correction` Calcul incorrect de la largeur lors de la concatenation verticale avec la methode images.concat
* `Correction` Probleme ou l'annulation de l'autorisation de la methode images.requestScreenCapture pouvait provoquer un plantage de l'application
* `Correction` Probleme ou les captures obtenues via la methode images.captureScreen pouvaient avoir une orientation incorrecte ou des bordures noires _[`issue #337`](http://issues.autojs6.com/337)_
* `Correction` Probleme ou la valeur 1 etait interpretee par erreur comme un pourcentage lors de l'utilisation de parametres de region dans certaines methodes de images
* `Correction` Probleme ou la fonction du marqueur oneShot echouait lorsqu'une exception survenait dans certaines methodes liees a images _[`issue #372`](http://issues.autojs6.com/372)_
* `Correction` Probleme ou certaines methodes liees a images pouvaient provoquer des fuites memoire _[`issue #372`](http://issues.autojs6.com/372)_
* `Correction` Probleme ou images.save et la methode ImageWrapper#saveTo ne pouvaient pas ecrire correctement les fichiers lorsque le chemin fourni n'existait pas
* `Correction` Probleme ou la methode ImageWrapper#recycle ne recyclait pas correctement les ressources d'image
* `Correction` Probleme ou certaines methodes surchargees de ocr pouvaient ne pas fonctionner correctement
* `Correction` Probleme ou les resultats obtenus via la methode ocr.detect pouvaient ne pas correspondre a ocr.mode _[`issue #468`](http://issues.autojs6.com/468)_
* `Correction` Probleme ou les evenements du service d'accessibilite enregistres avec auto.registerEvent pouvaient etre supprimes par erreur par d'autres scripts _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `Correction` Probleme ou la methode UiObject#child pouvait lancer ArrayIndexOutOfBoundsException sur Android 10 _[`issue #416`](http://issues.autojs6.com/416)_
* `Correction` Probleme ou l'appel des methodes de modules integres pouvait conduire a une incoherence de contexte Rhino
* `Correction` Probleme ou, apres la creation d'un grand nombre de fenetres flottantes dans un script, des fenetres residuelles pouvaient rester apres la sortie du script
* `Correction` Probleme ou les parametres de configuration de project.json n'etaient pas correctement analyses lors de l'execution d'un projet
* `Correction` Probleme ou le parametre excludedDirs de project.json pouvait provoquer un echec d'analyse du fichier de configuration lors de l'empaquetage d'un projet _[`issue #428`](http://issues.autojs6.com/428)_
* `Correction` Probleme ou la fonction d'empaquetage pouvait ne pas fonctionner correctement sur Android 7.x
* `Correction` Probleme ou les noms de cles d'origine pouvaient etre modifies lors de l'enregistrement du fichier de configuration d'un projet de script
* `Correction` Probleme ou de grands numeros de version de build ou d'heure de build dans le fichier de configuration d'un projet de script pouvaient provoquer un plantage de l'application
* `Correction` Probleme ou la recuperation ou la reconstruction frequente de ImageReader pouvait provoquer un plantage de l'application a cause de l'absence temporaire d'image disponible dans le tampon
* `Correction` Probleme ou l'observateur d'evenements d'entree InputEventObserver pouvait provoquer un ralentissement notable au demarrage de l'application
* `Correction` Probleme ou le processus de service utilisateur Shizuku ne se terminait pas correctement, provoquant une accumulation de processus _[`issue #474`](http://issues.autojs6.com/474)_
* `Correction` Probleme ou les applications empaquetees ne pouvaient pas utiliser correctement les fonctions Paddle OCR et Rapid OCR
* `Correction` Probleme ou la valeur de la cle key dans le fichier de configuration project.json des applications empaquetees pouvait etre modifiee de maniere inattendue
* `Correction` Probleme ou l'icone des cases a cocher pouvait clignoter lors du defilement de la liste "Permissions requises" dans la page de configuration de l'empaquetage
* `Correction` Probleme ou, dans la page Historique des versions, certains systemes affichaient des donnees statistiques incompletes en raison de differences de police
* `Correction` Probleme ou MLKit Google OCR ne pouvait pas s'initialiser correctement sur certains appareils (correction tentative) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `Correction` Probleme ou, sur certains appareils, les boutons fonctionnels du gestionnaire de fichiers ne declenchaient pas correctement les evenements de clic (correction tentative) _[`issue #465`](http://issues.autojs6.com/465)_
* `Correction` Probleme ou les lignes vides de l'editeur de code affichaient des caracteres en forme de carre sur certains appareils (correction tentative)
* `Correction` Probleme ou le clavier virtuel pouvait masquer le contenu texte dans l'editeur de code
* `Correction` Probleme ou l'editeur de code autorisait encore la modification du contenu en mode lecture seule
* `Correction` Probleme ou toucher la zone du titre ou certains elements de menu en mode lecture seule provoquait un plantage de l'application dans l'editeur de code
* `Correction` Probleme ou le chargement de gros fichiers dans l'editeur de code pouvait provoquer un plantage de l'application (correction tentative)
* `Correction` Initialisation anormale de l'etat des boutons fonctionnels lorsque l'editeur de code ouvrait automatiquement un nouveau fichier
* `Correction` Probleme ou mettre l'application en arriere-plan pendant l'edition d'un fichier dans l'editeur de code pouvait provoquer un plantage
* `Correction` Probleme ou, sur Android 15+, le bouton fx de l'editeur de code n'affichait pas le panneau rapide des fonctions de module (correction tentative)
* `Correction` Probleme ou ErrorDialogActivity pouvait ne pas demarrer correctement ou disparaitre automatiquement apres un court instant _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `Correction` Types de parametres acceptes incorrects dans le constructeur Canvas _[`issue #402`](http://issues.autojs6.com/402)_
* `Correction` Probleme ou la fonction de copie des informations detaillees ne fonctionnait pas dans la page de rapport de plantage
* `Correction` Probleme ou la logique personnalisee du bouton retour cessait de fonctionner sur Android 16+, provoquant un comportement anormal du retour
* `Correction` Probleme ou l'application pouvait planter lors de l'appui sur le bouton retour ou du changement d'onglet apres l'affichage des resultats de recherche du gestionnaire de fichiers
* `Correction` Probleme ou l'interface ne se rafraichissait pas automatiquement apres la suppression de dossiers de projet dans le gestionnaire de fichiers
* `Correction` Probleme ou l'application pouvait planter lorsque les elements de la liste du panneau des taches changeaient frequemment
* `Correction` Probleme ou la couleur du premier plan de la barre de navigation pouvait se comporter anormalement avec le mode nuit lors de l'utilisation de la navigation gestuelle "trois boutons"
* `Correction` Probleme ou la boite de dialogue d'informations de type de fichier APK pouvait ne pas obtenir le nom de l'application ni les informations SDK
* `Correction` Probleme ou, en mode client, une deconnexion suivie d'une reconnexion rapide apres une connexion pouvait provoquer un timeout de handshake et rendre la reconnexion difficile
* `Correction` Probleme ou, en mode client, des operations telles que la rotation de l'ecran ou le changement de langue, recreant l'Activity, faisaient toujours reconstruire la connexion par AutoJs6
* `Correction` Probleme ou, en mode serveur, des operations telles que la rotation de l'ecran ou le changement de langue, recreant l'Activity, empechaient la console VSCode d'afficher les journaux _[`issue #385`](http://issues.autojs6.com/385)_
* `Correction` Probleme ou, lors de la connexion au plugin VSCode de plusieurs manieres en meme temps, la quantite de journaux affiches pouvait etre multipliee
* `Correction` Probleme ou la generation de code dans la page d'analyse de disposition pouvait echouer avec les controles de collection (correction tentative) _[`issue #328`](http://issues.autojs6.com/328)_
* `Correction` Probleme ou l'interrupteur "Utiliser la touche 'Volume bas' pour controler l'enregistrement" ne fonctionnait pas dans la page Parametres _[`issue #480`](http://issues.autojs6.com/480)_
* `Correction` Probleme ou les valeurs de coordonnees dans les fichiers de code generes par l'enregistrement de scripts pouvaient ne pas correspondre aux pixels reels de l'ecran _[`issue #480`](http://issues.autojs6.com/480)_
* `Correction` Probleme ou la premiere action dans les fichiers de code generes par l'enregistrement de scripts (comme toucher ou glisser) etait toujours ignoree
* `Correction` Probleme ou l'execution d'operations sur des fichiers en arriere-plan depuis la boite de dialogue flottante "Executer le script" pouvait provoquer un plantage de l'application
* `Correction` Probleme ou l'etat du bouton flottant pouvait etre reinitialise lors du retour a la page d'accueil apres la fin du cycle de vie de l'Activity d'accueil
* `Correction` Probleme ou l'interrupteur "Afficher par-dessus les autres applications" sur les appareils Xiaomi pouvait ouvrir une mauvaise page de parametres
* `Correction` Probleme ou, apres l'abandon du telechargement de mise a jour, les paquets reseau continuaient quand meme a etre telecharges en arriere-plan (correction tentative)
* `Correction` Probleme ou la tache Rebuild Project ne pouvait pas se terminer correctement lorsque l'option isCleanup[Paddle/Rapid]Ocr etait activee dans les outils de build
* `Amélioration` Fonctionnalites liees a Paddle OCR (PP-OCRv3) pluginise _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `Amélioration` Compatibilite amelioree de la methode runtime.loadJar (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `Amélioration` Compatibilite amelioree dans une certaine mesure de la methode ui.getStatusBarHeight/propriete statusBarHeight (getter)
* `Amélioration` Les methodes comme console.log prennent en charge l'affichage d'informations detaillees lors de l'impression d'objets globaux (images, app, ocr, etc.)
* `Amélioration` La methode dialogs.build prend en charge les parametres d'option natifs de MaterialDialog.Builder
* `Amélioration` La methode dialogs.build prend en charge la correspondance souple de chaines pour les parametres d'option animation/linkify
* `Amélioration` Stabilite et coherence ameliorees lors de l'obtention de l'orientation d'ecran et des donnees largeur/hauteur via device.rotation/rotation/width/height
* `Amélioration` Efficacite d'execution et compatibilite ameliorees des methodes telles que app.launchDual/startDualActivity
* `Amélioration` android.graphics.Paint#setColor prend correctement en charge l'analyse des parametres de couleur tels que ColorInt/ColorHex/ColorName
* `Amélioration` Les messages d'exception des types d'arguments des methodes liees aux modules integres ajoutent des informations de resume de type
* `Amélioration` Les modules integres (dayjs/cheerio/dayjs/i18n) n'utilisent plus le mode d'execution de script et adoptent un chargement paresseux global
* `Amélioration` Lors de l'impression de donnees enormes via les methodes liees a la console, une partie du contenu est correctement omise selon des limites superieures afin de reduire le temps d'impression
* `Amélioration` La valeur initiale du compte a rebours de la fenetre flottante de console passe de 6 a 9 secondes
* `Amélioration` L'implementation interne de la fenetre flottante de console adopte un traitement sans verrou et en file d'attente afin d'ameliorer l'efficacite et le taux de reussite du parametrage
* `Amélioration` Les API liees au comportement de la couleur de fond de la fenetre flottante de console (transparence/teinte/couleur de base) sont davantage conformes aux normes de conception Android _[`issue #458`](http://issues.autojs6.com/458)_
* `Amélioration` Optimisation du moment de repli du menu lors du toucher des elements apres le deploiement du bouton flottant dans le gestionnaire de fichiers
* `Amélioration` Le gestionnaire de fichiers/le panneau des taches prennent en charge l'affichage des statistiques du nombre de fichiers/taches
* `Amélioration` Lorsque l'enregistrement echoue dans l'editeur de code, le fichier est automatiquement enregistre en brouillon et prend en charge Enregistrer sous comme nouveau fichier
* `Amélioration` Fluidite amelioree dans une certaine mesure lors du chargement de gros fichiers dans l'editeur de code
* `Amélioration` La fonction "Rechercher/Remplacer" de l'editeur de code prend en charge la persistance d'etat et l'affichage en temps reel du nombre de correspondances
* `Amélioration` Lorsque l'editeur de code invite a enregistrer, il s'assure desormais que l'enregistrement reussit avant de quitter afin de reduire le taux d'echec
* `Amélioration` L'etat du bouton Enregistrer de l'editeur de code correspond mieux a la logique subjective de l'utilisateur
* `Amélioration` L'editeur de code prend en charge l'appui long sur des touches telles que "-", "=", "(", "[", "{", "<"
* `Amélioration` La boite de dialogue du processus d'empaquetage ajoute un bouton "Interrompre"
* `Amélioration` Le mode d'affichage de la boite de dialogue du processus d'empaquetage est ameliore et des statistiques de duree sont ajoutees
* `Amélioration` Lors de l'installation d'applications empaquetees avec une nouvelle configuration, les configurations associees de l'ancienne application seront ecrasees
* `Amélioration` La page de configuration de l'empaquetage coche par defaut les permissions necessaires (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) _[`issue #397`](http://issues.autojs6.com/397)_
* `Amélioration` La page de configuration de l'empaquetage prend en charge la lecture et la configuration de davantage d'options (afficher les journaux au lancement/afficher l'ecran de demarrage au lancement/texte du slogan de l'ecran de demarrage/...)
* `Amélioration` La page de parametres des applications empaquetees ajoute un interrupteur de service au premier plan _[`issue #406`](http://issues.autojs6.com/406)_
* `Amélioration` L'enregistrement du fichier de configuration de projet de script ajoute une detection des conflits de noms de cles afin d'eviter toute ambiguite
* `Amélioration` La page de rapport de plantage prend en charge le pincement a deux doigts pour ajuster la taille de police et ajoute des boutons de fonctions courantes
* `Amélioration` Les icones du lanceur d'application prennent en charge les fonctionnalites d'icones adaptatives _[`issue #405`](http://issues.autojs6.com/405)_
* `Amélioration` Lorsque l'analyse des signatures generiques echoue dans le moteur Rhino, un retour aux types de reflection bruts est effectue afin d'ameliorer la disponibilite des methodes reflexives sur les anciennes versions d'Android
* `Amélioration` Les elements de type interrupteur du tiroir d'accueil ajoutent des separateurs, prennent en charge l'appui sur la zone du titre pour afficher une boite de dialogue detaillee et, selon le besoin, un acces rapide aux parametres systeme
* `Amélioration` Au lancement ou au redemarrage de AutoJs6, l'appui sur la zone du titre de "Mode client" dans le tiroir d'accueil permet d'abandonner une tentative de connexion en cours
* `Amélioration` Le mode client prend en charge l'etablissement de connexions via des adresses IPv6 et des noms de domaine
* `Amélioration` Le mode client prend en charge des invites de detection pour les adresses IPv4 a usage special (loopback/broadcast/multicast/reservee/...)
* `Amélioration` Le mode client prend en charge l'affichage et la gestion de l'etat de connexion (corriger l'adresse/interrompre la connexion)
* `Amélioration` Le mode serveur prend en charge l'affichage du nombre de clients connectes
* `Amélioration` La lecture de la permission de notification prend en charge un mecanisme de reconnexion automatique ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) afin d'ameliorer la stabilite des fonctions liees a l'ecoute des notifications
* `Amélioration` Le bouton flottant ameliore la securite du lancement d'Activity depuis l'arriere-plan afin d'eviter les plantages de l'application
* `Amélioration` La boite de dialogue "Plus" du bouton flottant utilise un chargement asynchrone des donnees pour un affichage plus fluide
* `Amélioration` La boite de dialogue "Executer le script" du bouton flottant ajoute l'element de menu "Accueil"
* `Amélioration` La boite de dialogue "Executer le script" du bouton flottant prend en charge la minimisation et la restauration d'etat, et fait de son mieux pour maintenir la fenetre residente ou la restaurer automatiquement
* `Amélioration` La permission Shizuku est prise en charge pour l'enregistrement et la lecture de scripts (la fluidite de lecture peut etre quelque peu affectee) _[`issue #320`](http://issues.autojs6.com/320)_
* `Amélioration` La page des parametres de couleur du theme utilise une methode de positionnement rapide pour localiser les couleurs du theme et ameliorer l'efficacite
* `Amélioration` Remplacement du [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager) obsolete par [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) et [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
* `Amélioration` Les scripts de build Gradle ameliorent l'efficacite d'extraction des fichiers au format 7z
* `Amélioration` Les scripts de build Gradle prennent en charge l'obtention de la version detaillee de l'IDE Android Studio (par exemple, "2025.1.4.7")
* `Amélioration` Les scripts de build Gradle prennent en charge la generation automatique des donnees requises par la classe VersionCodesList afin de reduire la latence de demarrage des scripts
* `Amélioration` Utilisation de [Version Catalogs](https://developer.android.com/build/migrate-to-catalogs) pour gerer de maniere centralisee les versions des dependances et plugins Gradle
* `Amélioration` Scripts de build Gradle modularises, avec migration de la logique de build partagee vers build-logic et abstraction sous forme de plugins de convention
* `Amélioration` Utilisation des plugins de convention Gradle pour simplifier la logique de chargement des bibliotheques AAR locales
* `Amélioration` Suppression de l'option Gradle "android.enableJetifier=true" afin de s'adapter a Gradle 9
* `Amélioration` Utilisation de [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) a la place de sourceCompatibility/targetCompatibility pour reduire les differences d'environnement de build
* `Dépendance` Expandable Layout modularise version 1.6.0
* `Dépendance` Expandable RecyclerView modularise version 3.0.0-RC1
* `Dépendance` Recyclerview Flexible Divider modularise version 1.4.0
* `Dépendance` Root Shell localise version 1.6
* `Dépendance` Ajout de AndroidX Activity (KTX) version 1.12.2
* `Dépendance` Ajout de AndroidX Core (KTX) version 1.15.0
* `Dépendance` Ajout de AndroidX Work Runtime version 2.11.0
* `Dépendance` Ajout de AndroidX Interpolator version 1.0.0
* `Dépendance` Ajout de Room RxJava2 version 2.8.1
* `Dépendance` Mise a niveau de Android SDK version 35 -> 36
* `Dépendance` Mise a niveau de Gradle 8 version 8.14 -> 8.14.4
* `Dépendance` Mise a niveau de Gradle 9 version 8.14 -> 9.4.0
* `Dépendance` Mise a niveau de Rhino version 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT
* `Dépendance` Mise a niveau de Desugar JDK Libs version 2.0.4 -> 2.1.5
* `Dépendance` Mise a niveau de Android Material version 1.12.0 -> 1.13.0
* `Dépendance` Mise a niveau de AndroidX AppCompat version 1.7.0 -> 1.7.1
* `Dépendance` Mise a niveau de AndroidX Room version 2.7.1 -> 2.7.2
* `Dépendance` Mise a niveau de AndroidX WebKit version 1.13.0 -> 1.14.0
* `Dépendance` Mise a niveau de Material Progressbar version 1.4.2 -> 1.6.1
* `Dépendance` Mise a niveau de Apache Commons version 3.16.0 -> 3.18.0
* `Dépendance` Mise a niveau de Retrofit2 Retrofit version 2.11.0 -> 2.12.0
* `Dépendance` Mise a niveau de Retrofit2 Converter Gson version 2.11.0 -> 2.12.0
* `Dépendance` Mise a niveau de Retrofit2 RxJava2 version 2.11.0 -> 2.12.0
* `Dépendance` Mise a niveau de Joda Time version 2.12.7 -> 2.14.0
* `Dépendance` Mise a niveau de Gson version 2.11.0 -> 2.13.1
* `Dépendance` Mise a niveau de ARSCLib version 1.3.1 -> 1.3.5
* `Dépendance` Mise a niveau de Jsoup version 1.19.1 -> 1.20.1
* `Dépendance` Mise a niveau de Junit Jupiter version 5.10.3 -> 5.13.0
* `Dépendance` Mise a niveau de OpenCC version 1.2.0 -> 1.2.2
* `Dépendance` Suppression de Android Support Annotation v7 version 28.0.0
* `Dépendance` Suppression de Android Support AppCompat v7 version 28.0.0
* `Dépendance` Suppression de Android Support RecyclerView v7 version 28.0.0

# v6.6.4

###### 2025/05/31

* `Astuce` Changement d'API : ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `Fonctionnalité` Méthodes util.dpToPx/spToPx/pxToDp/pxToSp pour la conversion d'unités en pixels
* `Correction` Les sous-titres peuvent s'afficher de manière incomplète lors du passage en mode paysage
* `Correction` Le contenu de certaines pages peut être masqué par la barre de navigation latérale en mode paysage
* `Correction` Zone de coloration de fond de la barre d'état incomplète sur certaines pages sous Android 15 _[`issue #398`](http://issues.autojs6.com/398)_
* `Correction` L'éditeur de code pouvait écrire des fichiers avec un encodage peu fiable, entraînant des erreurs de décodage (tentative de correction)
* `Amélioration` Adaptabilité de la mise en page améliorée pour les pages Application et Développeur et suppression des catégories de mise en page inutiles
* `Amélioration` Dans README.md, section Build, ajout de plusieurs méthodes pour faciliter la localisation de la page de configuration de la cible _[`issue #404`](http://issues.autojs6.com/404)_
* `Dépendance` Ajout de AndroidX ConstraintLayout version 2.2.1

# v6.6.3

###### 2025/05/27

* `Fonctionnalité` Fonction d'historique des versions : consulter le journal des changements multilingue et les statistiques
* `Fonctionnalité` Méthode timers.keepAlive (désormais globale) pour maintenir le script actif
* `Fonctionnalité` Méthodes d'écoute d'événements telles que engines.on('start/stop/error', callback) pour les événements globaux du moteur
* `Fonctionnalité` Méthode images.detectMultiColors pour la vérification de couleurs multipoints _[`issue #374`](http://issues.autojs6.com/374)_
* `Fonctionnalité` Méthodes images.matchFeatures/detectAndComputeFeatures : recherche d'image en pleine résolution (Réf. [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `Fonctionnalité` Méthode images.compressToBytes pour compresser une image et générer un tableau d'octets
* `Fonctionnalité` Méthode images.downsample pour l'échantillonnage de pixels et la création d'un nouveau ImageWrapper
* `Fonctionnalité` Méthode ui.keepScreenOn pour garder l'écran allumé lorsque la page UI a le focus
* `Fonctionnalité` Propriété ui.root (getter) pour obtenir le nœud « conteneur racine du contenu de fenêtre » du layout UI
* `Fonctionnalité` L'élément webview prend en charge les layouts de pages Web basés sur JsBridge (Réf. [Auto.js Pro](https://g.pro.autojs.org/)) [voir Code d'exemple > Layout > HTML interactif / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `Fonctionnalité` L'inspecteur de layout permet de masquer des contrôles (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `Correction` Le contenu de la documentation en ligne dans l'onglet Docs et l'activité Docs pouvait être recouvert par la barre de navigation système
* `Correction` Sur certaines pages, cliquer sur les boutons de la Toolbar pouvait déclencher par erreur l'événement de clic sur le titre
* `Correction` Sur certains appareils, les lignes vides de l'editeur de code affichaient des caracteres en forme de carre; correction tentative
* `Correction` La boîte de dialogue du sélecteur de couleurs dans les paramètres de couleur du thème pouvait se superposer indéfiniment
* `Correction` La touche volume + ne stoppait pas tous les scripts lorsque le service d'accessibilité était désactivé
* `Correction` Chevauchement du clavier IME lors de l'édition d'un contenu de diffusion personnalisé dans la page des tâches planifiées
* `Correction` Les contrôles dans les éléments webview ne pouvaient pas activer correctement le clavier virtuel
* `Correction` La boîte de dialogue d'information APK pouvait ne pas récupérer le nom de l'application ni les informations SDK
* `Correction` Le code d'exemple du gestionnaire de fichiers pouvait ne pas charger automatiquement le contenu des sous-dossiers à l'entrée d'un répertoire de projet
* `Correction` Le contenu supérieur du mode UI sous Android 15 était recouvert par la barre d'état
* `Correction` La couleur d'arrière-plan de la barre d'état sur certaines pages Android 15 ne suivait pas dynamiquement la couleur du thème
* `Correction` Le module dialogs ne pouvait pas utiliser la propriété customView _[`issue #364`](http://issues.autojs6.com/364)_
* `Correction` Le paramètre d'expression de dialogs.input pouvait ne pas retourner le résultat d'exécution
* `Correction` L'utilisation de JavaAdapter provoquait un débordement de pile ClassLoader _[`issue #376`](http://issues.autojs6.com/376)_
* `Correction` console.setContentTextColor entraînait la perte de la couleur de texte par défaut _[`issue #346`](http://issues.autojs6.com/346)_
* `Correction` console.setContentBackgroundColor n'acceptait pas les noms de couleur _[`issue #384`](http://issues.autojs6.com/384)_
* `Correction` images.compress ajuste désormais la qualité d'encodage au lieu de réduire les pixels
* `Correction` La méthode images.resize ne fonctionnait pas correctement
* `Correction` engines.all pouvait déclencher ConcurrentModificationException _[`issue #394`](http://issues.autojs6.com/394)_
* `Correction` Formats de date incorrects dans certaines langues du README.md
* `Correction` La compilation Gradle pouvait échouer à cause d'une longueur invalide d'archive de bibliothèque _[`issue #389`](http://issues.autojs6.com/389)_
* `Amélioration` Ajout de séparateurs dégradés dans le menu de l'inspecteur de layout pour regrouper les fonctions
* `Amélioration` project.json prend désormais en charge l'option permissions pour les projets de script (par [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `Amélioration` Lors de l'empaquetage d'un fichier unique, les permissions déclarées par l'application installée sont lues et cochées automatiquement _[`issue #362`](http://issues.autojs6.com/362)_
* `Amélioration` Portée d'adaptation de la couleur de thème élargie et prise en charge de plus de types de widgets
* `Amélioration` Largeur du tiroir d'accueil adaptée aux écrans paysage ou ultra-larges
* `Amélioration` Ajout de layouts horizontaux et petit écran pour les pages À propos de l'application et Développeur
* `Amélioration` Les boîtes de dialogue de la page Paramètres proposent l'option « Utiliser la valeur par défaut »
* `Amélioration` Le bouton flottant du gestionnaire de fichiers se masque automatiquement lorsqu'on touche en dehors
* `Amélioration` Le formateur de code prend désormais en charge les opérateurs `??`, `?.`, `??=`
* `Amélioration` L'éditeur de code prend en charge la lecture et l'écriture de fichiers aux encodages GB18030 / UTF-16 (LE/BE) / Shift_JIS, etc
* `Amélioration` L'éditeur de code prend en charge l'affichage des informations détaillées du fichier (chemin/encodage/retour à la ligne/nombre total d'octets et de caractères, etc.) _[`issue #395`](http://issues.autojs6.com/395)_
* `Amélioration` Ajout de messages d'erreur pour les actions d'intent (éditer / voir / installer / envoyer / lire, etc.)
* `Amélioration` L'attribut url de webview prend en charge les chemins relatifs
* `Amélioration` Le paramètre path de ImageWrapper#saveTo prend en charge les chemins relatifs
* `Amélioration` images.save permet la compression de fichiers PNG lors de l'utilisation du paramètre quality _[`issue #367`](http://issues.autojs6.com/367)_
* `Amélioration` Possibilité de vider les enregistrements de mises à jour ignorées et les adresses de connexion en mode client
* `Amélioration` Les informations de mise à jour de version sont affichées dans la langue de l'interface
* `Amélioration` Le chargement asynchrone améliore la fluidité de défilement du gestionnaire de fichiers
* `Amélioration` Contenu et format des messages d'exception de script améliorés dans la console
* `Amélioration` Le code d'exemple permet de réinitialiser un dossier à son contenu initial
* `Amélioration` Efficacité accrue lors de la vérification des informations de signature APK
* `Amélioration` Optimisation de l'efficacité d'affichage et de présentation des informations pour les fichiers APK/médias
* `Amélioration` Le script de build Gradle améliore sa capacité d'adaptation aux nouvelles versions _[`discussion #369`](http://discussions.autojs6.com/369)_
* `Dépendance` Inclus Material Dialogs version 0.9.6.0 (modularise)
* `Dépendance` Inclus Material Date Time Picker version 4.2.3 (modularise)
* `Dépendance` Inclus libimagequant version 2.17.0 (localisé)
* `Dépendance` Inclus libpng version 1.6.49 (localisé)
* `Dépendance` Ajouté ICU4J version 77.1
* `Dépendance` Ajouté Jsoup version 1.19.1
* `Dépendance` Ajouté Material Progressbar version 1.4.2
* `Dépendance` Ajouté Flexmark Java HTML to Markdown version 0.64.8
* `Dépendance` Mise à jour Gradle 8.14-rc-1 -> 8.14
* `Dépendance` Mise à jour AndroidX Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `Fonctionnalité` Méthodes telles que ui.statusBarAppearanceLight, statusBarAppearanceLightBy et navigationBarColor, etc
* `Fonctionnalité` Attribut ui.statusBarHeight (getter) servant à obtenir la hauteur de la barre d'état _[`issue #357`](http://issues.autojs6.com/357)_
* `Fonctionnalité` Méthode images.flip pour retourner une image _[`issue #349`](http://issues.autojs6.com/349)_
* `Fonctionnalité` Ajout de l'option « extension de fichier » dans la page des paramètres
* `Fonctionnalité` La page de configuration du thème prend désormais en charge une nouvelle mise en page (regroupement, positionnement, recherche, historique, amélioration de la palette de couleurs, etc.)
* `Correction` Problème où la couleur de fond de la barre d'état sur Android 15 ne correspond pas à celle du thème
* `Correction` Problème où la méthode plugins.load ne charge pas correctement les plugins _[`issue #290`](http://issues.autojs6.com/290)_
* `Correction` Problème où la bibliothèque dx ne fonctionne pas correctement sur Android 7.x _[`issue #293`](http://issues.autojs6.com/293)_
* `Correction` Problème où ScriptRuntime peut présenter un état de synchronisation anormal lors de l'utilisation de require pour importer des modules intégrés (solution provisoire) _[`issue #298`](http://issues.autojs6.com/298)_
* `Correction` Problème où le module notice ne dispose pas des méthodes d'extension telles que getBuilder _[`issue #301`](http://issues.autojs6.com/301)_
* `Correction` Problème où les méthodes shizuku/shell n'acceptent pas les paramètres de type chaîne _[`issue #310`](http://issues.autojs6.com/310)_
* `Correction` Problème où la méthode colors.pixel n'accepte pas les paramètres d'images à canal unique _[`issue #350`](http://issues.autojs6.com/350)_
* `Correction` Problème où les méthodes engines.execScript/execScriptFile utilisent un répertoire de travail par défaut incorrect lors de l'exécution des scripts _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `Correction` Problème où floaty.window/floaty.rawWindow ne peut pas être exécuté dans un thread secondaire
* `Correction` Problème où floaty.getClip risque de ne pas récupérer correctement le contenu du presse-papiers _[`issue #341`](http://issues.autojs6.com/341)_
* `Correction` Problème où ui.inflate renvoie un résultat auquel il manque les méthodes de prototype telles que attr, on et click
* `Correction` Problème où le contexte de portée n'est pas correctement lié lors de l'utilisation de la syntaxe XML pour utiliser une expression JavaScript comme valeur d'attribut _[`issue #319`](http://issues.autojs6.com/319)_
* `Correction` Problème où certaines exceptions générées lors de l'appel de méthodes ne sont pas capturées par un bloc try..catch _[`issue #345`](http://issues.autojs6.com/345)_
* `Correction` Problème où la génération de code dans la page d'analyse de la mise en page peut provoquer un crash de l'application _[`issue #288`](http://issues.autojs6.com/288)_
* `Correction` Problème où les applications packagées ne peuvent pas utiliser correctement le module shizuku _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `Correction` Problème dans l'éditeur de code où le passage à la fin d'une ligne peut positionner le curseur au début de la ligne suivante
* `Correction` Problème où des clics rapides consécutifs sur des éléments de type dialogue dans la page des paramètres peuvent provoquer un crash de l'application
* `Amélioration` Optimisation de la taille du fichier APK pour le modèle d'application packagée
* `Amélioration` L'application (et les applications packagées) prend désormais en charge davantage de permissions _[`issue #338`](http://issues.autojs6.com/338)_
* `Amélioration` La page de configuration de packaging prend maintenant en charge les options de bibliothèque Pinyin
* `Amélioration` Optimisation du fond de la barre d'état et de la couleur du texte dans la page principale des applications packagées
* `Amélioration` Ajout d'interrupteurs pour les permissions spéciales (accès à tous les fichiers et envoi de notifications) dans la page des paramètres des applications packagées _[`issue #354`](http://issues.autojs6.com/354)_
* `Amélioration` Les textes et icônes des contrôles s'ajustent automatiquement selon la luminosité du thème
* `Amélioration` Amélioration de l'expérience visuelle lorsque le contraste entre la couleur du contrôle et le fond est faible
* `Amélioration` Amélioration de la compatibilité du contrôle d'entrée HEX dans la palette de couleurs lors du collage de valeurs depuis le presse-papiers
* `Amélioration` La barre de navigation de l'application est désormais configurée pour être transparente ou semi-transparente afin d'améliorer l'expérience visuelle
* `Amélioration` Le mode UI par défaut de la barre d'état et de la barre de navigation est défini sur la couleur `md_grey_50` en mode clair
* `Amélioration` L'interrupteur du service d'accessibilité dans le tiroir de la page d'accueil se synchronise désormais avec le code du script
* `Amélioration` La page de documentation de l'accueil prend désormais en charge des boutons de recherche bidirectionnels
* `Amélioration` L'onglet « Fichiers » de la page d'accueil permet de changer la visibilité du bouton flottant par un appui long
* `Amélioration` Le titre de l'éditeur de code supporte désormais l'ajustement automatique de la taille de la police
* `Amélioration` La visibilité du bouton flottant dans la page des journaux est liée aux actions de défilement de la liste
* `Amélioration` Le fichier de configuration project.json du projet de script prend désormais en charge davantage d'options de packaging _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `Amélioration` Le fichier project.json prend désormais en charge une correspondance plus souple des noms d'option ainsi qu'une compatibilité avec les alias
* `Amélioration` La boîte de dialogue d'information sur le type de fichier APK inclut désormais la taille du fichier et des informations sur le schéma de signature
* `Amélioration` La boîte de dialogue d'information sur le type de fichier APK prend désormais en charge des écouteurs de clic pour copier le texte et accéder aux détails de l'application
* `Amélioration` Tentative de restauration des packages préfixés par com.stardust afin d'améliorer la compatibilité du code _[`issue #290`](http://issues.autojs6.com/290)_
* `Amélioration` Les méthodes floaty.window/floaty.rawWindow supportent désormais l'exécution sur le thread principal ainsi que sur des threads secondaires
* `Amélioration` La méthode globale getClip utilise désormais floaty.getClip au besoin pour améliorer la compatibilité
* `Amélioration` Amélioration de la compatibilité de files.path et des méthodes associées lorsqu'une valeur nulle est fournie pour le chemin
* `Amélioration` Synchronisation avec la dernière version officielle du moteur Rhino et adaptations nécessaires du code
* `Amélioration` Amélioration du README.md pour mieux documenter la construction et l'exécution du projet _[`issue #344`](http://issues.autojs6.com/344)_
* `Dépendance` Ajout d'Eclipse Paho Client Mqtt V3 version 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `Dépendance` Mise à jour de la version de Android SDK de 34 à 35
* `Dépendance` Mise à jour de Gradle de 8.12 à 8.14-rc-1
* `Dépendance` Mise à jour de Rhino de 1.8.0-SNAPSHOT à 1.8.1-SNAPSHOT
* `Dépendance` Mise à jour d'AndroidX Recyclerview de 1.3.2 à 1.4.0
* `Dépendance` Mise à jour d'AndroidX Room de 2.6.1 à 2.7.0
* `Dépendance` Mise à jour d'AndroidX WebKit de 1.12.1 à 1.13.0
* `Dépendance` Mise à jour de Pinyin4j de 2.5.0 à 2.5.1

# v6.6.1

###### 2025/01/01

* `Fonctionnalité` Module Pinyin pour la conversion du pinyin chinois (Consultez la documentation du projet > [Pinyin chinois](https://docs.autojs6.com/#/pinyin))
* `Fonctionnalité` Module Pinyin4j pour la conversion du pinyin chinois (Consultez la documentation du projet > [Pinyin chinois](https://docs.autojs6.com/#/pinyin4j))
* `Fonctionnalité` Méthodes UiObject#isSimilar et UiObjectCollection#isSimilar pour déterminer si une commande ou une collection de commandes est similaire
* `Fonctionnalité` Méthode globale "currentComponent", utilisée pour obtenir le nom du composant actif actuel
* `Correction` Problème empêchant la compilation correcte du projet dans certains environnements suite à un retour à une ancienne version
* `Correction` Exception "valeur non primitive" pouvant survenir lors de l'appel à des méthodes inexistantes
* `Correction` Problème empêchant l'ajout correct de raccourcis de script sur certains appareils (correction provisoire) _[`issue #221`](http://issues.autojs6.com/221)_
* `Correction` Erreur de restriction de type de paramètre dans les méthodes automator.click/longClick _[`issue #275`](http://issues.autojs6.com/275)_
* `Correction` Problème avec les sélecteurs ne prenant pas en charge les paramètres de type ConsString _[`issue #277`](http://issues.autojs6.com/277)_
* `Correction` Problème d'absence des méthodes et propriétés propres aux instances UiObjectCollection
* `Amélioration` La page de configuration de packaging prend maintenant en charge la configuration de signature, la gestion du keystore et les parametres d'autorisation (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `Amélioration` Amélioration de la précision de la reconnaissance du nom du package actuel et de l'activité en cours de la fenêtre flottante (Priorité : Shizuku > Root > A11Y)
* `Amélioration` Amélioration de la précision de la reconnaissance de currentPackage et currentActivity (Priorité : Shizuku > Root > A11Y)
* `Amélioration` Restauration de la possibilité de sélectionner le contenu texte d'une entrée individuelle dans la fenêtre de journal via un double-clic ou un appui long _[`issue #280`](http://issues.autojs6.com/280)_
* `Amélioration` Récupérer autant d'informations critiques que possible pour les projets de script en cas de corruption du fichier project.json
* `Amélioration` Convertir automatiquement le chinois simplifié en pinyin (y compris les caractères à tons multiples) pour les suffixes de noms de paquet générés lors de l'empaquetage de fichiers uniques
* `Amélioration` Soutien des arguments négatifs dans les méthodes UiSelector#findOnce et UiSelector#find
* `Amélioration` Amélioration de l'adaptabilité des méthodes app.startActivity/startDualActivity
* `Amélioration` Prise en charge des formes abrégées supplémentaires pour les préfixes de noms de paquet dans les sélecteurs liés aux éléments de l'interface utilisateur et aux className (par exemple RecyclerView, Snackbar, etc.)
* `Amélioration` Synchroniser le code en amont le plus récent du moteur Rhino et l'adapter au projet existant
* `Dépendance` Ajout de Pinyin4j version 2.5.0
* `Dépendance` Ajout de Jieba Analysis version 1.0.3-SNAPSHOT (modifiée)
* `Dépendance` Mise à niveau de la version de Gradle de 8.11.1 à 8.12

# v6.6.0

###### 2024/12/02 - Réécriture du module intégré, mise à jour avec prudence

* `Astuce` Les modules intégrés sont réécrits en Kotlin pour améliorer l'efficacité d'exécution des scripts, mais des améliorations itératives sont nécessaires
* `Astuce` Le fichier init.js intégré est vide par défaut, permettant aux développeurs d'étendre les modules intégrés ou de monter des modules externes.
* `Fonctionnalité` Module Axios / Module Cheerio (Réf. à [AutoX](https://github.com/kkevsekk1/AutoX))
* `Fonctionnalité` Module SQLite pour des opérations simples sur les bases de données SQLite (Réf. à [Auto.js Pro](https://g.pro.autojs.org/)) (Voir la documentation du projet > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Fonctionnalité` Module MIME pour le traitement et l'analyse des chaînes de type MIME (Voir la documentation du projet > [MIME](https://docs.autojs6.com/#/mime))
* `Fonctionnalité` Module Nanoid pour la génération d'ID de chaîne (Réf. à [ai/nanoid](https://github.com/ai/nanoid))
* `Fonctionnalité` Module Sysprops pour obtenir des données de configuration de l'environnement d'exécution (Voir la documentation du projet > [Propriétés système](https://docs.autojs6.com/#/sysprops))
* `Fonctionnalité` Le module OCR prend en charge le moteur [Rapid OCR](https://github.com/RapidAI/RapidOCR)
* `Fonctionnalité` L'analyse de la mise en page prend en charge le changement de fenêtre (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode auto.clearCache prend en charge le nettoyage des caches de contrôle (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode threads.pool prend en charge l'application simple des pools de threads (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.matchTemplate prend en charge le paramètre d'option useTransparentMask, prenant en charge la recherche d'images avec transparence (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.requestScreenCaptureAsync permet de demander des permissions de capture d'écran de manière asynchrone en mode UI (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.requestScreenCapture prend en charge le paramètre d'option isAsync, prenant en charge la capture d'écran de manière asynchrone (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` images.on('screen_capture', callback) et d'autres méthodes d'écoute d'événements prennent en charge l'écoute des événements de disponibilité de capture d'écran (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.stopScreenCapture soutient la libération active des ressources liées aux applications de capture d'écran (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Les méthodes Images.psnr/mse/ssim/mssim/hist/ncc et images.getSimilarity pour obtenir des mesures de similarité d'image
* `Fonctionnalité` La méthode images.isGrayscale pour déterminer si une image est en niveaux de gris
* `Fonctionnalité` La méthode images.invert pour la conversion d'image négative
* `Fonctionnalité` Les méthodes s13n.point/time pour normaliser les objets de point et de durée (Voir la documentation du projet > [Normalisation](https://docs.autojs6.com/#/s13n))
* `Fonctionnalité` Les méthodes gravity, touchThrough, backgroundTint du module console (Voir la documentation du projet > [Console](https://docs.autojs6.com/#/console))
* `Fonctionnalité` Les méthodes Mathx.randomInt/Mathx.randomFloat pour retourner des entiers aléatoires ou des nombres à virgule flottante aléatoires dans une plage spécifiée
* `Fonctionnalité` Les méthodes app.launchDual/startDualActivity pour gérer le lancement double d'applications (Nécessite des autorisations Shizuku ou Root) (Expérimental)
* `Fonctionnalité` La méthode app.kill pour arrêter de force une application (Nécessite des autorisations Shizuku ou Root)
* `Fonctionnalité` La méthode floaty.getClip pour obtenir indirectement le contenu du presse-papiers à l'aide d'une fenêtre flottante
* `Correction` Fuite de mémoire dans le View Binding des sous-classes de Fragment (par exemple, [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48))
* `Correction` Fuite de mémoire d'instance dans des classes telles que [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21)
* `Correction` Problème causant le crash de l'application lors de la demande de permissions de capture d'écran sur Android 14 (par [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `Correction` Problème causant le crash de l'application lors du démarrage du service en premier plan sur Android 14
* `Correction` Problème avec le bouton d'exécution dans l'éditeur de code qui ne s'allume pas correctement sur Android 14
* `Correction` L'application peut ne pas fonctionner correctement après l'empaquetage en raison de fichiers de bibliothèque nécessaires manquants _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `Correction` Crash de l'application lors de l'édition du projet en raison de ressources d'icône spécifiées manquantes _[`issue #203`](http://issues.autojs6.com/203)_
* `Correction` Incapacité à utiliser correctement les paramètres pour obtenir les ressources de capture d'écran de l'orientation spécifiée lorsqu'on demande des permissions de capture d'écran
* `Correction` Problème avec certains appareils incapables d'ajouter correctement des raccourcis de script (Correction d'essai) _[`issue #221`](http://issues.autojs6.com/221)_
* `Correction` Problème de retard cumulatif de l'envoi de requêtes avec des méthodes liées à l'envoi de requêtes dans le module http _[`issue #192`](http://issues.autojs6.com/192)_
* `Correction` Le service Shizuku peut ne pas fonctionner correctement avant qu'AutoJs6 n'entre dans la page principale de l'activité (Correction d'essai) _[`issue #255`](http://issues.autojs6.com/255)_
* `Correction` La méthode random(min, max) peut avoir des résultats hors limites
* `Correction` Problème où le paramètre de type de résultat des méthodes pickup ne peut pas passer correctement des tableaux vides
* `Correction` Problème de rectangle de contrôle obtenu par UiObject#bounds() pouvant être modifié par inadvertance, brisant son immutabilité
* `Correction` Problème avec les éléments texte/bouton/entrée où le texte contenant des guillemets doubles de largeur réduite ne peut pas être analysé correctement
* `Correction` Problème avec les éléments text/textswitcher où la fonctionnalité de l'attribut autoLink échoue
* `Correction` Problème avec différents scripts partageant par erreur le même objet ScriptRuntime
* `Correction` Problème avec les variables globales HEIGHT et WIDTH perdant leurs propriétés Getter générées dynamiquement
* `Correction` Problème avec un temps de démarrage potentiellement élevé causé par le chargement de RootShell au démarrage du script
* `Correction` Problème avec le réglage de couleur de fond de la fenêtre de console flottante entraînant la perte du style d'arrondi rectangulaire
* `Correction` Le démarrage automatique du service d'accessibilité peut rencontrer des problèmes de service anormaux (Correction d'essai)
* `Correction` Problème de déclenchement du changement de ViewPager lors du glissement du contrôle WebView à gauche ou à droite sur la page de document de la page d'accueil
* `Correction` Problème avec le gestionnaire de fichiers incapable de reconnaître les extensions de fichier contenant des lettres majuscules
* `Correction` Le gestionnaire de fichiers peut ne pas reconnaître automatiquement le projet lors de la première entrée dans le répertoire du projet
* `Correction` Problème avec la page du gestionnaire de fichiers incapable de se rafraîchir automatiquement après la suppression du dossier
* `Correction` Problème avec le tri des fichiers et des dossiers dans le gestionnaire de fichiers où les noms de lettre initiale ASCII sont mis en arrière
* `Correction` Exception 'FAILED ASSERTION' dans la fonction de débogage de l'éditeur de code
* `Correction` Problème d'impossibilité de déboguer à nouveau correctement après la fermeture de l'éditeur pendant le processus de débogage de l'éditeur de code
* `Correction` Problème de saut potentiel de caractères de fin lors du saut à la fin de la ligne dans l'éditeur de code
* `Correction` Problème de scintillement de l'écran lors du démarrage de la page d'activité de journal sur la page principale de l'activité
* `Correction` Problème avec l'application empaquetée ne pouvant pas utiliser correctement le module opencc
* `Amélioration` Amelioration de l'experience des indications au toucher pour le controle "ABI indisponible" dans la page de configuration de packaging
* `Amélioration` Prend en charge l'utilisation de Shizuku pour contrôler l'interrupteur d'affichage 'Localisation du pointeur'
* `Amélioration` Prend en charge l'utilisation de Shizuku pour contrôler les interrupteurs de permission 'Médias de projection' et 'Modifier les paramètres sécurisés'
* `Amélioration` Automator.gestureAsync/gesturesAsync prend en charge les paramètres de fonction de rappel
* `Amélioration` Le module tasks utilise une méthode synchrone pour les opérations de base de données afin d'éviter les incohérences potentielles d'accès aux données
* `Amélioration` Le mode d'exécution de scripts prend en charge les paramètres de mode de séparation par symbole pipeline (par exemple, en commençant par `"ui|auto";`)
* `Amélioration` Le mode d'exécution de scripts prend en charge les guillemets simples et guillemets inversés et permet d'omettre les points-virgules (par exemple, en commençant par `'ui';` ou `'ui'`)
* `Amélioration` Le mode d'exécution de scripts prend en charge l'importation rapide des modules d'extension intégrés tels que axios, cheerio, et dayjs (par exemple, en commençant par `"axios";`)
* `Amélioration` Le mode d'exécution de scripts prend en charge les paramètres de mode x ou jsox pour activer rapidement les modules d'extension d'objets intégrés JavaScript (par exemple, en commençant par `"x";`)
* `Amélioration` Les attributs src et path de l'élément img prennent en charge les chemins relatifs locaux (par exemple, `<img src="a.png"` />)
* `Amélioration` L'éditeur de code prend en charge la détermination intelligente de l'emplacement d'insertion lors de l'importation de classes Java et de noms de paquets
* `Amélioration` Le module images prend en charge l'utilisation des chemins directement comme paramètres d'image
* `Amélioration` importPackage prend en charge les paramètres de chaîne
* `Amélioration` En mode client, l'adresse IP prend en charge l'importation depuis le presse-papiers avec reconnaissance intelligente et permet une conversion intelligente de la touche espace
* `Amélioration` Le gestionnaire de fichiers prend en charge la sélection de préfixes par défaut lors de la création de nouveaux fichiers et génère automatiquement un suffixe numérique approprié
* `Amélioration` Le gestionnaire de fichiers informe spécifiquement sur le message d'exception lors de l'exécution du projet _[`issue #268`](http://issues.autojs6.com/268)_
* `Amélioration` Le gestionnaire de fichiers prend en charge plus de types et affiche les symboles d'icône correspondants (prend en charge plus de 800 types de fichiers)
* `Amélioration` Les types de fichiers éditables (jpg/doc/pdf/...) dans le gestionnaire de fichiers ont ajouté des boutons d'édition
* `Amélioration` Les fichiers APK dans le gestionnaire de fichiers prennent en charge la visualisation des informations de base, des informations du manifest et de la liste des permissions
* `Amélioration` Les fichiers multimédias audio/vidéo dans le gestionnaire de fichiers prennent en charge la visualisation des informations de base et des informations de MediaInfo
* `Amélioration` Le paquet de fichier unique prend en charge le remplissage automatique du nom de paquet standardisé approprié et l'indication de filtre de caractère invalide
* `Amélioration` Le paquet de fichier unique prend en charge la configuration automatique de l'icône et l'incrémentation automatique du numéro et du nom de version basé sur l'application du même nom de paquet installée
* `Amélioration` Le fichier de configuration du paquet prend en charge l'option abis/libs pour spécifier la bibliothèque et l'architecture ABI incluses par défaut
* `Amélioration` Prend en charge les indications de messages pertinents lorsque les options abis/libs du fichier de configuration du paquet sont invalides ou non disponibles
* `Amélioration` LeakCanary est exclu de la version officielle pour éviter une croissance inutile
* `Amélioration` Tous les commentaires en anglais dans le code source du projet sont accompagnés de traductions en chinois simplifié pour améliorer la lisibilité
* `Amélioration` README et CHANGELOG prennent en charge plusieurs langues (Généré automatiquement par script)
* `Amélioration` Améliorer l'adaptabilité de la version du script de construction Gradle
* `Dépendance` Inclure la version 2.3.1 de MIME Util
* `Dépendance` Inclure la version 12.6 de Toaster
* `Dépendance` Inclure la version 10.3 de EasyWindow (pour Toaster)
* `Dépendance` Mettre à niveau la version 8.5 de Gradle -> 8.11.1 
* `Dépendance` Mettre à niveau la version 1.7.15-SNAPSHOT de Rhino -> 1.8.0-SNAPSHOT
* `Dépendance` Mettre à niveau la version 1.10.0 d'Android Material -> 1.12.0
* `Dépendance` Mettre à niveau la version 1.7.0 d'AndroidX Annotation -> 1.9.1
* `Dépendance` Mettre à niveau la version 1.6.1 d'AndroidX AppCompat -> 1.7.0
* `Dépendance` Mettre à niveau la version 1.8.0 d'AndroidX WebKit -> 1.12.1
* `Dépendance` Mettre à niveau la version 3.13.0 de Apache Commons -> 3.16.0
* `Dépendance` Mettre à niveau la version 1.2.4 de ARSCLib -> 1.3.1
* `Dépendance` Mettre à niveau la version 2.10.1 de Gson -> 2.11.0
* `Dépendance` Mettre à niveau la version 2.13.3 de Jackson DataBind -> 2.13.4.2
* `Dépendance` Mettre à niveau la version 2.12.5 de Joda Time -> 2.12.7
* `Dépendance` Mettre à niveau la version 2.12 de LeakCanary -> 2.14
* `Dépendance` Mettre à niveau la version 17.2.0 de MLKit Barcode Scanning -> 17.3.0
* `Dépendance` Mettre à niveau la version 16.0.0 de MLKit Text Recognition Chinese -> 16.0.1
* `Dépendance` Mettre à niveau la version 2.9.0 de Retrofit2 Converter Gson -> 2.11.0
* `Dépendance` Mettre à niveau la version 2.9.0 de Retrofit2 Retrofit -> 2.11.0
* `Dépendance` Mettre à niveau la version 2.0.3 de Desugar JDK Libs -> 2.0.4
* `Dépendance` Mettre à niveau la version 1.5.2 de Test Runner -> 1.6.2
* `Dépendance` Mettre à niveau la version 5.10.0 de Junit Jupiter -> 5.10.3
* `Dépendance` Réduire la version 5.0.0-alpha.11 d'OkHttp3 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `Fonctionnalité` Module opencc (référez-vous à la documentation du projet > [Conversion en Chinois](https://docs.autojs6.com/#/opencc)) (Réf à [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `Fonctionnalité` Ajout des méthodes [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) et [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) pour UiSelector _[`issue #115`](http://issues.autojs6.com/115)_
* `Fonctionnalité` La page de configuration de packaging prend maintenant en charge le filtrage des ABI et des bibliotheques (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Correction` Problème de taille de fichier anormalement volumineux lors du packaging de l'application (Réf à [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Correction` Problème d'affichage de certains messages d'exception lors du packaging de l'application
* `Correction` Correction d'un probleme ou une icone vide pouvait s'afficher apres la selection de l'icone de l'application dans la page de configuration de packaging
* `Correction` Problème d'initialisation contextuelle lors de l'intégration de la bibliothèque OCR MLKit Google
* `Correction` Problème d'inefficacité des méthodes ocr.recognizeText/detect
* `Correction` Problème de langage d'affichage incohérent avec le paramètre de l'application dans certains textes (comme la page des logs)
* `Correction` Problème de débordement de texte sur certains appareils avec le service de réutilisation activé
* `Correction` Problème de fermeture automatique du service d'accessibilité sans notification sur certains appareils _[`issue #181`](http://issues.autojs6.com/181)_
* `Correction` Problème de crash de l'application causé par les boutons physiques du matériel avec le service d'accessibilité activé sur certains appareils (correctif partiel) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Correction` Problème de dysfonctionnement de la fonctionnalité pickup après avoir redémarré le service d'accessibilité avec auto(true) (correctif partiel) _[`issue #184`](http://issues.autojs6.com/184)_
* `Correction` Problème de crash potentiel de l'application lors de la création de fenêtres flottantes avec le module floaty (correctif partiel)
* `Correction` Problème d'utilisation des paramètres abrégés dans app.startActivity _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Correction` Problème de conflit de nommage entre les classes importées avec importClass et les variables globales _[`issue #185`](http://issues.autojs6.com/185)_
* `Correction` Problème d'inutilisation du service d'accessibilité sur Android 7.x
* `Correction` Problème potentiel d'utilisation des méthodes runtime.loadJar/loadDex sur Android 14 (correctif partiel)
* `Correction` Problème d'inutilisation des "layout bounds" et "layout hierarchy" sur le panneau de configuration rapide dans le système Android _[`issue #193`](http://issues.autojs6.com/193)_
* `Correction` Problème potentiel d'ANR ([Application Not Responding](https://developer.android.com/topic/performance/vitals/anr)) causé par la fonctionnalité de vérification automatique des mises à jour (correctif partiel) _[`issue #186`](http://issues.autojs6.com/186)_
* `Correction` Problème de retour à la page de chemin de travail après un clic sur le bouton "retour" dans le gestionnaire de fichiers _[`issue #129`](http://issues.autojs6.com/129)_
* `Correction` Problème de non-affichage du bouton de remplacement lors de l'utilisation de la fonctionnalité de remplacement dans l'éditeur de code
* `Correction` Problème potentiel de crash de l'application lors de la suppression longue dans l'éditeur de code (correctif partiel)
* `Correction` Problème de non-affichage du panneau de fonctions de module sur un clic sur le bouton fx dans l'éditeur de code
* `Correction` Problème de débordement de noms de fonctions sur le panneau de fonctions de module dans l'éditeur de code
* `Amélioration` Le panneau de fonctions rapides du module d'édition de code s'adapte au mode nuit
* `Amélioration` La page de démarrage de l'application emballée s'adapte au mode nuit et la disposition des icônes d'application est ajustée
* `Amélioration` La page de configuration de packaging prend maintenant en charge la touche ENTER du clavier virtuel pour deplacer le focus du curseur
* `Amélioration` La page de configuration de packaging permet maintenant d'appuyer sur les titres ABI et les titres de bibliotheque pour basculer l'etat de selection globale
* `Amélioration` La page de configuration de packaging utilise maintenant une selection ABI par defaut plus intelligente et ajoute des indications pour les options indisponibles
* `Amélioration` Le gestionnaire de fichiers ajuste l'affichage des éléments du menu en fonction du type et des caractéristiques des fichiers et dossiers
* `Amélioration` Le menu contextuel du gestionnaire de fichiers pour les dossiers ajoute une option d'emballage d'application
* `Amélioration` Lorsque les services d'accessibilité sont activés mais dysfonctionnent, un état anormal se reflète dans le commutateur du tiroir de la page d'accueil d'AutoJs6
* `Amélioration` La console inclut des informations détaillées sur la pile lors de l'impression des messages d'erreur.
* `Dépendance` Ajout de ARSCLib version 1.2.4
* `Dépendance` Ajout de Flexbox version 3.0.0
* `Dépendance` Ajout de Android OpenCC version 1.2.0
* `Dépendance` Mise à jour de Gradle version 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `Fonctionnalité` La méthode dialogs.build prend en charge le paramètre d'option inputSingleLine
* `Fonctionnalité` Méthode console.setTouchable _[`issue #122`](http://issues.autojs6.com/122)_
* `Correction` Problème où certaines méthodes OCR ne pouvaient pas reconnaître les paramètres de région _[`issue #162`](http://issues.autojs6.com/162)_  _[`issue #175`](http://issues.autojs6.com/175)_
* `Correction` Problème de récupération des détails de la version lors de la découverte d'une nouvelle version sur Android 7.x
* `Correction` Problème de crash de l'application lors de la demande de permissions de capture d'écran sur Android 14
* `Correction` Problème de crash potentiel de l'application lors de la commutation rapide de l'option "Floating Button" dans le panneau de navigation du tiroir principal
* `Correction` Problème de persistance du bouton flottant après la fermeture depuis le menu et le redémarrage de l'application
* `Correction` Problème où le paramètre de la méthode d'application AutoJs6 ne prenait pas effet sur les systèmes Android 13 et supérieurs
* `Correction` Problème de déploiement automatique des ressources OpenCV lors de la première compilation des outils de construction
* `Amélioration` Module de ponts natifs pour améliorer l'efficacité de l'exécution des scripts (Réf à [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `Amélioration` Réécriture du code lié aux services d'accessibilité pour améliorer la stabilité des fonctionnalités du service d'accessibilité (expérimental) _[`issue #167`](http://issues.autojs6.com/167)_
* `Amélioration` Format de sortie de l'impression pour UiObject et UiObjectCollection
* `Amélioration` Prompt de mise à niveau pour les versions non conformes de JDK dans l'outil de construction Gradle
* `Dépendance` Mise à jour de Gradle de la version 8.4 à la version 8.5-rc-1
* `Dépendance` Rétrogradation de Commons IO de la version 2.14.0 à la version 2.8.0
* `Dépendance` Rétrogradation de Jackson DataBind de la version 2.14.3 à la version 2.13.3

# v6.4.1

###### 2023/11/02

* `Correction` Problème d'adaptation des outils de construction à des plateformes inconnues (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Correction` Problème de crash potentiel de l'application lors de la sortie du script _[`issue #159`](http://issues.autojs6.com/159)_
* `Correction` Erreur de type de retour pour body.contentType dans le module http _[`issue #142`](http://issues.autojs6.com/142)_
* `Correction` Problème de données incorrectes pour device.width et device.height _[`issue #160`](http://issues.autojs6.com/160)_
* `Correction` Problème de crash potentiel de l'application lors de la suppression longue dans l'éditeur de code (tentative de correction) _[`issue #156`](http://issues.autojs6.com/156)_
* `Correction` Problème de crash potentiel de l'application lors de l'utilisation du sélecteur de texte inversé dans l'éditeur de code
* `Correction` Problème d'affichage des raccourcis pour certains appareils lors d'un appui long sur l'icône de l'application AutoJs6
* `Correction` Problème de réactivité nulle du bouton de confirmation lors du packaging des projets sur certains appareils
* `Correction` Problème d'utilisation de paramètres abrégés dans app.sendBroadcast et app.startActivity
* `Correction` Problème fonctionnel initial lors de l'appel des méthodes comme JsWindow#setPosition du module floaty
* `Amélioration` Ajout des permissions Termux pour supporter les appels Intent pour exécuter des commandes ADB _[`issue #136`](http://issues.autojs6.com/136)_
* `Amélioration` Object de réponse réutilisable avec les méthodes body.string() et body.bytes() dans le module http
* `Amélioration` Support de l'automatisation de build avec GitHub Actions (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Amélioration` Adaptation des outils de construction pour la plateforme Temurin
* `Dépendance` Mise à jour de Gradle de la version 8.4-rc-3 à la version 8.4
* `Dépendance` Mise à jour de Android dx de la version 1.11 à la version 1.14

# v6.4.0

###### 2023/10/30

* `Fonctionnalité` Support du moteur Paddle Lite pour le module ocr (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `Fonctionnalité` Support des plugins intégrés et externes pour le packaging (par [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `Fonctionnalité` Module WebSocket (voir Documentation > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `Fonctionnalité` Modules barcode / qrcode (voir Documentation > [Barcode](https://docs.autojs6.com/#/barcode) / [QRCode](https://docs.autojs6.com/#/qrcode))
* `Fonctionnalité` Module shizuku (voir Documentation > [Shizuku](https://docs.autojs6.com/#/shizuku)) et le commutateur de permissions dans le panneau de navigation de l'accueil
* `Fonctionnalité` Méthodes device.rotation / device.orientation
* `Fonctionnalité` Support d'accès aux propriétés statiques dans les classes Java internes
* `Fonctionnalité` Support de la sélection et du changement de langue de l'application dans la page des paramètres du système Android (Android 13 et supérieur)
* `Fonctionnalité` Ajout de la fonctionnalité d'activation des Raccourcis d'application _[application shortcuts](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn)_, via l'ajout ou un appui long sur l'icône de l'application
* `Correction` Re-fusion de certaines PR (par [aiselp](https://github.com/aiselp)) pour résoudre certains problèmes d'arrêt anormal des scripts _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Correction` Problème d'utilisation des nouvelles APIs AutoJs6 dans les applications packagées (par [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `Correction` Problème de stylisation des applications packagées en mode nuit
* `Correction` Problème de perte d'informations d'extension de fichier lors de l'enregistrement local via l'extension VSCode
* `Correction` Problème d'exceptions non interceptées dans les projets utilisant des coroutines, causant des crashs de l'application
* `Correction` Problème de persistance de position du bouton flottant après redémarrage ou sortie de l'application
* `Correction` Problème de mise à jour des informations de configuration de l'appareil après le changement d'orientation de l'écran _[`issue #153`](http://issues.autojs6.com/153)_
* `Correction` Problème de petite taille des caractères du titre de la barre d'outils lors de la rotation en paysage de l'écran
* `Correction` Problème de disposition compacte des onglets sur l'accueil en mode paysage
* `Correction` Problème de débordement du bouton flottant hors écran lors de la rotation en paysage _[`issue #90`](http://issues.autojs6.com/90)_
* `Correction` Problème de récupération des coordonnées et de l'orientation latérale de l'écran du bouton flottant après plusieurs rotations de l'écran
* `Correction` Problème d'affichage offensif des messages flottants sur certains appareils
* `Correction` Problème de masquage des messages flottants lorsque plusieurs scripts s'exécutent simultanément _[`issue #67`](http://issues.autojs6.com/67)_
* `Correction` Problème de plantage de l'application lorsque l'analyse de la mise en page via la diffusion échoue
* `Correction` Problème de non détection d'évènements pour les instances créées après la première instance de WebSocket
* `Correction` Annulation de la redirection globale importPackage pour éviter des erreurs d'importation dans certains scopes _[`issue #88`](http://issues.autojs6.com/88)_
* `Correction` Problème de crash de l'application lors de l'utilisation des fonctions copier ou exporter dans la page d'activité des journaux
* `Amélioration` Renommage de la fonction d'export des journaux en fonction d'envoi et refonte de la fonction d'export pour plus de pertinence
* `Amélioration` Fonction d'envoi des journaux compatible avec la coupure automatique d'entrées trop volumineuses
* `Amélioration` Module ocr compatible avec les moteurs Google MLKit et Paddle Lite (voir Documentation > [OCR](https://docs.autojs6.com/#/ocr?id=p-mode))
* `Amélioration` Augmentation du taux de succès de lancement automatique du service d'accessibilité
* `Amélioration` Migration du traitement des annotations Kotlin de kapt à KSP
* `Amélioration` Support des outils de construction pour les versions EAP d'IntelliJ IDEA
* `Amélioration` Adaptation des outils de construction Java pour éviter les problèmes de "version invalide"
* `Amélioration` Optimisation de la logique de rétrogradation et capacité de prédiction des versions pour IDE et plugins
* `Amélioration` Adaptation de l'extension VSCode 1.0.7
* `Dépendance` Ajout de Rikka Shizuku version 13.1.5
* `Dépendance` Ajout de MLKit Barcode Scanning version 17.2.0
* `Dépendance` Mise à jour de Android SDK de la version 33 à la version 34
* `Dépendance` Mise à jour de OpenCV version 4.5.5 à 4.8.0 (Réf à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dépendance` Mise à jour de Gradle de la version 8.3-rc-1 à 8.4-rc-3
* `Dépendance` Mise à jour de Apache Commons Lang3 de la version 3.12.0 à la version 3.13.0
* `Dépendance` Mise à jour de Glide de la version 4.15.1 à 4.16.0
* `Dépendance` Mise à jour de Android Analytics de la version 14.3.0 à 14.4.0
* `Dépendance` Mise à jour de AndroidX WebKit de la version 1.7.0 à 1.8.0
* `Dépendance` Mise à jour de AndroidX Preference de la version 1.2.0 à 1.2.1
* `Dépendance` Mise à jour de AndroidX Annotation de la version 1.6.0 à 1.7.0
* `Dépendance` Mise à jour de AndroidX Recyclerview de la version 1.3.0 à 1.3.2
* `Dépendance` Mise à jour de Android Material de la version 1.9.0 à 1.10.0
* `Dépendance` Mise à jour de AndroidX AppCompat de la version 1.4.2 à 1.6.1
* `Dépendance` Mise à jour de Commons IO de la version 2.8.0 à 2.14.0
* `Dépendance` Mise à jour de Jackson DataBind de la version 2.13.3 à 2.14.3
* `Dépendance` Suppression de Zeugma Solutions LocaleHelper version 1.5.1

# v6.3.3

###### 2023/07/21

* `Fonctionnalité` Fonction de commentaire de code dans l'éditeur de code (par [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Fonctionnalité` auto.stateListener pour l'écoute de l'état de connexion du service d'accessibilité (par [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Fonctionnalité` Ajout des méthodes nextSibling / lastChild / offset pour le type UiObject (voir Documentation > [Nœud de contrôles](https://docs.autojs6.com/#/uiObjectType))
* `Correction` Problème de l'extension VSCode lors de l'analyse des scripts avec une longueur de caractères de plus de quatre chiffres décimaux _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `Correction` Problème de non-sauvegarde des fichiers par l'extension VSCode _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `Correction` Problème de non-navigation vers la page "Gestion des services d'accessibilité" après clic sur l'élément de menu du bouton flottant
* `Correction` Problème de perte de la méthode runtime.requestPermissions _[`issue #104`](http://issues.autojs6.com/104)_
* `Correction` Problème de non-support du paramètre MainThreadProxy pour events.emitter _[`issue #103`](http://issues.autojs6.com/103)_
* `Correction` Problème lors de l'édition de code dans _[`pr #78`](http://pr.autojs6.com/78)_
* `Correction` Problème de surcharge de la pile d'appels ClassLoader lors de l'utilisation de JavaAdapter _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `Amélioration` Ajustement du scope des modules (par [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Amélioration` Suppression de la vérification de signature au démarrage de l'application en version de distribution (par [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `Amélioration` Amélioration de la fonctionnalité de commentaire de code dans l'éditeur sur la base de _[`pr #98`](http://pr.autojs6.com/98)_
* `Amélioration` Ajout d'éléments de menu pour commentaire de code dans l'éditeur sur la base de _[`pr #98`](http://pr.autojs6.com/98)_
* `Amélioration` Adaptation de l'extension VSCode 1.0.6
* `Amélioration` Support du paramètre de niveau pour la méthode UiObject#parent (voir documentation > [Nœud de contrôles](https://docs.autojs6.com/#/uiObjectType))
* `Dépendance` Mise à jour de Gradle de la version 8.2 à la version 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `Fonctionnalité` module crypto (voir documentation du projet > [Cryptage](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `Fonctionnalité` Mode UI ajoute des contrôles tels que textswitcher / viewswitcher / viewflipper / numberpicker / video / search
* `Fonctionnalité` Ajout des fonctionnalités de copie et d'exportation des journaux sur la page d'activité des journaux _[`issue #76`](http://issues.autojs6.com/76)_
* `Fonctionnalité` Le mode client ajoute une fonction d'historique des adresses IP
* `Correction` Problème où le mode client ne peut pas afficher l'adresse IP après une connexion automatique ou une activation automatique du mode serveur
* `Correction` Problème où la connexion se perd et ne peut pas se reconnecter après le changement de langue ou le passage en mode nuit en mode client/serveur
* `Correction` Problème empêchant l'utilisation d'un port personnalisé lors de la saisie de l'adresse cible en mode client
* `Correction` Problème où certains caractères peuvent provoquer un crash d'AutoJs6 lors de la saisie de l'adresse cible en mode client
* `Correction` Problème de défaillance de certaines commandes à distance du plugin VSCode (tentative de correction)
* `Correction` Problème où les détails de la version ne peuvent pas être obtenus lors de la détection d'une nouvelle version sur Android 7.x
* `Correction` Problème où images.pixel ne peut pas obtenir la valeur des pixels de la capture d'écran du service d'accessibilité _[`issue #73`](http://issues.autojs6.com/73)_
* `Correction` Problème où les propriétés prédéfinies des contrôles natifs Android (commençant par une majuscule) ne peuvent pas être utilisées en mode UI
* `Correction` Problème où lors du chargement de plusieurs fichiers avec runtime.loadDex/loadJar, seul le premier fichier est pris en compte _[`issue #88`](http://issues.autojs6.com/88)_
* `Correction` Problème où seules les icônes de la documentation s'affichent sur certains appareils après l'installation de l'application (tentative de correction) _[`issue #85`](http://issues.autojs6.com/85)_
* `Amélioration` Adaptation pour le plugin VSCode 1.0.5
* `Amélioration` Support du module cheerio (Réf. à [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `Amélioration` L'instance JsWebSocket prend en charge l'utilisation de la méthode rebuild pour recréer et établir une connexion _[`issue #69`](http://issues.autojs6.com/69)_
* `Amélioration` Le module base64 prend en charge le codage et le décodage des tableaux de nombres et des tableaux d'octets Java
* `Amélioration` Ajout du support de JavaMail pour Android _[`issue #71`](http://issues.autojs6.com/71)_
* `Amélioration` Utilisation du type de données Blob pour obtenir des informations de mise à jour de version afin d'améliorer la compatibilité avec les réseaux sans proxy
* `Amélioration` Affichage de l'adresse IP cible dans le sous-titre du tiroir de la page d'accueil lorsqu'une connexion client est en cours
* `Amélioration` Messages d'erreur pour les entrées non valides lors de la saisie de l'adresse cible en mode client
* `Amélioration` Support de la connexion via la touche Entrée du clavier virtuel en mode client
* `Amélioration` Maintien de l'état actif du mode serveur après activation (sauf désactivation manuelle ou arrêt du processus de l'application) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `Amélioration` Détection bidirectionnelle des versions entre AutoJs6 et le plugin VSCode et affichage des résultats anormaux _[`issue #89`](http://issues.autojs6.com/89)_
* `Amélioration` Ajout de l'autorisation de lecture des messages SMS (android.permission.READ_SMS) (désactivée par défaut)
* `Amélioration` Implémentation de la méthode findMultiColors (par [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `Amélioration` Support du chargement de plusieurs fichiers ou de tout un répertoire avec runtime.loadDex/loadJar/load
* `Dépendance` Mise à jour de LeakCanary version 2.11 -> 2.12
* `Dépendance` Mise à jour de Android Analytics version 14.2.0 -> 14.3.0
* `Dépendance` Mise à jour de Gradle version 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `Fonctionnalité` Autorisation de notification de publication et interrupteur dans le tiroir d'accueil _[`issue #55`](http://issues.autojs6.com/55)_
* `Fonctionnalité` Support de l'analyse de la mise en page Android simple en mode UI (voir Code d'exemple > Mise en page > Mise en page Android simple)
* `Fonctionnalité` Ajout de contrôles tels que console / imagebutton / ratingbar / switch / textclock / togglebutton en mode UI
* `Fonctionnalité` Support du type OmniColor pour les valeurs de couleur des contrôles en mode UI (ex. color="orange")
* `Fonctionnalité` Pleine compatibilité des contrôles avec la méthode attr pour définir les propriétés des contrôles en mode UI (ex. ui.text.attr('color', 'blue'))
* `Fonctionnalité` Support des valeurs de propriété booléennes sous forme abrégée (ex. clickable="true" peut être abrégé en clickable ou isClickable)
* `Fonctionnalité` Support des propriétés booléennes isColored et isBorderless pour les contrôles button
* `Fonctionnalité` La méthode console.resetGlobalLogConfig réinitialise la configuration globale des journaux
* `Fonctionnalité` La méthode web.newWebSocket crée une instance de WebSocket (voir documentation du projet > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `Correction` Problème de tri des dossiers dans le gestionnaire de fichiers
* `Correction` Problème où la fenêtre flottante du module floaty ne peut pas ajuster le style et la position _[`issue #60`](http://issues.autojs6.com/60)_
* `Correction` Problème de chevauchement de la fenêtre flottante du module floaty avec la barre d'état du système
* `Correction` Problème de fonctionnement de la méthode http.postMultipart _[`issue #56`](http://issues.autojs6.com/56)_
* `Correction` Problème où aucun script ne peut s'exécuter sous Android 7.x _[`issue #61`](http://issues.autojs6.com/61)_
* `Correction` Problème de construction du projet en l'absence du fichier sign.property
* `Correction` Problème de crash potentiel d'AutoJs6 en arrière-plan en l'absence d'autorisation de notification de premier plan (API >= 33)
* `Correction` Problème où après avoir appelé la méthode console.show, le bouton FAB de la fenêtre de journalisation ne peut pas effacer les journaux
* `Correction` Exception de pointeur nul prototype lors du débogage dans l'éditeur de scripts
* `Correction` Problème où l'éditeur de scripts exécute des scripts dans un dossier temporaire au lieu de les enregistrer et les exécuter à l'emplacement d'origine
* `Correction` Problème où les contrôles d'analyse de mise en page ne montrent pas correctement les noms de contrôle en cas de profondeur excessive _[`issue #46`](http://issues.autojs6.com/46)_
* `Amélioration` Ajout d'un bouton de sortie dans la fenêtre flottante d'analyse de la mise en page _[`issue #63`](http://issues.autojs6.com/63)_
* `Amélioration` Abrécurité des chemins de scripts absolus pour réduire la longueur du texte et augmenter la lisibilité
* `Amélioration` Remplacement de Error par Exception pour éviter les crashs d'application en cas d'erreur
* `Amélioration` Migration de la méthode de liaison de vue (View) avec ButterKnife vers View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `Amélioration` Redémarrage automatique du mode serveur à l'ouverture de l'application après une fermeture anormale _[`issue #64`](http://issues.autojs6.com/64)_
* `Amélioration` Reconnexion automatique en mode client avec la dernière adresse historique après une fermeture anormale à l'ouverture de l'application
* `Dépendance` Mise à jour de LeakCanary version 2.10 -> 2.11
* `Dépendance` Mise à jour de Android Material version 1.8.0 -> 1.9.0
* `Dépendance` Mise à jour de AndroidX WebKit version 1.6.1 -> 1.7.0
* `Dépendance` Mise à jour de OkHttp3 version 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `Dépendance` Mise à jour de MLKit Text Recognition Chinese version 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `Fonctionnalité` module ocr (voir documentation du projet > [Reconnaissance Optique de Caractères](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Fonctionnalité` module notice (voir documentation du projet > [Notifications](https://docs.autojs6.com/#/notice))
* `Fonctionnalité` module s13n (voir documentation du projet > [Standardisation](https://docs.autojs6.com/#/s13n))
* `Fonctionnalité` module Color (voir documentation du projet > [Type de Couleur](https://docs.autojs6.com/#/colorType))
* `Fonctionnalité` colors.setColorPaint méthode, pour définir la couleur du pinceau android.graphics.Paint (voir la documentation du projet > [Color Type](https://docs.autojs6.com/#/colorType?id=m-setpaintcolor))
* `Fonctionnalité` Support de maintien de l'écran allumé en avant-plan et options de configuration
* `Fonctionnalité` Ajout d'une application de lecture indépendante de la documentation
* `Correction` Problème dans la méthode colors.toString
* `Correction` Problème d'ajout automatique du préfixe de protocole dans la méthode app.openUrl
* `Correction` Comportement anormal des méthodes app.viewFile/editFile si le fichier correspondant n'existe pas
* `Correction` Problème avec la fonction de rappel de la méthode pickup
* `Correction` Problème où l'information bounds des contrôles affichée dans l'analyse de mise en page remplace les signes négatifs par des virgules
* `Correction` Problème où les sélecteurs bounds/boundsInside/boundsContains ne peuvent pas filtrer correctement les rectangles nuls (par exemple, rectangles inversés) _[`issue #49`](http://issues.autojs6.com/49)_
* `Correction` Problème où l'application crash après changement de thème ou de langue en cliquant ou appuyant longuement sur le tag de document de la page d'accueil
* `Correction` Problème de tremblement lors de l'ajustement de la taille de la police par zoom à deux doigts dans l'éditeur de texte
* `Correction` Problème de téléchargement de certains dépôts dans les scripts de construction (tous intégrés) _[`issue #40`](http://issues.autojs6.com/40)_
* `Correction` Problème de plugin d'action de Tasker ne pouvant pas ajouter d'actions AutoJs6 (tentative de correction) _[`issue #41`](http://issues.autojs6.com/41)_
* `Correction` Problème de ButterKnife ne pouvant pas résoudre les identifiants de ressources avec les versions récentes de JDK _[`issue #48`](http://issues.autojs6.com/48)_
* `Correction` Problème de service d'accessibilité avec des erreurs de service fréquentes (tentative de correction)
* `Correction` Problème d'utilisation incorrecte du paramètre de taille dans la méthode images.medianBlur
* `Correction` Problème d'affichage complet des noms de scripts dans le module engines perdant le point entre le nom et l'extension de fichier
* `Correction` Problème potentiel de calcul incorrect dans l'algorithme de détection de distance RGB pondéré (tentative de correction)
* `Correction` Problème où les méthodes liées aux fenêtres flottantes du module console ne peuvent pas être appelées avant la méthode show
* `Correction` Problème où certaines méthodes, comme console.setSize, peuvent ne pas fonctionner _[`issue #50`](http://issues.autojs6.com/50)_
* `Correction` Problème de valeurs de constantes de couleur erronées dans colors.material
* `Correction` Problème où les propriétés minDate et maxDate du contrôle de sélection de date en mode UI ne parviennent pas à analyser correctement les dates
* `Correction` Problème de duplication de tâches en cours d'exécution lors de la commutation rapide vers l'onglet "Tâches" de la page d'accueil après le lancement d'un script
* `Correction` Problème de réinitialisation de l'état de la page de gestion des fichiers lors du retour d'autres pages _[`issue #52`](http://issues.autojs6.com/52)_
* `Correction` Problème de tri des fichiers dans la gestion des fichiers ne correspondant pas à l'icône d'affichage
* `Amélioration` Affichage de la date de modification des fichiers et dossiers dans la page de gestion des fichiers
* `Amélioration` Support de la mémorisation du type de tri dans la page de gestion des fichiers
* `Amélioration` Ajout de sections sur la compilation et construction du projet et sur les outils d'aide au développement des scripts dans README.md _[`issue #33`](http://issues.autojs6.com/33)_
* `Amélioration` Support d'autres formats pour le paramètre d'option de région dans les méthodes du module images (voir documentation du projet > [Types Omnipotents](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Amélioration` Support de pages abrégées additionnelles (comme pref/homepage/docs/about) pour app.startActivity
* `Amélioration` Ancrage des méthodes globales du module web au module lui-même pour améliorer l'utilisabilité (voir documentation du projet > [Web](https://docs.autojs6.com/#/web))
* `Amélioration` Support par la méthode web.newInjectableWebView des options de configuration WebView par défaut pour une meilleure fonctionnalité
* `Amélioration` Ajout de plusieurs méthodes de conversion et utilitaires dans le module colors, ajout de constantes statiques supplémentaires et de noms de couleurs pouvant être directement utilisés comme paramètres
* `Amélioration` Ajout de plusieurs méthodes de configuration de la fenêtre flottante du module console et support de l'unification des styles de fenêtre via un constructeur build
* `Amélioration` Support du déplacement de la fenêtre flottante du module console via le glissement de la zone de titre
* `Amélioration` Support de la fermeture automatique retardée de la fenêtre flottante du module console après la fin du script
* `Amélioration` Support du zoom à deux doigts pour ajuster la taille de la police dans la fenêtre flottante et dans l'Activité du module console
* `Amélioration` Support du paramètre d'option de délai d'attente (timeout) pour les méthodes liées au module http
* `Amélioration` Support de la réduction proactive de la version JDK dans les scripts de construction Gradle en cas de besoin
* `Amélioration` Support de la sélection automatique de la version appropriée des outils de construction selon le type et la version de la plateforme dans les scripts de construction Gradle (dans la mesure du possible)
* `Dépendance` Intégration de la version localisée 1.0.3 de Auto.js APK Builder
* `Dépendance` Intégration de la version localisée 1.1 de MultiLevelListView
* `Dépendance` Intégration de la version localisée 1.1.5 de Settings Compat
* `Dépendance` Intégration de la version localisée 0.31 de Enhanced Floaty
* `Dépendance` Ajout du MLKit Text Recognition Chinese version 16.0.0-beta6
* `Dépendance` Mise à jour de Gradle version 8.0-rc-1 -> 8.2-milestone-1
* `Dépendance` Mise à jour de Android Material version 1.7.0 -> 1.8.0
* `Dépendance` Mise à jour de Glide version 4.14.2 -> 4.15.1
* `Dépendance` Mise à jour de Joda Time version 2.12.2 -> 2.12.5
* `Dépendance` Mise à jour de Android Analytics version 14.0.0 -> 14.2.0
* `Dépendance` Mise à jour de AndroidX WebKit version 1.5.0 -> 1.6.1
* `Dépendance` Mise à jour de AndroidX Recyclerview version 1.2.1 -> 1.3.0
* `Dépendance` Mise à jour de Zip4j version 2.11.2 -> 2.11.5
* `Dépendance` Mise à jour de Junit Jupiter version 5.9.2 -> 5.9.3
* `Dépendance` Mise à jour de AndroidX Annotation version 1.5.0 -> 1.6.0
* `Dépendance` Mise à jour de Jackson DataBind version 2.14.1 -> 2.14.2
* `Dépendance` Mise à jour de Desugar JDK Libs version 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `Fonctionnalité` Refonte et réécriture de la documentation du projet (partiellement accomplie)
* `Fonctionnalité` Adaptation multilingue pour français/espagnol/russe/arabe/japonais/coréen/anglais/chinois traditionnel
* `Fonctionnalité` Ajout d'options de paramétrage du chemin de travail avec sélection de chemin, historique et suggestions intelligentes par défaut
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge la navigation jusqu'au répertoire "stockage interne"
* `Fonctionnalité` Le gestionnaire de fichiers permet de définir n'importe quel répertoire comme chemin de travail par raccourci
* `Fonctionnalité` Gestion d'ignorance des mises à jour et des mises à jour ignorées
* `Fonctionnalité` L'éditeur de texte prend en charge le zoom à deux doigts pour ajuster la taille de la police
* `Fonctionnalité` Sélecteur idHex (UiSelector#idHex) (voir documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType))
* `Fonctionnalité` Sélecteur action (UiSelector#action) (voir documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType))
* `Fonctionnalité` Sélecteurs de la série Match (UiSelector#xxxMatch) (voir documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType))
* `Fonctionnalité` Sélecteur de capture (UiSelector#pickup) (voir documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `Fonctionnalité` Détection de contrôles (UiObject#detect) (voir documentation du projet > [Nœuds de contrôle](https://docs.autojs6.com/#/uiObjectType))
* `Fonctionnalité` Boussole de contrôle (UiObject#compass) (voir documentation du projet > [Nœuds de contrôle](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `Fonctionnalité` Méthode d'attente globale wait (voir documentation du projet > [Objets globaux](https://docs.autojs6.com/#/global?id=m-wait))
* `Fonctionnalité` Méthodes de zoom globales cX/cY/cYx (voir documentation du projet > [Objets globaux](https://docs.autojs6.com/#/global?id=m-wait))
* `Fonctionnalité` Type d'application global (voir documentation du projet > [Types d'application](https://docs.autojs6.com/#/appType))
* `Fonctionnalité` Module i18n (Solution JavaScript multilingue basée sur banana-i18n) (voir documentation du projet > Internationalisation)
* `Correction` Problèmes d'affichage temporaire du texte et défaillances fonctionnelles de certains boutons après un changement de langue
* `Correction` Barre d'outils de projet non affichée lors du démarrage de l'application si le chemin de travail est un projet
* `Correction` Chemin de travail modifié automatiquement après un changement de langue _[`issue #19`](http://issues.autojs6.com/19)_
* `Correction` Retard significatif lors du démarrage des tâches planifiées (tentative de correction) _[`issue #21`](http://issues.autojs6.com/21)_
* `Correction` Problèmes d'utilisation des modules internes lorsque le nom de module JavaScript est redéfini _[`issue #29`](http://issues.autojs6.com/29)_
* `Correction` Impossibilité de réduire automatiquement le panneau de paramètres rapides sur les systèmes Android avancés (tentative de correction) _[`issue #7`](http://issues.autojs6.com/7)_
* `Correction` Chevauchement de certaines pages avec la zone de la barre de notification sur les systèmes Android avancés
* `Correction` Exemples de code pour le réglage des couleurs des pinceaux non fonctionnels sur les systèmes Android 10 et plus
* `Correction` Correction du nom du fichier "Gestion de la musique" en "Gestionnaire de fichiers" et rétablissement de ses fonctions
* `Correction` Problèmes de décalage possible lors de la mise à jour du gestionnaire de fichiers
* `Correction` Erreur de portée dans le module ui causant l'inaccessibilité des propriétés de composants dans certains scripts basés sur l'UI
* `Correction` Risques de perte de contenu enregistré lors de la fermeture de la boîte de dialogue de nom de fichier via un clic extérieur
* `Correction` Problèmes d'affichage hors écran des titres de certaines sections de la documentation
* `Correction` Incapacité de faire défiler horizontalement dans certaines zones d'exemple de code dans la documentation
* `Correction` Défaillances lors de l'actualisation de la page de la documentation et impossibilité d'annuler l'opération (tentative de correction)
* `Correction` Problèmes d'activation du mode nuit à l'installation initiale de l'application
* `Correction` Obligation de forcer le mode nuit au démarrage de l'application si le mode nuit est activé
* `Correction` Ne pas pouvoir activer les couleurs thématiques après l'activation du mode nuit
* `Correction` Éléments de texte indiscernables en mode nuit en raison de la couleur de fond
* `Correction` Problèmes d'affichage des textes sur les boutons de fonctionnalité de la page À propos
* `Correction` Chevauchement des textes et des boutons en raison de titres trop longs dans le tiroir principal
* `Correction` Échecs de synchronisation des états d'avertissement après fermeture de la boîte de dialogue dans le tiroir principal
* `Correction` Problèmes de continuité d'affichage de la boîte de dialogue ADB après l'échec de la modification des paramètres de permissions root
* `Correction` Problèmes de permissions root lors de la première instruction à afficher la position du pointeur
* `Correction` Problèmes de mise en page des éléments d'icône dans la page de sélection d'icône
* `Correction` Clignotement possible de l'éditeur de texte au démarrage causé par les paramètres du mode nuit (tentative de correction)
* `Correction` Problèmes de limitations de la taille maximale de la police lors du réglage
* `Correction` Problèmes d'affichage des journaux à la fin de l'exécution du script pour certains systèmes Android
* `Correction` Persistence du bouton flottant après la fermeture via le menu du bouton flottant au redémarrage de l'application
* `Correction` Débordement de l'écran inférieur avec le menu contextuel lors de l'ouverture des éléments de liste lors de l'analyse des couches de disposition
* `Correction` Problèmes de visibilité des boutons de navigation en mode nuit sur les systèmes Android 7.x
* `Correction` Problèmes de requêtes non fermées pour http.post et autres méthodes similaires
* `Correction` Problèmes de perte des informations du canal alpha dans les résultats de colors.toString lorsque la valeur alpha est 0
* `Amélioration` Redirection des classes publiques de la version Auto.js 4.x pour assurer une compatibilité descendante maximale possible (dans une certaine mesure)
* `Amélioration` Fusionner tous les modules du projet pour éviter d'éventuels problèmes de référence circulaire (supprimer temporairement le module inrt)
* `Amélioration` Migrer la configuration de construction Gradle de Groovy à KTS
* `Amélioration` Ajouter un support multilingue pour les messages d'exception de Rhino
* `Amélioration` L'interrupteur de permissions du tiroir de la page d'accueil ne montre des messages que lorsqu'il est activé
* `Amélioration` La disposition du tiroir de la page d'accueil s'attache directement sous la barre d'état pour éviter les incompatibilités de barre de couleur supérieure
* `Amélioration` Compatibilité des fonctions de vérification de mise à jour, téléchargement et avis de mise à jour avec le système Android 7.x
* `Amélioration` Reconcevoir la page des paramètres (migration vers AndroidX)
* `Amélioration` La page des paramètres prend en charge la pression longue sur les options de configuration pour afficher des informations détaillées
* `Amélioration` Ajout de l'option "Suivre le système" pour le mode nocturne (Android 9+)
* `Amélioration` Compatibilité de l'écran de démarrage de l'application avec le mode nocturne
* `Amélioration` Ajouter des identifiants numériques aux icônes d'application pour améliorer l'expérience utilisateur avec plusieurs versions open source coexistantes
* `Amélioration` Ajouter plus d'options de couleurs Material Design au thème de l'application
* `Amélioration` Alléger et adapter les icônes des éléments de liste dans le gestionnaire de fichiers/panneau des tâches à la couleur du thème
* `Amélioration` Compatibilité de la couleur du texte indicatif dans la boîte de recherche de la page d'accueil avec le mode nocturne
* `Amélioration` Compatibilité des composants tels que les dialogues/textes/Fab/AppBar/éléments de liste avec le mode nocturne
* `Amélioration` Compatibilité des pages telles que les documents/paramètres/à propos/couleurs de thème/analyse de mise en page et des menus des boutons flottants avec le mode nocturne
* `Amélioration` Compatibilité de la disposition des pages avec la disposition RTL (Right-To-Left) dans la mesure du possible
* `Amélioration` Ajouter des effets d'animation des icônes à la page à propos
* `Amélioration` Mise à jour automatique de l'année dans le texte de la déclaration de droits d'auteur sur la page à propos
* `Amélioration` Après l'installation initiale de l'application, déterminer et définir automatiquement un répertoire de travail approprié
* `Amélioration` Désactiver la fonction de zoom à deux doigts sur la page des documents pour éviter une anomalie de l'affichage du contenu
* `Amélioration` Simplifier le nom et le chemin des tâches affichés dans les éléments de liste du panneau des tâches par un chemin relatif
* `Amélioration` Abréger le texte des boutons de l'éditeur de texte pour éviter le débordement du contenu
* `Amélioration` Prise en charge de la restauration de la taille de police par défaut dans la configuration de l'éditeur de texte
* `Amélioration` Améliorer la vitesse de réponse au clic sur le bouton flottant
* `Amélioration` Cliquer sur le bouton d'analyse de mise en page du bouton flottant effectue directement l'analyse de la plage de mise en page
* `Amélioration` Le thème de l'analyse de mise en page est adaptable (la fenêtre flottante suit le thème de l'application, le panneau de configuration rapide suit le thème du système)
* `Amélioration` Réorganiser la liste des informations de contrôle de mise en page selon la fréquence d'utilisation potentielle
* `Amélioration` Optimiser le format de sortie automatiquement lors du clic et copie sur les informations de contrôle de mise en page selon le type de sélecteur
* `Amélioration` Lors de la sélection de fichiers à l'aide de la fenêtre flottante, appuyer sur la touche de retour ramène au répertoire supérieur au lieu de fermer directement la fenêtre
* `Amélioration` Prise en charge de la détection de validité numérique et de la conversion automatique des symboles pointillés lors de la saisie d'une adresse en mode client se connectant à l'ordinateur
* `Amélioration` Afficher l'adresse IP du périphérique correspondant dans le tiroir de la page d'accueil après que le client et le serveur se sont connectés
* `Amélioration` Ajouter une protection contre la réécriture à certains objets globaux et modules intégrés (consultez la documentation du projet > Objets globaux > [Protection contre la réécriture](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `Amélioration` importClass et importPackage prennent en charge des paramètres de chaîne et des paramètres de longueur variable
* `Amélioration` ui.run prend en charge l'impression des informations de trace de pile en cas d'exception
* `Amélioration` ui.R et auto.R peuvent obtenir commodément l'ID de ressources d'AutoJs6
* `Amélioration` Les méthodes associées à l'utilisation d'applications dans le module app prennent en charge des paramètres de type App et des paramètres d'alias d'application
* `Amélioration` Les méthodes associées au rappel asynchrone dans le module dialogs prennent en charge l'omission des paramètres pré-remplis
* `Amélioration` app.startActivity et autres prennent en charge des paramètres d'option d'URL (voir code d'exemple > Applications > Intention)
* `Amélioration` Le module device renvoie null au lieu de lancer une exception lorsque l'obtention de l'IMEI ou du numéro de série matériel échoue
* `Amélioration` Augmenter la luminosité du texte dans le journal de la fenêtre flottante affichée par console.show pour améliorer la lisibilité du contenu
* `Amélioration` ImageWrapper#saveTo prend en charge la sauvegarde de fichiers image dans des chemins relatifs
* `Amélioration` Reconcevoir l'objet global colors et inclure la prise en charge des modes de couleur HSV / HSL (consultez la documentation du projet > [Couleurs](https://docs.autojs6.com/#/color)).
* `Dépendance` Localisation de la version 1.4.3 de Android Job
* `Dépendance` Localisation de la version 9.0.0 du SDK Client de Plugin Android pour Locale
* `Dépendance` Localisation de la version 1.306 de l'API GitHub
* `Dépendance` Mise à jour de la version de Android SDK 32 -> 33
* `Dépendance` Ajout de la version 1.0 de JCIP Annotations
* `Dépendance` Ajout de la version 1.5.0 de AndroidX WebKit
* `Dépendance` Ajout de la version 2.8.0 de Commons IO
* `Dépendance` Ajout de la version 2.0.0 de Desugar JDK Libs
* `Dépendance` Ajout de la version 2.13.3 de Jackson DataBind
* `Dépendance` Ajout de la version 2.1.0 de Jaredrummler Android Device Names
* `Dépendance` Ajout de la version 1.0.6 de Jaredrummler Animated SVG View
* `Dépendance` Remplacement de la version 2.1.7 de Jrummyapps ColorPicker par la version 1.1.0 de Jaredrummler ColorPicker
* `Dépendance` Mise à jour de la version de Gradle 7.5-rc-1 -> 8.0-rc-1
* `Dépendance` Mise à jour de la version des outils de compilation Gradle 7.4.0-alpha02 -> 8.0.0-alpha09
* `Dépendance` Mise à jour de la version du plugin Kotlin Gradle 1.6.10 -> 1.8.0-RC2
* `Dépendance` Mise à jour de la version de Android Material 1.6.0 -> 1.7.0
* `Dépendance` Mise à jour de la version de AndroidX Annotation 1.3.0 -> 1.5.0
* `Dépendance` Mise à jour de la version de AndroidX AppCompat 1.4.1 -> 1.4.2
* `Dépendance` Mise à jour de la version de Android Analytics 13.3.0 -> 14.0.0
* `Dépendance` Mise à jour de la version de Gson 2.9.0 -> 2.10
* `Dépendance` Mise à jour de la version de Joda Time 2.10.14 -> 2.12.1
* `Dépendance` Mise à jour de la version de Kotlinx Coroutines 1.6.1-native-mt -> 1.6.1
* `Dépendance` Mise à jour de la version d'OkHttp3 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `Dépendance` Mise à jour de la version de Zip4j 2.10.0 -> 2.11.2
* `Dépendance` Mise à jour de la version de Glide 4.13.2 -> 4.14.2
* `Dépendance` Mise à jour de la version de Junit Jupiter 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `Fonctionnalité` Fonction de vérification des mises à jour / téléchargement des mises à jour / notification des mises à jour (voir la page des paramètres) (pas encore supporté pour le système Android 7.x)
* `Correction` Problème de lecture/écriture sur le stockage externe sous Android 10 _[`issue #17`](http://issues.autojs6.com/17)_
* `Correction` Problème de crash de l'application lors de l'appui long sur la page de l'éditeur _[`issue #18`](http://issues.autojs6.com/18)_
* `Correction` Problème de fonctionnement des fonctions "Supprimer la ligne" et "Copier la ligne" dans le menu contextuel de l'éditeur
* `Correction` Problème de l'absence de la fonction "Coller" dans le menu des options de l'éditeur
* `Amélioration` Internationalisation partielle des messages d'erreur (en / zh)
* `Amélioration` Réorganisation des boutons de la boîte de dialogue de contenu non enregistré et ajout de la différenciation des couleurs
* `Dépendance` Ajout de la version 1.306 de github-api
* `Dépendance` Remplacement de la version 1.0.0 de retrofit2-rxjava2-adapter par la version 2.9.0 d'adapter-rxjava2

# v6.1.0

###### 2022/05/26 - Changement du nom du package, mise à niveau avec prudence

* `Astuce` Modification du nom du package de l'application vers org.autojs.autojs6 pour éviter le conflit avec le nom du package de l'application open source Auto.js
* `Fonctionnalité` Ajout du commutateur "Permission de projection média" dans le menu principal (Mode Root / ADB) (détection de l'état du commutateur en expérimental)
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge l'affichage des fichiers et dossiers cachés (voir la page des paramètres)
* `Fonctionnalité` Fonction de vérification forcée Root (voir la page des paramètres et les exemples de code)
* `Fonctionnalité` Module autojs (voir des exemples de code > AutoJs6)
* `Fonctionnalité` Module tasks (voir des exemples de code > Tâches)
* `Fonctionnalité` Méthode console.launch() pour démarrer la page d'activité des journaux
* `Fonctionnalité` Outil util.morseCode (voir des exemples de code > Outils > Code Morse)
* `Fonctionnalité` Outil util.versionCodes (voir des exemples de code > Outils > Infos des versions Android)
* `Fonctionnalité` Méthode util.getClass() et autres (voir des exemples de code > Outils > Obtenir une classe et un nom de classe)
* `Fonctionnalité` Méthode timers.setIntervalExt() (voir des exemples de code > Minuteries > Exécution périodique conditionnelle)
* `Fonctionnalité` Méthodes colors.toInt() / rgba() etc. (voir des exemples de code > Images et couleurs > Conversion de base des couleurs)
* `Fonctionnalité` Méthodes automator.isServiceRunning() / ensureService()
* `Fonctionnalité` Méthodes automator.lockScreen() etc. (voir des exemples de code > Services d'accessibilité > Android 9)
* `Fonctionnalité` Méthodes automator.headsethook() etc. (voir des exemples de code > Services d'accessibilité > Android 11)
* `Fonctionnalité` Méthode automator.captureScreen() (voir des exemples de code > Services d'accessibilité > Capture d'écran)
* `Fonctionnalité` La méthode dialogs.build prend en charge des paramètres d'option tels que animation, linkify, etc. (voir des exemples de code > Dialogues > Dialogues personnalisés)
* `Correction` Les paramètres d'option inputHint, itemsSelectedIndex, etc. de dialogs.build ne fonctionnent pas correctement
* `Correction` Anomalies des paramètres de rappel dans JsDialog#on('multi_choice')
* `Correction` Problème de retour constant de -1 par UiObject#parent().indexInParent() _[`issue #16`](http://issues.autojs6.com/16)_
* `Correction` Problème de non-exécution des Thenable retournées par Promise.resolve() à la fin du script
* `Correction` Erreurs potentielles dans les noms des packages ou classes (boardcast -> broadcast / auojs -> autojs)
* `Correction` Problème potentiel de crash de l'application lors de l'utilisation de images.requestScreenCapture() sous les versions Android ≥ 31
* `Correction` Problème potentiel de crash de l'application lors de demandes simultanées d'images.requestScreenCapture() par plusieurs scripts
* `Correction` Problème de gel potentiel lors de l'appel à new RootAutomator()
* `Amélioration` RootAutomator ne peut pas être instancié sans permissions Root
* `Amélioration` Refonte de la page "À propos de l'application et du développeur"
* `Amélioration` Refactorisation de tous les modules JavaScript intégrés
* `Amélioration` Refactorisation de tous les scripts Gradle de la construction et ajout d'un script de configuration commun (config.gradle)
* `Amélioration` L'outil de construction Gradle prend en charge la gestion automatique des numéros de version et le nommage automatique des fichiers de construction
* `Amélioration` L'outil de construction Gradle ajoute une tâche pour joindre un résumé CRC32 aux fichiers de construction (appendDigestToReleasedFiles)
* `Amélioration` L'écriture des exceptions dans le résultat de retour au lieu de lancer directement des exceptions lors d'un appel de shell() (aucune nécessité de try/catch)
* `Amélioration` Utilisation de JSON intégré à Rhino à la place de l'ancien module json2
* `Amélioration` Support de paramètre de timeout dans auto.waitFor()
* `Amélioration` Support des fonctions fléchées comme arguments pour threads.start()
* `Amélioration` Support des niveaux de logs dans console.trace() (voir des exemples de code > Console > Impression de l'appel de pile)
* `Amélioration` Support de vibration en mode et en code Morse dans device.vibrate() (voir des exemples de code > Appareil > Vibration en mode / code Morse)
* `Amélioration` Adaptation des permissions de lecture/écriture du stockage externe aux versions Android ≥ 30
* `Amélioration` Adoption de Material Color pour améliorer la lisibilité des polices en thème normal et nuit dans la Console
* `Amélioration` Références faibles sauvegardées pour toutes les instances d'ImageWrapper et récupération automatique après fin du script (expérimental)
* `Dépendance` Ajout de la version 3.1.0 de CircleImageView
* `Dépendance` Mise à jour d'Android Analytics de la version 13.1.0 à 13.3.0
* `Dépendance` Mise à jour des outils de construction Gradle de la version 7.3.0-alpha06 à 7.4.0-alpha02
* `Dépendance` Mise à jour d'Android Job de la version 1.4.2 à 1.4.3
* `Dépendance` Mise à jour d'Android Material de la version 1.5.0 à 1.6.0
* `Dépendance` Mise à jour de CrashReport de la version 2.6.6 à 4.0.4
* `Dépendance` Mise à jour de Glide de la version 4.13.1 à 4.13.2
* `Dépendance` Mise à jour de Joda Time de la version 2.10.13 à 2.10.14
* `Dépendance` Mise à jour du plugin Kotlin Gradle de la version 1.6.10 à 1.6.21
* `Dépendance` Mise à jour de Kotlinx Coroutines de la version 1.6.0 à 1.6.1-native-mt
* `Dépendance` Mise à jour de LeakCanary de la version 2.8.1 à 2.9.1
* `Dépendance` Mise à jour d'OkHttp3 de la version 5.0.0-alpha.6 à 5.0.0-alpha.7
* `Dépendance` Mise à jour de Rhino Engine de la version 1.7.14 à 1.7.15-SNAPSHOT
* `Dépendance` Mise à jour de Zip4j de la version 2.9.1 à 2.10.0
* `Dépendance` Suppression de Groovy JSON version 3.0.8
* `Dépendance` Suppression de Kotlin Stdlib JDK7 version 1.6.21

# v6.0.3

###### 2022/03/19

* `Fonctionnalité` Fonction de changement de langue (encore incomplète)
* `Fonctionnalité` Module enregistreur (voir des exemples de code > Minuteries)
* `Fonctionnalité` Utilisation de "la permission de modification des paramètres de sécurité" pour activer automatiquement les services d'accessibilité et les commutateurs de réglages
* `Correction` Problème de non-fermeture automatique du panneau des paramètres rapides après avoir cliqué sur les icônes concernées (tentative de correction) _[`issue #7`](http://issues.autojs6.com/7)_
* `Correction` Problème potentiel de crash d'AutoJs6 lors de l'utilisation de la fonction toast avec le paramètre de forçage d'affichage
* `Correction` Problème potentiel de crash d'AutoJs6 lors de l'envoi de paquets de données incomplets via Socket
* `Amélioration` Activation automatique des services d'accessibilité lors du démarrage ou redémarrage d'AutoJs6 selon les paramètres d'options
* `Amélioration` Tentative d'ouverture automatique des services d'accessibilité lors de l'activation des boutons flottants
* `Amélioration` Complétion des traductions anglaises de tous les fichiers de ressources
* `Amélioration` Ajustement mineur de la mise en page du tiroir principal pour réduire l'espacement entre les éléments
* `Amélioration` Ajout de la synchronisation de l'état du commutateur de service en premier plan dans le tiroir principal
* `Amélioration` Synchronisation instantanée de l'état des commutateurs selon les besoins lors de l'ouverture du tiroir principal
* `Amélioration` Ajout de la détection de l'état et des messages de résultat pour l'affichage de la position du pointeur
* `Amélioration` Support des systèmes d'exploitation en 64 bits (référence à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Amélioration` Application immédiate des paramètres de transparence des boutons flottants lors de l'initialisation (sans nécessiter un clic supplémentaire pour appliquer la transparence)
* `Amélioration` Ajout de la détection des fichiers de code d'exemple lors de la réinitialisation du contenu des fichiers et fourniture d'un message de résultat
* `Amélioration` Migration de l'adresse de téléchargement des plugins packagés de GitHub vers JsDelivr
* `Dépendance` Ajout de la version 1.5.1 de Zeugma Solutions LocaleHelper
* `Dépendance` Rétrogradation de la version de Android Material de 1.6.0-alpha02 à 1.5.0
* `Dépendance` Mise à jour de Android SDK de la version 31 à 32
* `Dépendance` Mise à jour de Kotlinx Coroutines de la version 1.6.0-native-mt à 1.6.0
* `Dépendance` Mise à jour de OpenCV de la version 3.4.3 à 4.5.4 puis 4.5.5 (référence à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dépendance` Mise à jour d'OkHttp3 de la version 3.10.0 à 5.0.0-alpha.4 puis 5.0.0-alpha.6
* `Dépendance` Mise à jour des outils de construction Gradle de la version 7.2.0-beta01 à 7.3.0-alpha06
* `Dépendance` Mise à jour d'Auto.js-ApkBuilder de la version 1.0.1 à 1.0.3
* `Dépendance` Mise à jour de Glide Compiler de la version 4.12.0 à 4.13.1
* `Dépendance` Mise à jour de la distribution Gradle de la version 7.4-rc-2 à 7.4.1
* `Dépendance` Mise à jour de Gson de la version 2.8.9 à 2.9.0

# v6.0.2

###### 2022/02/05

* `Fonctionnalité` images.bilateralFilter() méthode de traitement d'image de filtre bilatéral
* `Correction` Problème où les appels multiples à toast ne font effet qu'au dernier appel
* `Correction` Problème où toast.dismiss() peut être inefficace
* `Correction` Problème où le mode client et le mode serveur peuvent ne pas fonctionner correctement
* `Correction` Problème où l'état des commutateurs de mode client et serveur peut ne pas se rafraîchir correctement
* `Correction` Anomalie de l'analyse des éléments textuels en mode UI sous Android 7.x (Réf à [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `Amélioration` Ignorer l'exception ScriptInterruptedException de sleep()
* `Dépendance` Ajout de la version 1.0.2 de AndroidX AppCompat (Legacy)
* `Dépendance` Mise à niveau de la version de AndroidX AppCompat de 1.4.0 à 1.4.1
* `Dépendance` Mise à niveau de la version de AndroidX Preference de 1.1.1 à 1.2.0
* `Dépendance` Mise à niveau de la version du moteur Rhino de 1.7.14-SNAPSHOT à 1.7.14
* `Dépendance` Mise à niveau de la version d'OkHttp3 de 3.10.0 à 5.0.0-alpha.3 puis 5.0.0-alpha.4
* `Dépendance` Mise à niveau de la version de Android Material de 1.6.0-alpha01 à 1.6.0-alpha02
* `Dépendance` Mise à niveau de la version des outils de construction Gradle de 7.2.0-alpha06 à 7.2.0-beta01
* `Dépendance` Mise à niveau de la version de Gradle 7.3.3 à 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `Fonctionnalité` Support de la connexion du plugin VSCode en mode client (LAN) et serveur (LAN/ADB) (Réf à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Module base64 (Réf à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Ajout des méthodes globales isInteger/isNullish/isObject/isPrimitive/isReference
* `Fonctionnalité` Ajout de polyfill (Object.getOwnPropertyDescriptors)
* `Fonctionnalité` Ajout de polyfill (Array.prototype.flat)
* `Amélioration` Extension de global.sleep pour supporter la compatibilité des valeurs de portée/négatives
* `Amélioration` Extension de global.toast pour supporter le contrôle de la durée/déclencheur de suppression forcée
* `Amélioration` Globaliser l'objet de nom de package (okhttp3/androidx/de)
* `Dépendance` Mise à jour de la version de Android Material de 1.5.0-beta01 à 1.6.0-alpha01
* `Dépendance` Mise à jour de la version des outils de construction Gradle de 7.2.0-alpha04 à 7.2.0-alpha06
* `Dépendance` Mise à jour de la version de Kotlinx Coroutines de 1.5.2-native-mt à 1.6.0-native-mt
* `Dépendance` Mise à jour de la version du plugin Gradle Kotlin de 1.6.0 à 1.6.10
* `Dépendance` Mise à jour de la version de Gradle 7.3 à 7.3.3

# v6.0.0

###### 2021/12/01

* `Fonctionnalité` Ajout d'un bouton de redémarrage d'application en bas du tiroir de la page d'accueil
* `Fonctionnalité` Ajout de commutateurs pour ignorer l'optimisation de la batterie/afficher au-dessus d'autres applications dans le tiroir de la page d'accueil
* `Correction` Problème d'affichage anormal de la couleur du thème dans certaines zones après l'installation initiale de l'application
* `Correction` Problème empêchant la construction du projet en l'absence du fichier sign.property
* `Correction` Problème d'enregistrement incorrect du mois pour les tâches uniques dans le panneau des tâches programmées
* `Correction` Problème où la couleur des commutateurs de la page des paramètres de l'application ne change pas avec le thème
* `Correction` Problème où les plugins de packaging ne sont pas reconnus et l'adresse de téléchargement des plugins de packaging est invalide
* `Correction` Problème où l'état du commutateur de la permission d'utilisation sur la page d'accueil peut ne pas se synchroniser
* `Correction` Problème potentiel de fuite de mémoire Mat dans TemplateMatching.fastTemplateMatching
* `Amélioration` Mise à niveau de la version du moteur Rhino de 1.7.7.2 à 1.7.13 puis 1.7.14-SNAPSHOT
* `Amélioration` Mise à niveau d'OpenCV de 3.4.3 à 4.5.4
* `Amélioration` Amélioration de la compatibilité de ViewUtil.getStatusBarHeight
* `Amélioration` Suppression des modules relatifs à la connexion utilisateur et du placement des mises en page dans le tiroir de la page d'accueil
* `Amélioration` Suppression des onglets communautaires et du marché de la page d'accueil et optimisation de la mise en page
* `Amélioration` Modification de l'état par défaut de certains commutateurs de paramètres
* `Amélioration` Ajout de la date de sortie et optimisation de l'affichage des droits d'auteur sur la page À propos
* `Amélioration` Mise à niveau du module JSON à la version 2017-06-12 et intégration de cycle.js
* `Amélioration` Suppression de la fonction de vérification automatique des mises à jour lors de la mise en avant de l'activité et suppression des boutons associés à la vérification des mises à jour
* `Amélioration` Optimisation de la logique interne du code de AppOpsKt#isOpPermissionGranted
* `Amélioration` Utilisation de ReentrantLock pour améliorer la sécurité dans ResourceMonitor (Réf à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Amélioration` Remplacement de JCenter par des dépôts tels que Maven Central
* `Amélioration` Suppression et élimination des fichiers de bibliothèque locale redondants
* `Dépendance` Localisation de la version 2.6.6 de CrashReport
* `Dépendance` Localisation de la version 1.0.0 de MutableTheme
* `Dépendance` Ajout de la version 1.1.1 de AndroidX Preference
* `Dépendance` Ajout de la version 1.1.0 de SwipeRefreshLayout
* `Dépendance` Mise à jour de Android SDK de 28 à 30 puis à 31
* `Dépendance` Mise à niveau d'Android Analytics de 7.0.0 à 13.1.0
* `Dépendance` Mise à niveau d'Android Annotations de 4.5.2 à 4.8.0
* `Dépendance` Mise à jour des outils de construction Gradle de 3.2.1 à 4.1.0 puis à 7.0.3 puis à 7.2.0-alpha04
* `Dépendance` Mise à jour de Android Job de 1.2.6 à 1.4.2
* `Dépendance` Mise à niveau de Android Material de 1.1.0-alpha01 à 1.5.0-beta01
* `Dépendance` Mise à jour d'AndroidX MultiDex de 2.0.0 à 2.0.1
* `Dépendance` Mise à jour d'Apache Commons Lang3 de 3.6 à 3.12.0
* `Dépendance` Mise à jour d'Appcompat de 1.0.2 à 1.4.0
* `Dépendance` Mise à niveau du plugin Gradle ButterKnife de 9.0.0-rc2 à 10.2.1 puis 10.2.3
* `Dépendance` Mise à niveau de ColorPicker de 2.1.5 à 2.1.7
* `Dépendance` Mise à niveau d'Espresso Core de 3.1.1-alpha01 à 3.5.0-alpha03
* `Dépendance` Mise à jour d'Eventbus de 3.0.0 à 3.2.0
* `Dépendance` Mise à jour de Glide Compiler de 4.8.0 à 4.12.0
* `Dépendance` Mise à jour de l'outil de construction Gradle de 29.0.2 à 30.0.2
* `Dépendance` Mise à jour de Gradle de 4.10.2 à 6.5 puis 7.0.2 puis 7.3
* `Dépendance` Mise à jour du plugin Groovy-Json de 3.0.7 à 3.0.8
* `Dépendance` Mise à jour de Gson de 2.8.2 à 2.8.9
* `Dépendance` Mise à jour de JavaVersion de 1.8 à 11 puis 16
* `Dépendance` Mise à jour de Joda Time de 2.9.9 à 2.10.13
* `Dépendance` Mise à jour de Junit de 4.12 à 4.13.2
* `Dépendance` Mise à jour du plugin Gradle Kotlin de 1.3.10 à 1.4.10 puis 1.6.0
* `Dépendance` Mise à jour de Kotlinx Coroutines de 1.0.1 à 1.5.2-native-mt
* `Dépendance` Mise à jour de LeakCanary de 1.6.1 à 2.7
* `Dépendance` Mise à niveau de LicensesDialog de 1.8.1 à 2.2.0
* `Dépendance` Mise à jour de Material Dialogs de 0.9.2.3 à 0.9.6.0
* `Dépendance` Mise à jour d'OkHttp3 de 3.10.0 à 5.0.0-alpha.2 puis 5.0.0-alpha.3
* `Dépendance` Mise à jour de Reactivex RxJava2 RxAndroid de 2.0.1 à 2.1.1
* `Dépendance` Mise à jour de Reactivex RxJava2 de 2.1.2 à 2.2.21
* `Dépendance` Mise à jour de Retrofit2 Converter Gson de 2.3.0 à 2.9.0
* `Dépendance` Mise à jour de Retrofit2 Retrofit de 2.3.0 à 2.9.0
* `Dépendance` Mise à jour de Zip4j de 1.3.2 à 2.9.1