# Editor Native-Only Audit

This document records editor APIs and implementation details that must stay
inside the Native editor path unless they are exposed through an engine-specific
interface. It exists to keep ACE free from old `CodeEditText` workarounds and
to make future editor cores easier to add.

## Classification

| Class | Meaning |
| --- | --- |
| A | Native-only implementation detail. |
| B | Engine-specific interface surface. Public command exists, implementation is per engine. |
| C | Engine-neutral service or model. |
| D | Removed from ACE path or prohibited in common editor host code. |

## Native-Only Inventory

| Item | Class | Owner | Boundary |
| --- | --- | --- | --- |
| `CodeEditor` | A/B | `NativeCodeEditorEngine` and native editor package | Native widget implementation only; common code talks to `ScriptEditorEngine`. |
| `CodeEditText` | A | Native editor package and `NativeCodeEditorEngine` | Prohibited in `EditorView`, common services, ACE, and keyboard command UI. |
| `TextViewUndoRedo` | A/B | `CodeEditor` | Undo implementation for Native only; public commands go through `ScriptEditorEngine`. |
| `HVScrollView` | A | Native layout | Native scroll container only; ACE owns WebView and ACE renderer scrolling. |
| `JavaScriptHighlighter` | A | Native editor package | Native syntax painting only; ACE uses ACE modes and themes. |
| `LayoutHelper` | A | Native editor package and `NativeCodeEditorEngine` | Native `TextView` layout math only. |
| Android `Layout` | A | Native editor implementation | Native cursor geometry only; never used for ACE viewport logic. |
| `Editable.append` | A | Native large text target | Native progressive loading only; ACE uses engine text loading or routes large files to Native. |
| `requestRectangleOnScreen` | A/D | `NativeCodeEditorEngine` | Native cursor visibility only; removed from ACE path. |
| `selectionStart` | A/B | Native engine implementation | Native selection lookup only; public selection behavior goes through engine commands. |
| `selectionEnd` | A/B | Native engine implementation | Native selection lookup only; public selection behavior goes through engine commands. |
| `setLoadingText` | A/B | Native engine implementation | Native loading presentation only; common code uses text load targets. |
| `setLoadingGutterDigits` | A | `CodeEditText` and `NativeCodeEditorEngine` | Native loading gutter only. |
| `setRedoUndoEnabled` | B | Per-engine implementation | Public load policy may request undo suppression, but each engine implements it independently. |
| `AutoCompletion(context, CodeEditText)` | A | `NativeCodeEditorEngine` | Native completion only; ACE completion is provided by ACE completers. |
| `FunctionsKeyboardHelper` direct edit-view assumptions | D | Common keyboard UI | Common keyboard UI must issue insert and replace commands, not hold `CodeEditText` or `Editable`. |

## Allowed Source Locations

- `requestRectangleOnScreen` is allowed only in `NativeCodeEditorEngine.kt`.
- `CodeEditText` is prohibited in `EditorView` and ACE code; it is allowed only
  in the native editor package and `NativeCodeEditorEngine.kt`.
- `LayoutHelper` is allowed only in native editor layout and native engine code.
- `TextViewUndoRedo` is allowed only in `CodeEditor.kt` and
  `TextViewUndoRedo.java`.
- `AutoCompletion(context, CodeEditText)` is allowed only through the Native
  engine factory path.
- `setLoadingGutterDigits`, `Editable.append`, and `beginBatchEdit()` are
  Native large text loading details.

## ACE Forbidden Paths

ACE must not use:

- Native bottom padding as an IME workaround.
- Android `TextView` layout or cursor geometry.
- `requestRectangleOnScreen`.
- Native progressive `Editable.append` loading.
- Native `AutoCompletion(context, CodeEditText)`.
- Direct common-host references to `CodeEditor` or `CodeEditText`.

ACE should receive neutral host events and handle them through:

- `ScriptEditorEngine`.
- `AceEditorViewportPolicy`.
- ACE bridge resize and scroll commands.
- `AceTextMirror` for fallback text preservation.

## Current Enforcement

`tools/editor-refactor/check-editor-boundaries.mjs` verifies the current source
locations for the most important Native-only APIs and checks this audit document
for the expected boundary language. If a later refactor needs a new Native-only
exception, update this document and the boundary script in the same commit.
