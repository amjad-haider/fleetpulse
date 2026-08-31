# Contributing

This is mainly a personal project I'm using to get hands-on with a stack I
don't use day to day at work, not a large open-source effort with a team
behind it. That said, if you spot a bug, want to fix something on the "Still
to do" list in the [README](README.md), or just have a suggestion, pull
requests and issues are genuinely welcome.

## Getting set up

Follow the Quickstart in the [README](README.md#quickstart) to get the infra
and services running locally. There's nothing beyond that, no separate dev
environment or secrets to request.

## Before opening a PR

Run whatever part of the test suite touches your change:

```bash
mvn test                       # any Java service
dotnet test vehicle-simulator     # vehicle-simulator
cd ml-training && python -m pytest tests/   # ml-training
```

If your change touches Kafka or Postgres interaction in one of the Java
services, add or update the relevant Testcontainers integration test rather
than only a mocked unit test. That's the pattern the existing services
follow (see e.g. `HealthEngineIntegrationTest` or `AlertServiceIntegrationTest`)
and it's caught real bugs mocks wouldn't have.

CI runs the same checks automatically on the PR, so if it's green there
you're in good shape.

## Code style

Nothing enforced by a linter, just match what's already there:

- Services are organized by feature package (`ingest`, `scoring`, `security`,
  not by layer), each with its own `pom.xml` under its own top-level directory
- DTOs are plain records, response DTOs have a package-private static
  `from(...)` factory next to the entity they wrap rather than a separate
  mapper class
- Kafka event types are duplicated per consuming service on purpose (see the
  comment on `TelemetryReadingEvent` in `health-engine` for why) rather than
  shared through a common library, since services shouldn't need a
  compile-time dependency on each other just to read an event off a topic
- Keep comments to the "why", not the "what". The code should read fine
  without them for anything that isn't a genuinely non-obvious decision

## Scope

If you're picking something off the "Still to do" list in the README and it's
a big one (the ONNX/circuit-breaker work in `health-engine`, or a new
`maintenance-service`), it's worth opening an issue first to say what you're
planning before sinking time into it, mainly so two people don't end up
building the same thing at once.
