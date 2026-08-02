#!/bin/sh

rabbitmq-server -detached

# Wait until RabbitMQ is fully ready (prevents the crash)
echo "Waiting for RabbitMQ..."
until rabbitmqctl status 2>/dev/null; do
  sleep 2
done

# Now it's safe to set the memory limit
rabbitmqctl set_vm_memory_high_watermark absolute 30MB

# Normal JVM settings (still safe for 4 services on 512 MB)
JAVA_OPTS="-Xms32m -Xmx80m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=20.0"

java $JAVA_OPTS -jar /app/auth-service.jar &
java $JAVA_OPTS -jar /app/api-gateway.jar &
java $JAVA_OPTS -jar /app/payment-orchestrator.jar &
java $JAVA_OPTS -jar /app/ledger-service.jar &

wait