FROM eclipse-temurin:21.0.13_9-jdk-jammy@sha256:d378c0bb8ab3f2a09ce0a22a3876e7c64ef4fcfcefbd9b6d60638f5858ae8985
RUN groupadd --gid 10001 ecosync && useradd --uid 10001 --gid 10001 --no-create-home ecosync
WORKDIR /opt/ecosync
COPY target/ecosync-platform-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
