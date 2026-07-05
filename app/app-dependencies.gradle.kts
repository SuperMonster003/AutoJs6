import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.artifacts.Dependency
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.the

private val libs = the<LibrariesForLibs>()

@Suppress("UNCHECKED_CAST")
private val leakCanaryEnabled = extensions.extraProperties.get("nodeJsRuntimeConfig.leakCanaryEnabled") as Boolean

private fun DependencyHandlerScope.implementation(dependencyNotation: Any) {
    add("implementation", dependencyNotation)
}

private fun <T : Dependency> DependencyHandlerScope.implementation(
    dependencyNotation: T,
    dependencyConfiguration: T.() -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    (add("implementation", dependencyNotation) as T).dependencyConfiguration()
}

private fun DependencyHandlerScope.debugImplementation(dependencyNotation: Any) {
    add("debugImplementation", dependencyNotation)
}

private fun DependencyHandlerScope.testImplementation(dependencyNotation: Any) {
    add("testImplementation", dependencyNotation)
}

private fun DependencyHandlerScope.androidTestImplementation(dependencyNotation: Any) {
    add("androidTestImplementation", dependencyNotation)
}

private fun DependencyHandlerScope.ksp(dependencyNotation: Any) {
    add("ksp", dependencyNotation)
}

private fun DependencyHandlerScope.coreLibraryDesugaring(dependencyNotation: Any) {
    add("coreLibraryDesugaring", dependencyNotation)
}

