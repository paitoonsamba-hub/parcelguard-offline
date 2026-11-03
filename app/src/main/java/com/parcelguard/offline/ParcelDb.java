package com.parcelguard.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ParcelDb extends SQLiteOpenHelper {
    public ParcelDb(Context context) { super(context, "parcelguard.db", null, 5); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bags(id TEXT PRIMARY KEY, carrier TEXT NOT NULL, closed INTEGER DEFAULT 0, created_at INTEGER, expected_count INTEGER DEFAULT 0, video_path TEXT DEFAULT '', video_started INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE parcels(tracking TEXT PRIMARY KEY, bag_id TEXT NOT NULL, photo TEXT NOT NULL, scanned_at INTEGER, deleted_at INTEGER DEFAULT 0, video_offset_ms INTEGER DEFAULT -1, video_file TEXT DEFAULT '', video_status INTEGER DEFAULT 0, weight_g INTEGER DEFAULT NULL, ocr_weight_g INTEGER DEFAULT NULL, weight_source TEXT DEFAULT '', weighed_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_bag ON parcels(bag_id)");
        db.execSQL("CREATE INDEX idx_scanned ON parcels(scanned_at)");
        db.execSQL("CREATE TABLE scale_trials(id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +" tracking TEXT NOT NULL, created_at INTEGER NOT NULL, stable INTEGER NOT NULL,"
            +" ocr_g INTEGER, confirmed_g INTEGER, outcome TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE bags ADD COLUMN expected_count INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE bags ADD COLUMN video_path TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE bags ADD COLUMN video_started INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE parcels ADD COLUMN deleted_at INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE parcels ADD COLUMN video_offset_ms INTEGER DEFAULT -1");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bag ON parcels(bag_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_scanned ON parcels(scanned_at)");
        }
        if(oldV < 3){
            db.execSQL("ALTER TABLE parcels ADD COLUMN video_file TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE parcels ADD COLUMN video_status INTEGER DEFAULT 0");
        }
        if(oldV < 4) {
            db.execSQL("ALTER TABLE parcels ADD COLUMN weight_g INTEGER DEFAULT NULL");
            db.execSQL("ALTER TABLE parcels ADD COLUMN ocr_weight_g INTEGER DEFAULT NULL");
            db.execSQL("ALTER TABLE parcels ADD COLUMN weight_source TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE parcels ADD COLUMN weighed_at INTEGER DEFAULT 0");
        }
        if(oldV < 5){
            db.execSQL("CREATE TABLE IF NOT EXISTS scale_trials("
                +"id INTEGER PRIMARY KEY AUTOINCREMENT,tracking TEXT NOT NULL,"
                +"created_at INTEGER NOT NULL, stable INTEGER NOT NULL,ocr_g INTEGER,"
                +"confirmed_g INTEGER,outcome TEXT NOT NULL)");
        }
    }

    String latestOpenBag() {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id FROM bags WHERE closed=0 ORDER BY created_at DESC LIMIT 1",null)) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }
    boolean addBag(String id, String carrier) {
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("carrier", carrier); v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("bags", null, v) > 0;
    }
    String bagCarrier(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT carrier FROM bags WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    Cursor expiredVideos(long cutoff) {
        return getReadableDatabase().rawQuery("SELECT id, video_path FROM bags WHERE created_at<? AND video_path != ''", new String[]{Long.toString(cutoff)});
    }
    void clearVideo(String id) {setVideo(id,"",0L);}
    void setExpected(String id, int value) {
        ContentValues v = new ContentValues(); v.put("expected_count", value);
        getWritableDatabase().update("bags", v, "id=?", new String[]{id});
    }
    int expected(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT expected_count FROM bags WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    boolean isClosed(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT closed FROM bags WHERE id=?", new String[]{id})) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }
    void closeBag(String id) {
        ContentValues v = new ContentValues(); v.put("closed", 1);
        getWritableDatabase().update("bags", v, "id=?", new String[]{id});
    }
    void setVideo(String id, String path, long started) {
        ContentValues v = new ContentValues(); v.put("video_path", path); v.put("video_started", started);
        getWritableDatabase().update("bags", v, "id=?", new String[]{id});
    }
    String videoPath(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT video_path FROM bags WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    long videoStart(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT video_started FROM bags WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }
    Cursor bagList() { return getReadableDatabase().rawQuery("SELECT id,carrier,closed,created_at,expected_count,video_path FROM bags ORDER BY created_at DESC LIMIT 100", null); }
    boolean exists(String t) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM parcels WHERE tracking=?", new String[]{t})) {return c.moveToFirst();}
    }
    boolean addParcel(String t, String bag, String photo, long offset) {
        if (isClosed(bag)) return false;
        ContentValues v = new ContentValues(); v.put("tracking", t); v.put("bag_id", bag); v.put("photo", photo);
        v.put("scanned_at", System.currentTimeMillis()); v.put("video_offset_ms", offset);
        return getWritableDatabase().insert("parcels", null, v) != -1;
    }
    // 0 = no clip, 1 = recording, 2 = completed, 3 = failed.
    void markParcelVideoPending(String tracking) {setParcelVideoState(tracking,"",1);}
    void markParcelVideoFailed(String tracking) {setParcelVideoState(tracking,"",3);}
    void clearParcelVideo(String tracking) {setParcelVideoState(tracking,"",0);}
    void setParcelVideo(String tracking,String path){setParcelVideoState(tracking,path,2);}
    private void setParcelVideoState(String tracking,String path,int state){
        ContentValues v=new ContentValues();v.put("video_file",path);v.put("video_status",state);
        getWritableDatabase().update("parcels",v,"tracking=?",new String[]{tracking});
    }
    String photoPath(String tracking){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT photo FROM parcels WHERE tracking=? AND deleted_at=0",new String[]{tracking})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    static final class WeightRecord {
        final int actual,ocr; final String source;
        WeightRecord(int actual,int ocr,String source){
            this.actual=actual;this.ocr=ocr;this.source=source;
        }
    }
    WeightRecord weight(String tracking){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT weight_g,ocr_weight_g,weight_source FROM parcels WHERE tracking=? AND deleted_at=0",
                new String[]{tracking})){
            if(!c.moveToFirst())return new WeightRecord(-1,-1,"");
            return new WeightRecord(c.isNull(0)?-1:c.getInt(0),
                c.isNull(1)?-1:c.getInt(1),c.getString(2)==null?"":c.getString(2));
        }
    }
    boolean setWeight(String tracking,Integer corrected,Integer recognized,String source){
        if(corrected!=null && (corrected<1 || corrected>100000))return false;
        ContentValues v=new ContentValues();
        if(corrected==null){
            v.putNull("weight_g");v.putNull("ocr_weight_g");
            v.put("weight_source","");v.put("weighed_at",0L);
        }else{
            v.put("weight_g",corrected);
            if(recognized==null || recognized<1)v.putNull("ocr_weight_g");
            else v.put("ocr_weight_g",recognized);
            v.put("weight_source",source);
            v.put("weighed_at",System.currentTimeMillis());
        }
        return getWritableDatabase().update("parcels",v,
            "tracking=? AND deleted_at=0",new String[]{tracking})>0;
    }
    long addScaleTrial(String tracking,boolean stable,int grams){
        ContentValues v=new ContentValues();
        v.put("tracking",tracking);
        v.put("created_at",System.currentTimeMillis());
        v.put("stable",stable?1:0);
        if(grams>0)v.put("ocr_g",grams);else v.putNull("ocr_g");
        v.put("outcome",stable?"not_confirmed":"unreadable");
        return getWritableDatabase().insert("scale_trials",null,v);
    }
    void confirmScaleTrial(long id,int actual,int initial){
        if(id<0)return;
        ContentValues v=new ContentValues();
        v.put("confirmed_g",actual);
        v.put("outcome",actual==initial?"confirmed":"corrected");
        getWritableDatabase().update("scale_trials",v,"id=?",new String[]{Long.toString(id)});
    }
    Cursor scaleTrialStats(){
        return getReadableDatabase().rawQuery(
            "SELECT COUNT(*),"
            +" SUM(CASE WHEN stable=1 THEN 1 ELSE 0 END),"
            +" SUM(CASE WHEN outcome='confirmed' THEN 1 ELSE 0 END),"
            +" SUM(CASE WHEN outcome='corrected' THEN 1 ELSE 0 END)"
            +" FROM scale_trials",null);
    }
    Cursor weightStats(){
        return getReadableDatabase().rawQuery(
            "SELECT COUNT(*), SUM(CASE WHEN weight_g IS NOT NULL THEN 1 ELSE 0 END),"
            +" SUM(CASE WHEN weight_source='ocr_verified' THEN 1 ELSE 0 END),"
            +" SUM(CASE WHEN weight_source='ocr_corrected' THEN 1 ELSE 0 END),"
            +" SUM(CASE WHEN weight_source='manual' THEN 1 ELSE 0 END)"
            +" FROM parcels WHERE deleted_at=0",null);
    }
    String parcelVideoPath(String tracking){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT video_file FROM parcels WHERE tracking=?",new String[]{tracking})){
            return c.moveToFirst() && !c.isNull(0)?c.getString(0):"";
        }
    }
    int parcelVideoState(String tracking){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT video_status FROM parcels WHERE tracking=?",new String[]{tracking})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    Cursor expiredParcelVideos(long cutoff) {
        return getReadableDatabase().rawQuery("SELECT tracking,video_file FROM parcels WHERE scanned_at<? AND video_file!=''",new String[]{Long.toString(cutoff)});
    }
    int count(String bag) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM parcels WHERE bag_id=? AND deleted_at=0", new String[]{bag})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    Cursor find(String q, boolean deleted, long from, long to) {
        String like = "%" + q.replace("%", "\\%").replace("_", "\\_") + "%";
        return getReadableDatabase().rawQuery("SELECT tracking,bag_id,photo,scanned_at,video_offset_ms FROM parcels WHERE deleted_at " + (deleted ? ">0" : "=0") + " AND scanned_at>=? AND scanned_at<=? AND (tracking LIKE ? ESCAPE '\\' OR bag_id LIKE ? ESCAPE '\\') ORDER BY scanned_at DESC LIMIT 150", new String[]{Long.toString(from), Long.toString(to), like, like});
    }
    Cursor allActive() {
        return getReadableDatabase().rawQuery("SELECT tracking,bag_id,photo,scanned_at,video_offset_ms FROM parcels WHERE deleted_at=0 ORDER BY scanned_at DESC", null);
    }
    Cursor bagActive(String bag) {
        return getReadableDatabase().rawQuery("SELECT tracking,bag_id,photo,scanned_at,video_offset_ms FROM parcels WHERE deleted_at=0 AND bag_id=? ORDER BY scanned_at DESC", new String[]{bag});
    }
    Cursor deletedRowsOlder(long cutoff) {
        return getReadableDatabase().rawQuery("SELECT tracking,photo FROM parcels WHERE deleted_at>0 AND deleted_at<?", new String[]{Long.toString(cutoff)});
    }
    void trash(String tracking) {
        ContentValues v = new ContentValues(); v.put("deleted_at", System.currentTimeMillis());
        getWritableDatabase().update("parcels", v, "tracking=? AND deleted_at=0", new String[]{tracking});
    }
    void restore(String tracking) {
        ContentValues v = new ContentValues(); v.put("deleted_at", 0);
        getWritableDatabase().update("parcels", v, "tracking=? AND deleted_at>0", new String[]{tracking});
    }
    void permanentlyDelete(String tracking) { getWritableDatabase().delete("parcels", "tracking=? AND deleted_at>0", new String[]{tracking}); }
}
