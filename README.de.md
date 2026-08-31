[English](README.md)

[![CI](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml/badge.svg)](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml)

# FleetPulse

Plattform zur vorausschauenden Wartung von Nutzfahrzeugflotten. Simulierte
Fahrzeuge senden Sensordaten (Motortemperatur, Vibration, Drehzahl,
Fehlercodes), ein Scoring-Dienst markiert die Fahrzeuge, die gewartet werden
müssen, bevor es zu einem Ausfall kommt, und ein Dashboard zeigt den Zustand
der Flotte auf einen Blick.

Ich baue das Projekt, um praktische Erfahrung mit Technologien zu sammeln, die
ich im Arbeitsalltag nicht nutze (gRPC, Kafka, Spring-Microservices, Vaadin)

## Bisher gebaut

- `fleetpulse-proto`: gemeinsame gRPC-Contracts für Telemetrie und Health-Scoring
- `vehicle-simulator`: C#-Konsolenanwendung, simuliert eine Fahrzeugflotte, die Telemetriedaten per gRPC sendet
- `fleet-service`: Fahrzeug- und Fahrerregister, JWT-Authentifizierung, ADMIN/FLEET_MANAGER-Rollen mit echtem Unterschied (die erste registrierte Person wird ADMIN)
- `telemetry-service`: gRPC-Ingestion, Persistierung in Postgres, Veröffentlichung auf Kafka
- `health-engine`: Risiko-Scoring, aktuell regelbasiert, konsumiert Kafka und stellt gRPC bereit
- `alert-service`: wandelt Risikoereignisse in Benachrichtigungen um, mit Cooldown gegen Spam
- `ops-dashboard`: Vaadin-Oberfläche mit Fahrzeugen, Health-Scores und Alerts, läuft jetzt über das Gateway
- `ml-training`: Python, trainiert ein gradient-boosted Risikomodell, Export nach ONNX (noch nicht an health-engine angebunden)
- `gateway-service`: Spring Cloud Gateway, einheitlicher Einstiegspunkt, validiert JWTs zentral, damit fleet-service, health-engine und alert-service das nicht jeweils selbst tun müssen

## Noch offen

- trainiertes ONNX-Modell in `health-engine` laden, abgesichert durch einen Circuit Breaker mit der Regel-Engine als Fallback
- `maintenance-service`: Arbeitsaufträge, gleicht Vorhersagen mit tatsächlicher Wartung ab
- Rate Limiting im Gateway (braucht Redis, das auch für die geplanten Rolling-Features in health-engine sinnvoll wäre)
- Vaadin-Produktionsbuild für `ops-dashboard`, damit es ebenfalls containerisiert werden kann

## Status

Jeder Service oben hat echte Tests und wurde tatsächlich gegen echtes
Postgres/Kafka ausgeführt, nicht nur anhand des Codes vertraut. CI baut das
gesamte Projekt bei jedem Push.
