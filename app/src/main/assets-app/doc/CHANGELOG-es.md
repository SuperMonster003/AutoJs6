******

### Historial de Versiones

******

# v6.8.0

###### 2026/10/15 - Algunas funciones pasan a complementos; actualiza con precaución

* `Sugerencia` Algunas funciones integradas pasan a complementos independientes (como OCR, reconocimiento de códigos de barras, OpenCV, OpenCC, Pinyin, MediaInfo, terminal y documentación del proyecto sin conexión); instala y activa los complementos correspondientes en el Centro de complementos antes de usarlas
* `Sugerencia` El SDK de destino de las aplicaciones empaquetadas cambia de 29 a 37; el acceso al almacenamiento compartido y a la red local requiere los permisos exigidos por el sistema
* `Sugerencia` Al usar images u otras funciones que dependen de OpenCV en aplicaciones empaquetadas, selecciona el complemento OpenCV al empaquetar
* `Sugerencia` El control console y la consola flotante muestran los registros del script actual; los registros globales se consultan mediante globalconsole o la página de registros
* `Sugerencia` Los registros en archivos utilizan una implementación integrada que conserva las opciones documentadas y los formatos PatternLayout habituales; las demás API de Log4j dejan de estar disponibles
* `Nuevo` Asistente de configuración para ejecutar sucesivamente los asistentes de permisos e instalación de complementos, accesible desde el mensaje de bienvenida tras el primer inicio (acceso: Ajustes > Configuración > Asistente de configuración)
* `Nuevo` Asistente de permisos para seleccionar los permisos del panel lateral y solicitarlos sucesivamente, con perfiles "Estándar"/"Mínimo"/"Todos" y perfiles personalizados; los permisos concedidos pueden ocultarse desde el menú (acceso: Asistente de configuración/botón del encabezado "Permisos" del panel lateral)
* `Nuevo` Asistente de instalación de complementos para seleccionar por categorías e instalar en lotes complementos oficiales, con perfiles "Estándar"/"Mínimo"/"Todos" y guardado, cambio de nombre y eliminación de perfiles personalizados (acceso: Asistente de configuración/menú del botón flotante de la pestaña de complementos/menú "+" del Centro de complementos)
* `Nuevo` El asistente permite instalar mientras se descarga e instalar silenciosamente mediante 3-Setup Installer con autorización Shizuku/Root/Dhizuku; al usar el instalador del sistema comprueba el permiso para instalar aplicaciones desconocidas antes de descargar
* `Nuevo` Interruptor "Mostrar icono en el lanzador" en los ajustes de complementos (activado por defecto); el asistente permite configurarlo para todos los complementos nuevos de la instalación actual (modificar componentes requiere Root o Shizuku)
* `Nuevo` Administrador del servicio de accesibilidad con inicio y parada, limpieza de caché, autodiagnóstico, supervisión de eventos y copia de diagnósticos (acceso: entrada del servicio de accesibilidad en el panel lateral)
* `Nuevo` La página de opciones de desarrollador permite consultar el historial de finalización de procesos y copiar diagnósticos (Android 11+) (acceso: mantener pulsado el icono de la aplicación en Acerca de la aplicación y el desarrollador)
* `Nuevo` Interruptor "Permitir Inspector en scripts Node.js" en las opciones de desarrollador (desactivado por defecto), para depuración con puntos de interrupción y análisis de CPU/montón mediante Node.js Runtime
* `Nuevo` Página de gestión de modelos de generación de código con importación local, descargas bajo demanda, gestión y comparación de versiones y actualizaciones de modelos oficiales (acceso: Ajustes > Servicio de accesibilidad > Modelos de generación de código)
* `Nuevo` Complemento MCP Server para acceder a scripts, archivos y operaciones del dispositivo de AutoJs6 mediante clientes MCP (acceso: panel lateral/Centro de complementos)
* `Nuevo` Complemento 3-Shell Terminal que proporciona funciones de terminal (acceso: panel lateral, barra de herramientas de proyectos, menú de directorios del administrador de archivos)
* `Nuevo` Complemento Node.js Runtime para ejecutar archivos y proyectos Node.js (admite el parámetro de modo "nodejs" y archivos .node.js/.cjs/.mjs)
* `Nuevo` Complemento Python Runtime para ejecutar archivos y proyectos Python, con entradas de módulo, límites de tiempo de ejecución y entrada estándar predefinida
* `Nuevo` Complemento Lua Runtime para ejecutar scripts Lua
* `Nuevo` Complementos Java Runtime y Kotlin Runtime para compilar y ejecutar código Java/Kotlin (Java requiere Android 7+, Kotlin requiere Android 8+)
* `Nuevo` Complemento QuickJS Runtime para ejecutar archivos JavaScript y ES module (admite los parámetros de modo "quickjs"/"qjs" y archivos .quickjs.js/.quickjs.mjs)
* `Nuevo` Complemento Bun Runtime para ejecutar archivos y proyectos JavaScript/TypeScript (admite el parámetro de modo "bun" y archivos .bun.js/.bun.ts/.bun.tsx)
* `Nuevo` Complemento TypeScript Engine para compilar archivos y proyectos TypeScript de Rhino/Node.js, con comprobación de tipos y Source Map
* `Nuevo` Módulo ai para invocar modelos locales o en línea mediante complementos AI Provider (como Three Stone AI), con salida progresiva, sesiones persistentes y resultados JSON estructurados (consulta: Ejemplos > ai)
* `Nuevo` API de scripts ai.agent para iniciar, consultar, responder, confirmar y cancelar tareas de Three Stove Agent, con tareas en segundo plano detached (requiere Three Stove Agent) (consulta: Ejemplos > agent)
* `Nuevo` Los métodos ai.agent.run/create admiten la opción plan para que Three Stove Agent proponga un plan de pasos antes de ejecutarlo en modo de planificación
* `Nuevo` Los scripts pueden registrarse mediante project.json o un comentario de cabecera @agent para que Three Stove Agent los invoque, con validación de parámetros, parada por tiempo excedido y devolución de resultados estructurados
* `Nuevo` Módulo compose para construir interfaces con Jetpack Compose, con fábricas de nodos, referencias duraderas a nodos, state/render/ref reactivos y control del tema (requiere 3-Block Compose) (consulta: Ejemplos > 3-Block Compose)
* `Nuevo` Opción "Abrir en el diseñador de 3-Block Compose" en el menú del editor: guarda el archivo actual y lo abre en el diseñador visual de 3-Block Compose (1.2.0 o posterior); al regresar al editor se recarga automáticamente lo guardado en el diseñador (pregunta antes si hay cambios sin guardar) (consulta: Ejemplos > 3-Block Compose)
* `Nuevo` Servicio de vista previa de diseños en modo ui, que permite al diseñador de 3-Block Compose previsualizar diseños XML en su lienzo con el inflador real del anfitrión, incrustado en Android 11 y posteriores y como instantánea en versiones anteriores (requiere el plugin 3-Block Compose 1.6.0 o posterior)
* `Nuevo` Métodos compose.mount/floaty para montar páginas UI o crear ventanas flotantes (admitidas en scripts UI y normales), con limpieza automática de las sesiones del script al detenerlo
* `Nuevo` Método compose.dialog para crear sesiones de diálogo y panel inferior modal que pueden actualizarse y cerrarse, disponible en scripts UI y normales
* `Nuevo` Contenedor XML <compose> y método compose.attach para integrar sesiones Compose en páginas UI o ventanas flotantes; compose.AndroidView puede alojar Android View existentes
* `Nuevo` Los diseños TSX admiten componentes Compose como <compose.Column> y <compose:Text>, referencias a fábricas de nodos, ranuras y callbacks reactivos (requiere TypeScript Engine)
* `Nuevo` ui.inflate admite diseños XML TSX compilados y la vinculación de sus callbacks onClick
* `Nuevo` Módulo flow y método global waitAsync para esperas asíncronas y operaciones encadenadas, con bifurcaciones, bucles, límites de tiempo, reintentos y cancelación (consulta: Documentación del proyecto > [Flujo](https://docs.autojs6.com/#/flow))
* `Nuevo` Objeto global terminal para gestionar sesiones de terminal, ejecución visible y escucha de salida, con espera de salida y códigos de finalización (requiere 3-Shell Terminal) (consulta: Ejemplos > Terminal)
* `Nuevo` Módulo installer para instalar, actualizar y desinstalar aplicaciones, con operaciones silenciosas Shizuku/Root/Dhizuku y opciones como instalación mediante notificaciones (requiere 3-Setup Installer) (consulta: Documentación del proyecto > [Instalador](https://docs.autojs6.com/#/installer))
* `Nuevo` Módulo mail para enviar, recibir y consultar correo, descargar adjuntos y gestionar carpetas, con llamadas síncronas y asíncronas (requiere 3-Stamp Mail) (consulta: Documentación del proyecto > [Correo](https://docs.autojs6.com/#/mail))
* `Nuevo` Método mail.watch para escuchar nuevos correos, con reconexión tras perder la conexión
* `Nuevo` Módulo epub para leer metadatos, índice, texto de capítulos y portada de libros electrónicos, con exportación de recursos y búsqueda de texto completo (requiere 3-Folio EPUB) (consulta: Documentación del proyecto > [Libros electrónicos](https://docs.autojs6.com/#/epub))
* `Nuevo` Método epub.read para abrir el lector de libros electrónicos y controlar la posición de lectura, los marcadores y las preferencias
* `Nuevo` Métodos epub.annotations/annotationsAsync para leer resaltados y notas; las sesiones del lector permiten escuchar cambios en las anotaciones
* `Nuevo` Módulo yolo para cargar modelos y detectar objetos mediante el complemento YOLO (consulta: Documentación del proyecto > [YOLO](https://docs.autojs6.com/#/yolo))
* `Nuevo` Módulo tts para convertir texto en voz (consulta: Documentación del proyecto > [Texto a voz](https://docs.autojs6.com/#/tts))
* `Nuevo` Módulo settings para leer y modificar los ajustes de la aplicación (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (consulta: Documentación del proyecto > [Ajustes](https://docs.autojs6.com/#/settings))
* `Nuevo` Módulo work_manager para gestionar tareas programadas y tareas de difusión (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (consulta: Documentación del proyecto > [Work Manager](https://docs.autojs6.com/#/workManager))
* `Nuevo` Módulo power_manager para consultar y solicitar la exclusión de la optimización de batería (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (consulta: Documentación del proyecto > [Power Manager](https://docs.autojs6.com/#/powerManager))
* `Nuevo` Objeto global pangu para ajustar y comprobar el espaciado entre texto chino y occidental (consulta: Documentación del proyecto > [Espaciado de texto](https://docs.autojs6.com/#/pangu))
* `Nuevo` Propiedad device.pageSize (getter) para obtener el tamaño de página de memoria del sistema
* `Nuevo` Método device.getMarketName para obtener el nombre comercial del dispositivo _[`issue #513`](http://issues.autojs6.com/513)_
* `Nuevo` Método ui.useAndroidLayout para activar los controles de diseño nativos de Android
* `Nuevo` Método runtime.loadAar para cargar archivos AAR dinámicamente
* `Nuevo` Métodos runtime.loadJarWithR8/retraceR8Stack para cargar archivos JAR y reconstruir trazas ofuscadas mediante R8 Compiler (experimental)
* `Nuevo` Métodos images.quantize/quantizeToFile para cuantizar imágenes PNG y guardarlas en archivos, devolviendo información de tamaño y calidad (requiere Image Quantization)
* `Nuevo` Métodos images.countPointsByColor/getMeanColor para contar píxeles del color indicado y obtener el color medio
* `Nuevo` Métodos colors.distance/invert/blend/contrast para calcular distancia de color, inversión, mezcla y contraste (también disponibles en instancias Color)
* `Nuevo` Métodos colors.toOklab/oklab/toOklch/oklch para convertir entre espacios de color OKLab y OKLCH; colors.distance/isSimilar e images.detectColor admiten el algoritmo oklab
* `Nuevo` Métodos colors.ensureContrast/harmony/tones/derive/nightVariant/simulate/extract para ajustar contraste, generar armonías, tonos, paletas derivadas y colores nocturnos, simular deficiencias de visión cromática y extraer colores dominantes de imágenes (consulta: Ejemplos > Imágenes y colores)
* `Nuevo` Objeto colors.theme para leer, modificar y restablecer colores del tema y controlar bibliotecas inteligentes mediante scripts; la página de colores del tema permite deshacer los cambios de scripts (consulta: Ejemplos > Imágenes y colores)
* `Nuevo` Métodos console.setTimeVisible/setTimeFormat/setColorful/setAvoidStatusBar/setInputVisible y sus opciones correspondientes en console.build
* `Nuevo` El modo UI admite scripts TypeScript y diseños TSX (requiere TypeScript Engine)
* `Nuevo` Control globalconsole en modo UI para mostrar registros globales; los controles console admiten configuración de título, estilo y campo de entrada
* `Nuevo` Métodos de automatización smartClick/clickIfExists/clickAny/findAny y otros para buscar y pulsar controles, con llamadas encadenadas Flow
* `Nuevo` Métodos smartClickBounds/clickBoundsIfExists/clickBoundsAny para pulsar coordenadas dentro de los límites de controles
* `Nuevo` Métodos scrollUntil/typeInto/dismissPopups/collectList y otros para buscar mediante desplazamiento, introducir texto, gestionar ventanas emergentes y recopilar listas
* `Nuevo` Métodos launchAndWait/backUntil/backToApp/toggle/retry y otros para cambiar de aplicación, establecer estados y reintentar operaciones
* `Nuevo` Métodos waitForIdle/waitForEvent/waitForToast/waitForNotification para esperar inactividad de la interfaz, eventos de accesibilidad, mensajes flotantes y notificaciones
* `Nuevo` select admite sintaxis de selectores de cadena para combinar atributos, expresiones regulares y condiciones booleanas
* `Nuevo` Selectores UiSelector#minDepth/maxDepth/minIndexInParent/maxIndexInParent para filtrar por profundidad del nodo e índice en el padre, también disponibles en la sintaxis de selectores de cadena
* `Nuevo` Método UiSelector#findIterator para recorrer controles coincidentes de forma diferida
* `Nuevo` Métodos UiObject#isStale/waitUntilGone/waitForStable para detectar nodos obsoletos y esperar su desaparición o estabilidad
* `Nuevo` Métodos UiObject#contentInvalid/contextClickable/multiLine/dismissable para obtener atributos booleanos de controles
* `Nuevo` Métodos UiObject#toJSON/dumpSubtree/fingerprint para serializar nodos, exportar subárboles y calcular huellas del estado
* `Nuevo` Las colecciones de controles admiten at/first/last/nonNull/slice y operaciones sobre cada control como clickEach/setTextEach
* `Nuevo` Métodos auto.findWindows/findWindowRoots para obtener ventanas y nodos raíz según tipo de ventana, paquete, título, pantalla y otros criterios
* `Nuevo` Métodos auto.explain/dump y objeto auto.stats para diagnosticar selectores, exportar árboles de nodos y recopilar tiempos de consulta
* `Nuevo` auto.registerEvent admite opciones de deduplicación y debounce de eventos (desactivadas por defecto)
* `Nuevo` auto.setFlags admite appWindowsFallback para usar la raíz de una ventana de aplicación cuando la raíz de la ventana activa no está disponible (desactivado por defecto)
* `Nuevo` El administrador de archivos admite selección múltiple y copia/movimiento/eliminación por lotes
* `Nuevo` Barra de ruta en el administrador de archivos, con acceso a directorios superiores mediante pulsación y navegación por desplazamiento horizontal
* `Nuevo` Opción "Ejecutar en ventana flotante" en el menú de scripts del administrador de archivos, con barra flotante para iniciar/detener scripts, editar archivos y consultar registros
* `Nuevo` Opción "Ejecutar con AutoJs4" en el menú de scripts del administrador de archivos para ejecutar scripts o proyectos con el motor AutoJs4 y comparar resultados (requiere AutoJs4 Runtime)
* `Nuevo` Opción "Ejecutar con AutoJs4 y comparar" en el menú de scripts para ejecutar sucesivamente el mismo script o proyecto con AutoJs6 y AutoJs4 y comparar diferencias (requiere AutoJs4 Runtime)
* `Nuevo` Opción "Copiar ruta" en los menús de archivos y directorios del administrador de archivos, para copiar rutas absolutas al portapapeles, con copia por lotes (una ruta por línea) en "Más" de la barra de selección
* `Nuevo` El administrador de archivos permite explorar, previsualizar, comprimir y extraer archivos mediante 3-Stack Archive, con contraseñas, volúmenes y apertura o ejecución directa de archivos internos
* `Nuevo` El administrador de archivos previsualiza Markdown/HTML mediante Markdown Previewer/HTML Previewer, con temas, CSS personalizado y pantalla completa
* `Nuevo` El administrador de archivos permite ver imágenes, reproducir audio y vídeo y abrir EPUB mediante sus complementos; el audio admite reproducción en segundo plano y controles en notificaciones
* `Nuevo` El visor de imágenes permite cambiar entre imágenes del mismo directorio, ampliar con dos dedos, mostrar EXIF y usar pantalla completa, además de editar y convertir imágenes mediante 3-Maple Image
* `Nuevo` Los archivos abiertos en un complemento desde el administrador de archivos se pueden mover a la papelera desde el complemento y recuperar con deshacer (requiere compatibilidad del complemento)
* `Nuevo` Cuando varias acciones de complementos sirven para los archivos seleccionados en el administrador de archivos, la barra de selección ofrece las demás en "Más" (por ejemplo, convertir imágenes por lotes con 3-Maple Image)
* `Nuevo` El administrador de archivos permite consultar información y manifiestos de paquetes APK/APKS/XAPK/APKM/APKZ/AAB e instalar APK y contenedores de APK divididos
* `Nuevo` El editor de código permite cargar Ace mediante Ace Editor y seleccionar el motor en los ajustes; ofrece acceso al Centro de complementos si el complemento no está disponible
* `Nuevo` Ace admite completado, pistas de parámetros, documentación flotante y diagnósticos; los proyectos TypeScript admiten salto a definición, renombrado de símbolos e importación automática (requiere TypeScript Engine)
* `Nuevo` Ace admite un panel de atajos, historial de acciones y acciones favoritas
* `Nuevo` Ace permite desplegar o plegar todos los bloques de código (requiere actualizar el complemento Ace Editor) (acceso: Editar > Plegado > Desplegar todo/Plegar todo)
* `Nuevo` Grupo "Sugerencias inteligentes" en los ajustes del editor (solo Ace), para configurar los desencadenantes y retardos del completado y las pistas de parámetros
* `Nuevo` El editor admite conversión de codificación y recarga de archivos, y muestra nombre de archivo, posición del cursor y codificación en la barra de información
* `Nuevo` El editor admite modo inmersivo de pantalla completa
* `Nuevo` El editor admite un panel inferior de registros, inicio/parada de scripts y salto al código fuente de marcos de pila (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Nuevo` Página de ajustes independiente del editor, con tema, fuente, números de línea, ajuste de líneas, umbral de resaltado y visibilidad del panel de registros
* `Nuevo` La página de registros y el panel inferior del editor permiten ejecutar fragmentos TypeScript (requiere TypeScript Engine)
* `Nuevo` Herramienta "Selector de color de pantalla" en el panel lateral (requiere 3-Color Picker)
* `Nuevo` Las tareas programadas admiten programación mensual/anual/cuenta atrás y condiciones de finalización de repeticiones _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `Nuevo` Las carpetas de proyecto permiten crear tareas programadas y de difusión
* `Nuevo` Tipo de tarea de difusión "Al llegar un correo", con filtros por remitente y asunto; los datos se obtienen mediante engines.myEngine().execArgv.mail (requiere 3-Stamp Mail)
* `Nuevo` El inspector de diseño permite copiar selectores con varios atributos, guardar árboles de nodos y analizarlos sin conexión
* `Nuevo` Barra de búsqueda en el inspector para text/desc/id/className mediante palabras clave o expresiones regulares; la vista jerárquica conserva coincidencias y ancestros, y la vista de límites solo dibuja coincidencias, con lista de resultados, navegación e historial
* `Nuevo` La barra de búsqueda del inspector admite filtros (atributos booleanos, clases predefinidas, intervalos de profundidad y tamaño), selección rectangular, modo de campos (`k=v`) y modo selector (`$()`); conserva la configuración y el modo entre sesiones
* `Nuevo` El inspector permite "Copiar como selector" para convertir palabras clave, condiciones de campos, filtros y selección rectangular en selectores encadenados
* `Nuevo` "Generar código" en el inspector admite alcance individual/colección, estrategias de espera, parámetros de acciones, estilo clásico/Flow, posicionamiento relativo y alternativas, con previsualización de coincidencias en el diseño
* `Nuevo` Los colores del tema admiten bibliotecas propias que pueden crearse o clonarse, añadir colores desde la paleta, otras bibliotecas o el historial, renombrar, modificar valores, reordenar arrastrando y eliminar por lotes
* `Nuevo` Bibliotecas de colores inteligentes que cambian grupos según modo nocturno, hora, amanecer/atardecer, batería y estado del script, con cambios programados o aleatorios y excepciones por fecha; incluyen "Parejas día/noche de Material Design", "Color de fondo" y "Seguir fondo de pantalla", y generación desde un color semilla
* `Nuevo` Las bibliotecas de colores admiten importación y exportación JSON (con exportación múltiple e instantáneas de preajustes)
* `Nuevo` "Herramientas de color" en los detalles (armonías, tonos, contraste y simulación de deficiencias de visión cromática), con colores favoritos y fijados y biblioteca virtual "Frecuentes y recomendados"
* `Nuevo` Ajuste "Colores derivados" del tema (desactivado por defecto), para deducir automáticamente colores de acento y oscuros a partir del color principal
* `Nuevo` Políticas de conservación de papelera, historial de versiones y borradores de emergencia en los ajustes, con limpieza por días, cantidad y capacidad
* `Nuevo` El historial de versiones admite lista independiente, vista previa y comparación de diferencias, con búsqueda, ordenación y eliminación por lotes
* `Nuevo` El historial de versiones admite instantáneas manuales, fijación de versiones, edición de etiquetas, exportación y guardado directo tras restaurar
* `Nuevo` La papelera admite restauración por lotes, vista previa, detalles, avisos de caducidad y deshacer el traslado, además de detectar y limpiar entradas inválidas
* `Nuevo` Las aplicaciones empaquetadas admiten proyectos y scripts Node.js; requieren Node.js Runtime durante la ejecución (muestran instrucciones de instalación si falta)
* `Nuevo` Las aplicaciones empaquetadas permiten compilar proyectos Rhino TypeScript durante la construcción, cifrar los resultados compilados y adjuntar Source Map
* `Nuevo` La configuración de aplicaciones empaquetadas permite mostrar u ocultar registros y configurar accesos directos del lanzador _[`issue #550`](http://issues.autojs6.com/550)_
* `Nuevo` La conexión del complemento VSCode admite escaneo QR, confirmación de emparejamiento y detección en la red local (requiere el complemento VSCode 1.1.0+)
* `Nuevo` El complemento VSCode admite gestión remota de archivos y ejecución de scripts del dispositivo, con progreso en transferencias grandes (requiere el complemento VSCode 1.1.0+)
* `Nuevo` Administrador de conexiones en los modos cliente y servidor para consultar estados, gestionar clientes y acceder al historial de direcciones
* `Nuevo` El modo cliente admite reconexión automática tras desconexiones inesperadas, reintentos al recuperarse la red y restauración de conexiones al iniciar la aplicación
* `Corrección` runtime.loadJar no podía cargar dinámicamente archivos class compilados con versiones superiores de Java
* `Corrección` En Android 7.x, runtime.loadDex podía dañar la caché DEX y provocar errores de carga de clases
* `Corrección` Comportamiento anómalo de algunos scripts debido a incompatibilidades en ámbitos, conversión de parámetros y tratamiento de resultados de Rhino y los módulos integrados
* `Corrección` Las funciones envueltas en Proxy y algunos callbacks JavaScript podían rechazarse en módulos integrados o provocar parámetros anidados y ambigüedad de sobrecargas
* `Corrección` Los listeners EventEmitter#once podían ejecutarse repetidamente al despachar eventos recursivamente o lanzar excepciones desde callbacks
* `Corrección` app.isInstalled/kill y otros métodos no podían utilizar correctamente parámetros de nombre de paquete _[`issue #495`](http://issues.autojs6.com/495)_
* `Corrección` Las llamadas shell podían bloquearse aparentemente en dispositivos sin Root
* `Corrección` dialogs.multiChoice convertía incorrectamente los resultados de selección múltiple en cadenas _[`issue #518`](http://issues.autojs6.com/518)_
* `Corrección` Cerrar diálogos de scripts al terminar un hilo podía bloquear la aplicación
* `Corrección` Tras cancelar temporizadores o detener subhilos, callbacks caducados podían impedir la finalización automática del script
* `Corrección` Los métodos de enfoque de floaty.window/rawWindow podían provocar excepciones de hilo al invocarse desde el hilo del script
* `Corrección` Los resultados de console.setPosition/setSize/setGravity dependían del orden de llamada o del momento en que se mostraba la ventana
* `Corrección` El arrastre de la barra de título de la consola flotante podía fallar y la ventana no podía acercarse a los bordes de la pantalla
* `Corrección` console.log/util.format omitían comillas simples en los valores de cadena de objetos o matrices, con un formato distinto de util.inspect en Auto.js 4
* `Corrección` Las opciones inseguras de solicitudes http podían hacer que solicitudes posteriores siguieran omitiendo la validación del certificado y del nombre del servidor
* `Corrección` La configuración de seguridad de red podía bloquear inesperadamente solicitudes HTTP de versiones oficiales y aplicaciones empaquetadas
* `Corrección` El selector de archivos del control WebView en modo UI no se abría correctamente
* `Corrección` Los scripts UI podían terminar inesperadamente al entrar en pantalla dividida o cambiar el tamaño de la ventana
* `Corrección` Los parámetros de expresión regular no funcionaban correctamente en sobrecargas de selectores, el método global filter y selectores de colecciones
* `Corrección` Los selectores contains interpretaban incorrectamente cadenas normales como expresiones regulares
* `Corrección` Las búsquedas con límite de resultados podían devolver resultados de más o no liberar los nodos restantes
* `Corrección` Errores al analizar parámetros de brújula y devolver resultados en UiObject#compass y pickup/detect
* `Corrección` UiObject#child/parent podían entrar en recursión infinita
* `Corrección` Nombres de atributos incorrectos en la representación textual de selectores y formato decimal anómalo en algunos idiomas
* `Corrección` Las excepciones de métodos selectores globales no podían capturarse con try/catch
* `Corrección` Con findOnUiThread, las búsquedas podían esperar indefinidamente o no agotar el tiempo a tiempo
* `Corrección` UiObject#clickBounds podía finalizar inesperadamente el bucle de eventos e invalidar callbacks posteriores, o lanzar excepciones al invocarse en el hilo principal (como en scripts de modo "ui")
* `Corrección` El nodo source de callbacks auto.registerEvent podía entrar en bucle infinito o provocar errores de límites al cambiar el árbol de nodos
* `Corrección` Tras reiniciar el servicio de accesibilidad, pickup y las consultas UiSelector podían devolver siempre resultados vacíos
* `Corrección` auto.stateListener se sobrescribía entre scripts y no se eliminaba al finalizar el script
* `Corrección` auto.waitFor podía esperar hasta agotar el tiempo aunque el servicio de accesibilidad ya enviara eventos
* `Corrección` Desactivar o reiniciar el servicio de accesibilidad con Root podía no surtir efecto
* `Corrección` Crear páginas de la aplicación podía reactivar el servicio de accesibilidad desactivado manualmente por el usuario
* `Corrección` auto.start/enable reiniciaban repetidamente el servicio de accesibilidad aunque ya estuviera activo
* `Corrección` auto.windowRoots podía devolver nodos vacíos cuando la ventana activa no estaba disponible
* `Corrección` El estado consultado y los mensajes de error del servicio de accesibilidad no coincidían con el interruptor del panel lateral
* `Corrección` auto.services no podía leer la lista de servicios activos si no se había configurado un método de activación automática
* `Corrección` El inspector de diseño requería activarse otra vez después de habilitar automáticamente la accesibilidad
* `Corrección` Los callbacks de eventos registrados y listeners de teclas dejaban de funcionar tras reiniciar la accesibilidad
* `Corrección` La primera llamada a operaciones globales automator podía lanzar una excepción de puntero nulo y el escalado vertical de gestos era incorrecto
* `Corrección` El servicio de accesibilidad podía interrumpirse inesperadamente al ejecutar repetidamente operaciones como back _[`issue #527`](http://issues.autojs6.com/527)_
* `Corrección` El observador de notificaciones confundía notificaciones del sistema con mensajes flotantes y omitía eventos de notificación
* `Corrección` En Android 14+, solicitar captura de pantalla podía bloquear la aplicación por errores al iniciar el servicio en primer plano _[`issue #516`](http://issues.autojs6.com/516)_
* `Corrección` Invocar OCR repetidamente desde varios hilos o archivos podía solicitar una y otra vez el permiso de captura _[`issue #551`](http://issues.autojs6.com/551)_
* `Corrección` Al fallar la selección de un complemento OCR, los errores no coincidían con el motor real y su estado de instalación
* `Corrección` El servicio Paddle OCR no se recuperaba tras desconectarse ni se iniciaba automáticamente si no estaba en ejecución _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_ _[`issue #517`](http://issues.autojs6.com/517)_
* `Corrección` Usar Paddle OCR y luego Rapid OCR en aplicaciones empaquetadas podía bloquearlas
* `Corrección` Paddle OCR no funcionaba en aplicaciones empaquetadas al seleccionar simultáneamente los complementos v3 y v4/v5/v6 (las aplicaciones empaquetadas solo admiten PP-OCRv3)
* `Corrección` images.resize podía bloquear la aplicación al recibir parámetros incorrectos
* `Corrección` Algunos métodos images podían liberar imágenes prematuramente, filtrar memoria nativa o liberar imágenes de otros scripts
* `Corrección` Resultados incorrectos del algoritmo hs en colors.isSimilar/images.detectColor y manejo incorrecto de componentes decimales o porcentuales en colors.rgb/rgba
* `Corrección` Errores en resultados de comparación y procesamiento de imágenes grises o RGBA en images.isEqual/mse/ncc/isGrayscale
* `Corrección` Pérdida de transparencia en images.invert, canales rojo y azul invertidos en ImageWrapper#saveTo y manejo incorrecto de regiones fuera de límites en images.clip
* `Corrección` images.load y otros métodos podían no cerrar conexiones o liberar imágenes temporales
* `Corrección` Errores de coincidencia en bordes, precisión de coordenadas, ordenación y límite de resultados en images.findImage/matchTemplate
* `Corrección` images.matchTemplate no manejaba correctamente imágenes con distintos canales, plantillas uniformes o máscaras transparentes; MatchingResult#sortBy rechazaba resultados decimales de comparación
* `Corrección` images.matchFeatures devolvía bordes que no correspondían a la posición real, con errores al detectar características en imágenes pequeñas, dibujar coincidencias y liberar recursos
* `Corrección` El consumo de memoria podía crecer continuamente tras ejecutar scripts repetidas veces (corrección tentativa) _[`issue #548`](http://issues.autojs6.com/548)_
* `Corrección` Al ejecutar scripts mediante URI externos o en segundo plano podían perderse la ruta original o la extensión _[`issue #545`](http://issues.autojs6.com/545)_
* `Corrección` Tasker no podía invocar y ejecutar scripts correctamente _[`issue #501`](http://issues.autojs6.com/501)_
* `Corrección` La actualización en lote de widgets podía borrar por error rutas de scripts y asignaciones de pulsación
* `Corrección` El administrador de archivos no leía con la codificación real los scripts con BOM al ejecutarlos directamente
* `Corrección` Los proyectos sin datos de empaquetado no se reconocían correctamente o no utilizaban la entrada indicada en project.json
* `Corrección` Los archivos auto se ejecutaban como JavaScript desde el administrador de archivos, tareas programadas o intents externos
* `Corrección` El administrador de archivos no mostraba información de algunos APK o carecía del acceso al manifiesto
* `Corrección` Importar URI externos podía escribir fuera del directorio de destino o sobrescribir archivos homónimos por tratamiento incorrecto del nombre
* `Corrección` El administrador de archivos podía parpadear o reiniciar el desplazamiento al volver a la página o eliminar entradas de la papelera
* `Corrección` El administrador no distinguía correctamente archivos TypeScript de vídeos con la misma extensión y algunos archivos fuente carecían de opción de ejecución
* `Corrección` Los mensajes de operaciones del administrador de archivos podían mostrarse repetidamente
* `Corrección` Los elementos de listas como el administrador de archivos podían conservar un fondo gris tras abrir otra página y volver, en algunas pantallas táctiles
* `Corrección` El número de "Ejecutar tarea" en la lista de tareas no coincidía con los elementos mostrados, y los scripts UI ya cerrados seguían apareciendo como en ejecución
* `Corrección` Errores al resolver rutas multimedia e imágenes y visualización de rutas de caché en detalles de MediaInfo
* `Corrección` Las consultas fallidas de detalles multimedia podían bloquear la aplicación y algunos campos de metadatos de vídeo se mostraban incorrectamente
* `Corrección` El historial de versiones no seguía los cambios de ruta tras mover o renombrar archivos, o se asociaba incorrectamente a copias de contenido idéntico
* `Corrección` La papelera y el historial no accedían correctamente al almacenamiento interno en entornos multiusuario o perfiles de trabajo
* `Corrección` Restaurar sobrescribiendo archivos desde la papelera o el historial podía perder el contenido anterior o dejar archivos incompletos
* `Corrección` Renombrado automático incorrecto de directorios con puntos al restaurarlos desde la papelera
* `Corrección` El historial no mostraba todas las versiones conservadas y el modo de visualización fallaba al cambiar de operación
* `Corrección` La limpieza del historial podía eliminar instantáneas nuevas cuyo registro aún no había terminado
* `Corrección` La papelera notificaba la finalización aunque la restauración fallara
* `Corrección` Al restaurar directorios, la papelera podía escribir entradas de archivo anómalas fuera del destino
* `Corrección` El editor podía perder cursor, selección o contenido sin guardar al recrearse la página
* `Corrección` El contenido del editor y la página de registros podía quedar oculto o comprimido al mostrar el teclado o campo de entrada
* `Corrección` Errores al guardar el estado, colorear el cursor y elegir el idioma de las sugerencias del cuadro de búsqueda principal
* `Corrección` Los eventos multitáctiles anómalos en la pestaña de documentación podían bloquear la aplicación
* `Corrección` Faltaba la animación de transición al cambiar a la pestaña de archivos
* `Corrección` En pantallas estrechas, el panel lateral recortaba controles, omitía botones inferiores y ofrecía áreas de pulsación demasiado pequeñas en interruptores
* `Corrección` Al cerrar o recrear la página principal, el listener de limpieza del panel accedía a un contexto liberado y bloqueaba la aplicación
* `Corrección` El fondo de inicio no cubría la zona del borde corto en pantallas con muesca
* `Corrección` Información incorrecta del paquete de la aplicación reciente y su Activity en el menú flotante _[`issue #529`](http://issues.autojs6.com/529)_
* `Corrección` El diálogo "Ejecutar script" del botón flotante podía no restaurarse después de minimizarlo (corrección tentativa) _[`issue #500`](http://issues.autojs6.com/500)_
* `Corrección` El inspector podía generar instrucciones inválidas para colecciones de controles
* `Corrección` Contraste insuficiente de textos y controles de las ventanas y diálogos del inspector con algunos colores del tema o modo nocturno
* `Corrección` Los colores Material Design elegidos en el diseño tradicional no se encontraban con "Localizar color actual del tema" tras cambiar de diseño, y la página de colores Material Design no mostraba el resumen del color actual
* `Corrección` La pestaña de complementos y el Centro conservaban filtros al cerrar la búsqueda o cambiar de pestaña
* `Corrección` El Centro de complementos elegía paquetes x86_64 en dispositivos x86
* `Corrección` No se podía completar la activación de un complemento tras autorizarlo correctamente
* `Corrección` El Centro de complementos no interpretaba correctamente algunas fechas con meses abreviados
* `Corrección` Errores en caché de iconos, cambio de modo nocturno y estado de actualización por arrastre del Centro de complementos
* `Corrección` Actualizar el Centro podía omitir complementos instalados, mostrar listas vacías o restablecer el desplazamiento
* `Corrección` El Centro podía instalar variantes incorrectas o duplicar plantillas de empaquetado y avisos de actualización
* `Corrección` Tras actualizar correctamente el índice, el Centro recuperaba de caché complementos antiguos eliminados o renombrados
* `Corrección` Abrir los detalles de un complemento inmediatamente tras un inicio en frío provocaba parpadeos al reconstruirse todo el diálogo durante la actualización de la lista
* `Corrección` El Centro mantenía desactivado un complemento después de una indisponibilidad temporal (por ejemplo durante una actualización)
* `Corrección` El listener de solicitudes de permiso Shizuku no se eliminaba correctamente al destruirse
* `Corrección` No se podía continuar confirmando la instalación de APK en segundo plano
* `Corrección` Cancelar el empaquetado APK podía notificarse como fallo de construcción
* `Corrección` Errores de validación de consistencia del certificado de firma de APK empaquetados y reconocimiento de firmas en Android 9-11
* `Corrección` Si faltaban componentes de ejecución en la aplicación anfitriona o empaquetada, la biblioteca nativa shell podía faltar o usar una ABI incorrecta y bloquear la aplicación
* `Corrección` Al empaquetar APK se conservaba silenciosamente una sola biblioteca nativa entre archivos homónimos con contenido distinto (ahora se detiene el empaquetado y se enumeran los orígenes)
* `Corrección` Los scripts UI empaquetados a veces mostraban una página vacía y terminaban al iniciarse
* `Corrección` Al guardar proyectos mediante VSCode, archivos eliminados permanecían en el dispositivo o en directorios de caché _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `Corrección` Recibir archivos de proyecto desde VSCode podía agotar la memoria por el tratamiento de registros _[`issue #523`](http://issues.autojs6.com/523)_
* `Corrección` Las conexiones caducadas de VSCode bloqueaban el envío de registros e impedían que las conexiones nuevas los recibieran _[`issue #385`](http://issues.autojs6.com/385)_
* `Corrección` Mostrar diálogos en páginas destruidas al fallar la conexión VSCode podía provocar excepciones
* `Corrección` Las direcciones conectadas correctamente en modo cliente no se guardaban en el historial
* `Corrección` La búsqueda de actualizaciones no seleccionaba paquetes según la prioridad ABI del dispositivo
* `Corrección` Al construir con IntelliJ IDEA podía fallar la resolución de dependencias, la selección de tareas de compilación o la localización del APK actual
* `Corrección` Memoria insuficiente de la compilación Kotlin al construir varias variantes
* `Corrección` Errores al resolver el SDK y las rutas de plantillas de empaquetado durante la construcción Gradle
* `Mejora` Inicialización diferida de componentes no esenciales para acelerar el inicio
* `Mejora` Al iniciar ya no se muestran automáticamente solicitudes de permisos; si falta el acceso a todos los archivos, aparece un aviso en la página principal con autorización directa
* `Mejora` Adaptación de la aplicación y aplicaciones empaquetadas a Android 17, con mejores avisos de acceso a red local, protección avanzada y restricciones de audio en segundo plano
* `Mejora` Compatibilidad con páginas de memoria de 16 KB en la aplicación y aplicaciones empaquetadas, con avisos de bibliotecas nativas incompatibles al instalar, cargar y empaquetar complementos
* `Mejora` Mejoras de los textos multilingües de la interfaz y los mensajes de excepción
* `Mejora` Reducción de dependencias, recursos de idiomas e imágenes del instalador para disminuir su tamaño
* `Mejora` La búsqueda de actualizaciones obtiene versiones y paquetes mediante GitHub REST API, con representación local de las notas
* `Mejora` La aplicación incluye historial de versiones en chino simplificado e inglés; otros idiomas se descargan y almacenan en caché (sin conexión se prefiere la caché y, si no existe, el inglés)
* `Mejora` Documentación sin conexión convertida en complemento Offline Docs, con instalación y actualización independientes y cambio inmediato del origen de documentos
* `Mejora` Plantilla de empaquetado convertida en APK Builder Template, con comprobación de firma del complemento, versión anfitriona y compatibilidad de plantilla al empaquetar
* `Mejora` Funciones ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization convertidas en complementos
* `Mejora` Funciones OpenCC/Pinyin/Pinyin4j/MediaInfo convertidas en complementos
* `Mejora` Los complementos oficiales pueden seguir los colores del tema, modo nocturno e idioma del anfitrión
* `Mejora` Mejoras de instalación y actualización en línea, elección de variantes, ajustes, instrucciones e historial del Centro de complementos
* `Mejora` Los iconos del Centro de complementos responden a toques/pulsaciones largas según su estado de instalación: los instalados abren los detalles del sistema/su página de inicio independiente (con un aviso si no existe), y los no instalados abren los detalles del complemento/inician su instalación
* `Mejora` El menú FAB de la pestaña Complementos del inicio prioriza el asistente de instalación, los repositorios oficiales y de terceros y el Centro de complementos; la instalación por URL/archivo local y las acciones de gestión se agrupan en "Más"
* `Mejora` El Centro admite dos niveles de ordenación y dirección independiente; por defecto ordena instalados > desactivados > no instalados y, dentro de cada estado, por actualización más reciente
* `Mejora` Tras operaciones explícitas o cambios de ajustes, las entradas del Centro conservan su posición y actualizan el estado; continúa la actualización automática en segundo plano y se restaura el orden al actualizar por arrastre o cambiar orden/filtros
* `Mejora` El Centro admite montaje automático de complementos SDK y detección de ABI disponibles
* `Mejora` El Centro permite activar automáticamente complementos oficiales recién instalados; instalaciones externas y sobreinstalaciones conservan la política anterior
* `Mejora` Caché y carga asíncrona para mejorar la fluidez de listas y actualizaciones del Centro, con instrucciones de recuperación si falla la activación
* `Mejora` Mantener pulsado el botón flotante de la pestaña de complementos abre el Centro
* `Mejora` El diálogo de información distingue las ABI compatibles con el complemento de las del paquete instalado
* `Mejora` Caché de inicialización Rhino para acelerar los scripts, mayor eficiencia al imprimir matrices grandes y mejor compatibilidad de reflexión Java en Android antiguo
* `Mejora` Sincronización de Rhino con el proyecto original, soporte de new.target y mejoras de ámbitos, TypedArray y depurador
* `Mejora` runtime.loadJar puede cargar JAR mediante DEX Compiler, con diagnósticos de compilación y alternativa ante fallos (experimental, desactivado por defecto)
* `Mejora` Tareas programadas, tareas de difusión, accesos directos y Tasker pueden ejecutar TypeScript (requiere TypeScript Engine)
* `Mejora` pinyin admite diccionarios de pronunciación personalizados por llamada y mejora compare/compact
* `Mejora` pinyin4j admite distintos sistemas de romanización y consultas completas de pronunciación
* `Mejora` opencc admite consulta de tipos de conversión, conversión por lotes y conversiones encadenadas
* `Mejora` ocr selecciona motores disponibles en orden ML Kit/Paddle/Rapid por defecto y permite elegir explícitamente o volver a la selección automática
* `Mejora` El módulo ocr invocado a través del puente del host (plugin Node.js y otras sesiones de plugins) selecciona un motor habilitado en orden ML Kit/Paddle/Rapid cuando no se indica ninguno, en lugar de exigir siempre el plugin ML Kit OCR
* `Mejora` El módulo accessibility del puente del host añade captureScreenImage, que convierte una captura de pantalla de accesibilidad (con reserva a la captura de pantalla cuando no está disponible) en un identificador de imagen de sesión que las sesiones de plugins como MCP Server usan directamente para el reconocimiento de códigos de barras y la búsqueda de imágenes o colores; las sesiones de plugins ahora pueden mantener el doble de su número de llamadas concurrentes en identificadores de imagen en lugar de 4
* `Mejora` Cuando sesiones de plugins orientadas a modelos como MCP Server y 3-Stove Agent leen una imagen a través del puente (image.readImage), las imágenes cuyo lado mayor supera 4096 px se decodifican con un submuestreo en potencia de dos y el resultado incluye scale y las dimensiones originales, para que las fotos grandes de la galería no agoten la memoria del host; los scripts del plugin Node.js no se ven afectados
* `Mejora` Las sesiones de plugins orientadas a modelos como MCP Server y 3-Stove Agent que leen imágenes o información de medios por ruta a través del puente (image.readImage, mediainfo) quedan limitadas al directorio de trabajo, y las escrituras (image.saveImage) también permanecen dentro de él; cuando la solicitud declara files.shared_read y AutoJs6 tiene acceso a todos los archivos, el almacenamiento compartido pasa a ser legible en modo de solo lectura (excluyendo Android/data y Android/obb), y files.list y files.stat pueden listar el almacenamiento compartido con el mismo token; los scripts del plugin Node.js no se ven afectados
* `Mejora` El módulo app del puente del host añade pluginCapabilities, que informa en tiempo real del estado de las familias de plugins OCR, códigos de barras, Pinyin, Pinyin4j, MediaInfo, OpenCV y documentación sin conexión (available / not_installed / disabled / incompatible), la versión, habilitación y autorización de los plugins instalados, los métodos del puente que respaldan y la entrada del Plugin Center, para que sesiones de plugins como MCP Server muestren herramientas según el estado de instalación y orienten a los usuarios al plugin que deben instalar; la lista de métodos opcionales que recibe una sesión de plugin al abrirse se completa con la misma lógica para las familias disponibles en ese momento
* `Mejora` La autorización de capacidades predeterminada de las sesiones del plugin MCP Server se amplía: los métodos de los módulos ocr, barcode, pinyin, pinyin4j y mediainfo, image.readImage y los métodos de búsqueda de imágenes / colores, accessibility.readScreenText / captureScreenImage y app.pluginCapabilities, con sus tokens de permiso (incluido files.shared_read para el almacenamiento compartido de solo lectura); estos métodos solo funcionan mientras el plugin correspondiente está instalado y habilitado, image.saveImage e image.toBytes quedan fuera de la autorización, y la autorización de 3-Stove Agent no cambia
* `Mejora` images.save/saveImage/compress/compressToBytes admiten cuantización PNG (conservando transparencia) y límites de píxeles y memoria (requiere Image Quantization)
* `Mejora` images.readPixels admite parámetros ImageWrapper
* `Mejora` images.matchTemplate/findPointByImage admiten scales; los resultados incluyen tamaño, escala y región
* `Mejora` images.matchFeatures admite imágenes o rutas y más opciones de coincidencia, con información adicional de características y resultados
* `Mejora` Mayor eficiencia y precisión de búsqueda multicolor, coincidencia de plantillas y características
* `Mejora` La captura de pantalla reintenta cuando temporalmente no hay fotogramas y reduce el impacto de liberar recursos en operaciones de imagen concurrentes
* `Mejora` mediainfo admite snapshot/capabilities y conteo de flujos multimedia, reduciendo el tiempo de consulta de detalles
* `Mejora` KeyCode mejora la compatibilidad de parámetros y permite enviar códigos de tecla con Shizuku _[`issue #492`](http://issues.autojs6.com/492)_
* `Mejora` La accesibilidad puede activarse automáticamente mediante Shizuku y sincronizar su inicio/parada con el complemento acompañante oficial
* `Mejora` El nodo source de callbacks auto.registerEvent se resuelve bajo demanda (en versiones anteriores a Android 13 debe leerse dentro del callback)
* `Mejora` Las consultas de ventanas omiten raíces no disponibles y mejoran la liberación de nodos entre versiones Android
* `Mejora` automator.captureScreen mejora la liberación de recursos y los reintentos ante límites del sistema para evitar solicitudes sin terminar durante demasiado tiempo
* `Mejora` Los parámetros enteros de selectores globales y de colecciones se redondean uniformemente al entero más cercano, con consultas multihilo e interrupciones por tiempo más estables
* `Mejora` Los selectores y esperas Flow admiten sondeo asistido por eventos, reintentando antes cuando llega un evento de accesibilidad (se activa con eventAssistedPolling en auto.setFlags)
* `Mejora` Restaurados console.rawInput/input para leer texto o evaluar expresiones desde el campo de entrada de la consola
* `Mejora` Cada consola conserva su visibilidad y formato de hora; volver a ejecutar un script homónimo sustituye su ventana flotante anterior
* `Mejora` media.playMusic admite reproducción en primer plano, foco de audio y controles de notificaciones, y libera recursos al finalizar el script
* `Mejora` Eliminadas las copias integradas de compatibilidad com.stardust; las clases con el prefijo com.stardust se asignan a implementaciones homónimas de AutoJs6 (218 clases completas, 90 parciales y 46 sin equivalente ya no disponibles), unificando usos por nombre como new e instanceof con los objetos runtime (consulta: Documentación del proyecto > [Entorno de ejecución AutoJs4](https://docs.autojs6.com/#/autojs4Runtime))
* `Mejora` Ejemplos de 3-Block Compose, flujos de automatización, interacción con la consola y libros electrónicos, con mejoras del ejemplo OCR de pantalla
* `Mejora` Menú de operaciones unificado en la consola flotante para tamaño, hora, colores, fuente y transparencia
* `Mejora` Colores del tema y distribución de menús unificados en el editor, con mejoras de fuentes, modo de solo lectura y selección de texto
* `Mejora` El botón de ejecución del editor cambia a parada tras iniciar un script
* `Mejora` Mejoras de avisos de descarga de fuentes, corrección de números de línea de destino y detección y guardado de codificación
* `Mejora` Información de instaladores cargada bajo demanda en el administrador, con mejor presentación de paquetes grandes y manifiestos
* `Mejora` Accesos de previsualización y operaciones de complementos unificados; actualización diferencial de listas conservando el desplazamiento
* `Mejora` El historial evita instantáneas duplicadas, conserva el contenido antes de sobrescribir y migra la historia al restaurar la papelera a otra ruta
* `Mejora` Mejores avisos de capacidad, restauración de entradas dañadas y mensajes de error de la papelera, con actualización diferencial
* `Mejora` El inspector jerárquico admite resúmenes de atributos, resaltado de padres, desplazamiento horizontal y selección ascendente de padres
* `Mejora` El inspector jerárquico elimina las líneas verticales de colores y mueve el indicador de expansión a la izquierda del texto, diferenciando contraído, expandido y sin hijos
* `Mejora` El diálogo de nodos resalta selecciones múltiples con el color del tema; copia mediante "Preguntar siempre/Copiar selector de objeto/Copiar selector encadenado", conservando la elección entre sesiones
* `Mejora` Diseño agrupado unificado de accesibilidad y administradores de conexión, con expansión/contracción por niveles y ajustes independientes
* `Mejora` Las listas de tareas y registros usan rutas abreviadas como el directorio de trabajo, conservando nombre y extensión reales del archivo de entrada
* `Mejora` El botón flotante y sus ventanas siguen el idioma y modo nocturno, con carga de información del menú en segundo plano
* `Mejora` El grupo "Conectar al ordenador" del panel lateral pasa a llamarse "Conexiones remotas"
* `Mejora` Los fondos claro y oscuro de la biblioteca predeterminada se unifican en "Color de fondo", con opciones claro, oscuro, seguimiento automático e inversión automática; se conservan elecciones anteriores
* `Mejora` Los ajustes de colores permiten localizar colores propios y la biblioteca inteligente actual; la búsqueda incluye colores de bibliotecas propias y grupos inteligentes
* `Mejora` La instalación APK utiliza diálogos de 3-Setup Installer; si falta se usa el instalador del sistema y se sugiere instalar el complemento; los contenedores apks/xapk/apkm/apkz lo requieren
* `Mejora` La conexión VSCode incorpora comprobación de actividad y longitud de datos para que conexiones caducadas y datos anómalos no afecten la comunicación _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `Mejora` Al guardar archivos desde VSCode se conserva la estructura relativa de directorios dentro del directorio de trabajo _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `Mejora` La conexión QR en modo cliente mejora el reconocimiento y traslada su acceso al menú del diálogo de direcciones
* `Mejora` Gradle permite emparejar dinámicamente versiones compatibles Kotlin/KSP/AGP/R8, reduce la memoria de inicio y elimina advertencias de compilación
* `Mejora` Datos de compatibilidad de plataformas Gradle y tareas de actualización trasladados a AutoJs6-Gradle-Platform-Versions; Scrapers permite sincronizar entre repositorios
* `Mejora` device.getAndroidId admite useSystemId booleano para obtener en Android 8.0+ el valor del sistema del usuario Android que ejecuta la aplicación mediante Shizuku/Root; lanza excepciones si fallan permisos o lectura y los sistemas anteriores ignoran el parámetro
* `Dependencia` Modularizado Android AXML
* `Dependencia` Modularizado Android ARSC Editor
* `Dependencia` Modularizado APK Signer
* `Dependencia` Modularizado APK Verifier
* `Dependencia` Modularizado ARSCLib versión 1.3.8
* `Dependencia` Modularizado Image Quantization versión 2.17.0
* `Dependencia` Modularizado OpenCV versión 4.8.0
* `Dependencia` Añadido juniversalchardet versión 2.0.4
* `Dependencia` Añadido AndroidX AsyncLayoutInflater versión 1.1.0
* `Dependencia` Añadido AndroidX Test Ext JUnit versión 1.3.0
* `Dependencia` Añadido ZXing Android Embedded versión 4.3.0
* `Dependencia` Añadido Android Hidden API Bypass versión 6.1
* `Dependencia` Actualizado Android SDK versión 36 -> 37
* `Dependencia` Actualizado Gradle 9 versión 9.4.0 -> 9.7.1
* `Dependencia` Actualizado AndroidX Lifecycle versión 2.6.2 -> 2.9.4
* `Dependencia` Actualizado AndroidX SavedState versión 1.2.1 -> 1.3.2
* `Dependencia` Actualizado AndroidX Emoji2 versión 1.3.0 -> 1.4.0
* `Dependencia` Actualizado AndroidX Window versión 1.0.0 -> 1.5.0
* `Dependencia` Eliminado Android Annotations versión 4.8.0
* `Dependencia` Eliminado Android dx versión 1.14
* `Dependencia` Eliminado AndroidX AppCompat (Legacy) versión 1.0.2
* `Dependencia` Eliminado AndroidX MultiDex versión 2.0.1
* `Dependencia` Eliminado Apache Commons Lang3 versión 3.18.0
* `Dependencia` Eliminado APK Parser (módulo local)
* `Dependencia` Eliminado Commons IO versión 2.8.0
* `Dependencia` Eliminado Flexmark Java HTML to Markdown versión 0.64.8
* `Dependencia` Eliminado GitHub API versión 1.306
* `Dependencia` Eliminado Glide KSP versión 5.0.5
* `Dependencia` Eliminado ICU4J versión 77.1
* `Dependencia` Eliminado Jackson DataBind versión 2.13.4.2
* `Dependencia` Eliminado Jaredrummler Android Device Names versión 2.1.1
* `Dependencia` Eliminado JavaMail for Android (biblioteca local)
* `Dependencia` Eliminado JCIP Annotations versión 1.0
* `Dependencia` Eliminado Jieba Analysis versión 1.0.3-SNAPSHOT (modified)
* `Dependencia` Eliminado JUnit Jupiter versión 5.13.4
* `Dependencia` Eliminado Log4j versión 1.2.17
* `Dependencia` Eliminado Android Logging Log4j versión 1.0.3
* `Dependencia` Eliminado MLKit Barcode Scanning versión 17.3.0
* `Dependencia` Eliminado MLKit Text Recognition Chinese versión 16.0.1
* `Dependencia` Eliminado OpenCC versión 1.2.2
* `Dependencia` Eliminado Pinyin4j versión 2.5.1
* `Dependencia` Eliminado Rapid OCR (biblioteca local)
* `Dependencia` Eliminado Retrofit2 Converter Gson versión 3.0.0
* `Dependencia` Eliminado Retrofit2 Kotlin Coroutines Adapter versión 0.9.2
* `Dependencia` Eliminado Spongy Castle Prov versión 1.58.0.0
* `Dependencia` Eliminado Tiny Sign versión 0.9

# v6.7.0

###### 2026/03/14

* `Nuevo` Funcion Centro de plugins, compatible con operaciones como instalar/desinstalar/actualizar plugins (entrada: boton del cajon de inicio/pestana de inicio)
* `Nuevo` Los accesos directos del lanzador en la pagina de configuracion agregan la opcion "Plugins", para saltar a la pagina del Centro de plugins mediante un acceso directo
* `Nuevo` Funcion Historial de versiones, compatible con ver/restaurar versiones historicas de archivos editables (entrada: boton del cajon de inicio/menu del administrador de archivos/menu del editor de codigo)
* `Nuevo` Menú "Teclado multifunción" > "Ajustes de símbolos" en el editor, con edición de símbolos y creación/importación/exportación por nivel de configuración
* `Nuevo` Funcion Papelera, compatible con ver/restaurar archivos/carpetas eliminados (entrada: boton del cajon de inicio)
* `Nuevo` Plugin Paddle OCR (PP-OCRv5), para reconocimiento optico de caracteres
* `Nuevo` Modulo cvt, para conversion de unidades de datos (consulte Documentacion del proyecto > [Conversion de unidades](https://docs.autojs6.com/#/cvt))
* `Nuevo` Metodo cvt.bytes, para conversion de unidades de datos de bytes (consulte Documentacion del proyecto > [Conversion de unidades](https://docs.autojs6.com/#/cvt))
* `Nuevo` Modulo fmt, para formateo de datos (consulte Documentacion del proyecto > [Formateo](https://docs.autojs6.com/#/fmt))
* `Nuevo` Metodo fmt.bytes, para formateo de datos de bytes (consulte Documentacion del proyecto > [Formateo](https://docs.autojs6.com/#/fmt))
* `Nuevo` Modulo zip, para operaciones de compresion y descompresion de archivos (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (consulte Documentacion del proyecto > [Zip](https://docs.autojs6.com/#/zip))
* `Nuevo` Modulo mediainfo, para ver informacion detallada de archivos multimedia (consulte Documentacion del proyecto > [Informacion multimedia](https://docs.autojs6.com/#/mediainfo))
* `Nuevo` Metodo s13n.bytes, para estandarizar datos de bytes (consulte Documentacion del proyecto > [Estandarizacion](https://docs.autojs6.com/#/s13n))
* `Nuevo` Metodo app.isDualInstalled, para detectar si una aplicacion dual esta instalada (requiere permisos de Shizuku o Root) _[`issue #450`](http://issues.autojs6.com/450)_
* `Nuevo` Metodo device.getSharedDeviceId, para obtener un ID de dispositivo compartido y unificado entre aplicaciones _[`issue #455`](http://issues.autojs6.com/455)_
* `Nuevo` Metodo device.setPointerLocation y otros metodos de la serie Toggleable, para establecer u obtener la opcion del sistema de ubicacion del puntero _[`issue #381`](http://issues.autojs6.com/381)_
* `Nuevo` El metodo dialogs.build admite parametros de opcion textAllCaps/(positive/negative/neutral)TextAllCaps, para controlar si el texto de los botones se muestra completamente en mayusculas
* `Nuevo` Metodo images.loadAsync, para obtener recursos de imagen de red de forma asincrona _[`issue #327`](http://issues.autojs6.com/327)_
* `Nuevo` Metodo ui.getNavigationBarHeight/propiedad navigationBarHeight (getter), para obtener la altura de la barra de navegacion _[`issue #456`](http://issues.autojs6.com/456)_
* `Nuevo` Método ui.getVisible(Status/Navigation)BarHeight/propiedad visible(Status/Navigation)BarHeight (getter) para obtener la altura visible de las barras de estado o navegación _[`issue #456`](http://issues.autojs6.com/456)_
* `Nuevo` Métodos http.put/del(ete)/head para enviar distintos tipos de solicitudes HTTP
* `Nuevo` Metodos como http.getAsync/postAsync/requestAsync, para enviar solicitudes de red de forma asincrona _[`issue #423`](http://issues.autojs6.com/423)_
* `Nuevo` El objeto body obtenido por los metodos relacionados con solicitudes del modulo http agrega metodos como stream/saveToFile/close _[`issue #452`](http://issues.autojs6.com/452)_
* `Nuevo` Los metodos relacionados con solicitudes del modulo http admiten parametros de opcion de control de cache (cacheBody/bodyCacheThresholdBytes)
* `Nuevo` Los metodos relacionados con solicitudes del modulo http admiten parametros de opcion inseguros (isInsecure/insecure), para ignorar excepciones relacionadas con certificados _[`issue #417`](http://issues.autojs6.com/417)_
* `Nuevo` Los metodos relacionados con solicitudes del modulo http admiten la opcion options.client, para configurar OkHttpClient.Builder (como followRedirects, etc.) _[`issue #454`](http://issues.autojs6.com/454)_
* `Nuevo` Propiedad auto.state (getter) y propiedad shizuku.state (getter), para obtener el estado del servicio de accesibilidad y del servicio Shizuku
* `Nuevo` Metodo UiObject#isShifted, para detectar cambios de posicion de controles _[`issue #469`](http://issues.autojs6.com/469)_
* `Nuevo` Metodo UiObject#snapshot, para capturar el estado actual de un control _[`issue #469`](http://issues.autojs6.com/469)_
* `Nuevo` Los selectores de la serie equivalente (UiSelector#id/text/...) y de la serie contiene (UiSelector#xxxContains) admiten parametros de expresion regular
* `Nuevo` Los parametros de expresion regular de los selectores admiten banderas (i, m, s, u)
* `Nuevo` Metodos runtime.(set/is)JavaPrimitiveWrap, para establecer u obtener la estrategia de envoltura de tipos primitivos de Java _[`issue #435`](http://issues.autojs6.com/435)_
* `Nuevo` Métodos autojs.restart/exit para reiniciar o salir de AutoJs6 y ejecutar un script indicado tras reiniciar _[`issue #460`](http://issues.autojs6.com/460)_
* `Nuevo` Metodo engines.myEngine().getStartTime(), para obtener la marca de tiempo de inicio del motor de scripts _[`issue #449`](http://issues.autojs6.com/449)_
* `Nuevo` Metodo global crash, para probar la pagina de informe de fallos
* `Nuevo` Metodo global structuredClone, para copia profunda de objetos JavaScript (consulte [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `Nuevo` Las expresiones regulares admiten sintaxis de lookbehind _[`issue #464`](http://issues.autojs6.com/464)_
* `Nuevo` El administrador de archivos agrega elementos de menu "Mover a" y "Copiar a", con soporte para abortar operaciones y mostrar estado de progreso
* `Nuevo` El cajon de inicio agrega la herramienta "Ubicacion del puntero"
* `Nuevo` El cajon de inicio agrega el interruptor "Acceso a todos los archivos"
* `Nuevo` Interruptor "Mostrar interfaz desde segundo plano" en el panel lateral (para dispositivos Xiaomi/Vivo)
* `Nuevo` La pagina de configuracion agrega la opcion "Envoltura de tipos primitivos de Java" _[`issue #435`](http://issues.autojs6.com/435)_
* `Nuevo` La pagina de configuracion agrega la opcion "Motor de programacion de tareas", con soporte para AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `Nuevo` La pagina de configuracion agrega la opcion "Icono del lanzador de aplicaciones", con soporte para iconos adaptativos/iconos con fondo transparente _[`issue #405`](http://issues.autojs6.com/405)_
* `Nuevo` Opción "Estrategia de reinicio" en los ajustes para configurar si el botón del panel lateral utiliza reinicio rápido
* `Nuevo` La pagina de configuracion agrega la opcion "Retraso en la solicitud de permiso de captura de pantalla", para evitar que la ventana de solicitud de permiso bloquee la pantalla
* `Nuevo` Las aplicaciones empaquetadas admiten inicio automatico al arrancar y agregan un interruptor en la pagina de configuracion
* `Nuevo` Las aplicaciones empaquetadas admiten accesos directos de aplicacion (pagina de registros/pagina de configuracion)
* `Nuevo` La pagina de configuracion de empaquetado agrega botones de deshacer/rehacer/guardar
* `Nuevo` La configuración de aplicaciones empaquetadas admite creación/modificación/eliminación/importación/exportación de perfiles _[`issue #483`](http://issues.autojs6.com/483)_
* `Nuevo` Herramienta Scrapers (run-scrapers.mjs) para actualizar automáticamente datos de construcción Gradle y datos comunes y plantillas README
* `Nuevo` Herramienta Gradle Wrapper switcher (gradle-wrapper-switcher), para cambiar la version principal local de Gradle (como Gradle 8.x y Gradle 9.x)
* `Corrección` Error en la restriccion de cantidad y tipos de parametros del metodo notice _[`issue #462`](http://issues.autojs6.com/462)_
* `Corrección` Problema por el que la propiedad ui.R (getter) no podia usarse correctamente
* `Corrección` Problema por el que las excepciones generadas en parametros de callback del metodo ui.post podian fallar en silencio
* `Corrección` Problema por el que metodos globales como isJavaClass/isJavaPackage eran invalidos
* `Corrección` Problema por el que el parametro timeout del metodo timers.keepAlive no funcionaba
* `Corrección` Problema por el que los metodos floaty.window/rawWindow no podian aceptar parametros de tipo cadena _[`issue #467`](http://issues.autojs6.com/467)_
* `Corrección` Problema por el que floaty.rawWindow mostraba algunos controles de forma anomala debido a que se pasaba un contexto incorrecto
* `Corrección` Problema por el que el metodo util.isString no era valido
* `Corrección` Problema por el que util.class[Name]/getClass[Name] podia devolver resultados incorrectos
* `Corrección` Problema por el que util.java.array no podia usar correctamente parametros de tipo como Boolean
* `Corrección` Problema por el que el objeto devuelto por threads.disposable() podia ser encapsulado inesperadamente al acceder a datos _[`issue #435`](http://issues.autojs6.com/435)_
* `Corrección` Problema por el que, al llamar varias veces al metodo console.build, las opciones de estilo de la ventana flotante de registros no se restablecian correctamente
* `Corrección` Problema por el que las opciones (title/content)BackgroundColor del metodo console.build sobrescribian opciones de transparencia o tintado _[`issue #458`](http://issues.autojs6.com/458)_
* `Corrección` En modo servidor, recrear la Activity al girar la pantalla o cambiar el idioma impedía mostrar registros en la consola VSCode _[`issue #385`](http://issues.autojs6.com/385)_
* `Corrección` Problema por el que metodos como console/toast podian perder ceros finales al mostrar Java Double
* `Corrección` Problema por el que no era posible usar metodos como console/toast para mostrar el tipo de datos BigInt
* `Corrección` toast podía causar fugas de memoria al mantener una referencia fuerte a Toast
* `Corrección` Problema por el que algunos objetos globales podian perder propiedades y metodos del prototipo de JavaScript
* `Corrección` Problema por el que el objeto this podia apuntar incorrectamente al usar expresiones JavaScript como valores de atributos en sintaxis XML
* `Corrección` Problema por el que el parametro de opcion linkify del metodo dialogs.build no funcionaba
* `Corrección` Problema por el que el metodo dialogs.build podia dañar los datos originales del parametro objeto
* `Corrección` Calculo incorrecto de la tasa de fotogramas interna del metodo setMaxFps del control de elemento canvas
* `Corrección` Calculo incorrecto del ancho al concatenar verticalmente con el metodo images.concat
* `Corrección` Problema por el que cancelar la autorizacion del metodo images.requestScreenCapture podia provocar un fallo de la aplicacion
* `Corrección` Problema por el que las capturas obtenidas por el metodo images.captureScreen podian tener orientacion incorrecta o relleno de bordes negros _[`issue #337`](http://issues.autojs6.com/337)_
* `Corrección` Problema por el que el valor 1 se interpretaba erroneamente como porcentaje al usar parametros de region en algunos metodos de images
* `Corrección` Problema por el que la funcion de la marca oneShot fallaba cuando ocurrian excepciones en algunos metodos relacionados con images _[`issue #372`](http://issues.autojs6.com/372)_
* `Corrección` Algunos métodos images podían provocar fugas de memoria _[`issue #372`](http://issues.autojs6.com/372)_
* `Corrección` Problema por el que images.save y el metodo ImageWrapper#saveTo no podian escribir correctamente archivos cuando la ruta proporcionada no existia
* `Corrección` Problema por el que el metodo ImageWrapper#recycle no reciclaba correctamente recursos de imagen
* `Corrección` Problema por el que algunos metodos sobrecargados de ocr podian no funcionar correctamente
* `Corrección` Problema por el que los resultados obtenidos por el metodo ocr.detect podian no coincidir con ocr.mode _[`issue #468`](http://issues.autojs6.com/468)_
* `Corrección` ML Kit OCR no se inicializaba correctamente en algunos dispositivos (corrección tentativa) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `Corrección` Problema por el que los eventos del servicio de accesibilidad registrados con auto.registerEvent podian ser eliminados por error por otros scripts _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `Corrección` Problema por el que el metodo UiObject#child podia lanzar ArrayIndexOutOfBoundsException en Android 10 _[`issue #416`](http://issues.autojs6.com/416)_
* `Corrección` El contexto Rhino podía ser inconsistente al llamar a métodos de módulos integrados
* `Corrección` Problema por el que, tras crear muchas ventanas flotantes en un script, podian quedar ventanas residuales al salir del script
* `Corrección` Problema por el que los parametros de configuracion de project.json no se analizaban correctamente al ejecutar un proyecto
* `Corrección` Problema por el que el parametro excludedDirs de project.json podia hacer fallar el analisis del archivo de configuracion al empaquetar un proyecto _[`issue #428`](http://issues.autojs6.com/428)_
* `Corrección` Problema por el que las aplicaciones empaquetadas no podian usar correctamente las funciones Paddle OCR y Rapid OCR
* `Corrección` Problema por el que el valor de la clave key en el archivo de configuracion project.json de aplicaciones empaquetadas podia modificarse inesperadamente
* `Corrección` Problema por el que al desplazarse por la lista de "Permisos requeridos" en la pagina de configuracion de empaquetado podia parpadear el icono de las casillas
* `Corrección` Problema por el que la funcion de empaquetado podia no funcionar correctamente en Android 7.x
* `Corrección` Problema por el que los nombres originales de las claves podian modificarse al guardar el archivo de configuracion de un proyecto de script
* `Corrección` Problema por el que numeros grandes en version de compilacion o tiempo de compilacion dentro del archivo de configuracion del proyecto de script podian provocar un fallo de la aplicacion
* `Corrección` Problema por el que obtener o reconstruir ImageReader con frecuencia podia provocar un fallo de la aplicacion al no haber fotogramas disponibles temporalmente en el buffer
* `Corrección` Problema por el que el observador de eventos de entrada InputEventObserver podia causar una ralentizacion evidente al iniciar la aplicacion
* `Corrección` Problema por el que el proceso de servicio de usuario Shizuku no terminaba correctamente y se acumulaban procesos _[`issue #474`](http://issues.autojs6.com/474)_
* `Corrección` Problema por el que, en la pagina de historial de versiones, algunos sistemas mostraban datos estadisticos incompletos debido a diferencias de fuente
* `Corrección` Problema por el que, en algunos dispositivos, los botones funcionales del administrador de archivos no activaban correctamente los eventos de clic (correccion tentativa) _[`issue #465`](http://issues.autojs6.com/465)_
* `Corrección` Problema por el que la aplicacion podia fallar al pulsar el boton atras o cambiar de pestana despues de mostrar resultados de busqueda del administrador de archivos
* `Corrección` Problema por el que la interfaz no se actualizaba automaticamente tras eliminar carpetas de proyecto en el administrador de archivos
* `Corrección` Problema por el que las lineas en blanco del editor de codigo mostraban caracteres de cuadro en algunos dispositivos (correccion tentativa)
* `Corrección` Problema por el que el teclado virtual podia tapar el contenido de texto en el editor de codigo
* `Corrección` Problema por el que el editor de codigo seguia permitiendo editar contenido en modo de solo lectura
* `Corrección` Problema por el que tocar el area del titulo o algunos elementos del menu en modo de solo lectura hacia fallar la aplicacion en el editor de codigo
* `Corrección` Problema por el que cargar archivos grandes en el editor de codigo podia provocar un fallo de la aplicacion (correccion tentativa)
* `Corrección` Inicializacion anomala del estado de los botones funcionales cuando el editor de codigo abria automaticamente un archivo nuevo
* `Corrección` Problema por el que poner la aplicacion en segundo plano mientras se editaba un archivo en el editor de codigo podia provocar un fallo
* `Corrección` Problema por el que, en Android 15+, al pulsar el boton fx en el editor de codigo no se mostraba el panel rapido de funciones de modulo (correccion tentativa)
* `Corrección` Problema por el que ErrorDialogActivity podia no iniciarse correctamente o desaparecer automaticamente en poco tiempo _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `Corrección` Tipos de parametros aceptados incorrectos en el constructor Canvas _[`issue #402`](http://issues.autojs6.com/402)_
* `Corrección` Problema por el que la funcion de copiar informacion detallada no funcionaba en la pagina de informe de fallos
* `Corrección` Problema por el que la logica personalizada de retroceso dejaba de funcionar en Android 16+, causando un comportamiento anomalo del boton atras
* `Corrección` Problema por el que la aplicacion podia fallar cuando los elementos de la lista del panel de tareas cambiaban con frecuencia
* `Corrección` Problema por el que el color del primer plano de la barra de navegacion podia comportarse de forma anomala con el modo nocturno al usar navegacion gestual de "tres botones"
* `Corrección` Problema por el que el cuadro de dialogo de informacion de tipo de archivo APK podia no obtener el nombre de la aplicacion ni la informacion del SDK
* `Corrección` Problema por el que, en modo cliente, al desconectar y volver a conectar en poco tiempo tras una conexion, aparecia un tiempo de espera de handshake y resultaba dificil reconectar
* `Corrección` Problema por el que, en modo cliente, operaciones como rotar la pantalla o cambiar el idioma, que recreaban la Activity, hacian que AutoJs6 reconstruyera siempre la conexion
* `Corrección` Problema por el que, al conectarse al plugin de VSCode de varias formas al mismo tiempo, la cantidad de registros impresos podia multiplicarse
* `Corrección` Problema por el que la generacion de codigo en la pagina de analisis de diseño podia fallar con controles de coleccion (correccion tentativa) _[`issue #328`](http://issues.autojs6.com/328)_
* `Corrección` Problema por el que el interruptor "Usar la tecla 'Bajar volumen' para controlar la grabacion" no funcionaba en la pagina de configuracion _[`issue #480`](http://issues.autojs6.com/480)_
* `Corrección` Problema por el que el interruptor "Mostrar sobre otras aplicaciones" en dispositivos Xiaomi podia abrir una pagina de configuracion incorrecta
* `Corrección` Las coordenadas del código generado al grabar scripts podían no coincidir con los píxeles reales de la pantalla _[`issue #480`](http://issues.autojs6.com/480)_
* `Corrección` Problema por el que la primera accion en los archivos de codigo generados por grabacion de scripts (como tocar o deslizar) siempre era ignorada
* `Corrección` Problema por el que operar archivos en segundo plano desde el dialogo flotante "Ejecutar script" podia provocar un fallo de la aplicacion
* `Corrección` Problema por el que el estado del boton flotante podia restablecerse al volver a entrar en la pagina principal tras finalizar el ciclo de vida de la Activity principal
* `Corrección` Problema por el que, despues de cancelar el proceso de descarga de actualizaciones, los paquetes de red seguian descargandose en segundo plano (correccion tentativa)
* `Corrección` Problema por el que la tarea Rebuild Project no podia completarse correctamente cuando se habilitaba la opcion isCleanup[Paddle/Rapid]Ocr en las herramientas de compilacion
* `Mejora` Funciones relacionadas con Paddle OCR (PP-OCRv3) modularizado _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `Mejora` Mejora de compatibilidad del metodo runtime.loadJar (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `Mejora` Mejora de cierta compatibilidad del metodo ui.getStatusBarHeight/propiedad statusBarHeight (getter)
* `Mejora` Metodos como console.log admiten mostrar informacion detallada al imprimir objetos globales (images, app, ocr, etc.)
* `Mejora` Los métodos de consola omiten parte de los datos gigantes según los límites para reducir el tiempo de impresión
* `Mejora` El valor inicial de la cuenta atrás de la consola flotante aumenta de 6 a 9 segundos
* `Mejora` La implementación de la consola flotante utiliza procesamiento sin bloqueos y colas para mejorar la eficiencia y el éxito de la configuración
* `Mejora` Las API de color de fondo de la consola flotante (transparencia/tinte/color base) se ajustan mejor a las pautas Android _[`issue #458`](http://issues.autojs6.com/458)_
* `Mejora` El metodo dialogs.build admite parametros de opcion nativos de MaterialDialog.Builder
* `Mejora` El metodo dialogs.build admite coincidencia flexible de cadenas para los parametros de opcion animation/linkify
* `Mejora` Mejoradas la estabilidad y coherencia de las propiedades device.rotation/orientation/width/height al obtener orientación y dimensiones de pantalla
* `Mejora` Mejora de eficiencia de ejecucion y compatibilidad de metodos como app.launchDual/startDualActivity
* `Mejora` android.graphics.Paint#setColor admite analizar correctamente parametros de color como ColorInt/ColorHex/ColorName
* `Mejora` Los mensajes de excepcion de tipos de argumentos en metodos relacionados con modulos integrados agregan informacion resumida de tipos
* `Mejora` Los módulos integrados (dayjs/cheerio/i18n) utilizan carga diferida global en lugar de ejecución como scripts
* `Mejora` Se optimiza el momento de cierre del menu al tocar elementos tras expandir el boton flotante del administrador de archivos
* `Mejora` El administrador de archivos/panel de tareas admite mostrar informacion estadistica de cantidad de archivos/tareas
* `Mejora` Cuando falla el guardado en el editor de codigo, se guarda automaticamente como borrador y admite guardar como archivo nuevo
* `Mejora` Mejora de cierta fluidez al cargar archivos grandes en el editor de codigo
* `Mejora` La funcion "Buscar/Reemplazar" del editor de codigo admite persistencia de estado y visualizacion en tiempo real del recuento de resultados
* `Mejora` Cuando el editor de codigo solicita guardar, se asegura de guardar correctamente antes de salir para reducir la tasa de fallos de guardado
* `Mejora` El estado del boton Guardar del editor de codigo coincide mejor con la logica subjetiva del usuario
* `Mejora` El editor de codigo admite pulsacion larga en teclas como "-", "=", "(", "[", "{", "<"
* `Mejora` El cuadro de dialogo del proceso de empaquetado agrega un boton "Abortar"
* `Mejora` Se mejora la forma de visualizacion del cuadro de dialogo del proceso de empaquetado y se agregan estadisticas de tiempo consumido
* `Mejora` Al instalar aplicaciones empaquetadas con una nueva configuracion, se sobrescriben las configuraciones relacionadas de la aplicacion antigua
* `Mejora` La pagina de configuracion de empaquetado marca por defecto los permisos necesarios (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) _[`issue #397`](http://issues.autojs6.com/397)_
* `Mejora` La pagina de configuracion de empaquetado admite lectura y configuracion de mas opciones (mostrar registros al iniciar/mostrar pantalla de inicio al iniciar/texto del lema de la pantalla de inicio/...)
* `Mejora` La pagina de configuracion de aplicaciones empaquetadas agrega un interruptor de servicio en primer plano _[`issue #406`](http://issues.autojs6.com/406)_
* `Mejora` Al guardar el archivo de configuracion del proyecto de script se agrega deteccion de conflictos de nombres de claves para evitar ambigüedad
* `Mejora` La pagina de informe de fallos admite pellizcar con dos dedos para ajustar el tamaño de fuente y agrega botones de funciones comunes
* `Mejora` Los iconos del lanzador de aplicaciones admiten caracteristicas de iconos adaptativos _[`issue #405`](http://issues.autojs6.com/405)_
* `Mejora` Cuando el motor Rhino falla al analizar firmas genericas, retrocede a tipos reflejados sin procesar para mejorar la disponibilidad de metodos reflectivos en versiones bajas de Android
* `Mejora` Los elementos de tipo interruptor en el cajon de inicio agregan separadores, admiten tocar el area del titulo para mostrar un cuadro de dialogo de detalles y, cuando corresponde, acceso rapido a ajustes del sistema
* `Mejora` Al iniciar o reiniciar AutoJs6, se puede tocar el area del titulo de "Modo cliente" en el cajon de inicio para abortar un intento de conexion en curso
* `Mejora` El modo cliente admite establecer conexion usando direcciones IPv6 y nombres de dominio
* `Mejora` El modo cliente admite avisos de deteccion para direcciones IPv4 de uso especial (loopback/broadcast/multicast/reservada/...)
* `Mejora` El modo cliente admite mostrar y gestionar el estado de la conexion (corregir direccion/abortar conexion)
* `Mejora` El modo servidor admite mostrar la cantidad de clientes conectados
* `Mejora` La lectura del permiso de notificaciones admite un mecanismo de reconexion automatica ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) para mejorar la estabilidad de funciones relacionadas con escucha de notificaciones
* `Mejora` El boton flotante mejora la seguridad al iniciar Activity desde segundo plano para evitar fallos de la aplicacion
* `Mejora` El cuadro de dialogo "Mas" del boton flotante usa carga asincrona de datos para mejorar la fluidez de visualizacion
* `Mejora` El cuadro de dialogo "Ejecutar script" del boton flotante agrega el elemento de menu "Inicio"
* `Mejora` El cuadro de dialogo "Ejecutar script" del boton flotante admite minimizacion y restauracion de estado, y hace el mayor esfuerzo por mantener la ventana residente o restaurarla automaticamente
* `Mejora` Se admite usar permisos de Shizuku para grabar scripts y reproducir grabaciones (la fluidez de reproduccion puede verse algo afectada) _[`issue #320`](http://issues.autojs6.com/320)_
* `Mejora` La pagina de configuracion de color de tema usa un metodo de posicionamiento rapido para localizar colores de tema y mejorar la eficiencia
* `Mejora` Se reemplaza [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager), ya obsoleto, por [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) y [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
* `Mejora` Los scripts de compilacion Gradle mejoran la eficiencia de extraccion de archivos en formato 7z
* `Mejora` Los scripts de compilacion Gradle admiten obtener la version detallada del IDE Android Studio (por ejemplo, "2025.1.4.7")
* `Mejora` Los scripts de compilacion Gradle admiten generar automaticamente los datos requeridos por la clase VersionCodesList para reducir la latencia de inicio de scripts
* `Mejora` Uso de [Version Catalogs](https://developer.android.com/build/migrate-to-catalogs) para gestionar centralizadamente versiones de dependencias y plugins de Gradle
* `Mejora` Scripts de compilacion Gradle modularizados, migrando la logica compartida a build-logic y abstraida como plugins de convencion
* `Mejora` Uso de plugins de convencion de Gradle para simplificar la logica de carga de bibliotecas AAR locales
* `Mejora` Se elimina la opcion de Gradle "android.enableJetifier=true" para adaptarse a Gradle 9
* `Mejora` Uso de [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) en lugar de sourceCompatibility/targetCompatibility para reducir diferencias del entorno de compilacion
* `Dependencia` Expandable Layout modularizado version 1.6.0
* `Dependencia` Expandable RecyclerView modularizado version 3.0.0-RC1
* `Dependencia` Modularizado RecyclerView Flexible Divider versión 1.4.0
* `Dependencia` Root Shell localizado version 1.6
* `Dependencia` Se agrega AndroidX Activity (KTX) version 1.12.2
* `Dependencia` Se agrega AndroidX Core (KTX) version 1.15.0
* `Dependencia` Se agrega AndroidX Work Runtime version 2.11.0
* `Dependencia` Se agrega AndroidX Interpolator version 1.0.0
* `Dependencia` Se agrega Room RxJava2 version 2.8.1
* `Dependencia` Actualizacion de Android SDK version 35 -> 36
* `Dependencia` Actualizacion de Gradle 8 version 8.14 -> 8.14.4
* `Dependencia` Actualizacion de Gradle 9 version 8.14 -> 9.4.0
* `Dependencia` Actualizacion de Rhino version 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT
* `Dependencia` Actualizacion de Desugar JDK Libs version 2.0.4 -> 2.1.5
* `Dependencia` Actualizacion de Android Material version 1.12.0 -> 1.13.0
* `Dependencia` Actualizacion de AndroidX AppCompat version 1.7.0 -> 1.7.1
* `Dependencia` Actualizacion de AndroidX Room version 2.7.1 -> 2.7.2
* `Dependencia` Actualizacion de AndroidX WebKit version 1.13.0 -> 1.14.0
* `Dependencia` Actualizacion de Material Progressbar version 1.4.2 -> 1.6.1
* `Dependencia` Actualizado Apache Commons Lang3 versión 3.16.0 -> 3.18.0
* `Dependencia` Actualizacion de Retrofit2 Retrofit version 2.11.0 -> 2.12.0
* `Dependencia` Actualizacion de Retrofit2 Converter Gson version 2.11.0 -> 2.12.0
* `Dependencia` Actualizacion de Retrofit2 RxJava2 version 2.11.0 -> 2.12.0
* `Dependencia` Actualizacion de Joda Time version 2.12.7 -> 2.14.0
* `Dependencia` Actualizacion de Gson version 2.11.0 -> 2.13.1
* `Dependencia` Actualizacion de ARSCLib version 1.3.1 -> 1.3.5
* `Dependencia` Actualizacion de Jsoup version 1.19.1 -> 1.20.1
* `Dependencia` Actualizado JUnit Jupiter versión 5.10.3 -> 5.13.0
* `Dependencia` Actualizacion de OpenCC version 1.2.0 -> 1.2.2
* `Dependencia` Se elimina Android Support Annotation v7 version 28.0.0
* `Dependencia` Se elimina Android Support AppCompat v7 version 28.0.0
* `Dependencia` Se elimina Android Support RecyclerView v7 version 28.0.0

# v6.6.4

###### 2025/05/31

* `Sugerencia` Cambio de API: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `Nuevo` Métodos util.dpToPx/spToPx/pxToDp/pxToSp para conversión de unidades de píxeles
* `Corrección` Subtítulos que pueden mostrarse incompletos al rotar la pantalla a orientación horizontal
* `Corrección` Parte del contenido de algunas páginas oculto por la barra de navegación lateral al rotar la pantalla a horizontal
* `Corrección` Área de coloreado de fondo de la barra de estado incompleta en algunas páginas bajo Android 15 _[`issue #398`](http://issues.autojs6.com/398)_
* `Corrección` El editor de código podría escribir archivos con una codificación de confianza insuficiente causando errores de decodificación (arreglo tentativa)
* `Mejora` Mejorada la adaptación del diseño de Acerca de la aplicación y el desarrollador, eliminando categorías innecesarias
* `Mejora` La sección de compilación de README.md incluye cómo localizar la página de ajustes _[`issue #404`](http://issues.autojs6.com/404)_
* `Dependencia` Añadido AndroidX ConstraintLayout versión 2.2.1

# v6.6.3

###### 2025/05/27

* `Nuevo` Historial de versiones para consultar registros multilingües y estadísticas
* `Nuevo` Método timers.keepAlive (ahora global) para mantener activo el script
* `Nuevo` Métodos de escucha de eventos como engines.on('start/stop/error', callback) para eventos globales del motor
* `Nuevo` Método images.detectMultiColors para verificación de colores multipunto _[`issue #374`](http://issues.autojs6.com/374)_
* `Nuevo` Métodos images.matchFeatures/detectAndComputeFeatures: búsqueda de imágenes a resolución completa (Ref a [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `Nuevo` Método images.compressToBytes para comprimir una imagen y generar un arreglo de bytes
* `Nuevo` Método images.downsample para reducir la resolución y devolver un nuevo objeto ImageWrapper
* `Nuevo` Método ui.keepScreenOn para mantener la pantalla encendida cuando la página UI está en foco
* `Nuevo` Propiedad ui.root (getter) para obtener el nodo "contenedor raíz del contenido de la ventana" del layout UI
* `Nuevo` El elemento webview admite diseños web basados en JsBridge (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (consulta: Ejemplos > Diseño > HTML interactivo/Vue2 + Vant (SFC)) _[`issue #281`](http://issues.autojs6.com/281)_
* `Nuevo` El inspector de diseño permite ocultar controles (por [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `Corrección` El contenido de la documentación en línea en la pestaña Documentos y la actividad Documentos podía quedar cubierto por la barra de navegación del sistema
* `Corrección` En algunas páginas, al pulsar botones de la Toolbar se podía activar por error el evento de clic en el título
* `Corrección` En algunos dispositivos, las líneas en blanco del editor de código mostraban cuadros; corrección provisional
* `Corrección` El cuadro de diálogo del selector de colores en los ajustes de color de tema podía apilarse infinitamente
* `Corrección` El color de fondo de la barra de estado en algunas páginas de Android 15 no seguía el color de tema de forma dinámica
* `Corrección` La tecla de subir volumen no detenía todos los scripts cuando el servicio de accesibilidad estaba desactivado
* `Corrección` Superposición del teclado IME al editar contenido de difusión personalizado en la página de tareas programadas
* `Corrección` Los controles dentro de elementos webview no podían activar correctamente el teclado en pantalla
* `Corrección` El diálogo de información de APK podía no obtener el nombre de la app ni la información del SDK
* `Corrección` El ejemplo del administrador de archivos podía no cargar automáticamente el contenido de subcarpetas al acceder a un directorio de proyecto
* `Corrección` El contenido superior en modo UI de Android 15 quedaba cubierto por la barra de estado
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
* `Mejora` Añadidos separadores degradados en el menú del inspector de diseño para agrupar funciones
* `Mejora` project.json admite la opción permissions para proyectos de scripts (por [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `Mejora` Al empaquetar un solo archivo se leen y seleccionan automáticamente los permisos declarados por la app instalada _[`issue #362`](http://issues.autojs6.com/362)_
* `Mejora` Ampliado el soporte de adaptación del color de tema y más tipos de controles
* `Mejora` Adaptabilidad de la anchura del cajón en la pantalla principal para modo horizontal o pantallas ultra-anchas
* `Mejora` Nuevos diseños horizontal y de pantalla pequeña para las páginas Acerca de la app y Desarrollador
* `Mejora` Las páginas de ajustes ofrecen la opción "Usar valor predeterminado" en los diálogos
* `Mejora` El botón flotante del administrador de archivos se oculta automáticamente al pulsar fuera
* `Mejora` La carga asíncrona mejora la fluidez de la lista en el administrador de archivos
* `Mejora` El formateador de código admite operadores `??`, `?.`, `??=`
* `Mejora` El editor permite leer y escribir archivos GB18030, UTF-16 (LE/BE), Shift_JIS y otras codificaciones
* `Mejora` El editor de código ahora muestra información detallada del archivo (ruta/codificación/salto de línea/total de bytes y caracteres, etc.) _[`issue #395`](http://issues.autojs6.com/395)_
* `Mejora` Se añaden mensajes de error para acciones de intent (editar / ver / instalar / enviar / reproducir, etc.)
* `Mejora` El atributo url de webview admite rutas relativas
* `Mejora` El parámetro ruta de ImageWrapper#saveTo admite rutas relativas
* `Mejora` images.save permite comprimir archivos PNG al usar el parámetro quality _[`issue #367`](http://issues.autojs6.com/367)_
* `Mejora` Se permite limpiar registros de actualizaciones ignoradas y direcciones de conexión en modo cliente
* `Mejora` La información de actualización de versión se muestra en varios idiomas según el idioma actual
* `Mejora` Mejorado el contenido y formato de los mensajes de excepción de scripts en la consola
* `Mejora` El código de ejemplo puede restablecer carpetas a su contenido inicial
* `Mejora` Mayor eficiencia al comprobar la firma de APK
* `Mejora` Optimizada la eficiencia y presentación de diálogos de información de archivos APK y multimedia
* `Mejora` El script de compilación Gradle mejora su capacidad de adaptación de versiones _[`discussion #369`](http://discussions.autojs6.com/369)_
* `Dependencia` Incluido Material Dialogs versión 0.9.6.0 (modularizado)
* `Dependencia` Incluido Material Date Time Picker versión 4.2.3 (modularizado)
* `Dependencia` Incluido libimagequant versión 2.17.0 (localizado)
* `Dependencia` Incluido libpng versión 1.6.49 (localizado)
* `Dependencia` Añadido ICU4J versión 77.1
* `Dependencia` Añadido Jsoup versión 1.19.1
* `Dependencia` Añadido Material Progressbar versión 1.4.2
* `Dependencia` Añadido Flexmark Java HTML to Markdown versión 0.64.8
* `Dependencia` Actualizado Gradle 8.14-rc-1 -> 8.14
* `Dependencia` Actualizado AndroidX Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `Nuevo` Métodos ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor y otros para configurar estilos de las barras de estado y navegación
* `Nuevo` Atributo ui.statusBarHeight (getter), utilizado para obtener la altura de la barra de estado _[`issue #357`](http://issues.autojs6.com/357)_
* `Nuevo` Método images.flip para voltear imágenes _[`issue #349`](http://issues.autojs6.com/349)_
* `Nuevo` Se añadió la opción 'extensión de archivo' en la página de configuración
* `Nuevo` La página de colores del tema admite agrupación, localización, búsqueda, historial y mejoras de la paleta
* `Corrección` Problema en el que el color de fondo de la barra de estado de Android 15 no coincide con el color del tema
* `Corrección` Problema en el que el método plugins.load no carga correctamente los plugins _[`issue #290`](http://issues.autojs6.com/290)_
* `Corrección` Problema en el que la biblioteca dx no funciona correctamente en Android 7.x _[`issue #293`](http://issues.autojs6.com/293)_
* `Corrección` Problema en el que ScriptRuntime puede presentar un estado de sincronización anómalo al utilizar require para importar módulos integrados (solución tentativa) _[`issue #298`](http://issues.autojs6.com/298)_
* `Corrección` Problema en el que el módulo notice carece de métodos de extensión como getBuilder _[`issue #301`](http://issues.autojs6.com/301)_
* `Corrección` Problema en el que los métodos shizuku/shell no aceptan parámetros de tipo cadena _[`issue #310`](http://issues.autojs6.com/310)_
* `Corrección` Problema en el que el método colors.pixel no acepta parámetros con imágenes de un solo canal _[`issue #350`](http://issues.autojs6.com/350)_
* `Corrección` Problema en el que los métodos engines.execScript / execScriptFile asignan de forma anómala el directorio de trabajo predeterminado al ejecutar scripts _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `Corrección` Problema en el que floaty.window / floaty.rawWindow no puede ejecutarse en subprocesos
* `Corrección` floaty.getClip podía no obtener correctamente el contenido del portapapeles _[`issue #341`](http://issues.autojs6.com/341)_
* `Corrección` Problema en el que el retorno de ui.inflate pierde métodos de prototipo como attr, on y click
* `Corrección` Problema en el que se produce un enlace incorrecto del contexto de ámbito al utilizar la sintaxis XML para usar una expresión JavaScript como valor de atributo _[`issue #319`](http://issues.autojs6.com/319)_
* `Corrección` Las excepciones de algunos métodos no podían capturarse con bloques try/catch _[`issue #345`](http://issues.autojs6.com/345)_
* `Corrección` Problema en el que la generación de código en la página de análisis de layouts puede provocar que la aplicación se bloquee _[`issue #288`](http://issues.autojs6.com/288)_
* `Corrección` Problema en el que las aplicaciones empaquetadas no pueden utilizar correctamente el módulo shizuku _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `Corrección` Problema en el que el editor de código, al saltar al final de una línea, puede mover el cursor al inicio de la siguiente línea
* `Corrección` Problema en el que pulsaciones rápidas consecutivas en elementos tipo diálogo de la página de configuración pueden provocar que la aplicación se bloquee
* `Mejora` Optimización del tamaño del archivo APK en la plantilla de aplicaciones empaquetadas
* `Mejora` La aplicación (y las aplicaciones empaquetadas) ahora soportan más permisos _[`issue #338`](http://issues.autojs6.com/338)_
* `Mejora` La pagina de configuracion de empaquetado ahora admite opciones de biblioteca Pinyin
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
* `Dependencia` Añadido Eclipse Paho Client Mqtt V3 versión 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `Dependencia` Actualizado la versión de Android SDK de 34 a 35
* `Dependencia` Actualizado Gradle de 8.12 a 8.14-rc-1
* `Dependencia` Actualizado Rhino de 1.8.0-SNAPSHOT a 1.8.1-SNAPSHOT
* `Dependencia` Actualizado AndroidX RecyclerView versión 1.3.2 -> 1.4.0
* `Dependencia` Actualizado AndroidX Room de 2.6.1 a 2.7.0
* `Dependencia` Actualizado AndroidX WebKit de 1.12.1 a 1.13.0
* `Dependencia` Actualizado Pinyin4j de 2.5.0 a 2.5.1

# v6.6.1

###### 2025/01/01

* `Nuevo` Módulo Pinyin para la conversión de pinyin en chino (Consulte la documentación del proyecto > [Pinyin chino](https://docs.autojs6.com/#/pinyin))
* `Nuevo` Módulo Pinyin4j para la conversión de pinyin en chino (Consulte la documentación del proyecto > [Pinyin chino](https://docs.autojs6.com/#/pinyin4j))
* `Nuevo` Métodos UiObject#isSimilar y UiObjectCollection#isSimilar para comprobar similitud de controles o colecciones
* `Nuevo` Método global currentComponent para obtener el nombre del componente activo
* `Corrección` Problema donde no se podía compilar correctamente el proyecto en determinados entornos debido a una reversión a una versión anterior
* `Corrección` Excepción de "valor no primitivo" que puede ocurrir al llamar a métodos inexistentes
* `Corrección` Problema donde los accesos directos de scripts no se podían agregar correctamente en algunos dispositivos (arreglo tentativa) _[`issue #221`](http://issues.autojs6.com/221)_
* `Corrección` Restricción de tipo de parámetro incorrecta en los métodos automator.click/longClick _[`issue #275`](http://issues.autojs6.com/275)_
* `Corrección` Problema donde los selectores no admitían parámetros del tipo ConsString _[`issue #277`](http://issues.autojs6.com/277)_
* `Corrección` Problema donde las instancias de UiObjectCollection carecían de métodos y propiedades propios
* `Mejora` La pagina de configuracion de empaquetado ahora admite configuracion de firma, gestion de keystore y ajustes de permisos (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `Mejora` Convertir automáticamente chino simplificado a Pinyin (incluyendo caracteres con múltiples tonos) para los sufijos de nombres de paquetes generados al empaquetar archivos individuales
* `Mejora` Mejorada la precisión en la identificación del nombre del paquete y de la actividad actual de la ventana flotante (Prioridad: Shizuku > Root > A11Y)
* `Mejora` Mejorada la precisión en la identificación de currentPackage y currentActivity (Prioridad: Shizuku > Root > A11Y)
* `Mejora` Restaurada la selección de texto de entradas individuales del registro mediante doble pulsación o pulsación prolongada _[`issue #280`](http://issues.autojs6.com/280)_
* `Mejora` Recuperar la mayor cantidad de información crítica posible para proyectos de scripts cuando el archivo project.json está dañado
* `Mejora` Soporte para argumentos negativos en los métodos UiSelector#findOnce y UiSelector#find
* `Mejora` Mayor soporte para formas abreviadas de prefijos de nombres de paquetes en selectores relacionados con elementos UI y className (por ejemplo, RecyclerView, Snackbar, etc.)
* `Mejora` Se mejoró la adaptabilidad de los métodos app.startActivity/startDualActivity
* `Mejora` Sincronizar el código más reciente del upstream del motor Rhino y adaptarlo al proyecto existente
* `Dependencia` Agregado Pinyin4j versión 2.5.0
* `Dependencia` Agregado Jieba Analysis versión 1.0.3-SNAPSHOT (modificada)
* `Dependencia` Actualizar la versión de Gradle de 8.11.1 a 8.12

# v6.6.0

###### 2024/12/02 - Reescritura de módulos integrados, actualización con precaución

* `Sugerencia` Los módulos integrados se reescriben en Kotlin para mejorar la eficiencia de ejecución del script, pero se necesitan mejoras iterativas
* `Sugerencia` El archivo init.js integrado está vacío por defecto, lo que permite a los desarrolladores ampliar los módulos integrados o montar módulos externos.
* `Nuevo` Módulos axios/cheerio para solicitudes de red y análisis HTML (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `Nuevo` Módulo sqlite para operar bases de datos SQLite (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (consulta: Documentación del proyecto > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Nuevo` Módulo MIME para procesamiento y análisis de cadenas de tipos MIME (Consulte la documentación del proyecto > [MIME](https://docs.autojs6.com/#/mime))
* `Nuevo` Módulo nanoid para generar identificadores de cadena (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `Nuevo` Módulo Sysprops para obtener datos de configuración del entorno de ejecución (Consulte la documentación del proyecto > [Propiedades del sistema](https://docs.autojs6.com/#/sysprops))
* `Nuevo` El módulo OCR admite el motor [Rapid OCR](https://github.com/RapidAI/RapidOCR)
* `Nuevo` El análisis de diseño admite el cambio de ventanas (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Método auto.clearCache para limpiar la caché de controles (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Método threads.pool para crear y utilizar grupos de hilos (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` images.matchTemplate admite useTransparentMask para buscar imágenes con transparencia (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Método images.requestScreenCaptureAsync para solicitar captura asíncronamente en modo UI (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` images.requestScreenCapture admite isAsync para obtener capturas de pantalla asíncronamente (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` images.on('screen_capture', callback) y otros métodos permiten escuchar la disponibilidad de capturas de pantalla (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Método images.stopScreenCapture para liberar explícitamente recursos de la solicitud de captura (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Los métodos images.psnr/mse/ssim/mssim/hist/ncc y images.getSimilarity se utilizan para obtener métricas de similitud de imágenes
* `Nuevo` El método images.isGrayscale se utiliza para determinar si una imagen es en escala de grises
* `Nuevo` Método images.invert para invertir los colores de imágenes
* `Nuevo` Los métodos s13n.point/time se utilizan para estandarizar objetos de punto y objetos de duración (Consulte la documentación del proyecto > [Normalización](https://docs.autojs6.com/#/s13n))
* `Nuevo` Métodos de console relacionados con gravity (alineación), touchThrough (pulsación a través) y backgroundTint (tinte del fondo) (consulta: Documentación del proyecto > [Consola](https://docs.autojs6.com/#/console))
* `Nuevo` Métodos Mathx.randomInt/randomFloat para obtener enteros o números flotantes aleatorios dentro del intervalo indicado
* `Nuevo` Los métodos app.launchDual/startDualActivity se utilizan para manejar el lanzamiento dual de aplicaciones (Requiere permisos de Shizuku o Root) (Experimental)
* `Nuevo` El método app.kill se utiliza para detener forzosamente una aplicación (Requiere permisos de Shizuku o Root)
* `Nuevo` Método floaty.getClip para obtener el portapapeles mediante el enfoque de una ventana flotante
* `Corrección` Fuga de memoria en el View Binding de subclases de Fragment (por ejemplo, [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48))
* `Corrección` Fuga de memoria de instancia en clases como [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21)
* `Corrección` Problema que causa que la aplicación se bloquee al solicitar permisos de captura de pantalla en Android 14 (por [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `Corrección` Problema que causa que la aplicación se bloquee al iniciar el servicio en primer plano en Android 14
* `Corrección` Problema con el botón de ejecución en el editor de código que no se enciende correctamente en Android 14
* `Corrección` Excepción 'FAILED ASSERTION' en la función de depuración del editor de código
* `Corrección` Problema con la imposibilidad de volver a depurar adecuadamente después de cerrar el editor durante el proceso de depuración del editor de código
* `Corrección` Problema de omisión potencial de caracteres finales al saltar al final de la línea en el editor de código
* `Corrección` La aplicación puede no funcionar correctamente después de empaquetar debido a la falta de archivos de biblioteca necesarios _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `Corrección` Problema con la aplicación empaquetada que no puede utilizar adecuadamente el módulo opencc
* `Corrección` Bloqueo de la aplicación cuando se edita el proyecto debido a recursos de icono especificados que faltan _[`issue #203`](http://issues.autojs6.com/203)_
* `Corrección` Incapacidad para usar parámetros adecuadamente para obtener recursos de captura de pantalla de orientación de pantalla especificada al solicitar permisos de captura de pantalla
* `Corrección` Problema con algunos dispositivos que no pueden agregar accesos directos de script correctamente (Reparación de prueba) _[`issue #221`](http://issues.autojs6.com/221)_
* `Corrección` Problema de retraso acumulativo en el envío de solicitudes con métodos relacionados con el envío de solicitudes en el módulo http _[`issue #192`](http://issues.autojs6.com/192)_
* `Corrección` El servicio Shizuku puede no funcionar correctamente antes de que AutoJs6 entre en la página de actividad principal (Reparación de prueba) _[`issue #255`](http://issues.autojs6.com/255)_
* `Corrección` El método random(min, max) puede tener resultados fuera de límites
* `Corrección` Problema donde el tipo de parámetro de resultado de los métodos pickup no puede pasar adecuadamente matrices vacías
* `Corrección` Problema donde el rectángulo de control obtenido por UiObject#bounds() puede ser modificado inadvertidamente, rompiendo su inmutabilidad
* `Corrección` El inicio automático del servicio de accesibilidad puede encontrar problemas de servicio anormales (Reparación de prueba)
* `Corrección` Problema con elementos de texto/botón/entrada donde el texto que contiene comillas dobles de ancho medio no puede ser analizado correctamente
* `Corrección` Problema con elementos de text/textswitcher donde la funcionalidad del atributo autoLink falla
* `Corrección` Problema con diferentes scripts que comparten erróneamente el mismo objeto ScriptRuntime
* `Corrección` Las variables globales HEIGHT y WIDTH perdían sus propiedades getter dinámicas
* `Corrección` Problema con un alto tiempo de inicio potencial causado por la carga de RootShell al iniciar un script
* `Corrección` Configurar el color de fondo de la consola flotante eliminaba el estilo rectangular con esquinas redondeadas
* `Corrección` Abrir la página de registros desde la actividad principal podía producir parpadeos
* `Corrección` Problema con el desencadenante de ViewPager al deslizar el control WebView a la izquierda o derecha en la página de documentos de la página principal
* `Corrección` Problema donde el administrador de archivos no puede reconocer extensiones de archivo que contienen letras mayúsculas
* `Corrección` El administrador de archivos puede no reconocer automáticamente el proyecto al ingresar por primera vez al directorio del proyecto
* `Corrección` Problema donde la página del administrador de archivos no puede refrescarse automáticamente después de eliminar la carpeta
* `Corrección` Problema con la clasificación de archivos y carpetas en el administrador de archivos donde los nombres de letra inicial ASCII se colocan detrás
* `Mejora` Se mejoro la experiencia del aviso al tocar el control "ABI no disponible" en la pagina de configuracion de empaquetado
* `Mejora` Al empaquetar un archivo individual se completa automáticamente un nombre de paquete normalizado y se avisa del filtrado de caracteres inválidos
* `Mejora` El paquete de archivo único admite la configuración automática del ícono y el incremento automático del número de versión y del nombre de versión basado en la misma aplicación de nombre de paquete instalada
* `Mejora` El archivo de configuración del paquete admite la opción abis/libs para especificar la arquitectura ABI incluidos por defecto y las bibliotecas
* `Mejora` Admite avisos de mensajes relevantes cuando las opciones abis/libs del archivo de configuración del paquete son inválidas o no están disponibles
* `Mejora` Admite el uso de Shizuku para controlar el interruptor de visualización 'Ubicación del puntero'
* `Mejora` Admite el uso de Shizuku para controlar los interruptores de permisos 'Medios de proyección' y 'Modificar configuraciones seguras'
* `Mejora` automator.gestureAsync/gesturesAsync admite parámetros de función de devolución de llamada
* `Mejora` El módulo tasks utiliza una forma síncrona para las operaciones de base de datos para evitar inconsistencias potenciales de acceso a datos
* `Mejora` El modo de ejecución de script admite parámetros de separación de modo con símbolo de tubería (por ejemplo, comenzando con `"ui|auto";`)
* `Mejora` El modo de ejecución de script admite comillas simples y comillas invertidas y permite omitir puntos y comas (por ejemplo, comenzando con `'ui';` o `'ui'`)
* `Mejora` El modo de ejecución de script admite la importación rápida de módulos de extensión integrados como axios, cheerio y dayjs (por ejemplo, comenzando con `"axios";`)
* `Mejora` El modo de ejecución de script admite parámetros de modo x o jsox para habilitar rápidamente los módulos de extensión de objetos integrados de JavaScript (por ejemplo, comenzando con `"x";`)
* `Mejora` Los atributos src y path de img admiten rutas relativas locales (por ejemplo, `<img src="a.png" />`)
* `Mejora` El editor de código admite la determinación inteligente de la ubicación de inserción al importar clases Java y nombres de paquetes
* `Mejora` El módulo images admite el uso de rutas directamente como parámetros de imagen
* `Mejora` importPackage admite parámetros de cadena
* `Mejora` La dirección IP en modo cliente admite detección inteligente al importar del portapapeles y conversión inteligente de la tecla de espacio
* `Mejora` El administrador de archivos admite la selección de prefijos predeterminados al crear nuevos archivos y genera automáticamente el sufijo numérico apropiado
* `Mejora` El administrador de archivos informa específicamente sobre el mensaje de excepción al ejecutar el proyecto _[`issue #268`](http://issues.autojs6.com/268)_
* `Mejora` El administrador de archivos admite más tipos y muestra símbolos de icono correspondientes (admite más de 800 tipos de archivos)
* `Mejora` Los tipos de archivos editables (jpg/doc/pdf/...) en el administrador de archivos han añadido botones de edición
* `Mejora` Los archivos APK en el administrador de archivos admiten la visualización de información básica, información de Manifesto y lista de permisos
* `Mejora` Los archivos de medios de audio/video en el administrador de archivos admiten la visualización de información básica e información de MediaInfo
* `Mejora` LeakCanary excluido de las versiones oficiales para reducir el tamaño del instalador
* `Mejora` Todos los comentarios en inglés en el código fuente del proyecto vienen acompañados de traducciones al chino simplificado para mejorar la legibilidad
* `Mejora` README y CHANGELOG admiten varios idiomas (Generado automáticamente por el script)
* `Mejora` Mejorar la adaptabilidad de la versión del script de construcción de Gradle
* `Dependencia` Incluir la versión 2.3.1 de MIME Util
* `Dependencia` Incluir la versión 12.6 de Toaster
* `Dependencia` Añadido EasyWindow (for Toaster) versión 10.3
* `Dependencia` Actualización de versión de Gradle de 8.5 a 8.11.1
* `Dependencia` Actualizar la versión 1.7.15-SNAPSHOT de Rhino -> 1.8.0-SNAPSHOT
* `Dependencia` Actualizar la versión 1.10.0 de Android Material -> 1.12.0
* `Dependencia` Actualizar la versión 1.7.0 de AndroidX Annotation -> 1.9.1
* `Dependencia` Actualizar la versión 1.6.1 de AndroidX AppCompat -> 1.7.0
* `Dependencia` Actualizar la versión 1.8.0 de AndroidX WebKit -> 1.12.1
* `Dependencia` Actualizado Apache Commons Lang3 versión 3.13.0 -> 3.16.0
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
* `Dependencia` Actualizado JUnit Jupiter versión 5.10.0 -> 5.10.3
* `Dependencia` Degradar la versión 5.0.0-alpha.11 de OkHttp3 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `Nuevo` Módulo opencc para convertir chino simplificado/tradicional y vocabulario regional (consulta: Documentación del proyecto > [Conversión de chino](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `Nuevo` Métodos [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) y [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) agregados a UiSelector _[`issue #115`](http://issues.autojs6.com/115)_
* `Nuevo` La pagina de configuracion de empaquetado ahora admite filtrado de ABI y bibliotecas (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Corrección` Problema de tamaño anormalmente grande del archivo empaquetado (Ref a [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Corrección` Problema en el que el paquete de la aplicación no muestra o imprime ciertos mensajes de error
* `Corrección` Se corrigio un problema por el que podia mostrarse un icono vacio tras seleccionar el icono de la app en la pagina de configuracion de empaquetado
* `Corrección` Excepción de contexto sin inicializar al incluir ML Kit OCR en aplicaciones empaquetadas
* `Corrección` Problema de métodos inoperativos ocr.recognizeText/detect
* `Corrección` Problema en el que algunos textos (como la página de registros) no coinciden con el idioma de configuración de la aplicación
* `Corrección` Problema de desbordamiento de texto en el interruptor del cajón de la página de inicio en ciertos idiomas
* `Corrección` Problema de servicios de accesibilidad que se cierran inmediatamente sin ningún mensaje en ciertos dispositivos _[`issue #181`](http://issues.autojs6.com/181)_
* `Corrección` Problema de cierres inesperados al usar botones físicos en ciertos dispositivos después de activar el servicio de accesibilidad (solución tentativa) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Corrección` Problema de funcionalidad anormal de pickup después de reiniciar el servicio de accesibilidad con auto(true) (solución tentativa) _[`issue #184`](http://issues.autojs6.com/184)_
* `Corrección` Problema de uso inoperativo del servicio de accesibilidad en Android 7.x
* `Corrección` Problema de cierre inesperado de la aplicación al arrastrar la ventana flotante creada por el módulo floaty (solución tentativa)
* `Corrección` Problema de uso de parámetros abreviados en app.startActivity _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Corrección` Problema de conflicto de nombres con importClass que causa una excepción _[`issue #185`](http://issues.autojs6.com/185)_
* `Corrección` Problema de uso inoperativo de runtime.loadJar/loadDex en Android 14 (solución tentativa)
* `Corrección` Problema de inoperatibilidad de "análisis de rango de diseño" y "análisis de jerarquía de diseño" en el panel de configuraciones rápidas de Android _[`issue #193`](http://issues.autojs6.com/193)_
* `Corrección` Problema de potencial [ANR](https://developer.android.com/topic/performance/vitals/anr) causado por la función de comprobación automática de actualizaciones (solución tentativa) _[`issue #186`](http://issues.autojs6.com/186)_
* `Corrección` Problema en el gestor de archivos al regresar a la página de ruta de trabajo después de hacer clic en "hacia arriba" en la carpeta de ejemplo _[`issue #129`](http://issues.autojs6.com/129)_
* `Corrección` Problema de botón de reemplazo no visible en el editor de código al usar la función de reemplazo
* `Corrección` Problema de cierre inesperado de la aplicación al mantener presionado el botón de borrar en el editor de código (solución tentativa)
* `Corrección` Problema de panel de funciones rápidas del módulo que no se muestra correctamente al hacer clic en el botón fx en el editor de código
* `Corrección` Problema de nombres de funciones de botón de panel de funciones rápidas que se desbordan en el editor de código
* `Mejora` El panel rápido de funciones del módulo del editor de código se adapta al modo nocturno
* `Mejora` La página de inicio de la aplicación empaquetada se adapta al modo nocturno y se ajusta el diseño de los iconos de la aplicación
* `Mejora` La pagina de configuracion de empaquetado ahora admite usar la tecla ENTER del teclado virtual para mover el foco del cursor
* `Mejora` La pagina de configuracion de empaquetado ahora admite tocar los titulos de ABI y de bibliotecas para alternar el estado de seleccionar todo
* `Mejora` La pagina de configuracion de empaquetado ahora usa una seleccion predeterminada de ABI mas inteligente y agrega avisos para opciones no disponibles
* `Mejora` El menú de clic derecho en carpetas del administrador de archivos añade una opción para empaquetar aplicaciones
* `Mejora` El administrador ajusta los menús según el tipo y las características de archivos y carpetas
* `Mejora` Cuando los servicios de accesibilidad están habilitados pero funcionan incorrectamente, se refleja un estado de anomalía en el interruptor del cajón de la página de inicio de AutoJs6
* `Mejora` La consola adjunta trazas detalladas al imprimir errores
* `Dependencia` Versión ARSCLib 1.2.4 adjunta
* `Dependencia` Versión Flexbox 3.0.0 adjunta
* `Dependencia` Versión OpenCC para Android 1.2.0 adjunta
* `Dependencia` Actualización de versión de Gradle de 8.5-rc-1 a 8.5

# v6.4.2

###### 2023/11/15

* `Nuevo` El método dialogs.build admite el parámetro de opción inputSingleLine
* `Nuevo` Método console.setTouchable para configurar si la consola flotante acepta toques _[`issue #122`](http://issues.autojs6.com/122)_
* `Corrección` Algunos métodos ocr no reconocían el parámetro de región _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `Corrección` Problema de no obtención de detalles de versión al encontrar una nueva versión en Android 7.x
* `Corrección` Problema de cierre inesperado de la aplicación al solicitar permisos de captura de pantalla en Android 14
* `Corrección` Problema de cierre inesperado de la aplicación al alternar rápidamente el interruptor de "botón flotante" en el cajón de la página principal
* `Corrección` Problema de botón flotante que todavía aparece después de reiniciar la aplicación tras cerrarlo con el menú
* `Corrección` En Android 13+, el cambio de idioma de AutoJs6 desde los ajustes no surtía efecto
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
* `Mejora` Los objetos de respuesta http permiten llamar repetidamente a body.string/bytes
* `Mejora` Soporte de empaquetado automático con GitHub Actions (por [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Mejora` Adaptación del entorno de construcción a la plataforma Temurin
* `Dependencia` Actualización de versión de Gradle de 8.4-rc-3 a 8.4
* `Dependencia` Actualización de versión de Android dx de 1.11 a 1.14

# v6.4.0

###### 2023/10/30

* `Nuevo` Soporte para Paddle Lite Engine en el módulo ocr (por [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `Nuevo` Soporte de empaquetado de aplicaciones con plugins embebidos y externos (por [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `Nuevo` Módulo WebSocket para comunicación WebSocket (consulta: Documentación del proyecto > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `Nuevo` Módulos barcode/qrcode para reconocer códigos de barras y QR (consulta: Documentación del proyecto > [Códigos de barras](https://docs.autojs6.com/#/barcode) / [Códigos QR](https://docs.autojs6.com/#/qrcode))
* `Nuevo` Módulo shizuku (Consulte Documentación del proyecto > [Shizuku](https://docs.autojs6.com/#/shizuku)) y permisos en el cajón de la página principal
* `Nuevo` Propiedades device.rotation/orientation y otras para obtener la orientación de pantalla
* `Nuevo` Soporte para acceso a propiedades estáticas en clases Java internas
* `Nuevo` Los ajustes del sistema Android permiten cambiar el idioma de la aplicación (Android 13+)
* `Nuevo` [Accesos directos de la aplicación](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) que pueden añadirse en los ajustes o activarse manteniendo pulsado el icono, para abrir documentación, ajustes y otras páginas
* `Corrección` Reintegración de ciertos PR (por [aiselp](https://github.com/aiselp)) para resolver problemas de cierre anómalo de scripts _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Corrección` Problema de uso inoperativo de nuevas API de AutoJs6 en el paquete de la aplicación (por [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `Corrección` Problema de estilo anómalo del paquete de la aplicación en modo noche del sistema
* `Corrección` Problema de pérdida de extensión de archivo al guardar archivo localmente con el plugin VSCode
* `Corrección` Problema de cierre inesperado al usar características de coroutines en el proyecto
* `Corrección` Problema de no registro del estado de posición del botón flotante al reiniciar o salir de la aplicación
* `Corrección` Problema de desbordamiento de botón flotante al rotar la pantalla múltiples veces _[`issue #90`](http://issues.autojs6.com/90)_
* `Corrección` Tras varias rotaciones no se recuperaban las coordenadas y el lado de pantalla del botón flotante
* `Corrección` Problema de no obtención de información de configuración actualizada del dispositivo al cambiar la orientación de pantalla _[`issue #153`](http://issues.autojs6.com/153)_
* `Corrección` Problema de fuente pequeña del título de la toolbar al rotar la pantalla a modo horizontal
* `Corrección` Problema de disposición apretada de pestañas en la página principal en modo horizontal
* `Corrección` Problema de visualización faltante o repetida de mensajes en el marco flotante en ciertos dispositivos
* `Corrección` Problema de mensajes flotantes ocultos cuando múltiples scripts se ejecutan simultáneamente _[`issue #67`](http://issues.autojs6.com/67)_
* `Corrección` Problema de cierre inesperado de la aplicación al hacer clic en el menú de análisis de diseño con broadcast
* `Corrección` Problema de segunda instancia y posteriores de WebSocket sin activar adecuadamente los listeners
* `Corrección` Reversión de redireccionamiento global de importPackage para evitar excepciones en algunos ámbitos específicos _[`issue #88`](http://issues.autojs6.com/88)_
* `Corrección` Copiar o exportar desde la página de registros podía bloquear la aplicación
* `Mejora` La exportación de registros pasa a llamarse Enviar y se implementa una verdadera función de exportación
* `Mejora` El envío de registros recorta automáticamente listas excesivas y muestra un aviso
* `Mejora` ocr es compatible con ML Kit y Paddle Lite (consulta: Documentación del proyecto > [Reconocimiento óptico de caracteres](https://docs.autojs6.com/#/ocr?id=p-mode))
* `Mejora` Mejorada la probabilidad de éxito de arranque automático del servicio de accesibilidad
* `Mejora` Migración de anotaciones de procesamiento de Kotlin desde kapt a KSP
* `Mejora` Las herramientas de construcción admiten IntelliJ IDEA EAP
* `Mejora` Adaptación de herramientas de construcción a la versión de Java para evitar problemas de "versión inválida"
* `Mejora` Optimización de lógica de downgrade de versión de IDE y plugins relacionados en herramientas de construcción, y aumento de capacidad de predicción de versiones
* `Mejora` Compatibilidad con plugin VSCode 1.0.7
* `Dependencia` Versión Rikka Shizuku 13.1.5 adjunta
* `Dependencia` Versión MLKit Barcode Scanning 17.2.0 adjunta
* `Dependencia` Actualización de versión de Android SDK de 33 a 34
* `Dependencia` Actualización de versión de OpenCV de 4.5.5 a 4.8.0 (Ref a [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependencia` Actualización de versión de Gradle de 8.3-rc-1 a 8.4-rc-3
* `Dependencia` Actualización de versión de Apache Commons Lang3 de 3.12.0 a 3.13.0
* `Dependencia` Actualización de versión de Glide de 4.15.1 a 4.16.0
* `Dependencia` Actualización de versión de Android Analytics de 14.3.0 a 14.4.0
* `Dependencia` Actualización de versión de AndroidX WebKit de 1.7.0 a 1.8.0
* `Dependencia` Actualización de versión de AndroidX Preference de 1.2.0 a 1.2.1
* `Dependencia` Actualización de versión de AndroidX Annotation de 1.6.0 a 1.7.0
* `Dependencia` Actualizado AndroidX RecyclerView versión 1.3.0 -> 1.3.2
* `Dependencia` Actualización de versión de Android Material de 1.9.0 a 1.10.0
* `Dependencia` Actualización de versión de AndroidX AppCompat de 1.4.2 a 1.6.1
* `Dependencia` Actualización de versión de Commons IO de 2.8.0 a 2.14.0
* `Dependencia` Actualización de versión de Jackson DataBind de 2.13.3 a 2.14.3
* `Dependencia` Remoción de Zeugma Solutions LocaleHelper versión 1.5.1

# v6.3.3

###### 2023/07/21

* `Nuevo` El editor de código admite comentarios (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Nuevo` Propiedad auto.stateListener para escuchar el estado de conexión del servicio de accesibilidad (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Nuevo` Métodos UiObject#nextSibling/lastChild/offset y otros para navegar entre controles (consulta: Documentación del proyecto > [Nodos de controles](https://docs.autojs6.com/#/uiObjectType))
* `Corrección` Problema con el plugin de VSCode que no puede analizar datos cuando el total de caracteres del script supera los cuatro dígitos decimales _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `Corrección` El complemento VSCode no guardaba archivos correctamente _[`issue #92`](http://issues.autojs6.com/92)_ _[`issue #94`](http://issues.autojs6.com/94)_
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

* `Nuevo` Módulo crypto para cifrar y descifrar datos (consulta: Documentación del proyecto > [Criptografía](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `Nuevo` Controles textswitcher/viewswitcher/viewflipper/numberpicker/video/search y otros en modo UI
* `Nuevo` Funciones para copiar y exportar registros en la página de registros _[`issue #76`](http://issues.autojs6.com/76)_
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
* `Mejora` Muestra la dirección IP objetivo en la subtítulo del cajón principal durante la conexión en modo cliente
* `Mejora` Soporte para mostrar advertencias de entradas inválidas al ingresar la dirección de destino en el modo cliente
* `Mejora` Soporte para usar la tecla Enter del teclado suave para establecer la conexión en modo cliente
* `Mejora` Mantenimiento del estado activo del modo servidor después de activarlo a menos que sea cerrado manualmente o termine el proceso de la aplicación _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `Mejora` Implementación de detección de versión bidireccional entre AutoJs6 y el plugin de VSCode con alertas de resultados anómalos _[`issue #89`](http://issues.autojs6.com/89)_
* `Mejora` Soporte para el módulo cheerio (Ref a [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `Mejora` Las instancias JsWebSocket permiten reconstruirse y conectarse mediante rebuild _[`issue #69`](http://issues.autojs6.com/69)_
* `Mejora` El soporte del módulo base64 para codificar y decodificar matrices numéricas y matrices de bytes Java
* `Mejora` Soporte para JavaMail for Android _[`issue #71`](http://issues.autojs6.com/71)_
* `Mejora` Uso del tipo de datos Blob para mejorar la adaptabilidad en entornos de red sin proxy al obtener información de actualización de versión
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
* `Nuevo` Controles console/imagebutton/ratingbar/switch/textclock/togglebutton y otros en modo UI
* `Nuevo` El soporte para el tipo de color [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) en los controles del modo UI (por ejemplo, color="orange")
* `Nuevo` El soporte total del método attr para establecer propiedades de controles en el modo UI (por ejemplo, ui.text.attr('color', 'blue'))
* `Nuevo` Soporte para valores de tipo booleano en atributos de control del modo UI de forma abreviada (por ejemplo, clickable="true" se puede abrevinar como clickable o isClickable)
* `Nuevo` Soporte para los atributos booleanos isColored y isBorderless de controles button
* `Nuevo` Método console.resetGlobalLogConfig para restablecer la configuración global de registros
* `Nuevo` Método web.newWebSocket para crear instancias WebSocket (consulta: Documentación del proyecto > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
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
* `Dependencia` Actualización de la versión de AndroidX WebKit 1.6.1 -> 1.7.0
* `Dependencia` Actualización de OkHttp3 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `Dependencia` Actualización de la versión de MLKit Text Recognition Chinese 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `Nuevo` Módulo ocr para reconocimiento óptico de caracteres (consulta: Documentación del proyecto > [Reconocimiento óptico de caracteres](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Nuevo` Módulo notice para enviar notificaciones (consulta: Documentación del proyecto > [Notificaciones](https://docs.autojs6.com/#/notice))
* `Nuevo` Módulo s13n para normalizar datos (consulta: Documentación del proyecto > [Normalización](https://docs.autojs6.com/#/s13n))
* `Nuevo` Clase Color para crear y manipular objetos de color (consulta: Documentación del proyecto > [Clase Color](https://docs.autojs6.com/#/colorType))
* `Nuevo` Método colors.setPaintColor para configurar el color de android.graphics.Paint (consulta: Documentación del proyecto > [Colores](https://docs.autojs6.com/#/color?id=m-setpaintcolor))
* `Nuevo` Función y ajuste para mantener la pantalla encendida mientras la aplicación está en primer plano
* `Nuevo` Lanzador independiente de documentación para leer la documentación de la aplicación (puede mostrarse u ocultarse en los ajustes)
* `Corrección` Problemas con la funcionalidad del método colors.toString
* `Corrección` Problemas de asignación incorrecta de los valores de color de colors.material
* `Corrección` Problemas con la funcionalidad de agregar automáticamente el prefijo de protocolo en el método app.openUrl
* `Corrección` Comportamiento anómalo de los métodos app.viewFile/editFile al no existir el archivo correspondiente en los parámetros
* `Corrección` Problema de que el callback de pickup no se llama
* `Corrección` Problemas con los selectores bounds/boundsInside/boundsContains al filtrar rectángulos nulos (por ejemplo, rectángulos con límites invertidos) _[`issue #49`](http://issues.autojs6.com/49)_
* `Corrección` Problema de que el servicio de accesibilidad tiende a fallar o tener errores de servicio (intento de reparación)
* `Corrección` Problema donde los valores negativos en la propiedad bounds mostrados en el análisis de diseño son reemplazados por comas
* `Corrección` Problema de que al cambiar el tema o el idioma al hacer clic o mantener presionado el tab de documentos de la página principal causa que la aplicación se bloquee
* `Corrección` Problema de temblor al ajustar el tamaño de la fuente con pellizco de dos dedos en el editor de texto
* `Corrección` Problemas para descargar algunas fuentes de dependencias en el script de construcción (integradas) _[`issue #40`](http://issues.autojs6.com/40)_
* `Corrección` Problema de que Tasker no puede agregar el plugin de acción de AutoJs6 (intento de reparación) _[`issue #41`](http://issues.autojs6.com/41)_
* `Corrección` Problema de que las anotaciones de ButterKnife no pueden resolver los ID de recursos en proyectos compilados con versiones JDK más altas _[`issue #48`](http://issues.autojs6.com/48)_
* `Corrección` Problemas con el uso del parámetro size en images.medianBlur según la documentación
* `Corrección` Problema de que al mostrar el nombre completo del script en el módulo engines se pierde el punto entre el nombre del archivo y su extensión
* `Corrección` Posibles errores de cálculo en la implementación interna del algoritmo de detección de distancia RGB ponderada (intento de reparación)
* `Corrección` Problemas con los métodos relacionados al flotante del módulo console antes de llamar al método show
* `Corrección` Problemas con la efectividad de los métodos como console.setSize _[`issue #50`](http://issues.autojs6.com/50)_
* `Corrección` Problemas de análisis del formato de fechas en los atributos minDate y maxDate de los controles de selección de fecha en el modo UI
* `Corrección` Problema de que aparecen dos tareas ejecutándose en el tab de "Tareas" de la página principal después de ejecutar un script rápidamente
* `Corrección` El estado del administrador de archivos podía restablecerse al volver desde otras páginas _[`issue #52`](http://issues.autojs6.com/52)_
* `Corrección` El estado de ordenación del administrador no coincidía con sus iconos
* `Mejora` El administrador muestra las fechas de modificación de archivos y carpetas
* `Mejora` El administrador recuerda el tipo de ordenación
* `Mejora` Añadir secciones de construcción del proyecto y ayuda para el desarrollo de scripts en el README.md _[`issue #33`](http://issues.autojs6.com/33)_
* `Mejora` Soporte para degradar la versión de JDK en el script de construcción de Gradle (fallback)
* `Mejora` Soporte para seleccionar automáticamente la versión adecuada de las herramientas de construcción según el tipo de plataforma y versión (limitado en cierto grado)
* `Mejora` Soporte para múltiples formas de pasar el parámetro de región en los métodos relacionados del módulo images (ver Documentos del Proyecto > [Tipos Omni](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Mejora` Soporte para abreviaciones como pref/homepage/docs/about en los parámetros del método app.startActivity
* `Mejora` Montar métodos globales del módulo web dentro del mismo para mejorar la usabilidad (ver Documentos del Proyecto > [World Wide Web](https://docs.autojs6.com/#/web))
* `Mejora` Implementación predeterminada de algunas configuraciones comunes de WebView en el método web.newInjectableWebView
* `Mejora` Añadir métodos de conversión y herramientas en el módulo colors, junto con más constantes estáticas y nombres de colores utilizables directamente como parámetros
* `Mejora` console incorpora métodos de estilo para ventanas flotantes y configuración unificada mediante el constructor build
* `Mejora` La consola flotante permite mover la ventana arrastrando el título
* `Mejora` La consola flotante admite cierre automático con retardo al finalizar el script
* `Mejora` La consola flotante y su ventana Activity permiten ajustar el tamaño de fuente con dos dedos
* `Mejora` Soporte para parámetros de tiempo de espera (timeout) en los métodos relacionados con el módulo http
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
* `Dependencia` Actualización de AndroidX WebKit 1.5.0 -> 1.6.1
* `Dependencia` Actualizado AndroidX RecyclerView versión 1.2.1 -> 1.3.0
* `Dependencia` Actualización de Zip4j 2.11.2 -> 2.11.5
* `Dependencia` Actualizado JUnit Jupiter versión 5.9.2 -> 5.9.3
* `Dependencia` Actualización de AndroidX Annotation 1.5.0 -> 1.6.0
* `Dependencia` Actualización de Jackson DataBind 2.14.1 -> 2.14.2
* `Dependencia` Actualización de Desugar JDK Libs 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `Nuevo` Rediseño y reescritura de la documentación del proyecto (parcialmente completado)
* `Nuevo` Adaptación a español, francés, ruso, árabe, japonés, coreano, inglés, chino tradicional y otros idiomas
* `Nuevo` Opciones de configuración de la ruta de trabajo con selección de ruta/historial de rutas/sugerencias inteligentes de valores predeterminados
* `Nuevo` El administrador permite ascender directorios sucesivamente hasta "Almacenamiento interno"
* `Nuevo` El administrador de archivos permite establecer cualquier directorio como ruta de trabajo rápidamente
* `Nuevo` Las actualizaciones pueden ignorarse y se gestionan los registros ignorados
* `Nuevo` El editor de texto admite ajuste de tamaño de fuente a través de pinch zoom
* `Nuevo` Selector UiSelector#idHex para filtrar por ID hexadecimal de controles (consulta: Documentación del proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType))
* `Nuevo` Selector UiSelector#action para filtrar por acciones admitidas por controles (consulta: Documentación del proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType))
* `Nuevo` Selectores Match como UiSelector#xxxMatch para coincidencias mediante expresiones regulares (consulta: Documentación del proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType))
* `Nuevo` Selector UiSelector#pickup para obtener controles combinando condiciones (consulta: Documentación del proyecto > [Selectores](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `Nuevo` Método UiObject#detect para explorar controles (consulta: Documentación del proyecto > [Nodos de controles](https://docs.autojs6.com/#/uiObjectType))
* `Nuevo` Método UiObject#compass para navegar entre controles (consulta: Documentación del proyecto > [Nodos de controles](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `Nuevo` Método global wait para esperar condiciones (consulta: Documentación del proyecto > [Objeto global](https://docs.autojs6.com/#/global?id=m-wait))
* `Nuevo` Métodos globales cX/cY/cYx para escalar coordenadas (consulta: Documentación del proyecto > [Objeto global](https://docs.autojs6.com/#/global?id=m-cx))
* `Nuevo` Tipo global App para enumerar aplicaciones (consulta: Documentación del proyecto > [Clase de enumeración de aplicaciones](https://docs.autojs6.com/#/appType))
* `Nuevo` Módulo i18n para gestionar varios idiomas en JavaScript (basado en banana-i18n) (consulta: Documentación del proyecto > Internacionalización)
* `Corrección` Problemas con el cambio de idioma de la aplicación que provocan parpadeo de texto y fallos de botones en algunas páginas
* `Corrección` Problema donde la barra de herramientas del proyecto no se mostraba al iniciar la aplicación con una ruta de trabajo establecida
* `Corrección` Problema donde la ruta de trabajo podría cambiar automáticamente al cambiar el idioma _[`issue #19`](http://issues.autojs6.com/19)_
* `Corrección` Retraso significativo al iniciar tareas programadas (intento de solución) _[`issue #21`](http://issues.autojs6.com/21)_
* `Corrección` Problema donde los módulos internos no funcionaban correctamente debido a la declaración de nombres de módulos JavaScript sobreescritos _[`issue #29`](http://issues.autojs6.com/29)_
* `Corrección` Problema en sistemas Android más recientes donde los iconos del panel de configuración rápida no se cerraban automáticamente al ser tocados (intento de solución) _[`issue #7`](http://issues.autojs6.com/7)_
* `Corrección` Problema de superposición de algunas páginas con la barra de notificaciones en sistemas Android más recientes
* `Corrección` En Android 10+ no funcionaban correctamente los ejemplos de configuración de color del pincel
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
* `Corrección` Problemas de reconocimiento de botones en la barra de navegación en sistemas Android 7.x con el modo nocturno deshabilitado
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
* `Corrección` Problema de excepciones no cerradas en métodos como http.post
* `Corrección` Problema donde la información del canal Alpha se perdía en colores con canal Alpha de 0 en el método colors.toString
* `Mejora` Redireccionar clases públicas de Auto.js 4.x para lograr la máxima compatibilidad descendente posible (limitada)
* `Mejora` Fusionar todos los módulos del proyecto para evitar posibles problemas de referencia circular (eliminar temporalmente el módulo inrt)
* `Mejora` Migrar la configuración de construcción de Gradle de Groovy a KTS
* `Mejora` Agregar soporte multilingüe para mensajes de excepción de Rhino
* `Mejora` El interruptor de permisos del cajón de la página principal solo muestra mensajes cuando está activado
* `Mejora` La disposición del cajón de la página principal se adhiere directamente debajo de la barra de estado para evitar incompatibilidades de la barra de color en la parte superior
* `Mejora` Mostrar la dirección IP del dispositivo correspondiente en el cajón de la página principal después de que el cliente y servidor se conecten
* `Mejora` Compatibilidad de funciones de comprobación de actualizaciones, descarga y aviso de actualización con el sistema Android 7.x
* `Mejora` Rediseñar la página de configuración (migración a AndroidX)
* `Mejora` La página de configuración admite la presión prolongada sobre las opciones de configuración para obtener información detallada
* `Mejora` Opción "Seguir el sistema" en el modo nocturno (Android 9+)
* `Mejora` Compatibilidad de la pantalla de inicio de la aplicación con el modo nocturno
* `Mejora` Agregar más opciones de colores Material Design al tema de la aplicación
* `Mejora` Compatibilidad del color del texto de sugerencia en la caja de búsqueda de la página principal con el modo nocturno
* `Mejora` Adaptación de diálogos/texto/FAB/AppBar/elementos de listas al modo nocturno
* `Mejora` Agregar identificadores numéricos a los iconos de la aplicación para mejorar la experiencia del usuario con múltiples versiones de código abierto coexistentes
* `Mejora` Aligerar y adaptar los iconos de elementos de lista en el gestor de archivos/panel de tareas al color del tema
* `Mejora` Compatibilidad de páginas como documentos/configuración/sobre/colores del tema/análisis de diseño y menús de botones flotantes con el modo nocturno
* `Mejora` Hacer clic en el botón de análisis de diseño del botón flotante realiza directamente el análisis de rango de diseño
* `Mejora` El tema de análisis de diseño es adaptable (la ventana flotante sigue el tema de la aplicación, el panel de configuración rápida sigue el tema del sistema)
* `Mejora` Compatibilidad del diseño de páginas con la disposición RTL (Right-To-Left) en la medida de lo posible
* `Mejora` Agregar efectos de animación de iconos a la página sobre
* `Mejora` Actualización automática del año en el texto de la declaración de derechos de autor en la página sobre
* `Mejora` Después de la instalación inicial de la aplicación, determinar y establecer automáticamente un directorio de trabajo adecuado
* `Mejora` Desactivar la función de zoom a dos dedos en la página de documentos para evitar la visualización anormal del contenido
* `Mejora` Simplificar el nombre y la ruta de las tareas mostradas en los elementos de la lista del panel de tareas por ruta relativa
* `Mejora` Abreviar el texto de los botones del editor de texto para evitar desbordamiento del contenido
* `Mejora` Soporte para restaurar el tamaño de fuente predeterminado en la configuración del editor de textos
* `Mejora` Mejorar la velocidad de respuesta al hacer clic en el botón flotante
* `Mejora` Reordenar la lista de información de control de diseño según la posible frecuencia de uso
* `Mejora` Optimizar el formato de salida automáticamente al hacer clic y copiar sobre información de control de diseño según el tipo de selector
* `Mejora` ui.R y auto.R pueden obtener cómodamente el ID de recursos de AutoJs6
* `Mejora` Al seleccionar archivos usando la ventana flotante, presionar la tecla de retorno lleva al directorio superior en lugar de cerrar directamente la ventana
* `Mejora` Soporte para detección de validez numérica y conversión automática de símbolos punteados mientras se ingresa una dirección en modo cliente conectándose a la computadora
* `Mejora` Agregar protección contra sobrescritura a ciertos objetos globales y módulos integrados (consultar documentos del proyecto > Objetos globales > [Protección contra sobrescritura](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `Mejora` importClass e importPackage admiten parámetros de cadena y parámetros de longitud variable
* `Mejora` ui.run admite la impresión de información de seguimiento de pila en caso de excepción
* `Mejora` Los métodos relacionados con el uso de aplicaciones en el módulo app admiten parámetros de tipo App y parámetros de alias de aplicación
* `Mejora` app.startActivity y otros admiten parámetros de opción de URL (ver código de ejemplo > Aplicaciones > Intención)
* `Mejora` Los métodos relacionados con la devolución de llamada asincrónica en el módulo dialogs admiten la omisión de parámetros pre-rellenos
* `Mejora` El módulo device devuelve null en lugar de lanzar una excepción cuando la obtención de IMEI o número de serie de hardware falla
* `Mejora` Aumentar el brillo del texto en el log de ventana flotante que muestra console.show para mejorar la legibilidad del contenido
* `Mejora` ImageWrapper#saveTo admite guardar archivos de imagen en rutas relativas
* `Mejora` Objeto global colors rediseñado con modos HSV/HSL y otros (consulta: Documentación del proyecto > [Colores](https://docs.autojs6.com/#/color))
* `Dependencia` Localización de la versión 1.4.3 de Android Job
* `Dependencia` Localizado Android Plugin Client SDK For Locale versión 9.0.0
* `Dependencia` Localización de GitHub API versión 1.306
* `Dependencia` Añadida la versión 1.0 de JCIP Annotations
* `Dependencia` Añadida la versión 1.5.0 de AndroidX WebKit
* `Dependencia` Añadida la versión 2.8.0 de Commons IO
* `Dependencia` Añadida la versión 2.0.0 de Desugar JDK Libs
* `Dependencia` Añadida la versión 2.13.3 de Jackson DataBind
* `Dependencia` Añadida la versión 2.1.0 de Jaredrummler Android Device Names
* `Dependencia` Añadida la versión 1.0.6 de Jaredrummler Animated SVG View
* `Dependencia` Sustitución de ColorPicker versión 2.1.7 de Jrummyapps por ColorPicker versión 1.1.0 de Jaredrummler
* `Dependencia` Actualización de Android SDK versión 32 a 33
* `Dependencia` Actualización de la versión de Gradle de 7.5-rc-1 a 8.0-rc-1
* `Dependencia` Actualización de la versión de las herramientas de construcción de Gradle de 7.4.0-alpha02 a 8.0.0-alpha09
* `Dependencia` Actualización de la versión de Kotlin Gradle Plugin de 1.6.10 a 1.8.0-RC2
* `Dependencia` Actualización de la versión de Android Material de 1.6.0 a 1.7.0
* `Dependencia` Actualización de la versión de AndroidX Annotation de 1.3.0 a 1.5.0
* `Dependencia` Actualización de la versión de AndroidX AppCompat de 1.4.1 a 1.4.2
* `Dependencia` Actualización de la versión de Android Analytics de 13.3.0 a 14.0.0
* `Dependencia` Actualización de la versión de Gson de 2.9.0 a 2.10
* `Dependencia` Actualización de la versión de Joda Time de 2.10.14 a 2.12.1
* `Dependencia` Actualización de la versión de Kotlinx Coroutines de 1.6.1-native-mt a 1.6.1
* `Dependencia` Actualización de la versión de OkHttp3 de 3.10.0 a 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `Dependencia` Actualización de la versión de Zip4j de 2.10.0 a 2.11.2
* `Dependencia` Actualización de la versión de Glide de 4.13.2 a 4.14.2
* `Dependencia` Actualizado JUnit Jupiter versión 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `Nuevo` Función de actualización de software (ver página de configuración) (no compatible con Android 7.x por el momento)
* `Corrección` Problema de lectura y escritura en almacenamiento externo en Android 10 _[`issue #17`](http://issues.autojs6.com/17)_
* `Corrección` Mantener pulsado en el editor podía bloquear la aplicación _[`issue #18`](http://issues.autojs6.com/18)_
* `Corrección` "Eliminar línea" y "Copiar línea" del menú de pulsación prolongada no funcionaban
* `Corrección` Faltaba "Pegar" en el menú de opciones del editor
* `Mejora` Algunos mensajes de excepción convertidos en recursos de cadena (en/zh)
* `Mejora` Ajuste de los botones y adición de diferenciación de color en el diálogo de contenido no guardado
* `Dependencia` Añadido GitHub API versión 1.306
* `Dependencia` Sustitución de retrofit2-rxjava2-adapter versión 1.0.0 por adapter-rxjava2 versión 2.9.0

# v6.1.0

###### 2022/05/26 - Cambio de nombre de paquete, actualiza con precaución

* `Sugerencia` Cambio del nombre del paquete de la aplicación a org.autojs.autojs6 para evitar conflictos con el nombre del paquete de la aplicación Auto.js de código abierto
* `Nuevo` Interruptor "Permiso de proyección multimedia" en el panel lateral (Root/ADB) (detección de estado experimental)
* `Nuevo` El administrador permite mostrar archivos y carpetas ocultos (consulta: Ajustes)
* `Nuevo` Función de comprobación forzada de Root (consulta: Ajustes y ejemplos)
* `Nuevo` Módulo autojs para obtener y manipular información de AutoJs6 (consulta: Ejemplos > AutoJs6)
* `Nuevo` Módulo tasks para gestionar tareas programadas (consulta: Ejemplos > Tareas)
* `Nuevo` Método console.launch para abrir la página de registros
* `Nuevo` Herramienta util.morseCode para procesar código Morse (consulta: Ejemplos > Herramientas > Código Morse)
* `Nuevo` Herramienta util.versionCodes para consultar versiones Android (consulta: Ejemplos > Herramientas > Consulta de versiones Android)
* `Nuevo` Métodos util.getClass y otros para obtener clases Java y sus nombres (consulta: Ejemplos > Herramientas > Obtener clases y nombres)
* `Nuevo` Método timers.setIntervalExt para ejecución periódica condicional (consulta: Ejemplos > Temporizadores > Ejecución periódica condicional)
* `Nuevo` Métodos colors.toInt/rgba y otros para convertir colores (consulta: Ejemplos > Imágenes y colores > Conversión básica de colores)
* `Nuevo` Métodos automator.isServiceRunning/ensureService para comprobar y garantizar la disponibilidad de la accesibilidad
* `Nuevo` Métodos automator.lockScreen y otros para las operaciones de accesibilidad incorporadas en Android 9 (consulta: Ejemplos > Accesibilidad > Novedades de Android 9)
* `Nuevo` Métodos automator.headsethook y otros para las operaciones de accesibilidad incorporadas en Android 11 (consulta: Ejemplos > Accesibilidad > Novedades de Android 11)
* `Nuevo` Método automator.captureScreen para obtener capturas mediante accesibilidad (consulta: Ejemplos > Accesibilidad > Obtener capturas de pantalla)
* `Nuevo` El método dialogs.build admite parámetros de opción como animation, linkify, etc. (ver Código de Ejemplo > Cuadros de diálogo > Personalización del cuadro de diálogo)
* `Corrección` Los parámetros de opción inputHint, itemsSelectedIndex, etc. de dialogs.build presentan un comportamiento anómalo
* `Corrección` Solución a problemas con el parámetro de callback de JsDialog#on('multi_choice')
* `Corrección` Corrección del problema donde UiObject#parent().indexInParent() siempre devolvía -1 _[`issue #16`](http://issues.autojs6.com/16)_
* `Corrección` Resolución del problema donde las promesas Thenable de Promise.resolve() podrían no ser llamadas cerca del final del script
* `Corrección` Posibles errores ortográficos en nombres de paquetes o clases (boardcast -> broadcast/auojs -> autojs)
* `Corrección` images.requestScreenCapture podía bloquear la aplicación en versiones nuevas de Android (API >= 31)
* `Corrección` Llamadas simultáneas a images.requestScreenCapture desde varias instancias de scripts podían bloquear la aplicación
* `Corrección` Solución al problema de posible congelación al llamar a new RootAutomator()
* `Mejora` RootAutomator no podrá ser instanciado sin permisos de Root
* `Mejora` Rediseño de la página 'Sobre la Aplicación y el Desarrollador'
* `Mejora` Refactorización de todos los módulos integrados de JavaScript
* `Mejora` Refactorización de todos los scripts de construcción de Gradle y adición de un script de configuración común (config.gradle)
* `Mejora` Soporte de las herramientas de construcción de Gradle para gestión automática de versiones y nombrado automático de archivos de construcción
* `Mejora` Las herramientas de construcción de Gradle ahora incluyen la tarea de agregar CRC32 al nombre de los archivos de construcción (appendDigestToReleasedFiles)
* `Mejora` shell incluye las excepciones de llamada en el resultado en lugar de lanzarlas directamente (no requiere try/catch)
* `Mejora` Reemplazo del módulo json2 por JSON integrado de Rhino
* `Mejora` auto.waitFor admite un parámetro de tiempo límite
* `Mejora` threads.start admite funciones flecha como parámetro
* `Mejora` console.trace admite un parámetro de nivel de registro (consulta: Ejemplos > Consola > Imprimir pila de llamadas)
* `Mejora` La fuente de la consola utiliza Material Color para mejorar la legibilidad en temas normales y nocturnos
* `Mejora` device.vibrate admite patrones y código Morse (consulta: Ejemplos > Dispositivo > Vibración por patrones/código Morse)
* `Mejora` Adaptación de permisos de lectura/escritura en almacenamiento externo para versiones recientes de Android (API >= 30)
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
* `Nuevo` Módulo recorder para medir tiempos (consulta: Ejemplos > Cronómetro)
* `Nuevo` La accesibilidad puede activarse automáticamente mediante "Modificar ajustes seguros", con el interruptor correspondiente en los ajustes
* `Corrección` Solución al problema del panel de configuración rápida que no se retraía automáticamente tras tocar los iconos relevantes (intento de solución) _[`issue #7`](http://issues.autojs6.com/7)_
* `Corrección` Solución al posible fallo de AutoJs6 al usar parámetros de "mostrar con fuerza" en toast
* `Corrección` Solución al posible fallo de AutoJs6 debido a información incompleta en los encabezados de datos de transmisión por Socket
* `Mejora` Activar automáticamente el servicio de accesibilidad según configuración al iniciar o reiniciar AutoJs6
* `Mejora` Intentar activar automáticamente el servicio de accesibilidad al activar el botón flotante
* `Mejora` Completar elementos de traducción al inglés en todos los archivos de recursos
* `Mejora` Ajustado el diseño del panel lateral y reducido el espacio entre entradas
* `Mejora` Añadido interruptor de estado del servicio en primer plano en el cajón de la página principal
* `Mejora` Sincronizar automáticamente el estado del interruptor cuando el cajón de la página principal se despliega
* `Mejora` Detección de estado y notificación de resultados al mostrar la posición del cursor
* `Mejora` Soporte para sistemas operativos de 64 bits (gracias a [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Mejora` Aplicar configuración de transparencia al inicializar el botón flotante (sin necesidad de clics adicionales)
* `Mejora` Detección y aviso al intentar reasignar contenido de archivos de ejemplos de código
* `Mejora` Cambio de dirección de descarga de plugins empaquetados de GitHub a JsDelivr
* `Dependencia` Añadida la versión 1.5.1 de Zeugma Solutions LocaleHelper
* `Dependencia` Actualización de las herramientas de Android SDK de la versión 31 a 32
* `Dependencia` Actualización de Kotlinx Coroutines de la versión 1.6.0-native-mt a 1.6.0
* `Dependencia` Actualización de OpenCV de la versión 3.4.3 a 4.5.4 y a 4.5.5 (gracias a [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependencia` Actualización de OkHttp3 de la versión 3.10.0 a 5.0.0-alpha.4 y a 5.0.0-alpha.6
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 7.2.0-beta01 a 7.3.0-alpha06
* `Dependencia` Actualización de Auto.js-ApkBuilder de la versión 1.0.1 a 1.0.3
* `Dependencia` Actualizado Glide Compiler versión 4.12.0 -> 4.13.1
* `Dependencia` Actualizado Gradle versión 7.4-rc-2 -> 7.4.1
* `Dependencia` Actualización de Gson de la versión 2.8.9 a 2.9.0
* `Dependencia` Degradación de la versión de Android Material de 1.6.0-alpha02 a 1.5.0

# v6.0.2

###### 2022/02/05

* `Nuevo` Método images.bilateralFilter para filtrado bilateral de imágenes
* `Corrección` Corrección del problema donde múltiples llamadas a toast solo aplicaban el último llamado
* `Corrección` toast.dismiss podía no funcionar
* `Corrección` Solución al problema donde los interruptores de modo cliente y servidor podrían no funcionar correctamente
* `Corrección` Solución al problema donde el estado de los interruptores de modo cliente y servidor no se actualizaba correctamente
* `Corrección` Corrección de la excepción de análisis del elemento text en el modo UI en Android 7.x (gracias a [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `Mejora` Se ignoran excepciones ScriptInterruptedException lanzadas por sleep
* `Dependencia` Añadida compatibilidad con AndroidX AppCompat (Legacy) versión 1.0.2
* `Dependencia` Actualización de AndroidX AppCompat de la versión 1.4.0 a 1.4.1
* `Dependencia` Actualización de AndroidX Preference de la versión 1.1.1 a 1.2.0
* `Dependencia` Actualización del motor Rhino de la versión 1.7.14-SNAPSHOT a 1.7.14
* `Dependencia` Actualización de OkHttp3 de la versión 3.10.0 a 5.0.0-alpha.3 y a 5.0.0-alpha.4
* `Dependencia` Actualización de Android Material de la versión 1.6.0-alpha01 a 1.6.0-alpha02
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 7.2.0-alpha06 a 7.2.0-beta01
* `Dependencia` Actualizado Gradle versión 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `Nuevo` Soporte del complemento VSCode para clientes (LAN) y servidores (LAN/ADB) (Ref a [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Módulo base64 para codificar y descodificar Base64 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Nuevo` Métodos globales isInteger/isNullish/isObject/isPrimitive/isReference para comprobar tipos
* `Nuevo` Implementación de compatibilidad (polyfill) de Object.getOwnPropertyDescriptors
* `Nuevo` Implementación de compatibilidad (polyfill) de Array.prototype.flat
* `Mejora` global.sleep admite intervalos aleatorios y parámetros negativos
* `Mejora` global.toast admite duración, sustitución forzada y dismiss
* `Mejora` Globalización de objetos de paquetes (okhttp3/androidx/de)
* `Dependencia` Actualización de Android Material de la versión 1.5.0-beta01 -> 1.6.0-alpha01
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 7.2.0-alpha04 -> 7.2.0-alpha06
* `Dependencia` Actualización de Kotlinx Coroutines de la versión 1.5.2-native-mt -> 1.6.0-native-mt
* `Dependencia` Actualización del plugin de Gradle de Kotlin de la versión 1.6.0 -> 1.6.10
* `Dependencia` Actualizado Gradle versión 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `Nuevo` Añadido botón de reinicio de la aplicación en la parte inferior del cajón de la página principal
* `Nuevo` Interruptores "Ignorar optimización de batería"/"Mostrar sobre otras aplicaciones" y otros en el panel lateral
* `Corrección` Corrección de problemas de renderizado del tema en algunos áreas después de la instalación inicial de la aplicación
* `Corrección` Corrección del problema que impide la construcción del proyecto cuando el archivo sign.property no existe
* `Corrección` Corrección de errores de almacenamiento de meses en tareas programadas únicas
* `Corrección` Corrección de problemas de color en los interruptores de la página de configuración de la aplicación que no cambian con el tema
* `Corrección` Corrección de problemas de reconocimiento del plugin de empaquetado y la URL de descarga del plugin
* `Corrección` Corrección del problema de sincronización del estado del interruptor de "ver permisos de uso" en el cajón de la página principal
* `Corrección` Corrección de fugas de memoria potenciales en TemplateMatching.fastTemplateMatching relacionadas con Mat
* `Mejora` Mejora de la compatibilidad de ViewUtil.getStatusBarHeight
* `Mejora` Eliminación del módulo de inicio de sesión de usuario del cajón de la página principal y eliminación del espacio ocupado en el diseño
* `Mejora` Eliminadas las pestañas Comunidad y Mercado y mejorada la alineación de la página principal
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
* `Dependencia` Añadida AndroidX Preference versión 1.1.1
* `Dependencia` Añadido AndroidX SwipeRefreshLayout versión 1.1.0
* `Dependencia` Actualización de Android SDK de la versión 28 -> 30 -> 31
* `Dependencia` Actualización de Android Analytics de la versión 7.0.0 -> 13.1.0
* `Dependencia` Actualización de Android Annotations de la versión 4.5.2 -> 4.8.0
* `Dependencia` Actualización de las herramientas de construcción de Gradle de la versión 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `Dependencia` Actualización de Android Job de la versión 1.2.6 -> 1.4.2
* `Dependencia` Actualización de Android Material de la versión 1.1.0-alpha01 -> 1.5.0-beta01
* `Dependencia` Actualización de AndroidX MultiDex de la versión 2.0.0 -> 2.0.1
* `Dependencia` Actualización de Apache Commons Lang3 de la versión 3.6 -> 3.12.0
* `Dependencia` Actualizado AndroidX AppCompat versión 1.0.2 -> 1.4.0
* `Dependencia` Actualización del plugin de Gradle de ButterKnife de la versión 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `Dependencia` Actualización de ColorPicker de la versión 2.1.5 -> 2.1.7
* `Dependencia` Actualización de Espresso Core de la versión 3.1.1-alpha01 -> 3.5.0-alpha03
* `Dependencia` Actualizado EventBus versión 3.0.0 -> 3.2.0
* `Dependencia` Actualizado Glide Compiler versión 4.8.0 -> 4.12.0
* `Dependencia` Actualizado Gradle Build Tool versión 29.0.2 -> 30.0.2
* `Dependencia` Actualizado Gradle versión 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `Dependencia` Actualización del plugin Groovy-Json de la versión 3.0.7 -> 3.0.8
* `Dependencia` Actualización de Gson de la versión 2.8.2 -> 2.8.9
* `Dependencia` Actualización de la versión de Java de 8 -> 11 -> 16
* `Dependencia` Actualización de Joda Time de la versión 2.9.9 -> 2.10.13
* `Dependencia` Actualizado JUnit versión 4.12 -> 4.13.2
* `Dependencia` Actualización del plugin de Gradle de Kotlin de la versión 1.3.10 -> 1.4.10 -> 1.6.0
* `Dependencia` Actualización de Kotlinx Coroutines de la versión 1.0.1 -> 1.5.2-native-mt
* `Dependencia` Actualización de LeakCanary de la versión 1.6.1 -> 2.7
* `Dependencia` Actualización de LicensesDialog de la versión 1.8.1 -> 2.2.0
* `Dependencia` Actualización de Material Dialogs de la versión 0.9.2.3 -> 0.9.6.0
* `Dependencia` Actualización de OkHttp3 de la versión 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `Dependencia` Actualizado ReactiveX RxJava2 RxAndroid versión 2.0.1 -> 2.1.1
* `Dependencia` Actualizado ReactiveX RxJava2 versión 2.1.2 -> 2.2.21
* `Dependencia` Actualización de Retrofit2 Converter Gson de la versión 2.3.0 -> 2.9.0
* `Dependencia` Actualización de Retrofit2 Retrofit de la versión 2.3.0 -> 2.9.0
* `Dependencia` Actualización de Zip4j de la versión 1.3.2 -> 2.9.1
* `Dependencia` Actualización de la versión del motor Rhino de 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `Dependencia` Actualización de OpenCV de 3.4.3 -> 4.5.4