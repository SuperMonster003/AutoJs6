******

### Release History

******

# v6.8.0

###### 2026/10/17 - Some features moved to plugins; upgrade with care

* `Hint` Some built-in features are now provided by independent plugins (such as OCR, barcode recognition, OpenCV, OpenCC, Pinyin, MediaInfo, the terminal and offline project documentation); install and enable the corresponding plugins in the Plugin Center before use
* `Hint` The target SDK of packaged apps changes from 29 to 37; shared storage and local network access require permissions according to system requirements
* `Hint` When packaged apps use images or other features that depend on OpenCV, select the OpenCV plugin when packaging
* `Hint` The console element and floating console now display logs from the current script; global logs are available through the globalconsole element or the log page
* `Hint` File logging now uses a built-in implementation, retaining documented configuration options and common PatternLayout formats; other Log4j APIs are no longer available
* `Hint` Node.js pinyin/pinyin4j and console window controls now call the host asynchronously; scripts must await their results
* `Feature` Desktop shortcuts support automatic archiving of new and pre-existing pinned shortcuts, backup export/import and batch restoration; unreadable icons can be chosen individually or filled with defaults in bulk without replacing saved icons; missing shortcuts after unfreezing are shown on the shortcut backup page without home page prompts, and recognized manual removals are not selected by default; copies of the same script keep separate original IDs (existing shortcut discovery requires Android 7.1+, automatic detection requires Android 8+) (entry: Settings > Configuration > Shortcut backup)
* `Feature` Setup wizard, for running the permission wizard and plugin installation wizard in sequence, with an entry in the home page welcome prompt after first launch (entry: Settings > Configuration > Setup wizard)
* `Feature` Permission wizard, for selecting permissions from the home drawer and requesting them in sequence, with built-in "Standard"/"Minimal"/"All" profiles and custom profiles; granted permissions can be hidden through the menu (entry: Setup wizard/the button in the home drawer's "Permissions" group heading)
* `Feature` Plugin installation wizard, for selecting official plugins by category and installing them in batches, with built-in "Standard"/"Minimal"/"All" profiles and support for saving, renaming and deleting custom profiles (entry: Setup wizard/the floating button menu on the home Plugins tab/the Plugin Center's "+" menu)
* `Feature` Plugin installation wizard supports installing while downloading, silent installation through the 3-Setup Installer plugin with Shizuku/Root/Dhizuku authorization, and checking permission to install unknown apps before downloading when using the system installer
* `Feature` "Show launcher icon" switch in plugin settings (enabled by default), with a shared setting in the plugin installation wizard for icons of plugins newly installed in the current batch (changing app components requires Root or Shizuku)
* `Feature` Accessibility service manager, supporting starting/stopping services, clearing caches, self-checks, event monitoring and copying diagnostic information (entry: Accessibility service item in the home drawer)
* `Feature` Process exit records and copying diagnostic information in Developer options (Android 11+) (entry: long-press the app icon on the About app and developer page)
* `Feature` "Allow Inspector for Node.js scripts" switch in Developer options (disabled by default), for breakpoint debugging and CPU/heap profiling with the Node.js Runtime plugin
* `Feature` Code generation model management page, supporting local import, on-demand downloads, management and comparison of multiple versions, and updates to official models (entry: Settings > Accessibility service > Code generation models)
* `Feature` MCP Server plugin, for accessing AutoJs6 script, file and device operations through MCP clients (entry: home drawer/Plugin Center)
* `Feature` 3-Shell Terminal plugin, providing terminal capabilities (entry: home drawer, project toolbar, file manager directory menu)
* `Feature` Node.js Runtime plugin, for running Node.js files and projects (supports the "nodejs" mode directive and .node.js/.cjs/.mjs files)
* `Feature` Python Runtime plugin, for running Python files and projects, with module entry points, execution timeouts and preset standard input
* `Feature` Lua Runtime plugin, for running Lua scripts
* `Feature` Java Runtime and Kotlin Runtime plugins, for compiling and running Java/Kotlin source code (Java requires Android 7+, Kotlin requires Android 8+)
* `Feature` QuickJS Runtime plugin, for running JavaScript files and ES modules (supports the "quickjs"/"qjs" mode directives and .quickjs.js/.quickjs.mjs files)
* `Feature` Bun Runtime plugin, for running JavaScript/TypeScript files and projects (supports the "bun" mode directive and .bun.js/.bun.ts/.bun.tsx files)
* `Feature` TypeScript Engine plugin, for compiling TypeScript files and projects for Rhino/Node.js, with type checking and Source Map support
* `Feature` ai module, for accessing local or online models through AI Provider plugins (such as Three Stone AI), with streaming output, persistent sessions and structured JSON results (see Sample code > ai)
* `Feature` ai.agent script API, for starting, querying, responding to, confirming and cancelling Three Stove Agent tasks, with detached background tasks (requires the Three Stove Agent plugin) (see Sample code > agent)
* `Feature` plan option for ai.agent.run/create, for having Three Stove Agent propose a step-by-step plan before execution in plan mode
* `Feature` Scripts can be registered for Three Stove Agent to call through project.json or an @agent header comment, with argument validation, stopping on timeout and returning structured results
* `Feature` compose module, for building interfaces with Jetpack Compose, providing node factories, long-lived node handles, reactive state/render/ref and theme control (requires the 3-Block Compose plugin) (see Sample code > 3-Block Compose)
* `Feature` "Open in 3-Block Compose designer" in the editor menu: saves the current file and opens it in the visual designer of the 3-Block Compose plugin (1.2.0 or later); returning to the editor automatically reloads content saved by the designer (asks first if there are unsaved changes) (see Sample code > 3-Block Compose)
* `Feature` ui-mode layout preview service, which lets the 3-Block Compose designer preview XML layouts on its canvas with the host's real layout inflater, embedded on Android 11 and later and as a snapshot on older versions (requires 3-Block Compose plugin 1.6.0 or later)
* `Feature` compose.mount/floaty methods, for mounting UI pages or creating floating windows (floating windows support both UI and non-UI scripts), with automatic cleanup of owned sessions when the script stops
* `Feature` compose.dialog method, for creating updatable and closable dialog and modal bottom sheet sessions in UI or ordinary scripts
* `Feature` XML <compose> container and compose.attach method, for embedding Compose sessions in UI pages or floating windows; compose.AndroidView can host existing Android Views
* `Feature` TSX layouts support Compose components such as <compose.Column> and <compose:Text>, node factory references, slots and reactive callbacks (requires the TypeScript Engine plugin)
* `Feature` ui.inflate supports compiled XML TSX layouts and their onClick callback bindings
* `Feature` flow module and waitAsync global method, for asynchronous waiting and chained operations, with branches, loops, timeouts, retries and cancellation (see Project documentation > [Flow](https://docs.autojs6.com/#/flow))
* `Feature` terminal global object, for managing terminal sessions, visible execution and output listeners, with output waiting and exit codes (requires the 3-Shell Terminal plugin) (see Sample code > Terminal)
* `Feature` installer module, for installing, updating and uninstalling apps, with options for silent Shizuku/Root/Dhizuku operations and notification installation (requires the 3-Setup Installer plugin) (see Project documentation > [Installer](https://docs.autojs6.com/#/installer))
* `Feature` mail module, for sending, receiving and querying mail, downloading attachments and managing folders, with synchronous and asynchronous calls (requires the 3-Stamp Mail plugin) (see Project documentation > [Mail](https://docs.autojs6.com/#/mail))
* `Feature` mail.watch method, for monitoring new mail with reconnection support
* `Feature` epub module, for reading ebook metadata, tables of contents, chapter text and covers, with resource export and full-text search (requires the 3-Folio EPUB plugin) (see Project documentation > [Ebooks](https://docs.autojs6.com/#/epub))
* `Feature` epub.read method, for opening the ebook reader and controlling reading position, bookmarks and reading preferences
* `Feature` epub.annotations/annotationsAsync methods, for reading highlights and notes, with annotation change listeners in reader sessions
* `Feature` yolo module, for loading models and detecting objects through YOLO plugins (see Project documentation > [YOLO](https://docs.autojs6.com/#/yolo))
* `Feature` tts module, for text-to-speech (see Project documentation > [Text-to-speech](https://docs.autojs6.com/#/tts))
* `Feature` settings module, for reading and changing app settings (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (see Project documentation > [Settings](https://docs.autojs6.com/#/settings))
* `Feature` work_manager module, for managing scheduled and broadcast tasks (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (see Project documentation > [Work Manager](https://docs.autojs6.com/#/workManager))
* `Feature` power_manager module, for querying and requesting exemption from battery optimization (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (see Project documentation > [Power Manager](https://docs.autojs6.com/#/powerManager))
* `Feature` pangu global object, for adjusting and checking spacing in mixed Chinese and Western text (see Project documentation > [Text spacing](https://docs.autojs6.com/#/pangu))
* `Feature` device.pageSize property (getter), for obtaining the system memory page size
* `Feature` device.getMarketName method, for obtaining the device's market name _[`issue #513`](http://issues.autojs6.com/513)_
* `Feature` ui.useAndroidLayout method, for enabling native Android layout widget support
* `Feature` runtime.loadAar method, for dynamically loading AAR files
* `Feature` runtime.loadJarWithR8/retraceR8Stack methods, for loading JAR files and restoring obfuscated stack traces through the R8 Compiler plugin (experimental)
* `Feature` images.quantize/quantizeToFile methods, for PNG image quantization and file saving, returning image size and quality information (requires the Image Quantization plugin)
* `Feature` images.countPointsByColor/getMeanColor methods, for counting pixels that match a color and obtaining the average color
* `Feature` colors.distance/invert/blend/contrast methods, for calculating color distance, inversion, blending and contrast (also available on Color instances)
* `Feature` colors.toOklab/oklab/toOklch/oklch methods, for OKLab and OKLCH color space conversion; colors.distance/isSimilar and images.detectColor support the oklab algorithm
* `Feature` colors.ensureContrast/harmony/tones/derive/nightVariant/simulate/extract methods, for contrast adjustment, color harmonies, tonal palettes, derived colors, night variants, color vision deficiency simulation and dominant image color extraction (see Sample code > Images and colors)
* `Feature` colors.theme object, for reading, changing and resetting theme colors and controlling intelligent color libraries through scripts, with undo for script changes on the theme color page (see Sample code > Images and colors)
* `Feature` console.setTimeVisible/setTimeFormat/setColorful/setAvoidStatusBar/setInputVisible methods and corresponding console.build options
* `Feature` TypeScript scripts and TSX layouts in UI mode (requires the TypeScript Engine plugin)
* `Feature` globalconsole element in UI mode, for displaying global logs; console elements support title, style and input bar settings
* `Feature` Automation methods such as smartClick/clickIfExists/clickAny/findAny, for finding and clicking widgets, with Flow chaining support
* `Feature` smartClickBounds/clickBoundsIfExists/clickBoundsAny methods, for coordinate clicks within widget bounds
* `Feature` Automation methods such as scrollUntil/typeInto/dismissPopups/collectList, for scrolling searches, text input, popup handling and list collection
* `Feature` Automation methods such as launchAndWait/backUntil/backToApp/toggle/retry, for switching apps, setting states and retrying operations
* `Feature` waitForIdle/waitForEvent/waitForToast/waitForNotification methods, for waiting for an idle interface, accessibility events, toasts and notifications
* `Feature` String selector syntax in select, for combining attributes, regular expressions and Boolean conditions
* `Feature` UiSelector#minDepth/maxDepth/minIndexInParent/maxIndexInParent selectors, for filtering by node depth and index in the parent, also supported in string selector syntax
* `Feature` UiSelector#findIterator method, for lazily traversing matching widgets
* `Feature` UiObject#isStale/waitUntilGone/waitForStable methods, for detecting stale nodes and waiting for nodes to disappear or stabilize
* `Feature` UiObject#contentInvalid/contextClickable/multiLine/dismissable methods, for obtaining Boolean widget properties
* `Feature` UiObject#toJSON/dumpSubtree/fingerprint methods, for serializing nodes, exporting subtrees and calculating state fingerprints
* `Feature` Widget collections support at/first/last/nonNull/slice and per-widget operations such as clickEach/setTextEach
* `Feature` auto.findWindows/findWindowRoots methods, for obtaining windows and root nodes by window type, package name, title, display and other conditions
* `Feature` auto.explain/dump methods and auto.stats object, for selector diagnostics, node tree export and query timing statistics
* `Feature` Event deduplication and debouncing options in auto.registerEvent (disabled by default)
* `Feature` appWindowsFallback flag in auto.setFlags, for using application window root nodes when the active window root is unavailable (disabled by default)
* `Feature` File manager supports selecting multiple files and batch copying/moving/deleting
* `Feature` Path bar in the file manager, supporting navigation to parent directories by clicking and horizontal scrolling
* `Feature` Compact layout for the file manager path bar menu, with back/forward/up/refresh navigation, returning to the working directory, copying the path and setting the working directory, storage capacity display and switching between internal storage and storage cards, plus quick settings for hidden files and file extensions
* `Feature` File manager supports browsing restricted directories through Shizuku or Root, opening and editing text files, and saving changes after verifying the original; edits are kept with guidance when authorization is lost, files conflict or save results are uncertain, while other apps use read-only temporary copies; supports copying/moving/renaming/permanently deleting files or folders and multiple selection, without replacing existing targets when copying/moving, and shows permission status and preserved paths on failure (requires the corresponding authorization, accessible locations depend on the system; moves across filesystems are limited to shared storage)
* `Feature` "Floating run" option in the file manager script menu, for running/stopping scripts, editing files and viewing logs through a floating toolbar
* `Feature` "Run with AutoJs4" option in the file manager script menu, for running scripts or projects with the AutoJs4 engine and comparing results (requires the AutoJs4 Runtime plugin)
* `Feature` "Run with AutoJs4 and compare" option in the file manager script menu, for running the same script or project with the AutoJs6 and AutoJs4 engines in sequence to compare differences (requires the AutoJs4 Runtime plugin)
* `Feature` "Copy path" option in the file manager file and directory menus, for copying absolute paths to the clipboard, with batch copying (one path per line) under "More" in the selection bar
* `Feature` File manager supports browsing, previewing, compressing and extracting archives through the 3-Stack Archive plugin, with passwords, split archives and directly opening or running files within archives
* `Feature` File manager supports previewing Markdown/HTML files through the Markdown Previewer/HTML Previewer plugins, with themes, custom CSS and full-screen mode
* `Feature` File manager supports viewing images, playing audio/video and opening EPUB ebooks through the corresponding plugins, with background audio playback and notification controls
* `Feature` Image viewer supports switching between images in the same directory, pinch zoom, EXIF information and full-screen mode, with image editing and format conversion through the 3-Maple Image plugin
* `Feature` Files opened in a plugin from the file manager can be moved to the recycle bin from within the plugin and restored with undo (requires plugin support)
* `Feature` When several plugin actions fit the files selected in the file manager, the selection bar offers the others under "More" (such as batch converting images with 3-Maple Image)
* `Feature` File manager supports viewing information and manifests of APK/APKS/XAPK/APKM/APKZ/AAB packages, and installing APKs and split APK containers
* `Feature` Code editor supports loading Ace through the Ace Editor plugin, with editor engine selection in Settings and a Plugin Center entry when the plugin is unavailable
* `Feature` Ace editor supports code completion, parameter hints, hover documentation and diagnostics; TypeScript projects support go to definition, symbol renaming and automatic imports (requires the TypeScript Engine plugin)
* `Feature` Ace editor supports a shortcut panel, action history and favorite actions
* `Feature` Ace editor supports unfolding or folding all code blocks (requires an updated Ace Editor plugin) (entry: Edit > Folding > Unfold all/Fold all)
* `Feature` "Intelligent hints" group in code editor settings (Ace engine only), for configuring completion and parameter hint triggers and delays
* `Feature` Code editor supports file encoding conversion and reloading, with file name, cursor position and encoding shown in the information bar
* `Feature` Immersive full-screen mode in the code editor
* `Feature` Bottom log panel in the code editor, with script start/stop and navigation to stack frame source code (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Feature` Dedicated code editor settings page, with options for theme, font, line numbers, word wrapping, syntax highlighting thresholds and log panel visibility
* `Feature` Code editor settings add highlighted indentation guides, vector gutter icons, editor scrollbars (including automatic hiding), whitespace in selections, a drag magnifier, matching-symbol highlighting and inline completion previews (Ace engine only, requires Ace Editor plugin 2.0.0 or later)
* `Feature` TypeScript snippet execution on the log page and in the code editor's bottom log panel (requires the TypeScript Engine plugin)
* `Feature` "Screen color picker" tool in the home drawer (requires the 3-Color Picker plugin)
* `Feature` Monthly/yearly/countdown scheduled tasks and conditions for ending repetition _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `Feature` Scheduled and broadcast task creation for project folders
* `Feature` "When mail arrives" broadcast task type, with sender and subject filters; mail information is available through engines.myEngine().execArgv.mail (requires the 3-Stamp Mail plugin)
* `Feature` Layout inspection supports copying selectors that combine multiple attributes, saving node trees and offline analysis
* `Feature` Search bar in layout inspection windows, matching widget text/desc/id/className by keywords or regular expressions; the hierarchy window retains only matching nodes and their ancestors, and the bounds window draws only matching node borders, with a result list, navigation between matches and query history
* `Feature` Layout inspection search bar supports filter profiles (Boolean attributes, class presets, depth and size ranges, etc.), region selection, field mode (`k=v`) and selector mode (`$()`), retaining profiles and query modes across sessions
* `Feature` "Copy as selector" in layout inspection, converting keywords, field conditions, filter profiles and selected regions into chained selectors
* `Feature` "Generate code" in layout inspection supports single/collection scope, waiting modes, action arguments, classic/Flow style, relative locating and alternatives, with matching result previews in the layout
* `Feature` Custom theme color libraries, supporting creation or cloning, adding colors from palettes, other libraries or history, renaming, editing color values, drag sorting and batch deletion
* `Feature` Intelligent color libraries that automatically switch color groups based on night mode, time periods, sunrise/sunset, battery level, script states and other state sources, with timed or random switching and date overrides; built-in "Material day/night pairs", "Background colors" and "Follow wallpaper" presets, and generation from a seed color
* `Feature` JSON import and export for color libraries (including multi-selection export and preset snapshots)
* `Feature` "Color tools" in color details (harmonies, tonal palettes, contrast and color vision deficiency simulation), favorite and pinned colors, and a "Frequently used and recommended" virtual library
* `Feature` "Derived colors" theme setting (disabled by default), for automatically deriving accent and dark colors from the primary color
* `Feature` Retention policies for the recycle bin, version history and emergency drafts in Settings, with cleanup by age, count and storage size
* `Feature` Dedicated version history list, version previews and comparisons, with search, sorting and batch deletion
* `Feature` Manual snapshots, version pinning, tag editing, export and direct saving after restoration in version history
* `Feature` Batch restore, content previews, details, expiration hints and undo after moving to the recycle bin, with detection and cleanup of invalid entries
* `Feature` Packaged apps support Node.js projects and scripts, requiring the Node.js Runtime plugin at runtime (installation guidance appears if it is missing)
* `Feature` Packaged apps support compiling Rhino TypeScript projects during the build, with encrypted compilation output and attached Source Maps
* `Feature` Packaged app configuration supports showing or hiding logs and setting launcher shortcuts _[`issue #550`](http://issues.autojs6.com/550)_
* `Feature` QR code scanning, pairing confirmation and local network discovery for VSCode plugin connections (requires VSCode plugin 1.1.0+)
* `Feature` Remote file management and device script execution through the VSCode plugin, with progress display for large file transfers (requires VSCode plugin 1.1.0+)
* `Feature` Connection manager in client and server modes, for viewing connection status, managing clients and accessing address history
* `Feature` Client mode supports automatic reconnection after unexpected disconnection, retries when the network returns and connection restoration at app startup
* `Feature` Node.js screen_metrics module and compatibility coordinate globals, sharing host conversion rules with the Bun SDK and following current screen rotation
* `Feature` Explicit Bun host API client with Pinyin, Pinyin4j, coordinate and console operations, with independent capability switches
* `Feature` Node.js and Bun support asynchronous OpenCC conversions and common image codecs, pixel reads, transforms and color searches; Bun image file access is authorized separately
* `Feature` Node.js Workers support OpenCC and image modules through the parent runtime, including CJS, ESM, nested Workers and binary image data
* `Feature` Bun supports screen capture with a separate opt-in grant, Android consent, timeout/cancellation cleanup and repeated authorization
* `Fix` Issue where runtime.loadJar could not dynamically load class files compiled with newer Java versions
* `Fix` Issue where runtime.loadDex on Android 7.x could corrupt the DEX cache and cause class loading failures
* `Fix` Abnormal script behavior caused by incompatible scope, argument conversion and return value handling in the Rhino engine and built-in modules
* `Fix` Object.fromEntries created inaccessible properties and incomplete console output when keys were numbers or numeric strings _[`Rhino #2518`](https://github.com/mozilla/rhino/issues/2518)_
* `Fix` Issue where Proxy-wrapped functions and some JavaScript callbacks could not be passed to built-in modules, or caused nested arguments and overload ambiguity
* `Fix` Issue where EventEmitter#once listeners could be invoked repeatedly during recursive event dispatch or when callbacks threw exceptions
* `Fix` Issue where app.isInstalled/kill and other methods could not use package name arguments correctly _[`issue #495`](http://issues.autojs6.com/495)_
* `Fix` Issue where shell-related methods could hang on devices without Root access
* `Fix` Issue where dialogs.multiChoice incorrectly converted multiple-choice results to strings _[`issue #518`](http://issues.autojs6.com/518)_
* `Fix` Issue where closing script dialogs when a thread ended could crash the app
* `Fix` Issue where expired callbacks could prevent scripts from ending automatically after cancelling timers or stopping child threads
* `Fix` Issue where focus-related methods of floaty.window/rawWindow could throw threading exceptions when called from script threads
* `Fix` Issue where the results of console.setPosition/setSize/setGravity and other methods depended on call order or window display timing
* `Fix` Issue where dragging the floating console title bar could fail and the window could not approach screen edges
* `Fix` Issue where string values in objects or arrays printed by console.log/util.format and other methods lacked single quotes, differing from the util.inspect format in Auto.js 4
* `Fix` Issue where insecure options of an http request could cause subsequent requests to keep skipping certificate and hostname verification
* `Fix` Issue where network security configuration could unexpectedly block HTTP requests in release builds and packaged apps
* `Fix` Issue where the file chooser of WebView elements in UI mode could not open correctly
* `Fix` Dynamic attribute expressions in UI-mode list/grid item templates could not access the current item object (such as its name property), causing ReferenceError and blank items, and numeric expression results displayed as 18.0 instead of 18
* `Fix` Issue where UI scripts could exit unexpectedly when entering split-screen mode or resizing the window
* `Fix` Issue where regular expression arguments did not work correctly in selector overloads, the filter global method and collection selectors
* `Fix` Issue where contains selectors incorrectly interpreted ordinary string arguments as regular expressions
* `Fix` Issue where limiting widget search results could return too many results or leave remaining nodes unrecycled
* `Fix` Incorrect parsing of compass arguments and returned results in UiObject#compass and pickup/detect
* `Fix` Issue where UiObject#child/parent and other methods could enter infinite recursion
* `Fix` Incorrect attribute names in selector string representations and incorrect decimal formatting in some languages
* `Fix` Issue where exceptions thrown by global selector methods could not be caught with try/catch
* `Fix` Issue where widget searches with the findOnUiThread flag could wait forever or fail to time out on schedule
* `Fix` Issue where UiObject#clickBounds could unexpectedly end the script event loop and invalidate subsequent callbacks, or throw exceptions when called on the main thread (such as in "ui" mode scripts)
* `Fix` Issue where the source node of auto.registerEvent callbacks could cause infinite loops or out-of-bounds exceptions after node tree changes
* `Fix` Issue where pickup and UiSelector queries could always return empty results after restarting the accessibility service
* `Fix` Issue where auto.stateListener was overwritten between scripts and not removed when scripts ended
* `Fix` Issue where auto.waitFor could continue waiting until timeout even after the accessibility service began delivering events
* `Fix` Issue where disabling or restarting the accessibility service with Root access could have no effect
* `Fix` Issue where creating an app page could re-enable an accessibility service the user had manually disabled
* `Fix` Issue where auto.start/enable repeatedly restarted the accessibility service even when it was already running
* `Fix` Issue where auto.windowRoots could return empty nodes when the active window was unavailable
* `Fix` Inconsistent accessibility service status queries and error messages compared with the home drawer switch state
* `Fix` Issue where auto.services could not read enabled services when no automatic enabling method was configured
* `Fix` Issue where layout inspection required a second trigger after automatically enabling the accessibility service
* `Fix` Issue where registered event callbacks and key listeners stopped working after restarting the accessibility service
* `Fix` Possible null pointer exceptions on the first call to automator global actions, and incorrect gesture Y-coordinate scaling
* `Fix` Issue where the accessibility service could be unexpectedly interrupted when repeatedly performing back and similar actions _[`issue #527`](http://issues.autojs6.com/527)_
* `Fix` Node.js accessibility.ensureEnabled reporting a bridge call timeout instead of returning false when the accessibility service was enabled but did not bind in time
* `Fix` Issue where the notification observer misreported system notifications as toasts and missed notification events
* `Fix` Issue where requesting screen capture permission on Android 14+ could crash the app due to foreground service startup failures _[`issue #516`](http://issues.autojs6.com/516)_
* `Fix` Issue where repeated OCR calls across threads or files could repeatedly request screen capture permission _[`issue #551`](http://issues.autojs6.com/551)_
* `Fix` Issue where OCR plugin selection failures produced error messages inconsistent with the actual engine and installation state
* `Fix` Issue where the Paddle OCR plugin could not recover after service disconnection or wake automatically when not running _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_ _[`issue #517`](http://issues.autojs6.com/517)_
* `Fix` Issue where using Paddle OCR followed by Rapid OCR in packaged apps could crash the app
* `Fix` Issue where selecting both Paddle OCR v3 and v4/v5/v6 plugins during packaging made Paddle OCR unavailable in packaged apps (packaged apps support only the PP-OCRv3 plugin)
* `Fix` Issue where invalid arguments to images.resize could crash the app
* `Fix` Issue where some images methods could recycle images prematurely, leak native memory or recycle images belonging to other scripts
* `Fix` Incorrect results from the hs algorithm in colors.isSimilar/images.detectColor, and incorrect handling of fractional or percentage components in colors.rgb/rgba
* `Fix` Incorrect comparison results and grayscale or RGBA image handling in images.isEqual/mse/ncc/isGrayscale and related methods
* `Fix` Lost transparency in images.invert, swapped red and blue channels in ImageWrapper#saveTo, and incorrect handling of out-of-bounds regions in images.clip
* `Fix` Issue where images.load and other methods could leave connections open or fail to release temporary image resources
* `Fix` Incorrect edge matching, coordinate precision, result sorting and result count limits in images.findImage/matchTemplate
* `Fix` Issue where images.matchTemplate could not correctly handle images with different channel counts, solid-color templates or transparency masks, and MatchingResult#sortBy rejected fractional comparison results
* `Fix` Incorrect bounds returned by images.matchFeatures and errors in small-image feature detection, drawing match results and resource release
* `Fix` Issue where memory usage could keep growing after repeated script runs (tentative fix) _[`issue #548`](http://issues.autojs6.com/548)_
* `Fix` Issue where scripts run through external URIs or in the background could lose their original paths or file extensions _[`issue #545`](http://issues.autojs6.com/545)_
* `Fix` Issue where Tasker could not invoke and run scripts correctly _[`issue #501`](http://issues.autojs6.com/501)_
* `Fix` Issue where script paths and click mappings could be incorrectly deleted during batch updates of home screen widgets
* `Fix` Issue where the file manager did not read scripts with a BOM using their actual encoding when running them directly
* `Fix` Issue where script projects without packaging information were not recognized correctly or did not run the entry specified by project.json
* `Fix` Issue where auto files launched through the file manager, scheduled tasks or external intents were executed as JavaScript scripts
* `Fix` Issue where the file manager could not show information for some APKs or lacked an entry for viewing their manifests
* `Fix` Issue where external URI imports could write outside the destination directory or overwrite files with the same name due to improper file name handling
* `Fix` Issue where the file manager could flicker or reset its scroll position when returning to the page or deleting recycle bin entries
* `Fix` Issue where the file manager could not distinguish TypeScript files from videos with the same extension, and some source files lacked a run action
* `Fix` Issue where file manager operation messages could appear repeatedly
* `Fix` Issue where list items such as those in the file manager could keep a gray background after opening another page and returning, on some touch screens
* `Fix` Issue where the count of "Running task" in the task list did not match the items shown, and UI scripts that had exited were still listed as running
* `Fix` Incorrect resolution of media and image file paths, and cache paths displayed in MediaInfo details
* `Fix` Issue where failed media detail queries could crash the app, and incorrect video metadata fields
* `Fix` Issue where version history did not follow file moves or renames, or was incorrectly associated with copies having identical content
* `Fix` Issue where the recycle bin and version history could not access internal storage correctly in multi-user or work profile environments
* `Fix` Issue where overwriting files during version history or recycle bin restoration could lose original content or leave incomplete files
* `Fix` Incorrect automatic renaming of directory names containing dots during recycle bin restoration
* `Fix` Missing retained versions in version history and incorrect display modes after switching operations
* `Fix` Issue where version history cleanup could delete new snapshots before their records were complete
* `Fix` Issue where failed recycle bin restorations were still reported as completed
* `Fix` Issue where restoring directories from the recycle bin could write malformed archive entries outside the destination directory
* `Fix` Issue where the code editor could lose the cursor, selection or unsaved content after page recreation
* `Fix` Issue where the content area of the code editor and log page could be obscured or compressed when showing the keyboard or input bar
* `Fix` The "More" menus in code editor dialogs having similar background and text colors when the editor theme and the app light/dark mode differed, making menu items hard to read
* `Fix` Incorrect saved state, cursor colors and hint language in the home page search field
* `Fix` Issue where handling abnormal multi-touch input on the home Documentation tab could crash the app
* `Fix` Missing transition animation when switching to the home Files tab
* `Fix` Clipped widgets, missing bottom buttons and overly small switch touch targets in the home drawer on narrow screens
* `Fix` Issue where drawer cleanup listeners accessed a released context and crashed the app when the home page closed or was recreated
* `Fix` Issue where the splash screen background did not cover the short-edge cutout area
* `Fix` Incorrect recent app package name and Activity information in the floating button menu _[`issue #529`](http://issues.autojs6.com/529)_
* `Fix` Issue where the floating button's "Run script" dialog could not be restored after minimization (tentative fix) _[`issue #500`](http://issues.autojs6.com/500)_
* `Fix` Issue where layout inspection could generate invalid statements for widget collections
* `Fix` Insufficient text/widget contrast in layout inspection floating windows and dialogs with some theme colors or in night mode
* `Fix` Issue where Material colors selected in the classic layout could not be found through "Locate current theme color" after changing layouts, and the Material colors page did not show the current color summary
* `Fix` Issue where the home Plugins tab and Plugin Center retained search filters after closing search or switching tabs
* `Fix` Issue where the Plugin Center incorrectly selected x86_64 packages on x86 devices
* `Fix` Issue where enabling a plugin could not continue after successful authorization
* `Fix` Issue where the Plugin Center could not parse dates with some abbreviated month names
* `Fix` Incorrect icon caching, night mode switching and pull-to-refresh states in the Plugin Center
* `Fix` Issue where refreshing the Plugin Center could lose installed plugins, show an empty list or reset the scroll position
* `Fix` Issue where the Plugin Center could install the wrong variant or show duplicate packaging templates and update prompts
* `Fix` Issue where the Plugin Center restored removed or renamed plugins from the cache even after a successful index refresh
* `Fix` Issue where a plugin details dialog opened immediately after a cold start flickered because list refreshes rebuilt the entire dialog
* `Fix` Issue where the Plugin Center kept plugins disabled after they became temporarily unreachable (such as during an update installation)
* `Fix` Issue where Shizuku permission request listeners were not properly unregistered after destruction
* `Fix` Issue where APK installation started in the background could not proceed to confirmation
* `Fix` Issue where cancelling APK packaging could incorrectly report a build failure
* `Fix` Incorrect signing certificate consistency checks for packaged APKs and signature recognition on Android 9-11
* `Fix` Issue where missing runtime components in packaged apps or the host could leave the shell native library missing or select the wrong ABI, crashing the app
* `Fix` Issue where APK packaging silently kept one of several same-named native libraries with different content (packaging now stops and lists their sources)
* `Fix` Issue where packaged UI scripts occasionally showed a blank page and exited on startup
* `Fix` Issue where files deleted from a project remained on the device or in cache directories after saving through the VSCode plugin _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `Fix` Issue where log processing could exhaust memory when receiving project archives from the VSCode plugin _[`issue #523`](http://issues.autojs6.com/523)_
* `Fix` Issue where stale VSCode plugin connections blocked log sending and prevented new connections from receiving logs _[`issue #385`](http://issues.autojs6.com/385)_
* `Fix` Issue where showing a dialog on a destroyed page after a VSCode plugin connection failure could throw exceptions
* `Fix` Issue where successfully connected addresses in client mode were not saved to history
* `Fix` Issue where update checks did not select packages according to the device's ABI priority
* `Fix` Issue where IntelliJ IDEA builds could fail to resolve module dependencies, match compilation tasks or locate the current APK output
* `Fix` Insufficient memory for Kotlin compilation during multi-variant builds
* `Fix` Incorrect SDK resolution and packaging template path resolution during Gradle builds
* `Fix` cXy/cYx ignoring absolute coordinate mode when false is specified, reversing landscape aspect ratios, and rejecting some calls after setting only one scale base
* `Fix` setScaleBases changing the other axis and consuming its one-time assignment when the second argument is invalid or a scale base has already been set
* `Fix` device.width/height and coordinate conversions reading zero dimensions when an activity is recreated concurrently with a script screen-size read
* `Fix` Node.js pinyin/pinyin4j converting only a small built-in dictionary; they now use the installed Pinyin plugins
* `Fix` Node.js console window and styling methods having no effect
* `Fix` Bun losing trailing logs when the synchronous result arrived before output callbacks at script completion
* `Fix` Screen capture consent ending prematurely when its caller is covered, system dialogs remaining after cancellation, and requested image dimensions being ignored
* `Improvement` Deferred initialization of noncritical components to improve app startup speed
* `Improvement` Permission dialogs no longer appear automatically at startup; when all-files access is missing, the home page shows a reminder with a direct authorization action
* `Improvement` Adapted the app and packaged apps for Android 17, with improved guidance for local network permission, Advanced Protection and background audio restrictions
* `Improvement` Added 16 KB memory page support for the app and packaged apps, with warnings about incompatible native libraries during plugin installation, loading and packaging
* `Improvement` Improved multilingual display of interfaces and exception messages
* `Improvement` Reduced package size by trimming dependencies, language resources and image resources
* `Improvement` Update checks obtain version and package information through the GitHub REST API, with local rendering of release notes
* `Improvement` Bundled Simplified Chinese and English release histories; other languages can be fetched online and cached (offline use prefers the cache and falls back to English when no cache exists)
* `Improvement` Moved offline project documentation into the Offline Docs plugin for independent installation and updates, with documentation source changes taking effect immediately
* `Improvement` Moved packaging templates into the APK Builder Template plugin, validating plugin signatures, host versions and template compatibility during packaging
* `Improvement` Pluginized functionality related to ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization
* `Improvement` Pluginized functionality related to OpenCC/Pinyin/Pinyin4j/MediaInfo
* `Improvement` Official plugins can follow the host's theme color, night mode and language settings
* `Improvement` Improved online installation and updates, variant selection, plugin settings, instructions and release history display in the Plugin Center
* `Improvement` Plugin Center list icons respond to taps/long presses according to installation status: installed plugins open system app details/their standalone launch page (with a message when no launch page exists), while uninstalled plugins open plugin details/start installation
* `Improvement` The home Plugins tab FAB menu prioritizes the installation wizard, official and third-party repositories, and Plugin Center, with URL/local file installation and management actions grouped under "More"
* `Improvement` Plugin Center supports two-level sorting with independent ascending/descending order at each level, defaulting to installed > disabled > not installed, with most recently updated first within each state
* `Improvement` Entries operated on in the Plugin Center or changed through plugin settings retain their positions while updating status; background refresh continues normally, and pull-to-refresh or changing sorting/filtering restores normal ordering
* `Improvement` Plugin Center supports automatic mounting of SDK plugins and available ABI detection
* `Improvement` Plugin Center supports automatically enabling freshly installed official plugins, retaining the existing enablement policy for external installations and when installing over an existing plugin
* `Improvement` Improved list display and refresh smoothness in the Plugin Center through caching and asynchronous loading, with recovery guidance when enabling plugins fails
* `Improvement` Long-pressing the floating button on the home Plugins tab opens the Plugin Center
* `Improvement` Plugin information dialogs separately show supported ABIs and the ABI of the installed package
* `Improvement` Cached Rhino runtime initialization data to speed up script startup, improved large-array printing performance and Java reflection compatibility on older Android versions
* `Improvement` Synced the Rhino engine with upstream, adding new.target support and improving scope, TypedArray and debugger compatibility
* `Improvement` runtime.loadJar supports loading JAR files through the DEX Compiler plugin, with compilation diagnostics and fallback on failure (experimental, disabled by default)
* `Improvement` Scheduled tasks, broadcast tasks, shortcuts and Tasker support TypeScript scripts (requires the TypeScript Engine plugin)
* `Improvement` pinyin supports per-call custom pronunciation dictionaries, with improved compare/compact methods
* `Improvement` pinyin4j supports multiple romanization schemes and complete pronunciation queries
* `Improvement` opencc supports conversion type queries, batch conversion and chained conversion
* `Improvement` ocr automatically selects available engines in ML Kit/Paddle/Rapid order, with explicit engine selection and restoration of automatic selection
* `Improvement` The ocr module reached through the host bridge (Node.js plugin and other plugin sessions) selects an enabled engine in ML Kit/Paddle/Rapid order when no engine is specified, instead of always requiring the ML Kit OCR plugin
* `Improvement` The host bridge accessibility module adds captureScreenImage, which turns an accessibility screenshot (falling back to screen capture when unavailable) into a session image handle that plugin sessions such as MCP Server use directly for barcode recognition and image or color search; plugin sessions may now hold twice their concurrent-call count of image handles instead of 4
* `Improvement` When model-facing plugin sessions such as MCP Server and 3-Stove Agent read an image through the bridge (image.readImage), images whose longest edge exceeds 4096 px are decoded at a sample size that is a power of 2 and the result carries scale plus the source dimensions, so large gallery photos cannot exhaust host memory; Node.js plugin scripts are unaffected
* `Improvement` Model-facing plugin sessions such as MCP Server and 3-Stove Agent that read images or media information by path through the bridge (image.readImage, mediainfo) are confined to the working directory, and writes (image.saveImage) stay inside it as well; when the request declares files.shared_read and AutoJs6 holds all-files access, shared storage becomes readable read-only (excluding Android/data and Android/obb), and files.list and files.stat can list shared storage under the same token; Node.js plugin scripts are unaffected
* `Improvement` The host bridge app module adds pluginCapabilities, which reports in real time the status of the plugin families OCR, barcode, Pinyin, Pinyin4j, MediaInfo, OpenCV and offline docs (available / not_installed / disabled / incompatible), the installed plugins' versions, enablement and authorization, the bridge methods they back and the Plugin Center entry, so plugin sessions such as MCP Server can show tools by installation state and point users to the plugin to install; the optional method list a plugin session receives when it opens is completed by the same logic for the families available at that time
* `Improvement` The default capability grant of MCP Server plugin sessions grows: the ocr, barcode, pinyin, pinyin4j and mediainfo module methods, image.readImage and the image / color search methods, accessibility.readScreenText / captureScreenImage and app.pluginCapabilities, with their permission tokens (including files.shared_read for read-only shared storage); these methods work only while the corresponding plugin is installed and enabled, image.saveImage and image.toBytes stay outside the grant, and the 3-Stove Agent grant is unchanged; agent.listScripts / readManifest / execRegistered with the agent and agent.exec tokens join as well, so MCP Server can list and run registered scripts
* `Improvement` images.save/saveImage/compress/compressToBytes support PNG quantization options (preserving transparency) and pixel/memory limits (requires the Image Quantization plugin)
* `Improvement` images.readPixels supports ImageWrapper arguments
* `Improvement` images.matchTemplate/findPointByImage support the scales option, with size, scale and region information in matching results
* `Improvement` images.matchFeatures supports image or path arguments and more matching options, with additional feature and result information
* `Improvement` Improved performance and accuracy of multi-point color searches, template matching and feature matching
* `Improvement` Screen capture supports retries when no frame is temporarily available, reducing the impact of capture resource release on concurrent image operations
* `Improvement` mediainfo supports snapshot/capabilities methods and media stream counts, with faster file detail queries
* `Improvement` Improved argument compatibility in KeyCode and support for sending keycode commands through Shizuku _[`issue #492`](http://issues.autojs6.com/492)_
* `Improvement` Accessibility service supports automatic enabling through Shizuku and synchronized start/stop states with the official companion plugin
* `Improvement` The source node in auto.registerEvent callbacks is now resolved on demand (must be read within the callback below Android 13)
* `Improvement` Accessibility window queries skip unavailable root nodes, with improved node recycling strategies across Android versions
* `Improvement` Improved resource recycling and retries after system rate limiting in automator.captureScreen, preventing screenshot requests from remaining unfinished for long periods
* `Improvement` Integer arguments in global and collection selectors are consistently rounded, with more stable multithreaded queries and timeout interruption
* `Improvement` Selectors and Flow waits support event-assisted polling, retrying early when accessibility events arrive (enabled through the eventAssistedPolling flag in auto.setFlags)
* `Improvement` Restored console.rawInput/input, for reading text or evaluating expressions through the console input bar
* `Improvement` Each console remembers its own time visibility and format settings; rerunning a script with the same name replaces its existing floating window
* `Improvement` media.playMusic supports foreground playback, audio focus and notification controls, with playback resource cleanup when the script ends
* `Improvement` Removed bundled copies of com.stardust compatibility classes; class names referenced with the com.stardust prefix now map to the same-named AutoJs6 implementations (218 classes fully mapped, 90 partially mapped, and the remaining 46 without counterparts no longer provided), making class-name usage such as new and instanceof consistent with runtime objects (see Project documentation > [AutoJs4 runtime](https://docs.autojs6.com/#/autojs4Runtime))
* `Improvement` Added sample code for 3-Block Compose, automation flows, console interaction and ebook operations, and improved screen OCR samples
* `Improvement` Unified action menu for the floating console, with resizing and settings for time, coloring, font size and transparency
* `Improvement` Unified code editor theme colors and menu layout, with improved fonts, read-only mode and text selection
* `Improvement` The code editor run button switches to a stop button after starting a script
* `Improvement` Improved code editor font download messages, go-to-line correction, and file encoding detection and saving
* `Improvement` Faster page display when opening files in the code editor
* `Improvement` File manager loads package information on demand, with improved display of large packages and manifest information
* `Improvement` Unified file preview and plugin action entries in the file manager, with incremental list refreshes that retain scroll position
* `Improvement` Version history avoids duplicate snapshots and preserves original content before overwrites; restoring recycle bin items to a new path also migrates their version history
* `Improvement` Improved recycle bin storage hints, recovery of damaged entries and error messages, with incremental list refreshes
* `Improvement` Layout hierarchy inspection supports attribute summaries, parent highlighting, horizontal scrolling and selecting parent widgets level by level
* `Improvement` Removed colored hierarchy lines from layout hierarchy inspection; expand/collapse indicators move to the left of widget text and distinguish expandable, expanded and leaf nodes
* `Improvement` Layout inspection node information dialogs mark multiple selections with the theme color; copy modes support "Always ask/Copy object selector/Copy chained selector" and persist across sessions
* `Improvement` Unified grouped layouts in accessibility service and connection managers, with expansion/collapse at each level and separate settings
* `Improvement` Task lists and script execution logs use abbreviated paths such as the working directory, while preserving actual entry file names and extensions
* `Improvement` Floating buttons and related windows follow the app language and night mode, with menu information loaded in the background
* `Improvement` Renamed the home drawer group "Connect to computer" to "Remote connections"
* `Improvement` Combined light and dark window backgrounds in the default color library into "Background colors", with light, dark, automatic following and automatic inversion options; pre-upgrade selections are preserved
* `Improvement` Theme settings support locating colors in custom libraries and the active intelligent color library; search now includes custom library colors and intelligent library groups
* `Improvement` APK installation dialogs are provided by the 3-Setup Installer plugin; when unavailable, the system installer is used with guidance to install the plugin; apks/xapk/apkm/apkz split-package containers require this plugin
* `Improvement` Added heartbeat detection and data length validation for VSCode plugin connections to prevent stale connections and malformed data from disrupting communication _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `Improvement` VSCode plugin file saving supports retaining relative directory structure within the working directory _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `Improvement` Improved QR code recognition for client mode connections and moved its entry to the address dialog menu
* `Improvement` Gradle builds support dynamically matching compatible Kotlin/KSP/AGP/R8 versions, with reduced startup memory usage and cleaned-up compilation warnings
* `Improvement` Moved Gradle platform compatibility data and update tasks to AutoJs6-Gradle-Platform-Versions, with cross-repository synchronization in Scrapers
* `Improvement` Added the useSystemId Boolean argument to device.getAndroidId, obtaining the system value for the Android user running the app through Shizuku/Root on Android 8.0+; permission or read failures throw exceptions, and older systems ignore this argument
* `Improvement` cX/cY/cXy/cYx support explicit auto mode, consistently validate coordinates and scale bases, and preserve large finite results beyond the Long range
* `Dependency` Modularized Android AXML
* `Dependency` Modularized Android ARSC Editor
* `Dependency` Modularized APK Signer
* `Dependency` Modularized APK Verifier
* `Dependency` Modularized ARSCLib version 1.3.8
* `Dependency` Modularized Image Quantization version 2.17.0
* `Dependency` Modularized OpenCV version 4.8.0
* `Dependency` Added juniversalchardet version 2.0.4
* `Dependency` Added AndroidX AsyncLayoutInflater version 1.1.0
* `Dependency` Added AndroidX Test Ext JUnit version 1.3.0
* `Dependency` Added ZXing Android Embedded version 4.3.0
* `Dependency` Added Android Hidden API Bypass version 6.1
* `Dependency` Added NDK version 28.2.13676358 and CMake version 3.22.1 for building the native components of privileged file operations
* `Dependency` Upgraded Android SDK version 36 -> 37
* `Dependency` Upgraded Gradle 9 version 9.4.0 -> 9.7.1
* `Dependency` Upgraded AndroidX Lifecycle version 2.6.2 -> 2.9.4
* `Dependency` Upgraded AndroidX SavedState version 1.2.1 -> 1.3.2
* `Dependency` Upgraded AndroidX Emoji2 version 1.3.0 -> 1.4.0
* `Dependency` Upgraded AndroidX Window version 1.0.0 -> 1.5.0
* `Dependency` Removed Android Annotations version 4.8.0
* `Dependency` Removed Android dx version 1.14
* `Dependency` Removed AndroidX AppCompat (Legacy) version 1.0.2
* `Dependency` Removed AndroidX MultiDex version 2.0.1
* `Dependency` Removed Apache Commons Lang3 version 3.18.0
* `Dependency` Removed APK Parser local module
* `Dependency` Removed Commons IO version 2.8.0
* `Dependency` Removed Flexmark Java HTML to Markdown version 0.64.8
* `Dependency` Removed GitHub API version 1.306
* `Dependency` Removed Glide KSP version 5.0.5
* `Dependency` Removed ICU4J version 77.1
* `Dependency` Removed Jackson DataBind version 2.13.4.2
* `Dependency` Removed Jaredrummler Android Device Names version 2.1.1
* `Dependency` Removed JavaMail for Android local library
* `Dependency` Removed JCIP Annotations version 1.0
* `Dependency` Removed Jieba Analysis version 1.0.3-SNAPSHOT (modified)
* `Dependency` Removed JUnit Jupiter version 5.13.4
* `Dependency` Removed Log4j version 1.2.17
* `Dependency` Removed Android Logging Log4j version 1.0.3
* `Dependency` Removed MLKit Barcode Scanning version 17.3.0
* `Dependency` Removed MLKit Text Recognition Chinese version 16.0.1
* `Dependency` Removed OpenCC version 1.2.2
* `Dependency` Removed Pinyin4j version 2.5.1
* `Dependency` Removed Rapid OCR local library
* `Dependency` Removed Retrofit2 Converter Gson version 3.0.0
* `Dependency` Removed Retrofit2 Kotlin Coroutines Adapter version 0.9.2
* `Dependency` Removed Spongy Castle Prov version 1.58.0.0
* `Dependency` Removed Tiny Sign version 0.9

# v6.7.0

###### 2026/03/14

* `Feature` Plugin Center feature, supporting plugin install/uninstall/update and related operations (entry: home drawer button/home tab)
* `Feature` Launcher shortcuts in the Settings page now include the "Plugins" option, for jumping to the Plugin Center page via a shortcut
* `Feature` Version History feature, supporting viewing/restoring history versions of editable files (entry: home drawer button/file manager menu/code editor menu)
* `Feature` "Multi-function Keyboard" > "Symbol Settings" menu item in the code editor, supporting symbol editing and creating/importing/exporting symbols by configuration level
* `Feature` Trash feature, supporting viewing/restoring deleted files/folders (entry: home drawer button)
* `Feature` Paddle OCR (PP-OCRv5) plugin, for optical character recognition
* `Feature` cvt module, for data unit conversion (see Project Docs > [Unit Conversion](https://docs.autojs6.com/#/cvt))
* `Feature` cvt.bytes method, for byte data unit conversion (see Project Docs > [Unit Conversion](https://docs.autojs6.com/#/cvt))
* `Feature` fmt module, for data formatting (see Project Docs > [Formatting](https://docs.autojs6.com/#/fmt))
* `Feature` fmt.bytes method, for byte data formatting (see Project Docs > [Formatting](https://docs.autojs6.com/#/fmt))
* `Feature` zip module, for file compression and extraction operations (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (see Project Docs > [Zip](https://docs.autojs6.com/#/zip))
* `Feature` mediainfo module, for viewing detailed information of media files (see Project Docs > [Media Info](https://docs.autojs6.com/#/mediainfo))
* `Feature` s13n.bytes method, for standardizing byte data (see Project Docs > [Standardization](https://docs.autojs6.com/#/s13n))
* `Feature` app.isDualInstalled method, for detecting whether a dual-installed app is installed (requires Shizuku or Root permission) _[`issue #450`](http://issues.autojs6.com/450)_
* `Feature` device.getSharedDeviceId method, for obtaining a unified shared device ID across apps _[`issue #455`](http://issues.autojs6.com/455)_
* `Feature` device.setPointerLocation and other Toggleable series methods, for setting or getting the pointer location system setting _[`issue #381`](http://issues.autojs6.com/381)_
* `Feature` dialogs.build method now supports textAllCaps/(positive/negative/neutral)TextAllCaps option parameters, for controlling whether button texts are all uppercase
* `Feature` images.loadAsync method, for asynchronously fetching network image resources _[`issue #327`](http://issues.autojs6.com/327)_
* `Feature` ui.getNavigationBarHeight method/navigationBarHeight property (getter), for getting navigation bar height _[`issue #456`](http://issues.autojs6.com/456)_
* `Feature` ui.getVisible(Status/Navigation)BarHeight method/visible(Status/Navigation)BarHeight property (getter), for obtaining the visible height of the status bar or navigation bar _[`issue #456`](http://issues.autojs6.com/456)_
* `Feature` http.put/del(ete)/head methods, for sending network requests with different HTTP methods
* `Feature` http.getAsync/postAsync/requestAsync and related methods, for asynchronously sending network requests _[`issue #423`](http://issues.autojs6.com/423)_
* `Feature` body objects returned by http module request-related methods now include stream/saveToFile/close and related methods _[`issue #452`](http://issues.autojs6.com/452)_
* `Feature` http module request-related methods now support cache control option parameters (cacheBody/bodyCacheThresholdBytes)
* `Feature` http module request-related methods now support insecure option parameters (isInsecure/insecure), for ignoring certificate-related exceptions _[`issue #417`](http://issues.autojs6.com/417)_
* `Feature` http module request-related methods now support the options.client option, for configuring OkHttpClient.Builder (such as followRedirects) _[`issue #454`](http://issues.autojs6.com/454)_
* `Feature` auto.state property (getter) and shizuku.state property (getter), for obtaining accessibility service status and Shizuku service status
* `Feature` UiObject#isShifted method, for detecting control position changes _[`issue #469`](http://issues.autojs6.com/469)_
* `Feature` UiObject#snapshot method, for capturing the current state of a control _[`issue #469`](http://issues.autojs6.com/469)_
* `Feature` Equivalent series selectors (UiSelector#id/text/...) and contains series selectors (UiSelector#xxxContains) now support regular expression parameters
* `Feature` Regular expression parameters of selectors now support flags (i, m, s, u)
* `Feature` runtime.(set/is)JavaPrimitiveWrap methods, for setting or getting Java primitive wrapping strategy _[`issue #435`](http://issues.autojs6.com/435)_
* `Feature` autojs.restart/exit methods, for restarting or exiting AutoJs6 and running a specified script after restart _[`issue #460`](http://issues.autojs6.com/460)_
* `Feature` engines.myEngine().getStartTime() method, for getting the script engine startup timestamp _[`issue #449`](http://issues.autojs6.com/449)_
* `Feature` crash global method, for testing the crash report page
* `Feature` structuredClone global method, for deep-cloning JavaScript objects (see [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `Feature` Regular expressions now support lookbehind assertion syntax _[`issue #464`](http://issues.autojs6.com/464)_
* `Feature` File manager now includes "Move to" and "Copy to" menu items, supporting operation abort and progress status display
* `Feature` Home drawer now includes the "Pointer Location" tool
* `Feature` Home drawer now includes the "All Files Access" switch
* `Feature` "Show UI from Background" switch in the home drawer (for Xiaomi/Vivo devices)
* `Feature` Settings page now includes the "Java Primitive Wrap" option _[`issue #435`](http://issues.autojs6.com/435)_
* `Feature` Settings page now includes the "Scheduled Task Engine" option, supporting AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `Feature` Settings page now includes the "App Launcher Icon" option, supporting adaptive icons/transparent background icons _[`issue #405`](http://issues.autojs6.com/405)_
* `Feature` Settings page now includes the "Restart Strategy" option, for configuring whether the home drawer restart button uses the fast restart strategy
* `Feature` Settings page now includes the "Screen Capture Permission Request Delay" option, for preventing the permission request window from blocking the screen
* `Feature` Packaged apps now support auto-start on boot, with a switch added on the configuration page
* `Feature` Packaged apps now support app shortcuts (log page/settings page)
* `Feature` Packaged app configuration page now includes undo/redo/save buttons
* `Feature` Packaged app configuration page now includes a profile mechanism, supporting profile creation/modification/deletion/import/export _[`issue #483`](http://issues.autojs6.com/483)_
* `Feature` Scrapers tool (run-scrapers.mjs), for automatically updating Gradle build data and README common data and templates
* `Feature` Gradle Wrapper switcher tool (gradle-wrapper-switcher), for switching local Gradle major versions (such as Gradle 8.x and Gradle 9.x)
* `Fix` Incorrect parameter count and type restrictions for the notice method _[`issue #462`](http://issues.autojs6.com/462)_
* `Fix` Issue where the ui.R property (getter) could not be used properly
* `Fix` Issue where exceptions raised in ui.post callback parameters could fail silently
* `Fix` Issue where global methods such as isJavaClass/isJavaPackage were invalid
* `Fix` Issue where the timeout parameter of timers.keepAlive did not work
* `Fix` Issue where floaty.window/rawWindow methods could not accept string parameters _[`issue #467`](http://issues.autojs6.com/467)_
* `Fix` Issue where floaty.rawWindow could display some controls incorrectly due to an incorrect context being passed
* `Fix` Issue where util.isString method was invalid
* `Fix` Issue where util.class[Name]/getClass[Name] could return incorrect results
* `Fix` Issue where util.java.array could not properly use type parameters such as Boolean
* `Fix` Issue where objects returned by threads.disposable() could be unexpectedly boxed when accessing stored data _[`issue #435`](http://issues.autojs6.com/435)_
* `Fix` Issue where style options of the floating log window could not reset properly when console.build was called multiple times
* `Fix` Issue where (title/content)BackgroundColor options of console.build could override transparency or tint options _[`issue #458`](http://issues.autojs6.com/458)_
* `Fix` Issue where in server mode, actions such as screen rotation or language switching that recreated the Activity caused the VSCode console to stop outputting logs _[`issue #385`](http://issues.autojs6.com/385)_
* `Fix` Issue where console/toast and related methods could lose trailing zeros when displaying Java Double values
* `Fix` Issue where console/toast and related methods could not display BigInt values
* `Fix` Issue where the toast method could cause memory leaks due to holding a strong reference to the Toast instance
* `Fix` Issue where some global objects could lose JavaScript prototype properties and methods
* `Fix` Issue where the this object could point incorrectly when JavaScript expressions were used as attribute values in XML syntax
* `Fix` Issue where the linkify option parameter of dialogs.build did not work
* `Fix` Issue where dialogs.build could break original data in object parameters
* `Fix` Incorrect internal frame rate calculation in the setMaxFps method of the canvas element control
* `Fix` Incorrect width calculation when images.concat performed vertical concatenation
* `Fix` Issue where canceling authorization in images.requestScreenCapture could crash the app
* `Fix` Issue where screenshots obtained by images.captureScreen could have incorrect orientation or black border padding _[`issue #337`](http://issues.autojs6.com/337)_
* `Fix` Issue where 1 could be incorrectly parsed as a percentage when some images-related methods used region parameters
* `Fix` Issue where the oneShot flag could fail when exceptions occurred in some images-related methods _[`issue #372`](http://issues.autojs6.com/372)_
* `Fix` Issue where some images-related methods could cause memory leaks _[`issue #372`](http://issues.autojs6.com/372)_
* `Fix` Issue where images.save and ImageWrapper#saveTo could not properly write files when the input path did not exist
* `Fix` Issue where ImageWrapper#recycle failed to recycle image resources
* `Fix` Issue where some overloaded ocr methods might not work properly
* `Fix` Issue where results obtained by ocr.detect might not match ocr.mode _[`issue #468`](http://issues.autojs6.com/468)_
* `Fix` Issue where ML Kit OCR could not initialize properly on some devices (tentative fix) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `Fix` Issue where accessibility events registered by auto.registerEvent could be mistakenly cleared by other scripts _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `Fix` Issue where UiObject#child could throw ArrayIndexOutOfBoundsException on Android 10 _[`issue #416`](http://issues.autojs6.com/416)_
* `Fix` Issue where built-in module method calls could run into inconsistent Rhino context problems
* `Fix` Issue where creating a large number of floating windows in scripts could leave residual windows after script exit
* `Fix` Issue where project.json configuration parameters might not be parsed properly when running a project
* `Fix` Issue where the excludedDirs parameter in project.json could cause configuration parsing failure during project packaging _[`issue #428`](http://issues.autojs6.com/428)_
* `Fix` Issue where packaged apps could not properly use Paddle OCR and Rapid OCR features
* `Fix` Issue where key values in packaged app configuration file project.json could be modified unexpectedly
* `Fix` Issue where checkbox icons could flicker when scrolling the "Required Permissions" list on the packaged app configuration page
* `Fix` Issue where packaging might not work properly on Android 7.x
* `Fix` Issue where original key names could be modified when saving script project configuration files
* `Fix` Issue where very large build version codes or build times in script project configuration files could crash the app
* `Fix` Issue where frequent fetching or rebuilding of ImageReader could crash the app due to no available frame in the buffer
* `Fix` Issue where the input event observer InputEventObserver could cause noticeable app startup lag
* `Fix` Issue where Shizuku user service processes did not terminate properly and accumulated _[`issue #474`](http://issues.autojs6.com/474)_
* `Fix` Issue where statistics on the Release History page could be displayed incompletely on some systems due to font differences
* `Fix` Issue where file manager function buttons could not properly receive click events on some devices (tentative fix) _[`issue #465`](http://issues.autojs6.com/465)_
* `Fix` Issue where the app could crash when pressing the back button or switching tabs after displaying file manager search results
* `Fix` Issue where the UI did not refresh automatically after deleting project folders in the file manager
* `Fix` Issue where blank lines in the code editor displayed box glyphs on some devices (tentative fix)
* `Fix` Issue where the soft keyboard could block text content in the code editor
* `Fix` Issue where code content could still be edited in read-only mode in the code editor
* `Fix` Issue where tapping the title area or some menu items in read-only mode in the code editor could crash the app
* `Fix` Issue where loading large files in the code editor could crash the app (tentative fix)
* `Fix` Abnormal initialization of function button states when the code editor automatically opened a new file
* `Fix` Issue where putting the app in background while editing a file in the code editor could crash the app
* `Fix` Issue where tapping the fx button in the code editor on Android 15+ could not show the quick panel for module functions (tentative fix)
* `Fix` Issue where ErrorDialogActivity could fail to start properly or disappear automatically in a short time _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `Fix` Incorrect accepted parameter types for the Canvas constructor _[`issue #402`](http://issues.autojs6.com/402)_
* `Fix` Issue where copying detailed information on the crash report page did not work
* `Fix` Issue where custom back navigation logic stopped working on Android 16+, causing abnormal back behavior
* `Fix` Issue where the app could crash when task panel list items changed frequently
* `Fix` Issue where navigation bar foreground color could behave abnormally with night mode when using "three-button" gesture navigation
* `Fix` Issue where the APK file type information dialog might fail to obtain app name and SDK information
* `Fix` Issue where in client mode, disconnecting and reconnecting shortly after connecting could lead to handshake timeout and make reconnection difficult
* `Fix` Issue where in client mode, actions such as screen rotation or language switching that recreated the Activity always caused AutoJs6 to reconnect
* `Fix` Issue where connecting to the VSCode plugin in multiple ways at the same time could multiply the number of printed logs
* `Fix` Issue where code generation on the layout analysis page could fail for collection controls (tentative fix) _[`issue #328`](http://issues.autojs6.com/328)_
* `Fix` Issue where the "Use 'Volume Down' Key to Control Recording" switch on the Settings page did not work _[`issue #480`](http://issues.autojs6.com/480)_
* `Fix` Issue where the Xiaomi device "Display over other apps" switch could jump to the wrong settings page
* `Fix` Issue where coordinate values in code files generated by script recording might not match actual screen pixel values _[`issue #480`](http://issues.autojs6.com/480)_
* `Fix` Issue where the first recorded action (such as tap or swipe) in code files generated by script recording was always ignored
* `Fix` Issue where background file operations from the floating button "Run Script" dialog could crash the app
* `Fix` Issue where the floating button state could reset when re-entering the home page after the home Activity lifecycle ended
* `Fix` Issue where after aborting update download, network packets could still continue downloading in the background (tentative fix)
* `Fix` Issue where the Rebuild Project task could not complete properly when isCleanup[Paddle/Rapid]Ocr option was enabled in build tools
* `Improvement` Pluginized Paddle OCR (PP-OCRv3) related features _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `Improvement` Enhanced compatibility of runtime.loadJar method (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `Improvement` Improved compatibility of ui.getStatusBarHeight method/statusBarHeight property (getter) to a certain extent
* `Improvement` console.log and related methods now support displaying detailed information when printing global objects (images, app, ocr, etc.)
* `Improvement` When printing huge data through console-related methods, some content is properly omitted according to upper limits to reduce print time
* `Improvement` Initial countdown value of the console floating window increased from 6 seconds to 9 seconds
* `Improvement` Internal implementation of the console floating window is now lock-free and queue-based, improving parameter setting efficiency and success rate
* `Improvement` Background color behavior-related APIs (alpha/tint/base color) of the console floating window now better align with Android design conventions _[`issue #458`](http://issues.autojs6.com/458)_
* `Improvement` dialogs.build method now supports native MaterialDialog.Builder option parameters
* `Improvement` dialogs.build method now supports loose string matching for animation/linkify option parameters
* `Improvement` Improved stability and consistency of screen orientation and size data obtained through the device.rotation/orientation/width/height properties
* `Improvement` Improved execution efficiency and compatibility of app.launchDual/startDualActivity and related methods
* `Improvement` android.graphics.Paint#setColor now properly parses color parameters such as ColorInt/ColorHex/ColorName
* `Improvement` Exception messages for argument types of built-in module-related methods now include type summary information
* `Improvement` Built-in modules (dayjs/cheerio/i18n) now use global lazy loading instead of script execution mode
* `Improvement` Optimized menu collapse timing when tapping menu items after expanding the floating button in the file manager
* `Improvement` File manager/task panel now supports showing file/task count statistics
* `Improvement` When saving files fails in the code editor, they are automatically saved as drafts and support Save As to a new file
* `Improvement` Improved smoothness to a certain extent when loading large files in the code editor
* `Improvement` Code editor "Find/Replace" now supports state persistence and real-time display of search count information
* `Improvement` When prompting to save in the code editor, it now ensures saving succeeds before exiting the editor to reduce save failure rate
* `Improvement` State of the code editor save button now better matches user expectations
* `Improvement` Code editor now supports long-press functionality for keys such as "-", "=", "(", "[", "{", "<"
* `Improvement` Packaged app build process dialog now includes an "Abort" button
* `Improvement` Improved display behavior of the packaged app build process dialog and added elapsed time statistics
* `Improvement` When installing packaged apps with new configuration, related configuration of the old app will be overwritten
* `Improvement` Packaged app configuration page now checks required permissions by default (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) _[`issue #397`](http://issues.autojs6.com/397)_
* `Improvement` Packaged app configuration page now supports reading/configuring more options (show logs on launch/show splash screen on launch/splash slug text/...)
* `Improvement` Packaged app settings page now includes a foreground service switch _[`issue #406`](http://issues.autojs6.com/406)_
* `Improvement` Key name conflict detection added when saving script project configuration files to prevent ambiguous keys
* `Improvement` Crash report page now supports pinch-to-zoom font resizing and adds common function buttons
* `Improvement` App launcher icons now support adaptive icon features _[`issue #405`](http://issues.autojs6.com/405)_
* `Improvement` Rhino engine now falls back to raw reflection types when generic signature parsing fails, improving reflective method availability on lower Android versions
* `Improvement` Divider lines added to switch-type items in the home drawer; tapping the title text area now shows detail dialogs and supports quick jump to system settings when needed
* `Improvement` When starting or restarting AutoJs6, tapping the title text area of "Client Mode" in the home drawer now supports aborting an ongoing connection attempt
* `Improvement` Client mode now supports connecting via IPv6 addresses and domain names
* `Improvement` Client mode now supports detection prompts for special-purpose IPv4 addresses (loopback/broadcast/multicast/reserved/...)
* `Improvement` Client mode now supports connection status display and management (correct address/abort connection)
* `Improvement` Server mode now supports showing the number of connected clients
* `Improvement` Reading notification permission now supports automatic reconnect mechanism ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) to improve stability of notification-listening related features
* `Improvement` Floating button now improves safety when launching Activities from background to avoid app crashes
* `Improvement` Floating button "More" dialog now uses asynchronous data loading for smoother display
* `Improvement` Floating button "Run Script" dialog now includes a "Home" menu item
* `Improvement` Floating button "Run Script" dialog now supports minimization and state restoration, and does its best to keep the window resident or restore it automatically
* `Improvement` Script recording and playback now support Shizuku permission (playback smoothness may be somewhat affected) _[`issue #320`](http://issues.autojs6.com/320)_
* `Improvement` Theme color settings page now uses a fast positioning method when locating theme colors to improve efficiency
* `Improvement` Replaced deprecated [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager) with [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) and [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
* `Improvement` Gradle build scripts now improve extraction efficiency for 7z files
* `Improvement` Gradle build scripts now support obtaining detailed Android Studio IDE version (such as "2025.1.4.7")
* `Improvement` Gradle build scripts now support automatically generating data required by VersionCodesList class to reduce script startup delay
* `Improvement` Use [Version Catalogs](https://developer.android.com/build/migrate-to-catalogs) to centrally manage Gradle dependency and plugin versions
* `Improvement` Modularized Gradle build scripts, moved shared build logic into build-logic, and abstracted it as convention plugins
* `Improvement` Use Gradle convention plugins to simplify loading logic of local AAR libraries
* `Improvement` Removed the "android.enableJetifier=true" Gradle option to adapt to Gradle 9
* `Improvement` Use [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) instead of sourceCompatibility/targetCompatibility to reduce build environment differences
* `Dependency` Modularized Expandable Layout version 1.6.0
* `Dependency` Modularized Expandable RecyclerView version 3.0.0-RC1
* `Dependency` Modularized RecyclerView Flexible Divider version 1.4.0
* `Dependency` Localized Root Shell version 1.6
* `Dependency` Added AndroidX Activity (KTX) version 1.12.2
* `Dependency` Added AndroidX Core (KTX) version 1.15.0
* `Dependency` Added AndroidX Work Runtime version 2.11.0
* `Dependency` Added AndroidX Interpolator version 1.0.0
* `Dependency` Added Room RxJava2 version 2.8.1
* `Dependency` Upgraded Android SDK version 35 -> 36
* `Dependency` Upgraded Gradle 8 version 8.14 -> 8.14.4
* `Dependency` Upgraded Gradle 9 version 8.14 -> 9.4.0
* `Dependency` Upgraded Rhino version 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT
* `Dependency` Upgraded Desugar JDK Libs version 2.0.4 -> 2.1.5
* `Dependency` Upgraded Android Material version 1.12.0 -> 1.13.0
* `Dependency` Upgraded AndroidX AppCompat version 1.7.0 -> 1.7.1
* `Dependency` Upgraded AndroidX Room version 2.7.1 -> 2.7.2
* `Dependency` Upgraded AndroidX WebKit version 1.13.0 -> 1.14.0
* `Dependency` Upgraded Material Progressbar version 1.4.2 -> 1.6.1
* `Dependency` Upgraded Apache Commons Lang3 version 3.16.0 -> 3.18.0
* `Dependency` Upgraded Retrofit2 Retrofit version 2.11.0 -> 2.12.0
* `Dependency` Upgraded Retrofit2 Converter Gson version 2.11.0 -> 2.12.0
* `Dependency` Upgraded Retrofit2 RxJava2 version 2.11.0 -> 2.12.0
* `Dependency` Upgraded Joda Time version 2.12.7 -> 2.14.0
* `Dependency` Upgraded Gson version 2.11.0 -> 2.13.1
* `Dependency` Upgraded ARSCLib version 1.3.1 -> 1.3.5
* `Dependency` Upgraded Jsoup version 1.19.1 -> 1.20.1
* `Dependency` Upgraded JUnit Jupiter version 5.10.3 -> 5.13.0
* `Dependency` Upgraded OpenCC version 1.2.0 -> 1.2.2
* `Dependency` Removed Android Support Annotation v7 version 28.0.0
* `Dependency` Removed Android Support AppCompat v7 version 28.0.0
* `Dependency` Removed Android Support RecyclerView v7 version 28.0.0

# v6.6.4

###### 2025/05/31

* `Hint` API change: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `Feature` util.dpToPx/spToPx/pxToDp/pxToSp methods for pixel unit conversion
* `Fix` Subtitles may be displayed incompletely when rotating the screen to landscape orientation
* `Fix` Some page content may be obscured by the side navigation bar when rotating to landscape
* `Fix` Incomplete status bar background tint area on some pages in Android 15 _[`issue #398`](http://issues.autojs6.com/398)_
* `Fix` Code editor might write files with low-confidence encoding causing decoding errors (attempted fix)
* `Improvement` Improved layout adaptability on the About app and developer page and removed unnecessary layout categories
* `Improvement` README.md build section added multiple methods to help locate the target settings page _[`issue #404`](http://issues.autojs6.com/404)_
* `Dependency` Added AndroidX ConstraintLayout version 2.2.1

# v6.6.3

###### 2025/05/27

* `Feature` Release history feature: view multi-language release changelogs and statistics
* `Feature` timers.keepAlive method (now global) for keeping the script alive
* `Feature` Event listeners such as engines.on('start/stop/error', callback) for global engine events
* `Feature` images.detectMultiColors method for multi-point color verification _[`issue #374`](http://issues.autojs6.com/374)_
* `Feature` images.matchFeatures / detectAndComputeFeatures methods: full-resolution image matching (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `Feature` images.compressToBytes method to compress an image and generate a byte array
* `Feature` images.downsample method, for image downsampling and returning a new ImageWrapper object
* `Feature` ui.keepScreenOn method to keep the device screen on while a UI page is focused
* `Feature` ui.root property (getter) to obtain the "window content root container" node of the UI layout
* `Feature` webview element now supports JsBridge-based web page layouts (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) [see Sample Code > Layout > Interactive HTML / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `Feature` Layout Inspector supports hiding controls (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `Fix` Online docs in the Home > Docs tab and Docs activity may be covered by the system navigation bar
* `Fix` Clicking Toolbar buttons on some pages could accidentally trigger the title click event
* `Fix` Blank lines in the code editor showed box glyphs on some devices (tentative fix)
* `Fix` Color-picker dialog in the theme-color settings page could stack infinitely
* `Fix` Status-bar background color on some Android 15 pages could not follow the theme color dynamically
* `Fix` Volume-up key failed to stop all scripts when the accessibility service was disabled
* `Fix` IME overlay issue when editing custom broadcast content in the Scheduled Tasks page
* `Fix` Controls inside webview elements could not activate the soft keyboard properly
* `Fix` APK file info dialog might fail to obtain app name and SDK info
* `Fix` File-manager sample code might fail to auto-load child directory contents when entering a project folder
* `Fix` Top content of UI mode on Android 15 was covered by the status bar
* `Fix` dialogs module could not use the customView property _[`issue #364`](http://issues.autojs6.com/364)_
* `Fix` Expression parameter of dialogs.input might not return the execution result
* `Fix` Using JavaAdapter led to a ClassLoader stack overflow _[`issue #376`](http://issues.autojs6.com/376)_
* `Fix` console.setContentTextColor caused log text color to lose its default value _[`issue #346`](http://issues.autojs6.com/346)_
* `Fix` console.setContentBackgroundColor could not accept color-name arguments _[`issue #384`](http://issues.autojs6.com/384)_
* `Fix` images.compress implementation corrected: now changes encoding quality instead of pixel down-sampling
* `Fix` images.resize method did not work properly
* `Fix` engines.all could trigger ConcurrentModificationException _[`issue #394`](http://issues.autojs6.com/394)_
* `Fix` Incorrect date formats in some languages within README.md
* `Fix` Gradle build could fail due to invalid library archive length _[`issue #389`](http://issues.autojs6.com/389)_
* `Improvement` Added gradient separators to the Layout Inspector menu for lightweight grouping
* `Improvement` project.json now supports a permissions option for script projects (by [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `Improvement` Automatically read and check declared permissions of installed apps when packaging a single file _[`issue #362`](http://issues.autojs6.com/362)_
* `Improvement` Expanded theme-color adaptation scope; supports more widget types
* `Improvement` Adaptive drawer width on the Home page for landscape / ultra-wide screens
* `Improvement` Added horizontal and small-screen layouts for About App & Developer pages
* `Improvement` Settings-page dialogs now offer a "Use default value" menu option
* `Improvement` Floating Action Button in File Manager auto-hides when tapping elsewhere
* `Improvement` Asynchronous loading significantly improves File Manager list scrolling smoothness
* `Improvement` Code formatter now supports operators such as `??`, `?.`, `??=`
* `Improvement` Code editor supports reading and writing files encoded in GB18030 / UTF-16 (LE/BE) / Shift_JIS, etc
* `Improvement` Code editor supports displaying detailed file information (path/encoding/line break format/total bytes and characters, etc.) _[`issue #395`](http://issues.autojs6.com/395)_
* `Improvement` Added operation-error prompts for intent actions (edit / view / install / send / play, etc.)
* `Improvement` webview element's url attribute supports relative paths
* `Improvement` ImageWrapper#saveTo path parameter supports relative paths
* `Improvement` images.save supports PNG file-size compression when using the quality parameter _[`issue #367`](http://issues.autojs6.com/367)_
* `Improvement` Clearing ignored update records and client-mode connection addresses is now supported
* `Improvement` Version-update information supports multi-language display (in sync with current UI language)
* `Improvement` Improved content and formatting of script exception messages in the console
* `Improvement` Sample code now supports resetting a folder to its initial contents
* `Improvement` Increased efficiency when checking APK signature information
* `Improvement` Optimized dialog display efficiency and information presentation for APK/media file type info
* `Improvement` Gradle build script now better adapts to newer versions _[`discussion #369`](http://discussions.autojs6.com/369)_
* `Dependency` Bundled Material Dialogs 0.9.6.0 (modularized)
* `Dependency` Bundled Material Date Time Picker 4.2.3 (modularized)
* `Dependency` Bundled libimagequant 2.17.0 (localized)
* `Dependency` Bundled libpng 1.6.49 (localized)
* `Dependency` Added ICU4J 77.1
* `Dependency` Added Jsoup 1.19.1
* `Dependency` Added Material Progressbar 1.4.2
* `Dependency` Added Flexmark Java HTML to Markdown 0.64.8
* `Dependency` Upgraded Gradle 8.14-rc-1 -> 8.14
* `Dependency` Upgraded AndroidX Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `Feature` ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor and related methods, for setting status bar and navigation bar styles
* `Feature` ui.statusBarHeight attribute (getter) for obtaining the status bar height _[`issue #357`](http://issues.autojs6.com/357)_
* `Feature` images.flip method for image flipping _[`issue #349`](http://issues.autojs6.com/349)_
* `Feature` Added "file extension" option in the settings page
* `Feature` Theme color settings support grouping, locating, searching, history and enhanced palette features
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
* `Fix` Issue where incorrect scope context binding when using XML syntax to use a JavaScript expression as an attribute value _[`issue #319`](http://issues.autojs6.com/319)_
* `Fix` Issue where exceptions in some method calls could not be caught by try/catch blocks _[`issue #345`](http://issues.autojs6.com/345)_
* `Fix` Issue where generating code on the layout analysis page could cause the app to crash _[`issue #288`](http://issues.autojs6.com/288)_
* `Fix` Issue where packaged applications could not properly use the shizuku module _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `Fix` Issue in the code editor where jumping to the end of a line might actually move the cursor to the beginning of the next line
* `Fix` Issue where rapid consecutive clicks on dialog-type items in the settings page could cause the app to crash
* `Improvement` Streamlined the APK file size for the packaged application template
* `Improvement` Enhanced app (and packaged apps) support for more permissions _[`issue #338`](http://issues.autojs6.com/338)_
* `Improvement` Packaging app configuration page now supports Pinyin library options
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
* `Dependency` Added Eclipse Paho Client Mqtt V3 version 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `Dependency` Upgraded Android SDK version from 34 to 35
* `Dependency` Upgraded Gradle from 8.12 to 8.14-rc-1
* `Dependency` Upgraded Rhino from 1.8.0-SNAPSHOT to 1.8.1-SNAPSHOT
* `Dependency` Upgraded AndroidX RecyclerView from 1.3.2 to 1.4.0
* `Dependency` Upgraded AndroidX Room from 2.6.1 to 2.7.0
* `Dependency` Upgraded AndroidX WebKit from 1.12.1 to 1.13.0
* `Dependency` Upgraded Pinyin4j from 2.5.0 to 2.5.1

# v6.6.1

###### 2025/01/01

* `Feature` Pinyin module for Chinese Pinyin conversion (Refer to project documentation > [Chinese Pinyin](https://docs.autojs6.com/#/pinyin))
* `Feature` Pinyin4j module for Chinese Pinyin conversion (Refer to project documentation > [Chinese Pinyin](https://docs.autojs6.com/#/pinyin4j))
* `Feature` Methods UiObject#isSimilar and UiObjectCollection#isSimilar for determining whether a control or a collection of controls is similar
* `Feature` Global method "currentComponent", used to obtain the name information of the currently active component
* `Fix` Issue where the project could not compile properly in certain environments due to a rollback to an earlier version
* `Fix` "Non-primitive value" exception that may occur when calling non-existent methods
* `Fix` Issue where script shortcuts could not be added properly on certain devices (tentative fix) _[`issue #221`](http://issues.autojs6.com/221)_
* `Fix` Incorrect parameter type restrictions for the automator.click/longClick methods _[`issue #275`](http://issues.autojs6.com/275)_
* `Fix` Issue where selectors did not support ConsString type parameters _[`issue #277`](http://issues.autojs6.com/277)_
* `Fix` Missing methods and properties on UiObjectCollection instances
* `Improvement` Packaging app configuration page now supports signing configuration, keystore management, and permission settings (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `Improvement` Automatically convert Simplified Chinese to Pinyin (including multi-tone characters) for generated package name suffixes when packaging single files
* `Improvement` Improved accuracy in identifying the current package name and activity name of the floating window (Priority: Shizuku > Root > A11Y)
* `Improvement` Improved accuracy in recognizing currentPackage and currentActivity (Priority: Shizuku > Root > A11Y)
* `Improvement` Restore the ability to select text content of individual entries in the log activity window via double-click or long press _[`issue #280`](http://issues.autojs6.com/280)_
* `Improvement` Recover as much critical information as possible for script projects when project.json is corrupted
* `Improvement` Support for negative arguments in the UiSelector#findOnce and UiSelector#find methods
* `Improvement` UI elements and className-related selectors now support more package name prefix omission forms (e.g., RecyclerView, Snackbar, etc.)
* `Improvement` Enhanced adaptability of app.startActivity/startDualActivity methods
* `Improvement` Synchronize with the latest upstream code of the Rhino engine and adapt it to the existing project
* `Dependency` Added Pinyin4j version 2.5.0
* `Dependency` Added Jieba Analysis version 1.0.3-SNAPSHOT (modified)
* `Dependency` Upgrade Gradle version from 8.11.1 to 8.12

# v6.6.0

###### 2024/12/02 - Built-in module rewrite, upgrade cautiously

* `Hint` The built-in modules are rewritten in Kotlin to enhance script execution efficiency, but iterative improvements are needed
* `Hint` The built-in init.js file is empty by default, allowing developers to extend built-in modules or mount external modules.
* `Feature` axios/cheerio modules, for network requests and HTML parsing (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `Feature` sqlite module, for SQLite database operations (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (see Project documentation > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Feature` MIME module for processing and parsing MIME type strings (See project documentation > [MIME](https://docs.autojs6.com/#/mime))
* `Feature` nanoid module for string ID generation (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `Feature` Sysprops module for obtaining runtime environment configuration data (See project documentation > [System Properties](https://docs.autojs6.com/#/sysprops))
* `Feature` OCR module supports [Rapid OCR](https://github.com/RapidAI/RapidOCR) engine
* `Feature` Layout analysis supports window switching (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` auto.clearCache method supports clearing control caches (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` threads.pool method, for creating and using thread pools (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.matchTemplate method supports the useTransparentMask option parameter, enabling transparent template matching (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.requestScreenCaptureAsync method for asynchronously requesting screenshot permissions in UI mode (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.requestScreenCapture method supports the isAsync option parameter, enabling asynchronous screen capture (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.on('screen_capture', callback) and other event listener methods support listening for screen capture availability events (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.stopScreenCapture method, for actively releasing resources associated with screen capture requests (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.psnr/mse/ssim/mssim/hist/ncc and images.getSimilarity methods for obtaining image similarity metrics
* `Feature` images.isGrayscale method for determining whether an image is grayscale
* `Feature` images.invert method for negative image conversion
* `Feature` s13n.point/time methods for standardizing point objects and duration objects (See project documentation > [Normalization](https://docs.autojs6.com/#/s13n))
* `Feature` console module methods related to gravity (layout gravity), touchThrough (click-through), backgroundTint (background tinting) and other settings (See project documentation > [Console](https://docs.autojs6.com/#/console))
* `Feature` Mathx.randomInt/randomFloat methods, for obtaining random integers or floating-point numbers within a specified range
* `Feature` app.launchDual/startDualActivity methods for handling dual app launch (Requires Shizuku or Root permissions) (Experimental)
* `Feature` app.kill method for forcefully stopping an app (Requires Shizuku or Root permissions)
* `Feature` floaty.getClip method, for obtaining clipboard content by focusing a floating window
* `Fix` Memory leak in View Binding of Fragment subclasses (e.g., [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48))
* `Fix` Instance memory leak in classes such as [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21)
* `Fix` Issue causing app crash when requesting screenshot permissions on Android 14 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `Fix` Issue causing app crash when starting foreground service on Android 14
* `Fix` Issue with run button in code editor not lighting up properly on Android 14
* `Fix` FAILED ASSERTION exception in code editor debug function
* `Fix` Issue with unable to debug again properly after closing editor during code editor debug process
* `Fix` Issue with potentially omitting end characters when jumping to line end in code editor
* `Fix` App may not run properly after packaging due to missing necessary library files _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `Fix` Issue with packaged app unable to use opencc module properly
* `Fix` App crash when editing project due to missing specified icon resources _[`issue #203`](http://issues.autojs6.com/203)_
* `Fix` Unable to use parameters properly to obtain screenshot resources of specified screen orientation when requesting screenshot permissions
* `Fix` Issue with some devices unable to add script shortcuts properly (Trial fix) _[`issue #221`](http://issues.autojs6.com/221)_
* `Fix` Cumulative request sending delay issue with methods related to sending requests in http module _[`issue #192`](http://issues.autojs6.com/192)_
* `Fix` Shizuku service may not work properly before AutoJs6 enters the main activity page (Trial fix) _[`issue #255`](http://issues.autojs6.com/255)_
* `Fix` random(min, max) method may have out-of-bounds results
* `Fix` Issue where result type parameter of pickup methods cannot be properly passed empty arrays
* `Fix` Issue where control rectangle obtained by UiObject#bounds() may be inadvertently modified, breaking its immutability
* `Fix` Access service auto-start may encounter abnormal service issues (Trial fix)
* `Fix` Issue with text/button/input elements where text containing half-width double quotes cannot be parsed properly
* `Fix` Issue with text/textswitcher elements where autoLink attribute functionality fails
* `Fix` Issue with different scripts erroneously sharing the same ScriptRuntime object
* `Fix` Issue where the global variables HEIGHT and WIDTH lost their dynamic getter properties
* `Fix` Issue with potential high-latency startup caused by RootShell loading on script startup
* `Fix` Issue with floating console window background color setting leading to loss of rectangular rounding style
* `Fix` Issue with flickering screen when starting log activity page on main activity page
* `Fix` Issue with triggering ViewPager switch when swiping left or right on WebView control on homepage document page
* `Fix` Issue with file manager unable to recognize file extensions containing uppercase letters
* `Fix` File manager may not automatically recognize project when first entering project directory
* `Fix` Issue with file manager page unable to refresh automatically after deleting folder
* `Fix` Issue with file manager sorting files and folders where ASCII initial letter names are put back
* `Improvement` Improved tap hint experience for the "Unavailable ABI" control on the packaging app configuration page
* `Improvement` Package single file support auto-fill appropriate standardized package name and invalid character filter prompt
* `Improvement` Package single file support automatically sets icon and auto-increments version number and version name based on installed same package name application
* `Improvement` Package configuration file supports abis/libs option to specify default included ABI architecture and libraries
* `Improvement` Support relevant message prompts when abis/libs options of package configuration file are invalid or unavailable
* `Improvement` Supports using Shizuku to control 'Pointer Location' display switch
* `Improvement` Supports using Shizuku to control 'Projection Media' and 'Modify Secure Settings' permission switches
* `Improvement` automator.gestureAsync/gesturesAsync supports callback function parameters
* `Improvement` tasks module uses synchronous way for database operations to avoid potential data access inconsistencies
* `Improvement` Script execution mode supports pipeline symbol separation mode parameters (e.g., starting with `"ui|auto";`)
* `Improvement` Script execution mode supports single quotes and backticks and allows omitting semicolons (e.g., starting with `'ui';` or `'ui'`)
* `Improvement` Script execution mode supports quick import of built-in extension modules such as axios, cheerio, and dayjs (e.g., starting with `"axios";`)
* `Improvement` Script execution mode supports x or jsox mode parameters for quick enabling of JavaScript built-in object extension modules (e.g., starting with `"x";`)
* `Improvement` img element src and path attributes support local relative paths (e.g., `<img src="a.png" />`)
* `Improvement` Code editor supports intelligently determining insertion location when importing Java classes and package names
* `Improvement` images module supports using paths directly as image parameters
* `Improvement` importPackage supports string parameters
* `Improvement` In client mode, the IP address supports smart recognition when importing from the clipboard and also supports intelligent conversion of space-key input
* `Improvement` File manager supports default prefix selection when creating new files and automatically generates appropriate numeric suffix
* `Improvement` File manager specifically informs on exception message when running project _[`issue #268`](http://issues.autojs6.com/268)_
* `Improvement` File manager supports more types and displays corresponding icon symbols (supports over 800 file types)
* `Improvement` Editable file types (jpg/doc/pdf/...) in file manager have added edit buttons
* `Improvement` APK files in file manager support viewing basic information, Manifest information, and permissions list
* `Improvement` Audio/video media files in file manager support viewing basic information and MediaInfo information
* `Improvement` Excluded LeakCanary from official releases to reduce package size
* `Improvement` All English comments in project source code are accompanied by Simplified Chinese translations to enhance readability
* `Improvement` README and CHANGELOG support multi-language (Automatically generated by script)
* `Improvement` Enhance Gradle build script's version adaptability
* `Dependency` Include MIME Util version 2.3.1
* `Dependency` Include Toaster version 12.6
* `Dependency` Include EasyWindow (for Toaster) version 10.3
* `Dependency` Upgrade Gradle version from 8.5 -> 8.11.1
* `Dependency` Upgrade Rhino version 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT
* `Dependency` Upgrade Android Material version 1.10.0 -> 1.12.0
* `Dependency` Upgrade AndroidX Annotation version 1.7.0 -> 1.9.1
* `Dependency` Upgrade AndroidX AppCompat version 1.6.1 -> 1.7.0
* `Dependency` Upgrade AndroidX WebKit version 1.8.0 -> 1.12.1
* `Dependency` Upgrade Apache Commons Lang3 version 3.13.0 -> 3.16.0
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
* `Dependency` Upgrade JUnit Jupiter version 5.10.0 -> 5.10.3
* `Dependency` Downgrade OkHttp3 version 5.0.0-alpha.11 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `Feature` opencc module, for conversion between Simplified and Traditional Chinese and regional vocabulary (see Project documentation > [Chinese conversion](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `Feature` UiSelector adds [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) and [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) methods _[`issue #115`](http://issues.autojs6.com/115)_
* `Feature` Packaging app configuration page now supports filtering for ABI and libraries (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Fix` Abnormally large file size issue in packaged applications (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Fix` Issue where packaged applications cannot display and print some exception messages
* `Fix` Fixed an issue where a blank app icon could appear after selecting an app icon on the packaging app configuration page
* `Fix` Issue where packaged apps including the ML Kit OCR library could encounter a context-not-initialized exception
* `Fix` ocr.recognizeText/detect methods not working
* `Fix` Mismatch between displayed language and application setting language for some texts (such as log page)
* `Fix` Text overflow issue in home page drawer switch items for some languages
* `Fix` Issue where accessibility service automatically closes immediately after being enabled on some devices without any prompt messages _[`issue #181`](http://issues.autojs6.com/181)_
* `Fix` Issue where enabling accessibility service on some devices might cause application crash due to physical keys (attempted fix) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Fix` Issue with pickup functionality after restarting accessibility service using auto(true) (attempted fix) _[`issue #184`](http://issues.autojs6.com/184)_
* `Fix` Issue where accessibility service cannot be used on Android 7.x
* `Fix` Issue where creating floating window in floaty module might cause application crash when dragging (attempted fix)
* `Fix` Issue where app.startActivity cannot use shorthand parameters _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Fix` Issue where code throws exception when imported class name conflicts with global variable using importClass _[`issue #185`](http://issues.autojs6.com/185)_
* `Fix` Issue where runtime.loadJar/loadDex methods might not work properly on Android 14 (attempted fix)
* `Fix` Issue where 'Layout Bounds Analysis' and 'Layout Hierarchy Analysis' are unavailable in Android system quick settings panel _[`issue #193`](http://issues.autojs6.com/193)_
* `Fix` Issue where auto-update check might cause application [ANR](https://developer.android.com/topic/performance/vitals/anr) (attempted fix) _[`issue #186`](http://issues.autojs6.com/186)_
* `Fix` Issue where file manager cannot return to workspace path page after clicking 'Up' button in example code folder _[`issue #129`](http://issues.autojs6.com/129)_
* `Fix` Issue where replace button is not shown when using replace function in code editor
* `Fix` Issue where long-press delete in code editor might cause application crash (attempted fix)
* `Fix` Issue where fx button in code editor cannot show module function quick panel
* `Fix` Issue where module function quick panel button function name might overflow in code editor
* `Improvement` The code editor module's quick function panel adapts to night mode
* `Improvement` The startup page of the packaged application is adapted to night mode, and the layout of application icons is adjusted
* `Improvement` Packaging app configuration page now supports using the soft keyboard ENTER key to move the cursor focus
* `Improvement` Packaging app configuration page now supports tapping ABI headings and library headings to toggle select-all state
* `Improvement` Packaging app configuration page now uses smarter default ABI selection and adds guidance hints for unavailable options
* `Improvement` The file manager's folder right-click menu adds a packaging application option
* `Improvement` The file manager adjusts the display of menu items based on the type and characteristics of files and folders
* `Improvement` When accessibility services are enabled but malfunction, an abnormal state is reflected in the drawer switch of the AutoJs6 homepage
* `Improvement` The console includes detailed stack information when printing error messages.
* `Dependency` Added ARSCLib version 1.2.4
* `Dependency` Added Flexbox version 3.0.0
* `Dependency` Added Android OpenCC version 1.2.0
* `Dependency` Upgrade Gradle version from 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `Feature` dialogs.build method supports the inputSingleLine option parameter
* `Feature` console.setTouchable method, for setting whether the floating console window accepts touch input _[`issue #122`](http://issues.autojs6.com/122)_
* `Fix` Issue where some methods in ocr module could not recognize region parameters _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
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
* `Improvement` http module's response object can reuse body.string and body.bytes() methods
* `Improvement` Added GitHub Actions support for automatic packaging (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Improvement` Build tools adapt to Temurin platform
* `Dependency` Upgraded Gradle version from 8.4-rc-3 -> 8.4
* `Dependency` Upgraded Android dx version from 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `Feature` ocr module supports Paddle Lite engine (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `Feature` Packaging function supports internal and external plugin packaging methods (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `Feature` WebSocket module, for WebSocket communication (see Project documentation > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `Feature` barcode/qrcode modules, for barcode and QR code recognition (see Project documentation > [Barcode](https://docs.autojs6.com/#/barcode) / [QR code](https://docs.autojs6.com/#/qrcode))
* `Feature` shizuku module (Refer to project documentation > [Shizuku](https://docs.autojs6.com/#/shizuku)) and permission switch in home page drawer
* `Feature` device.rotation/orientation and related properties, for obtaining screen orientation
* `Feature` Support for static property access of internal Java classes
* `Feature` Support for selecting and switching application language in Android system settings page (Android 13 and above)
* `Feature` [App shortcuts](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn), added through Settings or activated by long-pressing the app icon, for opening documentation, settings and other pages
* `Fix` Re-merged some PRs (by [aiselp](https://github.com/aiselp)) to solve the issue where some scripts could not end normally _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Fix` Issue where packaged applications could not use new APIs in AutoJs6 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `Fix` Styling anomaly in packaged applications under system night mode
* `Fix` Issue where file extension information was lost when saving files locally with VSCode plugin
* `Fix` Issue where running project with coroutine feature caused uncaught exceptions leading to application crash
* `Fix` Issue where floating button could not record its position state information when restarting or exiting application
* `Fix` Issue where floating button might overflow the screen when screen rotated to landscape orientation _[`issue #90`](http://issues.autojs6.com/90)_
* `Fix` Issue where floating button's coordinates and screen edge directions could not be restored when screen rotated multiple times
* `Fix` Issue where updated device configuration information could not be retrieved when device screen orientation changed _[`issue #153`](http://issues.autojs6.com/153)_
* `Fix` Issue where Toolbar title font size was too small when screen rotated to landscape orientation
* `Fix` Issue where home page tabs layout was too crowded when screen rotated to landscape orientation
* `Fix` Issue where message floating frame might miss or repeatedly display in some devices
* `Fix` Issue where message floating frame might be obstructed when multiple scripts are running simultaneously _[`issue #67`](http://issues.autojs6.com/67)_
* `Fix` Issue where clicking layout while analyzing layout with broadcast could not pop up menu and caused application crash
* `Fix` Issue where WebSocket instances created from the second time onwards could not normally trigger listeners
* `Fix` Reverted importPackage's global redirection method to avoid package import anomalies in some scopes _[`issue #88`](http://issues.autojs6.com/88)_
* `Fix` Issue where using copy or export functions in log activity page might cause application crash
* `Improvement` Renamed export function to send function and re-implemented meaningful export function in log activity page
* `Improvement` The log page send function automatically truncates an excessive number of entries and displays a notice
* `Improvement` ocr module is compatible with both ML Kit and Paddle Lite engines (Refer to project documentation > [Optical Character Recognition](https://docs.autojs6.com/#/ocr?id=p-mode))
* `Improvement` Increased the success rate of auto-starting accessibility service
* `Improvement` Migrated Kotlin annotation processor from kapt to KSP
* `Improvement` Build tools support IntelliJ IDEA EAP versions
* `Improvement` Build tools adapt to Java release versions as much as possible to avoid 'invalid release version' issues
* `Improvement` Build tools optimize IDE and related plugin downgrade logic and add version prediction capability
* `Improvement` Adapted to VSCode plugin 1.0.7
* `Dependency` Added Rikka Shizuku version 13.1.5
* `Dependency` Added MLKit Barcode Scanning version 17.2.0
* `Dependency` Upgraded Android SDK version from 33 -> 34
* `Dependency` Upgraded OpenCV version from 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependency` Upgraded Gradle version from 8.3-rc-1 -> 8.4-rc-3
* `Dependency` Upgraded Apache Commons Lang3 version from 3.12.0 -> 3.13.0
* `Dependency` Upgraded Glide version from 4.15.1 -> 4.16.0
* `Dependency` Upgraded Android Analytics version from 14.3.0 -> 14.4.0
* `Dependency` Upgraded AndroidX WebKit version from 1.7.0 -> 1.8.0
* `Dependency` Upgraded AndroidX Preference version from 1.2.0 -> 1.2.1
* `Dependency` Upgraded AndroidX Annotation version from 1.6.0 -> 1.7.0
* `Dependency` Upgraded AndroidX RecyclerView version from 1.3.0 -> 1.3.2
* `Dependency` Upgraded Android Material version from 1.9.0 -> 1.10.0
* `Dependency` Upgraded AndroidX AppCompat version from 1.4.2 -> 1.6.1
* `Dependency` Upgraded Commons IO version from 2.8.0 -> 2.14.0
* `Dependency` Upgraded Jackson DataBind version from 2.13.3 -> 2.14.3
* `Dependency` Removed Zeugma Solutions LocaleHelper version 1.5.1

# v6.3.3

###### 2023/07/21

* `Feature` Code commenting feature in code editor (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Feature` auto.stateListener property, for listening to accessibility service connection state (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Feature` UiObject#nextSibling/lastChild/offset and related methods, for widget navigation (see Project documentation > [Widget nodes](https://docs.autojs6.com/#/uiObjectType))
* `Fix` Issue where VSCode plugin could not parse data when script character total length exceeded four-decimal digits _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `Fix` Issue where VSCode plugin could not save file properly _[`issue #92`](http://issues.autojs6.com/92)_ _[`issue #94`](http://issues.autojs6.com/94)_
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
* `Improvement` Added level parameter support to UiObject#parent method (Refer to project documentation > [UiObject](https://docs.autojs6.com/#/uiObjectType))
* `Dependency` Upgraded Gradle version from 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `Feature` crypto module, for data encryption and decryption (see Project documentation > [Crypto](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
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
* `Improvement` Display target IP address in home page drawer subtitle during Client Mode connection
* `Improvement` Added prompt for invalid input when entering target address in Client Mode
* `Improvement` Supported using enter key on soft keyboard to establish connection in Client Mode
* `Improvement` Kept Server Mode active after being enabled (unless manually closed or app process ended) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `Improvement` Implemented bidirectional version detection and prompt abnormal results between AutoJs6 and VSCode plugin _[`issue #89`](http://issues.autojs6.com/89)_
* `Improvement` Supported cheerio module (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `Improvement` JsWebSocket instance supports rebuild method to recreate instance and establish connection _[`issue #69`](http://issues.autojs6.com/69)_
* `Improvement` base64 module supports number array and Java byte array as main parameters for encoding and decoding
* `Improvement` Added support for JavaMail for Android _[`issue #71`](http://issues.autojs6.com/71)_
* `Improvement` Used Blob data type when fetching version update information to enhance adaptability in proxy-free network environments
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
* `Feature` web.newWebSocket method for creating WebSocket instance (Refer to project documentation > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
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
* `Dependency` Upgraded AndroidX WebKit version from 1.6.1 -> 1.7.0
* `Dependency` Upgraded OkHttp3 version from 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `Dependency` Upgraded MLKit Text Recognition Chinese version from 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `Feature` ocr module, for optical character recognition (see Project documentation > [Optical character recognition](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Feature` notice module, for sending notifications (see Project documentation > [Notifications](https://docs.autojs6.com/#/notice))
* `Feature` s13n module, for data standardization (see Project documentation > [Standardization](https://docs.autojs6.com/#/s13n))
* `Feature` Color class, for creating and manipulating color objects (see Project documentation > [Color class](https://docs.autojs6.com/#/colorType))
* `Feature` colors.setPaintColor method, for setting android.graphics.Paint colors (see Project documentation > [Colors](https://docs.autojs6.com/#/color?id=m-setpaintcolor))
* `Feature` Keep the screen on functionality and setting options while in the foreground
* `Feature` Independent documentation launcher, for reading app documentation (can be hidden or shown in Settings)
* `Fix` Abnormal function of colors.toString method
* `Fix` Color constants assignment error in colors.material color space
* `Fix` Abnormal function of app.openUrl method automatically adding protocol prefix
* `Fix` Abnormal behavior of app.viewFile/editFile when the parameter corresponding file does not exist
* `Fix` Callback function of pickup method cannot be called
* `Fix` Selectors bounds/boundsInside/boundsContains cannot normally filter narrow empty rectangles (e.g., inverted boundary rectangles) _[`issue #49`](http://issues.autojs6.com/49)_
* `Fix` Higher probability of accessibility services encountering service exceptions (tentative fix)
* `Fix` The bounds attribute value of control information displayed by layout analysis is replaced by a comma symbol
* `Fix` Issue causing app crash when clicking or long pressing the home document tab after changing theme or language
* `Fix` Text editor jitter issue when zooming font size with two fingers
* `Fix` Issue with some dependency sources unable to be downloaded in build scripts (now fully integrated) _[`issue #40`](http://issues.autojs6.com/40)_
* `Fix` Issue with Tasker unable to add AutoJs6 Action Plugin (tentative fix) _[`issue #41`](http://issues.autojs6.com/41)_
* `Fix` Issue with ButterKnife annotations unable to parse resource ID when compiling projects with higher version JDKs _[`issue #48`](http://issues.autojs6.com/48)_
* `Fix` Issue with images.medianBlur size parameter usage not matching documentation
* `Fix` Issue with engines module losing the period symbol between file name and extension when displaying full script name
* `Fix` Potential computational error in the weighted RGB distance detection algorithm internal implementation (tentative fix)
* `Fix` Issue with console module floating window related methods not usable before the show method
* `Fix` Issue with console.setSize and other methods possibly not taking effect _[`issue #50`](http://issues.autojs6.com/50)_
* `Fix` Issue with UI mode date picker minDate and maxDate attributes unable to correctly parse date format
* `Fix` Issue with switching to the home "Task" tab page quickly after running the script displaying two identical running tasks
* `Fix` Issue with file management page state possibly being reset when returning from other pages _[`issue #52`](http://issues.autojs6.com/52)_
* `Fix` Issue with file management page sort state not matching icon display state
* `Improvement` Added file and folder modification time display on the file management page
* `Improvement` File management page sort type supports status memory
* `Improvement` README.md added sections for project build and script development assistance _[`issue #33`](http://issues.autojs6.com/33)_
* `Improvement` Support active downgrade (fallback) of JDK version in Gradle build script
* `Improvement` Support for Gradle build script to automatically select appropriate build tool versions based on platform type and version (to some extent)
* `Improvement` Region option parameter of images module related methods supports more input methods (refer to project documentation > [Omni Types](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Improvement` Added pref/homepage/docs/about etc. support for shorthand parameters in app.startActivity
* `Improvement` Mounted global methods of web module to the module itself to enhance availability (refer to project documentation > [World Wide Web](https://docs.autojs6.com/#/web))
* `Improvement` Default implementation of some commonly used WebView setting options in web.newInjectableWebView method
* `Improvement` Added various conversion methods, utility methods, more static constants and color names that can be directly used as parameters in colors module
* `Improvement` Added various console floating window style configuration methods in console module and support for unified window style configuration by build builder
* `Improvement` Support dragging title area to move window position for console floating window
* `Improvement` Support automatic delayed closing after script ends for console floating window
* `Improvement` Support adjusting font size with pinch-to-zoom for both console floating window and its Activity window
* `Improvement` Support timeout option parameter for http module related methods
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
* `Dependency` Upgraded AndroidX WebKit version 1.5.0 -> 1.6.1
* `Dependency` Upgraded AndroidX RecyclerView version 1.2.1 -> 1.3.0
* `Dependency` Upgraded Zip4j version 2.11.2 -> 2.11.5
* `Dependency` Upgraded JUnit Jupiter version 5.9.2 -> 5.9.3
* `Dependency` Upgraded AndroidX Annotation version 1.5.0 -> 1.6.0
* `Dependency` Upgraded Jackson DataBind version 2.14.1 -> 2.14.2
* `Dependency` Upgraded Desugar JDK Libs version 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `Feature` Redesign and rewrite of project documentation (partially completed)
* `Feature` Multilingual support for Spanish/French/Russian/Arabic/Japanese/Korean/English/Traditional Chinese and other languages
* `Feature` Added path selection/history/default value intelligent suggestion functions to the work path setting options
* `Feature` File manager supports jumping to the parent directory of any directory (up to "Internal Storage")
* `Feature` File manager supports setting any directory as the work path shortcut
* `Feature` Ignoring version updates and managing ignored update records
* `Feature` Text editor supports zooming font size with two fingers
* `Feature` UiSelector#idHex selector, for filtering widgets by hexadecimal IDs (see Project documentation > [Selectors](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` UiSelector#action selector, for filtering widgets by supported actions (see Project documentation > [Selectors](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` Match selectors such as UiSelector#xxxMatch, for regular expression matching (see Project documentation > [Selectors](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` UiSelector#pickup selector, for obtaining widgets using combined conditions (see Project documentation > [Selectors](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `Feature` UiObject#detect method, for widget detection (see Project documentation > [Widget nodes](https://docs.autojs6.com/#/uiObjectType))
* `Feature` UiObject#compass method, for widget navigation (see Project documentation > [Widget nodes](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `Feature` wait global method, for conditional waiting (see Project documentation > [Global object](https://docs.autojs6.com/#/global?id=m-wait))
* `Feature` cX/cY/cYx global methods, for coordinate scaling (see Project documentation > [Global object](https://docs.autojs6.com/#/global?id=m-cx))
* `Feature` App global type, for enumerating applications (see Project documentation > [App enumeration class](https://docs.autojs6.com/#/appType))
* `Feature` i18n module, for JavaScript multilingual processing (based on banana-i18n) (see Project documentation > Internationalization)
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
* `Fix` Issue with navigation bar buttons hard to identify on Android 7.x system when night mode is off
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
* `Fix` Possible issue with request not closing in http.post and other methods
* `Fix` Issue with loss of alpha channel information in the result when colors.toString method alpha channel is 0
* `Improvement` Redirected public classes of Auto.js 4.x version to achieve downward compatibility as far as possible (to some extent)
* `Improvement` Merged all project modules to avoid possible circular references and other issues (temporarily removed inrt module)
* `Improvement` Migrated Gradle build configuration from Groovy to KTS
* `Improvement` Added multi-language support to Rhino exception messages
* `Improvement` Home drawer permission switch prompts message only when enabled
* `Improvement` Home drawer layout closely aligned under the status bar to avoid the top color strip's low compatibility
* `Improvement` Displayed IP address of corresponding device in home drawer after client and server establish a connection
* `Improvement` Check for updates/download updates/update prompt functions compatible with Android 7.x system
* `Improvement` Redesigned settings page (migrated to AndroidX)
* `Improvement` Settings page supports long press on settings options to get detailed information
* `Improvement` Added "Follow System" setting option in night mode (Android 9 and above)
* `Improvement` Application startup screen adapted for night mode
* `Improvement` Added more Material Design Color options to theme color
* `Improvement` Text color of prompt text in home search box adapted for night mode
* `Improvement` Adapted dialog box/text/Fab/AppBar/list items and other components for night mode
* `Improvement` Added numeric identifier to application icon to enhance the user experience of coexistence with multiple open-source versions
* `Improvement` Appropriately lightweighted and theme color compatible icons for list items such as file manager/task panel
* `Improvement` Adapted document/settings/about/theme color/layout analysis and other pages and floating button menu for night mode
* `Improvement` Directly performed layout range analysis when clicking floating button layout analysis button
* `Improvement` Adaptive layout analysis theme (floating window follows application theme, quick settings panel follows system theme)
* `Improvement` Page layout as compatible as possible with RTL (Right-To-Left) layout
* `Improvement` Added icon animation effects on the about page
* `Improvement` Automatically updated year information in copyright statement text on the about page
* `Improvement` Automatically determined and set appropriate work directory after initial installation
* `Improvement` Disabled pinch-to-zoom function on document pages to avoid abnormal document content display
* `Improvement` Simplified task name and path display in relative path on Task Panel list items
* `Improvement` Appropriately abbreviated button text to avoid text overflow in text editor
* `Improvement` Supported resetting the font size setting to the default value in text editor
* `Improvement` Enhanced floating button click response speed
* `Improvement` Reordered layout control information list according to possible frequency of use
* `Improvement` Automatically optimized output format according to selector type when clicking to copy layout control information
* `Improvement` Conveniently obtained resource ID of AutoJs6 through ui.R and auto.R
* `Improvement` Supported returning to the parent directory instead of directly closing the window when selecting files with a floating window and pressing the return key
* `Improvement` Supported digital validity detection and automatic conversion of dot-separated symbols when entering address in client mode to connect to computer
* `Improvement` Added rewrite protection to some global objects and built-in modules (refer to project documentation > Global Object > [Rewrite Protection](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `Improvement` Supported string parameters and variable-length parameters in importClass and importPackage
* `Improvement` Supported printing stack trace information when exception occurs in ui.run
* `Improvement` Supported App type parameters and application alias parameters in methods related to operating applications in app module
* `Improvement` Supported url option parameters in app.startActivity, etc. (refer to example code > Application > Intent)
* `Improvement` Supported omission of pre-filled parameters in methods related to asynchronous callbacks in dialogs module
* `Improvement` Returned null instead of throwing exceptions when failing to get IMEI or hardware serial number in device module
* `Improvement` Enhanced text brightness of the log floating window displayed by console.show to improve content recognition
* `Improvement` Supported saving image files with relative paths in ImageWrapper#saveTo
* `Improvement` Redesigned global colors object and added support for HSV / HSL and other color modes (refer to project documentation > [Color](https://docs.autojs6.com/#/color))
* `Dependency` Localized Android Job version 1.4.3
* `Dependency` Localized Android Plugin Client SDK For Locale version 9.0.0
* `Dependency` Localized GitHub API version 1.306
* `Dependency` Added JCIP Annotations version 1.0
* `Dependency` Added AndroidX WebKit version 1.5.0
* `Dependency` Added Commons IO version 2.8.0
* `Dependency` Added Desugar JDK Libs version 2.0.0
* `Dependency` Added Jackson DataBind version 2.13.3
* `Dependency` Added Jaredrummler Android Device Names version 2.1.0
* `Dependency` Added Jaredrummler Animated SVG View version 1.0.6
* `Dependency` Replaced Jrummyapps ColorPicker version 2.1.7 with Jaredrummler ColorPicker version 1.1.0
* `Dependency` Upgraded Android SDK version 32 -> 33
* `Dependency` Upgraded Gradle version 7.5-rc-1 -> 8.0-rc-1
* `Dependency` Upgraded Gradle build tool version 7.4.0-alpha02 -> 8.0.0-alpha09
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.10 -> 1.8.0-RC2
* `Dependency` Upgraded Android Material version 1.6.0 -> 1.7.0
* `Dependency` Upgraded AndroidX Annotation version 1.3.0 -> 1.5.0
* `Dependency` Upgraded AndroidX AppCompat version 1.4.1 -> 1.4.2
* `Dependency` Upgraded Android Analytics version 13.3.0 -> 14.0.0
* `Dependency` Upgraded Gson version 2.9.0 -> 2.10
* `Dependency` Upgraded Joda Time version 2.10.14 -> 2.12.1
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.1-native-mt -> 1.6.1
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `Dependency` Upgraded Zip4j version 2.10.0 -> 2.11.2
* `Dependency` Upgraded Glide version 4.13.2 -> 4.14.2
* `Dependency` Upgraded JUnit Jupiter version 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `Feature` Check for updates/download updates/update prompt functions (refer to Settings page) (currently not supported on Android 7.x systems)
* `Fix` Issue with the application unable to read/write external storage on Android 10 systems _[`issue #17`](http://issues.autojs6.com/17)_
* `Fix` Issue where long presses on the editor page could cause the application to crash _[`issue #18`](http://issues.autojs6.com/18)_
* `Fix` Issue with the long-press menu 'Delete Row' and 'Copy Row' functions not working on the editor page
* `Fix` Issue with the 'Paste' function missing from the options menu on the editor page
* `Improvement` Resource localization of some exception message strings (en / zh)
* `Improvement` Adjust the button layout of the unsaved content dialog and add color differentiation
* `Dependency` Added GitHub API version 1.306
* `Dependency` Replaced retrofit2-rxjava2-adapter version 1.0.0 with adapter-rxjava2 version 2.9.0

# v6.1.0

###### 2022/05/26 - Package name changed, proceed with caution

* `Hint` Changed the application package name to org.autojs.autojs6 to avoid conflicts with the open-source Auto.js application package name
* `Feature` Added 'Projection Media Permission' switch to the home drawer (Root / ADB method) (experimental switch status detection)
* `Feature` File browser supports displaying hidden files and folders (refer to Settings page)
* `Feature` Forced Root check function (refer to Settings page and example code)
* `Feature` autojs module, for obtaining and manipulating AutoJs6 information (see Sample code > AutoJs6)
* `Feature` tasks module, for managing scheduled tasks (see Sample code > Tasks)
* `Feature` console.launch method, for opening the log page
* `Feature` util.morseCode utility, for Morse code processing (see Sample code > Utilities > Morse code)
* `Feature` util.versionCodes utility, for querying Android version information (see Sample code > Utilities > Android version queries)
* `Feature` util.getClass and related methods, for obtaining Java classes and class names (see Sample code > Utilities > Get classes and class names)
* `Feature` timers.setIntervalExt method, for conditional periodic execution (see Sample code > Timers > Conditional periodic execution)
* `Feature` colors.toInt/rgba and related methods, for color conversion (see Sample code > Images and colors > Basic color conversion)
* `Feature` automator.isServiceRunning/ensureService methods, for checking and ensuring accessibility service availability
* `Feature` automator.lockScreen and related methods, for accessibility actions introduced in Android 9 (see Sample code > Accessibility service > New in Android 9)
* `Feature` automator.headsethook and related methods, for accessibility actions introduced in Android 11 (see Sample code > Accessibility service > New in Android 11)
* `Feature` automator.captureScreen method, for obtaining screenshots through the accessibility service (see Sample code > Accessibility service > Take screenshots)
* `Feature` dialogs.build method supports option parameters such as animation, linkify, etc. (refer to Example Code > Dialogs > Customized Dialogs)
* `Fix` inputHint, itemsSelectedIndex, and other option parameters of dialogs.build behave abnormally
* `Fix` Issue with JsDialog#on('multi_choice') callback parameters
* `Fix` Issue with UiObject#parent().indexInParent() always returning -1 _[`issue #16`](http://issues.autojs6.com/16)_
* `Fix` Issue with Promise.resolve() returned Thenable not being called near the end of the script
* `Fix` Possible spelling errors in package or class names (boardcast -> broadcast / auojs -> autojs)
* `Fix` Issue with images.requestScreenCapture potentially causing application crashes on higher Android versions (API >= 31)
* `Fix` Issue with images.requestScreenCapture potentially causing application crashes when multiple script instances request it simultaneously
* `Fix` Possible deadlock issue when invoking new RootAutomator()
* `Improvement` RootAutomator cannot be instantiated without Root permissions
* `Improvement` Redesigned the 'About Application and Developer' page
* `Improvement` Refactored all built-in JavaScript modules
* `Improvement` Refactored all Gradle build scripts and added a common configuration script (config.gradle)
* `Improvement` Gradle build tools support automatic version number management and automatic naming of build files
* `Improvement` Gradle build tools add task support to append CRC32 digest to build files (appendDigestToReleasedFiles)
* `Improvement` shell calls will write exceptions into the return result instead of directly throwing exceptions (no need for try/catch)
* `Improvement` Replaced original json2 module with Rhino built-in JSON
* `Improvement` auto.waitFor supports timeout parameters
* `Improvement` threads.start supports arrow function parameters
* `Improvement` console.trace supports log level parameters (refer to Example Code > Console > Print Call Stack)
* `Improvement` Enhanced font readability in both normal and night themes for the console using Material Color
* `Improvement` device.vibrate supports vibration patterns and Morse code vibration (refer to Example Code > Device > Vibration Patterns / Morse Code Vibration)
* `Improvement` Adapted read/write permissions for external storage to higher Android versions (API >= 30)
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
* `Feature` recorder module, for timing (see Sample code > Timer)
* `Feature` Accessibility service supports automatic enabling through "Modify secure settings permission", with a corresponding settings switch
* `Fix` Issue with quick settings panel not automatically collapsing after clicking relevant icons (tentative fix) _[`issue #7`](http://issues.autojs6.com/7)_
* `Fix` Issue where AutoJs6 might crash when the toast is forced to display
* `Fix` Issue with AutoJs6 possibly crashing when the Socket transmission data header information is incomplete
* `Improvement` Automatically enable accessibility services according to option settings when starting or restarting AutoJs6
* `Improvement` Attempt to automatically enable accessibility services when the floating button switch is turned on
* `Improvement` Added corresponding English translations for all resource file elements
* `Improvement` Adjusted the home drawer layout and reduced spacing between entries
* `Improvement` Added synchronization of foreground service status switch in the home drawer
* `Improvement` Synchronize switch status on demand immediately when the home drawer is expanded
* `Improvement` Added status detection and result prompts for displaying pointer position
* `Improvement` Supported 64-bit operating systems (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Improvement` Applied transparency settings simultaneously when initializing the floating button (no need to click to apply transparency)
* `Improvement` Added detection of whether the file is example code and result prompt when resetting file content
* `Improvement` Transferred plugin download address from GitHub to JsDelivr
* `Dependency` Added Zeugma Solutions LocaleHelper version 1.5.1
* `Dependency` Upgraded Android SDK version 31 -> 32
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.0-native-mt -> 1.6.0
* `Dependency` Upgraded OpenCV version 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `Dependency` Upgraded Gradle build tools version 7.2.0-beta01 -> 7.3.0-alpha06
* `Dependency` Upgraded Auto.js-ApkBuilder version 1.0.1 -> 1.0.3
* `Dependency` Upgraded Glide Compiler version 4.12.0 -> 4.13.1
* `Dependency` Upgraded Gradle version 7.4-rc-2 -> 7.4.1
* `Dependency` Upgraded Gson version 2.8.9 -> 2.9.0
* `Dependency` Downgraded Android Material version 1.6.0-alpha02 -> 1.5.0

# v6.0.2

###### 2022/02/05

* `Feature` images.bilateralFilter method, for bilateral image filtering
* `Fix` Issue where multiple calls to toast only took effect on the last call
* `Fix` Issue where toast.dismiss might not work
* `Fix` Issues with client mode and server mode switches possibly not working properly
* `Fix` Issues with client mode and server mode switch statuses not refreshing properly
* `Fix` Issue with parsing UI mode text elements on Android 7.x (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `Improvement` Ignore sleep's ScriptInterruptedException
* `Dependency` Added AndroidX AppCompat (Legacy) version 1.0.2
* `Dependency` Upgraded AndroidX AppCompat version 1.4.0 -> 1.4.1
* `Dependency` Upgraded AndroidX Preference version 1.1.1 -> 1.2.0
* `Dependency` Upgraded Rhino engine version 1.7.14-SNAPSHOT -> 1.7.14
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `Dependency` Upgraded Android Material version 1.6.0-alpha01 -> 1.6.0-alpha02
* `Dependency` Upgraded Gradle build tools version 7.2.0-alpha06 -> 7.2.0-beta01
* `Dependency` Upgraded Gradle version 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `Feature` Support for VSCode plugin connection in client (LAN) and server (LAN/ADB) modes (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` base64 module, for Base64 encoding and decoding (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` isInteger/isNullish/isObject/isPrimitive/isReference global methods, for type checking
* `Feature` Compatibility implementation (polyfill) of Object.getOwnPropertyDescriptors
* `Feature` Compatibility implementation (polyfill) of Array.prototype.flat
* `Improvement` global.sleep supports random ranges and negative arguments
* `Improvement` global.toast supports duration settings, forced replacement and dismiss
* `Improvement` Globalized package name objects (okhttp3/androidx/de)
* `Dependency` Upgraded Android Material version 1.5.0-beta01 -> 1.6.0-alpha01
* `Dependency` Upgraded Gradle build tools version 7.2.0-alpha04 -> 7.2.0-alpha06
* `Dependency` Upgraded Kotlinx Coroutines version 1.5.2-native-mt -> 1.6.0-native-mt
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.0 -> 1.6.10
* `Dependency` Upgraded Gradle version 7.3 -> 7.3.3

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
* `Improvement` Enhanced compatibility of ViewUtil.getStatusBarHeight
* `Improvement` Removed user login related modules and layout placeholders from the home drawer
* `Improvement` Removed the community and market tabs from the home page and improved layout alignment
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
* `Dependency` Added AndroidX Preference version 1.1.1
* `Dependency` Added AndroidX SwipeRefreshLayout version 1.1.0
* `Dependency` Upgraded Android SDK version 28 -> 30 -> 31
* `Dependency` Upgraded Android Analytics version 7.0.0 -> 13.1.0
* `Dependency` Upgraded Android Annotations version 4.5.2 -> 4.8.0
* `Dependency` Upgraded Gradle build tools version 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `Dependency` Upgraded Android Job version 1.2.6 -> 1.4.2
* `Dependency` Upgraded Android Material version 1.1.0-alpha01 -> 1.5.0-beta01
* `Dependency` Upgraded AndroidX MultiDex version 2.0.0 -> 2.0.1
* `Dependency` Upgraded Apache Commons Lang3 version 3.6 -> 3.12.0
* `Dependency` Upgraded AndroidX AppCompat version 1.0.2 -> 1.4.0
* `Dependency` Upgraded ButterKnife Gradle plugin version 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `Dependency` Upgraded ColorPicker version 2.1.5 -> 2.1.7
* `Dependency` Upgraded Espresso Core version 3.1.1-alpha01 -> 3.5.0-alpha03
* `Dependency` Upgraded EventBus version 3.0.0 -> 3.2.0
* `Dependency` Upgraded Glide Compiler version 4.8.0 -> 4.12.0
* `Dependency` Upgraded Gradle Build Tool version 29.0.2 -> 30.0.2
* `Dependency` Upgraded Gradle version 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `Dependency` Upgraded Groovy-Json plugin version 3.0.7 -> 3.0.8
* `Dependency` Upgraded Gson version 2.8.2 -> 2.8.9
* `Dependency` Upgraded Java version 8 -> 11 -> 16
* `Dependency` Upgraded Joda Time version 2.9.9 -> 2.10.13
* `Dependency` Upgraded JUnit version 4.12 -> 4.13.2
* `Dependency` Upgraded Kotlin Gradle plugin version 1.3.10 -> 1.4.10 -> 1.6.0
* `Dependency` Upgraded Kotlinx Coroutines version 1.0.1 -> 1.5.2-native-mt
* `Dependency` Upgraded LeakCanary version 1.6.1 -> 2.7
* `Dependency` Upgraded LicensesDialog version 1.8.1 -> 2.2.0
* `Dependency` Upgraded Material Dialogs version 0.9.2.3 -> 0.9.6.0
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `Dependency` Upgraded ReactiveX RxJava2 RxAndroid version 2.0.1 -> 2.1.1
* `Dependency` Upgraded ReactiveX RxJava2 version 2.1.2 -> 2.2.21
* `Dependency` Upgraded Retrofit2 Converter Gson version 2.3.0 -> 2.9.0
* `Dependency` Upgraded Retrofit2 Retrofit version 2.3.0 -> 2.9.0
* `Dependency` Upgraded Zip4j version 1.3.2 -> 2.9.1
* `Dependency` Upgraded Rhino engine version 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `Dependency` Upgraded OpenCV version 3.4.3 -> 4.5.4