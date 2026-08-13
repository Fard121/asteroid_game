# AsteroidsFX

> **Forked from** [sweat-tek/AsteroidsFX](https://github.com/sweat-tek/AsteroidsFX) — the original repository.  
> **Author:** [Fard121](https://github.com/Fard121)

A modular Asteroids game built with **JavaFX**, the **Java Platform Module System (JPMS)**, and a plugin-based architecture. Gameplay systems (player, enemies, bullets, asteroids, collision) are loaded at runtime as independent modules. Score persistence is handled by a separate **Spring Boot** microservice.

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Controls](#controls)
4. [Scoring Rules](#scoring-rules)
5. [Testing](#testing)
6. [Lab Coverage](#lab-coverage)
7. [Documentation](#documentation)
8. [License](#license)

---

## Overview

AsteroidsFX is an educational remake of the classic arcade game that demonstrates component-oriented design on the JVM:

- **JPMS modules** with clear `provides` / `uses` service contracts
- **Runtime plugin loading** via a dedicated `ModuleLayer` and `ServiceLoader`
- **Spring** for composition in the Core module
- **HTTP-backed scoring** as an out-of-process microservice

Clear all asteroids across waves, dodge enemy ships, and survive with three lives. Reach **wave 3** for victory.

---

## Getting Started

### Prerequisites

| Requirement | Version | Check with |
|---|---|---|
| JDK | **17 or newer** (JavaFX 21 + Spring 6 need it) | `java -version` |
| Maven | 3.8+ — or just use the bundled wrapper | `mvn -v` |
| Git | any | `git --version` |

You do **not** need to install Maven or JavaFX separately: the repo ships a Maven wrapper (`mvnw` / `mvnw.cmd`) and JavaFX arrives as ordinary Maven dependencies.

> **Command style used below.** `mvn …` works if Maven is on your `PATH`. Otherwise substitute the wrapper: `./mvnw …` on macOS/Linux/Git Bash, `mvnw.cmd …` on Windows CMD/PowerShell.

### 0. Clone

```bash
git clone https://github.com/Fard121/asteroid_game.git
```

Run every command below **from the repository root** (the folder containing the top-level `pom.xml`). The build resolves `plugins/` relative to the working directory, so launching from a subfolder will find no plugins.

### 1. Build everything

```bash
mvn clean install
```

This compiles all 10 modules, runs the full test suite, and copies the jars into the two directories the runtime needs:

| Destination | Modules | Role at runtime |
|---|---|---|
| `mods-mvn/` | Core, Common, CommonBullet, CommonAsteroids | Boot module path (`--module-path`) |
| `plugins/` | Player, Bullet, Enemy, Asteroids, Collision | Discovered into a child `ModuleLayer` |

Both directories must exist and be populated before launch — that is what `install` (not `package`) produces.

### 2. Start the Scoring microservice

In a **separate terminal**, also from the repo root:

```bash
java -jar Scoring/target/Scoring-1.0.1-SNAPSHOT.jar
```

It starts on port **8081**. (Step 1 already built this jar; `mvn -pl Scoring clean package` rebuilds just it.)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/score` | Current score |
| `POST` | `/api/score` | Set score (`{"score": N}`) |
| `POST` | `/api/score/reset` | Reset to 0 |

Verify it is up:

```bash
curl http://localhost:8081/api/score
```

If the service is down the game **still runs** — the score sync logs one warning and then retries quietly about every 3 seconds. See [`docs/MICROSERVICE.md`](docs/MICROSERVICE.md).

### 3. Run the game

```bash
mvn exec:exec --non-recursive
```

`--non-recursive` matters: it runs the `exec` plugin **once, for the root project only**. Without it Maven repeats the launch for every child module.

Under the hood this is exactly:

```bash
java --module-path=mods-mvn --module=Core/dk.sdu.mmmi.cbse.main.Main
```

A window titled **ASTEROIDS** opens on the start menu. Pick **Start** with `Space`.

### 4. Verify the whole stack

While playing, shoot an asteroid, then in another terminal:

```bash
curl http://localhost:8081/api/score
```

The number returned should match the **Score** in the game's HUD — that round trip proves the plugin layer, the game loop and the microservice are all wired correctly.

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Failed to load plugins from the 'plugins' directory` | `plugins/` empty, or you launched from a subfolder | Run `mvn clean install` from the repo root, then launch from the root |
| The game launches repeatedly, once per module | `--non-recursive` was omitted | Use `mvn exec:exec --non-recursive` |
| `Scoring microservice unreachable` in the console | Step 2 not running | Start the Scoring jar; gameplay is unaffected either way |
| `UnsupportedClassVersionError` or JavaFX link errors | JDK older than 17 | Install JDK 17+ and point `JAVA_HOME` at it |
| Port 8081 already in use | Another process holds it | Change `server.port` in `Scoring/src/main/resources/application.properties` |

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
| `1` | Install / uninstall the **Player** component |
| `2` | Install / uninstall the **Enemy** component |
| `3` | Install / uninstall the **Weapon** (Bullet) component |

Menu options: **Start / Resume**, **Help**, **Quit**.

### Live component install / uninstall

Keys `1`, `2` and `3` toggle a whole component in and out of the running game via `ComponentRegistry` (Core). Uninstalling calls that plugin's `IGamePluginService.stop()` and drops its `IEntityProcessingService` from the per-frame loop; reinstalling calls `start()` again. Press `3` and the ships stop being able to fire — the weapon provider is simply gone, and nothing else breaks. It is the plugin architecture made visible without restarting the JVM.

---

## Scoring Rules

| Target | Points |
|---|---|
| Large asteroid | 20 |
| Medium asteroid | 50 |
| Small asteroid | 100 |
| Enemy ship | 200 |

Local score lives in `ScoreState` (Common). Core’s `ScoreClient` mirrors it to the Scoring service whenever the value changes.

Points are credited **only for kills the player shot**. Ramming an asteroid or enemy costs a life instead, and an enemy saucer wrecked by an asteroid scores nothing — it wasn't the player's doing.

---

## Testing

```bash
mvn test
```

Or just the modules that have tests:

```bash
mvn -pl Collision,Player,Common,CommonAsteroids test
```

**21 tests across 5 classes**, all green:

| Test class | Module | Tests | Covers |
|---|---|---|---|
| `CollisionDetectorTest` | Collision | 6 | Pythagorean distance, 3-4-5 triangle case, multi-hit destruction, ship-vs-asteroid, mocked `World` interactions |
| `PlayerStateTest` | Common | 5 | Lives, invulnerability window, hit consumption, reset |
| `EntityTest` | Common | 4 | Health defaults, damage flooring, `setMaxHealth` refill |
| `PlayerControlSystemTest` | Player | 3 | Rotation, thrust + friction, screen wrap |
| `AsteroidSizeTest` | CommonAsteroids | 3 | Split chain LARGE → MEDIUM → SMALL → none |

Both testing styles are represented: **state-based** (build a real `World`, assert the outcome) and **interaction-based** (Mockito mock of `World`, `verify` the calls).

---

## Lab Coverage

This project is coursework for nine labs (`instructionlabs/labs.pdf`). Where each one lives in the code:

| Lab | Asked for | Primary evidence |
|---|---|---|
| **IntroLab** | JavaFX game; Core, Bullet, Player; enemy that moves and shoots randomly | `Core/Main.java`, `Core/Game.java`, `Player/`, `Bullet/`, `Enemy/EnemyControlSystem.java` |
| **GameLab** | JavaDoc contracts on the 3 service interfaces; Player/Enemy/Asteroids as components; Pythagoras collision; asteroid splitting; ships destroyed | `Common/services/*.java`, `Collision/CollisionDetector.java`, `Asteroids/AsteroidSplitterImpl.java`, [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| **JavaLab** | `ServiceLoader` assembly, whiteboard model | `Common/util/ServiceLocator.java` |
| **JPMSLab 1** | `requires` / `exports` per module | All 9 `module-info.java` files |
| **JPMSLab 2** | `provides … with`, `uses` | `Player`, `Enemy`, `Bullet`, `Asteroids`, `Collision`, `Common` module descriptors |
| **JPMSLab 3** | Reproduce a split package; fix with a `ModuleLayer`; `plugins/` folder | [`docs/JPMS_LAB3_SPLIT_PACKAGE.md`](docs/JPMS_LAB3_SPLIT_PACKAGE.md), `docs/jpms-lab3-demo/`, `ServiceLocator` |
| **SpringLab** | Spring container; DI of the plugin/processor lists into `Game` | `Core/ModuleConfig.java`, `Core/Main.java` |
| **MicroServiceLab** | Spring Boot scoring module; integrate via `RestTemplate` | `Scoring/`, `Core/ScoreClient.java`, [`docs/MICROSERVICE.md`](docs/MICROSERVICE.md) |
| **TestLab** | A unit test for a component; Mockito where stubbing is needed | The 5 test classes above |

The full requirement-by-requirement assessment is in [`full_report.md`](full_report.md).

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/AsteroidsFX-Technical-Report.pdf`](docs/AsteroidsFX-Technical-Report.pdf) | **The hand-in report.** Requirements, analysis, design, implementation, testing and discussion, with UML diagrams, sequence diagrams and captured evidence (43 pages) |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Components, provides/requires, service contracts, known gaps |
| [`docs/MICROSERVICE.md`](docs/MICROSERVICE.md) | Scoring API, Core dependency, failure handling |
| [`docs/JPMS_LAB3_SPLIT_PACKAGE.md`](docs/JPMS_LAB3_SPLIT_PACKAGE.md) | Split-package demo and ModuleLayer isolation |

---

## License

Educational / coursework project (SDU MMMI CBSE).  
Original project by [sweat-tek](https://github.com/sweat-tek/AsteroidsFX).  
This fork maintained by [Fard121](https://github.com/Fard121).

---

## Author

| Name | GitHub | Email |
|---|---|---|
| Fard121 | [github.com/Fard121](https://github.com/Fard121) | fjama23@student.sdu.dk |
