#!/bin/sh
rabbitmq-server -detached
sleep 10

java -jar /app/auth-service.jar &
java -jar /app/user-service.jar &
java -jar /app/payment-method-service.jar &
java -jar /app/payment-orchestrator.jar &
java -jar /app/ledger-service.jar &
java -jar /app/notification-service.jar &
java -jar /app/api-gateway.jar &

wait