# Distributed Checkout

[![CI](https://github.com/kivo2/workflow-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/kivo2/workflow-engine/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-messaging-231F20.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1.svg)](https://www.postgresql.org/)

A saga-based orchestration engine that coordinates an e-commerce checkout across independent services over Apache Kafka — built to stay correct under partial failures, retries, and message redelivery.

## Overview

A central orchestrator drives a checkout saga across three worker services — inventory, payment, and order. Each service owns its own database and communicates only through Kafka. There's no distributed transaction; consistency comes from asynchronous messaging with idempotent, crash-tolerant consumers.

## Highlights

- **Orchestrated saga** with compensation (automatic rollback on failure)
- **Transactional outbox** — state changes and published messages commit atomically
- **Idempotent consumers** and event deduplication over at-least-once delivery
- **Retries** with exponential backoff and a dead-letter queue
- **Per-service database isolation** — no shared schema, no cross-service reads
- End-to-end integration tests, with CI on every push

## Tech

`Java 21` · `Spring Boot 3.3` · `Apache Kafka` · `PostgreSQL` · `Docker Compose` · `Gradle`

## Running it

```bash
docker compose up -d          # Kafka + Postgres
./gradlew build               # build & test all modules
```

Then start the services — `:orchestrator` (`:8080`), `:inventory-worker`, `:payment-worker`, `:order-worker` — with `./gradlew :<module>:bootRun`. See [`checkout.http`](checkout.http) for example requests.