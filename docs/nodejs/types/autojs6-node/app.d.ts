declare module "app" {
  namespace app {
    export interface StartActivityRequest {
      action?: string;
      data?: string;
      type?: string;
      packageName?: string;
      className?: string;
      category?: string | readonly string[];
      flags?: number | readonly number[];
      extras?: AutoJs6Node.JsonObject;
    }

    export interface AppModule {
      readonly packageName: string;
      readonly versionName: string;
      readonly versionCode: number;
      launchPackage(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      launchApp(appName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      openAppSetting(packageName?: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      startActivity(request: StartActivityRequest, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }
  }

  const app: app.AppModule;
  export = app;
}
