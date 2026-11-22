package com.group4.kaoyan.modules.chat.stream;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCancellationRegistryTest {

    @Test
    void shouldCancelOnlyOwnerAndRunCallbackOnce() {
        ChatCancellationRegistry registry = new ChatCancellationRegistry();
        AtomicInteger calls = new AtomicInteger();
        ChatCancellationRegistry.Registration registration =
                registry.register(7L, "stream-1", calls::incrementAndGet);

        assertFalse(registry.cancel(8L, "stream-1"));
        assertTrue(registry.cancel(7L, "stream-1"));
        assertTrue(registry.cancel(7L, "stream-1"));
        assertEquals(1, calls.get());

        registration.close();
        assertFalse(registry.cancel(7L, "stream-1"));
    }
}
