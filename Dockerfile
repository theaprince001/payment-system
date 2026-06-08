FROM eclipse-temurin:17-jre-alpine

# Install RabbitMQ
RUN apk add --no-cache rabbitmq-server

# Copy all JARs
COPY auth-service/target/*.jar /app/auth-service.jar
COPY user-service/target/*.jar /app/user-service.jar
COPY payment-method-service/target/*.jar /app/payment-method-service.jar
COPY payment-orchestrator/target/*.jar /app/payment-orchestrator.jar
COPY ledger-service/target/*.jar /app/ledger-service.jar
COPY notification-service/target/*.jar /app/notification-service.jar
COPY api-gateway/target/*.jar /app/api-gateway.jar

# Copy startup script
COPY start-all.sh /app/start-all.sh
RUN chmod +x /app/start-all.sh

EXPOSE 8080

CMD ["/app/start-all.sh"]