package org.autojs.autojs.ui.settings.codegen

import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogConfiguration
import org.autojs.autojs6.R

/** Change this source-only switch for distribution smoke tests, then rebuild the debug APK.
 * Normal debug builds use production, and no preference or Intent can change the trusted key.
 * zh-CN: 联调测试分发时显式修改此源码开关并重建 debug APK; 默认使用正式源, 偏好与 Intent 均不能改变信任公钥. */
internal object CodegenCatalogBuildConfiguration {
    private const val USE_DISTRIBUTION_TEST_CATALOG = false

    internal val DISTRIBUTION_TEST = CodegenCatalogResources(
        ModelCatalogConfiguration(
            expectedRepository = "SuperMonster003/AutoJs6-Codegen-Distribution-Test",
            expectedKeyId = "p256-fc44588d1cb789dc",
            publicKeySha256 = "fc44588d1cb789dc3ac0002821b8f698b663b44529606a2937910c0a80ccb7c3",
            cacheDirectoryName = "catalog-debug-distribution-test",
        ),
        R.raw.codegen_model_catalog_debug_test_public_key_v1,
        R.raw.codegen_model_catalog_debug_test_v1,
    )

    fun resources(): CodegenCatalogResources =
        if (USE_DISTRIBUTION_TEST_CATALOG) DISTRIBUTION_TEST else CodegenCatalogResources.PRODUCTION
}
