package org.autojs.autojs.network.entity

import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs.util.UpdateUtils.isVersionIgnored
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/9/20.
 * Modified by SuperMonster003 as of May 29, 2022.
 */
class VersionInfo : ExtendedVersionInfo {
    var versionName: String? = null
        private set
    var versionCode = 0
        private set
    private var mSize: Long = -1
    private var mDownloadUrl: String? = null
    private var mAbi: String? = null
    private var mFileName: String? = null

    constructor(propertiesFileRawString: String) {
        val regexVersionName = "VERSION_NAME=.+".toRegex()
        val regexVersionCode = "VERSION_BUILD=.+".toRegex()
        for (string in propertiesFileRawString.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (string.matches(regexVersionName)) {
                versionName = string.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            } else if (string.matches(regexVersionCode)) {
                versionCode = string.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toInt()
            }
            if (versionName != null && versionCode > 0) {
                break
            }
        }
    }

    constructor(versionName: String, versionCode: Int) {
        this.versionName = versionName
        this.versionCode = versionCode
    }

    val isNewer: Boolean
        get() = versionCode > BuildConfig.VERSION_CODE
    val isNotIgnored: Boolean
        get() = !isVersionIgnored(this)

    override fun toString(): String {
        return if (mAbi == null) {
            "${str(R.string.text_version)}: $versionName ($versionCode)"
        } else "${str(R.string.text_version)}: $versionName ($versionCode) [$mAbi]"
    }

    fun toSummary(): String {
        return "$versionName ($versionCode)"
    }

    fun setFileName(fileName: String?) {
        mFileName = fileName
    }

    fun setSize(size: Long) {
        mSize = size
    }

    fun setDownloadUrl(downloadUrl: String?) {
        mDownloadUrl = downloadUrl
    }

    override fun getFileName(): String {
        return mFileName!!
    }

    fun setAbi(abi: String?) {
        mAbi = abi
    }

    override fun getSize(): Long {
        return mSize
    }

    override fun getDownloadUrl(): String {
        return mDownloadUrl!!
    }

    override fun getAbi(): String {
        return mAbi!!
    }

    companion object {
        fun parseSummary(summary: CharSequence): SimpleVersionInfo {
            val indexForVersionName = 0
            val indexForVersionCode = 1
            var versionName: String? = null
            var versionCode = -1
            val split = summary.toString().split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in split.indices) {
                if (i == indexForVersionName) {
                    versionName = split[i]
                } else if (i == indexForVersionCode) {
                    versionCode = split[i].replace("[()]".toRegex(), "").toInt()
                }
            }
            return SimpleVersionInfo(versionName, versionCode)
        }
    }
}