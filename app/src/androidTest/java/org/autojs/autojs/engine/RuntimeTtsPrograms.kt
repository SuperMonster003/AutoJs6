package org.autojs.autojs.engine

import org.json.JSONObject

internal object RuntimeTtsPrograms {
    fun body(relativeDirectory: String? = null) = """
        const relativeDirectory=${relativeDirectory?.let { JSONObject.quote(it) } ?: "null"};
        const context=await java.getContext();const packageName=await context.call('getPackageName');let folder;
        try {folder=await context.call('getExternalFilesDir',[null]);}finally{await context.release();}
        let directory;try{directory=await folder.call('getAbsolutePath');}finally{await folder.release();}if(relativeDirectory)directory=relativeDirectory;
        const errors=[],engines=await tts.engines();if(!engines.length)throw Error('No Android TTS engine is installed');
        let selected,voice,output,task,events=[];
        for(const engine of engines) {
            try {
                const voices=await tts.voices({engine:engine.name,offlineOnly:true,installedOnly:true});
                if(!voices.length){errors.push({engine:engine.name,message:'No installed offline voice'});continue;}
                voice=voices.find(v=>v.locale.startsWith('zh'))||voices.find(v=>v.locale.startsWith('en'))||voices[0];
                await tts.configure({engine:engine.name,voice:voice.name,volume:0,usage:'media',timeout:20000});
                output=directory+'/runtime-tts-'+Date.now()+'-'+Math.random().toString(16).slice(2)+'.wav';
                task=await tts.synthesis('你好 AutoJs6',relativeDirectory?output.slice(directory.length+1):output,{audioEvents:true});
                for(const name of ['queued','start','synthesis','audio','done'])task.on(name,value=>{
                    if(name==='audio'&&!(value.data instanceof Uint8Array))throw Error('Audio event bytes are not typed');
                    events.push(name);
                });
                const result=await task.result;if(result.path!==output||result.status!=='done')throw Error('Synthesis result');
                selected=engine.name;break;
            }catch(error){errors.push({engine:engine.name,message:String(error)});await tts.reset();}
        }
        if(!selected)throw Error('Offline TTS synthesis failed: '+JSON.stringify(errors));
        try {
            const file=await java.new('java.io.File',[output]);let length;
            try{length=Number(await file.call('length'));}finally{await file.release();}
            if(length<=44)throw Error('Synthesis file is empty');
            const stream=await java.new('java.io.RandomAccessFile',[output,'r']);
            try{eq(await stream.call('readInt'),0x52494646,'RIFF header');await stream.call('seek',[8]);eq(await stream.call('readInt'),0x57415645,'WAVE header');}
            finally{await stream.call('close');await stream.release();}
            if(!task.isDone||task.state!=='done'||!events.includes('done'))throw Error('Terminal event/state missing');
            const ready=await tts.ready();eq(ready.engine,selected,'selected engine');
            if(!(await tts.languages()).length)throw Error('No languages');
            if(!await tts.isLanguageAvailable(voice.locale))throw Error('Selected language unavailable');
            if(!(await tts.languageAvailability(voice.locale)).available)throw Error('Language details');
            eq(tts.QUEUE_FLUSH,'flush','queue constant');eq(tts.QUEUE_ADD,'add','add constant');
            eq(tts.getMaxInputLength(),tts.maxInputLength,'input limit');
            eq((await tts.config()).volume,0,'muted options');
            const spoken=await tts.enqueue('AutoJs6',{volume:0});eq(spoken.status,'done','muted speech');
            const silent=await tts.silenceTask(10000);await silent.stop();
            let cancelled=false;try{await silent.result;}catch(error){cancelled=error.code==='ERR_AUTOJS6_TTS_ABORTED';}
            if(!cancelled)throw Error('Stopped task did not reject');
            await tts.reset();eq((await tts.defaults()).engine,null,'reset clears engine');eq((await tts.defaults()).volume,1,'reset clears volume');await tts.ready(selected);if(await tts.isSpeaking()||await tts.isBusy())throw Error('TTS remained busy after reset');
            const status=await tts.status();await tts.stopAll();
            return {packageName,engine:selected,voice:voice.name,locale:voice.locale,length,path:output,events,errors,quiet:true,file:true,cancellation:true,status:status.state};
        }finally{await tts.close();}
    """.trimIndent()
}
