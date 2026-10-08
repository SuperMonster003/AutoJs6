// Q1(b): verify the production shared classpath in every app/inrt and debug/release variant.
// SavedState 1.3 brings compile-time Compose annotations. Keep them on the compiler classpath,
// but leave every Compose class, including the annotation definitions, to the plugin at runtime.
val composeHostVariants = listOf("appDebug", "appRelease", "inrtDebug", "inrtRelease")
configurations.matching { it.name in composeHostVariants.map { variant -> "${variant}RuntimeClasspath" } }.configureEach {
    exclude(group = "androidx.compose.runtime", module = "runtime-annotation")
    exclude(group = "androidx.compose.runtime", module = "runtime-annotation-android")
}
val composeClasspathSnapshot = layout.buildDirectory.file("reports/compose-ui/shared-runtime-classpath.txt")
val verifyComposeSharedClasspath = tasks.register("verifyComposeUiSharedClasspath") {
    group = "verification"
    doLast {
        val locked = rootProject.file("locks/host-shared-deps.lock").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val records = mutableListOf<String>()
        composeHostVariants.forEach { variant ->
            val components = configurations.getByName("${variant}RuntimeClasspath").incoming.resolutionResult.allComponents
            check(components.any { it.id.displayName.contains(":plugin-api:compose-ui-api") }) {
                "The V1 API must be supplied by the $variant host parent loader"
            }
            val resolved = components.mapNotNull { it.moduleVersion }.associate { "${it.group}:${it.name}" to it.version }
            locked.forEach { (id, version) -> check(resolved[id] == version) { "$variant $id: locked $version, resolved ${resolved[id]}" } }
            check(resolved.keys.none { it.startsWith("androidx.compose.") || it.startsWith("androidx.compose:") }) {
                "Compose must be supplied by the renderer APK, not $variant"
            }
            resolved.toSortedMap().forEach { (id, version) -> records += "$variant|$id=$version" }
        }
        composeClasspathSnapshot.get().asFile.apply {
            parentFile.mkdirs()
            writeText(records.joinToString("\n", postfix = "\n"))
        }
        println("Compose host shared classpath verified: ${locked.size} components in ${composeHostVariants.size} variants; V1 API present, no Compose implementation")
    }
}

// Fingerprint negotiation describes the resolved host, so every APK build must enforce its lock.
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyComposeSharedClasspath) }

tasks.withType<Test>().configureEach {
    // src/test is shared by all Android variants. Invalidate tests when either snapshot changes.
    dependsOn(verifyComposeSharedClasspath)
    inputs.file(rootProject.file("locks/host-shared-deps.lock"))
    inputs.file(composeClasspathSnapshot)
    systemProperty("compose.host.shared.lock", rootProject.file("locks/host-shared-deps.lock").absolutePath)
    systemProperty("compose.host.shared.snapshot", composeClasspathSnapshot.get().asFile.absolutePath)
    systemProperty("compose.host.project", rootProject.projectDir.absolutePath)
}
