.#!/bin/bash

echo "Building gRPC Common Library..."
cd grpc-common
./gradlew clean build publishToMavenLocal
cd ..

echo "Building Main Application..."
cd main-application
./gradlew clean build
cd ..

echo "Building Custom Axon Server..."
cd custom-axon-server
./gradlew clean build
cd ..

echo "All applications built successfully!"
