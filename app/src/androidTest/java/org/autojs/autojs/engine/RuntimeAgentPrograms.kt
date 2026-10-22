package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.util.UUID

internal object RuntimeAgentPrograms {
    val capabilities = listOf("ai.agent", "ui")
    val grants = listOf("agent.control", "ui.layout")
    private val testRunId by lazy {
        UUID.fromString(InstrumentationRegistry.getArguments().getString("runtimeAgentTestId")
            ?: UUID.randomUUID().toString()).toString()
    }
    val goalPrefix get() = "[runtime-ai-agent:$testRunId] "
    fun goal(text: String) = goalPrefix + text

    fun exercise(target: String, full: Boolean = true): String = """
        let checks=0;const check=(value,label)=>{checks++;if(!value)throw Error(label);};
        const agent=ai.agent,start=Date.now();
        check(await agent.hasAccess(),'application access');check(await agent.requestAccess(),'retained application access');
        check(typeof(await agent.status()).state==='string','link status');
        const presets=await agent.presets();check(Array.isArray(presets),'presets');
        check(Array.isArray(await agent.catalog('runtime-ai-agent-fixture')),'registered metadata');
        check(await agent.context()===null,'outside registered context');check(await agent.result({outside:true})===false,'outside result');
        const invalid=await agent.run('invalid',{budget:{maxSteps:201}}).then(()=>null,e=>e);
        check(invalid?.code==='INVALID_REQUEST','budget validation before admission');
        const aborted=new AbortController();aborted.abort();
        const early=await agent.run('never',{}, {signal:aborted.signal}).then(()=>null,e=>e);
        check(early?.code==='CANCELLED','pre-aborted admission');
        const options={target:${JSONObject.quote(target)},tools:[],memory:false,plan:false,context:'',locale:'en',interaction:'script',
            budget:{maxSteps:4,maxModelCalls:6,maxDurationMs:120000,maxTotalTokens:8192}};
        const assistant=agent.create(options);options.budget.maxSteps=99;
        check(assistant.options.budget.maxSteps===4,'assistant snapshot');
        const wider=await assistant.run('invalid',{budget:{maxSteps:5}}).then(()=>null,e=>e);
        check(wider?.code==='INVALID_REQUEST','assistant budget cannot widen');
        const run=await assistant.run(${JSONObject.quote(goal("Reply with the word OK as your greeting. The greeting is the entire requested deliverable. Include the produced greeting in done.evidence."))},{budget:{maxSteps:3}});
        check(typeof run.id==='string'&&run.goal.length>0&&run.startedAt>0&&!run.detached,'run metadata');
        let states=0,progress=0,steps=0,done=0,once=0;
        const removed=()=>{throw Error('Removed listener ran');};run.on('progress',removed).off('progress',removed);
        run.on('state',()=>states++).on('progress',()=>progress++).on('step',()=>steps++).once('done',()=>{done++;once++;});
        const observed=await agent.get(run.id);check(observed===run,'live get reuses facade');
        const result=await run.join(120000);
        check(result.status==='completed','local Agent completion: '+JSON.stringify({id:run.id,status:result.status,error:result.error,usage:result.usage}));
        check(typeof result.summary==='string'&&result.summary.length>0,'real final summary');
        check(Array.isArray(result.evidence)&&result.evidence.length>0,'native completion evidence');
        check(result.usage?.modelCalls>0,'real model call');
        check(done===1&&once===1&&run.state==='completed'&&run.error===null,'terminal lifecycle');
        check((await run.result).id===run.id,'shared result');
        const historical=await agent.get(run.id);check((await historical.result).status==='completed','historical result');
        const rows=await agent.list({since:start-1000,limit:50});check(rows.some(row=>row.id===run.id),'run history');
        const response=await run.respond('missing','answer').then(()=>null,e=>e);
        check(['SESSION_CLOSED','RUN_NOT_INTERACTIVE'].includes(response?.code),'stale input is refused');
        const confirmation=await run.confirm('missing',true).then(()=>null,e=>e);
        check(['SESSION_CLOSED','RUN_NOT_INTERACTIVE'].includes(confirmation?.code),'stale confirmation is refused');
        check(await run.cancel()===run,'terminal cancellation is harmless');
        let cancellation=null;
        ${if (full) """
        const cancel=await assistant.run(${JSONObject.quote(goal("Write a long detailed explanation of clouds in the final summary."))});
        const initial=cancel.state;await cancel.cancel('runtime-fixture');const cancelled=await cancel.result;
        check(['cancelled','completed'].includes(cancelled.status),'native cancellation: '+cancelled.status);
        check(cancelled.status==='cancelled'||initial==='completed','cancelled while active');
        cancellation={id:cancel.id,initial,status:cancelled.status};
        """.trimIndent() else ""}
        await agent.close();check(typeof(await agent.status()).state==='string','fresh client after close');await agent.close();
        return {checks,id:run.id,goalPrefix:${JSONObject.quote(goalPrefix)},target:${JSONObject.quote(target)},status:result.status,summaryLength:result.summary.length,
            usage:result.usage,events:{states,progress,steps,done},cancellation,full:$full};
    """.trimIndent()
}
