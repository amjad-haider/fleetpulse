[Deutsch](README.de.md)

# FleetPulse

Predictive maintenance platform for commercial vehicle fleets. Simulated vehicles
stream sensor telemetry (engine temp, vibration, RPM, fault codes), a scoring
service flags the ones that need maintenance before they actually break down,
and an ops dashboard shows fleet health at a glance.

Building this to get hands-on with the stack I don't use day to day at work
(gRPC, Kafka, Spring microservices, Vaadin) 
## Planned pieces

- `vehicle-simulator`: fakes a fleet of vehicle ECUs pushing telemetry over gRPC
- `fleet-service`: vehicle/driver registry, auth
- `telemetry-service`: gRPC ingestion, persistence, publishes to Kafka
- `health-engine`: risk scoring (trained model + rule-based fallback)
- `alert-service`: turns risk events into maintenance alerts
- `maintenance-service`: work orders, reconciles predictions against actual maintenance
- `ops-dashboard`: Vaadin UI for fleet managers
- `ml-training`: Python, trains the scoring model, exports to ONNX

## Status

Just the repo scaffold so far. Building it service by service, will update this
as things land.
