// Type definitions for AutoJs6 internal module notice
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/notice/Notice.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/notice/Channel.kt
 */

declare namespace Internal {

    interface Notice {

        (title: string | null, content: string | null, options?: Notice.Options): number;
        (content: string | null, options?: Notice.Options): number;
        (options?: Notice.Options): number;
        (builder: androidx.core.app.NotificationCompat.Builder, options?: Notice.Options): number;

        channel: Notice.Channel;
        readonly builder: androidx.core.app.NotificationCompat.Builder;

        getBuilder(): androidx.core.app.NotificationCompat.Builder;

        isEnabled(): boolean;

        ensureEnabled(): void;

        launchSettings(): void;

        config(preset: Notice.ConfigPreset): void;

        cancel(id: number | null | undefined): void;

    }

}

declare namespace Notice {

    namespace Channel {

        namespace Options {

            /**
             * @enum
             * * NotificationManager.IMPORTANCE_UNSPECIFIED = -1000
             * * NotificationManager.IMPORTANCE_NONE = 0
             * * NotificationManager.IMPORTANCE_MIN = 1
             * * NotificationManager.IMPORTANCE_LOW = 2
             * * NotificationManager.IMPORTANCE_DEFAULT = 3
             * * NotificationManager.IMPORTANCE_HIGH = 4
             * * NotificationManager.IMPORTANCE_MAX = 5
             */
            type Importance = number | 'unspecified' | 'none' | 'min' | 'low' | 'default' | 'high' | 'max';

            /**
             * @enum
             * * NotificationManager.VISIBILITY_NO_OVERRIDE = -1000
             * * Notification.VISIBILITY_PUBLIC = 1
             * * Notification.VISIBILITY_PRIVATE = 0
             * * Notification.VISIBILITY_SECRET = -1
             */
            type LockscreenVisibility = number | 'public' | 'private' | 'secret';

        }

        interface Options {

            id?: string | number | null;
            channelId?: string | number | null;
            /**
             * @default string of R.string.default_script_notification_channel_name
             */
            name?: string;
            /**
             * @default string of R.string.default_script_notification_channel_description
             */
            description?: string;
            /**
             * Property importance only takes effect when create a channel at the first time.
             *
             * @default NotificationManager.IMPORTANCE_HIGH (4)
             */
            importance?: Options.Importance;
            enableVibration?: boolean;
            vibrationPattern?: number[];
            enableLights?: boolean;
            lightColor?: OmniColor;
            lockscreenVisibility?: Options.LockscreenVisibility;

        }

    }

    interface Channel {

        create(channelId: Options.Id | null | undefined, options?: Notice.Channel.Options): string;
        create(options?: Notice.Channel.Options): string;

        createIfNeeded(channelId: Options.Id | null | undefined, options?: Notice.Channel.Options): void;
        createIfNeeded(options?: Notice.Channel.Options): void;

        remove(channelId: Options.Id | null | undefined): boolean;

        contains(channelId: Options.Id | null | undefined): boolean;

        get(channelId: Options.Id | null | undefined): android.app.NotificationChannel | null;

        getAll(): android.app.NotificationChannel[];

    }

    namespace Options {

        type Id = string | number;

        /**
         * @enum
         * * NotificationCompat.PRIORITY_DEFAULT = 0;
         * * NotificationCompat.PRIORITY_LOW = -1;
         * * NotificationCompat.PRIORITY_MIN = -2;
         * * NotificationCompat.PRIORITY_HIGH = 1;
         * * NotificationCompat.PRIORITY_MAX = 2;
         */
        type Priority = number | 'default' | 'low' | 'min' | 'high' | 'max';

        type AppendScriptName = boolean | 'auto' | 'title' | 'content' | 'bigContent';

    }

    interface Options {

        channelId?: Notice.Options.Id | null;

        /**
         * Will override argument title if specified.
         *
         * @default string of R.string.default_script_notification_title (only when both title and content are null)
         */
        title?: string | null;
        /**
         * Will override argument content if specified.
         *
         * @default string of R.string.default_script_notification_content (only when both title and content are null)
         */
        content?: string | null;
        /**
         * @default null
         */
        bigContent?: string | null;
        /**
         * @default false
         */
        appendScriptName?: Options.AppendScriptName;
        /**
         * @default null
         */
        intent?: Intent.Common | Intent | Intent.ShortForm.Activity | Intent.UriString | null;
        /**
         * @default varies by (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
         */
        notificationId?: number;
        /**
         * @default false
         */
        autoCancel?: boolean;
        /**
         * @default false
         */
        isSilent?: boolean;
        /**
         * @default NotificationCompat.PRIORITY_HIGH (1)
         */
        priority?: Options.Priority;
    }

    interface ConfigPreset {

        useScriptNameAsDefaultChannelId?: boolean | null;
        useDynamicDefaultNotificationId?: boolean | null;
        enableChannelInvalidModificationWarnings?: boolean | null;

        defaultTitle?: string | null;
        defaultContent?: string | null;
        defaultBigContent?: string | null;
        defaultAppendScriptName?: Options.AppendScriptName | null;
        defaultAutoCancel?: boolean | null;
        defaultIsSilent?: boolean | null;
        defaultPriority?: Options.Priority | null;

        defaultChannelId?: string | null;

        defaultChannelName?: string | null;
        defaultChannelDescription?: string | null;

        /**
         * @default NotificationManager.IMPORTANCE_HIGH (4)
         */
        defaultImportanceForChannel?: Channel.Options.Importance | null;
        defaultEnableVibrationForChannel?: boolean | null;
        defaultVibrationPatternForChannel?: number[] | null;
        defaultEnableLightsForChannel?: boolean | null;
        defaultLightColorForChannel?: OmniColor | null;
        /**
         * @default Notification.VISIBILITY_PUBLIC (1)
         */
        defaultLockscreenVisibilityForChannel?: Channel.Options.LockscreenVisibility | null;

    }

    interface Config extends Required<ConfigPreset> {
        defaultTitle: string | null;
        defaultContent: string | null;
        defaultBigContent: string | null;
        defaultAppendScriptName: Options.AppendScriptName | null;
        defaultAutoCancel: boolean | null;
        defaultIsSilent: boolean | null;
        defaultPriority: Options.Priority | null;
        defaultChannelName: string | null;
        defaultChannelDescription: string | null;
        defaultImportanceForChannel: Channel.Options.Importance | null;
        defaultEnableVibrationForChannel: boolean | null;
        defaultVibrationPatternForChannel: number[] | null;
        defaultEnableLightsForChannel: boolean | null;
        defaultLightColorForChannel: OmniColor | null;
        defaultLockscreenVisibilityForChannel: Channel.Options.LockscreenVisibility | null;
    }

}
