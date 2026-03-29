package org.autojs.plugin.common.api

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class PluginInfo(
    var name: String,
    var description: String?,
    var instruction: String?,
    var author: String,
    var collaborators: Array<String>?,
    var versionName: String,
    var versionCode: Long,
    var versionDate: String?,
    var id: String?,
    var engine: String?,
    var variant: String?,
    var supportedAbis: Array<String>?,
    var capabilities: Bundle?,
) : Parcelable {
    constructor() : this(
        name = "",
        description = null,
        instruction = null,
        author = "",
        collaborators = null,
        versionName = "",
        versionCode = 0L,
        versionDate = null,
        id = null,
        engine = null,
        variant = null,
        supportedAbis = null,
        capabilities = null,
    )
}
