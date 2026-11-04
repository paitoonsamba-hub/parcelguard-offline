package com.parcelguard.offline;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;

/**
 * Offline A4 printing and Save-as-PDF from the Android system Print dialog.
 * Each document is a read-only snapshot of ONE bag (no contact/customer PII).
 * QR contains the BAG ID only; it never contains a LAN token or public URL.
 */
final class BagManifestPrint {
    static final class Entry {
        final String tracking; final long scannedAt; final int weight; final String source;
        Entry(String tracking,long scannedAt,int weight,String source) {
            this.tracking=tracking;this.scannedAt=scannedAt;
            this.weight=weight;this.source=source;
        }
    }
    static final class Snapshot {
        final String id, carrier, digest;
        final boolean closed; final int expected; final long createdAt, exportedAt;
        final List<Entry> entries;
        Snapshot(String id,String carrier,boolean closed,int expected,long createdAt,List<Entry> entries) {
            this.id=id;this.carrier=carrier;this.closed=closed;this.expected=expected;
            this.createdAt=createdAt;this.exportedAt=System.currentTimeMillis();
            this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
            this.digest=fingerprint(id,this.entries);
        }
        int count(){return entries.size();}
        int weighed(){int n=0;for(Entry e:entries)if(e.weight>=0)n++;return n;}
        long totalGrams(){long n=0;for(Entry e:entries)if(e.weight>=0)n+=e.weight;return n;}
    }

    private static String fingerprint(String id,List<Entry> entries) {
        try {
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            sha.update(id.getBytes("UTF-8"));
            for(Entry entry:entries) {
                sha.update((byte)'\n');
                sha.update(entry.tracking.getBytes("UTF-8"));
                sha.update((byte)'|');
                sha.update(Integer.toString(entry.weight).getBytes("UTF-8"));
                sha.update((byte)'|');
                sha.update(entry.source.getBytes("UTF-8"));
            }
            byte[] bytes=sha.digest();
            StringBuilder out=new StringBuilder();
            for(int i=0;i<8;i++)out.append(String.format(Locale.US,"%02X",bytes[i]&0xff));
            return out.toString();
        }catch(Exception e){return "UNAVAILABLE";}
    }

