#pragma once

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <unistd.h>

#include "node.h"
#include "node_version.h"
#include "uv.h"
#include "node_bridge/embedded_inline_probe_payload.h"
#include "node_bridge/embedded_probe_kind.h"
#include "node_bridge/embedded_probe_metadata.h"
#include "node_bridge/embedded_probe_payload_utils.h"
#include "node_bridge/embedded_probe_sources.h"

#include <cerrno>
#include <cctype>
#include <cinttypes>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <chrono>
#include <cstdint>
#include <exception>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <string_view>
#include <thread>
#include <utility>
#include <vector>

#ifndef AUTOJS6_NODE_ANDROID_ABI
#define AUTOJS6_NODE_ANDROID_ABI "unknown"
#endif

#ifndef AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE
#define AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE 0
#endif

#ifndef AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION
#define AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION 0
#endif

#ifndef AUTOJS6_NODE_BRIDGE_LINKS_LIBNODE
#define AUTOJS6_NODE_BRIDGE_LINKS_LIBNODE 0
#endif

namespace autojs6::node_bridge::internal {

using namespace autojs6::node_bridge;

using NodeStart = int (*)(int, char**);
using NodeInitializeOncePerProcess =
        std::shared_ptr<node::InitializationResult> (*)(
                const std::vector<std::string>&,
                node::ProcessInitializationFlags::Flags
        );
using NodeTearDownOncePerProcess = void (*)();
using NodeArrayBufferAllocatorCreate = std::unique_ptr<node::ArrayBufferAllocator> (*)(bool);
using NodeMultiIsolatePlatformCreate =
        std::unique_ptr<node::MultiIsolatePlatform> (*)(
                int,
                v8::TracingController*,
                v8::PageAllocator*
        );
using NodeNewIsolateRaw =
        v8::Isolate* (*)(
                node::ArrayBufferAllocator*,
                uv_loop_s*,
                node::MultiIsolatePlatform*,
                const node::EmbedderSnapshotData*,
                const node::IsolateSettings&
        );
using NodeCreateIsolateDataRaw =
        node::IsolateData* (*)(
                v8::Isolate*,
                uv_loop_s*,
                node::MultiIsolatePlatform*,
                node::ArrayBufferAllocator*,
                const node::EmbedderSnapshotData*
        );
using NodeFreeIsolateData = void (*)(node::IsolateData*);
using NodeNewContext =
        v8::Local<v8::Context> (*)(v8::Isolate*, v8::Local<v8::ObjectTemplate>);
using NodeCreateEnvironmentRaw =
        node::Environment* (*)(
                node::IsolateData*,
                v8::Local<v8::Context>,
                const std::vector<std::string>&,
                const std::vector<std::string>&,
                node::EnvironmentFlags::Flags,
                node::ThreadId,
                std::unique_ptr<node::InspectorParentHandle>
        );
using NodeFreeEnvironment = void (*)(node::Environment*);
using NodeLoadEnvironmentSource =
        v8::MaybeLocal<v8::Value> (*)(
                node::Environment*,
                std::string_view,
                node::EmbedderPreloadCallback
        );
using NodeSpinEventLoop = v8::Maybe<int> (*)(node::Environment*);
using V8IsolateEnter = void (*)(v8::Isolate*);
using V8IsolateExit = void (*)(v8::Isolate*);
using V8HandleScopeConstructor = void (*)(v8::HandleScope*, v8::Isolate*);
using V8HandleScopeDestructor = void (*)(v8::HandleScope*);
using V8ContextEnter = void (*)(v8::Context*);
using V8ContextExit = void (*)(v8::Context*);
using V8ContextGlobal = v8::Local<v8::Object> (*)(v8::Context*);
using V8StringNewFromUtf8 =
        v8::MaybeLocal<v8::String> (*)(v8::Isolate*, const char*, v8::NewStringType, int);
using V8ObjectGet =
        v8::MaybeLocal<v8::Value> (*)(v8::Object*, v8::Local<v8::Context>, v8::Local<v8::Value>);
using V8ValueToString =
        v8::MaybeLocal<v8::String> (*)(const v8::Value*, v8::Local<v8::Context>);
using V8StringUtf8ValueConstructor =
        void (*)(v8::String::Utf8Value*, v8::Isolate*, v8::Local<v8::Value>, v8::String::WriteOptions);
using V8StringUtf8ValueDestructor = void (*)(v8::String::Utf8Value*);
using V8IsolateDispose = void (*)(v8::Isolate*);
using V8InitializePlatform = void (*)(v8::Platform*);
using V8Initialize = bool (*)(int);
using UvLoopInit = int (*)(uv_loop_t*);
using UvLoopClose = int (*)(uv_loop_t*);
using UvRun = int (*)(uv_loop_t*, uv_run_mode);
using UvWalk = void (*)(uv_loop_t*, uv_walk_cb, void*);
using UvHandleGetType = uv_handle_type (*)(const uv_handle_t*);
using UvIsActive = int (*)(const uv_handle_t*);
using UvIsClosing = int (*)(const uv_handle_t*);
using UvClose = void (*)(uv_handle_t*, uv_close_cb);
using Clock = std::chrono::steady_clock;

struct SymbolRequirement {
    const char* description;
    const char* symbols[3];
};

struct InlineSystemServiceDeniedValidationSpec {
    const char* deniedErrorCode;
    const char* deniedErrorMessage;
    const char* finalState;
    const char* const* falseJsonFields;
    size_t falseJsonFieldCount;
};

struct SymbolLookup {
    bool found = false;
    std::string symbol;
    std::string error;
    void* address = nullptr;
};

struct HandleAttemptResult {
    std::string name;
    std::string flags;
    void* handle = nullptr;
    std::string error;
    long long durationMs = 0;
};

struct OutputMethods {
    jmethodID stdoutMethod = nullptr;
    jmethodID stderrMethod = nullptr;
};

struct UvHandleDiagnosticSample {
    std::string address;
    std::string type;
    bool active = false;
    bool closing = false;
};

struct UvDiagnosticsSummary {
    bool valid = false;
    int count = 0;
    int active = 0;
    int closing = 0;
};

struct UvDiagnosticsContext {
    UvHandleGetType handleGetType = nullptr;
    UvIsActive isActive = nullptr;
    UvIsClosing isClosing = nullptr;
    int count = 0;
    int active = 0;
    int closing = 0;
    std::vector<int> typeCounts;
    std::vector<UvHandleDiagnosticSample> samples;

