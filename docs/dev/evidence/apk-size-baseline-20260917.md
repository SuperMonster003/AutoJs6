# APK 体积报告

生成时间: 2026-09-17 23:07

压缩 / 原始均为 MiB. DEX 包大小为 apkanalyzer `dex packages` 归属到包的原始字节 (不含共享的字符串池 / 类型表), 与 DEX 文件原始大小不等; DEX 文件在 APK 中约按 0.39 倍压缩, 移除一个包的 APK 收益以 0.39 倍归属字节为保守下限.

## autojs6-v6.8.0-universal.apk

- 路径: `D:/idea-projects/AutoJs6/app/build/outputs/apk/app/release/autojs6-v6.8.0-universal.apk`
- SHA-256: `074e67bfa1725a7de1429708accb854afc9b1f5f010fab3a6dc213fb5ae6ed50`

| 指标 | 值 |
| --- | ---: |
| APK 字节 | 41,146,055 |
| APK MiB | 39.24 |
| 条目数 | 4340 |
| 压缩合计 MiB | 38.73 |
| 原始合计 MiB | 92.55 |
| DEX 文件数 | 10 |
| DEX 包归属原始 MiB | 40.94 |
| DEX 定义方法数 | 433,419 |
| DEX 引用方法数 | 470,161 |

### 顶层构成 (压缩 >= 16 KiB 或基线中存在)

| 组 | 压缩 MiB | 原始 MiB | 文件数 |
| --- | ---: | ---: | ---: |
| dex | 26.18 | 67.64 | 10 |
| resources.arsc | 3.33 | 3.33 | 1 |
| other/resources | 2.93 | 6.38 | 1 |
| res | 2.55 | 3.68 | 3109 |
| assets/doc | 0.95 | 3.18 | 12 |
| lib/x86 | 0.40 | 1.10 | 3 |
| lib/x86_64 | 0.40 | 1.19 | 3 |
| lib/arm64-v8a | 0.39 | 1.21 | 3 |
| lib/armeabi-v7a | 0.34 | 0.82 | 3 |
| assets/modules | 0.21 | 0.86 | 42 |
| assets/sample | 0.19 | 0.41 | 200 |
| other/org/joda/time | 0.12 | 0.19 | 484 |
| other/org/jline/nativ | 0.11 | 0.39 | 7 |
| assets/fonts | 0.10 | 0.18 | 1 |
| other/org/mozilla/javascript | 0.10 | 0.33 | 13 |
| other/org/apache/commons | 0.09 | 0.22 | 128 |
| assets/indices | 0.06 | 0.54 | 4 |
| META-INF | 0.05 | 0.12 | 204 |
| other/okhttp3/internal | 0.04 | 0.04 | 2 |
| other/(root files) | 0.03 | 0.10 | 17 |
| assets/js-beautify | 0.03 | 0.11 | 2 |
| other/languages | 0.02 | 0.06 | 25 |
| other/org/jline/utils | 0.02 | 0.04 | 17 |
| other/org/eclipse/paho | 0.02 | 0.04 | 17 |

### DEX 包 (深度 <= 2 且 >= 150 KiB, 或关注列表)

