package org.autojs.autojs.core.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class LocalNetworkDiagnosticsTest {
    @Test fun loopbackAndUnspecifiedAddressesDoNotGetPermissionHints() {
        listOf("localhost", "LOCALHOST.", "test.localhost", "127.0.0.1", "127.2.3.4", "::1", "[::1]", "::ffff:127.0.0.1", "0.0.0.0", "::").forEach {
            assertFalse(it, LocalNetworkDiagnostics.couldBeLocalHost(it))
        }
    }

    @Test fun privateLinkLocalSharedAndMulticastAddressesCanGetHints() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.254", "192.168.1.1", "169.254.1.2", "100.64.0.1", "100.127.255.254", "224.0.0.251", "239.1.2.3", "255.255.255.255", "fe80::1", "fd00::1", "ff02::fb").forEach {
            assertTrue(it, LocalNetworkDiagnostics.couldBeLocalHost(it))
        }
    }

    @Test fun publicIpv4BoundariesDoNotGetHints() {
        listOf("172.15.255.255", "172.32.0.0", "100.63.255.255", "100.128.0.0", "8.8.8.8", "1.1.1.1").forEach {
            assertFalse(it, LocalNetworkDiagnostics.couldBeLocalHost(it))
        }
    }

    @Test fun namesAndIpv6RoutesAreOnlyCandidatesNotAuthorizationDecisions() {
        listOf("workstation.local", "workstation", "example.com", "2001:db8::1").forEach {
            assertTrue(it, LocalNetworkDiagnostics.couldBeLocalHost(it))
        }
    }

    @Test fun tlsAndCancellationFailuresAreNotDiagnosedAsPermissionFailures() {
        assertFalse(LocalNetworkDiagnostics.isConnectionFailure(SSLHandshakeException("certificate rejected")))
        assertFalse(LocalNetworkDiagnostics.isConnectionFailure(InterruptedIOException("cancelled")))
        assertTrue(LocalNetworkDiagnostics.isConnectionFailure(SocketTimeoutException("connect timeout")))
        assertTrue(LocalNetworkDiagnostics.isConnectionFailure(ConnectException("connect failed")))
        assertTrue(LocalNetworkDiagnostics.isCallTimeout(InterruptedIOException("timeout")))
        assertFalse(LocalNetworkDiagnostics.isCallTimeout(InterruptedIOException("cancelled")))
        assertFalse(LocalNetworkDiagnostics.isCallTimeout(SocketTimeoutException("timeout")))
    }
}