    UvDiagnosticsContext(UvHandleGetType getType, UvIsActive activeFn, UvIsClosing closingFn)
            : handleGetType(getType),
              isActive(activeFn),
              isClosing(closingFn),
              typeCounts(static_cast<size_t>(UV_HANDLE_TYPE_MAX) + 1, 0) {}
};

struct UvCloseTarget {
    uv_handle_t* handle = nullptr;
    std::string address;
    std::string type;
    bool activeBefore = false;
    bool closingBefore = false;
};

struct UvCloseContext {
    UvHandleGetType handleGetType = nullptr;
    UvIsActive isActive = nullptr;
    UvIsClosing isClosing = nullptr;
    std::vector<UvCloseTarget> targets;
    int seen = 0;
    int skipped = 0;

    UvCloseContext(UvHandleGetType getType, UvIsActive activeFn, UvIsClosing closingFn)
            : handleGetType(getType),
              isActive(activeFn),
              isClosing(closingFn) {}
};

extern const char* const kLogTag;
extern const char* const kNodeLibraryName;
extern const char* const kNodeStartSymbol;
extern const char* const kInitializeOncePerProcessNode24Symbol;
extern const char* const kTearDownOncePerProcessSymbol;
extern const char* const kArrayBufferAllocatorCreateSymbol;
extern const char* const kMultiIsolatePlatformCreateSymbol;
extern const char* const kNewIsolateRawSymbol;
extern const char* const kCreateIsolateDataRawSymbol;
extern const char* const kFreeIsolateDataSymbol;
extern const char* const kNewContextSymbol;
extern const char* const kCreateEnvironmentRawSymbol;
extern const char* const kFreeEnvironmentSymbol;
extern const char* const kLoadEnvironmentSourceSymbol;
extern const char* const kSpinEventLoopSymbol;
extern const char* const kInlineJsProbeSource;
extern const char* const kProcessJsProbeSource;
extern const char* const kConsoleJsProbeSource;
extern const char* const kJsResultProbeSource;
extern const char* const kStdoutWriteProbeSource;
extern const char* const kConsoleDiagnosticsProbeSource;
extern const char* const kConsoleStreamProbeSource;
extern const char* const kConsoleShapeProbeSource;
extern const char* const kConsoleReplaceProbeSource;
extern const char* const kConsoleFamilyProbeSource;
extern const char* const kConsoleFormatProbeSource;
extern const char* const kConsoleRejectionProbeSource;
extern const char* const kConsoleUncaughtProbeSource;
extern const char* const kSchedulingProbeSource;
extern const char* const kAsyncConsoleProbeSource;
extern const char* const kAsyncErrorProbeSource;
extern const char* const kBootstrapProbeSource;
extern const char* const kBootstrapScriptBootstrapSource;
extern const char* const kBootstrapScriptProbeScriptSource;
extern const char* const kScriptCompletionBootstrapSource;
extern const char* const kScriptCompletionProbeScriptSource;
extern const char* const kScriptFailureProbeScriptSource;
extern const char* const kScriptCancelProbeScriptSource;
extern const char* const kCapabilityDescriptorBootstrapSource;
extern const char* const kCapabilityDescriptorProbeScriptSource;
extern const char* const kPluginContractBootstrapSource;
extern const char* const kPluginContractProbeScriptSource;
extern const char* const kPluginRequestBootstrapSource;
extern const char* const kPluginRequestProbeScriptSource;
extern const char* const kPluginEventsBootstrapSource;
extern const char* const kPluginEventsProbeScriptSource;
extern const char* const kPluginFailureBootstrapSource;
extern const char* const kPluginFailureProbeScriptSource;
extern const char* const kPluginCancelBootstrapSource;
extern const char* const kPluginCancelProbeScriptSource;
extern const char* const kPluginDisposeBootstrapSource;
extern const char* const kPluginDisposeProbeScriptSource;
extern const char* const kPluginLifecycleBootstrapSource;
extern const char* const kPluginLifecycleProbeScriptSource;
extern const char* const kPluginInvalidRequestBootstrapSource;
extern const char* const kPluginInvalidRequestProbeScriptSource;
extern const char* const kPluginStateMachineBootstrapSource;
extern const char* const kPluginStateMachineProbeScriptSource;
extern const char* const kPluginTimeoutBootstrapSource;
extern const char* const kPluginTimeoutProbeScriptSource;
extern const char* const kPluginCrashBootstrapSource;
extern const char* const kPluginCrashProbeScriptSource;
extern const char* const kPluginBackpressureBootstrapSource;
extern const char* const kPluginBackpressureProbeScriptSource;
extern const char* const kPluginNegotiationBootstrapSource;
extern const char* const kPluginNegotiationProbeScriptSource;
extern const char* const kPluginPermissionBootstrapSource;
extern const char* const kPluginPermissionProbeScriptSource;
extern const char* const kPluginManifestBootstrapSource;
extern const char* const kPluginManifestProbeScriptSource;
extern const char* const kPluginAbiBootstrapSource;
extern const char* const kPluginAbiProbeScriptSource;
extern const char* const kPluginLibraryBootstrapSource;
extern const char* const kPluginLibraryProbeScriptSource;
extern const char* const kPluginInstanceBootstrapSource;
extern const char* const kPluginInstanceProbeScriptSource;
extern const char* const kPluginSessionBootstrapSource;
extern const char* const kPluginSessionProbeScriptSource;
extern const char* const kPluginQueueBootstrapSource;
extern const char* const kPluginQueueProbeScriptSource;
extern const char* const kPluginConcurrencyBootstrapSource;
extern const char* const kPluginConcurrencyProbeScriptSource;
extern const char* const kPluginRaceBootstrapSource;
extern const char* const kPluginRaceProbeScriptSource;
extern const char* const kPluginShutdownBootstrapSource;
extern const char* const kPluginShutdownProbeScriptSource;
extern const char* const kPluginHeartbeatBootstrapSource;
extern const char* const kPluginHeartbeatProbeScriptSource;
extern const char* const kPluginHealthBootstrapSource;
extern const char* const kPluginHealthProbeScriptSource;
extern const char* const kPluginTelemetryBootstrapSource;
extern const char* const kPluginTelemetryProbeScriptSource;
extern const char* const kPluginDiagnosticsBootstrapSource;
extern const char* const kPluginDiagnosticsProbeScriptSource;
extern const char* const kPluginRecoveryBootstrapSource;
extern const char* const kPluginRecoveryProbeScriptSource;
extern const char* const kPluginRestartBootstrapSource;
extern const char* const kPluginRestartProbeScriptSource;
extern const char* const kPluginRestartBudgetBootstrapSource;
extern const char* const kPluginRestartBudgetProbeScriptSource;
extern const char* const kPluginQuarantineBootstrapSource;
extern const char* const kPluginQuarantineProbeScriptSource;
extern const char* const kScriptDescriptorBootstrapSource;
extern const char* const kScriptDescriptorProbeScriptSource;
extern const char* const kScriptNormalizationBootstrapSource;
extern const char* const kScriptNormalizationProbeScriptSource;
extern const char* const kScriptContextBootstrapSource;
extern const char* const kScriptContextProbeScriptSource;
extern const char* const kScriptResultV2BootstrapSource;
extern const char* const kScriptResultV2ProbeScriptSource;
extern const char* const kScriptCancellationTokenBootstrapSource;
extern const char* const kScriptCancellationTokenProbeScriptSource;
extern const char* const kScriptTimeoutPolicyBootstrapSource;
extern const char* const kScriptTimeoutPolicyProbeScriptSource;
extern const char* const kScriptMemoryPolicyBootstrapSource;
extern const char* const kScriptMemoryPolicyProbeScriptSource;
extern const char* const kScriptPreflightSummaryBootstrapSource;
extern const char* const kScriptPreflightSummaryProbeScriptSource;
extern const char* const kCompletionDrainProbeScriptSource;
extern const char* const kPostCompletionErrorProbeScriptSource;
extern const char* const kOutputEventsProbeScriptSource;
extern const char* const kOutputEnvelopeBootstrapSource;
extern const char* const kOutputEnvelopeProbeScriptSource;
extern const char* const kSchedulingOrderProbeSource;
extern const char* const kJsResultPropertyName;
extern const char* const kStdoutCaptureExpectedText;
extern const char* const kJsResultExpectedText;
extern const size_t kStdoutCaptureTextMax;
extern const char* const kV8IsolateEnterSymbol;
extern const char* const kV8IsolateExitSymbol;
extern const char* const kV8HandleScopeConstructorSymbol;
extern const char* const kV8HandleScopeDestructorSymbol;
extern const char* const kV8ContextEnterSymbol;
extern const char* const kV8ContextExitSymbol;
extern const char* const kV8ContextGlobalSymbol;
extern const char* const kV8StringNewFromUtf8Symbol;
extern const char* const kV8ObjectGetSymbol;
extern const char* const kV8ValueToStringSymbol;
extern const char* const kV8StringUtf8ValueConstructorSymbol;
extern const char* const kV8StringUtf8ValueDestructorSymbol;
extern const char* const kV8IsolateDisposeSymbol;
extern const char* const kV8InitializePlatformSymbol;
extern const char* const kV8InitializeSymbol;
extern const char* const kUvLoopInitSymbol;
extern const char* const kUvLoopCloseSymbol;
extern const char* const kUvRunSymbol;
extern const char* const kUvWalkSymbol;
extern const char* const kUvHandleGetTypeSymbol;
extern const char* const kUvIsActiveSymbol;
extern const char* const kUvIsClosingSymbol;
extern const char* const kUvCloseSymbol;
extern const int kUvRunCleanupMaxIterations;
extern const SymbolRequirement kEmbedderSymbols[];
extern const size_t kEmbedderSymbolCount;

std::mutex& nodeStartMutex();
void throwJava(JNIEnv* env, const char* className, const std::string& message);
class JavaOutputSink {
public:
    JavaOutputSink(JNIEnv* env, jobject sink) {
        if (sink == nullptr) {
            return;
        }
        if (env->GetJavaVM(&vm_) != JNI_OK || vm_ == nullptr) {
            throwJava(env, "java/lang/IllegalStateException", "Failed to get JavaVM");
            return;
        }
        sink_ = env->NewGlobalRef(sink);
        jclass localClass = env->GetObjectClass(sink);
        if (localClass == nullptr) {
            return;
        }
        sinkClass_ = static_cast<jclass>(env->NewGlobalRef(localClass));
        env->DeleteLocalRef(localClass);
        methods_.stdoutMethod = env->GetMethodID(sinkClass_, "onStdout", "([B)V");
        methods_.stderrMethod = env->GetMethodID(sinkClass_, "onStderr", "([B)V");
        if (methods_.stdoutMethod == nullptr || methods_.stderrMethod == nullptr) {
            throwJava(env, "java/lang/NoSuchMethodError", "OutputSink must implement onStdout([B)V and onStderr([B)V");
        }
    }