| 包 | 定义方法 | 原始 MiB |
| --- | ---: | ---: |
| org | 162,178 | 15.86 |
| com | 148,393 | 13.39 |
| org.autojs | 94,176 | 9.52 |
| org.autojs.autojs | 85,173 | 8.48 |
| com.android | 76,688 | 7.30 |
| com.android.tools.r8 | 70,320 | 6.59 |
| androidx | 66,373 | 6.29 |
| com.google | 31,399 | 2.60 |
| kotlin | 26,848 | 2.36 |
| org.spongycastle | 19,350 | 2.28 |
| androidx.media3 | 23,191 | 2.18 |
| kotlin.reflect | 16,438 | 1.44 |
| org.mozilla | 12,176 | 1.29 |
| com.google.common | 16,095 | 1.13 |
| org.mozilla.javascript | 11,644 | 1.10 |
| com.google.android.material | 11,597 | 0.99 |
| io | 10,871 | 0.98 |
| com.fasterxml | 11,570 | 0.93 |
| org.apache | 10,854 | 0.91 |
| io.reactivex | 9,827 | 0.86 |
| com.vladsch | 9,229 | 0.71 |
| androidx.constraintlayout | 4,723 | 0.66 |
| androidx.core | 7,251 | 0.56 |
| kotlinx | 5,272 | 0.47 |
| kotlinx.coroutines | 5,259 | 0.47 |
| com.legacy | 4,397 | 0.46 |
| com.android.dx | 4,286 | 0.45 |
| com.legacy.android.dx | 4,042 | 0.43 |
| kotlin.collections | 4,476 | 0.43 |
| com.stardust | 5,354 | 0.43 |
| org.apache.commons.lang3 | 5,595 | 0.41 |
| androidx.appcompat | 4,545 | 0.39 |
| androidx.collection | 3,335 | 0.37 |
| org.jline | 3,067 | 0.36 |
| androidx.appcompatlegacy | 3,867 | 0.33 |
| org.joda | 4,501 | 0.33 |
| org.opencv | 6,649 | 0.31 |
| com.bumptech | 3,665 | 0.30 |
| com.bumptech.glide | 3,665 | 0.30 |
| okhttp3 | 2,759 | 0.27 |
| org.kohsuke | 3,706 | 0.27 |
| androidx.work | 2,736 | 0.26 |
| j$ | 2,016 | 0.23 |
| j$.time | 1,948 | 0.23 |
| org.apache.log4j | 2,359 | 0.23 |
| org.jsoup | 2,605 | 0.23 |
| android | 1,441 | 0.21 |
| androidx.recyclerview | 2,160 | 0.21 |
| androidx.room | 2,193 | 0.20 |
| com.android.apksig | 1,631 | 0.20 |
| okhttp3.internal | 1,539 | 0.17 |
| okio | 1,574 | 0.16 |
| org.apache.commons.io | 1,802 | 0.14 |
| com.sun.mail | 1,094 | 0.14 |
| org.eclipse.paho | 1,366 | 0.12 |
| org.apache.commons.codec | 974 | 0.12 |
| com.google.gson | 1,246 | 0.12 |
| org.junit | 1,331 | 0.10 |
| javax.mail | 1,007 | 0.09 |
| org.commonmark | 817 | 0.06 |
| retrofit2 | 463 | 0.05 |
| com.mcal.apksigner | 143 | 0.02 |
| junit | 296 | 0.02 |
| javax.activation | 183 | 0.02 |
| org.hamcrest | 275 | 0.02 |

### 最大条目 (前 30)

| 压缩 MiB | 原始 MiB | 条目 |
| ---: | ---: | --- |
| 3.81 | 10.34 | classes7.dex |
| 3.66 | 10.17 | classes8.dex |
| 3.46 | 8.60 | classes.dex |
| 3.39 | 9.38 | classes6.dex |
| 3.33 | 3.33 | resources.arsc |
| 3.23 | 8.14 | classes3.dex |
| 3.12 | 7.52 | classes4.dex |
| 2.98 | 7.39 | classes5.dex |
| 2.93 | 6.38 | resources/new_api_database.ser |
| 2.39 | 5.78 | classes9.dex |
| 0.40 | 1.09 | lib/x86/libc++_shared.so |
| 0.39 | 1.17 | lib/x86_64/libc++_shared.so |
| 0.38 | 1.20 | lib/arm64-v8a/libc++_shared.so |
| 0.33 | 0.81 | lib/armeabi-v7a/libc++_shared.so |
| 0.14 | 0.32 | classes10.dex |
| 0.13 | 0.60 | assets/modules/cheerio.js |
| 0.11 | 0.46 | assets/doc/CHANGELOG-ru.md |
| 0.10 | 0.18 | assets/fonts/droid_sans.ttf |
| 0.10 | 0.36 | assets/doc/CHANGELOG-ar.md |
| 0.09 | 0.32 | assets/doc/CHANGELOG-ja.md |
| 0.09 | 0.31 | assets/doc/CHANGELOG-fr.md |
| 0.09 | 0.30 | assets/doc/CHANGELOG-es.md |
| 0.09 | 0.28 | assets/doc/CHANGELOG-ko.md |
| 0.08 | 0.23 | assets/doc/CHANGELOG-zh-Hant-TW.md |
| 0.08 | 0.22 | assets/doc/CHANGELOG-zh-Hant-HK.md |
| 0.08 | 0.25 | assets/doc/CHANGELOG-en.md |
| 0.08 | 0.22 | assets/doc/CHANGELOG.md |
| 0.08 | 0.22 | assets/doc/CHANGELOG-zh-Hans.md |
| 0.05 | 0.05 | assets/sample/σ¢╛σâÅΣ╕ÄΘó£Φë▓/µë╛σ¢╛/super_mario.jpg |
| 0.05 | 0.48 | assets/indices/all_android_classes.json |

