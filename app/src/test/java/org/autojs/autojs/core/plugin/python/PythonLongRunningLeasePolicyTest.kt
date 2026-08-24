package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PythonLongRunningLeasePolicyTest {
    @Test
    fun providerMustStartWithinTheInitialLease() {
        val policy = policy(startLease = 100, heartbeatLease = 50, serviceLease = 1_000)
        policy.onForegroundServicePulse(100)
        assertNull(policy.failureAt(100))
        assertEquals(
            PythonLongRunningLeasePolicy.Failure.START_LEASE_EXPIRED,
            policy.failureAt(101),
        )
    }

    @Test
    fun orderedHeartbeatsRenewOnlyTheProviderLease() {
        val policy = policy(startLease = 100, heartbeatLease = 50, serviceLease = 1_000)
        policy.onProviderStarted(10)
        policy.onProviderHeartbeat(50)
        policy.onForegroundServicePulse(90)
        assertNull(policy.failureAt(100))
        assertEquals(
            PythonLongRunningLeasePolicy.Failure.HEARTBEAT_LEASE_EXPIRED,
            policy.failureAt(101),
        )
    }

    @Test
    fun foregroundServiceMustRemainAliveIndependentlyOfProviderHeartbeats() {
        val policy = policy(startLease = 100, heartbeatLease = 1_000, serviceLease = 20)
        policy.onProviderStarted(1)
        policy.onProviderHeartbeat(20)
        assertEquals(
            PythonLongRunningLeasePolicy.Failure.FOREGROUND_SERVICE_LEASE_EXPIRED,
            policy.failureAt(21),
        )

        val stopped = policy(startLease = 100, heartbeatLease = 1_000, serviceLease = 20)
        stopped.onForegroundServiceStopped()
        assertEquals(
            PythonLongRunningLeasePolicy.Failure.FOREGROUND_SERVICE_STOPPED,
            stopped.failureAt(0),
        )
    }

    @Test
    fun providerSignalOrderAndMonotonicTimeFailClosed() {
        val beforeStart = policy()
        assertThrows(IllegalStateException::class.java) {
            beforeStart.onProviderHeartbeat(1)
        }

        val backwards = policy()
        backwards.onProviderStarted(10)
        assertThrows(IllegalArgumentException::class.java) {
            backwards.onProviderHeartbeat(9)
        }

        val lateStart = policy(startLease = 10)
        assertThrows(IllegalStateException::class.java) {
            lateStart.onProviderStarted(11)
        }

        val lateHeartbeat = policy(heartbeatLease = 10, serviceLease = 1_000)
        lateHeartbeat.onProviderStarted(1)
        assertThrows(IllegalStateException::class.java) {
            lateHeartbeat.onProviderHeartbeat(12)
        }
    }

    private fun policy(
        startLease: Long = 100,
        heartbeatLease: Long = 50,
        serviceLease: Long = 20,
    ) = PythonLongRunningLeasePolicy(
        createdAtNanos = 0,
        startLeaseNanos = startLease,
        heartbeatLeaseNanos = heartbeatLease,
        foregroundServiceLeaseNanos = serviceLease,
    )
}
