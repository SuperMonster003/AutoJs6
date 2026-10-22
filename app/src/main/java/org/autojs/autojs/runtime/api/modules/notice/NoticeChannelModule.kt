package org.autojs.autojs.runtime.api.modules.notice

import android.app.NotificationChannel
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.notice.Channel
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

/**
 * `notice.channel` on the new script module layer (roadmap P3.2): `create`, `createIfNeeded`, `remove`, `contains`, `get`
 * and `getAll`, in the order of the current `augment/notice/Channel`, whose companions still create and read the channels
 * (to be moved out before P5.2); they are called with the checked channel id and options.
 *
 * Arguments follow the real signatures (T4, appendix C Q7). A channel id is a string or a number; nullish names the
 * default channel in `create` / `createIfNeeded` as before and is a TypeError in `remove` / `contains` / `get` (the current
 * module returned false / false / null). An options object together with a second argument, an options value of
 * another type and a vibration pattern that is not an array are TypeErrors; an unknown `importance` /
 * `lockscreenVisibility` word is a RangeError (the current module threw IllegalArgumentException for both), and the
 * documented `'no_override'` is accepted (the current module did not know it).
 *
 * zh-CN: 新脚本模块层上的 notice.channel (P3.2): create, createIfNeeded, remove, contains, get 与 getAll, 顺序同现有 augment/notice/Channel, 渠道仍由其伴生对象
 * 创建与读取 (P5.2 前迁出), 传入检查后的渠道 ID 与选项. 参数按真实签名 (T4, 附录 C Q7). 渠道 ID 为字符串或数值; nullish 在 create / createIfNeeded 中照旧
 * 表示默认渠道, 在 remove / contains / get 中为 TypeError (现有模块分别返回 false / false / null). 选项对象再带第二个参数, 选项值类型不符, 振动模式不是数组
 * 为 TypeError; 未知的 importance / lockscreenVisibility 单词为 RangeError (现有模块两者都抛 IllegalArgumentException), 并接受文档所列的 'no_override'
 * (现有模块不认识).
 */
@ScriptModule(name = "channel", parent = NoticeModule::class, dollarPrefix = false)
class NoticeChannelModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun create(channelId: Any? = null, options: Any? = null): String {
        val function = "notice.channel.create"
        if (channelId !is NativeObject) {
            val id = NoticeValues.channelId(function, "channelId", channelId)
            return Channel.create(runtime, arrayOf(id, NoticeValues.channelOptions(function, NoticeValues.objectOrNull(function, "options", options))))
        }
        if (!NoticeValues.isNullish(options)) throw ScriptArgs.noOverload(function, arrayOf(channelId, options), CREATE_SIGNATURES)
        val id = NoticeValues.channelId(function, "options.id", channelId.prop("id"))
        return Channel.create(runtime, arrayOf(id, NoticeValues.channelOptions(function, channelId)))
    }

    @ScriptFunction
    fun createIfNeeded(channelId: Any? = null, options: Any? = null) {
        val function = "notice.channel.createIfNeeded"
        if (channelId !is NativeObject) {
            val id = NoticeValues.channelId(function, "channelId", channelId)
            Channel.createIfNeeded(runtime, arrayOf(id, NoticeValues.channelOptions(function, NoticeValues.objectOrNull(function, "options", options))))
            return
        }
        if (!NoticeValues.isNullish(options)) throw ScriptArgs.noOverload(function, arrayOf(channelId, options), CREATE_IF_NEEDED_SIGNATURES)
        val id = NoticeValues.channelId(function, "options.channelId", channelId.prop("channelId"))
        Channel.createIfNeeded(runtime, arrayOf(id, NoticeValues.channelOptions(function, channelId)))
    }

    @ScriptFunction
    fun remove(channelId: Any?): Boolean = Channel.remove(runtime, arrayOf(id("notice.channel.remove", channelId)))

    @ScriptFunction
    fun contains(channelId: Any?): Boolean = Channel.contains(runtime, arrayOf(id("notice.channel.contains", channelId)))

    @ScriptFunction
    fun get(channelId: Any?): NotificationChannel? = Channel.get(runtime, arrayOf(id("notice.channel.get", channelId)))

    @ScriptFunction
    fun getAll(): NativeArray = Channel.getAll(runtime, emptyArray())

    private fun id(function: String, channelId: Any?): Any =
        NoticeValues.channelId(function, "channelId", channelId) ?: throw GlobalValues.typeError(function, "channelId", "string | number", channelId)

    private companion object {
        const val CREATE_SIGNATURES = "(channelId), (channelId, options), (options)"
        const val CREATE_IF_NEEDED_SIGNATURES = "(channelId, options?), (options)"
    }

}
