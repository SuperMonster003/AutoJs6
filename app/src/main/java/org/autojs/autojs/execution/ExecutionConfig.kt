package org.autojs.autojs.execution

import android.os.Parcel
import android.os.Parcelable
import org.autojs.autojs.extension.ArrayExtensions.toHashCode
import org.autojs.autojs.project.ScriptConfig

/**
 * Created by Stardust on Feb 1, 2017.
 */
data class ExecutionConfig(
    var workingDirectory: String = "",
    var envPath: Array<out String> = emptyArray(),
    var intentFlags: Int = 0,
    var delay: Long = 0,
    var interval: Long = 0,
    var loopTimes: Int = 1,
    var scriptConfig: ScriptConfig = ScriptConfig(),
) : Parcelable {

    private val mArguments = HashMap<String, Any>()

    val arguments: HashMap<String, Any>
        get() = mArguments

    constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(),
        parcel.createStringArray().orEmpty(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt()
    )

    fun setArgument(key: String, `object`: Any) {
        mArguments[key] = `object`
    }

    fun getArgument(key: String): Any? {
        return mArguments[key]
    }

    fun getArguments(): Any {
        return mArguments
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExecutionConfig

        if (workingDirectory != other.workingDirectory) return false
        if (!envPath.contentEquals(other.envPath)) return false
        if (intentFlags != other.intentFlags) return false
        if (delay != other.delay) return false
        if (interval != other.interval) return false
        if (loopTimes != other.loopTimes) return false
        if (mArguments != other.mArguments) return false

        return true
    }

    override fun hashCode(): Int = listOf(
        workingDirectory,
        envPath,
        intentFlags,
        delay,
        interval,
        loopTimes,
        mArguments,
    ).toHashCode()

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeString(workingDirectory)
            writeStringArray(envPath)
            writeInt(intentFlags)
            writeLong(delay)
            writeLong(interval)
            writeInt(loopTimes)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    // @Hint by SuperMonster003 on Jun 26, 2024.
    //  ! Compatible purpose only.
    //  ! zh-CN: 仅用于兼容.
    fun getPath() = envPath

    // @Hint by SuperMonster003 on Jun 26, 2024.
    //  ! Compatible purpose only.
    //  ! zh-CN: 仅用于兼容.
    fun setPath(path: Array<out String>) {
        envPath = path
    }

    companion object CREATOR : Parcelable.Creator<ExecutionConfig> {

        @Suppress("ConstPropertyName")
        const val tag = "execution.config"

        @JvmStatic
        fun getTag() = tag

        @JvmStatic
        val default: ExecutionConfig
            get() = ExecutionConfig()

        override fun createFromParcel(parcel: Parcel): ExecutionConfig {
            return ExecutionConfig(parcel)
        }

        override fun newArray(size: Int): Array<ExecutionConfig?> {
            return arrayOfNulls(size)
        }

    }

}
