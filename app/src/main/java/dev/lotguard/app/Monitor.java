package dev.lotguard.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.WindowManager;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.*;

public class Monitor extends Service {
    private static final String WATCH="session", WARN="mismatch";
    final Handler main=new Handler(Looper.getMainLooper());
    MediaProjection projection;
    VirtualDisplay display;
    ImageReader reader;
    TextRecognizer ocr;
    NotificationManager notices;
    Detector detector;
    boolean busy, stopped, visible;
    long lastRead;
    int generation;
    String lastStatus="";
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();notices=getSystemService(NotificationManager.class);
        notices.createNotificationChannel(new NotificationChannel(WATCH,"Session status",NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alerts=new NotificationChannel(WARN,"Lot-size warnings",NotificationManager.IMPORTANCE_HIGH);
        alerts.enableVibration(true);notices.createNotificationChannel(alerts);
    }
    Notification note(String channel,String title,String body,boolean ongoing){
        Notification.Builder b=new Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info)
          .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
          .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(ongoing).setOnlyAlertOnce(ongoing);
        if(ongoing){
            PendingIntent end=PendingIntent.getService(this,1,new Intent(this,Monitor.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null,"Stop session",end).build());
        } else b.setTimeoutAfter(12000);
        return b.build();
    }
    void status(String msg){if(!msg.equals(lastStatus)&&!stopped){lastStatus=msg;notices.notify(1,note(WATCH,"LotGuard · demo session",msg,true));}}
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i==null || "stop".equals(i.getAction())){end();return START_NOT_STICKY;}
        if(projection!=null)return START_NOT_STICKY;
        startForeground(1,note(WATCH,"LotGuard · demo session",Detector.OPEN,true));
        try {
            detector=new Detector(Profiles.load(this));
            ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK,i.getParcelableExtra("grant",Intent.class));
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){end();}
                @Override public void onCapturedContentVisibilityChanged(boolean shown){
                    if(stopped)return;
                    if(!shown){end();return;} visible=true;
                }
                @Override public void onCapturedContentResize(int w,int h){if(!stopped&&display!=null&&w>0&&h>0)surface(w,h);}
            },main);
            Rect bounds=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            surface(bounds.width(),bounds.height());
        }catch(RuntimeException e){end();}
        return START_NOT_STICKY;
    }
    void surface(int w,int h){
        float scale=Math.min(1f,1920f/Math.max(w,h));
        w=Math.max(1,Math.round(w*scale));h=Math.max(1,Math.round(h*scale));
        if(reader!=null&&reader.getWidth()==w&&reader.getHeight()==h)return;
        generation++;
        ImageReader previous=reader;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(this::frame,main);
        int density=getResources().getDisplayMetrics().densityDpi;
        if(display==null) display=projection.createVirtualDisplay("LotGuard",w,h,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
        else {display.resize(w,h,density);display.setSurface(reader.getSurface());}
        if(previous!=null)previous.close();
    }
    void frame(ImageReader source){
        if(source!=reader||stopped)return;
        try(Image image=source.acquireLatestImage()){
            if(image==null||busy||!visible||SystemClock.elapsedRealtime()-lastRead<260)return;
            Image.Plane plane=image.getPlanes()[0];
            int width=image.getWidth(),height=image.getHeight(),stride=plane.getRowStride();
            // Copy each valid row. Some devices omit padding bytes in the final row.
            ByteBuffer src=plane.getBuffer();ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);
            for(int y=0;y<height;y++){src.limit(src.capacity());src.position(y*stride);src.limit(y*stride+width*4);pixels.put(src);}
            pixels.flip();Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.copyPixelsFromBuffer(pixels);
            busy=true;lastRead=SystemClock.elapsedRealtime();int version=generation;
            ocr.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(text->{
                if(stopped||version!=generation)return;
                Detector.Result result=detector.scan(rows(text));
                status(result.state());
                if(result.warning()!=null)notices.notify(2,note(WARN,"Check MT5 lot size",result.warning(),false));
            }).addOnFailureListener(e->{if(!stopped){detector=new Detector(Profiles.load(this));status("Text recognition unavailable. Check manually.");}})
              .addOnCompleteListener(task->{bitmap.recycle();busy=false;});
        }catch(RuntimeException e){end();}
    }
    static String rows(Text text){
        List<Text.Element> words=new ArrayList<>();
        for(var block:text.getTextBlocks())for(var line:block.getLines())for(var word:line.getElements())if(word.getBoundingBox()!=null)words.add(word);
        words.sort(Comparator.comparingInt(a->a.getBoundingBox().centerY()));
        StringBuilder result=new StringBuilder();List<Text.Element> row=new ArrayList<>();int center=0;
        for(var word:words){
            Rect box=word.getBoundingBox();
            if(!row.isEmpty()&&box.centerY()-center>Math.max(4,box.height()/2)){appendRow(row,result);row.clear();}
            if(row.isEmpty())center=box.centerY();
            row.add(word);
        }
        appendRow(row,result);return result.toString();
    }
    static void appendRow(List<Text.Element> row,StringBuilder result){
        row.sort(Comparator.comparingInt(w->w.getBoundingBox().left));
        for(var w:row)result.append(w.getText()).append(' ');
        result.append('\n');
    }
    void end(){if(stopped)return;stopped=true;stopSelf();}
    @Override public void onDestroy(){
        stopped=true;generation++;
        if(display!=null)display.release();
        if(reader!=null)reader.close();
        if(projection!=null)projection.stop();
        if(ocr!=null)ocr.close();
        notices.cancel(2);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
