@file:Suppress("SpellCheckingInspection")

import com.android.build.api.variant.FilterConfiguration
import org.gradle.kotlin.dsl.support.uppercaseFirstChar

plugins {
    id("org.autojs.build.utils")
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("idea")
}

idea {
    module {
        excludeDirs.addAll(
            listOf(
                file("$rootDir/app/src/main/java/com/stardust/"),
                file("$rootDir/app/src/main/assets/modules/obsolete/"),
                file("$rootDir/app/src/main/assets-app/js-beautify/"),
            )
        )
    }
}

val globalApplicationId = "org.autojs.autojs6"

val flavorDimension = "channel"
val flavorNameApp = "app"
val flavorNameInrt = "inrt"
val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val buildActionAssemble = "assemble"
extra.apply {
    set("nodeJsRuntimeConfig.globalApplicationId", globalApplicationId)
    set("nodeJsRuntimeConfig.flavorNameApp", flavorNameApp)
    set("nodeJsRuntimeConfig.flavorNameInrt", flavorNameInrt)
    set("nodeJsRuntimeConfig.buildActionAssemble", buildActionAssemble)
}

apply(from = "node-runtime-config.gradle.kts")

@Suppress("UNCHECKED_CAST")
fun <T> nodeJsRuntimeConfigExtra(name: String): T =
    extensions.extraProperties.get("nodeJsRuntimeConfig.$name") as T

val leakCanaryEnabled = nodeJsRuntimeConfigExtra<Boolean>("leakCanaryEnabled")
utils.registerTemplateApkCopy(project)

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
    //  ! 渚濊禆搴?log4j (绗竴鐗堟湰) 鏄槗鍙楁敾鍑荤殑 (鍚?5 椤规紡娲?:
    //  ! - CVE-2022-23307, 璇勫垎: 8.8
    //  ! - CVE-2022-23305, 璇勫垎: 9.8
    //  ! - CVE-2022-23302, 璇勫垎: 8.8
    //  ! - CVE-2021-4104, 璇勫垎: 7.5
    //  ! - CVE-2019-17571, 璇勫垎: 9.8
    //  ! 浣?log4j 绗簩鐗堟湰瑕佹眰瀹夊崜 API 绾у埆涓嶄綆浜?26,
    //  ! 涓庢渶浣?API 绾у埆涓?24 鐨勫綋鍓嶉」鐩棤娉曞吋瀹?
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

    // Pinyin4j
    implementation(libs.pinyin4j)

    // Jieba Analysis (zh-CN: 缁撳反鍒嗚瘝)
    // implementation("com.huaban:jieba-analysis:1.0.2")
    implementation(project(":modules:jieba-analysis"))

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
    //  ! zh-CN: 浠呴€傜敤浜庡畨鍗?API 绾у埆 26 (8.0) [O] 鍙婁互涓?
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
    //  ! 鏌ョ湅 Appcompat 搴撶殑鍙戣鐗堟湰,
    //  ! 鍙闂?https://developer.android.com/jetpack/androidx/releases/appcompat.
    implementation(libs.appcompat)

    // AppCompat for legacy views (such as JsTextViewLegacy)
    implementation(project(":libs:androidx-appcompat-1_0_2")) {
        setVersion("1.0.2")
    }
}

