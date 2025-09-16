# Aeron Toys

An event-driven toy manufacturing system using Aeron Cluster for distributed state management.

<img src="factory.png" height="512" alt="Toy Factory"/>

## Overview

This project recreates a toy manufacturing workflow using Aeron Cluster for fault-tolerant state management. It consists of two main services:

- **toyfactory**: Manages toy creation state machine using Aeron Cluster
- **toyworld**: Provides world/supplier services for the manufacturing workflow

## Architecture

The system uses an event-driven design where each consumer handles one part of the state machine workflow:

1. **Pending** → **Sourced**: Raw materials acquisition
2. **Sourced** → **Assembled**: Toy assembly process
3. **Assembled** → **Completed**: Final packaging and completion

The **toyfactory** service uses Aeron Cluster to maintain consistent state across multiple nodes, while **toyworld** provides supporting services for customers, suppliers, and workers.

## Prerequisites

- Java 17 or later
- Maven 3.6+
- Make (for build convenience)

## Building

```bash
# Build all modules
make build

# Package JAR files
make package

# Run tests
make test

# See all available targets
make help
```

## Running

### Option 1: Using Make (Recommended)

In separate terminals:

```bash
# Terminal 1 - Start ToyFactory
make run-toyfactory

# Terminal 2 - Start ToyWorld
make run-toyworld
```

### Option 2: Using Maven directly

```bash
# Terminal 1 - ToyFactory
mvn compile exec:java -pl toyfactory -Dexec.mainClass="io.aeron.toys.toyfactory.ToyFactoryNode" -Dexec.args="0"

# Terminal 2 - ToyWorld
mvn compile exec:java -pl toyworld -Dexec.mainClass="io.aeron.toys.toyworld.ToyWorldApp"
```

### Option 3: Using JAR files

```bash
# Build JARs first
make package

# Run services
java -jar toyfactory/target/toyfactory.jar 0
java -jar toyworld/target/toyworld.jar
```

## Project Structure

```
aeron-toys/
├── shared/           # Common types and utilities
│   └── src/main/java/io/aeron/toys/shared/
│       ├── Toy.java         # Toy entity
│       └── ToyStatus.java   # Status enumeration
├── toyfactory/       # Aeron Cluster service
│   └── src/main/java/io/aeron/toys/toyfactory/
│       ├── ToyFactoryService.java  # Clustered service implementation
│       └── ToyFactoryNode.java     # Main application
├── toyworld/         # World services
│   └── src/main/java/io/aeron/toys/toyworld/
│       └── ToyWorldApp.java        # Main application with workers
├── Makefile          # Build automation
└── pom.xml          # Parent Maven configuration
```

## State Machine

Toys progress through the following states:

- **UNKNOWN**: Invalid state
- **PENDING**: Newly created toy awaiting processing
- **SOURCED**: Materials have been sourced from suppliers
- **ASSEMBLED**: Toy has been assembled by workers
- **COMPLETED**: Toy is finished and ready for delivery

## Services

### ToyFactory Service

The ToyFactory runs as an Aeron Cluster service providing:

- **Fault tolerance**: State is replicated across cluster nodes
- **Consistency**: All state changes go through Raft consensus
- **Event sourcing**: All state transitions are logged as events
- **State machine**: Handles toy lifecycle transitions

### ToyWorld Service

The ToyWorld service provides supporting infrastructure:

- **Customer Service**: Handles customer interactions and orders
- **Supplier Service**: Manages material sourcing and supplier communications
- **Worker Service**: Coordinates assembly line workers

## Configuration

The services use the following default ports:

- **Aeron Cluster**: 20110, 20220, 20330, 8010
- **ToyWorld Communication**: 40123

Logs are written to the `logs/` directory with daily rotation.

## Development

For development, use the dev targets for automatic recompilation:

```bash
# Development mode (auto-recompile)
make dev-toyfactory
make dev-toyworld
```

## Cluster Operation

The ToyFactory can run in cluster mode for high availability. To start a 3-node cluster:

```bash
# Node 0
java -jar toyfactory/target/toyfactory.jar 0

# Node 1 (in separate terminal)
java -jar toyfactory/target/toyfactory.jar 1

# Node 2 (in separate terminal)
java -jar toyfactory/target/toyfactory.jar 2
```

## API

The ToyFactory service accepts simple text messages:

- `CREATE_TOY:<customerId>` - Create a new toy for customer
- `UPDATE_TOY:<toyId>:<newStatus>` - Update toy status
- `GET_TOY:<toyId>` - Retrieve toy information

Example responses:
- `TOY_CREATED:<toyId>:<customerId>:<status>`
- `TOY_UPDATED:<toyId>:<status>`
- `TOY_INFO:<toyId>:<customerId>:<status>`
- `TOY_NOT_FOUND:<toyId>`