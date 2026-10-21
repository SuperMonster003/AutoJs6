package org.autojs.autojs.engine

/** Exercise portable scope semantics and observe the real touch stream inside the owned WebView. */
internal object RuntimeFlowScopePrograms {
    val capabilities = RuntimeFlowToolsPrograms.capabilities
    val grants = RuntimeFlowToolsPrograms.grants
    fun exercise(launch: Boolean = false) = """
        const scope=await(async()=>{${scopes()}})();
        const tools=await(async()=>{${RuntimeFlowToolsPrograms.exercise(launch).replace("flow-tools-", "flow-scope-")}})();
        return {...tools,checks:tools.checks+scope.checks,scope};
    """.trimIndent()

    private fun scopes() = """
        const labels=[],check=async(ok,label)=>{if(!ok)throw Error(label);labels.push(label);await pause(80);};
        const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
        async function rejects(task,code){try{await task;}catch(e){await check(code?e.code===code:!!e,'rejected '+(code||'invalid arguments'));return;}throw Error('Expected rejection '+code);}
        const html='<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"><style>body{margin:0;background:#dedede;touch-action:none}</style><body>Flow gesture pad<script>window.events=[];for(const type of ["touchstart","touchmove","touchend","touchcancel"]){document.addEventListener(type,e=>{events.push({type,n:e.touches.length,x:e.changedTouches[0].clientX,y:e.changedTouches[0].clientY});e.preventDefault();},{passive:false});}</script>';
        const root=await ui.inflate({type:'Column',padding:8,children:[
            {type:'Text',text:'Flow scopes and gestures'},
            {type:'Row',id:'a',contentDescription:'flow-scope-a',children:[
                {type:'Text',text:'Scope duplicate',contentDescription:'flow-scope-leaf-a'},
                {type:'Button',id:'ba',text:'Scope A',contentDescription:'flow-scope-button-a'}]},
            {type:'Row',id:'b',contentDescription:'flow-scope-b',children:[
                {type:'Text',text:'Scope duplicate',contentDescription:'flow-scope-leaf-b'},
                {type:'Button',id:'bb',text:'Scope B',contentDescription:'flow-scope-button-b'}]},
            {type:'WebView',id:'pad',height:220,javaScriptEnabled:true,contentDescription:'flow-scope-pad',html}
        ]});
        const window=await ui.showLayout(root),aView=await root.findView('a'),ba=await root.findView('ba'),bb=await root.findView('bb'),pad=await root.findView('pad');
        let aClicks=0,bClicks=0;ba.on('click',()=>aClicks++);bb.on('click',()=>bClicks++);
        flow.defaults({timeout:12000,interval:100});
        try {
            const a=await flow.waitForVisible({desc:'flow-scope-a'},{stableFor:500}),b=await flow.wait({desc:'flow-scope-b'});
            const base=flow.of('input'),scoped=base.within({desc:'flow-scope-a'});
            await check((await scoped).desc==='flow-scope-a','within result');
            await check((await scoped.wait('Scope duplicate')).desc==='flow-scope-leaf-a','within confines duplicate lookup');
            await rejects(scoped.wait({desc:'flow-scope-leaf-b'},{timeout:0}),'TIMEOUT');
            await check((await base.wait({desc:'flow-scope-leaf-b'})).desc==='flow-scope-leaf-b','sibling remains global');
            await check(await base.scope({root:a})==='input','scope preserves input');
            await check((await scoped.wait('Scope duplicate',{root:b})).desc==='flow-scope-leaf-b','wait root overrides once');
            await check((await scoped.wait('Scope duplicate')).desc==='flow-scope-leaf-a','wait override does not leak');
            await check((await scoped.scope({root:b}).wait('Scope duplicate')).desc==='flow-scope-leaf-b','scope root override');
            await check((await scoped.scope({root:null}).wait('Scope duplicate')).desc==='flow-scope-leaf-a','null keeps scope');
            await check((await scoped.wait('Scope duplicate',{compass:'p'})).desc==='flow-scope-a','compass parent');
            await check((await scoped.wait('Scope duplicate',{compass:'s>1'})).desc==='flow-scope-button-a','compass sibling');
            await check((await scoped.wait({desc:'flow-scope-a'},{compass:'c0'})).desc==='flow-scope-leaf-a','compass child');
            await check(await scoped.wait('Scope duplicate',{resultType:'text'})==='Scope duplicate','text projection');
            await check(await scoped.scope({resultType:'text'}).sleep(1).map(async()=>0).wait('Scope duplicate')==='Scope duplicate','projection survives asynchronous chain');
            await check(await scoped.wait({desc:'flow-scope-a'},{resultType:'indexInParent'})>=0,'numeric zero projection is found');
            const point=await scoped.wait('Scope duplicate',{resultType:'.'});await check(typeof point.x==='number'&&typeof point.y==='number','point projection');
            const all=await base.scope({resultType:['#']}).wait('Scope duplicate');await check(all.length===2&&all[0].desc!==all[1].desc,'symbol array projection');
            const texts=await flow.wait('Scope duplicate',{resultType:'contents'});await check(texts.length===2&&texts.includes('flow-scope-leaf-a')&&texts.includes('flow-scope-leaf-b'),'plural content projection');
            const collection=await flow.wait('Scope duplicate',{resultType:'{}'});await check(Array.isArray(collection)&&collection.length===2,'collection is portable array');
            await check((await scoped.wait({desc:'flow-scope-a'},{resultType:['child',0]})).desc==='flow-scope-leaf-a','navigation result projection');
            await check((await scoped.wait('Scope duplicate',{resultType:['parent']})).desc==='flow-scope-a','parent result projection');
            await check(await scoped.wait('Scope duplicate',{resultType:['text']})==='Scope duplicate','single property array projection');
            await check(await scoped.wait('Scope duplicate',{resultType:'?'})===true,'existence projection');
            await check((await scoped.wait('Scope duplicate',{resultType:'@'})).content==='Scope duplicate','selector descriptor projection');
            await check((await scoped.findAny(['Scope duplicate'])).node.desc==='flow-scope-leaf-a','tool inherits root');
            let optional;await scoped.whenPresent('Scope duplicate',n=>{optional=n.desc;});await check(optional==='flow-scope-leaf-a','optional wait inherits root');
            await check((await scoped.repeatUntil(()=>{throw Error('must already be found');},'Scope duplicate')).desc==='flow-scope-leaf-a','repeat condition inherits root');
            const clicked=await base.scope({resultType:'#[]'}).wait({descMatches:'flow-scope-button-.*'}).click();
            await check(clicked.length===2,'node array actions');await flow.wait(()=>aClicks===1&&bClicks===1,{timeout:3000});await check(aClicks===1&&bClicks===1,'both scoped node actions delivered');
            await rejects(scoped.wait('Scope duplicate',{compass:'not-a-compass'}));
            await rejects(scoped.wait('Scope duplicate',{resultType:['performAction',16]}));
            await aView.set({contentDescription:'flow-scope-changed'});await flow.wait({desc:'flow-scope-changed'});
            await rejects(scoped.wait('Scope duplicate'),'INVALID_TARGET');
            await aView.set({contentDescription:'flow-scope-a'});
            await rejects(base.within(()=>42),'INVALID_TARGET');
            const cancelled=base.within(()=>false);cancelled.cancel();await rejects(cancelled,'CANCELLED');
            await flow.wait(async()=>await pad.evaluateJavascript('Array.isArray(window.events)'),{timeout:10000});
            const bounds=(await flow.waitForVisible({desc:'flow-scope-pad'},{stableFor:500})).bounds;
            const x=Math.round((bounds.left+bounds.right)/2),y=Math.round((bounds.top+bounds.bottom)/2),dx=Math.max(20,Math.round((bounds.right-bounds.left)/5)),dy=Math.max(20,Math.round((bounds.bottom-bounds.top)/5));
            const p=[x,y+dy],q=[x,y-dy],flat=[...p,...q],pair=[p,q];
            async function gesture(name,args,moving=false,multi=false){
                await pad.evaluateJavascript('events=[]');
                const token={name};const value=await flow.of(token)[name](...args);
                await check(value===token,name+' input identity');
                let events;await flow.wait(async()=>{events=await pad.evaluateJavascript('events');return events.some(e=>e.type==='touchend');},{timeout:4000,interval:40});
                await check(events.some(e=>e.type==='touchstart')&&!events.some(e=>e.type==='touchcancel'),name+' actual touch stream');
                if(moving)await check(events.some(e=>e.type==='touchmove'),name+' movement delivered');
                if(multi)await check(events.some(e=>e.n===2),name+' two simultaneous pointers');
            }
            for(const args of [[p],[x,y+dy],[p,120],[120,p],[x,y+dy,120]])await gesture('press',args);
            for(const args of [[...flat,200],[p,q,200],[pair,200],[flat,200],[200,p,q],[200,pair],[200,flat]])await gesture('swipe',args,true);
            for(const args of [[200,p,q],[200,pair],[200,flat]])await gesture('gesture',args,true);
            await gesture('gestures',[[200,p,q],[40,200,[x+dx,y+dy],[x+dx,y-dy]]],true,true);
            await rejects(flow.of(0).press(p,0));await rejects(flow.of(0).swipe([1,2,3],100));
            await pad.evaluateJavascript('events=[]');
            const delayed=flow.sleep(10000).press(p,100);delayed.cancel();await rejects(delayed,'CANCELLED');
            await pause(100);await check((await pad.evaluateJavascript('events')).length===0,'cancelled predecessor dispatches no gesture');
            return {checks:labels.length,labels,gestureOverloads:16,aClicks,bClicks};
        }catch(e){console.error('FLOW_SCOPE_CHECKS '+JSON.stringify(labels));throw e;}
        finally{try{await window.close();}finally{await root.dispose();}}
    """.trimIndent()
}
