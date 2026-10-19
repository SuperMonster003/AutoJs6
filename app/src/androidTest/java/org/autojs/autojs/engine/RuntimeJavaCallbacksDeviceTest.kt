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

class RuntimeJavaCallbacksDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private fun fixture(action:(File)->Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val old=if(prefs.contains(Grants.JAVA_INTEROP))prefs.getBoolean(Grants.JAVA_INTEROP,false)else null
        prefs.edit().putBoolean(Grants.JAVA_INTEROP,true).commit()
        try {harness.withFixture(setup={it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}) {
            val root=File(context.cacheDir,"java-callbacks-${UUID.randomUUID()}").canonicalFile.apply{mkdirs()}
            try {for(name in listOf("callback.jar","fixture.dex"))File(context.getExternalFilesDir(null),"java-callback-fixtures/$name").copyTo(File(root,name));action(root)}
            finally {check(root.parentFile==context.cacheDir.canonicalFile);root.deleteRecursively()}
        }} finally {prefs.edit().apply{if(old==null)remove(Grants.JAVA_INTEROP)else putBoolean(Grants.JAVA_INTEROP,old)}.commit()}
    }
    private fun source(mode:String,program:String):String=when(mode) {
            "bun"->"const java=host.java;$program"
            "node"->"const java=require('autojs6:java');$program"
            else->{
                val child="const {parentPort}=require('node:worker_threads'),java=require('java');const eq=(a,b,label)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label);};(async()=>{$program})().then(v=>parentPort.postMessage(v)).catch(e=>{throw e;});"
                "const {Worker}=require('node:worker_threads');const w=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}"
            }
        }

    private fun exercise(mode:String)=fixture {root->
        val program=RuntimeJavaCallbacksPrograms.body()
        val script=source(mode,program)
        val result=harness.execute(if(mode=="bun")"bun" else "node",script,capabilities=listOf("java_interop"),workingDirectory=root.path)
        assertTrue(result.getBoolean("callbacks"));File(context.getExternalFilesDir(null),"runtime-java-callbacks-$mode.json").writeText(result.toString(2))
    }
    @Test fun bunCallbacksSubclassJarAndDex()=exercise("bun")
    @Test fun nodeCallbacksSubclassJarAndDex()=exercise("node")
    @Test fun workerCallbacksSubclassJarAndDex()=exercise("worker")
    @Test fun esmAndNestedWorkersKeepCallbacksInTheirOwnerAndReleaseThemOnExit()=fixture {root->
        val result=harness.execute("node","""
            const java=require('java'),{Worker}=require('node:worker_threads');
            const parent=await java.proxy('java.util.concurrent.Callable',()=> 'parent');
            const body="const own=await java.proxy('java.util.concurrent.Callable',async()=> 'child:'+await java.callStatic('java.lang.Math','max',[1,9]));parentPort.postMessage(own);setInterval(()=>{},1000);";
            const cjs="const {parentPort}=require('node:worker_threads'),java=require('java');(async()=>{"+body+"})().catch(e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import java from 'autojs6:java';"+body;
            const nested="const {parentPort,Worker}=require('node:worker_threads');const child=new Worker("+JSON.stringify(cjs)+",{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e;});";
            try {for(const [entry,options]of [[cjs,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}],[nested,{eval:true}]]) {
                const w=new Worker(entry,options);let retained;
                try {
                    const proxy=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});
                    retained=await java.retain(proxy);eq(await java.call(retained,'call'),'child:9','callback routed to owner');
                    await w.terminate();await pause(200);await rejects(()=>java.call(retained,'call'));
                    eq(await parent.call('call'),'parent','parent callback survives');
                }finally{await w.terminate();if(retained)await retained.release();}
            }}finally{await parent.release();await java.closeCallbacks();}
            return {workerModes:3,parentPreserved:true};
        """.trimIndent(),capabilities=listOf("java_interop"),workingDirectory=root.path)
        assertEquals(3,result.getInt("workerModes"))
    }

    @Test fun snapshotsAvoidUserCodeAndNestedSuperCallbacksReturn()=fixture {root->
        val proofs=org.json.JSONArray()
        for(mode in listOf("node","bun","worker")) {
            val program="""
                let strings=0,hashes=0;
                const type=await java.defineClass({methods:{
                    hashCode(){hashes++;return 42;},
                    toString:async function(){strings++;return 'wrapped:'+await this.callSuper();}
                }},{callbackTimeoutMs:15000});
                const object=await type.new();
                try {
                    eq(strings,0,'snapshot must not call toString');eq(hashes,0,'snapshot must not call hashCode');
                    const text=await object.call('toString',[],{timeoutMs:20000});
                    eq(text,'wrapped:'+object.className+'@2a','nested superclass callback');eq(strings,1,'explicit toString');eq(hashes,1,'nested hashCode');
                    return {strings,hashes,nested:true};
                }finally{await object.release();await type.release();await java.closeCallbacks();}
            """.trimIndent()
            val result=harness.execute(if(mode=="bun")"bun" else "node",source(mode,program),capabilities=listOf("java_interop"),workingDirectory=root.path)
            assertTrue(result.getBoolean("nested"));proofs.put(JSONObject().put("mode",mode).put("result",result))
        }
        File(context.getExternalFilesDir(null),"runtime-java-callbacks-snapshot.json").writeText(proofs.toString(2))
    }

}
