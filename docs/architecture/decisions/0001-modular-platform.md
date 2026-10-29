# ADR 0001: Modular monolith with independent source systems

Date: 2026-10-29. Status: accepted for implementation; not implemented.

## Context

The platform needs multiple integration types, authorization, mapping and evaluation without turning a portfolio/research project into a distributed platform management exercise.

## Decision

Use one Spring MVC/JPA platform backend and one React frontend. Maintain explicit module interfaces and dependency directions documented in [architecture](../ARCHITECTURE.md). Use JDBC within read-only external connectors. Keep platform metadata PostgreSQL separate from independent sales PostgreSQL, inventory MySQL and supplier REST systems. Native MCP servers are independent deployments. Initially embed the managed gateway module in the platform while exercising an actual internal MCP transport boundary.

## Alternatives and consequences

One microservice per conceptual package adds network, consistency and operational overhead without evidence of independent scaling needs. Hardcoded connector logic in chat would undermine extensibility. The monolith shares a process failure boundary; bounded pools and lifecycle cleanup are necessary, and extracting the gateway later may improve isolation. No broker, vector database, service mesh or empty Maven submodules are required now.
