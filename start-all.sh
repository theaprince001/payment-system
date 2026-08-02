#!/bin/sh

rabbitmq-server -detached
sleep 10

# Ultra‑tight JVM settings: 64 MB heap, capped Metaspace, thread stack reduced
JAVA_OPTS="-Xms32m -Xmx64m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=20.0 -XX:MaxMetaspaceSize=48m -XX:ReservedCodeCacheSize=32m -Xss256k"

java $JAVA_OPTS -jar /app/auth-service.jar &
java $JAVA_OPTS -jar /app/payment-orchestrator.jar &
java $JAVA_OPTS -jar /app/ledger-service.jar &
java $JAVA_OPTS -jar /app/api-gateway.jar &

wait