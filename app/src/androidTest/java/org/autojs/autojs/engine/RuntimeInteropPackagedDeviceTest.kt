package org.autojs.autojs.engine

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Produces and exercises genuine APK Builder outputs with application versionCode 1. */
class RuntimeInteropPackagedDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val args=InstrumentationRegistry.getArguments()
    private val fixture=RuntimeUiComposeDeviceTest()
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val kind get()=requireNotNull(args.getString("runtimeKind")).also{require(it in listOf("node","bun"))}
    private val packageName get()="org.autojs.autojs6.interop.$kind"

    @Test fun bunProjectTimeoutAndExecutionOverride() {
        val root=File(context.cacheDir,"bun-project-timeout-${java.util.UUID.randomUUID()}").apply{mkdirs()}
        val source=File(root,"main.bun.js").apply { writeText("\"bun\"; await new Promise(r=>setTimeout(r,1200));") }
        val project=ProjectConfig().setName("Bun timeout fixture").setMainScriptFileName(source.name).setProjectType("bun")
            .setBunConfig(ProjectConfig.BunConfig().setTimeoutMs(150L))
        File(root,"project.json").writeText(project.toJson(false))
        assertEquals(150L,ProjectConfig.fromProjectDirForExecution(root.path)?.bunConfig?.timeoutMs)
        fun run(explicit:Long?) {
            val engine=BunPluginScriptEngine(context,org.autojs.autojs.AutoJs.instance.globalConsole)
            engine.setTag(org.autojs.autojs.execution.ExecutionConfig.tag,org.autojs.autojs.execution.ExecutionConfig().apply {
                workingDirectory=root.path
                explicit?.let { setArgument("bun.timeoutMillis",it) }
            })
            try { engine.init();engine.execute(org.autojs.autojs.script.JavaScriptFileSource(source)) } finally { engine.destroy() }
        }
        try {
            try { run(null);fail("Project timeout ignored") } catch(error:BunRuntimeExecutionException) { assertTrue(error.message.orEmpty().contains("TIMEOUT")) }
            run(10000L)
        } finally { check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively() }
    }

    @Test fun produce() {
        val root=File(context.cacheDir,"runtime-interop-package-$kind").apply{mkdirs()}
        val output=File(context.getExternalFilesDir(null),"runtime-interop-$kind.apk")
        try {
            val entry=if(kind=="bun")"main.bun.js" else "main.cjs"
            val config=ProjectConfig().setName("Interop $kind").setPackageName(packageName)
                .setVersionName("1.0.0").setVersionCode(1).setMainScriptFileName(entry).setProjectType(kind)
                .setSourcePath(root.path).setAbis(listOf("arm64-v8a")).setLibs(emptyList()).setPermissions(listOf(android.Manifest.permission.INTERNET))
            config.launchConfig.apply{isSplashVisible=false;isLogsVisible=false;isRunOnBoot=false;requiresSharedStorage=false}
            if(kind=="node")config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(listOf("ui")).setTimeoutMs(120000L))
            else {
                config.setBunConfig(ProjectConfig.BunConfig().setHostCapabilities(listOf("ui.layout","ui.compose")).setTimeoutMs(180000L))
                val sdk=File(context.getExternalFilesDir(null),"interop-host-client.mjs")
                assertEquals("The SDK must match the recorded candidate",args.getString("sdkSha256"),sha256(sdk))
                sdk.copyTo(File(root,"host-client.mjs"),overwrite=true)
            }
            val imports=if(kind=="node")"const ui=require('ui'),compose=require('compose');" else "const {createHostClient}=await import('./host-client.mjs');const host=await createHostClient(),ui=host.ui,compose=host.compose;"
            File(root,entry).writeText("\"$kind\";\n(async()=>{$imports try {"+"""
                const ctx=await ui.context();
                if(ctx.packageName!=='$packageName'||ctx.applicationVersionCode!==1||ctx.runtimeVersionCode!==${BuildConfig.VERSION_CODE})throw Error('Wrong packaged identity '+JSON.stringify(ctx));
                const browserRoot=await ui.inflate('<vertical><input id="fixture" text="Fixture-$kind" singleLine="true"/><webview id="browser" h="300" javaScriptEnabled="true"/></vertical>');
                const browserWindow=await ui.showLayout(browserRoot),browser=await browserRoot.findView('browser'),address=await browserRoot.findView('fixture');
                let browserDownload,job,lastSelection,chooser=0,prompts=0,loaded=0;const localJobs=[];
                async function browserWait(fn,label){const end=Date.now()+60000;while(!await fn()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,80));}}
                async function downloaded(job){let state;await browserWait(async()=>{state=await browser.getDownload(job.id);if(state.status==='failed')throw Error(state.error||state.reason);return state.status==='successful';},'local download');return state;}
                try {
                  await browserWait(async()=>String(await address.text).startsWith('http://127.0.0.1:'),'packaged fixture address');const base=await address.text;
                  await browser.setWebViewClient({onPageFinished(){loaded++;}},{resourceRules:[{url:base+'/virtual.js',response:{mimeType:'application/javascript',body:'window.virtualLoaded=73;'}}]});
                  await browser.setWebChromeClient({onJsPrompt(){prompts++;return 'packaged-$kind';},async onShowFileChooser(e){chooser++;lastSelection=await this.chooseFiles({...e,initialUri:'content://org.autojs.autojs6.test.web.documents/root/fixtures'});return lastSelection;}});
                  await browser.setDownloadListener(e=>browserDownload=e);await browser.loadUrl(base+'/index');
                  await browserWait(async()=>await browser.evaluateJavascript('document.readyState==="complete"&&!!document.getElementById("pick")')===true,'packaged web page');
                  if(await browser.evaluateJavascript('window.virtualLoaded')!==73||await browser.evaluateJavascript('prompt("Packaged prompt")')!=='packaged-$kind')throw Error('Packaged WebView callbacks');
                  await browser.evaluateJavascript('document.getElementById("pick").textContent="Packaged-$kind-pick"');
                  await browserWait(async()=>await browser.evaluateJavascript('window.uploaded')===true,'packaged file upload');
                  if((await browser.evaluateJavascript('window.fileTexts'))[0]!==${JSONObject.quote(RuntimeWebDocumentsProvider.content("alpha"))})throw Error('Packaged file bytes');
                  await browser.evaluateJavascript('document.getElementById("download").textContent="Packaged-$kind-download"');
                  await browserWait(()=>browserDownload,'packaged download event');
                  job=await browser.download({url:browserDownload.url,fileName:'autojs6-packaged-web-$kind-'+Date.now()+'.txt'});
                  await browserWait(async()=>{const state=await browser.getDownload(job.id);if(state.status==='failed')throw Error('Packaged download '+state.reason);return state.status==='successful';},'packaged download completion');
                  if(new TextDecoder().decode(await browser.readDownload(job.id))!==${JSONObject.quote(RuntimeWebFixtureServer.DOWNLOAD)}||!loaded||chooser!==1||prompts!==1)throw Error('Packaged browser results');
                  const blobUrl=await browser.evaluateJavascript('(function(){var data=new Uint8Array(131241);for(var i=0;i<data.length;i++)data[i]=(i*19+137)%256;return URL.createObjectURL(new Blob([data]));})()');
                  const blobJob=await browser.download({url:blobUrl,fileName:'autojs6-packaged-blob-$kind-'+Date.now()+'.bin'});localJobs.push(blobJob.id);await downloaded(blobJob);
                  let offset=0;while(offset<131241){const chunk=await browser.readDownload(blobJob.id,offset);if(!chunk.length)throw Error('Packaged short blob');for(let i=0;i<chunk.length;i++)if(chunk[i]!==((offset+i)*19+137)%256)throw Error('Packaged blob byte');offset+=chunk.length;}
                  await browser.loadUrl(base+'/capture');await browserWait(async()=>await browser.evaluateJavascript('document.readyState==="complete"&&!!document.getElementById("capture")')===true,'packaged camera page');
                  await browser.evaluateJavascript('document.getElementById("capture").textContent="Packaged-$kind-camera"');await browserWait(async()=>await browser.evaluateJavascript('window.uploaded')===true,'packaged camera upload');
                  const photo=await browser.evaluateJavascript('window.photoInfo');if(photo.size<20000||!lastSelection||photo.width<200)throw Error('Packaged camera photo');
                  const saved=await browser.chooseFiles({mode:'save',fileName:'autojs6-webfile-fixture-$kind-photo-'+Date.now()+'.jpg',acceptTypes:['image/jpeg'],initialUri:'content://org.autojs.autojs6.test.web.documents/document/outputs'});if(!saved)throw Error('Packaged save cancelled');
                  const photoJob=await browser.download({url:lastSelection[0],destinationUri:saved[0],mimeType:'image/jpeg'});localJobs.push(photoJob.id);const savedState=await downloaded(photoJob);if(savedState.bytesDownloaded!==photo.size)throw Error('Packaged saved photo size');
                  const bytes=new Uint8Array(photo.size);offset=0;while(offset<bytes.length){const chunk=await browser.readDocument(saved[0],offset);if(!chunk.length)throw Error('Packaged short photo');bytes.set(chunk,offset);offset+=chunk.length;}
                  const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');
                  await address.set({text:'Photo-$kind-'+photo.size+'-'+hash});
                  await browserWait(async()=>await address.text==='Photo-checked','photo byte acknowledgement');
                  const tree=await browser.chooseFiles({mode:'directory',writable:true,initialUri:'content://org.autojs.autojs6.test.web.documents/document/outputs'});if(!tree)throw Error('Packaged tree cancelled');
                  const treeJob=await browser.download({url:'data:text/plain;charset=UTF-8,packaged%20tree%20%2B%20%E4%BD%A0',directoryUri:tree[0],fileName:'autojs6-webfile-fixture-$kind-tree-'+Date.now()+'.txt'});localJobs.push(treeJob.id);const treeState=await downloaded(treeJob);
                  if(new TextDecoder().decode(await browser.readDocument(treeState.contentUri))!=='packaged tree + 你')throw Error('Packaged tree file');await browser.cancelDownload(treeJob.id);
                  if(new TextDecoder().decode(await browser.readDocument(treeState.contentUri))!=='packaged tree + 你')throw Error('SAF output deleted');
                } finally {if(job)await browser.cancelDownload(job.id);for(const id of localJobs)await browser.cancelDownload(id).catch(()=>{});await browserWindow.close();await browserRoot.dispose();}
                const components=await ui.inflate('<drawer id="drawer"><vertical><toolbar id="toolbar" title="Packaged components" h="56" navigationIcon="@android:drawable/ic_menu_more" navigationContentDescription="Packaged-$kind-open"/><viewpager id="pager" h="64"><text text="First page"/><text text="Second page"/></viewpager><webview id="web" h="64" javaScriptEnabled="true"/></vertical><vertical w="220" h="*" layout_gravity="start" bg="#ffffff"><button id="close" text="Packaged-$kind-close"/></vertical></drawer>');
                const componentWindow=await ui.showLayout(components),toolbar=await components.findView('toolbar'),pager=await components.findView('pager'),web=await components.findView('web');
                let navigation=0,menu=0,closed=0,componentError;
                const guard=fn=>()=>Promise.resolve().then(fn).catch(e=>componentError=e);
                toolbar.on('navigationClick',guard(async()=>{navigation++;await components.openDrawer('start',false);}));
                (await components.findView('close')).on('click',guard(async()=>{await components.closeDrawer('start',false);closed++;}));
                toolbar.on('menuClick',guard(async()=>{await pager.setCurrentItem(1,false);menu++;}));
                await web.loadHtml('<title>Packaged WebView</title><p>Local page</p>');
                const webEnd=Date.now()+15000;while(await web.title!=='Packaged WebView'&&Date.now()<webEnd)await new Promise(r=>setTimeout(r,30));
                if(await web.evaluateJavascript('21*2')!==42)throw Error('Packaged WebView result');
                toolbar.menu=[{id:'next',title:'Packaged-$kind-page',showAsAction:'always'}];await toolbar.flush();
                const componentEnd=Date.now()+45000;while((!navigation||!menu||!closed)&&!componentError&&Date.now()<componentEnd)await new Promise(r=>setTimeout(r,30));
                if(componentError)throw componentError;if(!navigation||!menu||!closed||await pager.currentItem!==1||await pager.pageCount!==2)throw Error('Packaged component callbacks');
                await componentWindow.close();await components.dispose();
                const root=await ui.inflate('<vertical><card cardCornerRadius="8"><text text="Identity-$kind-'+ctx.applicationVersionCode+'-'+ctx.runtimeVersionCode+'"/></card><spinner id="spinner" entries="One|Two|Three"/><seekbar id="seek" max="100"/><compose id="content" h="240"/></vertical>');
                const window=await ui.showLayout(root),native=await ui.createView('Button',{text:'Packaged-$kind-native'});
                const spinner=await root.findView('spinner'),seek=await root.findView('seek');
                spinner.selectedIndex=2;seek.progress=75;native.text='Packaged-$kind-native';await spinner.flush();await seek.flush();await native.flush();
                let nativeClicks=0;native.on('click',()=>nativeClicks++);const count=compose.state(0);
                const session=await compose.attach(await root.findView('content'),()=>compose.Column({},
                    compose.AndroidView({key:'native',view:native}),compose.Text({text:'Packaged-$kind-count:'+count.value}),
                    compose.Button({onClick(){count.value++;}},compose.Text({text:'Packaged-$kind-next'}))));
                let composeFailure;session.on('error',e=>composeFailure=e.message);
                const end=Date.now()+120000;while((count.value<2||nativeClicks<1)&&Date.now()<end){if(composeFailure||session.isClosed())throw Error('Packaged Compose: '+(composeFailure||'session closed'));await new Promise(r=>setTimeout(r,30));}
                if(count.value!==2||nativeClicks!==1)throw Error('Packaged callbacks missing');
                if(await spinner.selectedIndex!==2||await seek.progress!==75||await native.text!=='Packaged-$kind-native')throw Error('Packaged XML properties lost');
                await session.close();await native.dispose();await window.close();await root.dispose();
            """.trimIndent()+"} catch(e){const errorView=await ui.createView('Text',{text:'Packaged failure: '+String(e.stack||e)});await ui.showLayout(errorView);await new Promise(r=>setTimeout(r,40000));throw e;}})().catch(e=>{console.error(e.stack);throw e;});\n")
            File(root,"project.json").writeText(config.toJson(false))
            val result=RemoteApkBuildClient.buildBlocking(context,config,output,AtomicBoolean(false),
                RemoteApkBuildClient.ProgressListener{},ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            assertTrue("APK Builder: ${result.errors}",result.isSuccess)
            val archive=requireNotNull(context.packageManager.getPackageArchiveInfo(output.path,android.content.pm.PackageManager.GET_PERMISSIONS))
            assertEquals(packageName,archive.packageName);assertEquals(1L,PackageInfoCompat.getLongVersionCode(archive))
            assertFalse(archive.requestedPermissions.orEmpty().contains("org.autojs.permission.PLUGIN"))
            assertTrue(archive.requestedPermissions.orEmpty().contains(android.Manifest.permission.INTERNET))
            assertTrue("Packaged runtime permission must be present in the binary Manifest",
                archive.requestedPermissions.orEmpty().contains(if(kind=="bun") "org.autojs.permission.BUN_PACKAGED_RUNTIME" else "org.autojs.plugin.nodejs.permission.RUNTIME"))
            File(output.parentFile,output.name+".json").writeText(JSONObject().put("packageName",packageName)
                .put("runtimeVersionCode",BuildConfig.VERSION_CODE).put("applicationVersionCode",1).put("sha256",sha256(output)).toString(2))
        } finally {check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
    }

    @Test fun runInstalledOutput() = harness.withFixture { RuntimeWebFixtureServer().use { server ->
        val launch=requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName))
        harness.automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0)
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun failure(node:AccessibilityNodeInfo?):String? {
            if(node==null)return null
            val text=node.text?.toString();if(text?.startsWith("Packaged failure:")==true)return text
            for(i in 0 until node.childCount)failure(node.getChild(i))?.let{return it};return null
        }
        fun wait(label:String,timeout:Long=15000,predicate:()->Boolean) {
            harness.await(label,timeout){failure(harness.automation.rootInActiveWindow)?.let{fail(it+"\nHTTP fixture: "+server.requests)};predicate()}
        }
        fun click(label:String){
            wait("Packaged action: $label",75000){
                var node=fixture.find(label) ?: return@wait false
                while(!node.isClickable&&node.parent!=null)node=node.parent
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        fun findNode(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
            fun walk(node:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(node==null)return null;if(node.isVisibleToUser&&predicate(node))return node;for(i in 0 until node.childCount)walk(node.getChild(i))?.let{return it};return null}
            return walk(harness.automation.rootInActiveWindow)
        }
        fun tap(node:AccessibilityNodeInfo){val box=android.graphics.Rect();node.getBoundsInScreen(box);android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("input touchscreen tap ${box.centerX()} ${box.centerY()}")).use{it.readBytes()}}
        fun button(label:String,values:List<String>){wait(label,45000){var node=findNode{it.text?.toString() in values}?:return@wait false;while(!node.isClickable&&node.parent!=null)node=node.parent;node.performAction(AccessibilityNodeInfo.ACTION_CLICK)}}
        wait("Packaged fixture address",45000) {
            fixture.find("Fixture-$kind")?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,server.url)
            })==true
        }
        click("Packaged-$kind-pick")
        wait("Packaged document picker",20000){fixture.find("aj6-web-fixture-alpha.txt")!=null}
        harness.automation.waitForIdle(500,5000)
        var fileNode=requireNotNull(fixture.find("aj6-web-fixture-alpha.txt"));var fileParent=fileNode.parent
        while(fileParent!=null){if(fileParent.viewIdResourceName?.endsWith(":id/item_root")==true){fileNode=fileParent;break};fileParent=fileParent.parent}
        val bounds=android.graphics.Rect();fileNode.getBoundsInScreen(bounds)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}")).use{it.readBytes()}
        android.os.SystemClock.sleep(800)
        if(fixture.find("aj6-web-fixture-alpha.txt")!=null) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}")).use{it.readBytes()}
        }
        click("Packaged-$kind-download")
        click("Packaged-$kind-camera")
        wait("Packaged system camera",30000){findNode{it.isEnabled&&(it.viewIdResourceName?.endsWith(":id/shutter_button")==true||it.viewIdResourceName?.endsWith(":id/v9_shutter_button_internal")==true)}!=null}
        harness.automation.waitForIdle(500,5000);android.os.SystemClock.sleep(800)
        tap(requireNotNull(findNode{it.viewIdResourceName?.endsWith(":id/shutter_button")==true||it.viewIdResourceName?.endsWith(":id/v9_shutter_button_internal")==true}))
        var lastCameraAccept=0L
        wait("Packaged camera result",45000){
            if(harness.automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true)return@wait true
            val accept=findNode{val id=it.viewIdResourceName.orEmpty();val text=(it.contentDescription?:it.text)?.toString();id.endsWith(":id/intent_done_apply")||id.endsWith(":id/done_button")||id.endsWith(":id/v9_btn_done")||text in listOf("Done","OK","Save","完成","确定","確定")}
            if(accept?.isEnabled==true&&android.os.SystemClock.uptimeMillis()-lastCameraAccept>800){tap(accept);lastCameraAccept=android.os.SystemClock.uptimeMillis()};false
        }
        button("Save camera document",listOf("SAVE","Save","保存"))
        wait("Copied camera bytes",60000){
            val field=findNode{it.text?.toString()?.startsWith("Photo-$kind-")==true}?:return@wait false
            val match=requireNotNull(Regex("Photo-$kind-(\\d+)-([a-f0-9]{64})").matchEntire(field.text.toString()))
            val size=match.groupValues[1].toInt();val upload=server.uploads.lastOrNull()?:return@wait false
            val start=(0 until upload.size-2).first{upload[it]==(-1).toByte()&&upload[it+1]==(-40).toByte()&&upload[it+2]==(-1).toByte()}
            val digest=MessageDigest.getInstance("SHA-256").digest(upload.copyOfRange(start,start+size)).joinToString(""){"%02x".format(it.toInt() and 255)}
            assertEquals("Saved camera content differs from the actual upload",digest,match.groupValues[2])
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Photo-checked")})
        }
        button("Select output directory",listOf("USE THIS FOLDER","Use this folder","使用此文件夹","使用此資料夾"))
        button("Grant output directory",listOf("ALLOW","Allow","允许","允許"))
        // The navigation control is located by content description; its icon has no text.
        fun navigation(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.contentDescription?.toString()=="Packaged-$kind-open"&&node.isVisibleToUser)return node
            for(i in 0 until node.childCount)navigation(node.getChild(i))?.let{return it}
            return null
        }
        val pageAction="Packaged-$kind-page".uppercase(java.util.Locale.ROOT)
        wait("Packaged WebView initialized",45000){fixture.find(pageAction)!=null}
        wait("Packaged drawer action",20000){navigation(harness.automation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true}
        click("Packaged-$kind-close");click(pageAction)
        wait("Packaged runtime identity",45000){fixture.find("Identity-$kind-1-${BuildConfig.VERSION_CODE}")!=null}
        click("Packaged-$kind-native");click("Packaged-$kind-next")
        wait("Packaged state"){fixture.find("Packaged-$kind-count:1")!=null}
        harness.automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
        val display=(context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        wait("Packaged landscape display"){display.rotation==android.view.Surface.ROTATION_90}
        wait("Packaged state after rotation"){failure(harness.automation.rootInActiveWindow)?.let{fail(it)};fixture.find("Packaged-$kind-count:1")!=null}
        click("Packaged-$kind-next")
        wait("Packaged window released"){fixture.find("Packaged-$kind-next")==null}
        assertEquals(2,server.uploads.size)
        assertTrue(server.uploads.first().toString(Charsets.UTF_8).contains(RuntimeWebDocumentsProvider.content("alpha")))
    } }

    private fun sha256(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it.toInt() and 255)}
}
