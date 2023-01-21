"crashreport-4.0.4.aar"
    .let { path ->
        rootProject.extra["configurationName"].toString().let { name ->
            configurations.maybeCreate(name)
            file(path).takeIf { it.exists() }?.also {
                artifacts.add(name, it)
            } ?: throw Exception("File not found: \"$path\"")
        }
    }