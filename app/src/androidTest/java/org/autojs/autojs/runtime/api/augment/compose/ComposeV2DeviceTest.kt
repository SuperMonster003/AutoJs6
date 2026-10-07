package org.autojs.autojs.runtime.api.augment.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.open.RunIntentActivity
import org.autojs.autojs.script.StringScriptSource
import org.autojs.plugin.compose.api.ComposeUiIds
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** V2 components cross real script, loader, popup and retained V1 boundaries. */
@RunWith(AndroidJUnit4::class)
class ComposeV2DeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service get() = AutoJs.instance.scriptEngineService
    private var prepared = false
    private var enabled = false
    private var accessibilityFlags = 0

    @Before fun setup() {
        assumeTrue("Use the isolated Compose spike host", context.packageName.endsWith(".compose.spike"))
        context.packageManager.getApplicationInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0)
        enabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        instrumentation.uiAutomation.serviceInfo.also {
            accessibilityFlags = it.flags
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            instrumentation.uiAutomation.serviceInfo = it
        }
        prepared = true
    }
    @After fun restore() {
        if (!prepared) return
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, enabled)
        instrumentation.uiAutomation.serviceInfo.also { it.flags=accessibilityFlags; instrumentation.uiAutomation.serviceInfo=it }
    }


    @Test fun controlledNavigationTabsChipsAndSegmentsUseTheScriptDispatcher() {
        Running("""
            var selected=compose.state(0), checked=compose.state(false), segment=compose.state(0);
            compose.mount(function(){return compose.Column({padding:12,spacing:6},
                compose.Text({text:'V2 selected '+selected.value+' chip '+checked.value+' segment '+segment.value}),
                compose.NavigationBar({},
                    compose.NavigationBarItem({selected:selected.value===0,icon:compose.Icon({name:'Home'}),label:compose.Text({text:'V2 home'}),onClick:function(){selected.value=0;}}),
                    compose.NavigationBarItem({selected:selected.value===1,icon:compose.Icon({name:'Settings'}),label:compose.Text({text:'V2 settings'}),onClick:function(){selected.value=1;}})),
                compose.TabRow({selectedIndex:selected.value},
                    compose.Tab({text:compose.Text({text:'V2 first tab'}),onClick:function(){selected.value=0;}}),
                    compose.Tab({text:compose.Text({text:'V2 second tab'}),onClick:function(){selected.value=1;}})),
                compose.FilterChip({selected:checked.value,label:compose.Text({text:'V2 controlled filter'}),onSelectedChange:function(value){check(this.type==='FilterChip','callback receiver');check(typeof value==='boolean','callback type');checked.value=value;}}),
                compose.SegmentedButton({},
                    compose.SegmentedButtonItem({selected:segment.value===0,label:compose.Text({text:'V2 segment A'}),onSelectedChange:function(value){check(value===true,'single selection result');segment.value=0;}}),
                    compose.SegmentedButtonItem({selected:segment.value===1,label:compose.Text({text:'V2 segment B'}),onSelectedChange:function(value){check(value===true,'single selection result');segment.value=1;}})));
            });
        """).use { running ->
            running.await("The V2 catalog produces native interactive components") { text("V2 selected 0 chip false segment 0") }
            click("V2 settings")
            running.await("Navigation selection is acknowledged by the script") { text("V2 selected 1 chip false segment 0") }
            click("V2 first tab")
            running.await("Tab selection shares controlled state") { text("V2 selected 0 chip false segment 0") }
            click("V2 controlled filter")
            running.await("Chip selection delivers a boolean with its node receiver") { text("V2 selected 0 chip true segment 0") }
            click("V2 segment B")
            running.await("A single segment selection publishes one valid tree") { text("V2 selected 0 chip true segment 1") }
        }
    }

    @Test fun invalidV2UpdatesPreserveTheAcceptedTreeAndItsNativeCallbacks() {
        Running("""
            var count=0, first=compose.Tab({text:compose.Text({text:'Retained first tab'})}),second=compose.Tab({text:compose.Text({text:'Retained second tab'})});
            var tabs=compose.TabRow({selectedIndex:1},first,second);
            var label=compose.Text({text:'V2 rollback count 0'});
            var chip=compose.AssistChip({label:compose.Text({text:'V2 retained callback'}),onClick:function(){label.text='V2 rollback count '+(++count);}});
            var date=compose.DatePicker({selectedDateMillis:0}),time=compose.TimePicker({hour:23,minute:59});
            compose.mount(compose.Column({padding:12},label,tabs,chip));
            actions.reject=function(){
                var cases=[function(){tabs.selectedIndex=2;},function(){tabs.remove(second);},function(){date.selectedDateMillis=1;},
                    function(){date.yearStart=2000;},function(){time.set({hour:0,minute:60});},
                    function(){tabs.append(compose.Text({text:'invalid child'}));},
                    function(){compose.mount(compose.Tab({text:compose.Text({text:'invalid standalone tab'})}));}];
                cases.forEach(function(run,index){try{run();throw Error('accepted invalid '+index);}catch(error){check(error instanceof ComposeError,'not a typed error '+error);check(error.code==='INVALID_ARGUMENT'||error.code==='SCOPE_MISMATCH','unexpected '+error.code);}});
                check(tabs.selectedIndex===1&&tabs.children.length===2,'tree changed');check(time.hour===23&&time.minute===59,'time partially changed');
                report('rejected',cases.length);
            };
        """).use { running ->
            running.await("The valid tree is accepted before negative updates") { text("V2 rollback count 0") }
            val retained = requireNotNull(node { it.text?.toString() == "V2 retained callback" })
            running.command("reject")
            running.await("All prospective invalid updates are rejected synchronously") { running.property("rejected") == "7" }
            assertTrue(retained.refresh())
            assertTrue(text("Retained second tab"))
            click("V2 retained callback")
            running.await("The prior native event binding still works") { text("V2 rollback count 1") }
        }
    }

    @Test fun legacyV1ProviderRejectsOnlyNewNodesAndPreservesTheExistingPage() {
        org.autojs.autojs.core.plugin.compose.ComposeUiPluginHost(context).use { host ->
            assumeTrue("Run this phase with the archived actual V1 provider", host.load().factory.contractVersion() == 1)
        }
        Running("""
            var count=0,label=compose.Text({text:'V1 retained page 0'});
            var page=compose.mount(compose.Column({padding:12},label,compose.Button({onClick:function(){label.text='V1 retained page '+(++count);}},'V1 retained callback')));
            try{compose.mount(compose.TimePicker({hour:12,minute:30}));throw Error('V1 accepted V2');}
            catch(error){check(error instanceof ComposeError&&error.code==='UNKNOWN_COMPONENT','wrong V1 error: '+error);check(!page.isClosed(),'old page closed');report('rejected',error.code);}
        """).use { running ->
            running.await("The new host retains support for an actual V1 renderer") { running.property("rejected") == "UNKNOWN_COMPONENT" && text("V1 retained page 0") }
            click("V1 retained callback")
            running.await("A failed V2 mount leaves the V1 callback and view intact") { text("V1 retained page 1") }
        }
    }

    @Test fun dropdownTooltipAndModalSheetWorkFromAnActivity() = popupScenario(ui = true)
    @Test fun dropdownTooltipAndModalSheetWorkFromAnOverlay() {
        assertTrue(Settings.canDrawOverlays(context))
        popupScenario(ui = false)
    }

    private fun popupScenario(ui: Boolean) {
        Running("""
            var menu,tooltip,sheet,native;
            menu=compose.DropdownMenu({anchor:compose.Button({onClick:function(){menu.open=true;}},'V2 open menu'),
                onDismissRequest:function(){menu.open=false;report('menuDismiss',true);}},
                compose.DropdownMenuItem({text:compose.Text({text:'V2 menu selection'}),onClick:function(){menu.open=false;report('menuClick',true);}}));
            tooltip=compose.Tooltip({tooltip:compose.Text({text:'V2 popup tooltip'}),onOpenChange:function(open){tooltip.open=open;}},compose.Text({text:'V2 tooltip anchor'}));
            var borrowed=compose.AndroidView({view:function(ctx){native=new android.widget.TextView(ctx);native.setText('V2 sheet native window');return native;}});
            sheet=compose.ModalBottomSheet({cancelable:false,onDismissRequest:function(){sheet.open=false;report('sheetDismiss',true);}},
                compose.Column({padding:16},compose.Text({text:'V2 component sheet'}),borrowed,
                    compose.Button({onClick:function(){sheet.open=false;report('sheetClose',true);}},'V2 close component sheet')));
            var root=compose.Column({padding:12,spacing:8,bg:'#FFFFFFFF'},menu,tooltip,sheet);
            var owner=${if (ui) "compose.mount(root)" else "compose.floaty(root,{raw:true,x:24,y:120,width:900,height:600,focusable:true})"};
            actions.tooltip=function(){tooltip.open=true;};
            actions.hideTooltip=function(){tooltip.open=false;};
            actions.sheet=function(){sheet.open=true;};
            actions.inspect=function(){ui.run(function(){report('windowType',native.getRootView().getLayoutParams().type);});};
        """, ui = ui).use { running ->
            running.await("The owned popup anchor is visible") { text("V2 open menu") }
            click("V2 open menu")
            running.await("DropdownMenu attaches to its actual hosting window") { text("V2 menu selection") }
            click("V2 menu selection")
            running.await("Menu click closes the controlled popup") { running.property("menuClick") == "true" && !text("V2 menu selection") }
            running.command("tooltip")
            running.await("Tooltip uses a real public Popup above the current host") { text("V2 popup tooltip") }
            running.command("hideTooltip")
            running.await("A controlled tooltip hides without closing its owner") { !text("V2 popup tooltip") }
            running.command("sheet")
            running.await("The component opens a real modal sheet") { text("V2 component sheet") && text("V2 sheet native window") }
            running.command("inspect")
            val expectedType = if (ui) android.view.WindowManager.LayoutParams.TYPE_APPLICATION
                else org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat.getWindowType()
            running.await("The actual nested native view observes the host's public window type") { running.property("windowType") == "$expectedType" }
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            instrumentation.waitForIdleSync()
            assertTrue("Cancelable=false preserves the component sheet on Back", text("V2 component sheet"))
            click("V2 close component sheet")
            running.await("The controlled sheet closes and preserves its owner") { running.property("sheetClose") == "true" && !text("V2 component sheet") && text("V2 open menu") }
        }
    }

    @Test fun modalSheetInsideAnOverlayDialogKeepsItsWindowTypeAndIndependentLifetime() {
        assertTrue(Settings.canDrawOverlays(context))
        Running("""
            var borrowed,native;
            borrowed=compose.AndroidView({view:function(ctx){native=new android.widget.TextView(ctx);native.setText('V2 nested dialog sheet native');return native;}});
            var sheet=compose.ModalBottomSheet({open:true,cancelable:false},compose.Column({padding:16},borrowed,
                compose.Button({onClick:function(){sheet.open=false;report('nestedClosed',true);}},'V2 close nested sheet')));
            var dialog=compose.dialog(compose.Column(compose.Text({text:'V2 outer overlay dialog'}),sheet),{cancelable:false});
            actions.inspect=function(){ui.run(function(){report('windowType',native.getRootView().getLayoutParams().type);});};
            actions.close=function(){dialog.close();report('outerClosed',dialog.isClosed());};
        """, ui = false).use { running ->
            running.await("Nested component sheet is visible above an overlay dialog") { text("V2 nested dialog sheet native") }
            running.command("inspect")
            val expected = org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat.getPhoneWindowType()
            running.await("Both windows are created with the existing overlay permission type") { running.property("windowType") == "$expected" }
            click("V2 close nested sheet")
            running.await("Closing the sheet retains the independent outer dialog") { running.property("nestedClosed") == "true" && text("V2 outer overlay dialog") }
            running.command("close")
            running.await("Explicit outer close releases its remaining window") { running.property("outerClosed") == "true" && !text("V2 outer overlay dialog") }
        }
    }

    @Test fun modalSheetInsideAnOverlayPopupInheritsTheOuterSystemWindowType() {
        assertTrue(Settings.canDrawOverlays(context))
        Running("""
            var native;
            var borrowed=compose.AndroidView({view:function(ctx){native=new android.widget.TextView(ctx);native.setText('V2 nested popup sheet native');return native;}});
            var sheet=compose.ModalBottomSheet({cancelable:false},compose.Column({padding:16},borrowed,
                compose.Button({onClick:function(){sheet.open=false;report('sheetClosed',true);}},'V2 close popup sheet')));
            var menu=compose.DropdownMenu({anchor:compose.Button({onClick:function(){menu.open=true;}},'V2 open outer popup'),
                onDismissRequest:function(){menu.open=false;}},
                compose.DropdownMenuItem({text:compose.Text({text:'V2 open popup sheet'}),onClick:function(){sheet.open=true;}}),sheet);
            var owner=compose.floaty(compose.Column({padding:12,bg:'#FFFFFFFF'},menu),{raw:true,x:24,y:120,width:900,height:400,focusable:true});
            actions.inspect=function(){ui.run(function(){report('windowType',native.getRootView().getLayoutParams().type);});};
            actions.close=function(){owner.close();report('ownerClosed',true);};
        """, ui = false).use { running ->
            running.await("The overlay exposes a popup anchor") { text("V2 open outer popup") }
            click("V2 open outer popup")
            running.await("The application subpanel is visible above its system overlay") { text("V2 open popup sheet") }
            click("V2 open popup sheet")
            running.await("The nested modal sheet survives the intermediate popup window") { text("V2 nested popup sheet native") }
            running.command("inspect")
            val expected = org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat.getWindowType()
            running.await("The intermediate application subpanel cannot erase overlay ownership") { running.property("windowType") == "$expected" }
            click("V2 close popup sheet")
            running.await("Closing the sheet keeps its popup and owner alive") { running.property("sheetClosed") == "true" && text("V2 open popup sheet") }
            running.command("close")
            running.await("Owner close removes both the popup and its anchor") { running.property("ownerClosed") == "true" && !text("V2 open outer popup") && !text("V2 open popup sheet") }
        }
    }

    @Test fun overlayDrawerAndSearchBarReceiveRealBackThroughTheirOwnedDispatcher() {
        assertTrue(Settings.canDrawOverlays(context))
        Running("""
            var search=compose.SearchBar({query:'',expanded:false,placeholder:compose.Text({text:'V2 overlay search'}),
                onExpandedChange:function(expanded){search.expanded=expanded;report('expanded',expanded);},
                onQueryChange:function(text){search.query=text;}},compose.Text({text:'V2 expanded search results'}));
            var drawer=compose.NavigationDrawer({open:true,
                drawerContent:compose.Column({padding:16},compose.Text({text:'V2 opened overlay drawer'}),
                    compose.NavigationDrawerItem({label:compose.Text({text:'V2 drawer destination'})})),
                onOpenChange:function(open){drawer.open=open;report('drawer',open);}},search);
            var owner=compose.floaty(drawer,{raw:true,x:24,y:80,width:1000,height:1600,focusable:true});
            actions.expand=function(){search.expanded=true;};
            actions.close=function(){owner.close();report('closed',true);};
        """, ui = false).use { running ->
            running.await("Drawer BackHandler resolves its owner and its actual overlay window has focus") {
                node { it.text?.toString() == "V2 opened overlay drawer" }?.window?.isFocused == true
            }
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            running.await("A real Back event requests controlled drawer closure") { running.property("drawer") == "false" }
            running.command("expand")
            running.await("Expanded SearchBar resolves the same public dispatcher owner") {
                node { it.text?.toString() == "V2 expanded search results" && it.isVisibleToUser } != null
            }
            if (instrumentation.uiAutomation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
                assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                running.await("The first Back hides the native IME") {
                    instrumentation.uiAutomation.windows.none { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                }
            }
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            running.await("The next Back reaches the actual expanded search callback") { running.property("expanded") == "false" }
            assertFalse(running.engine.isDestroyed)
            running.command("close")
            running.await("Closing the owner removes its Back registration with the window") {
                running.property("closed") == "true" && !text("V2 overlay search")
            }
        }
    }

    private inner class Running(source: String, ui: Boolean=true, keepAlive: Boolean=true, initialProperties: Map<String, Any> = emptyMap()) : Closeable {
        private val marker="compose-f4-${UUID.randomUUID()}"
        private val started=CountDownLatch(1)
        private val engineRef=AtomicReference<RhinoJavaScriptEngine?>()
        private val failure=AtomicReference<Throwable?>()
        private val stopping=AtomicBoolean()
        private val logs=ConcurrentLinkedQueue<String>()
        val engine get()=requireNotNull(engineRef.get())
        private val logListener=object:ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) { logs+=logEntry.content.toString() }
            override fun onLogClear()=Unit
        }
        private val listener=object:SimpleScriptExecutionListener() {
            private fun owned(execution:ScriptExecution)=(execution.source as? StringScriptSource)?.script?.contains(marker)==true
            override fun onStart(execution:ScriptExecution) {
                if(!owned(execution))return
                (execution.engine as? RhinoJavaScriptEngine)?.let { engineRef.set(it); initialProperties.forEach { (key,value) -> it.runtime.putProperty(key,value) };(it.runtime.console as? ConsoleImpl)?.addLogListener(logListener) }
                    ?:failure.set(AssertionError("V2 script did not use Rhino"))
                started.countDown()
            }
            override fun onException(execution:ScriptExecution,error:Throwable) { if(owned(execution)){if(!stopping.get())failure.compareAndSet(null,error);started.countDown()} }
        }
        init {
            val script=(if(ui)"\"ui\";\n" else "")+"// $marker\n"+"""
                function check(ok,message){if(!ok)throw Error(message);}
                function report(key,value){runtime.putProperty('f4.'+key,String(value));}
                var actions={}; var keepAlive=${keepAlive};
                var commandTimer=keepAlive?setInterval(function(){var command=runtime.getProperty('f4.command');if(command){runtime.putProperty('f4.command','');actions[String(command)]();}},30):null;
            """.trimIndent()+"\n"+source.trimIndent()
            service.registerGlobalScriptExecutionListener(listener)
            try {
                instrumentation.runOnMainSync { context.startActivity(Intent(context,RunIntentActivity::class.java)
                    .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT,script).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                assertTrue("The owned V2 engine starts",started.await(30,TimeUnit.SECONDS))
                healthy();assertNotNull(engineRef.get())
            }catch(error:Throwable){close();throw error}
        }
        fun property(name:String)=engine.runtime.getProperty("f4.$name")?.toString()
        fun command(name:String){engine.runtime.putProperty("f4.command",name)}
        private fun healthy(){failure.get()?.let{throw AssertionError("V2 script failed; logs=${logs.joinToString(" | ")}",it)};logs.firstOrNull{it.startsWith("Compose UI: ")}?.let{throw AssertionError(it)}}
        fun await(message:String,condition:()->Boolean){val end=SystemClock.uptimeMillis()+25000;do{healthy();if(condition())return;SystemClock.sleep(50)}while(SystemClock.uptimeMillis()<end);healthy();fail("$message; logs=${logs.joinToString(" | ")}; ${diagnostics()}")}
        private fun diagnostics(): String {
            val native = ArrayList<String>()
            instrumentation.runOnMainSync {
                val views = java.util.ArrayDeque<View>()
                engine.runtime.ui.view?.let(views::add)
                while (views.isNotEmpty() && native.size < 80) {
                    val view = views.removeFirst()
                    val text = (view as? android.widget.TextView)?.text?.toString().orEmpty()
                    val transformation = (view as? android.widget.TextView)?.transformationMethod?.javaClass?.simpleName.orEmpty()
                    native += "${view.javaClass.simpleName}(text=$text,transform=$transformation,size=${view.width}x${view.height},shown=${view.isShown},clickable=${view.isClickable},a11y=${view.importantForAccessibility})"
                    if (view is ViewGroup) for (index in 0 until view.childCount) views.add(view.getChildAt(index))
                }
            }
            return "drawer=${property("drawer")}, expanded=${property("expanded")}; native=$native; ${accessibilityDescription()}"
        }
        fun stop(){stopping.set(true);val owned=engineRef.get()?:return;if(!owned.isDestroyed)owned.forceStop();val end=SystemClock.uptimeMillis()+15000;while(!owned.isDestroyed&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);assertTrue(owned.isDestroyed);instrumentation.waitForIdleSync()}
        override fun close(){
            stopping.set(true)
            try{engineRef.get()?.let{owned->if(!owned.isDestroyed){val activity=owned.getTag("activity")as?Activity;if(activity!=null)instrumentation.runOnMainSync{activity.finish()}else owned.forceStop();val end=SystemClock.uptimeMillis()+15000;while(!owned.isDestroyed&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);if(!owned.isDestroyed)owned.forceStop()};(owned.runtime.console as?ConsoleImpl)?.removeLogListener(logListener);owned.runtime.floaty.closeAll()}}
            finally{service.unregisterGlobalScriptExecutionListener(listener);instrumentation.waitForIdleSync()}
        }
    }
    private fun text(value:String)=node{it.text?.toString()==value}!=null
    private fun click(value: String) {
        // A new summary label can precede refreshed sibling actions in the accessibility tree.
        // Re-resolve the label and its ancestry, using the same bound as Running.await.
        val deadline = SystemClock.uptimeMillis() + 25000
        do {
            var current = node { it.text?.toString() == value && it.isVisibleToUser && it.isEnabled }
            repeat(6) {
                val target = current ?: return@repeat
                if (!target.refresh()) { current = null; return@repeat }
                if (target.isVisibleToUser && target.isEnabled && target.isClickable &&
                    target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } &&
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                current = target.parent
            }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("No ready native click action: $value; ${accessibilityDescription()}")
    }
    private fun accessibilityDescription(): String {
        val values = ArrayList<String>()
        node { info ->
            if (values.size < 80) {
                val bounds = android.graphics.Rect().also(info::getBoundsInScreen)
                values += "${info.className}(text=${info.text},id=${info.viewIdResourceName},clickable=${info.isClickable},visible=${info.isVisibleToUser},bounds=$bounds)"
            }
            false
        }
        return "a11y=$values"
    }
    private fun node(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{
        val nodes=java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.mapNotNull{it.root}.filter{it.packageName?.toString()==context.packageName}.forEach(nodes::add)
        var visited=0
        while(nodes.isNotEmpty()&&visited++<2000){val node=nodes.removeFirst();if(!node.refresh())continue;if(predicate(node))return node;for(i in 0 until node.childCount)node.getChild(i)?.let(nodes::add)}
        return null
    }
}
