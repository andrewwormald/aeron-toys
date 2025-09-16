package io.github.andrewwormald.aerontoys.toyworld.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupplierService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupplierService.class);

    @Override
    public void run() {
        LOGGER.info("Supplier service started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Simulate supplier operations
                Thread.sleep(7000);
                LOGGER.debug("Supplier service heartbeat");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.info("Supplier service interrupted");
        }
    }
}