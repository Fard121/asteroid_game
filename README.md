# AsteroidsFX

A modular Asteroids game built with **JavaFX**, the **Java Platform Module System (JPMS)**, and a plugin-based architecture. Gameplay systems (player, enemies, bullets, asteroids, collision) are loaded at runtime as independent modules. Score persistence is handled by a separate **Spring Boot** microservice.

---

## Overview

AsteroidsFX is an educational and production-minded remake of the classic arcade game. It demonstrates component-oriented design on the JVM:

- **JPMS modules** with clear `provides` / `uses` service contracts
- **Runtime plugin loading** via a dedicated `ModuleLayer` and `ServiceLoader`
- **Spring** for composition in the Core module
- **HTTP-backed scoring** as an out-of-process microservice

Clear all asteroids across waves, dodge enemy ships, and survive with three lives. Reach wave 3 for victory.

---

## Features

| Feature | Description |
|---|---|
| Classic Asteroids gameplay | Thrust, rotate, shoot; wrap around screen edges |
| Asteroid splitting | Large → medium → small fragments with increasing speed |
| Enemy ships | AI-controlled foes that spawn and fire at the player |
| Wave progression | Difficulty scales per wave (more asteroids, higher speed) |
| Lives & invulnerability | Three lives with brief respawn protection |
| Menus | Start menu, pause menu, help overlay, game over / victory |
| HUD | Live score, lives, wave number, asteroids remaining |
| Sound effects | Shooting, explosions, menu navigation (muteable) |
| Responsive window | Logical 800×800 playfield scales to the window size |
| External scoring API | Score synced to a Spring Boot service on score changes |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 11+ (JPMS) |
| UI | JavaFX 21 |
| Build | Maven (multi-module) |
| DI / composition | Spring Framework 6 |
| Scoring service | Spring Boot (standalone JAR) |
| Testing | JUnit 5, Mockito |
| Module discovery | `ServiceLoader` + child `ModuleLayer` |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Core (composition root)                                    │
│  JavaFX game loop · Spring ModuleConfig · ScoreClient       │
│  module-path: mods-mvn/  (Common, CommonBullet, …)          │
└──────────────────────────┬──────────────────────────────────┘
                           │ ServiceLocator
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  plugins/  (child ModuleLayer)                              │
│  Player · Enemy · Bullet · Asteroids · Collision            │
└─────────────────────────────────────────────────────────────┘
                           │ HTTP (RestTemplate)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Scoring microservice  →  http://localhost:8081/api/score   │
└─────────────────────────────────────────────────────────────┘
```

**Core** never declares compile-time dependencies on Player, Enemy, Bullet, Asteroids, or Collision. Those jars are discovered from `plugins/` at startup and resolved into an isolated module layer, so plugins can be added or swapped without relinking Core.

Shared contracts live in API modules:

| Module | Role |
|---|---|
| **Common** | `Entity`, `World`, `GameData`, service interfaces (`IGamePluginService`, `IEntityProcessingService`, `IPostEntityProcessingService`) |
| **CommonBullet** | `BulletSPI`, `Bullet` marker type |
| **CommonAsteroids** | `IAsteroidSplitter`, `Asteroid`, `AsteroidSize` |

For a full component matrix and operation contracts, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Project Structure

```
AsteroidsFX-master/
├── Core/                 # JavaFX entry point, HUD, rendering, ScoreClient
├── Common/               # Shared data model & service interfaces
├── CommonBullet/         # Bullet SPI
├── CommonAsteroids/      # Asteroid types & splitter SPI
├── Player/               # Player plugin
├── Enemy/                # Enemy plugin
├── Bullet/               # Bullet plugin + BulletSPI provider
├── Asteroids/            # Asteroid spawn, movement, splitting
├── Collision/            # Post-processing collision & scoring hooks
├── Scoring/              # Standalone Spring Boot scoring microservice
├── docs/                 # Architecture, microservice, JPMS lab notes
├── mods-mvn/             # Built by Maven — Core + Common* on module path
└── plugins/              # Built by Maven — gameplay plugins for ModuleLayer
```

---

## Prerequisites

- **JDK 11 or newer** (JavaFX 21 and modern Spring require a current JDK; JDK 17+ recommended)
- **Maven 3.8+** (or use the included Maven Wrapper: `./mvnw` / `mvnw.cmd`)

---

## Getting Started

### 1. Build

From the repository root:

```bash
mvn clean install
```

This compiles every module, runs unit tests, and copies:

- Core / Common / CommonBullet / CommonAsteroids → `mods-mvn/`
- Player / Bullet / Enemy / Asteroids / Collision → `plugins/`

Both directories must exist before launching the game.

### 2. Start the Scoring microservice

The game syncs score over HTTP. Start the service **before** (or alongside) the game:

```bash
mvn -pl Scoring clean package
java -jar Scoring/target/Scoring-1.0.1-SNAPSHOT.jar
```

Listens on **http://localhost:8081**.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/score` | Current score |
| `POST` | `/api/score` | Set score (`{"score": N}`) |
| `POST` | `/api/score/reset` | Reset to 0 |

Quick check:

```bash
curl http://localhost:8081/api/score
```

If the service is down, the game still runs: score sync fails quietly after one log message and retries about every 3 seconds. Details: [`docs/MICROSERVICE.md`](docs/MICROSERVICE.md).

### 3. Run the game

```bash
mvn exec:exec
```

Or with the Maven Wrapper:

```bash
./mvnw exec:exec        # Unix / macOS / Git Bash
mvnw.cmd exec:exec      # Windows
```

---

## Controls

| Key | Action |
|---|---|
| ← → | Rotate |
| ↑ | Thrust |
| ↓ | Navigate menus |
| `Space` | Shoot |
| `P` | Pause / open pause menu |
| `M` | Mute / unmute sound |
| `H` | Toggle help (menus) |
| `R` | Restart (after Game Over or Victory) |
| `Q` | Quit (from menus) |

Use **↑ / ↓** and **Space** (or confirm) to navigate Start and Pause menus: Start / Resume, Help, Quit.

---

## Testing

Unit tests live under each module’s `src/test/java` (JUnit 5 + Mockito).

```bash
# Entire reactor
mvn test

# Single module
mvn -pl Collision,Player,Common,CommonAsteroids test
```

Covered areas include entity behavior, player state, asteroid sizes, collision detection, and player control logic.

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Components, provides/requires, service contracts, known gaps |
| [`docs/MICROSERVICE.md`](docs/MICROSERVICE.md) | Scoring service API, dependency on Core, failure handling |
| [`docs/JPMS_LAB3_SPLIT_PACKAGE.md`](docs/JPMS_LAB3_SPLIT_PACKAGE.md) | Split-package demo and how `ModuleLayer` isolation applies to plugins |

---

## Module Map

| Module | Provides | Requires |
|---|---|---|
| **Core** | Composition root (not a plugin) | `IGamePluginService`, `IEntityProcessingService`, `IPostEntityProcessingService` |
| **Player** | Plugin + processing | `BulletSPI` |
| **Enemy** | Plugin + processing | `BulletSPI` |
| **Bullet** | Plugin, processing, `BulletSPI` | — |
| **Asteroids** | Plugin, processing, `IAsteroidSplitter` | — |
| **Collision** | Post-entity processing | `IAsteroidSplitter` |
| **Scoring** | REST scoring API (separate process) | — |

---

## License

Educational / coursework project (SDU MMMI CBSE). Adjust this section if you publish under a specific open-source license.
