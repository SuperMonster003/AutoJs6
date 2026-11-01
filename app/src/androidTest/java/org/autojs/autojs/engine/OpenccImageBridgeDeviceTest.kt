package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.autojs.runtime.api.modules.opencc.OpenccConversionPlan
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Real CJS, ESM and Bun executions, with the current OpenCC and OpenCV plugins installed. */
class OpenccImageBridgeDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val global get() = AutoJs.instance.globalConsole
    private val kinds = runtimeModes("node-cjs", "node-esm", "bun")
    private val evidence = JSONArray()

    @Test fun nodeWorkerCanUseOpenccAndImagesThroughItsDocumentedBridge() = withGrants {
        val worker = """
            const {parentPort}=require('node:worker_threads');
            (async()=>{
              const bridge=require('autojs6:bridge');
              const call=(module,method,args)=>bridge.callAutoJs(module,method,args,{permissions:[module==='images'?'image':module]});
              const img=await call('images','fromBase64',['${encode(1,1,intArrayOf(0x80ff0000.toInt()))}']);
              const encoded=await call('images','toBase64',[img]);
              const copy=await call('images','fromBase64',[encoded]);
              parentPort.postMessage({text:await call('opencc','s2t',['汉语']),pixel:await call('images','pixel',[copy,0,0])});
              await call('images','recycle',[copy]);await call('images','recycle',[img]);
            })().catch(error=>{throw error;});
        """.trimIndent()
        execute("node-cjs", """
            const {Worker}=require('node:worker_threads');
            let result;
            await new Promise((resolve,reject)=>{
              const worker=new Worker(${JSONObject.quote(worker)},{eval:true});
              worker.on('message',value=>{result=value;});worker.on('error',reject);
              worker.on('exit',code=>code===0?resolve():reject(Error('Worker exit '+code)));
            });
            eq(result,{text:'漢語',pixel:${0x80ff0000.toInt()}},'worker modules');
            return result;
        """.trimIndent())
        writeEvidence("worker")
    }

    @Test fun allOpenccConversionsUseTheProvider() = withGrants {
        val names = OpenccConversionPlan.supportedNames.sorted()
        val backend = AndroidNodeOpenccBackend(context)
        val text = "汉字转换, 软件鼠标里面的头发. 漢字轉換, 軟體滑鼠裏面的頭髮. 国学広場."
        val expected = names.associateWith { backend.convert(text, it) }
        for (kind in kinds) {
            val report = execute(kind, """
                const results = {};
                for (const type of ${JSONArray(names)}) results[type] = await cc[type](${JSONObject.quote(text)});
                eq(await cc('汉语转换', 'S2T'), '漢語轉換', 'callable');
                eq(await cc.convert('漢語轉換', 't2s'), '汉语转换', 'convert');
                eq(await cc.s2t(''), '', 'empty');
                eq(await cc.s2t(123), '123', 'string coercion');
                await rejects(() => cc.s2t(null));
                await rejects(() => cc.convert('汉字', 'missing'));
                return {results};
            """.trimIndent())
            for ((name, value) in expected) assertEquals("$kind $name", value, report.getJSONObject("results").getString(name))
        }
        writeEvidence("opencc")
    }

    @Test fun publicImagesPreservePixelsDimensionsAndOwnership() = withGrants {
        val pixels = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0x80ff0000.toInt(), 0, 0xffffffff.toInt())
        val encoded = encode(3, 2, pixels)
        for (kind in kinds) {
            val file = File(context.cacheDir, "image-bridge-${UUID.randomUUID()}.png")
            try {
                execute(kind, """
                    const img = await images.fromBase64('data:image/png;base64,$encoded');
                    const expected = ${JSONArray(pixels.toList())};
                    eq(images.getSize(img), {width:3,height:2}, 'size');
                    eq((await images.readPixels(img)).data, expected, 'ARGB');
                    eq(await images.pixel(img, 0, 1), expected[3], 'alpha');
                    eq((await images.readPixels(img,{region:[1,0,2,2]})).data, [expected[1],expected[2],expected[4],expected[5]], 'region');
                    for (const make of [() => images.copy(img), () => images.rotate(img,0), () => images.flip(img,false,false),
                      () => images.clip(img,0,0,3,2), () => images.medianBlur(img,1), () => images.blur(img,1)]) {
                      const child = await make();
                      if (child.id === img.id) throw Error('Derived handle aliases source');
                      eq((await images.readPixels(child)).data,expected,'identity transform');
                      await images.recycle(child);
                      eq(await images.pixel(img,0,0),expected[0],'source survived child recycle');
                    }
                    const flipped = await images.flip(img,'horizontal');
                    eq((await images.readPixels(flipped)).data,[expected[2],expected[1],expected[0],expected[5],expected[4],expected[3]],'flip');
                    await images.recycle(flipped);
                    const rotated = await images.rotate(img,90,{filter:false});
                    eq(images.getSize(rotated),{width:2,height:3},'rotate size');
                    eq((await images.readPixels(rotated)).data,[expected[3],expected[0],expected[4],expected[1],expected[5],expected[2]],'rotate pixels');
                    await images.recycle(rotated);
                    const scaled = await images.scale(img,2,{interpolation:'nearest'});
                    eq(images.getSize(scaled),{width:6,height:4},'scale');
                    eq(await images.pixel(scaled,5,3),-1,'scale pixel');
                    await images.recycle(scaled);
                    for (const method of ['grayscale','threshold','gaussianBlur','medianBlur','invert']) {
                      const child = await (method==='gaussianBlur' || method==='medianBlur' ? images[method](img,3) : images[method](img));
                      eq(images.getSize(child),{width:3,height:2},method);
                      if (method==='invert') { eq(await images.pixel(child,0,0),-16711681,'invert RGB'); eq(await images.pixel(child,0,1),${0x8000ffff.toInt()},'invert straight-alpha RGB'); }
                      await images.recycle(child);
                    }
                    eq(await images.detectsColor(img,-65536,0,0,{threshold:0,algorithm:'equal'}),true,'signed color');
                    eq(await images.findColor(img,-65536,{threshold:0}),{x:0,y:0},'find color');
                    eq(await images.findAllPointsForColor(img,-1,{threshold:0}),[{x:2,y:1}],'all points');
                    eq(await images.findMultiColors(img,-65536,[[1,0,-16711936]],{threshold:0}),{x:0,y:0},'multicolor');
                    for (const format of ['png','jpeg','webp']) {
                      const bytes = await images.toBytes(img,{format,quality:100});
                      const decoded = await images.fromBytes(bytes);
                      eq(images.getSize(decoded),{width:3,height:2},format);
                      if (format==='png') eq((await images.readPixels(decoded)).data,expected,'lossless PNG');
                      await images.recycle(decoded);
                    }
                    await images.saveImage(img,${JSONObject.quote(file.path)},{format:'png'});
                    const read = await images.readImage(${JSONObject.quote(file.path)});
                    eq((await images.readPixels(read)).data,expected,'file round trip');
                    await images.recycle(read);
                    await rejects(() => images.pixel(img,3,0));
                    await rejects(() => images.gaussianBlur(img,2));
                    await rejects(() => images.readPixels(img,{region:[0,0,4,2]}));
                    await images.recycle(img);
                    await rejects(() => images.pixel(img,0,0));
                    return {passed:true};
                """.trimIndent())
            } finally { file.delete() }
        }
        writeEvidence("images")
    }

    @Test fun binaryTransportJsonBoundsAndExecutionOwnership() = withGrants {
        val random = java.util.Random(20261017)
        val pixels = IntArray(257 * 257) { 0xff000000.toInt() or random.nextInt(0x1000000) }
        val encoded = encode(257,257,pixels)
        assertTrue("Large PNG must exceed both JSON transport budgets: ${encoded.length}", encoded.length > 262144)
        for (kind in listOf("node-cjs", "node-esm")) {
            val result = execute(kind, """
                const img = await images.fromBase64('$encoded');
                const bytes = await images.toBytes(img,'png');
                if (bytes.length < 65536) throw Error('Fixture did not exercise PFD upload');
                const decoded = await images.fromBytes(bytes);
                const pixels = await images.readPixels(decoded);
                eq(pixels.data.length,66049,'full binary pixels');
                eq(pixels.data[66048],${pixels.last()},'last pixel');
                await images.recycle(decoded);
                return {handle:img};
            """.trimIndent())
            execute(kind, "await rejects(() => images.pixel(${result.getJSONObject("handle")},0,0)); return {staleRejected:true};")
        }
        if (!bunRuntimeAvailable) { writeEvidence("transport-grants"); return@withGrants }
        val file = File(context.cacheDir,"image-bridge-large-${UUID.randomUUID()}.png")
        try {
            file.writeBytes(Base64.decode(encoded,Base64.DEFAULT))
            execute("bun", """
                const img = await images.readImage(${JSONObject.quote(file.path)});
                await rejects(() => images.readPixels(img));
                eq((await images.readPixels(img,{region:[256,256,1,1]})).data,[${pixels.last()}],'bounded region');
                await rejects(() => images.fromBase64('$encoded'));
                await rejects(() => images.toBase64(img));
                await images.recycle(img);
                const white = await images.fromBase64('${encode(1,1,intArrayOf(-1))}');
                const solid = await images.resize(white,65,65);
                await rejects(() => images.findAllPointsForColor(solid,-1,{threshold:0}));
                eq((await images.findAllPointsForColor(solid,-1,{threshold:0,region:[0,0,64,64]})).length,4096,'point boundary');
                await images.recycle(solid); await images.recycle(white);
                return {bounded:true};
            """.trimIndent())
            BunHostCapabilityGrants.setImageFilesGranted(context,false)
            BunHostCapabilityGrants.setTextOpenccGranted(context,false)
            execute("bun", """
                eq(host.hasCapability('image.files'),false,'files grant');
                await rejects(() => images.readImage(${JSONObject.quote(file.path)}));
                await rejects(() => cc.s2t('汉字'));
                const response = await fetch('http://autojs6/v1/image.files',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',
                  headers:{'Content-Type':'application/json'},body:JSON.stringify({method:'readImage',args:[${JSONObject.quote(file.path)}]})});
                eq(response.status,403,'raw grant bypass');
                await rejects(() => host.call('image.process',{method:'readImage',args:[${JSONObject.quote(file.path)}]}));
                const img = await images.fromBase64('${encode(1,1,intArrayOf(-65536))}');
                eq(await images.pixel(img,0,0),-65536,'processing remains granted');
                await images.recycle(img);
                return {denied:true};
            """.trimIndent())
        } finally { file.delete() }
        writeEvidence("transport-grants")
    }

    private fun encode(width:Int,height:Int,pixels:IntArray):String {
        val bitmap=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it); Base64.encodeToString(it.toByteArray(),Base64.NO_WRAP) } }
        finally { bitmap.recycle() }
    }

    private fun execute(kind:String,body:String):JSONObject {
        val marker="OPENCC_IMAGES_${UUID.randomUUID()}:"
        val imports=when(kind) {
            "bun" -> "const {createHostClient}=await import(process.env.AUTOJS6_HOST_CLIENT_MODULE); const host=await createHostClient(); const cc=host.opencc, images=host.images;"
            "node-esm" -> "const cc=(await import('autojs6:opencc')).default, images=(await import('images')).default;"
            else -> "const cc=require('autojs6:opencc'), images=require('images');"
        }
        val source="""
            ${if(kind=="bun") "\"bun\";" else "\"nodejs\";"}
            (async()=>{
              $imports
              function eq(actual,expected,label) { if(JSON.stringify(actual)!==JSON.stringify(expected)) throw Error(label+': '+JSON.stringify(actual)+' != '+JSON.stringify(expected)); }
              async function rejects(action) { try { await action(); } catch(error) { return; } throw Error('Expected rejection'); }
              const result=await(async()=>{ $body })();
              console.log(${JSONObject.quote(marker)}+JSON.stringify(result));
            })().catch(error=>{console.error(error.stack||String(error)); process.exitCode=1;});
        """.trimIndent()
        val console=ConsoleImpl(global.uiHandler,global)
        val output:String
        if(kind=="bun") {
            val engine=BunPluginScriptEngine(context,global){console}
            try { engine.init(); engine.execute(StringScriptSource("opencc-images.bun.js",source)) }
            catch(error:Throwable) { throw AssertionError("Bun: "+console.snapshotLogEntries().joinToString("\n"){it.content.toString()}.takeLast(5000),error) }
            finally { engine.destroy() }
            output=console.snapshotLogEntries().joinToString("\n"){it.content.toString()}
        } else {
            try {
                val manifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("opencc","image"))
                val result=NodeJsRuntimePluginBridge.runScript(context,NodePluginScriptRequest(
                    source=source,sourceName=if(kind=="node-esm") "opencc-images.mjs" else "opencc-images.cjs",
                    workingDirectory=context.cacheDir.path,timeoutMs=90_000,
                    runtimeModuleSources=mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                    esmEnabled=kind=="node-esm",dynamicImportEnabled=true),console=console)
                assertTrue("$kind ${result.errorCode} ${result.errorMessage}\n${result.stderr.takeLast(5000)}",result.succeeded && !result.timedOut)
                output=result.stdout
            } finally { console.onRuntimeExit() }
        }
        val line=output.lineSequence().lastOrNull{it.contains(marker)} ?: throw AssertionError("$kind: ${output.takeLast(5000)}")
        val result=JSONObject(line.substringAfter(marker))
        evidence.put(JSONObject().put("runtime",kind).put("result",result))
        return result
    }

    private fun withGrants(action:()->Unit) {
        val store=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val keys=listOf(BunHostCapabilityGrants.TEXT_OPENCC,BunHostCapabilityGrants.IMAGE_PROCESS,BunHostCapabilityGrants.IMAGE_FILES)
        val old=keys.associateWith{if(store.contains(it))store.getBoolean(it,false) else null}
        store.edit().apply{keys.forEach{putBoolean(it,true)}}.commit()
        try { action() } finally { store.edit().apply{old.forEach{(key,value)->if(value==null)remove(key) else putBoolean(key,value)}}.commit() }
    }
    private fun writeEvidence(name:String) { File(context.getExternalFilesDir(null),"opencc-images-$name.json").writeText(JSONObject().put("cases",evidence).toString(2)) }
}
