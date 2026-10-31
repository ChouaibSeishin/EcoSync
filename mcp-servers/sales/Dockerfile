FROM eclipse-temurin:21.0.13_9-jdk-jammy@sha256:d378c0bb8ab3f2a09ce0a22a3876e7c64ef4fcfcefbd9b6d60638f5858ae8985
WORKDIR /app
COPY target/sales-mcp-0.3.0.jar app.jar
USER 1000:1000
EXPOSE 8091
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar"]
