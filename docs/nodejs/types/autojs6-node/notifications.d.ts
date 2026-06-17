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

    export interface NotificationsModule {
      notify(options: NotifyOptions, callOptions?: AutoJs6Node.BridgeCallOptions): Promise<NotificationId>;
      cancel(id: NotificationId, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      cancelAll(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }
  }

  const notifications: notifications.NotificationsModule;
  export = notifications;
}
