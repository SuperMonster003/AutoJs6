# Speech plugin protocol v1

## Purpose

The speech plugin (`AutoJs6-Plugin-Three-Macaw-Speech`, display name 3-Macaw Speech) gives AutoJs6 offline speech
recognition, voice activity detection, wake words and neural speech synthesis on top of sherpa-onnx. AutoJs6 offers it
as the global script object `speech` (alias `$speech`); the host keeps no recognition or synthesis of its own.

Division of work (plugin roadmap D3, D6, D20, D22):

- The plugin records the microphone itself, owns the models (download, import, storage, defaults) and the runtime.
  Audio never crosses Binder as a stream: microphone sessions report text events, files travel as descriptors.
- The host discovers and binds the plugin, checks every script argument and the contract ceilings first, opens the
  script's files itself (read-only inputs, staged outputs) and maps every failure to the script error codes.
- Synthesis for playback goes through the system TTS engine the plugin registers: `speech.speak` is the host `tts` with
  the engine fixed to the plugin package. The Binder contract adds synthesis to a file with progress and cancellation.

The contract lives in the host module `plugin-api/speech-api` (package `org.autojs.plugin.speech.api`); its literals
are frozen by `SpeechContractTest` and its transaction order by `SpeechAidlOrderTest`. The host client is
`core/plugin/speech` (`SpeechPluginHost`, `SpeechSessionProxy`, `SpeechErrorMapper`), the script object
`runtime/api/augment/speech` (`Speech`, `SpeechModels`, `SpeechSession`, `SpeechWakeSession`).

## Discovery and binding

| Item | Value |
| --- | --- |
| Capability service | action `org.autojs.plugin.SPEECH`, category `speech` |
| Info service | action `org.autojs.plugin.INFO`, category `three-macaw-speech` |
| Permission | `org.autojs.permission.PLUGIN` (signature) on every exported entry |
| Identity | id `three-macaw-speech`, engine `speech`, variant `default` |
| Package | `io.github.supermonster003.autojs6.plugin.three.macaw.speech` |

Any package that answers the capability query with engine `speech` and variant `default` may be selected; the Plugin
Center lists the official package once, under its INFO identity, and probes it through the capability service.

`PluginInfo.capabilities` carries:

| Key | Type | Meaning |
| --- | --- | --- |
| `requiresHostVersion` | Long | at least 5451 (`SpeechPluginIds.REQUIRED_HOST_VERSION_CODE`; temporary, see Status) |
| `speechContractVersion` | Int | 1 |
| `speechFeatures` | String array | the features the build implements, a subset of the table below |
| `speechCatalogVersion` | Int | version of the bundled model catalog, 0 before one is bundled |
| `speechMaxPcmSessions` | Int, optional | external PCM sessions per caller, at most 4 |
| `speechTtsEnginePackage` | String, optional | package of the system TTS engine (`speech.speak`) |

`getCapabilities` may add `speechEngineName`, `speechEngineVersion` (the runtime, `sherpa-onnx` / `1.13.8`) and
`speechMicrophoneGranted` (Boolean, the plugin holds `RECORD_AUDIO` right now).

| Feature | Unlocks |
| --- | --- |
| `asr` | `recognizeFile`, `openSession` with source `external` |
| `asrStreaming` | `openSession` with source `mic`, the `thenListen` rounds of wake sessions |
| `vad` | `detectVoice` |
| `kws` | `openWakeSession` |
| `tts` | `synthesizeToFile` |
| `ttsSystemEngine` | the system TTS engine of `speechTtsEnginePackage` |
| `models` | `installModel`, `importModel`, `removeModel`, `setDefaultModel`, `refreshCatalog` |

The host refuses a call whose feature the plugin does not advertise with `PLUGIN_INCOMPATIBLE` before any transaction;
`getInfo`, `getCapabilities`, `listModels` and `cancelOperation` need no feature.

Every binding to the capability service carries `BIND_AUTO_CREATE | BIND_ALLOW_ACTIVITY_STARTS |
BIND_INCLUDE_CAPABILITIES | BIND_IMPORTANT` (`BIND_INCLUDE_CAPABILITIES` from API 29, `BIND_ALLOW_ACTIVITY_STARTS` from
API 34; `AidlPluginHost.bindFlags`), so the plugin process inherits the host's while-in-use capabilities such as the
microphone (roadmap D22) and, while AutoJs6 is visible, may start an activity of its own to start its microphone
foreground service (plugin roadmap P3.1). Sessions and operations hold a dedicated binding for their whole life; short
calls use the pooled binding that idles out after 30 seconds. See "Background microphone" for what the two flags cover.

