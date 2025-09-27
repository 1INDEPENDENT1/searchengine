package searchengine.services.impl.scraper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActiveTasks {
    final AtomicInteger count = new AtomicInteger();
    final Object monitor = new Object();

    public ActiveTasks() {
    }

    public void inc() { count.incrementAndGet(); }

    public void decAndSignal() {
        synchronized (monitor) {
            if (count.decrementAndGet() == 0) monitor.notifyAll();
        }
    }

    public void awaitZero(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (monitor) {
            while (count.get() > 0) {
                long rem = deadline - System.nanoTime();
                if (rem <= 0) break;
                TimeUnit.NANOSECONDS.timedWait(monitor, rem);
            }
            count.get();
        }
    }
}