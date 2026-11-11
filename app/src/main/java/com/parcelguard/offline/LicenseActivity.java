package com.parcelguard.offline;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class LicenseActivity extends AppCompatActivity {
    private static final int WHITE = Color.WHITE;
    private static final int BACKGROUND = 0xFF0A0F15;
    private static final int ORANGE = 0xFFFF9009;
    private FirebaseAuth auth;
    private FirebaseFunctions functions;
    private LinearLayout layout;
    private TextView feedback;
    private boolean loading = false;
    private boolean manageMode = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        auth = FirebaseAuth.getInstance();
        functions = FirebaseFunctions.getInstance("asia-southeast1");
        manageMode = getIntent().getBooleanExtra("manage_license", false);
        draw();
        if (auth.getCurrentUser() != null) checkAccess();
        else showMessage("เข้าสู่ระบบเพื่อเริ่มทดลองใช้ ParcelGuard 7 วัน");
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String s, int size) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(WHITE);
        v.setPadding(0, dp(8), 0, dp(8));
        return v;
    }
    private Button action(String s, Runnable click) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(v -> { if (!loading) click.run(); });
        layout.addView(b);
        return b;
    }
    private EditText edit(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setTextColor(WHITE);
        e.setHintTextColor(0xFF94A3B8);
        e.setHint(hint);
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        layout.addView(e, new LinearLayout.LayoutParams(-1, dp(56)));
        return e;
    }
    private void showMessage(String message) { feedback.setText(message); }
    private void busy(boolean value) { loading = value; }
    private void draw() {
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(BACKGROUND);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(42), dp(24), dp(24));
        sc.addView(layout);
        TextView title = label("ParcelGuard", 30);
        title.setTextColor(ORANGE);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        TextView sub = label("บัญชีผู้ใช้ • ทดลอง 7 วัน • License", 14);
        sub.setGravity(Gravity.CENTER);
        layout.addView(sub);
        feedback = label("", 15);
        layout.addView(feedback);
        if (auth.getCurrentUser() != null) {
            layout.addView(label("บัญชี: " + auth.getCurrentUser().getEmail(), 14));
            action("ตรวจสอบสิทธิ์อีกครั้ง", this::checkAccess);
            action("กรอก License Key", this::showRedeem);
            if (manageMode) action("กลับไปหน้าตั้งค่า", this::finish);
            action("ออกจากระบบ", () -> {
                LicenseGate.clear();
                auth.signOut();
                draw();
                showMessage("ออกจากระบบแล้ว");
            });
        } else {
            EditText email = edit("อีเมล", false);
            EditText pass = edit("รหัสผ่าน (อย่างน้อย 6 ตัวอักษร)", true);
            action("เข้าสู่ระบบ", () -> {
                String e = email.getText().toString().trim();
                String p = pass.getText().toString();
                if (e.isEmpty() || p.isEmpty()) {showMessage("กรุณากรอกอีเมลและรหัสผ่าน");return;}
                busy(true);
                showMessage("กำลังเข้าสู่ระบบ...");
                auth.signInWithEmailAndPassword(e,p).addOnCompleteListener(this,task -> {
                    busy(false);
                    if (task.isSuccessful()) { draw(); checkAccess(); }
                    else showMessage("เข้าสู่ระบบไม่สำเร็จ: "+task.getException().getLocalizedMessage());
                });
            });
            action("สมัครสมาชิกและเริ่มทดลองใช้", () -> {
                String e = email.getText().toString().trim();
                String p = pass.getText().toString();
                if (e.isEmpty() || p.length() < 6) {showMessage("กรอกอีเมลและรหัสผ่านอย่างน้อย 6 ตัวอักษร");return;}
                busy(true);
                showMessage("กำลังสมัครสมาชิก...");
                auth.createUserWithEmailAndPassword(e,p).addOnCompleteListener(this,task -> {
                    busy(false);
                    if (task.isSuccessful()) { draw(); checkAccess(); }
                    else showMessage("สมัครสมาชิกไม่สำเร็จ: "+task.getException().getLocalizedMessage());
                });
            });
            action("ลืมรหัสผ่าน", () -> {
                String e=email.getText().toString().trim();
                if(e.isEmpty()){showMessage("กรุณากรอกอีเมลก่อน");return;}
                auth.sendPasswordResetEmail(e).addOnCompleteListener(this,t ->
                    showMessage(t.isSuccessful()?"ส่งอีเมลรีเซ็ตรหัสผ่านแล้ว":"ส่งอีเมลไม่สำเร็จ"));
            });
        }
        setContentView(sc);
    }
    private void checkAccess() {
        if (auth.getCurrentUser()==null) {draw();return;}
        busy(true);
        showMessage("กำลังตรวจสอบสิทธิ์กับเซิร์ฟเวอร์...");
        functions.getHttpsCallable("checkAccess").call().addOnCompleteListener(this, task -> {
            busy(false);
            if (!task.isSuccessful()) {
                showMessage("ไม่สามารถตรวจสอบสิทธิ์ได้ กรุณาต่ออินเทอร์เน็ตและตรวจว่า Cloud Functions ถูก Deploy แล้ว\n"+task.getException().getLocalizedMessage());
                return;
            }
            Map<?,?> data=(Map<?,?>)task.getResult().getData();
            long expires=num(data.get("expiresAt")), serverNow=num(data.get("serverNow"));
            boolean lifetime = Boolean.TRUE.equals(data.get("lifetime"));
            if (Boolean.TRUE.equals(data.get("active")) && (lifetime || expires > serverNow)) {
                LicenseGate.grant(lifetime ? 15*60*1000L : Math.min(expires-serverNow,15*60*1000L));
                if (manageMode) {
                    boolean trial = Boolean.TRUE.equals(data.get("trial"));
                    String plan = String.valueOf(data.get("plan"));
                    if (lifetime) {
                        showMessage(("pro".equals(plan) ?
                            "ParcelGuard Pro (Android + Windows)" : "ParcelGuard Mobile (Android)") +
                            "\nสิทธิ์ใช้งาน: ตลอดชีพ (ไม่มีวันหมดอายุ)" +
                            "\nสามารถกรอกคีย์ Pro เพื่ออัปเกรดได้");
                    } else {
                        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("th","TH"))
                            .format(new Date(expires));
                        showMessage((trial ? "ทดลองใช้งานอยู่" : "License แบบกำหนดระยะเวลา") +
                            "\nวันหมดอายุ: " + date + "\nกรอก License Key เพื่อเปลี่ยนเป็นซื้อขาดได้");
                    }
                } else {
                    startActivity(new Intent(this,MainActivity.class));
                    finish();
                }
            } else {
                LicenseGate.clear();
                showMessage("หมดระยะทดลองแล้ว กรุณากรอก License Key เพื่อใช้งานต่อ");
            }
        });
    }
    private static long num(Object value) {
        return value instanceof Number ? ((Number)value).longValue():0L;
    }
    private void showRedeem() {
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setHint("PG-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("เปิดใช้งาน License Key")
            .setView(input)
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ยืนยัน",(dialog,which)->{
                if(auth.getCurrentUser()==null)return;
                busy(true);
                showMessage("กำลังตรวจสอบ License...");
                java.util.HashMap<String,Object> args=new java.util.HashMap<>();
                args.put("key",input.getText().toString().trim());
                functions.getHttpsCallable("redeemLicense").call(args).addOnCompleteListener(this,task->{
                    busy(false);
                    if(task.isSuccessful())checkAccess();
                    else showMessage("เปิดใช้งานไม่สำเร็จ: "+task.getException().getLocalizedMessage());
                });
            }).show();
    }
}
