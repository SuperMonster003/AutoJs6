package org.autojs.autojs.project

import com.google.gson.annotations.SerializedName

data class ScriptConfig @JvmOverloads constructor(
        @SerializedName("useFeatures") var features: List<String> = emptyList(),
) {

    fun hasFeature(feature: String) = features.contains(feature)

    companion object {
        const val FEATURE_CONTINUATION = "continuation"
    }

}