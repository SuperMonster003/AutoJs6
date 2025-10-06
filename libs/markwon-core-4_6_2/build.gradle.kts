"markwon-core-4.6.2.aar"
    .let { path ->
        rootProject.extra["configurationName"].toString().let { name ->
            configurations.maybeCreate(name)
            file(path).takeIf { it.exists() }?.also {
                artifacts.add(name, it)
            } ?: throw Exception("File not found: \"$path\"")
        }
    }