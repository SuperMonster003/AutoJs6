# AutoJs6 Node.js Device Matrix

Last reviewed: 2026-08-21.

Node.js acceptance requires devices representative of the plugin's published ABIs
and Android policy boundaries. The host no longer selects nodeFull/nodeMini builds
or compiles native Node per device.

## Minimum Release Sample

| Dimension | Minimum evidence |
| --- | --- |
| ABI | Every ABI published by the runtime plugin |
| Android | Oldest supported API plus a current API |
| OEM | At least one non-AOSP physical device |
| Plugin state | Installed/authorized success and missing/disabled denial |
| Capabilities | Core bridge plus one permission-sensitive provider |
| Lifecycle | Repeated plugin/Rhino switching and cancellation |

## Focused Profiles

The build configuration retains only **accessibility** and **screen_capture** device
profiles for permission-sensitive connected runs. General regression should select
instrumentation classes explicitly rather than relying on retired vendor/phase
profiles.

Before each run, record:

- adb serial, model, Android API, supported ABI list;
- host and plugin version;
- accessibility/screen-capture state when relevant;
- whether each test actually executed rather than being skipped by Assume.

See [Testing](TESTING.md) for commands and [Flake policy](FLAKE_POLICY.md) for
classification.
