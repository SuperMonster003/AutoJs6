declare module "ui" {
  namespace ui {
    export type LayoutType = "Text" | "Button" | "Input" | "LinearLayout" | "Vertical" | "Horizontal" | "text" | "button" | "input" | "linearLayout" | "vertical" | "horizontal";

    export interface LayoutNode {
      readonly type: LayoutType | string;
      readonly id?: string;
      readonly text?: string;
      readonly hint?: string;
      readonly value?: AutoJs6Node.JsonValue;
      readonly children?: readonly LayoutNode[];
      readonly [key: string]: AutoJs6Node.JsonValue | readonly LayoutNode[] | undefined;
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
      readonly handleId?: string;
      readonly [key: string]: AutoJs6Node.JsonValue | undefined;
    }

    export interface UiHandle {
      readonly id: string;
      on(event: string, callback: (event: UiEvent) => void, options?: UiEventOptions): () => void;
      update(patch: LayoutPatch, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      close(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }

    export interface UiModule {
      showLayout(layoutJson: LayoutDescriptor, options?: UiShowOptions): Promise<UiHandle>;
    }
  }

  const ui: ui.UiModule;
  export = ui;
}