dependencies /* Material Dialogs */ {
    // TODO by SuperMonster003 on Feb 5, 2022.
    //  ! Upgrade to 3.3.0 (more difficult than expected).
    //  ! zh-CN: 鍗囩骇鑷?3.3.0 (瀹為檯闅惧害瓒呭嚭棰勬湡杈冨).
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

dependencies /* MLKit */ {
    // OCR
    implementation(libs.text.recognition.chinese)

    // Barcode
    implementation(libs.barcode.scanning)
}

dependencies /* OpenCC */ {
    // OpenCC
    // implementation("com.github.qichuan:android-opencc:1.2.0")
    implementation(libs.opencc)
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
    //  ! 宸叉棤闇€鍦?Kotlin Gradle 椤圭洰涓樉寮忓０鏄庢爣鍑嗗簱 (stdlib).
    //  ! 鐩稿叧渚濊禆宸查粯璁よ娣诲姞.
    //  ! 鍙傞槄 https://kotlinlang.org/docs/gradle.html#dependency-on-the-standard-library.
    // Kotlin
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21")

    // @Comment by SuperMonster003 on Jun 1, 2022.
    //  ! Not necessary for current project but worth keeping the trace.
    //  ! zh-CN: 浜庡綋鍓嶉」鐩凡涓嶅啀闇€瑕? 浣嗕緷鐒跺€煎緱鐣欏瓨鍏惰釜杩?(浠ュ涓嶆椂涔嬮渶).
    // Google Guava
    // implementation("com.google.guava:guava:31.1-jre")

    // @Comment by SuperMonster003 on Apr 9, 2024.
    //  ! It was ever imported and used for MediaType constants.
    //  ! zh-CN: 鏇剧敤浜?MediaType 甯搁噺鐨勫鍏ュ強浣跨敤.
    // Javax WS RS API (Java API for RESTful Web Services)
    // implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")

    // @Hint by SuperMonster003 on Jan 11, 2026.
    //  ! The AutoJs6 core project has deprecated the Local Broadcast Manager library,
    //  ! replaced by LiveData and SharedFlow.
    //  ! It is only retained for dependencies of certain libraries (such as MQTT).
    //  ! zh-CN:
    //  ! AutoJs6 鏍稿績椤圭洰宸插純鐢?Local Broadcast Manager 搴?
    //  ! 鐢?LiveData 鍙?SharedFlow 鏇夸唬.
    //  ! 浠呯敤浜庢煇浜涘簱 (濡?MQTT) 鐨勪緷璧栬€屼繚鐣?
    // Local Broadcast Manager
    implementation(libs.localbroadcastmanager)
}

dependencies /* Reserved for auto append by IDE */ {

}

android {

    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = applicationId

        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget

        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true

        buildConfigField("String", "VERSION_DATE", "\"${utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"")
        buildConfigField("String", "VSCODE_EXT_REQUIRED_VERSION", "\"${versions.vscodeExtRequiredVersion}\"")
        buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "false")

        javaCompileOptions {
            annotationProcessorOptions {
                mapOf(
                    "resourcePackageName" to (this@defaultConfig.applicationId ?: globalApplicationId),
                    "androidManifestFile" to ("$projectDir/src/main/AndroidManifest.xml")
                ).let { arguments(it) }
            }
        }
    }

    flavorDimensions.add(flavorDimension)

    productFlavors {

        create(flavorNameApp) {
            dimension = flavorDimension
            versionCode = versions.appVersionCode
            versionName = versions.appVersionName
            buildConfigField("String", "CHANNEL", "\"$flavorNameApp\"")
            buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "false")
            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameApp,
                    "appName" to "@string/app_name",
                    "intentCategory" to "android.intent.category.LAUNCHER",
                    "intentCategoryInrt" to "android.intent.category.DEFAULT",
                    "authorities" to "org.autojs.autojs6.fileprovider",
                    "icon" to "@mipmap/ic_app_launcher_adaptive",
                )
            )
        }

        create(flavorNameInrt) {
            dimension = flavorDimension
            applicationIdSuffix = ".$flavorNameInrt"
            minSdk = versions.sdkVersionMin
            targetSdk = versions.sdkVersionTargetInrt
            compileSdk = versions.sdkVersionCompile
            versionCode = versions.appVersionCode
            versionName = versions.appVersionName

            buildConfigField("String", "CHANNEL", "\"$flavorNameInrt\"")
            buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "true")

            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameInrt,
                    "appName" to "AutoJs6.$flavorNameInrt",
                    "intentCategory" to "android.intent.category.DEFAULT",
                    "intentCategoryInrt" to "android.intent.category.LAUNCHER",
                    "authorities" to "org.autojs.autojs6.$flavorNameInrt.fileprovider",
                    "icon" to "@mipmap/ic_launcher",
                )
            )

        }

    }

    sourceSets {

        // @Hint by LZX284 (https://github.com/LZX284) on Nov 15, 2023.
        //  ! The assets file is divided into three directories according to different flavors.
        //  ! But the files are not actually moved to avoid conflicts with the latest modifications.
        //  ! zh-CN (translated by SuperMonster003 on Jul 26, 2024):
        //  ! 璧勪骇鏂囦欢鏍规嵁涓嶅悓鐨?Gradle flavor 琚殧绂讳负涓変釜涓嶅悓鐨勭洰褰?
        //  ! 涓嶈繃鐪熷疄鏂囦欢骞舵病鏈夎繘琛岀Щ鍔? 浠ラ伩鍏嶄笌鏈€鏂扮殑椤圭洰淇敼鍙戠敓鍐茬獊.
        //  !
        // @Hint by SuperMonster003 on Nov 16, 2023.
        //  ! The assets division idea was accepted, and it wouldn't hurt to try. :)
        //  ! zh-CN: 璧勪骇闅旂鐨勬兂娉曞彲浠ヨ鎺ュ彈, 姣曠珶璇曚竴涓嬩篃鏃犲Θ. [绗戣劯绗﹀彿]

        // @Archived by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 7, 2026.
        //  # getByName("main") {
        //  #     assets.srcDirs("src/main/assets")
        //  # }
        //  # getByName("release") {
        //  #     java.srcDirs("src/release/java")
        //  # }
        //  # getByName("debug") {
        //  #     java.srcDirs("src/debug/java")
        //  # }
        //  # getByName(flavorNameApp) {
        //  #     assets.srcDirs("src/main/assets-$flavorNameApp")
        //  # }
        //  # getByName(flavorNameInrt) {
        //  #     assets.srcDirs("src/main/assets-$flavorNameInrt")
        //  # }
        //  !
        getByName("main") {
            assets.directories.add("src/main/assets")
        }
        getByName("release") {
            java.directories.add("src/release/java")
        }
        getByName("debug") {
            java.directories.add("src/debug/java")
        }
        getByName(flavorNameApp) {
            assets.directories.add("src/main/assets-$flavorNameApp")
        }
        getByName(flavorNameInrt) {
            assets.directories.add("src/main/assets-$flavorNameInrt")
        }

    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        listOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.*",
            "META-INF/LICENSE-notice.*",
            "META-INF/license.*",
            "META-INF/NOTICE",
            "META-INF/NOTICE.*",
            "META-INF/notice.*",
            "META-INF/ASL2.0",
            "META-INF/*.kotlin_module",
            "lib/x86/libc++_shared.so",
            "lib/x86_64/libc++_shared.so",
            "lib/armeabi-v7a/libc++_shared.so",
            "lib/arm64-v8a/libc++_shared.so",
            "lib/armeabi/libc++_shared.so",
        ).let { resources.pickFirsts.addAll(it) }

        listOf(
            "com/**/*",
            "frameworks/**/*",
            "junit/**/*",
            "LICENSE-junit.txt",
            "spec.txt",
            "EmojiReference.txt",
        ).let { resources.excludes.addAll(it) }

        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
    }

    signingConfigs {
        if (signs.isValid) {
            create(buildTypeRelease) {
                storeFile = signs.properties["storeFile"]?.let { file(it as String) }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val proguardFiles = arrayOf<Any>(
            // @Archived by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 7, 2026.
            //  # getDefaultProguardFile("proguard-android.txt"),
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        val niceSigningConfig = takeIf { signs.isValid }?.let {
            signingConfigs.getByName(buildTypeRelease)
        }
        debug {
            isMinifyEnabled = getByName(buildTypeRelease).isMinifyEnabled
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        aidl = true
        viewBinding = true

        // @Hint by SuperMonster003 on Aug 14, 2023.
        //  ! Substitution of "android.defaults.buildfeatures.buildconfig=true"
        //  ! zh-CN: "android.defaults.buildfeatures.buildconfig=true" 鐨勬浛浠ｆ柟妗?
        buildConfig = true
        resValues = true

        // @Archived by SuperMonster003 on Sep 23, 2024.
        //  ! Jetpack Compose
        //  # compose = true
        //  # composeOptions {
        //  #     kotlinCompilerExtensionVersion = "1.5.12"
        //  # }
    }

}

apply(from = "node-android-config.gradle.kts")

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val mergeAssetsTaskName = "merge${variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Assets"
        val isInrtVariant = variantName.startsWith(flavorNameInrt, ignoreCase = true)

        tasks.matching { it.name == mergeAssetsTaskName }.configureEach {
            doLast {
                val reflectedOutputDir = when (val dir = javaClass.methods.firstOrNull {
                    it.name == "getOutputDir" && it.parameterTypes.isEmpty()
                }?.invoke(this)) {
                    is File -> dir
                    is Directory -> dir.asFile
                    else -> null
                }

                val includes = when (isInrtVariant) {
                    true -> listOf(
                        "doc/**/*",
                        "docs/**/*",
                        "editor/**/*",
                        "fonts/**/*",
                        "indices/**/*",
                        "js-beautify/**/*",
                        "mlkit-google-ocr-models/**/*",
                        "mlkit_barcode_models/**/*",
                        "models/**/*",
                        "modules/**/*.d.ts",
                        "modules/**/*.md",
                        "modules/obsolete/**/*",
                        "openccdata/**/*",
                        "project/**/*",
                        "sample/**/*",
                        "stored-locales/**/*",
                        "android-devices.db",
                        "autojs.keystore",
                        "**/prob_emit.txt", // Jieba Analysis
                        "**/dict-chinese-*.db.gzip", // Jieba Analysis
                    )
                    else -> listOf(
                        "declarations/**/*",
                        "sample/declarations/**/*",
                        "modules/obsolete/**/*",
                    )
                }

                listOfNotNull(
                    reflectedOutputDir,
                    layout.buildDirectory.dir("intermediates/assets/$variantName/$mergeAssetsTaskName").get().asFile,
                )
                    .map { it.absoluteFile.normalize() }
                    .distinctBy { it.path }
                    .filter { it.isDirectory }
                    .forEach { outputDir ->
                        delete(
                            fileTree(
                                mapOf(
                                    "dir" to outputDir,
                                    "includes" to includes,
                                )
                            )
                        )
                    }
            }
        }

        variant.outputs.forEach { output ->
            val architecture = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            val outputFileNameProperty = output.javaClass.methods.firstOrNull {
                it.name == "getOutputFileName" && it.parameterTypes.isEmpty()
            }?.invoke(output) as? Property<*>

            @Suppress("UNCHECKED_CAST")
            (outputFileNameProperty as? Property<String>)?.set(
                variant.applicationId.zip(output.versionName) { appId, versionName ->
                    val autojs = appId.substringAfterLast('.')
                    val version = versionName.replace("\\s".toRegex(), "-")
                    val extension = utils.FILE_EXTENSION_APK
                    "$autojs-v$version-$architecture.$extension".lowercase()
                }
            )
        }
    }
}

apply(from = "node-build-tasks.gradle.kts")

apply(from = "node-gradle-scripts.gradle.kts")

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"

        // @Hint by SuperMonster003 on May 18, 2022.
        //  ! Comment or remove this option if you are tired of plenty of warnings. :)
        //  ! zh-CN: 娉ㄩ噴鎴栫Щ闄ゆ閫夐」鍙伩鍏嶈繃澶氳鍛婃秷鎭€犳垚鐨勫洶鎵? [绗戣劯绗﹀彿]
        // options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    register<Copy>("appendDigestToReleasedFiles") {
        listOf(flavorNameApp, flavorNameInrt).forEach { flavorName ->
            val src = "$flavorName/$buildTypeRelease"
            val dst = "${src}s"
            val ext = utils.FILE_EXTENSION_APK

            if (!file(src).isDirectory) {
                return@forEach
            }

            from(src); into(dst); include("*.$ext")

            rename { name ->
                utils.digestCRC32(file("${src}/$name")).let { digest ->
                    name.replace(Regex("^(.+?)(\\.$ext)$"), "$1-$digest$2")
                }
            }

            doLast { println("Destination: ${file(dst)}") }
        }
    }
}

extra {
    versions.handleIfNeeded(project, flavorNameApp, listOf(buildTypeDebug, buildTypeRelease))
}
