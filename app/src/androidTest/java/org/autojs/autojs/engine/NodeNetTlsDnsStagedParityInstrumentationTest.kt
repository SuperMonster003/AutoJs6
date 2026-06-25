package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeNetTlsDnsStagedParityInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun netTlsDnsStagedCompatibilityStaysLimitedAndFailClosed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-30 net/tls/dns staged compatibility regression")

        val workDir = File(context.cacheDir, "node-net-tls-dns-staged-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create net/tls/dns staged work dir: $absolutePath" }
        }

        try {
            writeShadowModule(workDir, "net")
            writeShadowModule(workDir, "tls")
            writeShadowModule(workDir, "dns")
            File(workDir, "node_modules/dns/promises.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true, id: "dns/promises-shadow" };""" + "\n", StandardCharsets.UTF_8)
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const net = require("net");
                      const nodeNet = require("node:net");
                      const tls = require("tls");
                      const nodeTls = require("node:tls");
                      const dns = require("dns");
                      const nodeDns = require("node:dns");
                      const dnsPromises = require("dns/promises");
                      const nodeDnsPromises = require("node:dns/promises");

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      async function asyncCode(action) {
                        try {
                          await action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      function dnsLookup(hostname, options) {
                        return new Promise((resolve, reject) => {
                          dns.lookup(hostname, options, (error, address, family) => {
                            if (error) {
                              reject(error);
                              return;
                            }
                            resolve(Array.isArray(address)
                              ? address.map((entry) => entry.address + ":" + entry.family).join(",")
                              : String(address) + ":" + String(family));
                          });
                        });
                      }

                      function dnsLookupCode(hostname) {
                        return new Promise((resolve) => {
                          dns.lookup(hostname, (error) => {
                            resolve(error ? codeOf(error) : "allowed");
                          });
                        });
                      }

                      function dnsResolveCode(method, hostname) {
                        return new Promise((resolve) => {
                          method(hostname, (error) => {
                            resolve(error ? codeOf(error) : "allowed");
                          });
                        });
                      }

                      const socket = new net.Socket({ allowHalfOpen: true });
                      const socketWriteCode = await new Promise((resolve) => {
                        let returned = "unset";
                        returned = String(socket.write("payload", (error) => {
                          resolve(returned + ":" + codeOf(error));
                        }));
                      });
                      socket.end();
                      socket.destroy();
                      const socketHasFd = Object.prototype.hasOwnProperty.call(socket, "fd");

                      const serverConnections = await new Promise((resolve, reject) => {
                        net.createServer().getConnections((error, count) => {
                          if (error) {
                            reject(error);
                            return;
                          }
                          resolve(count);
                        });
                      });
                      const address = new net.SocketAddress({ address: "127.0.0.1", port: 8080 });
                      const parsed = net.SocketAddress.parse("127.0.0.1:8080");
                      const blockList = new net.BlockList();
                      blockList.addAddress("127.0.0.2");
                      blockList.addRange("127.0.0.10", "127.0.0.12");
                      blockList.addSubnet("127.0.1.0", 24);
                      const attemptTimeoutBefore = net.getDefaultAutoSelectFamilyAttemptTimeout();
                      net.setDefaultAutoSelectFamily(false);
                      net.setDefaultAutoSelectFamilyAttemptTimeout(275);
                      const attemptTimeoutAfter = net.getDefaultAutoSelectFamilyAttemptTimeout();
                      const netDenials = [
                        syncCode(() => net.connect(80, "127.0.0.1")),
                        syncCode(() => nodeNet.createConnection(80, "127.0.0.1")),
                        syncCode(() => new net.Socket().connect(80, "127.0.0.1")),
                        syncCode(() => net.createServer().listen(0)),
                        syncCode(() => net.connect({ path: "/tmp/autojs6.sock" }))
                      ];

                      dns.setDefaultResultOrder("verbatim");
                      const localhostLookup = await dnsLookup("localhost", { all: true, order: "ipv4first" });
                      const ipLookup = await dnsPromises.lookup("127.0.0.9");
                      const lookupService = await dnsPromises.lookupService("127.0.0.1", 443);
                      const hostnameDenied = await dnsLookupCode("example.invalid");
                      const resolveDenied = await dnsResolveCode(dns.resolve4, "localhost");
                      const promiseResolveDenied = await asyncCode(() => dnsPromises.resolve4("localhost"));
                      const resolver = new dns.Resolver({ timeout: 1, tries: 1, maxTimeout: 1 });
                      const promiseResolver = new dnsPromises.Resolver({ timeout: 1, tries: 1, maxTimeout: 1 });
                      const resolverDenied = await dnsResolveCode(resolver.resolve4, "localhost");
                      const promiseResolverDenied = await asyncCode(() => promiseResolver.resolve4("localhost"));
                      const resolveRrtypeDenied = await dnsResolveCode((hostname, callback) => dns.resolve(hostname, "MX", callback), "localhost");
                      const promiseResolveRrtypeDenied = await asyncCode(() => dnsPromises.resolve("localhost", "MX"));
                      const resolve4TtlDenied = await dnsResolveCode((hostname, callback) => dns.resolve4(hostname, { ttl: true }, callback), "localhost");
                      const promiseResolve4TtlDenied = await asyncCode(() => dnsPromises.resolve4("localhost", { ttl: true }));
                      const tlsaDenied = await dnsResolveCode(dns.resolveTlsa, "localhost");
                      const promiseTlsaDenied = await asyncCode(() => dnsPromises.resolveTlsa("localhost"));
                      const resolverTlsaDenied = await dnsResolveCode(resolver.resolveTlsa, "localhost");
                      const promiseResolverTlsaDenied = await asyncCode(() => promiseResolver.resolveTlsa("localhost"));
                      const serverList = dns.getServers();
                      const defaultOrderBefore = dns.getDefaultResultOrder();
                      dns.setDefaultResultOrder("ipv6first");
                      const defaultOrderAfter = dns.getDefaultResultOrder();
                      const setServersEmpty = syncCode(() => dns.setServers([]));
                      const setServersDenied = syncCode(() => dns.setServers(["8.8.8.8"]));

                      const secureContext = tls.createSecureContext({ minVersion: "TLSv1.2", servername: "example.invalid" });
                      const tlsSocket = new tls.TLSSocket(new net.Socket(), { servername: "example.invalid" });
                      const tlsCert = tlsSocket.getPeerCertificate(true);
                      const cipher = tlsSocket.getCipher();
                      const tlsServer = tls.createServer({ requestCert: true, rejectUnauthorized: false });
                      tlsServer.addContext("example.invalid", secureContext);
                      tlsServer.setSecureContext({ minVersion: "TLSv1.3" });
                      const ticketKeys = tlsServer.getTicketKeys();
                      tlsServer.setTicketKeys(new Uint8Array(48));
                      const fragmentOk = tlsSocket.setMaxSendFragment(1024);
                      const tlsRenegotiateCode = await new Promise((resolve) => {
                        const returned = tlsSocket.renegotiate((error) => {
                          resolve(String(returned) + ":" + codeOf(error));
                        });
                      });
                      const tlsDenials = [
                        syncCode(() => tls.connect(443, "127.0.0.1")),
                        syncCode(() => nodeTls.createConnection(443, "127.0.0.1")),
                        syncCode(() => tlsSocket.connect(443, "127.0.0.1")),
                        syncCode(() => tlsSocket.exportKeyingMaterial(16, "label")),
                        syncCode(() => tlsServer.listen(0))
                      ];

                      console.log("p13ntd.resolve=" + [
                        require.resolve("net"),
                        require.resolve("node:net"),
                        require.resolve("tls"),
                        require.resolve("node:tls"),
                        require.resolve("dns"),
                        require.resolve("node:dns"),
                        require.resolve("dns/promises"),
                        require.resolve("node:dns/promises")
                      ].join("|"));
                      console.log("p13ntd.same=" + [
                        net === nodeNet,
                        tls === nodeTls,
                        dns === nodeDns,
                        dnsPromises === nodeDnsPromises,
                        net.shadow === true,
                        tls.shadow === true,
                        dns.shadow === true,
                        dnsPromises.shadow === true
                      ].join("|"));
                      console.log("p13ntd.net.shape=" + [
                        net.isIP("127.0.0.1"),
                        net.isIP("::1"),
                        net.isIP("localhost"),
                        net.isIPv4("127.0.0.1"),
                        net.isIPv6("::1"),
                        address.address,
                        address.port,
                        address.family,
                        net.isSocketAddress(parsed),
                        blockList.check("127.0.0.2"),
                        blockList.check("127.0.0.11"),
                        blockList.check("127.0.1.25"),
                        blockList.check("127.0.2.1"),
                        blockList.rules.length,
                        serverConnections
                      ].join("|"));
                      console.log("p13ntd.net.state=" + [
                        socketWriteCode,
                        socket.readyState,
                        socket.destroyed,
                        socket.bytesRead,
                        socket.bytesWritten,
                        socketHasFd,
                        net.getDefaultAutoSelectFamily(),
                        attemptTimeoutBefore,
                        attemptTimeoutAfter
                      ].join("|"));
                      console.log("p13ntd.net.denials=" + netDenials.join("|"));
                      console.log("p13ntd.dns.lookup=" + [
                        localhostLookup,
                        ipLookup.address + ":" + ipLookup.family,
                        lookupService.hostname + ":" + lookupService.service,
                        hostnameDenied,
                        serverList.length,
                        Object.isFrozen(serverList),
                        defaultOrderBefore,
                        defaultOrderAfter,
                        setServersEmpty,
                        setServersDenied
                      ].join("|"));
                      console.log("p13ntd.dns.denials=" + [
                        resolveDenied,
                        promiseResolveDenied,
                        resolverDenied,
                        promiseResolverDenied,
                        resolveRrtypeDenied,
                        promiseResolveRrtypeDenied,
                        resolve4TtlDenied,
                        promiseResolve4TtlDenied,
                        tlsaDenied,
                        promiseTlsaDenied,
                        resolverTlsaDenied,
                        promiseResolverTlsaDenied
                      ].join("|"));
                      console.log("p13ntd.dns.shape=" + [
                        typeof dns.resolveTlsa,
                        typeof dnsPromises.resolveTlsa,
                        typeof resolver.resolveTlsa,
                        typeof promiseResolver.resolveTlsa,
                        dns.CANCELLED,
                        dns.BADSTR,
                        dns.NOMEM,
                        dns.DESTRUCTION,
                        dns.ADDRGETNETWORKPARAMS
                      ].join("|"));
                      console.log("p13ntd.tls.shape=" + [
                        secureContext.secureProtocol,
                        tlsSocket.encrypted,
                        tlsSocket.authorized,
                        tlsSocket.authorizationError,
                        tlsSocket.servername,
                        cipher.name,
                        cipher.version,
                        tlsCert.bits,
                        tlsServer.requestCert,
                        tlsServer.rejectUnauthorized,
                        ticketKeys.length,
                        fragmentOk,
                        tls.checkServerIdentity("example.invalid", {}) === undefined,
                        tls.rootCertificates.length,
                        tls.getCiphers().length > 0
                      ].join("|"));
                      console.log("p13ntd.tls.denials=" + [tlsRenegotiateCode].concat(tlsDenials).join("|"));
                    })().catch((error) => {
                      console.error("p13ntd.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-net-tls-dns-staged-p13-30.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-30 net/tls/dns staged compatibility", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("p13ntd.resolve=net|net|tls|tls|dns|dns|dns/promises|dns/promises"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13ntd.same=true|true|true|true|false|false|false|false"))
            assertTrue(
                result.stdout,
                result.stdout.contains("p13ntd.net.shape=4|6|0|true|true|127.0.0.1|8080|ipv4|true|true|true|true|false|3|0"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13ntd.net.state=false:ERR_AUTOJS6_NETWORK_DISABLED|closed|true|0|0|false|false|250|275"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13ntd.net.denials=ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13ntd.dns.lookup=127.0.0.1:4,::1:6|127.0.0.9:4|localhost:443|ENOTFOUND|0|true|verbatim|ipv6first|allowed|ERR_AUTOJS6_NETWORK_DISABLED",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13ntd.dns.denials=ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13ntd.dns.shape=function|function|function|function|ECANCELLED|EBADSTR|ENOMEM|EDESTRUCTION|EADDRGETNETWORKPARAMS",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13ntd.tls.shape=TLSv1.2|true|false||example.invalid|TLS_AES_256_GCM_SHA384|TLSv1.3|0|true|false|48|true|true|0|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13ntd.tls.denials=false:ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED",
                ),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun rawNodeNetworkModulesOptInExposesNativeBuiltins() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("raw Node network modules opt-in")

        val workDir = File(context.cacheDir, "node-raw-network-opt-in-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create raw network opt-in work dir: $absolutePath" }
        }

        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const profile = require("autojs6:profile");
                      const dns = require("dns");
                      const nodeDns = require("node:dns");
                      const dnsPromises = require("dns/promises");
                      const http = require("http");
                      const net = require("net");
                      const tls = require("tls");

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }

                      function syncNativeNetwork(action) {
                        try {
                          const value = action();
                          if (value && typeof value.on === "function") {
                            value.on("error", () => {});
                          }
                          if (value && typeof value.destroy === "function") {
                            value.destroy();
                          } else if (value && typeof value.abort === "function") {
                            value.abort();
                          }
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      dns.setDefaultResultOrder("ipv4first");
                      const dnsResolveStatus = await new Promise((resolve) => {
                        dns.resolve("www.github.com", (error, addresses) => {
                          if (error) {
                            resolve("native_error:" + codeOf(error));
                            return;
                          }
                          resolve("resolved:" + (Array.isArray(addresses) && addresses.length > 0) + ":" + typeof addresses[0]);
                        });
                      });
                      const syncStatuses = [
                        syncNativeNetwork(() => http.request({ host: "127.0.0.1", port: 9, path: "/", method: "GET" })),
                        syncNativeNetwork(() => net.connect({ host: "127.0.0.1", port: 9 })),
                        syncNativeNetwork(() => tls.connect({ host: "127.0.0.1", port: 443, rejectUnauthorized: false }))
                      ];

                      console.log("p13ntd.raw.profile=" + [
                        profile.featureFlags.raw_node_network_modules.enabled,
                        profile.experimentalFlags.rawNodeNetworkModules,
                        profile.highRiskCapabilities.rawNodeNetworkModules
                      ].join("|"));
                      console.log("p13ntd.raw.same=" + [
                        dns === nodeDns,
                        dns.promises === dnsPromises
                      ].join("|"));
                      console.log("p13ntd.raw.shape=" + [
                        typeof dns.resolve,
                        typeof dnsPromises.resolve,
                        typeof http.Agent,
                        typeof http.ClientRequest,
                        typeof net.Server,
                        typeof tls.TLSSocket
                      ].join("|"));
                      console.log("p13ntd.raw.sync=" + syncStatuses.join("|"));
                      console.log("p13ntd.raw.dns=" + dnsResolveStatus);
                    })();
                """.trimIndent(),
                sourceName = "<node-raw-network-opt-in-p13-30.js>",
                workingDirectory = workDir,
                timeoutMs = 20_000L,
                rawNodeNetworkModulesExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("raw Node network modules opt-in", result)
            assertTrue(result.stdout, result.stdout.contains("p13ntd.raw.profile=true|true|true"))
            assertTrue(result.stdout, result.stdout.contains("p13ntd.raw.same=true|true"))
            assertTrue(
                result.stdout,
                result.stdout.contains("p13ntd.raw.shape=function|function|function|function|function|function"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13ntd.raw.sync=allowed|allowed|allowed"))
            assertTrue(result.stdout, result.stdout.contains("p13ntd.raw.dns="))
            assertFalse(result.stdout, result.stdout.contains("ERR_AUTOJS6_NETWORK_DISABLED"))
            assertFalse(result.stdout, result.stdout.contains("ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeShadowModule(workDir: File, moduleName: String) {
        File(workDir, "node_modules/$moduleName/index.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true, id: "$moduleName-shadow" };""" + "\n", StandardCharsets.UTF_8)
        }
    }
}
