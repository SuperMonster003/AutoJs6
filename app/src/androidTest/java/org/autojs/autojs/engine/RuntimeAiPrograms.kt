package org.autojs.autojs.engine

internal object RuntimeAiPrograms {
    val capabilities = listOf("ai", "ui")
    val grants = listOf("ai.control", "ui.layout")

    fun exercise(full: Boolean = true): String = """
        let checks=0;const check=(value,label)=>{checks++;if(!value)throw Error(label);};
        const stage=(name,promise)=>Promise.resolve(promise).catch(error=>{error.message=name+': '+error.message;throw error;});
        const catalog=await stage('catalog',ai.catalog());check(catalog.targets.length>0,'catalog targets');
        const local=catalog.targets.filter(item=>item.locality==='local'&&item.available);
        const target=local.find(item=>/E2B/i.test(item.displayName))||local[0];check(!!target,'available local model');
        check(await ai.hasAccess(),'AI access');check(await ai.requestAccess(),'existing access');
        const options={target:target.id,backend:'cpu',temperature:0,maxTokens:16,timeout:120000};
        const invalid=await ai.ask('x',{...options,maxTokens:-1}).then(()=>null,e=>e);
        check(invalid?.code==='INVALID_REQUEST','generation validation');
        const invalidCatalog=await ai.catalog({target:target.id}).then(()=>null,e=>e);
        check(invalidCatalog?.code==='INVALID_REQUEST','catalog has no target option');
        const aborted=new AbortController();aborted.abort();
        const early=await ai.ask('not dispatched',options,{signal:aborted.signal}).then(()=>null,e=>e);
        check(early?.code==='CANCELLED','pre-aborted generation');
        const prompt='Reply with only the word OK. Do not explain.';
        const chat=await stage('chat',ai.chat({messages:[{role:'user',content:prompt}],...options}));
        check(typeof chat.text==='string'&&typeof chat.reasoning==='string','chat strings');
        check(chat.route==='plugin'&&chat.target.id===target.id&&chat.model===target.model,'exact target');
        check(chat.usage.outputTokens>0,'real inference token usage');
        check(['stop','length'].includes(chat.finishReason),'finish reason');
        let chunks=0,delta='',reasoning='',opened=0,usage=0,done=0;
        const stream=ai.stream([{role:'user',content:prompt}],options);
        stream.once('open',()=>opened++).on('chunk',chunk=>{chunks++;reasoning+=chunk.reasoning;});
        stream.on('delta',text=>delta+=text).on('usage',()=>usage++).once('done',()=>done++);
        check(await stage('stream.ready',stream.ready)===stream,'stream ready');const streamed=await stage('stream.result',stream.result);
        check(opened===1&&done===1&&stream.isDone&&stream.state==='done','stream lifecycle');
        check(delta===streamed.text&&reasoning===streamed.reasoning,'chunk accumulation');
        check(streamed.usage.outputTokens>0&&usage>0,'stream usage');
        const session=await stage('session.open',ai.session({...options,system:'Keep replies brief.'}));
        try {
            check(session.target===target.id&&!session.isClosed&&session.state==='ready','session identity');
            const turn=await stage('session.chat',session.chat(prompt));check(turn.usage.outputTokens>0,'persistent native turn');
            ${if (full) """
            const second=session.stream('Reply OK again.');const result=await stage('session.stream',second.result);
            check(result.usage.outputTokens>0&&second.isDone,'next persistent streaming turn');
            """.trimIndent() else ""}
        }finally{await session.close();}
        check(session.isClosed&&session.state==='closed','session closed');await session.close();
        const closed=await session.ask('closed').then(()=>null,e=>e);check(closed?.code==='SESSION_CLOSED','closed session refuses turns');
        ${if (full) """
        const answer=await stage('ask',ai(prompt,options));check(typeof answer==='string','callable ask');
        let cancelled=0;const cancellation=ai.stream('Write a long list of words.',{...options,maxTokens:256});
        cancellation.once('open',()=>cancellation.cancel()).once('cancelled',()=>cancelled++);
        const error=await cancellation.result.then(()=>null,e=>e);
        check(error?.code==='CANCELLED'&&cancelled===1&&cancellation.state==='cancelled','stream cancellation');
        """.trimIndent() else ""}
        await ai.close();return {checks,target:target.id,model:target.model,chat:{text:chat.text,usage:chat.usage},
            stream:{chunks,opened,usage,done,text:streamed.text,outputTokens:streamed.usage.outputTokens},full:$full};
    """.trimIndent()
}
