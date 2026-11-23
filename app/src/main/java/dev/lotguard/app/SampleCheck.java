package dev.lotguard.app;

import android.graphics.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.List;
import java.util.function.Consumer;

/** Exercises the bundled OCR model with generated fixtures, without screen sharing. */
final class SampleCheck {
    static void run(Consumer<String> report) {
        TextRecognizer reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Detector detector=new Detector(List.of(new Detector.Profile("123456","DemoServer","EURUSD","0.05")));
        runStep(reader,detector,report,0);
    }
    private static void runStep(TextRecognizer reader,Detector detector,Consumer<String> report,int step) {
        boolean menu=step<2;
        String[] labels=menu ? new String[]{"Manage Accounts","123456  DemoServer","Settings","Messages"} :
                new String[]{"New Order","EURUSD","Volume 0.50","Buy","Sell"};
        Bitmap sample=Bitmap.createBitmap(720,560,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(sample);canvas.drawColor(Color.WHITE);
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setTextSize(38);ink.setColor(Color.BLACK);
        for(int i=0;i<labels.length;i++)canvas.drawText(labels[i],36,68+82*i,ink);
        reader.process(InputImage.fromBitmap(sample,0)).addOnSuccessListener(text->{
            Detector.Result result=detector.scan(Monitor.rows(text));sample.recycle();
            if(step<3){runStep(reader,detector,report,step+1);return;}
            reader.close();
            report.accept(result.warning()==null?"Sample check inconclusive: "+result.state():
                    "Sample OCR check passed. "+result.warning()+". Test real demo screens next.");
        }).addOnFailureListener(error->{sample.recycle();reader.close();report.accept("Sample OCR failed: "+error.getClass().getSimpleName());});
    }
}
