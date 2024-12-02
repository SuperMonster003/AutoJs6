package org.autojs.autojs.runtime.api.augment.notice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.console.Console
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.autojs.autojs.runtime.api.augment.notice.Notice as AugmentableNotice

class Channel(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    companion object {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun create(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 2) { argList ->
            var (arg0, arg1) = argList

            if (arg0 is NativeObject) {
                arg1 = arg0
                arg0 = arg1.prop("id")
            }

            val id = AugmentableNotice.parseChannelId(scriptRuntime, arg0)
            val options = arg1 as? NativeObject ?: newNativeObject()

            val name: String? = run {
                val optName = options.prop("name")
                when {
                    !optName.isJsNullish() -> coerceString(optName)
                    else -> scriptRuntime.notice.config.defaultChannelName.takeUnless { it.isJsNullish() }
                }
            }

            val description: String? = run {
                val optDescription = options.prop("description")
                when {
                    !optDescription.isJsNullish() -> coerceString(optDescription)
                    else -> scriptRuntime.notice.config.defaultChannelDescription.takeUnless { it.isJsNullish() }
                }
            }

            val importance: Int? = run {
                val optImportance = options.prop("importance").takeUnless { it.isJsNullish() }
                    ?: scriptRuntime.notice.config.defaultImportanceForChannel
                when {
                    optImportance.isJsNullish() -> null
                    optImportance is Number -> coerceIntNumber(optImportance)
                    optImportance is String -> when (optImportance.lowercase()) {
                        "unspecified" -> NotificationManager.IMPORTANCE_UNSPECIFIED
                        "none" -> NotificationManager.IMPORTANCE_NONE
                        "min" -> NotificationManager.IMPORTANCE_MIN
                        "low" -> NotificationManager.IMPORTANCE_LOW
                        "default" -> NotificationManager.IMPORTANCE_DEFAULT
                        "high" -> NotificationManager.IMPORTANCE_HIGH
                        "max" -> NotificationManager.IMPORTANCE_MAX
                        else -> throw WrappedIllegalArgumentException("Unknown importance ${optImportance.jsBrief()} for notice.channel")
                    }
                    else -> throw WrappedIllegalArgumentException("Unknown importance ${optImportance.jsBrief()} for notice.channel")
                }
            }

            val enableVibration: Boolean? = run {
                val optEnableVibration = options.prop("enableVibration")
                when {
                    !optEnableVibration.isJsNullish() -> coerceBoolean(optEnableVibration)
                    else -> scriptRuntime.notice.config.defaultEnableVibrationForChannel.takeUnless { it.isJsNullish() }
                }
            }

            val vibrationPattern: LongArray? = run {
                val optVibrationPattern = options.prop("vibrationPattern")
                when {
                    !optVibrationPattern.isJsNullish() -> {
                        require(optVibrationPattern is NativeArray) {
                            "Property vibrationPattern ${optVibrationPattern.jsBrief()} must be a JavaScript Array for notice.channel"
                        }
                        optVibrationPattern.map { coerceLongNumber(it) }.toLongArray()
                    }
                    else -> scriptRuntime.notice.config.defaultVibrationPatternForChannel.takeUnless { it.isJsNullish() }
                }
            }

            val enableLights: Boolean? = run {
                val optEnableLights = options.prop("enableLights")
                when {
                    !optEnableLights.isJsNullish() -> coerceBoolean(optEnableLights)
                    else -> scriptRuntime.notice.config.defaultEnableLightsForChannel.takeUnless { it.isJsNullish() }
                }
            }

            val lightColor: Int? = run {
                val optLightColor = options.prop("lightColor")
                when {
                    !optLightColor.isJsNullish() -> S13n.color(arrayOf(optLightColor))
                    else -> scriptRuntime.notice.config.defaultLightColorForChannel.takeUnless { it.isJsNullish() }?.let {
                        S13n.color(arrayOf(it))
                    }
                }
            }

            val lockscreenVisibility: Int? = run {
                val optLockscreenVisibility = options.prop("lockscreenVisibility").takeUnless { it.isJsNullish() }
                    ?: scriptRuntime.notice.config.defaultLockscreenVisibilityForChannel
                when {
                    optLockscreenVisibility.isJsNullish() -> null
                    optLockscreenVisibility is Number -> coerceIntNumber(optLockscreenVisibility)
                    optLockscreenVisibility is String -> {
                        when (optLockscreenVisibility.lowercase()) {
                            "public" -> Notification.VISIBILITY_PUBLIC
                            "private" -> Notification.VISIBILITY_PRIVATE
                            "secret" -> Notification.VISIBILITY_SECRET
                            else -> throw WrappedIllegalArgumentException("Unknown importance ${optLockscreenVisibility.jsBrief()} for notice.channel")
                        }
                    }
                    else -> throw WrappedIllegalArgumentException("Unknown importance ${optLockscreenVisibility.jsBrief()} for notice.channel")
                }
            }

            if (scriptRuntime.notice.config.enableChannelInvalidModificationWarnings
                && contains(scriptRuntime, arrayOf(id))
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ) options.forEach { entry ->
                val (k, v) = entry
                when (k) {
                    "name", "description" -> {}
                    "importance" -> when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && get(scriptRuntime, arrayOf(id))?.hasUserSetImportance() == true -> {
                            warn(scriptRuntime, "Property \"$k\" for channel with ID \"$id\" will not take effect, as the user has altered importance on this channel")
                        }
                        importance != null && importance > (get(scriptRuntime, arrayOf(id))?.importance ?: Int.MIN_VALUE) -> {
                            warn(scriptRuntime, "Property \"$k\" for channel with ID \"$id\" will not take effect, as the new $k \"$v\" should not be higher than the current value \"${get(scriptRuntime, arrayOf(id))?.importance}\"")
                        }
                    }
                    else -> warn(scriptRuntime, "Property \"$k\" for channel with ID \"$id\" will not take effect, as \"$k\" of channel cannot be modified programmatically after the channel has been created and submitted to the notification manager")
                }
            }

            NotificationUtils.createNotificationChannel(
                id,
                name,
                description,
                importance,
                enableVibration,
                vibrationPattern,
                enableLights,
                lightColor,
                lockscreenVisibility,
            )

            return@ensureArgumentsAtMost id
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun createIfNeeded(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 2) { argList ->
            val (arg0, arg1) = argList
            val channelId: String = when (arg0) {
                is NativeObject -> AugmentableNotice.parseChannelId(scriptRuntime, arg0.prop("channelId"))
                else -> AugmentableNotice.parseChannelId(scriptRuntime, arg0)
            }
            if (!contains(scriptRuntime, arrayOf(channelId))) {
                val options = if (arg0 is NativeObject) arg0 else arg1
                create(scriptRuntime, arrayOf(channelId, options))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun remove(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { channelId ->
            when {
                channelId.isJsNullish() -> false
                !contains(scriptRuntime, arrayOf(channelId)) -> false
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> false
                else -> scriptRuntime.notice.service.deleteNotificationChannel(coerceString(channelId)).let { true }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun contains(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { channelId ->
            when {
                channelId.isJsNullish() -> false
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> false
                else -> getAllRhinoWithRuntime(scriptRuntime).any { it.id == coerceString(channelId) }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun get(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NotificationChannel? = ensureArgumentsOnlyOne(args) { channelId ->
            when {
                channelId.isJsNullish() -> null
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> null
                else -> scriptRuntime.notice.service.getNotificationChannel(coerceString(channelId))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            getAllRhinoWithRuntime(scriptRuntime).toNativeArray()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun getAllRhinoWithRuntime(scriptRuntime: ScriptRuntime): List<NotificationChannel> = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> emptyList()
            else -> scriptRuntime.notice.service.getNotificationChannels()
        }

        private fun warn(scriptRuntime: ScriptRuntime, message: String) {
            Console.warn(scriptRuntime, arrayOf(message))
        }

    }

}
