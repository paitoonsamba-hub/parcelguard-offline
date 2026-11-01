package com.parcelguard.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Foreground, Wi-Fi-only sync. No Internet/cloud. One parcel per ZIP; retries are idempotent on PC. */
final class HybridSync {
    interface Listener { void message(String message); }
    private final Context context;
    private final ParcelDb db;
    private final SharedPreferences settings;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final Listener listener;
    HybridSync(Context c,ParcelDb database,Listener l){context=c.getApplicationContext();db=database;
        settings=c.getSharedPreferences("hybrid_sync",Context.MODE_PRIVATE);listener=l;}
    SharedPreferences settings(){return settings;}
    void request(){if(!settings.getBoolean("enabled",false)||!running.compareAndSet(false,true))return;
        worker.execute(()->{try{sync();}catch(Exception e){report("ซิงก์ล้มเหลว: "+e.getMessage());}
            finally{running.set(false);}});
    }
    private void report(String msg){listener.message(msg);}
    private void sync() throws Exception {
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.Network network=cm.getActiveNetwork();
        NetworkCapabilities cap=network==null?null:cm.getNetworkCapabilities(network);
        if(cap==null||!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){report("รอ Wi-Fi ร้าน");return;}
        String host=settings.getString("host","").trim();String token=settings.getString("token","").trim();
        if(!host.matches("(?:192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}|10\\.(?:[0-9]{1,3}\\.){2}[0-9]{1,3}|172\\.(?:1[6-9]|2[0-9]|3[01])\\.[0-9]{1,3}\\.[0-9]{1,3})")||token.length()<16){report("กรอก IP ของ PC และ Token ก่อน");return;}
        // PC LAN endpoint is plaintext HTTP: only on private Wi-Fi under direct user control.
        String endpoint="http://"+host+":8766/api/sync/archive";
        int uploaded=0;
        try(Cursor rows=db.allActive()){
            while(rows.moveToNext()){
                String tracking=rows.getString(0),bag=rows.getString(1),photoPath=rows.getString(2);
                File photo=new File(photoPath);
                if(!photo.isFile())continue;
                String vpath=db.parcelVideoPath(tracking);
                File video=vpath.isEmpty()?null:new File(vpath);
                // PC limits each individual media object to 130 MiB; fail safely rather than partial upload.
                if(photo.length()>120L*1024*1024 || (video!=null && video.isFile() && video.length()>120L*1024*1024)){
                    report("หลักฐานใหญ่เกิน 120MB โปรดส่งออก ZIP ผ่าน USB: "+tracking);
                    continue;
                }
                // For videos still recording, defer instead of prematurely marking as synchronized.
                int state=db.parcelVideoState(tracking);
                if(state==1)continue;
                String signature=photo.length()+"-"+photo.lastModified()+"-"+(video!=null&&video.isFile()?video.length()+"-"+video.lastModified():"none")+"-"+state;
                String key="sent_"+digest(tracking+"|"+bag);
                if(signature.equals(settings.getString(key,"")))continue;
                File zip=File.createTempFile("pg-sync-",".zip",context.getCacheDir());
                try{
                    makeArchive(zip,tracking,bag,db.bagCarrier(bag),photo,video,rows.getLong(3),rows.getLong(4),state);
                    if(zip.length()>230L*1024*1024){report("คลิปใหญ่เกิน 230MB: "+tracking);continue;}
                    HttpURLConnection conn=(HttpURLConnection)new URL(endpoint).openConnection();
                    try{
                        conn.setRequestMethod("POST");conn.setConnectTimeout(4500);conn.setReadTimeout(60000);
                        conn.setRequestProperty("Authorization","Bearer "+token);
                        conn.setRequestProperty("Content-Type","application/zip");
                        conn.setFixedLengthStreamingMode(zip.length());conn.setDoOutput(true);
                        try(OutputStream out=conn.getOutputStream();InputStream in=new FileInputStream(zip)){
                            byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                        }
                        int code=conn.getResponseCode();
                        if(code!=200)throw new IOException("PC ตอบ HTTP "+code);
                        String result;
                        try(InputStream in=conn.getInputStream()){
                            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
                            while((n=in.read(b))!=-1 && bytes.size()<65536)bytes.write(b,0,n);
                            result=bytes.toString("UTF-8");
                        }
                        if(!result.contains("\"imported\"") && !result.contains("\"already_imported\""))throw new IOException("PC ไม่ยืนยันรับข้อมูล");
                        settings.edit().putString(key,signature).apply();uploaded++;
                    }finally{conn.disconnect();}
                }finally{zip.delete();}
            }
        }
        if(uploaded>0)report("ซิงก์เข้า PC แล้ว "+uploaded+" รายการ");
    }
    private static void makeArchive(File zip,String tracking,String bag,String carrier,File photo,File video,long stamp,long offset,int state)throws Exception {
        String pic="photos/"+safe(tracking)+".jpg";
        String clip=video!=null&&video.isFile()?"videos/parcels/"+safe(tracking)+".mp4":"";
        StringBuilder sums=new StringBuilder();
        try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
            add(z,photo,pic);sums.append(fileSha(photo)).append("  ").append(pic).append('\n');
            if(!clip.isEmpty()){add(z,video,clip);sums.append(fileSha(video)).append("  ").append(clip).append('\n');}
            String csv="tracking,bag,photo_name,scanned_at,video_offset_ms,parcel_video_file,video_status,carrier\n"+
                quote(tracking)+","+quote(bag)+","+quote(pic)+","+stamp+","+offset+","+quote(clip)+","+state+","+quote(carrier)+"\n";
            z.putNextEntry(new ZipEntry("manifest.csv"));z.write(csv.getBytes("UTF-8"));z.closeEntry();
            z.putNextEntry(new ZipEntry("SHA256SUMS.txt"));z.write(sums.toString().getBytes("UTF-8"));z.closeEntry();
        }
    }
    private static String quote(String t){return "\""+t.replace("\"","\"\"")+"\"";}
    private static String safe(String s){return s.replaceAll("[^A-Za-z0-9_-]","_");}
    private static void add(ZipOutputStream z,File f,String name)throws IOException {
        z.putNextEntry(new ZipEntry(name));try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();
    }
    private static String fileSha(File f)throws Exception{
        MessageDigest m=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)m.update(b,0,n);}return hex(m.digest());
    }
    private static String digest(String s)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")));}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
    void close(){worker.shutdownNow();}
}
