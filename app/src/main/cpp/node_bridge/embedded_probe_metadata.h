#pragma once

#include "embedded_probe_kind.h"

namespace autojs6::node_bridge {

struct EmbeddedInlineProbeSelectorFlags {
    bool jsResult;
    bool stdoutWriteRequested;
    bool consoleDiagnostics;
    bool consoleStream;
    bool consoleShape;
    bool consoleReplaceRequested;
    bool consoleFamilyRequested;
    bool consoleFormatRequested;
    bool consoleRejectionRequested;
    bool consoleUncaughtRequested;
    bool schedulingRequested;
    bool schedulingOrderRequested;
};

struct EmbeddedInlineProbeMetadata {
    EmbeddedLifecycleJsProbeKind kind;
    const char* payloadPrefix;
    const char* logName;
    const char* notStartedDetail;
    const char* timingKey;
    const char* expectedStdout;
    const char* expectedStderr;
    EmbeddedInlineProbeSelectorFlags selectorFlags;
    bool stdoutCapture;
};

const EmbeddedInlineProbeMetadata* findEmbeddedInlineProbeMetadata(EmbeddedLifecycleJsProbeKind kind);
const EmbeddedInlineProbeSelectorFlags* embeddedInlineProbeSelectorFlags(EmbeddedLifecycleJsProbeKind kind);
EmbeddedLifecycleJsProbeKind embeddedInlineProbeKindFromSelectorFlags(const EmbeddedInlineProbeSelectorFlags& flags);
const char* embeddedInlineProbePayloadPrefix(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedInlineProbeLogName(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedInlineProbeNotStartedDetail(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedInlineProbeTimingKey(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedInlineProbeExpectedStdout(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedInlineProbeExpectedStderr(EmbeddedLifecycleJsProbeKind kind);
bool embeddedInlineProbeStdoutCapture(EmbeddedLifecycleJsProbeKind kind);

}  // namespace autojs6::node_bridge
