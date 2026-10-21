package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.APK_BUILD
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.ui.project.BuildConfigStore

/**
 * APK build profiles (docs/settings/ROADMAP.md P3.3, appendix A.1 `apk_build`): the named store
 * `build_apk_config_profiles` of `BuildConfigStore`, exported as a whole; the signing key stores are not part of it
 * (appendix C Q5). Names are unchanged (R1); the profiles stay org.json text written by `BuildConfigStore` (R2).
 *
 * zh-CN: APK 构建配置档 (ROADMAP P3.3, 附录 A.1 `apk_build`): `BuildConfigStore` 的命名存储, 整份导出; 签名密钥库不在其中 (附录 C
 * Q5). 名称不变 (R1); 配置档仍是 `BuildConfigStore` 写出的 org.json 文本 (R2).
 */
object ApkBuildKeys : SettingsDomain {

    /** The active build profile (`BuildConfigStore.getActiveProfileName`); missing or blank means the default profile. */
    @JvmField
    val ACTIVE_PROFILE = SettingKey.Str(
        name = "active_profile", category = APK_BUILD, default = SettingDefault.of(BuildConfigStore.PROFILE_DEFAULT_ID),
        store = NamedStores.BUILD_APK_CONFIG_PROFILES,
    )

    /** The build profiles as an org.json object text ({"profiles": {...}}). */
    @JvmField
    val PROFILES = SettingKey.Json(
        name = "profiles_json", category = APK_BUILD, jsonType = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None,
        store = NamedStores.BUILD_APK_CONFIG_PROFILES,
    )

    override val keys: List<PrefKey<*>> = listOf(ACTIVE_PROFILE, PROFILES)
}
