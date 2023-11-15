@file:Suppress("SpellCheckingInspection")

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.zip.CRC32
import kotlin.text.RegexOption.IGNORE_CASE

val applicationId = "org.autojs.autojs6"
val sign = Sign("$rootDir/sign.properties")
val versions = Versions("$rootDir/version.properties")

val dimention = "channel"
val flavorNameApp = "app"
val flavorNameInrt = "inrt"
val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val buildActionAssemble = "assemble"
val templateName = "template"

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.android") /* kotlin("android") */
}

dependencies /* Unclassified */ {
    // LeakCanary
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

    // Android supports
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("com.google.android.material:material:1.10.0")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Common Markdown
    implementation("com.github.atlassian:commonmark-java:commonmark-parent-0.9.0")

    // Licenses Dialog
    implementation("de.psdev.licensesdialog:licensesdialog:2.2.0")

    // Commons Lang
    implementation("org.apache.commons:commons-lang3:3.13.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlin-coroutines-adapter:0.9.2")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")

    // Joda Time
    implementation("joda-time:joda-time:2.12.5")

    // Flurry
    implementation("com.flurry.android:analytics:14.4.0")

    // Bugly
    implementation(project(":libs:com.tencent.bugly.crashreport-4.0.4"))

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")

    // Webkit
    implementation("androidx.webkit:webkit:1.8.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Zip4j
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Log4j
    implementation("de.mindpipe.android:android-logging-log4j:1.0.3")
    implementation("log4j:log4j:1.2.17")

    // Preference
    implementation("androidx.preference:preference-ktx:1.2.1")

    // RootShell
    implementation("com.github.Stericson:RootShell:1.6")

    // JDeferred
    implementation("org.jdeferred:jdeferred-android-aar:1.2.6")

    // Rx
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1@aar")

    // Device Names
    implementation("com.jaredrummler:android-device-names:2.1.1")

    // Version Compare
    implementation("io.github.g00fy2:versioncompare:1.5.0")

    // Terminal Emulator
    implementation(project(":libs:jackpal.androidterm.libtermexec-1.0"))
    implementation(project(":libs:jackpal.androidterm.emulatorview-1.0.42"))
    implementation(project(":libs:jackpal.androidterm-1.0.70"))

    // Dex
    implementation(files("$rootDir/libs/com.android.dx-1.14.jar"))

    // OpenCV
    implementation(project(":libs:org.opencv-4.8.0"))

    // PaddleOCR
    implementation(project(":libs:paddleocr"))

    // Android Job
    implementation(project(":libs:android-job-simplified-1.4.3"))

    // Rhino
    implementation(files("$rootDir/libs/org.mozilla.rhino-1.7.15-snapshot.jar"))

    // Tiny Sign
    implementation(files("$rootDir/libs/tiny-sign-0.9.jar"))

    // Tasker Plugin
    implementation(project(":libs:android-spackle-9.0.0"))
    implementation(project(":libs:android-assertion-9.0.0"))
    implementation(project(":libs:android-plugin-client-sdk-for-locale-9.0.0"))

    // JavaMail for Android
    implementation(files("$rootDir/libs/javamail-android/activation.jar"))
    implementation(files("$rootDir/libs/javamail-android/additionnal.jar"))
    implementation(files("$rootDir/libs/javamail-android/mail.jar"))

    // Shizuku
    implementation(project(":libs:dev.rikka.shizuku-shared-13.1.5"))
    implementation(project(":libs:dev.rikka.shizuku-aidl-13.1.5"))
    implementation(project(":libs:dev.rikka.shizuku-api-13.1.5"))
    implementation(project(":libs:dev.rikka.shizuku-provider-13.1.5"))

    // ARSCLib
    implementation("io.github.reandroid:ARSCLib:1.2.4")
}

dependencies /* Test */ {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

dependencies /* Annotations */ {
    // Android Annotations
    implementation("org.androidannotations:androidannotations-api:4.8.0")
    implementation("androidx.annotation:annotation:1.7.0")
    ksp("org.androidannotations:androidannotations:4.8.0")

    // JCIP Annotations
    implementation("net.jcip:jcip-annotations:1.0")

    // EventBus
    implementation("org.greenrobot:eventbus:3.3.1")
}

dependencies /* AppCompat */ {
    // @Obsoleted by SuperMonster003 on Oct 5, 2023.
    //  ! Comment with related code will be removed since Jan 5, 2024.
    //
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! It looks like that bugs below in version 1.5.x has gone away since 1.6.x.
    // implementation("androidx.appcompat:appcompat") {
    //     version {
    //         strictly("1.4.2")
    //         because("Version 1.5.0 duplicates some classes")
    //     }
    // }
    //
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! To check the releases for Appcompat library,
    //  ! visit https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.6.1")

    // AppCompat for legacy views (such as JsTextViewLegacy)
    implementation(project(":libs:androidx.appcompat-1.0.2")) {
        setVersion("1.0.2")
    }
}

dependencies /* Material Dialogs */ {
    // Material Dialogs
    // TODO by SuperMonster003 on Feb 5, 2022.
    //  ! Upgrade to 3.3.0 (more difficult than expected)
    val configuration: (ExternalModuleDependency).() -> Unit = {
        version {
            prefer("0.9.6.0")
            because("Not ready to update to version 3.3.0 yet")
        }
    }
    configuration.let {
        implementation("com.afollestad.material-dialogs:core", it)
        implementation("com.afollestad.material-dialogs:commons", it)
    }
}

dependencies /* Layout */ {
    // Expandable Layout
    implementation("com.github.aakira:expandable-layout:1.6.0")

    // Expandable RecyclerView
    implementation("com.bignerdranch.android:expandablerecyclerview:3.0.0-RC1")

    // Flexible Divider
    implementation("com.yqritc:recyclerview-flexibledivider:1.4.0")
}

dependencies /* View */ {
    // RoundedImageView
    implementation("com.makeramen:roundedimageview:2.3.0")

    // CircleImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Animated SVG
    implementation("com.jaredrummler:animated-svg-view:1.0.6")
}

dependencies /* GitHub API */ {
    implementation(files("$rootDir/libs/github-api-1.306.jar"))

    implementation("commons-io:commons-io") {
        because("Compatibility for Android API Level < 26 (Android 8.0) [O]")
        version {
            strictly("2.8.0")
            because("Exception on newer versions: 'NoClassDefFoundError: org.apache.commons.io.IOUtils'")
        }
    }

    implementation("com.fasterxml.jackson.core:jackson-databind") {
        because("Compatibility for Android API Level < 26 (Android 8.0) [O]")
        version {
            strictly("2.13.3")
            because("Exception on 2.14.x: 'No virtual method getParameterCount()I in class Ljava/lang/reflect/Method'")
        }
    }

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3") {
        because("Compatibility of java.time.* for Android API Level < 26 (Android 8.0) [O]")
    }
}

dependencies /* MLKit */ {
    // OCR
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // Barcode
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
}

dependencies /* Auto.js Extensions */ {
    // Settings Compat
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    // implementation("com.github.hyb1996:settingscompat:1.1.5")

    // Enhanced Floaty
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    // implementation("com.github.hyb1996:EnhancedFloaty:0.31")

    // MultiLevelListView
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    // implementation("com.github.hyb1996:android-multi-level-listview:1.1")

    // Auto.js APK Builder
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    // implementation(project(":libs:Auto.js-ApkBuilder-1.0.3"))

    // Extracted from com.github.hyb1996:MutableTheme:1.0.0
    // @Legacy com.jrummyapps:colorpicker:2.1.7
    implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.github.ozodrukh:CircularReveal:2.0.1")
}

dependencies /* Archived */ {
    // Kotlin
    // @Comment by SuperMonster003 on May 19, 2022.
    //  ! It is no longer necessary to declare a dependency on the stdlib library in any Kotlin Gradle project.
    //  ! The dependency is added by default.
    //  ! See https://kotlinlang.org/docs/gradle.html#dependency-on-the-standard-library
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21")

    // Google Guava
    // @Comment by SuperMonster003 on Jun 1, 2022.
    //  ! Not necessary for current project but worth keeping the trace.
    // implementation("com.google.guava:guava:31.1-jre")
}

dependencies /* Reserved for auto append by IDE */ {

}

android {

    namespace = applicationId
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = applicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "false")
    }

    flavorDimensions.add(dimention)

    productFlavors {

        create(flavorNameApp) {
            dimension = dimention
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
                    "icon" to "@drawable/autojs6_material",
                )
            )
        }

        create(flavorNameInrt) {
            dimension = dimention
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

            gradle.taskGraph.whenReady(object : Action<TaskExecutionGraph> {
                override fun execute(taskGraph: TaskExecutionGraph) {
                    val taskName = "$buildActionAssemble${flavorNameInrt.uppercaseFirstChar()}${buildTypeRelease.uppercaseFirstChar()}"
                    project.getTasksByName(taskName, true)
                        .firstOrNull()
                        ?.doLast {
                            copy {
                                val src = "build/outputs/apk/$flavorNameInrt/$buildTypeRelease"
                                val dst = "src/main/assets"
                                val ext = Utils.FILE_EXTENSION_APK

                                if (!file(src).isDirectory) {
                                    return@copy
                                }

                                from(src); into(dst)

                                val verName = versionName?.replace(Regex("\\s"), "-")?.lowercase()
                                val srcFileName = "$flavorNameInrt-v$verName-universal.$ext" /* e.g. inrt-v6.4.0-beta-universal.apk */
                                val dstFileName = "$templateName.$ext"
                                val isOverridden = file(File(dst, dstFileName)).exists()
                                include(srcFileName)
                                rename(srcFileName, dstFileName)
                                println("Source: ${file(File(src, srcFileName))}")
                                println("Destination: ${file(File(dst, dstFileName))}${if (isOverridden) " [overridden]" else ""}")
                            }
                        }
                        ?: println("$taskName doesn't exist in project ${project.name}")
                }
            })
        }

        androidResources {
            if (gradle.startParameter.taskNames.any { it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble")) }) {
                ignoreAssetsPatterns.add(".idea")
            }
            if (gradle.startParameter.taskNames.any { it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble$flavorNameInrt", IGNORE_CASE)) }) {
                // @Hint by SuperMonster003 on Oct 16, 2023.
                //  ! Nothing needs to be added into assets for flavor "inrt",
                //  ! as assets will be copied from flavor "app"
                //  ! while building an apk on org.autojs.autojs.ui.project.BuildActivity.
                ignoreAssetsPatterns.add("!*") /* Ignore everything. */
            }
        }

    }

    sourceSets {
        // @Hint by LZX284 on Nov 15, 2023.
        //  ! The assets file is divided into three directories according to different flavors.
        //  ! But the files are not actually moved to avoid conflicts with the latest modifications.
        getByName("main"){
            assets.srcDirs("src/main/assets")
        }
        getByName(flavorNameApp){
            assets.srcDirs("src/main/assets_$flavorNameApp")
        }
        getByName(flavorNameInrt){
            assets.srcDirs("src/main/assets_$flavorNameInrt")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = versions.javaVersion
        targetCompatibility = versions.javaVersion
    }

    // @Legacy packagingOptions { ... }
    packaging {
        arrayOf(
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
        ).let { resources.pickFirsts.addAll(it) }

        jniLibs {
            useLegacyPackaging = true
        }
    }

    kotlinOptions {
        jvmTarget = versions.javaVersion.toString()
        // freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
    }

    lint {
        abortOnError = false
    }

    signingConfigs {
        if (sign.isValid) {
            create(buildTypeRelease) {
                storeFile = sign.properties["storeFile"]?.let { file(it as String) }
                keyPassword = sign.properties["keyPassword"] as String
                keyAlias = sign.properties["keyAlias"] as String
                storePassword = sign.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val proguardFiles = arrayOf(
            getDefaultProguardFile("proguard-android.txt"),
            "proguard-rules.pro",
        )
        val niceSigningConfig = takeIf { sign.isValid }?.let {
            signingConfigs.getByName(buildTypeRelease)
        }
        getByName(buildTypeRelease) {
            isMinifyEnabled = false
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
        getByName(buildTypeDebug) {
            isMinifyEnabled = getByName(buildTypeRelease).isMinifyEnabled
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        aidl = true
        viewBinding = true
        // @Hint by SuperMonster003 on Aug 14, 2023.
        //  ! Substitution of "android.defaults.buildfeatures.buildconfig=true"
        buildConfig = true
    }

    defaultConfig {
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName
        multiDexEnabled = true
        javaCompileOptions {
            annotationProcessorOptions {
                mapOf(
                    "resourcePackageName" to (this@defaultConfig.applicationId ?: this@Build_gradle.applicationId),
                    "androidManifestFile" to ("$projectDir/src/main/AndroidManifest.xml")
                ).let { arguments(it) }
            }
        }
        buildConfigField("String", "VERSION_DATE", "\"${Utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"")
        buildConfigField("String", "VSCODE_EXT_REQUIRED_VERSION", "\"${versions.vscodeExtRequiredVersion}\"")
    }

    applicationVariants.all {
        mergeAssetsProvider
            .configure {
                doLast {
                    mapOf(
                        "dir" to outputDir,
                        "includes" to listOf("declarations/**", "sample/declarations/**"),
                    ).let { delete(fileTree(it)) }
                }
            }
        outputs
            .map { it as BaseVariantOutputImpl }
            .forEach { it.outputFileName = Utils.getOutputFileName(this@all as ApplicationVariantImpl, it) }
    }

    splits {
        // Configures multiple APKs based on ABI.
        abi {
            // Enables building multiple APKs per ABI.
            isEnable = true
            // By default, all ABIs are included, so use reset() and include to specify that we only
            // want APKs for x86 and x86_64.
            // Resets the list of ABIs that Gradle should create APKs for to none.
            reset()
            // Specifies a list of ABIs that Gradle should create APKs for.
            include("x86", "armeabi-v7a", "arm64-v8a", "x86_64")
            // Specifies that we do not want to also generate a universal APK that includes all ABIs.
            isUniversalApk = true
        }
    }

}

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"

        // @Hint by SuperMonster003 on May 18, 2022.
        //  ! Comment or remove this option if you are tired of plenty of warnings. :)
        // options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = versions.javaVersion.toString()
        }
    }

    register<Copy>("appendDigestToReleasedFiles") {
        listOf(flavorNameApp, flavorNameInrt).forEach { flavorName ->
            val src = "$flavorName/$buildTypeRelease"
            val dst = "${src}s"
            val ext = Utils.FILE_EXTENSION_APK

            if (!file(src).isDirectory) {
                return@forEach
            }

            from(src); into(dst); include("*.$ext")

            rename { name ->
                Utils.digestCRC32(file("${src}/$name")).let { digest ->
                    name.replace("^(.+?)(\\.$ext)\$".toRegex(), "\$1-$digest\$2")
                }
            }

            doLast { println("Destination: ${file(dst)}") }
        }
    }
}

extra {
    versions.handleIfNeeded(project, flavorNameApp, listOf(buildTypeDebug, buildTypeRelease))
}

class Sign(filePath: String) {

    var isValid = false
        private set

    val properties = Properties().also { props ->
        File(filePath).takeIf { it.exists() }?.let {
            props.load(FileInputStream(it))
            isValid = props.isNotEmpty()
        }
    }

}

class Versions(filePath: String) {

    private val properties = Properties()
    private val file = File(filePath).apply {
        if (!canRead()) {
            throw FileNotFoundException("Can't read file '$filePath'")
        }
        properties.load(FileInputStream(this))
    }

    val sdkVersionMin = properties["MIN_SDK_VERSION"].let { it as String }.toInt()
    val sdkVersionTarget = properties["TARGET_SDK_VERSION"].let { it as String }.toInt()
    val sdkVersionTargetInrt = properties["TARGET_SDK_VERSION_INRT"].let { it as String }.toInt()
    val sdkVersionCompile = properties["COMPILE_SDK_VERSION"].let { it as String }.toInt()
    val appVersionName = properties["VERSION_NAME"] as String
    val appVersionCode = properties["VERSION_BUILD"].let { it as String }.toInt()
    val vscodeExtRequiredVersion = properties["VSCODE_EXT_REQUIRED_VERSION"] as String

    private val currentVersionInt = JavaVersion.current().majorVersion.toInt()

    private val javaVersionMinSupported: Int = properties["JAVA_VERSION_MIN_SUPPORTED"]
        .let { it as String }.toInt()
        .also {
            if (currentVersionInt < it) {
                throw GradleException(
                    "Current Gradle JDK version ${JavaVersion.current()} does not meet " +
                            "the minimum requirement which $it is needed."
                )
            }
        }
    private val javaVersionMinSuggested: Int = properties["JAVA_VERSION_MIN_SUGGESTED"]
        .let { it as String }.toInt()
        .also {
            if (currentVersionInt < it) {
                logger.error(
                    "It is recommended to upgrade current Gradle JDK version ${JavaVersion.current()} " +
                            "to $it or higher (but lower than $javaVersionMinRadical)."
                )
            }
        }
    private val javaVersionMinRadical: Int = properties["JAVA_VERSION_MIN_RADICAL"]
        .let { it as String }.toInt()
        .also {
            if (it in currentVersionInt downTo 1) {
                logger.error(
                    "It is recommended to downgrade current Gradle JDK version $currentVersionInt " +
                            "to ${it - 1}${if (it - 1 > javaVersionMinSuggested) " or lower (but not lower than $javaVersionMinSuggested)" else ""}, " +
                            "as Gradle may be not compatible with JDK $it${if (currentVersionInt > it) " (and above)" else ""} for now."
                )
            }
        }
    private val javaVersionRaw = properties["JAVA_VERSION"] as String
    private var javaVersionInfoSuffix = ""
    private val javaVersionCeilMap = mapOf(
        "idea" to mapOf(
            "2023.2" to javaVersionMinSuggested, /* Aug 17, 2023. */
            "2023.1" to javaVersionMinSuggested, /* Aug 17, 2023. */
            "2022.3" to javaVersionMinSuggested, /* Aug 17, 2023. */
        ),
    )

    val javaVersion: JavaVersion by lazy {
        var niceVersionInt = javaVersionRaw.toInt()
        var isFallback = false

        while (niceVersionInt > javaVersionMinSupported) {
            if (JvmTarget.values().any { it.name.contains(Regex("_$niceVersionInt$")) }) {
                break
            }
            niceVersionInt -= 1
            isFallback = true
        }

        if (isFallback) {
            javaVersionInfoSuffix += " [fallback]"
        }

        val platformVersion = gradle.extra["platformVersion"] as String
        val platformType = gradle.extra["platformType"] as String

        javaVersionCeilMap[platformType]?.get(platformVersion)?.let { ceil: Int ->
            if (niceVersionInt > ceil) {
                niceVersionInt = ceil
                javaVersionInfoSuffix += " [coerced]"
            }
        }

        if (niceVersionInt > currentVersionInt) {
            niceVersionInt = currentVersionInt
            javaVersionInfoSuffix += " [consistent]"
        }

        JavaVersion.toVersion(niceVersionInt)
    }

    private var isBuildNumberAutoIncremented = false
    private val minBuildTimeGap = Utils.hours2Millis(0.75)

    private val isBuildGapEnough
        get() = properties["BUILD_TIME"]?.let {
            Date().time - (it as String).toLong() > minBuildTimeGap
        } ?: false

    private fun appendToTask(project: Project, flavorName: String, buildType: String) {
        project.tasks.getByName(Utils.getAssembleTaskName(flavorName, buildType)).doLast {
            updateProperties()
            println()
            showInfo()
        }
    }

    private fun updateProperties() {
        if (isBuildGapEnough) {
            properties["VERSION_BUILD"] = "${appVersionCode + 1}"
            isBuildNumberAutoIncremented = true
        }
        properties["BUILD_TIME"] = "${Date().time}"
        properties.store(file.writer(), null)
    }

    fun showInfo() {
        val title = "Version information for AutoJs6 app library"

        val infoVerName = "Version name: $appVersionName"
        val infoVerCode = "Version code: ${if (isBuildNumberAutoIncremented) "${appVersionCode + 1} [auto-incremented]" else appVersionCode}"
        val infoVerSdk = "SDK versions: min [$sdkVersionMin] / target [$sdkVersionTarget] / compile [$sdkVersionCompile]"
        val infoVerJava = "Java version: $javaVersion$javaVersionInfoSuffix"

        val maxLength = arrayOf(title, infoVerName, infoVerCode, infoVerSdk, infoVerJava).maxOf { it.length }

        arrayOf(
            "=".repeat(maxLength),
            title,
            "-".repeat(maxLength),
            infoVerName,
            infoVerCode,
            infoVerSdk,
            infoVerJava,
            "=".repeat(maxLength),
            "",
        ).forEach { println(it) }
    }

    fun handleIfNeeded(project: Project, flavorName: String, targetBuildType: List<String>) {
        project.gradle.taskGraph.whenReady(object : Action<TaskExecutionGraph> {
            override fun execute(taskGraph: TaskExecutionGraph) {
                for (buildType in targetBuildType) {
                    if (taskGraph.hasTask(Utils.getAssembleFullTaskName(project.name, flavorName, buildType))) {
                        return appendToTask(project, flavorName, buildType)
                    }
                }
                return showInfo()
            }
        })
    }

}

object Utils {

    const val FILE_EXTENSION_APK = "apk"

    fun hours2Millis(hour: Double) = hour * 3.6e6

    fun getDateString(format: String, zone: String): String {
        // e.g. May 23, 2011
        return SimpleDateFormat(format).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date())
    }

    fun getOutputFileName(variant: ApplicationVariantImpl, output: BaseVariantOutputImpl): String {
        val autojs = variant.applicationId.replace("^.+\\.(.+)$".toRegex(), "$1") // e.g. autojs6
        val version = variant.versionName.replace("\\s".toRegex(), "-") // e.g. 6.1.0
        val architecture = output.getFilter("ABI") ?: "universal"
        val extension = FILE_EXTENSION_APK

        return "$autojs-v$version-$architecture.$extension".lowercase(Locale.getDefault())
    }

    fun getAssembleTaskName(flavorName: String, buildType: String) = "assemble${capitalize(flavorName)}${capitalize(buildType)}"

    fun getAssembleFullTaskName(projectName: String, flavorName: String, buildType: String) = ":$projectName:${getAssembleTaskName(flavorName, buildType)}"

    fun digestCRC32(file: File): String {
        val fis = FileInputStream(file)
        val buffer = ByteArray(4096)
        var read: Int

        return CRC32().let { o ->
            while (fis.read(buffer).also { read = it } > 0) {
                o.update(buffer, 0, read)
            }
            String.format("%08x", o.value)
        }
    }

    private fun capitalize(s: String) = "${s[0].uppercase(Locale.getDefault())}${s.substring(1)}"

}