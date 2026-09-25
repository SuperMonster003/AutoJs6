# P7 independent Agent peer conformance

Production host APK 5296; API 37.1 / 16 KiB private AVD emulator-5586. No production host code or published AIDL/AAR changed. The independent fake Agent remains debug-only/testOnly with a signature-protected, host-package/signer-checked control service and no network/device permissions.

`AiAgentPeerConformanceTest`: **6/6, 5.551 s**. Production AiAgentLinkController/AiAgentRunRegistry are exercised with injected test discovery/transport and a real remote IAiAgentPlugin/IAiAgentLink. This does not relax production package pinning. UID/PID/remote-interface checks, real bind/unbind leases and callback UID checks exclude accidental local mocks.

- startRun emits an echo before returning its admission response; the host handle receives the completion. Detach revokes the old capability grant and closes its binding.
- Invalid status/event kinds, queue overflow, wrong Bundle type, contract mismatch and oversized callbacks cannot alter the attachment. Old-generation status/events cannot affect the replacement.
- Wrong run ID, stale/duplicate sequence and post-terminal completion cannot overwrite the accepted result. Malformed run events fail only that handle with INVALID_REQUEST, leaving later tasks usable.
- Unexpected descriptors on link status, link event and run event are closed in the receiver. After both sender-side copies close, a real pipe reports EPIPE, proving receiver closure.
- Killing the fake Agent process produces actual Binder death, fails the old host handle, closes the lease and reattaches without replay. An explicitly started new task still completes.

Existing `AiAgentGrantConformanceTest`: **9/9, 3.419 s**, including foreign-UID entry rejection, quotas/rate/size/revocation and actual broadcasts with three unauthorized identities. Both copies of the test-only control AIDL are byte-identical. Fake-Agent build/lint and host instrumentation build/test-source lint passed; fixture lint has 0 errors / 2 existing manifest warnings. Whole-host lint is still pending.

Reciprocal Agent-repository `test-apps:fake-host`: **4/4, 1.288 s**, using production Agent build 67 on a newly created `AI_Agent_Conformance_P7` AVD with separate data (emulator-5588). Fake host package/version/signer satisfies the unchanged production verifier. Its unexported broker process dies independently of instrumentation, proving running/queued tasks become blocked and fresh attachment does not replay. Other cases cover attach/start/cancel/detach, grant widening, disabled tools and explicit capability denial observations. No real host installation, model credentials, device actions or orders are involved.

The original P7 full fake Agent/fake host item is satisfied. P1.3 now has independent attach/detach/death evidence, while positive production attach-broadcast acceptance remains explicit and pending. This evidence does not close compatibility, security/UI, whole-host lint or release gates. Reproduction commands are in `test-apps/ai-agent-conformance/README.md` and the Agent fake-host README. Local logs remain under ignored build directories.
