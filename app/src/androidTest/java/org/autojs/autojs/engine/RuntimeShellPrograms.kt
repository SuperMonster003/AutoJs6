package org.autojs.autojs.engine

internal object RuntimeShellPrograms {
    fun exercise(remote:Boolean=false)="""
        const options={shizuku:$remote,timeoutMs:5000};
        const access=await shell.getAccess();eq(access.shell,true,'Shell available');
        const run=await shell.exec('printf out; printf err >&2; exit 7',options);eq(run.code,7,'Exit code '+run.stderr);eq(run.stdout,'out','Stdout');eq(run.stderr,'err','Stderr');
        const env=await shell.exec('printf "%s" "${'$'}OWNED"; pwd',{...options,cwd:'/system',env:{OWNED:"b a'r"}});eq(env.stdout,"b a'r/system\n",'Host cwd/environment');
        const args=await shell.execFile('/system/bin/printf',['%s','literal; 中文'],options);eq(args.stdout,'literal; 中文','Literal execFile args');
        const uid=Number((await shell.exec('id -u',options)).stdout.trim());eq(uid,$remote?2000:access.uid,'Actual process UID');
        const capture=await shell.exec('printf 123456789',{...options,maxOutputBytes:3});eq(capture.stdout,'123','Capture bound');eq(capture.truncated,true,'Truncation indicator');
        const lines=[],errors=[],lineProcess=shell.spawn("printf 'alpha\\nbeta'; printf 'err\\n' >&2",options);
        lineProcess.on('output',line=>lines.push(line));lineProcess.on('errorOutput',line=>errors.push(line));await lineProcess.waitFor();eq(lines,['alpha','beta'],'Line event tail');eq(errors,['err'],'Error line');
        const binary=shell.open({...options,cmd:'cat'}),chunks=[];binary.stdout.on('data',chunk=>chunks.push(chunk));binary.stderr.resume();await binary.ready;
        const bytes=Buffer.alloc(70000);for(let i=0;i<bytes.length;i++)bytes[i]=i<35000?255:i%256;
        await binary.write(bytes);await binary.closeInput();eq(await binary.waitFor(),0,'Binary process exit');eq(Buffer.concat(chunks).equals(bytes),true,'Binary stdin/stdout');
        const stream=shell.spawn('echo first; sleep 0.35; echo last',options),at=[];stream.on('output',()=>at.push(Date.now()));stream.on('errorOutput',()=>{});await stream.waitFor();eq(at.length,2,'Streaming lines');if(at[1]-at[0]<200)throw Error('Output arrived only after exit');
        const pressure=shell.spawn('head -c 262144 /dev/zero',options);await pressure.ready;await new Promise(r=>setTimeout(r,120));
        const readable=pressure.stdout;if(readable.readableLength>readable.readableHighWaterMark+32768)throw Error('Unbounded client stream queue');
        let pressureBytes=0;readable.on('data',chunk=>pressureBytes+=chunk.length);pressure.stderr.resume();await pressure.waitFor();eq(pressureBytes,262144,'Delayed stream consumer');
        const tty=await shell.exec('test -t 1 && printf tty',{...options,pty:true});eq(tty.code,0,'PTY descriptor');eq(tty.stdout,'tty','PTY output');
        const ptyInput=shell.open({...options,cmd:'cat',pty:true});await ptyInput.ready;await ptyInput.writeLine('terminal EOF');eq(await ptyInput.readLine(),'terminal EOF','PTY input');await ptyInput.closeInput();eq(await ptyInput.waitFor(),0,'PTY EOF');
        const slow=shell.spawn("trap '' TERM; while true; do sleep 1; done",{shizuku:$remote});await slow.ready;eq(await slow.waitFor(40),null,'Wait timeout does not kill');eq(await slow.close(),true,'Close running process');eq(slow.closed,true,'Closed state');
        const timed=await shell.exec("trap '' TERM; while true; do sleep 1; done",{...options,timeoutMs:150});eq(timed.code,124,'Command timeout');eq(timed.timedOut,true,'Timeout status');
        const abort=new AbortController(),cancelled=shell.spawn('sleep 20',{shizuku:$remote,signal:abort.signal});await cancelled.ready;abort.abort();eq((await rejects(()=>cancelled.exited)).code,'ABORT_ERR','Abort cleanup');
        const early=shell.spawn('sleep 20',{shizuku:$remote});await early.close();eq(early.closed,true,'Close pending creation');
        await rejects(()=>shell.exec('true',{env:{BAD:3}}));
        return {uid,hostUid:access.uid,remote:$remote,binaryBytes:bytes.length,streamDelay:at[1]-at[0],pty:true,timeout:true,abort:true,closed:true};
    """.trimIndent()
}
