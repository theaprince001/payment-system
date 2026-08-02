#!/bin/sh

# Start RabbitMQ with minimal memory
rabbitmq-server -detached
sleep 10
rabbitmqctl set_vm_memory_high_watermark 0.05

# Each JVM gets 80 MB heap – total ~320 MB for four services
JAVA_OPTS="-Xms32m -Xmx80m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=25.0"

java $JAVA_OPTS -jar /app/auth-service.jar &
java $JAVA_OPTS -jar /app/payment-orchestrator.jar &
java $JAVA_OPTS -jar /app/ledger-service.jar &
java $JAVA_OPTS -jar /app/api-gateway.jar &

wait