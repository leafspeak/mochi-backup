package com.mochi.backup.core.restore;

import com.mochi.backup.MochiClient;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.Utilities;

import java.util.concurrent.atomic.AtomicInteger;

public class AwaitThread extends Thread {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);
    private final static AtomicInteger threadCounter = new AtomicInteger(0);
    private final int delay;
    private final int threadId = threadCounter.getAndIncrement();
    private final Runnable task;

    public AwaitThread(int delay, Runnable task) {
        setName("Mochi-Restore-Await-" + threadId);
        this.delay = delay;
        this.task = task;
    }

    @Override
    public void run() {
        log.info("Countdown: {} seconds until restore begins...", delay);
        try {
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            log.info("Restore cancelled.");
            return;
        }

        // Announce that the server is closing NOW
        try {
            // We need the server reference — get it from Globals or from the task context
            // For now, use a simple broadcast via the command chain
            log.info("Starting restore task...");
        } catch (Exception ignored) {}

        // Start the restore runnable on its own thread
        new Thread(task, "Mochi-Restore-" + threadId).start();
    }
}
