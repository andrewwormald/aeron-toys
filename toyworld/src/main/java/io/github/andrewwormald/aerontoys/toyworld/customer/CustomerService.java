package io.github.andrewwormald.aerontoys.toyworld.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomerService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    @Override
    public void run() {
        LOGGER.info("Customer service started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Simulate customer interactions
                Thread.sleep(5000);
                LOGGER.debug("Customer service heartbeat");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.info("Customer service interrupted");
        }
    }
}