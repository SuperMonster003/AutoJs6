package org.autojs.autojs.engine

import android.app.UiAutomation
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real native widgets and asynchronous property access in every portable runtime mode. */
class RuntimeXmlViewDeviceTest {
    private val fixture = RuntimeUiComposeDeviceTest()
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modes = listOf("node", "node-esm", "worker", "worker-esm", "worker-nested", "bun")
    private val evidence = JSONArray()

    @Test fun commonXmlWidgetsAndOrderedPropertiesAcrossRuntimes() = fixture.grants {
        for (mode in modes) {
            val result = fixture.run(mode, """
                function eq(a,b,label){if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify([a,b]));}
                const root=await ui.inflate('<vertical id="root" padding="4"><card id="card" w="240" h="240" cardCornerRadius="8"><relative id="relative"><text id="label" text="before" w="110" h="28"/><text id="anchor" text="below" layout_below="label"/><space id="space" w="4" h="4"/></relative></card><spinner id="spinner" entries="One|Two|Three" selectedIndex="1"/><seekbar id="seek" max="20" progress="4"/><ratingbar id="rating" numStars="5" stepSize="0.5" rating="2.5"/><radiogroup id="radios" checkedButton="@id/first"><radio id="first" text="First"/><radio id="second" text="Second"/></radiogroup><toggle id="toggle" textOn="On" textOff="Off"/><checkedtext id="checked" text="Check"/><numberpicker id="picker" min="2" max="4" value="3"/><hscroll id="hscroll" h="40"><horizontal><text text="Horizontal"/><space w="3" h="3"/></horizontal></hscroll><img id="image" src="@android:drawable/ic_menu_info_details" w="36" h="36"/><input id="input" text="native"/></vertical>');
                const get=id=>root.findView(id),label=await get('label'),spinner=await get('spinner'),seek=await get('seek'),rating=await get('rating'),radios=await get('radios'),toggle=await get('toggle'),checked=await get('checked'),picker=await get('picker'),image=await get('image'),input=await get('input');
                for(const [id,type] of Object.entries({card:'CardView',relative:'RelativeLayout',space:'Space',spinner:'Spinner',seek:'SeekBar',rating:'RatingBar',radios:'RadioGroup',first:'RadioButton',toggle:'ToggleButton',checked:'CheckedTextView',picker:'NumberPicker',hscroll:'HorizontalScrollView'}))eq((await get(id)).type,type,'native '+id);
                label.text='first';label.text='last';eq(await label.text,'last','ordered write/read');
                label.textColor='#123456';label.textSize=18;label.textStyle='bold';label.padding=[2,3];label.margin=[1,2,3,4];label.alpha=0.5;await label.flush();
                eq(await label.xmlId,'label','XML identity');eq(await label.textStyle,'bold','style');eq(await label.textColor,0xff123456|0,'color');eq(await label.alpha,0.5,'alpha');
                spinner.items=['Alpha','Beta','Gamma'];spinner.selectedIndex=2;eq(await spinner.selectedIndex,2,'spinner');eq(await spinner.items,['Alpha','Beta','Gamma'],'entries');
                seek.progress=13;eq(await seek.progress,13,'seek');rating.rating=4;eq(await rating.rating,4,'rating');
                radios.checkedId='second';eq(await radios.checkedId,'second','radio identity');eq(await (await get('second')).checked,true,'radio state');
                toggle.checked=true;checked.checked=true;picker.value=4;eq(await toggle.checked,true,'toggle');eq(await checked.checked,true,'checked text');eq(await picker.value,4,'number picker');
                input.text='edited';input.hint='Hint';input.inputType='number';await input.flush();eq(await input.text,'edited','input');
                if(await image.drawableWidth<=0)throw Error('Drawable missing');image.scaleType='centerInside';eq(await image.scaleType,'CENTER_INSIDE','image scale');
                const width=await (await get('card')).layoutWidth;const density=(await ui.context()).density;eq(width,Math.trunc(240*density),'declared child width retained');
                const snapshot=await picker.get();await root.dispose();return {widgets:12,ordered:true,selected:snapshot.value,width};
            """.trimIndent())
            evidence.put(JSONObject().put("mode",mode).put("result",result))
        }
        save("properties")
    }

