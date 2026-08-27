plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

android {
    namespace = "org.autojs.plugin.python.runtime.consumer"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "org.autojs.plugin.python.runtime.consumer"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
}

val pythonR1DistributionDirectory = rootProject.layout.buildDirectory.dir(
    "distributions/python-runtime-r1",
)
val isIdeaActive = System.getProperty("idea.active").toBoolean()

dependencies {
    if (isIdeaActive) {
        // IntelliJ resolves every imported Android module before it executes Gradle tasks. Use
        // source projects only for that IDE model/build path so a clean checkout does not expose
        // nonexistent staged AARs to AGP. Command-line gates retain the packaged-AAR boundary.
        implementation(project(":plugin-api:protocol-wire-api"))
        implementation(project(":plugin-api:python-runtime-api"))
    } else {
        implementation(
            files(
                pythonR1DistributionDirectory.map { directory ->
                    directory.file("protocol-wire-api-debug.aar").asFile
                },
                pythonR1DistributionDirectory.map { directory ->
                    directory.file("python-runtime-api-debug.aar").asFile
                },
            ),
        )
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
}

// The distribution task is owned by the root project. Task-path dependencies
// deliberately fail at execution time if it has not been registered: this
// consumer must never fall back to compiling against the source projects.
val preparePythonR1ApiDistributionTask = ":preparePythonR1ApiDistribution"
val distributionConsumerTasks = setOf(
    "preDebugBuild",
    "compileDebugKotlin",
    "compileDebugJavaWithJavac",
    "assembleDebug",
    "preDebugUnitTestBuild",
    "compileDebugUnitTestKotlin",
    "compileDebugUnitTestJavaWithJavac",
    "testDebugUnitTest",
    "lintDebug",
    "preDebugAndroidTestBuild",
    "compileDebugAndroidTestKotlin",
    "compileDebugAndroidTestJavaWithJavac",
    "assembleDebugAndroidTest",
)

if (!isIdeaActive) {
    tasks.configureEach {
        if (name in distributionConsumerTasks) {
            dependsOn(preparePythonR1ApiDistributionTask)
        }
    }
}
