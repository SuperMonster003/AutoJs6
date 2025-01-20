package org.autojs.autojs.project

import com.google.gson.annotations.SerializedName
import org.autojs.autojs.annotation.SerializedNameCompatible
import org.autojs.autojs.annotation.SerializedNameCompatible.With

data class ScriptConfig @JvmOverloads constructor(
    @SerializedName("useFeatures")
    @field:SerializedNameCompatible(
        With(value = "useFeature"),
        With(value = "useFeatureList"),
        With(value = "features"),
        With(value = "feature"),
        With(value = "featureList"),
    )
    var features: List<String> = emptyList(),
) {

    fun hasFeature(feature: String) = features.contains(feature)

    companion object {
        const val FEATURE_CONTINUATION = "continuation"
    }

}