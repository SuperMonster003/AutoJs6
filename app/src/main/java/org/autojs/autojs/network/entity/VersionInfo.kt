package org.autojs.autojs.network.entity

import android.util.Log
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs.util.UpdateUtils.isVersionIgnored
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/9/20.
 * Modified by SuperMonster003 as of May 29, 2022.
 */
class VersionInfo : ExtendedVersionInfo {

    var versionName: String = ""
        private set

    var versionCode = 0
        private set

    val isNewer: Boolean
        get() = versionCode > BuildConfig.VERSION_CODE

    val isNotIgnored: Boolean
        get() = !isVersionIgnored(this)

    private var mFileName: String = ""
    private var mSize: Long = -1
    private var mDownloadUrl: String? = null
    private var mAbi: String? = null

    constructor(propertiesFileRawString: String) {
        val versionNameKey = str(R.string.property_key_app_version_name)
        val versionCodeKey = str(R.string.property_key_app_version_code)

        val regexVersionName = "$versionNameKey(</\\w+>)?=.+".toRegex()
        val regexVersionCode = "$versionCodeKey(</\\w+>)?=.+".toRegex()

        for (string in propertiesFileRawString.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (versionName.isEmpty() && string.contains(regexVersionName)) {
                string.split("$versionNameKey(</\\w+>)?=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                    .replace("<.+?>".toRegex(), "")
                    .takeIf { it.isNotEmpty() }?.let { versionName = it }
                Log.d(TAG, "versionName: $versionName")
            }
            if (versionCode <= 0 && string.contains(regexVersionCode)) {
                string.split("$versionCodeKey(</\\w+>)?=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                    .replace("<.+?>".toRegex(), "")
                    .toInt()
                    .takeIf { it > 0 }?.let { versionCode = it }
                Log.d(TAG, "versionCode: $versionCode")
            }
            if (versionName.isNotEmpty() && versionCode > 0) {
                break
            }
        }
    }

    constructor(versionName: String, versionCode: Int) {
        this.versionName = versionName
        this.versionCode = versionCode
    }

    override fun toString() = when (mAbi) {
        null -> "${str(R.string.text_version)}: $versionName ($versionCode)"
        else -> "${str(R.string.text_version)}: $versionName ($versionCode) [$mAbi]"
    }

    fun toSummary() = "$versionName ($versionCode)"

    fun setFileName(fileName: String) {
        mFileName = fileName
    }

    fun setSize(size: Long) {
        mSize = size
    }

    fun setDownloadUrl(downloadUrl: String) {
        mDownloadUrl = downloadUrl
    }

    fun setAbi(abi: String) {
        mAbi = abi
    }

    override fun getFileName() = mFileName

    override fun getSize() = mSize

    override fun getDownloadUrl() = mDownloadUrl

    override fun getAbi() = mAbi

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

        private val TAG = VersionInfo::class.java.simpleName

    }

}