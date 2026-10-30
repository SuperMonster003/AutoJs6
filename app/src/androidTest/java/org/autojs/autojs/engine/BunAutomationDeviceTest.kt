package org.autojs.autojs.engine

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BunAutomationDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val keys=listOf(Grants.APP_CONTROL,Grants.ACCESSIBILITY_CONTROL,Grants.ACCESSIBILITY_GESTURE,Grants.CLIPBOARD_CONTROL)
    private val rows=JSONArray()
    internal fun grants(enabled:List<String> = keys, action:()->Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val before=keys.associateWith { if(prefs.contains(it))prefs.getBoolean(it,false)else null }
        prefs.edit().apply { keys.forEach { putBoolean(it,it in enabled) } }.commit()
        try{action()}finally{prefs.edit().apply{before.forEach{(key,value)->if(value==null)remove(key)else putBoolean(key,value)}}.commit()}
    }
    internal fun run(mode:String,body:String):JSONObject {
        val imports=if(mode=="bun")"const app=host.app,clipboard=host.clipboard,a=host.accessibility;" else "const app=require('app'),clipboard=require('clipboard'),a=require('accessibility');"
        return harness.execute(mode,imports+body,capabilities=listOf("app","app.launch","app.settings","app.activity","app.query","clipboard","accessibility","accessibility.gesture"))
            .also{rows.put(JSONObject().put("mode",mode).put("result",it))}
    }
    @Test fun applicationAndClipboardMatchNode()=grants {harness.withFixture {
        val clipboard=context.getSystemService(ClipboardManager::class.java)
        var previous:android.content.ClipData?=null
        instrumentation.runOnMainSync { previous=clipboard.primaryClip }
        try { for(mode in listOf("node","bun"))run(mode,BunAutomationPrograms.appAndClipboard(context.packageName)) }
        finally { instrumentation.runOnMainSync { previous?.let(clipboard::setPrimaryClip)?:clipboard.clearPrimaryClip() } }
        write("app-clipboard")
    }}
    @Test fun accessibilityAndIntentsReachAnotherUid()=grants {
      for(mode in listOf("node","bun")) harness.withFixture {
        // Instrumentation restarts its target process. Rebind the already-authorized service in this run.
        val component=context.packageName+"/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        val configured=android.provider.Settings.Secure.getString(context.contentResolver,"enabled_accessibility_services").orEmpty().split(':').filter{it.isNotBlank()&&it!="null"}
        assertTrue("Enable the host Android accessibility service before running this test",component in configured)
        fun setServices(services:List<String>) {
            val value=services.joinToString(":");require(value.matches(Regex("[A-Za-z0-9_./:$]*")))
            val command=if(value.isEmpty())"settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $value"
            android.os.ParcelFileDescriptor.AutoCloseInputStream(harness.automation.executeShellCommand(command)).use{it.readBytes()}
            val actual=android.provider.Settings.Secure.getString(context.contentResolver,"enabled_accessibility_services").orEmpty()
                .split(':').mapNotNull(android.content.ComponentName::unflattenFromString)
            if(component in services)assertTrue(android.content.ComponentName.unflattenFromString(component) in actual)
        }
        if(!AccessibilityService.registry.isOperational){setServices(configured-component);android.os.SystemClock.sleep(750);setServices(configured)}
        assertTrue("Host Android accessibility service state: ${AccessibilityService.registry.state}",AccessibilityService.waitForOperational(30000))
        assertNotEquals(context.applicationInfo.uid,instrumentation.context.applicationInfo.uid)
        run(mode,BunAutomationPrograms.accessibility(instrumentation.context.packageName,mode=="bun"))
      }
        write("accessibility")
    }
    @Test fun independentGrantsAndRawRequestsRejectEscapes() {
        grants(emptyList()) { run("bun","""
            for(const [cap,fn] of [['app.control',()=>app.isInstalled('org.autojs.autojs6')],['clipboard.control',()=>clipboard.getText()],['accessibility.control',()=>a.isEnabled()]]){
              eq((await rejects(fn)).code,'NOT_GRANTED',cap);
              const response=await fetch('http://autojs6/v1/'+cap,{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',body:JSON.stringify({action:'start',method:'isEnabled',args:[]})});eq(response.status,403,'Raw grant');
            }return {denied:true};
        """.trimIndent()) }
        grants(keys-Grants.ACCESSIBILITY_GESTURE) { run("bun","""
            eq((await rejects(()=>a.swipe(1,1,2,2,100))).code,'NOT_GRANTED','Gesture grant');
            async function raw(cap,body){const response=await fetch('http://autojs6/v1/'+cap,{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',body:JSON.stringify(body)});return response.status;}
            eq(await raw('accessibility.control',{action:'start',method:'gesture',args:[100,[[1,1],[2,2]]]}),403,'Raw gesture');
            eq(await raw('accessibility.control',{action:'start',method:'inspectNode',args:[]}),400,'Internal diagnostics');
            eq(await raw('app.control',{action:'start',method:'readDoc',args:['runtimeApis']}),400,'Internal app route');
            eq(await raw('app.control',{action:'start',method:3,args:[]}),400,'Invalid method type');
            eq(await raw('clipboard.control',{action:'start',method:'getText',args:'invalid'}),400,'Invalid argument list');
            eq(await raw('clipboard.control',{action:'poll',id:'foreign-task'}),400,'Foreign task');
            return {gestureDenied:true,internalRoutesDenied:true};
        """.trimIndent()) }
        write("denials")
    }
    internal fun write(name:String){File(context.getExternalFilesDir(null),"bun-automation-$name.json").writeText(rows.toString(2))}
}

/** Explicit unavailable-service gate; it must not be confused with successful UI action coverage. */
class BunAutomationUnavailableDeviceTest {
    @Test fun unavailableServiceRejectsActionsInsteadOfPretendingSuccess() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("expectedServiceState")=="unavailable")
        val test=BunAutomationDeviceTest()
        test.grants {CaptureWorkerBridgeDeviceTest().withFixture {
            assertFalse("This gate requires a non-operational service",AccessibilityService.registry.isOperational)
            for(mode in listOf("node","bun"))test.run(mode,"""
                eq(await a.isEnabled(),false,'Unavailable service state');
                const error=await rejects(()=>a.findOne(a.text('Owned unavailable fixture')));
                if(!String(error.message).includes('enabled accessibility capability provider'))throw error;
                ${if(mode=="bun")"eq(error.code,'HOST_UNAVAILABLE','Bun unavailable code');" else ""}
                return {isEnabled:false,errorCode:error.code};
            """.trimIndent())
            test.write("unavailable")
        }}
    }
}
