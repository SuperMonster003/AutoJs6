// @Reference
//  ! Current library (OpenCV) was referred to TonyJiangWJ/Auto.js (https://github.com/TonyJiangWJ/Auto.js) on Oct 18, 2022.

"opencv-4.8.0.aar"
    .let { path ->
        rootProject.extra["configurationName"].toString().let { name ->
            configurations.maybeCreate(name)
            file(path).takeIf { it.exists() }?.also {
                artifacts.add(name, it)
            } ?: throw Exception("File not found: \"$path\"")
        }
    }