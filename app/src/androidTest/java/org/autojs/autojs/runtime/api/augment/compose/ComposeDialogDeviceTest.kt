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

/** Public dialog sessions with a real provider and owned UI/non-UI engines. */
@RunWith(AndroidJUnit4::class)
class ComposeDialogDeviceTest {
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


    @Test fun alertUsesItsBoundActivityAndSurvivesUnderlyingLayoutReplacement() {
        Running("""
            var ownActivity=activity, closes=0, value=compose.state(0);
            var page=compose.mount(compose.Text({text:'Original background'}));
            var dialog;
            activity=null;
            try {
                dialog=compose.dialog(function(){return compose.Column({padding:16,spacing:8},
                    compose.Text({text:'Dialog count: '+value.value}),
                    compose.AndroidView({view:function(ctx){check(ctx.equals(ownActivity),'wrong factory Activity');var view=new android.widget.TextView(ctx);view.setText('Bound Activity native view');return view;}}),
                    compose.Button({onClick:function(){value.value++;}},compose.Text({text:'Increment dialog'})),
                    compose.Button({onClick:function(){dialog.close();}},compose.Text({text:'Close alert'})));
                });
            } finally { activity=ownActivity; }
            dialog.on('close',function(){report('closes',++closes);});
            actions.replace=function(){check(!page.isClosed(),'dialog replaced page');ui.layout('<vertical><text text="Replacement background"/></vertical>');check(!dialog.isClosed(),'layout closed dialog');report('replaced',true);};
        """).use { running ->
            running.await("The UI dialog and bound Activity factory are visible") { text("Dialog count: 0") && text("Bound Activity native view") }
            click("Increment dialog")
            running.await("Dialog node callbacks update the accepted tree") { text("Dialog count: 1") }
            running.command("replace")
            running.await("Replacing ordinary UI preserves the independent dialog") { running.property("replaced")=="true" && text("Dialog count: 1") }
            click("Close alert")
            running.await("Normal session.close restores the current underlying page") { running.property("closes")=="1" && text("Replacement background") && !text("Dialog count: 1") }
        }
    }

