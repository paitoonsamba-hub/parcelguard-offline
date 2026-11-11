package com.parcelguard.offline;
import android.os.SystemClock;
/** Short-lived session lease. Only LicenseActivity can grant after a server entitlement check. */
final class LicenseGate {
    private static long untilElapsed = 0;
    static void grant(long millis) {
        untilElapsed = SystemClock.elapsedRealtime() + Math.max(0L, millis);
    }
    static boolean allowed() {
        return untilElapsed > SystemClock.elapsedRealtime();
    }
    static void clear() {untilElapsed = 0;}
    private LicenseGate() {}
}
