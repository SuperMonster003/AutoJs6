// P0.2 only: keep the draft API and dependency experiment outside release variants.
// SavedState 1.3 brings compile-time Compose annotations. Keep them on the compiler classpath,
// but leave every Compose class, including the annotation definitions, to the plugin at runtime.
configurations.matching { it.name in setOf("appDebugRuntimeClasspath", "inrtDebugRuntimeClasspath") }.configureEach {
    exclude(group = "androidx.compose.runtime", module = "runtime-annotation")
    exclude(group = "androidx.compose.runtime", module = "runtime-annotation-android")
}
dependencies {
    add("debugImplementation", project(":plugin-api:compose-ui-api"))
    add("debugImplementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    add("debugImplementation", "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4")
    add("debugImplementation", "androidx.savedstate:savedstate-ktx:1.3.2")
    add("debugImplementation", "androidx.emoji2:emoji2:1.4.0")
    add("debugImplementation", "androidx.window:window:1.5.0")
}

tasks.register("verifyComposeUiSharedClasspath") {
    group = "verification"
    doLast {
        val locked = rootProject.file("locks/host-shared-deps.lock").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val resolved = configurations.getByName("appDebugRuntimeClasspath").incoming.resolutionResult
            .allComponents.mapNotNull { it.moduleVersion }
            .associate { "${it.group}:${it.name}" to it.version }
        locked.forEach { (id, version) -> check(resolved[id] == version) { "$id: locked $version, resolved ${resolved[id]}" } }
        check(resolved.keys.none { it.startsWith("androidx.compose.") || it.startsWith("androidx.compose:") })
        println("Compose P0 host shared classpath verified: ${locked.size} components; no Compose in host")
    }
}
