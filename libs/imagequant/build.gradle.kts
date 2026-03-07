plugins {
    id("org.autojs.build.utils")
    id("org.autojs.build.properties")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

ext {
    set("projectName", "Image Quantization")
}

val versionMap = mapOf(
    "libimagequant" to "2.17.0",
    "libpng" to "1.6.49",

    "MIN_SDK" to props["MIN_SDK"].toInt(),
    "COMPILE_SDK" to props["COMPILE_SDK"].toInt(),
    "TARGET_SDK" to props["TARGET_SDK"].toInt(),

    "NDK" to props["IMAGE_QUANT/NDK"],
    "CMAKE" to props["IMAGE_QUANT/CMAKE"],
)

val nameMap = mapOf(
    "PROJECT" to extensions.extraProperties.get("projectName") as String,
    "NDK" to "NDK",
    "CMAKE" to "Cmake",
)

utils.configureLibraryLifecycleHooks(
    project,
    nameMap["PROJECT"] as String,
    listOf("libimagequant", "libpng", "NDK", "CMAKE").map { "${nameMap[it] ?: it}: ${versionMap[it]}" },
    emptyList(),
    null,
    listOf(".cxx"),
)

android {

    namespace = "org.pngquant"

    ndkVersion = versionMap["NDK"] as String
    compileSdk = versionMap["COMPILE_SDK"] as Int

    defaultConfig {
        minSdk = versionMap["MIN_SDK"] as Int

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"

                // @Hint by SuperMonster003 on Aug 28, 2025.
                //  ! Moved into `target_link_libraries(pngquant_bridge ...)` in `CMakeLists.txt`.
                //  ! zh-CN: 移至 `CMakeLists.txt` 文件 `target_link_libraries(pngquant_bridge ...)` 代码中.
                //  # cFlags "-ljnigraphics"
            }
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        lint {
            targetSdk = versionMap["TARGET_SDK"] as Int
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = versionMap["CMAKE"] as String
        }
    }
}
