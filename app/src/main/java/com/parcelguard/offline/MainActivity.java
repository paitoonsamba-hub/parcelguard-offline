package com.parcelguard.offline;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.media.Image;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.video.*;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

public class MainActivity extends AppCompatActivity {
    private ParcelDb db;
    private BarcodeScanner scanner;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean working = new AtomicBoolean(false);
    private ProcessCameraProvider cameraProvider;
    private androidx.camera.core.Camera boundCamera;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private boolean videoSupported = false;
    private boolean recordingStarting = false;
    private long recordingStartTime = 0;
    private String recordingBag = "";
    private boolean torch = false;
    private boolean active = true;
    private String bag = "", carrier = "SPX";
    private String pendingCode = "", lastRecorded = "";
    private int pendingHits = 0, missingFrames = 0;
    private boolean armed = true;
    private long lastCaptureAt = 0;
    private boolean enableVideo = false;
    private final ToneGenerator tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
    private TextToSpeech savedSpeech;
    private volatile boolean thaiOfflineVoiceReady = false;
    private boolean speakAfterSave = true;
    private PreviewView preview;
    private TextView status, count, bagLabel, liveTracking, goalLabel;
    private ProgressBar bagProgress;
    private EditText query, expectedEntry, dateFrom, dateTo;
    private LinearLayout root;
    private final int BG = 0xFF0A0F15;
    private final int PANEL = 0xFF161E26;
    private final int PANEL2 = 0xFF202A34;
    private final int ORANGE = 0xFFFF9009;
    private final int WHITE = Color.WHITE;
    private final int MUTED = 0xFFAFBAC6;
    private final int GREEN = 0xFF12C987;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        db = new ParcelDb(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        speakAfterSave = getPreferences(0).getBoolean("voice_on_save", true);
        initSavedSpeech();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_QR_CODE, Barcode.FORMAT_EAN_13, Barcode.FORMAT_ITF).build());
        enableVideo = getPreferences(0).getBoolean("video", false);
        cleanupTrash();
        cleanupVideos();
        String previous=db.latestOpenBag();
        if(previous!=null){bag=previous;carrier=db.bagCarrier(previous);}
        else createNewBag(false);
        showScan();
    }

    private TextView text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size);
        v.setTextColor(WHITE); v.setPadding(dp(8), dp(7), dp(8), dp(7));
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private int dp(int value) {return (int) (value * getResources().getDisplayMetrics().density + 0.5f);}
    private GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(border!=0)d.setStroke(dp(1),border);return d;
    }
    private LinearLayout card(int inset) {
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(inset),dp(inset),dp(inset),dp(inset));
        c.setBackground(shape(PANEL,0xFF35414D,16));return c;
    }
    private LinearLayout horizontal() {LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout.LayoutParams spaced(int height,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.topMargin=dp(top);return p;
    }
    private TextView prominent(String value,int size,int color) {
        TextView t=text(value,size);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setTextColor(color);return t;
    }
    private Button button(String name, Runnable action) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setTextSize(14);
        b.setTextColor(WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(shape(PANEL2,0xFF435261,12));b.setBackgroundTintList(null);b.setOnClickListener(v->action.run());
        b.setPadding(dp(8),dp(8),dp(8),dp(8));return b;
    }
    private Button orangeButton(String name,Runnable action){Button b=button(name,action);b.setTextColor(0xFF17191B);b.setBackground(shape(ORANGE,0,12));b.setBackgroundTintList(null);return b;}
    private void addButton(String name,Runnable action){root.addView(button(name,action),spaced(48,8));}
    private void base(String subtitle) {
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(BG);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout top=horizontal();top.setPadding(dp(16),dp(10),dp(12),dp(7));
        ImageView logo=new ImageView(this);logo.setImageResource(R.mipmap.ic_launcher);
        top.addView(logo,new LinearLayout.LayoutParams(dp(45),dp(45)));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);
        TextView title=prominent("ParcelGuard",23,WHITE);
        SpannableString colored=new SpannableString("ParcelGuard");colored.setSpan(new ForegroundColorSpan(ORANGE),6,11,0);title.setText(colored);
        brand.addView(title);
        TextView sub=text("ระบบตรวจสอบการแพ็คพัสดุ",11);sub.setTextColor(MUTED);sub.setPadding(dp(8),0,0,0);brand.addView(sub);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("⚙",this::showSettings);settings.setTextSize(23);
        settings.setBackground(shape(BG,0,10));top.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46)));
        shell.addView(top,new LinearLayout.LayoutParams(-1,dp(72)));
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(false);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(8),dp(14),dp(22));sc.addView(root);
        shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=horizontal();bottom.setPadding(dp(7),dp(7),dp(7),dp(9));bottom.setBackground(shape(0xFF0E151D,0xFF263441,0));
        String[] names={"⌂  แพ็คพัสดุ","▤  ประวัติ","▥  รายงาน","⚙  ตั้งค่า"};
        Runnable[] actions={this::showScan,this::showHistory,this::showReports,this::showSettings};
        for(int i=0;i<names.length;i++){
            Button nav=button(names[i],actions[i]);nav.setTextSize(11);nav.setPadding(0,0,0,0);
            nav.setTextColor((i==0 && subtitle.contains("ถุง")) ? ORANGE : MUTED);
            nav.setBackground(shape(0xFF111A23,0,9));
            LinearLayout.LayoutParams navP=new LinearLayout.LayoutParams(0,dp(48),1);navP.setMargins(dp(2),0,dp(2),0);
            bottom.addView(nav,navP);
        }
        shell.addView(bottom,new LinearLayout.LayoutParams(-1,dp(66)));
        setContentView(shell);
        if(!subtitle.isEmpty() && !subtitle.contains("ถุงใหญ่")) {
            TextView heading=prominent(subtitle,18,WHITE);root.addView(heading,spaced(-2,2));
        }
    }
    private void showReports(){
        pauseCamera();base("รายงานและหลักฐาน");
        LinearLayout summary=card(14);summary.addView(prominent("รายงานการแพ็ค",18,ORANGE));
        StringBuilder lines=new StringBuilder();int totalBags=0,totalScans=0;
        try(Cursor c=db.bagList()){
            while(c.moveToNext()){totalBags++;int count=db.count(c.getString(0));totalScans+=count;
                lines.append(c.getString(0)).append("  •  ").append(count).append(" ชิ้น")
                    .append(c.getInt(2)==1?"  [ปิด]":"  [เปิด]").append("\n");}
        }
        summary.addView(prominent(totalBags+" ถุง  •  "+totalScans+" พัสดุ",21,WHITE));
        root.addView(summary,spaced(-2,10));
        root.addView(prominent("รายการถุง",16,WHITE),spaced(-2,14));
        TextView all=text(lines.length()==0?"ยังไม่มีข้อมูล":lines.toString(),13);all.setTextColor(MUTED);root.addView(all);
        addButton("ส่งออก ZIP หลักฐานทั้งหมด",()->exportZip(null));
        addButton("ส่งออก ZIP ถุงปัจจุบัน",()->exportZip(bag));
        addButton("ดูสถานะถุงทั้งหมด",this::showBags);
        root.addView(text("ข้อมูลอยู่ในมือถือเครื่องนี้เท่านั้น • ควรสำรองหลักฐานเป็นประจำ",12));
    }
    private void pauseCamera() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        boundCamera = null; videoCapture = null;
        if (recording != null) {recording.stop(); recording = null;}
        working.set(false); active = false;
    }
    private void showScan() {
        if(root!=null)pauseCamera();active=true;
        base("ตรวจพัสดุก่อนใส่ถุงใหญ่");
        LinearLayout intro=card(12);
        LinearLayout introRow=horizontal();
        TextView scanMark=prominent("▥",28,ORANGE);introRow.addView(scanMark,new LinearLayout.LayoutParams(dp(45),-2));
        LinearLayout introText=new LinearLayout(this);introText.setOrientation(LinearLayout.VERTICAL);
        introText.addView(prominent("สแกนก่อนใส่ถุง",19,WHITE));
        TextView introSub=text("สแกนบาร์โค้ดเพื่อบันทึกภาพหลักฐาน",12);introSub.setTextColor(MUTED);introText.addView(introSub);
        introRow.addView(introText,new LinearLayout.LayoutParams(0,-2,1));intro.addView(introRow);
        root.addView(intro,spaced(-2,3));

        LinearLayout current=card(12);
        LinearLayout bagRow=horizontal();
        LinearLayout bagInfo=new LinearLayout(this);bagInfo.setOrientation(LinearLayout.VERTICAL);
        bagLabel=prominent("",14,WHITE);bagLabel.setSingleLine(true);bagLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        bagInfo.addView(bagLabel);
        TextView service=text("ขนส่งสำหรับถุงใหม่",12);service.setTextColor(MUTED);bagInfo.addView(service);
        bagRow.addView(bagInfo,new LinearLayout.LayoutParams(0,-2,1));
        Spinner carrierSelector=new Spinner(this);
        String[] options={"SPX","J&T","Flash","LEX","Other"};
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options){
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){
                TextView v=text(getItem(position),15);v.setTextColor(ORANGE);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                v.setGravity(Gravity.CENTER);v.setBackground(shape(PANEL2,0xFF53606E,8));return v;
            }
        };
        carrierSelector.setAdapter(adapter);
        for(int i=0;i<options.length;i++)if(options[i].equals(carrier))carrierSelector.setSelection(i);
        carrierSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> a){}
            public void onItemSelected(android.widget.AdapterView<?> a,View v,int i,long id){carrier=options[i];}
        });
        bagRow.addView(carrierSelector,new LinearLayout.LayoutParams(dp(87),dp(45)));
        current.addView(bagRow);
        LinearLayout countRow=horizontal();
        count=prominent("",22,ORANGE);countRow.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        goalLabel=text("",12);goalLabel.setTextColor(MUTED);countRow.addView(goalLabel);
        current.addView(countRow);
        bagProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bagProgress.setMax(100);bagProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        bagProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF38434F));
        current.addView(bagProgress,spaced(6,3));
        root.addView(current,spaced(-2,10));

        LinearLayout actions=horizontal();
        Button open=orangeButton("✚  เปิดถุงใหม่",()->createNewBag(true));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(56),1);a.rightMargin=dp(5);actions.addView(open,a);
        Button close=button("▣  ปิดถุง",this::confirmClose);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(56),1);b.leftMargin=dp(5);actions.addView(close,b);
        root.addView(actions,spaced(-2,10));

        LinearLayout target=card(10);
        LinearLayout targetRow=horizontal();
        TextView targetLabel=text("เป้าหมาย (ชิ้น)",13);targetLabel.setTextColor(MUTED);
        targetRow.addView(targetLabel,new LinearLayout.LayoutParams(0,-2,1));
        expectedEntry=new EditText(this);expectedEntry.setSingleLine(true);expectedEntry.setInputType(InputType.TYPE_CLASS_NUMBER);
        expectedEntry.setTextColor(WHITE);expectedEntry.setTextSize(17);expectedEntry.setGravity(Gravity.CENTER);
        expectedEntry.setBackground(shape(0xFF0D141B,0xFF485562,9));expectedEntry.setHintTextColor(MUTED);
        expectedEntry.setText(String.valueOf(db.expected(bag)));targetRow.addView(expectedEntry,new LinearLayout.LayoutParams(dp(68),dp(42)));
        Button saveGoal=button("ตั้งค่า",()->{try{int n=Integer.parseInt(expectedEntry.getText().toString().trim());
            if(n<0)throw new NumberFormatException();db.setExpected(bag,n);updateCount();message("ตั้งเป้าหมายแล้ว "+n+" ชิ้น");
        }catch(NumberFormatException e){message("ระบุจำนวนเต็มตั้งแต่ 0 ขึ้นไป");}});
        LinearLayout.LayoutParams sg=new LinearLayout.LayoutParams(dp(80),dp(42));sg.leftMargin=dp(8);targetRow.addView(saveGoal,sg);
        target.addView(targetRow);root.addView(target,spaced(-2,10));

        LinearLayout cameraCard=card(8);
        LinearLayout camHead=horizontal();
        TextView label=prominent("▣  กล้องตรวจพัสดุ",16,WHITE);camHead.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView ready=text("● อัตโนมัติ",11);ready.setTextColor(GREEN);camHead.addView(ready);
        cameraCard.addView(camHead);
        FrameLayout cameraBox=new FrameLayout(this);
        preview=new PreviewView(this);preview.setBackgroundColor(Color.BLACK);
        cameraBox.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        View corners=new View(this){ Paint line=new Paint(3);@Override protected void onDraw(Canvas c){super.onDraw(c);
            float d=dp(16),length=dp(26),w=getWidth(),h=getHeight();line.setColor(WHITE);line.setStrokeWidth(dp(3));
            line.setStrokeCap(Paint.Cap.ROUND);line.setStyle(Paint.Style.STROKE);
            c.drawLine(d,d,d+length,d,line);c.drawLine(d,d,d,d+length,line);
            c.drawLine(w-d,d,w-d-length,d,line);c.drawLine(w-d,d,w-d,d+length,line);
            c.drawLine(d,h-d,d+length,h-d,line);c.drawLine(d,h-d,d,h-d-length,line);
            c.drawLine(w-d,h-d,w-d-length,h-d,line);c.drawLine(w-d,h-d,w-d,h-d-length,line);
        }};
        cameraBox.addView(corners,new FrameLayout.LayoutParams(-1,-1));
        liveTracking=text("Tracking: —",14);liveTracking.setTextColor(WHITE);
        liveTracking.setBackground(shape(0xDD111820,0,7));
        FrameLayout.LayoutParams tr=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        tr.setMargins(dp(12),dp(14),dp(12),0);cameraBox.addView(liveTracking,tr);
        cameraCard.addView(cameraBox,spaced(310,8));
        status=prominent("กำลังเปิดกล้อง...",13,WHITE);
        status.setBackground(shape(0xFF263541,0,10));
        cameraCard.addView(status,spaced(-2,9));
        LinearLayout controls=horizontal();
        Button light=button("ϟ  ไฟฉาย",()->{torch=!torch;if(boundCamera!=null&&boundCamera.getCameraInfo().hasFlashUnit())
            boundCamera.getCameraControl().enableTorch(torch);});
        controls.addView(light,new LinearLayout.LayoutParams(0,dp(61),1));
        Button shutter=orangeButton("▣  ถ่ายภาพ",this::manualCaptureDialog);shutter.setTextSize(16);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(61),1.2f);cp.setMargins(dp(7),0,dp(7),0);controls.addView(shutter,cp);
        Button vid=button("▣  วิดีโอ",()->{if(recording!=null)stopVideo();else startVideo();});
        controls.addView(vid,new LinearLayout.LayoutParams(0,dp(61),1));
        cameraCard.addView(controls,spaced(-2,10));
        root.addView(cameraCard,spaced(-2,12));
        addButton("▶  ดู / แชร์ / จัดการวิดีโอถุงปัจจุบัน",this::manageCurrentBagVideo);
        TextView note=text("กล้องสแกนอัตโนมัติเมื่อวางพัสดุใต้กล้อง • มีเสียง 'บันทึกแล้ว' เมื่อเก็บหลักฐานสำเร็จ • โหมดวิดีโอเปิดได้ในตั้งค่า",12);
        note.setTextColor(MUTED);root.addView(note,spaced(-2,8));
        updateCount();
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},12);
    }
    private void updateCount(){
        int n=db.count(bag),goal=db.expected(bag);
        if(count!=null)count.setText("สแกน  "+n+"  ชิ้น");
        if(goalLabel!=null)goalLabel.setText(goal>0?"เป้าหมาย "+goal:"ไม่กำหนดเป้าหมาย");
        if(bagProgress!=null)bagProgress.setProgress(goal>0?Math.min(100,n*100/goal):0);
        if(bagLabel!=null)bagLabel.setText("ถุง: "+bag+(db.isClosed(bag)?" [ปิด]":" [เปิด]"));
    }
    /** Manual evidence capture is a fallback for labels that cameras cannot decode. */
    private void manualCaptureDialog(){
        if(db.isClosed(bag)){message("ถุงปิดแล้ว • เปิดถุงใหม่ก่อน");return;}
        final EditText field=new EditText(this);field.setSingleLine(true);field.setTextColor(WHITE);
        field.setHint("กรอก Tracking ก่อนถ่ายภาพ");field.setHintTextColor(MUTED);
        new AlertDialog.Builder(this).setTitle("ถ่ายภาพด้วยมือ")
            .setMessage("ใช้เมื่อสแกนบาร์โค้ดไม่ติด • ต้องกรอกเลขพัสดุจริงให้ถูกต้อง")
            .setView(field).setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ถ่ายและบันทึก",(dialog,which)->{
                String code=field.getText().toString().trim().toUpperCase(Locale.US);
                if(!code.matches("[A-Z0-9_-]{6,80}")){message("เลขพัสดุไม่ถูกต้อง");return;}
                if(db.exists(code)){message("เลขพัสดุซ้ำ: "+code);tones.startTone(ToneGenerator.TONE_PROP_NACK,180);return;}
                Bitmap shot=preview==null?null:preview.getBitmap();
                if(shot==null){message("กล้องยังไม่พร้อมถ่ายภาพ");return;}
                String bagAtCapture=bag;
                ioExecutor.execute(()->saveManualEvidence(code,bagAtCapture,shot));
            }).show();
    }
    private void saveManualEvidence(String code,String targetBag,Bitmap shot){
        File photo=null;
        try{
            if(db.isClosed(targetBag))throw new IOException("ถุงปิดแล้ว");
            Bitmap marked=shot.copy(Bitmap.Config.ARGB_8888,true);shot.recycle();
            Canvas canvas=new Canvas(marked);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xD9000000);canvas.drawRect(0,0,marked.getWidth(),dp(110),p);
            p.setColor(WHITE);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(dp(17));canvas.drawText("TRACKING: "+code,dp(12),dp(31),p);
            p.setTextSize(dp(12));canvas.drawText(targetBag,dp(12),dp(59),p);
            canvas.drawText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()),dp(12),dp(83),p);
            File folder=new File(getFilesDir(),"photos");if(!folder.exists()&&!folder.mkdirs())throw new IOException("สร้างโฟลเดอร์รูปไม่ได้");
            photo=new File(folder,UUID.randomUUID()+".jpg");
            try(FileOutputStream f=new FileOutputStream(photo)){
                if(!marked.compress(Bitmap.CompressFormat.JPEG,82,f))throw new IOException("บันทึกไฟล์ภาพผิดพลาด");
            }
            marked.recycle();
            boolean ok=db.addParcel(code,targetBag,photo.getAbsolutePath(),-1);
            if(!ok){photo.delete();runOnUiThread(()->message("เลขซ้ำหรือบันทึกไม่สำเร็จ: "+code));return;}
            runOnUiThread(()->{updateCount();message("✓ บันทึกภาพด้วยมือแล้ว: "+code);announceSaved();});
        }catch(Exception e){if(photo!=null)photo.delete();runOnUiThread(()->message("ถ่ายภาพผิดพลาด: "+e.getMessage()));}
    }
    private boolean createNewBag(boolean withConfirm) {
        if(withConfirm && !db.isClosed(bag) && db.count(bag)>0) {
            new AlertDialog.Builder(this).setTitle("ถุงเดิมยังไม่ปิด")
                .setMessage("ต้องการเปิดถุงใหม่โดยปล่อยให้ถุงเดิมยังเปิดอยู่หรือไม่?")
                .setNegativeButton("ยกเลิก", null).setPositiveButton("เปิดถุงใหม่", (d,w)->createNewBag(false)).show();
            return false;
        }
        if(recording!=null) stopVideo();
        String newId="BAG-"+new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        if(!db.addBag(newId, carrier)) {message("สร้างถุงไม่สำเร็จ");return false;}
        bag=newId; lastRecorded=""; armed=true; pendingCode=""; pendingHits=0; missingFrames=0;
        if(expectedEntry!=null) expectedEntry.setText("0"); updateCount();
        return true;
    }
    private void confirmClose() {
        if(db.isClosed(bag)) {message("ถุงนี้ถูกปิดแล้ว");return;}
        int n=db.count(bag), expected=db.expected(bag);
        String warning=expected>0 && n!=expected ? "⚠ จำนวนไม่ตรงเป้าหมาย! ควรมี "+expected+" ชิ้น แต่สแกน "+n+" ชิ้น\n\n" : "";
        new AlertDialog.Builder(this).setTitle("ยืนยันปิดถุง")
            .setMessage(warning+bag+"\nจำนวนที่บันทึก: "+n+" ชิ้น\nตรวจสอบว่าถุงซีลแล้ว")
            .setNegativeButton("กลับไปตรวจ",null)
            .setPositiveButton("ยืนยันปิด",(d,w)->{if(recording!=null)stopVideo();db.closeBag(bag);updateCount();message("ปิดถุงแล้ว • "+n+" ชิ้น");}).show();
    }
    private void message(String s) {
        if(status!=null && active){
            status.setText(s);
            int bg=(s.contains("✓") || s.contains("บันทึกแล้ว") || s.contains("พร้อม"))?0xFF0E533E:
                ((s.contains("ผิด") || s.contains("ซ้ำ") || s.contains("ไม่ได้") || s.contains("ปิดแล้ว"))?0xFF642525:0xFF263541);
            status.setBackground(shape(bg,0,10));
        } else Toast.makeText(this,s,Toast.LENGTH_LONG).show();
    }

    /** Thai text-to-speech is selected ONLY if an offline Thai voice is installed.
     *  Fallback is a short beep; no network is needed during packing.
     */
    private void initSavedSpeech() {
        try {
            savedSpeech = new TextToSpeech(getApplicationContext(), result -> {
                if (result != TextToSpeech.SUCCESS || savedSpeech == null) return;
                try {
                    savedSpeech.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    savedSpeech.setSpeechRate(1.0f);
                    int language = savedSpeech.setLanguage(new Locale("th", "TH"));
                    if (language < TextToSpeech.LANG_AVAILABLE) return;
                    Set<Voice> voices = savedSpeech.getVoices();
                    if (voices == null) return;
                    for (Voice voice : voices) {
                        if (voice.getLocale() != null
                                && "th".equals(voice.getLocale().getLanguage())
                                && !voice.isNetworkConnectionRequired()
                                && savedSpeech.setVoice(voice) == TextToSpeech.SUCCESS) {
                            thaiOfflineVoiceReady = true;
                            break;
                        }
                    }
                } catch (Exception ignored) { thaiOfflineVoiceReady = false; }
            });
        } catch (Exception ignored) { thaiOfflineVoiceReady = false; }
    }

    /** Call only after BOTH the JPEG and the database row were committed. */
    private void announceSaved() {
        if (speakAfterSave && thaiOfflineVoiceReady && savedSpeech != null) {
            try {
                int result = savedSpeech.speak("บันทึกแล้ว", TextToSpeech.QUEUE_FLUSH,
                        null, "parcelguard_saved_" + System.currentTimeMillis());
                if (result == TextToSpeech.SUCCESS) return;
            } catch (Exception ignored) { /* Fall back to beep */ }
        }
        tones.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            try {
                cameraProvider=future.get(); cameraProvider.unbindAll();
                Preview pv=new Preview.Builder().build(); pv.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new android.util.Size(1280,720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(cameraExecutor,this::analyze);
                videoSupported=false; videoCapture=null;
                if(enableVideo) {
                    try {
                        Recorder recorder=new Recorder.Builder().setQualitySelector(
                            QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build();
                        VideoCapture<Recorder> capture=VideoCapture.withOutput(recorder);
                        boundCamera=cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,pv,analysis,capture);
                        videoCapture=capture; videoSupported=true;
                    }catch(Exception ex){
                        cameraProvider.unbindAll();
                        boundCamera=cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,pv,analysis);
                    }
                }else boundCamera=cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,pv,analysis);
                if(torch && boundCamera.getCameraInfo().hasFlashUnit()) boundCamera.getCameraControl().enableTorch(true);
                message("กล้องพร้อม • สแกนอัตโนมัติ"+(videoSupported?" • วิดีโอพร้อม":""));
            }catch(Exception e){message("เปิดกล้องไม่สำเร็จ: "+e.getMessage());}
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyze(ImageProxy frame) {
        if(!active || !working.compareAndSet(false,true)) {frame.close();return;}
        Image media=frame.getImage();
        if(media==null) {working.set(false);frame.close();return;}
        InputImage input=InputImage.fromMediaImage(media, frame.getImageInfo().getRotationDegrees());
        scanner.process(input).addOnSuccessListener(cameraExecutor,codes->{
            String value="";
            for(Barcode bc: codes) {
                String raw=bc.getRawValue();
                if(raw!=null) {
                    String candidate=raw.trim().toUpperCase(Locale.US);
                    if(candidate.matches("[A-Z0-9_-]{6,80}")) {value=candidate;break;}
                }
            }
            if(value.isEmpty()) {
                missingFrames++;
                if(missingFrames>=4) {armed=true;pendingHits=0;pendingCode="";}
            }else {
                missingFrames=0;
                final String scanned=value;
                runOnUiThread(()->{if(liveTracking!=null && active)liveTracking.setText("Tracking: "+scanned);});
                if(!value.equals(pendingCode)) {pendingCode=value;pendingHits=1;}
                else pendingHits++;
                if(armed && pendingHits>=2 && System.currentTimeMillis()-lastCaptureAt>650) {
                    armed=false; lastRecorded=value;lastCaptureAt=System.currentTimeMillis();
                    saveEvidence(value,frame);
                }
            }
        }).addOnFailureListener(cameraExecutor,e->runOnUiThread(()->message("อ่านบาร์โค้ดไม่สำเร็จ: "+e.getMessage())))
          .addOnCompleteListener(cameraExecutor, task->{working.set(false);frame.close();});
    }
    @androidx.camera.core.ExperimentalGetImage
    private void saveEvidence(String tracking, ImageProxy frame) {
        if(db.isClosed(bag)) {runOnUiThread(()->message("ถุงปิดแล้ว • เปิดถุงใหม่ก่อน"));return;}
        if(db.exists(tracking)) {runOnUiThread(()->{tones.startTone(ToneGenerator.TONE_PROP_NACK,180);message("เลขพัสดุซ้ำ: "+tracking);});return;}
        File photo=null;
        try {
            android.os.StatFs stat = new android.os.StatFs(getFilesDir().getAbsolutePath());
            if(stat.getAvailableBytes()<100L*1024*1024) throw new IOException("พื้นที่เหลือน้อยกว่า 100MB: ย้ายข้อมูลออกก่อน");
            Image img=frame.getImage(); if(img==null)throw new IOException("ไม่มีภาพจากกล้อง");
            int w=img.getWidth(),h=img.getHeight();
            byte[] nv21=toNV21(img.getPlanes(),w,h);
            YuvImage yuv=new YuvImage(nv21,ImageFormat.NV21,w,h,null);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!yuv.compressToJpeg(new Rect(0,0,w,h),85,out))throw new IOException("แปลงภาพไม่สำเร็จ");
            Bitmap raw=BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size());
            if(raw==null)throw new IOException("อ่านภาพไม่สำเร็จ");
            Matrix matrix=new Matrix();matrix.postRotate(frame.getImageInfo().getRotationDegrees());
            Bitmap rotated=Bitmap.createBitmap(raw,0,0,raw.getWidth(),raw.getHeight(),matrix,true);
            if(rotated!=raw)raw.recycle();
            Bitmap marked=rotated.copy(Bitmap.Config.ARGB_8888,true); rotated.recycle();
            Canvas canvas=new Canvas(marked);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xC9000000);canvas.drawRect(0,0,marked.getWidth(),dp(110),p);
            p.setColor(WHITE);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(dp(19));canvas.drawText("TRACKING: "+tracking,dp(14),dp(31),p);
            p.setTextSize(dp(14));canvas.drawText(bag,dp(14),dp(62),p);
            canvas.drawText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()),dp(14),dp(89),p);
            File dir=new File(getFilesDir(),"photos");if(!dir.exists() && !dir.mkdirs())throw new IOException("สร้างโฟลเดอร์รูปไม่ได้");
            photo=new File(dir,UUID.randomUUID().toString()+".jpg");
            try(FileOutputStream fos=new FileOutputStream(photo)) {if(!marked.compress(Bitmap.CompressFormat.JPEG,83,fos))throw new IOException("บันทึก JPEG ไม่สำเร็จ");}
            marked.recycle();
            long offset=recording!=null && recordingBag.equals(bag)?Math.max(0,System.currentTimeMillis()-recordingStartTime):-1;
            boolean ok=db.addParcel(tracking,bag,photo.getAbsolutePath(),offset);
            if(!ok) {photo.delete(); runOnUiThread(()->message("บันทึกฐานข้อมูลไม่สำเร็จ: "+tracking));return;}
            runOnUiThread(()->{updateCount();message("✓ บันทึกภาพ: "+tracking+" • ใส่ถุงได้");announceSaved();});
        }catch(Exception e){if(photo!=null)photo.delete();runOnUiThread(()->message("ถ่ายภาพผิดพลาด: "+e.getMessage()));}
    }
    private byte[] toNV21(Image.Plane[] planes,int width,int height) {
        byte[] nv21=new byte[width*height*3/2];
        for(int plane=0;plane<3;plane++) {
            ByteBuffer b=planes[plane].getBuffer();int row=planes[plane].getRowStride();int pixel=planes[plane].getPixelStride();
            int pw=plane==0?width:width/2, ph=plane==0?height:height/2;
            for(int y=0;y<ph;y++)for(int x=0;x<pw;x++) {
                int index=y*row+x*pixel;if(index>=b.limit())continue;
                byte v=b.get(index);
                if(plane==0)nv21[y*width+x]=v;
                else {int idx=width*height+(y*pw+x)*2+(plane==1?1:0);if(idx<nv21.length)nv21[idx]=v;}
            }
        }
        return nv21;
    }

    /** Allows access to an existing per-bag video without leaving the scanning screen.
     *  Never deletes or overwrites video evidence automatically.
     */
    private void manageCurrentBagVideo() {
        if (recording != null || recordingStarting) {
            message("กำลังบันทึกวิดีโออยู่ กรุณาหยุดและรอปิดไฟล์ก่อน");
            return;
        }
        String path = db.videoPath(bag);
        File file = path == null || path.isEmpty() ? null : new File(path);
        if (file == null || !file.isFile()) {
            message("ถุงนี้ยังไม่มีไฟล์วิดีโอที่เปิดได้ • ลองเริ่มบันทึกวิดีโอ");
            return;
        }
        String currentBag = bag;
        new AlertDialog.Builder(this)
            .setTitle("พบวิดีโอของถุงนี้แล้ว")
            .setMessage(currentBag + "\nขนาด " + (file.length() / (1024 * 1024)) + " MB\n\n"
                    + "แอปรุ่นนี้รองรับ 1 คลิปต่อถุง ป้องกันการบันทึกทับหลักฐานเดิม")
            .setItems(new String[]{"▶ ดูวิดีโอเดิม", "↗ แชร์วิดีโอเพื่อสำรอง", "ลบคลิปเก่าเพื่อเริ่มบันทึกใหม่"}, (dialog, choice) -> {
                if (choice == 0) {
                    playVideo(currentBag, 0);
                } else if (choice == 1) {
                    shareFile(file, "video/mp4");
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("ลบหลักฐานวิดีโอเก่า?")
                        .setMessage("ควรแชร์และสำรองไฟล์เก่าก่อนลบ\n\n"
                            + "การลบถาวรจะกู้คลิปเดิมไม่ได้ และเวลาวิดีโอที่เชื่อมกับ Tracking เดิมจะใช้อ้างอิงไม่ได้อีก\n\n"
                            + "รูปถ่ายและเลข Tracking จะยังคงอยู่")
                        .setNegativeButton("ยกเลิก", null)
                        .setPositiveButton("ยืนยันลบคลิปเก่า", (d, whichButton) -> {
                            if (recording != null || recordingStarting || !currentBag.equals(bag)) {
                                message("สถานะถุงเปลี่ยน กรุณาลองใหม่");
                                return;
                            }
                            if (file.delete()) {
                                db.clearVideo(currentBag);
                                message("ลบวิดีโอเดิมแล้ว กดเริ่มบันทึกเพื่อถ่ายใหม่");
                            } else {
                                message("ลบคลิปไม่สำเร็จ กรุณาสำรองแล้วลองใหม่");
                            }
                        }).show();
                }
            })
            .setNegativeButton("ปิด", null)
            .show();
    }

    private void startVideo() {
        if(db.isClosed(bag)){message("ต้องเปิดถุงใหม่ก่อน");return;}
        String prior=db.videoPath(bag);
        if(prior!=null && !prior.isEmpty() && new File(prior).isFile()){
            manageCurrentBagVideo();
            return;
        }
        if(!enableVideo){message("เปิดการบันทึกวิดีโอในหน้าตั้งค่าก่อน");return;}
        if(!videoSupported || videoCapture==null){message("อุปกรณ์ไม่รองรับบันทึกวิดีโอพร้อมสแกน • ภาพถ่ายยังทำงานได้");return;}
        if(recording!=null || recordingStarting)return;
        try {
            File dir=new File(getFilesDir(),"videos");if(!dir.exists() && !dir.mkdirs())throw new IOException("สร้างโฟลเดอร์วิดีโอไม่ได้");
            File video=new File(dir,bag+".mp4");
            recordingStarting=true; recordingBag=bag;
            final String targetBag=bag;
            PendingRecording pending=videoCapture.getOutput().prepareRecording(this,new FileOutputOptions.Builder(video).build());
            recording=pending.start(ContextCompat.getMainExecutor(this), event->{
                if(event instanceof VideoRecordEvent.Start) {
                    recordingStarting=false; recordingStartTime=System.currentTimeMillis();
                    db.setVideo(targetBag, video.getAbsolutePath(), recordingStartTime);
                    message("● กำลังบันทึกวิดีโอถุงนี้");
                } else if(event instanceof VideoRecordEvent.Finalize) {
                    VideoRecordEvent.Finalize fin=(VideoRecordEvent.Finalize)event;
                    if(recordingBag.equals(targetBag)){recordingStarting=false;recording=null;}
                    if(fin.hasError()) {
                        db.setVideo(targetBag,"",0);
                        message("บันทึกวิดีโอไม่สำเร็จ (ภาพถ่ายยังอยู่): "+fin.getError());
                    }else message("บันทึกวิดีโอเสร็จ • "+targetBag);
                }
            });
        }catch(Exception e){recordingStarting=false;message("เปิดบันทึกวิดีโอไม่ได้: "+e.getMessage());}
    }
    private void stopVideo() {if(recording!=null){recording.stop();recording=null;message("กำลังปิดไฟล์วิดีโอ...");}}

    private void showHistory(){pauseCamera();base("ค้นหา Tracking / รหัสถุง และดูหลักฐานย้อนหลัง");
        query=new EditText(this);query.setTextColor(WHITE);query.setSingleLine(true);query.setHintTextColor(Color.LTGRAY);query.setHint("Tracking บางส่วนหรือรหัสถุง");root.addView(query);
        dateFrom=new EditText(this); dateFrom.setTextColor(WHITE);dateFrom.setHintTextColor(Color.LTGRAY);dateFrom.setSingleLine(true);
        dateFrom.setHint("จากวันที่ YYYY-MM-DD (เว้นว่างได้)");root.addView(dateFrom);
        dateTo=new EditText(this); dateTo.setTextColor(WHITE);dateTo.setHintTextColor(Color.LTGRAY);dateTo.setSingleLine(true);
        dateTo.setHint("ถึงวันที่ YYYY-MM-DD (เว้นว่างได้)");root.addView(dateTo);
        addButton("ค้นหา",()->populateList(query.getText().toString().trim(),false));
        populateList("",false);
    }
    private void showTrash(){pauseCamera();base("ถังขยะ • กู้คืนได้ก่อนลบถาวร");addButton("ล้างถังขยะที่เกิน 30 วัน",()->{cleanupTrash();showTrash();});populateList("",true);}
    private void populateList(String q,boolean deleted) {
        if(root==null)return;
        if(root.getTag() instanceof LinearLayout)root.removeView((LinearLayout)root.getTag());
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);root.setTag(list);
        int n=0;
        long from=0, to=Long.MAX_VALUE;
        if(!deleted) {
            try {
                SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd",Locale.US);parser.setLenient(false);
                if(dateFrom!=null && !dateFrom.getText().toString().trim().isEmpty())from=parser.parse(dateFrom.getText().toString().trim()).getTime();
                if(dateTo!=null && !dateTo.getText().toString().trim().isEmpty())to=parser.parse(dateTo.getText().toString().trim()).getTime()+24L*60*60*1000-1;
            }catch(Exception e){Toast.makeText(this,"กรุณากรอกวันที่รูปแบบ YYYY-MM-DD",Toast.LENGTH_LONG).show();return;}
        }
        try(Cursor c=db.find(q,deleted,from,to)) {
            while(c.moveToNext()) {
                String tracking=c.getString(0), bagId=c.getString(1),path=c.getString(2);
                long time=c.getLong(3), offset=c.getLong(4);
                String stamp=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date(time));
                Button b=button(tracking+"\n"+bagId+" • "+stamp,()->showEvidence(tracking,bagId,path,offset,deleted));
                list.addView(b);n++;
            }
        }
        if(n==0)list.addView(text("ไม่พบรายการ",15));
    }
    private void showEvidence(String tracking,String bagId,String path,long offset,boolean deleted) {
        File file=new File(path);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text(tracking+"\n"+bagId,15));
        if(file.isFile()){
            ImageView image=new ImageView(this);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
            image.setImageBitmap(BitmapFactory.decodeFile(path,opts));image.setAdjustViewBounds(true);
            content.addView(image,new LinearLayout.LayoutParams(-1,dp(330)));
        }else content.addView(text("⚠ ไฟล์รูปสูญหายหรือไม่พบในเครื่อง",15));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog.Builder dlg=new AlertDialog.Builder(this).setTitle("หลักฐานพัสดุ").setView(scroll).setNegativeButton("ปิด",null);
        if(deleted){
            dlg.setPositiveButton("กู้คืน",(d,w)->{db.restore(tracking);showTrash();});
            dlg.setNeutralButton("ลบถาวร",(d,w)->confirmPermanent(tracking,path));
        }else{
            dlg.setPositiveButton("แชร์รูป",(d,w)->shareFile(file,"image/jpeg"));
            dlg.setNeutralButton("เพิ่มเติม",(d,w)->new AlertDialog.Builder(this).setItems(new String[]{"เปิดวิดีโอถุงนี้", "ย้ายไปถังขยะ"},(d2,index)->{
                if(index==0)playVideo(bagId,offset);else{
                    new AlertDialog.Builder(this).setMessage("ย้าย "+tracking+" ไปถังขยะหรือไม่?")
                        .setNegativeButton("ยกเลิก",null).setPositiveButton("ย้าย",(d3,whichButton)->{db.trash(tracking);showHistory();}).show();
                }
            }).show());
        }
        dlg.show();
    }
    private void confirmPermanent(String tracking,String path){
        new AlertDialog.Builder(this).setTitle("ลบถาวร?").setMessage("หลังลบจะกู้รูปหลักฐานนี้กลับไม่ได้")
            .setNegativeButton("ยกเลิก",null).setPositiveButton("ลบถาวร",(d,w)->{
                db.permanentlyDelete(tracking);new File(path).delete();showTrash();
            }).show();
    }
    private void playVideo(String bagId,long offset) {
        String path=db.videoPath(bagId);File file=new File(path==null?"":path);
        if(!file.isFile()) {Toast.makeText(this,"ถุงนี้ไม่มีไฟล์วิดีโอ",Toast.LENGTH_LONG).show();return;}
        VideoView player=new VideoView(this);player.setMediaController(new MediaController(this));
        player.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,dp(280)));
        player.setVideoPath(file.getAbsolutePath());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("วิดีโอ "+bagId).setView(player)
            .setPositiveButton("แชร์",(d,w)->shareFile(file,"video/mp4"))
            .setNeutralButton("ลบวิดีโอ",(d,w)->{
                new AlertDialog.Builder(this).setTitle("ยืนยันลบวิดีโอ?")
                    .setMessage("เลข Tracking และภาพถ่ายจะยังอยู่ แต่ไฟล์วิดีโอนี้กู้คืนไม่ได้")
                    .setNegativeButton("ยกเลิก",null)
                    .setPositiveButton("ลบ",(d2,w2)->{
                        boolean deletedFile=file.delete();
                        if(deletedFile){db.clearVideo(bagId);message("ลบวิดีโอแล้ว");}
                        else Toast.makeText(this,"ไม่สามารถลบไฟล์วิดีโอได้",Toast.LENGTH_LONG).show();
                    }).show();
            }).setNegativeButton("ปิด",null).create();
        player.setOnPreparedListener(mp->{if(offset>0)player.seekTo((int)Math.min(offset,Integer.MAX_VALUE));player.start();});
        dialog.setOnDismissListener(d->player.stopPlayback());dialog.show();
    }

    private void showSettings(){pauseCamera();base("ตั้งค่ากล้อง / ส่งออกข้อมูล / ความเป็นส่วนตัว");
        CheckBox voiceNotify = new CheckBox(this);
        voiceNotify.setTextColor(WHITE);
        voiceNotify.setText("พูด 'บันทึกแล้ว' เมื่อถ่ายภาพและบันทึกสำเร็จ");
        voiceNotify.setChecked(speakAfterSave);
        root.addView(voiceNotify);
        voiceNotify.setOnCheckedChangeListener((button, checked) -> {
            speakAfterSave = checked;
            getPreferences(0).edit().putBoolean("voice_on_save", checked).apply();
        });
        addButton("ทดลองเสียงแจ้งเตือน", this::announceSaved);
        root.addView(text("เสียงพูดต้องมีเสียงภาษาไทยแบบออฟไลน์ในเครื่อง หากยังไม่มีจะดังบี๊บแทน • ปรับระดับเสียงที่ปุ่มเพิ่มเสียงสื่อของมือถือ", 12));
        CheckBox video=new CheckBox(this);video.setTextColor(WHITE);video.setText("เปิดใช้โหมดวิดีโอทดลองพร้อมสแกน (กินพื้นที่มาก)");
        video.setChecked(enableVideo);root.addView(video);
        video.setOnCheckedChangeListener((b,checked)->{
            enableVideo=checked;getPreferences(0).edit().putBoolean("video",checked).apply();
            Toast.makeText(this,"กลับไปหน้าสแกนเพื่อเริ่มกล้องใหม่",Toast.LENGTH_SHORT).show();
        });
        File dir=getFilesDir();android.os.StatFs st=new android.os.StatFs(dir.getAbsolutePath());
        long free=st.getAvailableBytes()/1024/1024;
        root.addView(text("พื้นที่ว่างประมาณ "+free+" MB\nCameraX / ML Kit (แบบรวมโมเดลในแอป)\nภาพและวิดีโออยู่ในพื้นที่ส่วนตัวของแอป",14));
        root.addView(text("ลบวิดีโอเก่าอัตโนมัติ (ภาพถ่ายจะไม่ถูกลบ)",14));
        Spinner retention=new Spinner(this);
        String[] retentionLabels={"ปิดการลบวิดีโออัตโนมัติ", "หลัง 30 วัน", "หลัง 90 วัน", "หลัง 180 วัน"};
        int[] retentionDays={0,30,90,180};
        ArrayAdapter<String> retentionAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,retentionLabels);
        retention.setAdapter(retentionAdapter);
        int selected=getPreferences(0).getInt("video_retention",0);
        for(int i=0;i<retentionDays.length;i++)if(retentionDays[i]==selected)retention.setSelection(i);
        retention.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){
                getPreferences(0).edit().putInt("video_retention",retentionDays[index]).apply();
            }
        });root.addView(retention);
        addButton("ดำเนินการลบวิดีโอตามอายุที่ตั้งไว้",()->{
            new AlertDialog.Builder(this).setMessage("ลบไฟล์วิดีโอที่เกินระยะเวลาอย่างถาวรหรือไม่? (รูป JPEG ยังคงอยู่)")
                .setNegativeButton("ยกเลิก",null).setPositiveButton("ดำเนินการ",(d,w)->{int removed=cleanupVideos();message("ลบวิดีโอเก่าแล้ว "+removed+" ไฟล์");}).show();
        });
        addButton("ส่งออก ZIP หลักฐานทั้งหมด (รวมวิดีโอ)",()->exportZip(null));
        addButton("ส่งออก ZIP เฉพาะถุงปัจจุบัน",()->exportZip(bag));
        addButton("ตรวจสอบถุงทั้งหมด",this::showBags);
        root.addView(text("ข้อจำกัด: วิดีโอที่เปิดโหมดทดลองเป็นภาพสด ไม่ฝังเลข Tracking ลงในวิดีโอโดยตรง แต่บันทึกตำแหน่งเวลาไว้ในฐานข้อมูล ส่วนภาพ JPEG มี Tracking/วันที่/รหัสถุงอยู่บนภาพ",13));
        root.addView(text("ป้องกันข้อมูลลูกค้า: อย่าส่ง ZIP/ภาพไปเผยแพร่สาธารณะ และสำรองไฟล์ไว้ภายนอกมือถือเป็นประจำ",13));
    }
    private void showBags(){
        StringBuilder s=new StringBuilder();
        try(Cursor c=db.bagList()){
            while(c.moveToNext()){String id=c.getString(0);s.append(id).append(" • ").append(c.getString(1))
                    .append(" • ").append(db.count(id)).append(" ชิ้น").append(c.getInt(2)==1?" [ปิด]":" [เปิด]").append("\n");}
        }
        new AlertDialog.Builder(this).setTitle("ถุงที่เคยสร้าง").setMessage(s.length()==0?"ไม่มีข้อมูล":s.toString()).setPositiveButton("ปิด",null).show();
    }
    private void exportZip(String onlyBag){
        Toast.makeText(this,"กำลังรวบรวมหลักฐาน ZIP...",Toast.LENGTH_LONG).show();
        ioExecutor.execute(()->{
            try {
                File zip=new File(getCacheDir(),"ParcelGuard-"+System.currentTimeMillis()+".zip");
                HashSet<String> includedVideo=new HashSet<>();
                StringBuilder checksums=new StringBuilder();
                try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
                    StringBuilder csv=new StringBuilder("tracking,bag,photo_name,scanned_at,video_offset_ms\n");
                    try(Cursor c=(onlyBag==null?db.allActive():db.bagActive(onlyBag))){
                        while(c.moveToNext()){
                            String tracking=c.getString(0),bagId=c.getString(1),photoPath=c.getString(2);
                            String photoName="photos/"+tracking.replaceAll("[^A-Z0-9_-]","_")+".jpg";
                            csv.append(csv(tracking)).append(',').append(csv(bagId)).append(',').append(csv(photoName))
                                    .append(',').append(c.getLong(3)).append(',').append(c.getLong(4)).append('\n');
                            File photo=new File(photoPath);if(photo.isFile()){
                                addZipFile(out,photo,photoName);
                                checksums.append(sha256(photo)).append("  ").append(photoName).append('\n');
                            }
                            if(includedVideo.add(bagId)) {
                                File v=new File(db.videoPath(bagId));if(v.isFile()){
                                    addZipFile(out,v,"videos/"+v.getName());
                                    checksums.append(sha256(v)).append("  videos/").append(v.getName()).append('\n');
                                }
                            }
                        }
                    }
                    out.putNextEntry(new ZipEntry("manifest.csv"));out.write(csv.toString().getBytes("UTF-8"));out.closeEntry();
                    out.putNextEntry(new ZipEntry("SHA256SUMS.txt"));out.write(checksums.toString().getBytes("UTF-8"));out.closeEntry();
                }
                runOnUiThread(()->shareFile(zip,"application/zip"));
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"สร้าง ZIP ไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show());}
        });
    }
    private String sha256(File f) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] b=new byte[8192];
        try(InputStream in=new BufferedInputStream(new FileInputStream(f))){int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte value:digest.digest())s.append(String.format(Locale.US,"%02x",value&0xff));
        return s.toString();
    }
    private String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private void addZipFile(ZipOutputStream out,File file,String entryName)throws IOException{
        out.putNextEntry(new ZipEntry(entryName));byte[] buf=new byte[8192];
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){int n;while((n=in.read(buf))>=0){if(n>0)out.write(buf,0,n);}}
        out.closeEntry();
    }
    private void shareFile(File file,String mime) {
        if(!file.isFile()) {Toast.makeText(this,"ไฟล์ไม่พบ",Toast.LENGTH_LONG).show();return;}
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
        Intent send=new Intent(Intent.ACTION_SEND);send.setType(mime);send.putExtra(Intent.EXTRA_STREAM,uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send,"ส่งหลักฐาน ParcelGuard"));
    }
    private int cleanupVideos(){
        int days=getPreferences(0).getInt("video_retention",0);
        if(days<=0)return 0;
        long cutoff=System.currentTimeMillis()-days*24L*60*60*1000;
        int removed=0;
        try(Cursor c=db.expiredVideos(cutoff)){
            while(c.moveToNext()){
                File video=new File(c.getString(1));
                if(!video.isFile() || video.delete()){
                    db.clearVideo(c.getString(0));removed++;
                }
            }
        }
        return removed;
    }
    private void cleanupTrash(){
        long cutoff=System.currentTimeMillis()-30L*24*60*60*1000;
        try(Cursor c=db.deletedRowsOlder(cutoff)){
            while(c.moveToNext()){new File(c.getString(1)).delete();db.permanentlyDelete(c.getString(0));}
        }
    }
    @Override public void onRequestPermissionsResult(int code,@NonNull String[] p,@NonNull int[] results){
        super.onRequestPermissionsResult(code,p,results);
        if(code==12){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)startCamera();
            else message("ต้องอนุญาตกล้องในตั้งค่าแอปก่อนใช้งาน");}
    }
    @Override protected void onDestroy(){
        pauseCamera();
        scanner.close();
        cameraExecutor.shutdown();
        ioExecutor.shutdown();
        if(savedSpeech!=null){savedSpeech.stop();savedSpeech.shutdown();savedSpeech=null;}
        tones.release();
        db.close();
        super.onDestroy();
    }
}
