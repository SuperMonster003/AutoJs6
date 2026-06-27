# Editor Engine-Neutral Services

This document describes the services and models that may be shared by Native,
ACE, and future editor cores. Shared services define what the editor feature
does; each `ScriptEditorEngine` implementation defines how the feature touches
the concrete editing surface.

## Allowed Dependencies

Engine-neutral services may depend on:

- `ScriptEditorEngine`.
- `EditorViewportPolicy`.
- Neutral state models such as `EditorStateSnapshot`.
- Neutral breakpoint models such as `EditorBreakpoint` and
  `EditorBreakpointModelStore`.
- Neutral search models such as `EditorSearchOptions` and
  `EditorSearchResult`.
- Neutral loading models such as `EditorTextLoadPolicy` and
  `EditorTextLoadTarget`.
- File, draft, history, theme, and command services that do not expose a
  concrete editor widget.

## Forbidden Dependencies

Engine-neutral services and common UI must not depend on:

- `CodeEditor`.
- `CodeEditText`.
- Android `Editable` or `Layout`.
- `TextViewUndoRedo`.
- `WebView` or `AceCodeEditor`.
- ACE JavaScript globals such as `window.AutoJsAce`.
- Native IME workarounds such as `requestRectangleOnScreen`.

## Service Boundaries

| Capability | Owner | Allowed dependencies | Prohibited dependencies |
| --- | --- | --- | --- |
| File open | `EditorFileService`, `EditorFileReadPipeline` | `Uri`, file metadata, text chunks, charset and BOM models | Concrete editor widgets |
| File save | `EditorFileService`, `EditorTextProvider` | `ScriptEditorEngine.getTextForSaving`, save encoding models | `CodeEditText`, `WebView` |
| Draft restore/save | `EditorDraftService` | `ScriptEditorEngine`, draft key, neutral text | Native or ACE widget internals |
| Version history | `EditorVersionHistoryService` | `ScriptEditorEngine`, logical path, history repository | Native selection or ACE bridge internals |
| Run script text | `RunScriptTextProvider` | `EditorTextProvider`, temporary script files | Direct editor widget reads |
| Search/replace UI | `FindOrReplaceDialogBuilder`, `ScriptEditorEngine` search APIs | Search options and engine commands | Native `Layout`, ACE globals |
| Jump/navigation | `EditorCommandDispatcher` | `ScriptEditorEngine.jumpTo` and command APIs | `CodeEditText.setSelection` outside Native engine |
| Breakpoint model | `EditorBreakpointModelStore` | `EditorBreakpoint`, `ScriptEditorEngine` breakpoint APIs | Gutter drawing implementation details |
| Debug state | Debug toolbar and `ScriptEditorEngine` | Breakpoint model and debug line commands | Native gutter or ACE marker internals |
| Theme/font | `EditorThemeFontService` | Stored preferences and engine theme/font APIs | Direct paint objects or DOM operations in common code |
| Symbol/function keyboard | `EditorCommandDispatcher` | `insert` and `replaceSelection` commands | `FunctionsKeyboardHelper` holding `CodeEditText` or `Editable` |
| Read-only/dirty state | `ScriptEditorEngine` | Engine state methods and neutral menu state | Concrete undo manager implementation |
| Large file loading | `EditorTextLoadPolicy`, `EditorTextLoadTarget` | Size policy, progress, cancel, route-to-Native decision | Native loading gutter APIs outside Native engine |
| Viewport/IME | `EditorViewportPolicy` | IME visible state, bottom inset, policy events | Native cursor math in ACE policy |

## Future Editor Core Contract

A future editor core should be able to integrate by providing:

- A `ScriptEditorEngine` implementation.
- An `EditorViewportPolicy` implementation.
- Optional engine-specific completion, theme/font, and loading adapters.

It should automatically reuse file open/save, draft, history, run-text,
commands, breakpoint models, keyboard commands, and common release gates without
requiring access to `CodeEditor`, `CodeEditText`, `WebView`, or ACE-specific
bridge objects.

## Current Enforcement

`tools/editor-refactor/check-editor-boundaries.mjs` checks that this document
continues to name the public service boundaries and the forbidden dependencies.
When a shared service needs a concrete editor exception, prefer adding a
per-engine adapter before widening the common dependency surface.
