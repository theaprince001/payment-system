#!/bin/sh

# Start RabbitMQ first – nothing else runs yet
echo "Starting RabbitMQ..."
rabbitmq-server -detached

# Wait until RabbitMQ is fully operational
echo "Waiting for RabbitMQ to become ready..."
until rabbitmqctl status 2>/dev/null; do
  sleep 2
done

# Now safe to configure memory
rabbitmqctl set_vm_memory_high_watermark absolute 30MB
echo "RabbitMQ memory limited to 30MB"

# Start Java services one at a time with tiny gaps
echo "Starting auth-service..."
java -Xms24m -Xmx48m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=15.0 \
     -XX:MaxMetaspaceSize=32m -Xss256k -jar /app/auth-service.jar &
sleep 3

echo "Starting api-gateway..."
java -Xms24m -Xmx48m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=15.0 \
     -XX:MaxMetaspaceSize=32m -Xss256k -jar /app/api-gateway.jar &
sleep 3

echo "Starting payment-orchestrator..."
java -Xms24m -Xmx48m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=15.0 \
     -XX:MaxMetaspaceSize=32m -Xss256k -jar /app/payment-orchestrator.jar &
sleep 3

echo "Starting ledger-service..."
java -Xms24m -Xmx48m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=15.0 \
     -XX:MaxMetaspaceSize=32m -Xss256k -jar /app/ledger-service.jar &

wait