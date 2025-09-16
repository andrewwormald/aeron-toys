package io.github.andrewwormald.aerontoys.gateway.client;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

public class ClusterClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterClient.class);

    private AeronCluster cluster;
    private MediaDriver mediaDriver;
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    private final AtomicLong correlationIdGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cluster-poller");
        t.setDaemon(true);
        return t;
    });

    public void connect() {
        try {
            // Calculate correct ports based on cluster configuration
            // PORT_BASE = 20000, NODE_ID = 0, CLIENT_FACING_PORT_OFFSET = 2
            // Port = 20000 + (0 * 100) + 2 = 20002
            int ingressPort = 20002;

            LOGGER.info("Attempting to connect to cluster on port {}", ingressPort);

            // Launch embedded media driver for the client (following BasicSubscriber pattern)
            LOGGER.info("Launching embedded media driver for gateway client");
            mediaDriver = MediaDriver.launchEmbedded();

            // Create AeronCluster context with proper ingress and egress channels
            AeronCluster.Context ctx = new AeronCluster.Context()
                .egressListener(new ClusterEgressListener())
                .ingressChannel("aeron:udp?endpoint=localhost:" + ingressPort)
                .egressChannel("aeron:udp?endpoint=localhost:0") // Let Aeron choose egress port
                .ingressEndpoints("0=localhost:" + ingressPort)
                .messageTimeoutNs(5_000_000_000L) // 5 second timeout for faster failure
                .aeronDirectoryName(mediaDriver.aeronDirectoryName()); // Use embedded driver's directory

            LOGGER.info("Using Aeron directory: {}", mediaDriver.aeronDirectoryName());

            cluster = AeronCluster.connect(ctx);

            // Start polling the cluster to keep connection alive and receive messages
            startPolling();

            LOGGER.info("Successfully connected to ToyFactory cluster on port {}", ingressPort);
        } catch (Exception e) {
            LOGGER.error("Failed to connect to cluster on port 20002. Make sure ToyFactory cluster is running first.", e);

            // Clean up media driver if connection failed
            if (mediaDriver != null) {
                mediaDriver.close();
                mediaDriver = null;
            }

            // Throw exception to fail fast if cluster connection is required
            throw new RuntimeException("Cannot start gateway without cluster connection", e);
        }
    }

    public boolean isConnected() {
        return cluster != null && !cluster.isClosed();
    }

    public String createToy(Long customerId) {
        if (!isConnected()) {
            throw new RuntimeException("Cluster not connected. Cannot create toy for customer " + customerId);
        }

        try {
            long correlationId = correlationIdGenerator.getAndIncrement();
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingRequests.put(correlationId, future);

            String message = String.format("CREATE_TOY:%d", customerId != null ? customerId : 1L);
            buffer.putStringWithoutLengthAscii(0, message);

            long result = cluster.offer(buffer, 0, message.length());

            if (result < 0) {
                pendingRequests.remove(correlationId);
                return "{\"error\":\"Failed to send message to cluster\"}";
            }

            // Wait for response with timeout
            String response = future.get(5, TimeUnit.SECONDS);
            return response;

        } catch (Exception e) {
            LOGGER.error("Error creating toy", e);
            return "{\"error\":\"Request failed\"}";
        }
    }

    public String getToy(long toyId) {
        if (!isConnected()) {
            throw new RuntimeException("Cluster not connected. Cannot get toy " + toyId);
        }

        try {
            long correlationId = correlationIdGenerator.getAndIncrement();
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingRequests.put(correlationId, future);

            String message = String.format("GET_TOY:%d", toyId);
            buffer.putStringWithoutLengthAscii(0, message);

            long result = cluster.offer(buffer, 0, message.length());
            if (result < 0) {
                pendingRequests.remove(correlationId);
                return "{\"error\":\"Failed to send message to cluster\"}";
            }

            // Wait for response with timeout
            String response = future.get(5, TimeUnit.SECONDS);
            return response;

        } catch (Exception e) {
            LOGGER.error("Error getting toy", e);
            return "{\"error\":\"Request failed\"}";
        }
    }

    private void startPolling() {
        // Poll the cluster every 10ms to keep connection alive and receive messages
        pollingExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (cluster != null && !cluster.isClosed()) {
                    cluster.pollEgress();
                }
            } catch (Exception e) {
                LOGGER.warn("Error during cluster polling", e);
            }
        }, 10, 10, TimeUnit.MILLISECONDS);

        LOGGER.info("Started cluster polling");
    }

    public void close() {
        pollingExecutor.shutdown();

        if (cluster != null && !cluster.isClosed()) {
            cluster.close();
            LOGGER.info("Disconnected from cluster");
        }

        if (mediaDriver != null) {
            mediaDriver.close();
            mediaDriver = null;
            LOGGER.info("Closed embedded media driver");
        }
    }

    private class ClusterEgressListener implements EgressListener {
        @Override
        public void onMessage(
                long clusterSessionId,
                long timestamp,
                DirectBuffer buffer,
                int offset,
                int length,
                Header header) {

            String message = buffer.getStringWithoutLengthAscii(offset, length);

            // Process different message types
            if (message.startsWith("TOY_CREATED:")) {
                handleToyCreatedResponse(message);
            } else if (message.startsWith("TOY_INFO:")) {
                handleToyInfoResponse(message);
            } else if (message.startsWith("TOY_NOT_FOUND:")) {
                handleToyNotFoundResponse(message);
            }
        }

        private void handleToyCreatedResponse(String message) {
            try {
                // Parse: TOY_CREATED:toyId:customerId:status
                String[] parts = message.split(":");
                if (parts.length >= 4) {
                    String toyId = parts[1];
                    String customerId = parts[2];
                    String status = parts[3];

                    String jsonResponse = String.format(
                        "{\"id\":\"%s\",\"customerId\":\"%s\",\"status\":\"%s\"}",
                        toyId, customerId, status
                    );

                    // Complete the first pending request (simple approach)
                    completeNextPendingRequest(jsonResponse);
                }
            } catch (Exception e) {
                LOGGER.error("Error parsing TOY_CREATED response", e);
            }
        }

        private void handleToyInfoResponse(String message) {
            try {
                // Parse: TOY_INFO:toyId:customerId:status
                String[] parts = message.split(":");
                if (parts.length >= 4) {
                    String toyId = parts[1];
                    String customerId = parts[2];
                    String status = parts[3];

                    String jsonResponse = String.format(
                        "{\"id\":\"%s\",\"customerId\":\"%s\",\"status\":\"%s\"}",
                        toyId, customerId, status
                    );

                    completeNextPendingRequest(jsonResponse);
                }
            } catch (Exception e) {
                LOGGER.error("Error parsing TOY_INFO response", e);
            }
        }

        private void handleToyNotFoundResponse(String message) {
            try {
                // Parse: TOY_NOT_FOUND:toyId
                String[] parts = message.split(":");
                if (parts.length >= 2) {
                    String toyId = parts[1];
                    String jsonResponse = String.format(
                        "{\"error\":\"Toy not found\",\"toyId\":\"%s\"}",
                        toyId
                    );

                    completeNextPendingRequest(jsonResponse);
                }
            } catch (Exception e) {
                LOGGER.error("Error parsing TOY_NOT_FOUND response", e);
            }
        }

        private void completeNextPendingRequest(String response) {
            // Simple approach: complete the first pending request
            // In a real implementation, you'd match by correlation ID
            if (!pendingRequests.isEmpty()) {
                Long firstKey = pendingRequests.keys().nextElement();
                CompletableFuture<String> future = pendingRequests.remove(firstKey);
                if (future != null) {
                    future.complete(response);
                } else {
                    LOGGER.warn("Found null future for key: {}", firstKey);
                }
            } else {
                LOGGER.warn("Received response but no pending requests: {}", response);
            }
        }
    }
}