dependencies /* Unclassified */ {
    // Compose
    // implementation("androidx.compose.ui:ui-android:1.6.7")

    // Kotlin reflect
    implementation(kotlin("reflect"))

    // AndroidX Core
    implementation(libs.core.ktx)

    // AndroidX Activity
    implementation(libs.activity.ktx)

    // LeakCanary
    if (leakCanaryEnabled) {
        debugImplementation(libs.leakcanary)
    }

    // Android supports
    implementation(libs.cardview)
    implementation(libs.multidex)

    // Material Components
    implementation(libs.material)

    // SwipeRefreshLayout
    implementation(libs.swiperefreshlayout)

    // ConstraintLayout
    implementation(libs.constraintlayout)

    // FlexboxLayout
    implementation(libs.flexbox)

    // Common Markdown
    implementation(libs.commonmark)

    // Flexmark Java HTML to Markdown Extensible Converter
    implementation(libs.flexmark.html2md)

    // Licenses Dialog
    implementation(libs.licensesdialog)

    // Apache Commons
    implementation(libs.commons.lang3)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.adapter.rxjava2)
    implementation(libs.retrofit2.kotlin.coroutines.adapter)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.ksp)

    // Joda Time
    implementation(libs.joda.time)

    // OkHttp
    implementation(libs.okhttp)

    // Webkit
    implementation(libs.webkit)

    // Gson
    implementation(libs.gson)

    // Zip4j
    implementation(libs.zip4j)

    // FIXME by SuperMonster003 on Aug 14, 2024.
    //  ! Vulnerable dependency (5 vulnerabilities) for log4j (version 1):
    //  ! - CVE-2022-23307, Score: 8.8
    //  ! - CVE-2022-23305, Score: 9.8
    //  ! - CVE-2022-23302, Score: 8.8
    //  ! - CVE-2021-4104, Score: 7.5
    //  ! - CVE-2019-17571, Score: 9.8
    //  ! However, log4j version 2 which requires Android API Level not lower than 26
    //  ! is not compatible with current project with min API Level 24.
    //  !
    //  ! zh-CN:
    //  !
    //  ! 依赖库 log4j (第一版本) 是易受攻击的 (含 5 项漏洞):
    //  ! - CVE-2022-23307, 评分: 8.8
    //  ! - CVE-2022-23305, 评分: 9.8
    //  ! - CVE-2022-23302, 评分: 8.8
    //  ! - CVE-2021-4104, 评分: 7.5
    //  ! - CVE-2019-17571, 评分: 9.8
    //  ! 但 log4j 第二版本要求安卓 API 级别不低于 26,
    //  ! 与最低 API 级别为 24 的当前项目无法兼容.
    // Log4j
    implementation(libs.log4j)

    // Android Logging Log4j
    implementation(libs.android.logging.log4j)

    // Preference
    implementation(libs.preference.ktx)

    // RootShell
    // implementation("com.github.Stericson:RootShell:1.6")
    implementation(project(":libs:root-shell-1_6"))

    // JDeferred
    implementation(libs.jdeferred)

    // Rx
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // Device Names
    implementation(libs.android.device.names)

    // Version Compare
    implementation(libs.versioncompare)

    // Terminal Emulator
    implementation(project(":libs:jackpal-androidterm-libtermexec-1_0"))
    implementation(project(":libs:jackpal-androidterm-emulatorview-1_0_42"))
    implementation(project(":libs:jackpal-androidterm-1_0_70"))

    // Dex
    implementation(files("$rootDir/libs/com-android-dx-1_14.jar"))
    implementation(files("$rootDir/libs/com-legacy-android-dx-1_7_0.jar"))

    // OpenCV
    implementation(project(":libs:org-opencv-4_8_0"))

    // Image Quantization
    implementation(project(":libs:imagequant"))

    // Android Job
    implementation(project(":libs:android-job-simplified-1_4_3"))

    // AndroidX Work Runtime
    implementation(libs.work.runtime)

    // APK Parser
    // implementation("com.jaredrummler:apk-parser:1.0.2")
    // implementation("com.github.AndroidDeveloperLB:apk-parser:6")
    implementation(project(":modules:apk-parser"))

    // Prism4j
    implementation(files("$rootDir/libs/prism4j-2_0_0.jar"))
    implementation(files("$rootDir/libs/prism4j-bundler-2_0_0.jar"))
    implementation(project(":libs:markwon-core-4_6_2"))
    implementation(project(":libs:markwon-syntax-highlight-4_6_2"))

    // Rhino
    implementation(files("$rootDir/libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar"))

    // Tasker Plugin
    implementation(project(":libs:android-spackle-9_0_0"))
    implementation(project(":libs:android-assertion-9_0_0"))
    implementation(project(":libs:android-plugin-client-sdk-for-locale-9_0_0"))

    // JavaMail for Android
    implementation(files("$rootDir/libs/javamail-android/activation.jar"))
    implementation(files("$rootDir/libs/javamail-android/additionnal.jar"))
    implementation(files("$rootDir/libs/javamail-android/mail.jar"))

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // ARSCLib
    implementation(libs.arsclib)

    // Toaster
    implementation(libs.toaster)
    implementation(libs.easywindow)

    // Tiny Sign
    implementation(files("$rootDir/libs/tiny-sign-0_9.jar"))

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.rxjava2)
    ksp(libs.room.compiler)

    // ApkSig
    // implementation("com.android.tools.build:apksig:8.7.3")

    // ApkSigner
    implementation(project(":modules:apk-signer"))

    // Prov
    implementation(libs.prov)

    // MQTT
    implementation(libs.paho.client.mqttv3)
    implementation(libs.paho.android.service)

    // Jsoup
    implementation(libs.jsoup)

    // Material Date Time Picker
    implementation(project(":modules:material-date-time-picker"))

    // ICU4J
    implementation(libs.icu4j)

    // R8
    implementation(libs.r8)

    // Plugin API: Common API
    implementation(project(":plugin-api:common-plugin-api"))

    // Plugin API: Node.js API
    implementation(project(":plugin-api:nodejs-api"))

    // Plugin API: OpenCC API
    implementation(project(":plugin-api:opencc-api"))

    // Plugin API: Pinyin API
    implementation(project(":plugin-api:pinyin-api"))

    // Plugin API: Pinyin4j API
    implementation(project(":plugin-api:pinyin4j-api"))

    // Plugin API: MediaInfo API
    implementation(project(":plugin-api:mediainfo-api"))

    // Plugin API: ML Kit Barcode API
    implementation(project(":plugin-api:mlkit-barcode-api"))

    // Plugin API: Paddle OCR API
    implementation(project(":plugin-api:paddle-ocr-api"))

    // Plugin API: Paddle OCR Engine
    implementation(project(":plugin-api:paddle-ocr-engine"))

    // Plugin API: Rapid OCR Engine
    implementation(project(":plugin-api:rapid-ocr-engine"))
}

dependencies /* MIME */ {
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! Only for Android API 26 (8.0) [O] and above.
    //  ! zh-CN: 仅适用于安卓 API 级别 26 (8.0) [O] 及以上
    // Apache Tika Core
    // implementation("org.apache.tika:tika-core:2.9.2")

    // MIME Util
    // implementation("eu.medsea.mimeutil:mime-util:2.1.3")
    implementation(files("$rootDir/libs/mime-util-2_1_3.jar"))
}

dependencies /* Test */ {
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.junit.jupiter)
}

dependencies /* Annotations */ {
    // Android Annotations
    implementation(libs.android.annotations.api)
    implementation(libs.annotation)
    ksp(libs.android.annotations)

    // JCIP Annotations
    implementation(libs.jcip.annotations)

    // EventBus
    implementation(libs.eventbus)
}

dependencies /* AppCompat */ {
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! To check the releases for Appcompat library,
    //  ! visit https://developer.android.com/jetpack/androidx/releases/appcompat.
    //  ! zh-CN:
    //  ! 查看 Appcompat 库的发行版本,
    //  ! 可访问 https://developer.android.com/jetpack/androidx/releases/appcompat.
    implementation(libs.appcompat)

    // AppCompat for legacy views (such as JsTextViewLegacy)
    implementation(project(":libs:androidx-appcompat-1_0_2")) {
        setVersion("1.0.2")
    }
}

