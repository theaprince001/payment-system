FROM eclipse-temurin:17-jre-alpine

COPY --from=build /workspace/auth-service/target/*.jar /app/auth-service.jar
COPY --from=build /workspace/api-gateway/target/*.jar /app/api-gateway.jar
COPY --from=build /workspace/payment-orchestrator/target/*.jar /app/payment-orchestrator.jar
COPY --from=build /workspace/ledger-service/target/*.jar /app/ledger-service.jar

COPY start-all.sh /app/start-all.sh
RUN chmod +x /app/start-all.sh

EXPOSE 8080
CMD ["/app/start-all.sh"]