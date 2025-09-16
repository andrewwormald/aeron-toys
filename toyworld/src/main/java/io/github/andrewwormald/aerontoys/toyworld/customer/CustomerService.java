package io.github.andrewwormald.aerontoys.toyworld.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class CustomerService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String gatewayUrl;
    private long customerIdCounter = 1000;

    public CustomerService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.objectMapper = new ObjectMapper();
        this.gatewayUrl = System.getProperty("gateway.url", "http://localhost:9090");
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Request a new toy every 5 seconds
                requestNewToy();
                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.info("Customer service interrupted");
        }
    }

    private void requestNewToy() {
        try {
            long customerId = customerIdCounter++;

            // Create toy request payload
            CreateToyRequest request = new CreateToyRequest(customerId);
            String requestBody = objectMapper.writeValueAsString(request);

            // Send POST request to gateway
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/api/toys"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                LOGGER.info("🎉 Customer {} successfully requested a new toy! Response: {}",
                    customerId, response.body());
            } else {
                LOGGER.warn("Customer {} toy request failed with status {}: {}",
                    customerId, response.statusCode(), response.body());
            }

        } catch (Exception e) {
            LOGGER.error("Failed to request toy from gateway", e);
        }
    }

    public static class CreateToyRequest {
        public Long customerId;

        public CreateToyRequest() {}

        public CreateToyRequest(Long customerId) {
            this.customerId = customerId;
        }
    }
}