    @Test fun nativeSelectionAndProgressEventsReachTheCreatingRuntime() = fixture.grants {
        for (mode in modes) {
            val executor=Executors.newSingleThreadExecutor()
            try {
                val future=executor.submit<JSONObject>{fixture.run(mode,"""
                    const window=await ui.showLayout('<vertical><spinner id="spinner" entries="XML-$mode-One|XML-$mode-Two"/><seekbar id="seek" max="100" progress="10" contentDescription="XML-$mode-seek"/><radiogroup id="group"><radio id="r1" text="XML-$mode-radio1"/><radio id="r2" text="XML-$mode-radio2"/></radiogroup><toggle id="toggle" textOn="XML-$mode-on" textOff="XML-$mode-off"/></vertical>');
                    const spinner=await window.findView('spinner'),seek=await window.findView('seek'),group=await window.findView('group'),toggle=await window.findView('toggle');
                    let selected=false,progress=false,radio=false,checked=false;
                    spinner.on('select',e=>{if(e.selectedIndex===1)selected=true;});seek.on('change',e=>{if(e.fromUser&&e.progress===70)progress=true;});
                    group.on('change',e=>{if(e.checkedId==='r2')radio=true;});toggle.on('change',e=>{if(e.checked)checked=true;});
                    const end=Date.now()+30000;while(!(selected&&progress&&radio&&checked)&&Date.now()<end)await new Promise(r=>setTimeout(r,25));
                    if(!(selected&&progress&&radio&&checked))throw Error('Native events '+JSON.stringify({selected,progress,radio,checked}));
                    if(await spinner.selectedIndex!==1||await seek.progress!==70||await group.checkedId!=='r2'||!await toggle.checked)throw Error('Native state mismatch');
                    await window.close();return {selected,progress,radio,checked};
                """.trimIndent())}
                fun click(label:String) { harness.await(label,20000) {
                    if(future.isDone)fail(future.get().toString())
                    var node=fixture.find(label) ?: return@await false
                    while(!node.isClickable&&node.parent!=null)node=node.parent
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } }
                click("XML-$mode-One");click("XML-$mode-Two")
                harness.await("Native seekbar action") {
                    val node=findDescription("XML-$mode-seek") ?: return@await false
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,70f) })
                }
                click("XML-$mode-radio2");click("XML-$mode-off")
                evidence.put(JSONObject().put("mode",mode).put("result",future.get(35,TimeUnit.SECONDS)))
            } finally { executor.shutdownNow() }
        }
        save("events")
    }

    @Test fun listSelectionUsesItemsAfterAPropertyUpdate() = fixture.grants {
        for (mode in modes) fixture.interact(mode,"""
            const window=await ui.showLayout('<vertical><list id="list" h="120"/></vertical>');
            const list=await window.findView('list');let selected;
            list.items=[{text:'XML-list-$mode-new',value:42}];await list.flush();
            list.on('select',event=>{selected=event;});const end=Date.now()+20000;
            while(!selected&&Date.now()<end)await new Promise(r=>setTimeout(r,25));
            if(!selected||selected.text!=='XML-list-$mode-new'||selected.value!==42)throw Error('Stale ListView items '+JSON.stringify(selected));
            await window.close();return {selected:selected.value};
        """.trimIndent(),"XML-list-$mode-new")
    }

    @Test fun invalidPropertiesFailWithoutBlockingRecoveryOrDisposal() = fixture.grants {
        for (mode in modes) {
            val result=fixture.run(mode,"""
                async function reject(fn){try{await fn();}catch(e){return;}throw Error('Expected failure');}
                const text=await ui.createView('Text',{text:'before'});
                await reject(()=>text.set({text:'after',unknown:1}));if(await text.text!=='before')throw Error('Invalid patch mutated native state');
                text.checked=true;await reject(()=>text.flush());text.text='recovered';if(await text.text!=='recovered')throw Error('Write queue remained poisoned');
                text.alpha=2;await reject(()=>text.dispose());await reject(()=>text.get());
                await reject(()=>ui.inflate('<vertical><text id="same"/><text id="same"/></vertical>'));
                await reject(()=>ui.inflate('<relative><text below="missing"/></relative>'));
                await reject(()=>ui.createView('Spinner',{items:['one'],selectedIndex:4}));
                await reject(()=>ui.createView('NumberPicker',{min:5,max:2}));
                const borrowed=await ui.createView('Button',{text:'Borrowed'});const session=await compose.mount(compose.AndroidView({view:borrowed}));
                borrowed.text='Borrowed updated';await borrowed.flush();await session.close();await borrowed.dispose();return {recovered:true,disposed:true};
            """.trimIndent())
            evidence.put(JSONObject().put("mode",mode).put("result",result))
        }
        save("validation")
    }

    @Test fun xmlPropertiesAndComposeSurviveRotation() = fixture.grants {
        harness.withFixture {
            for (mode in listOf("node","worker","bun")) {
                harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                val executor=Executors.newSingleThreadExecutor()
                try {
                    val future=executor.submit<JSONObject>{fixture.run(mode,"""
                        const root=await ui.inflate('<vertical><spinner id="spinner" entries="A|B|C"/><seekbar id="seek" max="100"/><compose id="compose" h="240"/></vertical>');
                        const window=await ui.showLayout(root),spinner=await root.findView('spinner'),seek=await root.findView('seek');
                        spinner.selectedIndex=2;seek.progress=75;await spinner.flush();await seek.flush();let clicks=0;
                        const session=await compose.attach(await root.findView('compose'),compose.Button({onClick(){clicks++;}},compose.Text({text:'XML-rotation-$mode-next'})));
                        const end=Date.now()+30000;while(clicks<2&&Date.now()<end)await new Promise(r=>setTimeout(r,25));
                        if(clicks!==2||await spinner.selectedIndex!==2||await seek.progress!==75||session.isClosed())throw Error('XML state lost');
                        await session.close();await window.close();await root.dispose();return {clicks,selection:2,progress:75};
                    """.trimIndent())}
                    fun click(){harness.await("XML Compose action",20000){if(future.isDone)fail(future.get().toString());var node=fixture.find("XML-rotation-$mode-next")?:return@await false;while(!node.isClickable&&node.parent!=null)node=node.parent;node.performAction(AccessibilityNodeInfo.ACTION_CLICK)}}
                    click()
                    val instrumentation=InstrumentationRegistry.getInstrumentation()
                    val monitor=instrumentation.addMonitor(RuntimeInteropActivity::class.java.name,null,false)
                    try {harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_90);assertNotNull(monitor.waitForActivityWithTimeout(10000))}
                    finally {instrumentation.removeMonitor(monitor)}
                    click();evidence.put(JSONObject().put("mode",mode).put("result",future.get(35,TimeUnit.SECONDS)))
                } finally {executor.shutdownNow();harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_0)}
            }
        }
        save("rotation")
    }

    @Test fun imagePropertyKeepsCopiedPixelsAfterTheSourceIsRecycled() = fixture.grants {
        val bitmap=android.graphics.Bitmap.createBitmap(8,6,android.graphics.Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream()
        bitmap.eraseColor(android.graphics.Color.GREEN);bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle()
        val encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)
        for(mode in modes) {
            val executor=Executors.newSingleThreadExecutor()
            try {
                val future=executor.submit<JSONObject>{fixture.run(mode,"""
                    const images=${if(mode=="bun") "host.images" else "(await import('autojs6:images')).default"};
                    const source=await images.fromBase64('$encoded');
                    const view=await ui.createView('Image',{id:'picture-$mode',contentDescription:'XML-picture-$mode',clickable:true});
                    view.src=source;await view.flush();await images.recycle(source);
                    if(await view.drawableWidth<=0||await view.drawableHeight<=0)throw Error('Image drawable missing');
                    let rejected=false;try{await view.set({src:{path:'/not-an-image-handle'}});}catch(e){rejected=true;}if(!rejected)throw Error('Unscoped image object accepted');
                    const window=await ui.showLayout(view);let clicked=false;view.on('click',()=>clicked=true);
                    const end=Date.now()+20000;while(!clicked&&Date.now()<end)await new Promise(r=>setTimeout(r,25));
                    if(!clicked)throw Error('Image was not inspected');await window.close();await view.dispose();return {copied:true};
                """.trimIndent())}
                harness.await("ImageView ready",20000){if(future.isDone)fail(future.get().toString());findDescription("XML-picture-$mode")!=null}
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val image=RuntimeInteropActivity.windows.values.single { it.root.view.tag=="picture-$mode" }.root.view as android.widget.ImageView
                    val copy=(image.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                    assertFalse(copy.isRecycled);assertEquals(8,copy.width);assertEquals(6,copy.height);assertEquals(android.graphics.Color.GREEN,copy.getPixel(0,0))
                }
                assertTrue(requireNotNull(findDescription("XML-picture-$mode")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                evidence.put(JSONObject().put("mode",mode).put("result",future.get(30,TimeUnit.SECONDS)))
            } finally {executor.shutdownNow()}
        }
        save("images")
    }

    @Test fun dimensionsWeightsAndRelativeRulesAffectTheActualLayout() = fixture.grants {
        for (mode in listOf("node","worker","bun")) {
            val executor=Executors.newSingleThreadExecutor()
            try {
                val future=executor.submit<JSONObject>{fixture.run(mode,"""
                    const root=await ui.inflate('<vertical id="geometry"><horizontal id="row" w="240" h="40"><text id="weighted" text="Weighted" w="0" weight="1"/><text id="fixed" text="Fixed" w="80"/></horizontal><relative id="relative" w="240" h="120"><text id="above" text="Above" w="80" h="30"/><text id="below" text="Below" w="80" h="30" layout_below="above" margin="0 4 0 0"/></relative><button id="done" text="XML-layout-$mode-done"/></vertical>');
                    const window=await ui.showLayout(root),done=await root.findView('done');let clicked=false;done.on('click',()=>clicked=true);
                    const end=Date.now()+25000;while(!clicked&&Date.now()<end)await new Promise(r=>setTimeout(r,25));
                    if(!clicked)throw Error('Geometry fixture not released');await window.close();await root.dispose();return {layout:true};
                """.trimIndent())}
                harness.await("XML geometry ready",20000){if(future.isDone)fail(future.get().toString());fixture.find("XML-layout-$mode-done")!=null}
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val root=RuntimeInteropActivity.windows.values.single { it.root.view.tag=="geometry" }.root.view
                    fun view(id:String)=requireNotNull(root.findViewWithTag<android.view.View>(id))
                    val density=root.resources.displayMetrics.density
                    assertEquals((240*density).toInt(),view("row").width)
                    assertEquals((80*density).toInt(),view("fixed").width)
                    assertEquals(view("row").width-view("fixed").width,view("weighted").width)
                    assertTrue("below.top=${view("below").top}, above.bottom=${view("above").bottom}, margin=${(4*density).toInt()}",
                        view("below").top>=view("above").bottom+(4*density).toInt())
                }
                assertTrue(requireNotNull(fixture.find("XML-layout-$mode-done")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                evidence.put(JSONObject().put("mode",mode).put("result",future.get(30,TimeUnit.SECONDS)))
            } finally {executor.shutdownNow()}
        }
        save("geometry")
    }

    private fun findDescription(label:String):AccessibilityNodeInfo? {
        fun walk(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.contentDescription?.toString()==label&&node.isVisibleToUser)return node
            for(i in 0 until node.childCount)walk(node.getChild(i))?.let{return it}
            return null
        }
        return harness.automation.windows.firstNotNullOfOrNull{walk(it.root)}
    }
    private fun save(name:String){File(context.getExternalFilesDir(null),"runtime-xml-view-$name.json").writeText(evidence.toString(2))}
}
