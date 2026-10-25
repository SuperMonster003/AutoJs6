#!/usr/bin/env python3
"""Device sampling B of the api-coverage review (ROADMAP P1.3).

Runs subsets of the host's existing runtime device suites (Codex batches
runtime-java-interop / runtime-java-callbacks / runtime-flow-* / runtime-mail /
runtime-speech / runtime-ai-core / runtime-ai-agent, `app/src/androidTest/.../engine/`)
on one device and prints one line per case: pass / fail, time and the key numbers
of its evidence file. Tokens, credentials, recognized text and full tool lists are
never printed. Generic helpers come from device_sample_a.py (P1.2).

Phases (all take --serial and --state, a private directory such as a scratchpad):
  record   package versions, APK md5, settings, private plugin files and the
           owned external files of the device before sampling (state.json)
  backup   pull the original APKs (and --private packages' data) into <state>
  install  install the candidate APKs given by --apk package=path (md5 verified)
  prepare  per family fixtures: java = callback JAR / DEX, mail = synthetic
           loopback IMAP / SMTP / POP3 server, adb reverse and fixture JSON
  run      run one family (or --case ids) of the sampling list; results.json
  cleanup  stop the mail server, remove reverse mappings and the external files
           created during sampling, put back the external files recorded before
  restore  reinstall the original APKs that changed (and --private data) and
           compare with state.json
  verify   compare the device with state.json only
  list     print the sampling list

System consent dialogs (accessibility, microphone, Agent link, account login) are
answered by the maintainer on the phone; cases are annotated with what they need.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

from device_sample_a import (adb, sh, package_state as installed_package_state, md5, install_files, parse_instrumentation,
                             failure_message, fetch_evidence, summarize)

HOST = "org.autojs.autojs6"
HOST_TEST = "org.autojs.autojs6.test"
NODE = "io.github.supermonster003.autojs6.plugin.nodejs"
BUN = "io.github.supermonster003.autojs6.plugin.bun.runtime"
MAIL = "io.github.supermonster003.autojs6.plugin.three.stamp.mail"
MAIL_TEST = MAIL + ".test"
STONE = "io.github.supermonster003.autojs6.plugin.threestoneai"
STOVE = "io.github.supermonster003.autojs6.plugin.three.stove.agent"
MACAW = "io.github.supermonster003.autojs6.plugin.three.macaw.speech"
RUNNER = f"{HOST_TEST}/androidx.test.runner.AndroidJUnitRunner"
ENGINE = "org.autojs.autojs.engine."
EVIDENCE_DIR = f"/sdcard/Android/data/{HOST}/files"
ROOT = Path(__file__).resolve().parents[2]
MAIL_REPO = ROOT.parent / "AutoJs6-Plugin-Three-Stamp-Mail"
MAIL_SERVER = "io.github.supermonster003.autojs6.plugin.three.stamp.mail.core.runtime.RuntimeMailServerKt"
MAIL_ALIAS = "runtime-mail-fixture"

# Packages whose state is recorded; the first four are the ones sampling may replace.
PACKAGES = [HOST, HOST_TEST, NODE, BUN, MAIL, MAIL_TEST, MACAW, STONE, STOVE]
# Private files (shared_prefs, databases, files) compared before / after for these packages when run-as works;
# backups leave out files of 64 MiB or more (model binaries), which are compared by md5 only.
PRIVATE = [HOST, MAIL, MACAW, STONE, STOVE]
PRIVATE_DIRS = "shared_prefs databases files"
# External files the suites create or read under EVIDENCE_DIR.
OWNED_EXTERNAL = re.compile(r"^(runtime-(java|flow|mail|speech|ai)-.*|java-callback-fixtures)$")

SETTINGS = [("system", "user_rotation"), ("system", "accelerometer_rotation"),
            ("system", "screen_off_timeout"), ("global", "stay_on_while_plugged_in"),
            ("system", "screen_brightness"), ("system", "screen_brightness_mode"),
            ("secure", "enabled_accessibility_services"), ("secure", "accessibility_enabled"),
            ("secure", "enabled_notification_listeners"), ("secure", "voice_interaction_service"),
            ("secure", "voice_recognition_service"), ("secure", "assistant")]
APPOPS = [(HOST, "PROJECT_MEDIA"), (HOST, "SYSTEM_ALERT_WINDOW"), (HOST, "RECORD_AUDIO"), (MACAW, "RECORD_AUDIO")]
PERMISSIONS = [(HOST, "android.permission.RECORD_AUDIO"), (HOST, "android.permission.POST_NOTIFICATIONS"),
               (MACAW, "android.permission.RECORD_AUDIO")]
STREAMS = {"music": 3, "ring": 2, "alarm": 4, "notification": 5}


def case(family, cid, cls, method, apis, modes, evidence=None, needs=None, args=None):
    return {"family": family, "id": cid, "class": ENGINE + cls, "method": method,
            "apis": apis, "modes": modes, "evidence": evidence, "needs": needs or [], "args": args or {}}


ALL = ["main", "worker", "bun"]
BRIDGE = ["bridge"]  # native bridge tests shared by all runtime columns (no JavaScript program)
JAVA = ["java.type", "java.describe", "java.new", "java.callStatic", "java.getStatic", "java.setStatic",
        "java.cast", "java.newArray", "java.instanceOf", "java.getContext", "java.release",
        "JavaObject#call / field get / set"]
CALLBACKS = ["java.proxy", "java.wrap", "java.defineClass", "java.loadJar", "java.loadDex", "java.retain",
             "java.closeCallbacks", "GeneratedClass#callSuper"]
FLOW_CORE = ["flow.of", "flow.run", "flow.all", "flow.any", "flow.race", "flow.delay", "flow.sleep", "flow.wait",
             "flow.waitAsync", "flow.waitUntil", "flow.waitWhile", "flow.repeatUntil", "flow.defaults", "flow.pending",
             "flow.workers", "flow.waitForActivity", "flow.waitForPackage", "flow.waitForVisible", "flow.waitForGone",
             "flow.waitForHidden", "flow.waitForStable", "Flow#run", "Flow#map", "Flow#filter", "Flow#retry",
             "Flow#timeout", "Flow#catch", "Flow#whenPresent"]
FLOW_TOOLS = ["flow.clickAny", "flow.clickIfExists", "flow.clickBoundsAny", "flow.clickBoundsIfExists", "flow.findAny",
              "flow.collectList", "flow.scrollUntil", "flow.smartClick", "flow.smartClickBounds", "flow.toggle",
              "flow.typeInto", "flow.waitThenLongClick", "flow.backToApp", "flow.backUntil", "flow.launchAndWait",
              "flow.waitForEvent", "flow.waitForIdle", "flow.waitForNotification", "flow.waitForToast",
              "Flow#click", "Flow#setText", "Flow#appendText", "Flow#clear", "Flow#collectList", "Flow#dismissPopups"]
FLOW_SCOPE = ["flow.of (within / scope)", "flow.findAny", "flow.clickIfExists", "flow.waitForEvent",
              "flow.waitForToast", "Flow#press", "Flow#swipe", "Flow#waitForIdle", "Flow#toString"]
MAIL_ROOT = ["mail.providers", "mail.accounts", "mail.connect", "mail.connectAsync", "mail.setDefault", "mail.default",
             "mail.fetch", "mail.foldersAsync", "mail.searchAsync", "mail.sendAsync", "mail.flagAsync", "mail.rawAsync",
             "mail.close", "mail.MailError"]
MAIL_CLIENT = ["MailClient#test", "MailClient#testAsync", "MailClient#folder", "MailClient#folderStatus",
               "MailClient#createFolder", "MailClient#deleteFolder", "MailClient#append", "MailClient#appendAsync",
               "MailClient#fetch", "MailClient#fetchAsync", "MailClient#fetchBodyAsync", "MailClient#get",
               "MailClient#search", "MailClient#markRead", "MailClient#markUnreadAsync", "MailClient#setFlagsAsync",
               "MailClient#unflag", "MailClient#copyAsync", "MailClient#move", "MailClient#delete",
               "MailClient#deleteAsync", "MailClient#expungeAsync", "MailClient#watch", "MailClient#close"]
SPEECH = ["speech.isAvailable", "speech.capabilities", "speech.recognize", "speech.recognizeAsync", "speech.detectVoice",
          "speech.listen", "speech.synthesize", "speech.speak", "speech.SpeechError", "speech.models.list",
          "speech.models.getDefault", "speech.models.setDefault", "speech.models.importFile", "speech.models.install",
          "speech.models.installAsync", "speech.models.remove"]
AI = ["ai.ask", "ai.chat", "ai.stream", "ai.session", "ai.catalog", "AiSession#ask", "AiSession#chat",
      "AiSession#stream", "AiSession#close", "AiSession#isClosed", "AiSession#state", "AiSession#target"]
AGENT = ["ai.agent (hasAccess / requestAccess / status / presets / catalog / context / result / create / run / get / list)",
         "AgentRun#id", "AgentRun#goal", "AgentRun#startedAt", "AgentRun#detached", "AgentRun#state", "AgentRun#on",
         "AgentRun#join", "AgentRun#result", "AgentRun#error", "AgentRun#cancel", "AgentRun#respond", "AgentRun#confirm"]
A11Y = "the suite fixture enables the host accessibility service through `settings put secure` and restores it (maintainer consent)"
MODEL = "a local 3-Stone AI model (E2B preferred) is available; 3-Stone AI enabled in Plugin Center"

# The sampling list. `apis` are the API rows (matrix ids, or runtime handle / facade
# members outside the matrix denominator) the case exercises, `modes` the runtime
# columns (main = Node main thread CJS / ESM, worker = Node CJS / ESM / nested
# Worker, bun = Bun host client, bridge = native bridge level). See
# REVIEW-codex-2026-10.md "P1.3".
CASES = [
    # Java interop and callbacks (Codex runtime-java-interop / runtime-java-callbacks).
    case("java", "java-node", "RuntimeJavaInteropDeviceTest", "nodePublicJavaAndAndroidObjects", JAVA, ["main"],
         "runtime-java-interop-node.json"),
    case("java", "java-worker", "RuntimeJavaInteropDeviceTest", "workerPublicJavaAndAndroidObjects", JAVA, ["worker"],
         "runtime-java-interop-worker.json"),
    case("java", "java-bun", "RuntimeJavaInteropDeviceTest", "bunPublicJavaAndAndroidObjects", JAVA, ["bun"],
         "runtime-java-interop-bun.json"),
    case("java", "java-worker-handles", "RuntimeJavaInteropDeviceTest", "cjsEsmAndNestedWorkersReleaseOnlyTheirOwnHandles",
         ["java.new", "java.release", "JavaObject#call"], ["main", "worker"]),
    case("java", "java-native-grant", "RuntimeJavaInteropNativeTest", "publicClassMetadataAndBunDefaultGrant",
         ["java.describe", "java.type", "(Bun java.interop default off)"], BRIDGE),
    case("java", "java-native-ownership", "RuntimeJavaInteropNativeTest", "explicitDisableExecutionOwnershipAndDestruction",
         ["java.new", "java.release", "(execution ownership)"], BRIDGE),
    case("java", "jcb-node", "RuntimeJavaCallbacksDeviceTest", "nodeCallbacksSubclassJarAndDex", CALLBACKS, ["main"],
         "runtime-java-callbacks-node.json", needs=["java-callback-fixtures (prepare --family java)"]),
    case("java", "jcb-worker", "RuntimeJavaCallbacksDeviceTest", "workerCallbacksSubclassJarAndDex", CALLBACKS, ["worker"],
         "runtime-java-callbacks-worker.json", needs=["java-callback-fixtures (prepare --family java)"]),
    case("java", "jcb-bun", "RuntimeJavaCallbacksDeviceTest", "bunCallbacksSubclassJarAndDex", CALLBACKS, ["bun"],
         "runtime-java-callbacks-bun.json", needs=["java-callback-fixtures (prepare --family java)"]),
    case("java", "jcb-workers", "RuntimeJavaCallbacksDeviceTest", "esmAndNestedWorkersKeepCallbacksInTheirOwnerAndReleaseThemOnExit",
         ["java.proxy", "java.closeCallbacks", "java.callStatic"], ["main", "worker"],
         needs=["java-callback-fixtures (prepare --family java)"]),
    case("java", "jcb-snapshot", "RuntimeJavaCallbacksDeviceTest", "snapshotsAvoidUserCodeAndNestedSuperCallbacksReturn",
         ["java.defineClass", "java.proxy", "java.retain", "GeneratedClass#callSuper"], ALL,
         "runtime-java-callbacks-snapshot.json", needs=["java-callback-fixtures (prepare --family java)"]),
    case("java", "jcb-native-loaders", "RuntimeJavaCallbacksNativeTest", "loadersAreExecutionLocalAndOwnedReadOnlyCacheIsRemoved",
         ["java.loadJar", "java.loadDex"], BRIDGE, needs=["java-callback-fixtures (prepare --family java)"]),
    # Flow (Codex runtime-flow-core / runtime-flow-tools / runtime-flow-scope).
    case("flow", "flow-node", "RuntimeFlowDeviceTest", "nodeChainsAndQueriesUseRealHost", FLOW_CORE, ["main"],
         "runtime-flow-node.json", needs=[A11Y]),
    case("flow", "flow-bun", "RuntimeFlowDeviceTest", "bunChainsAndQueriesUseRealHost", FLOW_CORE, ["bun"],
         "runtime-flow-bun.json", needs=[A11Y]),
    case("flow", "flow-worker", "RuntimeFlowDeviceTest", "workerChainsAndQueriesUseRealHost", FLOW_CORE, ["worker"],
         "runtime-flow-worker.json", needs=[A11Y]),
    case("flow", "flow-unavailable", "RuntimeFlowDeviceTest", "unavailableServiceIsNotAHiddenTarget",
         ["flow.waitForVisible", "flow.findAny"], ["main", "bun"], needs=[A11Y]),
    case("flow", "flow-bun-grant", "RuntimeFlowDeviceTest", "bunLocalChainsNeedNoGrantButRawAndFacadeQueriesDo",
         ["flow.of", "flow.run", "flow.findAny"], ["bun"], needs=[A11Y]),
    case("flow", "flow-workers", "RuntimeFlowDeviceTest", "cjsEsmAndNestedWorkersReleaseTimersAndPreserveParentDefaults",
         ["flow.defaults", "flow.pending", "flow.cancelAll", "flow.delay"], ["main", "worker"],
         "runtime-flow-worker-modes.json", needs=[A11Y]),
    case("flow", "ftools-node", "RuntimeFlowToolsDeviceTest", "nodeAdvancedToolsAndEvents", FLOW_TOOLS, ["main"],
         "runtime-flow-tools-node.json", needs=[A11Y]),
    case("flow", "ftools-bun", "RuntimeFlowToolsDeviceTest", "bunAdvancedToolsAndEvents", FLOW_TOOLS, ["bun"],
         "runtime-flow-tools-bun.json", needs=[A11Y]),
    case("flow", "ftools-worker", "RuntimeFlowToolsDeviceTest", "workerAdvancedToolsAndEvents", FLOW_TOOLS, ["worker"],
         "runtime-flow-tools-worker.json", needs=[A11Y]),
    case("flow", "ftools-bun-grant", "RuntimeFlowToolsDeviceTest", "bunFlowGrantIsSeparateFromAccessibility",
         ["flow.clickAny", "(Bun flow.control separate from accessibility grants)"], ["bun"], needs=[A11Y]),
    case("flow", "fscope-node", "RuntimeFlowScopeDeviceTest", "nodeAdvancedToolsAndEvents", FLOW_SCOPE, ["main"],
         "runtime-flow-scope-node.json", needs=[A11Y]),
    case("flow", "fscope-bun", "RuntimeFlowScopeDeviceTest", "bunAdvancedToolsAndEvents", FLOW_SCOPE, ["bun"],
         "runtime-flow-scope-bun.json", needs=[A11Y]),
    case("flow", "fscope-worker", "RuntimeFlowScopeDeviceTest", "workerAdvancedToolsAndEvents", FLOW_SCOPE, ["worker"],
         "runtime-flow-scope-worker.json", needs=[A11Y]),
    # Mail (Codex runtime-mail): synthetic loopback accounts only, never a real mailbox.
    case("mail", "mail-node", "RuntimeMailDeviceTest", "nodeUsesRealMailProtocolsAndAttachments", MAIL_ROOT + MAIL_CLIENT,
         ["main"], "runtime-mail-node.json", needs=["mail fixture server + saved synthetic alias (prepare --family mail)"]),
    case("mail", "mail-bun", "RuntimeMailDeviceTest", "bunUsesRealMailProtocolsAndAttachments", MAIL_ROOT + MAIL_CLIENT,
         ["bun"], "runtime-mail-bun.json", needs=["mail fixture server + saved synthetic alias (prepare --family mail)"]),
    case("mail", "mail-worker", "RuntimeMailDeviceTest", "workerUsesRealMailProtocolsAndAttachments", MAIL_ROOT + MAIL_CLIENT,
         ["worker"], "runtime-mail-worker.json", needs=["mail fixture server + saved synthetic alias (prepare --family mail)"]),
    case("mail", "mail-token", "RuntimeMailDeviceTest", "asynchronousTokenProvidersRefreshThroughTheRealPlugin",
         ["mail.connectAsync (xoauth2 token provider)", "MailClient#test", "MailClient#close"], ALL,
         needs=["mail fixture server (prepare --family mail)"]),
    case("mail", "mail-bun-budget", "RuntimeMailDeviceTest", "bunDeniesMissingGrantAndKeepsStreamingBeyond1024Calls",
         ["mail.connectAsync", "MailClient#fetchAsync", "(Bun mail.control grant and streaming budget)"], ["bun"],
         "runtime-mail-streaming-budget.json", needs=["mail fixture server (prepare --family mail)"]),
    case("mail", "mail-workers", "RuntimeMailDeviceTest", "cjsEsmNestedWorkerExitPreservesTheParentMailClient",
         ["mail.connectAsync", "MailClient#test", "MailClient#close"], ["main", "worker"], "runtime-mail-worker-modes.json",
         needs=["mail fixture server (prepare --family mail)"]),
    case("mail", "mail-native-cancel", "RuntimeMailNativeTest", "cancelledTokenRequestsLeaveNoUndrainableTerminalPackets",
         ["(token request cancellation)"], BRIDGE),
    case("mail", "mail-native-errors", "RuntimeMailNativeTest", "invalidAccountsAndClosedClientHandlesRetainTheMailErrorCode",
         ["mail.MailError (codes)", "MailClient#close"], BRIDGE),
    # Speech (Codex runtime-speech): bundled WAV, muted synthesis, microphone opened and stopped without sound.
    case("speech", "speech-node", "RuntimeSpeechDeviceTest", "nodeRecognitionSessionsAndModels", SPEECH, ["main"],
         "runtime-speech-node.json", needs=["3-Macaw Speech with an installed streaming ASR model"]),
    case("speech", "speech-bun", "RuntimeSpeechDeviceTest", "bunRecognitionSessionsAndModels", SPEECH, ["bun"],
         "runtime-speech-bun.json", needs=["3-Macaw Speech with an installed streaming ASR model"]),
    case("speech", "speech-worker", "RuntimeSpeechDeviceTest", "workerRecognitionSessionsAndModels", SPEECH, ["worker"],
         "runtime-speech-worker.json", needs=["3-Macaw Speech with an installed streaming ASR model"]),
    case("speech", "speech-bun-budget", "RuntimeSpeechDeviceTest", "bunDeniedGrantAndMoreThan1024StreamingCalls",
         ["speech.models.list", "(Bun speech.control grant and streaming budget)"], ["bun"],
         "runtime-speech-streaming-budget.json"),
    case("speech", "speech-mic-workers", "RuntimeSpeechDeviceTest", "microphoneWakeAndEsmNestedWorkerCleanup",
         ["speech.listen", "speech.wake", "speech.capabilities"], ["main", "worker"], "runtime-speech-worker-modes.json",
         needs=["3-Macaw Speech microphone permission granted (no sound is played)"]),
    case("speech", "speech-native-pcm", "RuntimeSpeechNativeTest", "pcmUploadsStayWithTheirClientAndDisappearOnClose",
         ["speech.recognize (PCM upload)"], BRIDGE),
    case("speech", "speech-native-errors", "RuntimeSpeechNativeTest", "nativeArgumentFailuresKeepTheirSpeechCodeAcrossFragmentedPolling",
         ["speech.SpeechError (codes)"], BRIDGE),
    # AI core and Agent (Codex runtime-ai-core / runtime-ai-agent): local model only, no online account.
    case("ai", "ai-node", "RuntimeAiDeviceTest", "nodeCoreUsesRealProvider", AI, ["main"], "runtime-ai-core-node.json",
         needs=[MODEL]),
    case("ai", "ai-bun", "RuntimeAiDeviceTest", "bunCoreUsesRealProvider", AI, ["bun"], "runtime-ai-core-bun.json",
         needs=[MODEL]),
    case("ai", "ai-worker", "RuntimeAiDeviceTest", "workerCoreUsesRealProvider", AI, ["worker"], "runtime-ai-core-worker.json",
         needs=[MODEL]),
    case("ai", "ai-worker-modes", "RuntimeAiDeviceTest", "workerModesCloseSessionsAndKeepParentClientUsable",
         ["ai.catalog", "ai.session", "AiSession#isClosed", "AiSession#target"], ["main", "worker"],
         "runtime-ai-core-worker-modes.json", needs=[MODEL]),
    case("ai", "ai-denied", "RuntimeAiDeviceTest", "bunGrantAndNodeDeclarationRejectBeforeProviderAccess",
         ["ai.catalog (NOT_GRANTED / capability)"], ["main", "bun"], needs=[MODEL]),
    case("ai", "ai-native-terminal", "RuntimeAiDeviceTest", "terminalPacketsKeepCancellationOwnershipUntilAcknowledged",
         ["(terminal packet ownership)"], BRIDGE, needs=[MODEL]),
    case("ai", "agent-node", "RuntimeAgentDeviceTest", "nodeAgentUsesRealPluginAndModel", AGENT, ["main"],
         "runtime-ai-agent-node.json", needs=[MODEL, "3-Stove Agent enabled and linked to the host"]),
    case("ai", "agent-bun", "RuntimeAgentDeviceTest", "bunAgentUsesIndependentGrantWithRealPluginAndModel", AGENT, ["bun"],
         "runtime-ai-agent-bun.json", needs=[MODEL, "3-Stove Agent enabled and linked to the host"]),
    case("ai", "agent-worker", "RuntimeAgentDeviceTest", "workerAgentUsesRealPluginAndModel", AGENT, ["worker"],
         "runtime-ai-agent-worker.json", needs=[MODEL, "3-Stove Agent enabled and linked to the host"]),
    case("ai", "agent-discovery", "RuntimeAgentDeviceTest", "agentDiscoveryUsesTheOriginalProtectedService",
         ["ai.agent.status"], BRIDGE, needs=["3-Stove Agent enabled"]),
    case("ai", "agent-core-grant", "RuntimeAgentDeviceTest", "coreAiCapabilityDoesNotAuthorizeAgentTasks",
         ["ai.agent.run (denied with the core ai grant only)"], ["main", "bun"], needs=[MODEL]),
    case("ai", "agent-ownership", "RuntimeAgentDeviceTest", "nativeTaskAcknowledgmentAndExplicitDetachedCancellationRetainOwnership",
         ["ai.agent.run (detached)", "ai.agent.get", "AgentRun#cancel"], BRIDGE, "runtime-ai-agent-ownership.json",
         needs=[MODEL, "3-Stove Agent enabled and linked to the host"]),
    case("ai", "agent-worker-modes", "RuntimeAgentDeviceTest", "workerModesCancelOwnedRunsButRetainDetachedRunsAndParentClient",
         ["ai.agent.run", "ai.agent.get", "AgentRun#cancel", "AgentRun#detached"], ["main", "worker"],
         "runtime-ai-agent-worker-modes.json", needs=[MODEL, "3-Stove Agent enabled and linked to the host"]),
]


def package_state(serial, package):
    """Version, flags and split md5 of a package, or None when it is not installed (`pm path` exits 1)."""
    if "package:" not in adb(serial, "shell", f"pm path {package}", check=False):
        return None
    return installed_package_state(serial, package)


def run_as(serial, package, command, timeout=180):
    return adb(serial, "shell", f"run-as {package} sh -c '{command}'", timeout=timeout, check=False).strip()


def private_files(serial, package):
    """md5 of the package's private files, or None when run-as is not available (release build)."""
    out = run_as(serial, package, f"cd . && find {PRIVATE_DIRS} -type f 2>/dev/null | xargs -r md5sum")
    if "not debuggable" in out or "unknown package" in out or "run-as:" in out:
        return None
    files = {}
    for line in out.splitlines():
        parts = line.split(None, 1)
        if len(parts) == 2 and re.fullmatch(r"[0-9a-f]{32}", parts[0]):
            files[parts[1].strip()] = parts[0]
    return files


