# AsteroidsFX

A modular Asteroids game built with **JavaFX**, the **Java Platform Module System (JPMS)**, and a plugin-based architecture. Gameplay systems (player, enemies, bullets, asteroids, collision) are loaded at runtime as independent modules. Score persistence is handled by a separate **Spring Boot** microservice.

---

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Tech Stack](#tech-stack)
4. [System Architecture](#system-architecture)
5. [Module Dependency Graph](#module-dependency-graph)
6. [Work Flow](#work-flow)
7. [Game State Machine](#game-state-machine)
8. [Sequence Diagrams](#sequence-diagrams)
9. [Project Structure](#project-structure)
10. [Getting Started](#getting-started)
11. [Controls](#controls)
12. [Scoring Rules](#scoring-rules)
13. [Testing](#testing)
14. [Documentation](#documentation)
15. [License](#license)

---

## Overview

AsteroidsFX is an educational remake of the classic arcade game that demonstrates component-oriented design on the JVM:

- **JPMS modules** with clear `provides` / `uses` service contracts
- **Runtime plugin loading** via a dedicated `ModuleLayer` and `ServiceLoader`
- **Spring** for composition in the Core module
- **HTTP-backed scoring** as an out-of-process microservice

Clear all asteroids across waves, dodge enemy ships, and survive with three lives. Reach **wave 3** for victory.

---

## Features

| Feature | Description |
|---|---|
| Classic Asteroids gameplay | Thrust, rotate, shoot; wrap around screen edges |
| Asteroid splitting | Large → medium → small fragments with increasing speed |
| Enemy ships | AI-controlled foes that spawn and fire at the player |
| Wave progression | Difficulty scales per wave (more asteroids, higher speed) |
| Lives & invulnerability | Three lives with ~2.5s respawn protection |
| Menus | Start menu, pause menu, help overlay, game over / victory |
| HUD | Live score, lives, wave number, asteroids remaining |
| Sound effects | Shooting, explosions, menu navigation (muteable) |
| Responsive window | Logical 800×800 playfield scales to the window size |
| External scoring API | Score synced to Spring Boot on every score change |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 11+ (JPMS) |
| UI | JavaFX 21 |
| Build | Maven (multi-module) |
| DI / composition | Spring Framework 6 |
| Scoring service | Spring Boot (standalone JAR on port **8081**) |
| Testing | JUnit 5, Mockito |
| Module discovery | `ServiceLoader` + child `ModuleLayer` |

---

## System Architecture

The system has three runtime layers: the **boot module path** (`mods-mvn/`), a **child plugin layer** (`plugins/`), and an optional **scoring microservice** process.

```mermaid
flowchart TB
    subgraph ProcessA["Game Process"]
        direction TB
        subgraph Boot["Boot Layer — module-path: mods-mvn/"]
            Main["Main<br/>JavaFX Application"]
            Spring["Spring ModuleConfig"]
            Game["Game<br/>loop · render · HUD"]
            ScoreClient["ScoreClient<br/>RestTemplate"]
            Common["Common<br/>GameData · World · Entity · SPIs"]
            CB["CommonBullet"]
            CA["CommonAsteroids"]
            Main --> Spring
            Spring --> Game
            Game --> ScoreClient
            Game --> Common
            Common --- CB
            Common --- CA
        end

        subgraph Plugins["Child ModuleLayer — plugins/"]
            Player["Player"]
            Enemy["Enemy"]
            Bullet["Bullet"]
            Asteroids["Asteroids"]
            Collision["Collision"]
        end

        SL["ServiceLocator"]
        Spring --> SL
        SL -->|"defineModulesWithOneLoader"| Plugins
        Player -.->|"uses BulletSPI"| Bullet
        Enemy -.->|"uses BulletSPI"| Bullet
        Collision -.->|"uses IAsteroidSplitter"| Asteroids
        Plugins -->|"implements services"| Common
    end

    subgraph ProcessB["Scoring Process"]
        Scoring["Spring Boot<br/>ScoreController<br/>:8081 /api/score"]
    end

    ScoreClient -->|"POST /api/score<br/>on score delta"| Scoring
```

### Design principles

| Principle | How it is applied |
|---|---|
| Composition root | `Core` owns the game loop; it never `requires` Player/Enemy/Bullet/Asteroids/Collision |
| Interface segregation | Shared contracts live in `Common`, `CommonBullet`, `CommonAsteroids` |
| Plugin isolation | Gameplay jars load into a **child `ModuleLayer`** so they can be swapped without relinking Core |
| Post-processing boundary | Collision (and other cross-entity effects) run only as `IPostEntityProcessingService` after all entity processors finish |
| Externalized scoring | Authoritative score sync is a separate HTTP service (lab microservice design) |

---

## Module Dependency Graph

Who **provides** vs **uses** each service (matches `module-info.java` and `docs/ARCHITECTURE.md`):

```mermaid
flowchart LR
    subgraph API["API / Data Modules"]
        Common
        CommonBullet
        CommonAsteroids
    end

    subgraph Plugins["Plugin Modules"]
        Player
        Enemy
        Bullet
        Asteroids
        Collision
    end

    Core -->|"uses"| IGP["IGamePluginService"]
    Core -->|"uses"| IEP["IEntityProcessingService"]
    Core -->|"uses"| IPEP["IPostEntityProcessingService"]

    Player -->|"provides"| IGP
    Player -->|"provides"| IEP
    Enemy -->|"provides"| IGP
    Enemy -->|"provides"| IEP
    Bullet -->|"provides"| IGP
    Bullet -->|"provides"| IEP
    Bullet -->|"provides"| BulletSPI
    Asteroids -->|"provides"| IGP
    Asteroids -->|"provides"| IEP
    Asteroids -->|"provides"| Splitter["IAsteroidSplitter"]
    Collision -->|"provides"| IPEP

    Player -->|"uses"| BulletSPI
    Enemy -->|"uses"| BulletSPI
    Collision -->|"uses"| Splitter

    Player --> Common
    Enemy --> Common
    Bullet --> Common
    Asteroids --> Common
    Collision --> Common
    Bullet --> CommonBullet
    Player --> CommonBullet
    Enemy --> CommonBullet
    Asteroids --> CommonAsteroids
    Collision --> CommonAsteroids
    CommonBullet --> Common
    CommonAsteroids --> Common
    Core --> Common
```

| Module | Provides | Requires (`uses`) |
|---|---|---|
| **Core** | Composition root (not a plugin) | `IGamePluginService`, `IEntityProcessingService`, `IPostEntityProcessingService` |
| **Player** | Plugin + processing | `BulletSPI` |
| **Enemy** | Plugin + processing | `BulletSPI` |
| **Bullet** | Plugin, processing, `BulletSPI` | — |
| **Asteroids** | Plugin, processing, `IAsteroidSplitter` | — |
| **Collision** | Post-entity processing | `IAsteroidSplitter` |
| **Scoring** | REST scoring API (separate process) | — |

---

## Work Flow

### A. Developer / build workflow

```mermaid
flowchart LR
    A["Clone repo"] --> B["mvn clean install"]
    B --> C["mods-mvn/ filled<br/>Core + Common*"]
    B --> D["plugins/ filled<br/>Player · Enemy · Bullet · Asteroids · Collision"]
    B --> E["mvn -pl Scoring package"]
    E --> F["java -jar Scoring/...jar<br/>:8081"]
    C --> G["mvn exec:exec"]
    D --> G
    F --> G
    G --> H["Game window opens"]
```

1. **`mvn clean install`** — compile, test, and copy jars into `mods-mvn/` and `plugins/`.
2. **Start Scoring** — `java -jar Scoring/target/Scoring-1.0.1-SNAPSHOT.jar` on port `8081`.
3. **`mvn exec:exec`** — launch `Core/dk.sdu.mmmi.cbse.main.Main` with `--module-path=mods-mvn`.

### B. Runtime bootstrap workflow

```mermaid
flowchart TD
    Start["Main.launch"] --> Ctx["AnnotationConfigApplicationContext<br/>ModuleConfig"]
    Ctx --> SL["ServiceLocator.INSTANCE<br/>ModuleFinder.of(plugins/)"]
    SL --> Layer["Resolve plugins → child ModuleLayer"]
    Layer --> Beans["Spring beans:<br/>List&lt;IGamePluginService&gt;<br/>List&lt;IEntityProcessingService&gt;<br/>List&lt;IPostEntityProcessingService&gt;<br/>ScoreClient · Game"]
    Beans --> GS["Game.start(Stage)"]
    GS --> Plugins["Each IGamePluginService.start()<br/>Player · Enemy · Bullet · Asteroids"]
    Plugins --> Scene["Build Scene · polygons · HUD"]
    Scene --> Loop["Game.render() → AnimationTimer"]
```

### C. Per-frame game loop workflow

When the state is **PLAYING** (not Start Menu / Paused):

```mermaid
flowchart TD
    Frame["AnimationTimer.handle"] --> Mute["Handle Mute key"]
    Mute --> State["handleStateTransitions"]
    State --> Update{"State == PLAYING<br/>or GAME_OVER / VICTORY?"}
    Update -->|yes| Proc["IEntityProcessingService.process<br/>Player · Enemy · Bullet · Asteroids"]
    Proc --> Post["IPostEntityProcessingService.process<br/>Collision"]
    Post --> WinLose["Check GameOver / Victory"]
    Update -->|menu / paused| Draw
    WinLose --> Draw["draw() · HUD · screen shake"]
    Draw --> Keys["keys.update()"]
    Keys --> Sync["pushScoreIfChanged()"]
```

**Processing contract**

| Phase | Interface | Responsibility |
|---|---|---|
| Entity processing | `IEntityProcessingService` | Move / control **own** entities only |
| Post processing | `IPostEntityProcessingService` | Cross-entity effects (collision, split, score) after positions are final |

---

## Game State Machine

```mermaid
stateDiagram-v2
    [*] --> START_MENU
    START_MENU --> PLAYING: Start (Space)
    PLAYING --> PAUSED: P
    PAUSED --> PLAYING: Resume / P
    PLAYING --> GAME_OVER: Lives == 0
    PLAYING --> VICTORY: Wave >= 3
    GAME_OVER --> PLAYING: R (Restart)
    VICTORY --> PLAYING: R (Restart)
    START_MENU --> [*]: Quit
    PAUSED --> [*]: Quit
```

Owned by `GameStateManager` in Common; HUD and input routing in Core read this state each frame.

---

## Sequence Diagrams

### 1. Application startup

```mermaid
sequenceDiagram
    autonumber
    participant Main
    participant Spring as Spring ModuleConfig
    participant SL as ServiceLocator
    participant Layer as plugins ModuleLayer
    participant Game
    participant Plugins as IGamePluginServices

    Main->>Spring: new AnnotationConfigApplicationContext(ModuleConfig)
    Spring->>SL: locateAll(IGamePluginService / IEntity* / IPost*)
    SL->>Layer: ModuleFinder.of("plugins") + defineModulesWithOneLoader
    Layer-->>SL: ModuleLayer
    SL-->>Spring: List of service implementations
    Spring->>Game: new Game(plugins, processors, postProcessors, scoreClient)
    Main->>Game: start(Stage)
    Game->>Plugins: start(gameData, world) for each plugin
    Plugins-->>Game: entities added to World
    Main->>Game: render() — AnimationTimer starts
```

### 2. Shoot bullet (Player → BulletSPI)

```mermaid
sequenceDiagram
    autonumber
    participant Game
    participant Player as PlayerControlSystem
    participant SL as ServiceLocator layer
    participant BulletSPI as BulletControlSystem
    participant World

    Game->>Player: process(gameData, world)
    Player->>Player: SPACE pressed & cooldown ready
    Player->>SL: ServiceLoader.load(layer, BulletSPI.class)
    SL-->>Player: BulletSPI provider
    Player->>BulletSPI: createBullet(player, gameData)
    BulletSPI-->>Player: new Bullet entity
    Player->>World: addEntity(bullet)
```

### 3. Collision, asteroid split, and local score

```mermaid
sequenceDiagram
    autonumber
    participant Game
    participant Collision as CollisionDetector
    participant Splitter as IAsteroidSplitter
    participant Score as ScoreState
    participant World

    Game->>Collision: process(gameData, world)
    Collision->>Collision: pair-scan valid category pairs
    alt Player bullet hits asteroid
        Collision->>Collision: damage both sides
        Collision->>Score: addPoints(20 / 50 / 100 by size)
        Collision->>Splitter: createSplitAsteroid(asteroid, world)
        Splitter->>World: add medium/small fragments (or none if SMALL)
        Collision->>World: removeEntity(destroyed)
    else Player hits asteroid / enemy / enemy bullet
        Collision->>Collision: PlayerState.registerHit()
        Note over Collision: Player module later consumes hit → loseLife / respawn
    end
```

### 4. Score sync to microservice

```mermaid
sequenceDiagram
    autonumber
    participant Game
    participant Client as ScoreClient
    participant API as Scoring :8081

    Game->>Game: pushScoreIfChanged()
    alt score unchanged
        Game-->>Game: return
    else score changed
        Game->>Client: push(currentScore)
        Client->>API: POST /api/score {"score": N}
        alt reachable
            API-->>Client: {"score": N}
            Client-->>Game: OK — lastPushedScore updated
        else RestClientException
            API--xClient: connection failure
            Game->>Game: log once, quiet retry every ~3s
        end
    end
```

### 5. Wave clear → next wave

```mermaid
sequenceDiagram
    autonumber
    participant Game
    participant Asteroids as AsteroidProcessor
    participant Wave as WaveState
    participant World

    Game->>Asteroids: process(gameData, world)
    Asteroids->>World: getEntities(Asteroid.class)
    alt no asteroids left
        Asteroids->>Wave: nextWave()
        Asteroids->>World: spawn LARGE asteroids<br/>(count = 1 + waveNumber - 1)
        Note over Wave: Wave >= 3 → Core sets VICTORY next frame
    else asteroids remain
        Asteroids->>Asteroids: move with wave speed multiplier
    end
```

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

## Getting Started

### Prerequisites

- **JDK 17+** recommended (JavaFX 21 / Spring 6)
- **Maven 3.8+** (or the included wrapper: `./mvnw` / `mvnw.cmd`)

### 1. Build

```bash
mvn clean install
```

Copies:

| Destination | Modules |
|---|---|
| `mods-mvn/` | Core, Common, CommonBullet, CommonAsteroids |
| `plugins/` | Player, Bullet, Enemy, Asteroids, Collision |

Both directories must exist before launch.

### 2. Start the Scoring microservice

```bash
mvn -pl Scoring clean package
java -jar Scoring/target/Scoring-1.0.1-SNAPSHOT.jar
```

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/score` | Current score |
| `POST` | `/api/score` | Set score (`{"score": N}`) |
| `POST` | `/api/score/reset` | Reset to 0 |

```bash
curl http://localhost:8081/api/score
```

If the service is down, the game still runs: sync fails quietly after one log and retries about every 3 seconds. See [`docs/MICROSERVICE.md`](docs/MICROSERVICE.md).

### 3. Run the game

```bash
mvn exec:exec
```

---

## Controls

| Key | Action |
|---|---|
| ← → | Rotate |
| ↑ | Thrust |
| ↓ / ↑ | Navigate menus |
| `Space` | Shoot / confirm menu |
| `P` | Pause |
| `M` | Mute / unmute |
| `H` | Toggle help (menus) |
| `R` | Restart (Game Over / Victory) |
| `Q` | Quit (menus) |

Menu options: **Start / Resume**, **Help**, **Quit**.

---

## Scoring Rules

| Target | Points |
|---|---|
| Large asteroid | 20 |
| Medium asteroid | 50 |
| Small asteroid | 100 |
| Enemy ship | 200 |

Local score lives in `ScoreState` (Common). Core’s `ScoreClient` mirrors it to the Scoring service whenever the value changes.

---

## Testing

```bash
mvn test
mvn -pl Collision,Player,Common,CommonAsteroids test
```

Coverage includes entity behavior, player state, asteroid sizes, collision detection, and player control logic (JUnit 5 + Mockito).

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Components, provides/requires, service contracts, known gaps |
| [`docs/MICROSERVICE.md`](docs/MICROSERVICE.md) | Scoring API, Core dependency, failure handling |
| [`docs/JPMS_LAB3_SPLIT_PACKAGE.md`](docs/JPMS_LAB3_SPLIT_PACKAGE.md) | Split-package demo and ModuleLayer isolation |

---

## License

Educational / coursework project (SDU MMMI CBSE). Adjust this section if you publish under a specific open-source license.
