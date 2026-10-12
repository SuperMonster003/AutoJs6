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

/** Public XML/Compose bridges with the installed renderer; no Compose classes on the host classpath. */
@RunWith(AndroidJUnit4::class)
class ComposeInteropDeviceTest {
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

    @Test fun legacyRendererSupportsAttachButRejectsAndroidViewBeforeReplacingContent() {
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        assumeTrue("This compatibility phase requires the actual installed baseline renderer build 27", versionCode == 27L)
        Running("""
            check(compose.isAvailable(),'legacy provider must remain available');
            ui.layout('<vertical><button id="legacy" text="Legacy provider native click" textAllCaps="false"/><compose id="holder" h="260"/></vertical>');
            var clicks=0, closes=0;
            var label=compose.Text({text:'Legacy provider count: 0'});
            var button=compose.Button({onClick:function(){label.text='Legacy provider count: '+(++clicks);report('composeClicks',clicks);}},compose.Text({text:'Legacy provider Compose click'}));
            var root=compose.Column(label,button);
            var session=compose.attach(ui.holder,root);
            session.on('close',function(){closes++;});
            ui.legacy.on('click',function(){label.text='Legacy provider count: '+(++clicks);report('nativeClicks',clicks);});
            var firstView=ui.inflate('<text text="Legacy unsupported first View"/>');
            var secondView=ui.inflate('<text text="Legacy unsupported second View"/>');
            var codes=[];
            function rejected(name,action,expected){
                try { action(); throw Error(name+' unexpectedly accepted'); }
                catch(error){check(error instanceof ComposeError&&error.code===expected,name+' wrong error: '+error);codes.push(name+':'+error.code);}
            }
            actions.reject=function(){
                // Detached preparation has no provider. Capability checks belong to publication.
                var draft=compose.AndroidView({view:firstView});
                draft.set({view:secondView});
                check(draft.view.equals(secondView),'detached set must prepare its actual View');
                rejected('mount',function(){compose.mount(draft);},'PLUGIN_INCOMPATIBLE');
                rejected('attach',function(){compose.attach(ui.holder,draft);},'PLUGIN_INCOMPATIBLE');
                var candidate=compose.AndroidView({view:firstView});
                rejected('set',function(){session.update(function(){root.append(candidate);candidate.set({view:secondView});});},'PLUGIN_INCOMPATIBLE');
                check(root.children.length===2&&candidate.parent===null&&candidate.view.equals(firstView),'rejected native set did not roll back its tree and View');
                rejected('textProp',function(){label.set({view:secondView});},'UNKNOWN_PROP');
                check(!session.isClosed()&&closes===0&&session.root===root&&compose.sessions.length===1,'legacy session was replaced or closed');
                report('codes',codes.join(','));
            };
        """).use { running ->
            running.await("The real legacy provider supports a new host XML Compose container") {
                text("Legacy provider count: 0") && text("Legacy provider Compose click")
            }
            running.command("reject")
            running.await("Unsupported AndroidView operations synchronously reject before replacement") {
                running.property("codes") == "mount:PLUGIN_INCOMPATIBLE,attach:PLUGIN_INCOMPATIBLE,set:PLUGIN_INCOMPATIBLE,textProp:UNKNOWN_PROP"
            }
            assertTrue(text("Legacy provider count: 0"))
            click("Legacy provider Compose click")
            running.await("The accepted Compose callback and old tree survive all rejections") {
                running.property("composeClicks") == "1" && text("Legacy provider count: 1")
            }
            click("Legacy provider native click")
            running.await("The original XML callback and container session remain usable") {
                running.property("nativeClicks") == "2" && text("Legacy provider count: 2")
            }
            assertFalse(text("Legacy unsupported first View"))
            assertFalse(text("Legacy unsupported second View"))
        }
    }

