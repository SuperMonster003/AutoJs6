package org.autojs.autojs.core.http

import android.os.Build
import android.security.NetworkSecurityPolicy
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Proxy
import java.security.cert.CertificateException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLPeerUnverifiedException

/** Real TLS and Rhino requests; the explicit fixture is required, never silently skipped. */
class NetworkSecurityCompatibilityDeviceTest {
    private val args get() = InstrumentationRegistry.getArguments()
    private val fixtureHost get() = requireNotNull(args.getString("sdk37.tlsHost")) { "Start the SDK 37 TLS fixture and pass sdk37.tlsHost" }
    private val fixturePort get() = args.getString("sdk37.tlsPort")?.toInt() ?: 17647

    private fun client() = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                check(hostname.endsWith(".sdk37.test"))
                return listOf(InetAddress.getByName(fixtureHost))
            }
        })
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun request(host: String) {
        client().newCall(Request.Builder().url("https://$host:$fixturePort/$host").build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("sdk37-tls-ok", it.body!!.string())
            Log.i(TAG, "accepted $host, tls=${it.handshake!!.tlsVersion}, peer=${it.handshake!!.peerPrincipal}")
        }
    }

    private fun rejected(host: String, transparency: Boolean = false) {
        val error = runCatching { request(host) }.exceptionOrNull()
        assertNotNull("TLS unexpectedly accepted $host", error)
        val causes = generateSequence(error) { it.cause }.take(16).toList()
        assertTrue("Not a certificate failure: $error", causes.any { it is CertificateException || it is SSLPeerUnverifiedException })
        val messages = causes.joinToString(" <- ") { "${it.javaClass.name}: ${it.message}" }
        if (transparency) {
            assertTrue("Certificate rejected without CT evidence: $messages",
                messages.contains("transparency", ignoreCase = true) || messages.contains("SCT") || messages.contains("CT verification"))
        }
        Log.i(TAG, "rejected $host: $messages")
    }

    @Test fun defaultTrustRejectsThePrivateCa() = rejected("untrusted.sdk37.test")

    @Test fun debugCaTrustIsLimitedToItsDeclaredDomains() {
        request("trusted.sdk37.test")
        rejected("untrusted.sdk37.test")
    }

    @Test fun explicitCtRejectsAnUnloggedButTrustedChain() {
        request("trusted.sdk37.test")
        if (Build.VERSION.SDK_INT >= 36) {
            assertTrue(NetworkSecurityPolicy.getInstance().isCertificateTransparencyVerificationRequired("ct.sdk37.test"))
            rejected("ct.sdk37.test", transparency = true)
        } else {
            request("ct.sdk37.test")
            Log.i(TAG, "API ${Build.VERSION.SDK_INT}: CT configuration is not supported")
        }
    }

    @Test fun ctAndEchPolicyDefaultsMatchTheRecordedCompatState() {
        val policy = NetworkSecurityPolicy.getInstance()
        if (Build.VERSION.SDK_INT >= 36) {
            val expected = requireNotNull(args.getString("sdk37.defaultCt")).toBooleanStrict()
            assertEquals(expected, policy.isCertificateTransparencyVerificationRequired("untrusted.sdk37.test"))
            assertFalse(policy.isCertificateTransparencyVerificationRequired("trusted.sdk37.test"))
            assertFalse(policy.isCertificateTransparencyVerificationRequired("raw.githubusercontent.com"))
            if (Build.VERSION.SDK_INT >= 37) assertFalse(policy.isCertificateTransparencyVerificationRequired("localhost"))
            Log.i(TAG, "defaultCt=$expected, privateCaCt=false, githubResourceCaCt=false")
        }
        if (Build.VERSION.SDK_INT >= 37) {
            val expected = requireNotNull(args.getString("sdk37.echMode")).toInt()
            val mode = policy.getDomainEncryptionMode("untrusted.sdk37.test")
            Log.i(TAG, "ECH policy=$mode; a policy query does not prove ECH negotiation")
            assertEquals("ECH policy mode", expected, mode)
        }
    }

    @Test fun insecureOptionDoesNotLeakToTheNextRhinoRequest() = runRhino("isInsecure")

    @Test fun insecureAliasDoesNotLeakToTheNextRhinoRequest() = runRhino("insecure")

    private fun runRhino(option: String) {
        val address = InetAddress.getByName(fixtureHost).hostAddress!!
        val source = """
            var client = http.client();
            var dns = new JavaAdapter(Packages.okhttp3.Dns, {
                lookup: function (host) { return java.util.Collections.singletonList(java.net.InetAddress.getByName("$address")); }
            });
            http.__okhttp__.muteClient(client.newBuilder().dns(dns).proxy(java.net.Proxy.NO_PROXY));
            var url = "https://untrusted.sdk37.test:$fixturePort/rhino-$option";
            function fetch(options) {
                var response = http.get(url, options);
                if (response.statusCode !== 200 || response.body.string() !== "sdk37-tls-ok") throw new Error("Wrong fixture response");
            }
            function reject(options, label) {
                var error = null;
                try { fetch(options); } catch (e) { error = e; }
                if (error === null) throw new Error(label + ": untrusted TLS was accepted");
                if (!/certificate|trust anchor|CertPath|SSLPeerUnverified|transparency/i.test(String(error))) throw error;
                console.log("SDK37-TLS " + label + ": " + error);
            }
            var trustedUrl = "https://trusted.sdk37.test:$fixturePort/rhino-$option";
            var mismatchUrl = "https://mismatch.sdk37.test:$fixturePort/rhino-$option";
            function trusted() {
                if (http.get(trustedUrl, {timeout: 5000}).body.string() !== "sdk37-tls-ok") throw new Error("Lost trusted CA or DNS configuration");
            }
            trusted();
            reject({timeout: 5000}, "initial default");
            fetch({$option: true, timeout: 5000});
            reject({timeout: 5000}, "default after insecure");
            fetch({$option: true, timeout: 5000});
            reject({$option: false, timeout: 5000}, "explicit false after insecure");
            trusted();
            var hostnameError = null;
            try { http.get(mismatchUrl, {timeout: 5000}); } catch (e) { hostnameError = e; }
            if (!hostnameError || !/Hostname.*not verified/i.test(String(hostnameError))) throw new Error("Hostname validation was not restored: " + hostnameError);
            var latch = new java.util.concurrent.CountDownLatch(1);
            var callbackError = new java.util.concurrent.atomic.AtomicReference();
            http.get(url, {$option: true, timeout: 5000}, function (response, error) {
                try {
                    if (error) throw error;
                    if (response.body.string() !== "sdk37-tls-ok") throw new Error("Wrong callback fixture response");
                } catch (e) { callbackError.set(String(e)); } finally { latch.countDown(); }
            });
            if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new Error("Insecure callback timed out");
            if (callbackError.get() !== null) throw new Error(String(callbackError.get()));
            reject({timeout: 5000}, "default after insecure callback");
            var promiseLatch = new java.util.concurrent.CountDownLatch(1);
            var promiseError = new java.util.concurrent.atomic.AtomicReference();
            var promiseResponse = new java.util.concurrent.atomic.AtomicReference();
            http.getAsync(url, {$option: true, timeout: 5000}).then(function (response) {
                promiseResponse.set(response);
                promiseLatch.countDown();
            }, function (error) { promiseError.set(String(error)); promiseLatch.countDown(); });
            if (!promiseLatch.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new Error("Insecure Promise timed out");
            if (promiseError.get() !== null) throw new Error(String(promiseError.get()));
            // Promise handlers run on the UI thread; consume the network body here.
            if (promiseResponse.get().body.string() !== "sdk37-tls-ok") throw new Error("Wrong Promise fixture response");
            reject({timeout: 5000}, "default after insecure Promise");
            runtime.putProperty("sdk37.tlsCompleted", true);
            runtime.getProperty("sdk37.tlsLatch").countDown();
        """.trimIndent()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                (execution.engine as RhinoJavaScriptEngine).runtime.putProperty("sdk37.tlsLatch", completed)
            }
            override fun onSuccess(execution: ScriptExecution, result: Any?) { completed.countDown() }
            override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error); completed.countDown() }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("sdk37-tls-$option", source), listener, ExecutionConfig())
        try {
            assertTrue("Rhino TLS script timed out", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Rhino TLS option $option", it) }
            val engine = execution.engine as RhinoJavaScriptEngine
            assertEquals(true, engine.runtime.getProperty("sdk37.tlsCompleted"))
            Log.i(TAG, "Rhino $option: private CA rejection restored after each explicit insecure request")
        } finally {
            (execution.engine as? RhinoJavaScriptEngine)?.let { if (it.thread.isAlive) it.forceStop() }
        }
    }

    companion object { private const val TAG = "SDK37-TLS" }
}
