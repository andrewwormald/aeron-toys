package io.aeron.toys.toyworld;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToyWorldApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToyWorldApp.class);

    private static final String CHANNEL = "aeron:udp?endpoint=localhost:40123";
    private static final int STREAM_ID = 1001;

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Publication publication;
    private Subscription subscription;
    private ExecutorService executorService;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        new ToyWorldApp().run();
    }

    public void run() {
        LOGGER.info("Starting ToyWorld application...");

        try {
            startServices();
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

    private void startServices() {
        // Start embedded media driver
        mediaDriver = MediaDriver.launchEmbedded();

        // Create Aeron context and client
        final Aeron.Context aeronContext = new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName());

        aeron = Aeron.connect(aeronContext);

        // Create publication and subscription
        publication = aeron.addPublication(CHANNEL, STREAM_ID);
        subscription = aeron.addSubscription(CHANNEL, STREAM_ID);

        LOGGER.info("ToyWorld services started");
    }

    private void startWorkers() {
        executorService = Executors.newFixedThreadPool(3);

        // Start Customer Service
        executorService.submit(new CustomerService());

        // Start Supplier Service
        executorService.submit(new SupplierService());

        // Start Worker Service
        executorService.submit(new WorkerService());

        LOGGER.info("ToyWorld workers started");
    }

    private void shutdown() {
        try {
            if (executorService != null) {
                executorService.shutdown();
            }

            if (publication != null) {
                publication.close();
            }

            if (subscription != null) {
                subscription.close();
            }

            if (aeron != null) {
                aeron.close();
            }

            if (mediaDriver != null) {
                mediaDriver.close();
            }

            shutdownLatch.countDown();
            LOGGER.info("ToyWorld shutdown complete");

        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        }
    }

    private static class CustomerService implements Runnable {
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

    private static class SupplierService implements Runnable {
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

    private static class WorkerService implements Runnable {
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
}