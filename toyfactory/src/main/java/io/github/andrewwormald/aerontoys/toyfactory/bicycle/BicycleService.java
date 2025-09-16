package io.github.andrewwormald.aerontoys.toyfactory.bicycle;

import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.logbuffer.Header;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import org.agrona.collections.Hashing;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.github.andrewwormald.aerontoys.shared.Toy;
import io.github.andrewwormald.aerontoys.shared.ToyStatus;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BicycleService - Logical service for managing toy manufacturing
 *
 * API Commands (send as messages to this service):
 * - CREATE_TOY:{customerId} -> Creates new toy, returns TOY_CREATED:{toyId}:{customerId}:{status}
 * - UPDATE_TOY:{toyId}:{newStatus} -> Updates toy status, returns TOY_UPDATED:{toyId}:{status}
 * - GET_TOY:{toyId} -> Retrieves toy info, returns TOY_INFO:{toyId}:{customerId}:{status}
 */
public class BicycleService implements ClusteredService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BicycleService.class);

    public static final int SERVICE_ID = 100;

    private final Map<Long, Toy> toys = new ConcurrentHashMap<>();
    private final AtomicLong toyIdGenerator = new AtomicLong(1);
    private Cluster cluster;
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();

    // Background processing components (like Go goroutines + context)
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "egress-consumer");
        t.setDaemon(true); // Allows JVM to exit even if thread is running
        return t;
    });
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private EgressMessageHandler egressHandler;

    @Override
    public void onStart(Cluster cluster, Image snapshotImage) {
        this.cluster = cluster;
        this.egressHandler = new EgressMessageHandler();

        // Start background egress consumer (like go func() with context)
        startBackgroundEgressConsumer();

        LOGGER.info("ToyFactory logical service started (serviceId: {})", SERVICE_ID);
    }

    @Override
    public void onSessionMessage(
            ClientSession session,
            long timestamp,
            DirectBuffer buffer,
            int offset,
            int length,
            Header header) {

        // Simple message parsing - in real implementation would use proper serialization
        String message = buffer.getStringWithoutLengthAscii(offset, length);
        LOGGER.info("Received message: {}", message);

        if (message.startsWith("CREATE_TOY:")) {
            long customerId = Long.parseLong(message.substring(11));
            createToy(session, customerId);
        } else if (message.startsWith("UPDATE_TOY:")) {
            String[] parts = message.split(":");
            long toyId = Long.parseLong(parts[1]);
            ToyStatus newStatus = ToyStatus.valueOf(parts[2]);
            updateToyStatus(session, toyId, newStatus);
        } else if (message.startsWith("GET_TOY:")) {
            long toyId = Long.parseLong(message.substring(8));
            getToy(session, toyId);
        }
    }

    private void createToy(ClientSession session, long customerId) {
        long toyId = toyIdGenerator.getAndIncrement();
        Toy toy = new Toy(toyId, customerId, ToyStatus.PENDING);
        toys.put(toyId, toy);

        String response = String.format("TOY_CREATED:%d:%d:%s", toyId, customerId, ToyStatus.PENDING);

        // Send response back to the client session (for gateway)
        sendResponseToSession(session, response);

        // Also send to cluster for background processing (for internal workflows)
        offerStringMessage(response);

        LOGGER.info("Created toy: {}", toy);
    }

    private void updateToyStatus(ClientSession session, long toyId, ToyStatus newStatus) {
        Toy toy = toys.get(toyId);
        if (toy != null) {
            toy.setStatus(newStatus);

            String response = String.format("TOY_UPDATED:%d:%s", toyId, newStatus);
            sendResponseToSession(session, response);
            offerStringMessage(response);

            LOGGER.info("Updated toy {} to status {}", toyId, newStatus);
        } else {
            String response = String.format("TOY_NOT_FOUND:%d", toyId);
            sendResponseToSession(session, response);
            offerStringMessage(response);
        }
    }

    private void getToy(ClientSession session, long toyId) {
        Toy toy = toys.get(toyId);
        if (toy != null) {
            String response = String.format("TOY_INFO:%d:%d:%s",
                toy.getId(), toy.getCustomerId(), toy.getStatus());
            sendResponseToSession(session, response);
            offerStringMessage(response);
        } else {
            String response = String.format("TOY_NOT_FOUND:%d", toyId);
            sendResponseToSession(session, response);
            offerStringMessage(response);
        }
    }

    private void sendResponseToSession(ClientSession session, String message) {
        buffer.putStringWithoutLengthAscii(0, message);
        long result = session.offer(buffer, 0, message.length());
        if (result > 0) {
            LOGGER.info("Sent response to client session: {}", message);
        } else {
            LOGGER.warn("Failed to send response to client session: {} (result: {})", message, result);
        }
    }

    private void offerStringMessage(String message) {
        buffer.putStringWithoutLengthAscii(0, message);
        cluster.offer(buffer, 0, message.length());
    }


    @Override
    public void onTimerEvent(long correlationId, long timestamp) {
        // Handle timer events for workflow automation
    }

    @Override
    public void onTakeSnapshot(ExclusivePublication snapshotPublication) {
        LOGGER.info("Taking snapshot with publication: {}", snapshotPublication);
        // Implement state snapshot
    }

    public void onLoadSnapshot(Image snapshotImage) {
        LOGGER.info("Loading snapshot from image: {}", snapshotImage);
        // Implement state loading
    }

    @Override
    public void onSessionOpen(ClientSession session, long timestamp) {}

    @Override
    public void onSessionClose(ClientSession session, long timestamp, CloseReason closeReason) {}

    @Override
    public void onRoleChange(Cluster.Role newRole) {
        LOGGER.info("Role changed to: {}", newRole);
    }

    @Override
    public void onTerminate(Cluster cluster) {
        LOGGER.info("ToyFactory logical service shutting down...");

        // Graceful shutdown (like Go context cancellation)
        stopBackgroundEgressConsumer();

        LOGGER.info("ToyFactory logical service terminated");
    }

    /**
     * Start background egress consumer thread (like go func() with context)
     */
    private void startBackgroundEgressConsumer() {
        isRunning.set(true);
        backgroundExecutor.submit(this::runEgressConsumerLoop);
        LOGGER.info("Started background egress consumer thread");
    }

    /**
     * Stop background consumer gracefully (like Go context cancellation)
     */
    private void stopBackgroundEgressConsumer() {
        LOGGER.info("Stopping background egress consumer...");

        // Signal shutdown (like ctx.Done() in Go)
        isRunning.set(false);

        // Wake up the consumer thread if it's waiting
        messageQueue.offer("SHUTDOWN_SIGNAL");

        try {
            // Wait for graceful shutdown with timeout (like Go context timeout)
            if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Background consumer didn't shutdown gracefully, forcing shutdown");
                backgroundExecutor.shutdownNow();
            } else {
                LOGGER.info("Background egress consumer shutdown gracefully");
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for shutdown");
            backgroundExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Main consumer loop - runs in background thread (like goroutine)
     * Similar to: go func(ctx context.Context) { ... }()
     */
    private void runEgressConsumerLoop() {
        LOGGER.info("Egress consumer thread started");

        while (isRunning.get()) {
            try {
                // Wait for messages with timeout (like select with ctx.Done())
                String message = messageQueue.poll(1, TimeUnit.SECONDS);

                if (message != null && !message.equals("SHUTDOWN_SIGNAL")) {
                    // Process the message
                    egressHandler.handleEgressMessage(message);
                }

                // Check if we should continue (like checking ctx.Done())
                if (!isRunning.get()) {
                    break;
                }

            } catch (InterruptedException e) {
                LOGGER.info("Egress consumer interrupted, shutting down");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error in egress consumer loop", e);
                // Continue processing other messages
            }
        }

        LOGGER.info("Egress consumer thread exited");
    }

    /**
     * Queue message for background processing (non-blocking)
     * This is what gets called when egress messages arrive
     */
    public void onEgressMessage(String message) {
        if (isRunning.get()) {
            if (!messageQueue.offer(message)) {
                LOGGER.warn("Message queue full, dropping egress message: {}", message);
            }
        }
    }

    /**
     * Inner class to handle egress messages from the cluster
     */
    private class EgressMessageHandler {

        public void handleEgressMessage(String message) {
            LOGGER.debug("Processing egress message: {}", message);

            if (message.startsWith("TOY_CREATED:")) {
                handleToyCreatedEvent(message);
            } else if (message.startsWith("TOY_UPDATED:")) {
                handleToyUpdatedEvent(message);
            }
        }

        private void handleToyCreatedEvent(String message) {
            // Parse: TOY_CREATED:toyId:customerId:status
            String[] parts = message.split(":");
            if (parts.length >= 4) {
                long toyId = Long.parseLong(parts[1]);
                long customerId = Long.parseLong(parts[2]);
                String status = parts[3];

                LOGGER.info("🎉 TOY_CREATED event: toyId={}, customerId={}, status={}",
                    toyId, customerId, status);

                // Add your business logic here:
                // - Send notifications
                // - Update external systems
                // - Trigger workflow steps
                // - etc.

                processNewToyWorkflow(toyId, customerId);
            }
        }

        private void handleToyUpdatedEvent(String message) {
            // Parse: TOY_UPDATED:toyId:status
            String[] parts = message.split(":");
            if (parts.length >= 3) {
                long toyId = Long.parseLong(parts[1]);
                String status = parts[2];

                LOGGER.info("🔄 TOY_UPDATED event: toyId={}, status={}", toyId, status);

                // Add your business logic here for status updates
                processStatusChangeWorkflow(toyId, status);
            }
        }

        private void processNewToyWorkflow(long toyId, long customerId) {
            // Example workflow logic when a new toy is created
            LOGGER.info("Starting manufacturing workflow for toy {} (customer {})", toyId, customerId);

            // You could:
            // - Schedule production steps
            // - Allocate resources
            // - Send to manufacturing queue
            // - Notify customer
        }

        private void processStatusChangeWorkflow(long toyId, String status) {
            // Example workflow logic when toy status changes
            LOGGER.info("Processing status change workflow for toy {} -> {}", toyId, status);

            // You could:
            // - Update progress tracking
            // - Send customer notifications
            // - Trigger next manufacturing step
            // - Update delivery estimates
        }
    }

}