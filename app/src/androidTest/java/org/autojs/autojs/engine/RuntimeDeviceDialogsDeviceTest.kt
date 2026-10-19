package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class RuntimeDeviceDialogsDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val controls=RuntimeWebMediaControls(harness)
    private val caps=listOf("device","device.power","media","media.audio","dialogs")
    private fun grants(action:()->Unit) {
        val keys=listOf(Grants.DEVICE_CONTROL,Grants.DEVICE_POWER,Grants.DEVICE_AUDIO,Grants.DIALOGS_CONTROL)
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val old=keys.associateWith{if(prefs.contains(it))prefs.getBoolean(it,false)else null}
        prefs.edit().apply{keys.forEach{putBoolean(it,true)}}.commit()
        try{action()}finally{prefs.edit().apply{old.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
    internal fun interact(future:Future<*>?=null) {
        controls.click("Owned continue",future);controls.click("Owned yes",future);controls.click("Owned later",future)
        controls.await("Owned input visible",15000){if(future?.isDone==true)fail(future.get().toString());controls.labelled("Owned input")!=null}
        controls.await("Owned input edit",15000){controls.find{it.className?.toString()=="android.widget.EditText"}?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"edited-123")})==true}
        controls.click("Owned input done",future);controls.click("Owned raw done",future);controls.click("Owned cancel",future)
        controls.click("Owned beta",future);controls.click("Owned second",future);controls.click("Owned single done",future)
        controls.click("Owned three",future);controls.click("Owned multi done",future)
    }
    private fun exercise(mode:String)=grants {harness.withFixture {
        val pool=Executors.newSingleThreadExecutor()
        try {
            val program=RuntimeDeviceDialogsPrograms.exercise()
            val body=when(mode){
                "bun"->"const device=host.device,dialogs=host.dialogs;$program"
                "node"->"const device=require('device'),dialogs=require('dialogs');$program"
                else->{
                    val child="const {parentPort}=require('node:worker_threads'),device=require('autojs6:device'),dialogs=require('autojs6:dialogs');const pause=ms=>new Promise(r=>setTimeout(r,ms)),eq=(a,b,l)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(l);};(async()=>{$program})().then(result=>parentPort.postMessage(result)).catch(e=>{throw e;});"
                    "const {Worker}=require('node:worker_threads');const w=new Worker(${JSONObject.quote(child)},{eval:true});try{return await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});}finally{await w.terminate();}"
                }
            }
            val future=pool.submit<JSONObject>{harness.execute(if(mode=="bun")"bun" else "node",body,capabilities=caps)}
            interact(future);val result=future.get(35,TimeUnit.SECONDS);assertEquals(12,result.getInt("dialogs"));assertTrue(result.getBoolean("quiet"))
            controls.await("Owned dialog cleanup",5000){NodeDialogActivity.sessions.isEmpty()}
            File(context.getExternalFilesDir(null),"runtime-device-dialogs-$mode.json").writeText(result.toString(2))
        }finally{pool.shutdownNow()}
    }}
    @Test fun bunDeviceAndDialogs()=exercise("bun")
    @Test fun nodeMainDeviceAndDialogs()=exercise("node")
    @Test fun nodeWorkerDeviceAndDialogs()=exercise("worker")
    @Test fun esmAndNestedWorkerExitReleaseOwnedWindowsAndLocks()=harness.withFixture {
        val result=harness.execute("node","""
            const {Worker}=require('node:worker_threads'),device=require('device'),dialogs=require('dialogs');
            const parent=await dialogs.progress.show({title:'Owned parent'});await device.keepScreenOn(60000);
            const code="const {parentPort}=require('node:worker_threads'),device=require('device'),dialogs=require('dialogs');(async()=>{await device.keepScreenOn(60000);await dialogs.progress.show({title:'Owned worker'});parentPort.postMessage(await device.getWidth());setInterval(()=>{},1000);})().catch(e=>{throw e;});";
            const nested="const {Worker,parentPort}=require('node:worker_threads');const w=new Worker("+JSON.stringify(code)+",{eval:true});w.on('message',v=>parentPort.postMessage(v));w.on('error',e=>{throw e;});";
            const esm="import {parentPort} from 'node:worker_threads';import device from 'autojs6:device';import dialogs from 'autojs6:dialogs';await device.keepScreenOn(60000);await dialogs.progress.show({title:'Owned worker'});parentPort.postMessage(await device.getWidth());setInterval(()=>{},1000);";
            try{for(const [entry,opts] of [[code,{eval:true}],[nested,{eval:true}],[new URL('data:text/javascript,'+encodeURIComponent(esm)),{}]]){
                const w=new Worker(entry,opts);const width=await new Promise((resolve,reject)=>{w.once('message',resolve);w.once('error',reject);});eq(width,await device.getWidth(),'worker width');await w.terminate();await pause(500);
                await parent.update({message:'Parent survives'});eq(parent.shown,true,'parent dialog');
            }}finally{await parent.dismiss();await device.cancelKeepingAwake();}
            return {modes:3,parentSurvived:true};
        """.trimIndent(),capabilities=caps)
        assertEquals(3,result.getInt("modes"));controls.await("Worker dialogs removed",5000){NodeDialogActivity.sessions.isEmpty()}
    }
    @Test fun bunMissingGrantsRejectDevicePowerAudioAndDialogs() {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val keys=listOf(Grants.DEVICE_CONTROL,Grants.DEVICE_POWER,Grants.DEVICE_AUDIO,Grants.DIALOGS_CONTROL)
        val old=keys.associateWith{if(prefs.contains(it))prefs.getBoolean(it,false)else null}
        try{
            prefs.edit().apply{keys.forEach{putBoolean(it,false)}}.commit()
            val denied=harness.execute("bun","""
                for(const call of [()=>host.device.getWidth(),()=>host.device.keepScreenOn(1000),()=>host.device.getMusicVolume(),()=>host.dialogs.confirm('Denied')])eq((await rejects(call)).code,'NOT_GRANTED','missing grant');
                return {denied:4};
            """.trimIndent());assertEquals(4,denied.getInt("denied"));assertTrue(NodeDialogActivity.sessions.isEmpty())
        }finally{prefs.edit().apply{old.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
}
