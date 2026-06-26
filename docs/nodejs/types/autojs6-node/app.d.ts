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
      url?: string;
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

    export interface UriDescriptor {
      readonly schema: "autojs6-node-app-uri-descriptor-v1";
      readonly value: string;
      readonly scheme?: string;
      readonly authority?: string;
      readonly userInfo?: string;
      readonly host?: string;
      readonly port?: number;
      readonly path?: string;
      readonly query?: string;
      readonly fragment?: string;
    }

    export type StartActivityRequest = IntentRequest | string;

    export type EmailAddressList = string | readonly string[];

    export interface SendEmailRequest {
      readonly to?: EmailAddressList;
      readonly email?: EmailAddressList;
      readonly address?: EmailAddressList;
      readonly cc?: EmailAddressList;
      readonly bcc?: EmailAddressList;
      readonly subject?: string;
      readonly text?: string;
      readonly body?: string;
    }

    export interface AppModule {
      readonly packageName: string;
      readonly versionName: string;
      readonly versionCode: number;
      intent(request: IntentRequest): IntentDescriptor;
      parseUri(uri: string | null | undefined): UriDescriptor | null;
      getUriForFile(path: string): never;
      intentToShell(request: IntentRequest | IntentDescriptor): string;
      openUrl(url: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      openDualUrl(url: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      sendEmail(request: SendEmailRequest | EmailAddressList, options?: AutoJs6Node.BridgeCallOptions & Partial<SendEmailRequest>): Promise<void>;
      uninstall(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      uninstallDual(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      getPackageName(appName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<string | null>;
      getAppName(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<string | null>;
      isInstalled(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      isDualInstalled(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      viewFile(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      editFile(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      launch(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      launchDual(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      launchPackage(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      launchDualPackage(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      launchApp(appName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      launchDualApp(appName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      openAppSetting(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      openAppSetting(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      openAppSettings(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      openAppSettings(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      openDualAppSetting(options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      openDualAppSetting(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      openDualAppSettings(options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      openDualAppSettings(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      launchSettings(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      launchSettings(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      launchDualSettings(options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      launchDualSettings(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      launchAppDetailsSettings(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      launchAppDetailsSettings(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      launchDualAppDetailsSettings(options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      launchDualAppDetailsSettings(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      startActivity(request: IntentRequest, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      startActivity(url: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      startDualActivity(request: IntentRequest | string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      sendBroadcast(request: IntentRequest | string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      kill(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
      killDual(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;
    }
  }

  const app: app.AppModule;
  export = app;
}