## autojs6-v6.8.0-arm64-v8a.apk

- 路径: `D:/idea-projects/AutoJs6/app/build/outputs/apk/app/release/autojs6-v6.8.0-arm64-v8a.apk`
- SHA-256: `98bb9faf5cfdda8357ea8bfb63f5f660392f0ddb3b6d4570151e1b2215953375`

| 指标 | 值 |
| --- | ---: |
| APK 字节 | 39,949,029 |
| APK MiB | 38.10 |
| 条目数 | 4331 |
| 压缩合计 MiB | 37.59 |
| 原始合计 MiB | 89.44 |
| DEX 文件数 | 10 |
| DEX 包归属原始 MiB | 40.94 |
| DEX 定义方法数 | 433,419 |
| DEX 引用方法数 | 470,161 |

### 顶层构成 (压缩 >= 16 KiB 或基线中存在)

| 组 | 压缩 MiB | 原始 MiB | 文件数 |
| --- | ---: | ---: | ---: |
| dex | 26.18 | 67.64 | 10 |
| resources.arsc | 3.33 | 3.33 | 1 |
| other/resources | 2.93 | 6.38 | 1 |
| res | 2.55 | 3.68 | 3109 |
| assets/doc | 0.95 | 3.18 | 12 |
| lib/arm64-v8a | 0.39 | 1.21 | 3 |
| assets/modules | 0.21 | 0.86 | 42 |
| assets/sample | 0.19 | 0.41 | 200 |
| other/org/joda/time | 0.12 | 0.19 | 484 |
| other/org/jline/nativ | 0.11 | 0.39 | 7 |
| assets/fonts | 0.10 | 0.18 | 1 |
| other/org/mozilla/javascript | 0.10 | 0.33 | 13 |
| other/org/apache/commons | 0.09 | 0.22 | 128 |
| assets/indices | 0.06 | 0.54 | 4 |
| META-INF | 0.05 | 0.12 | 204 |
| other/okhttp3/internal | 0.04 | 0.04 | 2 |
| other/(root files) | 0.03 | 0.10 | 17 |
| assets/js-beautify | 0.03 | 0.11 | 2 |
| other/languages | 0.02 | 0.06 | 25 |
| other/org/jline/utils | 0.02 | 0.04 | 17 |
| other/org/eclipse/paho | 0.02 | 0.04 | 17 |

### DEX 包 (深度 <= 2 且 >= 150 KiB, 或关注列表)

