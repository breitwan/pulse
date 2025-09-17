package pulse.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WaitGroup based on CountDownLatch.
 *
 * @author Maxim Breitman
 * @see <a href="https://golang.org/pkg/sync/#WaitGroup">Go's WaitGroup documentation</a>
 */
public final class WaitGroup {
    private final AtomicInteger counter = new AtomicInteger(0);
    private final CountDownLatch latch = new CountDownLatch(1);

    public int add(int delta) {
        if (delta < 1) {
            throw new IllegalArgumentException("delta < 1");
        }
        return counter.addAndGet(delta);
    }

    public void done() {
        int count = counter.decrementAndGet();
        if (count == 0) {
            latch.countDown();
        } else if (count < 0) {
            throw new IllegalStateException("count < 0");
        }
    }

    public void await() {
        if (counter.get() < 1) return;
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("#await interrupted!", e);
        }
    }

    public boolean await(long deadline, TimeUnit unit) {
        if (counter.get() < 1) return true;
        try {
            return latch.await(deadline, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("#await interrupted!", e);
        }
    }
}
