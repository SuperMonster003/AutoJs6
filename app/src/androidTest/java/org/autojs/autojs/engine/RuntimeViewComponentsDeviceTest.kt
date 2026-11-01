package org.autojs.autojs.engine

import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Exercises the real Android controls through Node CJS/ESM, three Worker modes and Bun. */
class RuntimeViewComponentsDeviceTest {
    private val fixture = RuntimeUiComposeDeviceTest()
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modes = runtimeModes("node", "node-esm", "worker", "worker-esm", "worker-nested", "bun")
    private val evidence = JSONArray()

    @Test fun webPagesJavaScriptAndHistoryAcrossRuntimes() = fixture.grants {
        for (mode in modes) {
            val result = fixture.run(mode, """
                const web=await ui.createView('WebView',{height:240}),window=await ui.showLayout(web);
                let finished=0,titles=0,progress=0;
                web.on('pageFinished',()=>finished++);web.on('titleChanged',()=>titles++);web.on('progressChanged',()=>progress++);
                async function wait(test,label){const end=Date.now()+15000;while(!await test()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,30));}}
                try {
                  if(await web.javaScriptEnabled!==false)throw Error('Unexpected JS default');
                  let rejected=false;try{await web.evaluateJavascript('1+1');}catch(e){rejected=true;}if(!rejected)throw Error('JS disabled was ignored');
                  web.javaScriptEnabled=true;web.domStorageEnabled=true;
                  await web.loadHtml('<title>Portable-HTML</title><p>Local HTML</p>');
                  await wait(async()=>await web.title==='Portable-HTML','HTML title');
                  const value=await web.evaluateJavascript('({answer:42,text:"native",array:[1,true,null],bridge:typeof autojs6})');
                  if(value.answer!==42||value.text!=='native'||value.array[1]!==true||value.bridge!=='undefined')throw Error('Decoded JavaScript result '+JSON.stringify(value));
                  await web.loadUrl('data:text/html,'+encodeURIComponent('<title>Portable-A</title><p>A</p>'));
                  await wait(async()=>await web.title==='Portable-A','First URL');
                  await web.loadUrl('data:text/html,'+encodeURIComponent('<title>Portable-B</title><p>B</p>'));
                  await wait(async()=>await web.title==='Portable-B','Second URL');
                  if(!await web.canGoBack)throw Error('Missing browser history');await web.goBack();
                  await wait(async()=>await web.title==='Portable-A','Back');if(!await web.canGoForward)throw Error('Forward history');
                  await web.goForward();await wait(async()=>await web.title==='Portable-B','Forward');
                  const before=finished;await web.reload();await wait(()=>finished>before,'Reload completion');await web.stopLoading();
                  if(!titles||!progress)throw Error('Page events missing');return {value,finished,titles,progress,history:true};
                } finally {await window.close();await web.dispose();}
            """.trimIndent())
            evidence.put(JSONObject().put("mode", mode).put("result", result))
        }; save("web")
    }

