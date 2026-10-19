package org.autojs.autojs.engine

/** Shared quiet program for host and genuinely built application tests. */
internal object RuntimeDeviceDialogsPrograms {
    fun exercise() = """
        const info=await device.getInfo();
        if(!(info.screen.width>0&&info.screen.height>0&&info.memory.totalMem>=info.memory.availMem))throw Error('Device snapshot');
        for(const name of ['Width','Height','Density','DensityDpi','Brightness','BrightnessMode','Battery','SdkInt','TotalMem','AvailMem']){
            const value=await device['get'+name]();if(typeof value!=='number'||!Number.isFinite(value))throw Error(name);
        }
        for(const name of ['Release','Model','Brand','Product','Board','Bootloader','Hardware','Fingerprint','BuildId'])if(typeof await device['get'+name]()!=='string')throw Error(name);
        eq(typeof await device.isCharging(),'boolean','charging');eq(await device.isScreenOn(),true,'screen');
        await device.wakeUp();await device.vibrate(0);
        const volumes={};for(const stream of ['Music','Notification','Alarm']){
            const current=await device['get'+stream+'Volume'](),max=await device['get'+stream+'MaxVolume']();if(current<0||current>max)throw Error(stream);volumes[stream]=current;
        }
        await device.setMusicVolume(volumes.Music);eq(await device.getMusicVolume(),volumes.Music,'unchanged volume');
        let brightness='permission-denied';
        if(info.screen.canWriteSettings && info.screen.brightness>=0 && info.screen.brightness<=255){
            try{await device.setBrightnessMode(0);await device.setBrightness(80);eq(await device.getBrightness(),80,'brightness');brightness='written';}
            finally{await device.setBrightness(info.screen.brightness);await device.setBrightnessMode(info.screen.brightnessMode);}
        }else if(!info.screen.canWriteSettings){let denied=false;try{await device.setBrightness(80);}catch(e){denied=true;}if(!denied)throw Error('WRITE_SETTINGS required');}
        else brightness='vendor-range';
        await device.keepScreenOn(3000);eq(await device.isScreenOn(),true,'wake lock');await device.cancelKeepingAwake();
        const power=await device.openBatteryOptimizationSettings({dryRun:true});eq(power.opened,false,'dry settings');
        eq(typeof await device.isIgnoringBatteryOptimizations(),'boolean','battery policy');
        await dialogs.alert('Owned alert','Quiet test',{positive:'Owned continue'});
        eq(await dialogs.confirm('Owned confirm','Choose yes',{positive:'Owned yes',negative:'Owned no'}),true,'confirm');
        eq(await dialogs.confirm('Owned neutral','Later',{positive:'Owned yes',neutral:'Owned later'}),false,'neutral');
        eq(await dialogs.input('Owned input','before',{positive:'Owned input done'}),'edited-123','input');
        eq(await dialogs.rawInput('Owned raw','1+2',{positive:'Owned raw done'}),'1+2','raw text');
        eq(await dialogs.prompt('Owned prompt','initial',{negative:'Owned cancel'}),null,'cancel prompt');
        eq(await dialogs.select('Owned select',['Owned alpha','Owned beta']),1,'select');
        eq(await dialogs.singleChoice('Owned single',['Owned first','Owned second'],0,{positive:'Owned single done'}),1,'single');
        eq(await dialogs.multiChoice('Owned multi',['Owned one','Owned two','Owned three'],[0],{positive:'Owned multi done'}),[0,2],'multi');
        const progress=await dialogs.progress.show({title:'Owned progress',max:10,value:1});await progress.update({value:7,message:'Owned progress seven'});eq(progress.value,7,'live progress');await pause(600);eq(await progress.dismiss(),true,'dismiss');eq(progress.shown,false,'closed progress');
        const abort=new AbortController();const timer=setTimeout(()=>abort.abort(),500);
        try{let rejected=false;try{await dialogs.input('Owned abort','',{signal:abort.signal});}catch(e){rejected=true;}if(!rejected)throw Error('Abort must reject');}finally{clearTimeout(timer);}
        let expired=false;try{await dialogs.input('Owned timeout','',{timeoutMs:500});}catch(e){expired=true;}if(!expired)throw Error('Timeout must reject');
        return {model:info.build.model,sdk:info.build.sdkInt,width:info.screen.width,height:info.screen.height,brightness,volumes,dialogs:12,quiet:true};
    """.trimIndent()
}
