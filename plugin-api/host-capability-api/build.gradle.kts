import org.gradle.api.tasks.bundling.Zip
import java.util.zip.ZipFile

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

val packagedHostCapabilityAidl = linkedSetOf(
    "org/autojs/plugin/host/capability/api/IHostCapabilityBroker.aidl",
    "org/autojs/plugin/host/capability/api/IHostCapabilityCallback.aidl",
)

android {
    namespace = "org.autojs.plugin.host.capability.api"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        aarMetadata { minCompileSdk = 36 }
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

    // Both family contracts import these interfaces. Export source declarations for AIDL,
    // while their generated Binder classes stay exclusively in this shared module.
    requireNotNull(aidlPackagedList) {
        "AGP did not expose the packaged AIDL collection"
    }.addAll(packagedHostCapabilityAidl)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

val verifyHostCapabilityApiPackagedAidl = tasks.register("verifyHostCapabilityApiPackagedAidl") {
    group = "verification"
    description = "Checks the shared Binder declarations exported for dependent contract AARs."
    dependsOn("bundleDebugAar")
    outputs.upToDateWhen { false }
    doLast {
        val aar = tasks.named<Zip>("bundleDebugAar").get().archiveFile.get().asFile
        val actual = ZipFile(aar).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }
                .map { it.name }.filter { it.startsWith("aidl/") }.toList()
        }
        val expected = packagedHostCapabilityAidl.map { "aidl/$it" }.sorted()
        check(actual.sorted() == expected) { "Shared capability AIDL packaging mismatch: $actual" }
    }
}

tasks.named("check") {
    dependsOn(verifyHostCapabilityApiPackagedAidl)
}
