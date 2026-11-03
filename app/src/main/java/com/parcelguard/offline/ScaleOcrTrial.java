package com.parcelguard.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.camera.view.PreviewView;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Experimental, manual-start LCD scale OCR for Android.
 *  Nothing is saved without reading a stable value AND explicit visual human confirmation.
 *  Scale display must remain in camera field of view when the user taps Read.
 */
final class ScaleOcrTrial {
    private static int dp(Activity a,int n){
        return (int)(n*a.getResources().getDisplayMetrics().density+0.5f);
    }
    private static TextView text(Activity a,String s,int size){
        TextView v=new TextView(a);v.setText(s);v.setTextSize(size);
        v.setTextColor(Color.WHITE);v.setPadding(dp(a,8),dp(a,6),dp(a,8),dp(a,6));
        return v;
    }
    static boolean show(Activity a,ParcelDb db,PreviewView camera,String bag,
                        SharedPreferences settings,Runnable onExit,Runnable onSaved){
        if(camera==null){
            Toast.makeText(a,"กรุณากลับหน้าแพ็คและเปิดกล้อง",Toast.LENGTH_LONG).show();
            return false;
        }
        Bitmap initial=camera.getBitmap();
        if(initial==null){
            Toast.makeText(a,"กล้องยังไม่พร้อมอ่านน้ำหนัก",Toast.LENGTH_LONG).show();
            return false;
        }
        List<String> trackings=new ArrayList<>(),labels=new ArrayList<>();
        try(Cursor c=db.bagActive(bag)){
            while(c.moveToNext()){
                String tracking=c.getString(0);
                trackings.add(tracking);
                ParcelDb.WeightRecord record=db.weight(tracking);
                labels.add(tracking+(record.actual>=0?
                    String.format(Locale.US," • %.3f kg",record.actual/1000.0):" • ยังไม่ชั่ง"));
            }
        }
        if(trackings.isEmpty()){
            initial.recycle();
            Toast.makeText(a,"ต้องสแกนพัสดุเข้าถุงก่อนอ่านน้ำหนัก",Toast.LENGTH_LONG).show();
            return false;
        }
        ScrollView scroll=new ScrollView(a);
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
        content.setBackgroundColor(0xFF121C27);
        scroll.addView(content);
        content.addView(text(a,"เลือก Tracking ที่อยู่บนตาชั่งตอนนี้ (ค่าเริ่มต้นคือชิ้นล่าสุด)",14));
        Spinner parcel=new Spinner(a);
        ArrayAdapter<String> parcelAdapter=new ArrayAdapter<>(a,
            android.R.layout.simple_spinner_dropdown_item,labels);
        parcel.setAdapter(parcelAdapter);
        content.addView(parcel);
        content.addView(text(a,"แตะตรงตัวเลขบนจอตาชั่งในภาพ แล้วปรับกรอบสีส้มให้ครอบเฉพาะตัวเลข",13));
        RegionView preview=new RegionView(a,initial);
        content.addView(preview,new LinearLayout.LayoutParams(-1,dp(a,240)));
        Button refresh=new Button(a);refresh.setAllCaps(false);
        refresh.setText("จับภาพตัวอย่างใหม่");
        content.addView(refresh);
        refresh.setOnClickListener(v->{
            Bitmap bitmap=camera.getBitmap();
            if(bitmap!=null)preview.replace(bitmap);
            else Toast.makeText(a,"กล้องไม่พร้อมถ่ายภาพ",Toast.LENGTH_SHORT).show();
        });
        TextView cropInfo=text(a,"ขนาดกรอบ OCR",12);content.addView(cropInfo);
        android.widget.SeekBar size=new android.widget.SeekBar(a);
        size.setMax(50);size.setProgress(25);
        content.addView(size);
        size.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(android.widget.SeekBar b){}
            public void onStopTrackingTouch(android.widget.SeekBar b){}
            public void onProgressChanged(android.widget.SeekBar b,int progress,boolean fromUser){
                preview.setFractionWidth(0.30f+progress/100f);
            }
        });
        content.addView(text(a,"หน่วยที่จอตาชั่งแสดง (ถ้า OCR อ่าน kg/g ได้ จะใช้หน่วยบนจอก่อน)",12));
        Spinner unit=new Spinner(a);
        unit.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"kg / กิโลกรัม","g / กรัม"}));
        unit.setSelection("g".equals(settings.getString("scale_ocr_unit","kg"))?1:0);
        content.addView(unit);
        TextView output=text(a,"ระบบจะอ่าน 3 ครั้ง โดยเว้นระยะประมาณ 0.6 วินาที แล้วเทียบค่าน้ำหนัก",13);
        content.addView(output);
        Button read=new Button(a);read.setAllCaps(false);
        read.setText("อ่านน้ำหนัก 3 ครั้ง (ทดลอง)");
        content.addView(read);

        AlertDialog dialog=new AlertDialog.Builder(a)
            .setTitle("ParcelGuard • ทดลอง OCR ตาชั่ง")
            .setView(scroll).setNegativeButton("ปิด",null).create();
        final Session[] running=new Session[1];
        dialog.setOnDismissListener(d->{
            if(running[0]!=null)running[0].cancel();
            preview.recycle();
            if(onExit!=null)onExit.run();
        });
        read.setOnClickListener(v->{
            if(running[0]!=null && running[0].busy)return;
            String selected=trackings.get(parcel.getSelectedItemPosition());
            ScaleWeightParser.Unit displayUnit=unit.getSelectedItemPosition()==0
                ?ScaleWeightParser.Unit.KG:ScaleWeightParser.Unit.G;
            settings.edit().putString("scale_ocr_unit",
                displayUnit==ScaleWeightParser.Unit.KG?"kg":"g").apply();
            read.setEnabled(false);parcel.setEnabled(false);unit.setEnabled(false);
            output.setText("กำลังอ่านน้ำหนัก... โปรดอย่าขยับกล่องหรือจอตาชั่ง");
            Session session=new Session(a,dialog,db,camera,preview,selected,displayUnit,
                read,parcel,unit,output,onSaved);
            running[0]=session;
            session.read();
        });
        dialog.show();
        return true;
    }

    static void manualEntry(Activity a,ParcelDb db,String tracking,Runnable done){
        ParcelDb.WeightRecord prior=db.weight(tracking);
        EditText field=new EditText(a);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setHint("เช่น 0.750 kg");
        if(prior.actual>0)field.setText(String.format(Locale.US,"%.3f",prior.actual/1000.0));
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,16),dp(a,8),dp(a,16),dp(a,4));
        content.addView(text(a,"กรอกน้ำหนักจากตาชั่งจริง หน่วยกิโลกรัม (kg)",14));
        content.addView(field);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("น้ำหนัก • "+tracking)
            .setView(content)
            .setNegativeButton("ยกเลิก",null)
            .setNeutralButton("ลบข้อมูล",(d,w)->{
                db.setWeight(tracking,null,null,"");
                if(done!=null)done.run();
            }).setPositiveButton("บันทึก",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                try{
                    int grams=fromKg(field.getText().toString());
                    if(!db.setWeight(tracking,grams,null,"manual"))
                        throw new IllegalArgumentException("บันทึกไม่ได้");
                    dialog.dismiss();
                    if(done!=null)done.run();
                }catch(Exception ex){field.setError("น้ำหนัก 0.001 ถึง 100.000 kg");}
            }));
        dialog.show();
    }

    static int fromKg(String input){
        String normalized=input.trim().replace(',','.');
        int result=new BigDecimal(normalized).multiply(new BigDecimal("1000"))
            .setScale(0,RoundingMode.HALF_UP).intValueExact();
        if(result<1||result>100000)throw new IllegalArgumentException("weight out of bounds");
        return result;
    }

    private static final class Session {
        private final Activity a;
        private final AlertDialog parent;
        private final ParcelDb db;
        private final PreviewView camera;
        private final RegionView region;
        private final String tracking;
        private final ScaleWeightParser.Unit unit;
        private final Button start;
        private final Spinner parcel,units;
        private final TextView output;
        private final Runnable onSaved;
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final TextRecognizer reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        private final List<ScaleWeightParser.Sample> samples=new ArrayList<>();
        boolean busy=true;
        private boolean cancelled=false;
        private int index=0;
        Session(Activity a,AlertDialog parent,ParcelDb db,PreviewView camera,RegionView region,
                String tracking,ScaleWeightParser.Unit unit,Button start,Spinner parcel,Spinner units,
                TextView output,Runnable onSaved){
            this.a=a;this.parent=parent;this.db=db;this.camera=camera;this.region=region;
            this.tracking=tracking;this.unit=unit;this.start=start;
            this.parcel=parcel;this.units=units;this.output=output;this.onSaved=onSaved;
        }
        void read(){
            if(cancelled||!parent.isShowing())return;
            if(index>=3){finish();return;}
            index++;
            Bitmap full=camera.getBitmap();
            if(full==null){
                samples.add(new ScaleWeightParser.Sample(-1,"NO_FRAME"));
                handler.postDelayed(this::read,600);
                return;
            }
            Bitmap cropped=null;
            try{cropped=region.crop(full);}catch(Exception ignored){}
            full.recycle();
            if(cropped==null){
                samples.add(new ScaleWeightParser.Sample(-1,"CROP_ERROR"));
                handler.postDelayed(this::read,600);return;
            }
            final Bitmap sampleBitmap=cropped;
            reader.process(InputImage.fromBitmap(sampleBitmap,0))
                .addOnSuccessListener(result->{
                    if(cancelled)return;
                    samples.add(ScaleWeightParser.parse(result.getText(),unit));
                    output.setText("อ่านครั้งที่ "+index+"/3 • "+
                        (samples.get(samples.size()-1).valid()
                        ?String.format(Locale.US,"%.3f kg",samples.get(samples.size()-1).grams/1000.0)
                        :"ยังอ่านไม่ได้"));
                })
                .addOnFailureListener(err->{
                    if(!cancelled)samples.add(new ScaleWeightParser.Sample(-1,"OCR_ERROR"));
                })
                .addOnCompleteListener(task->{
                    sampleBitmap.recycle();
                    if(!cancelled)handler.postDelayed(this::read,600);
                });
        }
        void finish(){
            if(cancelled)return;
            busy=false;
            start.setEnabled(true);parcel.setEnabled(true);units.setEnabled(true);
            reader.close();
            ScaleWeightParser.Stability stable=ScaleWeightParser.evaluate(samples);
            StringBuilder details=new StringBuilder("ค่าที่ OCR อ่านได้:\n");
            for(int i=0;i<samples.size();i++){
                ScaleWeightParser.Sample s=samples.get(i);
                details.append(i+1).append(". ").append(s.valid()
                    ?String.format(Locale.US,"%.3f kg",s.grams/1000.0):"อ่านไม่สำเร็จ")
                    .append("   [").append(s.raw==null?"":s.raw.replace('\n',' ')).append("]\n");
            }
            if(!stable.stable()){
                output.setText("ยังไม่พบค่าน้ำหนักที่นิ่งอย่างน้อย 2 ใน 3 ครั้ง • ลองจัดแสงหรือกรอบใหม่");
                new AlertDialog.Builder(a).setTitle("น้ำหนักยังไม่นิ่ง / OCR อ่านไม่ครบ")
                    .setMessage(details.toString()+"\nระบบยังไม่บันทึกน้ำหนัก")
                    .setNegativeButton("กลับไปอ่านใหม่",null)
                    .setPositiveButton("กรอกเอง",(d,w)->manualEntry(a,db,tracking,onSaved))
                    .show();
                return;
            }
            int rawGrams=stable.grams;
            output.setText("OCR อ่านได้นิ่ง: "+String.format(Locale.US,"%.3f kg",rawGrams/1000.0)+
                " • ต้องตรวจเทียบจอตาชั่งก่อนบันทึก");
            EditText verified=new EditText(a);
            verified.setSingleLine(true);
            verified.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            verified.setText(String.format(Locale.US,"%.3f",rawGrams/1000.0));
            LinearLayout layout=new LinearLayout(a);layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(dp(a,14),dp(a,8),dp(a,14),dp(a,4));
            layout.addView(text(a,"ค่าที่ OCR อ่านได้นิ่ง "+stable.goodReadings+"/"+stable.totalReadings+
                " ครั้ง = "+String.format(Locale.US,"%.3f kg",rawGrams/1000.0),14));
            layout.addView(text(a,"เทียบตัวเลขบนจอตาชั่งจริงทุกหลัก หากอ่านผิด ให้แก้เลขด้านล่างก่อนบันทึก",13));
            layout.addView(verified);
            AlertDialog confirmation=new AlertDialog.Builder(a)
                .setTitle("ยืนยันน้ำหนัก • "+tracking).setView(layout)
                .setNegativeButton("ไม่บันทึก",null)
                .setPositiveButton("ตรวจแล้ว • บันทึก",null).create();
            confirmation.setOnShowListener(d->confirmation.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v->{
                    try{
                        int actual=fromKg(verified.getText().toString());
                        String source=actual==rawGrams?"ocr_verified":"ocr_corrected";
                        if(!db.setWeight(tracking,actual,rawGrams,source))
                            throw new IllegalStateException("Tracking not found");
                        output.setText("บันทึกแล้ว "+String.format(Locale.US,"%.3f kg",actual/1000.0)+
                            ("ocr_corrected".equals(source)?" • แก้ค่า OCR":" • OCR ตรงตามจอ"));
                        confirmation.dismiss();
                        if(onSaved!=null)onSaved.run();
                    }catch(Exception e){verified.setError("กรอกตัวเลขน้ำหนักจริง 0.001–100.000 kg");}
                }));
            confirmation.show();
        }
        void cancel(){
            cancelled=true;handler.removeCallbacksAndMessages(null);
            reader.close();
        }
    }

    /** Tap to select a small region around the LCD digits, never the parcel label.
     *  The selection uses normalized coordinates so future preview frames align.
     */
    private static final class RegionView extends View {
        private Bitmap image;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private float x=0.5f,y=0.5f,w=0.55f,h=0.20f;
        private RectF imageOnScreen=new RectF();
        RegionView(Activity a,Bitmap bitmap){super(a);image=bitmap;}
        void replace(Bitmap bitmap){Bitmap previous=image;image=bitmap;
            if(previous!=null)previous.recycle();invalidate();}
        void recycle(){if(image!=null){image.recycle();image=null;}}
        void setFractionWidth(float width){w=Math.max(0.3f,Math.min(0.8f,width));
            h=Math.max(0.13f,Math.min(0.28f,w*0.36f));
            x=Math.max(w/2,Math.min(1-w/2,x));y=Math.max(h/2,Math.min(1-h/2,y));
            invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            c.drawColor(0xFF16202A);
            if(image==null)return;
            float scale=Math.min(getWidth()/(float)image.getWidth(),
                getHeight()/(float)image.getHeight());
            float drawW=image.getWidth()*scale,drawH=image.getHeight()*scale;
            imageOnScreen.set((getWidth()-drawW)/2,(getHeight()-drawH)/2,
                (getWidth()+drawW)/2,(getHeight()+drawH)/2);
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
            c.drawBitmap(image,null,imageOnScreen,paint);
            RectF region=new RectF(imageOnScreen.left+(x-w/2)*imageOnScreen.width(),
                imageOnScreen.top+(y-h/2)*imageOnScreen.height(),
                imageOnScreen.left+(x+w/2)*imageOnScreen.width(),
                imageOnScreen.top+(y+h/2)*imageOnScreen.height());
            paint.setColor(0xFFFF9200);paint.setStrokeWidth(4f);
            paint.setStyle(Paint.Style.STROKE);
            c.drawRect(region,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent ev){
            if(ev.getAction()==MotionEvent.ACTION_UP && imageOnScreen.width()>0){
                x=(ev.getX()-imageOnScreen.left)/imageOnScreen.width();
                y=(ev.getY()-imageOnScreen.top)/imageOnScreen.height();
                x=Math.max(w/2,Math.min(1-w/2,x));
                y=Math.max(h/2,Math.min(1-h/2,y));
                invalidate();performClick();return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        Bitmap crop(Bitmap fresh){
            int width=fresh.getWidth(),height=fresh.getHeight();
            int left=Math.max(0,(int)((x-w/2)*width));
            int top=Math.max(0,(int)((y-h/2)*height));
            int right=Math.min(width,(int)((x+w/2)*width));
            int bottom=Math.min(height,(int)((y+h/2)*height));
            if(right-left<10||bottom-top<10)return null;
            return Bitmap.createBitmap(fresh,left,top,right-left,bottom-top);
        }
    }
    private ScaleOcrTrial(){}
}
