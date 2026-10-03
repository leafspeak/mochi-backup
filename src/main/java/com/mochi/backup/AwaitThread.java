package com.mochi.backup;

public class AwaitThread extends Thread {
    private final int delaySeconds;
    private final Runnable task;

    public AwaitThread(int delaySeconds, Runnable task) {
        this.delaySeconds = delaySeconds;
        this.task = task;
        setName("Mochi-Await");
        setDaemon(false);
    }

    @Override
    public void run() {
        MochiClient.LOGGER.info("Restore begins in {} seconds...", delaySeconds);
        try {
            Thread.sleep(delaySeconds * 1000L);
        } catch (InterruptedException e) {
            MochiClient.LOGGER.info("Restore cancelled.");
            return;
        }
        new Thread(task, "Mochi-Restore").start();
    }
}
