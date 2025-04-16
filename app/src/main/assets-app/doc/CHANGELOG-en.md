******

### Version Histories

******

# v6.6.2

###### 2025/04/16

* `Feature` Methods such as ui.statusBarAppearanceLight, statusBarAppearanceLightBy, navigationBarColor, etc.
* `Feature` ui.statusBarHeight attribute (getter) for obtaining the status bar height _[`issue #357`](http://issues.autojs6.com/357)_
* `Feature` images.flip method for image flipping _[`issue #349`](http://issues.autojs6.com/349)_
* `Feature` Added "file extension" option in the settings page
* `Feature` New layout support in the theme color settings page (grouping, positioning, search, history, enhanced color palette, etc.)
* `Fix` Issue where Android 15's status bar background color did not match the theme color
* `Fix` Issue where the plugins.load method failed to properly load plugins _[`issue #290`](http://issues.autojs6.com/290)_
* `Fix` Issue where the dx library could not be used properly on Android 7.x _[`issue #293`](http://issues.autojs6.com/293)_
* `Fix` Potential synchronization issues in ScriptRuntime when using require to import built-in modules (tentative fix) _[`issue #298`](http://issues.autojs6.com/298)_
* `Fix` Issue where the notice module was missing extension methods such as getBuilder _[`issue #301`](http://issues.autojs6.com/301)_
* `Fix` Issue where methods like shizuku/shell could not accept string parameters _[`issue #310`](http://issues.autojs6.com/310)_
* `Fix` Issue where the colors.pixel method could not accept single-channel image parameters _[`issue #350`](http://issues.autojs6.com/350)_
* `Fix` Issue where methods such as engines.execScript/execScriptFile had abnormal default working directories when executing scripts _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `Fix` Issue where floaty.window/floaty.rawWindow could not be executed on background threads
* `Fix` Issue where floaty.getClip might not correctly retrieve clipboard content _[`issue #341`](http://issues.autojs6.com/341)_
* `Fix` Issue where ui.inflate returned values were missing prototype methods such as attr/on/click
* `Fix` Issue where exceptions in some method calls could not be caught by try..catch blocks _[`issue #345`](http://issues.autojs6.com/345)_
* `Fix` Issue where generating code on the layout analysis page could cause the app to crash _[`issue #288`](http://issues.autojs6.com/288)_
* `Fix` Issue where packaged applications could not properly use the shizuku module _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `Fix` Issue in the code editor where jumping to the end of a line might actually move the cursor to the beginning of the next line
* `Fix` Issue where rapid consecutive clicks on dialog-type items in the settings page could cause the app to crash
* `Improvement` Streamlined the APK file size for the packaged application template
* `Improvement` Enhanced app (and packaged apps) support for more permissions _[`issue #338`](http://issues.autojs6.com/338)_
* `Improvement` Added support for the Pinyin library option on the packaging page
* `Improvement` Optimized the status bar background and text color on the main activity page of packaged apps
* `Improvement` Added special permission toggles (such as access all files and send notifications) to the packaged app settings page _[`issue #354`](http://issues.autojs6.com/354)_
* `Improvement` Automatically switched control text and icon colors based on the brightness of the theme color
* `Improvement` Improved visual experience when contrast between control theme color and background is too low
* `Improvement` Enhanced HEX input control in the color palette for better compatibility when pasting color values from the clipboard
* `Improvement` Set in-app navigation bar to be transparent or semi-transparent to enhance visual experience
* `Improvement` Default UI mode for status bar and navigation bar set to `md_grey_50` color in light mode
* `Improvement` Homepage drawer's accessibility service toggle now syncs with script code
* `Improvement` Homepage document page search now supports bidirectional find buttons
* `Improvement` Homepage "Files" tab now supports toggling floating button visibility via long press
* `Improvement` Code editor title text now supports auto-adjusting font size
* `Improvement` Log page floating button visibility now linked with list scrolling actions
* `Improvement` Script project configuration file project.json now supports more packaging options _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `Improvement` Improved project.json to support lenient matching of option names and alias compatibility
* `Improvement` APK file type info dialog now includes file size and signature scheme information
* `Improvement` APK file type info dialog now supports click listeners for copying text and navigating to app details
* `Improvement` Attempted to restore com.stardust prefixed packages to improve code compatibility _[`issue #290`](http://issues.autojs6.com/290)_
* `Improvement` Enhanced floaty.window/floaty.rawWindow to support execution on both main and background threads
* `Improvement` Global getClip method now leverages floaty.getClip as needed to improve compatibility
* `Improvement` Improved compatibility when passing null paths to files.path and related methods
* `Improvement` Synchronized with the latest official upstream code of the Rhino engine and performed necessary code adaptations
* `Improvement` Enhanced README.md to better document project build and run instructions _[`issue #344`](http://issues.autojs6.com/344)_
* `Dependency` Added Eclipse Paho Client Mqttv3 version 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `Dependency` Upgraded Gradle Compile version from 34 to 35
* `Dependency` Upgraded Gradle from 8.12 to 8.14-rc-1
* `Dependency` Upgraded Rhino from 1.8.0-SNAPSHOT to 1.8.1-SNAPSHOT
* `Dependency` Upgraded Androidx Recyclerview from 1.3.2 to 1.4.0
* `Dependency` Upgraded Androidx Room from 2.6.1 to 2.7.0
* `Dependency` Upgraded Androidx WebKit from 1.12.1 to 1.13.0
* `Dependency` Upgraded Pinyin4j from 2.5.0 to 2.5.1

# v6.6.1

###### 2025/01/01

* `Feature` Pinyin module for Chinese Pinyin conversion (Refer to Project Documentation > [Chinese Pinyin](https://docs.autojs6.com/#/pinyin))
* `Feature` Pinyin4j module for Chinese Pinyin conversion (Refer to Project Documentation > [Chinese Pinyin](https://docs.autojs6.com/#/pinyin4j))
* `Feature` Methods UiObject#isSimilar and UiObjectCollection#isSimilar for determining whether a control or a collection of controls is similar
* `Feature` Global method "currentComponent", used to obtain the name information of the currently active component
* `Fix` Issue where the project could not compile properly in certain environments due to a rollback to an earlier version
* `Fix` "Non-primitive value" exception that may occur when calling non-existent methods
* `Fix` Issue where script shortcuts could not be added properly on certain devices (tentative fix) _[`issue #221`](http://issues.autojs6.com/221)_
* `Fix` Incorrect parameter type restrictions for the automator.click/longClick methods _[`issue #275`](http://issues.autojs6.com/275)_
* `Fix` Issue where selectors did not support ConsString type parameters _[`issue #277`](http://issues.autojs6.com/277)_
* `Fix` Missing methods and properties on UiObjectCollection instances
* `Improvement` The packaging page supports signature configuration, keystore management, and permission settings (by [luckyloogn]()) _[`pr #286`]()_
* `Improvement` Improved accuracy in identifying the current package name and activity name of the floating window (Priority: Shizuku > Root > A11Y)
* `Improvement` Improved accuracy in recognizing `currentPackage` and `currentActivity` (Priority: Shizuku > Root > A11Y)
* `Improvement` Restore the ability to select text content of individual entries in the log activity window via double-click or long press _[`issue #280`](http://issues.autojs6.com/280)_
* `Improvement` Recover as much critical information as possible for script projects when project.json is corrupted
* `Improvement` Automatically convert Simplified Chinese to Pinyin (including multi-tone characters) for generated package name suffixes when packaging single files
* `Improvement` Support for negative arguments in the UiSelector#findOnce and UiSelector#find methods
* `Improvement` Enhanced adaptability of app.startActivity/startDualActivity methods
* `Improvement` UI elements and className-related selectors now support more package name prefix omission forms (e.g., RecyclerView, Snackbar, etc.)
* `Improvement` Synchronize with the latest upstream code of the Rhino engine and adapt it to the existing project
* `Dependency` Added Pinyin4j version 2.5.0
* `Dependency` Added Jieba Analysis version 1.0.3-SNAPSHOT (modified)
* `Dependency` Upgrade Gradle version from 8.11.1 to 8.12

# v6.6.0

###### 2024/12/02 - Built-in module rewrite, upgrade cautiously

* `Hint` The built-in modules are rewritten in Kotlin to enhance script execution efficiency, but iterative improvements are needed.
* `Hint` The built-in init.js file is empty by default, allowing developers to extend built-in modules or mount external modules.
* `Feature` Axios module / Cheerio module (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `Feature` SQLite module for simple operations on SQLite databases (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (See project documentation > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Feature` MIME module for processing and parsing MIME type strings (See project documentation > [MIME](https://docs.autojs6.com/#/mime))
* `Feature` Nanoid module for string ID generation (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `Feature` Sysprops module for obtaining runtime environment configuration data (See project documentation > [System Properties](https://docs.autojs6.com/#/sysprops))
* `Feature` OCR module supports [Rapid OCR](https://github.com/RapidAI/RapidOCR) engine
* `Feature` Layout analysis supports window switching (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` auto.clearCache method supports clearing control caches (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` threads.pool method supports simple application of thread pools (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.matchTemplate method adds useTransparentMask option parameter to support transparent image search (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.requestScreenCaptureAsync method for asynchronously requesting screenshot permissions in UI mode (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.requestScreenCapture method adds isAsync option parameter to support asynchronous screenshot capture (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.on('screen_capture', callback) and other event listener methods support listening for screen capture availability events (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.stopScreenCapture method supports actively releasing resources related to screenshot applications (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` Images.psnr/mse/ssim/mssim/hist/ncc and images.getSimilarity methods for obtaining image similarity metrics
* `Feature` images.isGrayscale method for determining whether an image is grayscale
* `Feature` images.invert method for negative image conversion
* `Feature` s13n.point/time methods for standardizing point objects and duration objects (See project documentation > [Normalization](https://docs.autojs6.com/#/s13n))
* `Feature` console module's gravity, touchThrough, backgroundTint methods (See project documentation > [Console](https://docs.autojs6.com/#/console))
* `Feature` Mathx.randomInt/Mathx.randomFloat methods for returning random integers or random floating-point numbers within a specified range
* `Feature` app.launchDual/startDualActivity methods for handling dual app launch (Requires Shizuku or Root permissions) (Experimental)
* `Feature` app.kill method for forcefully stopping an app (Requires Shizuku or Root permissions)
* `Feature` floaty.getClip method for indirectly obtaining clipboard content using a floating window
* `Fix` Memory leak in View Binding of Fragment subclasses (e.g., [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48))
* `Fix` Instance memory leak in classes such as [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21)
* `Fix` Issue causing app crash when requesting screenshot permissions on Android 14 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `Fix` Issue causing app crash when starting foreground service on Android 14
* `Fix` Issue with run button in code editor not lighting up properly on Android 14
* `Fix` App may not run properly after packaging due to missing necessary library files _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `Fix` App crash when editing project due to missing specified icon resources _[`issue #203`](http://issues.autojs6.com/203)_
* `Fix` Unable to use parameters properly to obtain screenshot resources of specified screen orientation when requesting screenshot permissions
* `Fix` Issue with some devices unable to add script shortcuts properly (Trial fix) _[`issue #221`](http://issues.autojs6.com/221)_
* `Fix` Cumulative request sending delay issue with methods related to sending requests in http module _[`issue #192`](http://issues.autojs6.com/192)_
* `Fix` Shizuku service may not work properly before AutoJs6 enters the main activity page (Trial fix) _[`issue #255`](http://issues.autojs6.com/255)_
* `Fix` random(min, max) method may have out-of-bounds results
* `Fix` Issue where result type parameter of pickup methods cannot be properly passed empty arrays
* `Fix` Issue where control rectangle obtained by UiObject#bounds() may be inadvertently modified, breaking its immutability
* `Fix` Issue with text/button/input elements where text containing half-width double quotes cannot be parsed properly
* `Fix` Issue with text/textswitcher elements where autoLink attribute functionality fails
* `Fix` Issue with different scripts erroneously sharing the same ScriptRuntime object
* `Fix` Issue with global variables HEIGHT and WIDTH losing dynamically-generated Getter properties
* `Fix` Issue with potential high-latency startup caused by RootShell loading on script startup
* `Fix` Issue with floating console window background color setting leading to loss of rectangular rounding style
* `Fix` Access service auto-start may encounter abnormal service issues (Trial fix)
* `Fix` Issue with triggering ViewPager switch when swiping left or right on WebView control on homepage document page
* `Fix` Issue with file manager unable to recognize file extensions containing uppercase letters
* `Fix` File manager may not automatically recognize project when first entering project directory
* `Fix` Issue with file manager page unable to refresh automatically after deleting folder
* `Fix` Issue with file manager sorting files and folders where ASCII initial letter names are put back
* `Fix` FAILED ASSERTION exception in code editor debug function
* `Fix` Issue with unable to debug again properly after closing editor during code editor debug process
* `Fix` Issue with potentially omitting end characters when jumping to line end in code editor
* `Fix` Issue with flickering screen when starting log activity page on main activity page
* `Fix` Issue with packaged app unable to use opencc module properly
* `Improvement` Click prompt experience for 'Unavailable ABI' control on package page
* `Improvement` Supports using Shizuku to control 'Pointer Location' display switch
* `Improvement` Supports using Shizuku to control 'Projection Media' and 'Modify Secure Settings' permission switches
* `Improvement` Automator.gestureAsync/gesturesAsync supports callback function parameters
* `Improvement` tasks module uses synchronous way for database operations to avoid potential data access inconsistencies
* `Improvement` Script execution mode supports pipeline symbol separation mode parameters (e.g., starting with `"ui|auto";`)
* `Improvement` Script execution mode supports single quotes and backticks and allows omitting semicolons (e.g., starting with `'ui';` or `'ui'`)
* `Improvement` Script execution mode supports quick import of built-in extension modules such as axios, cheerio, and dayjs (e.g., starting with `"axios";`)
* `Improvement` Script execution mode supports x or jsox mode parameters for quick enabling of JavaScript built-in object extension modules (e.g., starting with `"x";`)
* `Improvement` img element src and path attributes support local relative paths (e.g., `<img src="a.png"` />)
* `Improvement` Code editor supports intelligently determining insertion location when importing Java classes and package names
* `Improvement` images module supports using paths directly as image parameters
* `Improvement` importPackage supports string parameters
* `Improvement` Server mode IP address supports clipboard import with intelligent recognition and smart conversion with space key
* `Improvement` File manager supports default prefix selection when creating new files and automatically generates appropriate numeric suffix
* `Improvement` File manager specifically informs on exception message when running project _[`issue #268`](http://issues.autojs6.com/268)_
* `Improvement` File manager supports more types and displays corresponding icon symbols (supports over 800 file types)
* `Improvement` Editable file types (jpg/doc/pdf, etc.) in file manager have added edit buttons
* `Improvement` APK files in file manager support viewing basic information, Manifest information, and permissions list
* `Improvement` Audio/video media files in file manager support viewing basic information and MediaInfo information
* `Improvement` Package single file support auto-fill appropriate standardized package name and invalid character filter prompt
* `Improvement` Package single file support automatically sets icon and auto-increments version number and version name based on installed same package name application
* `Improvement` Package configuration file supports abis/libs option to specify default included ABI architecture and libraries
* `Improvement` Support relevant message prompts when abis/libs options of package configuration file are invalid or unavailable
* `Improvement` LeakCanary is excluded from official release version to avoid unnecessary growth
* `Improvement` All English comments in project source code are accompanied by Simplified Chinese translations to enhance readability
* `Improvement` README and CHANGELOG support multi-language (Automatically generated by script)
* `Improvement` Enhance Gradle build script's version adaptability
* `Dependency` Include MIME Util version 2.3.1
* `Dependency` Include Toaster version 12.6
* `Dependency` Include EasyWindow (for Toaster) version 10.3
* `Dependency` Upgrade Gradle version from 8.5 -> 8.11.1
* `Dependency` Upgrade Rhino version 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT
* `Dependency` Upgrade Android Material Lang3 version 1.10.0 -> 1.12.0
* `Dependency` Upgrade Androidx Annotation version 1.7.0 -> 1.9.1
* `Dependency` Upgrade Androidx AppCompat version 1.6.1 -> 1.7.0
* `Dependency` Upgrade Androidx WebKit version 1.8.0 -> 1.12.1
* `Dependency` Upgrade Apache Commons version 3.13.0 -> 3.16.0
* `Dependency` Upgrade ARSCLib version 1.2.4 -> 1.3.1
* `Dependency` Upgrade Gson version 2.10.1 -> 2.11.0
* `Dependency` Upgrade Jackson DataBind version 2.13.3 -> 2.13.4.2
* `Dependency` Upgrade Joda Time version 2.12.5 -> 2.12.7
* `Dependency` Upgrade LeakCanary version 2.12 -> 2.14
* `Dependency` Upgrade MLKit Barcode Scanning version 17.2.0 -> 17.3.0
* `Dependency` Upgrade MLKit Text Recognition Chinese version 16.0.0 -> 16.0.1
* `Dependency` Upgrade Retrofit2 Converter Gson version 2.9.0 -> 2.11.0
* `Dependency` Upgrade Retrofit2 Retrofit version 2.9.0 -> 2.11.0
* `Dependency` Upgrade Desugar JDK Libs version 2.0.3 -> 2.0.4
* `Dependency` Upgrade Test Runner version 1.5.2 -> 1.6.2
* `Dependency` Upgrade Junit Jupiter version 5.10.0 -> 5.10.3
* `Dependency` Downgrade OkHttp3 version 5.0.0-alpha.11 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `Feature` opencc module (Refer to Project Documentation > [Chinese Conversion](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `Feature` UiSelector adds [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) and [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) methods _[`issue #115`](http://issues.autojs6.com/115)_
* `Feature` Packaging application page adds support for filtering ABIs and libraries (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Fix` Abnormally large file size issue in packaged applications (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Fix` Issue where packaged applications cannot display and print some exception messages
* `Fix` Issue where selecting application icon on packaging application page might show empty icon
* `Fix` Context not initialized exception when packaging application includes MLKit Google OCR library
* `Fix` ocr.<u>mlkit/ocr</u>.<u>recognizeText/detect</u> methods not working
* `Fix` Mismatch between displayed language and application setting language for some texts (such as log page)
* `Fix` Text overflow issue in home page drawer switch items for some languages
* `Fix` Issue where accessibility service automatically closes immediately after being enabled on some devices without any prompt messages _[`issue #181`](http://issues.autojs6.com/181)_
* `Fix` Issue where enabling accessibility service on some devices might cause application crash due to physical keys (attempted fix) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Fix` Issue with pickup functionality after restarting accessibility service using auto(true) (attempted fix) _[`issue #184`](http://issues.autojs6.com/184)_
* `Fix` Issue where creating floating window in floaty module might cause application crash when dragging (attempted fix)
* `Fix` Issue where app.startActivity cannot use shorthand parameters _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Fix` Issue where code throws exception when imported class name conflicts with global variable using importClass _[`issue #185`](http://issues.autojs6.com/185)_
* `Fix` Issue where accessibility service cannot be used on Android 7.x
* `Fix` Issue where runtime.<u>loadJar/loadDex</u> methods might not work properly on Android 14 (attempted fix)
* `Fix` Issue where 'Layout Bounds Analysis' and 'Layout Hierarchy Analysis' are unavailable in Android system quick settings panel _[`issue #193`](http://issues.autojs6.com/193)_
* `Fix` Issue where auto-update check might cause application [ANR](https://developer.android.com/topic/performance/vitals/anr) (attempted fix) _[`issue #186`](http://issues.autojs6.com/186)_
* `Fix` Issue where file manager cannot return to workspace path page after clicking 'Up' button in example code folder _[`issue #129`](http://issues.autojs6.com/129)_
* `Fix` Issue where replace button is not shown when using replace function in code editor
* `Fix` Issue where long-press delete in code editor might cause application crash (attempted fix)
* `Fix` Issue where fx button in code editor cannot show module function quick panel
* `Fix` Issue where module function quick panel button function name might overflow in code editor
* `Improvement` The code editor module's quick function panel adapts to night mode.
* `Improvement` The startup page of the packaged application is adapted to night mode, and the layout of application icons is adjusted.
* `Improvement` The packaged application page supports cursor navigation using the ENTER key on the software keyboard.
* `Improvement` The packaged application page supports toggling select-all state by clicking on ABI and library titles.
* `Improvement` The default ABI selection is made intelligent on the packaged application page with added guide prompts for unselectable items.
* `Improvement` The file manager adjusts the display of menu items based on the type and characteristics of files and folders.
* `Improvement` The file manager's folder right-click menu adds a packaging application option.
* `Improvement` When accessibility services are enabled but malfunction, an abnormal state is reflected in the drawer switch of the AutoJs6 homepage.
* `Improvement` The console includes detailed stack information when printing error messages.
* `Dependency` Added ARSCLib version 1.2.4
* `Dependency` Added Flexbox version 3.0.0
* `Dependency` Added Android OpenCC version 1.2.0
* `Dependency` Upgrade Gradle version from 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `Feature` Added inputSingleLine option parameter property to dialogs.build()
* `Feature` Added console.setTouchable method _[`issue #122`](http://issues.autojs6.com/122)_
* `Fix` Issue where some methods in ocr module could not recognize region parameters _[`issue #162`](http://issues.autojs6.com/162)_  _[`issue #175`](http://issues.autojs6.com/175)_
* `Fix` Issue where version details could not be retrieved when a new version is found on Android 7.x
* `Fix` Issue where requesting screenshot permission on Android 14 caused application crash
* `Fix` Issue where quickly switching 'Floating Button' switch on the home page drawer might cause application crash
* `Fix` Issue where floating button might still be displayed after rebooting the application when it was closed using menu
* `Fix` Issue where changing AutoJs6 language in settings page could not take effect on systems Android 13 and above
* `Fix` Issue where initial build could not automatically complete OpenCV resource deployment using build tools
* `Improvement` Native implementation of bridges module to improve script execution efficiency (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `Improvement` Refactored accessibility service related code to enhance stability of accessibility service functionality (experimental) _[`issue #167`](http://issues.autojs6.com/167)_
* `Improvement` Enhanced print output format for UiObject and UiObjectCollection
* `Improvement` Added upgrade prompt when Gradle JDK version does not meet the requirements in build tools
* `Dependency` Upgraded Gradle version from 8.4 -> 8.5-rc-1
* `Dependency` Downgraded Commons IO version from 2.14.0 -> 2.8.0
* `Dependency` Downgraded Jackson DataBind version from 2.14.3 -> 2.13.3

# v6.4.1

###### 2023/11/02

* `Fix` Issue where build tools could not adapt to unknown platforms (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Fix` Issue where script exit might cause application crash _[`issue #159`](http://issues.autojs6.com/159)_
* `Fix` Incorrect return value type for body.contentType in http module's response object _[`issue #142`](http://issues.autojs6.com/142)_
* `Fix` Incorrect return data for device.width and device.height _[`issue #160`](http://issues.autojs6.com/160)_
* `Fix` Issue where long-press delete in code editor might cause application crash (attempted fix) _[`issue #156`](http://issues.autojs6.com/156)_
* `Fix` Issue where reverse selecting text in code editor followed by normal operations might cause application crash
* `Fix` Issue where long-pressing AutoJs6 application icon on some devices could not display shortcut menu
* `Fix` Issue where clicking confirm button was unresponsive when packaging project on some devices
* `Fix` Issue where app.sendBroadcast and app.startActivity could not use shorthand parameters
* `Fix` Functional anomaly when calling methods like JsWindow#setPosition in floaty module for the first time
* `Improvement` Added Termux related permissions to support Intent calls to Termux for executing ADB commands _[`issue #136`](http://issues.autojs6.com/136)_
* `Improvement` http module's response object can reuse body.string() and body.bytes() methods
* `Improvement` Added GitHub Actions support for automatic packaging (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Improvement` Build tools adapt to Temurin platform
* `Dependency` Upgraded Gradle version from 8.4-rc-3 -> 8.4
* `Dependency` Upgraded Android dx version from 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `Feature` ocr module supports Paddle Lite engine (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `Feature` Packaging function supports internal and external plugin packaging methods (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `Feature` WebSocket module (Refer to Project Documentation > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `Feature` barcode / qrcode modules (Refer to Project Documentation > [Barcode](https://docs.autojs6.com/#/barcode) / [QRCode](https://docs.autojs6.com/#/qrcode))
* `Feature` shizuku module (Refer to Project Documentation > [Shizuku](https://docs.autojs6.com/#/shizuku)) and permission switch in home page drawer
* `Feature` device.rotation / device.orientation methods
* `Feature` Support for static property access of internal Java classes
* `Feature` Support for selecting and switching application language in Android system settings page (Android 13 and above)
* `Feature` Added app shortcuts activation by adding or long-pressing application icon in settings page, allowing access to documents and settings etc. [App Shortcuts](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn)
* `Fix` Re-merged some PRs (by [aiselp](https://github.com/aiselp)) to solve the issue where some scripts could not end normally _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Fix` Issue where packaged applications could not use new APIs in AutoJs6 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `Fix` Styling anomaly in packaged applications under system night mode
* `Fix` Issue where file extension information was lost when saving files locally with VSCode plugin
* `Fix` Issue where running project with coroutine feature caused uncaught exceptions leading to application crash
* `Fix` Issue where floating button could not record its position state information when restarting or exiting application
* `Fix` Issue where updated device configuration information could not be retrieved when device screen orientation changed _[`issue #153`](http://issues.autojs6.com/153)_
* `Fix` Issue where Toolbar title font size was too small when screen rotated to landscape orientation
* `Fix` Issue where home page tabs layout was too crowded when screen rotated to landscape orientation
* `Fix` Issue where floating button might overflow the screen when screen rotated to landscape orientation _[`issue #90`](http://issues.autojs6.com/90)_
* `Fix` Issue where floating button's coordinates and screen edge directions could not be restored when screen rotated multiple times
* `Fix` Issue where message floating frame might miss or repeatedly display in some devices
* `Fix` Issue where message floating frame might be obstructed when multiple scripts are running simultaneously _[`issue #67`](http://issues.autojs6.com/67)_
* `Fix` Issue where clicking layout while analyzing layout with broadcast could not pop up menu and caused application crash
* `Fix` Issue where WebSocket instances created from the second time onwards could not normally trigger listeners
* `Fix` Reverted importPackage's global redirection method to avoid package import anomalies in some scopes _[`issue #88`](http://issues.autojs6.com/88)_
* `Fix` Issue where using copy or export functions in log activity page might cause application crash
* `Improvement` Renamed export function to send function and re-implemented meaningful export function in log activity page
* `Improvement` Added automatic interception and prompt when entry count is too large for send function in log activity page
* `Improvement` ocr module is compatible with both Google MLKit and Paddle Lite engines (Refer to Project Documentation > [Optical Character Recognition](https://docs.autojs6.com/#/ocr?id=p-mode))
* `Improvement` Increased the success rate of auto-starting accessibility service
* `Improvement` Migrated Kotlin annotation processor from kapt to KSP
* `Improvement` Build tools support IntelliJ Idea EAP versions
* `Improvement` Build tools adapt to Java release versions as much as possible to avoid 'invalid release version' issues
* `Improvement` Build tools optimize IDE and related plugin downgrade logic and add version prediction capability
* `Improvement` Adapted to VSCode plugin 1.0.7
* `Dependency` Added Rikka Shizuku version 13.1.5
* `Dependency` Added MLKit Barcode Scanning version 17.2.0
* `Dependency` Upgraded OpenCV version from 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependency` Upgraded Gradle Compile version from 33 -> 34
* `Dependency` Upgraded Gradle version from 8.3-rc-1 -> 8.4-rc-3
* `Dependency` Upgraded Apache Commons Lang3 version from 3.12.0 -> 3.13.0
* `Dependency` Upgraded Glide version from 4.15.1 -> 4.16.0
* `Dependency` Upgraded Android Analytics version from 14.3.0 -> 14.4.0
* `Dependency` Upgraded Androidx WebKit version from 1.7.0 -> 1.8.0
* `Dependency` Upgraded Androidx Preference version from 1.2.0 -> 1.2.1
* `Dependency` Upgraded Androidx Annotation version from 1.6.0 -> 1.7.0
* `Dependency` Upgraded Androidx Recyclerview version from 1.3.0 -> 1.3.2
* `Dependency` Upgraded Android Material version from 1.9.0 -> 1.10.0
* `Dependency` Upgraded Androidx AppCompat version from 1.4.2 -> 1.6.1
* `Dependency` Upgraded Commons IO version from 2.8.0 -> 2.14.0
* `Dependency` Upgraded Jackson DataBind version from 2.13.3 -> 2.14.3
* `Dependency` Removed Zeugma Solutions LocaleHelper version 1.5.1

# v6.3.3

###### 2023/07/21

* `Feature` Code commenting feature in code editor (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Feature` auto.stateListener for accessibility service connection status listening (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Feature` Added nextSibling / lastChild / offset methods to UiObject type (Refer to Project Documentation > [UiObject](https://docs.autojs6.com/#/uiObjectType))
* `Fix` Issue where VSCode plugin could not parse data when script character total length exceeded four-decimal digits _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `Fix` Issue where VSCode plugin could not save file properly _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `Fix` Issue where clicking 'Manage Accessibility Service' in floating button menu might not trigger page navigation
* `Fix` Issue where runtime.requestPermissions method was missing _[`issue #104`](http://issues.autojs6.com/104)_
* `Fix` Issue where events.emitter did not support MainThreadProxy parameter _[`issue #103`](http://issues.autojs6.com/103)_
* `Fix` Code formatting issue in code editor present in _[`pr #78`](http://pr.autojs6.com/78)_
* `Fix` StackOverflow error issue in ClassLoader call stack when using JavaAdapter _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `Improvement` Adjusted module scope (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Improvement` Removed signature verification during launch for release version apps (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `Improvement` Enhanced editor code commenting behavior, style, and cursor position handling based on _[`pr #98`](http://pr.autojs6.com/98)_
* `Improvement` Added code commenting menu item based on _[`pr #98`](http://pr.autojs6.com/98)_
* `Improvement` Adapted to VSCode plugin 1.0.6
* `Improvement` Added level parameter support to UiObject#parent method (Refer to Project Documentation > [UiObject](https://docs.autojs6.com/#/uiObjectType))
* `Dependency` Upgraded Gradle version from 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `Feature` crypto module (Refer to Project Documentation > [Crypto](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `Feature` Added textswitcher / viewswitcher / viewflipper / numberpicker / video / search controls to UI mode
* `Feature` Added copy and export log functionalities to Log Activity Page _[`issue #76`](http://issues.autojs6.com/76)_
* `Feature` Added IP address history feature to Client Mode
* `Fix` Issue where IP address information might not be displayed after auto-connecting in Client Mode or auto-starting in Server Mode
* `Fix` Issue where connection is lost and cannot be reconnected after switching language or night mode in Client Mode and Server Mode
* `Fix` Issue where custom port could not be used when entering target address in Client Mode
* `Fix` Issue where some characters cause AutoJs6 to crash when entering target address in Client Mode
* `Fix` Issue where remote commands in VSCode plugin might fail to parse, causing commands to be unresponsive (tentative fix)
* `Fix` Issue where version details could not be fetched when a new version is discovered on Android 7.x
* `Fix` Issue where images.pixel could not get pixel color values from accessibility service screenshots _[`issue #73`](http://issues.autojs6.com/73)_
* `Fix` Issue where built-in properties could not be used with Android native controls (uppercase) in UI mode
* `Fix` Issue where runtime.loadDex/loadJar only the first file took effect when loading multiple files _[`issue #88`](http://issues.autojs6.com/88)_
* `Fix` Issue where launcher only displayed document icon when installing app on some devices (tentative fix) _[`issue #85`](http://issues.autojs6.com/85)_
* `Improvement` Adapted to VSCode plugin 1.0.5
* `Improvement` Supported cheerio module (Refer to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `Improvement` JsWebSocket instance supports rebuild method to recreate instance and establish connection _[`issue #69`](http://issues.autojs6.com/69)_
* `Improvement` base64 module supports number array and Java byte array as main parameters for encoding and decoding
* `Improvement` Added support for JavaMail for Android _[`issue #71`](http://issues.autojs6.com/71)_
* `Improvement` Used Blob data type when fetching version update information to enhance adaptability in proxy-free network environments
* `Improvement` Display target IP address in home page drawer subtitle during Client Mode connection
* `Improvement` Added prompt for invalid input when entering target address in Client Mode
* `Improvement` Supported using enter key on soft keyboard to establish connection in Client Mode
* `Improvement` Kept Server Mode active after being enabled (unless manually closed or app process ended) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `Improvement` Implemented bidirectional version detection and prompt abnormal results between AutoJs6 and VSCode plugin _[`issue #89`](http://issues.autojs6.com/89)_
* `Improvement` Added reading SMS data permission (android.permission.READ_SMS) (default off)
* `Improvement` Internal implementation of findMultiColors method (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `Improvement` Supported loading by directory level or simultaneously loading multiple files with runtime.loadDex/loadJar/load
* `Dependency` Upgraded LeakCanary version from 2.11 -> 2.12
* `Dependency` Upgraded Android Analytics version from 14.2.0 -> 14.3.0
* `Dependency` Upgraded Gradle version from 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `Feature` Release notification permission and home page drawer switch _[`issue #55`](http://issues.autojs6.com/55)_
* `Feature` UI mode supports simple Android layout parsing (Refer to Sample Code > Layout > Simple Android Layout)
* `Feature` Added console / imagebutton / ratingbar / switch / textclock / togglebutton controls to UI mode
* `Feature` UI mode supports [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) type for control color values (e.g., color="orange")
* `Feature` Fully supports attr method for control properties in UI mode (e.g., ui.text.attr('color', 'blue'))
* `Feature` Supports concise form for boolean type property values in UI mode (e.g., clickable="true" can be shortened to clickable or isClickable)
* `Feature` Button control supports isColored and isBorderless boolean properties
* `Feature` console.resetGlobalLogConfig method for resetting global log configuration
* `Feature` web.newWebSocket method for creating WebSocket instance (Refer to Project Documentation > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `Fix` Abnormal folder sorting in File Manager
* `Fix` Issue where floaty module built floating window could not adjust style and position _[`issue #60`](http://issues.autojs6.com/60)_
* `Fix` Issue where floaty module built floating window overlapped with system status bar
* `Fix` Functional anomaly in http.postMultipart method _[`issue #56`](http://issues.autojs6.com/56)_
* `Fix` Issue where scripts could not run on Android 7.x _[`issue #61`](http://issues.autojs6.com/61)_
* `Fix` Issue where project could not be built when sign.property file was missing
* `Fix` Issue where AutoJs6 might crash when placed in the background due to lack of foreground notification permission in higher versions (API >= 33)
* `Fix` Issue where floating button in log window could not clear logs after invoking console.show method
* `Fix` NullPointerException related to prototype during script debugging in script editor
* `Fix` Issue where script editor ran temporary scripts in cache folder instead of saving and running at original location, to prevent potential loss of script content
* `Fix` Adjusted level color bar width in layout analysis to avoid control names not displaying when there are too many levels _[`issue #46`](http://issues.autojs6.com/46)_
* `Improvement` Added exit button to close window in layout analysis floating window _[`issue #63`](http://issues.autojs6.com/63)_
* `Improvement` Used abbreviated form for absolute script paths to reduce text length and increase readability
* `Improvement` Replaced use of Error with Exception to avoid crashing AutoJs6 on exceptions
* `Improvement` Migrated view binding method from ButterKnife to View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `Improvement` Auto-started Server Mode on AutoJs6 launch when it was abnormally closed _[`issue #64`](http://issues.autojs6.com/64)_
* `Improvement` Auto-reconnected last used address in Client Mode on AutoJs6 launch when it was abnormally closed
* `Dependency` Upgraded LeakCanary version from 2.10 -> 2.11
* `Dependency` Upgraded Android Material version from 1.8.0 -> 1.9.0
* `Dependency` Upgraded Androidx WebKit version from 1.6.1 -> 1.7.0
* `Dependency` Upgraded OkHttp3 version from 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `Dependency` Upgraded MLKit Text Recognition Chinese version from 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `Feature` ocr module (refer to project documentation > [Optical Character Recognition](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Feature` notice module (refer to project documentation > [Message Notification](https://docs.autojs6.com/#/notice))
* `Feature` s13n module (refer to project documentation > [Serialization](https://docs.autojs6.com/#/s13n))
* `Feature` Color module (refer to project documentation > [Color Type](https://docs.autojs6.com/#/colorType))
* `Feature` Keep the screen on functionality and setting options while in the foreground
* `Feature` Additional documentation launcher for independent reading of application documents (support hiding or showing in settings)
* `Fix` Abnormal function of colors.toString method
* `Fix` Abnormal function of app.openUrl method automatically adding protocol prefix
* `Fix` Abnormal behavior of app.viewFile/editFile when the parameter corresponding file does not exist
* `Fix` Callback function of pickup method cannot be called
* `Fix` The bounds attribute value of control information displayed by layout analysis is replaced by a comma symbol
* `Fix` Selectors bounds/boundsInside/boundsContains cannot normally filter narrow empty rectangles (e.g., inverted boundary rectangles) _[`issue #49`](http://issues.autojs6.com/49)_
* `Fix` Issue causing app crash when clicking or long pressing the home document tab after changing theme or language
* `Fix` Text editor jitter issue when zooming font size with two fingers
* `Fix` Issue with some dependency sources unable to be downloaded in build scripts (now fully integrated) _[`issue #40`](http://issues.autojs6.com/40)_
* `Fix` Issue with Tasker unable to add AutoJs6 Action Plugin (tentative fix) _[`issue #41`](http://issues.autojs6.com/41)_
* `Fix` Issue with ButterKnife annotations unable to parse resource ID when compiling projects with higher version JDKs _[`issue #48`](http://issues.autojs6.com/48)_
* `Fix` Higher probability of accessibility services encountering service exceptions (tentative fix)
* `Fix` Issue with images.medianBlur size parameter usage not matching documentation
* `Fix` Issue with engines module losing the period symbol between file name and extension when displaying full script name
* `Fix` Potential computational error in the weighted RGB distance detection algorithm internal implementation (tentative fix)
* `Fix` Issue with console module floating window related methods not usable before the show method
* `Fix` Issue with console.setSize and other methods possibly not taking effect _[`issue #50`](http://issues.autojs6.com/50)_
* `Fix` Color constants assignment error in colors.material color space
* `Fix` Issue with UI mode date picker minDate and maxDate attributes unable to correctly parse date format
* `Fix` Issue with switching to the home "Task" tab page quickly after running the script displaying two identical running tasks
* `Fix` Issue with file management page state possibly being reset when returning from other pages _[`issue #52`](http://issues.autojs6.com/52)_
* `Fix` Issue with file management page sort state not matching icon display state
* `Improvement` Added file and folder modification time display on the file management page
* `Improvement` File management page sort type supports status memory
* `Improvement` README.md added sections for project build and script development assistance _[`issue #33`](http://issues.autojs6.com/33)_
* `Improvement` Region option parameter of images module related methods supports more input methods (refer to project documentation > [Omni Types](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Improvement` Added pref/homepage/docs/about etc. support for shorthand parameters in app.startActivity
* `Improvement` Mounted global methods of web module to the module itself to enhance availability (refer to project documentation > [World Wide Web](https://docs.autojs6.com/#/web))
* `Improvement` Default implementation of some commonly used WebView setting options in web.newInjectableWebView method
* `Improvement` Added various conversion methods, utility methods, more static constants and color names that can be directly used as parameters in colors module
* `Improvement` Added various console floating window style configuration methods in console module and support for unified window style configuration by build builder
* `Improvement` Support dragging title area to move window position for console floating window
* `Improvement` Support automatic delayed closing after script ends for console floating window
* `Improvement` Support adjusting font size with pinch-to-zoom for both console floating window and its Activity window
* `Improvement` Support timeout parameter for http module related methods
* `Improvement` Support active downgrade (fallback) of JDK version in Gradle build script
* `Improvement` Support for Gradle build script to automatically select appropriate build tool versions based on platform type and version (to some extent)
* `Dependency` Localized Auto.js APK Builder version 1.0.3
* `Dependency` Localized MultiLevelListView version 1.1
* `Dependency` Localized Settings Compat version 1.1.5
* `Dependency` Localized Enhanced Floaty version 0.31
* `Dependency` Added MLKit Text Recognition Chinese version 16.0.0-beta6
* `Dependency` Upgraded Gradle version 8.0-rc-1 -> 8.2-milestone-1
* `Dependency` Upgraded Android Material version 1.7.0 -> 1.8.0
* `Dependency` Upgraded Glide version 4.14.2 -> 4.15.1
* `Dependency` Upgraded Joda Time version 2.12.2 -> 2.12.5
* `Dependency` Upgraded Android Analytics version 14.0.0 -> 14.2.0
* `Dependency` Upgraded Androidx WebKit version 1.5.0 -> 1.6.1
* `Dependency` Upgraded Androidx Recyclerview version 1.2.1 -> 1.3.0
* `Dependency` Upgraded Zip4j version 2.11.2 -> 2.11.5
* `Dependency` Upgraded Junit Jupiter version 5.9.2 -> 5.9.3
* `Dependency` Upgraded Androidx Annotation version 1.5.0 -> 1.6.0
* `Dependency` Upgraded Jackson DataBind version 2.14.1 -> 2.14.2
* `Dependency` Upgraded Desugar JDK Libs version 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `Feature` Redesign and rewrite of project documentation (partially completed)
* `Feature` Multi-language adaptation for Western/France/Russian/Arabic/Japanese/Korean/English/Traditional Chinese, etc.
* `Feature` Added path selection/history/default value intelligent suggestion functions to the work path setting options
* `Feature` File manager supports jumping to the parent directory of any directory (up to "Internal Storage")
* `Feature` File manager supports setting any directory as the work path shortcut
* `Feature` Version update ignore and manage ignored update functions
* `Feature` Text editor supports zooming font size with two fingers
* `Feature` idHex selector (UiSelector#idHex) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` action selector (UiSelector#action) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` Match series selectors (UiSelector#xxxMatch) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` pickup selector (UiSelector#pickup) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `Feature` Control detection (UiObject#detect) (refer to project documentation > [Control Node](https://docs.autojs6.com/#/uiObjectType))
* `Feature` Control compass (UiObject#compass) (refer to project documentation > [Control Node](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `Feature` Global wait method wait (refer to project documentation > [Global Object](https://docs.autojs6.com/#/global?id=m-wait))
* `Feature` Global scaling methods cX/cY/cYx (refer to project documentation > [Global Object](https://docs.autojs6.com/#/global?id=m-wait))
* `Feature` Global App type (refer to project documentation > [Application Enumeration](https://docs.autojs6.com/#/appType))
* `Feature` i18n module (JavaScript-based multi-language solution based on banana-i18n) (refer to project documentation > Internationalization)
* `Fix` Page text flickering and some page button function anomalies after software language switching
* `Fix` Issue with project toolbar not displaying after software startup when work path is a project
* `Fix` Issue with work path possibly changing automatically following software language switching _[`issue #19`](http://issues.autojs6.com/19)_
* `Fix` Significant delay in scheduled task startup (tentative fix) _[`issue #21`](http://issues.autojs6.com/21)_
* `Fix` Issue with internal modules with dependencies unable to be used normally when JavaScript module name is overwritten _[`issue #29`](http://issues.autojs6.com/29)_
* `Fix` Issue with Android higher versions possibly not automatically collapsing the quick settings panel after clicking relevant icons (tentative fix) _[`issue #7`](http://issues.autojs6.com/7)_
* `Fix` Issue with overlapping of some pages and notification bar area on higher version Andes systems
* `Fix` Issue with example code setting brush color not running normally on Android 10 and above
* `Fix` Corrected example code "Music Manager" to "File Manager" and restored normal function
* `Fix` Issue with possible localization drift when refreshing file manager
* `Fix` Issue with binding error in ui module scope causing some UI-based scripts to not access component properties
* `Fix` Issue with losing recorded content due to external area click in the dialog box when entering file name after recording script
* `Fix` Issue with automatic line-wrapping failure causing content loss when some chapter titles in the document exceed screen width
* `Fix` Issue with example code area in the document unable to slide left and right normally
* `Fix` Issue with abnormal performance and inability to undo refresh operation when refreshing document page
* `Fix` Issue with night mode linkage failure of home drawer after initial installation
* `Fix` Issue with night mode forced on after application startup when system night mode is on
* `Fix` Issue with theme color possibly not taking effect after night mode is turned on
* `Fix` Issue with some setting options text and background color being the same and unrecognizable in night mode
* `Fix` Issue with incomplete display of function button text on the about page due to excessive text length
* `Fix` Issue with text and button overlap on the home drawer settings item title due to excessive length
* `Fix` Issue with root permission switch status not synchronized after prompt message dialog disappears on home drawer permission
* `Fix` Issue with no ADB tool dialog popping up when root permission fails to modify home drawer permission switches
* `Fix` Issue with no root permission prompt during initial use of pointer position display
* `Fix` Abnormal layout of icon elements on the icon selection page
* `Fix` Issue with screen flickering caused by night mode setting when starting the text editor (tentative fix)
* `Fix` Issue with the maximum available value limited when setting font size in text editor
* `Fix` Issue with runtime duration not being logged in the log after script execution ends in some Android systems
* `Fix` Issue with floating button still displayed after application restart when floating button menu close button was used
* `Fix` Issue with menu overflow below long press list item possibly causing pop-up menu overflow screen during layout hierarchy analysis
* `Fix` Issue with navigation bar buttons hard to identify on Android 7.x system when night mode is off
* `Fix` Possible issue with request not closing in http.post and other methods
* `Fix` Issue with loss of alpha channel information in the result when colors.toString method alpha channel is 0
* `Improvement` Redirected public classes of Auto.js 4.x version to achieve downward compatibility as far as possible (to some extent)
* `Improvement` Merged all project modules to avoid possible circular references and other issues (temporarily removed inrt module)
* `Improvement` Migrated Gradle build configuration from Groovy to KTS
* `Improvement` Added multi-language support to Rhino exception messages
* `Improvement` Home drawer permission switch prompts message only when enabled
* `Improvement` Home drawer layout closely aligned under the status bar to avoid the top color strip's low compatibility
* `Improvement` Check for updates/download updates/update prompt functions compatible with Android 7.x system
* `Improvement` Redesigned settings page (migrated to AndroidX)
* `Improvement` Settings page supports long press on settings options to get detailed information
* `Improvement` Added "Follow System" setting option in night mode (Android 9 and above)
* `Improvement` Application startup screen adapted for night mode
* `Improvement` Added numeric identifier to application icon to enhance the user experience of coexistence with multiple open-source versions
* `Improvement` Added more Material Design Color options to theme color
* `Improvement` Appropriately lightweighted and theme color compatible icons for list items such as file manager/task panel
* `Improvement` Text color of prompt text in home search box adapted for night mode
* `Improvement` Adapted dialog box/text/Fab/AppBar/list items and other components for night mode
* `Improvement` Adapted document/settings/about/theme color/layout analysis and other pages and floating button menu for night mode
* `Improvement` Page layout as compatible as possible with RTL (Right-To-Left) layout
* `Improvement` Added icon animation effects on the about page
* `Improvement` Automatically updated year information in copyright statement text on the about page
* `Improvement` Automatically determined and set appropriate work directory after initial installation
* `Improvement` Disabled pinch-to-zoom function on document pages to avoid abnormal document content display
* `Improvement` Simplified task name and path display in relative path on Task Panel list items
* `Improvement` Appropriately abbreviated button text to avoid text overflow in text editor
* `Improvement` Supported resetting the font size setting to the default value in text editor
* `Improvement` Enhanced floating button click response speed
* `Improvement` Directly performed layout range analysis when clicking floating button layout analysis button
* `Improvement` Adaptive layout analysis theme (floating window follows application theme, quick settings panel follows system theme)
* `Improvement` Reordered layout control information list according to possible frequency of use
* `Improvement` Automatically optimized output format according to selector type when clicking to copy layout control information
* `Improvement` Supported returning to the parent directory instead of directly closing the window when selecting files with a floating window and pressing the return key
* `Improvement` Supported digital validity detection and automatic conversion of dot-separated symbols when entering address in client mode to connect to computer
* `Improvement` Displayed IP address of corresponding device in home drawer after client and server establish a connection
* `Improvement` Added rewrite protection to some global objects and built-in modules (refer to project documentation > Global Object > [Rewrite Protection](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `Improvement` Supported string parameters and variable-length parameters in importClass and importPackage
* `Improvement` Supported printing stack trace information when exception occurs in ui.run
* `Improvement` Conveniently obtained resource ID of AutoJs6 through ui.R and auto.R
* `Improvement` Supported App type parameters and application alias parameters in methods related to operating applications in app module
* `Improvement` Supported omission of pre-filled parameters in methods related to asynchronous callbacks in dialogs module
* `Improvement` Supported url option parameters in app.startActivity, etc. (refer to example code > Application > Intent)
* `Improvement` Returned null instead of throwing exceptions when failing to get IMEI or hardware serial number in device module
* `Improvement` Enhanced text brightness of the log floating window displayed by console.show to improve content recognition
* `Improvement` Supported saving image files with relative paths in ImageWrapper#saveTo
* `Improvement` Redesigned global colors object and added support for HSV / HSL and other color modes (refer to project documentation > [Color](https://docs.autojs6.com/#/color))
* `Dependency` Upgraded Gradle Compile version 32 -> 33
* `Dependency` Localized Android Job version 1.4.3
* `Dependency` Localized Android Plugin Client SDK For Locale version 9.0.0
* `Dependency` Localized GitHub API version 1.306
* `Dependency` Added JCIP Annotations version 1.0
* `Dependency` Added Androidx WebKit version 1.5.0
* `Dependency` Added Commons IO version 2.8.0
* `Dependency` Added Desugar JDK Libs version 2.0.0
* `Dependency` Added Jackson DataBind version 2.13.3
* `Dependency` Added Jaredrummler Android Device Names version 2.1.0
* `Dependency` Added Jaredrummler Animated SVG View version 1.0.6
* `Dependency` Replaced Jrummyapps ColorPicker version 2.1.7 with Jaredrummler ColorPicker version 1.1.0
* `Dependency` Upgraded Gradle version 7.5-rc-1 -> 8.0-rc-1
* `Dependency` Upgraded Gradle build tool version 7.4.0-alpha02 -> 8.0.0-alpha09
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.10 -> 1.8.0-RC2
* `Dependency` Upgraded Android Material version 1.6.0 -> 1.7.0
* `Dependency` Upgraded Androidx Annotation version 1.3.0 -> 1.5.0
* `Dependency` Upgraded Androidx AppCompat version 1.4.1 -> 1.4.2
* `Dependency` Upgraded Android Analytics version 13.3.0 -> 14.0.0
* `Dependency` Upgraded Gson version 2.9.0 -> 2.10
* `Dependency` Upgraded Joda Time version 2.10.14 -> 2.12.1
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.1-native-mt -> 1.6.1
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `Dependency` Upgraded Zip4j version 2.10.0 -> 2.11.2
* `Dependency` Upgraded Glide version 4.13.2 -> 4.14.2
* `Dependency` Upgraded Junit Jupiter version 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `Feature` Check for updates/download updates/update prompt functions (refer to Settings page) (currently not supported on Android 7.x systems)
* `Fix` Issue with the application unable to read/write external storage on Android 10 systems _[`issue #17`](http://issues.autojs6.com/17)_
* `Fix` Issue where long presses on the editor page could cause the application to crash _[`issue #18`](http://issues.autojs6.com/18)_
* `Fix` Issue with the long-press menu 'Delete Row' and 'Copy Row' functions not working on the editor page
* `Fix` Issue with the 'Paste' function missing from the options menu on the editor page
* `Improvement` Resource localization of some exception message strings (en / zh)
* `Improvement` Adjust the button layout of the unsaved content dialog and add color differentiation
* `Dependency` Added github-api version 1.306
* `Dependency` Replaced retrofit2-rxjava2-adapter version 1.0.0 with adapter-rxjava2 version 2.9.0

# v6.1.0

###### 2022/05/26 - Package name changed, proceed with caution

* `Hint` Changed the application package name to org.autojs.autojs6 to avoid conflicts with the open-source Auto.js application package name
* `Feature` Added 'Projection Media Permission' switch to the home drawer (Root / ADB method) (experimental switch status detection)
* `Feature` File browser supports displaying hidden files and folders (refer to Settings page)
* `Feature` Forced Root check function (refer to Settings page and example code)
* `Feature` autojs module (refer to Example Code > AutoJs6)
* `Feature` tasks module (refer to Example Code > Tasks)
* `Feature` console.launch() method to launch the log activity page
* `Feature` util.morseCode tool (refer to Example Code > Tools > Morse Code)
* `Feature` util.versionCodes tool (refer to Example Code > Tools > Android Version Information Query)
* `Feature` util.getClass() and other methods (refer to Example Code > Tools > Get Class and Class Names)
* `Feature` timers.setIntervalExt() method (refer to Example Code > Timers > Conditional Periodic Execution)
* `Feature` colors.toInt() / rgba() and other methods (refer to Example Code > Image and Color > Basic Color Conversion)
* `Feature` automator.isServiceRunning() / ensureService() methods
* `Feature` automator.lockScreen() and other methods (refer to Example Code > Accessibility Service > Android 9 New Additions)
* `Feature` automator.headsethook() and other methods (refer to Example Code > Accessibility Service > Android 11 New Additions)
* `Feature` automator.captureScreen() method (refer to Example Code > Accessibility Service > Take Screenshots)
* `Feature` dialogs.build() option parameter properties animation, linkify, etc. (refer to Example Code > Dialogs > Customized Dialogs)
* `Fix` Issues with dialogs.build() option parameter properties like inputHint, itemsSelectedIndex, etc.
* `Fix` Issue with JsDialog#on('multi_choice') callback parameters
* `Fix` Issue with UiObject#parent().indexInParent() always returning -1 _[`issue #16`](http://issues.autojs6.com/16)_
* `Fix` Issue with Promise.resolve() returned Thenable not being called near the end of the script
* `Fix` Possible spelling errors in package or class names (boardcast -> broadcast / auojs -> autojs)
* `Fix` Issue with images.requestScreenCapture() potentially causing application crashes on higher Android versions (API >= 31)
* `Fix` Issue with images.requestScreenCapture() potentially causing application crashes when multiple script instances request it simultaneously
* `Fix` Possible deadlock issue when invoking new RootAutomator()
* `Improvement` RootAutomator cannot be instantiated without Root permissions
* `Improvement` Redesigned the 'About Application and Developer' page
* `Improvement` Refactored all built-in JavaScript modules
* `Improvement` Refactored all Gradle build scripts and added a common configuration script (config.gradle)
* `Improvement` Gradle build tools support automatic version number management and automatic naming of build files
* `Improvement` Gradle build tools add task support to append CRC32 digest to build files (appendDigestToReleasedFiles)
* `Improvement` shell() calls will write exceptions into the return result instead of directly throwing exceptions (no need for try/catch)
* `Improvement` Replaced original json2 module with Rhino built-in JSON
* `Improvement` auto.waitFor() supports timeout parameters
* `Improvement` threads.start() supports arrow function parameters
* `Improvement` console.trace() supports log level parameters (refer to Example Code > Console > Print Call Stack)
* `Improvement` device.vibrate() supports mode vibration and Morse code vibration (refer to Example Code > Device > Mode Vibration / Morse Code Vibration)
* `Improvement` Adapted read/write permissions for external storage to higher Android versions (API >= 30)
* `Improvement` Enhanced font readability in both normal and night themes for the console using Material Color
* `Improvement` Saved all instances of ImageWrapper as weak references and automatically recovered them when the script ends (experimental)
* `Dependency` Added CircleImageView version 3.1.0
* `Dependency` Upgraded Android Analytics version 13.1.0 -> 13.3.0
* `Dependency` Upgraded Gradle build tools version 7.3.0-alpha06 -> 7.4.0-alpha02
* `Dependency` Upgraded Android Job version 1.4.2 -> 1.4.3
* `Dependency` Upgraded Android Material version 1.5.0 -> 1.6.0
* `Dependency` Upgraded CrashReport version 2.6.6 -> 4.0.4
* `Dependency` Upgraded Glide version 4.13.1 -> 4.13.2
* `Dependency` Upgraded Joda Time version 2.10.13 -> 2.10.14
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.10 -> 1.6.21
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.0 -> 1.6.1-native-mt
* `Dependency` Upgraded LeakCanary version 2.8.1 -> 2.9.1
* `Dependency` Upgraded OkHttp3 version 5.0.0-alpha.6 -> 5.0.0-alpha.7
* `Dependency` Upgraded Rhino engine version 1.7.14 -> 1.7.15-SNAPSHOT
* `Dependency` Upgraded Zip4j version 2.9.1 -> 2.10.0
* `Dependency` Removed Groovy JSON version 3.0.8
* `Dependency` Removed Kotlin Stdlib JDK7 version 1.6.21

# v6.0.3

###### 2022/03/19

* `Feature` Multi-language switch function (not yet perfect)
* `Feature` recorder module (refer to Example Code > Timers)
* `Feature` Use 'Modify Secure Settings Permissions' to automatically enable accessibility services and toggle settings
* `Fix` Issue with quick settings panel not automatically collapsing after clicking relevant icons (tentative fix) _[`issue #7`](http://issues.autojs6.com/7)_
* `Fix` Issue where AutoJs6 might crash when the toast is forced to display
* `Fix` Issue with AutoJs6 possibly crashing when the Socket transmission data header information is incomplete
* `Improvement` Automatically enable accessibility services according to option settings when starting or restarting AutoJs6
* `Improvement` Attempt to automatically enable accessibility services when the floating button switch is turned on
* `Improvement` Added corresponding English translations for all resource file elements
* `Improvement` Slightly adjusted the layout of the home drawer to reduce project item spacing
* `Improvement` Added synchronization of foreground service status switch in the home drawer
* `Improvement` Synchronize switch status on demand immediately when the home drawer is expanded
* `Improvement` Added status detection and result prompts for displaying pointer position
* `Improvement` Supported 64-bit operating systems (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Improvement` Applied transparency settings simultaneously when initializing the floating button (no need to click to apply transparency)
* `Improvement` Added detection of whether the file is example code and result prompt when resetting file content
* `Improvement` Transferred plugin download address from GitHub to JsDelivr
* `Dependency` Added Zeugma Solutions LocaleHelper version 1.5.1
* `Dependency` Downgraded Android Material version 1.6.0-alpha02 -> 1.5.0
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.0-native-mt -> 1.6.0
* `Dependency` Upgraded OpenCV version 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `Dependency` Upgraded Gradle build tools version 7.2.0-beta01 -> 7.3.0-alpha06
* `Dependency` Upgraded Auto.js-ApkBuilder version 1.0.1 -> 1.0.3
* `Dependency` Upgraded Glide Compiler version 4.12.0 -> 4.13.1
* `Dependency` Upgraded Gradle release version 7.4-rc-2 -> 7.4.1
* `Dependency` Upgraded Gradle Compile version 31 -> 32
* `Dependency` Upgraded Gson version 2.8.9 -> 2.9.0

# v6.0.2

###### 2022/02/05

* `Feature` images.bilateralFilter() bilateral filtration image processing method
* `Fix` Issue where multiple calls to toast only took effect on the last call
* `Fix` Issue where toast.dismiss() might not work
* `Fix` Issues with client mode and server mode switches possibly not working properly
* `Fix` Issues with client mode and server mode switch statuses not refreshing properly
* `Fix` Issue with parsing UI mode text elements on Android 7.x (ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `Improvement` Ignore sleep()'s ScriptInterruptedException
* `Dependency` Added Androidx AppCompat (Legacy) version 1.0.2
* `Dependency` Upgraded Androidx AppCompat version 1.4.0 -> 1.4.1
* `Dependency` Upgraded Androidx Preference version 1.1.1 -> 1.2.0
* `Dependency` Upgraded Rhino engine version 1.7.14-SNAPSHOT -> 1.7.14
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `Dependency` Upgraded Android Material version 1.6.0-alpha01 -> 1.6.0-alpha02
* `Dependency` Upgraded Gradle build tools version 7.2.0-alpha06 -> 7.2.0-beta01
* `Dependency` Upgraded Gradle release version 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `Feature` Support for VSCode plugin connection in client (LAN) and server (LAN/ADB) modes (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` base64 module (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` Added global methods isInteger/isNullish/isObject/isPrimitive/isReference
* `Feature` Added polyfill (Object.getOwnPropertyDescriptors)
* `Feature` Added polyfill (Array.prototype.flat)
* `Improvement` Extended global.sleep to support random ranges/negative numbers
* `Improvement` Extended global.toast to support duration control/forced overlay control/dismiss
* `Improvement` Globalized package name objects (okhttp3/androidx/de)
* `Dependency` Upgraded Android Material version 1.5.0-beta01 -> 1.6.0-alpha01
* `Dependency` Upgraded Gradle build tools version 7.2.0-alpha04 -> 7.2.0-alpha06
* `Dependency` Upgraded Kotlinx Coroutines version 1.5.2-native-mt -> 1.6.0-native-mt
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.0 -> 1.6.10
* `Dependency` Upgraded Gradle release version 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `Feature` Added a restart app button at the bottom of the home drawer
* `Feature` Added switches for ignoring battery optimization/showing on top of other apps in the home drawer
* `Fix` Fixed issue where initial installation caused theme color rendering issues in some areas
* `Fix` Fixed issue where the project could not be built when the sign.property file does not exist
* `Fix` Fixed incorrect month storage/read errors for one-time tasks in the scheduled task panel
* `Fix` Fixed issue where switch colors in the app settings page did not change with the theme
* `Fix` Fixed issue where packing plugins could not be detected and invalid plugin download addresses
* `Fix` Fixed issue where the switch state of 'View Usage Stats Permission' in the home drawer might not sync
* `Fix` Fixed potential Mat memory leak issue in TemplateMatching.fastTemplateMatching
* `Improvement` Upgraded Rhino engine version 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `Improvement` Upgraded OpenCV version 3.4.3 -> 4.5.4
* `Improvement` Enhanced compatibility of ViewUtil.getStatusBarHeight
* `Improvement` Removed user login related modules and layout placeholders from the home drawer
* `Improvement` Removed community and market tab pages from the home and optimized the layout
* `Improvement` Modified default switch states for some settings options
* `Improvement` Added SinceDate to the About page and optimized copyright display
* `Improvement` Upgraded JSON module to the 2017-06-12 version and integrated cycle.js
* `Improvement` Removed automatic update check functionality when Activity is brought to the foreground and removed related update check buttons
* `Improvement` Improved internal logic of AppOpsKt#isOpPermissionGranted
* `Improvement` Enhanced security of ResourceMonitor using ReentrantLock (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Improvement` Replaced JCenter repositories with Maven Central and other repositories
* `Improvement` Extracted and removed duplicate local library files
* `Dependency` Localized CrashReport version 2.6.6
* `Dependency` Localized MutableTheme version 1.0.0
* `Dependency` Added Androidx Preference version 1.1.1
* `Dependency` Added SwipeRefreshLayout version 1.1.0
* `Dependency` Upgraded Android Analytics version 7.0.0 -> 13.1.0
* `Dependency` Upgraded Android Annotations version 4.5.2 -> 4.8.0
* `Dependency` Upgraded Gradle build tools version 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `Dependency` Upgraded Android Job version 1.2.6 -> 1.4.2
* `Dependency` Upgraded Android Material version 1.1.0-alpha01 -> 1.5.0-beta01
* `Dependency` Upgraded Androidx MultiDex version 2.0.0 -> 2.0.1
* `Dependency` Upgraded Apache Commons Lang3 version 3.6 -> 3.12.0
* `Dependency` Upgraded Appcompat version 1.0.2 -> 1.4.0
* `Dependency` Upgraded ButterKnife Gradle plugin version 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `Dependency` Upgraded ColorPicker version 2.1.5 -> 2.1.7
* `Dependency` Upgraded Espresso Core version 3.1.1-alpha01 -> 3.5.0-alpha03
* `Dependency` Upgraded Eventbus version 3.0.0 -> 3.2.0
* `Dependency` Upgraded Glide Compiler version 4.8.0 -> 4.12.0 -> 4.12.0
* `Dependency` Upgraded Gradle Build Tool version 29.0.2 -> 30.0.2
* `Dependency` Upgraded Gradle Compile version 28 -> 30 -> 31
* `Dependency` Upgraded Gradle release version 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `Dependency` Upgraded Groovy-Json plugin version 3.0.7 -> 3.0.8
* `Dependency` Upgraded Gson version 2.8.2 -> 2.8.9
* `Dependency` Upgraded JavaVersion version 1.8 -> 11 -> 16
* `Dependency` Upgraded Joda Time version 2.9.9 -> 2.10.13
* `Dependency` Upgraded Junit version 4.12 -> 4.13.2
* `Dependency` Upgraded Kotlin Gradle plugin version 1.3.10 -> 1.4.10 -> 1.6.0
* `Dependency` Upgraded Kotlinx Coroutines version 1.0.1 -> 1.5.2-native-mt
* `Dependency` Upgraded LeakCanary version 1.6.1 -> 2.7
* `Dependency` Upgraded LicensesDialog version 1.8.1 -> 2.2.0
* `Dependency` Upgraded Material Dialogs version 0.9.2.3 -> 0.9.6.0
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `Dependency` Upgraded Reactivex RxJava2 RxAndroid version 2.0.1 -> 2.1.1
* `Dependency` Upgraded Reactivex RxJava2 version 2.1.2 -> 2.2.21
* `Dependency` Upgraded Retrofit2 Converter Gson version 2.3.0 -> 2.9.0
* `Dependency` Upgraded Retrofit2 Retrofit version 2.3.0 -> 2.9.0
* `Dependency` Upgraded Zip4j version 1.3.2 -> 2.9.1