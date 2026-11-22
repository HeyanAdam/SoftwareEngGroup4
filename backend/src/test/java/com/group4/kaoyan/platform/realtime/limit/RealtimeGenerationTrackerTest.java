package com.group4.kaoyan.platform.realtime.limit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeGenerationTrackerTest {

    @Test
    void shouldLimitPerConnectionAndReleaseBothCounters() {
        RealtimeGenerationTracker tracker = new RealtimeGenerationTracker();

        assertTrue(tracker.tryAcquire(7L, "tab-1", 2, 1));
        assertFalse(tracker.tryAcquire(7L, "tab-1", 2, 1));
        assertTrue(tracker.tryAcquire(7L, "tab-2", 2, 1));
        assertEquals(2, tracker.userCount(7L));

        tracker.release(7L, "tab-1");
        assertEquals(1, tracker.userCount(7L));
        assertEquals(0, tracker.connectionCount(7L, "tab-1"));
        assertTrue(tracker.tryAcquire(7L, "tab-1", 2, 1));
    }

    @Test
    void shouldRejectWhenUserLimitReached() {
        RealtimeGenerationTracker tracker = new RealtimeGenerationTracker();

        assertTrue(tracker.tryAcquire(7L, "tab-1", 1, 1));
        assertFalse(tracker.tryAcquire(7L, "tab-2", 1, 1));
        assertEquals(1, tracker.userCount(7L));
    }
}
