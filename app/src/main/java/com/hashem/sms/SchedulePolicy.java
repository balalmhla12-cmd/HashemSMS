package com.hashem.sms;

/** Pure scheduling-time policy; the ten-minute boundary is included in the send window. */
final class SchedulePolicy {
    static final long MAX_LATE_MILLIS = 10L * 60L * 1000L;

    private SchedulePolicy() {}

    static boolean isWithinLateWindow(long lateByMillis) {
        return lateByMillis <= MAX_LATE_MILLIS;
    }
}