    ~JavaOutputSink() = default;

    bool available() const {
        return vm_ != nullptr && sink_ != nullptr &&
                methods_.stdoutMethod != nullptr && methods_.stderrMethod != nullptr;
    }

    void emitStdout(const char* bytes, size_t length) const {
        emit(methods_.stdoutMethod, bytes, length);
    }

    void emitStderr(const char* bytes, size_t length) const {
        emit(methods_.stderrMethod, bytes, length);
    }

    void release(JNIEnv* env) {
        if (sink_ != nullptr) {
            env->DeleteGlobalRef(sink_);
            sink_ = nullptr;
        }
        if (sinkClass_ != nullptr) {
            env->DeleteGlobalRef(sinkClass_);
            sinkClass_ = nullptr;
        }
    }

private:
    JavaVM* vm_ = nullptr;
    jobject sink_ = nullptr;
    jclass sinkClass_ = nullptr;
    OutputMethods methods_;

    void emit(jmethodID method, const char* bytes, size_t length) const {
        if (!available() || method == nullptr) {
            return;
        }

        JNIEnv* env = nullptr;
        bool detach = false;
        jint envResult = vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (envResult == JNI_EDETACHED) {
            if (vm_->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                return;
            }
            detach = true;
        } else if (envResult != JNI_OK || env == nullptr) {
            return;
        }

        jbyteArray payload = env->NewByteArray(static_cast<jsize>(length));
        if (payload != nullptr) {
            env->SetByteArrayRegion(
                    payload,
                    0,
                    static_cast<jsize>(length),
                    reinterpret_cast<const jbyte*>(bytes)
            );
            env->CallVoidMethod(sink_, method, payload);
            env->DeleteLocalRef(payload);
        }
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        if (detach) {
            vm_->DetachCurrentThread();
        }
    }
};

class PipeReader {
public:
    using EmitFunction = void (JavaOutputSink::*)(const char*, size_t) const;

