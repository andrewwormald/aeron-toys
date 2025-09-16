package io.aeron.toys.toyfactory;

import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import io.aeron.toys.shared.Toy;
import io.aeron.toys.shared.ToyStatus;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ToyFactoryService implements ClusteredService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToyFactoryService.class);

    private final Map<Long, Toy> toys = new ConcurrentHashMap<>();
    private final AtomicLong toyIdGenerator = new AtomicLong(1);
    private Cluster cluster;

    @Override
    public void onStart(Cluster cluster, Cluster.Role role) {
        this.cluster = cluster;
        LOGGER.info("ToyFactory service started with role: {}", role);
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
        cluster.offer(response);
        LOGGER.info("Created toy: {}", toy);
    }

    private void updateToyStatus(ClientSession session, long toyId, ToyStatus newStatus) {
        Toy toy = toys.get(toyId);
        if (toy != null) {
            toy.setStatus(newStatus);
            String response = String.format("TOY_UPDATED:%d:%s", toyId, newStatus);
            cluster.offer(response);
            LOGGER.info("Updated toy {} to status {}", toyId, newStatus);
        } else {
            String response = String.format("TOY_NOT_FOUND:%d", toyId);
            cluster.offer(response);
        }
    }

    private void getToy(ClientSession session, long toyId) {
        Toy toy = toys.get(toyId);
        if (toy != null) {
            String response = String.format("TOY_INFO:%d:%d:%s",
                toy.getId(), toy.getCustomerId(), toy.getStatus());
            cluster.offer(response);
        } else {
            String response = String.format("TOY_NOT_FOUND:%d", toyId);
            cluster.offer(response);
        }
    }

    @Override
    public void onTimerEvent(long correlationId, long timestamp) {
        // Handle timer events for workflow automation
    }

    @Override
    public void onTakeSnapshot(long snapshotPosition) {
        LOGGER.info("Taking snapshot at position: {}", snapshotPosition);
        // Implement state snapshot
    }

    @Override
    public void onLoadSnapshot(long snapshotPosition) {
        LOGGER.info("Loading snapshot at position: {}", snapshotPosition);
        // Implement state loading
    }

    @Override
    public void onSessionOpen(ClientSession session, long timestamp) {
        LOGGER.info("Session opened: {}", session.id());
    }

    @Override
    public void onSessionClose(ClientSession session, long timestamp, CloseReason closeReason) {
        LOGGER.info("Session closed: {} reason: {}", session.id(), closeReason);
    }

    @Override
    public void onRoleChange(Cluster.Role newRole) {
        LOGGER.info("Role changed to: {}", newRole);
    }

    @Override
    public void onTerminate(Cluster cluster) {
        LOGGER.info("ToyFactory service terminated");
    }
}