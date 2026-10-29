// Source-free dependency probes; these are not backend/frontend application scaffolds.
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const versions = JSON.parse(await readFile(path.join(root, 'config/versions.json'), 'utf8'));
const base = path.join(root, '.verification/compatibility');
const dependencies = [
  ['org.springframework.boot', 'spring-boot-starter-webmvc'],
  ['org.springframework.boot', 'spring-boot-starter-security'],
  ['org.springframework.boot', 'spring-boot-starter-data-jpa'],
  ['org.springframework.boot', 'spring-boot-starter-validation'],
  ['org.springframework.boot', 'spring-boot-starter-actuator'],
  ['org.flywaydb', 'flyway-core'],
  ['org.flywaydb', 'flyway-database-postgresql'],
  ['org.postgresql', 'postgresql'],
  ['com.mysql', 'mysql-connector-j'],
  ['org.springframework.boot', 'spring-boot-starter-test', null, 'test'],
  ['org.testcontainers', 'testcontainers-postgresql', null, 'test'],
  ['org.testcontainers', 'testcontainers-junit-jupiter', null, 'test'],
  ['org.springdoc', 'springdoc-openapi-starter-webmvc-ui', versions.backend.springdoc],
  ['org.springframework.ai', 'spring-ai-starter-mcp-client'],
  ['org.springframework.ai', 'spring-ai-starter-mcp-server-webmvc'],
  ['org.springframework.ai', 'spring-ai-starter-model-ollama']
].map(([group, artifact, version, scope]) => `    <dependency><groupId>${group}</groupId><artifactId>${artifact}</artifactId>${version ? `<version>${version}</version>` : ''}${scope ? `<scope>${scope}</scope>` : ''}</dependency>`).join('\n');

const pom = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>${versions.backend.springBoot}</version><relativePath/></parent>
  <groupId>io.ecosync.verification</groupId><artifactId>phase-0-dependency-probe</artifactId><version>0.0.0</version>
  <properties><java.version>${versions.toolchain.javaRelease}</java.version></properties>
  <dependencyManagement><dependencies><dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-bom</artifactId><version>${versions.backend.springAi}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
  <dependencies>
${dependencies}
  </dependencies>
</project>
`;
const frontend = {
  name: 'ecosync-phase-0-dependency-probe', version: '0.0.0', private: true,
  description: 'Dependency resolution only; no application sources or scripts.',
  engines: { node: versions.toolchain.node, npm: versions.toolchain.npm },
  ...versions.frontend
};
await mkdir(path.join(base, 'backend'), { recursive: true });
await mkdir(path.join(base, 'frontend'), { recursive: true });
await writeFile(path.join(base, 'backend/pom.xml'), pom);
await writeFile(path.join(base, 'frontend/package.json'), JSON.stringify(frontend, null, 2) + '\n');
console.log('Generated source-free probes in .verification/compatibility (ignored by Git).');