    @Test fun twoXmlContainersShareStateAndReplacingOneKeepsTheOtherAndLegacyViewsAlive() {
        Running("""
            ui.layout('<vertical><button id="legacy" text="Legacy increment" textAllCaps="false"/><compose id="first" h="180"/><compose id="second" h="180"/></vertical>');
            var count=compose.state(0), closes=0;
            ui.legacy.on('click',function(){count.value++;});
            var first=compose.attach(ui.first,function(){return compose.Column(compose.Text({text:'First '+count.value}),compose.Button({onClick:function(){count.value++;}},compose.Text({text:'Compose increment'})));});
            var second=compose.attach(ui.second,function(){return compose.Text({text:'Second '+count.value});});
            first.on('close',function(){closes++;});
            actions.replace=function(){first=compose.attach(ui.first,compose.Text({text:'First replacement'}));check(!second.isClosed(),'other container closed');report('replaced',closes);};
        """).use { running ->
            running.await("Both independent containers compose") { text("First 0") && text("Second 0") }
            click("Legacy increment")
            running.await("Legacy click updates both Compose reactions") { text("First 1") && text("Second 1") }
            click("Compose increment")
            running.await("Compose callback updates both reactions") { text("First 2") && text("Second 2") }
            running.command("replace")
            running.await("Only the selected container is replaced") { running.property("replaced")=="1" && text("First replacement") && text("Second 2") }
            click("Legacy increment")
            running.await("Legacy listener and other session survive replacement") { text("Second 3") }
        }
    }

    @Test fun androidViewsAcceptExistingViewsAndMainThreadFactoriesAndReplaceThroughSet() {
        Running("""
            var calls=0, clicks=0;
            var original=ui.inflate('<button text="Existing native button" textAllCaps="false"/>');
            original.on('click',function(){report('clicks',++clicks);});
            var native=compose.AndroidView({view:original});
            var fromFactory=compose.AndroidView({view:function(ctx){check(ui.isUiThread(),'factory thread');check(ctx instanceof android.content.Context,'factory context');calls++;var v=new android.widget.TextView(ctx);v.setText('Factory native label');return v;}});
            var session=compose.mount(compose.Column(native,fromFactory));
            check(native.view instanceof android.view.View,'getter is native View');
            runtime.putProperty('f2.original',original);report('calls',calls);
            actions.replace=function(){native.set({view:function(ctx){check(ui.isUiThread(),'replacement thread');calls++;return ui.inflate('<button text="Replacement native button" textAllCaps="false"/>');}});report('calls',calls);};
        """).use { running ->
            running.await("Existing View and factory View are visible") { text("Existing native button") && text("Factory native label") }
            assertEquals("1", running.property("calls"))
            click("Existing native button")
            running.await("Existing native listener remains callable") { running.property("clicks")=="1" }
            running.command("replace")
            running.await("set replaces the AndroidView instance") { text("Replacement native button") && !text("Existing native button") }
            assertEquals("2", running.property("calls"))
            val original = running.engine.runtime.getProperty("f2.original")
            instrumentation.runOnMainSync { assertNull((ComposeScriptValues.unwrap(original) as View).parent) }
        }
    }

    @Test fun aContainerCanBeAttachedBeforeItsXmlTreeEntersTheWindow() {
        Running("""
            var layout=ui.inflate('<vertical><text text="Legacy before attachment"/><compose id="pending" h="180"/></vertical>');
            var field=compose.TextField({text:'Pending editor',testTag:'f2-pending-editor'});
            var session=compose.attach(layout.pending,compose.Column(compose.Text({text:'Pending Compose content'}),field));
            field.focus();
            ui.setContentView(layout);
            runtime.putProperty('f2.container',layout.pending);
            actions.remove=function(){layout.removeView(layout.pending);};
            session.on('close',function(){report('closed',true);});
        """).use { running ->
            running.await("An initially unattached container later composes") { text("Pending Compose content") }
            running.await("An immediate focus command waits for the embedded editor to compose") {
                node { it.isEditable && it.isFocused && it.text?.toString() == "Pending editor" } != null
            }
            val target = ComposeScriptValues.unwrap(running.engine.runtime.getProperty("f2.container")) as ViewGroup
            running.command("remove")
            running.await("Removing that container closes its session") { running.property("closed")=="true" }
            instrumentation.runOnMainSync { assertNull(target.parent); assertEquals(0,target.childCount) }
        }
    }

