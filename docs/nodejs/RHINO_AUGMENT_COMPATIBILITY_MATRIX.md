# Rhino Augment Compatibility Matrix

This Phase 11A matrix is generated from the Rhino augment inventory, the checked-in status manifest, and the Node bridge/types/docs gap report. It is a module-level tracking document; API-level details remain in `build/reports/nodejs/rhino-augment-api-inventory.json` and `build/reports/nodejs/rhino-node-compat-gap.json`.

## Summary

- Rhino modules: 73
- Rhino API entries: 879
- Node bridge modules: 23
- Node type modules: 46
- Modules with bridge evidence: 24
- Modules with type evidence: 37
- Modules without Node surface evidence: 35

## Status Totals

- blocked_by_design: 22
- partial: 382
- supported: 132
- supported_async: 45
- unsupported: 371

## Module Matrix

| Order | Rhino Module | Key | Status | Node Surface | API Status | Risks | Next Step |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 0 | global | global | partial | autojs6:compat, toast | supported_async:4, unsupported:42 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 1 | global.legacy | legacy | unsupported | - | unsupported:3 | none | Design a Node facade or record a permanent non-goal. |
| 2 | global.is_nullish | isNullish | unsupported | - | unsupported:1 | none | Design a Node facade or record a permanent non-goal. |
| 3 | util | util | unsupported | util | unsupported:50 | provider_availability | Decide explicit mapping or mark blocked-by-design. |
| 4 | util.java | java | blocked_by_design | java | blocked_by_design:6 | none | Keep denial documented and covered by policy tests. |
| 5 | util.version | version | unsupported | - | unsupported:2 | none | Design a Node facade or record a permanent non-goal. |
| 6 | util.version_codes | versionCodes | unsupported | - | unsupported:5 | provider_availability | Design a Node facade or record a permanent non-goal. |
| 7 | util.inspect | inspect | supported | util | supported:1 | none | Review status manifest decision. |
| 8 | util.morse_code | morseCode | unsupported | - | unsupported:4 | none | Design a Node facade or record a permanent non-goal. |
| 9 | global.species | species | unsupported | - | unsupported:36 | none | Design a Node facade or record a permanent non-goal. |
| 10 | app | app | partial | app | partial:36, supported_async:4 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 11 | autojs | autojs | partial | engines | partial:19 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 12 | autojs.version | version | unsupported | - | unsupported:8 | none | Design a Node facade or record a permanent non-goal. |
| 13 | shell | shell | partial | shell | partial:27 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 14 | timers | timers | partial | timers | supported:7, unsupported:4 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 15 | automator.auto | auto | partial | accessibility | partial:24 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 16 | automator | automator | partial | accessibility | partial:27, supported_async:5 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 17 | selector | selector | partial | accessibility | partial:5 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 18 | events | events | unsupported | - | unsupported:2 | provider_availability | Design a Node facade or record a permanent non-goal. |
| 19 | events.keys | keys | unsupported | - | unsupported:13 | none | Design a Node facade or record a permanent non-goal. |
| 20 | canvas | Canvas | unsupported | - | unsupported:1 | lifecycle_resource, raw_android_object | Design a Node facade or record a permanent non-goal. |
| 21 | images | images | partial | image, images | partial:65, supported_async:12 | event_loop_semantics, lifecycle_resource | Implement explicit compat facade and promote mapped APIs one by one. |
| 22 | ocr | ocr | partial | ocr | partial:6, supported_async:1 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 23 | ocr.ocr_ml_kit | mlkit | unsupported | - | unsupported:3 | none | Design a Node facade or record a permanent non-goal. |
| 24 | ocr.ocr_paddle | paddle | unsupported | - | unsupported:3 | none | Design a Node facade or record a permanent non-goal. |
| 25 | ocr.ocr_rapid | rapid | unsupported | - | unsupported:3 | none | Design a Node facade or record a permanent non-goal. |
| 26 | barcode | barcode | partial | image | partial:5 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 27 | barcode.qr_code | qrcode | partial | image | partial:5 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 28 | threads | threads | blocked_by_design | - | blocked_by_design:6 | provider_availability | Keep denial documented and covered by policy tests. |
| 29 | ui | ui | partial | ui | partial:35 | lifecycle_resource, provider_availability, thread_model | Implement explicit compat facade and promote mapped APIs one by one. |
| 30 | colors | colors | partial | colors | blocked_by_design:3, partial:2, supported:58 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 31 | colors.color | Color | blocked_by_design | colors | blocked_by_design:1 | provider_availability | Keep denial documented and covered by policy tests. |
| 32 | tasks | tasks | partial | work_manager | partial:16 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 33 | dialogs | dialogs | partial | dialogs | partial:5, supported_async:5 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 34 | continuation | continuation | unsupported | - | unsupported:4 | provider_availability | Design a Node facade or record a permanent non-goal. |
| 35 | http | http | unsupported | - | unsupported:21 | provider_availability | Design a Node facade or record a permanent non-goal. |
| 36 | web | web | partial | fetch, websocket | partial:4 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 37 | web.web_socket | WebSocket | partial | websocket | partial:1 | lifecycle_resource, provider_availability, raw_android_object | Implement explicit compat facade and promote mapped APIs one by one. |
| 38 | s13n | s13n | unsupported | - | unsupported:6 | none | Design a Node facade or record a permanent non-goal. |
| 39 | converter | cvt | supported | converter, cvt | supported:1 | none | Review status manifest decision. |
| 40 | converter.bytes | bytes | supported | converter | supported:7 | none | Review status manifest decision. |
| 41 | formatter | fmt | supported | fmt, formatter | supported:1 | none | Review status manifest decision. |
| 42 | formatter.bytes | bytes | supported | formatter | supported:7 | none | Review status manifest decision. |
| 43 | console | console | partial | console | partial:35, supported:16, unsupported:3 | lifecycle_resource, provider_availability, thread_model | Implement explicit compat facade and promote mapped APIs one by one. |
| 44 | plugins | plugins | partial | plugins | partial:2 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 45 | jsox.arrayx | Arrayx | unsupported | - | unsupported:16 | none | Design a Node facade or record a permanent non-goal. |
| 46 | jsox.numberx | Numberx | unsupported | - | unsupported:14 | none | Design a Node facade or record a permanent non-goal. |
| 47 | jsox.mathx | Mathx | unsupported | - | unsupported:25 | none | Design a Node facade or record a permanent non-goal. |
| 48 | jsox | jsox | unsupported | - | unsupported:3 | none | Design a Node facade or record a permanent non-goal. |
| 49 | files | files | partial | autojs6:compat, files | blocked_by_design:1, partial:1, supported:2, unsupported:1 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 50 | cryptyo.crypto | crypto | unsupported | - | unsupported:1 | provider_availability | Design a Node facade or record a permanent non-goal. |
| 51 | automator.root_automator | RootAutomator | blocked_by_design | - | blocked_by_design:1 | lifecycle_resource, raw_android_object | Keep denial documented and covered by policy tests. |
| 52 | engines | engines | partial | engines | partial:4, supported_async:5 | event_loop_semantics, lifecycle_resource | Implement explicit compat facade and promote mapped APIs one by one. |
| 53 | floaty | floaty | unsupported | - | unsupported:8 | none | Design a Node facade or record a permanent non-goal. |
| 54 | storages | storages | partial | storage, storages | partial:1, supported_async:3, unsupported:2 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 55 | device | device | partial | clipboard, device | partial:19, supported_async:1 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 56 | recorder | recorder | unsupported | - | unsupported:1 | provider_availability | Design a Node facade or record a permanent non-goal. |
| 57 | toast | toast | partial | toast | partial:1, supported_async:1 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 58 | media | media | partial | media_projection | partial:1 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 59 | sensors | sensors | partial | sensors | partial:1 | provider_availability | Implement explicit compat facade and promote mapped APIs one by one. |
| 60 | base64 | base64 | supported | base64 | supported:3 | none | Review status manifest decision. |
| 61 | notice | notice | partial | notifications | partial:6, supported_async:1 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 62 | notice.channel | channel | unsupported | - | unsupported:1 | none | Design a Node facade or record a permanent non-goal. |
| 63 | shizuku | shizuku | unsupported | - | unsupported:7 | provider_availability | Design a Node facade or record a permanent non-goal. |
| 64 | opencc | opencc | unsupported | opencc | unsupported:34 | none | Decide explicit mapping or mark blocked-by-design. |
| 65 | mime | mime | supported | mime | supported:1 | provider_availability | Review status manifest decision. |
| 66 | sysprops | sysprops | unsupported | - | unsupported:5 | none | Design a Node facade or record a permanent non-goal. |
| 67 | sqlite | sqlite | supported_async | database, sqlite | supported_async:2 | none | Add compat examples and dual-engine tests. |
| 68 | zip | zip | unsupported | - | unsupported:6 | none | Design a Node facade or record a permanent non-goal. |
| 69 | nanoid | nanoid | supported | nanoid | supported:1 | none | Review status manifest decision. |
| 70 | pinyin | pinyin | partial | pinyin | supported:16, unsupported:1 | none | Implement explicit compat facade and promote mapped APIs one by one. |
| 71 | pinyin4j | pinyin4j | supported | pinyin4j | supported:2 | none | Review status manifest decision. |
| 72 | mediainfo | mediainfo | unsupported | - | unsupported:2 | none | Design a Node facade or record a permanent non-goal. |