    @Test fun nonCancelableBottomSheetReceivesImmediateFocusAndKeepsActivityInsetsUnchanged() = withSoftwareKeyboard {
        Running("""
            var policyMask=android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE|android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
            var originalMode=activity.getWindow().getAttributes().softInputMode&policyMask, closes=0;
            var field=compose.TextField({text:'Dialog input',testTag:'f3-editor'});
            var dialog=compose.dialog(compose.Column({padding:16,spacing:8},compose.Text({text:'Noncancelable sheet'}),field),{type:'bottomSheet',cancelable:false});
            dialog.on('close',function(){report('closes',++closes);});
            field.focus();
            actions.inspect=function(){check((activity.getWindow().getAttributes().softInputMode&policyMask)===originalMode,'anchor changed Activity IME policy');check(!dialog.isClosed(),'sheet was canceled');report('inspected',true);};
            actions.close=function(){dialog.close();};
        """).use { running ->
            running.await("Focus targets the actual dialog editor after its first composition") {
                node { it.isEditable && it.isFocused && it.text?.toString()=="Dialog input" } != null
            }
            // focus() requests focus; the public contract does not promise a visible software IME.
            // Use the same actual accessibility click as the existing P2 native input acceptance.
            click("Dialog input")
            running.await("Clicking the focused dialog editor shows a real keyboard window") {
                instrumentation.uiAutomation.windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            }
            back()
            running.await("Back first hides the keyboard in the real dialog window") {
                instrumentation.uiAutomation.windows.none { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            }
            back()
            tapOutsideSheet()
            running.command("inspect")
            running.await("Back and scrim leave a noncancelable sheet open") { running.property("inspected")=="true" && text("Noncancelable sheet") }
            running.command("close")
            running.await("Explicit close remains available for noncancelable sheets") { running.property("closes")=="1" && !text("Noncancelable sheet") }
        }
    }

    @Test fun userDismissDuringAnUpdateClosesOnceAndRemovesTheUiAnchor() {
        Running("""
            var value=compose.state(0), closes=0;
            var dialog=compose.dialog(function(){return compose.Text({text:'Dismiss generation '+value.value,padding:16});});
            dialog.on('close',function(){report('closes',++closes);});
            actions.update=function(){value.value++;};
            var late;
            actions.late=function(){late=compose.dialog(compose.Text({text:'Late attached Activity dialog',padding:16}));late.on('close',function(){report('lateClosed',true);});};
            actions.closeLate=function(){late.close();};
        """).use { running ->
            fun currentAnchor(): View {
                lateinit var result: View
                instrumentation.runOnMainSync {
                    val decor=(running.engine.getTag("activity") as Activity).window.decorView as ViewGroup
                    val anchors=(0 until decor.childCount).map(decor::getChildAt).filter {
                        it.layoutParams.width==1 && it.layoutParams.height==1 && it.alpha==0f &&
                            it.importantForAccessibility==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    }
                    assertEquals("Exactly one owned transparent dialog anchor is attached",1,anchors.size)
                    result=anchors.single()
                    assertSame(decor,result.parent)
                }
                return result
            }
            running.await("Cancelable alert is shown") { text("Dismiss generation 0") }
            val firstAnchor=currentAnchor()
            running.command("update")
            back()
            running.await("Dismissal outlives an in-flight tree generation") { running.property("closes")=="1" }
            instrumentation.runOnMainSync {
                // System status/navigation background Views may appear on the first frame.
                // Assert the exact owned object was removed, independent of those decor children.
                assertNull(firstAnchor.parent)
                assertFalse(firstAnchor.isAttachedToWindow)
            }
            assertFalse(text("Dismiss generation 0")); assertFalse(text("Dismiss generation 1"))
            running.command("late")
            running.await("Opening after the Activity is already displayed uses its valid application token") {
                text("Late attached Activity dialog")
            }
            val lateAnchor=currentAnchor()
            assertNotSame(firstAnchor,lateAnchor)
            running.command("closeLate")
            running.await("The later dialog also releases its anchor") { running.property("lateClosed")=="true" && !text("Late attached Activity dialog") }
            instrumentation.runOnMainSync {
                assertNull(lateAnchor.parent)
                assertFalse(lateAnchor.isAttachedToWindow)
            }
        }
    }

    @Test fun overlayAlertKeepsTheNonUiEngineAliveUntilItsSessionCloses() {
        assumeTrue("Grant overlay permission to the owned test host", Settings.canDrawOverlays(context))
        Running("""
            var owner=String(java.lang.Thread.currentThread().getId()), closes=0;
            var dialog=compose.dialog(compose.Column({padding:16},compose.Text({text:'Overlay alert alive'}),
                compose.Button({onClick:function(){check(String(java.lang.Thread.currentThread().getId())===owner,'callback left its script dispatcher');dialog.close();}},compose.Text({text:'Close overlay alert'}))));
            dialog.on('close',function(){report('closes',++closes);});
        """,ui=false,keepAlive=false).use { running ->
            running.await("A dialog alone retains its non-UI engine") { text("Overlay alert alive") }
            assertFalse(running.engine.isDestroyed)
            click("Close overlay alert")
            running.await("Closing releases the only engine keepalive and closes exactly once") { running.property("closes")=="1" && running.engine.isDestroyed }
            instrumentation.waitForIdleSync()
            assertFalse(text("Overlay alert alive"))
        }
    }

    @Test fun overlayBottomSheetClosesWithFloatyCloseAllAndWithEngineStop() {
        assumeTrue("Grant overlay permission to the owned test host", Settings.canDrawOverlays(context))
        Running("""
            var closes=0;
            var dialog=compose.dialog(compose.Text({text:'Overlay sheet first',padding:24}),{type:'bottomSheet'});
            dialog.on('close',function(){report('closes',++closes);});
            actions.closeAll=function(){floaty.closeAll();};
            actions.next=function(){dialog=compose.dialog(compose.Text({text:'Overlay sheet second',padding:24}),{type:'bottomSheet'});};
        """,ui=false).use { running ->
            running.await("The non-UI bottom sheet is shown") { text("Overlay sheet first") }
            running.command("closeAll")
            running.await("Legacy closeAll owns the hidden overlay anchor as well") { running.property("closes")=="1" && !text("Overlay sheet first") }
            running.command("next")
            running.await("A released dialog slot can be reused") { text("Overlay sheet second") }
            running.stop()
            assertFalse(text("Overlay sheet second"))
        }
    }

    @Test fun immediateCloseAndRolledBackCreationDoNotShowLateWindows() {
        Running("""
            ui.layout('<vertical><text text="Immediate close background"/></vertical>');
            var closes=0, immediate=compose.dialog(compose.Text({text:'Must never show immediate'}));
            immediate.on('close',function(){closes++;});immediate.close();
            var orphan;try{compose.batch(function(){orphan=compose.dialog(compose.Text({text:'Must never show rollback'}),{type:'bottomSheet'});throw Error('rollback');});}catch(error){}
            actions.inspect=function(){check(immediate.isClosed()&&orphan.isClosed()&&compose.sessions.length===0,'late session survived');report('checked',closes);};
        """).use { running ->
            running.await("The underlying Activity is ready") { text("Immediate close background") }
            running.command("inspect")
            running.await("Deferred attachment observes both kinds of cancellation") { running.property("checked")=="1" }
            assertFalse(text("Must never show immediate"));assertFalse(text("Must never show rollback"))
        }
    }

    @Test fun nonUiBorrowedActivityCannotBypassDeniedOverlayPermission() {
        assumeTrue("Run this owned permission-negative phase with overlay permission denied", !Settings.canDrawOverlays(context))
        Running("ui.layout('<vertical><text text=\"Foreign UI remains alive\"/></vertical>');").use { foreign ->
            foreign.await("A separate owned UI engine is available") { text("Foreign UI remains alive") }
            val owner=foreign.engine.getTag("activity") as Activity
            Running("""
                activity=runtime.getProperty('f3.foreignActivity');
                var node=compose.AndroidView({view:function(ctx){check(!(ctx instanceof android.app.Activity),'foreign Activity used by non-UI factory');return new android.widget.TextView(ctx);}});
                try{compose.dialog(node);throw Error('permission bypassed');}
                catch(error){check(error instanceof ComposeError&&error.code==='PERMISSION_REQUIRED','wrong permission error: '+error);report('rejected',error.code);}
            """,ui=false,initialProperties=mapOf("f3.foreignActivity" to owner)).use { running ->
                running.await("A forged global alias cannot turn a worker into an Activity dialog") { running.property("rejected")=="PERMISSION_REQUIRED" }
                assertFalse(foreign.engine.isDestroyed)
                assertTrue(text("Foreign UI remains alive"))
            }
        }
    }

    @Test fun legacyProviderRejectsDialogBeforeChangingTheExistingUi() {
        org.autojs.autojs.core.plugin.compose.ComposeUiPluginHost(context).use { host ->
            assumeTrue("Run this phase with a real provider that predates the dialog feature", host.load().dialogFactory==null)
        }
        Running("""
            var count=0;
            var label=compose.Text({text:'Legacy dialog rejection 0'});
            var page=compose.mount(compose.Column(label,compose.Button({onClick:function(){label.text='Legacy dialog rejection '+(++count);}},compose.Text({text:'Legacy page still works'}))));
            try{compose.dialog(function(){report('renderCalled',true);return compose.Text({text:'unsupported'});});throw Error('old provider accepted dialog');}
            catch(error){check(error instanceof ComposeError&&error.code==='PLUGIN_INCOMPATIBLE','wrong capability error: '+error);check(!page.isClosed(),'old page closed');report('rejected',error.code);}
        """).use { running ->
            running.await("An old provider remains usable after capability rejection") { running.property("rejected")=="PLUGIN_INCOMPATIBLE" && text("Legacy dialog rejection 0") }
            assertNull(running.property("renderCalled"))
            click("Legacy page still works")
            running.await("The original callback remains bound") { text("Legacy dialog rejection 1") }
        }
    }

    private fun back() = assertTrue("Deliver Back through Android",instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
    private fun withSoftwareKeyboard(action: () -> Unit) {
        fun command(value: String): String = instrumentation.uiAutomation.executeShellCommand(value).let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
        }
        val old = command("settings get secure show_ime_with_hard_keyboard")
        check(old == "null" || old.toIntOrNull() != null) { "Unexpected keyboard setting" }
        command("settings put secure show_ime_with_hard_keyboard 1")
        try { action() } finally {
            command(if (old == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $old")
        }
    }
    private fun tapOutsideSheet() {
        val metrics=context.resources.displayMetrics
        val x=metrics.widthPixels/2f
        val y=metrics.heightPixels/4f
        val time=SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,y,0).apply { source=android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
    }

    private inner class Running(source: String, ui: Boolean=true, keepAlive: Boolean=true, initialProperties: Map<String, Any> = emptyMap()) : Closeable {
        private val marker="compose-f3-${UUID.randomUUID()}"
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
                    ?:failure.set(AssertionError("Dialog script did not use Rhino"))
                started.countDown()
            }
            override fun onException(execution:ScriptExecution,error:Throwable) { if(owned(execution)){if(!stopping.get())failure.compareAndSet(null,error);started.countDown()} }
        }
        init {
            val script=(if(ui)"\"ui\";\n" else "")+"// $marker\n"+"""
                function check(ok,message){if(!ok)throw Error(message);}
                function report(key,value){runtime.putProperty('f3.'+key,String(value));}
                var actions={}; var keepAlive=${keepAlive};
                var commandTimer=keepAlive?setInterval(function(){var command=runtime.getProperty('f3.command');if(command){runtime.putProperty('f3.command','');actions[String(command)]();}},30):null;
            """.trimIndent()+"\n"+source.trimIndent()
            service.registerGlobalScriptExecutionListener(listener)
            try {
                instrumentation.runOnMainSync { context.startActivity(Intent(context,RunIntentActivity::class.java)
                    .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT,script).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                assertTrue("The owned dialog engine starts",started.await(30,TimeUnit.SECONDS))
                healthy();assertNotNull(engineRef.get())
            }catch(error:Throwable){close();throw error}
        }
        fun property(name:String)=engine.runtime.getProperty("f3.$name")?.toString()
        fun command(name:String){engine.runtime.putProperty("f3.command",name)}
        private fun healthy(){failure.get()?.let{throw AssertionError("Dialog script failed; logs=${logs.joinToString(" | ")}",it)};logs.firstOrNull{it.startsWith("Compose UI: ")}?.let{throw AssertionError(it)}}
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
