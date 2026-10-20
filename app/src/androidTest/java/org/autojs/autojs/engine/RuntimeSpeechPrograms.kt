package org.autojs.autojs.engine

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object RuntimeSpeechPrograms {
    fun body() : String {
        val audio = InstrumentationRegistry.getInstrumentation().context.assets.open("runtime-speech/en.wav").use { it.readBytes() }
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("model.onnx"))
                zip.write("Owned import-management fixture; not an inference model.".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        return """
            const wav=Buffer.from(${JSONObject.quote(Base64.encodeToString(audio,Base64.NO_WRAP))},'base64');
            const archive=Buffer.from(${JSONObject.quote(Base64.encodeToString(archive,Base64.NO_WRAP))},'base64');
            const check=(value,label)=>{if(!value)throw Error(label);};
            const same=(a,b,label)=>check(JSON.stringify(a)===JSON.stringify(b),label);
            const context=await java.getContext();let packageName,directory;
            try {packageName=await context.call('getPackageName');const file=await context.call('getExternalFilesDir',[null]);
                try{directory=await file.call('getAbsolutePath');}finally{await file.release();}}
            finally{await context.release();}
            const prefix=directory+'/runtime-speech-'+Date.now()+'-'+Math.random().toString(16).slice(2);
            const audioPath=prefix+'.wav',archivePath=prefix+'.zip',output=prefix+'-out.wav';
            const importedId='runtime-speech-test-'+Date.now()+'-'+Math.random().toString(16).slice(2);
            async function write(path,bytes) {
                const stream=await java.new('java.io.FileOutputStream',[path]);
                try {for(let i=0;i<bytes.length;i+=8192){const part=bytes.subarray(i,i+8192),array=await java.newArray('byte',new Int8Array(part.buffer,part.byteOffset,part.byteLength));
                    try{await stream.call('write',[array],{parameterTypes:['byte[]']});}finally{await array.release();}}}
                finally{await stream.call('close');await stream.release();}
            }
            async function remove(path){const file=await java.new('java.io.File',[path]);try{if(await file.call('exists'))check(await file.call('delete'),'file cleanup');}finally{await file.release();}}
            async function rejects(action,code){try{await action();}catch(error){check(error instanceof speech.SpeechError,'SpeechError identity');same(error.code,code,'error code '+String(error));return error.code;}throw Error('Expected '+code);}
            const models=await speech.models.list(),installed=models.filter(m=>m.installed);
            const before=installed.map(m=>[m.id,m.version,m.isDefault]).sort();
            const caps=await speech.capabilities();check(await speech.isAvailable(),'speech unavailable');
            const asr=installed.find(m=>m.id==='asr.zipformer-streaming.en.2023-06-26.int8')||installed.find(m=>m.id==='asr.zipformer-streaming.zh-en.2023-02-20.int8');
            const vad=installed.find(m=>m.kind==='vad'&&m.license.commercialUse),tts=installed.find(m=>m.kind==='tts');
            check(asr&&vad&&tts,'Installed ASR, VAD and TTS fixtures are required');
            const originalAsr=await speech.models.getDefault('asr'),progress=[],trace=[];
            const normalize=text=>text.toLowerCase().replace(/[^a-z]/g,'');
            function recognition(result,label) {
                check(result.text.length>20&&result.durationMillis>5000&&result.segments.length>0,label+' result');
                check(normalize(result.text).includes('yellowlamps'),'recognition content '+label);
                return {characters:result.text.length,durationMillis:result.durationMillis,segments:result.segments.length,modelId:result.modelId};
            }
            const event=(session,name)=>new Promise((resolve,reject)=>{session.once(name,resolve);session.once('error',reject);});
            let summary;
            try {
                await write(audioPath,wav);await write(archivePath,archive);
                const options={model:asr.id,vad:false};
                const file=recognition(await speech.recognize(audioPath,options),'file');
                const pcm=wav.subarray(44);
                const fromPcm=recognition(await speech.recognizeAsync(new Int16Array(pcm.buffer,pcm.byteOffset,pcm.byteLength/2),options),'PCM');
                const detected=await speech.detectVoice(pcm,{model:vad.id});
                check(detected.durationMillis>5000&&detected.segments.length>0,'VAD');
                const session=await speech.listen({...options,source:'external',timeoutMillis:0});
                const ready=event(session,'ready'),ended=event(session,'end');
                for(const name of ['ready','partial','final','endpoint','end'])session.on(name,()=>trace.push(name));
                await ready;
                for(let i=0;i<pcm.length;i+=64000)await session.feed(pcm.subarray(i,i+64000));
                await session.stop();const final=await ended;
                same(final.reason,'stopped','session reason');check(session.isEnded&&!session.isActive(),'terminal state');
                check(normalize(session.result()).includes('yellowlamps'),'external recognition');
                check(trace.includes('final')&&trace[0]==='ready'&&trace.at(-1)==='end','session event ordering');
                const cancelled=await speech.listen({...options,source:'external',timeoutMillis:0});
                const cancelReady=event(cancelled,'ready'),cancelEnd=event(cancelled,'end');await cancelReady;await cancelled.cancel();
                same((await cancelEnd).reason,'cancelled','session cancellation');
                const imported=await speech.models.importFile(archivePath,{id:importedId,kind:'asr',modelType:'online-transducer',languages:['en'],name:'Runtime speech import test',onProgress:(done,total)=>progress.push([done,total])});
                same(imported.id,importedId,'import identity');same(imported.source,'imported','import source');
                check(imported.installed&&progress.length>0,'import progress');
                same((await speech.models.setDefault('asr',importedId)).id,importedId,'set default');
                same((await speech.models.getDefault('asr')).id,importedId,'get default');
                if(originalAsr)await speech.models.setDefault('asr',originalAsr.id);
                check(await speech.models.remove(importedId),'remove imported model');
                same((await speech.models.install(asr.id)).id,asr.id,'installed-model install');
                same((await speech.models.installAsync(asr.id)).id,asr.id,'install alias');
                const missing=await rejects(()=>speech.models.install('runtime-speech-no-such-model'),'MODEL_MISSING');
                await rejects(()=>speech.recognize(pcm,{model:asr.id,sampleRate:1}),'INVALID_OPTIONS');
                await rejects(()=>speech.recognize(archivePath,options),'UNSUPPORTED_FORMAT');
                const synthesis=await speech.synthesize('你好',output,{model:tts.id});
                check(synthesis.sizeBytes>44&&synthesis.durationMillis>0,'plugin synthesis');
                const read=await java.new('java.io.RandomAccessFile',[output,'r']);
                try{same(await read.call('readInt'),0x52494646,'WAV RIFF');await read.call('seek',[8]);same(await read.call('readInt'),0x57415645,'WAV WAVE');}finally{await read.call('close');await read.release();}
                const spoken=await speech.speak('你好',{model:tts.id,volume:0,usage:'media',timeout:20000});
                same(spoken.status,'done','muted speech');
                summary={packageName,plugin: caps.packageName,pluginVersion:caps.versionCode,model:asr.id,catalogCount:models.length,
                    file,pcm:fromPcm,vadSegments:detected.segments.length,externalCharacters:session.result().length,trace,
                    modelImport:true,progress:progress.length,missing,synthesisBytes:synthesis.sizeBytes,mutedSpeech:true,quiet:true};
            }finally {
                try {
                    if(originalAsr)await speech.models.setDefault('asr',originalAsr.id);
                    if((await speech.models.list({installed:true})).some(m=>m.id===importedId))await speech.models.remove(importedId);
                    const after=(await speech.models.list({installed:true})).map(m=>[m.id,m.version,m.isDefault]).sort();
                    same(after,before,'pre-existing models/defaults restored');
                } finally {
                    try {for(const path of [audioPath,archivePath,output])await remove(path);}
                    finally {await speech.close();}
                }
            }
            return summary;
        """.trimIndent()
    }
}
