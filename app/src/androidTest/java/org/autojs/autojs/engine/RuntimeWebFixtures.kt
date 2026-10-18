package org.autojs.autojs.engine

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.X509KeyManager
import javax.security.auth.x500.X500Principal

/** Loopback HTTP/TLS and actual uploads/download bytes; no external server or user files. */
internal class RuntimeWebFixtureServer private constructor(private val server: ServerSocket, private val cleanup: () -> Unit, secure: Boolean) : Closeable {
    constructor() : this(ServerSocket(0, 20, InetAddress.getByName("127.0.0.1")), {}, false)
    val url = "${if (secure) "https" else "http"}://127.0.0.1:${server.localPort}"
    val requests = CopyOnWriteArrayList<String>()
    val uploads = CopyOnWriteArrayList<ByteArray>()
    val errors = CopyOnWriteArrayList<String>()
    @Volatile var stopWorker = false
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val executor = Executors.newCachedThreadPool { task -> Thread(task, "web-fixture").apply { isDaemon = true } }
    init {
        executor.execute {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets.add(socket)
                executor.execute { try { socket.use { serve(it) } } catch (error: Exception) { errors.add(error.toString()) } finally { sockets.remove(socket) } }
            }
        }
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 10000
        val input = BufferedInputStream(socket.getInputStream())
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) { val next = input.read(); if (next < 0 || next == 10) break; if (next != 13) bytes.write(next); require(bytes.size() <= 8192) }
            return bytes.toString("UTF-8")
        }
        val request = line(); if (request.isBlank()) return
        val parts = request.split(' '); val target = parts[1]; val pathname = target.substringBefore('?')
        val headers = HashMap<String, String>()
        while (true) { val field = line(); if (field.isBlank()) break; headers[field.substringBefore(':').lowercase()] = field.substringAfter(':').trim() }
        val body = if (headers["transfer-encoding"] == "chunked") ByteArrayOutputStream().apply {
            while (true) { val size = line().substringBefore(';').toInt(16); if (size == 0) { line(); break }; require(size <= 1048576); write(input.readNBytes(size)); line() }
        }.toByteArray() else input.readNBytes((headers["content-length"]?.toInt() ?: 0).also { require(it <= 64 * 1048576) })
        requests.add(pathname)
        var status = "200 OK"; var mime = "text/html; charset=utf-8"; val extra = ArrayList<String>()
        val response = when {
            pathname in listOf("/capture-video", "/capture-audio") -> {
                val kind=pathname.removePrefix("/capture-")
                """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Media $kind</title>
                    <input id="media" type="file" accept="$kind/*" capture="environment" hidden><button id="capture" onclick="document.getElementById('media').click()">Capture $kind</button>
                    <script>window.uploaded=false;window.onunhandledrejection=e=>window.captureError=String(e.reason);
                    document.getElementById('media').onchange=async function(){var file=this.files[0],bytes=new Uint8Array(await file.arrayBuffer()),media=document.createElement('$kind');
                    media.preload='metadata';var loaded=new Promise((r,j)=>{media.onloadedmetadata=r;media.onerror=()=>j(Error('Media decode failed'))});media.src=URL.createObjectURL(file);await loaded;
                    var hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');
                    window.mediaInfo={size:file.size,mime:file.type,duration:media.duration,width:media.videoWidth||0,height:media.videoHeight||0,sha256:hash,head:Array.from(bytes.slice(0,16)),tail:Array.from(bytes.slice(-16))};
                    var form=new FormData();form.append('media',file);window.uploaded=(await fetch('/upload',{method:'POST',body:form})).ok;};</script>""".trimIndent().toByteArray()
            }
            pathname == "/capture" -> """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Camera fixture</title>
                <input id="photo" type="file" accept="image/*" capture="environment" hidden><button id="capture" onclick="document.getElementById('photo').click()">Capture</button>
                <script>window.uploaded=false;window.onunhandledrejection=e=>window.captureError=String(e.reason);
                document.getElementById('photo').onchange=async function(){var file=this.files[0],bytes=new Uint8Array(await file.arrayBuffer()),image=new Image();
                image.src=URL.createObjectURL(file);await new Promise((r,j)=>{image.onload=r;image.onerror=j});
                window.photoInfo={size:file.size,mime:file.type,width:image.naturalWidth,height:image.naturalHeight,header:Array.from(bytes.slice(0,3))};
                var form=new FormData();form.append('photo',file);window.uploaded=(await fetch('/upload',{method:'POST',body:form})).ok;};</script>""".trimIndent().toByteArray()
            pathname == "/index" -> """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Web callbacks</title>
                <script src="/virtual.js"></script><script>console.log('web-fixture');window.uploaded=false;window.onunhandledrejection=e=>window.uploadError=String(e.reason);</script>
                <a id="download" href="/download">Download pending</a>
                <input id="file" type="file" accept="text/plain" style="display:none">
                <button id="pick" onclick="document.getElementById('file').click()">Files pending</button>
                <script>document.getElementById('file').onchange=async function(){window.fileNames=Array.from(this.files,f=>f.name);
                window.fileTexts=await Promise.all(Array.from(this.files,f=>f.text()));const form=new FormData();for(const f of this.files)form.append('files',f);
                window.uploaded=(await fetch('/upload',{method:'POST',body:form})).ok;};</script>""".trimIndent().toByteArray()
            pathname == "/virtual.js" -> { mime = "application/javascript"; "window.virtualLoaded=-1;".toByteArray() }
            pathname == "/worker-stop" -> { mime = "application/json"; stopWorker.toString().toByteArray() }
            pathname == "/download" -> { mime = "application/octet-stream"; extra.add("Content-Disposition: attachment; filename=web-fixture.txt"); DOWNLOAD.toByteArray() }
            pathname == "/upload" -> { uploads.add(body); mime = "application/json"; "{\"ok\":true}".toByteArray() }
            pathname.startsWith("/auth/") -> {
                val mode = pathname.removePrefix("/auth/").trim('/')
                val expected = "Basic " + android.util.Base64.encodeToString("$mode:secret-$mode".toByteArray(), android.util.Base64.NO_WRAP)
                if (headers["authorization"] != expected) { status = "401 Unauthorized"; extra.add("WWW-Authenticate: Basic realm=\"AutoJs6-$mode\""); "auth".toByteArray() }
                else "<title>Authenticated</title><p>authenticated</p>".toByteArray()
            }
            pathname == "/popup" -> "<title>Popup</title><p>Popup fixture</p>".toByteArray()
            pathname == "/tls" -> "<title>TLS fixture</title><p>ephemeral TLS</p>".toByteArray()
            pathname == "/missing" -> { status = "404 Not Found"; extra.add("X-Fixture: missing"); "<title>Missing</title>".toByteArray() }
            else -> "<title>Unexpected</title>".toByteArray()
        }
        val output = socket.getOutputStream()
        output.write(("HTTP/1.1 $status\r\nContent-Type: $mime\r\nContent-Length: ${response.size}\r\nConnection: close\r\n" + extra.joinToString("") { "$it\r\n" } + "\r\n").toByteArray())
        output.write(response); output.flush()
    }
    override fun close() { server.close(); sockets.forEach { runCatching { it.close() } }; executor.shutdownNow(); cleanup() }
    companion object {
        const val DOWNLOAD = "AutoJs6 browser download payload\n"
        fun tls(): RuntimeWebFixtureServer {
                val alias = "fixture"
                val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val subject = X500Principal("CN=localhost")
                val certificate = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(subject, java.math.BigInteger.ONE,
                    Date(System.currentTimeMillis() - 86400000), Date(System.currentTimeMillis() + 86400000), subject, pair.public)
                    .build(org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
                val key = pair.private
                val chain = arrayOf(org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(certificate))
                val manager = object : X509KeyManager {
                    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = null
                    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
                    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = if (keyType == "RSA") arrayOf(alias) else null
                    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = if (keyType == "RSA") alias else null
                    override fun getCertificateChain(name: String?) = chain
                    override fun getPrivateKey(name: String?) = key
                }
                val ssl = SSLContext.getInstance("TLS").apply { init(arrayOf(manager), null, SecureRandom()) }
                val server = (ssl.serverSocketFactory.createServerSocket(0, 20, InetAddress.getByName("127.0.0.1")) as javax.net.ssl.SSLServerSocket).apply { enabledProtocols = arrayOf("TLSv1.2") }
                return RuntimeWebFixtureServer(server, {}, true)
        }
    }
}