    PipeReader(int readFd, std::shared_ptr<JavaOutputSink> sink, EmitFunction emitFunction)
            : readFd_(readFd), sink_(std::move(sink)), emitFunction_(emitFunction) {
    }

    void start() {
        thread_ = std::thread([this] { readLoop(); });
    }

    void join() {
        if (thread_.joinable()) {
            thread_.join();
        }
    }

private:
    int readFd_ = -1;
    std::shared_ptr<JavaOutputSink> sink_;
    EmitFunction emitFunction_;
    std::thread thread_;
    std::string pending_;

    void readLoop() {
        char buffer[4096];
        while (true) {
            ssize_t count = read(readFd_, buffer, sizeof(buffer));
            if (count > 0) {
                append(buffer, static_cast<size_t>(count));
                continue;
            }
            break;
        }
        flushPending();
        if (readFd_ >= 0) {
            close(readFd_);
            readFd_ = -1;
        }
    }

    void append(const char* bytes, size_t length) {
        pending_.append(bytes, length);
        size_t start = 0;
        while (true) {
            size_t newline = pending_.find('\n', start);
            if (newline == std::string::npos) {
                if (start > 0) {
                    pending_.erase(0, start);
                }
                return;
            }
            emitLine(start, newline);
            start = newline + 1;
        }
    }

    void flushPending() {
        if (!pending_.empty()) {
            emitLine(0, pending_.size());
            pending_.clear();
        }
    }

    void emitLine(size_t begin, size_t end) {
        if (end > begin && pending_[end - 1] == '\r') {
            --end;
        }
        (sink_.get()->*emitFunction_)(pending_.data() + begin, end - begin);
    }
};

class ScopedOutputRedirection {
public:
    bool start(JNIEnv* env, jobject outputSink) {
        if (outputSink == nullptr) {
            return true;
        }

        sink_ = std::make_shared<JavaOutputSink>(env, outputSink);
        if (env->ExceptionCheck()) {
            return false;
        }
        if (!sink_->available()) {
            return true;
        }

        if (!redirectFd(env, STDOUT_FILENO, stdoutPipe_, savedStdout_, true)) {
            return false;
        }
        if (!redirectFd(env, STDERR_FILENO, stderrPipe_, savedStderr_, false)) {
            restore();
            return false;
        }

        stdoutReader_ = std::make_unique<PipeReader>(
                stdoutPipe_[0],
                sink_,
                &JavaOutputSink::emitStdout
        );
        stderrReader_ = std::make_unique<PipeReader>(
                stderrPipe_[0],
                sink_,
                &JavaOutputSink::emitStderr
        );
        stdoutPipe_[0] = -1;
        stderrPipe_[0] = -1;

        stdoutReader_->start();
        stderrReader_->start();
        active_ = true;
        return true;
    }

    void stop(JNIEnv* env) {
        restore();
        joinReaders();
        if (sink_ != nullptr) {
            sink_->release(env);
            sink_.reset();
        }
    }

    ~ScopedOutputRedirection() {
        restore();
        joinReaders();
    }

private:
    bool active_ = false;
    int savedStdout_ = -1;
    int savedStderr_ = -1;
    int stdoutPipe_[2] = {-1, -1};
    int stderrPipe_[2] = {-1, -1};
    std::shared_ptr<JavaOutputSink> sink_;
    std::unique_ptr<PipeReader> stdoutReader_;
    std::unique_ptr<PipeReader> stderrReader_;

    bool redirectFd(JNIEnv* env, int targetFd, int pipeFds[2], int& savedFd, bool stdoutStream) {
        savedFd = dup(targetFd);
        if (savedFd < 0) {
            throwErrno(env, "dup");
            return false;
        }
        if (pipe(pipeFds) != 0) {
            throwErrno(env, "pipe");
            return false;
        }
        if (dup2(pipeFds[1], targetFd) < 0) {
            throwErrno(env, "dup2");
            return false;
        }
        close(pipeFds[1]);
        pipeFds[1] = -1;

        if (stdoutStream) {
            setvbuf(stdout, nullptr, _IONBF, 0);
        } else {
            setvbuf(stderr, nullptr, _IONBF, 0);
        }
        return true;
    }

    void restore() {
        fflush(stdout);
        fflush(stderr);
        if (savedStdout_ >= 0) {
            dup2(savedStdout_, STDOUT_FILENO);
            close(savedStdout_);
            savedStdout_ = -1;
        }
        if (savedStderr_ >= 0) {
            dup2(savedStderr_, STDERR_FILENO);
            close(savedStderr_);
            savedStderr_ = -1;
        }
        closeOpenPipeFds();
        active_ = false;
    }

    void joinReaders() {
        if (stdoutReader_ != nullptr) {
            stdoutReader_->join();
            stdoutReader_.reset();
        }
        if (stderrReader_ != nullptr) {
            stderrReader_->join();
            stderrReader_.reset();
        }
    }

    void closeOpenPipeFds() {
        closeIfOpen(stdoutPipe_[0]);
        closeIfOpen(stdoutPipe_[1]);
        closeIfOpen(stderrPipe_[0]);
        closeIfOpen(stderrPipe_[1]);
    }

    static void closeIfOpen(int& fd) {
        if (fd >= 0) {
            close(fd);
            fd = -1;
        }
    }

    static void throwErrno(JNIEnv* env, const char* operation) {
        throwJava(
                env,
                "java/lang/IllegalStateException",
                std::string("Failed to redirect Node.js output during ") + operation + ": " +
                        std::strerror(errno)
        );
    }
};

class CapturingPipeReader {
public:
    explicit CapturingPipeReader(int readFd) : readFd_(readFd) {
    }

    void start() {
        thread_ = std::thread([this] { readLoop(); });
    }

    void join() {
        if (thread_.joinable()) {
            thread_.join();
        }
    }

    long long totalBytes() const {
        return static_cast<long long>(totalBytes_);
    }

    const std::string& text() const {
        return text_;
    }

    bool truncated() const {
        return truncated_;
    }

private:
    int readFd_ = -1;
    std::thread thread_;
    std::string text_;
    size_t totalBytes_ = 0;
    bool truncated_ = false;

