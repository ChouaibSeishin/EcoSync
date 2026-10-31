FROM eclipse-temurin:21.0.13_9-jdk-jammy@sha256:d378c0bb8ab3f2a09ce0a22a3876e7c64ef4fcfcefbd9b6d60638f5858ae8985 AS java
FROM node:24.22.2-bookworm-slim@sha256:a43f7fc3e52e87d12a905f46eea0275def84ca8f2981a5b53fed44d7fb398ee0
COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"
WORKDIR /workspace
COPY typescript/package.json typescript/package-lock.json typescript/.npmrc ./verification/mcp-compatibility/typescript/
RUN npm ci --prefix verification/mcp-compatibility/typescript
COPY typescript/proof.mjs ./verification/mcp-compatibility/typescript/
COPY java/target/mcp-compatibility-java-1.0.0.jar ./verification/mcp-compatibility/java/target/
CMD ["node","verification/mcp-compatibility/typescript/proof.mjs"]