## Binder interfaces

```java
interface ISpeechPlugin {
    PluginInfo getInfo();
    Bundle getCapabilities();
    Bundle listModels(in Bundle query);
    int installModel(in Bundle request, ISpeechProgressCallback callback);
    void cancelOperation(int opId);
    Bundle removeModel(in Bundle request);
    Bundle recognizeFile(in ParcelFileDescriptor audio, in Bundle options);
    Bundle detectVoice(in ParcelFileDescriptor audio, in Bundle options);
    ISpeechSession openSession(in Bundle options, ISpeechSessionCallback callback);
    ISpeechSession openWakeSession(in Bundle options, ISpeechWakeCallback callback, ISpeechSessionCallback listenCallback);
    int synthesizeToFile(in Bundle request, in ParcelFileDescriptor output, ISpeechProgressCallback callback);
    int importModel(in ParcelFileDescriptor archive, in Bundle request, ISpeechProgressCallback callback);
    Bundle setDefaultModel(in Bundle request);
    int refreshCatalog(ISpeechProgressCallback callback);
}

interface ISpeechSession {
    void feed(in byte[] pcm16);
    void finish();
    void cancel();
    boolean isActive();
    Bundle snapshot();
    void finishRound(boolean discard);
}

oneway interface ISpeechSessionCallback {
    void onReady(in Bundle info);
    void onPartial(in Bundle result);
    void onFinal(in Bundle result);
    void onEndpoint();
    void onVoiceActivity(in Bundle activity);
    void onWarning(in Bundle warning);
    void onError(in Bundle error);
    void onEnd(in Bundle end);
}

oneway interface ISpeechWakeCallback {
    void onReady(in Bundle info);
    void onWake(in Bundle hit);
    void onWarning(in Bundle warning);
    void onError(in Bundle error);
    void onEnd(in Bundle end);
}

oneway interface ISpeechProgressCallback {
    void onProgress(int opId, long done, long total);
    void onCompleted(int opId, in Bundle result);
    void onFailed(int opId, in Bundle error);
}
```

Transaction codes follow the declaration order: a later version may only append methods and optional keys. Compared
with roadmap appendix C.1, version 1 adds `importModel`, `setDefaultModel`, `refreshCatalog`, the listen callback of
`openWakeSession` and `ISpeechSession.finishRound` (the `thenListen` rounds of roadmap D16).

### Common rules

- Every request Bundle carries `contractVersion` (Int, 1). Every answer Bundle and every completion of
  `ISpeechProgressCallback` carries `contractVersion` as well; the host refuses an answer of another version with
  `PLUGIN_INCOMPATIBLE`.
- Bundle answers never are null. A failed call answers `errorCode` and `errorMessage` only (no result keys); the host
  throws the carried error.
- Methods that answer a session or an operation id throw `IllegalArgumentException`, `IllegalStateException` or
  `SecurityException` whose message is `CODE: detail` (`SpeechErrorCodes.encode`); the host decodes it. A message
  without a known code becomes `INTERNAL`.
- Error messages are English and at most 4 KiB of UTF-8; the host truncates longer ones.
- Every request and event Bundle stays below 64 KiB (requests) and 256 KiB (events), measured as the parcel size.
- The Binder main path never loads a model (roadmap D30): `openSession` and `openWakeSession` return at once, the model
  loads on the plugin's own thread and `onReady` (or `onError` + `onEnd`) follows. File calls may load a model and block
  the calling Binder thread; the host calls them from its IO dispatcher.
- Unknown option keys are ignored by the plugin, so a newer host can add optional keys behind a feature.

### Per method

