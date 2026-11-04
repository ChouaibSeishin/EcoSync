FROM eclipse-temurin:21.0.13_9-jdk-jammy@sha256:d378c0bb8ab3f2a09ce0a22a3876e7c64ef4fcfcefbd9b6d60638f5858ae8985
WORKDIR /app
COPY target/managed-database-gateway-0.4.0.jar app.jar
USER 10001:10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar"]