    static Snapshot load(ParcelDb db,String id){
        String carrier=null;boolean closed=false;int expected=0;long createdAt=0;
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT carrier,closed,created_at,expected_count FROM bags WHERE id=?",
                new String[]{id})){
            if(!c.moveToFirst())return null;
            carrier=c.getString(0);closed=c.getInt(1)==1;createdAt=c.getLong(2);expected=c.getInt(3);
        }
        List<Entry> entries=new ArrayList<>();
        try(Cursor c=db.bagActive(id)){
            while(c.moveToNext()){
                String tracking=c.getString(0);
                ParcelDb.WeightRecord weight=db.weight(tracking);
                entries.add(new Entry(tracking,c.getLong(3),weight.actual,weight.source));
            }
        }
        // Consistent chronological sequence; do not include trashed records.
        Collections.sort(entries,(left,right)->{
            int x=Long.compare(left.scannedAt,right.scannedAt);
            return x!=0?x:left.tracking.compareTo(right.tracking);
        });
        return new Snapshot(id,carrier==null?"":carrier,closed,expected,createdAt,entries);
    }

    static boolean print(Activity activity,ParcelDb db,String id,boolean summaryOnly){
        Snapshot snapshot=load(db,id);
        if(snapshot==null)return false;
        PrintManager print=(PrintManager)activity.getSystemService(Context.PRINT_SERVICE);
        if(print==null)return false;
        PrintAttributes attrs=new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("a4","A4",300,300))
                .setMinMargins(new PrintAttributes.Margins(394,394,394,394))
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build();
        String safeName=id.replaceAll("[^A-Za-z0-9_-]","_");
        print.print("ParcelGuard-"+safeName+(summaryOnly?"-BagLabel":"-TrackingList"),
                new Adapter(activity,snapshot,summaryOnly),attrs);
        return true;
    }

    private static final class Adapter extends PrintDocumentAdapter {
        private final Activity activity;
        private final Snapshot data;
        private final boolean summaryOnly;
        private PrintAttributes attrs;
        private int pageCount;
        Adapter(Activity activity,Snapshot data,boolean summaryOnly){
            this.activity=activity;this.data=data;this.summaryOnly=summaryOnly;
        }
        @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes newAttributes,
                CancellationSignal cancellation,LayoutResultCallback cb,Bundle extras){
            if(cancellation.isCanceled()){cb.onLayoutCancelled();return;}
            attrs=newAttributes;
            pageCount=summaryOnly?1:BagManifestPages.pages(data.count());
            PrintDocumentInfo info=new PrintDocumentInfo.Builder(
                    "ParcelGuard-"+data.id+(summaryOnly?"-BagLabel.pdf":"-TrackingList.pdf"))
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount).build();
            cb.onLayoutFinished(info,!newAttributes.equals(oldAttributes));
        }
        @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,
                CancellationSignal cancellation,WriteResultCallback cb){
            if(attrs==null){cb.onWriteFailed("Print layout not ready");return;}
            PrintedPdfDocument pdf=new PrintedPdfDocument(activity,attrs);
            List<Integer> written=new ArrayList<>();
            try {
                for(int i=0;i<pageCount;i++){
                    if(cancellation.isCanceled()){cb.onWriteCancelled();return;}
                    if(!requested(pages,i))continue;
                    android.graphics.pdf.PdfDocument.Page page=pdf.startPage(i+1);
                    if(summaryOnly)drawLabel(page.getCanvas(),pdf.getPageContentRect(),data);
                    else drawTrackingPage(page.getCanvas(),pdf.getPageContentRect(),data,i,pageCount);
                    pdf.finishPage(page);
                    written.add(i);
                }
                try(FileOutputStream file=new FileOutputStream(destination.getFileDescriptor())){
                    pdf.writeTo(file);
                }
                if(cancellation.isCanceled()){cb.onWriteCancelled();return;}
                if(written.size()==0){cb.onWriteFinished(new PageRange[0]);return;}
                // Report precisely the individual pages that were written.
                List<PageRange> out=new ArrayList<>();
                int start=written.get(0),last=start;
                for(int j=1;j<written.size();j++){
                    int value=written.get(j);
                    if(value==last+1)last=value;
                    else{out.add(new PageRange(start,last));start=last=value;}
                }
                out.add(new PageRange(start,last));
                cb.onWriteFinished(out.toArray(new PageRange[0]));
            }catch(Exception e){cb.onWriteFailed(e.getMessage()==null?"Cannot produce PDF":e.getMessage());}
            finally{pdf.close();}
        }
        private boolean requested(PageRange[] requested,int page){
            if(requested==null || requested.length==0)return false;
            for(PageRange range:requested)if(page>=range.getStart() && page<=range.getEnd())return true;
            return false;
        }
    }

    private static Paint text(float size,boolean bold,int color){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return p;
    }
    private static void line(Canvas c,float left,float top,float right,int color){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(0.8f);
        c.drawLine(left,top,right,top,p);
    }
    private static String date(long value){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date(value));
    }
    private static void label(Canvas c,float x,float y,String value,float size,boolean bold){
        c.drawText(value,x,y,text(size,bold,Color.BLACK));
    }
    private static void qr(Canvas c,String value,float x,float y,float side){
        try {
            EnumMap<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN,1);
            hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);
            BitMatrix m=new QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,41,41,hints);
            float cell=side/m.getWidth();
            Paint ink=new Paint();ink.setColor(Color.BLACK);ink.setStyle(Paint.Style.FILL);
            for(int row=0;row<m.getHeight();row++)
                for(int col=0;col<m.getWidth();col++)
                    if(m.get(col,row))c.drawRect(x+col*cell,y+row*cell,
                            x+(col+1)*cell,y+(row+1)*cell,ink);
        }catch(Exception e){label(c,x,y+12,"BAG QR unavailable",8,false);}
    }
    private static void header(Canvas c,Rect bounds,Snapshot data,String heading){
        float l=bounds.left+6,r=bounds.right-6,t=bounds.top+6;
        label(c,l,t+20,"ParcelGuard / "+heading,17,true);
        label(c,l,t+44,data.id,12,true);
        label(c,l,t+64,"CARRIER: "+data.carrier+"    PARCELS: "+data.count(),11,true);
        label(c,l,t+81,"Bag created: "+date(data.createdAt),9,false);
        label(c,l,t+95,"Document issued: "+date(data.exportedAt),9,false);
        label(c,l,t+109,"Manifest ID: "+data.digest,9,false);
        label(c,l,t+123,data.closed?"STATUS: CLOSED IN APP":"DRAFT - BAG IS STILL OPEN",9,true);
        qr(c,"PARCELGUARD|BAG|"+data.id,r-85,t+6,82);
        line(c,l,t+135,r,Color.DKGRAY);
    }

    static void drawTrackingPage(Canvas c,Rect bounds,Snapshot data,int page,int pages){
        header(c,bounds,data,"TRACKING MANIFEST");
        float l=bounds.left+6,r=bounds.right-6,t=bounds.top+6;
        float split=(r-l-18)/2f;
        int from=BagManifestPages.first(page),to=BagManifestPages.lastExclusive(page,data.count());
        label(c,l,t+153,"NO.  TRACKING / TIME / WEIGHT",9,true);
        label(c,l+split+18,t+153,"NO.  TRACKING / TIME / WEIGHT",9,true);
        float firstY=t+175;
        // Fit 25 rows per column while reserving a signature / verification footer.
        float available=(bounds.bottom-6-83)-firstY;
        float gap=Math.min(22f,available/BagManifestPages.ROWS_PER_COLUMN);
        for(int i=from;i<to;i++){
            int within=i-from;
            int column=within/BagManifestPages.ROWS_PER_COLUMN;
            int row=within%BagManifestPages.ROWS_PER_COLUMN;
            float x=l+column*(split+18), y=firstY+row*gap;
            Entry entry=data.entries.get(i);
            label(c,x,y,String.format(Locale.US,"%03d",i+1),9,false);
            Paint code=text(10.7f,true,Color.BLACK);
            if(code.measureText(entry.tracking)>split-26)
                code.setTextSize(9.2f);
            c.drawText(entry.tracking,x+25,y,code);
            String weight=entry.weight<0?"-- kg (not weighed)":
                String.format(Locale.US,"%.3f kg%s",entry.weight/1000.0,
                entry.source.startsWith("ocr")?" (OCR)":" (manual)");
            label(c,x+25,y+10,date(entry.scannedAt)+"   "+weight,7.0f,false);
        }
        float foot=bounds.bottom-6-66;
        line(c,l,foot,r,Color.DKGRAY);
        label(c,l,foot+13,"Recorded weights: "+data.weighed()+"/"+data.count()+
            "   TOTAL: "+String.format(Locale.US,"%.3f kg",data.totalGrams()/1000.0)+
            (data.weighed()<data.count()?" (PARTIAL)":" (COMPLETE)"),9,true);
        label(c,l,foot+23,"Shop manifest, NOT confirmation that courier received individual parcels.",8,false);
        label(c,l,foot+32,"Packed by: ____________________   Checked by: ____________________",9,false);
        label(c,l,foot+47,"Bag ID: "+data.id,8,false);
        label(c,r-100,foot+47,"PAGE "+(page+1)+" / "+pages,9,true);
    }

    static void drawLabel(Canvas c,Rect bounds,Snapshot data){
        header(c,bounds,data,"BAG SUMMARY / ใบสรุปติดถุง");
        float l=bounds.left+6,r=bounds.right-6,t=bounds.top+6;
        label(c,l,t+185,"TRANSPORT / ขนส่ง",13,true);
        label(c,l,t+212,data.carrier,22,true);
        label(c,l,t+257,"BAG ID / รหัสถุง",13,true);
        Paint bagText=text(18,true,Color.BLACK);
        if(bagText.measureText(data.id)>r-l)bagText.setTextSize(13);
        c.drawText(data.id,l,t+281,bagText);
        label(c,l,t+335,"PACKED & SCANNED",13,true);
        label(c,l,t+396,String.format(Locale.US,"%d PARCELS",data.count()),34,true);
        if(data.expected>0)label(c,l,t+429,"Declared target: "+data.expected,11,false);
        label(c,l,t+453,"WEIGHED: "+data.weighed()+"/"+data.count()+" parcels",11,true);
        label(c,l,t+475,"TOTAL RECORDED WEIGHT: "+
            String.format(Locale.US,"%.3f kg",data.totalGrams()/1000.0)+
            (data.weighed()<data.count()?" (PARTIAL)":""),12,true);
        label(c,l,t+493,data.closed?"Closed in ParcelGuard":"DRAFT - STILL OPEN",11,true);
        label(c,l,t+511,"Manifest ID: "+data.digest,10,false);
        label(c,l,t+542,"Print full Tracking list separately when required.",11,false);
        float sign=Math.min(t+601,bounds.bottom-6-106);
        line(c,l,sign,r,Color.DKGRAY);
        label(c,l,sign+22,"Prepared by: _____________________________________",10,false);
        label(c,l,sign+43,"Carrier handover signature (if obtained): ___________",10,false);
        label(c,l,sign+64,"Received at: _________________________________",10,false);
        label(c,l,bounds.bottom-6-14,
                "ParcelGuard shop record only - NOT proof of carrier warehouse acceptance.",8,false);
    }
}
