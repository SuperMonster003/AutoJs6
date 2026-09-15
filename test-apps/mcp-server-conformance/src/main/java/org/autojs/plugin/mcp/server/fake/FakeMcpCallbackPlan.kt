package org.autojs.plugin.mcp.server.fake

/** How many callbacks a session sends per transition and which hostile extras it adds. */
data class FakeMcpCallbackPlan(
    /** `onStatus(running)` calls after the startup probe and after every `updateConfig`. */
    val runningStatusCallbacks: Int,
    /** `onStatus(stopped)` calls after `stop()` and after a `close()` of a running session. */
    val stoppedStatusCallbacks: Int,
    /** Extra `onStatus(stopped)` calls sent after `close()` returned. */
    val statusCallbacksAfterClose: Int,
    /** `onEvent(warning)` calls after reaching `running`. */
    val eventCallbacks: Int,
    /** `statusLastError` carries [FakeMcpServerFixture.OVERSIZED_ERROR_BYTES] characters. */
    val oversizedStatus: Boolean,
    /** One broker request above the inline JSON cap is dispatched during startup. */
    val oversizedBrokerRequest: Boolean,
    /** The service process kills itself shortly after the `running` callbacks. */
    val killProcessAfterRunning: Boolean,
) {

    companion object {
        fun forScenario(scenario: FakeMcpServerScenario): FakeMcpCallbackPlan = when (scenario) {
            FakeMcpServerScenario.NORMAL,
            FakeMcpServerScenario.WRONG_CONTRACT_VERSION,
            -> FakeMcpCallbackPlan(1, 1, 0, 1, oversizedStatus = false, oversizedBrokerRequest = false, killProcessAfterRunning = false)

            FakeMcpServerScenario.OVERSIZED_BUNDLE ->
                FakeMcpCallbackPlan(1, 1, 0, 1, oversizedStatus = true, oversizedBrokerRequest = true, killProcessAfterRunning = false)

            FakeMcpServerScenario.DUPLICATE_CALLBACK ->
                FakeMcpCallbackPlan(2, 2, 1, 2, oversizedStatus = false, oversizedBrokerRequest = false, killProcessAfterRunning = false)

            FakeMcpServerScenario.DEATH_CALLBACK ->
                FakeMcpCallbackPlan(1, 1, 0, 1, oversizedStatus = false, oversizedBrokerRequest = false, killProcessAfterRunning = true)
        }
    }
}
