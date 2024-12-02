package org.autojs.autojs.runtime.api

import android.app.NotificationManager
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.runtime.ScriptRuntime

class Notice(scriptRuntime: ScriptRuntime) {

    internal val config = Config(scriptRuntime)
    internal val default = Default
    internal val service by lazy { GlobalAppContext.get().getSystemService(NotificationManager::class.java) }

    companion object {

        internal object Default {

            val NOTIFICATION_ID = "script_notification".hashCode()
            const val CHANNEL_ID = "script_channel"
            const val USE_SCRIPT_NAME_AS_DEFAULT_CHANNEL_ID = true
            const val USE_DYNAMIC_DEFAULT_NOTIFICATION_ID = true
            const val ENABLE_CHANNEL_INVALID_MODIFICATION_WARNINGS = true

        }

        internal class Config(private val scriptRuntime: ScriptRuntime) {

            private var privateDefaultChannelId: String? = null

            var useScriptNameAsDefaultChannelId: Boolean = Default.USE_SCRIPT_NAME_AS_DEFAULT_CHANNEL_ID
            var useDynamicDefaultNotificationId: Boolean = Default.USE_DYNAMIC_DEFAULT_NOTIFICATION_ID
            var enableChannelInvalidModificationWarnings: Boolean = Default.ENABLE_CHANNEL_INVALID_MODIFICATION_WARNINGS

            var defaultTitle: String? = null
            var defaultContent: String? = null
            var defaultBigContent: String? = null

            /**
             * Type cases: <br>
             * - <1> - Boolean
             * - <2> - String ('auto' | 'title' | 'content' | 'bigContent')
             */
            var defaultAppendScriptName: Any? = null
            var defaultAutoCancel: Boolean? = null
            var defaultIsSilent: Boolean? = null

            /**
             * Type cases: <br>
             * - <1> - Int
             * - <2> - String ('default' | 'low' | 'min' | 'high' | 'max')
             */
            var defaultPriority: Any? = null

            var defaultChannelId
                get() = privateDefaultChannelId ?: scriptRuntime.engines.myEngine().source.fullName
                set(value) {
                    privateDefaultChannelId = value
                }
            var defaultChannelName: String? = null
            var defaultChannelDescription: String? = null

            /**
             * Type cases: <br>
             * - <1> - Int
             * - <2> - String ('unspecified' | 'none' | 'min' | 'low' | 'default' | 'high' | 'max')
             */
            var defaultImportanceForChannel: Any? = null
            var defaultEnableVibrationForChannel: Boolean? = null
            var defaultVibrationPatternForChannel: LongArray? = null
            var defaultEnableLightsForChannel: Boolean? = null

            /**
             * Type: OmniColor
             */
            var defaultLightColorForChannel: Any? = null

            /**
             * Type cases: <br>
             * - <1> - Int
             * - <2> - String ('no_override' | 'public' | 'private' | 'secret')
             */
            var defaultLockscreenVisibilityForChannel: Any? = null

        }

    }

}
