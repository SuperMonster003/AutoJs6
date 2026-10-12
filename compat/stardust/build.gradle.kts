// Frozen com.stardust compatibility package, part 1 of 2 (docs/dev/autojs4-runtime-plugin-roadmap.md D18 / P7.2,
// inventory in docs/dev/stardust-compat-inventory.md). The files in this module depend only on Android,
// AndroidX, Rhino and third-party libraries. The bridging and tainted files that reach host classes stay in
// app/src/main/java/com/stardust and depend on this module. Class names, package names and behavior are
// unchanged so scripts that call com.stardust.* directly keep working.
//
// zh-CN: 冻结的 com.stardust 兼容包第 1 部分 (D18 / P7.2, 盘点见 docs/dev/stardust-compat-inventory.md). 本模块的文件
// 只依赖 Android, AndroidX, Rhino 与第三方库; 依赖宿主类的桥接文件与受污染文件仍留在 app/src/main/java/com/stardust
// 并依赖本模块. 类名, 包名与行为不变, 直接调用 com.stardust.* 的脚本不受影响.

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "com.stardust.compat"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        // The package is a frozen binary-compatibility copy; lint findings are not acted upon (mirrors app/lint.xml).
        // zh-CN: 该包是冻结的二进制兼容副本, 不处理 lint 结果 (与 app/lint.xml 的忽略规则一致).
        lintConfig = file("lint.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    // Rhino is provided by the host at runtime, which derives a slim runtime jar from this same build.
    // zh-CN: Rhino 由宿主在运行时提供 (宿主从同一构建派生精简运行时 jar), 这里只参与编译.
    compileOnly(files("$rootDir/libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar"))

    implementation(libs.annotation)
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.material)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.eventbus)
}
