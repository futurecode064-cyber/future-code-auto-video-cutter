package com.futurecode.autovideocutter;

/** Unit-testable partition math with no Android dependencies. */
public final class ClipPlan {
    private ClipPlan() {}
    public static long count(long durationMs, long segmentMs) {
        if (durationMs <= 0 || segmentMs <= 0) throw new IllegalArgumentException("Duration and segment must be positive");
        return ((durationMs - 1) / segmentMs) + 1;
    }
    public static long[] bounds(long durationMs, long segmentMs, long index) {
        long count = count(durationMs, segmentMs);
        if (index < 0 || index >= count) throw new IllegalArgumentException("Invalid part index");
        if (index > Long.MAX_VALUE / segmentMs) throw new IllegalArgumentException("Overflow");
        long start = index * segmentMs;
        long end = start > durationMs - segmentMs ? durationMs : start + segmentMs;
        return new long[]{start, end};
    }
}