def volumes(serial):
    found = {}
    for name, stream in STREAMS.items():
        out = sh(serial, f"cmd media_session volume --stream {stream} --get")
        match = re.search(r"volume is (\d+)", out)
        found[name] = int(match.group(1)) if match else None
    return found


def external_files(serial):
    out = adb(serial, "shell", f"ls -1 {EVIDENCE_DIR}", check=False)
    return sorted(name.strip() for name in out.splitlines() if OWNED_EXTERNAL.match(name.strip()))


def device_state(serial):
    state = {"serial": serial,
             "model": sh(serial, "getprop ro.product.model"),
             "release": sh(serial, "getprop ro.build.version.release"),
             "sdk": sh(serial, "getprop ro.build.version.sdk"),
             "pageSize": sh(serial, "getconf PAGE_SIZE"),
             "packages": {p: package_state(serial, p) for p in PACKAGES},
             "settings": {f"{ns}/{key}": sh(serial, f"settings get {ns} {key}") for ns, key in SETTINGS},
             "appops": {f"{p}/{op}": sh(serial, f"appops get {p} {op}") for p, op in APPOPS},
             "volumes": volumes(serial),
             "external": external_files(serial),
             "private": {p: private_files(serial, p) for p in PRIVATE if package_state(serial, p)},
             "permissions": {}}
    for package, permission in PERMISSIONS:
        dump = sh(serial, f"dumpsys package {package}")
        match = re.search(re.escape(permission) + r": granted=(\w+)(?:, flags=\[([^\]]*)\])?", dump)
        state["permissions"][f"{package}/{permission}"] = match.group(0).split(": ", 1)[1] if match else None
    return state