| Method | Thread and duration | Host timeout | Input ceilings | Output | Errors |
| --- | --- | --- | --- | --- | --- |
| `getInfo` | any Binder thread, immediate | 30 s | - | never null | - |
| `getCapabilities` | immediate | 30 s | - | Bundle or null | - |
| `listModels` | immediate (reads the catalog) | 30 s | query keys `kind`, `language`, `installed` | `models` (at most 512 model Bundles) | `INVALID_OPTIONS` |
| `installModel` | returns the operation id at once | none (downloads may take long) | `model`, `acceptLicense` | completion `model` | `MODEL_MISSING` (unknown id), `MODEL_LICENSE_NOT_ACCEPTED`, `MODEL_DOWNLOAD_FAILED`, `MODEL_CHECKSUM_MISMATCH`, `IO_FAILED`, `SESSION_CANCELLED` |
| `cancelOperation` | immediate, unknown ids ignored | 30 s | - | - | - |
| `removeModel` | immediate | 30 s | `model` | `removed` | `MODEL_MISSING`, `SESSION_BUSY` (model in use) |
| `recognizeFile` | blocks until done | 10 min | WAV, 200 MiB, 30 min; `hotwords` 200 | `text`, `segments`, `language`, `durationMillis`, `modelId`, `warnings` | `MODEL_MISSING`, `UNSUPPORTED_FORMAT`, `LIMIT_EXCEEDED`, `ENGINE_FAILED`, `IO_FAILED` |
| `detectVoice` | blocks until done | 10 min | as `recognizeFile` | `segments` (`startMillis`, `endMillis`), `durationMillis` | `UNSUPPORTED_FORMAT`, `LIMIT_EXCEEDED`, `ENGINE_FAILED`, `IO_FAILED` |
| `openSession` | returns at once | 30 s | `maxDurationMillis` 30 min, `hotwords` 200, `feed` 1 MiB | session, events | `SESSION_BUSY`, `PERMISSION_DENIED` (no microphone permission), `MODEL_MISSING` (also as `error` event) |
| `openWakeSession` | returns at once | 30 s | `keywords` 50 | session, events | as `openSession` |
| `synthesizeToFile` | returns the operation id at once | 10 min to completion | `text` 8000 characters; output 100 MiB | completion `sampleRate`, `durationMillis`, `modelId`, `speakerId`, `sizeBytes` | `MODEL_MISSING`, `INVALID_OPTIONS`, `LIMIT_EXCEEDED`, `ENGINE_FAILED`, `IO_FAILED`, `SESSION_CANCELLED` |
| `importModel` | returns the operation id at once | none | archive (`tar.bz2` or `zip`) | completion `model` | `INVALID_OPTIONS`, `UNSUPPORTED_FORMAT`, `MODEL_CHECKSUM_MISMATCH`, `IO_FAILED` |
| `setDefaultModel` | immediate | 30 s | `model`, `kind` | `model` | `MODEL_MISSING`, `INVALID_OPTIONS` |
| `refreshCatalog` | returns the operation id at once | 2 min to completion | - | completion `catalogVersion`, `updated` | `IO_FAILED`, `MODEL_CHECKSUM_MISMATCH` (signature) |

### Resource ownership

- Input descriptors (`recognizeFile`, `detectVoice`, `importModel`) are read-only descriptors of regular files the
  host opened from the script's own paths; the host owns and closes them after the call returns, the plugin duplicates
  what it keeps. The host checks existence, readability, the 200 MiB ceiling and the `RIFF` / `WAVE` header before the
  call, so the plugin never sees a path or a URI. Script PCM (`speech.recognize(int16Array)`) is wrapped into a
  temporary WAV in the host cache and deleted after the call.
