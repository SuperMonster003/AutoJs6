declare module "accessibility" {
  namespace accessibility {
    export type SelectorField = "text" | "desc" | "id" | "className" | "textContains" | "descContains";

    export interface SelectorDescriptor {
      readonly type: "autojs6.accessibility.selector";
      readonly version: 1;
      readonly text?: string;
      readonly desc?: string;
      readonly id?: string;
      readonly className?: string;
      readonly textContains?: string;
      readonly descContains?: string;
    }

    export interface UiNodeSnapshot {
      readonly text?: string;
      readonly desc?: string;
      readonly id?: string;
      readonly className?: string;
      readonly bounds: AutoJs6Node.Bounds;
      readonly clickable?: boolean;
      readonly enabled?: boolean;
    }

    export interface EnsureEnabledOptions extends AutoJs6Node.BridgeCallOptions {
      prompt?: boolean;
    }

    export interface AccessibilityModule {
      isEnabled(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      ensureEnabled(options?: EnsureEnabledOptions): Promise<boolean>;
      click(x: number, y: number, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      click(selector: SelectorDescriptor, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      back(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      home(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      recentApps(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      findByText(text: string, options?: AutoJs6Node.BridgeCallOptions): Promise<UiNodeSnapshot | null>;
      clickText(text: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      findOne(selector: SelectorDescriptor, options?: AutoJs6Node.BridgeCallOptions): Promise<UiNodeSnapshot | null>;
      findAll(selector: SelectorDescriptor, options?: AutoJs6Node.BridgeCallOptions): Promise<readonly UiNodeSnapshot[]>;
      text(value: string): SelectorDescriptor;
      desc(value: string): SelectorDescriptor;
      id(value: string): SelectorDescriptor;
      className(value: string): SelectorDescriptor;
      textContains(value: string): SelectorDescriptor;
      descContains(value: string): SelectorDescriptor;
    }
  }

  const accessibility: accessibility.AccessibilityModule;
  export = accessibility;
}
