******

### Historique des Versions

******

# v6.8.0

###### 2026/11/03 - Certaines fonctions passent par des plugins, mise à niveau avec prudence

* `Astuce` Certaines fonctions intégrées sont désormais fournies par des plugins indépendants (OCR, reconnaissance de codes-barres, OpenCV, OpenCC, pinyin, MediaInfo, terminal et documentation hors ligne du projet); installez et activez les plugins correspondants dans le centre de plugins avant utilisation
* `Astuce` Promise et une partie de la syntaxe du moteur Rhino suivent désormais la spécification ECMAScript (par exemple, les rappels de Promise s'exécutent comme des microtâches et la réaffectation d'une variable const lève une TypeError); les scripts qui dépendent de l'ancien comportement peuvent nécessiter des ajustements
* `Astuce` Le composant console et la console flottante affichent désormais les journaux du script courant; les journaux globaux sont accessibles via le composant globalconsole ou la page des journaux
* `Astuce` Le SDK cible des applications empaquetées passe de 29 à 37; l'accès au stockage partagé et au réseau local nécessite les autorisations requises par le système
* `Astuce` Pour utiliser images ou d'autres fonctions dépendant d'OpenCV dans une application empaquetée, sélectionnez le plugin OpenCV lors de l'empaquetage
* `Fonctionnalité` Assistant de configuration, pour effectuer successivement les demandes d'autorisation et l'installation des plugins lors de la première utilisation, accessible depuis le message de bienvenue de l'accueil après le premier démarrage (accès: Paramètres > Configuration > Assistant de configuration)
* `Fonctionnalité` Assistant d'installation des plugins, pour sélectionner les plugins officiels par catégorie et les installer par lots, avec installation silencieuse via le plugin 3-Setup Installer lorsque l'autorisation Shizuku/Root/Dhizuku est disponible (accès: Assistant de configuration/menu du bouton flottant de l'onglet Plugins de l'accueil/menu "+" du centre de plugins)
* `Fonctionnalité` Assistant d'autorisations, pour sélectionner les autorisations nécessaires et les demander successivement, avec les profils intégrés "Standard"/"Minimal"/"Tout" et des profils personnalisés (accès: Assistant de configuration/bouton du titre du groupe "Autorisations" dans le tiroir d'accueil)
* `Fonctionnalité` Le moteur Rhino prend en charge la syntaxe class, dont les champs, les blocs statiques, les membres privés, l'héritage de types intégrés tels que Error/Array/Map ainsi que l'extension de classes et d'interfaces Java (voir Documentation du projet > [Syntaxe des classes](https://docs.autojs6.com/#/classSyntax))
* `Fonctionnalité` Le moteur Rhino prend en charge async/await, dont les fonctions async, les fonctions fléchées async, les méthodes async, les générateurs async et for await (voir Documentation du projet > [async/await et continuations](https://docs.autojs6.com/#/asyncAwait))
* `Fonctionnalité` Plugins Node.js Runtime et Bun Runtime, pour exécuter des scripts et projets Node.js et Bun, avec accès à la plupart des capacités de script d'AutoJs6 comme l'automatisation, les images, l'interface et les fichiers
* `Fonctionnalité` Plugins Python Runtime, Lua Runtime et QuickJS Runtime, pour exécuter des scripts Python, Lua et QuickJS (JavaScript)
* `Fonctionnalité` Plugins Java Runtime et Kotlin Runtime, pour compiler et exécuter du code source Java/Kotlin (Java nécessite Android 7+, Kotlin nécessite Android 8+)
* `Fonctionnalité` Plugin TypeScript Engine, pour compiler et exécuter des fichiers et projets TypeScript, avec vérification des types et mises en page TSX en mode UI; les tâches planifiées, les raccourcis et la page des journaux peuvent aussi exécuter TypeScript
* `Fonctionnalité` Module ai, pour appeler des modèles locaux ou en ligne via des plugins fournisseurs d'IA (comme 3-Stone AI), avec sortie en flux, sessions persistantes et résultats JSON structurés (voir Exemples de code > ai)
* `Fonctionnalité` API de script ai.agent, pour démarrer et contrôler des tâches 3-Stove Agent (y compris le mode planification et les tâches en arrière-plan); les scripts peuvent aussi être enregistrés pour être appelés par l'agent (nécessite le plugin 3-Stove Agent) (voir Exemples de code > agent)
* `Fonctionnalité` Plugin MCP Server, pour accéder aux opérations de scripts, fichiers et appareil d'AutoJs6 via des clients MCP (accès: tiroir d'accueil/centre de plugins)
* `Fonctionnalité` Module compose, pour construire des interfaces, des fenêtres flottantes et des dialogues avec Jetpack Compose; l'éditeur de code peut ouvrir les interfaces dans le concepteur visuel 3-Block Compose pour les modifier (nécessite le plugin 3-Block Compose) (voir Exemples de code > 3-Block Compose)
* `Fonctionnalité` Module speech, pour la reconnaissance vocale hors ligne, la détection de mot d'activation, la synthèse vocale, la reconnaissance du locuteur et l'analyse audio (nécessite le plugin 3-Macaw Speech) (voir Exemples de code > 3-Macaw Speech)
* `Fonctionnalité` Plugin 3-Shell Terminal, pour utiliser un terminal dans l'application; les scripts peuvent gérer les sessions de terminal via l'objet global terminal (accès: tiroir d'accueil/barre d'outils du projet/menu des dossiers du gestionnaire de fichiers) (voir Exemples de code > Terminal)
* `Fonctionnalité` Module installer, pour installer, mettre à jour et désinstaller des applications, avec opérations silencieuses via Shizuku/Root/Dhizuku (nécessite le plugin 3-Setup Installer) (voir Documentation du projet > [Installateur](https://docs.autojs6.com/#/installer))
* `Fonctionnalité` Module mail, pour envoyer, recevoir et rechercher des e-mails, télécharger les pièces jointes et surveiller l'arrivée de nouveaux e-mails (nécessite le plugin 3-Stamp Mail) (voir Documentation du projet > [Courrier](https://docs.autojs6.com/#/mail))
* `Fonctionnalité` Module epub, pour lire le contenu des livres électroniques et ouvrir le lecteur (nécessite le plugin 3-Folio EPUB) (voir Documentation du projet > [Livres électroniques](https://docs.autojs6.com/#/epub))
* `Fonctionnalité` Module gallery, pour choisir, afficher, modifier et convertir des images (nécessite le plugin 3-Maple Image) (voir Documentation du projet > [Galerie](https://docs.autojs6.com/#/gallery))
* `Fonctionnalité` Objet global imgui, pour construire des fenêtres flottantes et des interfaces plein écran avec Dear ImGui (nécessite le plugin ImGui)
* `Fonctionnalité` Module yolo, pour la détection d'objets via le plugin YOLO (voir Documentation du projet > [YOLO](https://docs.autojs6.com/#/yolo))
* `Fonctionnalité` Méthode files.batchRename, pour renommer des fichiers par lots selon des règles comme rechercher et remplacer, expressions régulières, compteurs et dates, avec aperçu et annulation (nécessite le plugin Batch Rename) (voir Documentation du projet > [Fichiers](https://docs.autojs6.com/#/files))
* `Fonctionnalité` Module plugins.tools, pour lister et appeler les outils fournis par les plugins (nécessite un plugin qui fournit des outils) (voir Documentation du projet > [Plugins](https://docs.autojs6.com/#/plugins))
* `Fonctionnalité` Méthodes images.quantize/quantizeToFile et option de quantification de images.save et des méthodes associées, pour compresser des images PNG (nécessite le plugin Image Quantization)
* `Fonctionnalité` Module flow et méthode globale waitAsync, pour enchaîner attentes et opérations, avec branches, boucles, délais d'expiration, nouvelles tentatives et annulation (voir Documentation du projet > [Flux](https://docs.autojs6.com/#/flow))
* `Fonctionnalité` Méthodes d'automatisation comme smartClick/scrollUntil/typeInto/launchAndWait et méthodes d'attente comme waitForIdle/waitForToast, pour cliquer sur des widgets, faire défiler jusqu'à trouver, saisir du texte et changer d'application (avec prise en charge des chaînes Flow)
* `Fonctionnalité` La méthode select prend en charge la syntaxe de sélecteur en chaîne, combinant attributs, expressions régulières et conditions booléennes
* `Fonctionnalité` Méthodes comme isStale/waitUntilGone/toJSON/first/last/clickEach pour UiObject et les collections de widgets, pour attendre l'état des noeuds, sérialiser et agir widget par widget
* `Fonctionnalité` Méthodes auto.findWindows/explain/dump, pour obtenir des fenêtres selon des conditions, diagnostiquer les sélecteurs et exporter les arbres de noeuds
* `Fonctionnalité` Méthodes shell.spawn/shizuku.spawn, pour exécuter des commandes en tant que processus et lire leur sortie en temps réel, avec écriture d'entrée, pseudo-terminaux et arrêt des processus (l'option adb et shizuku.spawn nécessitent Shizuku, l'option root nécessite l'accès Root)
* `Fonctionnalité` Le constructeur Shell prend en charge l'objet d'options { cmd, root, adb, pty, env, cwd }, avec de nouvelles méthodes comme $shell.setDefaultOptions/checkAccess (compatible avec Auto.js Pro)
* `Fonctionnalité` Module settings, pour lire, modifier et exporter par catégorie les paramètres de l'application (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Paramètres](https://docs.autojs6.com/#/settings))
* `Fonctionnalité` Module work_manager, pour gérer les tâches planifiées et de diffusion (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Work Manager](https://docs.autojs6.com/#/workManager))
* `Fonctionnalité` Module power_manager, pour consulter et demander l'exemption d'optimisation de la batterie (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (voir Documentation du projet > [Power Manager](https://docs.autojs6.com/#/powerManager))
* `Fonctionnalité` Module memory, pour lire dans les scripts les diagnostics mémoire d'AutoJs6, comme les instantanés de mémoire, les environnements d'exécution de scripts non récupérés et les vidages de tas hprof (voir Documentation du projet > [Mémoire](https://docs.autojs6.com/#/memory)) _[`issue #452`](http://issues.autojs6.com/452)_
* `Fonctionnalité` Module tts, pour la synthèse vocale (voir Documentation du projet > [Synthèse vocale](https://docs.autojs6.com/#/tts))
* `Fonctionnalité` Objet global pangu, pour traiter et vérifier l'espacement des textes mêlant chinois et écritures occidentales (voir Documentation du projet > [Espacement du texte](https://docs.autojs6.com/#/pangu))
* `Fonctionnalité` Méthodes comme distance/blend/contrast/harmony et objet colors.theme pour le module colors, pour les calculs de couleur, la génération de palettes, la conversion vers les espaces colorimétriques OKLab/OKLCH et la modification de la couleur du thème de l'application (voir Exemples de code > Images et couleurs)
* `Fonctionnalité` Méthodes images.countPointsByColor/getMeanColor, pour compter les pixels correspondant à une couleur et obtenir la couleur moyenne
* `Fonctionnalité` Méthode device.getMarketName et propriété device.pageSize (getter), pour obtenir le nom commercial de l'appareil et la taille de page mémoire du système _[`issue #513`](http://issues.autojs6.com/513)_
* `Fonctionnalité` Méthodes console.setTimeVisible/setTimeFormat/setColorful/setAvoidStatusBar/setInputVisible et options correspondantes de console.build
* `Fonctionnalité` Méthodes runtime.loadAar/loadJarWithR8, pour charger dynamiquement des fichiers AAR et charger des fichiers JAR via le plugin R8 Compiler (ce dernier est expérimental)
* `Fonctionnalité` Les mises en page UI prennent en charge les ressources du répertoire res du projet, comme les chaînes, couleurs, styles et images, en choisissant les répertoires à qualificateurs selon le mode nuit, la langue, l'orientation de l'écran, etc. (voir Documentation du projet > [Interface utilisateur](https://docs.autojs6.com/#/ui))
* `Fonctionnalité` Les mises en page XML Android natives prennent en charge davantage d'attributs de widgets, les mises en page ConstraintLayout/CoordinatorLayout et les composants Material (voir Documentation du projet > [Attributs XML Android](https://docs.autojs6.com/#/uiAndroidAttributes))
* `Fonctionnalité` Méthode ui.useAndroidLayout, pour activer les composants de mise en page Android natifs
* `Fonctionnalité` Composant globalconsole en mode UI, pour afficher les journaux globaux; les composants console permettent de configurer le titre, le style et la barre de saisie
* `Fonctionnalité` Les écouteurs d'événements du mode UI prennent en charge la suspension de coroutines (comme dialogs.confirm(...).await()), ce qui nécessite d'activer la fonctionnalité continuation dans le projet de script (voir Documentation du projet > [Continuations](https://docs.autojs6.com/#/continuation))
* `Fonctionnalité` Les rappels de base de données du module sqlite prennent en charge la méthode onDowngrade, pour gérer la rétrogradation de version de la base de données (voir Documentation du projet > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Fonctionnalité` Page "Gérer les paramètres d'AutoJs6", pour exporter/importer les paramètres par catégorie, restaurer les valeurs par défaut et annuler la dernière opération, avec chiffrement des identifiants et sauvegardes automatiques (accès: Paramètres > Configuration > Gérer les paramètres d'AutoJs6)
* `Fonctionnalité` Sauvegarde des raccourcis, pour archiver automatiquement les raccourcis de l'écran d'accueil, avec export/import et restauration par lots (accès: Paramètres > Configuration > Sauvegarde des raccourcis)
* `Fonctionnalité` Gestionnaire du service d'accessibilité, permettant le démarrage/l'arrêt, le nettoyage du cache, l'autodiagnostic, la surveillance des événements et la copie des informations de diagnostic (accès: entrée du service d'accessibilité du tiroir d'accueil)
* `Fonctionnalité` Le gestionnaire de fichiers prend en charge la sélection multiple et la copie/le déplacement/la suppression par lots, avec une nouvelle barre de chemin pour remonter aux dossiers parents, revenir en arrière/avancer et passer à la carte SD
* `Fonctionnalité` Le gestionnaire de fichiers permet de parcourir des dossiers restreints et d'en modifier les fichiers via Shizuku ou Root (nécessite l'autorisation correspondante; les emplacements accessibles dépendent du système)
* `Fonctionnalité` Option "Exécution flottante" dans le menu des scripts du gestionnaire de fichiers, pour exécuter/arrêter les scripts, modifier les fichiers et consulter les journaux via une barre d'outils flottante
* `Fonctionnalité` Le gestionnaire de fichiers permet de parcourir des archives, prévisualiser des fichiers Markdown/HTML, afficher des images, lire de l'audio/de la vidéo et ouvrir des livres électroniques via les plugins correspondants
* `Fonctionnalité` Le gestionnaire de fichiers permet d'afficher les informations et le manifeste des paquets d'installation comme APK/APKS/XAPK/AAB
* `Fonctionnalité` Le gestionnaire de fichiers permet de renommer par lots fichiers et dossiers via le plugin Batch Rename, avec aperçu des nouveaux noms, annulation et historique des renommages (accès: barre d'outils de sélection multiple/menu des dossiers/menu "Plus" du dialogue de renommage)
* `Fonctionnalité` Options "Exécuter avec AutoJs4" et "Exécuter avec AutoJs4 et comparer" dans le menu des scripts du gestionnaire de fichiers, pour exécuter des scripts avec le moteur AutoJs4 et comparer les résultats (nécessite le plugin AutoJs4 Runtime)
* `Fonctionnalité` L'éditeur de code prend en charge l'éditeur Ace via le plugin Ace Editor, avec complétion de code, indications de paramètres, documentation au survol, diagnostics et panneau de raccourcis, le moteur de l'éditeur se choisissant dans les paramètres
* `Fonctionnalité` L'éditeur de code permet de prévisualiser la mise en page des scripts en mode ui dans l'éditeur, et toucher un widget dans l'aperçu mène au code source correspondant (accès: interrupteur "Aperçu" de la barre d'outils)
* `Fonctionnalité` Panneau de journaux inférieur dans l'éditeur de code, avec démarrage/arrêt des scripts et accès au code source des cadres de pile (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Fonctionnalité` L'éditeur de code prend en charge une page de paramètres dédiée, le mode plein écran immersif et la conversion d'encodage des fichiers, avec une barre d'informations affichant la position du curseur et l'encodage
* `Fonctionnalité` L'analyse de mise en page ajoute une barre de recherche et "Copier comme sélecteur", permet d'enregistrer les arbres de noeuds pour une analyse hors ligne, et "Générer le code" prend en charge davantage de modes d'attente, d'actions et le style Flow
* `Fonctionnalité` Les couleurs du thème prennent en charge des bibliothèques de couleurs personnalisées, qui peuvent être créées, clonées, importées/exportées et modifiées
* `Fonctionnalité` Bibliothèques de couleurs intelligentes, pour changer automatiquement la couleur du thème selon des états comme le mode nuit, le moment de la journée, le lever/coucher du soleil et le niveau de batterie, avec des préréglages intégrés comme "Suivre le fond d'écran"
* `Fonctionnalité` L'historique des versions prend en charge les aperçus, la comparaison des différences, les instantanés manuels et l'épinglage de versions, et la corbeille prend en charge la restauration par lots et l'aperçu du contenu, les deux avec des politiques de conservation configurables
* `Fonctionnalité` Tâches planifiées mensuelles/annuelles/avec compte à rebours et conditions de fin de répétition _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `Fonctionnalité` Les dossiers de projet permettent de créer des tâches planifiées et des tâches de diffusion, et les tâches de diffusion ajoutent le type "À l'arrivée d'un e-mail" (nécessite le plugin 3-Stamp Mail)
* `Fonctionnalité` Outil "Prélever une couleur à l'écran" dans le tiroir d'accueil (nécessite le plugin 3-Color Picker)
* `Fonctionnalité` Page "Diagnostic mémoire" dans les options pour les développeurs, pour afficher l'utilisation de la mémoire et les scripts terminés mais non récupérés, avec export de vidages de tas hprof (accès: Options pour les développeurs > Diagnostic mémoire)
* `Fonctionnalité` Les options pour les développeurs permettent d'afficher les enregistrements de fin de processus (Android 11+), avec un interrupteur "Autoriser les scripts Node.js à utiliser Inspector" pour déboguer les scripts Node.js avec des points d'arrêt
* `Fonctionnalité` Page de gestion des modèles de génération de code, avec import local, téléchargement à la demande, gestion de plusieurs versions et mise à jour des modèles officiels (accès: Paramètres > Service d'accessibilité > Modèles de génération de code)
* `Fonctionnalité` Les applications empaquetées prennent en charge les projets et scripts Node.js, qui nécessitent le plugin Node.js Runtime à l'exécution
* `Fonctionnalité` Les applications empaquetées prennent en charge la compilation des projets TypeScript à la construction, avec chiffrement facultatif du résultat compilé
* `Fonctionnalité` La configuration des applications empaquetées permet d'afficher ou masquer les journaux et de définir les raccourcis du lanceur _[`issue #550`](http://issues.autojs6.com/550)_
* `Fonctionnalité` Connexion au plugin VSCode par code QR, confirmation d'appariement et découverte sur le réseau local (nécessite le plugin VSCode 1.1.0+)
* `Fonctionnalité` Gestion de fichiers à distance et exécution de scripts sur l'appareil depuis le plugin VSCode (nécessite le plugin VSCode 1.1.0+)
* `Fonctionnalité` Gestionnaire de connexions pour les modes client et serveur, avec reconnexion automatique après une déconnexion inattendue en mode client
* `Correction` La mémoire n'était pas libérée après la fin des scripts ou la fermeture de certaines pages, et son utilisation augmentait à chaque exécution (par exemple pour les scripts utilisant threads.start, des minuteurs, WebSocket, des widgets webview ou runtime.loadDex) _[`issue #548`](http://issues.autojs6.com/548)_
* `Correction` AutoJs6 et les applications empaquetées plantaient au démarrage, ou ne pouvaient pas ouvrir des données comme l'historique des versions et la corbeille, en présence de données écrites par une version plus récente (par exemple après l'installation d'une version antérieure)
* `Correction` Sous Android 14+, la demande d'autorisation de capture d'écran pouvait faire planter l'application à cause d'un échec de démarrage du service au premier plan _[`issue #516`](http://issues.autojs6.com/516)_
* `Correction` images.captureScreen levait une SecurityException sous Android 14+ après une rotation de l'écran ou lorsque l'image restait figée
* `Correction` Des appels OCR répétés depuis plusieurs threads ou fichiers pouvaient demander plusieurs fois l'autorisation de capture d'écran _[`issue #551`](http://issues.autojs6.com/551)_
* `Correction` Le service du plugin Paddle OCR ne se rétablissait pas après une déconnexion et ne se réveillait pas automatiquement lorsqu'il n'était pas en cours d'exécution _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_ _[`issue #517`](http://issues.autojs6.com/517)_
* `Correction` Sur certains systèmes (comme ColorOS), les plugins qui n'étaient pas en cours d'exécution ne pouvaient pas être connectés et devaient être activés manuellement dans le centre de plugins
* `Correction` Les applications empaquetées pouvaient planter en utilisant successivement Paddle OCR et Rapid OCR, et l'OCR était indisponible lorsque plusieurs versions du plugin Paddle OCR étaient sélectionnées
* `Correction` Les méthodes app.isInstalled/kill et similaires ne traitaient pas correctement les paramètres de nom de package _[`issue #495`](http://issues.autojs6.com/495)_
* `Correction` Le résultat de sélection multiple de dialogs.multiChoice était converti à tort en chaîne _[`issue #518`](http://issues.autojs6.com/518)_
* `Correction` Les déclarations const dans le corps d'une boucle n'étaient pas réaffectées à chaque itération (par exemple, `const item = list[i]` dans une boucle for/while gardait toujours la première valeur)
* `Correction` Comportements incorrects des portées, de la conversion des arguments et de certaines méthodes intégrées (comme Object.fromEntries et Object.defineProperty) du moteur Rhino _[`Rhino #2518`](https://github.com/mozilla/rhino/issues/2518)_
* `Correction` Les expressions régulières utilisant des échappements de propriétés Unicode levaient des exceptions sous Android 7.x
* `Correction` runtime.loadJar ne pouvait pas charger les fichiers class compilés par des versions plus récentes de Java, et runtime.loadDex/loadJar pouvaient échouer sous Android 7.x
* `Correction` await dans les coroutines ne levait pas d'erreur en cas de rejet, les scripts étaient arrêtés lors d'une nouvelle suspension après reprise, et la méthode wait de Promise bloquait indéfiniment le thread du script
* `Correction` L'appel des méthodes liées à shell pouvait se figer sur les appareils sans accès Root
* `Correction` L'application pouvait planter lorsqu'un dialogue de script se fermait à la fin de son thread, ou lorsqu'un script était arrêté pendant qu'un thread enfant traitait encore des images
* `Correction` Les scripts ne se terminaient parfois pas automatiquement après la fin de leurs threads enfants ou minuteurs, et des erreurs survenaient parfois à l'arrêt de scripts avec des minuteurs setInterval
* `Correction` ui.run, threads.disposable, les dialogues bloquants, les gestes et méthodes similaires retournaient parfois trop tôt ou bloquaient indéfiniment en attendant les résultats d'autres threads
* `Correction` Les réglages de position et de taille de la console flottante dépendaient de l'ordre des appels, et le glissement de sa barre de titre pouvait cesser de fonctionner
* `Correction` console.log et méthodes similaires affichaient les chaînes contenues dans les objets ou tableaux sans apostrophes, contrairement au format d'Auto.js 4
* `Correction` L'option insecure des requêtes http pouvait faire ignorer la validation des certificats aux requêtes suivantes, et la configuration de sécurité réseau pouvait bloquer de manière inattendue les requêtes HTTP des versions publiées
* `Correction` La lecture du corps de la réponse dans les rappels then des Promise renvoyées par http.getAsync et méthodes similaires levait une NetworkOnMainThreadException
* `Correction` Le sélecteur de fichiers des widgets WebView en mode UI ne pouvait pas s'ouvrir
* `Correction` Les expressions des modèles d'éléments des widgets list/grid en mode UI ne pouvaient pas accéder à l'élément courant, laissant les éléments de la liste vides
* `Correction` Des attributs comme l'alignement RelativeLayout, paddingHorizontal/paddingVertical et scrollbars étaient sans effet ou faisaient échouer le chargement de la mise en page, et les scripts UI pouvaient se fermer de manière inattendue en écran partagé
* `Correction` Les arguments d'expression régulière des sélecteurs, les sélecteurs contains et les limites du nombre de résultats fonctionnaient mal, et les exceptions levées par les méthodes de sélecteur globales ne pouvaient pas être interceptées avec try/catch
* `Correction` UiObject#child/parent et méthodes similaires pouvaient entrer en récursion infinie, UiObject#clickBounds pouvait terminer de manière inattendue la boucle d'événements du script, et la recherche de widgets pouvait attendre indéfiniment avec findOnUiThread activé
* `Correction` Après le redémarrage du service d'accessibilité, les requêtes de sélecteur renvoyaient des résultats vides et les rappels d'événements cessaient de fonctionner, et l'état ainsi que le démarrage/l'arrêt du service ne correspondaient pas à l'interrupteur du tiroir d'accueil
* `Correction` Le service d'accessibilité pouvait être interrompu de manière inattendue lors d'opérations consécutives comme back _[`issue #527`](http://issues.autojs6.com/527)_
* `Correction` Les actions globales d'automator pouvaient lever une NullPointerException au premier appel, et les coordonnées verticales des gestes étaient mal mises à l'échelle
* `Correction` L'observateur de notifications signalait les notifications système comme des toasts et manquait des événements de notification
* `Correction` currentPackage/currentActivity pouvaient renvoyer le paquet et l'Activity d'une fenêtre déjà fermée (comme l'interface système ou le lanceur) (by [DeanTMaxim](https://github.com/DeanTMaxim)) _[`issue #570`](http://issues.autojs6.com/570)_ _[`pr #571`](http://pr.autojs6.com/571)_
* `Correction` Certaines méthodes de images pouvaient recycler les images trop tôt, provoquer des fuites de mémoire native ou faire planter l'application avec des arguments non valides
* `Correction` Résultats de correspondance, précision des coordonnées et tri incorrects de images.findImage/matchTemplate/matchFeatures, et résultats erronés de images.invert/clip et méthodes similaires
* `Correction` L'algorithme hs de colors.isSimilar/images.detectColor renvoyait des résultats erronés, et colors.rgb/rgba ne traitaient pas correctement les composantes décimales ou en pourcentage
* `Correction` L'exécution de scripts via des URI externes, en arrière-plan ou depuis Tasker perdait le chemin ou l'extension d'origine, ou échouait _[`issue #545`](http://issues.autojs6.com/545)_ _[`issue #501`](http://issues.autojs6.com/501)_
* `Correction` Les scripts lancés par des intents avec des chemins /sdcard étaient introuvables sur les appareils sans lien /sdcard (comme Fire OS) (by [djbclark](https://github.com/djbclark)) _[`pr #558`](http://pr.autojs6.com/558)_
* `Correction` Les fichiers auto lancés depuis le gestionnaire de fichiers, les tâches planifiées ou des intents externes étaient exécutés comme des scripts JavaScript
* `Correction` Les projets de script ne s'exécutaient pas depuis l'entrée indiquée dans project.json, et les scripts avec BOM n'étaient pas lus avec leur encodage réel
* `Correction` L'importation depuis des URI externes pouvait écrire hors du dossier cible ou écraser des fichiers de même nom à cause d'un traitement incorrect des noms de fichiers
* `Correction` Le gestionnaire de fichiers affichait les échecs de lecture comme des dossiers vides, clignotait ou réinitialisait la position de défilement au retour sur une page, et laissait des éléments surlignés en gris
* `Correction` La restauration depuis l'historique des versions ou la corbeille pouvait perdre le contenu existant, l'historique des versions ne suivait pas les fichiers déplacés, et les deux étaient inaccessibles dans les environnements multi-utilisateurs
* `Correction` L'éditeur de code pouvait perdre le curseur, la sélection ou le contenu non enregistré après la recréation de la page, et le clavier virtuel masquait la zone de contenu
* `Correction` Les contrôles du tiroir d'accueil étaient tronqués sur les écrans étroits, et l'application pouvait planter à la fermeture de l'accueil ou lors de touchers à plusieurs doigts
* `Correction` Une valeur enregistrée non valide de "Garder l'écran allumé lorsqu'il est au premier plan" dans le tiroir d'accueil pouvait faire planter l'accueil (by [djbclark](https://github.com/djbclark)) _[`pr #557`](http://pr.autojs6.com/557)_
* `Correction` Nom de paquet et informations d'Activity incorrects pour l'application récente dans le menu du bouton flottant _[`issue #529`](http://issues.autojs6.com/529)_
* `Correction` Le dialogue "Exécuter un script" du bouton flottant pouvait ne pas être restauré après réduction (correction expérimentale) _[`issue #500`](http://issues.autojs6.com/500)_
* `Correction` Lors de l'actualisation, le centre de plugins pouvait perdre des plugins installés, afficher une liste vide ou installer une mauvaise variante (comme choisir des paquets x86_64 sur des appareils x86)
* `Correction` Les plugins ne pouvaient pas être activés après une autorisation ou un échec temporaire de connexion, et le plugin Offline Docs ne pouvait plus être réactivé après sa désactivation
* `Correction` Les fonctions liées à Shizuku signalaient que le service Shizuku était indisponible lors de la première utilisation après la mise à jour d'AutoJs6
* `Correction` Les commandes lancées via Shizuku (comme l'écoute des touches de volume) continuaient de s'exécuter en arrière-plan et s'accumulaient après l'arrêt forcé d'AutoJs6 _[`issue #474`](http://issues.autojs6.com/474)_
* `Correction` RootAutomator ne pouvait pas être créé avec le seul accès Shizuku, et le processus d'écoute des événements d'entrée ne se terminait pas à la fin de l'enregistrement d'un script
* `Correction` L'interrupteur "Contrôler l'exécution des scripts avec la touche 'Volume +'" des paramètres nécessitait de redémarrer l'application pour prendre effet
* `Correction` L'annulation de l'empaquetage d'un APK signalait un échec de construction, et la vérification du certificat de signature ainsi que la détection des signatures sous Android 9-11 fonctionnaient mal
* `Correction` Les applications empaquetées sans composants d'exécution pouvaient planter faute de bibliothèques natives shell, et les scripts UI empaquetés affichaient parfois une page blanche puis se fermaient au démarrage
* `Correction` La confirmation de l'installation ne pouvait pas être poursuivie lors de l'installation d'APK en arrière-plan
* `Correction` Les fichiers supprimés restaient sur l'appareil lors de l'enregistrement de projets depuis le plugin VSCode _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `Correction` Le traitement des journaux pouvait épuiser la mémoire à la réception d'une archive de projet par le plugin VSCode _[`issue #523`](http://issues.autojs6.com/523)_
* `Correction` Des connexions obsolètes du plugin VSCode bloquaient l'envoi des journaux, de sorte que les nouvelles connexions ne recevaient pas les journaux _[`issue #385`](http://issues.autojs6.com/385)_
* `Correction` La recherche de mises à jour ne choisissait pas les paquets d'installation selon la priorité des ABI de l'appareil
* `Correction` Conversion incorrecte des coordonnées d'écran (cXy/cYx/setScaleBases), et device.width/height inversés sur les appareils dont l'orientation naturelle est en paysage
* `Correction` Les wake locks de device.keepAwake/keepScreenOn/keepScreenDim sans délai d'expiration n'étaient pas libérés à la fin des scripts
* `Correction` Les chemins des dépendances de modules, du SDK et des modèles d'empaquetage étaient mal résolus lors de la construction du projet avec IntelliJ IDEA/Gradle
* `Amélioration` Initialisation différée des composants non essentiels pour accélérer le démarrage de l'application
* `Amélioration` Les dialogues d'autorisation ne s'ouvrent plus automatiquement au démarrage; l'absence d'autorisation de gestion de tous les fichiers est signalée sur l'accueil avec accès direct à son octroi
* `Amélioration` Adaptation de l'application et des applications empaquetées à Android 17 et aux appareils à pages mémoire de 16 KB
* `Amélioration` Allègement des dépendances, des ressources linguistiques et des ressources d'images du paquet d'installation pour réduire sa taille
* `Amélioration` Meilleur affichage multilingue de l'interface, des messages d'exception et des messages d'erreur du moteur
* `Amélioration` La recherche de mises à jour obtient les informations de version et de paquet d'installation via l'API REST de GitHub
* `Amélioration` L'application intègre l'historique des versions en chinois simplifié et en anglais; les autres langues sont récupérées en ligne et mises en cache
* `Amélioration` Documentation hors ligne du projet transformée en plugin (Offline Docs), installable et mise à jour indépendamment
* `Amélioration` Modèle d'empaquetage transformé en plugin (APK Builder Template), avec vérification de la compatibilité du modèle lors de l'empaquetage
* `Amélioration` Fonctions ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization déplacées vers des plugins
* `Amélioration` Fonctions OpenCC/Pinyin/Pinyin4j/MediaInfo déplacées vers des plugins
* `Amélioration` L'installation des APK utilise désormais le dialogue d'installation du plugin 3-Setup Installer, avec repli sur l'installateur système si le plugin est indisponible; les conteneurs d'APK fractionnés comme APKS/XAPK nécessitent le plugin
* `Amélioration` Amélioration de l'installation et des mises à jour en ligne, du choix des variantes, du tri, des paramètres des plugins et de l'historique des versions dans le centre de plugins, avec un chargement et une actualisation de la liste plus fluides
* `Amélioration` Les plugins officiels sont activés par défaut après installation et suivent la couleur du thème, le mode nuit et la langue d'AutoJs6
* `Amélioration` Le centre de plugins épingle temporairement en haut les plugins nouvellement installés et les marque "Nouvellement installé" ou "Échec de l'activation"
* `Amélioration` Le menu du bouton flottant de l'onglet Plugins de l'accueil affiche d'abord l'assistant d'installation et les bibliothèques de plugins, et un appui long sur le bouton flottant ouvre le centre de plugins
* `Amélioration` Synchronisation du moteur Rhino avec l'amont, avec de nouvelles fonctionnalités comme new.target et la conversion Base64/Hex de Uint8Array, un démarrage des scripts plus rapide et une meilleure compatibilité
* `Amélioration` La déstructuration, la décomposition, le chaînage optionnel, Proxy/Reflect, BigInt et les déclarations de fonctions de bloc du moteur Rhino suivent désormais la spécification ECMAScript
* `Amélioration` La Promise globale est désormais l'implémentation intégrée de Rhino, avec les nouvelles méthodes Promise.any/withResolvers/try, et les rejets non gérés sont signalés comme erreurs dans la console
* `Amélioration` Suppression des copies intégrées des classes de compatibilité com.stardust; les noms de classes référencés avec le préfixe com.stardust correspondent désormais aux implémentations AutoJs6 de même nom (voir Documentation du projet > [Environnement AutoJs4](https://docs.autojs6.com/#/autojs4Runtime))
* `Amélioration` La journalisation dans des fichiers utilise désormais une implémentation intégrée, conservant les options documentées et les formats PatternLayout courants; les autres API Log4j ne sont plus disponibles
* `Amélioration` ocr choisit par défaut automatiquement les moteurs installés dans l'ordre ML Kit/Paddle/Rapid, et le moteur Paddle permet de choisir les plugins par langue (profile) ainsi que l'option useCls de classification de l'orientation du texte
* `Amélioration` Les modules pinyin/pinyin4j/opencc prennent en charge les dictionnaires de prononciation personnalisés, plusieurs systèmes de romanisation, la conversion par lots et la conversion en chaîne
* `Amélioration` images.matchTemplate/matchFeatures et méthodes associées prennent en charge davantage d'arguments, et la recherche de couleurs multipoints, la correspondance de modèles et la correspondance de caractéristiques sont plus rapides et plus précises
* `Amélioration` images.captureScreen actualise les images à temps lorsque l'écran est figé, et images.requestScreenCapture ne demande que le partage de l'écran entier, afin que les captures correspondent aux coordonnées de l'écran sous Android 14+
* `Amélioration` runtime.loadJar prend en charge le chargement de fichiers JAR via le plugin DEX Compiler (expérimental, désactivé par défaut)
* `Amélioration` La méthode KeyCode améliore la compatibilité des arguments et permet d'envoyer des codes de touche avec l'accès Shizuku _[`issue #492`](http://issues.autojs6.com/492)_
* `Amélioration` La fonction shell prend en charge l'option adb, pour exécuter des commandes via Shizuku (compatible avec Auto.js Pro)
* `Amélioration` Le service d'accessibilité peut être activé automatiquement via Shizuku
* `Amélioration` device.getAndroidId prend en charge l'argument useSystemId, pour obtenir la valeur système via Shizuku/Root sous Android 8.0+
* `Amélioration` Rétablissement des méthodes console.rawInput/input, pour lire du texte ou évaluer des expressions via la barre de saisie de la console
* `Amélioration` Menu d'actions unifié de la console flottante, pour redimensionner et configurer l'heure, la coloration, la taille de police et l'opacité
* `Amélioration` media.playMusic prend en charge la lecture au premier plan, le focus audio et les commandes dans la barre de notifications
* `Amélioration` Les attributs qui ne peuvent pas être appliqués dans les mises en page XML Android natives sont désormais ignorés avec un avertissement dans la console au lieu de faire échouer toute la mise en page
* `Amélioration` Unification des couleurs du thème et de la disposition des menus de l'éditeur de code, affichage plus rapide à l'ouverture des fichiers, et le bouton d'exécution devient un bouton d'arrêt après le lancement d'un script
* `Amélioration` Unification de l'aperçu des fichiers et des entrées d'actions des plugins dans le gestionnaire de fichiers, avec conservation de la position de défilement lors de l'actualisation de la liste
* `Amélioration` L'historique des versions évite les instantanés en double, et la restauration depuis la corbeille vers un nouveau chemin déplace aussi l'historique des versions
* `Amélioration` La vue hiérarchique de l'analyse de mise en page prend en charge les résumés d'attributs, la mise en évidence du parent et la sélection des widgets parents niveau par niveau
* `Amélioration` Le groupe "Se connecter à l'ordinateur" du tiroir d'accueil est renommé "Connexion à distance"
* `Amélioration` Le bouton flottant et les fenêtres associées suivent la langue et le mode nuit de l'application
* `Amélioration` Les arrière-plans de fenêtre clair et sombre de la bibliothèque de couleurs par défaut sont fusionnés en un seul élément "Couleur d'arrière-plan", avec suivi ou inversion automatiques au choix, en conservant le choix fait avant la mise à niveau
* `Amélioration` Détection de battements de coeur et vérification de la longueur des données pour les connexions du plugin VSCode _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `Amélioration` Les fichiers enregistrés depuis le plugin VSCode peuvent conserver leur arborescence relative dans le répertoire de travail _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `Amélioration` Ajout d'exemples de code pour 3-Block Compose, la voix, les flux d'automatisation, l'interaction avec la console et les opérations sur les livres électroniques
* `Amélioration` Les constructions Gradle déterminent dynamiquement les versions compatibles de Kotlin/KSP/AGP/R8, et les données de compatibilité des plateformes sont déplacées vers le projet AutoJs6-Gradle-Platform-Versions
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
* `Dépendance` Ajout de NDK version 28.2.13676358 et de CMake version 3.22.1
* `Dépendance` Remplacement de Desugar JDK Libs version 2.1.5 par Desugar JDK Libs NIO version 2.1.5
* `Dépendance` Mise à niveau de Android SDK version 36 -> 37
* `Dépendance` Mise à niveau de Gradle 9 version 9.4.0 -> 9.7.1
* `Dépendance` Mise à niveau de AndroidX Lifecycle version 2.6.2 -> 2.9.4
* `Dépendance` Mise à niveau de AndroidX SavedState version 1.2.1 -> 1.3.2
* `Dépendance` Mise à niveau de AndroidX Emoji2 version 1.3.0 -> 1.4.0
* `Dépendance` Mise à niveau de AndroidX Window version 1.0.0 -> 1.5.0
* `Dépendance` Mise à niveau de Rhino 2.0.0-SNAPSHOT (instantané amont 2025/12/22 -> 2026/10/31)
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