def load(state_dir):
    return json.loads((state_dir / "state.json").read_text(encoding="utf-8"))


def save(state_dir, name, value):
    (state_dir / name).write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding="utf-8")


def record(args):
    args.state.mkdir(parents=True, exist_ok=True)
    target = args.state / "state.json"
    if target.exists() and not args.force:
        sys.exit(f"{target} exists; pass --force to record again")
    state = device_state(args.serial)
    state["agentTestId"] = str(uuid.uuid4())
    folder = args.state / "external-before"
    for name in state["external"]:
        folder.mkdir(parents=True, exist_ok=True)
        adb(args.serial, "pull", f"{EVIDENCE_DIR}/{name}", str(folder / name), check=False)
    target.write_text(json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"device {state['model']} Android {state['release']} / API {state['sdk']}, page {state['pageSize']}")
    for package, info in state["packages"].items():
        if info:
            print(f"{package}: versionCode {info['versionCode']} ({info['versionName']}), splits {len(info['splits'])}, "
                  f"md5 {','.join(s['md5'] for s in info['splits'].values())}{' debuggable' if info['debuggable'] else ''}")
        else:
            print(f"{package}: not installed")
    for group in ("settings", "appops", "permissions", "volumes"):
        for key, value in state[group].items():
            print(f"{group} {key}: {value}")
    for package, files in state["private"].items():
        print(f"private {package}: {'run-as unavailable' if files is None else f'{len(files)} files'}")
    print(f"external owned files before: {len(state['external'])}")


