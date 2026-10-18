package org.autojs.autojs.engine

import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Native Views, renderer borrowing and XML attachments across real JS processes. */
class RuntimeUiInteropDeviceTest {
    private val fixture = RuntimeUiComposeDeviceTest()
    private val harness = CaptureWorkerBridgeDeviceTest()
    private val modes = listOf("node", "node-esm", "worker", "worker-esm", "worker-nested", "bun")

    @Test fun xmlAttachAndNativeClickAcrossRuntimeModes() = fixture.grants {
        for (mode in modes) fixture.interact(mode, """
            const root=await ui.inflate('<vertical><text id="title" text="XML-$mode"/><compose id="content" h="320"/></vertical>');
            const window=await ui.showLayout(root),container=await root.findView('content');
            const native=await ui.createView('Button',{id:'borrowed',text:'Interop-$mode-next'});
            let clicked=0;native.on('click',()=>{clicked++;});
            const session=await compose.attach(container,compose.Column({},compose.Text({text:'Compose-$mode'}),compose.AndroidView({view:native})));
            const end=Date.now()+20000;while(!clicked&&Date.now()<end)await new Promise(r=>setTimeout(r,30));
            if(clicked!==1)throw Error('Native event missing');
            await native.set({text:'updated'});if(await native.get('text')!=='updated')throw Error('View update');
            await session.close();await native.dispose();await window.close();await root.dispose();return {clicked,closed:session.isClosed()};
        """.trimIndent(), "Interop-$mode-next")
        harness.await("Interop windows released") { RuntimeInteropActivity.windows.isEmpty() && RuntimeComposeActivity.sessions.isEmpty() }
    }

    @Test fun factoriesReplacementAndOwnershipAcrossRuntimeModes() = fixture.grants {
        for (mode in modes) {
            val result=fixture.run(mode,"""
                async function rejects(fn){try{await fn();}catch(e){return;}throw Error('Expected rejection');}
                const context=await ui.context();if(!context.packageName||!context.density)throw Error('Context metadata');
                const view=await context.createView('TextView',{text:'factory-$mode'});
                const root=await context.inflate('<vertical><compose id="content" h="220"/></vertical>');
                const container=await ui.findView(root,'content');
                const window=await ui.showLayout(root);
                await rejects(()=>compose.attach(view,compose.Text({text:'wrong container'})));
                await rejects(()=>ui.inflate('<!DOCTYPE a [<!ENTITY b "x">]><text text="x"/>'));
                await rejects(()=>ui.inflate('<text/>',{__autojs6UiContext:'foreign'}));
                await rejects(()=>compose.attach(container,compose.AndroidView({view:root})));
                let builds=0;
                const node=compose.AndroidView({view:async ctx=>{builds++;if(ctx.__autojs6UiContext!==context.__autojs6UiContext)throw Error('Wrong context');return view;}});
                const first=await compose.attach(container,compose.Column({},node));await first.flush();
                await rejects(()=>view.dispose());
                const other=await ui.createView('Compose');await rejects(()=>compose.attach(other,compose.AndroidView({view})));await other.dispose();
                await first.render(compose.Column({},node));if(builds!==1)throw Error('Factory not retained');
                const next=await compose.attach(container,compose.Text({text:'replacement'}));
                await new Promise(r=>setTimeout(r,200));if(!first.isClosed())throw Error('Replacement did not close old session');
                await view.dispose();await rejects(()=>ui.view({__autojs6ViewHandle:'foreign'}).get());
                await next.close();await window.close();await root.dispose();
                const detached=await ui.createView('Compose');const detachedSession=await compose.attach(detached,compose.Text({text:'detached'}));
                await detached.dispose();await new Promise(r=>setTimeout(r,200));if(!detachedSession.isClosed())throw Error('Detached container retained its session');
                return {builds,closed:next.isClosed()};
            """.trimIndent())
            assertEquals(result.toString(),1,result.getInt("builds"));assertTrue(result.getBoolean("closed"))
        }
    }

    @Test fun clonedHandlesAndWorkerExitReleaseOnlyItsComposition() = fixture.grants {
        val worker="""
            const {parentPort,workerData}=require('node:worker_threads'),ui=require('ui'),compose=require('compose');
            (async()=>{const container=ui.view(workerData.container);const native=await ui.createView('Text',{text:'worker-borrowed'});
            const session=await compose.attach(container,compose.AndroidView({view:native}));parentPort.postMessage({native,session:session.id});})();
        """.trimIndent()
        val result=fixture.run("node","""
            const root=await ui.inflate('<vertical><text text="parent-owned"/><compose id="content" h="220"/></vertical>');
            const window=await ui.showLayout(root),container=await root.findView('content');
            const {Worker}=require('node:worker_threads');const worker=new Worker(${JSONObject.quote(worker)},{eval:true,workerData:{container}});
            const result=await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
            await worker.terminate();await new Promise(r=>setTimeout(r,500));
            if(window.closed)throw Error('Parent window closed');
            const native=ui.view(result.native);if(await native.get('text')!=='worker-borrowed')throw Error('Execution-owned view lost');
            await native.dispose();const replacement=await compose.attach(container,compose.Text({text:'parent reattached'}));
            await replacement.close();await window.close();await root.dispose();return {released:true};
        """.trimIndent())
        assertTrue(result.toString(),result.getBoolean("released"))
    }

    @Test fun xmlAttachmentAndBorrowedViewSurviveRotation() = fixture.grants {
        harness.withFixture {
            for(mode in listOf("node","worker","bun")) {
                harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                val executor=Executors.newSingleThreadExecutor()
                try {
                    val future=executor.submit<JSONObject>{fixture.run(mode,"""
                        const root=await ui.inflate('<vertical><compose id="content" h="320"/></vertical>');
                        const native=await ui.createView('Button',{text:'Rotate-$mode-next'});let clicks=0;native.on('click',()=>clicks++);
                        const window=await ui.showLayout(root),container=await root.findView('content');
                        const session=await compose.attach(container,compose.AndroidView({view:native}));
                        let rejectedDisposals=0;
                        const end=Date.now()+30000;while(clicks<2&&Date.now()<end){
                            try {await native.dispose();throw Error('Borrowed native View was disposed');}
                            catch(error){if(!String(error.message).includes('Close the borrowing Compose session'))throw error;rejectedDisposals++;}
                            await new Promise(r=>setTimeout(r,100));
                        }
                        if(clicks!==2||session.isClosed())throw Error('Attachment lost on rotation '+clicks);
                        if(!rejectedDisposals)throw Error('Borrowed disposal was not exercised');
                        await session.close();await native.dispose();await window.close();await root.dispose();return {clicks,rejectedDisposals};
                    """.trimIndent())}
                    fun click(){harness.await("Interop rotation button action",20000){
                        if(future.isDone)fail(future.get().toString())
                        val node=fixture.find("Rotate-$mode-next")?:return@await false
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }}
                    click()
                    val instrumentation=InstrumentationRegistry.getInstrumentation()
                    val monitor=instrumentation.addMonitor(RuntimeInteropActivity::class.java.name,null,false)
                    try {harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_90);assertNotNull(monitor.waitForActivityWithTimeout(10000))}
                    finally {instrumentation.removeMonitor(monitor)}
                    click();assertEquals(2,future.get(35,TimeUnit.SECONDS).getInt("clicks"))
                } finally {executor.shutdownNow();harness.automation.setRotation(UiAutomation.ROTATION_FREEZE_0)}
            }
        }
    }
}
