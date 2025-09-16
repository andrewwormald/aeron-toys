.PHONY: all build clean test run-toyfactory run-toyworld package install

# Project configuration
PROJECT_NAME := aeron-toys
GRADLEW := ./gradlew
JAVA_OPTS := -Xmx1G -XX:+UseG1GC --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.fs=ALL-UNNAMED

# Build targets
all: build

# Install dependencies and compile all modules
build:
	$(GRADLEW) build

# Package JAR files with dependencies
package:
	$(GRADLEW) jar

# Install to local repository
install:
	$(GRADLEW) publishToMavenLocal

# Run tests
test:
	$(GRADLEW) test

# Clean build artifacts
clean:
	$(GRADLEW) clean
	rm -rf aeron-cluster-*
	rm -rf logs/

# Create output directory for binaries
bin:
	mkdir -p bin

# Copy built JARs to bin directory
binaries: package bin
	cp toyfactory/build/libs/toyfactory.jar bin/
	cp toyworld/build/libs/toyworld.jar bin/

# Run toyfactory service
run-toyfactory: package
	@echo "Starting ToyFactory service..."
	java $(JAVA_OPTS) -jar toyfactory/build/libs/toyfactory.jar 0

# Run toyworld service
run-toyworld: package
	@echo "Starting ToyWorld service..."
	java $(JAVA_OPTS) -jar toyworld/build/libs/toyworld.jar

# Run both services (requires two terminals)
run-all:
	@echo "To run both services, execute in separate terminals:"
	@echo "  make run-toyfactory"
	@echo "  make run-toyworld"

# Development helpers
dev-toyfactory:
	$(GRADLEW) :toyfactory:run --args="0"

dev-toyworld:
	$(GRADLEW) :toyworld:run

# Show project information
info:
	@echo "=== Aeron Toys Project ==="
	@echo "Project: $(PROJECT_NAME)"
	@echo ""
	@echo "Available targets:"
	@echo "  build          - Compile all modules"
	@echo "  package        - Build JAR files"
	@echo "  test           - Run tests"
	@echo "  clean          - Clean build artifacts"
	@echo "  binaries       - Copy JARs to bin/ directory"
	@echo "  run-toyfactory - Start ToyFactory service"
	@echo "  run-toyworld   - Start ToyWorld service"
	@echo "  dev-toyfactory - Run ToyFactory in development mode"
	@echo "  dev-toyworld   - Run ToyWorld in development mode"
	@echo ""
	@echo "Modules:"
	@echo "  shared         - Common types and utilities"
	@echo "  toyfactory     - Aeron Cluster service for toy state management"
	@echo "  toyworld       - World services (customers, suppliers, workers)"
	@echo ""
	@echo "Package structure: io.github.andrewwormald.aerontoys.*"

# Default target
help: info