plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.compose.api"
    compileSdk = versions.sdkVersionCompile
    defaultConfig {
        minSdk = versions.sdkVersionMin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { buildConfig = false }
}

dependencies {
    api(project(":plugin-api:common-plugin-api"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("compose.api.root", rootProject.projectDir.absolutePath)
    systemProperty("compose.api.reportDir", layout.buildDirectory.dir("reports/contract").get().asFile.absolutePath)
}

tasks.register<JavaExec>("exportComposeUiCatalog") {
    group = "documentation"
    description = "Export the actual frozen V1 component catalog as JSON for declaration/documentation checks."
    dependsOn("testDebugUnitTest")
    classpath = files(provider {
        val unitTests = tasks.named<Test>("testDebugUnitTest").get()
        unitTests.classpath + unitTests.testClassesDirs
    })
    mainClass.set("org.autojs.plugin.compose.api.ComposeCatalogExporter")
    val target = layout.buildDirectory.file("reports/compose-ui/component-catalog-v1.json")
    outputs.file(target)
    args(target.get().asFile.absolutePath)
}

tasks.register<JavaExec>("exportComposeUiInteropCatalog") {
    group = "documentation"
    description = "Export the negotiated AndroidView extension without changing the frozen V1 catalog."
    dependsOn("testDebugUnitTest")
    classpath = files(provider {
        val unitTests = tasks.named<Test>("testDebugUnitTest").get()
        unitTests.classpath + unitTests.testClassesDirs
    })
    mainClass.set("org.autojs.plugin.compose.api.ComposeInteropCatalogExporter")
    val target = layout.buildDirectory.file("reports/compose-ui/component-catalog-interop-v1.json")
    outputs.file(target)
    args(target.get().asFile.absolutePath)
}

tasks.register<JavaExec>("exportComposeUiV2Catalog") {
    group = "documentation"
    description = "Export the append-only V2 catalog and its retained V1 entries."
    dependsOn("testDebugUnitTest")
    classpath = files(provider {
        val unitTests = tasks.named<Test>("testDebugUnitTest").get()
        unitTests.classpath + unitTests.testClassesDirs
    })
    mainClass.set("org.autojs.plugin.compose.api.ComposeV2CatalogExporter")
    val target = layout.buildDirectory.file("reports/compose-ui/component-catalog-v2.json")
    outputs.file(target)
    args(target.get().asFile.absolutePath)
}