## Module Details

### global

- Key: `global`
- Status: `partial`
- Node surface: autojs6:compat, toast
- Coverage: `no_node_surface`
- API status: supported_async:4, unsupported:42
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### global.legacy

- Key: `legacy`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:3
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### global.is_nullish

- Key: `isNullish`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:1
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### util

- Key: `util`
- Status: `unsupported`
- Node surface: util
- Coverage: `types_only`
- API status: unsupported:50
- Risks: provider_availability
- Next step: Decide explicit mapping or mark blocked-by-design.

### util.java

- Key: `java`
- Status: `blocked_by_design`
- Node surface: java
- Coverage: `bridge_only`
- API status: blocked_by_design:6
- Risks: none
- Next step: Keep denial documented and covered by policy tests.

### util.version

- Key: `version`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:2
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### util.version_codes

- Key: `versionCodes`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:5
- Risks: provider_availability
- Next step: Design a Node facade or record a permanent non-goal.

### util.inspect

- Key: `inspect`
- Status: `supported`
- Node surface: util
- Coverage: `no_node_surface`
- API status: supported:1
- Risks: none
- Next step: Review status manifest decision.

### util.morse_code

- Key: `morseCode`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:4
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### global.species

- Key: `species`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:36
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### app

- Key: `app`
- Status: `partial`
- Node surface: app
- Coverage: `bridge_and_types`
- API status: partial:36, supported_async:4
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### autojs

