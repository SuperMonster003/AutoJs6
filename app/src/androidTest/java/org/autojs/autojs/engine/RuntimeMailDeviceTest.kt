package org.autojs.autojs.engine

import android.content.Context
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RuntimeMailDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()
    private fun configuration() = JSONObject(File(context.getExternalFilesDir(null), "runtime-mail-fixture.json").readText())

    private fun fixture(action: () -> Unit) {
        val owned = "io.github.supermonster003.autojs6.plugin.three.stamp.mail"
        val packages = (runBlocking { MailPluginHost.discover(context) }.map { it.serviceInfo.packageName } + owned).distinct()
        val enabled = context.getSharedPreferences("plugin_center_enable_state", Context.MODE_PRIVATE)
        val selectionKeys = packages.flatMap { listOf("key_\$_enabled_plugin_\$_" + it, "key_\$_suspended_plugin_\$_" + it) }
        val previousSelection = enabled.all.filterKeys { it in selectionKeys }
        val grants = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        val keys = listOf(Grants.MAIL_CONTROL, Grants.JAVA_INTEROP)
        val previous = keys.associateWith { if (grants.contains(it)) grants.getBoolean(it, false) else null }
        try {
            packages.forEach { PluginEnableStore.setEnabled(context, it, it == owned) }
            grants.edit().apply { keys.forEach { putBoolean(it, true) } }.commit()
            harness.withFixture(setup = { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) { action() }
        } finally {
            grants.edit().apply { previous.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) } }.commit()
            enabled.edit().apply {
                selectionKeys.forEach(::remove)
                previousSelection.forEach { (key, value) -> putBoolean(key, value as Boolean) }
            }.commit()
            packages.forEach(PluginStateChangeRegistry::notifyChanged)
        }
    }

    private fun exercise(mode: String) = fixture {
        val program = "const fixture=" + configuration().toString() + ";" + RuntimeMailPrograms.body()
        val source = when (mode) {
            "bun" -> "const mail=host.mail,java=host.java;" + program
            "node" -> "const mail=require('autojs6:mail'),java=require('java');" + program
            else -> {
                val child = "const {parentPort}=require('node:worker_threads'),mail=require('mail'),java=require('java');" +
                    "(async()=>{" + program + "})().then(value=>parentPort.postMessage(value)).catch(error=>{throw error;});"
                "const {Worker}=require('node:worker_threads'),worker=new Worker(" + JSONObject.quote(child) +
                    ",{eval:true});try{return await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});}finally{await worker.terminate();}"
            }
        }
        val result = harness.execute(if (mode == "bun") "bun" else "node", source, capabilities = listOf("mail", "java_interop"))
        assertEquals(context.packageName, result.getString("packageName"))
        assertEquals(5000, result.getInt("bodyMarkers"))
        assertTrue(result.getBoolean("watchClosed"))
        assertTrue(result.getBoolean("credentialsRedacted"))
        File(context.getExternalFilesDir(null), "runtime-mail-" + mode + ".json").writeText(result.toString(2))
    }

    @Test fun nodeUsesRealMailProtocolsAndAttachments() = exercise("node")
    @Test fun bunUsesRealMailProtocolsAndAttachments() = exercise("bun")
    @Test fun workerUsesRealMailProtocolsAndAttachments() = exercise("worker")

    @Test fun asynchronousTokenProvidersRefreshThroughTheRealPlugin() = fixture {
        RuntimeMailTokenServer().use { server ->
            for (mode in listOf("node", "bun", "worker")) {
                val program = """
                    let tokenCalls=0;
                    const client=await mail.connect({
                        address:'runtime@example.invalid',auth:'xoauth2',
                        imap:{host:'127.0.0.1',port:PORT,tls:'none'},
                        smtp:{host:'127.0.0.1',port:SMTP,tls:'none'},
                        tokenProvider:async()=>{await new Promise(resolve=>setTimeout(resolve,10));return ++tokenCalls===1?'expired-runtime-token':'valid-runtime-token';}
                    });
                    try{
                        const folders=await client.folders();
                        if(tokenCalls!==2||!folders.some(folder=>folder.path==='INBOX'))throw Error('Token retry contract');
                        if('accessToken' in client.account)throw Error('Token leaked to account snapshot');
                        return {tokenCalls,refreshed:true};
                    }finally{await mail.dispose();}
                """.trimIndent().replace("PORT", server.port.toString()).replace("SMTP", configuration().getInt("smtpPort").toString())
                val script = when (mode) {
                    "bun" -> "const mail=host.mail;" + program
                    "node" -> "const mail=require('mail');" + program
                    else -> {
                        val child = "const {parentPort}=require('node:worker_threads'),mail=require('mail');(async()=>{" +
                            program + "})().then(value=>parentPort.postMessage(value)).catch(error=>{throw error;});"
                        "const {Worker}=require('node:worker_threads'),worker=new Worker(" + JSONObject.quote(child) +
                            ",{eval:true});try{return await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});}finally{await worker.terminate();}"
                    }
                }
                val result = harness.execute(if (mode == "bun") "bun" else "node", script, capabilities = listOf("mail"))
                assertEquals(2, result.getInt("tokenCalls"))
                File(context.getExternalFilesDir(null), "runtime-mail-token-" + mode + ".json").writeText(result.toString(2))
            }
            assertEquals(6, server.attempts.get())
        }
    }

    @Test fun bunDeniesMissingGrantAndKeepsStreamingBeyond1024Calls() = fixture {
        val prefs = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Grants.MAIL_CONTROL, false).commit()
        try {
            val denied = harness.execute("bun", """
                let rejected=false;try{await host.mail.providers.list();}catch(error){rejected=error.code==='NOT_GRANTED';}
                const response=await fetch('http://autojs6/v1/mail.control',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,
                    method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'start',method:'open',args:[]})});
                return {rejected,status:response.status};
            """.trimIndent())
            assertTrue(denied.getBoolean("rejected"))
            assertEquals(403, denied.getInt("status"))
        } finally { prefs.edit().putBoolean(Grants.MAIL_CONTROL, true).commit() }
        val result = harness.execute("bun", """
            async function raw(method,args){
                const id=(await host.call('mail.control',{action:'start',method,args,timeoutMs:10000})).value.id;
                while(true){const result=(await host.call('mail.control',{action:'poll',id})).value;if(result.done)return result.value;}
            }
            const client=(await raw('open',[])).id;
            try{for(let i=0;i<520;i++)eq(await raw('poll',[client,[]]),[],'empty mail events');}
            finally{await raw('close',[client]);}
            const providers=await host.mail.providers.list();await host.mail.dispose();return {calls:1044,providers:providers.length};
        """.trimIndent())
        assertTrue(result.getInt("providers") >= 5)
        File(context.getExternalFilesDir(null), "runtime-mail-streaming-budget.json").writeText(result.toString(2))
    }

    @Test fun cjsEsmNestedWorkerExitPreservesTheParentMailClient() = fixture {
        val config = configuration()
        val account = JSONObject().put("address", "runtime@example.invalid").put("password", "runtime-mail-synthetic")
            .put("imap", JSONObject().put("host", "127.0.0.1").put("port", config.getInt("imapPort")).put("tls", "none"))
            .put("smtp", JSONObject().put("host", "127.0.0.1").put("port", config.getInt("smtpPort")).put("tls", "none"))
        val script = """
            const mail=require('mail'),{Worker}=require('node:worker_threads');
            const options=ACCOUNT;
            const body="const client=await mail.connect("+JSON.stringify(options)+");parentPort.postMessage((await client.test()).ok);setInterval(()=>{},1000);";
            const cjs="const {parentPort}=require('node:worker_threads'),mail=require('mail');(async()=>{"+body+"})().catch(error=>{throw error;});";
            const esm="import {parentPort} from 'node:worker_threads';import mail from 'autojs6:mail';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads');const child=new Worker("+JSON.stringify(cjs)+",{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e;});";
            const parent=await mail.connect(options);
            try{for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
                const worker=new Worker(entry,options);
                try{eq(await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}),true,'worker mail login');}
                finally{await worker.terminate();}
                if(!(await parent.test()).ok)throw Error('Parent connection was affected');
            }}finally{await mail.dispose();}
            return {workerModes:3,parentPreserved:true};
        """.trimIndent().replace("ACCOUNT", account.toString())
        val result = harness.execute("node", script, capabilities = listOf("mail"))
        assertEquals(3, result.getInt("workerModes"))
        File(context.getExternalFilesDir(null), "runtime-mail-worker-modes.json").writeText(result.toString(2))
    }
}
