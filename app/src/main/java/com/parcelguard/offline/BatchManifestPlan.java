package com.parcelguard.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure Java print plan, independent of Android and printer implementation. */
final class BatchManifestPlan {
    static final int OVERVIEW_BAGS_PER_PAGE=20;
    enum Mode { OVERVIEW, LABELS, FULL_TRACKING }
    enum PageType { OVERVIEW, LABEL, TRACKING }

    static final class Page {
        final PageType type;
        final int bagIndex;        // -1 for overview pages
        final int localPage;       // 0-indexed within bag, for TRACKING
        final int overviewFrom;    // inclusive within list of bags
        final int overviewTo;      // exclusive within list of bags
        Page(PageType type,int bagIndex,int localPage,int from,int to) {
            this.type=type;this.bagIndex=bagIndex;this.localPage=localPage;
            overviewFrom=from;overviewTo=to;
        }
    }
    static List<Page> build(List<Integer> bagParcelCounts, Mode mode) {
        if(mode==null || bagParcelCounts==null || bagParcelCounts.isEmpty())
            throw new IllegalArgumentException("Select at least one bag");
        if(bagParcelCounts.size()>100)
            throw new IllegalArgumentException("Maximum 100 bags per batch");
        for(Integer n:bagParcelCounts)if(n==null || n<=0)
            throw new IllegalArgumentException("All bags must contain parcels");
        List<Page> pages=new ArrayList<>();
        if(mode==Mode.OVERVIEW || mode==Mode.FULL_TRACKING)
            for(int from=0;from<bagParcelCounts.size();from+=OVERVIEW_BAGS_PER_PAGE)
                pages.add(new Page(PageType.OVERVIEW,-1,0,from,
                        Math.min(bagParcelCounts.size(),from+OVERVIEW_BAGS_PER_PAGE)));
        if(mode==Mode.LABELS)
            for(int i=0;i<bagParcelCounts.size();i++)
                pages.add(new Page(PageType.LABEL,i,0,-1,-1));
        if(mode==Mode.FULL_TRACKING)
            for(int i=0;i<bagParcelCounts.size();i++)
                for(int local=0;local<BagManifestPages.pages(bagParcelCounts.get(i));local++)
                    pages.add(new Page(PageType.TRACKING,i,local,-1,-1));
        return Collections.unmodifiableList(pages);
    }
    private BatchManifestPlan(){}
}
