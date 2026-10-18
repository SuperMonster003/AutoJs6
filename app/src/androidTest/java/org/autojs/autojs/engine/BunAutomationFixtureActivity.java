package org.autojs.autojs.engine;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Framework-only activity in the test APK's own UID, usable by packaged applications. */
public class BunAutomationFixtureActivity extends Activity {
    private TextView status;
    private int clicks, longClicks;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);setContentView(root);
        status=new TextView(this);status.setText("Bun fixture ready");root.addView(status);
        EditText input=new EditText(this);input.setContentDescription("Bun fixture input");input.setText("Original");root.addView(input);
        Button button=new Button(this);button.setText("Bun fixture button");button.setAllCaps(false);button.setContentDescription("Bun fixture button");root.addView(button);
        button.setOnClickListener(v->{clicks++;status.setText("Bun clicks "+clicks);});
        button.setOnLongClickListener(v->{longClicks++;status.setText("Bun long clicks "+longClicks);return true;});
        TextView scrollStatus=new TextView(this);scrollStatus.setText("Bun scroll 0");root.addView(scrollStatus);
        ScrollView scroll=new ScrollView(this);scroll.setContentDescription("Bun fixture scroll");root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        for(int i=0;i<40;i++){TextView row=new TextView(this);row.setText("Bun row "+i);row.setTextSize(28);row.setPadding(20,25,20,25);list.addView(row);}
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->scrollStatus.setText("Bun scroll "+y));
        TextView events=new TextView(this);root.addView(events);
        Runnable refresh=new Runnable(){public void run(){if(isFinishing())return;events.setText("Bun event "+getSharedPreferences("automation",0).getString("event","none"));events.postDelayed(this,150);}};
        events.post(refresh);recordIntent(getIntent());
    }
    private void recordIntent(Intent intent){if(intent.getData()!=null)status.setText("Bun URI "+intent.getDataString());else if(intent.hasExtra("runId"))status.setText("Bun ready "+intent.getStringExtra("runId"));}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);recordIntent(intent);}
    public static class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context,Intent intent){context.getSharedPreferences("automation",0).edit().putString("event","broadcast "+intent.getStringExtra("value")).apply();}
    }
    public static class FixtureService extends Service {
        @Override public IBinder onBind(Intent intent){return null;}
        @Override public int onStartCommand(Intent intent,int flags,int id){getSharedPreferences("automation",0).edit().putString("event","service "+intent.getStringExtra("value")).apply();stopSelf(id);return START_NOT_STICKY;}
    }
}
