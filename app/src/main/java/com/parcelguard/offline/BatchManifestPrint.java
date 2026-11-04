package com.parcelguard.offline;

import android.app.Activity;
import android.content.Context;
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
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * One Android print job for several PHYSICAL bags. No DB changes; all source
 * evidence stays associated with original bag IDs and carriers.
 * Closed, nonempty bags only. A read-only snapshot is taken before Print dialog.
 */
final class BatchManifestPrint {
    private static final class Batch {
        final List<BagManifestPrint.Snapshot> bags;
        final List<BatchManifestPlan.Page> pages;
        final String digest, timestamp;
        final int parcelCount, weighedCount;
        final long recordedGrams;
        final Map<String,Integer> courierCounts;
        Batch(List<BagManifestPrint.Snapshot> bags,BatchManifestPlan.Mode mode){
            this.bags=Collections.unmodifiableList(new ArrayList<>(bags));
            List<Integer> counts=new ArrayList<>();
            int parcels=0,weighed=0;long total=0;
            Map<String,Integer> carriers=new LinkedHashMap<>();
            try{
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                for(BagManifestPrint.Snapshot b:bags){
                    counts.add(b.count());parcels+=b.count();
                    weighed+=b.weighed();total+=b.totalGrams();
                    carriers.put(b.carrier,carriers.getOrDefault(b.carrier,0)+b.count());
                    hash.update((b.id+"|"+b.carrier+"|"+b.digest+"\n").getBytes("UTF-8"));
                }
                byte[] bytes=hash.digest();
                StringBuilder str=new StringBuilder();
                for(int i=0;i<8;i++)str.append(String.format(Locale.US,"%02X",bytes[i]&0xff));
                digest=str.toString();
            }catch(Exception e){throw new IllegalStateException("Cannot make batch checksum",e);}
            this.pages=BatchManifestPlan.build(counts,mode);
            this.parcelCount=parcels;this.weighedCount=weighed;this.recordedGrams=total;
            this.courierCounts=Collections.unmodifiableMap(carriers);
            this.timestamp=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date());
        }
    }

    static boolean print(Activity activity,ParcelDb db,List<String> bagIds,BatchManifestPlan.Mode mode){
        if(bagIds==null || bagIds.size()<2)throw new IllegalArgumentException("Select 2 or more bags");
        if(mode==null)throw new IllegalArgumentException("Select print format");
        List<BagManifestPrint.Snapshot> bags=new ArrayList<>();
        LinkedHashSet<String> unique=new LinkedHashSet<>(bagIds);
        if(unique.size()!=bagIds.size())throw new IllegalArgumentException("Duplicate bag ID");
        if(unique.size()>100)throw new IllegalArgumentException("Maximum 100 bags in a print batch");
        for(String id:unique){
            BagManifestPrint.Snapshot b=BagManifestPrint.load(db,id);
            if(b==null)throw new IllegalArgumentException("Bag not found: "+id);
            if(!b.closed)throw new IllegalArgumentException("Bag is still open: "+id);
            if(b.count()==0)throw new IllegalArgumentException("Bag has 0 Tracking items: "+id);
            bags.add(b);
        }
        // All SPX bags together, all LEX/Flash bags together; never shuffle tracking
        // across original bags even when selecting multiple couriers.
        Collections.sort(bags,(a,b)->{
            int carrier=a.carrier.compareToIgnoreCase(b.carrier);
            if(carrier!=0)return carrier;
            int timestamp=Long.compare(a.createdAt,b.createdAt);
            return timestamp!=0?timestamp:a.id.compareTo(b.id);
        });
        Batch batch=new Batch(bags,mode);
        PrintManager printManager=(PrintManager)activity.getSystemService(Context.PRINT_SERVICE);
        if(printManager==null)return false;
        PrintAttributes attrs=new PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(new PrintAttributes.Resolution("a4","A4",300,300))
            .setMinMargins(new PrintAttributes.Margins(394,394,394,394))
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build();
        printManager.print("ParcelGuard-MULTIBAG-"+batch.digest,
            new Adapter(activity,batch,mode),attrs);
        return true;
    }

    private static final class Adapter extends PrintDocumentAdapter {
        private final Activity activity;
        private final Batch batch;
        private final BatchManifestPlan.Mode mode;
        private PrintAttributes attrs;
        Adapter(Activity activity,Batch batch,BatchManifestPlan.Mode mode){
            this.activity=activity;this.batch=batch;this.mode=mode;
        }
        @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes newAttributes,
                CancellationSignal cancellation,LayoutResultCallback callback,Bundle extras){
            if(cancellation.isCanceled()){callback.onLayoutCancelled();return;}
            attrs=newAttributes;
            int count=batch.pages.size();
            PrintDocumentInfo info=new PrintDocumentInfo.Builder(
                "ParcelGuard-MULTIBAG-"+batch.digest+".pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(count).build();
            callback.onLayoutFinished(info,!newAttributes.equals(oldAttributes));
        }
        @Override public void onWrite(PageRange[] ranges,ParcelFileDescriptor destination,
                CancellationSignal cancellation,WriteResultCallback callback){
            if(attrs==null){callback.onWriteFailed("Print layout missing");return;}
            // Print service handles file persistence; no external storage permission.
            PrintedPdfDocument pdf=new PrintedPdfDocument(activity,attrs);
            List<Integer> written=new ArrayList<>();
            try{
                for(int i=0;i<batch.pages.size();i++){
                    if(cancellation.isCanceled()){callback.onWriteCancelled();return;}
                    if(!requested(ranges,i))continue;
                    android.graphics.pdf.PdfDocument.Page page=pdf.startPage(i+1);
                    BatchManifestPlan.Page plan=batch.pages.get(i);
                    if(plan.type==BatchManifestPlan.PageType.OVERVIEW){
                        drawOverview(page.getCanvas(),pdf.getPageContentRect(),
                            batch,plan.overviewFrom,plan.overviewTo,i+1,batch.pages.size());
                    }else{
                        BagManifestPrint.Snapshot b=batch.bags.get(plan.bagIndex);
                        if(plan.type==BatchManifestPlan.PageType.LABEL){
                            BagManifestPrint.drawLabel(page.getCanvas(),pdf.getPageContentRect(),b);
                        }else{
                            BagManifestPrint.drawTrackingPage(page.getCanvas(),pdf.getPageContentRect(),b,
                                plan.localPage,BagManifestPages.pages(b.count()));
                        }
                        drawBatchFooter(page.getCanvas(),pdf.getPageContentRect(),batch.digest,
                            i+1,batch.pages.size());
                    }
                    pdf.finishPage(page);
                    written.add(i);
                }
                try(FileOutputStream file=new FileOutputStream(destination.getFileDescriptor())){
                    pdf.writeTo(file);
                }
                if(cancellation.isCanceled()){callback.onWriteCancelled();return;}
                if(written.isEmpty()){callback.onWriteFinished(new PageRange[0]);return;}
                List<PageRange> result=new ArrayList<>();
                int first=written.get(0),last=first;
                for(int i=1;i<written.size();i++){
                    int v=written.get(i);
                    if(v==last+1)last=v;
                    else{result.add(new PageRange(first,last));first=last=v;}
                }
                result.add(new PageRange(first,last));
                callback.onWriteFinished(result.toArray(new PageRange[0]));
            }catch(Exception e){callback.onWriteFailed("Cannot create batch PDF: "+e.getMessage());}
            finally{pdf.close();}
        }
        private boolean requested(PageRange[] ranges,int page){
            if(ranges==null)return false;
            for(PageRange r:ranges)if(page>=r.getStart() && page<=r.getEnd())return true;
            return false;
        }
    }

    private static Paint ink(float size,boolean bold){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return p;
    }
    private static void drawBatchFooter(Canvas canvas,Rect bounds,String digest,int page,int total){
        canvas.drawText("BATCH "+digest+"  •  "+page+"/"+total,
            bounds.left+8,bounds.bottom-4,ink(7,false));
    }
    private static void drawOverview(Canvas c,Rect rect,Batch b,int from,int to,int page,int total){
        float x=rect.left+7,top=rect.top+7,right=rect.right-7;
        c.drawText("PARCELGUARD / MULTI-BAG MANIFEST",x,top+20,ink(16,true));
        c.drawText("BATCH ID: "+b.digest,x,top+43,ink(11,true));
        c.drawText("Issued: "+b.timestamp+"  /  Bags: "+b.bags.size()+
            "  /  Parcels: "+b.parcelCount,x,top+60,ink(10,false));
        String weight=String.format(Locale.US,"Recorded weight: %.3f kg  /  Weighed: %d of %d%s",
            b.recordedGrams/1000.0,b.weighedCount,b.parcelCount,
            b.weighedCount<b.parcelCount?" (PARTIAL)":"");
        c.drawText(weight,x,top+77,ink(9,true));
        c.drawText("Bags remain separate. Carrier receipt / warehouse intake NOT confirmed.",
            x,top+92,ink(9,false));
        Paint line=ink(1,false);line.setStrokeWidth(1);
        c.drawLine(x,top+105,right,top+105,line);
        c.drawText("BAG ID",x,top+124,ink(9,true));
        c.drawText("CARRIER",x+262,top+124,ink(9,true));
        c.drawText("COUNT",x+349,top+124,ink(9,true));
        c.drawText("WEIGHT (kg)",x+401,top+124,ink(9,true));
        for(int i=from;i<to;i++){
            BagManifestPrint.Snapshot s=b.bags.get(i);
            float y=top+146+(i-from)*21;
            c.drawText(s.id,x,y,ink(9,false));
            c.drawText(s.carrier,x+262,y,ink(8.5f,false));
            c.drawText(Integer.toString(s.count()),x+349,y,ink(9,true));
            c.drawText(String.format(Locale.US,"%.3f%s",s.totalGrams()/1000.0,
                s.weighed()<s.count()?"*":""),x+401,y,ink(9,false));
        }
        float footer=rect.bottom-105;
        c.drawLine(x,footer,right,footer,line);
        c.drawText("SUMMARY: "+b.bags.size()+" BAGS, "+b.parcelCount+" TRACKING ITEMS",
            x,footer+19,ink(11,true));
        int row=0;
        for(Map.Entry<String,Integer> courier:b.courierCounts.entrySet()){
            if(row++>=3)break;
            c.drawText(courier.getKey()+": "+courier.getValue()+" parcels",
                x,footer+19+row*14,ink(8.5f,false));
        }
        c.drawText("* Weight total is partial when some parcels are not weighed.",
            x,rect.bottom-28,ink(8,false));
        drawBatchFooter(c,rect,b.digest,page,total);
    }
    private BatchManifestPrint(){}
}
