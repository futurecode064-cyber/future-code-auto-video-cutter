package com.futurecode.autovideocutter;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClipPlanTest {
    @Test public void twoHoursFiveMinutesIsTwentyFourParts() {
        long count = ClipPlan.count(7_200_000L, 300_000L);
        assertEquals(24, count);
        assertArrayEquals(new long[]{0, 300_000}, ClipPlan.bounds(7_200_000, 300_000, 0));
        assertArrayEquals(new long[]{6_900_000, 7_200_000}, ClipPlan.bounds(7_200_000, 300_000, 23));
    }
    @Test public void trailingRemainderIsNotLost() {
        assertEquals(3, ClipPlan.count(601_501, 300_000));
        assertArrayEquals(new long[]{600_000, 601_501}, ClipPlan.bounds(601_501, 300_000, 2));
    }
    @Test public void segmentsAreContiguous() {
        long duration = 9_123_456, size = 250_000, last = 0;
        for (long i = 0; i < ClipPlan.count(duration, size); i++) {
            long[] b = ClipPlan.bounds(duration, size, i);
            assertEquals(last, b[0]);
            assertTrue(b[1] > b[0]);
            last = b[1];
        }
        assertEquals(duration, last);
    }
    @Test(expected = IllegalArgumentException.class) public void invalidInputIsRejected() {
        ClipPlan.count(0, 300_000);
    }
}
