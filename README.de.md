[English](README.md)

[![CI](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml/badge.svg)](https://github.com/amjad-haider/fleetpulse/actions/workflows/ci.yml)

# FleetPulse

Plattform zur vorausschauenden Wartung von Nutzfahrzeugflotten. Simulierte
Fahrzeuge senden Sensordaten (Motortemperatur, Vibration, Drehzahl,
Fehlercodes) per gRPC, eine Scoring-Engine markiert die Fahrzeuge, die
gewartet werden müssen, bevor es zu einem Ausfall kommt, und ein Dashboard
zeigt den Zustand der Flotte auf einen Blick.

Ich baue das Projekt, um praktische Erfahrung mit einem Stack zu sammeln, den
ich im Arbeitsalltag nicht nutze (gRPC, Kafka, Spring-Microservices, Vaadin),
und bleibe dabei nah an einem Bereich, den ich aus meinem Masterstudium in
Nutzfahrzeugtechnik gut kenne.

## Architektur

| Service | Verantwortung | Port |
|---|---|---|
| `gateway-service` | einheitlicher Einstiegspunkt, Routing, validiert JWTs zentral | 8080 |
| `fleet-service` | Fahrzeug- und Fahrerregister, Auth (JWT), ADMIN/FLEET_MANAGER-Rollen | 8081 |
| `telemetry-service` | gRPC-Ingestion von Fahrzeugen, Persistierung in Postgres, Veröffentlichung auf Kafka | 8082 |
| `alert-service` | konsumiert Health-Alerts, Benachrichtigungen mit Cooldown gegen Spam | 8084 |
| `health-engine` | Risiko-Scoring (aktuell regelbasiert), konsumiert Kafka, stellt gRPC bereit | 8085 (REST), 8083 (gRPC) |
| `ops-dashboard` | Vaadin-Oberfläche: Fahrzeuge, Health-Scores, Alerts | 8090 |
| `vehicle-simulator` | C#-Konsolenanwendung, simuliert eine Fahrzeugflotte, die Telemetrie sendet | - |
| `ml-training` | Python, trainiert ein gradient-boosted Risikomodell, Export nach ONNX | - |
| `fleetpulse-proto` | gemeinsame gRPC-Contracts für Telemetrie und Health-Scoring | - |

Kommunikation läuft per gRPC für die latenzkritischen Pfade (Simulator zu
telemetry-service) und per Kafka für alles Event-getriebene (Health-Alerts,
Telemetrie-Verteilung). Jeder Service, der eine Datenbank braucht, bekommt
seine eigene, Database-per-Service, kein gemeinsames Schema.

## Schnellstart

Braucht Docker, JDK 17, Maven, .NET 10 SDK und Python 3.11/3.12.

Infrastruktur starten (Postgres mit allen vier Datenbanken, Kafka im KRaft-Modus):

```bash
docker compose up -d
```

Einmalig alles bauen und installieren (`fleet-service`, `alert-service`,
`gateway-service` und `ops-dashboard` brauchen das eigentlich nicht, da sie
nicht von `fleetpulse-proto` abhängen, aber `telemetry-service` und
`health-engine` schon, und ein einzelnes Modul per `spring-boot:run` direkt
zu starten kann dessen eigene Abhängigkeiten nicht mitbauen, das können nur
Mavens normale Lifecycle-Goals wie `install` über den ganzen Reactor hinweg):

```bash
mvn install -DskipTests
```

Dann die gewünschten Services starten, jeweils in einem eigenen Terminal
(jeder ist eine normale Spring-Boot-Anwendung):

```bash
mvn -pl fleet-service spring-boot:run
mvn -pl telemetry-service spring-boot:run
mvn -pl health-engine spring-boot:run
mvn -pl alert-service spring-boot:run
mvn -pl gateway-service spring-boot:run
mvn -pl ops-dashboard spring-boot:run
```

Einen Nutzer über das Gateway registrieren (die erste registrierte Person
wird ADMIN), alles andere ist über das Gateway auf Port 8080 erreichbar:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"a-strong-password","fullName":"Your Name"}'
```

Um echte Telemetrie fließen zu sehen, den Simulator gegen telemetry-service laufen lassen:

```bash
cd vehicle-simulator
VEHICLE_COUNT=5 dotnet run --project VehicleSimulator
```

Die gesamte Testsuite ausführen (Unit-Tests plus Integrationstests, die
echtes Postgres/Kafka über Testcontainers hochfahren):

```bash
mvn test                     # Java, alle Module
dotnet test vehicle-simulator   # C#
```

`ml-training` hat ein eigenes README mit dem Python-Setup (`onnxruntime`
braucht ein nicht zu neues Python, 3.11 oder 3.12), da es ein eigener
Toolchain-Zweig getrennt vom Rest hier ist.

## Bisher gebaut

- `fleetpulse-proto`: gemeinsame gRPC-Contracts für Telemetrie und Health-Scoring
- `vehicle-simulator`: C#-Konsolenanwendung, simuliert eine Fahrzeugflotte, die Telemetriedaten per gRPC sendet
- `fleet-service`: Fahrzeug- und Fahrerregister, JWT-Authentifizierung, ADMIN/FLEET_MANAGER-Rollen mit echtem Unterschied (die erste registrierte Person wird ADMIN)
- `telemetry-service`: gRPC-Ingestion, Persistierung in Postgres, Veröffentlichung auf Kafka
- `health-engine`: Risiko-Scoring über das trainierte ONNX-Modell, mit Redis-gestützten Rolling Features (30-Tage-Durchschnitte, aktuelle Fehlercode-Häufigkeit) und einem Circuit Breaker, der bei ausfallendem Modellaufruf auf die Regel-Engine zurückfällt
- `alert-service`: wandelt Risikoereignisse in Benachrichtigungen um, mit Cooldown gegen Spam
- `ops-dashboard`: Vaadin-Oberfläche mit Fahrzeugen, Health-Scores und Alerts, läuft über das Gateway
- `ml-training`: Python, trainiert ein gradient-boosted Risikomodell, Export nach ONNX
- `gateway-service`: Spring Cloud Gateway, einheitlicher Einstiegspunkt, validiert JWTs zentral, damit fleet-service, health-engine und alert-service das nicht jeweils selbst tun müssen, und limitiert jede Route über Redis (pro authentifiziertem Nutzer, sofern ein Token vorliegt, sonst pro IP)
- CI/CD: GitHub Actions baut und testet alle drei Stacks bei jedem Push und packt die fünf Backend-Services in Container-Images (kein Dockerfile, das übernimmt Spring Boots Buildpacks-Unterstützung)

## Noch offen

- `maintenance-service`: Arbeitsaufträge, gleicht Vorhersagen mit tatsächlicher Wartung ab
- Vaadin-Produktionsbuild für `ops-dashboard`, damit es ebenfalls containerisiert werden kann

## Status

Jeder Service oben hat echte Tests und wurde tatsächlich gegen echtes
Postgres/Kafka ausgeführt, nicht nur anhand des Codes vertraut. CI baut das
gesamte Projekt bei jedem Push.