    @Test fun drawerToolbarPagerClicksAndRotationAcrossRuntimes() = fixture.grants {
        harness.withFixture {
            for (mode in modes) {
                harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val future = executor.submit<JSONObject> { fixture.run(mode, """
                        const root=await ui.inflate('<drawer id="drawer"><vertical><toolbar id="bar" title="Components-$mode" h="56" navigationIcon="@android:drawable/ic_menu_more" navigationContentDescription="Open-$mode"/><viewpager id="pager" h="130"><vertical id="page0"><text text="Page0-$mode"/></vertical><vertical id="page1"><text text="Page1-$mode"/></vertical><vertical id="page2"><text id="offscreen" text="Hidden"/></vertical></viewpager><webview id="web" h="65" javaScriptEnabled="true"/><button id="check" text="Preparing"/></vertical><vertical id="side" w="220" h="*" layout_gravity="start" bg="#ffffff"><button id="close" text="Close-$mode"/></vertical></drawer>');
                        const drawer=await root.findView('drawer'),bar=await root.findView('bar'),pager=await root.findView('pager'),web=await root.findView('web');
                        const offscreen=await root.findView('offscreen');if(!offscreen)throw Error('Offscreen page lookup');offscreen.text='Updated hidden page';
                        if(await pager.pageCount!==3||await offscreen.text!=='Updated hidden page')throw Error('Page registry');
                        bar.menu=[{id:'next',title:'Next-$mode',showAsAction:'always'}];bar.subtitle='Ready';await bar.flush();
                        const window=await ui.showLayout(root);let opened=0,closed=0,menus=0,selected=0,checks=0,failure;
                        const guard=fn=>()=>Promise.resolve().then(fn).catch(e=>failure=e);
                        drawer.on('drawerOpened',e=>{if(e.side==='start'&&e.drawerId==='side')opened++;});drawer.on('drawerClosed',()=>closed++);
                        bar.on('navigationClick',guard(()=>drawer.openDrawer('start',false)));
                        (await root.findView('close')).on('click',guard(()=>drawer.closeDrawer('start',false)));
                        bar.on('menuClick',e=>{if(e.itemId==='next'&&e.title==='Next-$mode')guard(async()=>{menus++;await pager.setCurrentItem(1,false);})();});pager.on('pageSelected',e=>{if(e.position===1&&e.pageId==='page1')selected++;});
                        await web.loadHtml('<title>Retained</title><p>DOM</p>');
                        const readyEnd=Date.now()+15000;while(await web.title!=='Retained'&&Date.now()<readyEnd)await new Promise(r=>setTimeout(r,30));
                        await web.evaluateJavascript('window.retainedValue=73');
                        (await root.findView('check')).on('click',guard(async()=>{
                          if(await pager.currentItem!==1||await web.evaluateJavascript('window.retainedValue')!==73||await bar.subtitle!=='Ready')throw Error('State lost on rotation');checks++;
                        }));
                        (await root.findView('check')).text='Check-$mode';
                        const end=Date.now()+40000;while((checks<2||!opened||!closed||!menus||!selected)&&!failure&&Date.now()<end)await new Promise(r=>setTimeout(r,30));
                        if(failure)throw failure;if(checks!==2||!opened||!closed||!menus||!selected)throw Error('Native events '+JSON.stringify({checks,opened,closed,menus,selected}));
                        await window.close();await root.dispose();return {checks,opened,closed,menus,selected,offscreen:true};
                    """.trimIndent()) }
                    fun click(label: String) { harness.await(label, 20000) {
                        if (future.isDone) fail(future.get().toString())
                        var node = find(label) ?: return@await false
                        while (!node.isClickable && node.parent != null) node = node.parent
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } }
                    click("Open-$mode"); click("Close-$mode"); click("Next-$mode".uppercase(java.util.Locale.ROOT))
                    harness.await("Selected native page") { find("Page1-$mode") != null }
                    click("Check-$mode")
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val monitor = instrumentation.addMonitor(RuntimeInteropActivity::class.java.name, null, false)
                    try { harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_90); assertNotNull(monitor.waitForActivityWithTimeout(10000)) }
                    finally { instrumentation.removeMonitor(monitor) }
                    click("Check-$mode")
                    evidence.put(JSONObject().put("mode", mode).put("result", future.get(45, TimeUnit.SECONDS)))
                } finally { executor.shutdownNow(); harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_0) }
            }
        }; save("interaction-rotation")
    }

    @Test fun invalidComponentOperationsRecoverAndRootDisposalClosesAliases() = fixture.grants {
        for (mode in modes) {
            val result = fixture.run(mode, """
                const root=await ui.inflate('<vertical><toolbar id="bar" title="Original"/><viewpager id="pager"><text text="A"/><text text="B"/></viewpager><webview id="web"/></vertical>');
                const bar=await root.findView('bar'),pager=await root.findView('pager'),web=await root.findView('web');let rejected=0;
                async function reject(fn){try{await fn();}catch(e){rejected++;return;}throw Error('Invalid operation accepted');}
                await reject(()=>bar.set({title:'Must not apply',menu:[{id:'x',title:'A'},{id:'x',title:'B'}]}));
                if(await bar.title!=='Original')throw Error('Failed patch partially applied');
                await reject(()=>pager.setCurrentItem(99));await reject(()=>web.openDrawer());await reject(()=>bar.loadUrl('about:blank'));
                await reject(()=>ui.inflate('<drawer><text/><text/></drawer>'));
                await reject(()=>ui.inflate('<vertical><webview/><viewpager currentItem="2"><text/></viewpager></vertical>'));
                pager.currentItem=1;await pager.flush();if(await pager.currentItem!==1)throw Error('Queue did not recover');
                await root.dispose();await reject(()=>web.get());await reject(()=>pager.setCurrentItem(0));
                return {rejected,recovered:true};
            """.trimIndent())
            assertEquals(8, result.getInt("rejected")); evidence.put(JSONObject().put("mode", mode).put("result", result))
        }; save("invalid-disposal")
    }

    @Test fun webViewDisposalReleasesAttachedAndOffscreenPages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val session = NodeBridgeUiSession("component-release", JSONObject())
            val theme = android.view.ContextThemeWrapper(context, org.autojs.autojs6.R.style.ScriptTheme)
            val root = NodeBridgeUiRenderer(theme, session).createView(RuntimeUiInterop.xml("<viewpager><webview id='a'/><text/><webview id='b'/></viewpager>"))
            val views = listOf("a", "b").map { RuntimeViewComponents.find(root, it) as RuntimeWebView }
            assertTrue(views.none { it.disposed }); RuntimeViewComponents.dispose(root)
            assertTrue(views.all { it.disposed }); assertTrue((root as RuntimeViewPager).pages.isEmpty())
            RuntimeViewComponents.dispose(root)
            val tabs = NodeBridgeUiRenderer(theme, session).createView(JSONObject("""{"type":"Tabs","tabs":[{"title":"A","content":{"type":"WebView","id":"old"}},{"title":"B","content":{"type":"WebView","id":"new"}}]}""")) as android.widget.LinearLayout
            val old = RuntimeViewComponents.find(tabs, "old") as RuntimeWebView
            ((tabs.getChildAt(0) as android.widget.LinearLayout).getChildAt(1) as android.widget.Button).performClick()
            assertTrue("Replacing tab content must destroy its WebView", old.disposed)
            val next = RuntimeViewComponents.find(tabs, "new") as RuntimeWebView
            assertFalse(next.disposed); RuntimeViewComponents.dispose(tabs); assertTrue(next.disposed)
            session.close("test")
        }
    }

    private fun find(label: String): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if ((node.text?.toString() == label || node.contentDescription?.toString() == label) && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) walk(node.getChild(i))?.let { return it }
            return null
        }
        return harness.automation.windows.firstNotNullOfOrNull { walk(it.root) } ?: walk(harness.automation.rootInActiveWindow)
    }
    private fun save(name: String) { File(context.getExternalFilesDir(null), "runtime-view-components-$name.json").writeText(evidence.toString(2)) }
}
