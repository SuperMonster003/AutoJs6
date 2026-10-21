******

### Historique des Versions

******

# v6.8.0

###### 2026/10/21 - Certaines fonctions passent par des plugins, mise à niveau avec prudence

* `Astuce` Certaines fonctions intégrées sont désormais fournies par des plugins indépendants (OCR, reconnaissance de codes-barres, OpenCV, OpenCC, pinyin, MediaInfo, terminal et documentation hors ligne du projet); installez et activez les plugins correspondants dans le centre de plugins avant utilisation
* `Astuce` Le SDK cible des applications empaquetées passe de 29 à 37; l'accès au stockage partagé et au réseau local nécessite les autorisations requises par le système
* `Astuce` Pour utiliser images ou d'autres fonctions dépendant d'OpenCV dans une application empaquetée, sélectionnez le plugin OpenCV lors de l'empaquetage
* `Astuce` Le composant console et la console flottante affichent désormais les journaux du script courant; les journaux globaux sont accessibles via le composant globalconsole ou la page des journaux
* `Astuce` La journalisation dans des fichiers utilise désormais une implémentation intégrée, conservant les options documentées et les formats PatternLayout courants; les autres API Log4j ne sont plus disponibles
* `Astuce` Les méthodes Node.js pinyin/pinyin4j et les contrôles de fenêtre de console appellent désormais l'hôte de façon asynchrone; les scripts doivent attendre leurs résultats avec await
* `Fonctionnalité` Chaînes asynchrones flow et attentes de condition/stabilité pour Node.js, Bun et Workers, avec annulation des branches, reprise des étapes et instantanés de l'interface hôte; Clics avancés, défilement, saisie, collecte de listes et attente des événements réels Flow pour Node.js, Bun et Workers, avec préparation des abonnements, validation des instantanés et annulation
* `Fonctionnalité` Node.js/Bun/Worker: Connexions aux comptes mail, messages, pièces jointes et surveillance des boîtes, avec annulation et nettoyage des Workers
* `Fonctionnalité` Les raccourcis nouveaux et déjà épinglés sont archivés automatiquement, avec export/import et restauration en lot; les icônes illisibles peuvent être choisies individuellement ou remplacées en lot par les icônes par défaut sans écraser les icônes enregistrées; après dégel, les raccourcis manquants sont indiqués sur la page de sauvegarde sans rappel sur la page principale, et les suppressions manuelles reconnues ne sont pas cochées par défaut; les copies du même script conservent leurs ID distincts (recherche des raccourcis existants sous Android 7.1+, détection automatique sous Android 8+) (accès: Paramètres > Configuration > Sauvegarde des raccourcis)
* `Fonctionnalité` Assistant de configuration, pour exécuter successivement les assistants d'autorisation et d'installation des plugins, accessible depuis le message de bienvenue après le premier démarrage (accès: Paramètres > Configuration > Assistant de configuration)
* `Fonctionnalité` Assistant d'autorisation, pour sélectionner les autorisations du tiroir d'accueil et les demander successivement, avec les profils intégrés "Standard"/"Minimal"/"Tout" et des profils personnalisés; les autorisations accordées peuvent être masquées par le menu (accès: Assistant de configuration/bouton du titre du groupe "Autorisations" du tiroir d'accueil)
* `Fonctionnalité` Assistant d'installation des plugins, pour sélectionner les plugins officiels par catégorie et les installer par lots, avec les profils intégrés "Standard"/"Minimal"/"Tout" et l'enregistrement, le renommage et la suppression de profils personnalisés (accès: Assistant de configuration/menu du bouton flottant de l'onglet Plugins de l'accueil/menu "+" du centre de plugins)
* `Fonctionnalité` L'assistant d'installation des plugins permet d'installer pendant le téléchargement, avec installation silencieuse via le plugin 3-Setup Installer sous autorisation Shizuku/Root/Dhizuku; avec l'installateur système, l'autorisation d'installer des applications inconnues est vérifiée avant le téléchargement
* `Fonctionnalité` Option "Afficher l'icône du lanceur" dans les paramètres des plugins (activée par défaut), avec réglage commun dans l'assistant d'installation pour les plugins nouvellement installés dans le lot courant (la modification des composants d'application nécessite Root ou Shizuku)
* `Fonctionnalité` Gestionnaire du service d'accessibilité, permettant le démarrage/l'arrêt, le nettoyage du cache, l'autodiagnostic, la surveillance des événements et la copie des informations de diagnostic (accès: entrée du service d'accessibilité du tiroir d'accueil)
* `Fonctionnalité` Consultation des enregistrements de sortie des processus et copie des informations de diagnostic dans les options développeur (Android 11+) (accès: appui long sur l'icône de l'application dans la page À propos de l'application et du développeur)
* `Fonctionnalité` Option "Autoriser Inspector pour les scripts Node.js" dans les options développeur (désactivée par défaut), pour le débogage par points d'arrêt et l'analyse CPU/tas avec le plugin Node.js Runtime
* `Fonctionnalité` Page de gestion des modèles de génération de code, permettant l'importation locale, le téléchargement à la demande, la gestion et la comparaison de versions multiples ainsi que la mise à jour des modèles officiels (accès: Paramètres > Service d'accessibilité > Modèles de génération de code)
* `Fonctionnalité` Plugin MCP Server, pour accéder aux opérations de scripts, fichiers et appareil d'AutoJs6 via des clients MCP (accès: tiroir d'accueil/centre de plugins)
* `Fonctionnalité` Plugin 3-Shell Terminal, fournissant les fonctions de terminal (accès: tiroir d'accueil, barre d'outils du projet, menu des dossiers du gestionnaire de fichiers)
* `Fonctionnalité` Plugin Node.js Runtime, pour exécuter des fichiers et projets Node.js (prise en charge du paramètre de mode "nodejs" et des fichiers .node.js/.cjs/.mjs)
* `Fonctionnalité` Plugin Python Runtime, pour exécuter des fichiers et projets Python, avec points d'entrée de modules, délais d'exécution et entrée standard prédéfinie
* `Fonctionnalité` Plugin Lua Runtime, pour exécuter des scripts Lua
* `Fonctionnalité` Plugins Java Runtime et Kotlin Runtime, pour compiler et exécuter du code source Java/Kotlin (Java nécessite Android 7+, Kotlin nécessite Android 8+)
* `Fonctionnalité` Plugin QuickJS Runtime, pour exécuter des fichiers JavaScript et des ES module (prise en charge des paramètres de mode "quickjs"/"qjs" et des fichiers .quickjs.js/.quickjs.mjs)
* `Fonctionnalité` Plugin Bun Runtime, pour exécuter des fichiers et projets JavaScript/TypeScript (prise en charge du paramètre de mode "bun" et des fichiers .bun.js/.bun.ts/.bun.tsx)
* `Fonctionnalité` Plugin TypeScript Engine, pour compiler les fichiers et projets TypeScript pour Rhino/Node.js, avec vérification des types et Source Map
* `Fonctionnalité` Module ai, pour appeler des modèles locaux ou en ligne via des plugins AI Provider (comme 3-Stone AI), avec sortie en continu, sessions persistantes et résultats JSON structurés (voir Exemples de code > ai)
* `Fonctionnalité` API de script ai.agent, pour démarrer, consulter, répondre, confirmer et annuler les tâches 3-Stove Agent, avec tâches en arrière-plan detached (nécessite le plugin 3-Stove Agent) (voir Exemples de code > agent)
* `Fonctionnalité` Les méthodes ai.agent.run/create acceptent l'option plan, pour que 3-Stove Agent propose d'abord un plan d'étapes puis l'exécute en mode planification
* `Fonctionnalité` Les scripts peuvent être enregistrés comme scripts appelables par 3-Stove Agent via project.json ou un commentaire d'en-tête @agent, avec validation des paramètres, arrêt sur délai dépassé et retour de résultats structurés
* `Fonctionnalité` Module compose, pour construire des interfaces avec Jetpack Compose, avec fabriques de noeuds, références durables aux noeuds, state/render/ref réactifs et contrôle du thème (nécessite le plugin 3-Block Compose) (voir Exemples de code > 3-Block Compose)
* `Fonctionnalité` Option "Ouvrir dans le concepteur 3-Block Compose" du menu de l'éditeur: enregistre le fichier courant puis l'ouvre dans le concepteur visuel du plugin 3-Block Compose (1.2.0 ou ultérieur); au retour dans l'éditeur, le contenu enregistré par le concepteur est rechargé automatiquement (avec confirmation préalable en présence de modifications non enregistrées) (voir Exemples de code > 3-Block Compose)
* `Fonctionnalité` Service d'aperçu des mises en page en mode ui, qui permet au concepteur 3-Block Compose de prévisualiser les mises en page XML sur sa toile avec le véritable inflater de l'hôte, incorporé sur Android 11 et ultérieur et sous forme d'instantané sur les versions antérieures (nécessite le plugin 3-Block Compose 1.6.0 ou ultérieur)
* `Fonctionnalité` Méthodes compose.mount/floaty, pour monter une page UI ou créer une fenêtre flottante (compatible avec les scripts UI et non UI), avec nettoyage automatique des sessions associées à l'arrêt du script
* `Fonctionnalité` Méthode compose.dialog, pour créer des sessions de dialogue ou de panneau inférieur modal pouvant être mises à jour et fermées, utilisables dans les scripts UI ou ordinaires
* `Fonctionnalité` Conteneur XML <compose> et méthode compose.attach, pour intégrer des sessions Compose dans des pages UI ou des fenêtres flottantes; compose.AndroidView peut héberger des Android View existantes
* `Fonctionnalité` Les mises en page TSX prennent en charge les composants Compose tels que <compose.Column> et <compose:Text>, les références de fabriques de noeuds, les emplacements et les rappels réactifs (nécessite le plugin TypeScript Engine)
* `Fonctionnalité` La méthode ui.inflate prend en charge les mises en page XML TSX compilées et la liaison de leurs rappels onClick
* `Fonctionnalité` Module flow et méthode globale waitAsync, pour l'attente asynchrone et les opérations chaînées, avec branchements, boucles, délais, nouvelles tentatives et annulation (voir Documentation du projet > [Flux](https://docs.autojs6.com/#/flow))
* `Fonctionnalité` Objet global terminal, pour gérer les sessions de terminal, l'exécution visible et l'écoute des sorties, avec attente de sortie et de code de retour (nécessite le plugin 3-Shell Terminal) (voir Exemples de code > Terminal)
* `Fonctionnalité` Module installer, pour installer, mettre à jour et désinstaller des applications, avec opérations silencieuses Shizuku/Root/Dhizuku et options telles que l'installation avec notification (nécessite le plugin 3-Setup Installer) (voir Documentation du projet > [Installateur](https://docs.autojs6.com/#/installer))
* `Fonctionnalité` Module mail, pour envoyer, recevoir et rechercher des messages, télécharger les pièces jointes et gérer les dossiers, avec appels synchrones et asynchrones (nécessite le plugin 3-Stamp Mail) (voir Documentation du projet > [Courrier](https://docs.autojs6.com/#/mail))
* `Fonctionnalité` Méthode mail.watch, pour surveiller les nouveaux messages avec reconnexion après interruption
* `Fonctionnalité` Module epub, pour lire les métadonnées, la table des matières, le texte des chapitres et les couvertures des livres électroniques, avec exportation des ressources et recherche intégrale (nécessite le plugin 3-Folio EPUB) (voir Documentation du projet > [Livres électroniques](https://docs.autojs6.com/#/epub))
* `Fonctionnalité` Méthode epub.read, pour ouvrir le lecteur de livres électroniques et contrôler la position de lecture, les signets et les préférences de lecture
* `Fonctionnalité` Méthodes epub.annotations/annotationsAsync, pour lire les surlignages et notes, avec écoute des changements d'annotations dans les sessions du lecteur
* `Fonctionnalité` Module yolo, pour charger des modèles et détecter des objets via les plugins YOLO (voir Documentation du projet > [YOLO](https://docs.autojs6.com/#/yolo))
* `Fonctionnalité` Module tts, pour la synthèse vocale (voir Documentation du projet > [Synthèse vocale](https://docs.autojs6.com/#/tts))
* `Fonctionnalité` Module settings, pour lire et modifier les paramètres de l'application (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Paramètres](https://docs.autojs6.com/#/settings))
* `Fonctionnalité` Module work_manager, pour gérer les tâches planifiées et de diffusion (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Work Manager](https://docs.autojs6.com/#/workManager))
* `Fonctionnalité` Module power_manager, pour consulter et demander l'exemption d'optimisation de la batterie (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Power Manager](https://docs.autojs6.com/#/powerManager))
* `Fonctionnalité` Objet global pangu, pour traiter et vérifier l'espacement des textes mêlant chinois et écritures occidentales (voir Documentation du projet > [Espacement du texte](https://docs.autojs6.com/#/pangu))
* `Fonctionnalité` Propriété device.pageSize (getter), pour obtenir la taille des pages mémoire du système
* `Fonctionnalité` Méthode device.getMarketName, pour obtenir le nom commercial de l'appareil _[`issue #513`](http://issues.autojs6.com/513)_
* `Fonctionnalité` Méthode ui.useAndroidLayout, pour activer les composants de mise en page Android natifs
* `Fonctionnalité` Méthode runtime.loadAar, pour charger dynamiquement des fichiers AAR
* `Fonctionnalité` Méthodes runtime.loadJarWithR8/retraceR8Stack, pour charger des fichiers JAR et reconstituer les piles obscurcies via le plugin R8 Compiler (expérimental)
* `Fonctionnalité` Méthodes images.quantize/quantizeToFile, pour quantifier les images PNG et les enregistrer, avec retour d'informations de taille et de qualité (nécessite le plugin Image Quantization)
* `Fonctionnalité` Méthodes images.countPointsByColor/getMeanColor, pour compter les pixels correspondant à une couleur et obtenir la couleur moyenne
* `Fonctionnalité` Méthodes colors.distance/invert/blend/contrast, pour calculer la distance, l'inversion, le mélange et le contraste des couleurs (également pour les instances Color)
* `Fonctionnalité` Méthodes colors.toOklab/oklab/toOklch/oklch, pour convertir les espaces colorimétriques OKLab et OKLCH; colors.distance/isSimilar et images.detectColor prennent en charge l'algorithme oklab
* `Fonctionnalité` Méthodes colors.ensureContrast/harmony/tones/derive/nightVariant/simulate/extract, pour ajuster le contraste, créer des harmonies, gammes, palettes dérivées et couleurs nocturnes, simuler les déficiences de vision des couleurs et extraire les couleurs dominantes d'images (voir Exemples de code > Images et couleurs)
* `Fonctionnalité` Objet colors.theme, pour lire, modifier et réinitialiser les couleurs du thème par script et contrôler la bibliothèque de couleurs intelligente; la page des couleurs du thème permet d'annuler les modifications des scripts (voir Exemples de code > Images et couleurs)
* `Fonctionnalité` Méthodes console.setTimeVisible/setTimeFormat/setColorful/setAvoidStatusBar/setInputVisible et options correspondantes de console.build
* `Fonctionnalité` Le mode UI prend en charge les scripts TypeScript et les mises en page TSX (nécessite le plugin TypeScript Engine)
* `Fonctionnalité` Composant globalconsole en mode UI, pour afficher les journaux globaux; les composants console permettent de configurer le titre, le style et la barre de saisie
* `Fonctionnalité` Méthodes d'automatisation smartClick/clickIfExists/clickAny/findAny, pour rechercher et cliquer sur des composants, avec appels chaînés Flow
* `Fonctionnalité` Méthodes smartClickBounds/clickBoundsIfExists/clickBoundsAny, pour cliquer par coordonnées dans les limites des composants
* `Fonctionnalité` Méthodes d'automatisation scrollUntil/typeInto/dismissPopups/collectList, pour rechercher par défilement, saisir du texte, gérer les fenêtres contextuelles et collecter des listes
* `Fonctionnalité` Méthodes d'automatisation launchAndWait/backUntil/backToApp/toggle/retry, pour changer d'application, définir des états et réessayer des opérations
* `Fonctionnalité` Méthodes waitForIdle/waitForEvent/waitForToast/waitForNotification, pour attendre l'inactivité de l'interface, des événements d'accessibilité, des messages flottants et des notifications
* `Fonctionnalité` La méthode select accepte une syntaxe de sélecteur textuel combinant propriétés, expressions régulières et conditions booléennes
* `Fonctionnalité` Sélecteurs UiSelector#minDepth/maxDepth/minIndexInParent/maxIndexInParent, pour filtrer par profondeur du noeud et indice dans le parent, avec syntaxe de sélecteur textuel
* `Fonctionnalité` Méthode UiSelector#findIterator, pour parcourir paresseusement les composants correspondants
* `Fonctionnalité` Méthodes UiObject#isStale/waitUntilGone/waitForStable, pour détecter les noeuds périmés et attendre leur disparition ou leur stabilisation
* `Fonctionnalité` Méthodes UiObject#contentInvalid/contextClickable/multiLine/dismissable, pour obtenir les propriétés booléennes des composants
* `Fonctionnalité` Méthodes UiObject#toJSON/dumpSubtree/fingerprint, pour sérialiser les noeuds, exporter les sous-arbres et calculer une empreinte d'état
* `Fonctionnalité` Les collections de composants prennent en charge at/first/last/nonNull/slice et les opérations par composant telles que clickEach/setTextEach
* `Fonctionnalité` Méthodes auto.findWindows/findWindowRoots, pour obtenir les fenêtres et noeuds racines selon le type de fenêtre, le nom de package, le titre ou l'écran
* `Fonctionnalité` Méthodes auto.explain/dump et objet auto.stats, pour diagnostiquer les sélecteurs, exporter les arbres de noeuds et mesurer la durée des requêtes
* `Fonctionnalité` La méthode auto.registerEvent accepte des options de déduplication et d'anti-rebond des événements (désactivées par défaut)
* `Fonctionnalité` La méthode auto.setFlags accepte le drapeau appWindowsFallback, pour utiliser la racine d'une fenêtre d'application lorsque celle de la fenêtre active est indisponible (désactivé par défaut)
* `Fonctionnalité` Sélection multiple et copie/déplacement/suppression par lots dans le gestionnaire de fichiers
* `Fonctionnalité` Barre de chemin dans le gestionnaire de fichiers, permettant d'accéder aux dossiers parents par un appui et de parcourir le chemin horizontalement
* `Fonctionnalité` Disposition compacte du menu de la barre de chemin du gestionnaire de fichiers, avec navigation retour/avancer/remonter/actualiser, retour au dossier de travail, copie du chemin et définition du dossier de travail, affichage de la capacité et bascule entre stockage interne et cartes de stockage, ainsi que réglages rapides des fichiers cachés et des extensions
* `Fonctionnalité` Le gestionnaire de fichiers permet de parcourir les dossiers restreints via Shizuku ou Root, d'ouvrir et modifier les fichiers texte et d'enregistrer les changements après vérification de l'original; les modifications sont conservées avec des indications en cas de perte d'autorisation, de conflit de fichiers ou de résultat d'enregistrement incertain, tandis que les autres applications utilisent un accès sur place ou à la demande en lecture seule, avec copies temporaires de compatibilité après confirmation; prend en charge la copie/le déplacement/le renommage/la suppression définitive des fichiers ou dossiers et la sélection multiple, sans remplacer les destinations existantes lors de la copie/du déplacement, avec affichage de l'état des autorisations et des chemins conservés en cas d'échec (autorisation correspondante requise, l'étendue de l'accès dépend du système; le déplacement entre systèmes de fichiers se limite au stockage partagé)
* `Fonctionnalité` Option "Exécution flottante" dans le menu des scripts du gestionnaire de fichiers, pour exécuter/arrêter les scripts, modifier les fichiers et consulter les journaux via une barre d'outils flottante
* `Fonctionnalité` Option "Exécuter avec AutoJs4" dans le menu des scripts du gestionnaire de fichiers, pour exécuter un script ou projet avec le moteur AutoJs4 et examiner ses résultats (nécessite le plugin AutoJs4 Runtime)
* `Fonctionnalité` Option "Exécuter avec AutoJs4 et comparer" dans le menu des scripts du gestionnaire de fichiers, pour exécuter successivement le même script ou projet avec AutoJs6 et AutoJs4 et comparer les différences (nécessite le plugin AutoJs4 Runtime)
* `Fonctionnalité` Option "Copier le chemin" dans les menus des fichiers et dossiers du gestionnaire de fichiers, pour copier des chemins absolus dans le presse-papiers, avec copie par lot (un chemin par ligne) sous "Plus" dans la barre de sélection
* `Fonctionnalité` Le gestionnaire de fichiers permet de parcourir, prévisualiser, compresser et extraire des archives via le plugin 3-Stack Archive, avec mots de passe, volumes multiples et ouverture ou exécution directe des fichiers archivés
* `Fonctionnalité` Le gestionnaire de fichiers permet de prévisualiser les fichiers Markdown/HTML via les plugins Markdown Previewer/HTML Previewer, avec thèmes, CSS personnalisé et mode plein écran
* `Fonctionnalité` Le gestionnaire de fichiers permet d'afficher des images, de lire des contenus audio/vidéo et d'ouvrir des livres EPUB via les plugins correspondants, avec lecture audio en arrière-plan et commandes dans les notifications
* `Fonctionnalité` La visionneuse d'images permet de changer d'image dans le même dossier, de zoomer avec deux doigts, d'afficher les données EXIF et de passer en plein écran, avec modification et conversion de format via le plugin 3-Maple Image
* `Fonctionnalité` Les fichiers ouverts dans un plugin depuis le gestionnaire de fichiers peuvent être placés dans la corbeille depuis le plugin et récupérés par annulation (nécessite la prise en charge par le plugin)
* `Fonctionnalité` Lorsque plusieurs actions de plugins conviennent aux fichiers sélectionnés dans le gestionnaire de fichiers, la barre de sélection propose les autres sous "Plus" (par exemple la conversion d'images par lot avec 3-Maple Image)
* `Fonctionnalité` Le gestionnaire de fichiers permet de consulter les informations et manifestes des paquets APK/APKS/XAPK/APKM/APKZ/AAB, et d'installer des APK et conteneurs d'APK fractionnés
* `Fonctionnalité` L'éditeur de code peut charger Ace via le plugin Ace Editor, avec choix du moteur dans les paramètres et accès au centre de plugins si le plugin est indisponible
* `Fonctionnalité` L'éditeur Ace propose complétion, indications de paramètres, documentation au survol et diagnostics; les projets TypeScript permettent l'accès aux définitions, le renommage des symboles et les importations automatiques (nécessite le plugin TypeScript Engine)
* `Fonctionnalité` L'éditeur Ace propose un panneau de raccourcis, un historique des actions et des actions favorites
* `Fonctionnalité` L'éditeur Ace permet de déplier ou de plier tous les blocs de code (nécessite une mise à jour du plugin Ace Editor) (accès: Editer > Pliage > Tout déplier/Tout plier)
* `Fonctionnalité` Groupe "Suggestions intelligentes" dans les paramètres de l'éditeur de code (moteur Ace uniquement), pour configurer les déclencheurs et délais de complétion et d'indication des paramètres
* `Fonctionnalité` L'éditeur de code permet de convertir l'encodage et de recharger les fichiers, avec affichage du nom, de la position du curseur et de l'encodage dans la barre d'information
* `Fonctionnalité` Mode plein écran immersif dans l'éditeur de code
* `Fonctionnalité` Panneau de journaux inférieur dans l'éditeur de code, avec démarrage/arrêt des scripts et accès au code source des cadres de pile (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Fonctionnalité` Page de paramètres dédiée à l'éditeur de code, proposant thème, police, numéros de ligne, retour automatique à la ligne, seuil de coloration du code et affichage du panneau de journaux
* `Fonctionnalité` Les réglages de l'éditeur de code ajoutent la mise en évidence des guides d'indentation, les icônes vectorielles de gouttière, les barres de défilement (avec masquage automatique), les espaces dans les sélections, une loupe de glissement, la mise en évidence des symboles identiques et les aperçus de complétion en ligne (moteur Ace uniquement, plugin Ace Editor 2.0.0 ou ultérieur requis)
* `Fonctionnalité` La page des journaux et le panneau inférieur de l'éditeur permettent d'exécuter des extraits TypeScript (nécessite le plugin TypeScript Engine)
* `Fonctionnalité` Outil "Prélever une couleur à l'écran" dans le tiroir d'accueil (nécessite le plugin 3-Color Picker)
* `Fonctionnalité` Tâches planifiées mensuelles/annuelles/avec compte à rebours et conditions de fin de répétition _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `Fonctionnalité` Création de tâches planifiées et de diffusion pour les dossiers de projet
* `Fonctionnalité` Type de tâche de diffusion "À l'arrivée d'un message", avec filtrage par expéditeur et objet; les informations du message sont accessibles via engines.myEngine().execArgv.mail (nécessite le plugin 3-Stamp Mail)
* `Fonctionnalité` L'analyse de disposition permet de copier des sélecteurs combinant plusieurs propriétés, d'enregistrer les arbres de noeuds et de les analyser hors ligne
* `Fonctionnalité` Barre de recherche dans l'analyse de disposition, avec correspondance par mot-clé ou expression régulière sur text/desc/id/className; la fenêtre hiérarchique conserve les noeuds correspondants et leurs ancêtres, celle des limites ne trace que leurs contours, avec liste de résultats, navigation entre correspondances et historique
* `Fonctionnalité` La recherche d'analyse de disposition accepte des filtres (propriétés booléennes, classes prédéfinies, plages de profondeur et de taille), une zone sélectionnée, le mode champs (`k=v`) et le mode sélecteur (`$()`), avec mémorisation des réglages et du mode entre sessions
* `Fonctionnalité` Option "Copier comme sélecteur" dans l'analyse de disposition, convertissant les mots-clés, conditions de champs, filtres et zone sélectionnée en sélecteur chaîné
* `Fonctionnalité` La génération de code de l'analyse de disposition accepte une cible unique ou une collection, le mode d'attente, les paramètres d'action, les styles classique/Flow, le positionnement relatif et les solutions alternatives, avec prévisualisation des correspondances dans la disposition
* `Fonctionnalité` Bibliothèques de couleurs de thème personnalisées, créables ou clonables, avec ajout depuis la palette, d'autres bibliothèques ou l'historique, renommage, modification des valeurs, tri par glissement et suppression par lots
* `Fonctionnalité` Bibliothèques de couleurs intelligentes, changeant automatiquement de groupe selon le mode nuit, la tranche horaire, le lever/coucher du soleil, la batterie ou l'état des scripts, avec changement planifié ou aléatoire et exceptions par date; préréglages "Paires jour/nuit Material Design", "Couleur de fond" et "Suivre le fond d'écran", et génération à partir d'une couleur initiale
* `Fonctionnalité` Importation et exportation des bibliothèques de couleurs en JSON (exportation multiple et instantanés des préréglages)
* `Fonctionnalité` "Outils de palette" dans les détails d'une couleur (harmonies, gammes, contraste et simulation des déficiences de vision des couleurs), avec favoris, épinglage et bibliothèque virtuelle "Fréquentes et recommandées"
* `Fonctionnalité` Réglage "Palette dérivée" des couleurs du thème (désactivé par défaut), pour déduire automatiquement les couleurs d'accentuation et sombre de la couleur principale
* `Fonctionnalité` Politiques de conservation de la corbeille, de l'historique des versions et des brouillons d'urgence dans les paramètres, avec nettoyage par ancienneté, nombre et volume
* `Fonctionnalité` Liste dédiée à l'historique des versions, avec prévisualisation, comparaison des différences, recherche, tri et suppression par lots
* `Fonctionnalité` Instantanés manuels, épinglage, modification des étiquettes, exportation et enregistrement direct après restauration dans l'historique des versions
* `Fonctionnalité` Restauration par lots, aperçu, détails, avertissements d'expiration et annulation après déplacement dans la corbeille, avec détection et nettoyage des entrées invalides
* `Fonctionnalité` Les applications empaquetées prennent en charge les projets et scripts Node.js, avec le plugin Node.js Runtime requis à l'exécution (guide d'installation affiché s'il manque)
* `Fonctionnalité` Les applications empaquetées permettent de compiler les projets Rhino TypeScript lors de la construction, de chiffrer les résultats et d'ajouter des Source Map
* `Fonctionnalité` La configuration des applications empaquetées permet d'afficher ou masquer les journaux et de définir les raccourcis du lanceur _[`issue #550`](http://issues.autojs6.com/550)_
* `Fonctionnalité` Connexion au plugin VSCode par code QR, confirmation d'appariement et découverte sur le réseau local (nécessite le plugin VSCode 1.1.0+)
* `Fonctionnalité` Gestion distante des fichiers et exécution des scripts de l'appareil via le plugin VSCode, avec progression des transferts volumineux (nécessite le plugin VSCode 1.1.0+)
* `Fonctionnalité` Gestionnaire de connexions en modes client et serveur, pour consulter l'état, gérer les clients et accéder à l'historique des adresses
* `Fonctionnalité` Le mode client permet la reconnexion après une coupure imprévue, de nouvelles tentatives au retour du réseau et la restauration de la connexion au démarrage de l'application
* `Fonctionnalité` Module Node.js screen_metrics et fonctions globales de conversion compatibles, partageant les règles de l'hôte avec le SDK Bun et suivant la rotation de l'écran
* `Fonctionnalité` Client explicite de l'API hôte Bun pour Pinyin, Pinyin4j, les coordonnées et la console, avec des interrupteurs de capacités indépendants
* `Fonctionnalité` Node.js et Bun prennent en charge OpenCC asynchrone, les codecs, la lecture des pixels, les transformations et la recherche de couleurs; accès aux fichiers image de Bun autorisé séparément
* `Fonctionnalité` Les Workers Node.js utilisent OpenCC et les images via le runtime parent, avec CJS, ESM, Workers imbriqués et transfert binaire
* `Fonctionnalité` Bun propose la capture écran avec autorisation distincte, consentement Android, nettoyage après expiration/annulation et nouvelle demande
* `Fonctionnalité` Les Workers Node.js proposent pinyin, pinyin4j, les commandes de console et les coordonnées, avec état parent partagé et flux natifs conservés
* `Fonctionnalité` Bun screenRecorder produit des MP4 H.264 avec audio AAC du microphone, des applications ou mixé et captures pendant la vidéo, avec autorisations audio distinctes et nettoyage après annulation
* `Fonctionnalité` Le thread principal Node.js et les Workers prennent en charge la capture H.264 MP4 avec audio AAC du microphone, de lecture ou mixte, les captures simultanees et la suppression des enregistrements Worker non sauvegardes
* `Fonctionnalité` Node.js/Bun images prend en charge les filtres avancés, la conversion des couleurs, les recherches multicolores, les cercles, les mesures de similarité et les URL
* `Fonctionnalité` Node.js/Bun images prend en charge SIFT/ORB avec coordonnées de scène, géométrie ObjectFrame et handles Mat/caractéristiques propres à chaque exécution
* `Fonctionnalité` Node.js/Bun images prend en charge la quantification PNG et la compression avec mesures de qualité/mémoire, résultats binaires et sortie par descripteur de fichier
* `Fonctionnalité` Node.js/Bun UI et Compose prennent en charge les interfaces natives, composants de données, états réactifs et événements, avec autorisations distinctes et fermeture automatique
* `Fonctionnalité` Node.js/Bun prennent en charge les handles View propres à une exécution, les fabriques AndroidView asynchrones, les conteneurs XML Compose, la fermeture des Workers et les applications empaquetées
* `Fonctionnalité` Node.js/Bun prennent en charge les formulaires et interfaces XML courants, les propriétés View asynchrones, les affectations ordonnées, la reprise après erreur et les Workers
* `Fonctionnalité` Node.js/Bun: Chargement WebView et résultats JavaScript, panneaux Drawer, navigation et menus Toolbar, pages ViewPager et événements natifs asynchrones
* `Fonctionnalité` Node.js/Bun: Téléchargements HTTP(S) WebView, sélection de fichiers système et rappels WebViewClient/WebChromeClient asynchrones avec règles natives de navigation et de ressources
* `Fonctionnalité` Node.js/Bun: Capture photo WebView en pleine résolution, sélection de fichiers et dossiers, lecture des octets et téléchargements blob/data vers les emplacements choisis
* `Fonctionnalité` Configuration timeoutMs des projets Bun pour les interactions photo et fichiers, y compris les applications empaquetées
* `Fonctionnalité` Node.js/Bun: Capture vidéo WebView et enregistrement audio AAC/M4A au premier plan, avec options média, délais configurables et nettoyage des fichiers temporaires
* `Fonctionnalité` Flux audio et video WebView getUserMedia/MediaRecorder, attente des Promise de page, demandes explicites de permissions multimedia Android et cycle de vie des Worker
* `Fonctionnalité` Adaptateur de partage ecran WebView, capture audio par service de premier plan et enregistrement camera natif en arriere-plan pour Node.js/Bun et Node Worker
* `Fonctionnalité` API Bun pour accessibilité, applications et presse-papiers alignées sur Node.js, avec autorisations distinctes et applications empaquetées
* `Fonctionnalité` API Bun de fichiers et de stockage persistant, avec autorisations distinctes et stockage partagé avec Node.js
* `Fonctionnalité` API hôtes database/sqlite pour Bun et les Workers Node.js, avec requêtes paramétrées, transactions et persistance
* `Fonctionnalité` Mesures et abonnements aux capteurs pour Bun et les Workers Node.js, avec annulation et libération automatique des écouteurs
* `Fonctionnalité` Shell hôte avec flux, autorisation Shizuku et nettoyage des processus pour Bun, Node.js et les Workers
* `Fonctionnalité` Événements et notifications hôtes pour Bun et les Workers Node.js, avec nettoyage indépendant des abonnements et notifications
* `Fonctionnalité` Commandes de périphérique et dialogues pour Bun et les Workers Node.js, avec luminosité, volume et nettoyage indépendant des fenêtres
* `Fonctionnalité` Objets Android pour Node.js, Bun et les Workers, avec Paint, ColorStateList, couleurs du thème et URI FileProvider
* `Fonctionnalité` API persistantes de calendrier et IntentTask pour Node.js et Bun, avec tâches quotidiennes, hebdomadaires, mensuelles, annuelles, ponctuelles, différées et de diffusion
* `Fonctionnalité` Interopérabilité Java/Android pour Node.js, Bun et Workers, avec constructeurs et membres publics, tableaux, valeurs long exactes, surcharges explicites et handles Context
* `Fonctionnalité` Callbacks Java, sous-classes générées et chargement JAR/DEX pour Node.js, Bun et Workers, avec retours Promise, appels de la superclasse et libération en fin de session
* `Fonctionnalité` API de synthèse vocale pour Node.js, Bun et Workers avec requêtes des moteurs et voix, lecture, silence, fichiers audio et événements des tâches
* `Fonctionnalité` API speech de reconnaissance vocale et de gestion des modèles pour Node.js, Bun et Workers avec WAV/PCM, dictée et activation vocale, installation/importation, synthèse et annulation
* `Fonctionnalité` Node.js/Bun/Worker: Lecture EPUB, export des ressources, recherche et sessions du lecteur avec événements
* `Correction` Attentes des événements UI Bun bloquant les autres appels hôte et provoquant une expiration de la vérification asynchrone Flow
* `Correction` Impossible de charger dynamiquement avec runtime.loadJar des fichiers class compilés avec des versions Java plus récentes
* `Correction` L'appel de runtime.loadDex sous Android 7.x pouvait corrompre le cache DEX et faire échouer le chargement des classes
* `Correction` Comportements anormaux de certains scripts dus à des incompatibilités de portée, conversion des paramètres et traitement des retours dans Rhino et les modules intégrés
* `Correction` Les propriétés créées par Object.fromEntries avec des clés numériques ou des chaînes numériques étaient inaccessibles et leur affichage dans la console était incomplet _[`Rhino #2518`](https://github.com/mozilla/rhino/issues/2518)_
* `Correction` Les fonctions enveloppées par Proxy et certains rappels JavaScript pouvaient être refusés par les modules intégrés, ou provoquer des paramètres imbriqués et des ambiguïtés de surcharge
* `Correction` Les écouteurs EventEmitter#once pouvaient être appelés plusieurs fois lors d'une émission récursive ou si un rappel levait une exception
* `Correction` Les méthodes app.isInstalled/kill et similaires ne traitaient pas correctement les paramètres de nom de package _[`issue #495`](http://issues.autojs6.com/495)_
* `Correction` Les méthodes shell pouvaient sembler se bloquer sur les appareils sans autorisation Root
* `Correction` Le résultat de sélection multiple de dialogs.multiChoice était converti à tort en chaîne _[`issue #518`](http://issues.autojs6.com/518)_
* `Correction` La fermeture des dialogues d'un script à la fin du fil d'exécution pouvait faire planter l'application
* `Correction` Des rappels expirés pouvaient empêcher la fin automatique d'un script après l'annulation d'une minuterie ou l'arrêt d'un fil secondaire
* `Correction` Les méthodes de focus de floaty.window/rawWindow pouvaient lever des exceptions de fil lorsqu'elles étaient appelées depuis celui du script
* `Correction` Les résultats de console.setPosition/setSize/setGravity et méthodes similaires dépendaient de l'ordre des appels ou du moment d'affichage de la fenêtre
* `Correction` Le déplacement par la barre de titre de la console flottante pouvait échouer et la fenêtre ne pouvait pas s'approcher des bords de l'écran
* `Correction` Les valeurs textuelles des objets ou tableaux affichés par console.log/util.format et méthodes similaires n'étaient pas entourées d'apostrophes, contrairement au format util.inspect d'Auto.js 4
* `Correction` Les options non sécurisées d'une requête http pouvaient laisser les requêtes suivantes ignorer la vérification du certificat et du nom d'hôte
* `Correction` La configuration de sécurité réseau pouvait bloquer involontairement les requêtes HTTP des versions publiées et applications empaquetées
* `Correction` Impossible d'ouvrir correctement le sélecteur de fichiers du composant WebView en mode UI
* `Correction` Les expressions d'attributs dynamiques des modèles d'éléments list/grid en mode UI ne pouvaient pas accéder à l'objet courant (par exemple sa propriété name), provoquant ReferenceError et des éléments vides; les résultats numériques s'affichaient aussi sous la forme 18.0 au lieu de 18
* `Correction` Les scripts UI pouvaient se fermer inopinément lors du passage en écran partagé ou d'un redimensionnement de fenêtre
* `Correction` Les paramètres d'expression régulière des méthodes de sélecteur surchargées, de la méthode globale filter et des sélecteurs de collection fonctionnaient mal
* `Correction` Les sélecteurs contains interprétaient à tort les chaînes ordinaires comme des expressions régulières
* `Correction` Une recherche de composants limitée en nombre pouvait renvoyer trop de résultats ou ne pas recycler les noeuds restants
* `Correction` Analyse des paramètres de boussole et retours incorrects de UiObject#compass et des méthodes pickup/detect
* `Correction` Les méthodes UiObject#child/parent et similaires pouvaient provoquer une récursion infinie
* `Correction` Noms de propriétés incorrects dans la représentation textuelle des sélecteurs et format décimal anormal dans certaines langues
* `Correction` Les exceptions des méthodes globales de sélecteur ne pouvaient pas être interceptées par try/catch
* `Correction` Avec findOnUiThread activé, la recherche de composants pouvait attendre indéfiniment ou dépasser le délai prévu
* `Correction` UiObject#clickBounds pouvait arrêter involontairement la boucle d'événements et invalider les rappels suivants, ou lever une exception sur le fil principal (notamment les scripts en mode "ui")
* `Correction` Le noeud source des rappels auto.registerEvent pouvait provoquer une boucle infinie ou une exception de dépassement d'indice après modification de l'arbre
* `Correction` Après redémarrage du service d'accessibilité, les requêtes pickup et UiSelector pouvaient toujours renvoyer un résultat vide
* `Correction` Les écouteurs auto.stateListener de plusieurs scripts s'écrasaient mutuellement et n'étaient pas supprimés à la fin du script
* `Correction` auto.waitFor pouvait attendre jusqu'au délai maximal alors que le service d'accessibilité distribuait déjà des événements
* `Correction` L'arrêt ou le redémarrage du service d'accessibilité avec Root pouvait ne pas prendre effet
* `Correction` La création d'une page de l'application pouvait réactiver un service d'accessibilité désactivé manuellement par l'utilisateur
* `Correction` Les méthodes auto.start/enable redémarraient le service d'accessibilité alors qu'il était déjà actif
* `Correction` auto.windowRoots pouvait renvoyer un noeud vide lorsque la fenêtre active était indisponible
* `Correction` Incohérence entre l'état et les erreurs signalés par le service d'accessibilité et l'interrupteur du tiroir d'accueil
* `Correction` auto.services ne pouvait pas lire la liste des services activés sans mode d'activation automatique configuré
* `Correction` L'analyse de disposition nécessitait un second déclenchement après activation automatique du service d'accessibilité
* `Correction` Les rappels d'événements et écouteurs de touches enregistrés cessaient de fonctionner après redémarrage du service d'accessibilité
* `Correction` Les opérations globales automator pouvaient provoquer une exception de pointeur nul au premier appel, et les coordonnées verticales des gestes étaient mal mises à l'échelle
* `Correction` Le service d'accessibilité pouvait s'interrompre lors d'opérations répétées telles que back _[`issue #527`](http://issues.autojs6.com/527)_
* `Correction` La méthode Node.js accessibility.ensureEnabled signalait un délai d'appel du pont dépassé au lieu de renvoyer false lorsque le service d'accessibilité était activé mais ne se liait pas à temps
* `Correction` La méthode settings.isEnabled renvoyait false pour les réglages jamais modifiés d'activation automatique du service d'accessibilité avec l'accès root et de contrôle de l'exécution des scripts par la touche de volume, au lieu de l'état activé par défaut affiché dans les paramètres
* `Correction` L'observateur de notifications signalait les notifications système comme messages flottants et omettait les événements de notification
* `Correction` Sous Android 14+, la demande d'autorisation de capture d'écran pouvait faire planter l'application à cause d'un échec de démarrage du service au premier plan _[`issue #516`](http://issues.autojs6.com/516)_
* `Correction` Les appels OCR répétés depuis plusieurs fils ou fichiers pouvaient demander à nouveau l'autorisation de capture d'écran _[`issue #551`](http://issues.autojs6.com/551)_
* `Correction` En cas d'échec de sélection d'un plugin OCR, le message d'erreur ne correspondait pas au moteur réel ni à son état d'installation
* `Correction` Le service du plugin Paddle OCR ne pouvait pas récupérer après déconnexion ou démarrer automatiquement lorsqu'il était inactif _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_ _[`issue #517`](http://issues.autojs6.com/517)_
* `Correction` L'utilisation successive de Paddle OCR et Rapid OCR pouvait faire planter une application empaquetée
* `Correction` Paddle OCR était inutilisable dans les applications empaquetées si les plugins Paddle OCR v3 et v4/v5/v6 étaient sélectionnés ensemble (les applications empaquetées ne prennent en charge que le plugin PP-OCRv3)
* `Correction` Des paramètres incorrects de images.resize pouvaient faire planter l'application
* `Correction` Certaines méthodes images pouvaient recycler trop tôt les images, provoquer des fuites de mémoire native ou recycler les images d'autres scripts
* `Correction` Résultats incorrects de l'algorithme hs dans colors.isSimilar/images.detectColor et traitement incorrect des composantes décimales ou en pourcentage dans colors.rgb/rgba
* `Correction` Résultats de comparaison et traitement des images en niveaux de gris ou RGBA incorrects dans images.isEqual/mse/ncc/isGrayscale et méthodes similaires
* `Correction` Perte de transparence dans images.invert, inversion des canaux rouge et bleu dans ImageWrapper#saveTo et traitement incorrect des zones hors limites dans images.clip
* `Correction` Les méthodes telles que images.load pouvaient laisser des connexions ouvertes ou des images temporaires non libérées
* `Correction` Correspondance aux bords, précision des coordonnées, tri et limitation du nombre de résultats incorrects dans images.findImage/matchTemplate
* `Correction` images.matchTemplate traitait mal les images avec nombres de canaux différents, les modèles unis et les masques transparents, et MatchingResult#sortBy refusait les résultats de comparaison décimaux
* `Correction` Les contours renvoyés par images.matchFeatures ne correspondaient pas aux positions réelles, avec anomalies de détection sur petites images, d'affichage des correspondances et de libération des ressources
* `Correction` La mémoire utilisée pouvait augmenter continuellement après des exécutions répétées de scripts (correction expérimentale) _[`issue #548`](http://issues.autojs6.com/548)_
* `Correction` Le chemin ou l'extension d'origine pouvait être perdu lors d'une exécution de script par URI externe ou en arrière-plan _[`issue #545`](http://issues.autojs6.com/545)_
* `Correction` Tasker ne pouvait pas appeler et exécuter correctement les scripts _[`issue #501`](http://issues.autojs6.com/501)_
* `Correction` La mise à jour groupée des widgets pouvait supprimer à tort les chemins de scripts et les associations de clics
* `Correction` Les scripts avec BOM exécutés directement dans le gestionnaire de fichiers n'étaient pas lus avec leur encodage réel
* `Correction` Les projets de scripts sans informations d'empaquetage étaient mal reconnus ou n'utilisaient pas le point d'entrée indiqué dans project.json
* `Correction` Les fichiers auto exécutés depuis le gestionnaire de fichiers, une tâche planifiée ou une intention externe étaient traités comme des scripts JavaScript
* `Correction` Impossible de consulter certaines informations APK ou absence d'accès au manifeste dans le gestionnaire de fichiers
* `Correction` Une mauvaise gestion des noms lors de l'importation par URI externe pouvait écrire hors du dossier cible ou écraser un fichier homonyme
* `Correction` Le gestionnaire de fichiers pouvait clignoter ou réinitialiser le défilement après retour à la page ou suppression d'une entrée de corbeille
* `Correction` Le gestionnaire de fichiers distinguait mal les fichiers TypeScript des vidéos de même extension, et certains fichiers source n'avaient pas d'option d'exécution
* `Correction` Les messages d'action du gestionnaire de fichiers pouvaient s'afficher plusieurs fois
* `Correction` Les éléments de listes comme le gestionnaire de fichiers pouvaient garder un fond gris après l'ouverture d'une autre page puis le retour, sur certains écrans tactiles
* `Correction` Le nombre de "Exécution d'une tâche" dans la liste des tâches ne correspondait pas aux éléments affichés, et les scripts UI déjà quittés restaient listés comme en cours
* `Correction` Résolution incorrecte des chemins de fichiers multimédias et d'images, et affichage des chemins de cache dans les détails MediaInfo
* `Correction` Un échec de consultation des détails multimédias pouvait faire planter l'application, et certains champs de métadonnées vidéo étaient mal affichés
* `Correction` L'historique des versions ne suivait pas les déplacements ou renommages de fichiers, ou était associé à tort à une copie de contenu identique
* `Correction` La corbeille et l'historique des versions accédaient mal au stockage interne en environnement multiutilisateur ou profil professionnel
* `Correction` La restauration avec écrasement depuis l'historique ou la corbeille pouvait perdre le contenu original ou laisser un fichier incomplet
* `Correction` Renommage automatique incorrect des dossiers contenant un point lors de la restauration depuis la corbeille
* `Correction` L'historique des versions n'affichait pas toutes les versions conservées et son mode d'affichage devenait incorrect après changement d'opération
* `Correction` Le nettoyage de l'historique pouvait supprimer de nouveaux instantanés encore en cours d'enregistrement
* `Correction` La corbeille annonçait la fin de l'opération même après échec de restauration
* `Correction` Lors de la restauration de dossiers, la corbeille pouvait écrire des entrées d'archive anormales hors du dossier cible
* `Correction` L'éditeur de code pouvait perdre le curseur, la sélection ou les modifications non enregistrées après recréation de la page
* `Correction` Le clavier virtuel ou la barre de saisie pouvait masquer ou comprimer le contenu de l'éditeur et de la page des journaux
* `Correction` Les menus "Plus" des boîtes de dialogue de l'éditeur étaient difficiles à lire, leurs couleurs de texte et de fond étant proches lorsque le thème de l'éditeur différait du mode clair/sombre de l'application
* `Correction` L'éditeur de code natif (JsDroidEditor) utilisait la taille de texte 15 lorsqu'aucune taille n'était définie, au lieu de la valeur par défaut 14 affichée dans les paramètres
* `Correction` Anomalies de sauvegarde d'état, de couleur du curseur et de langue de l'indication du champ de recherche de l'accueil
* `Correction` Des gestes multitactiles anormaux dans l'onglet Documentation de l'accueil pouvaient faire planter l'application
* `Correction` Animation de transition absente lors du passage à l'onglet Fichiers de l'accueil
* `Correction` Composants tronqués, boutons inférieurs absents et zones d'appui des interrupteurs trop petites dans le tiroir d'accueil sur écran étroit
* `Correction` À la fermeture ou recréation de l'accueil, un écouteur de nettoyage du tiroir accédait à un contexte libéré et faisait planter l'application
* `Correction` Le fond de l'écran de démarrage ne couvrait pas la zone du bord court des écrans à encoche
* `Correction` Informations de package et d'Activity incorrectes pour l'application récente dans le menu du bouton flottant _[`issue #529`](http://issues.autojs6.com/529)_
* `Correction` Le dialogue "Exécuter un script" du bouton flottant pouvait ne pas être restauré après réduction (correction expérimentale) _[`issue #500`](http://issues.autojs6.com/500)_
* `Correction` Le code de collection de composants produit par l'analyse de disposition pouvait contenir des instructions invalides
* `Correction` Contraste insuffisant des textes/composants des fenêtres flottantes et dialogues d'analyse de disposition avec certaines couleurs de thème ou en mode nuit
* `Correction` Une couleur Material Design choisie dans la disposition classique n'était pas retrouvée par "Localiser la couleur actuelle" après changement de disposition, et la page Material Design n'affichait pas son résumé
* `Correction` Les critères de recherche persistaient dans l'onglet Plugins et le centre de plugins après fermeture de la recherche ou changement d'onglet
* `Correction` Le centre de plugins sélectionnait à tort un paquet x86_64 sur les appareils x86
* `Correction` Impossible de terminer l'activation d'un plugin après autorisation réussie
* `Correction` Le centre de plugins interprétait mal certaines dates avec noms de mois abrégés
* `Correction` Anomalies de cache d'icônes, de changement de mode nuit et d'état d'actualisation par glissement dans le centre de plugins
* `Correction` L'actualisation du centre de plugins pouvait perdre des plugins installés, afficher une liste vide ou réinitialiser le défilement
* `Correction` Le centre de plugins pouvait installer la mauvaise variante ou afficher plusieurs fois les modèles d'empaquetage et indications de mise à jour
* `Correction` Après actualisation réussie de l'index, le centre de plugins rétablissait depuis le cache d'anciens plugins supprimés ou renommés
* `Correction` Les détails d'un plugin ouverts immédiatement après démarrage à froid clignotaient car le dialogue entier était recréé à l'actualisation de la liste
* `Correction` Le centre de plugins maintenait désactivé un plugin temporairement inaccessible, notamment pendant une mise à jour
* `Correction` L'écouteur de demande d'autorisation Shizuku n'était pas correctement désinscrit après destruction
* `Correction` Impossible de poursuivre la confirmation d'installation d'un APK en arrière-plan
* `Correction` L'annulation d'un empaquetage APK pouvait être signalée à tort comme un échec de construction
* `Correction` Anomalies de vérification de cohérence des certificats de signature des APK empaquetés et de reconnaissance des signatures sous Android 9-11
* `Correction` L'absence de composants d'exécution dans l'hôte ou une application empaquetée pouvait rendre la bibliothèque native shell absente ou d'ABI incorrecte et faire planter l'application
* `Correction` L'empaquetage APK conservait silencieusement une bibliothèque native parmi plusieurs de même nom mais de contenus différents (il s'arrête désormais en indiquant leurs sources)
* `Correction` Les scripts UI empaquetés affichaient parfois une page vide avant de se fermer au démarrage
* `Correction` Les fichiers supprimés restaient sur l'appareil ou dans le cache lors de l'enregistrement d'un projet via le plugin VSCode _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `Correction` Le traitement des journaux pouvait épuiser la mémoire à la réception d'une archive de projet par le plugin VSCode _[`issue #523`](http://issues.autojs6.com/523)_
* `Correction` Une connexion VSCode périmée bloquait l'envoi des journaux et empêchait les nouvelles connexions de les recevoir _[`issue #385`](http://issues.autojs6.com/385)_
* `Correction` L'affichage d'un dialogue sur une page détruite après échec de connexion au plugin VSCode pouvait provoquer une exception
* `Correction` Les adresses de connexion réussie en mode client n'étaient pas enregistrées dans l'historique
* `Correction` La recherche de mises à jour ne sélectionnait pas les paquets selon la priorité ABI de l'appareil
* `Correction` Les constructions dans IntelliJ IDEA pouvaient échouer à résoudre les dépendances de modules, sélectionner les tâches de compilation ou localiser l'APK courant
* `Correction` Mémoire insuffisante pour la compilation Kotlin lors de constructions multivariantes
* `Correction` Erreurs de résolution du SDK et du chemin des modèles d'empaquetage dans les constructions Gradle
* `Correction` cXy/cYx ignoraient le mode de coordonnées absolues avec false, inversaient les rapports largeur/hauteur en paysage et rejetaient certains appels après la modification d'une seule base
* `Correction` setScaleBases modifiant l'autre axe et consommant son unique affectation lorsque le second argument est invalide ou qu'une base est déjà définie
* `Correction` device.width/height et les conversions de coordonnées lisant des dimensions nulles lorsqu'une activité est recréée pendant la lecture des dimensions par un script
* `Correction` Node.js pinyin/pinyin4j limité à un petit dictionnaire intégré; les plugins Pinyin installés sont désormais utilisés
* `Correction` Méthodes Node.js de contrôle de la fenêtre et du style de console sans effet
* `Correction` Perte des derniers journaux Bun lorsque le résultat synchrone arrivait avant les rappels de sortie en fin de script
* `Correction` Fin prématurée du consentement lorsque son activité est masquée, dialogues persistants après annulation et dimensions de capture ignorées
* `Correction` Constantes numériques style/mode de pinyin rejetées après conversion en nombres JSON ou Rhino
* `Correction` La capture ignore les tampons entièrement transparents non composés et conserve les images noires valides
* `Correction` Les liaisons AndroidView conservent les View pendant la recréation du rendu Compose, évitant leur libération prématurée ou leur réutilisation par une autre session
* `Correction` JSON UI restaure les listes sans sélection et les onglets lors de la recréation de Activity, sans traiter les événements comme des propriétés View
* `Correction` Perte des types null/numériques dans les requêtes, comptage incorrect des INSERT multiples et connexions retenues par les transactions inachevées
* `Correction` Écouteurs de capteurs conservés après annulation, échec de lecture ou inscription pendant la destruction du fournisseur
* `Correction` Blocage du nettoyage des commandes ignorant SIGTERM, avec arrêt des descendants avant terminaison
* `Correction` Notifications de scripts distincts portant le même ID numérique qui se remplacent ou se suppriment
* `Correction` Valeurs obsolètes après la mise à jour des dialogues de progression et fenêtres restant ouvertes après annulation
* `Correction` Décalage de l'heure locale des tâches quotidiennes et hebdomadaires lors des changements d'heure
* `Correction` Conflits entre récepteurs IntentTask locaux et externes et inscriptions restantes après modification ou suppression
* `Correction` URI FileProvider des applications générées utilisant l'autorité du modèle au lieu de celle du manifeste réel
* `Correction` Échec des imports ESM Node.js lorsque le module hôte exporte un nom réservé tel que java.new
* `Correction` Arrêt prématuré de la page de lancement des applications Bun masquées, exposant le processus à la récupération pendant le démarrage à froid
* `Correction` tts.engines liste les moteurs installés même si le moteur par défaut ne peut pas démarrer
* `Correction` La progression du téléchargement des pièces jointes mail ne revient plus à zéro lorsque la sortie de débogage est activée
* `Amélioration` Initialisation différée des composants non essentiels pour accélérer le démarrage de l'application
* `Amélioration` Les dialogues d'autorisation ne s'ouvrent plus automatiquement au démarrage; l'absence d'autorisation de gestion de tous les fichiers est signalée sur l'accueil avec accès direct à son octroi
* `Amélioration` Adaptation de l'application et des applications empaquetées à Android 17, avec indications améliorées sur l'accès au réseau local, la protection avancée et les restrictions audio en arrière-plan
* `Amélioration` Prise en charge des pages mémoire de 16 KB dans l'application et les applications empaquetées, avec signalement des bibliothèques natives incompatibles à l'installation, au chargement et à l'empaquetage des plugins
* `Amélioration` Affichage multilingue amélioré des interfaces et messages d'exception
* `Amélioration` Réduction du volume des paquets par allègement des dépendances et ressources linguistiques et graphiques
* `Amélioration` La recherche de mises à jour utilise l'API REST GitHub pour les versions et paquets, avec rendu local des notes de mise à jour
* `Amélioration` Historiques des versions chinois simplifié et anglais intégrés; les autres langues sont téléchargées et mises en cache (hors ligne, priorité au cache, sinon affichage en anglais)
* `Amélioration` Documentation du projet hors ligne fournie par le plugin Offline Docs, installable et actualisable séparément, avec changement immédiat de source documentaire
* `Amélioration` Modèles d'empaquetage fournis par le plugin APK Builder Template, avec vérification de sa signature, de la version de l'hôte et de la compatibilité du modèle lors de l'empaquetage
* `Amélioration` Fonctions ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization déplacées vers des plugins
* `Amélioration` Fonctions OpenCC/Pinyin/Pinyin4j/MediaInfo déplacées vers des plugins
* `Amélioration` Les couleurs de thème, le mode nuit et la langue des plugins officiels peuvent suivre les paramètres de l'hôte
* `Amélioration` Installation et mise à jour en ligne, choix des variantes, paramètres, instructions et historique améliorés dans le centre de plugins
* `Amélioration` Les icônes du centre de plugins réagissent aux appuis courts/longs selon leur installation: les plugins installés ouvrent les détails système de l'application/leur page de lancement autonome (avec un message si aucune page n'existe), les autres ouvrent les détails du plugin/lancent son installation
* `Amélioration` Le menu FAB de l'onglet Plugins de l'accueil donne la priorité à l'assistant d'installation, aux dépôts officiels et tiers et au centre de plugins; l'installation par URL/fichier local et les actions de gestion sont regroupées sous "Plus"
* `Amélioration` Tri à deux niveaux et sens indépendant par niveau dans le centre de plugins; par défaut, installés > désactivés > non installés, puis date de mise à jour décroissante au sein de chaque état
* `Amélioration` Les entrées restent en place et actualisent leur état après une action explicite ou une modification de paramètres de plugin; l'actualisation automatique continue normalement, et le tri normal revient après actualisation manuelle ou changement de tri/filtre
* `Amélioration` Montage automatique des plugins SDK et détection des ABI disponibles dans le centre de plugins
* `Amélioration` Activation automatique possible après une première installation de plugin officiel depuis le centre; les installations externes et réinstallations conservent leur politique d'activation précédente
* `Amélioration` Cache et chargement asynchrone pour fluidifier l'affichage et l'actualisation de la liste des plugins, avec indications de récupération après échec d'activation
* `Amélioration` Un appui long sur le bouton flottant de l'onglet Plugins ouvre le centre de plugins
* `Amélioration` Le dialogue d'information distingue les ABI prises en charge par le plugin et celles de son paquet installé
* `Amélioration` Cache des données d'initialisation de Rhino pour accélérer le démarrage des scripts, affichage plus rapide des grands tableaux et meilleure compatibilité de la réflexion Java avec les anciens Android
* `Amélioration` Synchronisation du moteur Rhino avec l'amont, prise en charge de new.target et amélioration de la compatibilité des portées, TypedArray et du débogueur
* `Amélioration` La méthode runtime.loadJar peut charger des JAR via le plugin DEX Compiler, avec diagnostics de compilation et solution de repli en cas d'échec (expérimental, désactivé par défaut)
* `Amélioration` Exécution des scripts TypeScript dans les tâches planifiées, tâches de diffusion, raccourcis et Tasker (nécessite le plugin TypeScript Engine)
* `Amélioration` Dictionnaires de prononciation personnalisés par appel dans le module pinyin, avec améliorations de compare/compact
* `Amélioration` Plusieurs systèmes de romanisation et recherche complète des prononciations dans le module pinyin4j
* `Amélioration` Consultation des types de conversion, conversion par lots et conversion chaînée dans le module opencc
* `Amélioration` Le module ocr sélectionne par défaut un moteur disponible dans l'ordre ML Kit/Paddle/Rapid, avec choix explicite ou retour à la sélection automatique
* `Amélioration` Le module ocr appelé via le pont de l'hôte (plugin Node.js et autres sessions de plugins) sélectionne un moteur activé dans l'ordre ML Kit/Paddle/Rapid lorsqu'aucun moteur n'est indiqué, au lieu d'exiger systématiquement le plugin ML Kit OCR
* `Amélioration` Le module accessibility du pont de l'hôte ajoute captureScreenImage, qui transforme une capture d'écran d'accessibilité (avec repli sur la capture d'écran lorsqu'elle est indisponible) en un handle d'image de session, utilisable directement par les sessions de plugins telles que MCP Server pour la reconnaissance de codes-barres et la recherche d'images ou de couleurs; les sessions de plugins peuvent désormais détenir deux fois leur nombre d'appels simultanés en handles d'image au lieu de 4
* `Amélioration` Lorsque des sessions de plugins orientées modèle telles que MCP Server et 3-Stove Agent lisent une image via le pont (image.readImage), les images dont le plus grand côté dépasse 4096 px sont décodées avec un sous-échantillonnage en puissance de 2 et le résultat porte scale ainsi que les dimensions d'origine, afin que les grandes photos de la galerie n'épuisent pas la mémoire de l'hôte; les scripts du plugin Node.js ne sont pas concernés
* `Amélioration` Les sessions de plugins orientées modèle telles que MCP Server et 3-Stove Agent qui lisent des images ou des informations de médias par chemin via le pont (image.readImage, mediainfo) sont limitées au répertoire de travail, et les écritures (image.saveImage) y restent également; lorsque la requête déclare files.shared_read et qu'AutoJs6 dispose de l'accès à tous les fichiers, le stockage partagé devient lisible en lecture seule (hors Android/data et Android/obb), et files.list ainsi que files.stat peuvent lister le stockage partagé avec le même jeton; les scripts du plugin Node.js ne sont pas concernés
* `Amélioration` Le module app du pont de l'hôte ajoute pluginCapabilities, qui indique en temps réel l'état des familles de plugins OCR, codes-barres, Pinyin, Pinyin4j, MediaInfo, OpenCV et documentation hors ligne (available / not_installed / disabled / incompatible), la version, l'activation et l'autorisation des plugins installés, les méthodes du pont qu'ils prennent en charge et l'entrée du Plugin Center, afin que les sessions de plugins telles que MCP Server affichent les outils selon l'état d'installation et orientent les utilisateurs vers le plugin à installer; la liste des méthodes optionnelles reçue à l'ouverture d'une session de plugin est complétée par la même logique pour les familles disponibles à ce moment
* `Amélioration` L'autorisation de capacités par défaut des sessions du plugin MCP Server s'étend: les méthodes des modules ocr, barcode, pinyin, pinyin4j et mediainfo, image.readImage et les méthodes de recherche d'images / de couleurs, accessibility.readScreenText / captureScreenImage et app.pluginCapabilities, avec leurs jetons de permission (dont files.shared_read pour le stockage partagé en lecture seule); ces méthodes ne fonctionnent que lorsque le plugin correspondant est installé et activé, image.saveImage et image.toBytes restent hors de l'autorisation, et l'autorisation de 3-Stove Agent ne change pas; agent.listScripts / readManifest / execRegistered et les jetons agent et agent.exec s'y ajoutent aussi, pour que MCP Server puisse lister et lancer les scripts enregistrés
* `Amélioration` Les méthodes images.save/saveImage/compress/compressToBytes acceptent des options de quantification PNG (avec transparence conservée) et des limites de pixels et mémoire (nécessite le plugin Image Quantization)
* `Amélioration` La méthode images.readPixels accepte un paramètre ImageWrapper
* `Amélioration` Les méthodes images.matchTemplate/findPointByImage acceptent l'option scales et ajoutent des informations de taille, échelle et zone aux résultats
* `Amélioration` La méthode images.matchFeatures accepte des images ou chemins et davantage d'options, avec informations supplémentaires sur les caractéristiques et résultats
* `Amélioration` Efficacité et précision améliorées de la recherche multicolore, de la correspondance de modèles et de caractéristiques
* `Amélioration` Nouvelle tentative de capture d'écran si aucune image n'est temporairement disponible, avec moins d'impact de la libération des ressources sur les opérations d'image concurrentes
* `Amélioration` Méthodes snapshot/capabilities et comptage des flux multimédias dans mediainfo, avec consultation plus rapide des détails de fichier
* `Amélioration` Compatibilité des paramètres améliorée pour KeyCode et envoi de codes de touches avec l'autorisation Shizuku _[`issue #492`](http://issues.autojs6.com/492)_
* `Amélioration` Activation automatique du service d'accessibilité via Shizuku et synchronisation de son état de démarrage/arrêt avec le plugin compagnon officiel
* `Amélioration` Le noeud source des rappels auto.registerEvent est résolu à la demande (à lire dans le rappel avant Android 13)
* `Amélioration` Les requêtes de fenêtres d'accessibilité ignorent les racines indisponibles, avec recyclage des noeuds amélioré selon la version Android
* `Amélioration` Recyclage des ressources et nouvelles tentatives après limitation système améliorés dans automator.captureScreen, pour éviter des requêtes de capture restant longtemps inachevées
* `Amélioration` Arrondi uniforme des paramètres entiers des sélecteurs globaux et de collection, avec meilleure stabilité des requêtes multithreads et interruptions sur délai
* `Amélioration` Attentes des sélecteurs et de Flow assistées par événements, avec nouvelle tentative anticipée à l'arrivée d'un événement d'accessibilité (activation par le drapeau eventAssistedPolling de auto.setFlags)
* `Amélioration` Rétablissement des méthodes console.rawInput/input, pour lire du texte ou évaluer des expressions depuis la barre de saisie de la console
* `Amélioration` Chaque console mémorise séparément l'affichage et le format de l'heure; relancer un script de même nom remplace sa fenêtre flottante précédente
* `Amélioration` La méthode media.playMusic permet la lecture au premier plan, le focus audio et les commandes de notification, et libère les ressources à la fin du script
* `Amélioration` Suppression des copies intégrées des classes de compatibilité com.stardust; les références portant le préfixe com.stardust sont associées aux implémentations homonymes d'AutoJs6 (218 classes entièrement associées, 90 partiellement, les 46 sans équivalent ne sont plus fournies), pour rendre new et instanceof par nom de classe cohérents avec les objets runtime (voir Documentation du projet > [Environnement AutoJs4](https://docs.autojs6.com/#/autojs4Runtime))
* `Amélioration` Exemples 3-Block Compose, de flux d'automatisation, d'interaction avec la console et de manipulation de livres électroniques, et exemples OCR d'écran améliorés
* `Amélioration` Menu d'actions unifié de la console flottante, pour redimensionner et configurer l'heure, la coloration, la taille de police et l'opacité
* `Amélioration` Couleurs de thème et disposition des menus unifiées dans l'éditeur de code, avec amélioration des polices, du mode lecture seule et de la sélection de texte
* `Amélioration` Le bouton d'exécution de l'éditeur devient un bouton d'arrêt après lancement d'un script
* `Amélioration` Indications de téléchargement de polices, correction des numéros de ligne de destination et détection/enregistrement des encodages améliorés dans l'éditeur
* `Amélioration` Affichage plus rapide de la page lors de l'ouverture de fichiers dans l'éditeur de code
* `Amélioration` Chargement à la demande des informations de paquets dans le gestionnaire de fichiers, avec meilleur affichage des gros paquets et manifestes
* `Amélioration` Accès unifiés aux aperçus et actions de plugins dans le gestionnaire de fichiers, avec actualisation différentielle de la liste conservant le défilement
* `Amélioration` Les sauvegardes des fichiers modifiés avec privilèges peuvent être consultées, restaurées et nettoyées depuis un même emplacement, avec conservation automatique selon le nombre de versions et l'espace cible par fichier (par défaut: jusqu'à 5 versions, cible de 64 MiB et conservation d'au moins la sauvegarde la plus récente); les données de récupération utilisées, modifiées ou dont le résultat est incertain ne sont pas nettoyées automatiquement
* `Amélioration` Le gestionnaire de fichiers privilégie la lecture sur place des fichiers accessibles normalement lors de leur ouverture dans d'autres applications; la lecture à la demande des fichiers restreints en lecture seule prend en charge les fichiers de plus de 1 GiB et le déplacement dans les vidéos lorsque le système le permet; les copies de compatibilité nécessitent une confirmation indiquant l'espace temporaire requis et disponible, avec progression et annulation de la copie
* `Amélioration` L'historique évite les instantanés en double, conserve le contenu avant écrasement et migre lors d'une restauration de corbeille vers un nouveau chemin
* `Amélioration` Indications de capacité, restauration d'entrées endommagées et messages d'erreur améliorés dans la corbeille, avec actualisation différentielle de la liste
* `Amélioration` Résumés de propriétés, mise en évidence du parent, défilement horizontal et sélection progressive des parents dans l'analyse hiérarchique de disposition
* `Amélioration` Suppression des lignes verticales colorées de hiérarchie; les indicateurs de dépliage/repliage passent à gauche du texte et distinguent les états dépliable, déplié et sans enfant
* `Amélioration` Le dialogue d'information des noeuds marque les sélections multiples avec la couleur du thème; les modes de copie "Toujours demander/Copier un sélecteur objet/Copier un sélecteur chaîné" sont mémorisés entre sessions
* `Amélioration` Disposition par groupes unifiée du service d'accessibilité et des gestionnaires de connexions, avec dépliage/repliage progressif et réglages indépendants
* `Amélioration` La liste des tâches et les journaux d'exécution utilisent des chemins abrégés, notamment le dossier de travail, en conservant le vrai nom et l'extension du fichier d'entrée
* `Amélioration` Le bouton flottant et ses fenêtres suivent la langue et le mode nuit de l'application, avec chargement des informations du menu en arrière-plan
* `Amélioration` Le groupe "Connexion à l'ordinateur" du tiroir d'accueil est renommé "Connexions distantes"
* `Amélioration` Les fonds de fenêtre clair et sombre de la bibliothèque par défaut sont réunis sous "Couleur de fond", avec choix clair, sombre, suivi automatique ou inversion automatique; les choix antérieurs à la mise à niveau sont conservés
* `Amélioration` La page des couleurs du thème peut localiser les couleurs des bibliothèques personnalisées et la bibliothèque intelligente active; la recherche inclut leurs couleurs et groupes
* `Amélioration` Les dialogues d'installation APK sont fournis par 3-Setup Installer; si le plugin manque, l'installateur système prend le relais avec une invitation à l'installer; les conteneurs apks/xapk/apkm/apkz nécessitent ce plugin
* `Amélioration` Vérification de présence et de longueur des données dans les connexions VSCode, pour protéger la communication des connexions périmées et données anormales _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `Amélioration` Le plugin VSCode peut enregistrer les fichiers dans le dossier de travail en conservant la structure relative des dossiers _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `Amélioration` Reconnaissance des codes QR plus efficace en mode client, avec accès déplacé vers le menu du dialogue d'adresse
* `Amélioration` Correspondance dynamique des versions compatibles Kotlin/KSP/AGP/R8 dans Gradle, avec réduction de mémoire au démarrage et nettoyage des avertissements de compilation
* `Amélioration` Données de compatibilité des plateformes Gradle et tâches de mise à jour déplacées vers AutoJs6-Gradle-Platform-Versions, avec synchronisation inter-dépôts dans l'outil Scrapers
* `Amélioration` device.getAndroidId accepte le booléen useSystemId, pour obtenir via Shizuku/Root la valeur système de l'utilisateur Android auquel appartient l'application sous Android 8.0+; un échec d'autorisation ou de lecture lève une exception, et les anciens systèmes ignorent ce paramètre
* `Amélioration` cX/cY/cXy/cYx prennent en charge le mode auto explicite, valident les coordonnées et les bases de façon cohérente et préservent les grands résultats finis au-delà de la plage Long
* `Amélioration` Appels hôtes Bun mis en attente dans leur délai, avec annulation de l'attente et exécution successive des déclenchements rapprochés
* `Amélioration` Les instantanés Java utilisent une identité textuelle sans appeler implicitement toString ni déclencher de callback
* `Dépendance` Intégration en module de Android AXML
* `Dépendance` Intégration en module de Android ARSC Editor
* `Dépendance` Intégration en module de APK Signer
* `Dépendance` Intégration en module de APK Verifier
* `Dépendance` Intégration en module de ARSCLib version 1.3.8
* `Dépendance` Intégration en module de Image Quantization version 2.17.0
* `Dépendance` Intégration en module de OpenCV version 4.8.0
* `Dépendance` Ajout de juniversalchardet version 2.0.4
* `Dépendance` Ajout de AndroidX AsyncLayoutInflater version 1.1.0
* `Dépendance` Ajout de AndroidX Test Ext JUnit version 1.3.0
* `Dépendance` Ajout de ZXing Android Embedded version 4.3.0
* `Dépendance` Ajout de Android Hidden API Bypass version 6.1
* `Dépendance` Ajout de NDK version 28.2.13676358 et de CMake version 3.22.1 pour compiler les composants natifs des opérations de fichiers avec privilèges
* `Dépendance` Mise à niveau de Android SDK version 36 -> 37
* `Dépendance` Mise à niveau de Gradle 9 version 9.4.0 -> 9.7.1
* `Dépendance` Mise à niveau de AndroidX Lifecycle version 2.6.2 -> 2.9.4
* `Dépendance` Mise à niveau de AndroidX SavedState version 1.2.1 -> 1.3.2
* `Dépendance` Mise à niveau de AndroidX Emoji2 version 1.3.0 -> 1.4.0
* `Dépendance` Mise à niveau de AndroidX Window version 1.0.0 -> 1.5.0
* `Dépendance` Suppression de Android Annotations version 4.8.0
* `Dépendance` Suppression de Android dx version 1.14
* `Dépendance` Suppression de AndroidX AppCompat (Legacy) version 1.0.2
* `Dépendance` Suppression de AndroidX MultiDex version 2.0.1
* `Dépendance` Suppression de Apache Commons Lang3 version 3.18.0
* `Dépendance` Suppression de APK Parser (module local)
* `Dépendance` Suppression de Commons IO version 2.8.0
* `Dépendance` Suppression de Flexmark Java HTML to Markdown version 0.64.8
* `Dépendance` Suppression de GitHub API version 1.306
* `Dépendance` Suppression de Glide KSP version 5.0.5
* `Dépendance` Suppression de ICU4J version 77.1
* `Dépendance` Suppression de Jackson DataBind version 2.13.4.2
* `Dépendance` Suppression de Jaredrummler Android Device Names version 2.1.1
* `Dépendance` Suppression de JavaMail for Android (bibliothèque locale)
* `Dépendance` Suppression de JCIP Annotations version 1.0
* `Dépendance` Suppression de Jieba Analysis version 1.0.3-SNAPSHOT (modified)
* `Dépendance` Suppression de JUnit Jupiter version 5.13.4
* `Dépendance` Suppression de Log4j version 1.2.17
* `Dépendance` Suppression de Android Logging Log4j version 1.0.3
* `Dépendance` Suppression de MLKit Barcode Scanning version 17.3.0
* `Dépendance` Suppression de MLKit Text Recognition Chinese version 16.0.1
* `Dépendance` Suppression de OpenCC version 1.2.2
* `Dépendance` Suppression de Pinyin4j version 2.5.1
* `Dépendance` Suppression de Rapid OCR (bibliothèque locale)
* `Dépendance` Suppression de Retrofit2 Converter Gson version 3.0.0
* `Dépendance` Suppression de Retrofit2 Kotlin Coroutines Adapter version 0.9.2
* `Dépendance` Suppression de Spongy Castle Prov version 1.58.0.0
* `Dépendance` Suppression de Tiny Sign version 0.9

# v6.7.0

###### 2026/03/14

* `Fonctionnalité` Fonction Centre des plugins, prenant en charge l'installation/la desinstallation/la mise a jour des plugins et autres operations (entree: bouton du tiroir d'accueil/onglet d'accueil)
* `Fonctionnalité` Les raccourcis du lanceur dans la page Parametres ajoutent l'option "Plugins", pour acceder a la page Centre des plugins via un raccourci
* `Fonctionnalité` Fonction Historique des versions, prenant en charge l'affichage/la restauration des versions historiques des fichiers modifiables (entree: bouton du tiroir d'accueil/menu du gestionnaire de fichiers/menu de l'editeur de code)
* `Fonctionnalité` Menu "Clavier multifonction" > "Réglages des symboles" dans l'éditeur de code, pour modifier les symboles et les créer/importer/exporter par niveau de configuration
* `Fonctionnalité` Fonction Corbeille, prenant en charge l'affichage/la restauration des fichiers/dossiers supprimes (entree: bouton du tiroir d'accueil)
* `Fonctionnalité` Plugin Paddle OCR (PP-OCRv5), pour la reconnaissance optique de caracteres
* `Fonctionnalité` Module cvt, pour la conversion d'unites de donnees (voir Documentation du projet > [Conversion d'unites](https://docs.autojs6.com/#/cvt))
* `Fonctionnalité` Methode cvt.bytes, pour la conversion d'unites des donnees d'octets (voir Documentation du projet > [Conversion d'unites](https://docs.autojs6.com/#/cvt))
* `Fonctionnalité` Module fmt, pour le formatage des donnees (voir Documentation du projet > [Formatage](https://docs.autojs6.com/#/fmt))
* `Fonctionnalité` Methode fmt.bytes, pour le formatage des donnees d'octets (voir Documentation du projet > [Formatage](https://docs.autojs6.com/#/fmt))
* `Fonctionnalité` Module zip, pour les operations de compression et de decompression de fichiers (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Zip](https://docs.autojs6.com/#/zip))
* `Fonctionnalité` Module mediainfo, pour afficher les informations detaillees des fichiers medias (voir Documentation du projet > [Informations media](https://docs.autojs6.com/#/mediainfo))
* `Fonctionnalité` Methode s13n.bytes, pour la standardisation des donnees d'octets (voir Documentation du projet > [Standardisation](https://docs.autojs6.com/#/s13n))
* `Fonctionnalité` Methode app.isDualInstalled, pour detecter si une application duale est installee (necessite les permissions Shizuku ou Root) _[`issue #450`](http://issues.autojs6.com/450)_
* `Fonctionnalité` Methode device.getSharedDeviceId, pour obtenir un identifiant d'appareil partage unifie entre applications _[`issue #455`](http://issues.autojs6.com/455)_
* `Fonctionnalité` Methode device.setPointerLocation et autres methodes de la serie Toggleable, pour definir ou obtenir l'option systeme de position du pointeur _[`issue #381`](http://issues.autojs6.com/381)_
* `Fonctionnalité` La methode dialogs.build prend en charge les parametres d'option textAllCaps/(positive/negative/neutral)TextAllCaps, pour controler si le texte des boutons est entierement en majuscules
* `Fonctionnalité` Methode images.loadAsync, pour recuperer de maniere asynchrone des ressources d'image reseau _[`issue #327`](http://issues.autojs6.com/327)_
* `Fonctionnalité` Methode ui.getNavigationBarHeight/propriete navigationBarHeight (getter), pour obtenir la hauteur de la barre de navigation _[`issue #456`](http://issues.autojs6.com/456)_
* `Fonctionnalité` Méthodes ui.getVisible(Status/Navigation)BarHeight/propriétés visible(Status/Navigation)BarHeight (getter), pour obtenir la hauteur visible des barres d'état ou de navigation _[`issue #456`](http://issues.autojs6.com/456)_
* `Fonctionnalité` Méthodes http.put/del(ete)/head, pour envoyer différents types de requêtes HTTP
* `Fonctionnalité` Methodes telles que http.getAsync/postAsync/requestAsync, pour envoyer des requetes reseau de maniere asynchrone _[`issue #423`](http://issues.autojs6.com/423)_
* `Fonctionnalité` L'objet body obtenu via les methodes de requete du module http ajoute des methodes telles que stream/saveToFile/close _[`issue #452`](http://issues.autojs6.com/452)_
* `Fonctionnalité` Les methodes de requete du module http prennent en charge des parametres d'option de controle du cache (cacheBody/bodyCacheThresholdBytes)
* `Fonctionnalité` Les methodes de requete du module http prennent en charge des parametres d'option non securises (isInsecure/insecure), pour ignorer les exceptions liees aux certificats _[`issue #417`](http://issues.autojs6.com/417)_
* `Fonctionnalité` Les methodes de requete du module http prennent en charge l'option options.client, pour configurer OkHttpClient.Builder (comme followRedirects, etc.) _[`issue #454`](http://issues.autojs6.com/454)_
* `Fonctionnalité` Propriete auto.state (getter) et propriete shizuku.state (getter), pour obtenir l'etat du service d'accessibilite et du service Shizuku
* `Fonctionnalité` Methode UiObject#isShifted, pour detecter les changements de position des controles _[`issue #469`](http://issues.autojs6.com/469)_
* `Fonctionnalité` Methode UiObject#snapshot, pour capturer l'etat actuel d'un controle _[`issue #469`](http://issues.autojs6.com/469)_
* `Fonctionnalité` Les selecteurs de la serie equivalente (UiSelector#id/text/...) et de la serie contient (UiSelector#xxxContains) prennent en charge les parametres d'expression reguliere
* `Fonctionnalité` Les parametres d'expression reguliere des selecteurs prennent en charge les drapeaux (i, m, s, u)
* `Fonctionnalité` Methodes runtime.(set/is)JavaPrimitiveWrap, pour definir ou obtenir la strategie d'encapsulation des types primitifs Java _[`issue #435`](http://issues.autojs6.com/435)_
* `Fonctionnalité` Méthodes autojs.restart/exit, pour redémarrer ou quitter AutoJs6, avec exécution possible d'un scriptdésigné après redémarrage _[`issue #460`](http://issues.autojs6.com/460)_
* `Fonctionnalité` Methode engines.myEngine().getStartTime(), pour obtenir l'horodatage de demarrage du moteur de script _[`issue #449`](http://issues.autojs6.com/449)_
* `Fonctionnalité` Methode globale crash, pour tester la page de rapport de plantage
* `Fonctionnalité` Methode globale structuredClone, pour effectuer une copie profonde d'objets JavaScript (voir [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `Fonctionnalité` Les expressions regulieres prennent en charge la syntaxe de lookbehind _[`issue #464`](http://issues.autojs6.com/464)_
* `Fonctionnalité` Le gestionnaire de fichiers ajoute les elements de menu "Deplacer vers" et "Copier vers", avec prise en charge de l'abandon d'operation et de l'affichage de l'etat de progression
* `Fonctionnalité` Le tiroir d'accueil ajoute l'outil "Position du pointeur"
* `Fonctionnalité` Le tiroir d'accueil ajoute l'interrupteur "Acces a tous les fichiers"
* `Fonctionnalité` Option "Afficher des interfaces en arrière-plan" dans le tiroir d'accueil (appareils Xiaomi/Vivo)
* `Fonctionnalité` La page Parametres ajoute l'option "Encapsulation des types primitifs Java" _[`issue #435`](http://issues.autojs6.com/435)_
* `Fonctionnalité` La page Parametres ajoute l'option "Moteur de planification des taches", avec prise en charge de AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `Fonctionnalité` La page Parametres ajoute l'option "Icone du lanceur d'application", avec prise en charge des icones adaptatives/icones a fond transparent _[`issue #405`](http://issues.autojs6.com/405)_
* `Fonctionnalité` Option "Stratégie de redémarrage" dans les paramètres, pour choisir si le bouton du tiroir d'accueil utilise le redémarrage rapide
* `Fonctionnalité` La page Parametres ajoute l'option "Delai de demande de permission de capture d'ecran", pour eviter que la fenetre de demande de permission masque l'ecran
* `Fonctionnalité` Les applications empaquetees prennent en charge le demarrage automatique au boot et ajoutent un interrupteur sur la page de configuration
* `Fonctionnalité` Les applications empaquetees prennent en charge les raccourcis d'application (page des journaux/page des parametres)
* `Fonctionnalité` La page de configuration de l'empaquetage ajoute des boutons annuler/retablir/enregistrer
* `Fonctionnalité` Création/modification/suppression/importation/exportation des configurations d'applications empaquetées _[`issue #483`](http://issues.autojs6.com/483)_
* `Fonctionnalité` Outil Scrapers (run-scrapers.mjs), pour actualiser automatiquement les données de construction Gradle ainsi que les données communes et modèles README
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
* `Correction` En mode serveur, les opérations recréant l'Activity, notamment rotation d'écran et changement de langue, empêchaient la console VSCode d'afficher les journaux _[`issue #385`](http://issues.autojs6.com/385)_
* `Correction` Probleme ou des methodes comme console/toast pouvaient perdre les zeros finaux lors de l'affichage de valeurs Java Double
* `Correction` Probleme ou il etait impossible d'utiliser des methodes comme console/toast pour afficher le type de donnees BigInt
* `Correction` La méthode toast pouvait provoquer une fuite de mémoire en conservant une référence forte à une instance Toast
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
* `Correction` Certaines méthodes images pouvaient provoquer des fuites de mémoire _[`issue #372`](http://issues.autojs6.com/372)_
* `Correction` Probleme ou images.save et la methode ImageWrapper#saveTo ne pouvaient pas ecrire correctement les fichiers lorsque le chemin fourni n'existait pas
* `Correction` Probleme ou la methode ImageWrapper#recycle ne recyclait pas correctement les ressources d'image
* `Correction` Probleme ou certaines methodes surchargees de ocr pouvaient ne pas fonctionner correctement
* `Correction` Probleme ou les resultats obtenus via la methode ocr.detect pouvaient ne pas correspondre a ocr.mode _[`issue #468`](http://issues.autojs6.com/468)_
* `Correction` Impossible d'initialiser correctement ML Kit OCR sur certains appareils (correction expérimentale) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `Correction` Probleme ou les evenements du service d'accessibilite enregistres avec auto.registerEvent pouvaient etre supprimes par erreur par d'autres scripts _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `Correction` Probleme ou la methode UiObject#child pouvait lancer ArrayIndexOutOfBoundsException sur Android 10 _[`issue #416`](http://issues.autojs6.com/416)_
* `Correction` Le contexte Rhino pouvait être incohérent lors des appels aux méthodes des modules intégrés
* `Correction` Probleme ou, apres la creation d'un grand nombre de fenetres flottantes dans un script, des fenetres residuelles pouvaient rester apres la sortie du script
* `Correction` Probleme ou les parametres de configuration de project.json n'etaient pas correctement analyses lors de l'execution d'un projet
* `Correction` Probleme ou le parametre excludedDirs de project.json pouvait provoquer un echec d'analyse du fichier de configuration lors de l'empaquetage d'un projet _[`issue #428`](http://issues.autojs6.com/428)_
* `Correction` Probleme ou les applications empaquetees ne pouvaient pas utiliser correctement les fonctions Paddle OCR et Rapid OCR
* `Correction` Probleme ou la valeur de la cle key dans le fichier de configuration project.json des applications empaquetees pouvait etre modifiee de maniere inattendue
* `Correction` Probleme ou l'icone des cases a cocher pouvait clignoter lors du defilement de la liste "Permissions requises" dans la page de configuration de l'empaquetage
* `Correction` Probleme ou la fonction d'empaquetage pouvait ne pas fonctionner correctement sur Android 7.x
* `Correction` Probleme ou les noms de cles d'origine pouvaient etre modifies lors de l'enregistrement du fichier de configuration d'un projet de script
* `Correction` Probleme ou de grands numeros de version de build ou d'heure de build dans le fichier de configuration d'un projet de script pouvaient provoquer un plantage de l'application
* `Correction` Probleme ou la recuperation ou la reconstruction frequente de ImageReader pouvait provoquer un plantage de l'application a cause de l'absence temporaire d'image disponible dans le tampon
* `Correction` Probleme ou l'observateur d'evenements d'entree InputEventObserver pouvait provoquer un ralentissement notable au demarrage de l'application
* `Correction` Probleme ou le processus de service utilisateur Shizuku ne se terminait pas correctement, provoquant une accumulation de processus _[`issue #474`](http://issues.autojs6.com/474)_
* `Correction` Probleme ou, dans la page Historique des versions, certains systemes affichaient des donnees statistiques incompletes en raison de differences de police
* `Correction` Probleme ou, sur certains appareils, les boutons fonctionnels du gestionnaire de fichiers ne declenchaient pas correctement les evenements de clic (correction tentative) _[`issue #465`](http://issues.autojs6.com/465)_
* `Correction` Probleme ou l'application pouvait planter lors de l'appui sur le bouton retour ou du changement d'onglet apres l'affichage des resultats de recherche du gestionnaire de fichiers
* `Correction` Probleme ou l'interface ne se rafraichissait pas automatiquement apres la suppression de dossiers de projet dans le gestionnaire de fichiers
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
* `Correction` Probleme ou l'application pouvait planter lorsque les elements de la liste du panneau des taches changeaient frequemment
* `Correction` Probleme ou la couleur du premier plan de la barre de navigation pouvait se comporter anormalement avec le mode nuit lors de l'utilisation de la navigation gestuelle "trois boutons"
* `Correction` Probleme ou la boite de dialogue d'informations de type de fichier APK pouvait ne pas obtenir le nom de l'application ni les informations SDK
* `Correction` Probleme ou, en mode client, une deconnexion suivie d'une reconnexion rapide apres une connexion pouvait provoquer un timeout de handshake et rendre la reconnexion difficile
* `Correction` Probleme ou, en mode client, des operations telles que la rotation de l'ecran ou le changement de langue, recreant l'Activity, faisaient toujours reconstruire la connexion par AutoJs6
* `Correction` Probleme ou, lors de la connexion au plugin VSCode de plusieurs manieres en meme temps, la quantite de journaux affiches pouvait etre multipliee
* `Correction` Probleme ou la generation de code dans la page d'analyse de disposition pouvait echouer avec les controles de collection (correction tentative) _[`issue #328`](http://issues.autojs6.com/328)_
* `Correction` Probleme ou l'interrupteur "Utiliser la touche 'Volume bas' pour controler l'enregistrement" ne fonctionnait pas dans la page Parametres _[`issue #480`](http://issues.autojs6.com/480)_
* `Correction` Probleme ou l'interrupteur "Afficher par-dessus les autres applications" sur les appareils Xiaomi pouvait ouvrir une mauvaise page de parametres
* `Correction` Les coordonnées du code produit par l'enregistrement de scripts pouvaient ne pas correspondre aux pixels réels de l'écran _[`issue #480`](http://issues.autojs6.com/480)_
* `Correction` Probleme ou la premiere action dans les fichiers de code generes par l'enregistrement de scripts (comme toucher ou glisser) etait toujours ignoree
* `Correction` Probleme ou l'execution d'operations sur des fichiers en arriere-plan depuis la boite de dialogue flottante "Executer le script" pouvait provoquer un plantage de l'application
* `Correction` Probleme ou l'etat du bouton flottant pouvait etre reinitialise lors du retour a la page d'accueil apres la fin du cycle de vie de l'Activity d'accueil
* `Correction` Probleme ou, apres l'abandon du telechargement de mise a jour, les paquets reseau continuaient quand meme a etre telecharges en arriere-plan (correction tentative)
* `Correction` Probleme ou la tache Rebuild Project ne pouvait pas se terminer correctement lorsque l'option isCleanup[Paddle/Rapid]Ocr etait activee dans les outils de build
* `Amélioration` Fonctionnalites liees a Paddle OCR (PP-OCRv3) pluginise _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `Amélioration` Compatibilite amelioree de la methode runtime.loadJar (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `Amélioration` Compatibilite amelioree dans une certaine mesure de la methode ui.getStatusBarHeight/propriete statusBarHeight (getter)
* `Amélioration` Les methodes comme console.log prennent en charge l'affichage d'informations detaillees lors de l'impression d'objets globaux (images, app, ocr, etc.)
* `Amélioration` Les méthodes de console tronquent les données volumineuses selon une limite afin de réduire le temps d'affichage
* `Amélioration` Le compte à rebours initial de la console flottante passe de 6 à 9 secondes
* `Amélioration` Utilisation de files et suppression des verrous dans la console flottante pour améliorer l'efficacité et la fiabilité de ses réglages
* `Amélioration` Les API de couleur du fond de la console flottante (opacité/teinte/couleur de base) suivent mieux les conventions Android _[`issue #458`](http://issues.autojs6.com/458)_
* `Amélioration` La methode dialogs.build prend en charge les parametres d'option natifs de MaterialDialog.Builder
* `Amélioration` La methode dialogs.build prend en charge la correspondance souple de chaines pour les parametres d'option animation/linkify
* `Amélioration` Stabilité et cohérence améliorées des données d'orientation et de dimensions obtenues par device.rotation/orientation/width/height
* `Amélioration` Efficacite d'execution et compatibilite ameliorees des methodes telles que app.launchDual/startDualActivity
* `Amélioration` android.graphics.Paint#setColor prend correctement en charge l'analyse des parametres de couleur tels que ColorInt/ColorHex/ColorName
* `Amélioration` Les messages d'exception des types d'arguments des methodes liees aux modules integres ajoutent des informations de resume de type
* `Amélioration` Les modules intégrés dayjs/cheerio/i18n utilisent un chargement global différé au lieu du mode d'exécution de scripts
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
* `Dépendance` Intégration en module de RecyclerView Flexible Divider version 1.4.0
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
* `Dépendance` Mise à niveau de Apache Commons Lang3 version 3.16.0 -> 3.18.0
* `Dépendance` Mise a niveau de Retrofit2 Retrofit version 2.11.0 -> 2.12.0
* `Dépendance` Mise a niveau de Retrofit2 Converter Gson version 2.11.0 -> 2.12.0
* `Dépendance` Mise a niveau de Retrofit2 RxJava2 version 2.11.0 -> 2.12.0
* `Dépendance` Mise a niveau de Joda Time version 2.12.7 -> 2.14.0
* `Dépendance` Mise a niveau de Gson version 2.11.0 -> 2.13.1
* `Dépendance` Mise a niveau de ARSCLib version 1.3.1 -> 1.3.5
* `Dépendance` Mise a niveau de Jsoup version 1.19.1 -> 1.20.1
* `Dépendance` Mise à niveau de JUnit Jupiter version 5.10.3 -> 5.13.0
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
* `Amélioration` Meilleure adaptation de la disposition de la page À propos de l'application et du développeur, et suppression de catégories de disposition inutiles
* `Amélioration` Instructions pour localiser les pages de paramètres dans la section de compilation du README.md _[`issue #404`](http://issues.autojs6.com/404)_
* `Dépendance` Ajout de AndroidX ConstraintLayout version 2.2.1

# v6.6.3

###### 2025/05/27

* `Fonctionnalité` Historique des versions, pour consulter les changements et statistiques en plusieurs langues
* `Fonctionnalité` Méthode timers.keepAlive (désormais globale) pour maintenir le script actif
* `Fonctionnalité` Méthodes d'écoute d'événements telles que engines.on('start/stop/error', callback) pour les événements globaux du moteur
* `Fonctionnalité` Méthode images.detectMultiColors pour la vérification de couleurs multipoints _[`issue #374`](http://issues.autojs6.com/374)_
* `Fonctionnalité` Méthodes images.matchFeatures/detectAndComputeFeatures : recherche d'image en pleine résolution (Réf. [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `Fonctionnalité` Méthode images.compressToBytes pour compresser une image et générer un tableau d'octets
* `Fonctionnalité` Méthode images.downsample, pour sous-échantillonner une image et renvoyer un nouvel objet ImageWrapper
* `Fonctionnalité` Méthode ui.keepScreenOn pour garder l'écran allumé lorsque la page UI a le focus
* `Fonctionnalité` Propriété ui.root (getter) pour obtenir le nœud "conteneur racine du contenu de fenêtre" du layout UI
* `Fonctionnalité` Les éléments webview acceptent des interfaces Web basées sur JsBridge (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Exemples de code > Dispositions > HTML interactif/Vue2 + Vant (SFC)) _[`issue #281`](http://issues.autojs6.com/281)_
* `Fonctionnalité` L'inspecteur de layout permet de masquer des contrôles (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `Correction` Le contenu de la documentation en ligne dans l'onglet Docs et l'activité Docs pouvait être recouvert par la barre de navigation système
* `Correction` Sur certaines pages, cliquer sur les boutons de la Toolbar pouvait déclencher par erreur l'événement de clic sur le titre
* `Correction` Sur certains appareils, les lignes vides de l'editeur de code affichaient des caracteres en forme de carre; correction tentative
* `Correction` La boîte de dialogue du sélecteur de couleurs dans les paramètres de couleur du thème pouvait se superposer indéfiniment
* `Correction` La couleur d'arrière-plan de la barre d'état sur certaines pages Android 15 ne suivait pas dynamiquement la couleur du thème
* `Correction` La touche volume + ne stoppait pas tous les scripts lorsque le service d'accessibilité était désactivé
* `Correction` Chevauchement du clavier IME lors de l'édition d'un contenu de diffusion personnalisé dans la page des tâches planifiées
* `Correction` Les contrôles dans les éléments webview ne pouvaient pas activer correctement le clavier virtuel
* `Correction` La boîte de dialogue d'information APK pouvait ne pas récupérer le nom de l'application ni les informations SDK
* `Correction` Le code d'exemple du gestionnaire de fichiers pouvait ne pas charger automatiquement le contenu des sous-dossiers à l'entrée d'un répertoire de projet
* `Correction` Le contenu supérieur du mode UI sous Android 15 était recouvert par la barre d'état
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
* `Amélioration` Les boîtes de dialogue de la page Paramètres proposent l'option "Utiliser la valeur par défaut"
* `Amélioration` Le bouton flottant du gestionnaire de fichiers se masque automatiquement lorsqu'on touche en dehors
* `Amélioration` Le chargement asynchrone améliore la fluidité de défilement du gestionnaire de fichiers
* `Amélioration` Le formateur de code prend désormais en charge les opérateurs `??`, `?.`, `??=`
* `Amélioration` L'éditeur de code permet la lecture et l'écriture de fichiers en GB18030, UTF-16 (LE/BE), Shift_JIS et autres encodages
* `Amélioration` L'éditeur de code prend en charge l'affichage des informations détaillées du fichier (chemin/encodage/retour à la ligne/nombre total d'octets et de caractères, etc.) _[`issue #395`](http://issues.autojs6.com/395)_
* `Amélioration` Ajout de messages d'erreur pour les actions d'intent (éditer / voir / installer / envoyer / lire, etc.)
* `Amélioration` L'attribut url de webview prend en charge les chemins relatifs
* `Amélioration` Le paramètre path de ImageWrapper#saveTo prend en charge les chemins relatifs
* `Amélioration` images.save permet la compression de fichiers PNG lors de l'utilisation du paramètre quality _[`issue #367`](http://issues.autojs6.com/367)_
* `Amélioration` Possibilité de vider les enregistrements de mises à jour ignorées et les adresses de connexion en mode client
* `Amélioration` Les informations de mise à jour de version sont affichées dans la langue de l'interface
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

* `Fonctionnalité` Méthodes ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor et similaires, pour définir le style des barres d'état et de navigation
* `Fonctionnalité` Attribut ui.statusBarHeight (getter) servant à obtenir la hauteur de la barre d'état _[`issue #357`](http://issues.autojs6.com/357)_
* `Fonctionnalité` Méthode images.flip pour retourner une image _[`issue #349`](http://issues.autojs6.com/349)_
* `Fonctionnalité` Ajout de l'option "extension de fichier" dans la page des paramètres
* `Fonctionnalité` Groupes, localisation, recherche, historique et palette enrichie dans la page des couleurs du thème
* `Correction` Problème où la couleur de fond de la barre d'état sur Android 15 ne correspond pas à celle du thème
* `Correction` Problème où la méthode plugins.load ne charge pas correctement les plugins _[`issue #290`](http://issues.autojs6.com/290)_
* `Correction` Problème où la bibliothèque dx ne fonctionne pas correctement sur Android 7.x _[`issue #293`](http://issues.autojs6.com/293)_
* `Correction` Problème où ScriptRuntime peut présenter un état de synchronisation anormal lors de l'utilisation de require pour importer des modules intégrés (solution provisoire) _[`issue #298`](http://issues.autojs6.com/298)_
* `Correction` Problème où le module notice ne dispose pas des méthodes d'extension telles que getBuilder _[`issue #301`](http://issues.autojs6.com/301)_
* `Correction` Problème où les méthodes shizuku/shell n'acceptent pas les paramètres de type chaîne _[`issue #310`](http://issues.autojs6.com/310)_
* `Correction` Problème où la méthode colors.pixel n'accepte pas les paramètres d'images à canal unique _[`issue #350`](http://issues.autojs6.com/350)_
* `Correction` Problème où les méthodes engines.execScript/execScriptFile utilisent un répertoire de travail par défaut incorrect lors de l'exécution des scripts _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `Correction` Problème où floaty.window/floaty.rawWindow ne peut pas être exécuté dans un thread secondaire
* `Correction` floaty.getClip pouvait échouer à obtenir le contenu du presse-papiers _[`issue #341`](http://issues.autojs6.com/341)_
* `Correction` Problème où ui.inflate renvoie un résultat auquel il manque les méthodes de prototype telles que attr, on et click
* `Correction` Problème où le contexte de portée n'est pas correctement lié lors de l'utilisation de la syntaxe XML pour utiliser une expression JavaScript comme valeur d'attribut _[`issue #319`](http://issues.autojs6.com/319)_
* `Correction` Les exceptions de certains appels de méthode ne pouvaient pas être interceptées par un bloc try/catch _[`issue #345`](http://issues.autojs6.com/345)_
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
* `Amélioration` L'onglet "Fichiers" de la page d'accueil permet de changer la visibilité du bouton flottant par un appui long
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
* `Dépendance` Mise à niveau de AndroidX RecyclerView version 1.3.2 -> 1.4.0
* `Dépendance` Mise à jour d'AndroidX Room de 2.6.1 à 2.7.0
* `Dépendance` Mise à jour d'AndroidX WebKit de 1.12.1 à 1.13.0
* `Dépendance` Mise à jour de Pinyin4j de 2.5.0 à 2.5.1

# v6.6.1

###### 2025/01/01

* `Fonctionnalité` Module Pinyin pour la conversion du pinyin chinois (Consultez la documentation du projet > [Pinyin chinois](https://docs.autojs6.com/#/pinyin))
* `Fonctionnalité` Module Pinyin4j pour la conversion du pinyin chinois (Consultez la documentation du projet > [Pinyin chinois](https://docs.autojs6.com/#/pinyin4j))
* `Fonctionnalité` Méthodes UiObject#isSimilar et UiObjectCollection#isSimilar, pour déterminer la similarité de composants ou collections
* `Fonctionnalité` Méthode globale currentComponent, pour obtenir le nom du composant actuellement actif
* `Correction` Problème empêchant la compilation correcte du projet dans certains environnements suite à un retour à une ancienne version
* `Correction` Exception "valeur non primitive" pouvant survenir lors de l'appel à des méthodes inexistantes
* `Correction` Problème empêchant l'ajout correct de raccourcis de script sur certains appareils (correction provisoire) _[`issue #221`](http://issues.autojs6.com/221)_
* `Correction` Erreur de restriction de type de paramètre dans les méthodes automator.click/longClick _[`issue #275`](http://issues.autojs6.com/275)_
* `Correction` Problème avec les sélecteurs ne prenant pas en charge les paramètres de type ConsString _[`issue #277`](http://issues.autojs6.com/277)_
* `Correction` Problème d'absence des méthodes et propriétés propres aux instances UiObjectCollection
* `Amélioration` La page de configuration de packaging prend maintenant en charge la configuration de signature, la gestion du keystore et les parametres d'autorisation (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `Amélioration` Convertir automatiquement le chinois simplifié en pinyin (y compris les caractères à tons multiples) pour les suffixes de noms de paquet générés lors de l'empaquetage de fichiers uniques
* `Amélioration` Amélioration de la précision de la reconnaissance du nom du package actuel et de l'activité en cours de la fenêtre flottante (Priorité : Shizuku > Root > A11Y)
* `Amélioration` Amélioration de la précision de la reconnaissance de currentPackage et currentActivity (Priorité : Shizuku > Root > A11Y)
* `Amélioration` Rétablissement de la sélection du texte d'une entrée de journal par double appui ou appui long _[`issue #280`](http://issues.autojs6.com/280)_
* `Amélioration` Récupérer autant d'informations critiques que possible pour les projets de script en cas de corruption du fichier project.json
* `Amélioration` Soutien des arguments négatifs dans les méthodes UiSelector#findOnce et UiSelector#find
* `Amélioration` Prise en charge des formes abrégées supplémentaires pour les préfixes de noms de paquet dans les sélecteurs liés aux éléments de l'interface utilisateur et aux className (par exemple RecyclerView, Snackbar, etc.)
* `Amélioration` Amélioration de l'adaptabilité des méthodes app.startActivity/startDualActivity
* `Amélioration` Synchroniser le code en amont le plus récent du moteur Rhino et l'adapter au projet existant
* `Dépendance` Ajout de Pinyin4j version 2.5.0
* `Dépendance` Ajout de Jieba Analysis version 1.0.3-SNAPSHOT (modifiée)
* `Dépendance` Mise à niveau de la version de Gradle de 8.11.1 à 8.12

# v6.6.0

###### 2024/12/02 - Réécriture du module intégré, mise à jour avec prudence

* `Astuce` Les modules intégrés sont réécrits en Kotlin pour améliorer l'efficacité d'exécution des scripts, mais des améliorations itératives sont nécessaires
* `Astuce` Le fichier init.js intégré est vide par défaut, permettant aux développeurs d'étendre les modules intégrés ou de monter des modules externes.
* `Fonctionnalité` Modules axios/cheerio, pour les requêtes réseau et l'analyse HTML (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `Fonctionnalité` Module sqlite, pour les opérations de base de données SQLite (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Fonctionnalité` Module MIME pour le traitement et l'analyse des chaînes de type MIME (Voir la documentation du projet > [MIME](https://docs.autojs6.com/#/mime))
* `Fonctionnalité` Module nanoid, pour générer des identifiants textuels (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `Fonctionnalité` Module Sysprops pour obtenir des données de configuration de l'environnement d'exécution (Voir la documentation du projet > [Propriétés système](https://docs.autojs6.com/#/sysprops))
* `Fonctionnalité` Le module OCR prend en charge le moteur [Rapid OCR](https://github.com/RapidAI/RapidOCR)
* `Fonctionnalité` L'analyse de la mise en page prend en charge le changement de fenêtre (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Méthode auto.clearCache, pour vider le cache des composants (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Méthode threads.pool, pour créer et utiliser des pools de fils d'exécution (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.matchTemplate accepte l'option useTransparentMask, pour la recherche d'images avec transparence (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Méthode images.requestScreenCaptureAsync, pour demander l'autorisation de capture d'écran de façon asynchrone en mode UI (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.requestScreenCapture accepte l'option isAsync, pour obtenir des captures d'écran de façon asynchrone (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Méthodes d'écoute telles que images.on('screen_capture', callback), pour surveiller la disponibilité des captures d'écran (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Méthode images.stopScreenCapture, pour libérer explicitement les ressources associées à la demande de capture (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Les méthodes images.psnr/mse/ssim/mssim/hist/ncc et images.getSimilarity pour obtenir des mesures de similarité d'image
* `Fonctionnalité` La méthode images.isGrayscale pour déterminer si une image est en niveaux de gris
* `Fonctionnalité` Méthode images.invert, pour inverser les couleurs d'une image
* `Fonctionnalité` Les méthodes s13n.point/time pour normaliser les objets de point et de durée (Voir la documentation du projet > [Normalisation](https://docs.autojs6.com/#/s13n))
* `Fonctionnalité` Méthodes du module console relatives à gravity (positionnement), touchThrough (passage des appuis), backgroundTint (teinte du fond) et autres réglages (voir Documentation du projet > [Console](https://docs.autojs6.com/#/console))
* `Fonctionnalité` Méthodes Mathx.randomInt/randomFloat, pour obtenir des entiers ou nombres flottants aléatoires dans une plage donnée
* `Fonctionnalité` Les méthodes app.launchDual/startDualActivity pour gérer le lancement double d'applications (Nécessite des autorisations Shizuku ou Root) (Expérimental)
* `Fonctionnalité` La méthode app.kill pour arrêter de force une application (Nécessite des autorisations Shizuku ou Root)
* `Fonctionnalité` Méthode floaty.getClip, pour obtenir le presse-papiers en donnant le focus à une fenêtre flottante
* `Correction` Fuite de mémoire dans le View Binding des sous-classes de Fragment (par exemple, [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48))
* `Correction` Fuite de mémoire d'instance dans des classes telles que [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21)
* `Correction` Problème causant le crash de l'application lors de la demande de permissions de capture d'écran sur Android 14 (par [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `Correction` Problème causant le crash de l'application lors du démarrage du service en premier plan sur Android 14
* `Correction` Problème avec le bouton d'exécution dans l'éditeur de code qui ne s'allume pas correctement sur Android 14
* `Correction` Exception 'FAILED ASSERTION' dans la fonction de débogage de l'éditeur de code
* `Correction` Problème d'impossibilité de déboguer à nouveau correctement après la fermeture de l'éditeur pendant le processus de débogage de l'éditeur de code
* `Correction` Problème de saut potentiel de caractères de fin lors du saut à la fin de la ligne dans l'éditeur de code
* `Correction` L'application peut ne pas fonctionner correctement après l'empaquetage en raison de fichiers de bibliothèque nécessaires manquants _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `Correction` Problème avec l'application empaquetée ne pouvant pas utiliser correctement le module opencc
* `Correction` Crash de l'application lors de l'édition du projet en raison de ressources d'icône spécifiées manquantes _[`issue #203`](http://issues.autojs6.com/203)_
* `Correction` Incapacité à utiliser correctement les paramètres pour obtenir les ressources de capture d'écran de l'orientation spécifiée lorsqu'on demande des permissions de capture d'écran
* `Correction` Problème avec certains appareils incapables d'ajouter correctement des raccourcis de script (Correction d'essai) _[`issue #221`](http://issues.autojs6.com/221)_
* `Correction` Problème de retard cumulatif de l'envoi de requêtes avec des méthodes liées à l'envoi de requêtes dans le module http _[`issue #192`](http://issues.autojs6.com/192)_
* `Correction` Le service Shizuku peut ne pas fonctionner correctement avant qu'AutoJs6 n'entre dans la page principale de l'activité (Correction d'essai) _[`issue #255`](http://issues.autojs6.com/255)_
* `Correction` La méthode random(min, max) peut avoir des résultats hors limites
* `Correction` Problème où le paramètre de type de résultat des méthodes pickup ne peut pas passer correctement des tableaux vides
* `Correction` Problème de rectangle de contrôle obtenu par UiObject#bounds() pouvant être modifié par inadvertance, brisant son immutabilité
* `Correction` Le démarrage automatique du service d'accessibilité peut rencontrer des problèmes de service anormaux (Correction d'essai)
* `Correction` Problème avec les éléments texte/bouton/entrée où le texte contenant des guillemets doubles de largeur réduite ne peut pas être analysé correctement
* `Correction` Problème avec les éléments text/textswitcher où la fonctionnalité de l'attribut autoLink échoue
* `Correction` Problème avec différents scripts partageant par erreur le même objet ScriptRuntime
* `Correction` Les variables globales HEIGHT et WIDTH avaient perdu leurs accesseurs dynamiques getter
* `Correction` Problème avec un temps de démarrage potentiellement élevé causé par le chargement de RootShell au démarrage du script
* `Correction` Le réglage de la couleur de fond de la console flottante supprimait ses coins arrondis
* `Correction` Un clignotement pouvait survenir à l'ouverture de la page des journaux depuis l'activité principale
* `Correction` Problème de déclenchement du changement de ViewPager lors du glissement du contrôle WebView à gauche ou à droite sur la page de document de la page d'accueil
* `Correction` Problème avec le gestionnaire de fichiers incapable de reconnaître les extensions de fichier contenant des lettres majuscules
* `Correction` Le gestionnaire de fichiers peut ne pas reconnaître automatiquement le projet lors de la première entrée dans le répertoire du projet
* `Correction` Problème avec la page du gestionnaire de fichiers incapable de se rafraîchir automatiquement après la suppression du dossier
* `Correction` Problème avec le tri des fichiers et des dossiers dans le gestionnaire de fichiers où les noms de lettre initiale ASCII sont mis en arrière
* `Amélioration` Amelioration de l'experience des indications au toucher pour le controle "ABI indisponible" dans la page de configuration de packaging
* `Amélioration` Lors de l'empaquetage d'un fichier unique, remplissage automatique d'un nom de package normalisé approprié et indication du filtrage des caractères invalides
* `Amélioration` Le paquet de fichier unique prend en charge la configuration automatique de l'icône et l'incrémentation automatique du numéro et du nom de version basé sur l'application du même nom de paquet installée
* `Amélioration` Le fichier de configuration du paquet prend en charge l'option abis/libs pour spécifier la bibliothèque et l'architecture ABI incluses par défaut
* `Amélioration` Prend en charge les indications de messages pertinents lorsque les options abis/libs du fichier de configuration du paquet sont invalides ou non disponibles
* `Amélioration` Prend en charge l'utilisation de Shizuku pour contrôler l'interrupteur d'affichage 'Localisation du pointeur'
* `Amélioration` Prend en charge l'utilisation de Shizuku pour contrôler les interrupteurs de permission 'Médias de projection' et 'Modifier les paramètres sécurisés'
* `Amélioration` automator.gestureAsync/gesturesAsync prend en charge les paramètres de fonction de rappel
* `Amélioration` Le module tasks utilise une méthode synchrone pour les opérations de base de données afin d'éviter les incohérences potentielles d'accès aux données
* `Amélioration` Le mode d'exécution de scripts prend en charge les paramètres de mode de séparation par symbole pipeline (par exemple, en commençant par `"ui|auto";`)
* `Amélioration` Le mode d'exécution de scripts prend en charge les guillemets simples et guillemets inversés et permet d'omettre les points-virgules (par exemple, en commençant par `'ui';` ou `'ui'`)
* `Amélioration` Le mode d'exécution de scripts prend en charge l'importation rapide des modules d'extension intégrés tels que axios, cheerio, et dayjs (par exemple, en commençant par `"axios";`)
* `Amélioration` Le mode d'exécution de scripts prend en charge les paramètres de mode x ou jsox pour activer rapidement les modules d'extension d'objets intégrés JavaScript (par exemple, en commençant par `"x";`)
* `Amélioration` Les propriétés src et path de l'élément img acceptent des chemins locaux relatifs (comme `<img src="a.png" />`)
* `Amélioration` L'éditeur de code prend en charge la détermination intelligente de l'emplacement d'insertion lors de l'importation de classes Java et de noms de paquets
* `Amélioration` Le module images prend en charge l'utilisation des chemins directement comme paramètres d'image
* `Amélioration` importPackage prend en charge les paramètres de chaîne
* `Amélioration` Reconnaissance intelligente des adresses IP importées du presse-papiers en mode client et conversion intelligente de la touche espace
* `Amélioration` Le gestionnaire de fichiers prend en charge la sélection de préfixes par défaut lors de la création de nouveaux fichiers et génère automatiquement un suffixe numérique approprié
* `Amélioration` Le gestionnaire de fichiers informe spécifiquement sur le message d'exception lors de l'exécution du projet _[`issue #268`](http://issues.autojs6.com/268)_
* `Amélioration` Le gestionnaire de fichiers prend en charge plus de types et affiche les symboles d'icône correspondants (prend en charge plus de 800 types de fichiers)
* `Amélioration` Les types de fichiers éditables (jpg/doc/pdf/...) dans le gestionnaire de fichiers ont ajouté des boutons d'édition
* `Amélioration` Les fichiers APK dans le gestionnaire de fichiers prennent en charge la visualisation des informations de base, des informations du manifest et de la liste des permissions
* `Amélioration` Les fichiers multimédias audio/vidéo dans le gestionnaire de fichiers prennent en charge la visualisation des informations de base et des informations de MediaInfo
* `Amélioration` Exclusion de LeakCanary des versions publiées pour réduire la taille des paquets
* `Amélioration` Tous les commentaires en anglais dans le code source du projet sont accompagnés de traductions en chinois simplifié pour améliorer la lisibilité
* `Amélioration` README et CHANGELOG prennent en charge plusieurs langues (Généré automatiquement par script)
* `Amélioration` Améliorer l'adaptabilité de la version du script de construction Gradle
* `Dépendance` Inclure la version 2.3.1 de MIME Util
* `Dépendance` Inclure la version 12.6 de Toaster
* `Dépendance` Ajout de EasyWindow (for Toaster) version 10.3
* `Dépendance` Mettre à niveau la version 8.5 de Gradle -> 8.11.1
* `Dépendance` Mettre à niveau la version 1.7.15-SNAPSHOT de Rhino -> 1.8.0-SNAPSHOT
* `Dépendance` Mettre à niveau la version 1.10.0 d'Android Material -> 1.12.0
* `Dépendance` Mettre à niveau la version 1.7.0 d'AndroidX Annotation -> 1.9.1
* `Dépendance` Mettre à niveau la version 1.6.1 d'AndroidX AppCompat -> 1.7.0
* `Dépendance` Mettre à niveau la version 1.8.0 d'AndroidX WebKit -> 1.12.1
* `Dépendance` Mise à niveau de Apache Commons Lang3 version 3.13.0 -> 3.16.0
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
* `Dépendance` Mise à niveau de JUnit Jupiter version 5.10.0 -> 5.10.3
* `Dépendance` Réduire la version 5.0.0-alpha.11 d'OkHttp3 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `Fonctionnalité` Module opencc, pour convertir le chinois simplifié/traditionnel et le vocabulaire régional (voir Documentation du projet > [Conversion du chinois](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `Fonctionnalité` Ajout des méthodes [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) et [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) pour UiSelector _[`issue #115`](http://issues.autojs6.com/115)_
* `Fonctionnalité` La page de configuration de packaging prend maintenant en charge le filtrage des ABI et des bibliotheques (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Correction` Problème de taille de fichier anormalement volumineux lors du packaging de l'application (Réf à [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Correction` Problème d'affichage de certains messages d'exception lors du packaging de l'application
* `Correction` Correction d'un probleme ou une icone vide pouvait s'afficher apres la selection de l'icone de l'application dans la page de configuration de packaging
* `Correction` Des applications empaquetées contenant ML Kit OCR pouvaient rencontrer une exception de contexte non initialisé
* `Correction` Problème d'inefficacité des méthodes ocr.recognizeText/detect
* `Correction` Problème de langage d'affichage incohérent avec le paramètre de l'application dans certains textes (comme la page des logs)
* `Correction` Problème de débordement de texte sur certains appareils avec le service de réutilisation activé
* `Correction` Problème de fermeture automatique du service d'accessibilité sans notification sur certains appareils _[`issue #181`](http://issues.autojs6.com/181)_
* `Correction` Problème de crash de l'application causé par les boutons physiques du matériel avec le service d'accessibilité activé sur certains appareils (correctif partiel) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Correction` Problème de dysfonctionnement de la fonctionnalité pickup après avoir redémarré le service d'accessibilité avec auto(true) (correctif partiel) _[`issue #184`](http://issues.autojs6.com/184)_
* `Correction` Problème d'inutilisation du service d'accessibilité sur Android 7.x
* `Correction` Problème de crash potentiel de l'application lors de la création de fenêtres flottantes avec le module floaty (correctif partiel)
* `Correction` Problème d'utilisation des paramètres abrégés dans app.startActivity _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Correction` Problème de conflit de nommage entre les classes importées avec importClass et les variables globales _[`issue #185`](http://issues.autojs6.com/185)_
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
* `Amélioration` Le menu contextuel du gestionnaire de fichiers pour les dossiers ajoute une option d'emballage d'application
* `Amélioration` Le gestionnaire de fichiers adapte les menus selon le type et les caractéristiques des fichiers et dossiers
* `Amélioration` Lorsque les services d'accessibilité sont activés mais dysfonctionnent, un état anormal se reflète dans le commutateur du tiroir de la page d'accueil d'AutoJs6
* `Amélioration` Ajout d'informations détaillées de pile aux messages d'erreur affichés par la console
* `Dépendance` Ajout de ARSCLib version 1.2.4
* `Dépendance` Ajout de Flexbox version 3.0.0
* `Dépendance` Ajout de Android OpenCC version 1.2.0
* `Dépendance` Mise à jour de Gradle version 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `Fonctionnalité` La méthode dialogs.build prend en charge le paramètre d'option inputSingleLine
* `Fonctionnalité` Méthode console.setTouchable, pour définir si la console flottante reçoit les interactions tactiles _[`issue #122`](http://issues.autojs6.com/122)_
* `Correction` Certaines méthodes ocr ne reconnaissaient pas le paramètre de région _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `Correction` Problème de récupération des détails de la version lors de la découverte d'une nouvelle version sur Android 7.x
* `Correction` Problème de crash de l'application lors de la demande de permissions de capture d'écran sur Android 14
* `Correction` Problème de crash potentiel de l'application lors de la commutation rapide de l'option "Floating Button" dans le panneau de navigation du tiroir principal
* `Correction` Problème de persistance du bouton flottant après la fermeture depuis le menu et le redémarrage de l'application
* `Correction` Le changement de langue d'AutoJs6 depuis les paramètres ne prenait pas effet sous Android 13+
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
* `Amélioration` Les objets de réponse du module http permettent des appels répétés à body.string/bytes
* `Amélioration` Support de l'automatisation de build avec GitHub Actions (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Amélioration` Adaptation des outils de construction pour la plateforme Temurin
* `Dépendance` Mise à jour de Gradle de la version 8.4-rc-3 à la version 8.4
* `Dépendance` Mise à jour de Android dx de la version 1.11 à la version 1.14

# v6.4.0

###### 2023/10/30

* `Fonctionnalité` Support du moteur Paddle Lite pour le module ocr (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `Fonctionnalité` Support des plugins intégrés et externes pour le packaging (par [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `Fonctionnalité` Module WebSocket, pour la communication WebSocket (voir Documentation du projet > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `Fonctionnalité` Modules barcode/qrcode, pour reconnaître les codes-barres et codes QR (voir Documentation du projet > [Codes-barres](https://docs.autojs6.com/#/barcode) / [Codes QR](https://docs.autojs6.com/#/qrcode))
* `Fonctionnalité` Module shizuku (voir Documentation > [Shizuku](https://docs.autojs6.com/#/shizuku)) et le commutateur de permissions dans le panneau de navigation de l'accueil
* `Fonctionnalité` Propriétés device.rotation/orientation et similaires, pour obtenir l'orientation de l'écran
* `Fonctionnalité` Support d'accès aux propriétés statiques dans les classes Java internes
* `Fonctionnalité` Changement de langue de l'application depuis les paramètres Android (Android 13+)
* `Fonctionnalité` Activation des [raccourcis d'application](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) depuis les paramètres ou par appui long sur l'icône, pour ouvrir notamment la documentation et les paramètres
* `Correction` Re-fusion de certaines PR (par [aiselp](https://github.com/aiselp)) pour résoudre certains problèmes d'arrêt anormal des scripts _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Correction` Problème d'utilisation des nouvelles APIs AutoJs6 dans les applications packagées (par [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `Correction` Problème de stylisation des applications packagées en mode nuit
* `Correction` Problème de perte d'informations d'extension de fichier lors de l'enregistrement local via l'extension VSCode
* `Correction` Problème d'exceptions non interceptées dans les projets utilisant des coroutines, causant des crashs de l'application
* `Correction` Problème de persistance de position du bouton flottant après redémarrage ou sortie de l'application
* `Correction` Problème de débordement du bouton flottant hors écran lors de la rotation en paysage _[`issue #90`](http://issues.autojs6.com/90)_
* `Correction` La position et le côté d'écran du bouton flottant n'étaient pas rétablis après plusieurs rotations
* `Correction` Problème de mise à jour des informations de configuration de l'appareil après le changement d'orientation de l'écran _[`issue #153`](http://issues.autojs6.com/153)_
* `Correction` Problème de petite taille des caractères du titre de la barre d'outils lors de la rotation en paysage de l'écran
* `Correction` Problème de disposition compacte des onglets sur l'accueil en mode paysage
* `Correction` Problème d'affichage offensif des messages flottants sur certains appareils
* `Correction` Problème de masquage des messages flottants lorsque plusieurs scripts s'exécutent simultanément _[`issue #67`](http://issues.autojs6.com/67)_
* `Correction` Problème de plantage de l'application lorsque l'analyse de la mise en page via la diffusion échoue
* `Correction` Problème de non détection d'évènements pour les instances créées après la première instance de WebSocket
* `Correction` Annulation de la redirection globale importPackage pour éviter des erreurs d'importation dans certains scopes _[`issue #88`](http://issues.autojs6.com/88)_
* `Correction` La copie ou l'exportation depuis la page des journaux pouvait faire planter l'application
* `Amélioration` Renommage de l'exportation des journaux en envoi et nouvelle implémentation d'une véritable exportation
* `Amélioration` L'envoi des journaux tronque automatiquement un nombre excessif d'entrées et le signale
* `Amélioration` Le module ocr est compatible avec les moteurs ML Kit et Paddle Lite (voir Documentation du projet > [Reconnaissance optique de caractères](https://docs.autojs6.com/#/ocr?id=p-mode))
* `Amélioration` Augmentation du taux de succès de lancement automatique du service d'accessibilité
* `Amélioration` Migration du traitement des annotations Kotlin de kapt à KSP
* `Amélioration` Les outils de construction prennent en charge IntelliJ IDEA EAP
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
* `Dépendance` Mise à niveau de AndroidX RecyclerView version 1.3.0 -> 1.3.2
* `Dépendance` Mise à jour de Android Material de la version 1.9.0 à 1.10.0
* `Dépendance` Mise à jour de AndroidX AppCompat de la version 1.4.2 à 1.6.1
* `Dépendance` Mise à jour de Commons IO de la version 2.8.0 à 2.14.0
* `Dépendance` Mise à jour de Jackson DataBind de la version 2.13.3 à 2.14.3
* `Dépendance` Suppression de Zeugma Solutions LocaleHelper version 1.5.1

# v6.3.3

###### 2023/07/21

* `Fonctionnalité` Prise en charge des commentaires de code dans l'éditeur (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Fonctionnalité` Propriété auto.stateListener, pour surveiller l'état de connexion du service d'accessibilité (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Fonctionnalité` Méthodes UiObject#nextSibling/lastChild/offset et similaires, pour naviguer entre les composants (voir Documentation du projet > [Noeuds de composants](https://docs.autojs6.com/#/uiObjectType))
* `Correction` Problème de l'extension VSCode lors de l'analyse des scripts avec une longueur de caractères de plus de quatre chiffres décimaux _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `Correction` Le plugin VSCode ne pouvait pas enregistrer correctement les fichiers _[`issue #92`](http://issues.autojs6.com/92)_ _[`issue #94`](http://issues.autojs6.com/94)_
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

* `Fonctionnalité` Module crypto, pour chiffrer et déchiffrer des données (voir Documentation du projet > [Chiffrement](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `Fonctionnalité` Composants textswitcher/viewswitcher/viewflipper/numberpicker/video/search et similaires en mode UI
* `Fonctionnalité` Copie et exportation des journaux dans la page des journaux _[`issue #76`](http://issues.autojs6.com/76)_
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
* `Amélioration` Affichage de l'adresse IP cible dans le sous-titre du tiroir de la page d'accueil lorsqu'une connexion client est en cours
* `Amélioration` Messages d'erreur pour les entrées non valides lors de la saisie de l'adresse cible en mode client
* `Amélioration` Support de la connexion via la touche Entrée du clavier virtuel en mode client
* `Amélioration` Maintien de l'état actif du mode serveur après activation (sauf désactivation manuelle ou arrêt du processus de l'application) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `Amélioration` Détection bidirectionnelle des versions entre AutoJs6 et le plugin VSCode et affichage des résultats anormaux _[`issue #89`](http://issues.autojs6.com/89)_
* `Amélioration` Support du module cheerio (Réf. à [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `Amélioration` Les instances JsWebSocket permettent de reconstruire l'instance et d'établir une connexion avec rebuild _[`issue #69`](http://issues.autojs6.com/69)_
* `Amélioration` Le module base64 prend en charge le codage et le décodage des tableaux de nombres et des tableaux d'octets Java
* `Amélioration` Ajout du support de JavaMail pour Android _[`issue #71`](http://issues.autojs6.com/71)_
* `Amélioration` Utilisation du type de données Blob pour obtenir des informations de mise à jour de version afin d'améliorer la compatibilité avec les réseaux sans proxy
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
* `Fonctionnalité` Composants console/imagebutton/ratingbar/switch/textclock/togglebutton et similaires en mode UI
* `Fonctionnalité` Support du type [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) pour les valeurs de couleur des contrôles en mode UI (ex. color="orange")
* `Fonctionnalité` Pleine compatibilité des contrôles avec la méthode attr pour définir les propriétés des contrôles en mode UI (ex. ui.text.attr('color', 'blue'))
* `Fonctionnalité` Support des valeurs de propriété booléennes sous forme abrégée (ex. clickable="true" peut être abrégé en clickable ou isClickable)
* `Fonctionnalité` Support des propriétés booléennes isColored et isBorderless pour les contrôles button
* `Fonctionnalité` Méthode console.resetGlobalLogConfig, pour réinitialiser la configuration globale des journaux
* `Fonctionnalité` Méthode web.newWebSocket, pour créer des instances WebSocket (voir Documentation du projet > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
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

* `Fonctionnalité` Module ocr, pour la reconnaissance optique de caractères (voir Documentation du projet > [Reconnaissance optique de caractères](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Fonctionnalité` Module notice, pour envoyer des notifications (voir Documentation du projet > [Notifications](https://docs.autojs6.com/#/notice))
* `Fonctionnalité` Module s13n, pour normaliser les données (voir Documentation du projet > [Normalisation](https://docs.autojs6.com/#/s13n))
* `Fonctionnalité` Classe Color, pour créer et manipuler des objets couleur (voir Documentation du projet > [Classe Color](https://docs.autojs6.com/#/colorType))
* `Fonctionnalité` Méthode colors.setPaintColor, pour définir la couleur du pinceau android.graphics.Paint (voir Documentation du projet > [Couleurs](https://docs.autojs6.com/#/color?id=m-setpaintcolor))
* `Fonctionnalité` Fonction et réglage pour garder l'écran allumé pendant l'exécution de l'application au premier plan
* `Fonctionnalité` Lanceur de documentation indépendant, pour lire la documentation de l'application (affichable ou masquable dans les paramètres)
* `Correction` Problème dans la méthode colors.toString
* `Correction` Problème de valeurs de constantes de couleur erronées dans colors.material
* `Correction` Problème d'ajout automatique du préfixe de protocole dans la méthode app.openUrl
* `Correction` Comportement anormal des méthodes app.viewFile/editFile si le fichier correspondant n'existe pas
* `Correction` Problème avec la fonction de rappel de la méthode pickup
* `Correction` Problème où les sélecteurs bounds/boundsInside/boundsContains ne peuvent pas filtrer correctement les rectangles nuls (par exemple, rectangles inversés) _[`issue #49`](http://issues.autojs6.com/49)_
* `Correction` Problème de service d'accessibilité avec des erreurs de service fréquentes (tentative de correction)
* `Correction` Problème où l'information bounds des contrôles affichée dans l'analyse de mise en page remplace les signes négatifs par des virgules
* `Correction` Problème où l'application crash après changement de thème ou de langue en cliquant ou appuyant longuement sur le tag de document de la page d'accueil
* `Correction` Problème de tremblement lors de l'ajustement de la taille de la police par zoom à deux doigts dans l'éditeur de texte
* `Correction` Problème de téléchargement de certains dépôts dans les scripts de construction (tous intégrés) _[`issue #40`](http://issues.autojs6.com/40)_
* `Correction` Problème de plugin d'action de Tasker ne pouvant pas ajouter d'actions AutoJs6 (tentative de correction) _[`issue #41`](http://issues.autojs6.com/41)_
* `Correction` Problème de ButterKnife ne pouvant pas résoudre les identifiants de ressources avec les versions récentes de JDK _[`issue #48`](http://issues.autojs6.com/48)_
* `Correction` Problème d'utilisation incorrecte du paramètre de taille dans la méthode images.medianBlur
* `Correction` Problème d'affichage complet des noms de scripts dans le module engines perdant le point entre le nom et l'extension de fichier
* `Correction` Problème potentiel de calcul incorrect dans l'algorithme de détection de distance RGB pondéré (tentative de correction)
* `Correction` Problème où les méthodes liées aux fenêtres flottantes du module console ne peuvent pas être appelées avant la méthode show
* `Correction` Problème où certaines méthodes, comme console.setSize, peuvent ne pas fonctionner _[`issue #50`](http://issues.autojs6.com/50)_
* `Correction` Problème où les propriétés minDate et maxDate du contrôle de sélection de date en mode UI ne parviennent pas à analyser correctement les dates
* `Correction` Problème de duplication de tâches en cours d'exécution lors de la commutation rapide vers l'onglet "Tâches" de la page d'accueil après le lancement d'un script
* `Correction` L'état de la page du gestionnaire de fichiers pouvait être réinitialisé au retour d'une autre page _[`issue #52`](http://issues.autojs6.com/52)_
* `Correction` L'état du tri du gestionnaire de fichiers ne correspondait pas à l'icône affichée
* `Amélioration` Affichage des dates de modification des fichiers et dossiers dans le gestionnaire de fichiers
* `Amélioration` Mémorisation du type de tri dans le gestionnaire de fichiers
* `Amélioration` Ajout de sections sur la compilation et construction du projet et sur les outils d'aide au développement des scripts dans README.md _[`issue #33`](http://issues.autojs6.com/33)_
* `Amélioration` Support de la réduction proactive de la version JDK dans les scripts de construction Gradle en cas de besoin
* `Amélioration` Support de la sélection automatique de la version appropriée des outils de construction selon le type et la version de la plateforme dans les scripts de construction Gradle (dans la mesure du possible)
* `Amélioration` Support d'autres formats pour le paramètre d'option de région dans les méthodes du module images (voir documentation du projet > [Types Omnipotents](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Amélioration` Support de pages abrégées additionnelles (comme pref/homepage/docs/about) pour app.startActivity
* `Amélioration` Ancrage des méthodes globales du module web au module lui-même pour améliorer l'utilisabilité (voir documentation du projet > [Web](https://docs.autojs6.com/#/web))
* `Amélioration` Support par la méthode web.newInjectableWebView des options de configuration WebView par défaut pour une meilleure fonctionnalité
* `Amélioration` Ajout de plusieurs méthodes de conversion et utilitaires dans le module colors, ajout de constantes statiques supplémentaires et de noms de couleurs pouvant être directement utilisés comme paramètres
* `Amélioration` Plusieurs méthodes de style de la console flottante dans le module console, avec configuration unifiée par le constructeur build
* `Amélioration` Déplacement de la console flottante en faisant glisser son titre
* `Amélioration` Fermeture différée automatique de la console flottante après la fin du script
* `Amélioration` Ajustement de la taille de police par pincement à deux doigts dans la console flottante et sa fenêtre Activity
* `Amélioration` Support du paramètre d'option de délai d'attente (timeout) pour les méthodes liées au module http
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
* `Dépendance` Mise à niveau de AndroidX RecyclerView version 1.2.1 -> 1.3.0
* `Dépendance` Mise à jour de Zip4j version 2.11.2 -> 2.11.5
* `Dépendance` Mise à niveau de JUnit Jupiter version 5.9.2 -> 5.9.3
* `Dépendance` Mise à jour de AndroidX Annotation version 1.5.0 -> 1.6.0
* `Dépendance` Mise à jour de Jackson DataBind version 2.14.1 -> 2.14.2
* `Dépendance` Mise à jour de Desugar JDK Libs version 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `Fonctionnalité` Refonte et réécriture de la documentation du projet (partiellement accomplie)
* `Fonctionnalité` Adaptation multilingue en espagnol/français/russe/arabe/japonais/coréen/anglais/chinois traditionnel et autres langues
* `Fonctionnalité` Ajout d'options de paramétrage du chemin de travail avec sélection de chemin, historique et suggestions intelligentes par défaut
* `Fonctionnalité` Navigation vers les dossiers parents successifs dans le gestionnaire de fichiers (jusqu'au dossier "Stockage interne")
* `Fonctionnalité` Le gestionnaire de fichiers permet de définir n'importe quel répertoire comme chemin de travail par raccourci
* `Fonctionnalité` Possibilité d'ignorer des mises à jour et de gérer les versions ignorées
* `Fonctionnalité` L'éditeur de texte prend en charge le zoom à deux doigts pour ajuster la taille de la police
* `Fonctionnalité` Sélecteur UiSelector#idHex, pour filtrer par identifiant hexadécimal de composant (voir Documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType))
* `Fonctionnalité` Sélecteur UiSelector#action, pour filtrer selon les actions prises en charge par les composants (voir Documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType))
* `Fonctionnalité` Sélecteurs Match tels que UiSelector#xxxMatch, pour la correspondance par expressions régulières (voir Documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType))
* `Fonctionnalité` Sélecteur de récupération UiSelector#pickup, pour obtenir des composants en combinant des conditions (voir Documentation du projet > [Sélecteurs](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `Fonctionnalité` Méthode UiObject#detect, pour inspecter les composants (voir Documentation du projet > [Noeuds de composants](https://docs.autojs6.com/#/uiObjectType))
* `Fonctionnalité` Méthode UiObject#compass, pour naviguer entre les composants (voir Documentation du projet > [Noeuds de composants](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `Fonctionnalité` Méthode globale wait, pour l'attente conditionnelle (voir Documentation du projet > [Objet global](https://docs.autojs6.com/#/global?id=m-wait))
* `Fonctionnalité` Méthodes globales cX/cY/cYx, pour mettre les coordonnées à l'échelle (voir Documentation du projet > [Objet global](https://docs.autojs6.com/#/global?id=m-cx))
* `Fonctionnalité` Type global App, pour l'énumération des applications (voir Documentation du projet > [Classe d'énumération des applications](https://docs.autojs6.com/#/appType))
* `Fonctionnalité` Module i18n, pour le traitement multilingue JavaScript (basé sur banana-i18n) (voir Documentation du projet > Internationalisation)
* `Correction` Problèmes d'affichage temporaire du texte et défaillances fonctionnelles de certains boutons après un changement de langue
* `Correction` Barre d'outils de projet non affichée lors du démarrage de l'application si le chemin de travail est un projet
* `Correction` Chemin de travail modifié automatiquement après un changement de langue _[`issue #19`](http://issues.autojs6.com/19)_
* `Correction` Retard significatif lors du démarrage des tâches planifiées (tentative de correction) _[`issue #21`](http://issues.autojs6.com/21)_
* `Correction` Problèmes d'utilisation des modules internes lorsque le nom de module JavaScript est redéfini _[`issue #29`](http://issues.autojs6.com/29)_
* `Correction` Impossibilité de réduire automatiquement le panneau de paramètres rapides sur les systèmes Android avancés (tentative de correction) _[`issue #7`](http://issues.autojs6.com/7)_
* `Correction` Chevauchement de certaines pages avec la zone de la barre de notification sur les systèmes Android avancés
* `Correction` Les exemples de réglage de couleur de pinceau ne fonctionnaient pas correctement sous Android 10+
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
* `Correction` Problèmes de visibilité des boutons de navigation en mode nuit sur les systèmes Android 7.x
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
* `Correction` Problèmes de requêtes non fermées pour http.post et autres méthodes similaires
* `Correction` Problèmes de perte des informations du canal alpha dans les résultats de colors.toString lorsque la valeur alpha est 0
* `Amélioration` Redirection des classes publiques de la version Auto.js 4.x pour assurer une compatibilité descendante maximale possible (dans une certaine mesure)
* `Amélioration` Fusionner tous les modules du projet pour éviter d'éventuels problèmes de référence circulaire (supprimer temporairement le module inrt)
* `Amélioration` Migrer la configuration de construction Gradle de Groovy à KTS
* `Amélioration` Ajouter un support multilingue pour les messages d'exception de Rhino
* `Amélioration` L'interrupteur de permissions du tiroir de la page d'accueil ne montre des messages que lorsqu'il est activé
* `Amélioration` La disposition du tiroir de la page d'accueil s'attache directement sous la barre d'état pour éviter les incompatibilités de barre de couleur supérieure
* `Amélioration` Afficher l'adresse IP du périphérique correspondant dans le tiroir de la page d'accueil après que le client et le serveur se sont connectés
* `Amélioration` Compatibilité des fonctions de vérification de mise à jour, téléchargement et avis de mise à jour avec le système Android 7.x
* `Amélioration` Reconcevoir la page des paramètres (migration vers AndroidX)
* `Amélioration` La page des paramètres prend en charge la pression longue sur les options de configuration pour afficher des informations détaillées
* `Amélioration` Option "Suivre le système" pour le mode nuit (Android 9+)
* `Amélioration` Compatibilité de l'écran de démarrage de l'application avec le mode nocturne
* `Amélioration` Ajouter plus d'options de couleurs Material Design au thème de l'application
* `Amélioration` Compatibilité de la couleur du texte indicatif dans la boîte de recherche de la page d'accueil avec le mode nocturne
* `Amélioration` Adaptation au mode nuit des dialogues/textes/FAB/AppBar/éléments de liste et autres composants
* `Amélioration` Ajouter des identifiants numériques aux icônes d'application pour améliorer l'expérience utilisateur avec plusieurs versions open source coexistantes
* `Amélioration` Alléger et adapter les icônes des éléments de liste dans le gestionnaire de fichiers/panneau des tâches à la couleur du thème
* `Amélioration` Compatibilité des pages telles que les documents/paramètres/à propos/couleurs de thème/analyse de mise en page et des menus des boutons flottants avec le mode nocturne
* `Amélioration` Cliquer sur le bouton d'analyse de mise en page du bouton flottant effectue directement l'analyse de la plage de mise en page
* `Amélioration` Le thème de l'analyse de mise en page est adaptable (la fenêtre flottante suit le thème de l'application, le panneau de configuration rapide suit le thème du système)
* `Amélioration` Compatibilité de la disposition des pages avec la disposition RTL (Right-To-Left) dans la mesure du possible
* `Amélioration` Ajouter des effets d'animation des icônes à la page à propos
* `Amélioration` Mise à jour automatique de l'année dans le texte de la déclaration de droits d'auteur sur la page à propos
* `Amélioration` Après l'installation initiale de l'application, déterminer et définir automatiquement un répertoire de travail approprié
* `Amélioration` Désactiver la fonction de zoom à deux doigts sur la page des documents pour éviter une anomalie de l'affichage du contenu
* `Amélioration` Simplifier le nom et le chemin des tâches affichés dans les éléments de liste du panneau des tâches par un chemin relatif
* `Amélioration` Abréger le texte des boutons de l'éditeur de texte pour éviter le débordement du contenu
* `Amélioration` Prise en charge de la restauration de la taille de police par défaut dans la configuration de l'éditeur de texte
* `Amélioration` Améliorer la vitesse de réponse au clic sur le bouton flottant
* `Amélioration` Réorganiser la liste des informations de contrôle de mise en page selon la fréquence d'utilisation potentielle
* `Amélioration` Optimiser le format de sortie automatiquement lors du clic et copie sur les informations de contrôle de mise en page selon le type de sélecteur
* `Amélioration` ui.R et auto.R peuvent obtenir commodément l'ID de ressources d'AutoJs6
* `Amélioration` Lors de la sélection de fichiers à l'aide de la fenêtre flottante, appuyer sur la touche de retour ramène au répertoire supérieur au lieu de fermer directement la fenêtre
* `Amélioration` Prise en charge de la détection de validité numérique et de la conversion automatique des symboles pointillés lors de la saisie d'une adresse en mode client se connectant à l'ordinateur
* `Amélioration` Ajouter une protection contre la réécriture à certains objets globaux et modules intégrés (consultez la documentation du projet > Objets globaux > [Protection contre la réécriture](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `Amélioration` importClass et importPackage prennent en charge des paramètres de chaîne et des paramètres de longueur variable
* `Amélioration` ui.run prend en charge l'impression des informations de trace de pile en cas d'exception
* `Amélioration` Les méthodes associées à l'utilisation d'applications dans le module app prennent en charge des paramètres de type App et des paramètres d'alias d'application
* `Amélioration` app.startActivity et autres prennent en charge des paramètres d'option d'URL (voir code d'exemple > Applications > Intention)
* `Amélioration` Les méthodes associées au rappel asynchrone dans le module dialogs prennent en charge l'omission des paramètres pré-remplis
* `Amélioration` Le module device renvoie null au lieu de lancer une exception lorsque l'obtention de l'IMEI ou du numéro de série matériel échoue
* `Amélioration` Augmenter la luminosité du texte dans le journal de la fenêtre flottante affichée par console.show pour améliorer la lisibilité du contenu
* `Amélioration` ImageWrapper#saveTo prend en charge la sauvegarde de fichiers image dans des chemins relatifs
* `Amélioration` Refonte de l'objet global colors et prise en charge des modes HSV/HSL et autres espaces de couleur (voir Documentation du projet > [Couleurs](https://docs.autojs6.com/#/color))
* `Dépendance` Localisation de la version 1.4.3 de Android Job
* `Dépendance` Localisation de Android Plugin Client SDK For Locale version 9.0.0
* `Dépendance` Localisation de la version 1.306 de l'API GitHub
* `Dépendance` Ajout de la version 1.0 de JCIP Annotations
* `Dépendance` Ajout de la version 1.5.0 de AndroidX WebKit
* `Dépendance` Ajout de la version 2.8.0 de Commons IO
* `Dépendance` Ajout de la version 2.0.0 de Desugar JDK Libs
* `Dépendance` Ajout de la version 2.13.3 de Jackson DataBind
* `Dépendance` Ajout de la version 2.1.0 de Jaredrummler Android Device Names
* `Dépendance` Ajout de la version 1.0.6 de Jaredrummler Animated SVG View
* `Dépendance` Remplacement de la version 2.1.7 de Jrummyapps ColorPicker par la version 1.1.0 de Jaredrummler ColorPicker
* `Dépendance` Mise à jour de la version de Android SDK 32 -> 33
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
* `Dépendance` Mise à niveau de JUnit Jupiter version 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `Fonctionnalité` Fonction de vérification des mises à jour / téléchargement des mises à jour / notification des mises à jour (voir la page des paramètres) (pas encore supporté pour le système Android 7.x)
* `Correction` Problème de lecture/écriture sur le stockage externe sous Android 10 _[`issue #17`](http://issues.autojs6.com/17)_
* `Correction` Un appui long dans l'éditeur de code pouvait faire planter l'application _[`issue #18`](http://issues.autojs6.com/18)_
* `Correction` Les fonctions "Supprimer la ligne" et "Copier la ligne" du menu d'appui long de l'éditeur étaient inopérantes
* `Correction` Fonction "Coller" absente du menu d'options de l'éditeur de code
* `Amélioration` Conversion de certains messages d'exception en ressources textuelles (en/zh)
* `Amélioration` Réorganisation des boutons de la boîte de dialogue de contenu non enregistré et ajout de la différenciation des couleurs
* `Dépendance` Ajout de GitHub API version 1.306
* `Dépendance` Remplacement de la version 1.0.0 de retrofit2-rxjava2-adapter par la version 2.9.0 d'adapter-rxjava2

# v6.1.0

###### 2022/05/26 - Changement du nom du package, mise à niveau avec prudence

* `Astuce` Modification du nom du package de l'application vers org.autojs.autojs6 pour éviter le conflit avec le nom du package de l'application open source Auto.js
* `Fonctionnalité` Option "Autorisation de projection multimédia" dans le tiroir d'accueil (via Root/ADB) (détection de l'état expérimentale)
* `Fonctionnalité` Affichage des fichiers et dossiers cachés dans le gestionnaire de fichiers (voir Paramètres)
* `Fonctionnalité` Vérification forcée de Root (voir Paramètres et exemples de code)
* `Fonctionnalité` Module autojs, pour obtenir et manipuler les informations relatives à AutoJs6 (voir Exemples de code > AutoJs6)
* `Fonctionnalité` Module tasks, pour gérer les tâches planifiées (voir Exemples de code > Tâches)
* `Fonctionnalité` Méthode console.launch, pour ouvrir la page des journaux
* `Fonctionnalité` Outil util.morseCode, pour traiter le code Morse (voir Exemples de code > Outils > Code Morse)
* `Fonctionnalité` Outil util.versionCodes, pour consulter les informations de version Android (voir Exemples de code > Outils > Consultation des versions Android)
* `Fonctionnalité` Méthodes util.getClass et similaires, pour obtenir les classes Java et leurs noms (voir Exemples de code > Outils > Obtenir les classes et leurs noms)
* `Fonctionnalité` Méthode timers.setIntervalExt, pour l'exécution périodique conditionnelle (voir Exemples de code > Minuteries > Exécution périodique conditionnelle)
* `Fonctionnalité` Méthodes colors.toInt/rgba et similaires, pour convertir les couleurs (voir Exemples de code > Images et couleurs > Conversion élémentaire des couleurs)
* `Fonctionnalité` Méthodes automator.isServiceRunning/ensureService, pour vérifier et garantir la disponibilité du service d'accessibilité
* `Fonctionnalité` Méthodes automator.lockScreen et similaires, pour les opérations d'accessibilité introduites dans Android 9 (voir Exemples de code > Service d'accessibilité > Nouveautés Android 9)
* `Fonctionnalité` Méthodes automator.headsethook et similaires, pour les opérations d'accessibilité introduites dans Android 11 (voir Exemples de code > Service d'accessibilité > Nouveautés Android 11)
* `Fonctionnalité` Méthode automator.captureScreen, pour capturer l'écran via le service d'accessibilité (voir Exemples de code > Service d'accessibilité > Capture d'écran)
* `Fonctionnalité` La méthode dialogs.build prend en charge des paramètres d'option tels que animation, linkify, etc. (voir des exemples de code > Dialogues > Dialogues personnalisés)
* `Correction` Les paramètres d'option inputHint, itemsSelectedIndex, etc. de dialogs.build ne fonctionnent pas correctement
* `Correction` Anomalies des paramètres de rappel dans JsDialog#on('multi_choice')
* `Correction` Problème de retour constant de -1 par UiObject#parent().indexInParent() _[`issue #16`](http://issues.autojs6.com/16)_
* `Correction` Problème de non-exécution des Thenable retournées par Promise.resolve() à la fin du script
* `Correction` Fautes de frappe possibles dans les noms de packages ou classes (boardcast -> broadcast/auojs -> autojs)
* `Correction` La méthode images.requestScreenCapture pouvait faire planter l'application sur les versions Android récentes (API >= 31)
* `Correction` Des appels simultanés à images.requestScreenCapture par plusieurs instances de script pouvaient faire planter l'application
* `Correction` Problème de gel potentiel lors de l'appel à new RootAutomator()
* `Amélioration` RootAutomator ne peut pas être instancié sans permissions Root
* `Amélioration` Refonte de la page "À propos de l'application et du développeur"
* `Amélioration` Refactorisation de tous les modules JavaScript intégrés
* `Amélioration` Refactorisation de tous les scripts Gradle de la construction et ajout d'un script de configuration commun (config.gradle)
* `Amélioration` L'outil de construction Gradle prend en charge la gestion automatique des numéros de version et le nommage automatique des fichiers de construction
* `Amélioration` L'outil de construction Gradle ajoute une tâche pour joindre un résumé CRC32 aux fichiers de construction (appendDigestToReleasedFiles)
* `Amélioration` La méthode shell inscrit les exceptions d'appel dans le résultat retourné au lieu de les lever directement (try/catch inutile)
* `Amélioration` Utilisation de JSON intégré à Rhino à la place de l'ancien module json2
* `Amélioration` La méthode auto.waitFor accepte un paramètre de délai maximal
* `Amélioration` La méthode threads.start accepte des fonctions fléchées en paramètre
* `Amélioration` La méthode console.trace accepte un niveau de journalisation (voir Exemples de code > Console > Afficher la pile d'appels)
* `Amélioration` Utilisation de Material Color pour améliorer la lisibilité des caractères de la console en thèmes normal et nuit
* `Amélioration` La méthode device.vibrate prend en charge des motifs de vibration et le code Morse (voir Exemples de code > Appareil > Motifs de vibration/Vibration Morse)
* `Amélioration` Adaptation des permissions de lecture/écriture du stockage externe aux versions Android ≥ 30
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
* `Fonctionnalité` Module recorder, pour mesurer le temps (voir Exemples de code > Chronomètres)
* `Fonctionnalité` Activation automatique du service d'accessibilité avec l'autorisation "Modifier les paramètres sécurisés", et interrupteur de réglage correspondant
* `Correction` Problème de non-fermeture automatique du panneau des paramètres rapides après avoir cliqué sur les icônes concernées (tentative de correction) _[`issue #7`](http://issues.autojs6.com/7)_
* `Correction` Problème potentiel de crash d'AutoJs6 lors de l'utilisation de la fonction toast avec le paramètre de forçage d'affichage
* `Correction` Problème potentiel de crash d'AutoJs6 lors de l'envoi de paquets de données incomplets via Socket
* `Amélioration` Activation automatique des services d'accessibilité lors du démarrage ou redémarrage d'AutoJs6 selon les paramètres d'options
* `Amélioration` Tentative d'ouverture automatique des services d'accessibilité lors de l'activation des boutons flottants
* `Amélioration` Complétion des traductions anglaises de tous les fichiers de ressources
* `Amélioration` Ajustement de la disposition du tiroir d'accueil et réduction de l'espacement entre les entrées
* `Amélioration` Ajout de la synchronisation de l'état du commutateur de service en premier plan dans le tiroir principal
* `Amélioration` Synchronisation instantanée de l'état des commutateurs selon les besoins lors de l'ouverture du tiroir principal
* `Amélioration` Ajout de la détection de l'état et des messages de résultat pour l'affichage de la position du pointeur
* `Amélioration` Support des systèmes d'exploitation en 64 bits (référence à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Amélioration` Application immédiate des paramètres de transparence des boutons flottants lors de l'initialisation (sans nécessiter un clic supplémentaire pour appliquer la transparence)
* `Amélioration` Ajout de la détection des fichiers de code d'exemple lors de la réinitialisation du contenu des fichiers et fourniture d'un message de résultat
* `Amélioration` Migration de l'adresse de téléchargement des plugins packagés de GitHub vers JsDelivr
* `Dépendance` Ajout de la version 1.5.1 de Zeugma Solutions LocaleHelper
* `Dépendance` Mise à jour de Android SDK de la version 31 à 32
* `Dépendance` Mise à jour de Kotlinx Coroutines de la version 1.6.0-native-mt à 1.6.0
* `Dépendance` Mise à jour de OpenCV de la version 3.4.3 à 4.5.4 puis 4.5.5 (référence à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dépendance` Mise à jour d'OkHttp3 de la version 3.10.0 à 5.0.0-alpha.4 puis 5.0.0-alpha.6
* `Dépendance` Mise à jour des outils de construction Gradle de la version 7.2.0-beta01 à 7.3.0-alpha06
* `Dépendance` Mise à jour d'Auto.js-ApkBuilder de la version 1.0.1 à 1.0.3
* `Dépendance` Mise à jour de Glide Compiler de la version 4.12.0 à 4.13.1
* `Dépendance` Mise à niveau de Gradle version 7.4-rc-2 -> 7.4.1
* `Dépendance` Mise à jour de Gson de la version 2.8.9 à 2.9.0
* `Dépendance` Rétrogradation de la version de Android Material de 1.6.0-alpha02 à 1.5.0

# v6.0.2

###### 2022/02/05

* `Fonctionnalité` Méthode images.bilateralFilter, pour appliquer un filtre bilatéral aux images
* `Correction` Problème où les appels multiples à toast ne font effet qu'au dernier appel
* `Correction` La méthode toast.dismiss pouvait ne pas fonctionner
* `Correction` Problème où le mode client et le mode serveur peuvent ne pas fonctionner correctement
* `Correction` Problème où l'état des commutateurs de mode client et serveur peut ne pas se rafraîchir correctement
* `Correction` Anomalie de l'analyse des éléments textuels en mode UI sous Android 7.x (Réf à [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `Amélioration` Les exceptions ScriptInterruptedException levées par sleep sont ignorées
* `Dépendance` Ajout de la version 1.0.2 de AndroidX AppCompat (Legacy)
* `Dépendance` Mise à niveau de la version de AndroidX AppCompat de 1.4.0 à 1.4.1
* `Dépendance` Mise à niveau de la version de AndroidX Preference de 1.1.1 à 1.2.0
* `Dépendance` Mise à niveau de la version du moteur Rhino de 1.7.14-SNAPSHOT à 1.7.14
* `Dépendance` Mise à niveau de la version d'OkHttp3 de 3.10.0 à 5.0.0-alpha.3 puis 5.0.0-alpha.4
* `Dépendance` Mise à niveau de la version de Android Material de 1.6.0-alpha01 à 1.6.0-alpha02
* `Dépendance` Mise à niveau de la version des outils de construction Gradle de 7.2.0-alpha06 à 7.2.0-beta01
* `Dépendance` Mise à niveau de Gradle version 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `Fonctionnalité` Support de la connexion du plugin VSCode en mode client (LAN) et serveur (LAN/ADB) (Réf à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Module base64, pour l'encodage et le décodage Base64 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` Méthodes globales isInteger/isNullish/isObject/isPrimitive/isReference, pour vérifier les types
* `Fonctionnalité` Implémentation de compatibilité Object.getOwnPropertyDescriptors (polyfill)
* `Fonctionnalité` Implémentation de compatibilité Array.prototype.flat (polyfill)
* `Amélioration` La méthode global.sleep accepte des plages aléatoires et des paramètres négatifs
* `Amélioration` La méthode global.toast accepte le réglage de durée, le remplacement forcé et l'opération dismiss
* `Amélioration` Globaliser l'objet de nom de package (okhttp3/androidx/de)
* `Dépendance` Mise à jour de la version de Android Material de 1.5.0-beta01 à 1.6.0-alpha01
* `Dépendance` Mise à jour de la version des outils de construction Gradle de 7.2.0-alpha04 à 7.2.0-alpha06
* `Dépendance` Mise à jour de la version de Kotlinx Coroutines de 1.5.2-native-mt à 1.6.0-native-mt
* `Dépendance` Mise à jour de la version du plugin Gradle Kotlin de 1.6.0 à 1.6.10
* `Dépendance` Mise à niveau de Gradle version 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `Fonctionnalité` Ajout d'un bouton de redémarrage d'application en bas du tiroir de la page d'accueil
* `Fonctionnalité` Options "Ignorer l'optimisation de la batterie"/"Afficher au-dessus des autres applications" et similaires dans le tiroir d'accueil
* `Correction` Problème d'affichage anormal de la couleur du thème dans certaines zones après l'installation initiale de l'application
* `Correction` Problème empêchant la construction du projet en l'absence du fichier sign.property
* `Correction` Problème d'enregistrement incorrect du mois pour les tâches uniques dans le panneau des tâches programmées
* `Correction` Problème où la couleur des commutateurs de la page des paramètres de l'application ne change pas avec le thème
* `Correction` Problème où les plugins de packaging ne sont pas reconnus et l'adresse de téléchargement des plugins de packaging est invalide
* `Correction` Problème où l'état du commutateur de la permission d'utilisation sur la page d'accueil peut ne pas se synchroniser
* `Correction` Problème potentiel de fuite de mémoire Mat dans TemplateMatching.fastTemplateMatching
* `Amélioration` Amélioration de la compatibilité de ViewUtil.getStatusBarHeight
* `Amélioration` Suppression des modules relatifs à la connexion utilisateur et du placement des mises en page dans le tiroir de la page d'accueil
* `Amélioration` Suppression des onglets Communauté et Marché de l'accueil et amélioration de l'alignement de la disposition
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
* `Dépendance` Ajout de AndroidX SwipeRefreshLayout version 1.1.0
* `Dépendance` Mise à jour de Android SDK de 28 à 30 puis à 31
* `Dépendance` Mise à niveau d'Android Analytics de 7.0.0 à 13.1.0
* `Dépendance` Mise à niveau d'Android Annotations de 4.5.2 à 4.8.0
* `Dépendance` Mise à jour des outils de construction Gradle de 3.2.1 à 4.1.0 puis à 7.0.3 puis à 7.2.0-alpha04
* `Dépendance` Mise à jour de Android Job de 1.2.6 à 1.4.2
* `Dépendance` Mise à niveau de Android Material de 1.1.0-alpha01 à 1.5.0-beta01
* `Dépendance` Mise à jour d'AndroidX MultiDex de 2.0.0 à 2.0.1
* `Dépendance` Mise à jour d'Apache Commons Lang3 de 3.6 à 3.12.0
* `Dépendance` Mise à niveau de AndroidX AppCompat version 1.0.2 -> 1.4.0
* `Dépendance` Mise à niveau du plugin Gradle ButterKnife de 9.0.0-rc2 à 10.2.1 puis 10.2.3
* `Dépendance` Mise à niveau de ColorPicker de 2.1.5 à 2.1.7
* `Dépendance` Mise à niveau d'Espresso Core de 3.1.1-alpha01 à 3.5.0-alpha03
* `Dépendance` Mise à niveau de EventBus version 3.0.0 -> 3.2.0
* `Dépendance` Mise à jour de Glide Compiler de 4.8.0 à 4.12.0
* `Dépendance` Mise à jour de Gradle Build Tool version 29.0.2 -> 30.0.2
* `Dépendance` Mise à niveau de Gradle version 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `Dépendance` Mise à jour du plugin Groovy-Json de 3.0.7 à 3.0.8
* `Dépendance` Mise à jour de Gson de 2.8.2 à 2.8.9
* `Dépendance` Mise à jour de Java version 8 -> 11 -> 16
* `Dépendance` Mise à jour de Joda Time de 2.9.9 à 2.10.13
* `Dépendance` Mise à niveau de JUnit version 4.12 -> 4.13.2
* `Dépendance` Mise à jour du plugin Gradle Kotlin de 1.3.10 à 1.4.10 puis 1.6.0
* `Dépendance` Mise à jour de Kotlinx Coroutines de 1.0.1 à 1.5.2-native-mt
* `Dépendance` Mise à jour de LeakCanary de 1.6.1 à 2.7
* `Dépendance` Mise à niveau de LicensesDialog de 1.8.1 à 2.2.0
* `Dépendance` Mise à jour de Material Dialogs de 0.9.2.3 à 0.9.6.0
* `Dépendance` Mise à jour d'OkHttp3 de 3.10.0 à 5.0.0-alpha.2 puis 5.0.0-alpha.3
* `Dépendance` Mise à niveau de ReactiveX RxJava2 RxAndroid version 2.0.1 -> 2.1.1
* `Dépendance` Mise à niveau de ReactiveX RxJava2 version 2.1.2 -> 2.2.21
* `Dépendance` Mise à jour de Retrofit2 Converter Gson de 2.3.0 à 2.9.0
* `Dépendance` Mise à jour de Retrofit2 Retrofit de 2.3.0 à 2.9.0
* `Dépendance` Mise à jour de Zip4j de 1.3.2 à 2.9.1
* `Dépendance` Mise à niveau de la version du moteur Rhino de 1.7.7.2 à 1.7.13 puis 1.7.14-SNAPSHOT
* `Dépendance` Mise à niveau d'OpenCV de 3.4.3 à 4.5.4