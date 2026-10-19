package org.autojs.autojs.engine

/** Quiet hardware checks shared by installed-host and genuinely packaged runtime tests. */
internal object RuntimeSensorPrograms {
    fun exercise() = """
        const available=await sensors.getAvailableSensors();eq(Object.isFrozen(available),true,'Frozen inventory');
        if(!available.some(x=>x.type==='accelerometer'))throw Error('Expected a physical accelerometer');
        const samples=[],unavailable=[];
        for(const info of available){
          let sample;
          try{sample=await sensors.once(info.type,{samplingIntervalMs:50,drainIntervalMs:25,timeoutMs:5000});}catch(error){
            // Redmi lists a camera-backed ALS that its vendor SensorService rejects for third-party apps.
            if(info.type!=='light'||info.name!=='camera_light_Sensor'||info.vendor!=='Xiaomi'||!['ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED','NOT_GRANTED'].includes(error.code))throw error;
            unavailable.push({type:info.type,name:info.name,code:error.code,message:error.message});continue;
          }
          if(sample.type!==info.type||sample.timestamp<=0||!sample.values.length||sample.values.some(x=>!Number.isFinite(x)))throw Error('Invalid physical sample');
          eq(Object.isFrozen(sample.values),true,'Frozen values');samples.push(sample);
        }
        const sub=sensors.subscribe('accelerometer',undefined,{samplingIntervalMs:20,drainIntervalMs:25,maxQueueSize:2});
        let events=0;const first=new Promise((resolve,reject)=>{sub.on('event',()=>{if(++events===3)resolve();});sub.on('error',reject);});
        await sub.ready;let guard;try{await Promise.race([first,new Promise((_,reject)=>guard=setTimeout(()=>reject(Error('No continuous samples')),5000))]);}finally{clearTimeout(guard);await sub.close();}
        const atClose=events;await new Promise(r=>setTimeout(r,100));eq(events,atClose,'No callbacks after close');await sub.close();
        const c=new AbortController(),aborted=sensors.subscribe('accelerometer',undefined,{signal:c.signal});await aborted.ready;
        const closed=new Promise(r=>aborted.once('close',r));c.abort();await closed;eq(aborted.closed,true,'Active subscription abort');
        const early=sensors.subscribe('accelerometer');await early.close();eq(early.closed,true,'Immediate close');
        const pre=new AbortController();pre.abort();eq((await rejects(()=>sensors.once('accelerometer',{signal:pre.signal}))).name,'AbortError','Pre-abort');
        await rejects(()=>sensors.once('pressure'));
        const four=[];for(let i=0;i<4;i++){const s=sensors.subscribe('accelerometer');await s.ready;four.push(s);}
        const overflow=sensors.subscribe('accelerometer');await rejects(()=>overflow.ready);await overflow.close();for(const s of four)await s.close();
        return {available,samples,unavailable,events,closed:true,abort:true,limit:true};
    """.trimIndent()
}
