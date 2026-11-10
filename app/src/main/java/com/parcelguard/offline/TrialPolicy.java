package com.parcelguard.offline;

/**
 * Pure time policy for an authenticated seven-day trial.
 * A verified server receipt is required before this policy is evaluated.
 * Package-private by design so it can be smoke-tested with plain javac.
 */
final class TrialPolicy {
    static final long DAY = 24L*60*60*1000;
    static final long TRIAL = 7*DAY;
    static final long OFFLINE = 48L*60*60*1000;
    static final long CLOCK_SKEW = 2*60*1000;

    enum State { NEED_LOGIN, ACTIVE, NEED_ONLINE, EXPIRED, CLOCK_INVALID }

    static State evaluate(long wallNow, long elapsedNow, long trustedWall,
                          long trustedElapsed, long highWater,
                          long startedAt, long endsAt, long offlineUntil) {
        if (startedAt<=0 || endsAt-startedAt!=TRIAL) return State.NEED_LOGIN;
        if (wallNow<=0 || elapsedNow<0 || wallNow+CLOCK_SKEW<trustedWall
                || wallNow+CLOCK_SKEW<highWater)
            return State.CLOCK_INVALID;
        // elapsedRealtime cannot be moved with Settings -> Date/Time.
        // A reboot resets this clock; then we fall back to monotonic highWater.
        long effective=Math.max(wallNow,highWater);
        if (elapsedNow>=trustedElapsed && trustedWall>0) {
            long elapsedDelta=elapsedNow-trustedElapsed;
            if (elapsedDelta<=TRIAL+OFFLINE)
                effective=Math.max(effective,trustedWall+elapsedDelta);
        }
        if (effective>=endsAt) return State.EXPIRED;
        if (effective>=offlineUntil) return State.NEED_ONLINE;
        return State.ACTIVE;
    }

    private TrialPolicy() {}
}
