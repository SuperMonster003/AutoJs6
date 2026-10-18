package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Run separately with CAMERA and RECORD_AUDIO revoked before starting instrumentation. */
class RuntimeWebNativePermissionDeviceTest {
    @Test fun nativeRecordingRequestsAndroidPermissionsAndHonorsDenial() {
        val args=InstrumentationRegistry.getArguments();val mode=args.getString("webMode") ?: "node";val allow=args.getString("allowMedia")=="true"
        val ui=RuntimeUiComposeDeviceTest();val harness=CaptureWorkerBridgeDeviceTest();val controls=RuntimeWebMediaControls(harness)
        ui.grants {harness.withFixture {RuntimeWebFixtureServer().use {server->
            val pool=Executors.newSingleThreadExecutor()
            try {
                val future=pool.submit<JSONObject>{ui.run(mode,RuntimeWebStreamsFixture.setup(server.url)+"""
                    try {let allowed=false;try{await web.startBackgroundRecording({audio:true,width:640,height:480});allowed=true;await web.cancelBackgroundRecording();}catch(e){if(!String(e).includes('denied'))throw e;}return {allowed};}
                    finally {await web.cancelBackgroundRecording().catch(()=>{});await window.close();await web.dispose();}
                """.trimIndent())}
                controls.await("Native recording permissions",80000){controls.permission(allow);future.isDone}
                assertEquals(allow,future.get(1,TimeUnit.SECONDS).getBoolean("allowed"))
            }finally{pool.shutdownNow()}
        }}}
    }
}
