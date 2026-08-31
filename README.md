[Deutsch](README.de.md)

[![CI](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml/badge.svg)](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml)

# FleetPulse

Predictive maintenance platform for commercial vehicle fleets. Simulated vehicles
stream sensor telemetry (engine temp, vibration, RPM, fault codes), a scoring
service flags the ones that need maintenance before they actually break down,
and an ops dashboard shows fleet health at a glance.

Building this to get hands-on with the stack I don't use day to day at work
(gRPC, Kafka, Spring microservices, Vaadin)

## Built so far

- `fleetpulse-proto`: shared gRPC contracts for telemetry and health scoring
- `vehicle-simulator`: C# console app, fakes a fleet of vehicle ECUs pushing telemetry over gRPC
- `fleet-service`: vehicle/driver registry, JWT auth
- `telemetry-service`: gRPC ingestion, persists to Postgres, publishes to Kafka
- `health-engine`: risk scoring, currently rule-based, consumes Kafka + exposes gRPC
- `alert-service`: turns risk events into notifications, with cooldown so it doesn't spam
- `ops-dashboard`: Vaadin UI showing vehicles, health scores, and alerts, now routed through the gateway
- `ml-training`: Python, trains a gradient-boosted risk model, exports to ONNX (not wired into health-engine yet)
- `gateway-service`: Spring Cloud Gateway, single entry point, validates JWTs centrally so fleet-service, health-engine, and alert-service don't each have to

## Still to do

- Load the trained ONNX model into `health-engine` behind a circuit breaker, with the rule engine as fallback
- `maintenance-service`: work orders, reconciles predictions against actual maintenance
- Rate limiting at the gateway (needs Redis, which health-engine's rolling-feature work will also want)
- Production Vaadin build for `ops-dashboard` so it can be containerized too
- ADMIN vs FLEET_MANAGER role distinction in `fleet-service` (both currently behave the same)

## Status

Every service above has real tests and has been run for real against actual
Postgres/Kafka, not just trusted from the code. CI runs the whole build on
every push.
