package org.autojs.autojs.engine

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BunDatabaseDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private fun grant(enabled:Boolean=true,action:()->Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE);val key=Grants.DATABASE_CONTROL
        val before=if(prefs.contains(key))prefs.getBoolean(key,false)else null;prefs.edit().putBoolean(key,enabled).commit()
        try{action()}finally{prefs.edit().apply{if(before==null)remove(key)else putBoolean(key,before)}.commit()}
    }
    private fun run(mode:String,root:File,body:String)=harness.execute(mode,
        (if(mode=="bun")"const database=host.database,sqlite=host.sqlite;" else "const database=require('database'),sqlite=require('sqlite');")+body,
        capabilities=listOf("database"),workingDirectory=root.path)

    @Test fun nodeAndBunShareTypedSqlTransactionsAndPersistentData()=grant {
        val root=File(context.cacheDir,"bun-database-${UUID.randomUUID()}").apply{mkdirs()};val other=File(root,"other").apply{mkdirs()};val rows=JSONArray()
        try {
            rows.put(run("node",root,RuntimeDatabasePrograms.exercise("owned")))
            rows.put(run("bun",root,"const saved=await sqlite('owned');eq(await saved.get('SELECT COUNT(*) AS n FROM items'),{n:3},'Node to Bun');await saved.close();"+RuntimeDatabasePrograms.exercise("owned")))
            rows.put(run("node",root,"const db=await database.open('owned');eq(await db.get('SELECT score FROM items WHERE name=?',['alpha']),{score:3},'Bun to Node');await db.close();return {crossRuntime:true};"))
            rows.put(run("bun",other,"const db=await sqlite('owned');eq(await db.get('SELECT name FROM sqlite_master WHERE name=?',['items']),null,'Directory isolation');await db.close();return {isolated:true};"))
            File(context.getExternalFilesDir(null),"bun-database-host.json").writeText(rows.toString(2))
        }finally{RuntimeDatabasePrograms.cleanup(context,other);RuntimeDatabasePrograms.cleanup(context,root);root.deleteRecursively()}
    }
    @Test fun unfinishedManualTransactionRollsBackOnExecutionExitAndHandlesStayScoped()=grant {
        val root=File(context.cacheDir,"bun-database-lifecycle-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            for(mode in listOf("node","bun")) {
                val first=run(mode,root,"""
                    const db=await database.open('lifecycle');await db.exec('CREATE TABLE IF NOT EXISTS state(value INTEGER)');await db.exec('DELETE FROM state');
                    await db.exec('BEGIN IMMEDIATE');await db.run('INSERT INTO state VALUES(?)',[7]);return {id:db.id};
                """.trimIndent())
                run("bun",root,"""
                    const db=await sqlite('lifecycle');eq(await db.get('SELECT COUNT(*) AS n FROM state'),{n:0},'Exit rollback');await db.close();
                    async function raw(method,args){const id=(await host.call('database.control',{action:'start',method,args})).value.id;while(true){const r=(await host.call('database.control',{action:'poll',id})).value;if(r.done)return r.value;}}
                    eq(await raw('close',[${JSONObject.quote(first.getString("id"))}]),false,'Foreign execution handle');
                    const all=[];for(let i=0;i<4;i++)all.push(await database.open('owned-limit-'+i));
                    eq((await rejects(()=>database.open('owned-overflow'))).code,'QUOTA_EXCEEDED','Four handle budget');
                    for(const handle of all)await handle.close();return {rollback:true,scoped:true};
                """.trimIndent())
            }
        }finally{RuntimeDatabasePrograms.cleanup(context,root);root.deleteRecursively()}
    }
    @Test fun absentGrantAndRawMethodsCannotBypassTheDatabaseContract() {
        val root=File(context.cacheDir,"bun-database-denied-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            grant(false){run("bun",root,"""
                eq((await rejects(()=>database.open('owned'))).code,'NOT_GRANTED','Missing grant');
                const r=await fetch('http://autojs6/v1/database.control',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',body:JSON.stringify({action:'start',method:'open',args:[{name:'owned'}]})});eq(r.status,403,'Raw missing grant');return {denied:true};
            """.trimIndent())}
            grant{run("bun",root,"""
                for(const body of [{method:'loadExtension',args:['owned']},{method:3,args:[]},{method:'open',args:'owned'}]){
                  const r=await fetch('http://autojs6/v1/database.control',{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',body:JSON.stringify(body)});eq(r.status,400,'Invalid raw request');
                }return {invalid:true};
            """.trimIndent())}
        }finally{RuntimeDatabasePrograms.cleanup(context,root);root.deleteRecursively()}
    }
    @Test fun nodeWorkerCjsEsmAndNestedFacadesReleaseDatabasesOnExit() {
        val root=File(context.cacheDir,"database-worker-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val body="""
                (async()=>{
                  const eq=(a,b,label)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(label+': '+JSON.stringify(a));};
                  const rejects=async fn=>{try{await fn();}catch(e){return e;}throw Error('Expected rejection');};
                  const result=await(async()=>{${RuntimeDatabasePrograms.exercise("worker")}})();
                  const orphan=await database.open('worker');await orphan.exec('BEGIN IMMEDIATE');await orphan.run('INSERT INTO items(name,score) VALUES(?,?)',['orphan',9]);
                  parentPort.postMessage(result);
                })().catch(e=>{throw e});
            """.trimIndent()
            val cjs="const {parentPort}=require('node:worker_threads'),database=require('autojs6:database'),sqlite=require('sqlite');$body"
            val esm="import {parentPort} from 'node:worker_threads';import database from 'database';import sqlite from 'autojs6:sqlite';$body"
            val nested="const {Worker,parentPort}=require('node:worker_threads');const child=new Worker(${JSONObject.quote(cjs)},{eval:true});child.on('message',v=>parentPort.postMessage(v));child.on('error',e=>{throw e});"
            val result=run("node",root,"""
                const {Worker}=require('node:worker_threads');const results=[];
                for(const [source,esm] of [[${JSONObject.quote(cjs)},false],[${JSONObject.quote(esm)},true],[${JSONObject.quote(nested)},false]]) {
                  let value;await new Promise((resolve,reject)=>{const w=new Worker(esm?new URL('data:text/javascript,'+encodeURIComponent(source)):source,{eval:!esm});w.on('message',x=>value=x);w.on('error',reject);w.on('exit',code=>code===0?resolve():reject(Error('Worker exit '+code)));});
                  const db=await sqlite('worker');eq(await db.get('SELECT COUNT(*) AS n FROM items WHERE name=?',['orphan']),{n:0},'Worker exit rollback');await db.close();results.push(value);
                }
                const handles=[];for(let i=0;i<4;i++)handles.push(await sqlite('after-worker-'+i));for(const h of handles)await h.close();
                return {cjs:results[0],esm:results[1],nested:results[2],handlesReleased:true};
            """.trimIndent())
            File(context.getExternalFilesDir(null),"bun-database-worker.json").writeText(result.toString(2))
        }finally{RuntimeDatabasePrograms.cleanup(context,root);root.deleteRecursively()}
    }
    @Test fun databaseCloseCannotReleaseAnImageResource() {
        val provider=AndroidNodeBridgeCapabilityProvider(context,
            NodeBridgeEngineInfo.fromContext(context,id="database-resource-${UUID.randomUUID()}",cwd=context.cacheDir.path),
            permissionManifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("database","image")))
        fun call(module:String,method:String,args:JSONArray):Any {
            val future=java.util.concurrent.CompletableFuture<NodeBridgeResponse>()
            provider.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),module,method,args.toString(),5000,
                permissions=NodeBridgePermissionManifest.requiredCapabilitiesFor(module,method))){future.complete(it)}
            val response=future.get(5,java.util.concurrent.TimeUnit.SECONDS);assertTrue(response.toString(),response.ok)
            return org.json.JSONTokener(response.resultJson).nextValue()
        }
        val bitmap=android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.RED)}
        try {
            val bytes=java.io.ByteArrayOutputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
            val image=call("image","fromBase64",JSONArray().put(android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP))) as JSONObject
            assertEquals(false,call("database","close",JSONArray().put(image.getString("id"))))
            assertEquals(android.graphics.Color.RED,(call("image","pixel",JSONArray().put(image).put(0).put(0)) as Number).toInt())
        }finally{bitmap.recycle();provider.destroy()}
    }
    @Test fun cancelledUnclaimedBunOpensReleaseTheirNativeHandles() {
        val root=File(context.cacheDir,"database-cancel-${UUID.randomUUID()}").apply{mkdirs()}
        val provider=AndroidNodeBridgeCapabilityProvider(context,NodeBridgeEngineInfo.fromContext(context,id="database-cancel",cwd=root.path),
            permissionManifest=NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("database")))
        val bridge=BunAutomationBridge(context,listOf(Grants.DATABASE_CONTROL)){provider}
        fun awaitCount(expected:String) {
            val deadline=android.os.SystemClock.elapsedRealtime()+5000
            while(provider.nativeDiagnostics()["embedded_script.database_active_handle_count"]!=expected && android.os.SystemClock.elapsedRealtime()<deadline)android.os.SystemClock.sleep(10)
            assertEquals(expected,provider.nativeDiagnostics()["embedded_script.database_active_handle_count"])
        }
        try {
            repeat(6) { index ->
                val started=bridge.invoke(Grants.DATABASE_CONTROL,JSONObject().put("action","start").put("method","open").put("args",JSONArray().put(JSONObject().put("name","owned-$index"))))
                assertTrue(started.toString(),started.ok);val id=JSONObject(started.resultJson).getString("id")
                awaitCount("1")
                assertTrue(bridge.invoke(Grants.DATABASE_CONTROL,JSONObject().put("action","cancel").put("id",id)).ok)
                awaitCount("0")
            }
        }finally{bridge.close();provider.destroy();RuntimeDatabasePrograms.cleanup(context,root);root.deleteRecursively()}
    }
}
