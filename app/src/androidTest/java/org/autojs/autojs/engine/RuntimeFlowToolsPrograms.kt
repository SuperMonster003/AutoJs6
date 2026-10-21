package org.autojs.autojs.engine

/** Owned UI and real event sources, shared by host runtimes and independently signed applications. */
internal object RuntimeFlowToolsPrograms {
    val capabilities = listOf("accessibility", "accessibility.gesture", "ui", "events", "events.toast", "events.notification", "notifications", "app.query", "app.launch", "clipboard", "keys", "network")
    val grants = listOf("flow.control", "accessibility.control", "accessibility.gesture", "ui.layout", "events.observe", "events.toast", "events.notification", "notifications.post", "app.control", "clipboard.control")

    fun exercise(launch: Boolean = false) = """
        const checks=[],pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
        function check(value,label){if(!value)throw Error(label);checks.push(label);}
        async function failure(task,code){try{await task;}catch(error){check(error.code===code,'failure '+code+': '+error.code);return error;}throw Error('Expected '+code);}
        const marker='EVENTS_FLOW_'+Date.now()+'_'+Math.random().toString(36).slice(2),channel='flow-tools-'+marker.toLowerCase();
        const context=await ui.context(),pkg=context.packageName;
        const root=await ui.inflate({type:'Column',padding:8,children:[
            {type:'Text',id:'signal',text:'Flow tools running'},
            {type:'Button',id:'target',text:'Flow target',contentDescription:'flow-tools-target'},
            {type:'Row',id:'ancestor',clickable:true,children:[{type:'Text',text:'Flow leaf',contentDescription:'flow-tools-leaf'}]},
            {type:'Text',id:'plain',text:'Flow plain',contentDescription:'flow-tools-plain'},
            {type:'Input',id:'field',text:'seed',contentDescription:'flow-tools-field',singleLine:true,focusable:false},
            {type:'Checkbox',id:'check',text:'Flow toggle',contentDescription:'flow-tools-check',checked:false},
            {type:'Button',id:'popup',text:'Flow dismiss',contentDescription:'flow-tools-popup'},
            {type:'ScrollView',id:'list',height:160,contentDescription:'flow-tools-list',children:[{type:'Column',children:Array.from({length:12},(_,i)=>({type:'Text',text:'Flow row '+i,height:32,contentDescription:'flow-tools-row-'+i}))}]}
        ]});
        const window=await ui.showLayout(root),target=await root.findView('target'),ancestor=await root.findView('ancestor'),field=await root.findView('field'),popup=await root.findView('popup'),signal=await root.findView('signal');
        let clicks=0,climbs=0;target.on('click',()=>clicks++);ancestor.on('click',()=>climbs++);popup.on('click',()=>{void popup.set({visibility:'gone'});});
        const targetSelector={desc:'flow-tools-target'},fieldSelector={desc:'flow-tools-field'},listSelector={desc:'flow-tools-list'};
        flow.defaults({timeout:15000,interval:50});
        try{
            const samples=[];
            const first=await flow.waitForVisible(targetSelector,{stableFor:50,snapshot:value=>{samples.push(value);if(samples.length>4)samples.shift();return value;}})
                .catch(error=>{console.error('FLOW_TOOLS_SNAPSHOTS '+JSON.stringify(samples));throw error;});
            check(Number.isInteger(first.windowId)&&first.packageName===pkg,'action snapshot identity');
            let result=await flow.smartClick(targetSelector,{gestureFallback:false,verify:async()=>{await pause(1);return (await flow.findAny([targetSelector])).node;}});
            check(result.method==='node'&&result.verified.text==='Flow target','smart click and native callback reentry');
            result=await flow.smartClick({desc:'flow-tools-leaf'},{gestureFallback:false});check(result.method==='ancestor','ancestor click');
            result=await flow.smartClickBounds(targetSelector);check(result.method==='gesture','bounds gesture');
            check(await flow.clickIfExists('flow missing',{timeout:0,gestureFallback:false})===false,'optional click absent');
            check(await flow.clickBoundsIfExists(targetSelector,{timeout:2000})===true,'optional bounds present');
            const found=await flow.findAny(['flow missing',targetSelector],{timeout:2000});check(found.index===1&&found.node.text==='Flow target','ordered candidates');
            result=await flow.clickAny(['flow missing',targetSelector],{gestureFallback:false});check(result.index===1&&result.method==='node','click any');
            result=await flow.clickBoundsAny([targetSelector]);check(result.index===0&&result.method==='gesture','bounds any');
            const clickNames=['waitThenClick','clickWait','waitThenClickBounds','clickBoundsWait','waitForStableThenClick','clickWhenStable','waitForStableThenClickBounds','clickBoundsWhenStable','clickWhenStableAfter','clickBoundsWhenStableAfter'];
            for(const name of clickNames){const value=await flow[name](targetSelector,{timeout:3000,interval:30,stableFor:0,delay:[1,2]});check(value.text==='Flow target',name);}
            await failure(flow.waitThenLongClick({desc:'flow-tools-plain'},{timeout:2000}),'ACTION_FAILED');
            const text=await flow.typeInto(fieldSelector,'portable text');check(text.text==='portable text','type and verify');
            const appended=await flow.of(text).appendText('!');check(appended.text==='portable text!','node append action');
            const cleared=await flow.of(appended).clear();check(cleared.text==='','node clear action');
            check((await flow.of(cleared).setText('restored')).text==='restored','node set text action');
            const checkbox=await root.findView('check');await checkbox.set({checked:false});
            await flow.wait({desc:'flow-tools-check',checked:false},{timeout:3000});
            result=await flow.toggle({desc:'flow-tools-check'},true,{click:{gestureFallback:false}});check(result.changed&&result.checked,'toggle changed');
            result=await flow.toggle({desc:'flow-tools-check'},true,{click:{gestureFallback:false}});check(!result.changed&&result.checked,'toggle unchanged');
            const clicked=flow.waitForEvent('view_clicked',{packageName:pkg,text:'Flow target'},5000);await clicked.ready;
            await flow.wait(targetSelector).click();check((await clicked).packageName===pkg,'real accessibility click event');
            const idle=flow.of('keep').waitForIdle({quietFor:60,timeout:5000});await idle.ready;check(await idle==='keep','idle preserves chain value');
            const idleRoot=await flow.waitForIdle({quietFor:30,timeout:5000});check(idleRoot.quietFor===30&&idleRoot.elapsed>=0,'root idle result');
            const stale=await flow.wait(targetSelector);await target.set({text:'Flow changed'});
            await flow.wait({text:'Flow changed'});await failure(flow.of(stale).click(),'INVALID_TARGET');await target.set({text:'Flow target'});
            check(await flow.of('keep').dismissPopups([{desc:'flow-tools-popup'}],{click:{gestureFallback:false}})==='keep','popup chain pass through');
            await flow.waitForGone({desc:'flow-tools-popup'},{stableFor:0,timeout:3000});
            const scrolled=await flow.scrollUntil({desc:'flow-tools-row-11',visibleToUser:true},{container:listSelector,maxSteps:12,settle:60});
            check(scrolled.text==='Flow row 11','scroll until visible');
            const unicode='\u4f60\u597d\ud83d\ude03'.repeat(2200);
            const collected=await flow.collectList(listSelector,{descMatches:'flow-tools-row-.*'},{maxSteps:0,maxItems:4,dedupe:true,resultType:async n=>n.text+unicode,until:async()=>false});
            check(collected.length>=3&&collected.every(x=>x.endsWith(unicode))&&Buffer.byteLength(collected.join(''),'utf8')>36000,'chunked Unicode collection and async callbacks');
            const nodes=await flow.wait(listSelector).collectList({descMatches:'flow-tools-row-.*'},{maxSteps:0,maxItems:2,resultType:'#'});check(nodes.length===2&&nodes.every(x=>x.packageName===pkg),'collection node snapshots');
            check((await flow.launchAndWait(pkg,{bringToFront:false,timeout:2000})).launched===false,'launch already foreground');
            check(await flow.backUntil(()=> 'already',{maxTimes:0})==='already','back condition first');
            check(await flow.backToApp(pkg,{maxTimes:0})===pkg,'back package first');
            const ownError=new Error('Flow tools original callback');let observed;
            try{await flow.smartClick(targetSelector,{gestureFallback:false,verify:()=>{throw ownError;}});}catch(error){observed=error;}
            check(observed===ownError,'callback error identity');
            let child;await failure(flow.smartClick(targetSelector,{gestureFallback:false,timeout:1000,verify:async()=>{await pause(1);child=flow.sleep(10000);return child;}}),'TIMEOUT');
            check(child.state==='cancelled','native callback child cancellation');
            const cancelled=flow.waitForEvent('*',null,Infinity);await cancelled.ready;check(cancelled.cancel(),'event cancellation');await failure(cancelled,'CANCELLED');
            const notice=flow.waitForNotification({packageName:pkg,title:marker},10000);await notice.ready;
            await notifications.notify({id:9401,title:marker,text:'Flow silent notification',channel,priority:'low'});
            const notification=await notice;check(notification.title===marker&&notification.packageName===pkg,'real silent notification');await notifications.cancel(9401);
            const toast=flow.waitForToast(async event=>{await flow.sleep(1);return event.text===marker;},15000);await toast.ready;
            await signal.set({text:'FLOW_TOAST '+marker});
            const toastValue=await toast;check(toastValue.text===marker&&toastValue.packageName!==pkg,'real external toast');
            check(await flow.backToApp(pkg,{maxTimes:1,delay:1500,interval:50})===pkg,'back from owned Toast activity');
            await flow.waitForVisible(targetSelector,{timeout:10000,stableFor:20});
            await flow.wait(()=>clicks===18&&climbs===1,{timeout:3000,interval:30});check(clicks===18&&climbs===1,'real view callbacks');
            if(EXERCISE_LAUNCH)check((await flow.launchAndWait(pkg,{bringToFront:true,timeout:5000})).launched===true,'actual host launch');
            return {checks:checks.length,labels:checks,clicks,climbs,collectionItems:collected.length,collectionCharacters:collected.join('').length,notification,toast:toastValue,packageName:pkg,channel};
        }catch(error){console.error('FLOW_TOOLS_CHECKS '+JSON.stringify(checks));throw error;}
        finally{await flow.dispose();await notifications.cancel(9401).catch(()=>{});try{await window.close();}finally{await root.dispose();}}
    """.trimIndent().replace("EXERCISE_LAUNCH", launch.toString())
}
