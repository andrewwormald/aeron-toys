# Aeron Toys

An event-driven toy manufacturing system using Aeron Cluster for distributed state management.

<img src="factory.png" height="%" alt="Toy Factory"/>

## Quick Start

Clone and run the complete system in 3 simple steps:

```bash
# 1. Clone and build
git clone <repository-url>
cd aeron-toys
./gradlew build

# 2. Run services in separate terminals
# Terminal 1 - ToyFactory (cluster)
./gradlew :toyfactory:run --args="0"

# Terminal 2 - Gateway (HTTP API)
./gradlew :gateway:run --args="9090"

# Terminal 3 - ToyWorld (customer simulation)
./gradlew :toyworld:run
```

The system will start automatically creating toys! Check the logs to see the toy manufacturing workflow in action.

## System Overview

**Architecture**: Event-driven toy manufacturing with Aeron Cluster for fault-tolerant state management.

**Services**:
- **ToyFactory**: Aeron Cluster service managing toy manufacturing state machine
- **Gateway**: HTTP API gateway for external clients
- **ToyWorld**: Customer simulation service making HTTP requests

**Flow**: Customer → HTTP → Gateway → Aeron Cluster → ToyFactory → Manufacturing workflow

## Prerequisites

- Java 17 or later
- Gradle 8.5+ (included wrapper)

## Build

```bash
./gradlew build
```

## Services

### ToyFactory (Port 20002)
Aeron Cluster service managing the toy manufacturing state machine:
- Creates toys with PENDING status
- Manages toy lifecycle transitions
- Provides fault-tolerant state replication

### Gateway (Port 9090)
HTTP API gateway providing REST endpoints:
```bash
# Create a toy
curl -X POST http://localhost:9090/api/toys \
  -H "Content-Type: application/json" \
  -d '{"customerId": 123}'

# Get toy status
curl http://localhost:9090/api/toys/1

# Health check
curl http://localhost:9090/health
```

### ToyWorld (Customer Simulation)
Automatically creates new toy orders every 5 seconds by sending HTTP requests to the Gateway.

## Project Structure

```
aeron-toys/
├── shared/           # Common types (Toy, ToyStatus)
├── toyfactory/       # Aeron Cluster service
├── gateway/          # HTTP API gateway
├── toyworld/         # Customer simulation
└── build.gradle      # Gradle build
```

## API Reference

**Gateway REST API:**
- `POST /api/toys` - Create toy (requires `{"customerId": <id>}`)
- `GET /api/toys/{id}` - Get toy status
- `GET /health` - Service health check

**Internal Cluster Protocol:**
- `CREATE_TOY:<customerId>` → `TOY_CREATED:<toyId>:<customerId>:<status>`
- `GET_TOY:<toyId>` → `TOY_INFO:<toyId>:<customerId>:<status>`

## Troubleshooting

**Gateway connection issues:**
```bash
# Check if services are running
curl http://localhost:9090/health

# Restart gateway if cluster_connected is false
# Stop: pkill -f gateway
# Start: ./gradlew :gateway:run --args="9090"
```

**Port conflicts:**
- ToyFactory: 20002 (cluster ingress)
- Gateway: 9090 (HTTP API)
- Change ports in run commands if needed