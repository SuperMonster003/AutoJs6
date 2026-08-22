import org.gradle.api.tasks.bundling.Zip
import java.util.zip.ZipFile

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

val packagedJvmSourceAidl = linkedSetOf(
    "org/autojs/plugin/jvmsource/api/IJvmHostBridge.aidl",
    "org/autojs/plugin/jvmsource/api/IJvmHostBridgeCallback.aidl",
    "org/autojs/plugin/jvmsource/api/IJvmSourceCallback.aidl",
    "org/autojs/plugin/jvmsource/api/IJvmSourceProvider.aidl",
    "org/autojs/plugin/jvmsource/api/IJvmSourceSession.aidl",
)

android {
    namespace = "org.autojs.plugin.jvmsource.api"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }

    requireNotNull(aidlPackagedList) {
        "AGP did not expose the packaged AIDL collection"
    }.addAll(packagedJvmSourceAidl)
}

dependencies {
    api(project(":plugin-api:protocol-wire-api"))
    testImplementation(libs.junit)
}

val verifyJvmSourceApiPackagedAidl = tasks.register("verifyJvmSourceApiPackagedAidl") {
    group = "verification"
    description = "Verifies the exact public AIDL source set packaged in the JVM source API AAR."
    dependsOn("bundleDebugAar")
    outputs.upToDateWhen { false }

    doLast {
        val aar = tasks.named<Zip>("bundleDebugAar").get().archiveFile.get().asFile
        check(aar.isFile) { "Missing JVM source API debug AAR: $aar" }
        val actual = ZipFile(aar).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it.startsWith("aidl/") }
                .toList()
        }
        val duplicates = actual.groupingBy { it }.eachCount().filterValues { it != 1 }
        check(duplicates.isEmpty()) { "JVM source API AAR contains duplicate packaged AIDL: $duplicates" }
        val expected = packagedJvmSourceAidl.mapTo(linkedSetOf()) { "aidl/$it" }
        check(actual.toSet() == expected) {
            "Packaged JVM source API AIDL differs from exact public contract: actual=$actual expected=$expected"
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    dependsOn(verifyJvmSourceApiPackagedAidl)
}
