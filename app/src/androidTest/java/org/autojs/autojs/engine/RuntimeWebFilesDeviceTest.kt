package org.autojs.autojs.engine

import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class RuntimeWebFilesDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val ui=RuntimeUiComposeDeviceTest()
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val modes=InstrumentationRegistry.getArguments().getString("webMode")?.let(::listOf) ?: listOf("node","node-esm","worker","worker-esm","worker-nested","bun")
    private val authority get()=context.packageManager.getPackageInfo(instrumentation.context.packageName,PackageManager.GET_PROVIDERS).providers!!.single{it.name==RuntimeWebDocumentsProvider::class.java.name}.authority
    private val directory get()=DocumentsContract.buildDocumentUri(authority,"outputs").toString()
    private val evidence=JSONArray()
    private val helpers="""
        function eq(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify([a,b]));}
        async function pageReady(web,title){const end=Date.now()+15000;while(await web.title!==title||await web.evaluateJavascript('document.readyState')!=='complete'){if(Date.now()>end)throw Error('Page not ready');await new Promise(r=>setTimeout(r,40));}}
        async function done(web,id,expected='successful'){const end=Date.now()+60000;while(Date.now()<end){const value=await web.getDownload(id);if(value&&value.status===expected)return value;if(value&&value.status==='failed')throw Error(value.error||value.reason);await new Promise(r=>setTimeout(r,40));}throw Error('Download deadline');}
        async function verify(web,id,size){let offset=0;while(offset<size){const bytes=await web.readDownload(id,offset);if(!bytes.length)throw Error('Short download');for(let i=0;i<bytes.length;i++)if(bytes[i]!==((offset+i)*19+137)%256)throw Error('Corrupt byte '+(offset+i));offset+=bytes.length;}eq(offset,size,'download size');eq((await web.readDownload(id,offset)).length,0,'EOF');}
    """.trimIndent()

    @Test fun dataAndBlobBytesCancellationAndNavigationAcrossRuntimes()=ui.grants { harness.withFixture {
        val prefix="autojs6-webfiles-${UUID.randomUUID()}"
        for(mode in modes){val result=ui.run(mode,helpers+"""
            const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web),jobs=[];let reader;
            try {
              await web.loadHtml('<title>Web file transfers</title>');await pageReady(web,'Web file transfers');
              const size=2*1024*1024+137;
              const url=await web.evaluateJavascript('(function(){var bytes=new Uint8Array('+size+');for(var i=0;i<bytes.length;i++)bytes[i]=(i*19+137)%256;window.sourceBlob=URL.createObjectURL(new Blob([bytes],{type:"application/octet-stream"}));return window.sourceBlob;})()');
              const blob=await web.download({url,fileName:'$prefix-$mode.bin'});jobs.push(blob.id);if(typeof blob.id!=='string')throw Error('Opaque local download ID');
              const state=await done(web,blob.id);eq(state.bytesDownloaded,size,'native stream bytes');await verify(web,blob.id,size);
              for(const [suffix,url,expected] of [['percent','data:application/octet-stream,%00%FF%80+%2B%E4%BD%A0',[0,255,128,43,43,228,189,160]],['base64','data:application/octet-stream;base64,'+encodeURIComponent(btoa(String.fromCharCode(0,255,128,251,32))),[0,255,128,251,32]]]) {
                const job=await web.download({url,fileName:'$prefix-$mode-'+suffix+'.bin'});jobs.push(job.id);await done(web,job.id);eq([...await web.readDownload(job.id)],expected,'data URL bytes');
              }
              const invalid=await web.download({url:'data:text/plain,%XY',fileName:'$prefix-invalid.txt'});jobs.push(invalid.id);await done(web,invalid.id,'failed');
              await web.evaluateJavascript('URL.revokeObjectURL(window.sourceBlob)');const revoked=await web.download({url,fileName:'$prefix-revoked.bin'});jobs.push(revoked.id);await done(web,revoked.id,'failed');
              const slow=await web.evaluateJavascript('(function(){var original=window.fetch;window.fetch=function(url){return original(url).then(function(response){var reader=response.body.getReader();return new Response(new ReadableStream({pull:function(controller){return new Promise(function(r){setTimeout(r,150)}).then(function(){return reader.read()}).then(function(value){if(value.done)controller.close();else controller.enqueue(value.value)})},cancel:function(){return reader.cancel()}}),{headers:response.headers})})};return URL.createObjectURL(new Blob([new Uint8Array(1048576)]));})()');
              const cancelled=await web.download({url:slow,fileName:'$prefix-cancel.bin'});await web.cancelDownload(cancelled.id);eq(await web.getDownload(cancelled.id),null,'cancelled job removed');
              const navigated=await web.download({url:slow,fileName:'$prefix-navigation.bin'});jobs.push(navigated.id);await web.loadHtml('<title>Replaced</title>');await done(web,navigated.id,'failed');
              await window.close();await web.dispose();reader=await ui.createView('WebView');eq((await reader.getDownload(blob.id)).status,'successful','completed file survives source disposal');
              eq([...await reader.readDownload(blob.id,size-7)],Array.from({length:7},(_,i)=>((size-7+i)*19+137)%256),'read after disposal');
              return {blobBytes:size,percent:true,base64:true,revoked:true,cancelled:true,navigation:true,persisted:true};
            } finally {const target=reader||web;for(const id of jobs)await target.cancelDownload(id).catch(()=>{});if(reader)await reader.dispose();await window.close().catch(()=>{});await web.dispose().catch(()=>{});}
        """.trimIndent());evidence.put(JSONObject().put("mode",mode).put("result",result))};save("downloads")
    } }

    @Test fun blobAndDataLinksReachTheDownloadListener()=ui.grants { harness.withFixture {
        for(mode in listOf("node","worker-nested","bun")){val executor=Executors.newSingleThreadExecutor();try {
            val future=executor.submit<JSONObject>{ui.run(mode,helpers+"""
                const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web),events=[],jobs=[];
                async function wait(count){const end=Date.now()+20000;while(events.length<count){if(Date.now()>end)throw Error('Native download listener missing for $mode '+count+' '+JSON.stringify(await web.evaluateJavascript('[window.linkClicks,document.getElementById("link").outerHTML]')));await new Promise(r=>setTimeout(r,50));}}
                try {
                  await web.setDownloadListener(e=>{events.push(e);});await web.loadHtml('<meta name="viewport" content="width=device-width,initial-scale=1"><title>Page links</title><a id="link" style="display:block;padding:40px;font-size:24px" download="link.bin">Preparing</a>');await pageReady(web,'Page links');
                  await web.evaluateJavascript('(function(){var link=document.getElementById("link");window.linkClicks=[];link.onclick=function(e){var kind=link.href.startsWith("blob:")?"blob":"data";window.linkClicks.push({kind:kind,trusted:e.isTrusted});link.textContent="Clicked-$mode-"+kind;};link.href=URL.createObjectURL(new Blob([new Uint8Array([0,255,128,43])],{type:"application/octet-stream"}));link.textContent="Link-$mode-blob";})()');
                  await wait(1);if(!events[0].url.startsWith('blob:'))throw Error('Blob listener URL');
                  let job=await web.download({url:events[0].url,fileName:'autojs6-webfiles-link-'+Date.now()+'.bin'});jobs.push(job.id);await done(web,job.id);eq([...await web.readDownload(job.id)],[0,255,128,43],'blob link bytes');
                  await web.evaluateJavascript('document.getElementById("link").href="data:application/octet-stream;base64,AP+AKw==";document.getElementById("link").textContent="Link-$mode-data"');
                  await wait(2);if(!events[1].url.startsWith('data:'))throw Error('Data listener URL');
                  job=await web.download({url:events[1].url,fileName:'autojs6-webfiles-link-'+Date.now()+'.bin'});jobs.push(job.id);await done(web,job.id);eq([...await web.readDownload(job.id)],[0,255,128,43],'data link bytes');
                  const clicks=await web.evaluateJavascript('window.linkClicks');eq(clicks.map(e=>e.kind),['blob','data'],'actual page click sequence');return {blobLink:true,dataLink:true,clicks};
                } finally {for(const id of jobs)await web.cancelDownload(id).catch(()=>{});await window.close();await web.dispose();}
            """.trimIndent())}
            anchor(mode,"blob",future);anchor(mode,"data",future)
            evidence.put(JSONObject().put("mode",mode).put("result",future.get(50,TimeUnit.SECONDS)))
        }finally{executor.shutdownNow()}};save("links")
    } }

    @Test fun realSaveAndTreeTargetsPreserveExistingContentsAcrossRuntimes()=ui.grants { harness.withFixture {
        val before=permissions()
        for(mode in modes){val executor=Executors.newSingleThreadExecutor();try {
            val name="autojs6-webfile-fixture-${UUID.randomUUID()}.txt"
            val future=executor.submit<JSONObject>{ui.run(mode,helpers+"""
                const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web),jobs=[];
                try {
                  await web.loadHtml('<title>Save and directory</title>');await pageReady(web,'Save and directory');
                  const saved=await web.chooseFiles({mode:'save',fileName:'$name',acceptTypes:['text/plain'],initialUri:${JSONObject.quote(directory)}});if(!saved)throw Error('Save cancelled');
                  const job=await web.download({url:'data:text/plain;charset=UTF-8,Hello%20%2B%20%E4%BD%A0',destinationUri:saved[0]});jobs.push(job.id);const state=await done(web,job.id);eq(state.managed,false,'SAF file ownership');
                  eq(new TextDecoder().decode(await web.readDocument(saved[0])),'Hello + 你','saved document bytes');await web.cancelDownload(job.id);eq(new TextDecoder().decode(await web.readDocument(saved[0])),'Hello + 你','completed external file preserved');
                  const overwrite=await web.download({url:'data:text/plain,overwrite',destinationUri:saved[0]});jobs.push(overwrite.id);await done(web,overwrite.id,'failed');eq(new TextDecoder().decode(await web.readDocument(saved[0])),'Hello + 你','existing content protected');
                  const tree=await web.chooseFiles({mode:'directory',writable:true,initialUri:${JSONObject.quote(directory)}});if(!tree)throw Error('Tree cancelled');
                  const url=await web.evaluateJavascript('URL.createObjectURL(new Blob(["directory bytes"],{type:"text/plain"}))');const child=await web.download({url,directoryUri:tree[0],fileName:'$name'});jobs.push(child.id);const childState=await done(web,child.id);
                  eq(new TextDecoder().decode(await web.readDocument(childState.contentUri)),'directory bytes','tree child bytes');await web.cancelDownload(child.id);eq(new TextDecoder().decode(await web.readDocument(childState.contentUri)),'directory bytes','tree child preserved');
                  return {save:true,directory:true,overwriteRejected:true,file:saved[0],child:childState.contentUri};
                } finally {for(const id of jobs)await web.cancelDownload(id).catch(()=>{});await window.close();await web.dispose();}
            """.trimIndent())}
            action("Save document",future){it.text?.toString() in listOf("SAVE","Save","保存") || it.viewIdResourceName?.endsWith(":id/button_save")==true}
            action("Use directory",future){it.text?.toString() in listOf("USE THIS FOLDER","Use this folder","使用此文件夹","使用此資料夾")}
            action("Grant directory",future){it.text?.toString() in listOf("ALLOW","Allow","允许","允許") || it.viewIdResourceName=="android:id/button1"}
            evidence.put(JSONObject().put("mode",mode).put("result",future.get(80,TimeUnit.SECONDS)))
            harness.await("Document grants released"){permissions()==before}
        } finally {executor.shutdownNow()}};save("saf")
    } }

    @Test fun realCameraCaptureAndCancellationAcrossRuntimes()=ui.grants { harness.withFixture {
        RuntimeWebFixtureServer().use { server ->
            val captureDirectory=File(context.cacheDir,"runtime-web-capture")
            val before=captureDirectory.list().orEmpty().toSet()
            for((index,mode) in modes.withIndex()) {val executor=Executors.newSingleThreadExecutor();try {
                val cancelFirst=index==0&&mode!="bun"
                val future=executor.submit<JSONObject>{ui.run(mode,helpers+"""
                    const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web),selections=[],errors=[];let captureFlag=false;
                    web.on('webCallbackError',e=>errors.push(e));
                    async function wait(fn,label){const end=Date.now()+60000;while(!await fn()){if(Date.now()>end)throw Error('$mode '+label+' '+JSON.stringify({selections,errors,page:await web.evaluateJavascript('[window.captureError,window.photoInfo]')}));await new Promise(r=>setTimeout(r,60));}}
                    try {
                      ${if(mode!="bun") "await web.setWebChromeClient({onShowFileChooser(e){captureFlag=e.captureEnabled;return this.chooseFiles(e).then(result=>{selections.push(result);return result;});}});" else ""}
                      await web.loadUrl('${server.url}/capture');await pageReady(web,'Camera fixture');
                      await web.evaluateJavascript('document.getElementById("capture").textContent="Camera-$mode-ready"');
                      ${if(cancelFirst) "await wait(()=>selections.length===1,'Camera cancellation');eq(selections[0],null,'cancelled capture');await web.evaluateJavascript('document.getElementById(\"capture\").textContent=\"Camera-$mode-again\"');" else ""}
                      await wait(async()=>await web.evaluateJavascript('window.uploaded')===true,'Camera upload');
                      const selected=selections.at(-1);if(${mode!="bun"}&&(!selected||selected.length!==1||!captureFlag))throw Error('Native capture request');
                      const photo=await web.evaluateJavascript('window.photoInfo'),bytes=selected?await web.readDocument(selected[0],0,3):photo.header;
                      eq([...bytes],[255,216,255],'JPEG header');if(photo.size<20000||photo.width<200||photo.height<200)throw Error('Full-size photo missing');
                      return {captureFlag,defaultPicker:${mode=="bun"},size:photo.size,width:photo.width,height:photo.height,uploaded:true,cancelled:$cancelFirst};
                    } finally {await window.close();await web.dispose();}
                """.trimIndent())}
                action("Camera launch",future){it.text?.toString()=="Camera-$mode-ready"}
                cameraReady(future)
                if(cancelFirst){harness.automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);action("Camera relaunch",future){it.text?.toString()=="Camera-$mode-again"};cameraReady(future)}
                harness.automation.waitForIdle(500,5000);SystemClock.sleep(800)
                tap(requireNotNull(find(::isShutter)))
                var lastAccept=0L
                try { harness.await("Accept camera photo",45000) {
                    if(future.isDone)return@await true
                    val accept=find { val id=it.viewIdResourceName.orEmpty();val text=(it.contentDescription?:it.text)?.toString().orEmpty()
                        id.endsWith(":id/intent_done_apply") || id.endsWith(":id/done_button") || id.endsWith(":id/v9_btn_done") || text in listOf("Done","OK","Save","完成","确定","確定") }
                    if(accept?.isEnabled==true&&SystemClock.uptimeMillis()-lastAccept>800){tap(accept);lastAccept=SystemClock.uptimeMillis()};false
                } } catch(error:Throwable){throw AssertionError("Camera result UI: ${surface()}",error)}
                val result=future.get(80,TimeUnit.SECONDS);evidence.put(JSONObject().put("mode",mode).put("result",result));save("camera")
                assertEquals(1,server.uploads.size);assertTrue(server.uploads.single().size>result.getInt("size"));server.uploads.clear()
                harness.await("Temporary camera files released"){captureDirectory.list().orEmpty().toSet()==before}
            }finally{executor.shutdownNow()}}
        };save("camera")
    } }
    private fun isShutter(node:AccessibilityNodeInfo):Boolean {
        val id=node.viewIdResourceName.orEmpty();val text=(node.contentDescription?:node.text)?.toString().orEmpty()
        return node.isEnabled && (id.endsWith(":id/v9_shutter_button_internal") || id.endsWith(":id/shutter_button") || text in listOf("Shutter","Shutter button","Take picture","快门","拍摄","拍攝"))
    }
    private fun cameraReady(future:Future<*>) {
        harness.await("System camera",45000) {
            if(future.isDone)fail(future.get().toString())
            val hostPermission=find{it.text?.toString()?.contains("AutoJs6")==true}!=null
            var permission=if(hostPermission)find{it.viewIdResourceName?.endsWith(":id/permission_allow_foreground_only_button")==true || it.text?.toString() in listOf("仅在使用中允许","While using the app","WHILE USING THE APP")}else null
            if(permission!=null){while(!permission!!.isClickable&&permission!!.parent!=null)permission=permission!!.parent;permission!!.performAction(AccessibilityNodeInfo.ACTION_CLICK);return@await false}
            find(::isShutter)!=null
        }
    }

    @Test fun pickerModesAndDataDecodingRejectInvalidInputs() {
        assertEquals(3,RuntimeWebFileChooser.mode(JSONObject().put("mode","save")))
        assertEquals(2,RuntimeWebFileChooser.mode(JSONObject().put("mode","directory")))
        assertEquals(1,RuntimeWebFileChooser.mode(JSONObject().put("multiple",true)))
        assertArrayEquals(byteArrayOf(0,-1,-128,43,43),RuntimeWebLocalDownloads.decodeData("%00%FF%80+%2B",false))
        assertArrayEquals(byteArrayOf(0,-1,-128),RuntimeWebLocalDownloads.decodeData("AP%2BA",true))
        try{RuntimeWebLocalDownloads.decodeData("%x0",false);fail("Invalid escape accepted")}catch(_:IllegalArgumentException){}
        try{RuntimeWebFileChooser.mode(JSONObject().put("mode","folder"));fail("Invalid mode accepted")}catch(_:IllegalStateException){}
    }
    private fun permissions()=context.contentResolver.persistedUriPermissions.map{Triple(it.uri.toString(),it.isReadPermission,it.isWritePermission)}.toSet()
    private fun find(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        fun walk(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {if(node==null)return null;if(node.isVisibleToUser&&predicate(node))return node;for(i in 0 until node.childCount)walk(node.getChild(i))?.let{return it};return null}
        return walk(harness.automation.rootInActiveWindow) ?: harness.automation.windows.firstNotNullOfOrNull { walk(it.root) }
    }
    private fun action(label:String,future:Future<*>,predicate:(AccessibilityNodeInfo)->Boolean) {
        val history=LinkedHashSet<String>()
        try { harness.await(label,45000){
            if(future.isDone)fail(future.get().toString())
            if(history.size<8)history.add(surface().toString())
            var node=find(predicate)?:return@await false
            while(!node.isClickable&&node.parent!=null)node=node.parent
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } }
        catch(error:Throwable){throw AssertionError("$label UI: $history",error)}
    }
    private fun anchor(mode:String,kind:String,future:Future<*>) {
        fun labelled(value:String)=find{it.text?.toString()==value||it.contentDescription?.toString()==value}
        var lastClick=0L
        try { harness.await("$mode $kind page click",25000) {
            if(future.isDone){future.get();return@await true}
            if(labelled("Clicked-$mode-$kind")!=null || kind=="blob"&&labelled("Link-$mode-data")!=null)return@await true
            val node=labelled("Link-$mode-$kind")?:return@await false
            if(SystemClock.uptimeMillis()-lastClick>800) {
                if(!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))tap(node)
                lastClick=SystemClock.uptimeMillis()
            };false
        } } catch(error:Throwable){throw AssertionError("$mode $kind anchor UI: ${surface()}",error)}
    }
    private fun surface():JSONArray {
        val rows=JSONArray()
        fun visit(node:AccessibilityNodeInfo?){if(node==null||rows.length()>80)return;if(node.text!=null||node.contentDescription!=null)rows.put(JSONObject().put("id",node.viewIdResourceName).put("text",node.text?.toString()).put("description",node.contentDescription?.toString()).put("visible",node.isVisibleToUser));for(i in 0 until node.childCount)visit(node.getChild(i))}
        for(window in harness.automation.windows){val root=window.root?:continue;if(root.packageName?.toString()=="com.android.systemui")continue;rows.put(JSONObject().put("title",window.title).put("active",window.isActive));visit(root)};return rows
    }
    private fun tap(node:AccessibilityNodeInfo){val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds);android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}")).use{it.readBytes()}}
    private fun save(name:String){File(context.getExternalFilesDir(null),"runtime-web-files-$name.json").writeText(evidence.toString(2))}
}
