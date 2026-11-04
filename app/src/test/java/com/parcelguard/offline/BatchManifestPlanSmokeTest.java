package com.parcelguard.offline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BatchManifestPlanSmokeTest {
    private static int assertions=0;
    static void verify(List<Integer> counts,BatchManifestPlan.Mode mode,
                       int expectedPages,int expectedTracked){
        List<BatchManifestPlan.Page> pages=BatchManifestPlan.build(counts,mode);
        if(pages.size()!=expectedPages)throw new AssertionError(mode+" pages "+pages.size()+
            " expected "+expectedPages);
        assertions++;
        int totalTracked=0;
        boolean[] bagPageStart=new boolean[counts.size()];
        for(BatchManifestPlan.Page page:pages){
            if(page.type==BatchManifestPlan.PageType.OVERVIEW){
                if(page.bagIndex!=-1 || page.overviewFrom<0 || page.overviewTo>counts.size()
                        || page.overviewTo-page.overviewFrom>BatchManifestPlan.OVERVIEW_BAGS_PER_PAGE)
                    throw new AssertionError("Invalid overview page");
            } else {
                if(page.bagIndex<0 || page.bagIndex>=counts.size())
                    throw new AssertionError("Invalid bag page");
                if(page.type==BatchManifestPlan.PageType.TRACKING){
                    if(page.localPage==0)bagPageStart[page.bagIndex]=true;
                    totalTracked+=BagManifestPages.lastExclusive(page.localPage,
                            counts.get(page.bagIndex))-BagManifestPages.first(page.localPage);
                }
            }
            assertions++;
        }
        if(totalTracked!=expectedTracked)throw new AssertionError(
            "Expected all Tracking IDs exactly once: "+totalTracked+" vs "+expectedTracked);
        assertions++;
        if(mode==BatchManifestPlan.Mode.FULL_TRACKING)
            for(boolean started:bagPageStart)if(!started)throw new AssertionError("Bag omitted");
        assertions++;
    }
    public static void main(String[] args){
        verify(Arrays.asList(10,100,51),BatchManifestPlan.Mode.OVERVIEW,1,0);
        verify(Arrays.asList(10,100,51),BatchManifestPlan.Mode.LABELS,3,0);
        verify(Arrays.asList(10,100,51),BatchManifestPlan.Mode.FULL_TRACKING,6,161);
        List<Integer> hundred=new ArrayList<>();
        for(int i=0;i<100;i++)hundred.add(100);
        verify(hundred,BatchManifestPlan.Mode.OVERVIEW,5,0);
        verify(hundred,BatchManifestPlan.Mode.LABELS,100,0);
        verify(hundred,BatchManifestPlan.Mode.FULL_TRACKING,205,10000);
        boolean failed=false;
        try{BatchManifestPlan.build(Arrays.asList(0,10),BatchManifestPlan.Mode.OVERVIEW);}
        catch(IllegalArgumentException expected){failed=true;}
        if(!failed)throw new AssertionError("empty physical bags must be rejected");assertions++;
        System.out.println("BatchManifestPlan tests passed, assertions="+assertions);
    }
}
