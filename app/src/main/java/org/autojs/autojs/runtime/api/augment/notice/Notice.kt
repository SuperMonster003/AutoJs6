package org.autojs.autojs.runtime.api.augment.notice

import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationManagerCompat
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_object_create
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty1

// @Caution by SuperMonster003 on May 6, 2023.
//  ! On device running with Android 7.x, importing NotificationManagerCompat will cause an exception:
//  ! java.lang.ClassNotFoundException: Didn't find class "android.app.NotificationChannel" on path: DexPathList ...
//  ! zh-CN:
//  ! 在安卓 7.x 设备上, 导入 NotificationManagerCompat 类将导致异常:
//  ! java.lang.ClassNotFoundException: 无法找到 "android.app.NotificationChannel" 类在此路径: DexPathList ...
//  !
//  # import androidx.core.app.NotificationManagerCompat

@Suppress("unused", "UNUSED_PARAMETER")
class Notice(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    @Suppress("UnnecessaryVariable")
    override fun invoke(vararg args: Any?): Int = ensureArgumentsAtMost(args, 3) { argList ->
        val (arg0, arg1, arg2) = argList

        when {
            arg0 is Builder -> {

                // @Signature notice(builder: androidx.core.app.NotificationCompat.Builder, options?: Notice.Options): void;

                val builder = arg0

                require(argList.size <= 2) {
                    "Arguments length ${argList.size} shouldn't be greater than 2 for notice with builder"
                }
                require(argList.size <= 1 && (arg1.isJsNullish() || arg1 is NativeObject)) {
                    "Argument options ${arg1.jsBrief()} must be a JavaScript Object for notice with builder"
                }

                val options = when {
                    arg1 is NativeObject -> js_object_create(arg1)
                    else -> newNativeObject()
                }
                val channelId = parseChannelId(scriptRuntime, options.prop("channelId"))

                builder.setChannelId(channelId)
                Channel.createIfNeeded(scriptRuntime, arrayOf(channelId))

                appendScriptName(scriptRuntime, options)

                options.prop("title").takeUnless { it.isJsNullish() }
                    ?.let { builder.setContentTitle(coerceString(it)) }

                options.prop("content").takeUnless { it.isJsNullish() }
                    ?.let { builder.setContentText(coerceString(it)) }

                options.prop("bigContent").takeUnless { it.isJsNullish() }
                    ?.let { builder.setStyle(BigTextStyle().bigText(coerceString(it))) }

                val notificationId = parseNotificationId(scriptRuntime, options)

                NotificationUtils.notice(
                    builder,
                    notificationId,
                    parseAutoCancel(scriptRuntime, options),
                    parseIsSilent(scriptRuntime, options),
                    parseIntent(scriptRuntime, options),
                    parsePriority(options),
                )

                notificationId
            }
            arg0 is String? && arg1 !is String? -> {

                // @Signature notice(content: string, options?: Notice.Options): void;

                require(argList.size <= 2) {
                    "Arguments length ${argList.size} shouldn't be greater than 2 for notice with content"
                }
                require(argList.size <= 1 && (arg1.isJsNullish() || arg1 is NativeObject)) {
                    "Argument options ${arg1.jsBrief()} must be a JavaScript Object for notice with content"
                }

                val options = when {
                    arg1 is NativeObject -> js_object_create(arg1)
                    else -> newNativeObject()
                }
                if (options.prop("content").isJsNullish()) {
                    options.defineProp("content", arg0)
                }
                invoke(options)
            }
            arg0 is String? -> {

                // @Signature notice(title: string, content: string, options?: Notice.Options): void;

                require(argList.size <= 3) {
                    "Arguments length ${argList.size} shouldn't be greater than 3 for notice with title and content"
                }
                require(argList.size <= 2 && (arg2.isJsNullish() || arg2 is NativeObject)) {
                    "Argument options ${arg2.jsBrief()} must be a JavaScript Object for notice with title and content"
                }

                val title = arg0
                val content = arg1
                val options = when {
                    arg2 is NativeObject -> js_object_create(arg2)
                    else -> newNativeObject()
                }
                if (options.prop("title").isJsNullish() && !title.isJsNullish()) {
                    options.defineProp("title", title)
                }
                if (options.prop("content").isJsNullish() && !content.isJsNullish()) {
                    options.defineProp("content", content)
                }
                invoke(options)
            }
            else -> {

                // @Signature notice(options: Notice.Options): void;

                val options = when {
                    arg0 is NativeObject -> js_object_create(arg0)
                    else -> newNativeObject()
                }

                if (options.prop("title").isJsNullish()
                    && options.prop("content").isJsNullish()
                    && options.prop("bigContent").isJsNullish()
                ) {
                    options.defineProp("title", NotificationUtils.defaultTitle)
                    options.defineProp("content", NotificationUtils.defaultContent)
                }

                appendScriptName(scriptRuntime, options)

                val title: String? = run {
                    val optTitle = options.prop("title")
                    when {
                        !optTitle.isJsNullish() -> coerceString(optTitle)
                        else -> scriptRuntime.notice.config.defaultTitle.takeUnless { it.isJsNullish() }
                    }
                }

                val content: String? = run {
                    val optContent = options.prop("content")
                    when {
                        !optContent.isJsNullish() -> coerceString(optContent)
                        else -> scriptRuntime.notice.config.defaultContent.takeUnless { it.isJsNullish() }
                    }
                }

                val bigContent: String? = run {
                    val optBigContent = options.prop("bigContent")
                    when {
                        !optBigContent.isJsNullish() -> coerceString(optBigContent)
                        else -> scriptRuntime.notice.config.defaultBigContent.takeUnless { it.isJsNullish() }
                    }
                }

                val channelId = parseChannelId(scriptRuntime, options.prop("channelId"))

                Channel.createIfNeeded(scriptRuntime, arrayOf(channelId))

                val notificationId = parseNotificationId(scriptRuntime, options)

                NotificationUtils.notice(
                    channelId,
                    title,
                    content,
                    bigContent,
                    notificationId,
                    parseAutoCancel(scriptRuntime, options),
                    parseIsSilent(scriptRuntime, options),
                    parseIntent(scriptRuntime, options),
                    parsePriority(options),
                )

                notificationId
            }
        }
    }

    companion object {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isEnabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            NotificationUtils.isEnabled()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensureEnabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            undefined { NotificationUtils.ensureEnabled() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchSettings(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            undefined { NotificationUtils.launchSettings() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun config(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { config ->
            require(config is NativeObject) {
                "Argument config ${config.jsBrief()} must be a JavaScript Object for notice.config"
            }
            val rtConfig = scriptRuntime.notice.config
            val rtDefault = scriptRuntime.notice.default
            val rtConfigClass = rtConfig::class
            val rtDefaultClass = rtDefault::class
            config.forEach { entry ->
                val (keyArg, value) = entry
                val key = coerceString(keyArg)
                val configProperty = rtConfigClass.members.find { it.name == key }
                require(configProperty != null && Util.isPrimitive(arrayOf(configProperty.call(rtConfig)))) {
                    "Argument key \"${key.jsBrief()}\" is invalid for notice.config"
                }
                val valueToSet = when {
                    value.isJsNullish() -> {
                        /* To reset. */
                        rtDefaultClass.members.find { it.name == key }?.call(rtDefault).takeUnless { it.isJsNullish() }
                    }
                    else -> value
                }
                require(configProperty is KMutableProperty1<*, *>) {
                    "Property config.$key for notice must be mutable"
                }
                configProperty.setter.call(rtConfig, valueToSet)
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cancel(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { id ->
            if (!id.isJsNullish()) {
                Context.toNumber(id).takeUnless { it.isNaN() }?.let {
                    NotificationManagerCompat.from(globalContext).cancel(it.roundToInt())
                }
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getBuilder(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Builder = ensureArgumentsIsEmpty(args) {
            val builder = NotificationUtils.getSimpleBuilder()
            val options = newNativeObject().also { o ->
                o.defineProp("priority", scriptRuntime.notice.config.defaultPriority)
            }
            parsePriority(options)?.let { builder.setPriority(it) }
            builder
        }

        internal fun parseChannelId(scriptRuntime: ScriptRuntime, id: Any?): String = when {
            !id.isJsNullish() -> coerceString(id)
            scriptRuntime.notice.config.useScriptNameAsDefaultChannelId -> scriptRuntime.notice.config.defaultChannelId
            else -> scriptRuntime.notice.default.CHANNEL_ID
        }

        internal fun parseNotificationId(scriptRuntime: ScriptRuntime, options: NativeObject): Int {
            val notificationId = options.prop("notificationId")
            if (!notificationId.isJsNullish()) {
                val num = Context.toNumber(notificationId)
                if (!num.isNaN()) {
                    return num.roundToInt()
                }
            }
            return when {
                scriptRuntime.notice.config.useDynamicDefaultNotificationId -> {
                    NotificationUtils.defaultNotificationId
                }
                else -> scriptRuntime.notice.default.NOTIFICATION_ID
            }
        }

        @Suppress("HttpUrlsUsage")
        internal fun parseIntent(scriptRuntime: ScriptRuntime, options: NativeObject): Intent? {
            return when (val intent = options.prop("intent")) {
                is NativeObject -> App.intent(scriptRuntime, arrayOf(intent))
                is Intent -> intent
                is String -> when {
                    AppUtils.isActivityShortForm(intent) -> {
                        val prop = scriptRuntime.getProperty("class.$intent") as Class<*>?
                        checkNotNull(prop) { "Activity short form $intent not found" }
                        Intent(globalContext, prop).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    else -> {
                        val rexWebSiteWithoutProtocol = Regex("^(www\\.)?[a-z0-9]+(\\.[a-z]{2,}){1,3}(#?/?.*)?$")
                        val url = when {
                            rexWebSiteWithoutProtocol.matches(intent) -> "http://$intent"
                            else -> intent
                        }
                        val optIntent = newNativeObject().also { o ->
                            o.defineProp("data", url)
                        }
                        App.intent(scriptRuntime, arrayOf(optIntent))
                    }
                }
                else -> null
            }
        }

        internal fun parsePriority(options: NativeObject): Int? {
            val priority = options.prop("priority")
            return when {
                priority.isJsNullish() -> null
                priority is Number -> coerceIntNumber(priority)
                priority is String -> when (priority.lowercase()) {
                    "default" -> NotificationCompat.PRIORITY_DEFAULT
                    "low" -> NotificationCompat.PRIORITY_LOW
                    "min" -> NotificationCompat.PRIORITY_MIN
                    "high" -> NotificationCompat.PRIORITY_HIGH
                    "max" -> NotificationCompat.PRIORITY_MAX
                    else -> throw WrappedIllegalArgumentException("Unknown priority ${priority.jsBrief()} for notice")
                }
                else -> throw WrappedIllegalArgumentException("Unknown priority ${priority.jsBrief()} for notice")
            }
        }

        internal fun parseAutoCancel(scriptRuntime: ScriptRuntime, options: NativeObject): Boolean? {
            val autoCancel = options.prop("autoCancel")
            return when {
                !autoCancel.isJsNullish() -> coerceBoolean(autoCancel)
                else -> scriptRuntime.notice.config.defaultAutoCancel.takeUnless { it.isJsNullish() }
            }
        }

        internal fun parseIsSilent(scriptRuntime: ScriptRuntime, options: NativeObject): Boolean? {
            val isSilent = options.prop("isSilent")
            return when {
                !isSilent.isJsNullish() -> coerceBoolean(isSilent)
                else -> scriptRuntime.notice.config.defaultIsSilent.takeUnless { it.isJsNullish() }
            }
        }

        internal fun appendScriptName(scriptRuntime: ScriptRuntime, options: NativeObject) {
            var strategy = options.prop("appendScriptName")
            if (strategy == false) return
            if (strategy.isJsNullish()) {
                val def = scriptRuntime.notice.config.defaultAppendScriptName
                if (def.isJsNullish() || def == false) return
                strategy = def
            }
            val append = if (strategy == true) "auto" else coerceString(strategy)
            val scriptName = scriptRuntime.notice.config.defaultChannelId
            when (append.lowercase()) {
                "auto" -> appendScriptName(options, listOf("bigContent", "content", "title"), scriptName)
                "title" -> appendScriptName(options, "title", scriptName)
                "content" -> appendScriptName(options, "content", scriptName)
                "bigContent" -> appendScriptName(options, "bigContent", scriptName)
                else -> throw WrappedIllegalArgumentException("Unknown destination ${append.jsBrief()} for script name to append to")
            }
        }

        private fun appendScriptName(options: NativeObject, keys: List<String>, scriptName: String) {
            for (key in keys) {
                if (!options.prop(key).isJsNullish()) {
                    options.defineProp(key, "${coerceString(options.prop(key))} ($scriptName)")
                    break
                }
            }
        }

        private fun appendScriptName(options: NativeObject, key: String, scriptName: String) {
            val value = options.prop(key).takeUnless { it.isJsNullish() }?.let { "${coerceString(it)} ($scriptName)" } ?: scriptName
            options.defineProp(key, value)
        }

    }

}
