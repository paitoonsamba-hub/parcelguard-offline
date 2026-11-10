package com.parcelguard.offline;

public final class TrialPolicySmokeTest {
    private static int count=0;
    private static void eq(TrialPolicy.State actual,TrialPolicy.State expected){
        if(actual!=expected)throw new AssertionError("Expected "+expected+" got "+actual);
        count++;
    }
    public static void main(String[] unused){
        final long start=1762750000000L;
        final long ends=start+TrialPolicy.TRIAL;
        final long offline=start+TrialPolicy.OFFLINE;
        eq(TrialPolicy.evaluate(start+1000,10000,start,9000,start,start,ends,offline),
            TrialPolicy.State.ACTIVE);
        eq(TrialPolicy.evaluate(start+2*TrialPolicy.DAY,100000,start,9000,start,start,ends,offline),
            TrialPolicy.State.NEED_ONLINE);
        eq(TrialPolicy.evaluate(ends+1,10000,start,9000,start,start,ends,offline),
            TrialPolicy.State.EXPIRED);
        eq(TrialPolicy.evaluate(start-3600000,10000,start,9000,start,start,ends,offline),
            TrialPolicy.State.CLOCK_INVALID);
        // Reboot resets elapsedRealtime; a high-water wall clock still blocks rollback.
        eq(TrialPolicy.evaluate(start+10000,200,start+TrialPolicy.DAY,9000,start+TrialPolicy.DAY,start,ends,offline),
            TrialPolicy.State.CLOCK_INVALID);
        // No token? never guess that the customer is eligible.
        eq(TrialPolicy.evaluate(start+1000,10000,0,0,0,0,0,0),
            TrialPolicy.State.NEED_LOGIN);
        // Offline expiry is bounded by actual signed expiresAt.
        eq(TrialPolicy.evaluate(ends-1,9000,start,9000,start,start,ends,ends),
            TrialPolicy.State.ACTIVE);
        eq(TrialPolicy.evaluate(ends,9000,start,9000,start,start,ends,ends),
            TrialPolicy.State.EXPIRED);
        System.out.println("TrialPolicy smoke tests passed: "+count);
    }
}
