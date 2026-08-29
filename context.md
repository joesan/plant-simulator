# Project Context: plant-simulator

Source: https://github.com/joesan/plant-simulator

## What this project is

A power plant simulator service ("Digital Twin" / "Virtual Power Plant command & control"). It
simulates decentralized command-and-control of electricity producing/consuming power plant units
that can be remotely steered (dispatched, ramped up/down, taken in/out of service) and monitored
in near real time. It's built as a reactive backend service, not a UI (a separate
`plant-simulator-ui` repo consumes its API/WebSocket).

## Tech stack

- **Language**: Scala 2.13.2
- **Web framework**: Play Framework (`PlayScala` sbt plugin)
- **Concurrency / reactive streams**: Akka (actors, 2.6.20) and Monix (3.4.1) for streams/observables
- **Persistence**: Slick 3.4.1 (functional-relational mapping) with HikariCP connection pooling
- **Databases supported**: MySQL and H2 (H2 used for dev/test, bundled `.mv.db` file included)
- **JSON**: play-json (+ play-json-joda for Joda-Time support)
- **Metrics**: Dropwizard Metrics (core + jvm)
- **Build tool**: SBT (`build.sbt`, `project/plugins.sbt`, `project/build.properties`)
- **Testing**: ScalaTest, scalatestplus-play, Akka TestKit, Awaitility, a websocket test client
  (`websocket-scala-client`)
- **Containerization**: sbt-native-packager Docker plugin; Dockerfile in `docker/`; also deployable
  to Kubernetes (see companion repo `plant-simulator-deployment`)
- **CI/CD**: GitHub Actions (`.github/workflows`), Codacy, Codecov, Scala-Steward for dependency
  updates, Mergify for auto-merge
- **License**: Apache-2.0

Compiler is strict: `-Xfatal-warnings` is on, plus WartRemover lint checks (most warts enabled,
with a documented allow-list of exceptions in `build.sbt`), and `scalafmt` runs automatically on
compile (`scalafmtOnCompile`). Any code change must stay warning-free and correctly formatted or
the build will fail.

## Directory layout

```
app/com/inland24/plantsim/
  config/       AppConfig, ConfigUtil                     -- typesafe-config based app configuration
  controllers/  ApplicationController, ControllerBase,
                PowerPlantController,
                PowerPlantOperationsController             -- Play HTTP controllers (REST endpoints)
  core/         AppBindings, AppMetrics, Bootstrap,
                EventsWebSocketActor, LoggingFilter,
                PowerPlantEventObservable, SupervisorActor  -- app wiring, DI bindings, top-level
                                                             actor supervision, websocket handling
  models/       PowerPlantConfig, PowerPlantState,
                PowerPlantType, PowerPlantSignal,
                PowerPlantCommand, DispatchCommand,
                ReturnToNormalCommand,
                PowerPlantActorMessage, PowerPlantDBEvent,
                PowerPlantFilter, package.scala             -- domain model (ADTs / case classes)
  services/
    database/   DBServiceActor, PowerPlantService,
      models/   AddressRow, PowerPlantRow                   -- Slick row representations
      repository/ DBSchema, PowerPlantRepository
        impl/   PowerPlantRepoAsTask                         -- Slick-backed repository, Monix Task-based
    simulator/  RandomValueGeneratorService
      onOffType/    OnOffTypeActor, StateMachine             -- simulates simple on/off plants
      rampUpType/   RampUpTypeActor, StateMachine            -- simulates plants that ramp power up/down
  streams/      DBObservable, EventsStream                   -- Monix observables bridging DB/events to
                                                               subscribers (e.g. websocket clients)

conf/
  application.conf / .dev.conf / .test.conf / .staging.conf / .loadtest.conf  -- per-env config
  routes                                                     -- Play route definitions
  logback-*.xml

database/       bundled H2 database files (plant-simulator.mv.db, .trace.db) for local/dev use
docker/         Dockerfile + helper scripts for building/pushing the Docker image
loadtest/       load testing setup + docs (LOADTEST.md) + its own H2 db file
test/com/inland24/plantsim/  mirrors the app/ package structure with ScalaTest specs
```

## Domain model / core concepts

- **PowerPlant**: a producing/consuming unit, persisted via `PowerPlantRepository` /
  `PowerPlantRepoAsTask` (Slick + H2/MySQL). Configured as one of a few `PowerPlantType`s, notably:
  - `OnOffTypeConfig` — a simple plant that is either on or off
  - `RampUpTypeConfig` — a plant that ramps its power output up/down toward a set point over time
- **PowerPlantState / PowerPlantSignal**: state machine states and telemetry/alert signals emitted
  as a plant operates (e.g. `Genesis`, `Transition`, `DefaultAlert`).
- **StateMachine** (one per plant type, e.g. `services/simulator/rampUpType/StateMachine.scala`):
  a pure, immutable case class encoding `cfg`, current `setPoint`, timestamps, `oldState`/`newState`,
  accumulated `events`, and `signals` — advanced functionally as commands/time pass.
- **Actors**: `OnOffTypeActor` and `RampUpTypeActor` wrap a `StateMachine` and drive it in response
  to `PowerPlantActorMessage`s (dispatch, release, out-of-service, return-to-service) and timer
  ticks, publishing state transitions as events.
