#!/bin/sh

rabbitmq-server -detached
sleep 10

JAVA_OPTS="-Xms32m -Xmx80m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=25.0"

java $JAVA_OPTS -jar /app/auth-service.jar &
java $JAVA_OPTS -jar /app/payment-orchestrator.jar &
java $JAVA_OPTS -jar /app/ledger-service.jar &
java $JAVA_OPTS -jar /app/api-gateway.jar &

wait