declare module "notifications" {
  namespace notifications {
    export type NotificationId = number;
    export type NotificationPriority = "min" | "low" | "default" | "high" | "max" | -2 | -1 | 0 | 1 | 2;

    export interface NotifyOptions {
      id?: NotificationId;
      title?: string;
      text?: string;
      channel?: string;
      channelName?: string;
      ongoing?: boolean;
      priority?: NotificationPriority;
    }

    export interface NotificationSettingsOptions extends AutoJs6Node.BridgeCallOptions {
      dryRun?: boolean;
    }

    export interface NotificationPermissionStatus {
      readonly schema: "autojs6-node-notification-permission-status-v1";
      readonly sdkInt: number;
      readonly notificationsEnabled: boolean;
      readonly postNotificationsPermissionRequired: boolean;
      readonly postNotificationsPermissionGranted: boolean;
      readonly canOpenSettings: boolean;
    }

    export interface NotificationSettingsResult extends NotificationPermissionStatus {
      readonly opened: boolean;
      readonly dryRun: boolean;
      readonly action: string;
      readonly reason: string;
    }

    export interface NotificationsModule {
      notify(options: NotifyOptions, callOptions?: AutoJs6Node.BridgeCallOptions): Promise<NotificationId>;
      cancel(id: NotificationId, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      cancelAll(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      getPermissionStatus(options?: AutoJs6Node.BridgeCallOptions): Promise<NotificationPermissionStatus>;
      openSettings(options?: NotificationSettingsOptions): Promise<NotificationSettingsResult>;
      openNotificationSettings(options?: NotificationSettingsOptions): Promise<NotificationSettingsResult>;
    }
  }

  const notifications: notifications.NotificationsModule;
  export = notifications;
}
