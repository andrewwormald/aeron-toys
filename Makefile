.PHONY: all build clean test run-toyfactory run-toyworld package install

# Project configuration
PROJECT_NAME := aeron-toys
MVN := mvn
JAVA_OPTS := -Xmx1G -XX:+UseG1GC

# Build targets
all: build

# Install dependencies and compile all modules
build:
	$(MVN) clean compile

# Package JAR files with dependencies
package:
	$(MVN) clean package

# Install to local repository
install:
	$(MVN) clean install

# Run tests
test:
	$(MVN) test

# Clean build artifacts
clean:
	$(MVN) clean
	rm -rf aeron-cluster-*
	rm -rf logs/

# Create output directory for binaries
bin:
	mkdir -p bin

# Copy built JARs to bin directory
binaries: package bin
	cp toyfactory/target/toyfactory.jar bin/
	cp toyworld/target/toyworld.jar bin/

# Run toyfactory service
run-toyfactory: package
	@echo "Starting ToyFactory service..."
	java $(JAVA_OPTS) -jar toyfactory/target/toyfactory.jar 0

# Run toyworld service
run-toyworld: package
	@echo "Starting ToyWorld service..."
	java $(JAVA_OPTS) -jar toyworld/target/toyworld.jar

# Run both services (requires two terminals)
run-all:
	@echo "To run both services, execute in separate terminals:"
	@echo "  make run-toyfactory"
	@echo "  make run-toyworld"

# Development helpers
dev-toyfactory:
	$(MVN) compile exec:java -pl toyfactory -Dexec.mainClass="io.aeron.toys.toyfactory.ToyFactoryNode" -Dexec.args="0"

dev-toyworld:
	$(MVN) compile exec:java -pl toyworld -Dexec.mainClass="io.aeron.toys.toyworld.ToyWorldApp"

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

# Default target
help: info