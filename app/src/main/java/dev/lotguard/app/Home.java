package dev.lotguard.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.math.BigDecimal;
import java.util.List;

public class Home extends Activity {
    LinearLayout body;
    EditText login,server,symbol,lots;
    TextView presets,feedback;
    int pad(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView line(String text,int size){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(Color.rgb(23,53,52));t.setPadding(0,pad(5),0,pad(5));body.addView(t);return t;
    }
    EditText field(String hint,int type){
        EditText e=new EditText(this);e.setSingleLine();e.setHint(hint);e.setInputType(type);body.addView(e);return e;
    }
    void button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());body.addView(b);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(246,248,243));
        body=new LinearLayout(this);body.setOrientation(1);body.setPadding(pad(22),pad(16),pad(22),pad(20));
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        scroll.addView(body);setContentView(scroll);
        line("LotGuard",34);line("MT5 demo-account monitor",19);
        line("Save a preset for each demo login and symbol. Use the exact login, server and symbol shown in MT5.",15);
        login=field("Demo account login",2);server=field("Broker server",1);symbol=field("Symbol (including broker suffix)",1);lots=field("Lot preset, e.g. 0.05",8194);
        button("Save preset",this::save);presets=line("",14);refresh();
        button("Start MT5-only check",this::startCheck);
        button("Stop session",()->{stopService(new Intent(this,Monitor.class));feedback.setText("Session stopped.");});
        feedback=line("",15);
        button("Try sample OCR check",()->{feedback.setText("Reading generated sample screens…");SampleCheck.run(feedback::setText);});
        line("Choose “A single app” and select MT5 in Android’s picker. Open the MT5 side menu to verify the active login, then inspect the order ticket. Reopen the menu after switching accounts. Check monitoring status in the notification shade.",15);
        line("English UI · Lots mode · Standard notifications. Test with demo accounts. Warnings may be delayed or missed.",14);
    }
    String val(EditText e){return e.getText().toString().trim();}
    void save(){
        String id=val(login),s=val(server),sym=val(symbol),q=val(lots).replace(',','.');
        if(!id.matches("[0-9]{3,20}")||s.isEmpty()||sym.isEmpty()||!q.matches("[0-9]{1,5}(\\.[0-9]{1,4})?")||new BigDecimal(q).signum()<=0){
            feedback.setText("Enter a login, server, symbol and positive lot size.");return;}
        List<Detector.Profile> list=Profiles.load(this);
        list.removeIf(p->p.login().equals(id)&&p.server().equalsIgnoreCase(s)&&p.symbol().equalsIgnoreCase(sym));
        list.add(new Detector.Profile(id,s,sym,q));Profiles.save(this,list);refresh();feedback.setText("Preset saved.");
    }
    void refresh(){StringBuilder t=new StringBuilder("Saved presets");for(var p:Profiles.load(this))t.append("\n").append(p.server()).append(" · …").append(p.login().substring(Math.max(0,p.login().length()-4))).append(" · ").append(p.symbol()).append(" · ").append(p.preset());presets.setText(t);}
    void startCheck(){
        if(Profiles.load(this).isEmpty()){feedback.setText("Save a demo preset first.");return;}
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);return;}
        new AlertDialog.Builder(this).setTitle("Share MT5 only")
          .setMessage("Select a single app, then MetaTrader 5. Cancel if your phone offers only full-screen sharing. Frames are processed on this device.")
          .setNegativeButton("Cancel",null).setPositiveButton("Choose MT5",(d,w)->startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(),8)).show();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.length>0&&g[0]==0)startCheck();else feedback.setText("Notifications are required for warnings.");}
    @Override protected void onActivityResult(int r,int code,Intent data){super.onActivityResult(r,code,data);
        if(r==8&&code==RESULT_OK&&data!=null){startForegroundService(new Intent(this,Monitor.class).putExtra("grant",data));feedback.setText("Open the MT5 side menu to verify the active account.");}}
}
