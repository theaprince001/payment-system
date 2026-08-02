#!/bin/sh

JAVA_OPTS="-Xms32m -Xmx80m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=20.0"

echo "Starting auth-service..."
java $JAVA_OPTS -jar /app/auth-service.jar &
sleep 2

echo "Starting api-gateway..."
java $JAVA_OPTS -jar /app/api-gateway.jar &
sleep 2

echo "Starting payment-orchestrator..."
java $JAVA_OPTS -jar /app/payment-orchestrator.jar &
sleep 2

echo "Starting ledger-service..."
java $JAVA_OPTS -jar /app/ledger-service.jar &

wait