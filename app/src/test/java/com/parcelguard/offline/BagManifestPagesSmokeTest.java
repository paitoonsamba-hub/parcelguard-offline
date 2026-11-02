package com.parcelguard.offline;
public final class BagManifestPagesSmokeTest {
    private static void check(int size,int expected){
        int actual=BagManifestPages.pages(size);
        if(actual!=expected)throw new AssertionError(size+" -> "+actual+", expected "+expected);
        int total=0;
        for(int p=0;p<actual;p++){
            int from=BagManifestPages.first(p),to=BagManifestPages.lastExclusive(p,size);
            if(to<from || to-from>BagManifestPages.PER_PAGE)
                throw new AssertionError("Invalid page "+p+" for "+size);
            total+=to-from;
        }
        if(total!=size)throw new AssertionError("Missing tracking numbers: "+size+" vs "+total);
    }
    public static void main(String[] args){
        check(0,1);check(1,1);check(10,1);check(49,1);check(50,1);
        check(51,2);check(99,2);check(100,2);check(101,3);
        check(200,4);check(201,5);check(500,10);
        System.out.println("Bag manifest pagination: all 12 cases passed");
    }
}