def backup(args):
    state = load(args.state)
    for package in PACKAGES[:4] + list(args.also or []):
        info = state["packages"].get(package)
        if not info:
            continue
        folder = args.state / "apks" / package
        folder.mkdir(parents=True, exist_ok=True)
        for name, split in info["splits"].items():
            local = folder / name
            if not local.exists() or md5(local) != split["md5"]:
                adb(args.serial, "pull", split["path"], str(local), timeout=600)
            ok = md5(local) == split["md5"]
            print(f"backup {package}/{name}: {'ok' if ok else 'MD5 MISMATCH'}")
            if not ok:
                sys.exit(1)
    for package in args.private or []:
        if state["private"].get(package) is None:
            sys.exit(f"backup {package}: private files were not recorded (run-as unavailable)")
        local = args.state / "private" / f"{package}.tar"
        local.parent.mkdir(parents=True, exist_ok=True)
        with local.open("wb") as stream:
            subprocess.run(["adb", "-s", args.serial, "exec-out", "run-as", package, "sh", "-c",
                            f"cd . && tar -cf - $(find {PRIVATE_DIRS} -type f -size -65536k 2>/dev/null)"],
                           stdout=stream, check=True, timeout=600)
        print(f"backup {package} private data: {local.stat().st_size} bytes")


