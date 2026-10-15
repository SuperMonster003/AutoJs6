package org.autojs.autojs.core.accessibility.codegen.dataset

import java.math.BigInteger
import java.security.MessageDigest

/**
 * The Kotlin form of `tools/codegen-model/dataset/split.py` `real_app_split` (roadmap D25): an app goes to
 * real-test when the first 64 bits of `sha256(namespace + "\0" + app)`, read as a fraction of 2^64 and
 * rounded like Python's true division, are below the threshold, and to real-train otherwise. Each app is
 * assigned on its own, so adding apps never moves one between the two sides.
 * zh-CN: `tools/codegen-model/dataset/split.py` 中 `real_app_split` (路线图 D25) 的 Kotlin 形式: 应用的
 * `sha256(namespace + "\0" + app)` 前 64 位按 Python 真除法的舍入换算为 2^64 的分数, 小于阈值时归入 real-test,
 * 否则归入 real-train. 逐个应用独立判定, 新增应用不会移动已有应用.
 */
internal object RealAppSplit {
    const val NAMESPACE = "autojs6.codegen.real-app-split.v1"
    const val TEST_FRACTION = 0.5
    const val REAL_TRAIN = "real-train"
    const val REAL_TEST = "real-test"

    fun split(app: String): String {
        require(app.isNotEmpty()) { "Real application package name must not be empty" }
        val digest = MessageDigest.getInstance("SHA-256").digest((NAMESPACE + "\u0000" + app).toByteArray(Charsets.UTF_8))
        // BigInteger.toDouble rounds half to even, matching Python's correctly rounded int / 2**64.
        // zh-CN: BigInteger.toDouble 按四舍六入五成双舍入, 与 Python 正确舍入的 int / 2**64 一致.
        val point = Math.scalb(BigInteger(1, digest.copyOf(8)).toDouble(), -64)
        return if (point < TEST_FRACTION) REAL_TEST else REAL_TRAIN
    }
}
