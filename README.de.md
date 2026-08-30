[English](README.md)

# FleetPulse

Plattform zur vorausschauenden Wartung von Nutzfahrzeugflotten. Simulierte
Fahrzeuge senden Sensordaten (Motortemperatur, Vibration, Drehzahl,
Fehlercodes), ein Scoring-Dienst markiert die Fahrzeuge, die gewartet werden
müssen, bevor es zu einem Ausfall kommt, und ein Dashboard zeigt den Zustand
der Flotte auf einen Blick.

Ich baue das Projekt, um praktische Erfahrung mit Technologien zu sammeln, die
ich im Arbeitsalltag nicht nutze (gRPC, Kafka, Spring-Microservices, Vaadin)

## Geplante Komponenten

- `vehicle-simulator`: simuliert eine Fahrzeugflotte, die Telemetriedaten per gRPC sendet
- `fleet-service`: Fahrzeug- und Fahrerregister, Authentifizierung
- `telemetry-service`: gRPC-Ingestion, Persistierung, Veröffentlichung auf Kafka
- `health-engine`: Risiko-Scoring (trainiertes Modell mit regelbasiertem Fallback)
- `alert-service`: wandelt Risikoereignisse in Wartungsalarme um
- `maintenance-service`: Arbeitsaufträge, gleicht Vorhersagen mit tatsächlicher Wartung ab
- `ops-dashboard`: Vaadin-Oberfläche für Flottenmanager
- `ml-training`: Python, trainiert das Scoring-Modell, Export nach ONNX

## Status

Bisher nur das Grundgerüst des Repos. Wird Service für Service aufgebaut,
dieses Readme wird laufend aktualisiert.
