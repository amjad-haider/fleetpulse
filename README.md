[Deutsch](README.de.md)

[![CI](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml/badge.svg)](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml)

# FleetPulse

Predictive maintenance platform for commercial vehicle fleets. Simulated vehicles
stream sensor telemetry (engine temp, vibration, RPM, fault codes) over gRPC, a
scoring engine flags the ones that need maintenance before they actually break
down, and an ops dashboard shows fleet health at a glance.

Building this to get hands-on with a stack I don't use day to day at work
(gRPC, Kafka, Spring microservices, Vaadin) while staying close to a domain I
know well from my M.Sc. in commercial vehicle technology.

## Architecture

| Service | Responsibility | Port |
|---|---|---|
| `gateway-service` | single entry point, routes requests, validates JWTs centrally | 8080 |
| `fleet-service` | vehicle/driver registry, auth (JWT), ADMIN/FLEET_MANAGER roles | 8081 |
| `telemetry-service` | gRPC ingestion from vehicles, persists to Postgres, publishes to Kafka | 8082 |
| `alert-service` | consumes health alerts, notifications with a cooldown so it doesn't spam | 8084 |
| `health-engine` | risk scoring (rule-based today), consumes Kafka, exposes gRPC | 8085 (REST), 8083 (gRPC) |
| `ops-dashboard` | Vaadin UI: vehicles, health scores, alerts | 8090 |
| `vehicle-simulator` | C# console app, fakes a fleet of vehicle ECUs pushing telemetry | - |
| `ml-training` | Python, trains a gradient-boosted risk model, exports to ONNX | - |
| `fleetpulse-proto` | shared gRPC contracts for telemetry and health scoring | - |

Communication is gRPC for the latency-sensitive paths (simulator to
telemetry-service) and Kafka for everything event-driven (health alerts,
telemetry fan-out). Every service that needs a database gets its own,
database-per-service, no shared schema.

## Quickstart

Needs Docker, JDK 17, Maven, .NET 10 SDK, and Python 3.11/3.12.

Start the infra (Postgres with all four databases, Kafka in KRaft mode):

```bash
docker compose up -d
```

Build and install everything once (`fleet-service`, `alert-service`,
`gateway-service`, and `ops-dashboard` don't strictly need this since they
don't depend on `fleetpulse-proto`, but `telemetry-service` and
`health-engine` do, and running a single module's `spring-boot:run` directly
can't build its own dependencies alongside it, only Maven's normal lifecycle
goals like `install` handle that correctly across the whole reactor):

```bash
mvn install -DskipTests
```

Then run whichever services you want, each in its own terminal (each is a
normal Spring Boot app):

```bash
mvn -pl fleet-service spring-boot:run
mvn -pl telemetry-service spring-boot:run
mvn -pl health-engine spring-boot:run
mvn -pl alert-service spring-boot:run
mvn -pl gateway-service spring-boot:run
mvn -pl ops-dashboard spring-boot:run
```

Register a user through the gateway (the first person to register becomes
ADMIN) and everything else is reachable through it on port 8080:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"a-strong-password","fullName":"Your Name"}'
```

To see actual telemetry flowing, point the simulator at telemetry-service:

```bash
cd vehicle-simulator
VEHICLE_COUNT=5 dotnet run --project VehicleSimulator
```

Run the whole test suite (unit tests, plus integration tests that spin up
real Postgres/Kafka via Testcontainers):

```bash
mvn test                     # Java, all modules
dotnet test vehicle-simulator   # C#
```

`ml-training` has its own README with the Python environment setup (`onnxruntime`
needs a not-too-new Python, 3.11 or 3.12), since it's a separate toolchain
from everything else here.

## Built so far

- `fleetpulse-proto`: shared gRPC contracts for telemetry and health scoring
- `vehicle-simulator`: C# console app, fakes a fleet of vehicle ECUs pushing telemetry over gRPC
- `fleet-service`: vehicle/driver registry, JWT auth, ADMIN/FLEET_MANAGER roles that actually differ (the first person to register becomes ADMIN)
- `telemetry-service`: gRPC ingestion, persists to Postgres, publishes to Kafka
- `health-engine`: risk scoring against the trained ONNX model, with Redis-backed rolling features (30-day averages, recent fault counts) and a circuit breaker that falls back to the rule engine if the model call is failing
- `alert-service`: turns risk events into notifications, with cooldown so it doesn't spam
- `ops-dashboard`: Vaadin UI showing vehicles, health scores, and alerts, routed through the gateway
- `ml-training`: Python, trains a gradient-boosted risk model, exports to ONNX
- `gateway-service`: Spring Cloud Gateway, single entry point, validates JWTs centrally so fleet-service, health-engine, and alert-service don't each have to
- CI/CD: GitHub Actions builds and tests all three stacks on every push, and packages the five backend services into container images (no Dockerfile, Spring Boot's buildpacks support handles that)

## Still to do

- `maintenance-service`: work orders, reconciles predictions against actual maintenance
- Rate limiting at the gateway (Redis is already in the stack for health-engine's rolling features)
- Production Vaadin build for `ops-dashboard` so it can be containerized too

## Status

Every service above has real tests and has been run for real against actual
Postgres/Kafka, not just trusted from the code. CI runs the whole build on
every push.
