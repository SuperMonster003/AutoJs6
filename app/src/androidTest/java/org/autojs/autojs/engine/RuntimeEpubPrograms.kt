package org.autojs.autojs.engine

/** Programs shared by host and independently signed packaged-application tests. */
internal object RuntimeEpubPrograms {
    fun body(): String = """
        const check=(value,label)=>{if(!value)throw Error(label);};
        const same=(a,b,label)=>check(JSON.stringify(a)===JSON.stringify(b),label);
        const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
        const context=await java.getContext();let packageName,directory;
        try{packageName=await context.call('getPackageName');const dir=await context.call('getExternalFilesDir',[null]);
            try{directory=await dir.call('getAbsolutePath');}finally{await dir.release();}}
        finally{await context.release();}
        if(typeof expectedPackage!=='undefined')same(packageName,expectedPackage,'application context');
        const prefix=directory+'/runtime-epub-'+Date.now()+'-'+Math.random().toString(16).slice(2);
        const source=prefix+'.epub',coverPath=prefix+'.png',resourcePath=prefix+'.xhtml';
        async function write(path,bytes){
            const stream=await java.new('java.io.FileOutputStream',[path]);
            try{for(let offset=0;offset<bytes.length;offset+=8192){
                const part=bytes.subarray(offset,offset+8192),array=await java.newArray('byte',new Int8Array(part.buffer,part.byteOffset,part.byteLength));
                try{await stream.call('write',[array],{parameterTypes:['byte[]']});}finally{await array.release();}
            }}finally{await stream.call('close');await stream.release();}
        }
        async function remove(path){const file=await java.new('java.io.File',[path]);try{if(await file.call('exists'))check(await file.call('delete'),'file cleanup');}finally{await file.release();}}
        async function rejects(action,code){try{await action();}catch(error){check(error instanceof epub.EpubError,'EpubError identity');same(error.code,code,String(error));return code;}throw Error('Expected '+code);}
        async function wait(label,condition){const end=Date.now()+20000;while(!condition()){if(Date.now()>end)throw Error('Timeout: '+label);await pause(40);}}
        let book,reader,summary;
        try{
            await write(source,bookBytes);
            check(await epub.isAvailableAsync(),'EPUB plugin available');
            book=await epub.openAsync(source);
            same(book.metadata.title,'Runtime EPUB fixture','metadata title');
            same(book.readingOrder.length,3,'reading order');
            check(book.toc.length>=3&&book.positions>0&&!book.isClosed,'book snapshot');
            const first=await book.text(0),whole=await book.textAll({maxChars:1000000});
            check(first.includes('lighthouse keeper'),'first chapter content');
            same((whole.match(/Portable EPUB /g)||[]).length,10000,'long Unicode result');
            same((whole.match(/\u96ea\u{1f4d6}/gu)||[]).length,10000,'Unicode scalar round trip');
            check((await book.textAsync(book.readingOrder[0],{format:'markdown'})).includes('# Chapter 1'),'Markdown');
            same(await epub.metadata(source),book.metadata,'convenience metadata');
            same(await epub.tocAsync(source),book.toc,'convenience toc');
            same(await epub.readingOrder(source),book.readingOrder,'convenience spine');
            same(await epub.textAsync(source,0),first,'convenience text');
            const cover=await book.cover();
            check(cover instanceof Uint8Array&&cover.length>8,'portable cover bytes');
            same([...cover.subarray(0,8)],[137,80,78,71,13,10,26,10],'PNG signature');
            same(await epub.coverAsync(source,coverPath),coverPath,'cover export');
            await rejects(()=>book.cover(coverPath),'INVALID_ARGUMENT');
            same(await book.coverAsync(coverPath,{overwrite:true}),coverPath,'overwrite export');
            same(await book.resourceAsync(book.readingOrder[0].href,resourcePath),resourcePath,'resource export');
            const file=await java.new('java.io.File',[resourcePath]);
            try{check(await file.call('length')>100,'resource bytes');}finally{await file.release();}
            const hits=await book.search('lighthouse',{limit:10});check(hits.length>0&&hits[0].locator.href,'search locator');
            check((await epub.searchAsync(source,'lighthouse')).length>0,'convenience search');
            same(await book.annotationsAsync(),[],'owned fixture annotations');
            same(await epub.annotations(source),[],'convenience annotations');
            await rejects(()=>epub.open('content://invalid/book'),'INVALID_ARGUMENT');
            await rejects(()=>book.search(''),'INVALID_ARGUMENT');
            await rejects(()=>book.text('not-in-book.xhtml'),'RESOURCE_NOT_FOUND');
            const controller=new AbortController();controller.abort();
            await rejects(()=>epub.open(source,{signal:controller.signal}),'CANCELLED');
            const events=[];
            reader=await epub.readAsync(source,{href:book.readingOrder[0].href});
            reader.on('open',value=>events.push(['open',value.title])).on('progress',value=>{
                check('totalProgression' in value&&'chapterTitle' in value,'progress field names');
                events.push(['progress',value.href]);
            });
            await wait('reader open',()=>reader.isOpen);
            same(reader.title,'Runtime EPUB fixture','reader title');
            await reader.nextChapter();
            await wait('next chapter',()=>reader.href&&reader.href.endsWith('chapter2.xhtml'));
            await reader.prevChapter();
            await wait('previous chapter',()=>reader.href&&reader.href.endsWith('chapter1.xhtml'));
            await reader.next();await reader.prev();
            await reader.goTo(book.readingOrder[1].href);
            await wait('goTo',()=>reader.href&&reader.href.endsWith('chapter2.xhtml'));
            check(reader.progress>=0&&reader.progress<=1,'reader progress');
            await reader.setPreferences({fontSize:1.1});
            same(await reader.bookmarks(),[],'owned fixture bookmarks');
            const closed=new Promise(resolve=>reader.once('close',resolve));
            await reader.close({finish:true});same((await closed).reason,'host','reader close');
            check(reader.isClosed&&!reader.isOpen,'closed state');
            await reader.close();check(events.some(event=>event[0]==='progress'),'real progress events');
            const title=book.metadata.title;await book.close();check(book.isClosed,'book close');
            await rejects(()=>book.text(),'SESSION_CLOSED');
            summary={packageName,title,readingOrder:3,unicodeMarkers:10000,textCharacters:whole.length,coverBytes:cover.length,
                searchHits:hits.length,readerEvents:events.length,readerClosed:reader.isClosed,errors:true,quiet:true};
        }finally{
            try{if(reader&&!reader.isClosed)await reader.close({finish:true});if(book&&!book.isClosed)await book.close();}
            finally{try{await epub.close();}finally{for(const path of [source,coverPath,resourcePath])await remove(path);}}
        }
        return summary;
    """.trimIndent()
}
