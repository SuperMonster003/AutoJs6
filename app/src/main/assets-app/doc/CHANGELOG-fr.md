******

### Histoires des versions

******

# v6.6.3

###### 2025/05/27

* `Fonctionnalité` Fonction historique des versions : consulter les journaux de mise à jour multilingues et les statistiques
* `Fonctionnalité` Méthode timers.keepAlive (désormais globale) pour maintenir le script actif
* `Fonctionnalité` Méthodes d'écoute d'événements telles que engines.on('start/stop/error', callback) pour les événements globaux du moteur
* `Fonctionnalité` Méthode images.detectMultiColors pour la vérification de couleurs multipoints _[`issue #374`](http://issues.autojs6.com/374)_
* `Fonctionnalité` Méthodes images.matchFeatures/detectAndComputeFeatures : recherche d'image en pleine résolution (Réf. [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `Fonctionnalité` Méthode images.compressToBytes pour compresser une image et générer un tableau d'octets
* `Fonctionnalité` Méthode images.downsample pour l'échantillonnage de pixels et la création d'un nouveau ImageWrapper
* `Fonctionnalité` Méthode ui.keepScreenOn pour garder l'écran allumé lorsque la page UI a le focus
* `Fonctionnalité` Propriété ui.root (getter) pour obtenir le nœud « conteneur racine du contenu de fenêtre » du layout UI
* `Fonctionnalité` L'élément webview prend en charge les layouts de pages Web basés sur JsBridge (Réf. [Auto.js Pro](https://g.pro.autojs.org/)) [voir Code d'exemple > Layout > HTML interactif / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `Correction` Le contenu de la documentation en ligne dans l'onglet Docs et l'activité Docs pouvait être recouvert par la barre de navigation système
* `Correction` Sur certaines pages, cliquer sur les boutons de la Toolbar pouvait déclencher par erreur l'événement de clic sur le titre
* `Correction` Les lignes vides dans l'éditeur de code affichaient des carrés sur certains appareils
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
* `Amélioration` L'inspecteur de layout permet de masquer des contrôles (par [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `Amélioration` Ajout de séparateurs dégradés dans le menu de l'inspecteur de layout pour regrouper les fonctions
* `Amélioration` project.json prend désormais en charge l'option permissions pour les projets de script (par [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `Amélioration` Lors de l'empaquetage d'un fichier unique, les permissions déclarées par l'application installée sont lues et cochées automatiquement _[`issue #362`](http://issues.autojs6.com/362)_
* `Amélioration` Portée d'adaptation de la couleur de thème élargie et prise en charge de plus de types de widgets
* `Amélioration` Largeur du tiroir d'accueil adaptée aux écrans paysage ou ultra-larges
* `Amélioration` Ajout de layouts horizontaux et petit écran pour les pages À propos de l'application et Développeur
* `Amélioration` Les boîtes de dialogue de la page Paramètres proposent l'option « Utiliser la valeur par défaut »
* `Amélioration` Le bouton flottant du gestionnaire de fichiers se masque automatiquement lorsqu'on touche en dehors
* `Amélioration` Le formateur de code prend désormais en charge les opérateurs `??`, `?.`, `??=`
* `Amélioration` L'éditeur de code prend en charge la lecture et l'écriture de fichiers aux encodages GB18030 / UTF-16 (LE/BE) / Shift_JIS, etc.
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
* `Dépendance` Inclus Material Dialogs version 0.9.6.0 (localisé)
* `Dépendance` Inclus Material Date Time Picker version 4.2.3 (localisé)
* `Dépendance` Inclus libimagequant version 2.17.0 (localisé)
* `Dépendance` Inclus libpng version 1.6.49 (localisé)
* `Dépendance` Ajouté ICU4J version 77.1
* `Dépendance` Ajouté Jsoup version 1.19.1
* `Dépendance` Ajouté Material Progressbar version 1.4.2
* `Dépendance` Ajouté Flexmark Java HTML to Markdown version 0.64.8
* `Dépendance` Mise à jour Gradle 8.14-rc-1 -> 8.14
* `Dépendance` Mise à jour Androidx Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `Fonctionnalité` Méthodes telles que ui.statusBarAppearanceLight, statusBarAppearanceLightBy et navigationBarColor, etc.
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
* `Amélioration` Ajout de l'option pour la bibliothèque Pinyin dans la page de packaging
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
* `Dépendance` Ajout d'Eclipse Paho Client Mqttv3 version 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `Dépendance` Mise à jour de la version de Gradle Compile de 34 à 35
* `Dépendance` Mise à jour de Gradle de 8.12 à 8.14-rc-1
* `Dépendance` Mise à jour de Rhino de 1.8.0-SNAPSHOT à 1.8.1-SNAPSHOT
* `Dépendance` Mise à jour d'Androidx Recyclerview de 1.3.2 à 1.4.0
* `Dépendance` Mise à jour d'Androidx Room de 2.6.1 à 2.7.0
* `Dépendance` Mise à jour d'Androidx WebKit de 1.12.1 à 1.13.0
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
* `Amélioration` La page de packaging prend en charge la configuration des signatures, la gestion des clés et la configuration des autorisations (par [luckyloogn]()) _[`pr #286`]()_
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

* `Astuce` Les modules intégrés sont réécrits en Kotlin pour améliorer l'efficacité d'exécution des scripts, mais des améliorations itératives sont nécessaires.
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
* `Fonctionnalité` La méthode images.matchTemplate ajoute le paramètre d'option useTransparentMask pour prendre en charge la recherche d'image transparente (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.requestScreenCaptureAsync permet de demander des permissions de capture d'écran de manière asynchrone en mode UI (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
* `Fonctionnalité` La méthode images.requestScreenCapture ajoute le paramètre d'option isAsync pour prendre en charge la capture d'écran asynchrone (Réf. à [Auto.js Pro](https://g.pro.autojs.org/))
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
* `Amélioration` Expérience d'indication de clic pour le contrôle 'ABI non disponible' sur la page de paquet
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
* `Amélioration` L'adresse IP du mode serveur prend en charge l'importation du presse-papiers avec reconnaissance intelligente et conversion intelligente avec la touche espace
* `Amélioration` Le gestionnaire de fichiers prend en charge la sélection de préfixes par défaut lors de la création de nouveaux fichiers et génère automatiquement un suffixe numérique approprié
* `Amélioration` Le gestionnaire de fichiers informe spécifiquement sur le message d'exception lors de l'exécution du projet _[`issue #268`](http://issues.autojs6.com/268)_
* `Amélioration` Le gestionnaire de fichiers prend en charge plus de types et affiche les symboles d'icône correspondants (prend en charge plus de 800 types de fichiers)
* `Amélioration` Les types de fichiers éditables (jpg/doc/pdf, etc.) dans le gestionnaire de fichiers ont ajouté des boutons d'édition
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
* `Dépendance` Mettre à niveau la version 1.10.0 d'Android Material Lang3 -> 1.12.0
* `Dépendance` Mettre à niveau la version 1.7.0 d'Androidx Annotation -> 1.9.1
* `Dépendance` Mettre à niveau la version 1.6.1 d'Androidx AppCompat -> 1.7.0
* `Dépendance` Mettre à niveau la version 1.8.0 d'Androidx WebKit -> 1.12.1
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
* `Fonctionnalité` Support amélioré pour le filtrage des ABI et des bibliothèques sur la page de l'application de packaging (Réf à [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Correction` Problème de taille de fichier anormalement volumineux lors du packaging de l'application (Réf à [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Correction` Problème d'affichage de certains messages d'exception lors du packaging de l'application
* `Correction` Problème potentiel d'icône blanche après la sélection d'une icône d'application sur la page de packaging de l'application
* `Correction` Problème d'initialisation contextuelle lors de l'intégration de la bibliothèque OCR MLKit Google
* `Correction` Problème d'inefficacité des méthodes ocr.<u>mlkit/ocr</u>.<u>recognizeText/detect</u>
* `Correction` Problème de langage d'affichage incohérent avec le paramètre de l'application dans certains textes (comme la page des logs)
* `Correction` Problème de débordement de texte sur certains appareils avec le service de réutilisation activé
* `Correction` Problème de fermeture automatique du service d'accessibilité sans notification sur certains appareils _[`issue #181`](http://issues.autojs6.com/181)_
* `Correction` Problème de crash de l'application causé par les boutons physiques du matériel avec le service d'accessibilité activé sur certains appareils (correctif partiel) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Correction` Problème de dysfonctionnement de la fonctionnalité pickup après avoir redémarré le service d'accessibilité avec auto(true) (correctif partiel) _[`issue #184`](http://issues.autojs6.com/184)_
* `Correction` Problème de crash potentiel de l'application lors de la création de fenêtres flottantes avec le module floaty (correctif partiel)
* `Correction` Problème d'utilisation des paramètres abrégés dans app.startActivity _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Correction` Problème de conflit de nommage entre les classes importées avec importClass et les variables globales _[`issue #185`](http://issues.autojs6.com/185)_
* `Correction` Problème d'inutilisation du service d'accessibilité sur Android 7.x
* `Correction` Problème potentiel d'utilisation des méthodes runtime.<u>loadJar/loadDex</u> sur Android 14 (correctif partiel)
* `Correction` Problème d'inutilisation des "layout bounds" et "layout hierarchy" sur le panneau de configuration rapide dans le système Android _[`issue #193`](http://issues.autojs6.com/193)_
* `Correction` Problème potentiel d'ANR ([Application Not Responding](https://developer.android.com/topic/performance/vitals/anr)) causé par la fonctionnalité de vérification automatique des mises à jour (correctif partiel) _[`issue #186`](http://issues.autojs6.com/186)_
* `Correction` Problème de retour à la page de chemin de travail après un clic sur le bouton "retour" dans le gestionnaire de fichiers _[`issue #129`](http://issues.autojs6.com/129)_
* `Correction` Problème de non-affichage du bouton de remplacement lors de l'utilisation de la fonctionnalité de remplacement dans l'éditeur de code
* `Correction` Problème potentiel de crash de l'application lors de la suppression longue dans l'éditeur de code (correctif partiel)
* `Correction` Problème de non-affichage du panneau de fonctions de module sur un clic sur le bouton fx dans l'éditeur de code
* `Correction` Problème de débordement de noms de fonctions sur le panneau de fonctions de module dans l'éditeur de code
* `Amélioration` Le panneau de fonctions rapides du module d'édition de code s'adapte au mode nuit.
* `Amélioration` La page de démarrage de l'application emballée s'adapte au mode nuit et la disposition des icônes d'application est ajustée.
* `Amélioration` La page de l'application emballée prend en charge la navigation du curseur à l'aide de la touche ENTER sur le clavier logiciel.
* `Amélioration` La page de l'application emballée prend en charge le basculement de l'état de sélection totale en cliquant sur les titres ABI et de bibliothèque.
* `Amélioration` La sélection par défaut d'ABI est rendue intelligente sur la page de l'application emballée avec des invites guide pour les éléments non sélectionnables.
* `Amélioration` Le gestionnaire de fichiers ajuste l'affichage des éléments du menu en fonction du type et des caractéristiques des fichiers et dossiers.
* `Amélioration` Le menu contextuel du gestionnaire de fichiers pour les dossiers ajoute une option d'emballage d'application.
* `Amélioration` Lorsque les services d'accessibilité sont activés mais dysfonctionnent, un état anormal se reflète dans le commutateur du tiroir de la page d'accueil d'AutoJs6.
* `Amélioration` La console inclut des informations détaillées sur la pile lors de l'impression des messages d'erreur.
* `Dépendance` Ajout de ARSCLib version 1.2.4
* `Dépendance` Ajout de Flexbox version 3.0.0
* `Dépendance` Ajout de Android OpenCC version 1.2.0
* `Dépendance` Mise à jour de Gradle version 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `Fonctionnalité` Propriété de paramètre d'option inputSingleLine pour dialogs.build()
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
* `Dépendance` Mise à jour de OpenCV version 4.5.5 à 4.8.0 (Réf à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dépendance` Mise à jour de Gradle Compile de la version 33 à la version 34
* `Dépendance` Mise à jour de Gradle de la version 8.3-rc-1 à 8.4-rc-3
* `Dépendance` Mise à jour de Apache Commons Lang3 de la version 3.12.0 à la version 3.13.0
* `Dépendance` Mise à jour de Glide de la version 4.15.1 à 4.16.0
* `Dépendance` Mise à jour de Android Analytics de la version 14.3.0 à 14.4.0
* `Dépendance` Mise à jour de Androidx WebKit de la version 1.7.0 à 1.8.0
* `Dépendance` Mise à jour de Androidx Preference de la version 1.2.0 à 1.2.1
* `Dépendance` Mise à jour de Androidx Annotation de la version 1.6.0 à 1.7.0
* `Dépendance` Mise à jour de Androidx Recyclerview de la version 1.3.0 à 1.3.2
* `Dépendance` Mise à jour de Android Material de la version 1.9.0 à 1.10.0
* `Dépendance` Mise à jour de Androidx AppCompat de la version 1.4.2 à 1.6.1
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

* `Fonctionnalité` module crypto (voir Documentation du Projet > [Cryptage](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
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
* `Fonctionnalité` La méthode web.newWebSocket crée une instance de WebSocket (voir Documentation du Projet > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
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
* `Dépendance` Mise à jour de Androidx WebKit version 1.6.1 -> 1.7.0
* `Dépendance` Mise à jour de OkHttp3 version 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `Dépendance` Mise à jour de MLKit Text Recognition Chinese version 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `Fonctionnalité` module ocr (voir Documentation du Projet > [Reconnaissance Optique de Caractères](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Fonctionnalité` module notice (voir Documentation du Projet > [Notifications](https://docs.autojs6.com/#/notice))
* `Fonctionnalité` module s13n (voir Documentation du Projet > [Standardisation](https://docs.autojs6.com/#/s13n))
* `Fonctionnalité` module Color (voir Documentation du Projet > [Type de Couleur](https://docs.autojs6.com/#/colorType))
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
* `Amélioration` Support d'autres formats pour le paramètre d'option de région dans les méthodes du module images (voir Documentation du Projet > [Types Omnipotents](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Amélioration` Support de pages abrégées additionnelles (comme pref/homepage/docs/about) pour app.startActivity
* `Amélioration` Ancrage des méthodes globales du module web au module lui-même pour améliorer l'utilisabilité (voir Documentation du Projet > [Web](https://docs.autojs6.com/#/web))
* `Amélioration` Support par la méthode web.newInjectableWebView des options de configuration WebView par défaut pour une meilleure fonctionnalité
* `Amélioration` Ajout de plusieurs méthodes de conversion et utilitaires dans le module colors, ajout de constantes statiques supplémentaires et de noms de couleurs pouvant être directement utilisés comme paramètres
* `Amélioration` Ajout de plusieurs méthodes de configuration de la fenêtre flottante du module console et support de l'unification des styles de fenêtre via un constructeur build
* `Amélioration` Support du déplacement de la fenêtre flottante du module console via le glissement de la zone de titre
* `Amélioration` Support de la fermeture automatique retardée de la fenêtre flottante du module console après la fin du script
* `Amélioration` Support du zoom à deux doigts pour ajuster la taille de la police dans la fenêtre flottante et dans l'Activité du module console
* `Amélioration` Support du paramètre de délai d'expiration (timeout) dans les méthodes associées au module http
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
* `Dépendance` Mise à jour de Androidx WebKit version 1.5.0 -> 1.6.1
* `Dépendance` Mise à jour de Androidx Recyclerview version 1.2.1 -> 1.3.0
* `Dépendance` Mise à jour de Zip4j version 2.11.2 -> 2.11.5
* `Dépendance` Mise à jour de Junit Jupiter version 5.9.2 -> 5.9.3
* `Dépendance` Mise à jour de Androidx Annotation version 1.5.0 -> 1.6.0
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
* `Amélioration` Redirection des classes publiques de la version Auto.js 4.x pour assurer une compatibilité descendante maximale possible (dans une certaine mesure).
* `Amélioration` Fusionner tous les modules du projet pour éviter d'éventuels problèmes de référence circulaire (supprimer temporairement le module inrt).
* `Amélioration` Migrer la configuration de construction Gradle de Groovy à KTS.
* `Amélioration` Ajouter un support multilingue pour les messages d'exception de Rhino.
* `Amélioration` L'interrupteur de permissions du tiroir de la page d'accueil ne montre des messages que lorsqu'il est activé.
* `Amélioration` La disposition du tiroir de la page d'accueil s'attache directement sous la barre d'état pour éviter les incompatibilités de barre de couleur supérieure.
* `Amélioration` Compatibilité des fonctions de vérification de mise à jour, téléchargement et avis de mise à jour avec le système Android 7.x.
* `Amélioration` Reconcevoir la page des paramètres (migration vers AndroidX).
* `Amélioration` La page des paramètres prend en charge la pression longue sur les options de configuration pour afficher des informations détaillées.
* `Amélioration` Ajout de l'option "Suivre le système" pour le mode nocturne (Android 9+).
* `Amélioration` Compatibilité de l'écran de démarrage de l'application avec le mode nocturne.
* `Amélioration` Ajouter des identifiants numériques aux icônes d'application pour améliorer l'expérience utilisateur avec plusieurs versions open source coexistantes.
* `Amélioration` Ajouter plus d'options de couleurs Material Design au thème de l'application.
* `Amélioration` Alléger et adapter les icônes des éléments de liste dans le gestionnaire de fichiers/panneau des tâches à la couleur du thème.
* `Amélioration` Compatibilité de la couleur du texte indicatif dans la boîte de recherche de la page d'accueil avec le mode nocturne.
* `Amélioration` Compatibilité des composants tels que les dialogues/textes/Fab/AppBar/éléments de liste avec le mode nocturne.
* `Amélioration` Compatibilité des pages telles que les documents/paramètres/à propos/couleurs de thème/analyse de mise en page et des menus des boutons flottants avec le mode nocturne.
* `Amélioration` Compatibilité de la disposition des pages avec la disposition RTL (Right-To-Left) dans la mesure du possible.
* `Amélioration` Ajouter des effets d'animation des icônes à la page à propos.
* `Amélioration` Mise à jour automatique de l'année dans le texte de la déclaration de droits d'auteur sur la page à propos.
* `Amélioration` Après l'installation initiale de l'application, déterminer et définir automatiquement un répertoire de travail approprié.
* `Amélioration` Désactiver la fonction de zoom à deux doigts sur la page des documents pour éviter une anomalie de l'affichage du contenu.
* `Amélioration` Simplifier le nom et le chemin des tâches affichés dans les éléments de liste du panneau des tâches par un chemin relatif.
* `Amélioration` Abréger le texte des boutons de l'éditeur de texte pour éviter le débordement du contenu.
* `Amélioration` Prise en charge de la restauration de la taille de police par défaut dans la configuration de l'éditeur de texte.
* `Amélioration` Améliorer la vitesse de réponse au clic sur le bouton flottant.
* `Amélioration` Cliquer sur le bouton d'analyse de mise en page du bouton flottant effectue directement l'analyse de la plage de mise en page.
* `Amélioration` Le thème de l'analyse de mise en page est adaptable (la fenêtre flottante suit le thème de l'application, le panneau de configuration rapide suit le thème du système).
* `Amélioration` Réorganiser la liste des informations de contrôle de mise en page selon la fréquence d'utilisation potentielle.
* `Amélioration` Optimiser le format de sortie automatiquement lors du clic et copie sur les informations de contrôle de mise en page selon le type de sélecteur.
* `Amélioration` Lors de la sélection de fichiers à l'aide de la fenêtre flottante, appuyer sur la touche de retour ramène au répertoire supérieur au lieu de fermer directement la fenêtre.
* `Amélioration` Prise en charge de la détection de validité numérique et de la conversion automatique des symboles pointillés lors de la saisie d'une adresse en mode client se connectant à l'ordinateur.
* `Amélioration` Afficher l'adresse IP du périphérique correspondant dans le tiroir de la page d'accueil après que le client et le serveur se sont connectés.
* `Amélioration` Ajouter une protection contre la réécriture à certains objets globaux et modules intégrés (consultez la documentation du projet > Objets globaux > [Protection contre la réécriture](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4)).
* `Amélioration` importClass et importPackage prennent en charge des paramètres de chaîne et des paramètres de longueur variable.
* `Amélioration` ui.run prend en charge l'impression des informations de trace de pile en cas d'exception.
* `Amélioration` ui.R et auto.R peuvent obtenir commodément l'ID de ressources d'AutoJs6.
* `Amélioration` Les méthodes associées à l'utilisation d'applications dans le module app prennent en charge des paramètres de type App et des paramètres d'alias d'application.
* `Amélioration` Les méthodes associées au rappel asynchrone dans le module dialogs prennent en charge l'omission des paramètres pré-remplis.
* `Amélioration` app.startActivity et autres prennent en charge des paramètres d'option d'URL (voir code d'exemple > Applications > Intention).
* `Amélioration` Le module device renvoie null au lieu de lancer une exception lorsque l'obtention de l'IMEI ou du numéro de série matériel échoue.
* `Amélioration` Augmenter la luminosité du texte dans le journal de la fenêtre flottante affichée par console.show pour améliorer la lisibilité du contenu.
* `Amélioration` ImageWrapper#saveTo prend en charge la sauvegarde de fichiers image dans des chemins relatifs.
* `Amélioration` Reconcevoir l'objet global colors et inclure la prise en charge des modes de couleur HSV / HSL (consultez la documentation du projet > [Couleurs](https://docs.autojs6.com/#/color)).
* `Dépendance` Mise à jour de la version de compilation Gradle 32 -> 33
* `Dépendance` Localisation de la version 1.4.3 de Android Job
* `Dépendance` Localisation de la version 9.0.0 du SDK Client de Plugin Android pour Locale
* `Dépendance` Localisation de la version 1.306 de l'API GitHub
* `Dépendance` Ajout de la version 1.0 de JCIP Annotations
* `Dépendance` Ajout de la version 1.5.0 de Androidx WebKit
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
* `Dépendance` Mise à jour de la version de Androidx Annotation 1.3.0 -> 1.5.0
* `Dépendance` Mise à jour de la version de Androidx AppCompat 1.4.1 -> 1.4.2
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
* `Fonctionnalité` Options des paramètres d'animation, de mise en lien etc. dans dialogs.build() (voir des exemples de code > Dialogues > Dialogues personnalisés)
* `Correction` Anomalies des options des paramètres d'inputHint, des itemsSelectedIndex etc. dans dialogs.build()
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
* `Dépendance` Mise à jour d‘Android Analytics de la version 13.1.0 à 13.3.0
* `Dépendance` Mise à jour des outils de construction Gradle de la version 7.3.0-alpha06 à 7.4.0-alpha02
* `Dépendance` Mise à jour d‘Android Job de la version 1.4.2 à 1.4.3
* `Dépendance` Mise à jour d‘Android Material de la version 1.5.0 à 1.6.0
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
* `Dépendance` Mise à jour de Kotlinx Coroutines de la version 1.6.0-native-mt à 1.6.0
* `Dépendance` Mise à jour de OpenCV de la version 3.4.3 à 4.5.4 puis 4.5.5 (référence à [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dépendance` Mise à jour d'OkHttp3 de la version 3.10.0 à 5.0.0-alpha.4 puis 5.0.0-alpha.6
* `Dépendance` Mise à jour des outils de construction Gradle de la version 7.2.0-beta01 à 7.3.0-alpha06
* `Dépendance` Mise à jour d'Auto.js-ApkBuilder de la version 1.0.1 à 1.0.3
* `Dépendance` Mise à jour de Glide Compiler de la version 4.12.0 à 4.13.1
* `Dépendance` Mise à jour de la distribution Gradle de la version 7.4-rc-2 à 7.4.1
* `Dépendance` Mise à jour de Gradle Compile de la version 31 à 32
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
* `Dépendance` Ajout de la version 1.0.2 de Androidx AppCompat (Legacy)
* `Dépendance` Mise à niveau de la version de Androidx AppCompat de 1.4.0 à 1.4.1
* `Dépendance` Mise à niveau de la version de Androidx Preference de 1.1.1 à 1.2.0
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
* `Dépendance` Ajout de la version 1.1.1 de Androidx Preference
* `Dépendance` Ajout de la version 1.1.0 de SwipeRefreshLayout
* `Dépendance` Mise à niveau d'Android Analytics de 7.0.0 à 13.1.0
* `Dépendance` Mise à niveau d'Android Annotations de 4.5.2 à 4.8.0
* `Dépendance` Mise à jour des outils de construction Gradle de 3.2.1 à 4.1.0 puis à 7.0.3 puis à 7.2.0-alpha04
* `Dépendance` Mise à jour de Android Job de 1.2.6 à 1.4.2
* `Dépendance` Mise à niveau de Android Material de 1.1.0-alpha01 à 1.5.0-beta01
* `Dépendance` Mise à jour d'Androidx MultiDex de 2.0.0 à 2.0.1
* `Dépendance` Mise à jour d'Apache Commons Lang3 de 3.6 à 3.12.0
* `Dépendance` Mise à jour d'Appcompat de 1.0.2 à 1.4.0
* `Dépendance` Mise à niveau du plugin Gradle ButterKnife de 9.0.0-rc2 à 10.2.1 puis 10.2.3
* `Dépendance` Mise à niveau de ColorPicker de 2.1.5 à 2.1.7
* `Dépendance` Mise à niveau d'Espresso Core de 3.1.1-alpha01 à 3.5.0-alpha03
* `Dépendance` Mise à jour d'Eventbus de 3.0.0 à 3.2.0
* `Dépendance` Mise à jour de Glide Compiler de 4.8.0 à 4.12.0
* `Dépendance` Mise à jour de l'outil de construction Gradle de 29.0.2 à 30.0.2
* `Dépendance` Mise à jour de Gradle Compile de 28 à 30 puis à 31
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