package org.autojs.autojs.engine

internal object RuntimeFlowPrograms {
    fun core() = """
        const checks=[];
        function check(value,label){if(!value)throw Error(label);checks.push(label);}
        async function failure(task,code){try{await task.toPromise();}catch(error){check(error.code===code,'failure '+code);return error;}throw Error('Expected '+code);}
        check(await flow.of(3).sleep(2).map(async x=>x+2).filter(x=>x===5)===5,'chain');
        check(await flow.wait(()=>0)===0,'zero');check(await flow.wait(()=> '')==='','empty string');
        check(await flow.wait(Promise.resolve(false))===false,'Promise false');
        let count=0;check(await flow.waitUntil(()=>++count===3?'ready':false,{interval:2,times:3})==='ready'&&count===3,'polling');
        count=0;check(await flow.waitWhile(()=>++count<3,{interval:2})===true,'while');
        const parent=flow.sleep(60),a=parent.map(()=>1),b=parent.map(()=>2);a.cancel();await failure(a,'CANCELLED');check(await b===2,'independent branch');
        count=0;check(await flow.run(()=>{if(++count<3)throw Error('retry');return count;}).retry(2,1)===3,'retry step');
        check((await failure(flow.wait(()=>new Promise(()=>{}),{timeout:30}),'TIMEOUT')).attempts===1,'async deadline');
        check((await failure(flow.of(1).timeout(30).run(()=>new Promise(()=>{})),'TIMEOUT')).reason==='chain','chain deadline');
        let mutation={n:0},samples=0;const stable=await flow.waitForStable(()=>{if(++samples<3)mutation.n++;return mutation;},{interval:10,stableFor:30,snapshot:x=>({n:x.n})});
        check(stable.n===2&&samples>=3,'stable snapshot');
        let optional=0;check(await flow.of(8).whenPresent(()=>false,()=>optional++,{times:2,interval:1})===8&&optional===0,'optional absence');
        check(await flow.of(8).whenPresent(()=>true,async()=>{await flow.sleep(2);optional++;})===8&&optional===1,'optional handler');
        count=0;check(await flow.repeatUntil(()=>{count++;},()=>count===2?'done':false,{interval:1,maxAttempts:2})==='done','repeat');
        check(JSON.stringify(await flow.all([flow.of(1),Promise.resolve(2),()=>3]))==='[1,2,3]','all');
        check(await flow.any([Promise.reject(Error('first')),flow.of('ok')])==='ok','any');
        check(await flow.race([flow.sleep(2).map(()=>true),()=>false])===true,'race');
        const cancelled=flow.sleep(10000).map(()=>false);cancelled.cancel();await failure(cancelled,'CANCELLED');
        check(flow.wait===flow.waitAsync&&flow.waitForGone===flow.waitForHidden&&flow.delay===flow.sleep,'aliases');
        await new Promise(resolve=>setTimeout(resolve,10));check(flow.pending===0&&flow.workers===0,'settled cleanup');
        return {checks:checks.length,labels:checks,pending:flow.pending};
    """.trimIndent()

    fun query(label: String, packageName: String) = """
        const node=await flow.waitForVisible({text:LABEL},{timeout:10000,interval:50,stableFor:100});
        if(node.text!==LABEL||!node.visibleToUser||node.packageName!==PACKAGE)throw Error('Live Flow node snapshot');
        const stable=await flow.waitForStable(LABEL,{timeout:10000,interval:50,stableFor:100,compare:'bounds'});
        if(stable.bounds.right<=stable.bounds.left)throw Error('Real bounds');
        if(await flow.waitForHidden('Flow deliberately absent',{timeout:2000,interval:50,stableFor:100})!==true)throw Error('Hidden wait');
        if(await flow.waitForPackage(PACKAGE,{timeout:10000,interval:50})!==true)throw Error('Foreground package');
        return {node,stable:true,hidden:true,foreground:true};
    """.trimIndent().replace("LABEL", org.json.JSONObject.quote(label)).replace("PACKAGE", org.json.JSONObject.quote(packageName))
}