- Key: `autojs`
- Status: `partial`
- Node surface: engines
- Coverage: `bridge_and_types`
- API status: partial:19
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### autojs.version

- Key: `version`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:8
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### shell

- Key: `shell`
- Status: `partial`
- Node surface: shell
- Coverage: `bridge_and_types`
- API status: partial:27
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### timers

- Key: `timers`
- Status: `partial`
- Node surface: timers
- Coverage: `types_only`
- API status: supported:7, unsupported:4
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### automator.auto

- Key: `auto`
- Status: `partial`
- Node surface: accessibility
- Coverage: `bridge_and_types`
- API status: partial:24
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### automator

- Key: `automator`
- Status: `partial`
- Node surface: accessibility
- Coverage: `bridge_and_types`
- API status: partial:27, supported_async:5
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### selector

- Key: `selector`
- Status: `partial`
- Node surface: accessibility
- Coverage: `bridge_and_types`
- API status: partial:5
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### events

- Key: `events`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:2
- Risks: provider_availability
- Next step: Design a Node facade or record a permanent non-goal.

### events.keys

- Key: `keys`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:13
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### canvas

- Key: `Canvas`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:1
- Risks: lifecycle_resource, raw_android_object
- Next step: Design a Node facade or record a permanent non-goal.

### images

