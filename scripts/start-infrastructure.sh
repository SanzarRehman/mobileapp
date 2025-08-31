#!/bin/bash

echo "Starting infrastructure services..."
docker-compose up -d postgres redis zookeeper kafka

echo "Waiting for services to be ready..."
sleep 30

echo "Checking service health..."
docker-compose ps

echo "Infrastructure services are ready!"
echo ""
echo "Services available at:"
echo "- PostgreSQL: localhost:5432"
echo "- Redis: localhost:6379"
echo "- Kafka: localhost:9092"
echo "- Kafka UI: http://localhost:8090"
echo ""
echo "To start monitoring services, run:"
echo "docker-compose up -d prometheus grafana"