def install(args):
    log = args.state / "installed.json"
    installed = json.loads(log.read_text(encoding="utf-8")) if log.exists() else {}
    for item in args.apk:
        package, path = item.split("=", 1)
        path = Path(path)
        install_files(args.serial, package, [path])
        info = package_state(args.serial, package)
        remote = next(iter(info["splits"].values()))["md5"]
        ok = remote == md5(path)
        installed[package] = {"source": str(path), "md5": remote, "versionCode": info["versionCode"],
                              "versionName": info["versionName"]}
        print(f"install {package}: versionCode {info['versionCode']} ({info['versionName']}) md5 {remote} {'ok' if ok else 'MD5 MISMATCH'}")
        if not ok:
            sys.exit(1)
    log.write_text(json.dumps(installed, indent=2), encoding="utf-8")


def prepare_java(args):
    folder = args.state / "java-fixtures"
    if not (folder / "manifest.json").exists():
        subprocess.run([sys.executable, str(ROOT / "tools/api-coverage/build-java-callback-fixtures.py"),
                        "--output", str(folder)], check=True, capture_output=True, text=True)
    remote = f"{EVIDENCE_DIR}/java-callback-fixtures"
    sh(args.serial, f"mkdir -p {remote}")
    for name in ("callback.jar", "fixture.dex"):
        adb(args.serial, "push", str(folder / name), f"{remote}/{name}")
        ok = sh(args.serial, f"md5sum {remote}/{name}").split()[0] == md5(folder / name)
        print(f"prepare java {name}: {'ok' if ok else 'MD5 MISMATCH'}")
        if not ok:
            sys.exit(1)


