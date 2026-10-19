package org.autojs.autojs.engine

internal object RuntimeJavaInteropPrograms {
    fun body() = """
        const kept=[];
        const hold=value=>{if(Array.isArray(value))value.forEach(hold);else if(value&&value.__autojs6JavaHandle)kept.push(value);return value;};
        const check=(value,label)=>{if(!value)throw Error(label);};
        const rejects=async fn=>{try{await fn();}catch(e){return String(e.message||e);}throw Error('Expected Java rejection');};
        try {
            const rect=hold(await java.type('android.graphics.Rect').new([1,2,11,22]));
            eq(await rect.call('width'),10,'rect width');await rect.setField('right',21);eq(await rect.getField('right'),21,'live field');
            eq(await java.instanceOf(rect,'android.graphics.Rect'),true,'instanceOf');eq(await rect.getClassName(),'android.graphics.Rect','class');
            const copy=hold(await java.new('android.graphics.Rect',[rect]));eq(await copy.call('equals',[rect]),true,'handle argument');
            const sb=hold(await java.new('java.lang.StringBuilder',['abc']));hold(await sb.call('append',['def'],{parameterTypes:['java.lang.String']}));
            eq(await sb.call('toString'),'abcdef','mutable CharSequence');
            const bundle=hold(await java.new('android.os.Bundle'));
            await bundle.call('putLong',['exact',9223372036854775807n]);eq(String(await bundle.call('getLong',['exact'])),'9223372036854775807','long precision');
            await bundle.call('putString',['nullable',java.cast('java.lang.String',null)]);eq(await bundle.call('getString',['nullable']),null,'typed null');
            const intent=hold(await java.new('android.content.Intent',['owned.java.interop']));hold(await intent.call('putExtras',[bundle]));
            eq(String(await intent.call('getLongExtra',['exact',0n])),'9223372036854775807','Android object graph');
            const list=hold(await java.new('java.util.ArrayList'));await list.call('add',['first']);await list.call('add',[rect]);
            const entries=hold(await list.call('toArray'));eq(entries[0],'first','object array primitive');eq(await entries[1].call('width'),20,'object array handle');
            const wrapped=hold(await java.callStatic('java.util.Collections','unmodifiableList',[list]));eq(await wrapped.call('size'),2,'private implementation public interface');
            const ints=hold(await java.newArray('int',[4,1,3]));await java.callStatic('java.util.Arrays','sort',[ints]);eq(await ints.toArray(),[1,3,4],'array mutation');
            await ints.setArrayItem(1,9);eq(await ints.getArrayItem(1),9,'array access');eq(await ints.arrayLength(),3,'array length');
            const strings=hold(await java.newArray('java.lang.String',['one',null]));eq(await strings.toArray(),['one',null],'nullable array');
            const cyclic=hold(await java.newArray('java.lang.Object',1));await cyclic.setArrayItem(0,cyclic);const cycleSnapshot=hold(await cyclic.toArray());eq(await cycleSnapshot[0].arrayLength(),1,'cyclic array handle');
            const nested=hold(await java.newArray('int[]',[[1,2],[3]]));eq(await nested.toArray(),[[1,2],[3]],'nested arrays');
            const points=hold(await java.newArray('android.graphics.Rect',[rect,copy]));const returned=hold(await points.getArrayItem(1));eq(await returned.call('width'),20,'reference array');
            eq(await java.callStatic(java.type('java.lang.Math'),'max',[3,7]),7,'int overload');
            eq(await java.callStatic('java.lang.Math','max',[java.cast('double',3),java.cast('double',7.25)]),7.25,'typed overload');
            check(Number.isNaN(await java.callStatic('java.lang.Math','sqrt',[-1])),'NaN return');
            check((await java.callStatic('java.lang.Double','isInfinite',[Infinity]))===true,'infinite argument');
            eq(String(await java.getStatic('java.lang.Long','MAX_VALUE')),'9223372036854775807','static long');
            check((await java.describe('java.lang.StringBuilder')).methods.some(x=>x.startsWith('append(')),'signature metadata');
            const exception=await rejects(()=>java.callStatic('java.lang.Integer','parseInt',['not-a-number']));check(exception.includes('NumberFormatException'),'exception cause');
            await rejects(()=>java.setStatic('java.lang.Integer','MAX_VALUE',1));await rejects(()=>ints.setArrayItem(100,1));
            await rejects(()=>rect.setField('right',1.5));await rejects(()=>java.callStatic('java.lang.Math','max',[9007199254740992,0]));
            const context=hold(await java.getContext());const packageName=await context.call('getPackageName');eq(packageName,expectedPackage,'actual application Context');
            const cache=hold(await context.call('getCacheDir'));const cachePath=await cache.call('getAbsolutePath');check(cachePath.includes(packageName),'context File graph');
            const view=hold(await java.new('android.widget.TextView',[context],{thread:'main'}));
            await view.call('setText',['Java interop quiet test'],{thread:'main'});const text=hold(await view.call('getText',[],{thread:'main'}));
            eq(typeof text==='string'?text:await text.call('toString'),'Java interop quiet test','Android main Looper view');
            const main=hold(await java.callStatic('android.os.Looper','getMainLooper'));
            eq(await main.call('isCurrentThread',[],{thread:'main'}),true,'main thread dispatch');
            const released=hold(await java.new('android.graphics.Point',[2,3]));eq(await released.release(),true,'release');eq(await released.release(),false,'repeat release');
            await rejects(()=>released.getField('x'));
            return {packageName,cachePath,longExact:true,objectGraph:true,arrays:true,mainThread:true,quiet:true};
        } finally {for(const handle of kept.reverse())await java.release(handle).catch(()=>{});}
    """.trimIndent()
}
