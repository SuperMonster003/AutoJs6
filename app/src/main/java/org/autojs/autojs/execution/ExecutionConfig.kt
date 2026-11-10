package org.autojs.autojs.execution

import android.os.Parcel
import android.os.Parcelable
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.project.ScriptConfig
import org.autojs.autojs.rhino.extension.IterableExtensions.toHashCode

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

    /**
     * Task-level virtual screen mode. FOLLOW falls back to the global setting / project feature;
     * ON and OFF override both. This is a launch option, not a script API: an existing script runs
     * unchanged on the virtual display (docs/virtual-screen/README.md).
     *
     * These properties deliberately stay outside the primary constructor: adding them there would
     * change the signature of the generated data-class `copy()`, which the `com.stardust` Rhino
     * alias still exposes to existing scripts (see stardust-alias-snapshot.json). Callers that use
     * `copy()` must carry these options over explicitly.
     *
     * zh-CN: 任务级虚拟屏模式. 这些属性刻意不放进主构造函数, 否则 data class 生成的 copy() 签名会变化,
     * 破坏 com.stardust Rhino 别名对存量脚本的兼容承诺; 使用 copy() 的调用方需显式带上这些选项.
     */
    var virtualScreenMode: Int = VIRTUAL_SCREEN_MODE_FOLLOW
    /** 0 = follow the physical screen. zh-CN: 0 表示跟随物理屏幕尺寸. */
    var virtualScreenWidth: Int = 0
    var virtualScreenHeight: Int = 0
    var virtualScreenDensity: Int = 0
    /** 0 = create a new display; a positive id reuses an existing virtual display. */
    var virtualScreenDisplayId: Int = 0

    private val mArguments = HashMap<String, Any>()

    /** Monotonic host launch measurements; neither script arguments nor parcel input. */
    @JvmField var nodeLaunchStartedAtMs = 0L
    @JvmField var nodeTypeScriptPrepareMs = 0L

    /** Host-owned execution marker; never accepted from script arguments or parceled as input. */
    var agentExecutionState: AgentScriptExecutionState? = null
        internal set

    /**
     * Host-owned: the ui-mode preview root that takes the place of the Activity for this execution
     * (3-Block Compose roadmap P10.2). Never accepted from script arguments or parceled as input.
     * zh-CN: 宿主持有: 替代 Activity 承载本次执行 `ui.layout` 内容的预览根容器 (3-Block Compose 路线图 P10.2); 不从脚本参数接受, 也不参与 Parcel.
     */
    var uiContentHost: UiContentHost? = null
        internal set

    val arguments: HashMap<String, Any>
        get() = mArguments

    constructor(parcel: Parcel) : this(
        workingDirectory = parcel.readString().orEmpty(),
        envPath = parcel.createStringArray().orEmpty(),
        intentFlags = parcel.readInt(),
        delay = parcel.readLong(),
        interval = parcel.readLong(),
        loopTimes = parcel.readInt(),
    ) {
        virtualScreenMode = parcel.readInt()
        virtualScreenWidth = parcel.readInt()
        virtualScreenHeight = parcel.readInt()
        virtualScreenDensity = parcel.readInt()
        virtualScreenDisplayId = parcel.readInt()
    }

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
        if (virtualScreenMode != other.virtualScreenMode) return false
        if (virtualScreenWidth != other.virtualScreenWidth) return false
        if (virtualScreenHeight != other.virtualScreenHeight) return false
        if (virtualScreenDensity != other.virtualScreenDensity) return false
        if (virtualScreenDisplayId != other.virtualScreenDisplayId) return false
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
        virtualScreenMode,
        virtualScreenWidth,
        virtualScreenHeight,
        virtualScreenDensity,
        virtualScreenDisplayId,
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
            writeInt(virtualScreenMode)
            writeInt(virtualScreenWidth)
            writeInt(virtualScreenHeight)
            writeInt(virtualScreenDensity)
            writeInt(virtualScreenDisplayId)
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

        const val VIRTUAL_SCREEN_MODE_FOLLOW = 0
        const val VIRTUAL_SCREEN_MODE_ON = 1
        const val VIRTUAL_SCREEN_MODE_OFF = 2

        /** project.json feature that turns the run mode on for a whole project. */
        const val FEATURE_VIRTUAL_SCREEN = "virtualScreen"

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
