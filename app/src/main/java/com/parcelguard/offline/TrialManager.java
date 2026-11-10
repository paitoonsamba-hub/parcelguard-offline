package com.parcelguard.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trial V0.5.0: signed server receipt, 7-day total expiry and max 48h offline.
 *
 * Shipping protection: the feature is DISABLED in ordinary debug builds
 * until all PG_TRIAL_* build settings are supplied intentionally. This must
 * be enabled and tested for every commercial build before distribution.
 *
 * Data preservation: expired users can still browse/export old evidence.
 * No SQLite schema changes and no server upload of parcel media.
 */
final class TrialManager {
    interface Callback { void finish(boolean success,String message); }

    private final Context context;
    private final boolean enabled;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private FirebaseAuth auth;
    private JSONObject payload;
    private long trustedElapsed;
    private long highWater;
    private long lastWaterSaved=0L;
    private final String deviceDigest;

    TrialManager(Context ctx) {
        context=ctx.getApplicationContext();
        enabled=BuildConfig.TRIAL_ENABLED;
        prefs=context.getSharedPreferences("pg_trial_v1",Context.MODE_PRIVATE);
        deviceDigest=deviceDigest(ctx);
        if(enabled) {
            FirebaseOptions options=new FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.TRIAL_FIREBASE_APP_ID)
                .setApiKey(BuildConfig.TRIAL_FIREBASE_API_KEY)
                .setProjectId(BuildConfig.TRIAL_FIREBASE_PROJECT_ID).build();
            if(FirebaseApp.getApps(context).isEmpty())
                FirebaseApp.initializeApp(context,options);
            auth=FirebaseAuth.getInstance();
        }
        restoreReceipt();
    }

    boolean isEnabled(){return enabled;}
    boolean canPack(){return !enabled || status()==TrialPolicy.State.ACTIVE;}
    FirebaseUser account(){return auth==null?null:auth.getCurrentUser();}

    TrialPolicy.State status() {
        if(!enabled) return TrialPolicy.State.ACTIVE;
        if(payload==null) return TrialPolicy.State.NEED_LOGIN;
        FirebaseUser user=account();
        if(user!=null && !user.getUid().equals(payload.optString("uid")))
            return TrialPolicy.State.NEED_LOGIN;
        long wall=System.currentTimeMillis();
        long elapsed=SystemClock.elapsedRealtime();
        TrialPolicy.State result=TrialPolicy.evaluate(wall,elapsed,
            payload.optLong("issuedAt"),trustedElapsed,highWater,
            payload.optLong("startedAt"),payload.optLong("expiresAt"),
            payload.optLong("offlineUntil"));
        if(result==TrialPolicy.State.ACTIVE || result==TrialPolicy.State.EXPIRED
                || result==TrialPolicy.State.NEED_ONLINE){
            if(wall>highWater)highWater=wall;
            if(elapsed-lastWaterSaved>60000){
                lastWaterSaved=elapsed;
                prefs.edit().putLong("highWater",highWater).apply();
            }
        }
        return result;
    }

    String explain() {
        if(!enabled)return "โหมดทดสอบภายใน • ยังไม่เปิดระบบ License";
        switch(status()){
            case ACTIVE:
                long ms=Math.max(0,payload.optLong("expiresAt")-System.currentTimeMillis());
                long hours=(ms+3599999L)/3600000L;
                return "ทดลองใช้งาน • เหลือประมาณ "+(hours>=24?(hours+23)/24+" วัน":hours+" ชั่วโมง");
            case EXPIRED:return "ทดลองครบ 7 วันแล้ว • ดูและส่งออกหลักฐานเดิมได้";
            case NEED_ONLINE:return "ต้องเชื่อมอินเทอร์เน็ตเพื่อตรวจสิทธิ์ (ทุก 48 ชั่วโมง)";
            case CLOCK_INVALID:return "เวลามือถือผิดปกติ • ตั้งเวลาอัตโนมัติแล้วตรวจสิทธิ์ใหม่";
            default:return "ลงทะเบียนและยืนยันอีเมลเพื่อเริ่มทดลอง 7 วัน";
        }
    }

    void signIn(String email,String password,boolean create,Callback callback){
        if(!enabled || auth==null){callback.finish(false,"ยังไม่ได้เชื่อม Firebase");return;}
        if(email==null || !email.trim().contains("@") || password==null || password.length()<6){
            callback.finish(false,"กรอกอีเมลและรหัสผ่านอย่างน้อย 6 ตัวอักษร");return;
        }
        String normalized=email.trim();
        if(create){
            auth.createUserWithEmailAndPassword(normalized,password)
                .addOnCompleteListener(task->{
                    if(!task.isSuccessful()){
                        callback.finish(false,safeAuthError(task.getException()));return;
                    }
                    FirebaseUser u=auth.getCurrentUser();
                    if(u==null){callback.finish(false,"สร้างบัญชีไม่สำเร็จ");return;}
                    u.sendEmailVerification().addOnCompleteListener(mail->{
                        callback.finish(false,mail.isSuccessful()
                            ?"ส่งอีเมลยืนยันแล้ว • เปิดอีเมลแล้วกลับมากดตรวจสิทธิ์"
                            :"สร้างบัญชีแล้ว แต่ส่งอีเมลไม่ได้ • โปรดลองส่งยืนยันอีกครั้ง");
                    });
                });
        }else{
            auth.signInWithEmailAndPassword(normalized,password)
                .addOnCompleteListener(task->{
                    if(!task.isSuccessful()){
                        callback.finish(false,safeAuthError(task.getException()));return;
                    }
                    refresh(callback);
                });
        }
    }

    void resendVerification(Callback cb){
        if(!enabled || account()==null){cb.finish(false,"เข้าสู่ระบบก่อน");return;}
        account().sendEmailVerification().addOnCompleteListener(task->
            cb.finish(false,task.isSuccessful()?"ส่งลิงก์ยืนยันแล้ว":"ส่งอีเมลไม่สำเร็จ โปรดลองอีกครั้ง"));
    }

    void refresh(Callback callback) {
        if(!enabled || auth==null){callback.finish(false,"ยังไม่ได้เปิดระบบ Trial");return;}
        FirebaseUser u=account();
        if(u==null){callback.finish(false,"กรุณาเข้าสู่ระบบก่อน");return;}
        u.reload().addOnCompleteListener(reload->{
            FirebaseUser fresh=auth.getCurrentUser();
            if(!reload.isSuccessful() || fresh==null) {
                callback.finish(false,"ตรวจบัญชีไม่สำเร็จ • ตรวจอินเทอร์เน็ตแล้วลองใหม่");return;
            }
            if(!fresh.isEmailVerified()){
                callback.finish(false,"กรุณากดยืนยันอีเมล แล้วกลับมาตรวจสิทธิ์");return;
            }
            fresh.getIdToken(true).addOnCompleteListener(idTask->{
                if(!idTask.isSuccessful() || idTask.getResult()==null
                    || idTask.getResult().getToken()==null) {
                    callback.finish(false,"ขอรหัสยืนยันบัญชีไม่สำเร็จ");return;
                }
                final String uid=fresh.getUid();
                final String jwt=idTask.getResult().getToken();
                io.execute(()->{
                    String message;
                    boolean success=false;
                    try{
                        String signedReceipt=callServer(jwt);
                        verifyAndSave(signedReceipt,uid);
                        success=status()==TrialPolicy.State.ACTIVE;
                        message=explain();
                    }catch(Exception e){
                        message=errorFor(e.getMessage());
                    }
                    final boolean ok=success;
                    final String result=message;
                    main.post(()->callback.finish(ok,result));
                });
            });
        });
    }

    void close(){io.shutdown();}

    private String callServer(String idToken)throws Exception {
        URL url=new URL(BuildConfig.TRIAL_ENDPOINT);
        if(!"https".equalsIgnoreCase(url.getProtocol()))
            throw new SecurityException("HTTPS_REQUIRED");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        try{
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(12000);conn.setReadTimeout(18000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Authorization","Bearer "+idToken);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            conn.setDoOutput(true);
            byte[] body=new JSONObject().put("deviceDigest",deviceDigest)
                .toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=conn.getOutputStream()){out.write(body);}
            int status=conn.getResponseCode();
            InputStream in=status>=200&&status<300?conn.getInputStream():conn.getErrorStream();
            String response;
            if(in==null)response="{}";
            else try(InputStream src=in){
                java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
                byte[] b=new byte[2048];int n;int total=0;
                while((n=src.read(b))!=-1 && total<16384){buf.write(b,0,n);total+=n;}
                response=buf.toString("UTF-8");
            }
            JSONObject parsed=new JSONObject(response);
            if(status!=200)throw new SecurityException(parsed.optString("error","SERVER_ERROR"));
            return parsed.getString("receipt");
        }finally{conn.disconnect();}
    }

    private void restoreReceipt(){
        if(!enabled)return;
        String saved=prefs.getString("signedReceipt","");
        if(saved.isEmpty())return;
        try{
            payload=verifySignedReceipt(saved,null);
            trustedElapsed=prefs.getLong("trustedElapsed",SystemClock.elapsedRealtime());
            highWater=prefs.getLong("highWater",payload.getLong("issuedAt"));
        }catch(Exception ignored){
            payload=null;
            prefs.edit().remove("signedReceipt").apply();
        }
    }

    private void verifyAndSave(String signedReceipt,String uid)throws Exception{
        JSONObject verified=verifySignedReceipt(signedReceipt,uid);
        long now=System.currentTimeMillis();
        // A broken wall clock never makes a signed receipt newly valid.
        if(now+TrialPolicy.CLOCK_SKEW<verified.getLong("issuedAt"))
            throw new SecurityException("CLOCK_INVALID");
        payload=verified;
        trustedElapsed=SystemClock.elapsedRealtime();
        highWater=Math.max(now,verified.getLong("issuedAt"));
        lastWaterSaved=trustedElapsed;
        if(!prefs.edit().putString("signedReceipt",signedReceipt)
            .putLong("trustedElapsed",trustedElapsed)
            .putLong("highWater",highWater).commit())
            throw new IllegalStateException("SAVE_FAILED");
    }

    private JSONObject verifySignedReceipt(String signed,String expectedUid)throws Exception {
        if(signed==null || signed.length()>8192)throw new SecurityException("INVALID_RECEIPT");
        String[] parts=signed.split("\\.",-1);
        if(parts.length!=2)throw new SecurityException("INVALID_RECEIPT");
        PublicKey pub=KeyFactory.getInstance("EC").generatePublic(
            new X509EncodedKeySpec(Base64.decode(BuildConfig.TRIAL_VERIFY_SPKI,Base64.DEFAULT)));
        Signature verifier=Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pub);
        verifier.update(parts[0].getBytes(StandardCharsets.US_ASCII));
        if(!verifier.verify(Base64.decode(parts[1],Base64.URL_SAFE|Base64.NO_WRAP)))
            throw new SecurityException("INVALID_SIGNATURE");
        JSONObject p=new JSONObject(new String(Base64.decode(parts[0],
                Base64.URL_SAFE|Base64.NO_WRAP),StandardCharsets.UTF_8));
        if(p.optInt("version")!=1 || !deviceDigest.equals(p.optString("deviceDigest"))
            || p.optString("uid").isEmpty()
            || (expectedUid!=null&&!expectedUid.equals(p.optString("uid")))
            || p.optLong("startedAt")<=0
            || p.optLong("expiresAt")-p.optLong("startedAt")!=TrialPolicy.TRIAL
            || p.optLong("offlineUntil")>p.optLong("expiresAt")
            || p.optLong("offlineUntil")>p.optLong("issuedAt")+TrialPolicy.OFFLINE)
            throw new SecurityException("INVALID_CLAIMS");
        return p;
    }

    private static String deviceDigest(Context ctx) {
        try {
            String androidId=Settings.Secure.getString(ctx.getContentResolver(),Settings.Secure.ANDROID_ID);
            if(androidId==null || androidId.isEmpty() || "9774d56d682e549c".equals(androidId))
                throw new IllegalStateException("Android ID unavailable");
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(
                ("parcelguard-offline:v1:"+androidId).getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);
            for(byte b:hash)out.append(String.format(java.util.Locale.US,"%02x",b&255));
            return out.toString();
        }catch(Exception e){return "";} // server rejects empty digest; no bypass
    }

    private static String safeAuthError(Exception ex){
        return ex==null?"เข้าสู่ระบบไม่สำเร็จ":"Firebase: "+ex.getClass().getSimpleName()
            +" • ตรวจอีเมล/รหัสผ่านและการเชื่อมต่อ";
    }
    private static String errorFor(String code) {
        if(code==null)return "ตรวจสิทธิ์ไม่สำเร็จ";
        switch(code){
            case "TRIAL_ALREADY_USED":return "มือถือเครื่องนี้เคยรับสิทธิ์ทดลองแล้ว • ไม่สามารถเริ่มใหม่ด้วยอีเมลอื่น";
            case "ACCOUNT_ALREADY_USED":return "บัญชีนี้เคยเริ่มทดลองบนเครื่องอื่นแล้ว";
            case "VERIFY_EMAIL":return "กรุณายืนยันอีเมลก่อนเริ่มทดลอง";
            case "CLOCK_INVALID":return "กรุณาเปิดตั้งวันเวลาอัตโนมัติ แล้วลองใหม่";
            case "INVALID_SIGNATURE":return "ลายเซ็น License ไม่ถูกต้อง";
            case "INVALID_CLAIMS":return "ข้อมูลสิทธิ์ไม่ถูกต้อง กรุณาติดต่อผู้ดูแล";
            default:return "ตรวจสิทธิ์ไม่สำเร็จ ("+code.replaceAll("[^A-Z0-9_ -]","").substring(0,
                Math.min(80,code.length()))+")";
        }
    }
}
