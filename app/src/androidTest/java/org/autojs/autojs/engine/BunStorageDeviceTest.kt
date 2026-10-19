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

internal object BunStoragePrograms {
    val files = """
        eq(files.join('/a','/b','../c/'),'/a/c/','Join');eq(files.getName('/a/b.txt/'),'b.txt','Name');eq(files.getExtension('a.tar.gz'),'gz','Extension');
        eq(await files.path(null),null,'Null path');eq(await files.read(null),'','Null read');eq(await files.exists(null),false,'Null exists');
        eq(await files.listDir(null),[],'Null list');eq(await files.remove(null),false,'Null remove');
        eq(await files.ensureDir('owned/child/data.txt'),true,'Parents');eq(await files.isFile('owned/child/data.txt'),false,'No implicit file');
        const text='Owned \u4e2d\u6587 \ud83d\ude00';
        eq(await files.write('owned/child/data.txt',text),undefined,'Write');await files.append('owned/child/data.txt',' appended');
        eq(await files.read('owned/child/data.txt'),text+' appended','Read');
        for(const encoding of ['utf8','utf16le','latin1','ascii','hex','base64','base64url']){
          const input=encoding==='hex'?'00ff4142':encoding.startsWith('base64')?'AP9BQg==':text;
          await files.write('owned/encoded',input,encoding);eq(await files.read('owned/encoded',encoding),Buffer.from(input,encoding).toString(encoding),'Encoding '+encoding);
        }
        await files.write('owned/encoded',null);await files.append('owned/encoded',123);eq(await files.read('owned/encoded'),'123','Coercion');
        eq(await files.copy('owned/child/data.txt','copy/deep/data.txt'),true,'Copy parents');eq(await files.move('copy/deep/data.txt','copy/deep/moved.txt'),true,'Move');
        eq(await files.move('copy/deep/moved.txt','missing/new.txt'),false,'Missing move parent');
        eq(await files.copy('missing','copy.txt'),false,'Missing copy');eq(await files.isDir('copy/deep'),true,'Directory');eq(await files.isFile('copy/deep/moved.txt'),true,'File');
        eq(await files.listDir('copy/deep',name=>name.endsWith('.txt')),['moved.txt'],'Filter');eq(await files.listDir('copy/deep',()=>1),[],'Strict filter');
        await rejects(()=>files.remove('copy'));await rejects(()=>files.read('missing'));await rejects(()=>files.write(null,'x'));await rejects(()=>files.open('owned/encoded'));await rejects(()=>files.toFile('owned/encoded'));
        eq(await files.remove('copy/deep/moved.txt'),true,'Remove');eq(await files.remove('copy/deep/moved.txt'),false,'Missing remove');
        const absolute=await files.path('owned/child/../child/data.txt');eq(await files.read('file://'+absolute),text+' appended','File URL');
        return {files:true,encodings:7,hostFile:absolute};
    """.trimIndent()

    fun storage(name: String) = """
        const store=storage.create(${JSONObject.quote(name)}),alias=storages.open(${JSONObject.quote(name)});
        await store.clear();eq(await store.get('missing'),undefined,'Missing');eq(await store.get('missing',false),false,'Default');
        const values={nil:null,no:false,zero:0,text:'\u4e2d\u6587',nested:{items:[1,null,true]}};
        for(const [key,value] of Object.entries(values))await store.putSync(key,value);
        for(const [key,value] of Object.entries(values))eq(await alias.get(key,'fallback'),value,'Stored '+key);
        eq(await store.contains('zero'),true,'Contains');await store.removeSync('zero');eq(await store.contains('zero'),false,'Removed');
        const keys=await store.keys();eq(Object.isFrozen(keys),true,'Frozen keys');eq(keys,['nested','nil','no','text'],'Keys');
        eq(store.putSync,store.put,'Async putSync alias');eq(store.clearSync,store.clear,'Async clearSync alias');
        await rejects(()=>storage.create(''));await rejects(()=>store.put('bad',undefined));await rejects(()=>store.put('bad',1n));
        await rejects(()=>storages.names());await rejects(()=>storage.all());
        const abort=new AbortController();abort.abort(new Error('owned abort'));await rejects(()=>store.get('text',null,{signal:abort.signal}));
        await alias.selfRemoveSync();eq(await store.keys(),[],'Self remove');await store.put('durable',{written:true});
        return {storage:true};
    """.trimIndent()
}

class BunStorageDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val harness=CaptureWorkerBridgeDeviceTest()
    private val grants=listOf(Grants.FILES_CONTROL,Grants.STORAGE_CONTROL)
    private fun withGrants(enabled:List<String> = grants, action:()->Unit) {
        val prefs=context.getSharedPreferences("bun_host_capability_grants",Context.MODE_PRIVATE)
        val before=grants.associateWith{if(prefs.contains(it))prefs.getBoolean(it,false)else null}
        prefs.edit().apply{grants.forEach{putBoolean(it,it in enabled)}}.commit()
        try{action()}finally{prefs.edit().apply{before.forEach{(k,v)->if(v==null)remove(k)else putBoolean(k,v)}}.commit()}
    }
    private fun run(mode:String,root:File,body:String)=harness.execute(mode,
        (if(mode=="bun")"const files=host.files,storage=host.storage,storages=host.storages;" else "const files=require('files'),storage=require('storage'),storages=require('storages');")+body,
        capabilities=listOf("storage"),workingDirectory=root.path)

    @Test fun filesAndStorageMatchNodeAndSurviveFreshExecutions()=withGrants {
        val root=File(context.cacheDir,"bun-storage-${UUID.randomUUID()}").apply{mkdirs()};val name="owned-${UUID.randomUUID()}";val rows=JSONArray()
        try {
            for(mode in listOf("node","bun")) {
                val work=File(root,mode).apply{mkdirs()}
                rows.put(JSONObject().put("mode",mode).put("files",run(mode,work,BunStoragePrograms.files)))
            }
            assertEquals("Owned \u4e2d\u6587 \ud83d\ude00 appended",File(root,"bun/owned/child/data.txt").readText())
            rows.put(run("node",root,BunStoragePrograms.storage(name)))
            rows.put(run("bun",root,"eq(await storage.open('$name').get('durable'),{written:true},'Node to Bun persistence');"+BunStoragePrograms.storage(name)))
            rows.put(run("node",root,"eq(await storage.open('$name').get('durable'),{written:true},'Bun to Node persistence');return {crossRuntime:true};"))
            val other=File(root,"other").apply{mkdirs()}
            rows.put(run("bun",other,"eq(await storage.open('$name').get('durable'),undefined,'Working directory isolation');return {isolated:true};"))
            File(context.getExternalFilesDir(null),"bun-storage-host.json").writeText(rows.toString(2))
        }finally {
            run("bun",root,"await storage.remove('$name');return {removed:true};")
            check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()
        }
    }
    @Test fun independentGrantsAndRawCallsRejectUnsupportedOperations() {
        val root=File(context.cacheDir,"bun-storage-denials-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            for(enabled in listOf(emptyList(),listOf(Grants.FILES_CONTROL),listOf(Grants.STORAGE_CONTROL)))withGrants(enabled) {
                run("bun",root,"""
                    for(const [cap,fn] of [['files.control',()=>files.read('owned')],['storage.control',()=>storage.open('owned').get('key')]])if(!host.hasCapability(cap))eq((await rejects(fn)).code,'NOT_GRANTED','Independent grant');
                    const cap=${JSONObject.quote(enabled.firstOrNull()?:"files.control")};
                    const raw=async body=>fetch('http://autojs6/v1/'+cap,{unix:process.env.AUTOJS6_HOST_BRIDGE_SOCKET,method:'POST',body:JSON.stringify(body)});
                    const expected=host.hasCapability(cap)?400:403;
                    eq((await raw({method:'open',args:['owned']})).status,expected,'No raw Java handles');
                    if(host.hasCapability(cap)){eq((await raw({method:3,args:[]})).status,400,'Method type');eq((await raw({method:'read',args:'x'})).status,400,'Argument type');}
                    return {denied:true};
                """.trimIndent())
            }
        }finally{root.deleteRecursively()}
    }
    @Test fun oversizedFileRequestsRejectBeforeChangingOutput()=withGrants {
        val root=File(context.cacheDir,"bun-storage-limits-${UUID.randomUUID()}").apply{mkdirs()}
        File(root,"large").writeBytes(ByteArray(BunFilesBridge.MAX_READ_BYTES+1));File(root,"kept").writeText("original")
        try{run("bun",root,"""
            eq((await rejects(()=>files.read('large'))).code,'PAYLOAD_TOO_LARGE','Large read');
            eq((await rejects(()=>files.write('kept','x'.repeat(70000)))).code,'PAYLOAD_TOO_LARGE','Large write');
            eq(await files.read('kept'),'original','No partial write');return {limits:true};
        """.trimIndent())}finally{root.deleteRecursively()}
    }
}
