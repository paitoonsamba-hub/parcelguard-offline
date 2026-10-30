package com.parcelguard.offline;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
public class ParcelDb extends SQLiteOpenHelper {
 ParcelDb(Context c){super(c,"parcelguard.db",null,1);}
 public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE bags(id TEXT PRIMARY KEY, carrier TEXT NOT NULL, closed INTEGER DEFAULT 0, created_at INTEGER)");db.execSQL("CREATE TABLE parcels(tracking TEXT PRIMARY KEY, bag_id TEXT NOT NULL, photo TEXT NOT NULL, scanned_at INTEGER)");}
 public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}
 boolean addBag(String id,String carrier){try{ContentValues v=new ContentValues();v.put("id",id);v.put("carrier",carrier);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("bags",null,v)>0;}catch(Exception e){return false;}}
 boolean exists(String tracking){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM parcels WHERE tracking=?",new String[]{tracking})){return c.moveToFirst();}}
 boolean addParcel(String t,String bag,String path){try{ContentValues v=new ContentValues();v.put("tracking",t);v.put("bag_id",bag);v.put("photo",path);v.put("scanned_at",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("parcels",null,v)>0;}catch(Exception e){return false;}}
 int count(String bag){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM parcels WHERE bag_id=?",new String[]{bag})){c.moveToFirst();return c.getInt(0);}}
 void closeBag(String id){ContentValues v=new ContentValues();v.put("closed",1);getWritableDatabase().update("bags",v,"id=?",new String[]{id});}
 Cursor list(){return getReadableDatabase().rawQuery("SELECT tracking,bag_id,photo,scanned_at FROM parcels ORDER BY scanned_at DESC",null);}
}
