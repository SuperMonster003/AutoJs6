******

### Historial de versiones

******

# v6.6.4

###### 2025/05/31

* `Sugerencia` Cambio de API: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `Nuevo` Métodos util.dpToPx/spToPx/pxToDp/pxToSp para conversión de unidades de píxeles
* `Corrección` Subtítulos que pueden mostrarse incompletos al rotar la pantalla a orientación horizontal
* `Corrección` Parte del contenido de algunas páginas oculto por la barra de navegación lateral al rotar la pantalla a horizontal
* `Corrección` Área de coloreado de fondo de la barra de estado incompleta en algunas páginas bajo Android 15 _[`issue #398`](http://issues.autojs6.com/398)_
* `Corrección` El editor de código podría escribir archivos con una codificación de confianza insuficiente causando errores de decodificación (arreglo tentativa)
* `Mejora` Mejorada la adaptabilidad de diseño en las páginas de Aplicación y Desarrollador y eliminadas categorías de diseño innecesarias
* `Mejora` En README.md, sección de compilación, añadidas varias formas para facilitar la localización de la página de configuración del objetivo _[`issue #404`](http://issues.autojs6.com/404)_
* `Dependencia` Añadido Androidx ConstraintLayout versión 2.2.1

# v6.6.3

###### 2025/05/27

* `Nuevo` Función de historial de versiones: ver registros de cambios multilenguaje y estadísticas
* `Nuevo` Método timers.keepAlive (ahora global) para mantener activo el script
* `Nuevo` Métodos de escucha de eventos como engines.on('start/stop/error', callback) para eventos globales del motor
* `Nuevo` Método images.detectMultiColors para verificación de colores multipunto _[`issue #374`](http://issues.autojs6.com/374)_
* `Nuevo` Métodos images.matchFeatures/detectAndComputeFeatures: búsqueda de imágenes a resolución completa (Ref a [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `Nuevo` Método images.compressToBytes para comprimir una imagen y generar un arreglo de bytes
* `Nuevo` Método images.downsample para submuestreo de píxeles y creación de un nuevo ImageWrapper
* `Nuevo` Método ui.keepScreenOn para mantener la pantalla encendida cuando la página UI está en foco
* `Nuevo` Propiedad ui.root (getter) para obtener el nodo "contenedor raíz del contenido de la ventana" del layout UI
* `Nuevo` El elemento webview admite diseños de páginas web basados en JsBridge (Ref a [Auto.js Pro](https://g.pro.autojs.org/)) [ver Código de ejemplo > Diseño > HTML interactivo / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `Corrección` El contenido de la documentación en línea en la pestaña Documentos y la actividad Documentos podía quedar cubierto por la barra de navegación del sistema
* `Corrección` En algunas páginas, al pulsar botones de la Toolbar se podía activar por error el evento de clic en el título
* `Corrección` Las líneas en blanco del editor de código mostraban caracteres de cuadro en ciertos dispositivos
* `Corrección` El cuadro de diálogo del selector de colores en los ajustes de color de tema podía apilarse infinitamente
* `Corrección` La tecla de subir volumen no detenía todos los scripts cuando el servicio de accesibilidad estaba desactivado
* `Corrección` Superposición del teclado IME al editar contenido de difusión personalizado en la página de tareas programadas
* `Corrección` Los controles dentro de elementos webview no podían activar correctamente el teclado en pantalla
* `Corrección` El diálogo de información de APK podía no obtener el nombre de la app ni la información del SDK
* `Corrección` El ejemplo del administrador de archivos podía no cargar automáticamente el contenido de subcarpetas al acceder a un directorio de proyecto
* `Corrección` El contenido superior en modo UI de Android 15 quedaba cubierto por la barra de estado
* `Corrección` El color de fondo de la barra de estado en algunas páginas de Android 15 no seguía el color de tema de forma dinámica
* `Corrección` El módulo dialogs no podía usar la propiedad customView _[`issue #364`](http://issues.autojs6.com/364)_
* `Corrección` El parámetro de expresión de dialogs.input podía no devolver el resultado de ejecución
* `Corrección` El uso de JavaAdapter provocaba desbordamiento de pila de ClassLoader _[`issue #376`](http://issues.autojs6.com/376)_
* `Corrección` console.setContentTextColor hacía perder el color de texto por defecto _[`issue #346`](http://issues.autojs6.com/346)_
* `Corrección` console.setContentBackgroundColor no aceptaba nombres de color _[`issue #384`](http://issues.autojs6.com/384)_
* `Corrección` images.compress ahora ajusta la calidad de codificación en lugar de reducir píxeles
* `Corrección` El método images.resize no funcionaba correctamente
* `Corrección` engines.all podía lanzar ConcurrentModificationException _[`issue #394`](http://issues.autojs6.com/394)_
* `Corrección` Formato de fecha incorrecto en algunos idiomas de README.md
* `Corrección` La compilación Gradle podía fallar por longitud inválida de archivo de biblioteca _[`issue #389`](http://issues.autojs6.com/389)_
* `Mejora` El inspector de diseño permite ocultar controles (por [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `Mejora` Añadidos separadores degradados en el menú del inspector de diseño para agrupar funciones
* `Mejora` project.json admite la opción permissions para proyectos de scripts (por [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `Mejora` Al empaquetar un solo archivo se leen y seleccionan automáticamente los permisos declarados por la app instalada _[`issue #362`](http://issues.autojs6.com/362)_
* `Mejora` Ampliado el soporte de adaptación del color de tema y más tipos de controles
* `Mejora` Adaptabilidad de la anchura del cajón en la pantalla principal para modo horizontal o pantallas ultra-anchas
* `Mejora` Nuevos diseños horizontal y de pantalla pequeña para las páginas Acerca de la app y Desarrollador
* `Mejora` Las páginas de ajustes ofrecen la opción "Usar valor predeterminado" en los diálogos
* `Mejora` El botón flotante del administrador de archivos se oculta automáticamente al pulsar fuera
* `Mejora` El formateador de código admite operadores `??`, `?.`, `??=`
* `Mejora` El editor de código admite la lectura y escritura de archivos con codificaciones GB18030 / UTF-16 (LE/BE) / Shift_JIS, etc.
* `Mejora` El editor de código ahora muestra información detallada del archivo (ruta/codificación/salto de línea/total de bytes y caracteres, etc.) _[`issue #395`](http://issues.autojs6.com/395)_
* `Mejora` Se añaden mensajes de error para acciones de intent (editar / ver / instalar / enviar / reproducir, etc.)
* `Mejora` El atributo url de webview admite rutas relativas
* `Mejora` El parámetro ruta de ImageWrapper#saveTo admite rutas relativas
* `Mejora` images.save permite comprimir archivos PNG al usar el parámetro quality _[`issue #367`](http://issues.autojs6.com/367)_
* `Mejora` Se permite limpiar registros de actualizaciones ignoradas y direcciones de conexión en modo cliente
* `Mejora` La información de actualización de versión se muestra en varios idiomas según el idioma actual
* `Mejora` La carga asíncrona mejora la fluidez de la lista en el administrador de archivos
* `Mejora` Mejorado el contenido y formato de los mensajes de excepción de scripts en la consola
* `Mejora` El código de ejemplo puede restablecer carpetas a su contenido inicial
* `Mejora` Mayor eficiencia al comprobar la firma de APK
* `Mejora` Optimizada la eficiencia y presentación de diálogos de información de archivos APK y multimedia
* `Mejora` El script de compilación Gradle mejora su capacidad de adaptación de versiones _[`discussion #369`](http://discussions.autojs6.com/369)_
* `Dependencia` Incluido Material Dialogs versión 0.9.6.0 (localizado)
* `Dependencia` Incluido Material Date Time Picker versión 4.2.3 (localizado)
* `Dependencia` Incluido libimagequant versión 2.17.0 (localizado)
* `Dependencia` Incluido libpng versión 1.6.49 (localizado)
* `Dependencia` Añadido ICU4J versión 77.1
* `Dependencia` Añadido Jsoup versión 1.19.1
* `Dependencia` Añadido Material Progressbar versión 1.4.2
* `Dependencia` Añadido Flexmark Java HTML to Markdown versión 0.64.8
* `Dependencia` Actualizado Gradle 8.14-rc-1 -> 8.14
* `Dependencia` Actualizado Androidx Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `Nuevo` Métodos como ui.statusBarAppearanceLight, statusBarAppearanceLightBy y navigationBarColor, etc.
* `Nuevo` Atributo ui.statusBarHeight (getter), utilizado para obtener la altura de la barra de estado _[`issue #357`](http://issues.autojs6.com/357)_
* `Nuevo` Método images.flip para voltear imágenes _[`issue #349`](http://issues.autojs6.com/349)_
* `Nuevo` Se añadió la opción 'extensión de archivo' en la página de configuración
* `Nuevo` La página de configuración del color del tema añade soporte para nuevos diseños (agrupación, posicionamiento, búsqueda, historial, mejora del selector de color, etc.)
* `Corrección` Problema en el que el color de fondo de la barra de estado de Android 15 no coincide con el color del tema
* `Corrección` Problema en el que el método plugins.load no carga correctamente los plugins _[`issue #290`](http://issues.autojs6.com/290)_
* `Corrección` Problema en el que la biblioteca dx no funciona correctamente en Android 7.x _[`issue #293`](http://issues.autojs6.com/293)_
* `Corrección` Problema en el que ScriptRuntime puede presentar un estado de sincronización anómalo al utilizar require para importar módulos integrados (solución tentativa) _[`issue #298`](http://issues.autojs6.com/298)_
* `Corrección` Problema en el que el módulo notice carece de métodos de extensión como getBuilder _[`issue #301`](http://issues.autojs6.com/301)_
* `Corrección` Problema en el que los métodos shizuku/shell no aceptan parámetros de tipo cadena _[`issue #310`](http://issues.autojs6.com/310)_
* `Corrección` Problema en el que el método colors.pixel no acepta parámetros con imágenes de un solo canal _[`issue #350`](http://issues.autojs6.com/350)_
* `Corrección` Problema en el que los métodos engines.execScript / execScriptFile asignan de forma anómala el directorio de trabajo predeterminado al ejecutar scripts _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `Corrección` Problema en el que floaty.window / floaty.rawWindow no puede ejecutarse en subprocesos
* `Corrección` Problema en el que floaty.getClip puede no obtener correctamente el contenido del portapapeles _[`issue #341`](http://issues.autojs6.com/341)_
* `Corrección` Problema en el que el retorno de ui.inflate pierde métodos de prototipo como attr, on y click
* `Corrección` Problema en el que se produce un enlace incorrecto del contexto de ámbito al utilizar la sintaxis XML para usar una expresión JavaScript como valor de atributo _[`issue #319`](http://issues.autojs6.com/319)_
* `Corrección` Problema en el que algunas llamadas a métodos, al producir excepciones, no son capturadas por bloques try..catch _[`issue #345`](http://issues.autojs6.com/345)_
* `Corrección` Problema en el que la generación de código en la página de análisis de layouts puede provocar que la aplicación se bloquee _[`issue #288`](http://issues.autojs6.com/288)_
* `Corrección` Problema en el que las aplicaciones empaquetadas no pueden utilizar correctamente el módulo shizuku _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `Corrección` Problema en el que el editor de código, al saltar al final de una línea, puede mover el cursor al inicio de la siguiente línea
* `Corrección` Problema en el que pulsaciones rápidas consecutivas en elementos tipo diálogo de la página de configuración pueden provocar que la aplicación se bloquee
* `Mejora` Optimización del tamaño del archivo APK en la plantilla de aplicaciones empaquetadas
* `Mejora` La aplicación (y las aplicaciones empaquetadas) ahora soportan más permisos _[`issue #338`](http://issues.autojs6.com/338)_
* `Mejora` La página de empaquetado añade la opción para la biblioteca Pinyin
* `Mejora` Optimización del fondo de la barra de estado y del color del texto en la página principal de la aplicación empaquetada
* `Mejora` La página de configuración de la aplicación empaquetada añade interruptores para permisos especiales (acceso a todos los archivos y envío de notificaciones) _[`issue #354`](http://issues.autojs6.com/354)_
* `Mejora` Los textos e iconos de los controles cambian automáticamente a un color adecuado en función de la luminosidad del tema
* `Mejora` Mejora en la experiencia visual cuando el contraste entre el color del tema de un control y su fondo es bajo
* `Mejora` Mejora en la compatibilidad del control de entrada HEX en la paleta de colores al pegar valores desde el portapapeles
* `Mejora` La barra de navegación de la aplicación se configura para ser transparente o semi-transparente y así mejorar la experiencia visual
* `Mejora` El modo UI por defecto de la barra de estado y de la barra de navegación se establece en el color `md_grey_50` en modo claro
* `Mejora` El interruptor del servicio de accesibilidad en el cajón de la página principal ahora se sincroniza con el código del script
* `Mejora` La página de documentación de la página principal ahora soporta botones de búsqueda bidireccionales
* `Mejora` La pestaña 'Archivos' de la página principal permite cambiar la visibilidad del botón flotante mediante pulsación prolongada
* `Mejora` El título del editor de código ahora soporta el ajuste automático del tamaño de la fuente
* `Mejora` La visibilidad del botón flotante en la página de registros se vincula a las acciones de desplazamiento de la lista
* `Mejora` El archivo de configuración project.json del proyecto de script ahora soporta más opciones de empaquetado _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `Mejora` El archivo project.json ahora admite coincidencia flexible de nombres de opción y compatibilidad con alias
* `Mejora` El diálogo de información de tipo de archivo APK ahora incluye el tamaño del archivo y la información del esquema de firma
* `Mejora` El diálogo de información de tipo de archivo APK ahora admite escuchas de clic para copiar texto y navegar a los detalles de la aplicación
* `Mejora` Intento de restaurar los paquetes con prefijo com.stardust para mejorar la compatibilidad del código _[`issue #290`](http://issues.autojs6.com/290)_
* `Mejora` Los métodos floaty.window / floaty.rawWindow ahora soportan la ejecución tanto en el hilo principal como en hilos secundarios
* `Mejora` El método global getClip ahora utiliza floaty.getClip según sea necesario para mejorar la compatibilidad
* `Mejora` Mejora en la compatibilidad de files.path y métodos relacionados al recibir rutas nulas
* `Mejora` Sincronización con la última versión oficial del motor Rhino y adaptación necesaria del código
* `Mejora` Mejoras en el README.md para documentar la construcción y ejecución del proyecto _[`issue #344`](http://issues.autojs6.com/344)_
* `Dependencia` Añadido Eclipse Paho Client Mqttv3 versión 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `Dependencia` Actualizado la versión de Gradle Compile de 34 a 35
* `Dependencia` Actualizado Gradle de 8.12 a 8.14-rc-1
* `Dependencia` Actualizado Rhino de 1.8.0-SNAPSHOT a 1.8.1-SNAPSHOT
* `Dependencia` Actualizado Androidx Recyclerview de 1.3.2 a 1.4.0
* `Dependencia` Actualizado Androidx Room de 2.6.1 a 2.7.0
* `Dependencia` Actualizado Androidx WebKit de 1.12.1 a 1.13.0
* `Dependencia` Actualizado Pinyin4j de 2.5.0 a 2.5.1

# v6.6.1

###### 2025/01/01

* `Nuevo` Módulo Pinyin para la conversión de pinyin en chino (Consulte la documentación del proyecto > [Pinyin chino](https://docs.autojs6.com/#/pinyin))
* `Nuevo` Módulo Pinyin4j para la conversión de pinyin en chino (Consulte la documentación del proyecto > [Pinyin chino](https://docs.autojs6.com/#/pinyin4j))
* `Nuevo` Métodos UiObject#isSimilar y UiObjectCollection#isSimilar para determinar si un control o una colección de controles son similares
* `Nuevo` Método global "currentComponent", utilizado para obtener el nombre del componente activo actual
* `Corrección` Problema donde no se podía compilar correctamente el proyecto en determinados entornos debido a una reversión a una versión anterior
* `Corrección` Excepción de "valor no primitivo" que puede ocurrir al llamar a métodos inexistentes
* `Corrección` Problema donde los accesos directos de scripts no se podían agregar correctamente en algunos dispositivos (arreglo tentativa) _[`issue #221`](http://issues.autojs6.com/221)_
* `Corrección` Restricción de tipo de parámetro incorrecta en los métodos automator.click/longClick _[`issue #275`](http://issues.autojs6.com/275)_
* `Corrección` Problema donde los selectores no admitían parámetros del tipo ConsString _[`issue #277`](http://issues.autojs6.com/277)_
* `Corrección` Problema donde las instancias de UiObjectCollection carecían de métodos y propiedades propios
* `Mejora` La página de empaquetado admite configuración de firma, gestión de almacenes de claves y configuración de permisos (por [luckyloogn]()) _[`pr #286`]()_
* `Mejora` Mejorada la precisión en la identificación del nombre del paquete y de la actividad actual de la ventana flotante (Prioridad: Shizuku > Root > A11Y)
* `Mejora` Mejorada la precisión en la identificación de currentPackage y currentActivity (Prioridad: Shizuku > Root > A11Y)
* `Mejora` Restaurar la capacidad de seleccionar el contenido del texto de entradas individuales en la ventana de actividad del registro mediante doble clic o pulsación larga _[`issue #280`](http://issues.autojs6.com/280)_
* `Mejora` Recuperar la mayor cantidad de información crítica posible para proyectos de scripts cuando el archivo project.json está dañado
* `Mejora` Convertir automáticamente chino simplificado a Pinyin (incluyendo caracteres con múltiples tonos) para los sufijos de nombres de paquetes generados al empaquetar archivos individuales
* `Mejora` Soporte para argumentos negativos en los métodos UiSelector#findOnce y UiSelector#find
* `Mejora` Se mejoró la adaptabilidad de los métodos app.startActivity/startDualActivity
* `Mejora` Mayor soporte para formas abreviadas de prefijos de nombres de paquetes en selectores relacionados con elementos UI y className (por ejemplo, RecyclerView, Snackbar, etc.)
* `Mejora` Sincronizar el código más reciente del upstream del motor Rhino y adaptarlo al proyecto existente
* `Dependencia` Agregado Pinyin4j versión 2.5.0
* `Dependencia` Agregado Jieba Analysis versión 1.0.3-SNAPSHOT (modificada)
* `Dependencia` Actualizar la versión de Gradle de 8.11.1 a 8.12

# v6.6.0

###### 2024/12/02 - Reescritura de módulos integrados, actualización con precaución

* `Sugerencia` Los módulos integrados se reescriben en Kotlin para mejorar la eficiencia de ejecución del script, pero se necesitan mejoras iterativas.
* `Sugerencia` El archivo init.js integrado está vacío por defecto, lo que permite a los desarrolladores ampliar los módulos integrados o montar módulos externos.
* `Nuevo` Módulo Axios / Módulo Cheerio (Ref a [AutoX](https://github.com/kkevsekk1/AutoX))
* `Nuevo` Módulo SQLite para operaciones simples en bases de datos SQLite (Ref a [Auto.js Pro](https://g.pro.autojs.org/)) (Consulte la documentación del proyecto > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Nuevo` Módulo MIME para procesamiento y análisis de cadenas de tipos MIME (Consulte la documentación del proyecto > [MIME](https://docs.autojs6.com/#/mime))
* `Nuevo` Módulo Nanoid para generación de ID de cadenas (Ref a [ai/nanoid](https://github.com/ai/nanoid))
* `Nuevo` Módulo Sysprops para obtener datos de configuración del entorno de ejecución (Consulte la documentación del proyecto > [Propiedades del sistema](https://docs.autojs6.com/#/sysprops))
* `Nuevo` El módulo OCR admite el motor [Rapid OCR](https://github.com/RapidAI/RapidOCR)
* `Nuevo` El análisis de diseño admite el cambio de ventanas (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` El método auto.clearCache admite la limpieza de cachés de control (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` El método threads.pool admite la aplicación simple de grupos de hilos (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` El método images.matchTemplate agrega el parámetro de opción useTransparentMask para soportar la búsqueda de imágenes transparentes (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` El método images.requestScreenCaptureAsync se usa para solicitar permisos de captura de pantalla de manera asíncrona en el modo UI (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` El método images.requestScreenCapture agrega el parámetro de opción isAsync para soportar la captura de pantalla de manera asíncrona (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` images.on('screen_capture', callback) y otros métodos de escucha de eventos admiten la escucha de eventos de disponibilidad de captura de pantalla (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` El método images.stopScreenCapture soporta la liberación activa de recursos relacionados con la aplicación de captura de pantalla (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Los métodos Images.psnr/mse/ssim/mssim/hist/ncc y images.getSimilarity se utilizan para obtener métricas de similitud de imágenes
* `Nuevo` El método images.isGrayscale se utiliza para determinar si una imagen es en escala de grises
* `Nuevo` El método images.invert se utiliza para la conversión de imagen en negativo
* `Nuevo` Los métodos s13n.point/time se utilizan para estandarizar objetos de punto y objetos de duración (Consulte la documentación del proyecto > [Normalización](https://docs.autojs6.com/#/s13n))
* `Nuevo` Los métodos gravity, touchThrough, backgroundTint del módulo console (Consulte la documentación del proyecto > [Consola](https://docs.autojs6.com/#/console))
* `Nuevo` Los métodos Mathx.randomInt/Mathx.randomFloat se utilizan para devolver enteros aleatorios o números de coma flotante aleatorios dentro de un rango especificado
* `Nuevo` Los métodos app.launchDual/startDualActivity se utilizan para manejar el lanzamiento dual de aplicaciones (Requiere permisos de Shizuku o Root) (Experimental)
* `Nuevo` El método app.kill se utiliza para detener forzosamente una aplicación (Requiere permisos de Shizuku o Root)
* `Nuevo` El método floaty.getClip se utiliza para obtener indirectamente el contenido del portapapeles mediante una ventana flotante
* `Corrección` Fuga de memoria en el View Binding de subclases de Fragment (por ejemplo, [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48))
* `Corrección` Fuga de memoria de instancia en clases como [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21)
* `Corrección` Problema que causa que la aplicación se bloquee al solicitar permisos de captura de pantalla en Android 14 (por [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `Corrección` Problema que causa que la aplicación se bloquee al iniciar el servicio en primer plano en Android 14
* `Corrección` Problema con el botón de ejecución en el editor de código que no se enciende correctamente en Android 14
* `Corrección` La aplicación puede no funcionar correctamente después de empaquetar debido a la falta de archivos de biblioteca necesarios _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `Corrección` Bloqueo de la aplicación cuando se edita el proyecto debido a recursos de icono especificados que faltan _[`issue #203`](http://issues.autojs6.com/203)_
* `Corrección` Incapacidad para usar parámetros adecuadamente para obtener recursos de captura de pantalla de orientación de pantalla especificada al solicitar permisos de captura de pantalla
* `Corrección` Problema con algunos dispositivos que no pueden agregar accesos directos de script correctamente (Reparación de prueba) _[`issue #221`](http://issues.autojs6.com/221)_
* `Corrección` Problema de retraso acumulativo en el envío de solicitudes con métodos relacionados con el envío de solicitudes en el módulo http _[`issue #192`](http://issues.autojs6.com/192)_
* `Corrección` El servicio Shizuku puede no funcionar correctamente antes de que AutoJs6 entre en la página de actividad principal (Reparación de prueba) _[`issue #255`](http://issues.autojs6.com/255)_
* `Corrección` El método random(min, max) puede tener resultados fuera de límites
* `Corrección` Problema donde el tipo de parámetro de resultado de los métodos pickup no puede pasar adecuadamente matrices vacías
* `Corrección` Problema donde el rectángulo de control obtenido por UiObject#bounds() puede ser modificado inadvertidamente, rompiendo su inmutabilidad
* `Corrección` Problema con elementos de texto/botón/entrada donde el texto que contiene comillas dobles de ancho medio no puede ser analizado correctamente
* `Corrección` Problema con elementos de text/textswitcher donde la funcionalidad del atributo autoLink falla
* `Corrección` Problema con diferentes scripts que comparten erróneamente el mismo objeto ScriptRuntime
* `Corrección` Problema con variables globales HEIGHT y WIDTH perdiendo propiedades Getter generadas dinámicamente
* `Corrección` Problema con un alto tiempo de inicio potencial causado por la carga de RootShell al iniciar un script
* `Corrección` Problema con la configuración del color de fondo de la ventana flotante de la consola que conduce a la pérdida del estilo de redondeo rectangular
* `Corrección` El inicio automático del servicio de accesibilidad puede encontrar problemas de servicio anormales (Reparación de prueba)
* `Corrección` Problema con el desencadenante de ViewPager al deslizar el control WebView a la izquierda o derecha en la página de documentos de la página principal
* `Corrección` Problema donde el administrador de archivos no puede reconocer extensiones de archivo que contienen letras mayúsculas
* `Corrección` El administrador de archivos puede no reconocer automáticamente el proyecto al ingresar por primera vez al directorio del proyecto
* `Corrección` Problema donde la página del administrador de archivos no puede refrescarse automáticamente después de eliminar la carpeta
* `Corrección` Problema con la clasificación de archivos y carpetas en el administrador de archivos donde los nombres de letra inicial ASCII se colocan detrás
* `Corrección` Excepción 'FAILED ASSERTION' en la función de depuración del editor de código
* `Corrección` Problema con la imposibilidad de volver a depurar adecuadamente después de cerrar el editor durante el proceso de depuración del editor de código
* `Corrección` Problema de omisión potencial de caracteres finales al saltar al final de la línea en el editor de código
* `Corrección` Problema con la pantalla parpadeante al iniciar la página de actividad de registro en la página de actividad principal
* `Corrección` Problema con la aplicación empaquetada que no puede utilizar adecuadamente el módulo opencc
* `Mejora` Experiencia de aviso de clic para el control 'ABI no disponible' en la página de paquete
* `Mejora` Admite el uso de Shizuku para controlar el interruptor de visualización 'Ubicación del puntero'
* `Mejora` Admite el uso de Shizuku para controlar los interruptores de permisos 'Medios de proyección' y 'Modificar configuraciones seguras'
* `Mejora` Automator.gestureAsync/gesturesAsync admite parámetros de función de devolución de llamada
* `Mejora` El módulo tasks utiliza una forma síncrona para las operaciones de base de datos para evitar inconsistencias potenciales de acceso a datos
* `Mejora` El modo de ejecución de script admite parámetros de separación de modo con símbolo de tubería (por ejemplo, comenzando con `"ui|auto";`)
* `Mejora` El modo de ejecución de script admite comillas simples y comillas invertidas y permite omitir puntos y comas (por ejemplo, comenzando con `'ui';` o `'ui'`)
* `Mejora` El modo de ejecución de script admite la importación rápida de módulos de extensión integrados como axios, cheerio y dayjs (por ejemplo, comenzando con `"axios";`)
* `Mejora` El modo de ejecución de script admite parámetros de modo x o jsox para habilitar rápidamente los módulos de extensión de objetos integrados de JavaScript (por ejemplo, comenzando con `"x";`)
* `Mejora` Los atributos src y path del elemento img admiten rutas relativas locales (por ejemplo, `<img src="a.png"` />)
* `Mejora` El editor de código admite la determinación inteligente de la ubicación de inserción al importar clases Java y nombres de paquetes
* `Mejora` El módulo images admite el uso de rutas directamente como parámetros de imagen
* `Mejora` importPackage admite parámetros de cadena
* `Mejora` La dirección IP del modo servidor admite la importación del portapapeles con reconocimiento inteligente y conversión inteligente con la tecla de espacio
* `Mejora` El administrador de archivos admite la selección de prefijos predeterminados al crear nuevos archivos y genera automáticamente el sufijo numérico apropiado
* `Mejora` El administrador de archivos informa específicamente sobre el mensaje de excepción al ejecutar el proyecto _[`issue #268`](http://issues.autojs6.com/268)_
* `Mejora` El administrador de archivos admite más tipos y muestra símbolos de icono correspondientes (admite más de 800 tipos de archivos)
* `Mejora` Los tipos de archivos editables (jpg/doc/pdf, etc.) en el administrador de archivos han añadido botones de edición
* `Mejora` Los archivos APK en el administrador de archivos admiten la visualización de información básica, información de Manifesto y lista de permisos
* `Mejora` Los archivos de medios de audio/video en el administrador de archivos admiten la visualización de información básica e información de MediaInfo
* `Mejora` El paquete de archivo único admite el autocompletado de nombre de paquete estandarizado apropiado y el aviso de filtro de caracteres no válidos
* `Mejora` El paquete de archivo único admite la configuración automática del ícono y el incremento automático del número de versión y del nombre de versión basado en la misma aplicación de nombre de paquete instalada
* `Mejora` El archivo de configuración del paquete admite la opción abis/libs para especificar la arquitectura ABI incluidos por defecto y las bibliotecas
* `Mejora` Admite avisos de mensajes relevantes cuando las opciones abis/libs del archivo de configuración del paquete son inválidas o no están disponibles
* `Mejora` LeakCanary se excluye de la versión oficial para evitar un crecimiento innecesario
* `Mejora` Todos los comentarios en inglés en el código fuente del proyecto vienen acompañados de traducciones al chino simplificado para mejorar la legibilidad
* `Mejora` README y CHANGELOG admiten varios idiomas (Generado automáticamente por el script)
* `Mejora` Mejorar la adaptabilidad de la versión del script de construcción de Gradle
* `Dependencia` Incluir la versión 2.3.1 de MIME Util
* `Dependencia` Incluir la versión 12.6 de Toaster
* `Dependencia` Incluir la versión 10.3 de EasyWindow (para Toaster)
* `Dependencia` Actualización de versión de Gradle de 8.5 a 8.11.1
* `Dependencia` Actualizar la versión 1.7.15-SNAPSHOT de Rhino -> 1.8.0-SNAPSHOT
* `Dependencia` Actualizar la versión 1.10.0 de Android Material Lang3 -> 1.12.0
* `Dependencia` Actualizar la versión 1.7.0 de Androidx Annotation -> 1.9.1
* `Dependencia` Actualizar la versión 1.6.1 de Androidx AppCompat -> 1.7.0
* `Dependencia` Actualizar la versión 1.8.0 de Androidx WebKit -> 1.12.1
* `Dependencia` Actualizar la versión 3.13.0 de Apache Commons -> 3.16.0
* `Dependencia` Actualizar la versión 1.2.4 de ARSCLib -> 1.3.1
* `Dependencia` Actualizar la versión 2.10.1 de Gson -> 2.11.0
* `Dependencia` Actualizar la versión 2.13.3 de Jackson DataBind -> 2.13.4.2
* `Dependencia` Actualizar la versión 2.12.5 de Joda Time -> 2.12.7
* `Dependencia` Actualizar la versión 2.12 de LeakCanary -> 2.14
* `Dependencia` Actualizar la versión 17.2.0 de MLKit Barcode Scanning -> 17.3.0
* `Dependencia` Actualizar la versión 16.0.0 de MLKit Text Recognition Chinese -> 16.0.1
* `Dependencia` Actualizar la versión 2.9.0 de Retrofit2 Converter Gson -> 2.11.0
* `Dependencia` Actualizar la versión 2.9.0 de Retrofit2 Retrofit -> 2.11.0
* `Dependencia` Actualizar la versión 2.0.3 de Desugar JDK Libs -> 2.0.4
* `Dependencia` Actualizar la versión 1.5.2 de Test Runner -> 1.6.2
* `Dependencia` Actualizar la versión 5.10.0 de Junit Jupiter -> 5.10.3
* `Dependencia` Degradar la versión 5.0.0-alpha.11 de OkHttp3 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `Nuevo` Módulo opencc (Consulte Documentación del proyecto > [Conversión China](https://docs.autojs6.com/#/opencc)) (Ref a [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `Nuevo` Métodos [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) y [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) agregados a UiSelector _[`issue #115`](http://issues.autojs6.com/115)_
* `Nuevo` Página de empaquetado de aplicaciones añade soporte para filtrado de ABI y bibliotecas (Ref a [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Corrección` Problema de tamaño anormalmente grande del archivo empaquetado (Ref a [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Corrección` Problema en el que el paquete de la aplicación no muestra o imprime ciertos mensajes de error
* `Corrección` Problema de ícono vacío después de seleccionar el ícono de la aplicación en la página de empaquetado
* `Corrección` Problema de excepción de contexto no inicializado al incluir la biblioteca OCR de Google MLKit en el paquete
* `Corrección` Problema de métodos inoperativos ocr.<u>mlkit/ocr</u>.<u>recognizeText/detect</u>
* `Corrección` Problema en el que algunos textos (como la página de registros) no coinciden con el idioma de configuración de la aplicación
* `Corrección` Problema de desbordamiento de texto en el interruptor del cajón de la página de inicio en ciertos idiomas
* `Corrección` Problema de servicios de accesibilidad que se cierran inmediatamente sin ningún mensaje en ciertos dispositivos _[`issue #181`](http://issues.autojs6.com/181)_
* `Corrección` Problema de cierres inesperados al usar botones físicos en ciertos dispositivos después de activar el servicio de accesibilidad (solución tentativa) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Corrección` Problema de funcionalidad anormal de pickup después de reiniciar el servicio de accesibilidad con auto(true) (solución tentativa) _[`issue #184`](http://issues.autojs6.com/184)_
* `Corrección` Problema de cierre inesperado de la aplicación al arrastrar la ventana flotante creada por el módulo floaty (solución tentativa)
* `Corrección` Problema de uso de parámetros abreviados en app.startActivity _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Corrección` Problema de conflicto de nombres con importClass que causa una excepción _[`issue #185`](http://issues.autojs6.com/185)_
* `Corrección` Problema de uso inoperativo del servicio de accesibilidad en Android 7.x
* `Corrección` Problema de uso inoperativo de runtime.<u>loadJar/loadDex</u> en Android 14 (solución tentativa)
* `Corrección` Problema de inoperatibilidad de "análisis de rango de diseño" y "análisis de jerarquía de diseño" en el panel de configuraciones rápidas de Android _[`issue #193`](http://issues.autojs6.com/193)_
* `Corrección` Problema de potencial ANR causado por la función de comprobación automática de actualizaciones (solución tentativa) _[`issue #186`](http://issues.autojs6.com/186)_
* `Corrección` Problema en el gestor de archivos al regresar a la página de ruta de trabajo después de hacer clic en "hacia arriba" en la carpeta de ejemplo
* `Corrección` Problema de botón de reemplazo no visible en el editor de código al usar la función de reemplazo
* `Corrección` Problema de cierre inesperado de la aplicación al mantener presionado el botón de borrar en el editor de código (solución tentativa)
* `Corrección` Problema de panel de funciones rápidas del módulo que no se muestra correctamente al hacer clic en el botón fx en el editor de código
* `Corrección` Problema de nombres de funciones de botón de panel de funciones rápidas que se desbordan en el editor de código
* `Mejora` El panel rápido de funciones del módulo del editor de código se adapta al modo nocturno.
* `Mejora` La página de inicio de la aplicación empaquetada se adapta al modo nocturno y se ajusta el diseño de los iconos de la aplicación.
* `Mejora` La página de la aplicación empaquetada admite la navegación del cursor utilizando la tecla ENTER en el teclado de software.
* `Mejora` La página de la aplicación empaquetada admite alternar el estado de selección total al hacer clic en los títulos de ABI y librerías.
* `Mejora` La selección predeterminada de ABI se realiza de manera inteligente en la página de la aplicación empaquetada con mensajes guía para elementos no seleccionables.
* `Mejora` El administrador de archivos ajusta la visualización de los elementos del menú según el tipo y las características de los archivos y carpetas.
* `Mejora` El menú de clic derecho en carpetas del administrador de archivos añade una opción para empaquetar aplicaciones.
* `Mejora` Cuando los servicios de accesibilidad están habilitados pero funcionan incorrectamente, se refleja un estado de anomalía en el interruptor del cajón de la página de inicio de AutoJs6.
* `Mejora` La consola incluye información detallada de la pila al imprimir mensajes de error.
* `Dependencia` Versión ARSCLib 1.2.4 adjunta
* `Dependencia` Versión Flexbox 3.0.0 adjunta
* `Dependencia` Versión OpenCC para Android 1.2.0 adjunta
* `Dependencia` Actualización de versión de Gradle de 8.5-rc-1 a 8.5

# v6.4.2

###### 2023/11/15

* `Nuevo` Propiedad inputSingleLine añadida a la opción de parámetros de dialogs.build()
* `Nuevo` Método console.setTouchable _[`issue #122`](http://issues.autojs6.com/122)_
* `Corrección` Problema de parámetros de área de no reconocimiento en ciertos métodos del módulo ocr _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `Corrección` Problema de no obtención de detalles de versión al encontrar una nueva versión en Android 7.x
* `Corrección` Problema de cierre inesperado de la aplicación al solicitar permisos de captura de pantalla en Android 14
* `Corrección` Problema de cierre inesperado de la aplicación al alternar rápidamente el interruptor de "botón flotante" en el cajón de la página principal
* `Corrección` Problema de botón flotante que todavía aparece después de reiniciar la aplicación tras cerrarlo con el menú
* `Corrección` Problema de no efectividad para cambiar el idioma de AutoJs6 desde la página de configuraciones del sistema en Android 13 y superior
* `Corrección` Problema de despliegue no automático de recursos de OpenCV al construir herramientas por primera vez
* `Mejora` Uso del módulo bridges nativo para mejorar la eficiencia de ejecución de scripts (Ref a [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `Mejora` Reestructuración del código relacionado con el servicio de accesibilidad para mejorar su estabilidad (experimental) _[`issue #167`](http://issues.autojs6.com/167)_
* `Mejora` Formato de salida del print para UiObject y UiObjectCollection
* `Mejora` Sugerencias de actualización de versión en caso de requerimientos de versión no satisfechos en el entorno de construcción Gradle JDK
* `Dependencia` Actualización de versión de Gradle de 8.4 a 8.5-rc-1
* `Dependencia` Degradación de versión de Commons IO de 2.14.0 a 2.8.0
* `Dependencia` Degradación de versión de Jackson DataBind de 2.14.3 a 2.13.3

# v6.4.1

###### 2023/11/02

* `Corrección` Problema de no adaptación de herramientas de construcción a plataformas desconocidas (por [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Corrección` Problema de cierre inesperado de la aplicación al salir del script _[`issue #159`](http://issues.autojs6.com/159)_
* `Corrección` Problema de tipo de retorno incorrecto de body.contentType en objeto de respuesta del módulo http _[`issue #142`](http://issues.autojs6.com/142)_
* `Corrección` Problema de datos incorrectos de device.width y device.height _[`issue #160`](http://issues.autojs6.com/160)_
* `Corrección` Problema de cierre inesperado de la aplicación al mantener presionado el botón de borrar en el editor de código (solución tentativa) _[`issue #156`](http://issues.autojs6.com/156)_
* `Corrección` Problema de cierre inesperado al realizar operaciones generales después de seleccionar texto en reversa en el editor de código
* `Corrección` Problema de no mostrarse atajos al mantener presionado el ícono de la aplicación AutoJs6 en ciertos dispositivos
* `Corrección` Problema de no respuesta al hacer clic en el botón de confirmación al empaquetar proyectos en ciertos dispositivos
* `Corrección` Problema de uso de parámetros abreviados en app.sendBroadcast y app.startActivity
* `Corrección` Problema de comportamiento anómalo en primeras invocaciones de métodos JsWindow#setPosition y similares del módulo floaty
* `Mejora` Permisos relacionados con Termux añadidos para soportar llamada de Intent para ejecutar comandos ADB _[`issue #136`](http://issues.autojs6.com/136)_
* `Mejora` Capacidad de reutilización de métodos body.string() y body.bytes() en objeto de respuesta del módulo http
* `Mejora` Soporte de empaquetado automático con GitHub Actions (por [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Mejora` Adaptación del entorno de construcción a la plataforma Temurin
* `Dependencia` Actualización de versión de Gradle de 8.4-rc-3 a 8.4
* `Dependencia` Actualización de versión de Android dx de 1.11 a 1.14

# v6.4.0

###### 2023/10/30

* `Nuevo` Soporte para Paddle Lite Engine en el módulo ocr (por [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `Nuevo` Soporte de empaquetado de aplicaciones con plugins embebidos y externos (por [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `Nuevo` Módulo WebSocket (Consulte Documentación del proyecto > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `Nuevo` Módulos barcode y qrcode (Consulte Documentación del proyecto > [Código de Barras](https://docs.autojs6.com/#/barcode) / [Código QR](https://docs.autojs6.com/#/qrcode))
* `Nuevo` Módulo shizuku (Consulte Documentación del proyecto > [Shizuku](https://docs.autojs6.com/#/shizuku)) y permisos en el cajón de la página principal
* `Nuevo` Métodos device.rotation / device.orientation, entre otros
* `Nuevo` Soporte para acceso a propiedades estáticas en clases Java internas
* `Nuevo` Soporte para selección y cambio de idioma de aplicaciones desde la página de configuraciones del sistema en Android 13 y superior
* `Nuevo` Atajos de aplicación activables desde la página de configuración o manteniendo presionado el ícono de la aplicación [Documentación del desarrollador de Android](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn)
* `Corrección` Reintegración de ciertos PR (por [aiselp](https://github.com/aiselp)) para resolver problemas de cierre anómalo de scripts _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Corrección` Problema de uso inoperativo de nuevas API de AutoJs6 en el paquete de la aplicación (por [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `Corrección` Problema de estilo anómalo del paquete de la aplicación en modo noche del sistema
* `Corrección` Problema de pérdida de extensión de archivo al guardar archivo localmente con el plugin VSCode
* `Corrección` Problema de cierre inesperado al usar características de coroutines en el proyecto
* `Corrección` Problema de no registro del estado de posición del botón flotante al reiniciar o salir de la aplicación
* `Corrección` Problema de no obtención de información de configuración actualizada del dispositivo al cambiar la orientación de pantalla _[`issue #153`](http://issues.autojs6.com/153)_
* `Corrección` Problema de fuente pequeña del título de la toolbar al rotar la pantalla a modo horizontal
* `Corrección` Problema de disposición apretada de pestañas en la página principal en modo horizontal
* `Corrección` Problema de desbordamiento de botón flotante al rotar la pantalla múltiples veces _[`issue #90`](http://issues.autojs6.com/90)_
* `Corrección` Problema de que, tras múltiples rotaciones de pantalla, no se restauran las coordenadas del botón flotante ni la orientación del borde de la pantalla
* `Corrección` Problema de visualización faltante o repetida de mensajes en el marco flotante en ciertos dispositivos
* `Corrección` Problema de mensajes flotantes ocultos cuando múltiples scripts se ejecutan simultáneamente _[`issue #67`](http://issues.autojs6.com/67)_
* `Corrección` Problema de cierre inesperado de la aplicación al hacer clic en el menú de análisis de diseño con broadcast
* `Corrección` Problema de segunda instancia y posteriores de WebSocket sin activar adecuadamente los listeners
* `Corrección` Reversión de redireccionamiento global de importPackage para evitar excepciones en algunos ámbitos específicos _[`issue #88`](http://issues.autojs6.com/88)_
* `Corrección` Problema de cierre inesperado de la aplicación al usar la función de copiar o exportar en la página de logs
* `Mejora` Renombrado de la función de exportar en la página de logs a enviar, y reimplementación de exportar acorde a su significado real
* `Mejora` Soporte de recorte automático de entradas al enviar si son demasiado grandes en la página de logs
* `Mejora` Compabilidad de módulo ocr con motores Google MLKit y Paddle Lite (Consulte Documentación del proyecto > [OCR](https://docs.autojs6.com/#/ocr?id=p-mode))
* `Mejora` Mejorada la probabilidad de éxito de arranque automático del servicio de accesibilidad
* `Mejora` Migración de anotaciones de procesamiento de Kotlin desde kapt a KSP
* `Mejora` Soporte de herramientas de construcción para versiones EAP de IntelliJ Idea
* `Mejora` Adaptación de herramientas de construcción a la versión de Java para evitar problemas de "versión inválida"
* `Mejora` Optimización de lógica de downgrade de versión de IDE y plugins relacionados en herramientas de construcción, y aumento de capacidad de predicción de versiones
* `Mejora` Compatibilidad con plugin VSCode 1.0.7
* `Dependencia` Versión Rikka Shizuku 13.1.5 adjunta
* `Dependencia` Versión MLKit Barcode Scanning 17.2.0 adjunta
* `Dependencia` Actualización de versión de OpenCV de 4.5.5 a 4.8.0 (Ref a [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependencia` Actualización de versión de compilación Gradle de 33 a 34
* `Dependencia` Actualización de versión de Gradle de 8.3-rc-1 a 8.4-rc-3
* `Dependencia` Actualización de versión de Apache Commons Lang3 de 3.12.0 a 3.13.0
* `Dependencia` Actualización de versión de Glide de 4.15.1 a 4.16.0
* `Dependencia` Actualización de versión de Android Analytics de 14.3.0 a 14.4.0
* `Dependencia` Actualización de versión de Androidx WebKit de 1.7.0 a 1.8.0
* `Dependencia` Actualización de versión de Androidx Preference de 1.2.0 a 1.2.1
* `Dependencia` Actualización de versión de Androidx Annotation de 1.6.0 a 1.7.0
* `Dependencia` Actualización de versión de Androidx Recyclerview de 1.3.0 a 1.3.2
* `Dependencia` Actualización de versión de Android Material de 1.9.0 a 1.10.0
* `Dependencia` Actualización de versión de Androidx AppCompat de 1.4.2 a 1.6.1
* `Dependencia` Actualización de versión de Commons IO de 2.8.0 a 2.14.0
* `Dependencia` Actualización de versión de Jackson DataBind de 2.13.3 a 2.14.3
* `Dependencia` Remoción de Zeugma Solutions LocaleHelper versión 1.5.1

# v6.3.3

###### 2023/07/21

* `Nuevo` Funcionalidad de comentarios en el código del editor de código (por [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Nuevo` auto.stateListener para escuchar el estado de la conexión en servicios de accesibilidad (por [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Nuevo` Adición de métodos nextSibling / lastChild / offset al tipo UiObject (ver Documentos del Proyecto > [Nodos de Control](https://docs.autojs6.com/#/uiObjectType))
* `Corrección` Problema con el plugin de VSCode que no puede analizar datos cuando el total de caracteres del script supera los cuatro dígitos decimales _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `Corrección` Problema con el plugin de VSCode que no puede guardar archivos correctamente _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `Corrección` El problema de que el menú de botones flotantes "Gestionar servicios de accesibilidad" no redirige a la página correcta al hacer clic
* `Corrección` El problema de que el método runtime.requestPermissions se pierde _[`issue #104`](http://issues.autojs6.com/104)_
* `Corrección` El problema de que events.emitter no soporta parámetros de MainThreadProxy _[`issue #103`](http://issues.autojs6.com/103)_
* `Corrección` El problema de que el editor de código no puede formatear código en _[`pr #78`](http://pr.autojs6.com/78)_
* `Corrección` El problema de desbordamiento de la pila de llamadas del ClassLoader cuando se usa JavaAdapter _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `Mejora` Ajuste del ámbito del módulo (por [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Mejora` Eliminación de la verificación de firma al iniciar la versión de lanzamiento de la aplicación (por [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `Mejora` Mejoras en la funcionalidad de comentarios en el editor de código basada en _[`pr #98`](http://pr.autojs6.com/98)_ manejando comportamiento, estilo y posición del cursor
* `Mejora` Adición del elemento de menú de comentarios de código basada en _[`pr #98`](http://pr.autojs6.com/98)_
* `Mejora` Compatibilidad con el plugin de VSCode 1.0.6
* `Mejora` Adición del soporte para el parámetro de grado en el método UiObject#parent (ver Documentos del Proyecto > [Nodos de Control](https://docs.autojs6.com/#/uiObjectType))
* `Dependencia` Actualización de la versión de Gradle 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `Nuevo` Módulo crypto (ver Documentos del Proyecto > [Cifrado](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `Nuevo` Adición de controles como textswitcher / viewswitcher / viewflipper / numberpicker / video / search en el modo UI
* `Nuevo` Adición de funcionalidades de copiar y exportar en la página de actividades del registro _[`issue #76`](http://issues.autojs6.com/76)_
* `Nuevo` Adición de la funcionalidad de historial de direcciones IP en el modo cliente
* `Corrección` El problema de que la dirección IP no se muestra después de la conexión automática en modo cliente o la activación automática en modo servidor
* `Corrección` El problema de desconexión y la imposibilidad de volver a conectar después de cambiar el idioma o el modo nocturno en modo cliente y servidor
* `Corrección` El problema de que no se puede usar puertos personalizados al ingresar la dirección de destino en el modo cliente
* `Corrección` El problema de que ciertos caracteres pueden causar que AutoJs6 se bloquee al ingresar la dirección de destino en modo cliente
* `Corrección` El problema de falta de respuesta de los comandos remotos del plugin de VSCode debido a la falla en el análisis (intento de reparación)
* `Corrección` El problema de no poder obtener detalles de la versión en Android 7.x al descubrir una nueva versión
* `Corrección` El problema de que images.pixel no puede obtener el valor de color de píxel de la captura de pantalla del servicio de accesibilidad _[`issue #73`](http://issues.autojs6.com/73)_
* `Corrección` El problema de no poder usar atributos de control predefinidos en controles nativos de Android en el modo UI (con inicial mayúscula)
* `Corrección` El problema de que sólo el primer archivo es efectivo al cargar múltiples archivos con runtime.loadDex/loadJar _[`issue #88`](http://issues.autojs6.com/88)_
* `Corrección` El problema de que solo se muestra el icono de documentos en el lanzador en ciertos dispositivos después de instalar la aplicación (intento de reparación) _[`issue #85`](http://issues.autojs6.com/85)_
* `Mejora` Compatibilidad con el plugin de VSCode 1.0.5
* `Mejora` Soporte para el módulo cheerio (Ref a [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `Mejora` El soporte de instancias de JsWebSocket para usar el método rebuild y reconectar _[`issue #69`](http://issues.autojs6.com/69)_
* `Mejora` El soporte del módulo base64 para codificar y decodificar matrices numéricas y matrices de bytes Java
* `Mejora` Soporte para JavaMail for Android _[`issue #71`](http://issues.autojs6.com/71)_
* `Mejora` Uso del tipo de datos Blob para mejorar la adaptabilidad en entornos de red sin proxy al obtener información de actualización de versión
* `Mejora` Muestra la dirección IP objetivo en la subtítulo del cajón principal durante la conexión en modo cliente
* `Mejora` Soporte para mostrar advertencias de entradas inválidas al ingresar la dirección de destino en el modo cliente
* `Mejora` Soporte para usar la tecla Enter del teclado suave para establecer la conexión en modo cliente
* `Mejora` Mantenimiento del estado activo del modo servidor después de activarlo a menos que sea cerrado manualmente o termine el proceso de la aplicación _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `Mejora` Implementación de detección de versión bidireccional entre AutoJs6 y el plugin de VSCode con alertas de resultados anómalos _[`issue #89`](http://issues.autojs6.com/89)_
* `Mejora` Adición de permiso de lectura de SMS (android.permission.READ_SMS) (deshabilitado por defecto)
* `Mejora` Implementación del método findMultiColors internamente (por [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `Mejora` El soporte para runtime.loadDex/loadJar/load para cargar múltiples archivos o directorios a nivel del directorio
* `Dependencia` Actualización de la versión de LeakCanary 2.11 -> 2.12
* `Dependencia` Actualización de la versión de Android Analytics 14.2.0 -> 14.3.0
* `Dependencia` Actualización de la versión de Gradle 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `Nuevo` Permiso de notificación de publicación y conmutador en el cajón principal _[`issue #55`](http://issues.autojs6.com/55)_
* `Nuevo` El soporte para el análisis de diseño simple de Android en el modo UI (ver Código de Ejemplo > Diseño > Diseño simple de Android)
* `Nuevo` Adición de controles como console / imagebutton / ratingbar / switch / textclock / togglebutton en el modo UI
* `Nuevo` El soporte para el tipo de color [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) en los controles del modo UI (por ejemplo, color="orange")
* `Nuevo` El soporte total del método attr para establecer propiedades de controles en el modo UI (por ejemplo, ui.text.attr('color', 'blue'))
* `Nuevo` Soporte para valores de tipo booleano en atributos de control del modo UI de forma abreviada (por ejemplo, clickable="true" se puede abrevinar como clickable o isClickable)
* `Nuevo` Soporte para los atributos booleanos isColored y isBorderless de controles button
* `Nuevo` Método console.resetGlobalLogConfig para restablecer la configuración global del registro
* `Nuevo` Método web.newWebSocket para crear una instancia de Web Socket (ver Documentos del Proyecto > [World Wide Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `Corrección` Problema de ordenación de carpetas en el gestor de archivos
* `Corrección` El problema de no poder ajustar el estilo y la posición del cuadro flotante construido con el módulo floaty _[`issue #60`](http://issues.autojs6.com/60)_
* `Corrección` El problema de la superposición del cuadro flotante construido con el módulo floaty con la barra de estado del sistema
* `Corrección` El problema de que el método http.postMultipart no funciona correctamente _[`issue #56`](http://issues.autojs6.com/56)_
* `Corrección` El problema de que no se puede ejecutar ningún script en Android 7.x _[`issue #61`](http://issues.autojs6.com/61)_
* `Corrección` El problema de no poder construir el proyecto cuando no existe el archivo sign.property
* `Corrección` El problema de que la aplicación AutoJs6 falla al estar en segundo plano en sistemas con versiones altas debido a falta de permiso de notificación en primer plano (API >= 33)
* `Corrección` El problema de que el botón FAB no puede borrar el registro después de llamar al método console.show
* `Corrección` La excepción de puntero nulo en prototype durante la depuración en el editor de scripts
* `Corrección` Ejecución del script temporal en el directorio del caché durante la ejecución en el editor de scripts para evitar posibles pérdidas de contenido del script en lugar de guardar y ejecutar en la ubicación original
* `Corrección` Ajuste del ancho de la barra de color de nivel en el análisis de jerarquía de diseño para evitar que los nombres de los controles no se muestren cuando haya demasiados niveles _[`issue #46`](http://issues.autojs6.com/46)_
* `Mejora` Adición del botón de salida en la ventana flotante del análisis de diseño para cerrar la ventana _[`issue #63`](http://issues.autojs6.com/63)_
* `Mejora` Uso de rutas absolutas de scripts en forma abreviada para reducir la longitud del texto y mejorar la legibilidad
* `Mejora` Reemplazo de Error por Exception para evitar fallos de la aplicación AutoJs6 en caso de excepciones
* `Mejora` Migración del método de vínculo de vista de ButterKnife a View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `Mejora` Reactivación automática del modo servidor al iniciar AutoJs6 después de un cierre inesperado _[`issue #64`](http://issues.autojs6.com/64)_
* `Mejora` Reconexión automática al iniciar AutoJs6 después de un cierre inesperado en modo cliente usando la dirección de la última conexión histórica
* `Dependencia` Actualización de la versión de LeakCanary 2.10 -> 2.11
* `Dependencia` Actualización de la versión de Android Material 1.8.0 -> 1.9.0
* `Dependencia` Actualización de la versión de Androidx WebKit 1.6.1 -> 1.7.0
* `Dependencia` Actualización de OkHttp3 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `Dependencia` Actualización de la versión de MLKit Text Recognition Chinese 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `Nuevo` Módulo ocr (ver Documentos del Proyecto > [Reconocimiento Óptico de Caracteres](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Nuevo` Módulo notice (ver Documentos del Proyecto > [Notificaciones](https://docs.autojs6.com/#/notice))
* `Nuevo` Módulo s13n (ver Documentos del Proyecto > [Estandarización](https://docs.autojs6.com/#/s13n))
* `Nuevo` Módulo Color (ver Documentos del Proyecto > [Clase de Color](https://docs.autojs6.com/#/colorType))
* `Nuevo` Funcionalidad de mantener la pantalla despierta en primer plano y opciones de configuración
* `Nuevo` Lanzador de documentos adicional para una lectura independiente de la aplicación de documentos (opción para ocultar o mostrar en la configuración)
* `Corrección` Problemas con la funcionalidad del método colors.toString
* `Corrección` Problemas con la funcionalidad de agregar automáticamente el prefijo de protocolo en el método app.openUrl
* `Corrección` Comportamiento anómalo de los métodos app.viewFile/editFile al no existir el archivo correspondiente en los parámetros
* `Corrección` Problema de que el callback de pickup no se llama
* `Corrección` Problema donde los valores negativos en la propiedad bounds mostrados en el análisis de diseño son reemplazados por comas
* `Corrección` Problemas con los selectores bounds/boundsInside/boundsContains al filtrar rectángulos nulos (por ejemplo, rectángulos con límites invertidos) _[`issue #49`](http://issues.autojs6.com/49)_
* `Corrección` Problema de que al cambiar el tema o el idioma al hacer clic o mantener presionado el tab de documentos de la página principal causa que la aplicación se bloquee
* `Corrección` Problema de temblor al ajustar el tamaño de la fuente con pellizco de dos dedos en el editor de texto
* `Corrección` Problemas para descargar algunas fuentes de dependencias en el script de construcción (integradas) _[`issue #40`](http://issues.autojs6.com/40)_
* `Corrección` Problema de que Tasker no puede agregar el plugin de acción de AutoJs6 (intento de reparación) _[`issue #41`](http://issues.autojs6.com/41)_
* `Corrección` Problema de que las anotaciones de ButterKnife no pueden resolver los ID de recursos en proyectos compilados con versiones JDK más altas _[`issue #48`](http://issues.autojs6.com/48)_
* `Corrección` Problema de que el servicio de accesibilidad tiende a fallar o tener errores de servicio (intento de reparación)
* `Corrección` Problemas con el uso del parámetro size en images.medianBlur según la documentación
* `Corrección` Problema de que al mostrar el nombre completo del script en el módulo engines se pierde el punto entre el nombre del archivo y su extensión
* `Corrección` Posibles errores de cálculo en la implementación interna del algoritmo de detección de distancia RGB ponderada (intento de reparación)
* `Corrección` Problemas con los métodos relacionados al flotante del módulo console antes de llamar al método show
* `Corrección` Problemas con la efectividad de los métodos como console.setSize _[`issue #50`](http://issues.autojs6.com/50)_
* `Corrección` Problemas de asignación incorrecta de los valores de color de colors.material
* `Corrección` Problemas de análisis del formato de fechas en los atributos minDate y maxDate de los controles de selección de fecha en el modo UI
* `Corrección` Problema de que aparecen dos tareas ejecutándose en el tab de "Tareas" de la página principal después de ejecutar un script rápidamente
* `Corrección` Problemas con el estado reseteado de la página de gestión de archivos al volver desde otras páginas _[`issue #52`](http://issues.autojs6.com/52)_
* `Corrección` Problemas de sincronización del estado de clasificación con el estado de iconos en la página de gestión de archivos
* `Mejora` Añadir la visualización de la fecha de modificación de archivos y carpetas en la página de gestión de archivos
* `Mejora` Soporte para recordar el tipo de clasificación en la página de gestión de archivos
* `Mejora` Añadir secciones de construcción del proyecto y ayuda para el desarrollo de scripts en el README.md _[`issue #33`](http://issues.autojs6.com/33)_
* `Mejora` Soporte para múltiples formas de pasar el parámetro de región en los métodos relacionados del módulo images (ver Documentos del Proyecto > [Tipos Omni](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Mejora` Soporte para abreviaciones como pref/homepage/docs/about en los parámetros del método app.startActivity
* `Mejora` Montar métodos globales del módulo web dentro del mismo para mejorar la usabilidad (ver Documentos del Proyecto > [World Wide Web](https://docs.autojs6.com/#/web))
* `Mejora` Implementación predeterminada de algunas configuraciones comunes de WebView en el método web.newInjectableWebView
* `Mejora` Añadir métodos de conversión y herramientas en el módulo colors, junto con más constantes estáticas y nombres de colores utilizables directamente como parámetros
* `Mejora` Añadir varias configuraciones de estilo para la ventana flotante del módulo console, además de un constructor build para configuraciones unificadas
* `Mejora` Soporte de arrastrar el área del título para mover la posición de la ventana flotante del console
* `Mejora` Soporte para cierre automático de la ventana flotante del console después de un retraso tras terminar el script
* `Mejora` Soporte para suavizar la fuente en la ventana de la actividad de la consola y en su ventana flotante
* `Mejora` Soporte para parámetros de tiempo de espera (timeout) en métodos relacionados del módulo http
* `Mejora` Soporte para degradar la versión de JDK en el script de construcción de Gradle (fallback)
* `Mejora` Soporte para seleccionar automáticamente la versión adecuada de las herramientas de construcción según el tipo de plataforma y versión (limitado en cierto grado)
* `Dependencia` Versión localizada de Auto.js APK Builder 1.0.3
* `Dependencia` Versión localizada de MultiLevelListView 1.1
* `Dependencia` Versión localizada de Settings Compat 1.1.5
* `Dependencia` Versión localizada de Enhanced Floaty 0.31
* `Dependencia` Inclusión de MLKit Text Recognition Chinese versión 16.0.0-beta6
* `Dependencia` Actualización de Gradle 8.0-rc-1 -> 8.2-milestone-1
* `Dependencia` Actualización de Android Material 1.7.0 -> 1.8.0
* `Dependencia` Actualización de Glide 4.14.2 -> 4.15.1
* `Dependencia` Actualización de Joda Time 2.12.2 -> 2.12.5
* `Dependencia` Actualización de Android Analytics 14.0.0 -> 14.2.0
* `Dependencia` Actualización de Androidx WebKit 1.5.0 -> 1.6.1
* `Dependencia` Actualización de Androidx Recyclerview 1.2.1 -> 1.3.0
* `Dependencia` Actualización de Zip4j 2.11.2 -> 2.11.5
* `Dependencia` Actualización de Junit Jupiter 5.9.2 -> 5.9.3
* `Dependencia` Actualización de Androidx Annotation 1.5.0 -> 1.6.0
* `Dependencia` Actualización de Jackson DataBind 2.14.1 -> 2.14.2
* `Dependencia` Actualización de Desugar JDK Libs 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `Nuevo` Rediseño y reescritura de la documentación del proyecto (parcialmente completado)
* `Nuevo` Adaptación multilingüe para español/francés/ruso/árabe/japonés/coreano/inglés/chino tradicional, etc.
* `Nuevo` Opciones de configuración de la ruta de trabajo con selección de ruta/historial de rutas/sugerencias inteligentes de valores predeterminados
* `Nuevo` El administrador de archivos soporta la navegación al directorio superior (hasta el directorio 'Almacenamiento interno')
* `Nuevo` El administrador de archivos permite establecer cualquier directorio como ruta de trabajo rápidamente
* `Nuevo` Función de actualización de software y gestión de actualizaciones ignoradas
* `Nuevo` El editor de texto admite ajuste de tamaño de fuente a través de pinch zoom
* `Nuevo` Selector idHex (UiSelector#idHex) (ver Documentos del Proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType))
* `Nuevo` Selector de acción (UiSelector#action) (ver Documentos del Proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType))
* `Nuevo` Selector Match series (UiSelector#xxxMatch) (ver Documentos del Proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType))
* `Nuevo` Selector de recolección (UiSelector#pickup) (ver Documentos del Proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `Nuevo` Detección de control (UiObject#detect) (ver Documentos del Proyecto > [Nodo de Control](https://docs.autojs6.com/#/uiObjectType))
* `Nuevo` Brújula de control (UiObject#compass) (ver Documentos del Proyecto > [Nodo de Control](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `Nuevo` Método de espera global wait (ver Documentos del Proyecto > [Objeto Global](https://docs.autojs6.com/#/global?id=m-wait))
* `Nuevo` Métodos globales de escalado cX/cY/cYx (ver Documentos del Proyecto > [Objeto Global](https://docs.autojs6.com/#/global?id=m-wait))
* `Nuevo` Tipo de aplicación global App (ver Documentos del Proyecto > [Enumeraciones de Aplicación](https://docs.autojs6.com/#/appType))
* `Nuevo` Módulo i18n (solución multilingüe basada en banana-i18n para JavaScript) (ver Documentos del Proyecto > Internacionalización)
* `Corrección` Problemas con el cambio de idioma de la aplicación que provocan parpadeo de texto y fallos de botones en algunas páginas
* `Corrección` Problema donde la barra de herramientas del proyecto no se mostraba al iniciar la aplicación con una ruta de trabajo establecida
* `Corrección` Problema donde la ruta de trabajo podría cambiar automáticamente al cambiar el idioma _[`issue #19`](http://issues.autojs6.com/19)_
* `Corrección` Retraso significativo al iniciar tareas programadas (intento de solución) _[`issue #21`](http://issues.autojs6.com/21)_
* `Corrección` Problema donde los módulos internos no funcionaban correctamente debido a la declaración de nombres de módulos JavaScript sobreescritos _[`issue #29`](http://issues.autojs6.com/29)_
* `Corrección` Problema en sistemas Android más recientes donde los iconos del panel de configuración rápida no se cerraban automáticamente al ser tocados (intento de solución) _[`issue #7`](http://issues.autojs6.com/7)_
* `Corrección` Problema de superposición de algunas páginas con la barra de notificaciones en sistemas Android más recientes
* `Corrección` Problema donde el código de ejemplo que configura el color del pincel no funcionaba correctamente en Android 10 y versiones superiores
* `Corrección` Corrección del nombre del archivo y funcionalidad de 'File Manager' en el ejemplo 'Music Manager'
* `Corrección` Problemas de desplazamiento al refrescar hacia abajo en el administrador de archivos
* `Corrección` Problemas con el módulo ui que ocasionaban errores de acceso a propiedades de componentes en scripts basados en UI
* `Corrección` Problema donde la grabación de scripts se perdía al hacer clic fuera del cuadro de diálogo de entrada de nombre de archivo
* `Corrección` Problemas con la capacidad de cambiar el tamaño del texto en documentos cuando los encabezados superaban el ancho de la pantalla
* `Corrección` Problemas con el desplazamiento horizontal en áreas de código de ejemplo dentro de la documentación
* `Corrección` Problemas con la funcionalidad de refrescar haciendo swipe down en la página de documentación (intento de solución)
* `Corrección` Problema donde la sincronización nocturna en el cajón de la página principal fallaba al inicializar la aplicación
* `Corrección` Problemas con el modo nocturno habilitado que causaba apertura forzada del modo nocturno al iniciar la aplicación
* `Corrección` Problema donde el color de tema predeterminado no aplicaba correctamente en modo nocturno
* `Corrección` Problema donde algunos textos en la configuración eran ilegibles en modo nocturno debido al color de fondo coincidente
* `Corrección` Problemas con la longitud del texto del botón en la página 'Acerca de'
* `Corrección` Problemas con la superposición del texto y botones en el cajón de la página principal
* `Corrección` Problemas de sincronización en el cajón de la página principal después de cerrar los diálogos de permiso
* `Corrección` Problema donde no se mostraba el diálogo ADB Tools después de fallar al cambiar la configuración de permisos con root
* `Corrección` Problema donde la primera vez que se usa la opción de mostrar la posición del cursor con permisos root, mostraba un error de permisos
* `Corrección` Problemas con la disposición de los elementos de iconos en la página de selección de iconos
* `Corrección` Problemas de parpadeo en el editor de texto al iniciarse si el modo nocturno estaba habilitado (intento de arreglo)
* `Corrección` Problema con el valor máximo permitido al ajustar el tamaño de la fuente en el editor de texto
* `Corrección` Problemas con la incapacidad de mostrar la duración de ejecución en el registro de logs al finalizar el script en algunos sistemas Android
* `Corrección` Problemas con el botón de flotación que permanecía visible después de reiniciar la aplicación incluso después de cerrarlo
* `Corrección` Problemas con menúes desbordando la pantalla al mantener presionada una lista en el análisis de jerarquía de capas
* `Corrección` Problemas de reconocimiento de botones en la barra de navegación en sistemas Android 7.x con el modo nocturno deshabilitado
* `Corrección` Problema de excepciones no cerradas en métodos como http.post
* `Corrección` Problema donde la información del canal Alpha se perdía en colores con canal Alpha de 0 en el método colors.toString
* `Mejora` Redireccionar clases públicas de Auto.js 4.x para lograr la máxima compatibilidad descendente posible (limitada).
* `Mejora` Fusionar todos los módulos del proyecto para evitar posibles problemas de referencia circular (eliminar temporalmente el módulo inrt).
* `Mejora` Migrar la configuración de construcción de Gradle de Groovy a KTS.
* `Mejora` Agregar soporte multilingüe para mensajes de excepción de Rhino.
* `Mejora` El interruptor de permisos del cajón de la página principal solo muestra mensajes cuando está activado.
* `Mejora` La disposición del cajón de la página principal se adhiere directamente debajo de la barra de estado para evitar incompatibilidades de la barra de color en la parte superior.
* `Mejora` Compatibilidad de funciones de comprobación de actualizaciones, descarga y aviso de actualización con el sistema Android 7.x.
* `Mejora` Rediseñar la página de configuración (migración a AndroidX).
* `Mejora` La página de configuración admite la presión prolongada sobre las opciones de configuración para obtener información detallada.
* `Mejora` Adición de la opción "Seguir sistema" para el modo nocturno (Android 9+).
* `Mejora` Compatibilidad de la pantalla de inicio de la aplicación con el modo nocturno.
* `Mejora` Agregar identificadores numéricos a los iconos de la aplicación para mejorar la experiencia del usuario con múltiples versiones de código abierto coexistentes.
* `Mejora` Agregar más opciones de colores Material Design al tema de la aplicación.
* `Mejora` Aligerar y adaptar los iconos de elementos de lista en el gestor de archivos/panel de tareas al color del tema.
* `Mejora` Compatibilidad del color del texto de sugerencia en la caja de búsqueda de la página principal con el modo nocturno.
* `Mejora` Compatibilidad de componentes como diálogos/textos/Fab/AppBar/elementos de lista con el modo nocturno.
* `Mejora` Compatibilidad de páginas como documentos/configuración/sobre/colores del tema/análisis de diseño y menús de botones flotantes con el modo nocturno.
* `Mejora` Compatibilidad del diseño de páginas con la disposición RTL (Right-To-Left) en la medida de lo posible.
* `Mejora` Agregar efectos de animación de iconos a la página sobre.
* `Mejora` Actualización automática del año en el texto de la declaración de derechos de autor en la página sobre.
* `Mejora` Después de la instalación inicial de la aplicación, determinar y establecer automáticamente un directorio de trabajo adecuado.
* `Mejora` Desactivar la función de zoom a dos dedos en la página de documentos para evitar la visualización anormal del contenido.
* `Mejora` Simplificar el nombre y la ruta de las tareas mostradas en los elementos de la lista del panel de tareas por ruta relativa.
* `Mejora` Abreviar el texto de los botones del editor de texto para evitar desbordamiento del contenido.
* `Mejora` Soporte para restaurar el tamaño de fuente predeterminado en la configuración del editor de textos.
* `Mejora` Mejorar la velocidad de respuesta al hacer clic en el botón flotante.
* `Mejora` Hacer clic en el botón de análisis de diseño del botón flotante realiza directamente el análisis de rango de diseño.
* `Mejora` El tema de análisis de diseño es adaptable (la ventana flotante sigue el tema de la aplicación, el panel de configuración rápida sigue el tema del sistema).
* `Mejora` Reordenar la lista de información de control de diseño según la posible frecuencia de uso.
* `Mejora` Optimizar el formato de salida automáticamente al hacer clic y copiar sobre información de control de diseño según el tipo de selector.
* `Mejora` Al seleccionar archivos usando la ventana flotante, presionar la tecla de retorno lleva al directorio superior en lugar de cerrar directamente la ventana.
* `Mejora` Soporte para detección de validez numérica y conversión automática de símbolos punteados mientras se ingresa una dirección en modo cliente conectándose a la computadora.
* `Mejora` Mostrar la dirección IP del dispositivo correspondiente en el cajón de la página principal después de que el cliente y servidor se conecten.
* `Mejora` Agregar protección contra sobrescritura a ciertos objetos globales y módulos integrados (consultar documentos del proyecto > Objetos globales > [Protección contra sobrescritura](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4)).
* `Mejora` importClass e importPackage admiten parámetros de cadena y parámetros de longitud variable.
* `Mejora` ui.run admite la impresión de información de seguimiento de pila en caso de excepción.
* `Mejora` ui.R y auto.R pueden obtener cómodamente el ID de recursos de AutoJs6.
* `Mejora` Los métodos relacionados con el uso de aplicaciones en el módulo app admiten parámetros de tipo App y parámetros de alias de aplicación.
* `Mejora` Los métodos relacionados con la devolución de llamada asincrónica en el módulo dialogs admiten la omisión de parámetros pre-rellenos.
* `Mejora` app.startActivity y otros admiten parámetros de opción de URL (ver código de ejemplo > Aplicaciones > Intención).
* `Mejora` El módulo device devuelve null en lugar de lanzar una excepción cuando la obtención de IMEI o número de serie de hardware falla.
* `Mejora` Aumentar el brillo del texto en el log de ventana flotante que muestra console.show para mejorar la legibilidad del contenido.
* `Mejora` ImageWrapper#saveTo admite guardar archivos de imagen en rutas relativas.
* `Mejora` Rediseñar el objeto global colors e incluir el soporte de modos de color HSV / HSL (consultar documentos del proyecto > [Colores](https://docs.autojs6.com/#/color)).
* `Dependencia` Actualización de Gradle Compile versión 32 a 33
* `Dependencia` Localización de la versión 1.4.3 de Android Job
* `Dependencia` Localización del Plugin SDK para Locale versión 9.0.0 de Android
* `Dependencia` Localización de GitHub API versión 1.306
* `Dependencia` Añadida la versión 1.0 de JCIP Annotations
* `Dependencia` Añadida la versión 1.5.0 de Androidx WebKit
* `Dependencia` Añadida la versión 2.8.0 de Commons IO
* `Dependencia` Añadida la versión 2.0.0 de Desugar JDK Libs
* `Dependencia` Añadida la versión 2.13.3 de Jackson DataBind
* `Dependencia` Añadida la versión 2.1.0 de Jaredrummler Android Device Names
* `Dependencia` Añadida la versión 1.0.6 de Jaredrummler Animated SVG View
* `Dependencia` Sustitución de ColorPicker versión 2.1.7 de Jrummyapps por ColorPicker versión 1.1.0 de Jaredrummler
* `Dependencia` Actualización de la versión de Gradle de 7.5-rc-1 a 8.0-rc-1
* `Dependencia` Actualización de la versión de las herramientas de construcción de Gradle de 7.4.0-alpha02 a 8.0.0-alpha09
* `Dependencia` Actualización de la versión de Kotlin Gradle Plugin de 1.6.10 a 1.8.0-RC2
* `Dependencia` Actualización de la versión de Android Material de 1.6.0 a 1.7.0
* `Dependencia` Actualización de la versión de Androidx Annotation de 1.3.0 a 1.5.0
* `Dependencia` Actualización de la versión de Androidx AppCompat de 1.4.1 a 1.4.2
* `Dependencia` Actualización de la versión de Android Analytics de 13.3.0 a 14.0.0
* `Dependencia` Actualización de la versión de Gson de 2.9.0 a 2.10
* `Dependencia` Actualización de la versión de Joda Time de 2.10.14 a 2.12.1
* `Dependencia` Actualización de la versión de Kotlinx Coroutines de 1.6.1-native-mt a 1.6.1
* `Dependencia` Actualización de la versión de OkHttp3 de 3.10.0 a 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `Dependencia` Actualización de la versión de Zip4j de 2.10.0 a 2.11.2
* `Dependencia` Actualización de la versión de Glide de 4.13.2 a 4.14.2
* `Dependencia` Actualización de la versión de Junit Jupiter de 5.9.0 a 5.9.1

# v6.1.1

###### 2022/05/31

* `Nuevo` Función de actualización de software (ver página de configuración) (no compatible con Android 7.x por el momento)
* `Corrección` Problema de lectura y escritura en almacenamiento externo en Android 10 _[`issue #17`](http://issues.autojs6.com/17)_
* `Corrección` Problema que causaba que la aplicación se cerrara al mantener presionado en la página del editor _[`issue #18`](http://issues.autojs6.com/18)_
* `Corrección` Problemas con las funciones de 'Eliminar línea' y 'Copiar línea' al mantener presionado en la página del editor
* `Corrección` Función 'Pegar' faltante en el menú de opciones en la página del editor
* `Mejora` Recursos de cadena de mensajes de error parcial traducidos (en / zh)
* `Mejora` Ajuste de los botones y adición de diferenciación de color en el diálogo de contenido no guardado
* `Dependencia` Añadida la versión 1.306 de github-api
* `Dependencia` Sustitución de retrofit2-rxjava2-adapter versión 1.0.0 por adapter-rxjava2 versión 2.9.0

# v6.1.0

###### 2022/05/26 - Cambio de nombre de paquete, actualiza con precaución

* `Sugerencia` Cambio del nombre del paquete de la aplicación a org.autojs.autojs6 para evitar conflictos con el nombre del paquete de la aplicación Auto.js de código abierto
* `Nuevo` Añadido el interruptor de permiso de proyección de medios en el cajón de la página principal (vía Root / ADB) (detección de estado del interruptor experimental)
* `Nuevo` El administrador de archivos soporta mostrar archivos y carpetas ocultos (ver Configuración)
* `Nuevo` Inclusión de una función de verificación de Root (ver página de configuración y código de ejemplo)
* `Nuevo` Módulo autojs (ver Código de Ejemplo > AutoJs6)
* `Nuevo` Módulo tasks (ver Código de Ejemplo > Tareas)
* `Nuevo` Método console.launch() para lanzar la página de la actividad de log
* `Nuevo` Utilidad util.morseCode (ver Código de Ejemplo > Herramientas > Código Morse)
* `Nuevo` Utilidad util.versionCodes (ver Código de Ejemplo > Herramientas > Información de versión de Android)
* `Nuevo` Métodos util.getClass() y similares (ver Código de Ejemplo > Herramientas > Obtener clase y nombre de clase)
* `Nuevo` Método timers.setIntervalExt() (ver Código de Ejemplo > Temporizadores > Ejecución periódica condicionada)
* `Nuevo` Métodos colors.toInt() y rgba() (ver Código de Ejemplo > Imágenes y Colores > Conversión básica de colores)
* `Nuevo` Método automator.isServiceRunning() / ensureService()
* `Nuevo` Métodos automator.lockScreen() y similares (ver Código de Ejemplo > Servicios de Accesibilidad > Nuevo en Android 9)
* `Nuevo` Métodos automator.headsethook() y similares (ver Código de Ejemplo > Servicios de Accesibilidad > Nuevo en Android 11)
* `Nuevo` Método automator.captureScreen() (ver Código de Ejemplo > Servicios de Accesibilidad > Captura de Pantalla)
* `Nuevo` Opciones de propiedad animation y linkify en dialogs.build() (ver Código de Ejemplo > Cuadros de diálogo > Personalización del cuadro de diálogo)
* `Corrección` Solución a las anomalías en las propiedades inputHint y itemsSelectedIndex en dialogs.build()
* `Corrección` Solución a problemas con el parámetro de callback de JsDialog#on('multi_choice')
* `Corrección` Corrección del problema donde UiObject#parent().indexInParent() siempre devolvía -1 _[`issue #16`](http://issues.autojs6.com/16)_
* `Corrección` Resolución del problema donde las promesas Thenable de Promise.resolve() podrían no ser llamadas cerca del final del script
* `Corrección` Corrección de posibles errores ortográficos en nombres de paquetes o clases (boardcast -> broadcast / auojs -> autojs)
* `Corrección` Solución al problema que causaba que images.requestScreenCapture() provocara un fallo de la aplicación en versiones recientes de Android (API >= 31)
* `Corrección` Solución al problema donde múltiples instancias de scripts solicitando images.requestScreenCapture() simultáneamente podrían causar un fallo de la aplicación
* `Corrección` Solución al problema de posible congelación al llamar a new RootAutomator()
* `Mejora` RootAutomator no podrá ser instanciado sin permisos de Root
* `Mejora` Rediseño de la página 'Sobre la Aplicación y el Desarrollador'
* `Mejora` Refactorización de todos los módulos integrados de JavaScript
* `Mejora` Refactorización de todos los scripts de construcción de Gradle y adición de un script de configuración común (config.gradle)
* `Mejora` Soporte de las herramientas de construcción de Gradle para gestión automática de versiones y nombrado automático de archivos de construcción
* `Mejora` Las herramientas de construcción de Gradle ahora incluyen la tarea de agregar CRC32 al nombre de los archivos de construcción (appendDigestToReleasedFiles)
* `Mejora` Las llamadas a shell() ahora escribirán excepciones en el resultado en lugar de lanzarlas directamente (sin necesidad de try/catch)
* `Mejora` Reemplazo del módulo json2 por JSON integrado de Rhino
* `Mejora` El método auto.waitFor() ahora soporta un parámetro de timeout
* `Mejora` El método threads.start() ahora soporta parámetros de funciones de flecha
* `Mejora` El método console.trace() ahora soporta niveles de log (ver Código de Ejemplo > Consola > Trazar la pila de llamadas)
* `Mejora` El método device.vibrate() ahora soporta modos de vibración y vibración de código Morse (ver Código de Ejemplo > Dispositivo > Modo de vibración / Vibración de código Morse)
* `Mejora` Adaptación de permisos de lectura/escritura en almacenamiento externo para versiones recientes de Android (API >= 30)
* `Mejora` Fuente de la consola usa Material Color para mejorar la legibilidad en temas normales y nocturnos
* `Mejora` Guardar todas las instancias de ImageWrapper como referencias débiles y recolectarlas automáticamente al finalizar el script (experimental)
* `Dependencia` Añadida la versión 3.1.0 de CircleImageView
* `Dependencia` Actualización de Android Analytics de la versión 13.1.0 a 13.3.0
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 7.3.0-alpha06 a 7.4.0-alpha02
* `Dependencia` Actualización de Android Job de la versión 1.4.2 a 1.4.3
* `Dependencia` Actualización de Android Material de la versión 1.5.0 a 1.6.0
* `Dependencia` Actualización de CrashReport de la versión 2.6.6 a 4.0.4
* `Dependencia` Actualización de Glide de la versión 4.13.1 a 4.13.2
* `Dependencia` Actualización de Joda Time de la versión 2.10.13 a 2.10.14
* `Dependencia` Actualización del plugin de Gradle de Kotlin de la versión 1.6.10 a 1.6.21
* `Dependencia` Actualización de Kotlinx Coroutines de la versión 1.6.0 a 1.6.1-native-mt
* `Dependencia` Actualización de LeakCanary de la versión 2.8.1 a 2.9.1
* `Dependencia` Actualización de OkHttp3 de la versión 5.0.0-alpha.6 a 5.0.0-alpha.7
* `Dependencia` Actualización del motor Rhino de la versión 1.7.14 a 1.7.15-SNAPSHOT
* `Dependencia` Actualización de Zip4j de la versión 2.9.1 a 2.10.0
* `Dependencia` Eliminación de Groovy JSON versión 3.0.8
* `Dependencia` Eliminación de Kotlin Stdlib JDK7 versión 1.6.21

# v6.0.3

###### 2022/03/19

* `Nuevo` Soporte para cambio de idioma (aún en desarrollo)
* `Nuevo` Módulo recorder (ver Código de Ejemplo > Temporizadores)
* `Nuevo` Uso del permiso "Modificar configuración de seguridad" para activar servicios de accesibilidad y configuración de interruptores automáticamente
* `Corrección` Solución al problema del panel de configuración rápida que no se retraía automáticamente tras tocar los iconos relevantes (intento de solución) _[`issue #7`](http://issues.autojs6.com/7)_
* `Corrección` Solución al posible fallo de AutoJs6 al usar parámetros de "mostrar con fuerza" en toast
* `Corrección` Solución al posible fallo de AutoJs6 debido a información incompleta en los encabezados de datos de transmisión por Socket
* `Mejora` Activar automáticamente el servicio de accesibilidad según configuración al iniciar o reiniciar AutoJs6
* `Mejora` Intentar activar automáticamente el servicio de accesibilidad al activar el botón flotante
* `Mejora` Completar elementos de traducción al inglés en todos los archivos de recursos
* `Mejora` Ajustes menores en el diseño del cajón de la página principal para reducir el espacio entre elementos del proyecto
* `Mejora` Añadido interruptor de estado del servicio en primer plano en el cajón de la página principal
* `Mejora` Sincronizar automáticamente el estado del interruptor cuando el cajón de la página principal se despliega
* `Mejora` Detección de estado y notificación de resultados al mostrar la posición del cursor
* `Mejora` Soporte para sistemas operativos de 64 bits (gracias a [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Mejora` Aplicar configuración de transparencia al inicializar el botón flotante (sin necesidad de clics adicionales)
* `Mejora` Detección y aviso al intentar reasignar contenido de archivos de ejemplos de código
* `Mejora` Cambio de dirección de descarga de plugins empaquetados de GitHub a JsDelivr
* `Dependencia` Añadida la versión 1.5.1 de Zeugma Solutions LocaleHelper
* `Dependencia` Degradación de la versión de Android Material de 1.6.0-alpha02 a 1.5.0
* `Dependencia` Actualización de Kotlinx Coroutines de la versión 1.6.0-native-mt a 1.6.0
* `Dependencia` Actualización de OpenCV de la versión 3.4.3 a 4.5.4 y a 4.5.5 (gracias a [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependencia` Actualización de OkHttp3 de la versión 3.10.0 a 5.0.0-alpha.4 y a 5.0.0-alpha.6
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 7.2.0-beta01 a 7.3.0-alpha06
* `Dependencia` Actualización de Auto.js-ApkBuilder de la versión 1.0.1 a 1.0.3
* `Dependencia` Actualización del compilador de Glide de la versión 4.12.0 a 4.13.1
* `Dependencia` Actualización de Gradle de la versión 7.4-rc-2 a 7.4.1
* `Dependencia` Actualización de las herramientas de Gradle Compile de la versión 31 a 32
* `Dependencia` Actualización de Gson de la versión 2.8.9 a 2.9.0

# v6.0.2

###### 2022/02/05

* `Nuevo` Método images.bilateralFilter() para procesamiento de imágenes mediante filtro bilateral
* `Corrección` Corrección del problema donde múltiples llamadas a toast solo aplicaban el último llamado
* `Corrección` Corrección del posible fallo de toast.dismiss()
* `Corrección` Solución al problema donde los interruptores de modo cliente y servidor podrían no funcionar correctamente
* `Corrección` Solución al problema donde el estado de los interruptores de modo cliente y servidor no se actualizaba correctamente
* `Corrección` Corrección de la excepción de análisis del elemento text en el modo UI en Android 7.x (gracias a [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `Mejora` Ignorar ScriptInterruptedException en sleep()
* `Dependencia` Añadida compatibilidad con Androidx AppCompat (Legacy) versión 1.0.2
* `Dependencia` Actualización de Androidx AppCompat de la versión 1.4.0 a 1.4.1
* `Dependencia` Actualización de Androidx Preference de la versión 1.1.1 a 1.2.0
* `Dependencia` Actualización del motor Rhino de la versión 1.7.14-SNAPSHOT a 1.7.14
* `Dependencia` Actualización de OkHttp3 de la versión 3.10.0 a 5.0.0-alpha.3 y a 5.0.0-alpha.4
* `Dependencia` Actualización de Android Material de la versión 1.6.0-alpha01 a 1.6.0-alpha02
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 7.2.0-alpha06 a 7.2.0-beta01
* `Dependencia` Actualización de Gradle de la versión 7.3.3 a 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `Nuevo` Soporte del complemento VSCode para clientes (LAN) y servidores (LAN/ADB) (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Módulo base64 (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Añadidos los métodos globales isInteger/isNullish/isObject/isPrimitive/isReference
* `Nuevo` Añadido polyfill (Object.getOwnPropertyDescriptors)
* `Nuevo` Añadido polyfill (Array.prototype.flat)
* `Mejora` Extensión de global.sleep para soportar rangos aleatorios/compatibilidad con números negativos
* `Mejora` Extensión de global.toast para soportar control de duración/control de reemplazo forzado/dismiss
* `Mejora` Globalización de objetos de paquetes (okhttp3/androidx/de)
* `Dependencia` Actualización de Android Material de la versión 1.5.0-beta01 -> 1.6.0-alpha01
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 7.2.0-alpha04 -> 7.2.0-alpha06
* `Dependencia` Actualización de Kotlinx Coroutines de la versión 1.5.2-native-mt -> 1.6.0-native-mt
* `Dependencia` Actualización del plugin de Gradle de Kotlin de la versión 1.6.0 -> 1.6.10
* `Dependencia` Actualización de Gradle de la versión 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `Nuevo` Añadido botón de reinicio de la aplicación en la parte inferior del cajón de la página principal
* `Nuevo` Añadidos interruptores para ignorar optimización de batería/mostrar sobre otras aplicaciones en el cajón de la página principal
* `Corrección` Corrección de problemas de renderizado del tema en algunos áreas después de la instalación inicial de la aplicación
* `Corrección` Corrección del problema que impide la construcción del proyecto cuando el archivo sign.property no existe
* `Corrección` Corrección de errores de almacenamiento de meses en tareas programadas únicas
* `Corrección` Corrección de problemas de color en los interruptores de la página de configuración de la aplicación que no cambian con el tema
* `Corrección` Corrección de problemas de reconocimiento del plugin de empaquetado y la URL de descarga del plugin
* `Corrección` Corrección del problema de sincronización del estado del interruptor de "ver permisos de uso" en el cajón de la página principal
* `Corrección` Corrección de fugas de memoria potenciales en TemplateMatching.fastTemplateMatching relacionadas con Mat
* `Mejora` Actualización de la versión del motor Rhino de 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `Mejora` Actualización de OpenCV de 3.4.3 -> 4.5.4
* `Mejora` Mejora de la compatibilidad de ViewUtil.getStatusBarHeight
* `Mejora` Eliminación del módulo de inicio de sesión de usuario del cajón de la página principal y eliminación del espacio ocupado en el diseño
* `Mejora` Eliminación de las etiquetas de comunidad y mercado de la página principal y optimización del diseño
* `Mejora` Modificación del estado predeterminado de algunas opciones de configuración
* `Mejora` Añadido SinceDate en la página Sobre y optimización de la visualización de Copyright
* `Mejora` Actualización del módulo JSON a la versión del 12/06/2017 e integración con cycle.js
* `Mejora` Eliminación de la verificación automática de actualizaciones al iniciar la aplicación y eliminación del botón de verificación de actualizaciones
* `Mejora` Optimización del código lógico de AppOpsKt#isOpPermissionGranted
* `Mejora` Mejora de la seguridad en ResourceMonitor utilizando ReentrantLock (Ref a [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Mejora` Reemplazo de JCenter por Maven Central y otros repositorios
* `Mejora` Eliminación de archivos de biblioteca local duplicados
* `Dependencia` Localización de CrashReport versión 2.6.6
* `Dependencia` Localización de MutableTheme versión 1.0.0
* `Dependencia` Añadida Androidx Preference versión 1.1.1
* `Dependencia` Añadido SwipeRefreshLayout versión 1.1.0
* `Dependencia` Actualización de Android Analytics de la versión 7.0.0 -> 13.1.0
* `Dependencia` Actualización de Android Annotations de la versión 4.5.2 -> 4.8.0
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `Dependencia` Actualización de Android Job de la versión 1.2.6 -> 1.4.2
* `Dependencia` Actualización de Android Material de la versión 1.1.0-alpha01 -> 1.5.0-beta01
* `Dependencia` Actualización de Androidx MultiDex de la versión 2.0.0 -> 2.0.1
* `Dependencia` Actualización de Apache Commons Lang3 de la versión 3.6 -> 3.12.0
* `Dependencia` Actualización de Appcompat de la versión 1.0.2 -> 1.4.0
* `Dependencia` Actualización del plugin de Gradle de ButterKnife de la versión 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `Dependencia` Actualización de ColorPicker de la versión 2.1.5 -> 2.1.7
* `Dependencia` Actualización de Espresso Core de la versión 3.1.1-alpha01 -> 3.5.0-alpha03
* `Dependencia` Actualización de Eventbus de la versión 3.0.0 -> 3.2.0
* `Dependencia` Actualización del compilador de Glide de la versión 4.8.0 -> 4.12.0 -> 4.12.0
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 29.0.2 -> 30.0.2
* `Dependencia` Actualización de Gradle Compile de la versión 28 -> 30 -> 31
* `Dependencia` Actualización de Gradle de la versión 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `Dependencia` Actualización del plugin Groovy-Json de la versión 3.0.7 -> 3.0.8
* `Dependencia` Actualización de Gson de la versión 2.8.2 -> 2.8.9
* `Dependencia` Actualización de la versión de Java de 1.8 -> 11 -> 16
* `Dependencia` Actualización de Joda Time de la versión 2.9.9 -> 2.10.13
* `Dependencia` Actualización de Junit de la versión 4.12 -> 4.13.2
* `Dependencia` Actualización del plugin de Gradle de Kotlin de la versión 1.3.10 -> 1.4.10 -> 1.6.0
* `Dependencia` Actualización de Kotlinx Coroutines de la versión 1.0.1 -> 1.5.2-native-mt
* `Dependencia` Actualización de LeakCanary de la versión 1.6.1 -> 2.7
* `Dependencia` Actualización de LicensesDialog de la versión 1.8.1 -> 2.2.0
* `Dependencia` Actualización de Material Dialogs de la versión 0.9.2.3 -> 0.9.6.0
* `Dependencia` Actualización de OkHttp3 de la versión 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `Dependencia` Actualización de Reactivex RxJava2 RxAndroid de la versión 2.0.1 -> 2.1.1
* `Dependencia` Actualización de Reactivex RxJava2 de la versión 2.1.2 -> 2.2.21
* `Dependencia` Actualización de Retrofit2 Converter Gson de la versión 2.3.0 -> 2.9.0
* `Dependencia` Actualización de Retrofit2 Retrofit de la versión 2.3.0 -> 2.9.0
* `Dependencia` Actualización de Zip4j de la versión 1.3.2 -> 2.9.1