    void readLoop() {
        char buffer[4096];
        while (true) {
            ssize_t count = read(readFd_, buffer, sizeof(buffer));
            if (count > 0) {
                append(buffer, static_cast<size_t>(count));
                continue;
            }
            break;
        }
        if (readFd_ >= 0) {
            close(readFd_);
            readFd_ = -1;
        }
    }

    void append(const char* bytes, size_t length) {
        totalBytes_ += length;
        if (text_.size() >= kStdoutCaptureTextMax) {
            truncated_ = truncated_ || length > 0;
            return;
        }
        const size_t available = kStdoutCaptureTextMax - text_.size();
        const size_t copied = length < available ? length : available;
        text_.append(bytes, copied);
        truncated_ = truncated_ || copied < length;
    }
};

class ScopedFdOutputCapture {
public:
    bool start(std::string& error) {
        if (!redirectFd(STDOUT_FILENO, stdoutPipe_, savedStdout_, true, error)) {
            restore();
            return false;
        }
        if (!redirectFd(STDERR_FILENO, stderrPipe_, savedStderr_, false, error)) {
            restore();
            return false;
        }

        stdoutReader_ = std::make_unique<CapturingPipeReader>(stdoutPipe_[0]);
        stderrReader_ = std::make_unique<CapturingPipeReader>(stderrPipe_[0]);
        stdoutPipe_[0] = -1;
        stderrPipe_[0] = -1;
        stdoutReader_->start();
        stderrReader_->start();
        active_ = true;
        return true;
    }

    void stop() {
        restore();
        joinReaders();
    }

    ~ScopedFdOutputCapture() {
        stop();
    }

    long long stdoutBytes() const {
        return stdoutReader_ == nullptr ? 0 : stdoutReader_->totalBytes();
    }

    long long stderrBytes() const {
        return stderrReader_ == nullptr ? 0 : stderrReader_->totalBytes();
    }

    const std::string& stdoutText() const {
        static const std::string empty;
        return stdoutReader_ == nullptr ? empty : stdoutReader_->text();
    }

    const std::string& stderrText() const {
        static const std::string empty;
        return stderrReader_ == nullptr ? empty : stderrReader_->text();
    }

    bool stdoutTruncated() const {
        return stdoutReader_ != nullptr && stdoutReader_->truncated();
    }

    bool stderrTruncated() const {
        return stderrReader_ != nullptr && stderrReader_->truncated();
    }

private:
    bool active_ = false;
    int savedStdout_ = -1;
    int savedStderr_ = -1;
    int stdoutPipe_[2] = {-1, -1};
    int stderrPipe_[2] = {-1, -1};
    std::unique_ptr<CapturingPipeReader> stdoutReader_;
    std::unique_ptr<CapturingPipeReader> stderrReader_;

    bool redirectFd(int targetFd, int pipeFds[2], int& savedFd, bool stdoutStream, std::string& error) {
        savedFd = dup(targetFd);
        if (savedFd < 0) {
            error = std::string("dup failed: ") + std::strerror(errno);
            return false;
        }
        if (pipe(pipeFds) != 0) {
            error = std::string("pipe failed: ") + std::strerror(errno);
            return false;
        }
        if (dup2(pipeFds[1], targetFd) < 0) {
            error = std::string("dup2 failed: ") + std::strerror(errno);
            return false;
        }
        close(pipeFds[1]);
        pipeFds[1] = -1;

        if (stdoutStream) {
            setvbuf(stdout, nullptr, _IONBF, 0);
        } else {
            setvbuf(stderr, nullptr, _IONBF, 0);
        }
        return true;
    }

    void restore() {
        fflush(stdout);
        fflush(stderr);
        if (savedStdout_ >= 0) {
            dup2(savedStdout_, STDOUT_FILENO);
            close(savedStdout_);
            savedStdout_ = -1;
        }
        if (savedStderr_ >= 0) {
            dup2(savedStderr_, STDERR_FILENO);
            close(savedStderr_);
            savedStderr_ = -1;
        }
        closeOpenPipeFds();
        active_ = false;
    }

    void joinReaders() {
        if (stdoutReader_ != nullptr) {
            stdoutReader_->join();
        }
        if (stderrReader_ != nullptr) {
            stderrReader_->join();
        }
    }

    void closeOpenPipeFds() {
        closeIfOpen(stdoutPipe_[0]);
        closeIfOpen(stdoutPipe_[1]);
        closeIfOpen(stderrPipe_[0]);
        closeIfOpen(stderrPipe_[1]);
    }

    static void closeIfOpen(int& fd) {
        if (fd >= 0) {
            close(fd);
            fd = -1;
        }
    }
};

class ScopedWorkingDirectory {
public:
    bool enter(JNIEnv* env, const std::string& workingDirectory) {
        std::string error;
        if (enter(workingDirectory, &error)) {
            return true;
        }
        throwJava(
                env,
                "java/lang/IllegalStateException",
                error
        );
        return false;
    }

    bool enter(const std::string& workingDirectory, std::string* error) {
        if (workingDirectory.empty()) {
            return true;
        }

        char* current = getcwd(nullptr, 0);
        if (current != nullptr) {
            previous_ = current;
            std::free(current);
        }

        if (chdir(workingDirectory.c_str()) != 0) {
            if (error != nullptr) {
                *error = "Failed to change working directory to " + workingDirectory + ": " +
                        std::strerror(errno);
            }
            return false;
        }
        changed_ = true;
        return true;
    }

