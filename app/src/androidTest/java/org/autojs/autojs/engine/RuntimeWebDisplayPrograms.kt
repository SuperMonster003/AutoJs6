package org.autojs.autojs.engine

internal object RuntimeWebDisplayPrograms {
    fun waiters()="""
        async function wait(fn,label){const end=Date.now()+30000;while(!await fn()){if(Date.now()>end)throw Error(label);await new Promise(r=>setTimeout(r,60));}}
        async function background(){await fetch(base+'/background-ready');await wait(async()=>await web.evaluateJavascript('document.hidden')===true,'Page background');await new Promise(r=>setTimeout(r,4500));}
        async function foreground(){await fetch(base+'/background-finished');await wait(async()=>await web.evaluateJavascript('document.hidden')===false,'Page foreground');}
    """.trimIndent()

    fun audio()="""
        await web.setWebChromeClient({onPermissionRequest:e=>({resources:e.resources,requestAndroidPermissions:true,backgroundCapture:true})});
        try {
          const nativeDisplay=await web.evaluateJavascript('typeof navigator.mediaDevices.getDisplayMedia');
          await web.evaluateJavascriptAsync(`(async()=>{
            window.stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false}});
            window.chunks=[];window.recorder=new MediaRecorder(stream,{mimeType:'audio/webm;codecs=opus'});
            recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data);};window.stopped=new Promise(r=>recorder.onstop=r);recorder.start(250);return true;
          })()`);
          await background();
          const live=await web.evaluateJavascript('({hidden:document.hidden,chunks:chunks.length,live:stream.getTracks().every(t=>!t.muted&&t.readyState==="live")})');
          if(!live.hidden||!live.live||live.chunks<2)throw Error('Background audio '+JSON.stringify(live));
          await foreground();
          const result=await web.evaluateJavascriptAsync(`(async()=>{recorder.stop();await stopped;stream.getTracks().forEach(t=>t.stop());const blob=new Blob(chunks,{type:recorder.mimeType});const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',await blob.arrayBuffer())),b=>b.toString(16).padStart(2,'0')).join('');await fetch('/upload',{method:'POST',body:blob});return {size:blob.size,sha256:hash};})()`);
          await web.stopMediaCapture();return {...result,live,nativeDisplay};
        }finally {await web.stopMediaCapture().catch(()=>{});}
    """.trimIndent()

    fun display(label:String)="""
        const nativeDisplay=await web.evaluateJavascript('typeof navigator.mediaDevices.getDisplayMedia');
        const registration=await web.enableDisplayCapture({origin:base,maxWidth:320,maxHeight:240,frameRate:5,timeoutMs:45000});
        try {
          let gesture=false;try{await web.evaluateJavascriptAsync('navigator.mediaDevices.getDisplayMedia({video:true})');}catch(e){gesture=String(e).includes('InvalidStateError');}if(!gesture)throw Error('Missing user activation accepted');
          await web.evaluateJavascript(`document.body.innerHTML='<button id="share">Share $label</button>';window.displayReady=false;
            document.getElementById('share').onclick=async()=>{try{window.stream=await navigator.mediaDevices.getDisplayMedia({video:true,audio:true});window.chunks=[];
            if(!(stream instanceof MediaStream)||stream.getAudioTracks().length)throw Error('Display stream shape');
            window.recorder=new MediaRecorder(stream,{mimeType:'video/webm;codecs=vp8',videoBitsPerSecond:500000});
            recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data);};window.stopped=new Promise(r=>recorder.onstop=r);recorder.start(250);window.displayReady=true;
            }catch(e){window.displayError=String(e);}};true`);
          await wait(async()=>{const error=await web.evaluateJavascript('window.displayError');if(error)throw Error(error);return await web.evaluateJavascript('window.displayReady')===true;},'Display permission/frame');
          await background();
          const live=await web.evaluateJavascript('({hidden:document.hidden,chunks:chunks.length,live:stream.getTracks().every(t=>t.readyState==="live")})');
          if(!live.hidden||!live.live||live.chunks<3)throw Error('Background display '+JSON.stringify(live));
          await foreground();
          const result=await web.evaluateJavascriptAsync(`(async()=>{recorder.stop();await stopped;stream.getTracks().forEach(t=>t.stop());const blob=new Blob(chunks,{type:recorder.mimeType});const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',await blob.arrayBuffer())),b=>b.toString(16).padStart(2,'0')).join('');await fetch('/upload',{method:'POST',body:blob});return {size:blob.size,sha256:hash,chunks:chunks.length};})()`);
          await web.disableDisplayCapture(registration.id);return {...result,live,nativeDisplay};
        }finally{await web.disableDisplayCapture(registration.id).catch(()=>{});}
    """.trimIndent()

    fun camera()="""
        try {
          const started=await web.startBackgroundRecording({audio:true,width:640,height:480,frameRate:15});if(started.state!=='recording')throw Error('Camera startup');
          await background();const live=await web.getBackgroundRecording();if(live.state!=='recording')throw Error('Background camera '+JSON.stringify(live));
          await foreground();const result=await web.stopBackgroundRecording();if(result.durationMs<4500||result.size<1000||!result.uri)throw Error('Camera output');
          const pieces=[];let offset=0;while(offset<result.size){const bytes=await web.readDocument(result.uri,offset);if(!bytes.length)throw Error('Truncated camera file');pieces.push(bytes);offset+=bytes.length;}
          const bytes=new Uint8Array(offset);let position=0;for(const piece of pieces){bytes.set(piece,position);position+=piece.length;}
          const {createHash}=await import('node:crypto');result.sha256=createHash('sha256').update(bytes).digest('hex');
          await fetch(base+'/upload',{method:'POST',body:bytes});return {...result,live};
        }finally{await web.cancelBackgroundRecording().catch(()=>{});}
    """.trimIndent()
}