- **SupervisorActor**: created at bootstrap (see `Bootstrap` / `AppBindings`); initializes streams,
  subscribes to DB change events, starts/watches/restarts the per-plant child actors.
- **DBServiceActor / DBObservable**: watches for `PowerPlantDBEvent`s (create/update/delete of
  power plant configs) and pushes them into the actor system so running simulations reflect DB
  changes.
- **EventsStream / EventsWebSocketActor**: expose live telemetry/events over a WebSocket to
  external clients (e.g. the UI).

## HTTP API (see `conf/routes`)

Base path prefix: `/plantsim`

- `GET  /plantsim/config` — application configuration
- `GET  /plantsim/app/metrics` — app metrics
- `GET  /plantsim/powerplant/:id/details` — get one power plant's details
- `GET  /plantsim/powerplants?onlyActive=&page=` — list power plants
- `GET  /plantsim/powerplants/search?...` — search power plants (by type, name, org, active)
- `POST /plantsim/powerplant/:id/update` — update a power plant
- `GET  /plantsim/powerplant/:id/telemetry` — telemetry for a plant
- `POST /plantsim/powerplant/:id/dispatch` — dispatch (steer) a plant to a set point
- `POST /plantsim/powerplant/:id/release` — return-to-normal command
- `POST /plantsim/powerplant/:id/outofservice` — take a plant out of service
- `POST /plantsim/powerplant/:id/returntoservice` — bring a plant back into service
- `GET  /plantsim/powerplant/events?id=` — event stream (alerts/state changes)
- `GET  /plantsim/powerplant/signals?id=` — telemetry signal stream
- `GET  /plantsim/powerplant/:id/killEventStream` — **test/demo only**, not for production use

Controllers: `PowerPlantController` (CRUD/read), `PowerPlantOperationsController` (steering,
events, signals), `ApplicationController` (config/metrics).

## Build, run, and test commands

```bash
# Run tests (uses in-memory H2 + application.test.conf)
sbt clean test

# Run locally against the bundled H2 db (application.dev.conf)
sbt -Denv=dev run

# Run against default application.conf
sbt run

# Run against qa / prod environments
sbt -Denv=qa run
sbt -Denv=prod -Dlogger.resource=logger-prod.xml run

# App serves on http://localhost:9000 once running

# Build & run as Docker container
sbt docker:publishLocal
docker run -i -p 9000:9000 joesan/plant-simulator
```

Prerequisites historically documented: Java 8, Scala 2.12.4 (README is stale here — `build.sbt`
actually pins Scala 2.13.2), IntelliJ with Scala plugin.

Database credentials for MySQL/test/prod environments are set in the corresponding
`conf/application.<env>.conf` file. H2 is used for local dev/test with no external DB needed.

## Things a coding agent should know before making changes

- Compilation is strict (`-Xfatal-warnings` + WartRemover); code must be warning-clean and
  scalafmt-formatted (auto-formatted on compile, so don't fight the formatter).
- Business logic in the simulator layer (`StateMachine`s) is written as pure, immutable
  transformations; actors (`OnOffTypeActor`, `RampUpTypeActor`) are thin wrappers that call into
  the state machine and manage Akka lifecycle/timers — prefer keeping new simulation logic pure
  and testable in the `StateMachine`, not in the actor.
  Corresponding specs: `RampUpTypeStateMachineTest`, `OnOffTypeStateMachineTest`,
  `RampUpTypeActorTest`, `OnOffTypeActorTest`.
- DB access goes through `PowerPlantRepository` (interface) / `PowerPlantRepoAsTask` (Slick +
  Monix `Task` implementation) — don't bypass it with ad hoc Slick queries elsewhere.
- Streaming/reactive code uses Monix `Observable`/`Task`, not plain Scala `Future`, in the core
  streaming paths (`DBObservable`, `EventsStream`, `PowerPlantEventObservable`) — follow that
  convention for consistency.
- Config is environment-layered via typesafe-config (`application.conf` + `application.<env>.conf`
  overrides), read through `AppConfig`/`ConfigUtil`. New config keys should follow this pattern.
- Test suite mirrors the `app/` package structure 1:1 under `test/`; add new specs alongside the
  code they cover, following existing naming (`XyzTest.scala` / `XyzSpec.scala`).
- The `killEventStream` endpoint is explicitly flagged in the routes file as demo-only and should
  not be treated as a real production feature to build on.
- A companion UI project (`plant-simulator-ui`) and deployment project
  (`plant-simulator-deployment`, Kubernetes manifests) exist as separate repos; this repo is
  backend/API only.
- The previously hosted Heroku demo is defunct (Heroku ended free hosting); don't assume a live
  public instance exists.

## Useful links

- Wiki (background, API docs, architecture ideas): https://github.com/joesan/plant-simulator/wiki
- API documentation: https://github.com/joesan/plant-simulator/wiki/API-Documentation
- Suggested future architecture notes: https://github.com/joesan/plant-simulator/wiki/Application-Architecture
- Load testing docs: `loadtest/LOADTEST.md`
- UI repo: https://github.com/joesan/plant-simulator-ui
- Deployment repo: https://github.com/joesan/plant-simulator-deployment
