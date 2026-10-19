package org.autojs.autojs.engine

internal object RuntimeAndroidTasksPrograms {
    fun helpers() = """
        const paint=await colors.createPaint({color:'#ff0000',textSize:24,antiAlias:true});
        const states=await colors.createColorStateList({states:[['!enabled'],[]],colors:['#ff0000','#0000ff']});
        try{
            await colors.setPaintColor(paint,'#00ff00');eq((await paint.getInfo()).color,-16711936,'native paint color');
            if(!(await paint.measureText('AutoJs6')>0))throw Error('Native Paint text measurement');
            eq(await states.getColorForState([]),-65536,'disabled color');eq(await states.getColorForState(['enabled']),-16776961,'enabled color');
            const view=await ui.createView('Text',{text:'Owned Android color states',textColor:states}),window=await ui.showLayout(view);
            try{eq(await view.textColor,-16776961,'TextView default');await view.set({enabled:false});eq(await view.textColor,-65536,'TextView disabled');}
            finally{await window.close();await view.dispose();}
            const theme=await colors.getThemeColor();eq(typeof theme.colorPrimary,'number','native theme');eq(colors.toInt(theme),theme.colorPrimary,'theme conversion');eq((await colors.themeColor).colorPrimary,theme.colorPrimary,'theme property');
            const uri=await android.getUriForFile(ownedFile);eq(uri.scheme,'content','file URI');eq((await android.parseUri(uri.value)).value,uri.value,'native URI parser');
            return {paintColor:(await paint.getInfo()).color,stateColors:[states.defaultColor,await states.getColorForState([])],theme,uri:uri.value};
        }finally{await states.close();await paint.close();}
    """.trimIndent()
    fun crud() = """
        const created=[];
        try{
            const time=Date.now()+3600000;
            created.push(await tasks.addDailyTask({path:child,time,endMode:'count',times:2}));
            created.push(await tasks.addWeeklyTask({path:child,time,daysOfWeek:[0,1,2,3,4,5,6]}));
            created.push(await tasks.addMonthlyTask({path:child,time,dayOfMonth:31}));
            created.push(await tasks.addYearlyTask({path:child,time,monthOfYear:2,dayOfMonth:29}));
            created.push(await tasks.addDisposableTask({path:child,date:new Date(Date.now()+86400000)}));
            created.push(await tasks.addCountdownTask({path:child,delayMs:86400000}));
            eq(created.map(x=>x.repeatMode),['daily','weekly','monthly','yearly','disposable','countdown'],'calendar modes');
            eq((await tasks.queryTimedTasks({path:child})).length,6,'query modes');
            const next=await tasks.updateTask(created[0],{time:'23:58:59',times:3});eq(next.options.times,3,'update count');
            const action='owned.runtime.'+Date.now()+Math.random().toString(36).slice(2),intent=await tasks.addBroadcastIntentTask({path:child,action,local:true});created.push(intent);
            eq((await tasks.queryIntentTasks({action})).length,1,'intent query');eq((await tasks.getIntentTask(intent.id)).action,action,'intent read');
            const changed=await tasks.updateTask(intent,{action:action+'.changed'});eq(changed.action,action+'.changed','intent update');eq((await tasks.queryIntentTasks({action})).length,0,'old action removed');
            eq(tasks.daysToTimeFlag([0,1,6]),67,'weekday mask');eq(tasks.timeFlagToDays(67),[0,1,6],'weekday roundtrip');
            return {calendarModes:6,intentUpdated:true};
        }finally{for(const task of created)await tasks.removeTask(task);}
    """.trimIndent()
}
