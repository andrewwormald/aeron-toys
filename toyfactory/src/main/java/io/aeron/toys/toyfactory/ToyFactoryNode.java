package io.aeron.toys.toyfactory;

import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ToyFactoryNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToyFactoryNode.class);

    private static final int SERVICE_ID = 1;
    private static final String BASE_DIR = "aeron-cluster";

    public static void main(String[] args) {
        final String nodeId = args.length > 0 ? args[0] : "0";
        final int nodeIndex = Integer.parseInt(nodeId);

        LOGGER.info("Starting ToyFactory node {} ...", nodeId);

        final String baseDirName = BASE_DIR + "-" + nodeId;
        final File baseDir = new File(baseDirName);

        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IllegalStateException("Failed to create base directory: " + baseDirName);
        }

        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
            .aeronDirectoryName(baseDirName + "/media")
            .threadingMode(MediaDriver.Context.THREADING_MODE_SHARED);

        final ConsensusModule.Context consensusModuleContext = new ConsensusModule.Context()
            .clusterDir(new File(baseDirName, "consensus-module"))
            .clusterMemberId(nodeIndex)
            .clusterMembers("0,localhost:20110,localhost:20220,localhost:20330,localhost:8010")
            .appointmentTimeoutNs(1_000_000_000L);

        final ClusteredServiceContainer.Context serviceContainerContext = new ClusteredServiceContainer.Context()
            .clusteredService(new ToyFactoryService())
            .clusterDir(new File(baseDirName, "service"))
            .serviceId(SERVICE_ID);

        try (ClusteredMediaDriver clusteredMediaDriver = ClusteredMediaDriver.launch(
                mediaDriverContext,
                null,
                consensusModuleContext)) {

            try (ClusteredServiceContainer container = ClusteredServiceContainer.launch(
                    serviceContainerContext)) {

                LOGGER.info("ToyFactory node {} started successfully", nodeId);

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    LOGGER.info("Shutting down ToyFactory node {} ...", nodeId);
                    container.close();
                    clusteredMediaDriver.close();
                }));

                // Keep the service running
                Thread.currentThread().join();

            } catch (InterruptedException e) {
                LOGGER.warn("ToyFactory node {} interrupted", nodeId);
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to start ToyFactory node " + nodeId, e);
            System.exit(1);
        }
    }
}