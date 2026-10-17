package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

/** Actual pixels, OpenCV descriptors/homography and native PNG quantization through both runtimes. */
class AdvancedImageBridgeDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val capabilities=listOf("image","screen_capture","network","device")
    private val modes=listOf("node","node-esm","worker-cjs","worker-esm","worker-nested","bun")
    private val fixture get()=instrumentation.context.assets.open("advanced-images.js").bufferedReader().use{it.readText()}

    @Test fun filtersColorsMetricsAndEncodingAcrossAllExecutionModes()=matrix("filters")
    @Test fun featuresMatricesFilesAndUrlsAcrossAllExecutionModes()=matrix("features")
    @Test fun quantizationMetricsBytesAndFileOutputAcrossAllExecutionModes()=matrix("quantization")
    @Test fun circleDetectionAcrossAllExecutionModes()=matrix("circles")

    @Test fun rawNodeUrlLoadingRequiresItsDeclaredNetworkCapability() {
        val result=harness.execute("node","""
            const bridge=require('autojs6:bridge');
            const error=await rejects(()=>bridge.callAutoJs('images','load',['https://example.invalid/image.png'],{permissions:['image']}));
            eq(error.code,'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED','raw network capability');return {networkDenied:true};
        """.trimIndent(),capabilities=listOf("image"))
        assertTrue(result.getBoolean("networkDenied"))
    }

    @Test fun imageMatAndFeatureHandlesShareTheExistingQuota()=grants {
        val folder=File(context.getExternalFilesDir(null),"advanced-image-quota-${UUID.randomUUID()}").apply{mkdirs()}
        val bitmap=fixtureImage("filters");val source=File(folder,"source.png")
        try{
            source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            for(kind in listOf("node","bun"))harness.execute(kind,"""
                const path=${JSONObject.quote(source.path)},image=await images.readImage(path),handles=[image];
                const features=await images.detectAndComputeFeatures(image,{method:'ORB',maxFeatures:20});handles.push(features);
                try{
                  for(let i=0;i<30;i++)handles.push(await images.imread(path));
                  await rejects(()=>images.imread(path));await images.recycle(features);handles.push(await images.imread(path));
                }finally{await images.recycle(handles)}
                eq(images.isRecycled(handles),true,'resource cleanup');return {sharedQuota:32,recoveredAfterRecycle:true};
            """.trimIndent(),capabilities=capabilities)
        }finally{bitmap.recycle();folder.deleteRecursively()}
    }

    private fun matrix(kind:String)=grants {
        val folder=File(context.getExternalFilesDir(null),"advanced-images-${UUID.randomUUID()}").apply{mkdirs()}
        val bitmap=fixtureImage(kind);val source=File(folder,"source.png")
        try {
            source.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            HttpFixture(source.readBytes()).use { server ->
                val results=JSONArray()
                for(mode in modes){
                    val output=File(folder,"$mode-${if(kind=="features")"matches.jpg" else "quantized.png"}")
                    val denied=File(folder,"$mode-denied.png");denied.writeText("unchanged")
                    val input=JSONObject().put("kind",kind).put("mode",mode).put("path",source.path).put("url",server.url).put("output",output.path).put("denied",denied.path)
                    if(kind=="filters"){
                        val alpha=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(0x80ff0000.toInt())}
                        try{val bytes=java.io.ByteArrayOutputStream().use{alpha.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};input.put("alpha",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP))}finally{alpha.recycle()}
                    }
                    val result=run(mode,fixture,input)
                    if(kind=="features"||kind=="quantization"){
                        assertTrue("Output missing: $mode/$kind",output.length()>0)
                        val decoded=android.graphics.BitmapFactory.decodeFile(output.path);assertNotNull(decoded);decoded!!.recycle()
                    }
                    if(kind=="quantization")assertEquals("Failed quantization replaced output", "unchanged", denied.readText())
                    results.put(result)
                }
                File(context.getExternalFilesDir(null),"advanced-images-$kind.json").writeText(results.toString(2))
            }
        }finally{bitmap.recycle();folder.deleteRecursively()}
    }

    @Test fun featureHandlesCrossWorkersButNeverCrossExecutions()=grants {
        val folder=File(context.getExternalFilesDir(null),"advanced-image-owner-${UUID.randomUUID()}").apply{mkdirs()}
        val bitmap=fixtureImage("features");val source=File(folder,"source.png")
        try {
            source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            val result=harness.execute("node","""
                const {Worker}=require('node:worker_threads');const image=await images.readImage(${JSONObject.quote(source.path)});
                const features=await new Promise((resolve,reject)=>{const w=new Worker('const {parentPort,workerData}=require("node:worker_threads");require("images").detectAndComputeFeatures(workerData,{method:"ORB",maxFeatures:500}).then(v=>parentPort.postMessage(v));',{eval:true,workerData:image});w.on('message',resolve);w.on('error',reject);});
                await pause(100);await images.recycle(image);const frame=await images.matchFeatures(features,features);if(!frame||frame.inliers<4)throw Error('Worker feature ownership');
                return {features};
            """.trimIndent(),capabilities=capabilities)
            harness.execute("node","const stale=${result.getJSONObject("features")};await rejects(()=>images.matchFeatures(stale,stale));return {staleRejected:true};",capabilities=capabilities)
        }finally{bitmap.recycle();folder.deleteRecursively()}
    }

    @Test fun bunFileAndProcessingGrantsCannotBeBypassedByRawImageCalls()=grants {
        val store=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val folder=File(context.getExternalFilesDir(null),"advanced-image-grants-${UUID.randomUUID()}").apply{mkdirs()}
        val bitmap=fixtureImage("filters");val source=File(folder,"source.png")
        try {
            source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            store.edit().putBoolean(BunHostCapabilityGrants.IMAGE_FILES,false).commit()
            harness.execute("bun","""
                const image=await images.fromBase64(${JSONObject.quote(android.util.Base64.encodeToString(source.readBytes(),android.util.Base64.NO_WRAP))});
                eq((await rejects(()=>images.load('https://example.invalid/image.png'))).code,'NOT_GRANTED','file grant');
                eq((await rejects(()=>host.call('image.process',{method:'matchFeatures',args:[image,image,{drawMatches:${JSONObject.quote(File(folder,"forbidden.jpg").path)}}]}))).code,'NOT_GRANTED','raw drawing');
                eq((await rejects(()=>host.call('image.process',{method:'load',args:['file://'+${JSONObject.quote(source.path)}]}))).code,'INVALID_REQUEST','raw file read');
                await images.recycle(image);return {fileDenied:true};
            """.trimIndent())
            store.edit().putBoolean(BunHostCapabilityGrants.IMAGE_FILES,true).putBoolean(BunHostCapabilityGrants.IMAGE_PROCESS,false).commit()
            harness.execute("bun","""
                const image=await images.readImage(${JSONObject.quote(source.path)});
                eq((await rejects(()=>images.quantizeToFile(image,${JSONObject.quote(File(folder,"forbidden.png").path)}))).code,'NOT_GRANTED','quantization needs processing');
                await images.recycle(image);eq(images.isRecycled(image),true,'file-only cleanup');return {processingDenied:true};
            """.trimIndent())
            assertEquals(setOf("source.png"),folder.list()!!.toSet())
        }finally{bitmap.recycle();folder.deleteRecursively()}
    }

    @Test fun asyncCaptureAliasAndOptionsUseTheRealCaptureSession()=grants {
        for(kind in listOf("node","bun"))harness.withFixture {
            val pool=Executors.newSingleThreadExecutor()
            try{
                val future=pool.submit<JSONObject>{harness.execute(kind,"""
                    eq(await images.getScreenCaptureOptions(),null,'idle options');await images.requestScreenCaptureAsync({orientation:'portrait'});
                    const options=await images.getScreenCaptureOptions();const image=await images.captureScreen();eq(images.getSize(image),{width:options.width,height:options.height},'capture dimensions');
                    await images.recycle(image);await images.stopScreenCapture();eq(await images.getScreenCaptureOptions(),null,'stopped options');return {options};
                """.trimIndent(),capabilities=capabilities)}
                harness.await("advanced image capture consent"){if(future.isDone)fail(future.get().toString());harness.consentButton(true)!=null};harness.clickConsent(true)
                val result=future.get(25,TimeUnit.SECONDS);assertTrue(result.getJSONObject("options").getInt("height")>result.getJSONObject("options").getInt("width"));harness.awaitNoProjection()
            }finally{pool.shutdownNow()}
        }
    }

    private fun run(mode:String,code:String,input:JSONObject):JSONObject {
        val body=when(mode){
            "node","bun"->code+"\nreturn await exerciseAdvancedImages(images,$input);"
            "node-esm"->code+"\nreturn await exerciseAdvancedImages((await import('autojs6:images')).default,$input);"
            else->{
                val worker=if(mode=="worker-esm")"import images from 'autojs6:images';import {parentPort} from 'node:worker_threads';$code\nparentPort.postMessage(await exerciseAdvancedImages(images,$input));"
                    else "const {parentPort}=require('node:worker_threads');$code\nexerciseAdvancedImages(require('autojs6:images'),$input).then(v=>parentPort.postMessage(v));"
                val entry=if(mode=="worker-nested")"const {Worker,parentPort}=require('node:worker_threads');new Worker(${JSONObject.quote(worker)},{eval:true}).on('message',v=>parentPort.postMessage(v)).on('error',e=>{throw e});" else worker
                "const {Worker}=require('node:worker_threads');return await new Promise((resolve,reject)=>{let value;const w=new Worker("+
                    (if(mode=="worker-esm")"new URL('data:text/javascript,'+encodeURIComponent(${JSONObject.quote(entry)}))" else "${JSONObject.quote(entry)},{eval:true}")+
                    ");w.on('message',v=>value=v);w.on('error',reject);w.on('exit',code=>code?reject(Error('Worker exit '+code)):resolve(value));});"
            }
        }
        return harness.execute(if(mode=="bun")"bun" else "node",body,capabilities=capabilities)
    }

    private fun grants(action:()->Unit){
        val store=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val keys=listOf(BunHostCapabilityGrants.IMAGE_PROCESS,BunHostCapabilityGrants.IMAGE_FILES,BunHostCapabilityGrants.SCREEN_CAPTURE,BunHostCapabilityGrants.SCREEN_METRICS)
        val before=keys.associateWith{if(store.contains(it))store.getBoolean(it,false)else null}
        store.edit().apply{keys.forEach{putBoolean(it,true)}}.commit()
        try{action()}finally{store.edit().apply{before.forEach{(key,value)->if(value==null)remove(key)else putBoolean(key,value)}}.commit()}
    }

    private fun fixtureImage(kind:String):Bitmap {
        val width=when(kind){"filters"->80;"features"->480;"circles"->180;else->128}
        val height=when(kind){"filters"->60;"features"->360;"circles"->150;else->128}
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);val paint=Paint()
        when(kind){
            "filters"->{for((x,y,color)in listOf(Triple(0,0,Color.RED),Triple(40,0,Color.GREEN),Triple(0,30,Color.BLUE),Triple(40,30,Color.WHITE))){paint.color=color;canvas.drawRect(x.toFloat(),y.toFloat(),(x+40).toFloat(),(y+30).toFloat(),paint)}}
            "features"->{canvas.drawColor(Color.WHITE);val random=Random(471);paint.isAntiAlias=true
                repeat(800){paint.color=Color.rgb(random.nextInt(220),random.nextInt(220),random.nextInt(220));val x=random.nextInt(width).toFloat();val y=random.nextInt(height).toFloat();canvas.drawCircle(x,y,random.nextInt(2,12).toFloat(),paint)}
                paint.color=Color.BLACK;paint.textSize=25f;for(y in 30..330 step 40)canvas.drawText("AutoJs6 feature $y",35f,y.toFloat(),paint)
            }
            "circles"->{canvas.drawColor(Color.BLACK);paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.isAntiAlias=true;canvas.drawCircle(80f,70f,32f,paint)}
            else->{for(y in 0 until height)for(x in 0 until width)bitmap.setPixel(x,y,Color.rgb(x*2,y*2,(x+y)%256))}
        }
        return bitmap
    }

    private class HttpFixture(private val png:ByteArray):AutoCloseable {
        private val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        private val worker=thread(isDaemon=true,name="AdvancedImages-http-fixture"){
            while(!server.isClosed)runCatching{server.accept().use{socket->
                val reader=socket.getInputStream().bufferedReader();val first=reader.readLine().orEmpty();while(!reader.readLine().isNullOrEmpty()){}
                val bytes=if(first.contains("/invalid"))"invalid-image".toByteArray()else png
                socket.getOutputStream().use{it.write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());it.write(bytes);it.flush()}
            }}
        }
        val url get()="http://127.0.0.1:${server.localPort}/fixture.png"
        override fun close(){server.close();worker.join(1500)}
    }
}
