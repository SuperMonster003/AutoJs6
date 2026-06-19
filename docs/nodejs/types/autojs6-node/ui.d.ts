declare module "ui" {
  namespace ui {
    export type LayoutType =
      | "Text" | "Button" | "Input" | "Image" | "ScrollView" | "List" | "Switch" | "Checkbox" | "CheckBox" | "Progress" | "ProgressBar" | "Tabs" | "Tab"
      | "LinearLayout" | "Vertical" | "Horizontal" | "Column" | "Row"
      | "text" | "button" | "input" | "image" | "scrollView" | "list" | "switch" | "checkbox" | "checkBox" | "progress" | "progressBar" | "tabs" | "tab"
      | "linearLayout" | "vertical" | "horizontal" | "column" | "row";

    export interface Binding<T extends AutoJs6Node.JsonValue = AutoJs6Node.JsonValue> {
      readonly __autojs6UiBinding?: string;
    }

    export type BindableValue<T extends AutoJs6Node.JsonValue = AutoJs6Node.JsonValue> = T | Binding<T>;

    export interface UiState<T extends Record<string, AutoJs6Node.JsonValue> = Record<string, AutoJs6Node.JsonValue>> {
      get<K extends keyof T & string>(key: K): T[K] | undefined;
      set<K extends keyof T & string>(key: K, value: T[K]): this;
      update(values: Partial<T>): this;
      snapshot(): Readonly<T>;
      subscribe(callback: (event: { readonly keys: readonly string[]; readonly snapshot: Readonly<T> }) => void): () => void;
    }

    export interface LayoutNode {
      readonly type: LayoutType | string;
      readonly id?: string;
      readonly text?: BindableValue<string>;
      readonly hint?: BindableValue<string>;
      readonly value?: BindableValue;
      readonly checked?: BindableValue<boolean>;
      readonly enabled?: BindableValue<boolean>;
      readonly progress?: BindableValue<number>;
      readonly max?: BindableValue<number>;
      readonly selectedIndex?: BindableValue<number>;
      readonly items?: readonly AutoJs6Node.JsonValue[];
      readonly tabs?: readonly LayoutNode[];
      readonly content?: LayoutNode;
      readonly children?: readonly LayoutNode[];
      readonly [key: string]: AutoJs6Node.JsonValue | Binding | readonly AutoJs6Node.JsonValue[] | readonly LayoutNode[] | LayoutNode | undefined;
    }

    export type LayoutDescriptor = LayoutNode;

    export interface LayoutPatch {
      readonly id?: string;
      readonly [key: string]: AutoJs6Node.JsonValue | undefined;
    }

    export interface UiShowOptions extends AutoJs6Node.BridgeCallOptions {
      drainIntervalMs?: number;
    }

    export interface UiEventOptions extends AutoJs6Node.BridgeCallOptions {
      drainIntervalMs?: number;
    }

    export interface UiEvent {
      readonly type: string;
      readonly id?: string;
      readonly value?: AutoJs6Node.JsonValue;
      readonly text?: string;
      readonly checked?: boolean;
      readonly index?: number;
      readonly selectedIndex?: number;
      readonly tabId?: string;
      readonly handleId?: string;
      readonly [key: string]: AutoJs6Node.JsonValue | undefined;
    }

    export interface UiHandle {
      readonly id: string;
      on(event: string, callback: (event: UiEvent) => void, options?: UiEventOptions): () => void;
      update(patch: LayoutPatch, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      batchUpdate(patches: readonly LayoutPatch[], options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      close(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }

    export interface UiModule {
      showLayout(layoutJson: LayoutDescriptor, options?: UiShowOptions): Promise<UiHandle>;
      state<T extends Record<string, AutoJs6Node.JsonValue>>(initial: T): UiState<T>;
      bind<T extends Record<string, AutoJs6Node.JsonValue>, K extends keyof T & string>(state: UiState<T>, key: K): Binding<T[K]>;
    }
  }

  const ui: ui.UiModule;
  export = ui;
}
