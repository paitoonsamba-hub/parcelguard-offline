package com.parcelguard.offline;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.media.Image;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
    private Camera boundCamera;
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
    private final ToneGenerator tones = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75);
    private PreviewView preview;
    private TextView status, count, bagLabel, liveTracking;
    private EditText query, expectedEntry, dateFrom, dateTo;
    private LinearLayout root;
    private final int BG = Color.rgb(10, 20, 37);
    private final int PANEL = Color.rgb(24, 43, 67);
    private final int SKY = Color.rgb(0, 187, 237);
    private final int WHITE = Color.WHITE;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        db = new ParcelDb(this);
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
        v.setTextColor(WHITE); v.setPadding(dp(8), dp(9), dp(8), dp(9)); return v;
    }
    private int dp(int value) {return (int) (value * getResources().getDisplayMetrics().density + 0.5f);}
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(text);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void addButton(String text, Runnable runnable) {root.addView(button(text, runnable));}
    private void base(String title) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(20)); root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        root.addView(text("📦 ParcelGuard Offline 0.2", 23));
        root.addView(text(title, 13));
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button scan = button("สแกน", this::showScan); Button history = button("ประวัติ", this::showHistory);
        Button trash = button("ถังขยะ", this::showTrash); Button settings = button("ตั้งค่า", this::showSettings);
        for (Button b : new Button[]{scan, history, trash, settings}) tabs.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(tabs);
    }
    private void pauseCamera() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        boundCamera = null; videoCapture = null;
        if (recording != null) {recording.stop(); recording = null;}
        working.set(false); active = false;
    }
    private void showScan() {
        if (root != null) pauseCamera();
        active = true;
        base("ตรวจพัสดุก่อนใส่ถุงใหญ่ • บันทึกภาพออฟไลน์");
        Spinner carrierSelector = new Spinner(this);
        String[] options = {"SPX", "J&T", "Flash", "LEX", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        carrierSelector.setAdapter(adapter);
        for (int i=0; i<options.length; i++) if(options[i].equals(carrier)) carrierSelector.setSelection(i);
        carrierSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> a) {}
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) {carrier = options[i];}
        });
        root.addView(text("ขนส่งสำหรับถุงใหม่", 13)); root.addView(carrierSelector);
        bagLabel = text("", 17); root.addView(bagLabel);
        count = text("", 20); root.addView(count);
        LinearLayout row = new LinearLayout(this);
        row.addView(button("+ เปิดถุงใหม่", () -> createNewBag(true)), new LinearLayout.LayoutParams(0, dp(52), 1));
        row.addView(button("ปิดถุง", this::confirmClose), new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(row);
        expectedEntry = new EditText(this); expectedEntry.setSingleLine(true); expectedEntry.setInputType(InputType.TYPE_CLASS_NUMBER);
        expectedEntry.setTextColor(WHITE); expectedEntry.setHintTextColor(Color.LTGRAY);
        expectedEntry.setHint("จำนวนที่ควรมีในถุง (0=ไม่กำหนด)");
        expectedEntry.setText(String.valueOf(db.expected(bag))); root.addView(expectedEntry);
        addButton("กำหนดจำนวนเป้าหมาย", () -> {
            try {int v=Integer.parseInt(expectedEntry.getText().toString().trim());
                if(v<0) throw new NumberFormatException(); db.setExpected(bag,v); updateCount();
            } catch(NumberFormatException e) {message("ระบุจำนวนเต็ม 0 หรือมากกว่า");}
        });
        root.addView(text("กล้องตรวจพัสดุ (วางมือถือบนขาตั้งเหนือโต๊ะ)", 17));
        android.widget.FrameLayout cameraBox = new android.widget.FrameLayout(this);
        preview = new PreviewView(this); preview.setBackgroundColor(Color.BLACK);
        cameraBox.addView(preview,new android.widget.FrameLayout.LayoutParams(-1,-1));
        liveTracking = text("Tracking: —", 16); liveTracking.setBackgroundColor(0xC7000000);
        android.widget.FrameLayout.LayoutParams trackLayout = new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        cameraBox.addView(liveTracking,trackLayout);
        root.addView(cameraBox,new LinearLayout.LayoutParams(-1,dp(310)));
        status = text("กำลังขอใช้กล้อง...", 14); root.addView(status);
        addButton("เปิด/ปิดไฟฉาย", () -> {torch=!torch; if(boundCamera!=null && boundCamera.getCameraInfo().hasFlashUnit()) boundCamera.getCameraControl().enableTorch(torch);});
        addButton("บันทึกวิดีโอถุงนี้ (ทดลอง)", () -> {
            if(recording!=null) stopVideo(); else startVideo();
        });
        root.addView(text("วิดีโอเป็นตัวเลือกเสริม หากมือถือไม่รองรับกล้อง 3 งานพร้อมกัน ระบบจะใช้ภาพถ่ายอย่างเดียว", 12));
        root.addView(text("ยกพัสดุออกจากกรอบกล้อง ก่อนวางชิ้นถัดไป • สแกนสำเร็จเมื่อบันทึกภาพลงเครื่องแล้ว", 13));
        updateCount();
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, 12);
    }
    private void updateCount() {
        if(count != null) {
            int n=db.count(bag), goal=db.expected(bag);
            count.setText("สแกน " + n + " ชิ้น" + (goal>0?" / เป้าหมาย "+goal+" • ขาด "+Math.max(0,goal-n):""));
        }
        if(bagLabel!=null) bagLabel.setText("ถุง: " + bag + (db.isClosed(bag)?" [ปิดแล้ว]":" [เปิด]") + " • " + db.bagCarrier(bag));
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
    private void message(String s) {if(status!=null && active) status.setText(s); else Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

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
            runOnUiThread(()->{tones.startTone(ToneGenerator.TONE_PROP_BEEP,120);updateCount();message("✓ บันทึกภาพ: "+tracking+" • ใส่ถุงได้");});
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

    private void startVideo() {
        if(db.isClosed(bag)){message("ต้องเปิดถุงใหม่ก่อน");return;}
        String prior=db.videoPath(bag);
        if(prior!=null && !prior.isEmpty() && new File(prior).isFile()){
            message("ถุงนี้มีวิดีโออยู่แล้ว • ถ้าจะถ่ายใหม่ต้องจัดการไฟล์เก่าก่อน");return;
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
                        .setNegativeButton("ยกเลิก",null).setPositiveButton("ย้าย",(d3,w)->{db.trash(tracking);showHistory();}).show();
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
    @Override protected void onDestroy(){pauseCamera();scanner.close();cameraExecutor.shutdown();ioExecutor.shutdown();tones.release();db.close();super.onDestroy();}
}