def prepare_mail(args):
    folder = args.state / "mail-server"
    folder.mkdir(parents=True, exist_ok=True)
    process_file = folder / "process.json"
    if process_file.exists() and (folder / "ready.json").exists():
        print("prepare mail: fixture server already running")
    else:
        classpath = (MAIL_REPO / "mail-core/build/test-classpath.txt").read_text(encoding="utf-8")
        ports = []
        import socket
        sockets = [socket.socket() for _ in range(3)]
        try:
            for item in sockets:
                item.bind(("127.0.0.1", 0))
                ports.append(item.getsockname()[1])
        finally:
            for item in sockets:
                item.close()
        stop, ready = folder / "stop", folder / "ready.json"
        stop.unlink(missing_ok=True)
        command = [args.java, "-Xmx512m", "-cp", classpath, MAIL_SERVER, f"imapPort={ports[0]}", f"smtpPort={ports[1]}",
                   f"pop3Port={ports[2]}", f"stopFile={stop}", f"readyFile={ready}"]
        log = (folder / "server.log").open("w", encoding="utf-8")
        flags = getattr(subprocess, "CREATE_NO_WINDOW", 0) | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
        deadline = time.monotonic() + 60
        while not ready.exists():
            if process.poll() is not None or time.monotonic() > deadline:
                sys.exit("prepare mail: fixture server did not start (see server.log)")
            time.sleep(0.2)
        save(folder, "process.json", {"pid": process.pid, "ports": ports, "listen": "127.0.0.1"})
        print(f"prepare mail: synthetic IMAP / SMTP / POP3 server pid {process.pid} ready")
    fixture = json.loads((folder / "ready.json").read_text(encoding="utf-8"))
    existing = adb(args.serial, "reverse", "--list", check=False)
    owned = []
    for key in ("imapPort", "smtpPort", "pop3Port"):
        port = f"tcp:{fixture[key]}"
        if port not in existing:
            adb(args.serial, "reverse", "--no-rebind", port, port)
            owned.append(port)
    previous = json.loads((args.state / "reverse-owned.json").read_text(encoding="utf-8")) if (args.state / "reverse-owned.json").exists() else []
    save(args.state, "reverse-owned.json", sorted(set(previous + owned)))
    local = folder / "runtime-mail-fixture.json"
    local.write_text(json.dumps(fixture, indent=2) + "\n", encoding="utf-8")
    adb(args.serial, "push", str(local), f"{EVIDENCE_DIR}/runtime-mail-fixture.json")
    print(f"prepare mail: reverse mappings {len(owned)} new, fixture JSON pushed")
    if package_state(args.serial, MAIL_TEST):
        out = adb(args.serial, "shell", "am", "instrument", "-w", "-r", "-e", "imapPort", str(fixture["imapPort"]),
                  "-e", "smtpPort", str(fixture["smtpPort"]), "-e", "class", f"{MAIL}.RuntimeMailAccountFixtureTest",
                  f"{MAIL_TEST}/androidx.test.runner.AndroidJUnitRunner", timeout=300, check=False)
        seeded = "OK (1 test)" in out
        print(f"prepare mail: synthetic alias {MAIL_ALIAS} {'seeded' if seeded else 'not seeded: ' + failure_message(out)}")
    else:
        print(f"prepare mail: {MAIL_TEST} not installed, synthetic alias not seeded")