- Key: `images`
- Status: `partial`
- Node surface: image, images
- Coverage: `bridge_and_types`
- API status: partial:65, supported_async:12
- Risks: event_loop_semantics, lifecycle_resource
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### ocr

- Key: `ocr`
- Status: `partial`
- Node surface: ocr
- Coverage: `bridge_and_types`
- API status: partial:6, supported_async:1
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### ocr.ocr_ml_kit

- Key: `mlkit`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:3
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### ocr.ocr_paddle

- Key: `paddle`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:3
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### ocr.ocr_rapid

- Key: `rapid`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:3
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### barcode

- Key: `barcode`
- Status: `partial`
- Node surface: image
- Coverage: `bridge_and_types`
- API status: partial:5
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### barcode.qr_code

- Key: `qrcode`
- Status: `partial`
- Node surface: image
- Coverage: `bridge_and_types`
- API status: partial:5
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### threads

- Key: `threads`
- Status: `blocked_by_design`
- Node surface: -
- Coverage: `no_node_surface`
- API status: blocked_by_design:6
- Risks: provider_availability
- Next step: Keep denial documented and covered by policy tests.

### ui

- Key: `ui`
- Status: `partial`
- Node surface: ui
- Coverage: `bridge_and_types`
- API status: partial:35
- Risks: lifecycle_resource, provider_availability, thread_model
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### colors

- Key: `colors`
- Status: `partial`
- Node surface: colors
- Coverage: `types_only`
- API status: blocked_by_design:3, partial:2, supported:58
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### colors.color

- Key: `Color`
- Status: `blocked_by_design`
- Node surface: colors
- Coverage: `no_node_surface`
- API status: blocked_by_design:1
- Risks: provider_availability
- Next step: Keep denial documented and covered by policy tests.

### tasks

- Key: `tasks`
- Status: `partial`
- Node surface: work_manager
- Coverage: `bridge_and_types`
- API status: partial:16
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### dialogs

- Key: `dialogs`
- Status: `partial`
- Node surface: dialogs
- Coverage: `bridge_and_types`
- API status: partial:5, supported_async:5
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### continuation

- Key: `continuation`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:4
- Risks: provider_availability
- Next step: Design a Node facade or record a permanent non-goal.

### http

- Key: `http`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:21
- Risks: provider_availability
- Next step: Design a Node facade or record a permanent non-goal.

### web

- Key: `web`
- Status: `partial`
- Node surface: fetch, websocket
- Coverage: `bridge_and_types`
- API status: partial:4
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### web.web_socket

- Key: `WebSocket`
- Status: `partial`
- Node surface: websocket
- Coverage: `bridge_and_types`
- API status: partial:1
- Risks: lifecycle_resource, provider_availability, raw_android_object
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### s13n

- Key: `s13n`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:6
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### converter

- Key: `cvt`
- Status: `supported`
- Node surface: converter, cvt
- Coverage: `types_only`
- API status: supported:1
- Risks: none
- Next step: Review status manifest decision.

### converter.bytes

- Key: `bytes`
- Status: `supported`
- Node surface: converter
- Coverage: `no_node_surface`
- API status: supported:7
- Risks: none
- Next step: Review status manifest decision.

### formatter

- Key: `fmt`
- Status: `supported`
- Node surface: fmt, formatter
- Coverage: `types_only`
- API status: supported:1
- Risks: none
- Next step: Review status manifest decision.

### formatter.bytes

- Key: `bytes`
- Status: `supported`
- Node surface: formatter
- Coverage: `no_node_surface`
- API status: supported:7
- Risks: none
- Next step: Review status manifest decision.

### console

- Key: `console`
- Status: `partial`
- Node surface: console
- Coverage: `types_only`
- API status: partial:35, supported:16, unsupported:3
- Risks: lifecycle_resource, provider_availability, thread_model
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### plugins

- Key: `plugins`
- Status: `partial`
- Node surface: plugins
- Coverage: `types_only`
- API status: partial:2
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### jsox.arrayx

- Key: `Arrayx`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:16
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### jsox.numberx

- Key: `Numberx`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:14
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### jsox.mathx

- Key: `Mathx`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:25
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### jsox

- Key: `jsox`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:3
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### files