    ~ScopedWorkingDirectory() {
        if (changed_ && !previous_.empty()) {
            if (chdir(previous_.c_str()) != 0) {
                __android_log_print(
                        ANDROID_LOG_WARN,
                        kLogTag,
                        "Failed to restore working directory to %s: %s",
                        previous_.c_str(),
                        std::strerror(errno)
                );
            }
        }
    }

private:
    bool changed_ = false;
    std::string previous_;
};

struct EmbeddedScriptExecutionRequest {
    std::string source;
    std::string sourceName;
    std::string workingDirectory;
    std::string sandboxRoot;
    std::vector<std::pair<std::string, std::string>> moduleSources;
    std::vector<std::pair<std::string, std::string>> runtimeModuleSources;
    std::vector<std::pair<std::string, std::string>> env;
    bool esmExperimentalEnabled = false;
    bool dynamicImportExperimentalEnabled = false;
    bool rawNodeNetworkModulesExperimentalEnabled = false;
    bool workerThreadsExperimentalEnabled = false;
    bool childProcessExperimentalEnabled = false;
    bool javaInteropExperimentalEnabled = false;
};

std::vector<std::string> runEmbeddedScriptExecution(
        const EmbeddedScriptExecutionRequest& request,
        const char* runtimeAdapterPath = nullptr,
        const char* runtimeAdapterMode = nullptr
);

bool isInlineSystemServiceDeniedProbe(EmbeddedLifecycleJsProbeKind kind);
void putInlineSystemServiceDeniedSkippedPayload(
        std::vector<std::string>& payload,
        EmbeddedLifecycleJsProbeKind kind,
        const char* detail
);
void putInlineSystemServiceDeniedFields(
        std::vector<std::string>& payload,
        EmbeddedLifecycleJsProbeKind kind,
        const std::string& text
);
bool isUserSourcePreflightProbe(EmbeddedLifecycleJsProbeKind kind);
bool isControlledUserInlineProbe(EmbeddedLifecycleJsProbeKind kind);
bool isUserFilePreflightProbe(EmbeddedLifecycleJsProbeKind kind);
bool isEmbeddedScriptContractProbe(EmbeddedLifecycleJsProbeKind kind);
bool isEmbeddedMvpReadinessProbe(EmbeddedLifecycleJsProbeKind kind);
void putUserSourcePreflightProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail);
void putControlledUserInlineProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail);
void putUserFilePreflightProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail);
void putEmbeddedScriptContractProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail);
void putEmbeddedMvpReadinessProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail);
void putUserSourcePreflightProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text);
void putControlledUserInlineProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text);
void putUserFilePreflightProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text);
void putEmbeddedScriptContractProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text);
void putEmbeddedMvpReadinessProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text);
void putMetadataMappedInlineProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail);
void putMetadataMappedInlineProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text);
bool userSourcePreflightCommonValidationFailed(const std::string& resultText);
bool userSourcePreflightValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText);
bool controlledUserInlineCommonValidationFailed(const std::string& resultText);
bool controlledUserInlineValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText);
bool userFilePreflightCommonValidationFailed(const std::string& resultText);
bool userFilePreflightValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText);
bool embeddedScriptContractCommonValidationFailed(const std::string& resultText);
bool embeddedScriptContractValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText);
bool embeddedMvpReadinessCommonValidationFailed(const std::string& resultText);
bool embeddedMvpReadinessValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText);
const InlineSystemServiceDeniedValidationSpec* inlineSystemServiceDeniedValidationSpec(EmbeddedLifecycleJsProbeKind kind);
bool inlineSystemServiceDeniedValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText);
void throwJava(JNIEnv* env, const char* className, const std::string& message);
std::string extractBetween(const std::string& value, const char* prefix, const char* suffix);
bool isAbiMismatch(const std::string& error);
bool isLibcxxRuntimeSymbol(const std::string& symbol);
std::string classifyDlopenFailure(const char* libraryName, const std::string& error);
std::string buildDlopenFailureMessage(const char* libraryName, const char* rawError);
std::string toStdString(JNIEnv* env, jstring value);
void putPayloadRaw(std::vector<std::string>& payload, const std::string& key, const std::string& value);
std::string hexEncodeBytes(const std::string& value);
std::string jsonStringLiteral(const std::string& value);
std::string sanitizeSourceUrl(const std::string& value);
std::string buildEmbeddedModuleSourcesLiteral(const std::vector<std::pair<std::string, std::string>>& moduleSources);
std::string buildEmbeddedScriptExecutionSource(
        const std::string& source,
        const std::string& sourceName,
        const std::string& workingDirectory,
        const std::string& sandboxRoot,
        const std::vector<std::pair<std::string, std::string>>& moduleSources,
        const std::vector<std::pair<std::string, std::string>>& runtimeModuleSources,
        const std::vector<std::pair<std::string, std::string>>& env,
        bool esmExperimentalEnabled,
        bool dynamicImportExperimentalEnabled,
        bool rawNodeNetworkModulesExperimentalEnabled,
        bool workerThreadsExperimentalEnabled,
        bool childProcessExperimentalEnabled,
        bool javaInteropExperimentalEnabled);
