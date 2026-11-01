package com.parcelguard.offline;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.Image;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, one-time, LAN-only QR pairing. Never stores the one-time QR code. */
public final class PairQrActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private BarcodeScanner scanner;
    private PreviewView camera;
    private TextView status;
    private volatile boolean pairingDialogVisible = false;
    private boolean connected = false;

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0a0f15);
        getWindow().setNavigationBarColor(0xff0a0f15);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff0a0f15);root.setPadding(dp(16),dp(18),dp(16),dp(18));
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets safe=insets.getInsets(WindowInsetsCompat.Type.statusBars()|WindowInsetsCompat.Type.navigationBars()|WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(16)+safe.left,dp(18)+safe.top,dp(16)+safe.right,dp(18)+safe.bottom);
            return insets;
        });
        TextView title=new TextView(this);title.setText("▦ สแกน QR เชื่อมต่อ ParcelGuard PC");title.setTextSize(21);title.setTextColor(0xffff9009);title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(65)));
        TextView note=new TextView(this);note.setText("เปิด ParcelGuard PC → ซิงก์และอุปกรณ์ → เพิ่มอุปกรณ์ด้วย QR แล้วส่องกล้องมือถือไปที่ QR บนจอคอม");note.setTextSize(14);note.setTextColor(Color.WHITE);
        root.addView(note);
        camera=new PreviewView(this);camera.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams camParam=new LinearLayout.LayoutParams(-1,0,1);camParam.setMargins(0,dp(16),0,dp(16));root.addView(camera,camParam);
        status=new TextView(this);status.setText("พร้อมสแกน QR • ใช้ Wi-Fi ส่วนตัวของร้านเท่านั้น");status.setTextColor(0xff15d7a2);status.setGravity(Gravity.CENTER);status.setTextSize(15);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(65)));
        setContentView(root);ViewCompat.requestApplyInsets(root);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},87);
    }

    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);
        f.addListener(()->{
            try{
                if(isFinishing()||isDestroyed())return;
                ProcessCameraProvider p=f.get();p.unbindAll();
                Preview preview=new Preview.Builder().build();preview.setSurfaceProvider(camera.getSurfaceProvider());
                ImageAnalysis analyze=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analyze.setAnalyzer(executor,this::checkFrame);
                p.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,analyze);
            }catch(Exception e){status.setText("เปิดกล้องไม่ได้: "+e.getMessage());}
        },ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void checkFrame(@NonNull ImageProxy frame){
        if(connected||pairingDialogVisible||!processing.compareAndSet(false,true)){frame.close();return;}
        Image image=frame.getImage();
        if(image==null){processing.set(false);frame.close();return;}
        try{
            InputImage input=InputImage.fromMediaImage(image,frame.getImageInfo().getRotationDegrees());
            scanner.process(input).addOnSuccessListener(items->{
                for(Barcode item:items){
                    String raw=item.getRawValue();
                    if(raw!=null&&raw.startsWith("PGPAIR1|")){
                        String[] values=raw.split("\\|",-1);
                        if(values.length!=4 || !validHost(values[1]) || !"8766".equals(values[2]) || !values[3].matches("[A-Za-z0-9_-]{20,64}")){
                            runOnUiThread(()->status.setText("QR ไม่ถูกต้องสำหรับ ParcelGuard"));
                        }else if(!pairingDialogVisible){
                            pairingDialogVisible=true;
                            String host=values[1],code=values[3];
                            runOnUiThread(()->confirmPair(host,code));
                        }
                        break;
                    }
                }
            }).addOnCompleteListener(task->{processing.set(false);frame.close();});
        }catch(Exception e){processing.set(false);frame.close();}
    }
    private boolean validHost(String host){
        // This pilot permits only RFC1918 private IPv4 addresses, not public DNS/Internet hosts.
        String[] p=host.split("\\.",-1);if(p.length!=4)return false;
        try{int[] n=new int[4];for(int i=0;i<4;i++){if(p[i].isEmpty()||p[i].length()>3)return false;n[i]=Integer.parseInt(p[i]);if(n[i]<0||n[i]>255)return false;}
            return n[0]==10 || (n[0]==172&&n[1]>=16&&n[1]<=31) || (n[0]==192&&n[1]==168);
        }catch(NumberFormatException e){return false;}
    }
    private void confirmPair(String host,String code){
        if(isFinishing())return;
        new AlertDialog.Builder(this)
            .setTitle("ยืนยันจับคู่ ParcelGuard PC")
            .setMessage("เชื่อมต่อคอมพิวเตอร์ในร้าน IP "+host+"?\nQR ใช้ได้ครั้งเดียวและหมดอายุใน 90 วินาที")
            .setNegativeButton("ยกเลิก",(d,w)->{pairingDialogVisible=false;})
            .setPositiveButton("จับคู่",(d,w)->exchangeCode(host,code))
            .setOnCancelListener(d->pairingDialogVisible=false).show();
    }
    private void exchangeCode(String host,String code){
        status.setText("กำลังจับคู่กับ PC ...");
        executor.execute(()->{
            HttpURLConnection conn=null;
            try{
                SharedPreferences prefs=getSharedPreferences("hybrid_sync",Context.MODE_PRIVATE);
                String name="Android "+android.os.Build.MODEL;
                URL url=new URL("http://"+host+":8766/api/pair/claim");
                conn=(HttpURLConnection)url.openConnection();conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);conn.setReadTimeout(8000);conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
                byte[] raw=new JSONObject().put("code",code).put("name",name).toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(raw.length);
                try(OutputStream out=conn.getOutputStream()){out.write(raw);}
                int statusCode=conn.getResponseCode();
                if(statusCode!=200)throw new Exception("PC ปฏิเสธรหัส (HTTP "+statusCode+") อาจหมดอายุแล้ว");
                StringBuilder b=new StringBuilder();
                try(InputStream input=conn.getInputStream();BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
                    String line;while((line=reader.readLine())!=null && b.length()<10000)b.append(line);
                }
                JSONObject reply=new JSONObject(b.toString());String token=reply.getString("token");
                if(token.length()<24)throw new Exception("Token ที่ได้รับไม่ถูกต้อง");
                // Store only the long-term credential. The one-time QR code is discarded.
                prefs.edit().putString("host",host).putString("token",token).putString("device_id",reply.optString("id",""))
                    .putBoolean("enabled",true).apply();
                connected=true;
                runOnUiThread(()->{
                    Toast.makeText(this,"จับคู่ PC สำเร็จ • เปิด Wi-Fi Sync แล้ว",Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK,new Intent().putExtra("paired",true));finish();
                });
            }catch(Exception e){String message=e.getMessage();runOnUiThread(()->{pairingDialogVisible=false;status.setText("จับคู่ไม่สำเร็จ: "+message+" • สร้าง QR ใหม่บน PC แล้วลองอีกครั้ง");});}
            finally{if(conn!=null)conn.disconnect();}
        });
    }
    @Override public void onRequestPermissionsResult(int req,@NonNull String[] permissions,@NonNull int[] grants){
        super.onRequestPermissionsResult(req,permissions,grants);
        if(req==87 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)startCamera();
        else status.setText("กรุณาอนุญาตสิทธิ์กล้องในตั้งค่า Android");
    }
    @Override protected void onDestroy(){
        super.onDestroy();if(scanner!=null)scanner.close();executor.shutdownNow();
    }
}