    @Test fun factoryFailureNestedMountAndForeignParentKeepTheExistingMixedPage() {
        Running("""
            ui.layout('<vertical><button id="legacy" text="Still the same legacy button" textAllCaps="false"/><compose id="holder" h="180"/></vertical>');
            var session=compose.attach(ui.holder,compose.Text({text:'Preserved Compose content'}));
            var errors=[];
            function rejected(fn,code){try{fn();throw Error('unexpected acceptance');}catch(error){check(error instanceof ComposeError&&error.code===code,'wrong error '+error);errors.push(code);}}
            actions.validate=function(){
                rejected(function(){compose.attach(ui.holder,function(){return compose.AndroidView({view:function(){throw Error('factory failure');}});});},'RENDER_FAILED');
                ['mount','attach','floaty'].forEach(function(method){rejected(function(){compose.AndroidView({view:function(){if(method==='attach')compose.attach(ui.holder,compose.Text({text:'bad'}));else compose[method](compose.Text({text:'bad'}));return ui.legacy;}});},'INVALID_ARGUMENT');});
                rejected(function(){compose.attach(ui.holder,compose.AndroidView({view:ui.legacy}));},'INVALID_ARGUMENT');
                check(!session.isClosed(),'old session closed');report('validated',errors.length);
            };
            ui.legacy.on('click',function(){report('legacy',true);});
        """).use { running ->
            running.await("Original mixed page is visible") { text("Preserved Compose content") }
            running.command("validate")
            running.await("All invalid operations return typed errors") { running.property("validated")=="5" }
            assertTrue(text("Preserved Compose content"))
            click("Still the same legacy button")
            running.await("The original native listener still works") { running.property("legacy")=="true" }
        }
    }

    @Test fun layoutReplacementAndEngineStopDisposeAllContainerContent() {
        Running("""
            ui.layout('<vertical><compose id="one" h="160"/><compose id="two" h="160"/></vertical>');
            var closes=0;
            [ui.one,ui.two].forEach(function(target){compose.attach(target,compose.Text({text:'Owned container'})).on('close',function(){report('closes',++closes);});});
            runtime.putProperty('f2.first',ui.one);runtime.putProperty('f2.second',ui.two);
            actions.replace=function(){ui.layout('<vertical><compose id="last" h="160"/></vertical>');compose.attach(ui.last,compose.Text({text:'Replacement container'}));runtime.putProperty('f2.last',ui.last);};
        """).use { running ->
            running.await("Initial sessions attach") { text("Owned container") }
            val first=ComposeScriptValues.unwrap(running.engine.runtime.getProperty("f2.first")) as ViewGroup
            val second=ComposeScriptValues.unwrap(running.engine.runtime.getProperty("f2.second")) as ViewGroup
            running.command("replace")
            running.await("Layout replacement closes both old sessions") { running.property("closes")=="2" && text("Replacement container") }
            instrumentation.runOnMainSync { assertEquals(0,first.childCount);assertEquals(0,second.childCount) }
            val last=ComposeScriptValues.unwrap(running.engine.runtime.getProperty("f2.last")) as ViewGroup
            running.stop()
            instrumentation.runOnMainSync { assertEquals(0,last.childCount) }
        }
    }

    @Test fun legacyRawAndResizableWindowsHostContainersAndCloseAllEndsTheirSessions() {
        assumeTrue("Overlay permission is required for this owned test", Settings.canDrawOverlays(context))
        Running("""
            var raw=floaty.rawWindow('<vertical><text text="Legacy raw header"/><compose id="slot" w="220" h="150"/></vertical>');raw.setPosition(20,100);
            var resizable=floaty.window('<vertical><text text="Legacy resizable header"/><compose id="slot" w="220" h="150"/></vertical>');resizable.setPosition(280,100);
            var closes=0;
            var ownerThread=String(java.lang.Thread.currentThread().getId());
            var a=compose.attach(raw.slot,compose.Column(compose.Text({text:'Raw embedded Compose'}),compose.Button({onClick:function(){check(String(java.lang.Thread.currentThread().getId())===ownerThread,'callback escaped its script dispatcher');report('callback',true);}},compose.Text({text:'Non-UI Compose callback'}))));
            var b=compose.attach(resizable.slot,compose.AndroidView({view:function(ctx){check(ui.isUiThread(),'non-UI factory thread');var view=new android.widget.TextView(ctx);view.setText('Non-UI factory content');return view;}}));
            [a,b].forEach(function(session){session.on('close',function(){report('closes',++closes);});});
            actions.close=function(){floaty.closeAll();};
        """, ui=false).use { running ->
            running.await("Both existing window styles host Compose") { text("Raw embedded Compose") && text("Non-UI factory content") }
            click("Non-UI Compose callback")
            running.await("Compose callbacks return to the non-UI script dispatcher") { running.property("callback")=="true" }
            running.command("close")
            running.await("Legacy closeAll closes the embedded sessions") { running.property("closes")=="2" }
            assertFalse(text("Raw embedded Compose"))
            assertFalse(text("Non-UI factory content"))
        }
    }