- Key: `files`
- Status: `partial`
- Node surface: autojs6:compat, files
- Coverage: `types_only`
- API status: blocked_by_design:1, partial:1, supported:2, unsupported:1
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### cryptyo.crypto

- Key: `crypto`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:1
- Risks: provider_availability
- Next step: Design a Node facade or record a permanent non-goal.

### automator.root_automator

- Key: `RootAutomator`
- Status: `blocked_by_design`
- Node surface: -
- Coverage: `no_node_surface`
- API status: blocked_by_design:1
- Risks: lifecycle_resource, raw_android_object
- Next step: Keep denial documented and covered by policy tests.

### engines

- Key: `engines`
- Status: `partial`
- Node surface: engines
- Coverage: `bridge_and_types`
- API status: partial:4, supported_async:5
- Risks: event_loop_semantics, lifecycle_resource
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### floaty

- Key: `floaty`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:8
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### storages

- Key: `storages`
- Status: `partial`
- Node surface: storage, storages
- Coverage: `bridge_and_types`
- API status: partial:1, supported_async:3, unsupported:2
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### device

- Key: `device`
- Status: `partial`
- Node surface: clipboard, device
- Coverage: `bridge_and_types`
- API status: partial:19, supported_async:1
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### recorder

- Key: `recorder`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:1
- Risks: provider_availability
- Next step: Design a Node facade or record a permanent non-goal.

### toast

- Key: `toast`
- Status: `partial`
- Node surface: toast
- Coverage: `bridge_and_types`
- API status: partial:1, supported_async:1
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### media

- Key: `media`
- Status: `partial`
- Node surface: media_projection
- Coverage: `bridge_and_types`
- API status: partial:1
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### sensors

- Key: `sensors`
- Status: `partial`
- Node surface: sensors
- Coverage: `bridge_and_types`
- API status: partial:1
- Risks: provider_availability
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### base64

- Key: `base64`
- Status: `supported`
- Node surface: base64
- Coverage: `types_only`
- API status: supported:3
- Risks: none
- Next step: Review status manifest decision.

### notice

- Key: `notice`
- Status: `partial`
- Node surface: notifications
- Coverage: `bridge_and_types`
- API status: partial:6, supported_async:1
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### notice.channel

- Key: `channel`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:1
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### shizuku

- Key: `shizuku`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:7
- Risks: provider_availability
- Next step: Design a Node facade or record a permanent non-goal.

### opencc

- Key: `opencc`
- Status: `unsupported`
- Node surface: opencc
- Coverage: `types_only`
- API status: unsupported:34
- Risks: none
- Next step: Decide explicit mapping or mark blocked-by-design.

### mime

- Key: `mime`
- Status: `supported`
- Node surface: mime
- Coverage: `types_only`
- API status: supported:1
- Risks: provider_availability
- Next step: Review status manifest decision.

### sysprops

- Key: `sysprops`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:5
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### sqlite

- Key: `sqlite`
- Status: `supported_async`
- Node surface: database, sqlite
- Coverage: `bridge_and_types`
- API status: supported_async:2
- Risks: none
- Next step: Add compat examples and dual-engine tests.

### zip

- Key: `zip`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:6
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.

### nanoid

- Key: `nanoid`
- Status: `supported`
- Node surface: nanoid
- Coverage: `types_only`
- API status: supported:1
- Risks: none
- Next step: Review status manifest decision.

### pinyin

- Key: `pinyin`
- Status: `partial`
- Node surface: pinyin
- Coverage: `types_only`
- API status: supported:16, unsupported:1
- Risks: none
- Next step: Implement explicit compat facade and promote mapped APIs one by one.

### pinyin4j

- Key: `pinyin4j`
- Status: `supported`
- Node surface: pinyin4j
- Coverage: `types_only`
- API status: supported:2
- Risks: none
- Next step: Review status manifest decision.

### mediainfo

- Key: `mediainfo`
- Status: `unsupported`
- Node surface: -
- Coverage: `no_node_surface`
- API status: unsupported:2
- Risks: none
- Next step: Design a Node facade or record a permanent non-goal.
