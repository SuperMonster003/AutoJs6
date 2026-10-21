package org.autojs.autojs.engine

/** Synthetic loopback mail operations shared by host and independently signed application tests. */
internal object RuntimeMailPrograms {
    fun body(): String = """
        const check=(value,label)=>{if(!value)throw Error(label);};
        const same=(a,b,label)=>check(JSON.stringify(a)===JSON.stringify(b),label);
        const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
        const context=await java.getContext();let packageName,directory;
        try{packageName=await context.call('getPackageName');const dir=await context.call('getExternalFilesDir',[null]);
            try{directory=await dir.call('getAbsolutePath');}finally{await dir.release();}}
        finally{await context.release();}
        if(typeof expectedPackage!=='undefined')same(packageName,expectedPackage,'application context');
        const tag='RuntimeMail'+Date.now()+Math.random().toString(16).slice(2);
        const prefix=directory+'/runtime-mail-'+tag,attachmentPath=prefix+'.bin',rawPath=prefix+'.eml',uploadPath=prefix+'-upload.bin';
        async function remove(path){const file=await java.new('java.io.File',[path]);try{if(await file.call('exists'))check(await file.call('delete'),'file cleanup');}finally{await file.release();}}
        async function length(path){const file=await java.new('java.io.File',[path]);try{return await file.call('length');}finally{await file.release();}}
        async function hash(path){
            const file=await java.new('java.io.File',[path]),owned=[];
            try{
                const nio=await file.call('toPath');owned.push(nio);
                const digest=await java.callStatic('java.security.MessageDigest','getInstance',['SHA-256']);owned.push(digest);
                const sink=await java.new('java.io.ByteArrayOutputStream');owned.push(sink);
                const output=await java.new('java.security.DigestOutputStream',[sink,digest]);owned.push(output);
                try{await java.callStatic('java.nio.file.Files','copy',[nio,output],{parameterTypes:['java.nio.file.Path','java.io.OutputStream']});}
                finally{await output.call('close');}
                const value=await digest.call('digest');owned.push(value);
                const encoded=await java.callStatic('android.util.Base64','encodeToString',[value,2],{parameterTypes:['byte[]','int']});
                return Buffer.from(encoded,'base64').toString('hex');
            }finally{for(const value of owned.reverse())await value?.release?.();await file.release();}
        }
        async function write(path){
            const stream=await java.new('java.io.FileOutputStream',[path]);
            const bytes=await java.newArray('byte',new Int8Array([1,2,3,4,5,6,7,8]));
            try{await stream.call('write',[bytes],{parameterTypes:['byte[]']});}
            finally{await bytes.release();await stream.call('close');await stream.release();}
        }
        async function rejects(action,code){
            try{await action();}catch(error){check(error instanceof mail.MailError,'MailError identity');
                if(code)same(error.code,code,String(error));return error.code;}throw Error('Expected mail error '+code);
        }
        async function until(label,condition){const deadline=Date.now()+20000;while(!await condition()){if(Date.now()>deadline)throw Error('Timeout: '+label);await pause(100);}}
        const endpoint=port=>({host:'127.0.0.1',port,tls:'none'});
        const options={address:'runtime@example.invalid',password:'runtime-mail-synthetic',
            imap:endpoint(fixture.imapPort),smtp:endpoint(fixture.smtpPort),timeout:{connect:5000,read:15000,write:15000},debug:true};
        let client,pop,watch,summary,folderCreated=false,renamedCreated=false;
        const folderName=tag,renamedName=tag+'Renamed';
        try{
            check((await mail.providers.list()).length>=5,'real provider presets');
            check((await mail.providers.resolve('test@qq.com')).id==='qq','preset resolution');
            check((await mail.providers.get('qq')).id==='qq','preset lookup');
            let savedBoundary;
            if(packageName==='org.autojs.autojs6'){
                check(await mail.accounts.has('runtime-mail-fixture'),'fixture saved alias');
                const alias=await mail.connectAsync('runtime-mail-fixture');
                try{same(alias.account.alias,'runtime-mail-fixture','alias snapshot');check((await alias.test()).ok,'alias network');}
                finally{await alias.close();}
                savedBoundary='signed-host';
            }else{
                await rejects(()=>mail.accounts.list(),'PLUGIN_UNAVAILABLE');
                await rejects(()=>mail.connect('runtime-mail-fixture'),'PLUGIN_UNAVAILABLE');
                savedBoundary='inline-only';
            }
            await rejects(()=>mail.fetch(),'NO_DEFAULT_ACCOUNT');
            client=await mail.setDefault(options);
            check(client===mail.default&&client.isConnected&&!client.isClosed,'client/default state');
            check(!JSON.stringify(client.account).includes('runtime-mail-synthetic'),'credential redaction');
            const tested=await client.testAsync();check(tested.ok&&tested.imap.ok&&tested.smtp.ok,'IMAP/SMTP login');
            check(!JSON.stringify(tested.account).includes('runtime-mail-synthetic'),'test credential redaction');
            const folders=await mail.foldersAsync({status:true});check(folders.some(value=>value.path==='INBOX'),'folders');
            check((await client.folderStatus('INBOX')).messages>=1,'folder status');
            const hits=await mail.searchAsync({subject:'Runtime mail fixture'},{limit:5,fallback:'always'});
            check(hits.length>0&&hits.fallback==='client','search fallback result');
            const message=await hits[0].loadAsync({includeRaw:false});
            same(message.subject,'Runtime mail fixture','message subject');
            check(message.date instanceof Date,'message date');
            check(String(message.from).includes('sender@example.invalid'),'address formatter');
            same((message.text.match(/Mail /g)||[]).length,fixture.markerCount,'long body fragments');
            check(message.text.includes(String.fromCodePoint(0x96ea,0x1f4e7)),'Unicode body');
            check(message.attachments.length===1,'attachment metadata');
            const attachment=message.attachments[0],progress=[];
            same(await attachment.downloadAsync(attachmentPath,{onProgress:(done,total)=>progress.push([done,total])}),attachmentPath,'attachment output');
            same(await length(attachmentPath),fixture.attachmentBytes,'attachment bytes');
            same(await hash(attachmentPath),fixture.attachmentSha256,'attachment SHA-256');
            check(progress.length>0&&progress.at(-1)[0]===fixture.attachmentBytes,'download progress');
            same(await mail.rawAsync(message,rawPath),rawPath,'RFC822 output');
            check(await length(rawPath)>fixture.attachmentBytes,'raw MIME bytes');
            same((await client.fetchBodyAsync(message)).uid,message.uid,'fetchBody alias');
            check((await client.fetchAsync({limit:2})).length>0,'fetch');
            const folder=client.folder(folderName);await folder.createAsync();folderCreated=true;
            same((await folder.status()).path,folderName,'bound folder status');
            await folder.renameAsync(renamedName);folderCreated=false;renamedCreated=true;
            const uid=await client.appendAsync(renamedName,{to:'runtime@example.invalid',subject:tag+' draft',text:'owned fixture'},['seen']);
            check(uid!==null,'APPENDUID');
            const ref={uid,folder:renamedName};
            check((await client.markUnreadAsync(ref)).length===1,'mark unread');
            check((await client.markRead(ref)).length===1,'mark read');
            check((await mail.flagAsync(ref)).length===1,'flag');
            check((await client.unflag(ref)).length===1,'unflag');
            check((await client.setFlagsAsync(ref,['seen'],'set')).length===1,'set flags');
            const copied=await client.copyAsync(ref,'INBOX');check(copied===true||copied.length>0,'copy');
            const moved=await client.move(ref,'INBOX');check(moved===true||moved.length>0,'move');
            const own=await client.search({subject:tag+' draft'},{limit:10});
            check(own.length>=1,'copied messages');
            await client.deleteAsync(own,{expunge:false});
            check(await client.expungeAsync('INBOX')>=1,'expunge');
            await client.deleteFolder(renamedName);renamedCreated=false;
            watch=await client.watch({folder:'INBOX',mode:'idle',reconnect:true});
            check(watch.isActive&&watch.generation>0,'watch active');
            const delivered=[],watchErrors=[],readySubject=tag+' readiness';
            let watchReady=false,readinessProbes=0;
            watch.on('message',(value,owner)=>{check(owner===watch,'watch callback owner');if(value.subject===readySubject)watchReady=true;if(value.subject===tag)delivered.push(value);})
                .on('error',error=>watchErrors.push(error.code));
            // Handle creation starts an asynchronous connection; confirm its cursor before the one SMTP test message.
            await until('watch subscription baseline',async()=>{
                if(watchReady)return true;
                readinessProbes++;
                await client.append('INBOX',{to:'runtime@example.invalid',subject:readySubject,text:'synthetic readiness probe'});
                await pause(250);return watchReady;
            });
            const probes=await client.search({subject:readySubject},{limit:100,fallback:'always'});
            if(probes.length)await client.delete(probes,{expunge:true});
            same((await client.search({subject:readySubject},{limit:100,fallback:'always'})).length,0,'readiness probes removed before POP3');
            await write(uploadPath);
            const sent=await mail.sendAsync({to:'runtime@example.invalid',subject:tag,text:'runtime mail round trip',
                attachments:[{path:uploadPath,fileName:'upload.bin'}]},{saveToSent:false});
            check(sent.accepted.includes('runtime@example.invalid')&&sent.rejected.length===0,'SMTP accepted');
            await until('IDLE new message',()=>delivered.length>0);
            same(watchErrors,[],'watch errors');
            check((await delivered[0].load()).text.includes('runtime mail round trip'),'watch message load');
            const ended=new Promise(resolve=>watch.once('close',resolve));
            await watch.stop();same(await ended,'stopped','watch stop');check(watch.isClosed,'watch closed');
            pop=await mail.connect({...options,receive:'pop3',pop3:endpoint(fixture.pop3Port)});
            check((await pop.test()).ok,'POP3 login');
            const popMessages=await pop.fetch({limit:2});check(popMessages.length>0&&typeof popMessages[0].uid==='string','POP3 UIDL');
            check((await popMessages[0].load()).subject,'POP3 body');
            await rejects(()=>pop.createFolder('unsupported'),'UNSUPPORTED_OPERATION');
            await pop.close();pop=null;
            await rejects(()=>client.get(NaN),'INVALID_ARGUMENT');
            await rejects(()=>client.search('bad'),'INVALID_ARGUMENT');
            const abort=new AbortController();abort.abort();
            await rejects(()=>mail.connect(options,{signal:abort.signal}),'CANCELLED');
            await mail.close();check(client.isClosed&&mail.default===null,'default close');
            await rejects(()=>client.fetch(),'SESSION_CLOSED');
            summary={packageName,mailbox:'synthetic-loopback',bodyMarkers:fixture.markerCount,
                attachmentBytes:fixture.attachmentBytes,attachmentSha256:fixture.attachmentSha256,
                progressEvents:progress.length,watchEvents:delivered.length,watchClosed:watch.isClosed,watchReady,readinessProbes,
                protocols:['imap','smtp','pop3'],savedBoundary,credentialsRedacted:true,errors:true,quiet:true};
        }finally{
            try{
                if(watch&&!watch.isClosed)await watch.stop();
                if(client&&!client.isClosed){
                    if(folderCreated)await client.deleteFolder(folderName).catch(()=>{});
                    if(renamedCreated)await client.deleteFolder(renamedName).catch(()=>{});
                }
                if(pop&&!pop.isClosed)await pop.close();
            }finally{try{await mail.dispose();}finally{for(const path of [attachmentPath,rawPath,uploadPath])await remove(path);}}
        }
        return summary;
    """.trimIndent()
}