void putEmbeddedScriptExecutionFields(std::vector<std::string>& payload, const std::string& text);
std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray values, const char* label = "array");
std::vector<std::string> toStringVectorOrEmpty(JNIEnv* env, jobjectArray values, const char* label = "array");
NodeStart resolveNodeStart(JNIEnv* env);
SymbolLookup lookupSymbol(void* handle, const char* symbol);
SymbolLookup lookupAnySymbol(void* handle, const SymbolRequirement& requirement);
SymbolLookup lookupAnySymbolLogged(void* handle, const SymbolRequirement& requirement, int requirementIndex);
bool hasAnySymbol(void* handle, const SymbolRequirement& requirement);
long long elapsedMs(Clock::time_point start, Clock::time_point end = Clock::now());
std::string joinSymbolCandidates(const SymbolRequirement& requirement);
std::string joinStrings(const std::vector<std::string>& values, const char* separator);
jobjectArray toJavaStringArray(JNIEnv* env, const std::vector<std::string>& values);
void putCommonEmbeddedProbePayload(
        std::vector<std::string>& payload,
        int probeLevel,
        Clock::time_point startedAt
);
HandleAttemptResult probeLibnodeDlopenAttempt(
        std::vector<std::string>& payload,
        const char* attemptName,
        int flags,
        const char* flagsText
);
HandleAttemptResult unavailableLibnodeDlopenAttempt(
        std::vector<std::string>& payload,
        const char* attemptName,
        const char* flagsText,
        const char* reason
);
void* probeLoadedLibnodeHandle(std::vector<std::string>& payload);
void appendEmbeddedSymbolProbePayload(std::vector<std::string>& payload, void* handle);
void putLifecycleSkippedPayload(
        std::vector<std::string>& payload,
        const char* initializeDetail,
        const char* teardownDetail
);
void putIsolateSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putIsolateDataSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putEnvironmentSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putLoadEnvironmentSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putSpinEventLoopSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putInlineJsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putProcessJsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleJsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putStdoutCaptureSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putJsResultSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putStdoutWriteSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleDiagnosticsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleStreamSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleShapeSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleReplaceSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleFamilySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleFormatSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleRejectionSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putConsoleUncaughtSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putSchedulingSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putSchedulingOrderSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putAsyncConsoleSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putAsyncErrorSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putBootstrapSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putBootstrapScriptSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptCompletionSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptFailureSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptCancelSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putCapabilityDescriptorSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginContractSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginRequestSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginEventsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginFailureSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginCancelSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginDisposeSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginLifecycleSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginInvalidRequestSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginStateMachineSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginTimeoutSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginCrashSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginBackpressureSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginNegotiationSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginPermissionSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginManifestSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginAbiSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginLibrarySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginInstanceSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginSessionSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginQueueSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginConcurrencySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginRaceSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginShutdownSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginHeartbeatSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginHealthSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginTelemetrySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginDiagnosticsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginRecoverySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginRestartSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginRestartBudgetSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPluginQuarantineSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptDescriptorSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptNormalizationSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptContextSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptResultV2SkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptCancellationTokenSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptTimeoutPolicySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptMemoryPolicySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putScriptPreflightSummarySkippedPayload(std::vector<std::string>& payload, const char* detail);
void putCompletionDrainSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putPostCompletionErrorSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putOutputEventsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putOutputEnvelopeSkippedPayload(std::vector<std::string>& payload, const char* detail);
std::vector<std::string> splitPipeFields(const std::string& text);
void putJsResultFields(std::vector<std::string>& payload, const std::string& text);
void putStdoutWriteFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleDiagnosticsFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleStreamFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleShapeFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleReplaceFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleFamilyFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleFormatFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleRejectionFields(std::vector<std::string>& payload, const std::string& text);
void putConsoleUncaughtFields(std::vector<std::string>& payload, const std::string& text);
void putSchedulingFields(std::vector<std::string>& payload, const std::string& text);
void putSchedulingOrderFields(std::vector<std::string>& payload, const std::string& text);
void putAsyncConsoleFields(std::vector<std::string>& payload, const std::string& text);
void putAsyncErrorFields(std::vector<std::string>& payload, const std::string& text);
void putBootstrapFields(std::vector<std::string>& payload, const std::string& text);
void putBootstrapScriptFields(std::vector<std::string>& payload, const std::string& text);
void putScriptCompletionFields(std::vector<std::string>& payload, const std::string& text);
void putScriptFailureFields(std::vector<std::string>& payload, const std::string& text);
void putScriptCancelFields(std::vector<std::string>& payload, const std::string& text);
void putCapabilityDescriptorFields(std::vector<std::string>& payload, const std::string& text);
void putPluginContractFields(std::vector<std::string>& payload, const std::string& text);
void putPluginRequestFields(std::vector<std::string>& payload, const std::string& text);
void putPluginEventsFields(std::vector<std::string>& payload, const std::string& text);
void putPluginFailureFields(std::vector<std::string>& payload, const std::string& text);
void putPluginCancelFields(std::vector<std::string>& payload, const std::string& text);
void putPluginDisposeFields(std::vector<std::string>& payload, const std::string& text);
void putPluginLifecycleFields(std::vector<std::string>& payload, const std::string& text);
void putPluginInvalidRequestFields(std::vector<std::string>& payload, const std::string& text);
void putPluginStateMachineFields(std::vector<std::string>& payload, const std::string& text);
void putPluginTimeoutFields(std::vector<std::string>& payload, const std::string& text);
void putPluginCrashFields(std::vector<std::string>& payload, const std::string& text);
void putPluginBackpressureFields(std::vector<std::string>& payload, const std::string& text);
void putPluginNegotiationFields(std::vector<std::string>& payload, const std::string& text);
void putPluginPermissionFields(std::vector<std::string>& payload, const std::string& text);
void putPluginManifestFields(std::vector<std::string>& payload, const std::string& text);
void putPluginAbiFields(std::vector<std::string>& payload, const std::string& text);
void putPluginLibraryFields(std::vector<std::string>& payload, const std::string& text);
void putPluginInstanceFields(std::vector<std::string>& payload, const std::string& text);
void putPluginSessionFields(std::vector<std::string>& payload, const std::string& text);
void putPluginQueueFields(std::vector<std::string>& payload, const std::string& text);
void putPluginConcurrencyFields(std::vector<std::string>& payload, const std::string& text);
void putPluginRaceFields(std::vector<std::string>& payload, const std::string& text);
void putPluginShutdownFields(std::vector<std::string>& payload, const std::string& text);
void putPluginHeartbeatFields(std::vector<std::string>& payload, const std::string& text);
void putPluginHealthFields(std::vector<std::string>& payload, const std::string& text);
void putPluginTelemetryFields(std::vector<std::string>& payload, const std::string& text);
void putPluginDiagnosticsFields(std::vector<std::string>& payload, const std::string& text);
void putPluginRecoveryFields(std::vector<std::string>& payload, const std::string& text);
void putPluginRestartFields(std::vector<std::string>& payload, const std::string& text);
void putPluginRestartBudgetFields(std::vector<std::string>& payload, const std::string& text);
void putPluginQuarantineFields(std::vector<std::string>& payload, const std::string& text);
void putScriptDescriptorFields(std::vector<std::string>& payload, const std::string& text);
void putScriptNormalizationFields(std::vector<std::string>& payload, const std::string& text);
void putScriptContextFields(std::vector<std::string>& payload, const std::string& text);
void putScriptResultV2Fields(std::vector<std::string>& payload, const std::string& text);
void putScriptCancellationTokenFields(std::vector<std::string>& payload, const std::string& text);
void putScriptTimeoutPolicyFields(std::vector<std::string>& payload, const std::string& text);
void putScriptMemoryPolicyFields(std::vector<std::string>& payload, const std::string& text);
void putScriptPreflightSummaryFields(std::vector<std::string>& payload, const std::string& text);
void putCompletionDrainFields(std::vector<std::string>& payload, const std::string& text);
void putPostCompletionErrorFields(std::vector<std::string>& payload, const std::string& text);
void putOutputEventsFields(std::vector<std::string>& payload, const std::string& text);
void putOutputEnvelopeFields(std::vector<std::string>& payload, const std::string& text);
bool readJsResultGlobal(
        v8::Isolate* isolate,
        v8::Local<v8::Context> context,
        V8ContextGlobal contextGlobal,
        V8StringNewFromUtf8 stringNewFromUtf8,
        V8ObjectGet objectGet,
        V8ValueToString valueToString,
        V8StringUtf8ValueConstructor utf8ValueConstructor,
        V8StringUtf8ValueDestructor utf8ValueDestructor,
        std::string& resultText,
        std::string& error
);
void putUvLoopSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putV8SkippedPayload(std::vector<std::string>& payload, const char* detail);
void putUvDiagnosticsSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putUvCloseSkippedPayload(std::vector<std::string>& payload, const char* detail);
void putUvRunSkippedPayload(std::vector<std::string>& payload, const char* detail);
const char* uvHandleTypeNameForPayload(uv_handle_type type);
std::string pointerToHex(const void* pointer);
EmbeddedLifecycleJsProbeKind resolveEmbeddedLifecycleJsProbeKind(
        bool jsResult,
        bool stdoutWriteRequested,
        bool consoleDiagnostics,
        bool consoleStream,
        bool consoleShape,
        bool consoleReplaceRequested,
        bool consoleFamilyRequested,
        bool consoleFormatRequested,
        bool consoleRejectionRequested,
        bool consoleUncaughtRequested,
        bool schedulingRequested,
        bool schedulingOrderRequested
);
const char* embeddedLifecycleJsProbePayloadPrefix(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedLifecycleJsProbeLogName(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedLifecycleJsProbeNotStartedDetail(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedLifecycleJsProbeTimingKey(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedLifecycleJsProbeExpectedStdout(EmbeddedLifecycleJsProbeKind kind);
const char* embeddedLifecycleJsProbeExpectedStderr(EmbeddedLifecycleJsProbeKind kind);
void collectUvHandleDiagnostic(uv_handle_t* handle, void* arg);
void collectUvCloseTarget(uv_handle_t* handle, void* arg);
void noopUvCloseCallback(uv_handle_t* /* handle */);
UvDiagnosticsSummary appendUvDiagnosticsPayload(
        std::vector<std::string>& payload,
        const std::string& phase,
        uv_loop_t* eventLoop,
        UvWalk uvWalk,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing
);
void appendUvClosePayload(
        std::vector<std::string>& payload,
        uv_loop_t* eventLoop,
        UvWalk uvWalk,
        UvClose uvClose,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing
);
bool appendUvRunCleanupPayload(
        std::vector<std::string>& payload,
        uv_loop_t* eventLoop,
        UvRun uvRun,
        UvWalk uvWalk,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing,
        bool collectDiagnostics = true
);
int v8BuildConfiguration();
std::string uvLoopResultDetail(const char* operation, int result);
void appendEmbeddedLifecycleProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8LifecycleProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvIsolateLifecycleProbePayload(
        std::vector<std::string>& payload,
        void* handle,
        bool diagnoseUvHandles,
        bool closeUvHandles,
        bool runUvLoopCleanup,
        bool createIsolateData,
        bool createEnvironment,
        bool loadEnvironment,
        bool spinEventLoop,
        bool inlineJavaScript,
        bool processJavaScript,
        bool consoleJavaScript,
        bool stdoutCapture,
        bool jsResult,
        bool stdoutWriteRequested,
        bool consoleDiagnostics,
        bool consoleStream,
        bool consoleShape,
        bool consoleReplaceRequested,
        bool consoleFamilyRequested = false,
        bool consoleFormatRequested = false,
        bool consoleRejectionRequested = false,
        bool consoleUncaughtRequested = false,
        bool schedulingRequested = false,
        bool schedulingOrderRequested = false,
        const std::string* sourceOverride = nullptr,
        const char* sourceLabelOverride = nullptr,
        const char* workingDirectoryOverride = nullptr,
        bool scriptExecution = false,
        bool fullUvDiagnostics = true
);
void appendEmbeddedIsolateLifecycleProbePayload(
        std::vector<std::string>& payload,
        void* handle,
        bool useUvLoop
);
void appendEmbeddedIsolateLifecycleProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedUvIsolateLifecycleProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8InitOnlyLifecycleProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvIsolateProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvDiagnosticsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvCloseProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvRunProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvIsolateDataProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvEnvironmentProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvLoadEnvironmentProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvSpinEventLoopProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvInlineJsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvProcessJsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleJsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvStdoutCaptureProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvJsResultProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvStdoutWriteProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleDiagnosticsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleStreamProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleShapeProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleReplaceProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleFamilyProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleFormatProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleRejectionProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvConsoleUncaughtProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvSchedulingProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvSchedulingOrderProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvAsyncConsoleProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvAsyncErrorProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvBootstrapProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvBootstrapScriptProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptCompletionProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptFailureProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvCompletionDrainProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPostCompletionErrorProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvOutputEventsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvOutputEnvelopeProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptCancelProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvCapabilityDescriptorProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginContractProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginRequestProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginEventsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginFailureProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginCancelProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginDisposeProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginLifecycleProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginInvalidRequestProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginStateMachineProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginTimeoutProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginCrashProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginBackpressureProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginNegotiationProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginPermissionProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginManifestProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginAbiProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginLibraryProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginInstanceProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginSessionProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginQueueProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginConcurrencyProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginRaceProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginShutdownProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginHeartbeatProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginHealthProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginTelemetryProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginDiagnosticsProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginRecoveryProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginRestartProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginRestartBudgetProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvPluginQuarantineProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptDescriptorProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptNormalizationProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptContextProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptResultV2ProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptCancellationTokenProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptTimeoutPolicyProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptMemoryPolicyProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvScriptPreflightSummaryProbePayload(std::vector<std::string>& payload, void* handle);
void appendEmbeddedV8UvInlineProbePayload(
        std::vector<std::string>& payload,
        void* handle,
        EmbeddedLifecycleJsProbeKind kind
);
jobjectArray nativeEmbeddedProbeInlineKind(
        JNIEnv* env,
        EmbeddedLifecycleJsProbeKind kind,
        int probeLevel
);

}  // namespace autojs6::node_bridge::internal
