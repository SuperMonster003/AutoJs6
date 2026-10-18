package org.autojs.autojs.engine

import org.json.JSONObject

internal object BunAutomationPrograms {
    fun appAndClipboard(packageName: String) = """
        eq(app.packageName,${JSONObject.quote(packageName)},'Application identity');
        const info=await app.getPackageInfo(app.packageName);eq(info.packageName,app.packageName,'Package info');
        eq(await app.isInstalled(app.packageName),true,'Installed');eq(await app.isInstalled('invalid.autojs6.fixture.missing'),false,'Missing');
        const label=await app.getAppName(app.packageName);if(!label)throw Error('Application label');
        const packages=await app.getInstalledApps({includeSystem:false});if(!packages.some(p=>p.packageName===app.packageName))throw Error('Package list');
        eq(app.intent({action:'VIEW',flags:[1,2]}).flags,3,'Intent flags');eq(app.parseUri('https://example.invalid/p?q=v').host,'example.invalid','URI');
        await clipboard.setText('AutoJs6 owned clipboard fixture');eq(await clipboard.getText(),'AutoJs6 owned clipboard fixture','Clipboard');eq(await clipboard.hasText(),true,'Has text');
        await clipboard.setText('');eq(await clipboard.hasText(),false,'Empty clipboard');
        return {packageName:app.packageName,label,packages:packages.length,clipboard:true};
    """.trimIndent()

    fun accessibility(fixturePackage: String, longGesture: Boolean) = """
        async function wait(fn,label){const end=Date.now()+20000;while(!await fn()){if(Date.now()>end)throw Error(label);await pause(150);}}
        const fixture=${JSONObject.quote(fixturePackage)},activity='org.autojs.autojs.engine.BunAutomationFixtureActivity';
        let sequence=0;const show=async()=>{const runId=Date.now()+'-'+(++sequence);await app.startActivity({packageName:fixture,className:activity,flags:0x10008000,extras:{runId}});await wait(()=>a.findByText('Bun ready '+runId),'Fresh fixture window');};
        eq(await a.isEnabled(),true,'Accessibility enabled');eq(await a.ensureEnabled({prompt:false}),true,'Ensure enabled');
        await show();await wait(()=>a.findOne(a.desc('Bun fixture button')),'Fixture visible');
        const button=a.desc('Bun fixture button');eq(await a.click(button),true,'Selector click');
        await wait(()=>a.findByText('Bun clicks 1'),'Click delivered');eq(await a.longClick(button),true,'Long click');
        await wait(()=>a.findByText('Bun long clicks 1'),'Long click delivered');
        eq(await a.setText(a.desc('Bun fixture input'),'Bun edited text'),true,'Set text');
        const edited=await a.findOne({...a.textMatches(/^Bun edited text$/),...a.className('android.widget.EditText')});if(!edited)throw Error('Regex/input');
        if(!(await a.findAll(a.className('android.widget.Button'))).length)throw Error('Find all');
        eq(await a.clickText('Bun fixture button'),true,'Click text');
        const scroller=a.desc('Bun fixture scroll');eq(await a.scrollForward(scroller),true,'Scroll forward');await pause(350);
        const scrolled=await a.findOne(a.textMatches(/^Bun scroll [1-9][0-9]*$/));if(!scrolled)throw Error('Actual scroll');
        eq(await a.scrollBackward(scroller),true,'Scroll back');await pause(350);
        const node=await a.findOne(button),x=(node.bounds.left+node.bounds.right)/2,y=(node.bounds.top+node.bounds.bottom)/2;
        eq(await a.swipe(x,y,x,y,120),true,'Swipe completion');
        eq(await a.gesture(${if(longGesture)10000 else 120},[[x,y],[x,y]]),true,'Gesture completion');
        await app.sendBroadcast({packageName:fixture,className:activity+'${'$'}Receiver',action:'org.autojs.fixture.AUTOMATION',extras:{value:'owned'}});
        await wait(()=>a.findByText('Bun event broadcast owned'),'Broadcast delivered');
        const service=await app.startService({packageName:fixture,className:activity+'${'$'}FixtureService',extras:{value:'owned'}});if(!service)throw Error('Service component');
        await wait(()=>a.findByText('Bun event service owned'),'Service delivered');
        await app.openUrl('autojs6-bun-fixture://owned');await wait(()=>a.findByText('Bun URI autojs6-bun-fixture://owned'),'URL intent delivered');
        eq(await app.launchPackage(fixture),true,'Launch package');
        const label=await app.getAppName(fixture);eq(await app.getPackageName(label),fixture,'Package name');eq(await app.launchApp(label),true,'Launch app');
        // Navigation is last: Android may restrict subsequent activity starts after going Home.
        eq(await a.home(),true,'Home');eq(await a.recentApps(),true,'Recent apps');eq(await a.back(),true,'Back');
        return {accessibility:true,intents:true,gestureMs:${if(longGesture)10000 else 120},fixture};
    """.trimIndent()
}
