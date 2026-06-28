declare module "accessibility" {
  namespace accessibility {
    export type SelectorField =
      | "text"
      | "desc"
      | "id"
      | "className"
      | "textContains"
      | "descContains"
      | "textMatches"
      | "descMatches"
      | "idMatches"
      | "classNameMatches"
      | "clickable"
      | "enabled"
      | "scrollable"
      | "depth"
      | "boundsInside"
      | "boundsContains";

    export type SelectorPatternInput = string | RegExp | SelectorPattern;

    export interface SelectorPattern {
      readonly pattern: string;
      readonly flags?: string;
    }

    export interface SelectorDescriptor {
      readonly type: "autojs6.accessibility.selector";
      readonly version: 1;
      readonly text?: string;
      readonly desc?: string;
      readonly id?: string;
      readonly className?: string;
      readonly textContains?: string;
      readonly descContains?: string;
      readonly textMatches?: SelectorPattern;
      readonly descMatches?: SelectorPattern;
      readonly idMatches?: SelectorPattern;
      readonly classNameMatches?: SelectorPattern;
      readonly clickable?: boolean;
      readonly enabled?: boolean;
      readonly scrollable?: boolean;
      readonly depth?: number;
      readonly boundsInside?: AutoJs6Node.Bounds;
      readonly boundsContains?: AutoJs6Node.Bounds;
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
      longClick(selector: SelectorDescriptor, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      setText(selector: SelectorDescriptor, text: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      scrollForward(selector: SelectorDescriptor, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      scrollBackward(selector: SelectorDescriptor, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      text(value: string): SelectorDescriptor;
      desc(value: string): SelectorDescriptor;
      id(value: string): SelectorDescriptor;
      className(value: string): SelectorDescriptor;
      textContains(value: string): SelectorDescriptor;
      descContains(value: string): SelectorDescriptor;
      textMatches(value: SelectorPatternInput): SelectorDescriptor;
      descMatches(value: SelectorPatternInput): SelectorDescriptor;
      idMatches(value: SelectorPatternInput): SelectorDescriptor;
      classNameMatches(value: SelectorPatternInput): SelectorDescriptor;
      clickable(value?: boolean): SelectorDescriptor;
      enabled(value?: boolean): SelectorDescriptor;
      scrollable(value?: boolean): SelectorDescriptor;
      depth(value: number): SelectorDescriptor;
      boundsInside(bounds: AutoJs6Node.Bounds): SelectorDescriptor;
      boundsInside(left: number, top: number, right: number, bottom: number): SelectorDescriptor;
      boundsContains(bounds: AutoJs6Node.Bounds): SelectorDescriptor;
      boundsContains(left: number, top: number, right: number, bottom: number): SelectorDescriptor;
    }
  }

  const accessibility: accessibility.AccessibilityModule;
  export = accessibility;
}
