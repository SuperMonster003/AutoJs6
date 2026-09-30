package org.autojs.plugin.packagearchive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BundletoolTocDecoderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val arm64EnglishDevice = PackageDeviceSpec(
        sdk = 35,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        densityDpi = 440,
        locales = listOf("en-US"),
    )

    @Test
    fun tocInArchiveWithManyResourcesCanSelectSplits() {
        val file = temporaryFolder.newFile("many-entries.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            repeat(18_184) { index ->
                zip.putNextEntry(ZipEntry("assets/entry_$index"))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("toc.pb"))
            zip.write(tocWithInstantAndPersistentVariants())
            zip.closeEntry()
        }
        assertTrue(BundletoolTocDecoder.select(file, arm64EnglishDevice).packageName.orEmpty().contains("com.example.bundle"))
    }

    @Test
    fun persistentVariantSelectsInstallTimeSplitsAndAssetSlices() {
        val selection = BundletoolTocDecoder.select(
            tocWithInstantAndPersistentVariants(),
            arm64EnglishDevice,
        )

        assertEquals("com.example.bundle", selection.packageName)
        assertEquals(1L, selection.variantNumber)
        assertEquals(
            listOf(
                "splits/base-master.apk",
                "splits/base-en.apk",
                "splits/base-xxhdpi.apk",
                "asset-slices/textures-arm64.apk",
            ),
            selection.apkPaths,
        )
        assertFalse(selection.apkPaths.any { it.startsWith("instant/") })
        assertFalse(selection.apkPaths.any { "ondemand" in it })
        assertFalse(selection.apkPaths.any { "system" in it })
        assertTrue(selection.modules.single { it.name == "textures" }.assetModule)
    }

    @Test
    fun sdkConditionalInstallTimeModuleIsSelectedOnlyWhenItMatches() {
        val toc = tocWithSdkConditionalModule()

        val api35 = BundletoolTocDecoder.select(toc, arm64EnglishDevice)
        val api31 = BundletoolTocDecoder.select(
            toc,
            arm64EnglishDevice.copy(sdk = 31),
        )

        assertEquals(listOf("splits/base-master.apk"), api35.apkPaths)
        assertEquals(
            listOf("splits/base-master.apk", "splits/runtime-master.apk"),
            api31.apkPaths,
        )
    }

    @Test
    fun languageFallbackMatchesUntilAlternativesCoverEveryDeviceLanguage() {
        val partialCoverage = BundletoolTocDecoder.select(
            tocWithLanguageFallback(),
            arm64EnglishDevice.copy(locales = listOf("en-US", "de-DE")),
        )
        val fullCoverage = BundletoolTocDecoder.select(
            tocWithLanguageFallback(),
            arm64EnglishDevice.copy(locales = listOf("en-US", "fr-FR")),
        )

        assertEquals(
            listOf(
                "splits/base-master.apk",
                "splits/base-other-languages.apk",
            ),
            partialCoverage.apkPaths,
        )
        assertEquals(listOf("splits/base-master.apk"), fullCoverage.apkPaths)
    }

    @Test
    fun universalStandaloneVariantSelectsUniqueBaseAndIndependentFeature() {
        val selection = BundletoolTocDecoder.select(
            tocWithUniversalStandaloneVariant(),
            arm64EnglishDevice,
        )

        assertEquals(
            listOf("universal.apk", "standalones/camera.apk"),
            selection.apkPaths,
        )
        val selectedApks = selection.modules.flatMap { it.apks }
        assertEquals(1, selectedApks.count { it.master })
        assertTrue(selectedApks.single { it.path == "universal.apk" }.master)
        assertEquals(
            "camera",
            selectedApks.single { it.path == "standalones/camera.apk" }.splitId,
        )
    }

    @Test
    fun sdkExcludedModuleDoesNotInspectItsUnsupportedApkTargeting() {
        val selection = BundletoolTocDecoder.select(
            tocWithSdkExcludedUnsupportedApk(),
            arm64EnglishDevice,
        )

        assertEquals(listOf("splits/base-master.apk"), selection.apkPaths)
    }

    @Test
    fun sdkRuntimeVariantIsPreferredOnlyFromApi34() {
        val toc = tocWithSdkRuntimeVariants()

        val api33 = BundletoolTocDecoder.select(
            toc,
            arm64EnglishDevice.copy(sdk = 33),
        )
        val api34 = BundletoolTocDecoder.select(
            toc,
            arm64EnglishDevice.copy(sdk = 34),
        )

        assertEquals(listOf("splits/base-regular.apk"), api33.apkPaths)
        assertEquals(99L, api33.variantNumber)
        assertEquals(listOf("splits/base-sdk-runtime.apk"), api34.apkPaths)
        assertEquals(1L, api34.variantNumber)
    }

    @Test
    fun bundletoolVersionControlsConflictingDeliveryMetadata() {
        val legacy = BundletoolTocDecoder.select(
            tocWithConflictingDeliveryMetadata("0.10.1"),
            arm64EnglishDevice,
        )
        val modern = BundletoolTocDecoder.select(
            tocWithConflictingDeliveryMetadata("0.10.2"),
            arm64EnglishDevice,
        )

        assertEquals(listOf("splits/base-master.apk"), legacy.apkPaths)
        assertEquals(
            listOf("splits/base-master.apk", "splits/feature-master.apk"),
            modern.apkPaths,
        )
    }

    @Test
    fun apexArtifactIsParsedThenReportedAsUnsupported() {
        val failure = captureIOException {
            BundletoolTocDecoder.select(tocWithApexArtifact(), arm64EnglishDevice)
        }

        assertTrue(failure.message.orEmpty().contains("APEX"))
        assertFalse(failure.message.orEmpty().startsWith("Invalid APKS toc.pb"))
    }

    @Test
    fun textureCompressionAndUnknownTargetingFailConservatively() {
        val textureFailure = captureIOException {
            BundletoolTocDecoder.select(
                tocWithUnsupportedVariantTextureTargeting(),
                arm64EnglishDevice,
            )
        }
        val unknownFailure = captureIOException {
            BundletoolTocDecoder.select(
                tocWithUnknownNestedTargetingField(),
                arm64EnglishDevice,
            )
        }

        assertTrue(textureFailure.message.orEmpty().contains("targeting"))
        assertTrue(unknownFailure.message.orEmpty().contains("targeting"))
    }

    @Test
    fun multiAbiTargetingSelectsTheBestContainedSet() {
        val selection = BundletoolTocDecoder.select(
            tocWithMultiAbiVariants(),
            arm64EnglishDevice,
        )

        assertEquals(listOf("splits/base-arm64-v7a.apk"), selection.apkPaths)
    }

    @Test
    fun maximumUint32VariantNumberRetainsPriority() {
        val selection = BundletoolTocDecoder.select(
            tocWithMaximumVariantNumber(),
            arm64EnglishDevice,
        )

        assertEquals(UINT32_MAX, selection.variantNumber)
        assertEquals(listOf("splits/base-uint32-max.apk"), selection.apkPaths)
    }

    @Test
    fun duplicateTargetingInvalidDensityAndDefaultTargetingAreRejected() {
        captureIOException {
            BundletoolTocDecoder.select(
                tocWithDuplicateVariantTargeting(),
                arm64EnglishDevice,
            )
        }
        captureIOException {
            BundletoolTocDecoder.select(
                tocWithUnsetScreenDensity(),
                arm64EnglishDevice,
            )
        }
        val defaultTargetingFailure = captureIOException {
            BundletoolTocDecoder.select(
                tocWithDefaultTargetingValue(),
                arm64EnglishDevice,
            )
        }

        assertTrue(defaultTargetingFailure.message.orEmpty().contains("default targeting"))
    }

    @Test
    fun apksFileIsReadWithoutExtractingEntries() {
        val apks = temporaryFolder.newFile("sample.apks")
        ZipOutputStream(FileOutputStream(apks)).use { zip ->
            zip.putNextEntry(ZipEntry("toc.pb"))
            zip.write(tocWithSdkConditionalModule())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("splits/base-master.apk"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }

        val selection = BundletoolTocDecoder.select(apks, arm64EnglishDevice)

        assertEquals(listOf("splits/base-master.apk"), selection.apkPaths)
    }

    @Test
    fun unsupportedConditionalTargetingFailsClearly() {
        val failure = captureIOException {
            BundletoolTocDecoder.select(
                tocWithUnsupportedModuleTargeting(),
                arm64EnglishDevice,
            )
        }

        assertTrue(failure.message.orEmpty().contains("unsupported conditional targeting"))
    }

    @Test
    fun malformedAndOverBudgetTocAreRejected() {
        val malformed = byteArrayOf(
            fieldTag(BUILD_VARIANT_FIELD, WIRE_LENGTH_DELIMITED).toByte(),
            0x80.toByte(),
        )
        captureIOException {
            BundletoolTocDecoder.select(malformed, arm64EnglishDevice)
        }

        captureIOException {
            BundletoolTocDecoder.select(
                tocWithSdkConditionalModule(),
                arm64EnglishDevice,
                BundletoolTocDecoder.Limits(maxVariants = 0),
            )
        }
    }

    private fun tocWithInstantAndPersistentVariants(): ByteArray = ProtoWriter().apply {
        string(BUILD_PACKAGE_NAME_FIELD, "com.example.bundle")
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 0)
            sdkVariantTargeting(min = 24)
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("base", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "instant/instant-base-master.apk")
                    splitMetadata(APK_INSTANT_METADATA_FIELD, "base", master = true)
                }
            }
        }
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 1)
            sdkVariantTargeting(min = 24)
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("base", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/base-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "base", master = true)
                }
                languageSplit("splits/base-en.apk", "config.en", "en", "fr")
                languageSplit("splits/base-fr.apk", "config.fr", "fr", "en")
                densitySplit(
                    path = "splits/base-xhdpi.apk",
                    splitId = "config.xhdpi",
                    valueAlias = DENSITY_XHDPI,
                    alternativeAlias = DENSITY_XXHDPI,
                )
                densitySplit(
                    path = "splits/base-xxhdpi.apk",
                    splitId = "config.xxhdpi",
                    valueAlias = DENSITY_XXHDPI,
                    alternativeAlias = DENSITY_XHDPI,
                )
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "system/system.apk")
                    message(APK_SYSTEM_METADATA_FIELD) {}
                }
            }
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("ondemand", DELIVERY_ON_DEMAND)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/ondemand-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "ondemand", master = true)
                }
            }
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("fastfollow", DELIVERY_FAST_FOLLOW)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/fastfollow-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "fastfollow", master = true)
                }
            }
        }
        message(BUILD_ASSET_SLICE_SET_FIELD) {
            message(ASSET_SET_METADATA_FIELD) {
                string(ASSET_MODULE_NAME_FIELD, "textures")
                varint(ASSET_MODULE_DELIVERY_FIELD, DELIVERY_INSTALL_TIME)
            }
            message(ASSET_SET_DESCRIPTION_FIELD) {
                message(APK_TARGETING_FIELD) {
                    abiTargeting(value = ABI_ARM64, alternative = ABI_X86)
                }
                string(APK_PATH_FIELD, "asset-slices/textures-arm64.apk")
                splitMetadata(APK_ASSET_SLICE_METADATA_FIELD, "textures.arm64", master = false)
            }
            message(ASSET_SET_DESCRIPTION_FIELD) {
                message(APK_TARGETING_FIELD) {
                    abiTargeting(value = ABI_X86, alternative = ABI_ARM64)
                }
                string(APK_PATH_FIELD, "asset-slices/textures-x86.apk")
                splitMetadata(APK_ASSET_SLICE_METADATA_FIELD, "textures.x86", master = false)
            }
        }
        message(BUILD_ASSET_SLICE_SET_FIELD) {
            message(ASSET_SET_METADATA_FIELD) {
                string(ASSET_MODULE_NAME_FIELD, "ondemand-assets")
                varint(ASSET_MODULE_DELIVERY_FIELD, DELIVERY_ON_DEMAND)
            }
            message(ASSET_SET_DESCRIPTION_FIELD) {
                string(APK_PATH_FIELD, "asset-slices/ondemand.apk")
                splitMetadata(APK_ASSET_SLICE_METADATA_FIELD, "ondemand", master = false)
            }
        }
    }.toByteArray()

    private fun tocWithSdkConditionalModule(): ByteArray = ProtoWriter().apply {
        string(BUILD_PACKAGE_NAME_FIELD, "com.example.conditional")
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 0)
            sdkVariantTargeting(min = 24)
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("base", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/base-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "base", master = true)
                }
            }
            message(VARIANT_APK_SET_FIELD) {
                message(APK_SET_METADATA_FIELD) {
                    string(MODULE_NAME_FIELD, "runtime")
                    varint(MODULE_DELIVERY_FIELD, DELIVERY_INSTALL_TIME)
                    message(MODULE_TARGETING_FIELD) {
                        sdkTargeting(min = 1, alternative = 33)
                    }
                }
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/runtime-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "runtime", master = true)
                }
            }
        }
    }.toByteArray()

    private fun tocWithLanguageFallback(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("base", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/base-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "base", master = true)
                }
                message(APK_SET_DESCRIPTION_FIELD) {
                    message(APK_TARGETING_FIELD) {
                        message(APK_LANGUAGE_TARGETING_FIELD) {
                            string(TARGETING_ALTERNATIVE_FIELD, "en")
                            string(TARGETING_ALTERNATIVE_FIELD, "fr")
                        }
                    }
                    string(APK_PATH_FIELD, "splits/base-other-languages.apk")
                    splitMetadata(
                        APK_SPLIT_METADATA_FIELD,
                        "config.other_languages",
                        master = false,
                    )
                }
            }
        }
    }.toByteArray()

    private fun tocWithUniversalStandaloneVariant(): ByteArray = ProtoWriter().apply {
        string(BUILD_PACKAGE_NAME_FIELD, "com.example.universal")
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 7)
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("base", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "universal.apk")
                    standaloneMetadata(fusedModules = listOf("base"))
                }
            }
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("camera", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "standalones/camera.apk")
                    standaloneMetadata(
                        fusedModules = listOf("camera"),
                        splitId = "camera",
                    )
                }
            }
        }
    }.toByteArray()

    private fun tocWithUnsupportedModuleTargeting(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("base", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/base-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "base", master = true)
                }
            }
            message(VARIANT_APK_SET_FIELD) {
                message(APK_SET_METADATA_FIELD) {
                    string(MODULE_NAME_FIELD, "conditional")
                    varint(MODULE_DELIVERY_FIELD, DELIVERY_INSTALL_TIME)
                    message(MODULE_TARGETING_FIELD) {
                        message(MODULE_DEVICE_FEATURE_FIELD) {
                            message(1) {
                                string(1, "android.hardware.camera")
                            }
                        }
                    }
                }
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "splits/conditional-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "conditional", master = true)
                }
            }
        }
    }.toByteArray()

    private fun tocWithSdkExcludedUnsupportedApk(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            baseSplit("splits/base-master.apk")
            message(VARIANT_APK_SET_FIELD) {
                message(APK_SET_METADATA_FIELD) {
                    string(MODULE_NAME_FIELD, "future")
                    varint(MODULE_DELIVERY_FIELD, DELIVERY_INSTALL_TIME)
                    message(MODULE_TARGETING_FIELD) {
                        sdkTargeting(min = 36)
                    }
                }
                message(APK_SET_DESCRIPTION_FIELD) {
                    message(APK_TARGETING_FIELD) {
                        message(APK_TEXTURE_TARGETING_FIELD) {
                            varint(TARGETING_VALUE_FIELD, 1)
                        }
                    }
                    string(APK_PATH_FIELD, "splits/future-master.apk")
                    splitMetadata(APK_SPLIT_METADATA_FIELD, "future", master = true)
                }
            }
        }
    }.toByteArray()

    private fun tocWithSdkRuntimeVariants(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 99)
            baseSplit("splits/base-regular.apk")
        }
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 1)
            message(VARIANT_TARGETING_FIELD) {
                message(VARIANT_SDK_RUNTIME_TARGETING_FIELD) {
                    varint(SDK_RUNTIME_REQUIRED_FIELD, 1)
                }
            }
            baseSplit("splits/base-sdk-runtime.apk")
        }
    }.toByteArray()

    private fun tocWithConflictingDeliveryMetadata(version: String): ByteArray =
        ProtoWriter().apply {
            message(BUILD_BUNDLETOOL_FIELD) {
                string(BUNDLETOOL_VERSION_FIELD, version)
            }
            message(BUILD_VARIANT_FIELD) {
                baseSplit("splits/base-master.apk")
                message(VARIANT_APK_SET_FIELD) {
                    moduleMetadata(
                        name = "feature",
                        delivery = DELIVERY_INSTALL_TIME,
                        legacyOnDemand = true,
                    )
                    message(APK_SET_DESCRIPTION_FIELD) {
                        string(APK_PATH_FIELD, "splits/feature-master.apk")
                        splitMetadata(
                            APK_SPLIT_METADATA_FIELD,
                            "feature",
                            master = true,
                        )
                    }
                }
            }
        }.toByteArray()

    private fun tocWithApexArtifact(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            message(VARIANT_APK_SET_FIELD) {
                moduleMetadata("base", DELIVERY_INSTALL_TIME)
                message(APK_SET_DESCRIPTION_FIELD) {
                    string(APK_PATH_FIELD, "apex/base.apex")
                    message(APK_APEX_METADATA_FIELD) {}
                }
            }
        }
    }.toByteArray()

    private fun tocWithUnsupportedVariantTextureTargeting(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            message(VARIANT_TARGETING_FIELD) {
                message(VARIANT_TEXTURE_TARGETING_FIELD) {
                    varint(TARGETING_VALUE_FIELD, 1)
                }
            }
            baseSplit("splits/base-master.apk")
        }
    }.toByteArray()

    private fun tocWithUnknownNestedTargetingField(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            message(VARIANT_TARGETING_FIELD) {
                message(SDK_TARGETING_FIELD) {
                    message(TARGETING_VALUE_FIELD) {
                        message(SDK_MIN_FIELD) {
                            varint(WRAPPER_VALUE_FIELD, 24)
                        }
                    }
                    message(99) {
                        varint(1, 1)
                    }
                }
            }
            baseSplit("splits/base-master.apk")
        }
    }.toByteArray()

    private fun tocWithMultiAbiVariants(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 20)
            multiAbiVariantTargeting(
                value = listOf(ABI_ARM64),
                alternative = listOf(ABI_ARM64, ABI_ARMEABI_V7A),
            )
            baseSplit("splits/base-arm64.apk")
        }
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 10)
            multiAbiVariantTargeting(
                value = listOf(ABI_ARM64, ABI_ARMEABI_V7A),
                alternative = listOf(ABI_ARM64),
            )
            baseSplit("splits/base-arm64-v7a.apk")
        }
    }.toByteArray()

    private fun tocWithMaximumVariantNumber(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, 1)
            baseSplit("splits/base-low.apk")
        }
        message(BUILD_VARIANT_FIELD) {
            varint(VARIANT_NUMBER_FIELD, UINT32_MAX)
            baseSplit("splits/base-uint32-max.apk")
        }
    }.toByteArray()

    private fun tocWithDuplicateVariantTargeting(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            sdkVariantTargeting(24)
            sdkVariantTargeting(26)
            baseSplit("splits/base-master.apk")
        }
    }.toByteArray()

    private fun tocWithUnsetScreenDensity(): ByteArray = ProtoWriter().apply {
        message(BUILD_VARIANT_FIELD) {
            message(VARIANT_TARGETING_FIELD) {
                message(VARIANT_DENSITY_TARGETING_FIELD) {
                    message(TARGETING_VALUE_FIELD) {}
                }
            }
            baseSplit("splits/base-master.apk")
        }
    }.toByteArray()

    private fun tocWithDefaultTargetingValue(): ByteArray = ProtoWriter().apply {
        message(BUILD_DEFAULT_TARGETING_VALUE_FIELD) {
            varint(1, 1)
        }
        message(BUILD_VARIANT_FIELD) {
            baseSplit("splits/base-master.apk")
        }
    }.toByteArray()

    private fun ProtoWriter.moduleMetadata(
        name: String,
        delivery: Int,
        legacyOnDemand: Boolean = false,
    ) {
        message(APK_SET_METADATA_FIELD) {
            string(MODULE_NAME_FIELD, name)
            varint(MODULE_DELIVERY_FIELD, delivery)
            if (legacyOnDemand) {
                varint(MODULE_ON_DEMAND_DEPRECATED_FIELD, 1)
            }
        }
    }

    private fun ProtoWriter.baseSplit(path: String) {
        message(VARIANT_APK_SET_FIELD) {
            moduleMetadata("base", DELIVERY_INSTALL_TIME)
            message(APK_SET_DESCRIPTION_FIELD) {
                string(APK_PATH_FIELD, path)
                splitMetadata(APK_SPLIT_METADATA_FIELD, "base", master = true)
            }
        }
    }

    private fun ProtoWriter.sdkVariantTargeting(min: Int) {
        message(VARIANT_TARGETING_FIELD) {
            sdkTargeting(min)
        }
    }

    private fun ProtoWriter.sdkTargeting(min: Int, alternative: Int? = null) {
        message(SDK_TARGETING_FIELD) {
            message(TARGETING_VALUE_FIELD) {
                message(SDK_MIN_FIELD) {
                    varint(WRAPPER_VALUE_FIELD, min)
                }
            }
            alternative?.let { alternativeSdk ->
                message(TARGETING_ALTERNATIVE_FIELD) {
                    message(SDK_MIN_FIELD) {
                        varint(WRAPPER_VALUE_FIELD, alternativeSdk)
                    }
                }
            }
        }
    }

    private fun ProtoWriter.languageSplit(
        path: String,
        splitId: String,
        value: String,
        alternative: String,
    ) {
        message(APK_SET_DESCRIPTION_FIELD) {
            message(APK_TARGETING_FIELD) {
                message(APK_LANGUAGE_TARGETING_FIELD) {
                    string(TARGETING_VALUE_FIELD, value)
                    string(TARGETING_ALTERNATIVE_FIELD, alternative)
                }
            }
            string(APK_PATH_FIELD, path)
            splitMetadata(APK_SPLIT_METADATA_FIELD, splitId, master = false)
        }
    }

    private fun ProtoWriter.densitySplit(
        path: String,
        splitId: String,
        valueAlias: Int,
        alternativeAlias: Int,
    ) {
        message(APK_SET_DESCRIPTION_FIELD) {
            message(APK_TARGETING_FIELD) {
                message(APK_DENSITY_TARGETING_FIELD) {
                    message(TARGETING_VALUE_FIELD) {
                        varint(DENSITY_ALIAS_FIELD, valueAlias)
                    }
                    message(TARGETING_ALTERNATIVE_FIELD) {
                        varint(DENSITY_ALIAS_FIELD, alternativeAlias)
                    }
                }
            }
            string(APK_PATH_FIELD, path)
            splitMetadata(APK_SPLIT_METADATA_FIELD, splitId, master = false)
        }
    }

    private fun ProtoWriter.abiTargeting(value: Int, alternative: Int) {
        message(APK_ABI_TARGETING_FIELD) {
            message(TARGETING_VALUE_FIELD) {
                varint(ABI_ALIAS_FIELD, value)
            }
            message(TARGETING_ALTERNATIVE_FIELD) {
                varint(ABI_ALIAS_FIELD, alternative)
            }
        }
    }

    private fun ProtoWriter.multiAbiVariantTargeting(
        value: List<Int>,
        alternative: List<Int>,
    ) {
        message(VARIANT_TARGETING_FIELD) {
            message(VARIANT_MULTI_ABI_TARGETING_FIELD) {
                multiAbi(TARGETING_VALUE_FIELD, value)
                multiAbi(TARGETING_ALTERNATIVE_FIELD, alternative)
            }
        }
    }

    private fun ProtoWriter.multiAbi(fieldNumber: Int, aliases: List<Int>) {
        message(fieldNumber) {
            aliases.forEach { alias ->
                message(MULTI_ABI_ABI_FIELD) {
                    varint(ABI_ALIAS_FIELD, alias)
                }
            }
        }
    }

    private fun ProtoWriter.splitMetadata(
        fieldNumber: Int,
        splitId: String,
        master: Boolean,
    ) {
        message(fieldNumber) {
            string(SPLIT_ID_FIELD, splitId)
            varint(SPLIT_MASTER_FIELD, if (master) 1 else 0)
        }
    }

    private fun ProtoWriter.standaloneMetadata(
        fusedModules: List<String>,
        splitId: String? = null,
    ) {
        message(APK_STANDALONE_METADATA_FIELD) {
            fusedModules.forEach { module ->
                string(STANDALONE_FUSED_MODULE_FIELD, module)
            }
            splitId?.let { string(STANDALONE_SPLIT_ID_FIELD, it) }
        }
    }

    private fun captureIOException(block: () -> Unit): IOException {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (exception: IOException) {
            return exception
        }
    }

    private class ProtoWriter {

        private val output = ByteArrayOutputStream()

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val nested = ProtoWriter().apply(block).toByteArray()
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(nested.size.toLong())
            output.write(nested)
        }

        fun string(fieldNumber: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun varint(fieldNumber: Int, value: Int) {
            varint(fieldNumber, value.toLong() and UINT32_MAX)
        }

        fun varint(fieldNumber: Int, value: Long) {
            tag(fieldNumber, WIRE_VARINT)
            rawVarint(value)
        }

        private fun tag(fieldNumber: Int, wireType: Int) {
            rawVarint(fieldTag(fieldNumber, wireType).toLong())
        }

        private fun rawVarint(value: Long) {
            var remaining = value
            while (remaining and -0x80L != 0L) {
                output.write(((remaining and 0x7F) or 0x80).toInt())
                remaining = remaining ushr 7
            }
            output.write(remaining.toInt())
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private companion object {
        const val WIRE_VARINT = 0
        const val WIRE_LENGTH_DELIMITED = 2
        const val BUILD_VARIANT_FIELD = 1
        const val BUILD_BUNDLETOOL_FIELD = 2
        const val BUILD_ASSET_SLICE_SET_FIELD = 3
        const val BUILD_PACKAGE_NAME_FIELD = 4
        const val BUILD_DEFAULT_TARGETING_VALUE_FIELD = 7
        const val BUNDLETOOL_VERSION_FIELD = 2
        const val VARIANT_TARGETING_FIELD = 1
        const val VARIANT_APK_SET_FIELD = 2
        const val VARIANT_NUMBER_FIELD = 3
        const val APK_SET_METADATA_FIELD = 1
        const val APK_SET_DESCRIPTION_FIELD = 2
        const val ASSET_SET_METADATA_FIELD = 1
        const val ASSET_SET_DESCRIPTION_FIELD = 2
        const val MODULE_NAME_FIELD = 1
        const val MODULE_ON_DEMAND_DEPRECATED_FIELD = 2
        const val MODULE_TARGETING_FIELD = 5
        const val MODULE_DELIVERY_FIELD = 6
        const val ASSET_MODULE_NAME_FIELD = 1
        const val ASSET_MODULE_DELIVERY_FIELD = 4
        const val APK_TARGETING_FIELD = 1
        const val APK_PATH_FIELD = 2
        const val APK_SPLIT_METADATA_FIELD = 3
        const val APK_STANDALONE_METADATA_FIELD = 4
        const val APK_INSTANT_METADATA_FIELD = 5
        const val APK_SYSTEM_METADATA_FIELD = 6
        const val APK_ASSET_SLICE_METADATA_FIELD = 7
        const val APK_APEX_METADATA_FIELD = 8
        const val SPLIT_ID_FIELD = 1
        const val SPLIT_MASTER_FIELD = 2
        const val STANDALONE_FUSED_MODULE_FIELD = 1
        const val STANDALONE_SPLIT_ID_FIELD = 3
        const val SDK_TARGETING_FIELD = 1
        const val VARIANT_DENSITY_TARGETING_FIELD = 3
        const val VARIANT_MULTI_ABI_TARGETING_FIELD = 4
        const val VARIANT_TEXTURE_TARGETING_FIELD = 5
        const val VARIANT_SDK_RUNTIME_TARGETING_FIELD = 6
        const val APK_ABI_TARGETING_FIELD = 1
        const val APK_LANGUAGE_TARGETING_FIELD = 3
        const val APK_DENSITY_TARGETING_FIELD = 4
        const val APK_TEXTURE_TARGETING_FIELD = 6
        const val MODULE_DEVICE_FEATURE_FIELD = 2
        const val TARGETING_VALUE_FIELD = 1
        const val TARGETING_ALTERNATIVE_FIELD = 2
        const val SDK_MIN_FIELD = 1
        const val WRAPPER_VALUE_FIELD = 1
        const val ABI_ALIAS_FIELD = 1
        const val MULTI_ABI_ABI_FIELD = 1
        const val DENSITY_ALIAS_FIELD = 1
        const val SDK_RUNTIME_REQUIRED_FIELD = 1
        const val DELIVERY_INSTALL_TIME = 1
        const val DELIVERY_ON_DEMAND = 2
        const val DELIVERY_FAST_FOLLOW = 3
        const val ABI_ARM64 = 3
        const val ABI_ARMEABI_V7A = 2
        const val ABI_X86 = 4
        const val DENSITY_XHDPI = 6
        const val DENSITY_XXHDPI = 7
        const val UINT32_MAX = 0xFFFF_FFFFL

        fun fieldTag(fieldNumber: Int, wireType: Int): Int =
            (fieldNumber shl 3) or wireType
    }
}