    private inner class Running(source: String, ui: Boolean=true) : Closeable {
        private val marker="compose-f2-${UUID.randomUUID()}"
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
                (execution.engine as? RhinoJavaScriptEngine)?.let { engineRef.set(it);(it.runtime.console as? ConsoleImpl)?.addLogListener(logListener) }
                    ?:failure.set(AssertionError("Interop script did not use Rhino"))
                started.countDown()
            }
            override fun onException(execution:ScriptExecution,error:Throwable) { if(owned(execution)){if(!stopping.get())failure.compareAndSet(null,error);started.countDown()} }
        }
        init {
            val script=(if(ui)"\"ui\";\n" else "")+"// $marker\n"+"""
                function check(ok,message){if(!ok)throw Error(message);}
                function report(key,value){runtime.putProperty('f2.'+key,String(value));}
                var actions={};
                setInterval(function(){var command=runtime.getProperty('f2.command');if(command){runtime.putProperty('f2.command','');actions[String(command)]();}},30);
            """.trimIndent()+"\n"+source.trimIndent()
            service.registerGlobalScriptExecutionListener(listener)
            try {
                instrumentation.runOnMainSync { context.startActivity(Intent(context,RunIntentActivity::class.java)
                    .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT,script).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                assertTrue("The owned interop engine starts",started.await(30,TimeUnit.SECONDS))
                healthy();assertNotNull(engineRef.get())
            }catch(error:Throwable){close();throw error}
        }
        fun property(name:String)=engine.runtime.getProperty("f2.$name")?.toString()
        fun command(name:String){engine.runtime.putProperty("f2.command",name)}
        private fun healthy(){failure.get()?.let{throw AssertionError("Interop script failed; logs=${logs.joinToString(" | ")}",it)};logs.firstOrNull{it.startsWith("3-Block Compose: ")}?.let{throw AssertionError(it)}}
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
            return "native=$native; ${accessibilityDescription()}"
        }
        fun stop(){stopping.set(true);val owned=engineRef.get()?:return;if(!owned.isDestroyed)owned.forceStop();val end=SystemClock.uptimeMillis()+15000;while(!owned.isDestroyed&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);assertTrue(owned.isDestroyed);instrumentation.waitForIdleSync()}
        override fun close(){
            stopping.set(true)
            try{engineRef.get()?.let{owned->if(!owned.isDestroyed){val activity=owned.getTag("activity")as?Activity;if(activity!=null)instrumentation.runOnMainSync{activity.finish()}else owned.forceStop();val end=SystemClock.uptimeMillis()+15000;while(!owned.isDestroyed&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);if(!owned.isDestroyed)owned.forceStop()};(owned.runtime.console as?ConsoleImpl)?.removeLogListener(logListener);owned.runtime.floaty.closeAll()}}
            finally{service.unregisterGlobalScriptExecutionListener(listener);instrumentation.waitForIdleSync()}
        }
    }
    private fun text(value:String)=node{it.text?.toString()==value}!=null
    private fun click(value:String){var current=node{it.text?.toString()==value};repeat(6){val target=current?:return@repeat;if(target.isClickable&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;current=target.parent};fail("No clickable node: $value; ${accessibilityDescription()}")}
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
