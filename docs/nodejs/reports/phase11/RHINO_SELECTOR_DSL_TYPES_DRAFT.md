# Rhino Selector DSL Types Draft

Status: draft only. This is not loaded by `docs/nodejs/types/autojs6-node/index.d.ts` and does not declare a runtime module yet. S11-16 should move the accepted shape into checked TypeScript declarations when the facade is implemented.

```ts
import accessibility = require("accessibility");

declare namespace rhinoSelectorCompat {
  export type SelectorOptions = AutoJs6Node.BridgeCallOptions;

  export type FindOneOptions = number | SelectorOptions;

  export interface RhinoSelectorHandle {
    readonly type: "autojs6.rhino.selector.handle";
    readonly version: 1;
    readonly descriptor: accessibility.SelectorDescriptor;

    text(value: string): RhinoSelectorHandle;
    desc(value: string): RhinoSelectorHandle;
    id(value: string): RhinoSelectorHandle;
    className(value: string): RhinoSelectorHandle;
    textContains(value: string): RhinoSelectorHandle;
    descContains(value: string): RhinoSelectorHandle;
    textMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    descMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    idMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    classNameMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    clickable(value?: boolean): RhinoSelectorHandle;
    enabled(value?: boolean): RhinoSelectorHandle;
    depth(value: number): RhinoSelectorHandle;
    boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
    boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
    boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
    boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;

    findOne(options?: FindOneOptions): Promise<accessibility.UiNodeSnapshot | null>;
    find(options?: SelectorOptions): Promise<readonly accessibility.UiNodeSnapshot[]>;
    findAll(options?: SelectorOptions): Promise<readonly accessibility.UiNodeSnapshot[]>;
    click(options?: SelectorOptions): Promise<boolean>;
    longClick(options?: SelectorOptions): Promise<boolean>;
    setText(text: string, options?: SelectorOptions): Promise<boolean>;
    scrollForward(options?: SelectorOptions): Promise<boolean>;
    scrollBackward(options?: SelectorOptions): Promise<boolean>;
    exists(options?: FindOneOptions): Promise<boolean>;
  }

  export interface SelectorDslModule {
    text(value: string): RhinoSelectorHandle;
    desc(value: string): RhinoSelectorHandle;
    id(value: string): RhinoSelectorHandle;
    className(value: string): RhinoSelectorHandle;
    textContains(value: string): RhinoSelectorHandle;
    descContains(value: string): RhinoSelectorHandle;
    textMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    descMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    idMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    classNameMatches(value: accessibility.SelectorPatternInput): RhinoSelectorHandle;
    clickable(value?: boolean): RhinoSelectorHandle;
    enabled(value?: boolean): RhinoSelectorHandle;
    depth(value: number): RhinoSelectorHandle;
    boundsInside(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
    boundsInside(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
    boundsContains(bounds: AutoJs6Node.Bounds): RhinoSelectorHandle;
    boundsContains(left: number, top: number, right: number, bottom: number): RhinoSelectorHandle;
    click(x: number, y: number, options?: SelectorOptions): Promise<boolean>;
    back(options?: SelectorOptions): Promise<boolean>;
    home(options?: SelectorOptions): Promise<boolean>;
    recentApps(options?: SelectorOptions): Promise<boolean>;
  }
}
```

Accepted async boundary:

| Type member | Sync or async | Reason |
| --- | --- | --- |
| builder functions | sync | Build frozen JSON descriptors only. |
| chain functions | sync | Return a new handle with merged descriptor fields. |
| `descriptor` | sync value | Plain frozen JSON descriptor; no live Android object. |
| `findOne`, `find`, `findAll` | Promise | Crosses accessibility bridge. |
| `click`, `longClick`, `setText`, `scrollForward`, `scrollBackward` | Promise | Crosses accessibility bridge and may need provider permission. |
| `back`, `home`, `recentApps`, coordinate `click` | Promise | Crosses accessibility bridge/global action provider. |
| `exists` | Promise | Compatibility wrapper over `findOne`; still crosses the bridge. |
