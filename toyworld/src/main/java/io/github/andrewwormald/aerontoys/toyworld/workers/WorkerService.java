package io.github.andrewwormald.aerontoys.toyworld.workers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerService.class);

    @Override
    public void run() {
        LOGGER.info("Worker service started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Simulate worker operations
                Thread.sleep(3000);
                LOGGER.debug("Worker service heartbeat");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.info("Worker service interrupted");
        }
    }
}