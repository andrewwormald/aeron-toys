package io.github.andrewwormald.aerontoys.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.aeron.cluster.client.AeronCluster;
import io.github.andrewwormald.aerontoys.gateway.client.ClusterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class GatewayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayService.class);

    private final HttpServer server;
    private final ObjectMapper objectMapper;
    private final ClusterClient clusterClient;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public GatewayService(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.objectMapper = new ObjectMapper();
        this.clusterClient = new ClusterClient();

        setupRoutes();
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        try {
            GatewayService gateway = new GatewayService(port);
            gateway.start();
        } catch (Exception e) {
            LOGGER.error("Failed to start gateway service", e);
            System.exit(1);
        }
    }

    private void setupRoutes() {
        server.createContext("/api/toys", new CreateToyHandler());
        server.createContext("/api/toys/", new GetToyHandler());
        server.createContext("/health", new HealthHandler());
    }

    public void start() {
        try {
            // Start HTTP server first
            server.start();
            LOGGER.info("Gateway HTTP server started on port {}", server.getAddress().getPort());

            // Connect to ToyFactory cluster (required for operation)
            clusterClient.connect();
            LOGGER.info("Successfully connected to ToyFactory cluster");

            // Setup shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutting down gateway...");
                stop();
            }));

            shutdownLatch.await();
        } catch (Exception e) {
            LOGGER.error("Error running gateway service", e);
        }
    }

    public void stop() {
        try {
            server.stop(0);
            clusterClient.close();
            shutdownLatch.countDown();
            LOGGER.info("Gateway service stopped");
        } catch (Exception e) {
            LOGGER.error("Error stopping gateway", e);
        }
    }

    private class CreateToyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                CreateToyRequest request = objectMapper.readValue(requestBody, CreateToyRequest.class);

                // Send create toy message to cluster via Aeron client
                String response = clusterClient.createToy(request.customerId);

                sendResponse(exchange, 201, response);
            } catch (Exception e) {
                LOGGER.error("Error creating toy", e);
                sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private class GetToyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                String path = exchange.getRequestURI().getPath();
                String toyId = path.substring(path.lastIndexOf('/') + 1);

                // Query toy status from cluster via Aeron client
                String response = clusterClient.getToy(Long.parseLong(toyId));

                sendResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.error("Error getting toy status", e);
                sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String healthStatus = String.format(
                "{\"status\":\"healthy\",\"cluster_connected\":%b}",
                clusterClient.isConnected()
            );
            sendResponse(exchange, 200, healthStatus);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    public static class CreateToyRequest {
        public Long customerId;
    }
}