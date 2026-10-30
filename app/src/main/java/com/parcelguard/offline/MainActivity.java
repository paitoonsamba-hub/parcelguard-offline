package com.parcelguard.offline;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.common.InputImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
 ParcelDb db; String bag="",carrier="SPX",lastCode=""; long lastSeen=0; boolean busy=false,closed=false;
 PreviewView preview; TextView status,count,bagLabel; EditText search; ImageAnalysis analysis;
 ExecutorService cameraExecutor=Executors.newSingleThreadExecutor(); BarcodeScanner scanner;
 final int cyan=Color.rgb(0,189,242); LinearLayout root;
 @Override public void onCreate(Bundle b){super.onCreate(b);db=new ParcelDb(this);scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,Barcode.FORMAT_QR_CODE,Barcode.FORMAT_EAN_13,Barcode.FORMAT_ITF).build());makeUi();newBag();if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},12);else startCamera();}
 TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(12,12,12,12);return t;}
 Button btn(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());return b;}
 void makeUi(){root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,10,12,10);root.setBackgroundColor(Color.rgb(11,21,39));ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 root.addView(label("📦 ParcelGuard Offline",24));root.addView(label("สแกนก่อนใส่ถุง • เก็บหลักฐานในมือถือ",14));
 Spinner sp=new Spinner(this);String[] carriers={"SPX","J&T","Flash","LEX","Other"};ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,carriers);sp.setAdapter(a);sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){carrier=carriers[pos];}});root.addView(sp);
 bagLabel=label("",18);root.addView(bagLabel);count=label("0 ชิ้น",22);root.addView(count);
 root.addView(btn("+ เปิดถุงใหม่",this::newBag));root.addView(btn("ปิดถุง",()->{if(bag.isEmpty())return;db.closeBag(bag);closed=true;status.setText("ปิดถุงแล้ว: "+bag+" จำนวน "+db.count(bag)+" ชิ้น");}));
 preview=new PreviewView(this);preview.setBackgroundColor(Color.BLACK);root.addView(preview,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*300)));
 status=label("กำลังเตรียมกล้อง...",16);root.addView(status);
 search=new EditText(this);search.setSingleLine(true);search.setTextColor(Color.WHITE);search.setHintTextColor(Color.LTGRAY);search.setHint("ค้น Tracking");root.addView(search);
 root.addView(btn("ค้นภาพย้อนหลัง",this::findParcel));root.addView(btn("ส่งออก CSV",this::exportCsv));
 root.addView(label("หมายเหตุ: เมื่อถ่ายภาพสำเร็จจึงใส่พัสดุลงถุง และยกพัสดุออกจากจุดสแกนก่อนวางชิ้นต่อไป",13));}
 void newBag(){bag="BAG-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());db.addBag(bag,carrier);closed=false;lastCode="";if(bagLabel!=null)bagLabel.setText("ถุง: "+bag+" / "+carrier);updateCount();}
 void updateCount(){if(count!=null)count.setText(db.count(bag)+" ชิ้นในถุง");}
 void startCamera(){ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);f.addListener(()->{try{ProcessCameraProvider p=f.get();p.unbindAll();Preview pv=new Preview.Builder().build();pv.setSurfaceProvider(preview.getSurfaceProvider());analysis=new ImageAnalysis.Builder().setTargetResolution(new android.util.Size(1280,720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis.setAnalyzer(cameraExecutor,this::process);p.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,pv,analysis);status.setText("กล้องพร้อม • วางใบปะหน้าในกรอบ");}catch(Exception e){status.setText("เปิดกล้องไม่สำเร็จ: "+e.getMessage());}},ContextCompat.getMainExecutor(this));}
 @androidx.camera.core.ExperimentalGetImage void process(ImageProxy frame){if(busy){frame.close();return;}android.media.Image media=frame.getImage();if(media==null){frame.close();return;}InputImage input=InputImage.fromMediaImage(media,frame.getImageInfo().getRotationDegrees());busy=true;scanner.process(input).addOnSuccessListener(codes->{String value="";for(Barcode code:codes){if(code.getRawValue()!=null){value=code.getRawValue().trim();break;}}if(!value.isEmpty()){String v=value;long now=System.currentTimeMillis();if(v.equals(lastCode)&&now-lastSeen<4500){lastSeen=now;}else{lastCode=v;lastSeen=now;saveEvidence(v,frame);}}}).addOnCompleteListener(t->{busy=false;frame.close();});}
 void saveEvidence(String code,ImageProxy frame){if(closed){runOnUiThread(()->status.setText("ถุงปิดแล้ว กรุณาเปิดถุงใหม่"));return;}if(db.exists(code)){runOnUiThread(()->status.setText("เลขซ้ำ: "+code));return;}
 // Capture a JPEG from the analysis frame. This avoids a second camera shutter delay.
 try{android.media.Image img=frame.getImage();if(img==null)return;android.media.Image.Plane[] planes=img.getPlanes();int w=img.getWidth(),h=img.getHeight();byte[] nv21=toNV21(planes,w,h);YuvImage yuv=new YuvImage(nv21,ImageFormat.NV21,w,h,null);ByteArrayOutputStream out=new ByteArrayOutputStream();yuv.compressToJpeg(new Rect(0,0,w,h),80,out);Bitmap bmp=BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size());Matrix m=new Matrix();m.postRotate(frame.getImageInfo().getRotationDegrees());Bitmap rotated=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true);File dir=new File(getFilesDir(),"photos");dir.mkdirs();File photo=new File(dir,UUID.randomUUID()+".jpg");try(FileOutputStream fos=new FileOutputStream(photo)){rotated.compress(Bitmap.CompressFormat.JPEG,80,fos);}boolean ok=db.addParcel(code,bag,photo.getAbsolutePath());if(!ok)photo.delete();runOnUiThread(()->{status.setText(ok?"✓ บันทึกภาพแล้ว: "+code:"บันทึกไม่สำเร็จ: "+code);updateCount();});}catch(Exception e){runOnUiThread(()->status.setText("ถ่ายภาพผิดพลาด: "+e.getMessage()));}}
 byte[] toNV21(android.media.Image.Plane[] p,int w,int h){byte[] out=new byte[w*h*3/2];int off=0;for(int plane=0;plane<3;plane++){java.nio.ByteBuffer buf=p[plane].getBuffer();int row=p[plane].getRowStride(),pixel=p[plane].getPixelStride(),pw=plane==0?w:w/2,ph=plane==0?h:h/2;for(int y=0;y<ph;y++)for(int x=0;x<pw;x++){int index=y*row+x*pixel;if(index>=buf.limit())continue;byte val=buf.get(index);if(plane==0)out[y*w+x]=val;else{int idx=w*h+(y*pw+x)*2+(plane==1?1:0);if(idx<out.length)out[idx]=val;}}}return out;}
 void findParcel(){String q=search.getText().toString().trim();try(Cursor c=db.list()){while(c.moveToNext()){if(c.getString(0).equalsIgnoreCase(q)){File f=new File(c.getString(2));ImageView im=new ImageView(this);Bitmap bmp=BitmapFactory.decodeFile(f.getAbsolutePath());im.setImageBitmap(bmp);im.setAdjustViewBounds(true);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(c.getString(0)+" / "+c.getString(1)).setView(im).setPositiveButton("ปิด",null).show();return;}}}status.setText("ไม่พบ Tracking: "+q);}
 void exportCsv(){try{File f=new File(getExternalFilesDir(null),"parcelguard-export.csv");try(java.io.OutputStreamWriter w=new java.io.OutputStreamWriter(new FileOutputStream(f),"UTF-8")){w.write("tracking,bag,photo_path,timestamp\n");try(Cursor c=db.list()){while(c.moveToNext()){w.write(csv(c.getString(0))+","+csv(c.getString(1))+","+csv(c.getString(2))+","+c.getLong(3)+"\n");}}}status.setText("CSV อยู่ที่พื้นที่ไฟล์แอป: "+f.getAbsolutePath());}catch(Exception e){status.setText("ส่งออกผิดพลาด: "+e.getMessage());}}
 String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
 @Override protected void onDestroy(){super.onDestroy();scanner.close();cameraExecutor.shutdown();db.close();}
 @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==12&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();else status.setText("กรุณาอนุญาตสิทธิ์กล้องในตั้งค่าแอป");}
}