def prepare(args):
    {"java": prepare_java, "mail": prepare_mail}[args.family](args)


def run(args):
    state = load(args.state)
    selected = [c for c in CASES if (args.family and c["family"] == args.family) or (args.case and c["id"] in args.case)]
    if not selected:
        sys.exit("no cases selected")
    out_dir = args.state / "runs" / (args.family or "cases")
    out_dir.mkdir(parents=True, exist_ok=True)
    results_path = args.state / "results.json"
    results = json.loads(results_path.read_text(encoding="utf-8")) if results_path.exists() else {}
    for item in selected:
        if item["evidence"]:
            sh(args.serial, f"rm -f {EVIDENCE_DIR}/{item['evidence']}")
        extra = [part for key, value in item["args"].items() for part in ("-e", key, value)]
        if item["class"].endswith("AgentDeviceTest"):
            extra += ["-e", "runtimeAgentTestId", state["agentTestId"]]
        started = time.monotonic()
        output = adb(args.serial, "shell", "am", "instrument", "-w", "-r", *extra, "-e", "class",
                     f"{item['class']}#{item['method']}", RUNNER, timeout=args.timeout, check=False)
        elapsed = round(time.monotonic() - started, 1)
        (out_dir / f"{item['id']}.log").write_text(output, encoding="utf-8")
        tests = parse_instrumentation(output)
        status = tests.get(item["method"], "fail" if "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output or not tests else "unknown")
        if status == "pass" and ("FAILURES!!!" in output or "OK (" not in output):
            status = "fail"
        entry = {"status": status, "seconds": elapsed, "apis": item["apis"], "modes": item["modes"],
                 "class": item["class"], "method": item["method"], "at": time.strftime("%Y-%m-%d %H:%M:%S")}
        if status != "pass":
            entry["message"] = failure_message(output)
        if item["evidence"]:
            evidence = fetch_evidence(args.serial, item["evidence"], out_dir)
            if evidence is not None:
                entry["numbers"] = summarize(evidence)[:40]
        results[item["id"]] = entry
        results_path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
        line = f"{item['id']} {status} {elapsed}s [{'/'.join(item['modes'])}]"
        if entry.get("message"):
            line += f" :: {entry['message']}"
        print(line, flush=True)
        if entry.get("numbers") and args.numbers:
            print("    " + ", ".join(entry["numbers"][:args.numbers]), flush=True)


def cleanup(args):
    state = load(args.state)
    folder = args.state / "mail-server"
    if (folder / "process.json").exists() and not (folder / "stopped.json").exists():
        info = json.loads((folder / "process.json").read_text(encoding="utf-8"))
        (folder / "stop").write_text("stop\n", encoding="utf-8")
        deadline = time.monotonic() + 30
        while (folder / "ready.json").exists() and time.monotonic() < deadline:
            time.sleep(0.5)
        alive = subprocess.run(["tasklist", "/FI", f"PID eq {info['pid']}", "/NH"], capture_output=True, text=True).stdout
        if str(info["pid"]) in alive:
            subprocess.run(["taskkill", "/PID", str(info["pid"]), "/T", "/F"], capture_output=True)
        save(folder, "stopped.json", {"pid": info["pid"]})
        print(f"cleanup: mail fixture server pid {info['pid']} stopped")
    owned = args.state / "reverse-owned.json"
    if owned.exists():
        for port in json.loads(owned.read_text(encoding="utf-8")):
            adb(args.serial, "reverse", "--remove", port, check=False)
        owned.unlink()
        print("cleanup: owned reverse mappings removed")
    removed = 0
    for name in external_files(args.serial):
        if name not in state["external"]:
            sh(args.serial, f"rm -rf {EVIDENCE_DIR}/{name}")
            removed += 1
    restored = 0
    for name in state["external"]:
        local = args.state / "external-before" / name
        if local.is_file():
            adb(args.serial, "push", str(local), f"{EVIDENCE_DIR}/{name}")
            restored += 1
    print(f"cleanup: external files removed {removed}, put back {restored}")


def same_private(before, now):
    if before is None or now is None:
        return before == now, []
    changed = sorted(set(before) ^ set(now) | {f for f in set(before) & set(now) if before[f] != now[f]})
    return not changed, changed


def compare(serial, state, packages):
    differences = []
    for package in packages:
        before = state["packages"].get(package)
        now = package_state(serial, package)
        same = (before is None and now is None) or (
            before is not None and now is not None and before["versionCode"] == now["versionCode"]
            and sorted(s["md5"] for s in before["splits"].values()) == sorted(s["md5"] for s in now["splits"].values()))
        if not same:
            differences.append(package)
        print(f"verify {package}: {'same' if same else 'CHANGED'}"
              + ("" if same or now is None else f" (now versionCode {now['versionCode']})"))
    current = {"settings": {f"{ns}/{key}": sh(serial, f"settings get {ns} {key}") for ns, key in SETTINGS},
               "appops": {f"{p}/{op}": sh(serial, f"appops get {p} {op}") for p, op in APPOPS},
               "volumes": volumes(serial)}
    for group, values in current.items():
        for key, value in values.items():
            same = normalize(key, state[group].get(key)) == normalize(key, value)
            if not same:
                differences.append(key)
            print(f"verify {group} {key}: {'same' if same else 'CHANGED: ' + str(normalize(key, value))}")
    for package, files in state["private"].items():
        same, changed = same_private(files, private_files(serial, package))
        if not same:
            differences.append(f"{package} private")
        print(f"verify private {package}: {'same' if same else 'CHANGED ' + ', '.join(changed[:12])}")
    leftover = [name for name in external_files(serial) if name not in state["external"]]
    if leftover:
        differences.append("external files")
    print(f"verify external owned files: {'same' if not leftover else 'NEW ' + ', '.join(leftover)}")
    return differences


def normalize(key, value):
    """Service lists compare as sets; app ops compare by mode, not by their relative access times."""
    if not isinstance(value, str):
        return value
    if key.startswith("secure/enabled_"):
        return ":".join(sorted(part for part in value.split(":") if part and part != "null"))
    if "/" in key and key.split("/", 1)[1].isupper():
        match = re.match(r"\w+: (\w+)", value)
        return match.group(1) if match else value
    return value


def restore_private(serial, state_dir, state, package):
    local = state_dir / "private" / f"{package}.tar"
    if not local.is_file():
        sys.exit(f"restore {package}: no private backup")
    adb(serial, "shell", "am", "force-stop", package)
    now = private_files(serial, package) or {}
    for name in sorted(set(now) - set(state["private"][package] or {})):
        run_as(serial, package, f"rm -f {name}")
    remote = "cache/p13-restore.tar"
    with local.open("rb") as stream:
        subprocess.run(["adb", "-s", serial, "exec-in", "run-as", package, "sh", "-c", f"cat > {remote}"],
                       stdin=stream, check=True, timeout=600)
    if run_as(serial, package, f"md5sum {remote}").split()[:1] != [md5(local)]:
        run_as(serial, package, f"rm -f {remote}")
        sys.exit(f"restore {package}: private archive transfer mismatch")
    run_as(serial, package, f"tar -xf {remote} && rm -f {remote}")
    same, changed = same_private(state["private"][package], private_files(serial, package))
    print(f"restore {package} private data: {'same' if same else 'CHANGED ' + ', '.join(changed[:12])}")


def restore(args):
    state = load(args.state)
    for package in PACKAGES:
        if not (args.state / "apks" / package).is_dir() and package not in PACKAGES[:4]:
            continue
        before = state["packages"].get(package)
        now = package_state(args.serial, package)
        if before is None:
            if now is not None:
                print(f"restore {package}: installed during sampling, uninstalling")
                adb(args.serial, "uninstall", package)
            continue
        if now and now["versionCode"] == before["versionCode"] and sorted(s["md5"] for s in now["splits"].values()) == sorted(s["md5"] for s in before["splits"].values()):
            print(f"restore {package}: unchanged")
            continue
        files = [args.state / "apks" / package / name for name in before["splits"]]
        install_files(args.serial, package, files)
        print(f"restore {package}: reinstalled versionCode {before['versionCode']}")
    for package in args.private or []:
        restore_private(args.serial, args.state, state, package)
    differences = compare(args.serial, state, PACKAGES)
    print("restore: device matches the recorded state" if not differences else f"restore: differences {differences}")
    sys.exit(1 if differences else 0)


def verify(args):
    sys.exit(1 if compare(args.serial, load(args.state), PACKAGES) else 0)


def list_cases(args):
    for item in CASES:
        if args.family and item["family"] != args.family:
            continue
        needs = f" needs: {', '.join(item['needs'])}" if item["needs"] else ""
        print(f"{item['family']:7} {item['id']:22} {item['class'].rsplit('.', 1)[1]}#{item['method']} [{'/'.join(item['modes'])}] {len(item['apis'])} apis{needs}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["record", "backup", "install", "prepare", "run", "cleanup", "restore", "verify", "list"])
    parser.add_argument("--serial", default="968e9f18")
    parser.add_argument("--state", type=Path, help="private state directory (APK copies, logs, results)")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--also", nargs="*", help="backup: extra packages")
    parser.add_argument("--private", nargs="*", help="backup / restore: packages whose private data is archived via run-as")
    parser.add_argument("--apk", action="append", default=[], help="install: package=path")
    parser.add_argument("--family")
    parser.add_argument("--case", nargs="*")
    parser.add_argument("--java", default="E:/.java/jdk-21.0.12.1/bin/java.exe", help="prepare mail: JDK for the fixture server")
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--numbers", type=int, default=12, help="run: key numbers printed per case")
    args = parser.parse_args()
    if args.phase != "list" and not args.state:
        parser.error("--state is required")
    if args.phase == "prepare" and args.family not in ("java", "mail"):
        parser.error("prepare needs --family java or mail")
    {"record": record, "backup": backup, "install": install, "prepare": prepare, "run": run, "cleanup": cleanup,
     "restore": restore, "verify": verify, "list": list_cases}[args.phase](args)


if __name__ == "__main__":
    main()
