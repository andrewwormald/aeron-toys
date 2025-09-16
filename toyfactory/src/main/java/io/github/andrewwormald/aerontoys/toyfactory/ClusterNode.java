package io.github.andrewwormald.aerontoys.toyfactory;

import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.github.andrewwormald.aerontoys.toyfactory.ToyFactoryService;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class ClusterNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterNode.class);

    private static final int PORT_BASE = 20000;

    private static ErrorHandler errorHandler(final String context) {
        return (Throwable throwable) -> {
            LOGGER.error("{}: {}", context, throwable.getMessage(), throwable);
        };
    }

    public static void main(String[] args) {
        final int nodeId = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        final List<String> hostnames = Arrays.asList("localhost");
        final List<String> internalHostnames = Arrays.asList("localhost");

        LOGGER.info("Starting toys cluster node {} ...", nodeId);

        final ClusterConfig clusterConfig = ClusterConfig.create(
            nodeId, hostnames, internalHostnames, PORT_BASE, new ToyFactoryService());

        clusterConfig.mediaDriverContext().errorHandler(errorHandler("Media Driver"));
        clusterConfig.archiveContext().errorHandler(errorHandler("Archive"));
        clusterConfig.aeronArchiveContext().errorHandler(errorHandler("Aeron Archive"));
        clusterConfig.consensusModuleContext().errorHandler(errorHandler("Consensus Module"));
        clusterConfig.clusteredServiceContext().errorHandler(errorHandler("Clustered Service"));

        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        try (ClusteredMediaDriver clusteredMediaDriver = ClusteredMediaDriver.launch(
                 clusterConfig.mediaDriverContext().terminationHook(barrier::signalAll),
                 clusterConfig.archiveContext(),
                 clusterConfig.consensusModuleContext().terminationHook(barrier::signalAll));
             ClusteredServiceContainer container = ClusteredServiceContainer.launch(
                 clusterConfig.clusteredServiceContext().terminationHook(barrier::signalAll))) {

            LOGGER.info("Toys cluster node {} started successfully with all factory services", nodeId);
            barrier.await();
            LOGGER.info("Shutting down toys cluster node {} ...", nodeId);
        } catch (Exception e) {
            LOGGER.error("Failed to start cluster node {}", nodeId, e);
            System.exit(1);
        }
    }
}