- The output descriptor of `synthesizeToFile` is a read-write descriptor of an empty hidden file the host staged next
  to the target (`.speech-<uuid>.wav.part`). The plugin writes a complete WAV (PCM16 at the model's rate) and reports
  completion; the host checks the size and header and renames the file over the target. A failed or cancelled synthesis
  leaves no file at the script's path.
- A session belongs to the host that opened it. The plugin links to the death of the callback Binder and ends the
  session (releasing the microphone and its foreground service) when the host process dies; 3-Macaw Speech stopped
  recording within 0.32 to 0.48 seconds of a killed AutoJs6 (plugin roadmap P3.1). The host links to the death of the
  session Binder and turns the death of the plugin process into one `error` (`SESSION_CLOSED`) and `end` (`error`).
- The plugin has one microphone: at most one microphone session (dictation or wake) runs in the whole plugin, whichever
  host opened it, and a second one fails with `SESSION_BUSY` instead of waiting. External PCM sessions do not use the
  microphone and are counted per calling uid.
- An operation id belongs to the host that started it; the plugin completes or fails it exactly once. The host cancels
  the operation when the script exits or the host timeout elapses, and fails it with `SESSION_CLOSED` when the plugin
  process dies.

## Requests

Recognition and dictation options (`openSession`, `recognizeFile`, `thenListen`):

| Key | Type | Default | Notes |
| --- | --- | --- | --- |
| `model` | String | default model of the kind | id from `listModels` |
| `language` | String | - | hint (`zh`, `en`, `ja`, `ko`, `yue`); bilingual models ignore it |
| `source` | `mic`, `external` | `mic` | sessions only |
| `sampleRate` | Int | 16000 | external PCM is 16 kHz mono PCM16 LE only |
| `vad` | Boolean | true | VAD gating (microphone) or VAD segmentation (offline models) |
| `silenceEndMillis` | Long | 1200 | silence that ends an utterance |
| `maxDurationMillis` | Long | 300000 | at most 1800000; audio time |
| `timeoutMillis` | Long | 15000 | no speech or new text within it ends the session; audio time from the last speech or new text; 0 disables |
| `hotwords` | String array | - | transducer models only; others answer a `warning` |
| `punctuation` | Boolean | true | keep the punctuation the model emits |
| `displayName` | String | - | file name of the input, for diagnostics |

`timeoutMillis` and `maxDurationMillis` count the audio the plugin consumed, not the wall clock: a microphone session
runs in real time, an external session times out by the PCM fed to it (a paused feeder never times out), and a busy
recognizer does not shorten a session. Their ends are `end` with reason `timeout` / `maxDuration`.

Wake options (`openWakeSession`): `keywords` (ArrayList of Bundle with `keyword`, optional Float `boost` and
`threshold`; the plugin converts the text to model tokens, roadmap D18), `model`, `minIntervalMillis` (Long, default
1500) and `thenListen` (Bundle of dictation options; absent means hits only).

Voice activity (`detectVoice`): `model`, `vadThreshold` (Float), `minSpeechMillis`, `minSilenceMillis` (Long).

Synthesis (`synthesizeToFile`): `text`, `model`, `speaker` (String: a speaker id or name), `speed` (Double, 0.5 to
2.0), `format` (`wav`).

Models: `listModels` takes `kind`, `language`, `installed`; `installModel` takes `model` and `acceptLicense`;
`importModel` takes `model` (the id for a model outside the catalog), `kind`, `modelType`, `name`, `languages`,
`acceptLicense` and `displayName`; `removeModel` takes `model`; `setDefaultModel` takes `model` and `kind`.

## Results and events

A recognition result carries `text`, `segments` (ArrayList of Bundle: `startMillis`, `endMillis`, `text`, optional
`language` and `tags` with `emotion` / `event`), `language`, `durationMillis`, `modelId` and `warnings` (ArrayList of
Bundle with `errorCode` / `errorMessage`).

A model Bundle carries `id`, `kind` (`asr`, `tts`, `kws`, `vad`), `modelType`, `name`, `languages`, `sizeBytes`,
optional `installedBytes`, `installed`, `isDefault`, `recommended`, `version`, `license` (Bundle: `spdx`,
`commercialUse`, optional `noticeUrl`), optional `speakers` and `sampleRate`, and `source` (`catalog`, `imported`).

Session events, in order:

| Callback | Keys |
| --- | --- |
| `onReady` | `sessionId`, `modelId`; `round` on the listen callback |
| `onPartial` | `text` (the current utterance so far), `segmentIndex` |
| `onFinal` | `text`, `segmentIndex`, `startMillis`, `endMillis`, optional `language`, `tags` |
| `onEndpoint` | - (an utterance closed; after its `onFinal`, for streaming and offline models alike) |
| `onVoiceActivity` | `active` |
| `onWarning` | `errorCode`, `errorMessage`; the session goes on |
| `onError` | `errorCode`, `errorMessage`; `onEnd` follows with reason `error` |
| `onEnd` | `reason` (`stopped`, `timeout`, `maxDuration`, `cancelled`, `error`), `text` (the whole session); `round` on the listen callback |

Exactly one `onEnd` ends a session; nothing follows it. Offline models (SenseVoice) report `onPartial` per segment,
not per word, and close every segment with `onFinal` + `onEndpoint` like streaming models, so both kinds send the same
events. 3-Macaw Speech closes an utterance after `silenceEndMillis` of silence detected by its VAD or after 20 seconds
of speech. `snapshot` answers `sessionId`, `state` (`pending`, `active`, `ended`), `modelId` and the accumulated
`text`. `finish` flushes the recognizer (a last `onFinal` when text is pending) and ends with `stopped`; `cancel` ends
at once with `cancelled`; both are idempotent. `feed` on a microphone session throws `INVALID_OPTIONS`.

`onWarning` never ends a session. Besides `hotwords` a model ignores, it reports with `PERMISSION_DENIED` that the
microphone is silenced (Android hands the plugin all zero samples) or that the microphone foreground service was refused
(see "Background microphone"). The session keeps running: the audio comes back when the plugin holds the microphone
capability again, for instance when AutoJs6 returns to the foreground, and `timeoutMillis` may end the session in the
meantime. A missing `RECORD_AUDIO` permission is not a warning: `openSession` and `openWakeSession` fail with
`PERMISSION_DENIED` (see "Error codes").

Wake sessions report `onReady`, one `onWake` per hit (`keyword` as the script wrote it, `score`, `timestampMillis`) and
one `onEnd` (`reason`). sherpa-onnx 1.13.8 reports no confidence, so the `score` of 3-Macaw Speech is the trigger
threshold of the keyword that fired (its `threshold`, or the model default). With `thenListen` every hit carries `round`
(0 based) and is followed by one dictation round on the listen callback (`onReady` .. `onEnd` with the same `round`; the
events in between belong to the round of the last `onReady`), after which keyword spotting resumes. The wake and listen
callbacks are separate oneway Binders, so a round's `onReady` may arrive before its hit; the host matches them by
`round`. `finishRound(discard)` ends the current round (a last `onFinal` unless `discard`), `finish` ends the whole wake
session. A wake session, rounds included, is the plugin's one microphone session.

Progress: installs and imports report bytes, synthesis reports characters; `total` may be 0 when unknown. A cancelled
operation fails with `SESSION_CANCELLED`.

## Limits

| Limit | Value |
| --- | --- |
| Microphone sessions (dictation or wake) in the whole plugin | 1 |
| External PCM sessions per calling uid | 4 |
| Bytes of one `feed` | 1 MiB |
| Audio file | 200 MiB, 30 minutes |
| Session length (`maxDurationMillis`) | 30 minutes |
| Synthesis text | 8000 UTF-16 units |
| Synthesized file | 100 MiB |
| Keywords per wake session | 50 |
| Hotwords | 200 |
| Characters of a keyword, hotword, model id or speaker | 128 |
| Models of one `listModels` answer | 512 |
| Error message | 4 KiB UTF-8 |
| Request Bundle / event Bundle | 64 KiB / 256 KiB |

The host checks the session length, hotwords, keywords, text length, file size and `feed` size before any transaction
(`LIMIT_EXCEEDED`); the plugin enforces the same ceilings. `SpeechLimits.MAX_MIC_SESSIONS_PER_TOKEN` and
`MAX_PCM_SESSIONS_PER_TOKEN` keep their version 1 names; the plugin counts microphone sessions for the whole plugin and
external sessions per calling uid, as the table says.

## Error codes

`PLUGIN_UNAVAILABLE`, `PLUGIN_DISABLED`, `PLUGIN_INCOMPATIBLE` (host only), `PERMISSION_DENIED` (the plugin has no
microphone permission; as a `warning`, the microphone is silenced or its foreground service was refused),
`MODEL_MISSING`, `MODEL_DOWNLOAD_FAILED`, `MODEL_CHECKSUM_MISMATCH`, `MODEL_LICENSE_NOT_ACCEPTED`, `INVALID_OPTIONS`,
`UNSUPPORTED_FORMAT`, `LIMIT_EXCEEDED`, `SESSION_BUSY` (the plugin's microphone is held by another session of any
host, or the caller already runs 4 external sessions), `SESSION_CLOSED`, `SESSION_CANCELLED`, `ENGINE_FAILED`,
`IO_FAILED`, `TIMEOUT`, `INTERNAL`. The script sees the same codes on `speech.SpeechError` (roadmap D27).

The user grants the microphone permission in the plugin. Until its home screen does (plugin roadmap P5.1), 3-Macaw
Speech opens the system dialog from `MicrophonePermissionActivity`
(`io.github.supermonster003.autojs6.plugin.three.macaw.speech/.service.MicrophonePermissionActivity`, exported behind
`org.autojs.permission.PLUGIN`, which AutoJs6 holds and adb does not), and the message of `PERMISSION_DENIED` names that
component. A script opens it with `app.startActivity`, for instance:

```js
app.startActivity({
    packageName: 'io.github.supermonster003.autojs6.plugin.three.macaw.speech',
    className: 'io.github.supermonster003.autojs6.plugin.three.macaw.speech.service.MicrophonePermissionActivity',
});
```

The host has no helper of its own for it: the activity is an interim entry of one plugin, not part of contract version
1, and `speech.capabilities().microphoneGranted` already tells a script whether it is needed.

## Background microphone

The plugin records in its own process. Android lets a process record only while it holds the microphone capability:
when it is the top app, when it runs a foreground service of type `microphone`, or when a client that holds the
capability binds it with `BIND_INCLUDE_CAPABILITIES` (API 29+). A process without it reads silenced audio (all zero
samples; `AudioRecordingConfiguration.isClientSilenced()` from API 29). The host binds with
`BIND_INCLUDE_CAPABILITIES`, so the plugin records whenever AutoJs6 itself may: while AutoJs6 is visible, and while it
holds a microphone capability of its own (a bound accessibility service or a foreground service of type `microphone`).

Measured on 2026-10-16 with `test-apps/speech-provider-conformance/run_microphone_probe.py` (AutoJs6 without a
foreground service; `procState/capability` of host and plugin process in brackets):

| Device | AutoJs6 accessibility service | Foreground | Background (HOME) | Screen off |
| --- | --- | --- | --- | --- |
| Sony XQ-AT72, API 31 | bound | audio | audio (both `5/LCMN`) | audio (both `6/LCMN`) |
| OnePlus OPD2413, API 35 | off | audio (`2` / `3`, `LCMNFUA`) | silenced (both `15`, none) | silenced |
| Xiaomi Pad 23046RP50C, API 35 | enabled, never bound | audio (`2` / `3`, `LCMNFUA`) | silenced (both `15`, none) | silenced |

The plugin process always carried the host's capability, and nothing more. API 33 and 36 were not measured.

### The plugin's microphone foreground service

The inherited capability ends when AutoJs6 leaves the foreground. 3-Macaw Speech therefore starts a foreground service
of type `microphone` (with a recording notification) when a microphone session opens and stops it when the session
ends (plugin roadmap P3.1, appendix D Q5 (a)). Android lends that service the microphone only if it starts while the
plugin may record, that is while AutoJs6 is visible or holds the capability itself; from then on the session keeps
recording with AutoJs6 in the background or the screen off.

- Up to API 33 the plugin starts the service from its bound process.
- From API 34 Android refuses a `microphone` foreground service to a process that is only bound by the visible host
  (`SecurityException`; such a process is `BOUND_TOP`, not `TOP`). The plugin opens its transparent, unexported
  `MicrophoneStartActivity` instead, is visible for a moment and starts the service from there (directly, if the
  activity did not come up within 1.5 seconds). Android lets a process in the background open an activity only for a
  visible client that bound it with `BIND_ALLOW_ACTIVITY_STARTS`, so the host binds with that flag from API 34;
  without it the activity is blocked (`BAL_BLOCK`) and the session is silenced as soon as AutoJs6 leaves the foreground.
- A session opened while AutoJs6 is already in the background without a capability cannot gain one: the service starts
  without the microphone (API 33) or is refused (API 34+). The session opens all the same and reports `warning`
  `PERMISSION_DENIED`. Scripts that listen in the background therefore open the session while AutoJs6 is visible, or
  while the AutoJs6 accessibility service is bound.

Measured with `py .python/device_host_scripts.py listen <serial>` of the plugin repository (an AutoJs6 script opens
`speech.listen` while AutoJs6 is visible, a speaker plays speech, 10 seconds each in the foreground, in the background
(HOME) and with the screen off; `procState/capability` of the plugin process in brackets):

| Device | Foreground | Background (HOME) | Screen off |
| --- | --- | --- | --- |
| Sony G8441, API 28 | audio | audio | audio |
| Sony XQ-AT72, API 31 | audio | audio (`4/--MN`) | audio |
| Sony XQ-DQ72, API 33 | audio | audio (`4/--MN`) | audio |
| OnePlus OPD2413, API 35, binding without `BIND_ALLOW_ACTIVITY_STARTS` | audio | silenced (`4/---NFUA`), `warning` | silenced |
| OnePlus OPD2413, API 35 | audio | audio (`4/--MNFUA`) | audio (`4/--MNFUA`) |
| Xiaomi Pad 23046RP50C, API 35 | audio | audio (`4/--MNFUA`) | audio (`4/--MNFUA`) |

The two API 35 rows ran on 2026-10-16 with the host build 5456 that binds with `BIND_ALLOW_ACTIVITY_STARTS`: the
microphone foreground service was in the foreground in all three phases, no activity start of the plugin was blocked
and no `warning` arrived. Before the flag the activity was blocked (`BAL_BLOCK`, caller `BOUND_TOP`) on both. A session
the OnePlus opened 6 seconds after AutoJs6 went HOME (`device_host_scripts.py late`) still had its activity blocked and
its service refused; it reported `warning` `PERMISSION_DENIED` for the refusal and for the silenced audio and ran
silenced until `maxDuration`.

## Script path

`speech` (`runtime/api/augment/speech/Speech.kt`, with the Rhino-free rules in `runtime/api/speech/SpeechScriptArguments.kt`):

- `isAvailable()`, `capabilities()` (`{ contractVersion, features, catalogVersion, engineName, engineVersion,
  ttsEnginePackage, microphoneGranted, packageName, versionName, versionCode }`).
- `listen(options?)` answers a `SpeechSession` as soon as the plugin opened it (events `ready`, `partial`, `final`,
  `endpoint`, `vad`, `warning`, `error`, `end`; properties `id`, `modelId`, `source`, `round`, `startedAt`, `state`,
  `reason`, `isEnded`; methods `stop()`, `cancel()`, `feed(pcm)`, `isActive()`, `result()`). The session keeps the
  script alive until its `end`; the script's exit cancels it silently. `warning` carries `{ code, message }` and leaves
  the session running (`PERMISSION_DENIED` for silenced audio or a refused microphone foreground service).
- `wake(keywords, options?)` answers a `SpeechWakeSession` (`ready`, `wake`, `warning`, `error`, `end`; `stop()`,
  `isActive()`); `wake` carries `keyword`, `score` (the trigger threshold with 3-Macaw Speech) and `timestampMillis`.
  With `thenListen` every `wake` event carries `listen`, the `SpeechSession` of that round, whose `stop()` and
  `cancel()` end the round only.
- `recognize(source, options?)` / `recognizeAsync` take a WAV path or PCM (`ArrayBuffer`, `Int16Array`, `byte[]`, with
  `options.sampleRate`, default 16000); `detectVoice(source, options?)` likewise.
- `synthesize(text, path, options?)` / `synthesizeAsync` answer `{ path, sampleRate, durationMillis, modelId,
  speakerId, sizeBytes }`.
- `speak(text, options?)` is `tts(text, options)` with `engine` set to the plugin package, `voice` to
  `<model>/<speaker>` (roadmap D19) and `rate` to `speed`; `engine`, `voice` and `rate` themselves are refused.
- `models.list(filter?)`, `models.install(id, { acceptLicense, onProgress })` / `installAsync`,
  `models.importFile(path, options?)`, `models.remove(id)`, `models.setDefault(kind, id)`, `models.getDefault(kind)`,
  `models.refreshCatalog()`.

Paths resolve against the script's working directory; URIs are refused. Synchronous forms that may wait (recognition,
synthesis, downloads) refuse the UI thread. Recognized text is never written to the host log (roadmap appendix D Q8).
Without the plugin every call fails with `PLUGIN_UNAVAILABLE` and the localized message
`error_missing_required_plugin_for_module_label` for "3-Macaw Speech".

## Status

Contract version 1, the host client with `BIND_INCLUDE_CAPABILITIES` (and `BIND_ALLOW_ACTIVITY_STARTS` since plugin
roadmap P3.1), the Plugin Center registration (install wizard category Tools) and the script object `speech` are in
place (plugin roadmap P1). `SpeechContractTest`, `SpeechAidlOrderTest`, `AidlPluginHostBindFlagsTest` and the
`Speech*Test` JVM tests cover them, and `SpeechScriptApiTest` runs scripts on devices against the fake provider of
`test-apps/speech-provider-conformance`. 3-Macaw Speech answers the contract with the features `models`, `asr`,
`asrStreaming`, `vad` and `kws` since plugin roadmap P3 (not released yet); `tts` and `ttsSystemEngine` follow in P4,
and its `device_host_scripts.py` runs the host scripts against the real plugin.
`SpeechPluginIds.REQUIRED_HOST_VERSION_CODE` (5451) is the temporary value of plugin roadmap D28: the host build that
ships this module; plugin roadmap P6.4 raises it to the final 6.8.0 build.
