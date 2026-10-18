package org.autojs.autojs.engine

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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

class RuntimeWebViewDeviceTest {
    private val fixture = RuntimeUiComposeDeviceTest()
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val modes = InstrumentationRegistry.getArguments().getString("webMode")?.let(::listOf)
        ?: listOf("node", "node-esm", "worker", "worker-esm", "worker-nested", "bun")
    private val evidence = JSONArray()
    private val initialUri get() = DocumentsContract.buildRootUri(context.packageManager.getPackageInfo(instrumentation.context.packageName,
        PackageManager.GET_PROVIDERS).providers!!.single { it.name == RuntimeWebDocumentsProvider::class.java.name }.authority, "fixtures").toString()

    @Test fun clientsPoliciesDialogsPopupAndDownloadsAcrossRuntimes() = fixture.grants {
        RuntimeWebFixtureServer().use { server -> harness.withFixture {
            val prefix = "autojs6-web-fixture-${UUID.randomUUID()}"
            try {
                for (mode in modes) {
                    val executor = Executors.newSingleThreadExecutor()
                    try {
                        val future = executor.submit<JSONObject> { run(mode, """
                            const base='${server.url}',web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);
                            let downloadId,downloadEvent,child,childWindow,popupClosed=false;
                            let started=0,finished=0,committed=0,resources=0,history=0,auth=0,blocked=0,intercepted=0,httpError=0,dialogs=0,consoleMessages=0,visited=0;
                            async function wait(fn,label){const end=Date.now()+30000;while(!await fn()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,60));}}
                            try {
                              await web.setWebViewClient({onPageStarted(){started++;},onPageFinished(){finished++;},onPageCommitVisible(){committed++;},onLoadResource(){resources++;},doUpdateVisitedHistory(){history++;},
                                onReceivedHttpAuthRequest(e){auth++;if(e.realm!=='AutoJs6-$mode')throw Error('Wrong auth realm');return {username:'$mode',password:'secret-$mode'};},
                                shouldOverrideUrlLoading(e){if(e.blocked&&e.mainFrame)blocked++;},shouldInterceptRequest(e){if(e.intercepted)intercepted++;},
                                onReceivedHttpError(e){if(e.url.endsWith('/missing')&&e.statusCode===404&&Object.entries(e.responseHeaders).some(([key,value])=>key.toLowerCase()==='x-fixture'&&value==='missing'))httpError++;}
                              },{navigationRules:[{urlPrefix:base+'/blocked',action:'block'}],resourceRules:[{url:base+'/virtual.js',response:{mimeType:'application/javascript',body:'window.virtualLoaded=41;'}}]});
                              await web.setWebChromeClient({getVisitedHistory(){visited++;return [base+'/index'];},onConsoleMessage(e){if(e.message==='web-fixture')consoleMessages++;},onJsAlert(){dialogs++;return true;},
                                async onJsConfirm(){dialogs++;if(await this.title!=='Web callbacks')throw Error('UI read blocked in callback');return true;},onJsPrompt(){dialogs++;return 'answer-$mode';},
                                async onCreateWindow(){child=await ui.createView('WebView',{javaScriptEnabled:true});await child.setWebChromeClient({onCloseWindow(){popupClosed=true;}});childWindow=await ui.showLayout(child);return child;}
                              },{consumeConsoleMessages:true,allowAutomaticWindows:true});
                              await web.setDownloadListener(e=>{downloadEvent=e;});
                              console.log('Web stage: page');await web.loadUrl(base+'/index');await wait(async()=>await web.title==='Web callbacks'&&await web.evaluateJavascript('document.readyState')==='complete','initial page');
                              eq(await web.evaluateJavascript('window.virtualLoaded'),41,'intercepted resource');
                              eq(await web.evaluateJavascript('[alert("alert"),confirm("confirm"),prompt("prompt","seed")]'),[null,true,'answer-$mode'],'dialog decisions');
                              await web.evaluateJavascript('location.href="'+base+'/blocked"');await wait(()=>blocked>0,'navigation policy');
                              if(await web.title!=='Web callbacks')throw Error('Blocked navigation replaced page');
                              await web.loadUrl(base+'/auth/$mode');await wait(async()=>await web.title==='Authenticated','HTTP credentials');
                              await web.loadUrl(base+'/missing');await wait(()=>httpError>0,'HTTP error callback');
                              await web.loadUrl(base+'/index');await wait(async()=>await web.title==='Web callbacks'&&await web.evaluateJavascript('document.readyState')==='complete','return to page');
                              console.log('Web stage: popup');await web.evaluateJavascript('window.open("'+base+'/popup")');await wait(async()=>child&&await child.title==='Popup','popup WebView');
                              const closing=child.evaluateJavascript('window.close()').catch(()=>null);await wait(()=>popupClosed,'popup close callback');
                              await childWindow.close();await child.dispose();await closing;child=null;childWindow=null;
                              console.log('Web stage: download');await web.evaluateJavascript('document.getElementById("download").textContent="Download-$mode-ready"');
                              await wait(()=>downloadEvent,'download listener');
                              if(!['web-fixture.txt','web-fixture.bin'].includes(downloadEvent.suggestedFileName)||downloadEvent.contentDisposition!=='attachment; filename=web-fixture.txt'||downloadEvent.contentLength!==${RuntimeWebFixtureServer.DOWNLOAD.toByteArray().size})throw Error('Download metadata '+JSON.stringify(downloadEvent));
                              const job=await web.download({url:downloadEvent.url,fileName:'$prefix-$mode.txt'});downloadId=job.id;
                              await wait(async()=>{const status=await web.getDownload(job.id);if(status.status==='failed')throw Error('Download failed '+status.reason);return status.status==='successful';},'download bytes');
                              eq(new TextDecoder().decode(await web.readDownload(job.id)),${JSONObject.quote(RuntimeWebFixtureServer.DOWNLOAD)},'download content');
                              await window.close();await web.dispose();
                              const reader=await ui.createView('WebView');try{eq((await reader.getDownload(job.id)).status,'successful','persistent download');if(!await reader.cancelDownload(job.id))throw Error('Download removal');downloadId=null;}finally{await reader.dispose();}
                              if(!started||!finished||!committed||!resources||!history||!auth||!intercepted||!consoleMessages||!visited||dialogs!==3)throw Error('Callbacks '+JSON.stringify({started,finished,committed,resources,history,auth,intercepted,consoleMessages,dialogs}));
                              return {started,finished,committed,resources,history,auth,blocked,intercepted,httpError,dialogs,consoleMessages,visited,popupClosed,downloaded:true};
                            } finally {if(downloadId)await web.cancelDownload(downloadId).catch(()=>{});if(childWindow)await childWindow.close().catch(()=>{});if(child)await child.dispose().catch(()=>{});await window.close().catch(()=>{});await web.dispose().catch(()=>{});}
                        """.trimIndent()) }
                        click("Download-$mode-ready", future)
                        evidence.put(JSONObject().put("mode", mode).put("result", future.get(60, TimeUnit.SECONDS)))
                    } finally { executor.shutdownNow() }
                }
                assertFalse("Blocked navigation reached the HTTP server", server.requests.any { it.startsWith("/blocked") })
                assertFalse("Intercepted script reached the HTTP server", server.requests.contains("/virtual.js"))
            } finally { cleanDownloads(server.url, prefix) }
        } }; save("clients-downloads")
    }

