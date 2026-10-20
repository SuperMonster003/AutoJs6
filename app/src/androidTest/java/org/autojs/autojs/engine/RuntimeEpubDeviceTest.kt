package org.autojs.autojs.engine

import android.content.Context
import android.util.Base64
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.epub.EpubPluginHost
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RuntimeEpubDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val harness = CaptureWorkerBridgeDeviceTest()
    private fun fixture(action: () -> Unit) {
        assertEquals("Only the owned 3-Folio installation may be used by this fixture",
            "io.github.supermonster003.autojs6.plugin.three.folio.epub",
            runBlocking { EpubPluginHost.selectOrThrow(context).serviceInfo.packageName })
        val prefs = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        val keys = listOf(Grants.EPUB_CONTROL, Grants.JAVA_INTEROP)
        val saved = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        prefs.edit().apply { keys.forEach { putBoolean(it, true) } }.commit()
        try { harness.withFixture(setup = { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }) { action() } }
        finally { prefs.edit().apply { saved.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) } }.commit() }
    }

    private fun exercise(mode: String) = fixture {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("runtime-epub/book.epub").use { it.readBytes() }
        val program = "const bookBytes=Buffer.from(${JSONObject.quote(Base64.encodeToString(bytes, Base64.NO_WRAP))},'base64');" + RuntimeEpubPrograms.body()
        val source = when (mode) {
            "bun" -> "const epub=host.epub,java=host.java;$program"
            "node" -> "const epub=require('autojs6:epub'),java=require('java');$program"
            else -> {
                val child = "const {parentPort}=require('node:worker_threads'),epub=require('epub'),java=require('java');(async()=>{$program})().then(value=>parentPort.postMessage(value)).catch(error=>{throw error;});"
                "const {Worker}=require('node:worker_threads'),worker=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});}finally{await worker.terminate();}"
            }
        }
        val result = harness.execute(if (mode == "bun") "bun" else "node", source, capabilities = listOf("epub", "java_interop"))
        assertEquals(context.packageName, result.getString("packageName"))
        assertTrue(result.getBoolean("readerClosed"))
        assertEquals(10000, result.getInt("unicodeMarkers"))
        File(context.getExternalFilesDir(null), "runtime-epub-$mode.json").writeText(result.toString(2))
    }

    @Test fun nodeBooksAndReaderUseTheRealPlugin() = exercise("node")
    @Test fun bunBooksAndReaderUseTheRealPlugin() = exercise("bun")
    @Test fun workerBooksAndReaderUseTheRealPlugin() = exercise("worker")

    @Test fun bunDeniesMissingGrantAndKeepsStreamingBeyond1024Calls() = fixture {
        val prefs = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Grants.EPUB_CONTROL, false).commit()
        try {
            val denied = harness.execute("bun", """
                let rejected=false;try{await host.epub.isAvailable();}catch(error){rejected=error.code==='NOT_GRANTED';}
                const response=await fetch('http://autojs6/v1/epub.control',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,
                    method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'start',method:'open',args:[]})});
                return {denied:rejected,rawStatus:response.status};
            """.trimIndent())
            assertTrue(denied.getBoolean("denied"))
            assertEquals(403, denied.getInt("rawStatus"))
        } finally { prefs.edit().putBoolean(Grants.EPUB_CONTROL, true).commit() }
        val result = harness.execute("bun", """
            async function raw(method,args){
                const job=(await host.call('epub.control',{action:'start',method,args})).value.id;
                while(true){const result=(await host.call('epub.control',{action:'poll',id:job})).value;if(result.done)return result.value;}
            }
            const client=(await raw('open',[])).id;
            try{for(let i=0;i<520;i++)eq(await raw('poll',[client,[]]),[],'empty EPUB events');}
            finally{await raw('close',[client]);}
            return {calls:1044,available:await host.epub.isAvailable()};
        """.trimIndent())
        assertTrue(result.getBoolean("available"))
        File(context.getExternalFilesDir(null), "runtime-epub-streaming-budget.json").writeText(result.toString(2))
    }

    @Test fun cjsEsmNestedWorkerExitReleasesBooksAndPreservesTheParent() = fixture {
        val source = File(context.getExternalFilesDir(null), "runtime-epub-worker-fixture.epub")
        InstrumentationRegistry.getInstrumentation().context.assets.open("runtime-epub/book.epub").use { input -> source.outputStream().use { input.copyTo(it) } }
        try {
            val result = harness.execute("node", """
                const epub=require('epub'),{Worker}=require('node:worker_threads'),source=${JSONObject.quote(source.path)};
                const body="const book=await epub.open("+JSON.stringify(source)+");parentPort.postMessage(book.metadata.title);setInterval(()=>{},1000);";
                const cjs="const {parentPort}=require('node:worker_threads'),epub=require('epub');(async()=>{"+body+"})().catch(error=>{throw error;});";
                const esm="import {parentPort} from 'node:worker_threads';import epub from 'autojs6:epub';"+body;
                const nested="const {parentPort,Worker}=require('node:worker_threads'),worker=new Worker("+JSON.stringify(cjs)+",{eval:true});worker.on('message',value=>parentPort.postMessage(value));worker.on('error',error=>{throw error;});";
                for(const [entry,options] of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]){
                    const parent=await epub.open(source),worker=new Worker(entry,options),additional=[];
                    try{
                        eq(await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}),'Runtime EPUB fixture','worker title');
                        await worker.terminate();await pause(500);
                        if(!(await parent.text(0)).includes('lighthouse keeper'))throw Error('Parent book was closed');
                        for(let i=0;i<7;i++)additional.push(await epub.open(source));
                    }finally{await worker.terminate();for(const book of additional)await book.close();await parent.close();}
                }
                await epub.close();return {workerModes:3,parentPreserved:true,capacityRecovered:true};
            """.trimIndent(), capabilities = listOf("epub"))
            assertEquals(3, result.getInt("workerModes"))
            File(context.getExternalFilesDir(null), "runtime-epub-worker-modes.json").writeText(result.toString(2))
        } finally { source.delete() }
    }
}