dependencies /* Material Dialogs */ {
    // TODO by SuperMonster003 on Feb 5, 2022.
    //  ! Upgrade to 3.3.0 (more difficult than expected).
    //  ! zh-CN: 升级至 3.3.0 (实际难度超出预期较多).
    //  # val configuration: (ExternalModuleDependency).() -> Unit = {
    //  #     version {
    //  #         prefer("0.9.6.0")
    //  #         because("Not ready to update to version 3.3.0 yet")
    //  #     }
    //  # }
    //  # configuration.let { cfg ->
    //  #     implementation("com.afollestad.material-dialogs:core", cfg)
    //  #     implementation("com.afollestad.material-dialogs:commons", cfg)
    //  # }
    // Material Dialogs
    implementation(project(":modules:material-dialogs"))
    implementation(libs.materialprogressbar)
}

dependencies /* Layout */ {
    // Expandable Layout
    // implementation("com.github.aakira:expandable-layout:1.6.0")
    // implementation(project(":libs:expandable-layout-1_6_0"))
    implementation(project(":modules:expandable-layout"))

    implementation(project(":modules:expandable-recyclerview"))

    implementation(project(":modules:recyclerview-flexibledivider"))
}

dependencies /* View */ {
    // RoundedImageView
    implementation(libs.roundedimageview)

    // CircleImageView
    implementation(libs.circleimageview)

    // Animated SVG
    implementation(libs.animated.svg.view)
}

dependencies /* GitHub API */ {
    // GitHub API
    implementation(files("$rootDir/libs/github-api-1_306.jar"))

    // @Hint by SuperMonster003 on Sep 25, 2025.
    //  ! Strict version: "2.8.0".
    //  ! Compatibility for Android API Level < 26 (Android 8.0) [O].
    //  ! Exception on newer versions: 'NoClassDefFoundError: org.apache.commons.io.IObuildUtils'.
    // Commons IO
    implementation(libs.commons.io)

    // Jackson Databind
    implementation(libs.jackson.databind)

    // Desugar
    coreLibraryDesugaring(libs.desugar)
}

dependencies /* Auto.js Extensions */ {
    // Settings Compat
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation("com.github.hyb1996:settingscompat:1.1.5")

    // Enhanced Floaty
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation("com.github.hyb1996:EnhancedFloaty:0.31")

    // MultiLevelListView
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation("com.github.hyb1996:android-multi-level-listview:1.1")

    // Auto.js APK Builder
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation(project(":libs:Autojs-ApkBuilder-1_0_3"))

    // Extracted from com.github.hyb1996:MutableTheme:1.0.0
    implementation(libs.recyclerview)
    implementation(libs.circularreveal)
    // @Legacy com.jrummyapps:colorpicker:2.1.7
    // @Integrated by SuperMonster003 on Mar 25, 2025.
    //  # implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation(project(":modules:color-picker"))
}

dependencies /* Build Logic */ {
    // Version Codes Generator
    ksp(libs.ksp.version.codes.processor)
}

dependencies /* Archived */ {
    // @Comment by SuperMonster003 on May 19, 2022.
    //  ! It is no longer necessary to declare a dependency on the stdlib library in any Kotlin Gradle project.
    //  ! The dependency is added by default.
    //  ! See https://kotlinlang.org/docs/gradle.html#dependency-on-the-standard-library.
    //  ! zh-CN:
    //  ! 已无需在 Kotlin Gradle 项目中显式声明标准库 (stdlib).
    //  ! 相关依赖已默认被添加.
    //  ! 参阅 https://kotlinlang.org/docs/gradle.html#dependency-on-the-standard-library.
    // Kotlin
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21")

    // @Comment by SuperMonster003 on Jun 1, 2022.
    //  ! Not necessary for current project but worth keeping the trace.
    //  ! zh-CN: 于当前项目已不再需要, 但依然值得留存其踪迹 (以备不时之需).
    // Google Guava
    // implementation("com.google.guava:guava:31.1-jre")

    // @Comment by SuperMonster003 on Apr 9, 2024.
    //  ! It was ever imported and used for MediaType constants.
    //  ! zh-CN: 曾用于 MediaType 常量的导入及使用.
    // Javax WS RS API (Java API for RESTful Web Services)
    // implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")

    // @Hint by SuperMonster003 on Jan 11, 2026.
    //  ! The AutoJs6 core project has deprecated the Local Broadcast Manager library,
    //  ! replaced by LiveData and SharedFlow.
    //  ! It is only retained for dependencies of certain libraries (such as MQTT).
    //  ! zh-CN:
    //  ! AutoJs6 核心项目已弃用 Local Broadcast Manager 库,
    //  ! 由 LiveData 及 SharedFlow 替代.
    //  ! 仅用于某些库 (如 MQTT) 的依赖而保留.
    // Local Broadcast Manager
    implementation(libs.localbroadcastmanager)
}

dependencies /* Reserved for auto append by IDE */ {

}