    @Test fun realSystemChooserUploadsAndCancellationAcrossRuntimes() = fixture.grants {
        RuntimeWebFixtureServer().use { server -> harness.withFixture {
            val before = context.contentResolver.persistedUriPermissions.map { it.uri.toString() to it.isReadPermission }.toSet()
            for ((index, mode) in modes.withIndex()) {
                val multiple = index % 2 == 1
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val future = executor.submit<JSONObject> { run(mode, """
                        const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);const results=[],errors=[];let calls=0;
                        web.on('webCallbackError',e=>errors.push(e));
                        async function wait(fn,label){const end=Date.now()+40000;while(!await fn()){if(Date.now()>end)throw Error('$mode '+label+' '+JSON.stringify({calls,results,errors,page:await web.evaluateJavascript('[window.fileNames,window.fileTexts,window.uploadError]')}));await new Promise(r=>setTimeout(r,60));}}
                        try {
                          await web.setWebChromeClient({async onShowFileChooser(e){calls++;const pending=this.chooseFiles({...e,initialUri:${JSONObject.quote(initialUri)}});if(calls===1)setTimeout(()=>this.cancelFileChooser(),5000);const chosen=await pending;results.push(chosen);return chosen;}});
                          await web.loadUrl('${server.url}/index');await wait(async()=>await web.evaluateJavascript('document.readyState==="complete"&&!!document.getElementById("pick")')===true,'file page');
                          eq(await web.evaluateJavascript('document.getElementById("file").multiple=$multiple;document.getElementById("pick").textContent="Pick-$mode-first"'),'Pick-$mode-first','file button ready');
                          await wait(()=>results.length===1,'cancelled selection');eq(results[0],null,'cancelled URI result');eq(await web.evaluateJavascript('document.getElementById("file").files.length'),0,'cancelled file input');
                          await web.evaluateJavascript('document.getElementById("pick").textContent="Pick-$mode-again"');
                          await wait(async()=>await web.evaluateJavascript('window.uploaded')===true,'file upload');
                          const names=await web.evaluateJavascript('window.fileNames'),texts=await web.evaluateJavascript('window.fileTexts');
                          eq(names.slice().sort(),${if (multiple) "['aj6-web-fixture-alpha.txt','aj6-web-fixture-beta.txt']" else "['aj6-web-fixture-alpha.txt']"},'selected names');
                          if(texts.length!==${if (multiple) 2 else 1}||!texts.includes(${JSONObject.quote(RuntimeWebDocumentsProvider.content("alpha"))}))throw Error('Selected file bytes');
                          if(calls!==2||results[1].length!==${if (multiple) 2 else 1})throw Error('File callbacks');
                          return {calls,cancelled:true,names,texts,uploaded:true};
                        } finally {await window.close();await web.dispose();}
                    """.trimIndent()) }
                    click("Pick-$mode-first", future)
                    awaitNode("first system picker", future) { it.text?.toString() == "aj6-web-fixture-alpha.txt" }
                    click("Pick-$mode-again", future)
                    if (multiple) {
                        action("aj6-web-fixture-alpha.txt", future, true); action("aj6-web-fixture-beta.txt", future)
                        actionNode("confirm multiple files", future) { it.viewIdResourceName?.endsWith(":id/action_menu_select") == true || it.text?.toString() in listOf("OPEN", "Open", "打开", "開啟") }
                    } else action("aj6-web-fixture-alpha.txt", future)
                    evidence.put(JSONObject().put("mode", mode).put("result", future.get(45, TimeUnit.SECONDS)))
                    assertTrue("Document grants were not restored", before == context.contentResolver.persistedUriPermissions.map { it.uri.toString() to it.isReadPermission }.toSet())
                } finally { executor.shutdownNow() }
            }
            assertEquals(modes.size, server.uploads.size)
            assertTrue(server.uploads.all { it.toString(Charsets.UTF_8).contains(RuntimeWebDocumentsProvider.content("alpha")) })
        } }; save("file-chooser")
    }

    @Test fun defaultPromptCanWaitBeyondBunRpcDeadlineAndCallbackReplacementCancels() = fixture.grants {
        harness.withFixture {
            for (mode in listOf("node", "worker-nested", "bun")) {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val future = executor.submit<JSONObject> { run(mode, """
                        const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);
                        const errors=[];web.on('webCallbackError',e=>errors.push(e.message));
                        async function wait(fn){const end=Date.now()+15000;while(!fn()){if(Date.now()>end)throw Error('callback event');await new Promise(r=>setTimeout(r,30));}}
                        try {
                          await web.loadHtml('<title>Dialogs</title>');
                          const text=await web.evaluateJavascript('prompt("Native prompt-$mode","seed")',{timeoutMs:30000});eq(text,'Native response','system prompt');
                          eq(await web.evaluateJavascript('confirm("Native cancel-$mode")'),false,'system cancellation');
                          await web.setWebChromeClient({onJsPrompt(){return 7;}});eq(await web.evaluateJavascript('prompt("bad reply")'),null,'invalid reply cancelled');await wait(()=>errors.length>0);
                          await web.setWebChromeClient({onJsConfirm(){return new Promise(()=>{});}},{callbackTimeoutMs:250});eq(await web.evaluateJavascript('confirm("timeout")'),false,'timeout cancellation');
                          let entered=false,late;await web.setWebChromeClient({onJsConfirm(){entered=true;return new Promise(resolve=>late=resolve);}});
                          const pending=web.evaluateJavascript('confirm("replacement")');await wait(()=>entered);await web.setWebChromeClient(null);eq(await pending,false,'replacement cancellation');late(true);
                          await web.setWebChromeClient({onJsConfirm(){return false;}});eq(await web.evaluateJavascript('confirm("new request")'),false,'stale reply isolation');
                          return {text,errors:errors.length,replacement:true};
                        } finally {await window.close();await web.dispose();}
                    """.trimIndent()) }
                    awaitNode("native prompt", future) { it.text?.toString() == "Native prompt-$mode" }
                    if (mode == "bun") SystemClock.sleep(11000)
                    harness.await("prompt input") {
                        val node = find { it.isEditable } ?: return@await false
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Native response") })
                    }
                    actionNode("confirm prompt", future) { it.viewIdResourceName == "android:id/button1" }
                    awaitNode("native confirm", future) { it.text?.toString() == "Native cancel-$mode" }
                    actionNode("cancel confirm", future) { it.viewIdResourceName == "android:id/button2" }
                    evidence.put(JSONObject().put("mode", mode).put("result", future.get(40, TimeUnit.SECONDS)))
                } finally { executor.shutdownNow() }
            }
        }; save("dialogs")
    }

    @Test fun explicitSslDecisionsUseTheRealWebViewClient() = fixture.grants {
        for (mode in listOf("node", "bun")) for (allow in listOf(false, true)) {
            RuntimeWebFixtureServer.tls().use { server ->
                val result = try { run(mode, """
                    const web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);let ssl=0;const errors=[];
                    try {
                      await web.setWebViewClient({onReceivedError(e){errors.push(e);},onReceivedSslError(e){if(typeof e.primaryError!=='number')throw Error('SSL metadata');ssl++;return '${if (allow) "proceed" else "cancel"}';}});
                      await web.loadUrl('${server.url}/tls');const end=Date.now()+20000;
                      while((!ssl||await web.progress!==100)&&Date.now()<end)await new Promise(r=>setTimeout(r,60));
                      if(!ssl)throw Error('SSL callback missing '+JSON.stringify(errors));const title=await web.title;
                      if((title==='TLS fixture')!==$allow)throw Error('Wrong SSL decision '+title);return {ssl,allow:$allow};
                    } finally {await window.close();await web.dispose();}
                """.trimIndent()) } catch (failure: Throwable) { throw AssertionError("TLS fixture connections: ${server.errors}", failure) }
                assertEquals(allow, server.requests.contains("/tls")); evidence.put(JSONObject().put("mode", mode).put("result", result))
            }
        }; save("ssl")
    }

    @Test fun webResultsAndRequestsSurviveNotificationOverflow() {
        instrumentation.runOnMainSync {
            val events = ArrayDeque<JSONObject>(); val queue = RuntimeUiInterop.EventQueue(events)
            queue.emit(JSONObject().put("type","webTaskResult").put("taskId","fixture-result"))
            queue.emit(JSONObject().put("type","webCallback").put("requestId","fixture-request"))
            repeat(300) { queue.emit(JSONObject().put("type","progressChanged").put("progress",it)) }
            assertEquals(128,events.size)
            assertTrue(events.any { it.optString("taskId")=="fixture-result" })
            assertTrue(events.any { it.optString("requestId")=="fixture-request" })
            events.clear()
            repeat(130) { queue.emit(JSONObject().put("type","webTaskResult").put("taskId",it)) }
            queue.emit(JSONObject().put("type","close"));queue.emit(JSONObject().put("type","progressChanged"))
            assertEquals(131,events.size);assertEquals("close",events.last().getString("type"))
        }
    }

    @Test fun nativePoliciesHandleMissingHeadersWithoutReplayingRequests() {
        fun request(method: String) = object : android.webkit.WebResourceRequest {
            override fun getUrl() = android.net.Uri.parse("https://fixture.invalid/blocked")
            override fun isForMainFrame() = true
            override fun isRedirect() = false
            override fun hasGesture() = true
            override fun getMethod() = method
            override fun getRequestHeaders(): MutableMap<String, String>? = null
        }
        val policy = RuntimeWebPolicy(JSONObject("""{"navigationRules":[{"urlPrefix":"https://fixture.invalid/","method":"POST","action":"block"}],"resourceRules":[{"urlPrefix":"https://fixture.invalid/","response":{"body":"fixture"}}]}"""))
        assertEquals(0, RuntimeWebPolicy.request(request("GET")).getJSONObject("headers").length())
        assertFalse(policy.navigation(request("GET"))); assertTrue(policy.navigation(request("POST")))
        repeat(2) { assertEquals("fixture", policy.resource(request("GET"))!!.data.bufferedReader().use { it.readText() }) }
    }

    @Test fun workerExitCancelsPickerAndPreservesReplacementClient() = fixture.grants { harness.withFixture {
        for (mode in listOf("worker", "worker-esm", "worker-nested")) RuntimeWebFixtureServer().use { server ->
            val body = """
                const web=ui.view(workerData);await web.setWebChromeClient({onJsConfirm(){return true;}});
                web.chooseFiles({acceptTypes:['text/plain'],initialUri:${JSONObject.quote(initialUri)}}).catch(()=>{});
                parentPort.postMessage('ready');setInterval(()=>{},1000);
            """.trimIndent()
            val child = if (mode == "worker-esm") "import ui from 'ui';import {parentPort,workerData} from 'node:worker_threads';$body"
                else "const ui=require('ui'),{parentPort,workerData}=require('node:worker_threads');(async()=>{$body})();"
            val source = if (mode == "worker-nested") "const {Worker,parentPort,workerData}=require('node:worker_threads');new Worker(${JSONObject.quote(child)},{eval:true,workerData}).on('message',v=>parentPort.postMessage(v)).on('error',e=>{throw e;});" else child
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<JSONObject> { run("node", """
                    const {Worker}=require('node:worker_threads'),web=await ui.createView('WebView',{javaScriptEnabled:true}),window=await ui.showLayout(web);
                    let worker;
                    try {
                      await web.loadHtml('<title>Worker ownership</title>');
                      worker=new Worker(${if (mode == "worker-esm") "new URL('data:text/javascript,'+encodeURIComponent(${JSONObject.quote(source)})),{workerData:web}" else "${JSONObject.quote(source)},{eval:true,workerData:web}"});
                      await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
                      const end=Date.now()+30000;while(!await (await fetch('${server.url}/worker-stop')).json()){if(Date.now()>end)throw Error('picker readiness');await new Promise(r=>setTimeout(r,100));}
                      await worker.terminate();worker=null;
                      await web.setWebChromeClient({onJsConfirm(){return false;}});
                      eq(await web.evaluateJavascript('confirm("after worker exit")'),false,'replacement client');
                      await new Promise(r=>setTimeout(r,100));eq(await web.evaluateJavascript('confirm("late cleanup")'),false,'late cleanup isolation');
                      return {pickerCancelled:true,replacement:true};
                    } finally {if(worker)await worker.terminate();await window.close();await web.dispose();}
                """.trimIndent()) }
                awaitNode("Worker document picker",future){it.text?.toString()=="aj6-web-fixture-alpha.txt"}
                server.stopWorker=true
                evidence.put(JSONObject().put("mode",mode).put("result",future.get(30,TimeUnit.SECONDS)))
                harness.await("Worker picker dismissed"){find{it.text?.toString()=="aj6-web-fixture-alpha.txt"}==null}
            } finally {executor.shutdownNow()}
        };save("worker-exit")
    } }

    private fun run(mode: String, code: String) = fixture.run(mode, "function eq(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify([a,b]));}\n" + code)
    private fun cleanDownloads(base: String, prefix: String) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query()).use { cursor ->
            while (cursor.moveToNext()) {
                val url = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                if (url == "$base/download" && title.startsWith(prefix)) manager.remove(cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)))
            }
        }
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && predicate(node)) return node
            for (i in 0 until node.childCount) walk(node.getChild(i))?.let { return it }; return null
        }
        return walk(harness.automation.rootInActiveWindow)
    }
    private fun awaitNode(label: String, future: Future<*>, predicate: (AccessibilityNodeInfo) -> Boolean) {
        harness.await(label, 30000) { if (future.isDone) fail(future.get().toString()); find(predicate) != null }
    }
    private fun actionNode(label: String, future: Future<*>, predicate: (AccessibilityNodeInfo) -> Boolean) {
        harness.await(label, 45000) {
            if (future.isDone) fail(future.get().toString())
            var node = find(predicate) ?: return@await false
            while (!node.isClickable && node.parent != null) node = node.parent
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
    private fun action(label: String, future: Future<*>, long: Boolean = false) {
        harness.await(label, 45000) {
            if (future.isDone) fail(future.get().toString())
            var node = find { it.text?.toString() == label || it.contentDescription?.toString() == label } ?: return@await false
            if (label.startsWith("aj6-web-fixture-")) {
                harness.automation.waitForIdle(500, 5000)
                node = find { it.text?.toString() == label || it.contentDescription?.toString() == label } ?: return@await false
                // DocumentsUI's nameplate is not the activation target in grid mode.
                var parent = node.parent
                while (parent != null) { if (parent.viewIdResourceName?.endsWith(":id/item_root") == true) { node = parent; break }; parent = parent.parent }
                val bounds = android.graphics.Rect(); node.getBoundsInScreen(bounds)
                val x = bounds.centerX(); val y = bounds.centerY()
                val command = if (long) "input touchscreen swipe $x $y $x $y 700" else "input touchscreen tap $x $y"
                android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use { it.readBytes() }
                SystemClock.sleep(800)
                if (!long && label.endsWith("alpha.txt") && find { it.text?.toString() == label } != null) {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use { it.readBytes() }
                }
                return@await true
            }
            while (!(if (long) node.isLongClickable else node.isClickable) && node.parent != null) node = node.parent
            node.performAction(if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
    private fun click(label: String, future: Future<*>) = action(label, future)
    private fun save(name: String) { File(context.getExternalFilesDir(null), "runtime-webview-$name.json").writeText(evidence.toString(2)) }
}
