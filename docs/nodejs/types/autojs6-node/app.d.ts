declare module "app" {
  namespace app {
    export type IntentExtraValue =
      | AutoJs6Node.JsonPrimitive
      | readonly string[]
      | readonly number[]
      | readonly boolean[];

    export interface IntentExtras {
      readonly [key: string]: IntentExtraValue;
    }

    export interface IntentRequest {
      action?: string;
      data?: string;
      type?: string;
      packageName?: string;
      package?: string;
      className?: string;
      component?: string;
      category?: string | readonly string[];
      categories?: string | readonly string[];
      flags?: number | readonly number[];
      extras?: IntentExtras;
      readonly root?: never;
      readonly shizuku?: never;
      readonly dual?: never;
    }

    export interface IntentDescriptor {
      readonly action?: string;
      readonly data?: string;
      readonly type?: string;
      readonly packageName?: string;
      readonly className?: string;
      readonly component?: string;
      readonly categories?: readonly string[];
      readonly flags?: number;
      readonly extras?: IntentExtras;
    }

    export type StartActivityRequest = IntentRequest;

    export interface AppModule {
      readonly packageName: string;
      readonly versionName: string;
      readonly versionCode: number;
      intent(request: IntentRequest): IntentDescriptor;
      launchPackage(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      launchApp(appName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      openAppSetting(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      startActivity(request: IntentRequest, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      sendBroadcast(request: IntentRequest | string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
    }
  }

  const app: app.AppModule;
  export = app;
}
