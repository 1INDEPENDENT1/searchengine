package searchengine.services.impl.indexing;

import java.util.concurrent.atomic.AtomicBoolean;

public enum IndexingGate {
    INSTANCE;

    private final AtomicBoolean running = new AtomicBoolean();

    public boolean start() { return running.compareAndSet(false, true); }
    public void stop() { running.set(false); }
    public boolean isRunning() { return running.get(); }
}