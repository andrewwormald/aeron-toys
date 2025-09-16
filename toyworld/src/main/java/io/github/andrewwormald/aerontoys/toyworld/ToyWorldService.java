package io.github.andrewwormald.aerontoys.toyworld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.andrewwormald.aerontoys.toyworld.customer.CustomerService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToyWorldService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToyWorldService.class);

    private ExecutorService executorService;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        new ToyWorldService().run();
    }

    public void run() {
        LOGGER.info("Starting ToyWorld service...");

        try {
            startWorkers();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutting down ToyWorld...");
                shutdown();
            }));

            shutdownLatch.await();

        } catch (Exception e) {
            LOGGER.error("Failed to run ToyWorld", e);
        } finally {
            shutdown();
        }
    }

    private void startWorkers() {
        executorService = Executors.newFixedThreadPool(3);

        // Start Customer Service
        executorService.submit(new CustomerService());

        LOGGER.info("ToyWorld workers started");
    }

    private void shutdown() {
        try {
            if (executorService != null) {
                executorService.shutdown();
            }

            shutdownLatch.countDown();
            LOGGER.info("ToyWorld shutdown complete");

        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        }
    }
}