| 包 | 定义方法 | 原始 MiB |
| --- | ---: | ---: |
| org | 162,178 | 15.86 |
| com | 148,393 | 13.39 |
| org.autojs | 94,176 | 9.52 |
| org.autojs.autojs | 85,173 | 8.48 |
| com.android | 76,688 | 7.30 |
| com.android.tools.r8 | 70,320 | 6.59 |
| androidx | 66,373 | 6.29 |
| com.google | 31,399 | 2.60 |
| kotlin | 26,848 | 2.36 |
| org.spongycastle | 19,350 | 2.28 |
| androidx.media3 | 23,191 | 2.18 |
| kotlin.reflect | 16,438 | 1.44 |
| org.mozilla | 12,176 | 1.29 |
| com.google.common | 16,095 | 1.13 |
| org.mozilla.javascript | 11,644 | 1.10 |
| com.google.android.material | 11,597 | 0.99 |
| io | 10,871 | 0.98 |
| com.fasterxml | 11,570 | 0.93 |
| org.apache | 10,854 | 0.91 |
| io.reactivex | 9,827 | 0.86 |
| com.vladsch | 9,229 | 0.71 |
| androidx.constraintlayout | 4,723 | 0.66 |
| androidx.core | 7,251 | 0.56 |
| kotlinx | 5,272 | 0.47 |
| kotlinx.coroutines | 5,259 | 0.47 |
| com.legacy | 4,397 | 0.46 |
| com.android.dx | 4,286 | 0.45 |
| com.legacy.android.dx | 4,042 | 0.43 |
| kotlin.collections | 4,476 | 0.43 |
| com.stardust | 5,354 | 0.43 |
| org.apache.commons.lang3 | 5,595 | 0.41 |
| androidx.appcompat | 4,545 | 0.39 |
| androidx.collection | 3,335 | 0.37 |
| org.jline | 3,067 | 0.36 |
| androidx.appcompatlegacy | 3,867 | 0.33 |
| org.joda | 4,501 | 0.33 |
| org.opencv | 6,649 | 0.31 |
| com.bumptech | 3,665 | 0.30 |
| com.bumptech.glide | 3,665 | 0.30 |
| okhttp3 | 2,759 | 0.27 |
| org.kohsuke | 3,706 | 0.27 |
| androidx.work | 2,736 | 0.26 |
| j$ | 2,016 | 0.23 |
| j$.time | 1,948 | 0.23 |
| org.apache.log4j | 2,359 | 0.23 |
| org.jsoup | 2,605 | 0.23 |
| android | 1,441 | 0.21 |
| androidx.recyclerview | 2,160 | 0.21 |
| androidx.room | 2,193 | 0.20 |
| com.android.apksig | 1,631 | 0.20 |
| okhttp3.internal | 1,539 | 0.17 |
| okio | 1,574 | 0.16 |
| org.apache.commons.io | 1,802 | 0.14 |
| com.sun.mail | 1,094 | 0.14 |
| org.eclipse.paho | 1,366 | 0.12 |
| org.apache.commons.codec | 974 | 0.12 |
| com.google.gson | 1,246 | 0.12 |
| org.junit | 1,331 | 0.10 |
| javax.mail | 1,007 | 0.09 |
| org.commonmark | 817 | 0.06 |
| retrofit2 | 463 | 0.05 |
| com.mcal.apksigner | 143 | 0.02 |
| junit | 296 | 0.02 |
| javax.activation | 183 | 0.02 |
| org.hamcrest | 275 | 0.02 |

### 最大条目 (前 30)

| 压缩 MiB | 原始 MiB | 条目 |
| ---: | ---: | --- |
| 3.81 | 10.34 | classes7.dex |
| 3.66 | 10.17 | classes8.dex |
| 3.46 | 8.60 | classes.dex |
| 3.39 | 9.38 | classes6.dex |
| 3.33 | 3.33 | resources.arsc |
| 3.23 | 8.14 | classes3.dex |
| 3.12 | 7.52 | classes4.dex |
| 2.98 | 7.39 | classes5.dex |
| 2.93 | 6.38 | resources/new_api_database.ser |
| 2.39 | 5.78 | classes9.dex |
| 0.38 | 1.20 | lib/arm64-v8a/libc++_shared.so |
| 0.14 | 0.32 | classes10.dex |
| 0.13 | 0.60 | assets/modules/cheerio.js |
| 0.11 | 0.46 | assets/doc/CHANGELOG-ru.md |
| 0.10 | 0.18 | assets/fonts/droid_sans.ttf |
| 0.10 | 0.36 | assets/doc/CHANGELOG-ar.md |
| 0.09 | 0.32 | assets/doc/CHANGELOG-ja.md |
| 0.09 | 0.31 | assets/doc/CHANGELOG-fr.md |
| 0.09 | 0.30 | assets/doc/CHANGELOG-es.md |
| 0.09 | 0.28 | assets/doc/CHANGELOG-ko.md |
| 0.08 | 0.23 | assets/doc/CHANGELOG-zh-Hant-TW.md |
| 0.08 | 0.22 | assets/doc/CHANGELOG-zh-Hant-HK.md |
| 0.08 | 0.25 | assets/doc/CHANGELOG-en.md |
| 0.08 | 0.22 | assets/doc/CHANGELOG.md |
| 0.08 | 0.22 | assets/doc/CHANGELOG-zh-Hans.md |
| 0.05 | 0.05 | assets/sample/σ¢╛σâÅΣ╕ÄΘó£Φë▓/µë╛σ¢╛/super_mario.jpg |
| 0.05 | 0.48 | assets/indices/all_android_classes.json |
| 0.04 | 0.04 | res/GX.png |
| 0.04 | 0.04 | okhttp3/internal/publicsuffix/publicsuffixes.gz |
| 0.04 | 0.12 | org/jline/nativ/Windows/x86_64/jlinenative.dll |

