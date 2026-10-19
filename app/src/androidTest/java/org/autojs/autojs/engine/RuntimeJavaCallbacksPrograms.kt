package org.autojs.autojs.engine

internal object RuntimeJavaCallbacksPrograms {
    fun body() = """
        const kept=[];const hold=v=>{if(v&&v.__autojs6JavaHandle)kept.push(v);return v;};
        const check=(value,label)=>{if(!value)throw Error(label);};
        const rejects=async action=>{try{await action();}catch(e){return String(e.message||e);}throw Error('Expected rejection');};
        try {
            const loader=hold(await java.loadJar('callback.jar'));const fixture=loader.type('org.autojs.fixture.runtime.CallbackFixture');
            const dex=hold(await java.loadDex('fixture.dex'));eq(await dex.type('org.autojs.fixture.runtime.DexFixture').callStatic('identity'),'actual-dex-fixture','DEX execution');
            eq(String(await java.callStatic('org.autojs.fixture.runtime.DexFixture','exact')),'9223372036854775807','loaded static long');
            let runs=0;const runnable=hold(await java.wrap(async function(){runs++;eq(await this.java.callStatic('java.lang.Math','max',[3,9]),9,'callback reentry');}));
            eq(await fixture.callStatic('run',[runnable]),'ran','Runnable callback');eq(runs,1,'callback delivered');
            const comparator=hold(await java.proxy('java.util.Comparator',{compare:async function(a,b){await Promise.resolve();return a<b?-1:a>b?1:0;}}));
            eq(await fixture.callStatic('compare',[comparator]),-1,'return-valued interface');
            eq(await Promise.all([fixture.callStatic('compare',[comparator]),fixture.callStatic('compare',[comparator])]),[-1,-1],'parallel callback replies');
            const reversed=hold(await comparator.call('reversed'));eq(await reversed.call('compare',['a','b']),1,'interface default method');
            const longCallback=hold(await java.proxy('org.autojs.fixture.runtime.CallbackFixture${'$'}LongCallback',value=>value));
            eq(String(await fixture.callStatic('callLong',[longCallback,9223372036854775807n])),'9223372036854775807','callback long round trip');
            const callable=hold(await java.proxy('java.util.concurrent.Callable',async function(){return 'thread:'+await this.java.callStatic('java.lang.Math','max',[4,8]);}));
            eq(await fixture.callStatic('callOnThread',[callable]),'thread:8','native thread callback');
            const subclass=hold(await java.defineClass({extends:fixture,methods:{
                decorate:async function(value){eq(await this.receiver.getField('seed'),value,'constructor state and reentry');return 'js:'+value;},
                greet:async function(value){return (await this.callSuper(value))+':js';},
                'twice(long)':value=>BigInt(value)*3n
            }}));
            const object=hold(await subclass.new(['seed'],{thread:'main'}));
            eq(await object.getField('constructed'),'js:seed','constructor callback on main');
            eq(await object.call('greet',['hello'],{thread:'main'}),'seed:hello:js','super dispatch on main');
            eq(await object.call('callProtected',[java.cast('long','7')]),21,'protected override');
            eq(await object.instanceOf('org.autojs.fixture.runtime.CallbackFixture'),true,'real JVM subclass');
            let retained;
            const identity=hold(await java.proxy('java.util.function.Function',async value=>{retained=hold(await java.retain(value));return value;}));
            const point=hold(await java.new('android.graphics.Point',[4,5]));const returned=hold(await identity.call('apply',[point]));
            eq(await retained.getField('x'),4,'retained callback argument');eq(await returned.getField('y'),5,'returned callback object');
            const arrayCallback=hold(await java.proxy('org.autojs.fixture.runtime.CallbackFixture${'$'}ArrayCallback',async array=>{await array.setArrayItem(1,9);}));
            eq(await fixture.callStatic('changeArray',[arrayCallback]),9,'callback mutates original Java array');
            const errors=[];const broken=hold(await java.proxy('java.lang.Runnable',()=>{throw Error('owned callback failure');},{onError:e=>errors.push(String(e))}));
            check((await rejects(()=>fixture.callStatic('run',[broken]))).includes('owned callback failure'),'callback exception propagated');eq(errors.length,1,'callback error hook');
            await rejects(()=>java.defineClass({extends:'java.lang.String'}));await rejects(()=>java.defineClass({extends:fixture,methods:{fixed:()=>0}}));
            await rejects(()=>java.loadJar('missing-owned.jar'));await rejects(()=>java.loadDex('callback.jar'));
            await runnable.release();eq(await fixture.callStatic('run',[runnable]).then(()=>false,()=>true),true,'released proxy handle');
            const context=hold(await java.getContext()),packageName=await context.call('getPackageName');
            check(loader.paths.every(p=>p.includes('/'+packageName+'/')),'execution application code cache');
            const button=hold(await java.new('android.widget.Button',[context],{thread:'main'}));
            await button.call('setSoundEffectsEnabled',[false],{thread:'main'});await button.call('setHapticFeedbackEnabled',[false],{thread:'main'});
            let clicked=0;const listener=hold(await java.proxy('android.view.View${'$'}OnClickListener',async view=>{clicked++;await view.call('setText',['quiet callback'],{thread:'main'});}));
            await button.call('setOnClickListener',[listener],{thread:'main'});eq(await button.call('performClick',[],{thread:'main'}),true,'Android listener invoked');eq(clicked,1,'Android listener delivered');
            const label=hold(await button.call('getText',[],{thread:'main'}));eq(typeof label==='string'?label:await label.call('toString'),'quiet callback','listener main-thread reentry');
            await button.call('setOnClickListener',[null],{thread:'main'});
            const codeFile=hold(await java.new('java.io.File',[loader.paths[0]]));eq(await codeFile.call('canWrite'),false,'read-only loaded code');
            return {packageName,readOnlyCode:true,androidListener:true,callbacks:true,constructorOnMain:true,reentry:true,superCall:true,jar:true,dex:true,longExact:true,borrowedRetained:true,quiet:true};
        } finally {for(const value of kept.reverse())await value.release().catch(()=>{});await java.closeCallbacks();}
    """.trimIndent()
}
