declare module "clipboard" {
  namespace clipboard {
    export interface ClipboardModule {
      getText(options?: AutoJs6Node.BridgeCallOptions): Promise<string>;
      setText(text: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      hasText(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
    }
  }

  const clipboard: clipboard.ClipboardModule;
  export = clipboard;
}
