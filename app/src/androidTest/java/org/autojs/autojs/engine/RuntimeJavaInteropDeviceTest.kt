package org.autojs.autojs.engine

import android.content.Context
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RuntimeJavaInteropDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private fun fixture(action:(File)->Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val old=if(prefs.contains(Grants.JAVA_INTEROP))prefs.getBoolean(Grants.JAVA_INTEROP,false)else null
        prefs.edit().putBoolean(Grants.JAVA_INTEROP,true).commit()
        try { harness.withFixture(setup={it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}) {
            val root=File(context.cacheDir,"java-interop-${UUID.randomUUID()}").canonicalFile.apply{mkdirs()}
            try {action(root)} finally {check(root.parentFile==context.cacheDir.canonicalFile);root.deleteRecursively()}
        }} finally {prefs.edit().apply{if(old==null)remove(Grants.JAVA_INTEROP)else putBoolean(Grants.JAVA_INTEROP,old)}.commit()}
    }
    private fun exercise(mode:String)=fixture {root->
        val program="const expectedPackage=${JSONObject.quote(context.packageName)};${RuntimeJavaInteropPrograms.body()}"
        val script=when(mode) {
            "bun" -> "const java=host.java;$program"
            "node" -> "const java=require('autojs6:java');$program"
            else -> {
                val child="const {parentPort}=require('node:worker_threads'),java=require('java');const eq=(a,b,label)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label);};(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
                "const {Worker}=require('node:worker_threads');const w=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}"
            }
        }
        val result=harness.execute(if(mode=="bun")"bun" else "node",script,capabilities=listOf("java_interop"),workingDirectory=root.path)
        assertEquals(context.packageName,result.getString("packageName"));assertTrue(result.getBoolean("longExact"))
        File(context.getExternalFilesDir(null),"runtime-java-interop-$mode.json").writeText(result.toString(2))
    }
    @Test fun bunPublicJavaAndAndroidObjects()=exercise("bun")
    @Test fun nodePublicJavaAndAndroidObjects()=exercise("node")
    @Test fun workerPublicJavaAndAndroidObjects()=exercise("worker")
    @Test fun cjsEsmAndNestedWorkersReleaseOnlyTheirOwnHandles()=fixture {root->
        val result=harness.execute("node","""
            const java=require('java'),{Worker}=require('node:worker_threads');
            const esmModule=await import('autojs6:java');eq(typeof esmModule.new,'function','reserved named export');eq(await esmModule.default.callStatic('java.lang.Math','max',[1,2]),2,'main ESM');
            const parent=await java.new('android.graphics.Point',[7,8]);
            const body="const own=await java.new('android.graphics.Rect',[1,2,10,20]);parentPort.postMessage(own);setInterval(()=>{},1000);";
            const cjs="const {parentPort}=require('node:worker_threads'),java=require('java');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import java from 'autojs6:java';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads');const w=new Worker("+JSON.stringify(cjs)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
            try {for(const [entry,options]of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]) {
                const w=new Worker(entry,options);const handle=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});
                eq(await java.call(handle,'width'),9,'cloneable handle');await w.terminate();await pause(300);
                await rejects(()=>java.call(handle,'width'));eq(await parent.getField('x'),7,'parent survives');
            }}finally{await parent.release();}return {workerModes:3,parentPreserved:true};
        """.trimIndent(),capabilities=listOf("java_interop"),workingDirectory=root.path)
        assertEquals(3,result.getInt("workerModes"))
    }
}
