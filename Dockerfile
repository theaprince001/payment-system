# Stage 1: Build all services with Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common common
COPY auth-service auth-service
COPY user-service user-service
COPY payment-method-service payment-method-service
COPY payment-orchestrator payment-orchestrator
COPY ledger-service ledger-service
COPY notification-service notification-service
COPY api-gateway api-gateway
RUN mvn clean package -DskipTests -pl common,auth-service,user-service,payment-method-service,payment-orchestrator,ledger-service,notification-service,api-gateway

# Stage 2: Runtime image (no RabbitMQ)
FROM eclipse-temurin:17-jre-alpine

# Copy JARs from build stage
COPY --from=build /workspace/auth-service/target/*.jar /app/auth-service.jar
COPY --from=build /workspace/api-gateway/target/*.jar /app/api-gateway.jar
COPY --from=build /workspace/payment-orchestrator/target/*.jar /app/payment-orchestrator.jar
COPY --from=build /workspace/ledger-service/target/*.jar /app/ledger-service.jar

# Startup script (no RabbitMQ)
COPY start-all.sh /app/start-all.sh
RUN chmod +x /app/start-all.sh

EXPOSE 8080
CMD ["/app/start-all.sh"]