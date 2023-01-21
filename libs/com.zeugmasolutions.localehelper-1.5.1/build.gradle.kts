// @Modified
//  ! Current library (Locale Helper) was modified by SuperMonster003 on Jun 16, 2022.

"localehelper-1.5.1-modified.aar"
    .let { path ->
        rootProject.extra["configurationName"].toString().let { name ->
            configurations.maybeCreate(name)
            file(path).takeIf { it.exists() }?.also {
                artifacts.add(name, it)
            } ?: throw Exception("File not found: \"$path\"")
        }
    }