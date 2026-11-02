package com.parcelguard.offline;

/** Pure Java pagination, also verified in GitHub Actions without Android. */
final class BagManifestPages {
    static final int ROWS_PER_COLUMN = 25;
    static final int COLUMNS = 2;
    static final int PER_PAGE = ROWS_PER_COLUMN * COLUMNS;
    static int pages(int count) {
        if (count < 0) throw new IllegalArgumentException("negative count");
        return Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
    }
    static int first(int page) { return page * PER_PAGE; }
    static int lastExclusive(int page, int count) {
        return Math.min(count, first(page) + PER_PAGE);
    }
    private BagManifestPages() {}
}
