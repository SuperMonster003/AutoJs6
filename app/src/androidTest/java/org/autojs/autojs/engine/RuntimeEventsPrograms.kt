package org.autojs.autojs.engine

/** Exercises real Android sources; the driver supplies only an owned external Toast and a harmless F8 key. */
internal object RuntimeEventsPrograms {
    fun exercise()="""
        const seen={},errors=[];let batteryResolve;
        const batteryReady=new Promise(resolve=>batteryResolve=resolve);
        events.on('error',e=>errors.push(String(e.message||e)));
        events.on('notification',e=>{if(e.title===marker)seen.notification={id:e.id,title:e.title,packageName:e.packageName};});
        events.on('toast',e=>{if(e.text===marker)seen.toast={text:e.text,packageName:e.packageName};});
        events.on('key',e=>{if(e.keyCode===138&&e.action==='up')seen.key={key:e.key,keyCode:e.keyCode,action:e.action};});
        events.once('battery_changed',e=>{seen.battery={level:e.level,scale:e.scale,percent:e.percent};batteryResolve();});
        const subs=[];
        try {
            subs.push(await events.observeBroadcasts({drainIntervalMs:50}));
            subs.push(await events.observeNotification({drainIntervalMs:50}));
            subs.push(await events.observeToast({drainIntervalMs:50}));
            subs.push(await events.observeKey({drainIntervalMs:50}));
            eq(await events.observeKey(),subs[3],'same-client subscription');
            const status=await notifications.getPermissionStatus();eq(status.notificationsEnabled,true,'Notifications enabled');eq(status.postNotificationsPermissionGranted,true,'Posting permission');
            eq((await notifications.openNotificationSettings({dryRun:true})).dryRun,true,'Settings dry run');
            eq(await notifications.notify({id:7,title:marker,text:'owned silent event',channel,priority:'low'}),7,'Notification id');
            await ready(marker);
            const deadline=Date.now()+15000;
            while(!seen.notification||!seen.toast||!seen.key||!seen.battery){if(errors.length)throw Error(errors.join(';'));if(Date.now()>deadline)throw Error('Missing real event: '+JSON.stringify(seen));await pause(50);}
            if(seen.battery.scale<=0||seen.battery.percent<0||seen.battery.percent>100)throw Error('Invalid battery event');
            await notifications.notify({id:7,title:marker+' updated',channel,priority:'low'});
            await notifications.cancel(7);await notifications.cancelAll();
            return {notification:seen.notification,toast:seen.toast,key:seen.key,battery:seen.battery,sourceCount:subs.length};
        } finally {await events.close();await notifications.cancelAll();}
    """.trimIndent()
}
