declare module "dialogs" {
  namespace dialogs {
    export interface DialogOptions extends AutoJs6Node.BridgeCallOptions {
      positive?: string;
      negative?: string;
      neutral?: string;
    }

    export type SelectItem = string | number | boolean;

    export interface DialogsModule {
      alert(title: string, message?: string, options?: DialogOptions): Promise<void>;
      confirm(title: string, message?: string, options?: DialogOptions): Promise<boolean>;
      input(title: string, prefill?: string, options?: DialogOptions): Promise<string | null>;
      prompt(title: string, prefill?: string, options?: DialogOptions): Promise<string | null>;
      select(title: string, items: readonly SelectItem[], options?: DialogOptions): Promise<number>;
    }
  }

  const dialogs: dialogs.DialogsModule;
  export = dialogs;
}
