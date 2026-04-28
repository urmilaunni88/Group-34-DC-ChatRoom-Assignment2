package chatroom.dme;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Lamport logical clock.
 *
 * Rules:
 *  - Increment before every send.
 *  - On receive: clock = max(local, received) + 1.
 */
public class LamportClock {
    private final AtomicLong clock = new AtomicLong(0);

    /** Increment and return the new value (call before sending). */
    public long tick() {
        return clock.incrementAndGet();
    }

    /**
     * Update on receive: clock = max(local, received) + 1.
     * Returns the updated clock value.
     */
    public long update(long received) {
        long updated = Math.max(clock.get(), received) + 1;
        clock.set(updated);
        return updated;
    }

    public long get() {
        return clock.get();
    }
}
