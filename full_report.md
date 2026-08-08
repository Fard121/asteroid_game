# Full Lab Requirements Report — AsteroidsFX

**Project:** AsteroidsFX — a component-oriented Asteroids game
**Author:** [Fard121](https://github.com/Fard121) — fjama23@student.sdu.dk
**Repository:** https://github.com/Fard121/asteroid_game
**Forked from:** https://github.com/sweat-tek/AsteroidsFX
**Course:** SDU MMMI — Component-Based Software Engineering
**Report date:** 2026-08-08

> **Scope of this report.** Every one of the nine labs in `instructionlabs/labs.pdf` is checked
> requirement by requirement against the actual source code in this repository. For each lab the
> report states what the lab asked for, what was implemented, **how the implementation works**, and
> which file proves it.
>
> **Verification, 2026-08-08.** The audit was re-run against the labs PDF and the live code. It found
> one genuine functional gap (GameLab: enemy ships were not destroyed by asteroid collisions) and
> three stale documentation claims. All four were fixed; see
> [section 13](#13-gaps-and-recommendations) for the record. Evidence for every "✅" below was
> re-confirmed by a clean `mvn clean install` — **BUILD SUCCESS, 21 of 21 tests passing.**

---

## Table of Contents

1. [Executive summary](#1-executive-summary)
2. [Project inventory](#2-project-inventory)
3. [System architecture](#3-system-architecture)
4. [IntroLab — Introduction to an Asteroids Game](#4-introlab--introduction-to-an-asteroids-game)
5. [GameLab — Component and data-oriented game](#5-gamelab--component-and-data-oriented-game)
6. [JavaLab — Java ServiceLoader](#6-javalab--java-serviceloader)
7. [JPMSLab 1 — Java Platform Module System](#7-jpmslab-1--java-platform-module-system)
8. [JPMSLab 2 — JPMS Services](#8-jpmslab-2--jpms-services)
9. [JPMSLab 3 — Layers and split packages](#9-jpmslab-3--layers-and-split-packages)
10. [SpringLab — Spring in the Asteroids game](#10-springlab--spring-in-the-asteroids-game)
11. [MicroServiceLab — MicroServices](#11-microservicelab--microservices)
12. [TestLab — Testing component-based software](#12-testlab--testing-component-based-software)
13. [Gaps and recommendations](#13-gaps-and-recommendations)
14. [How to build and run](#14-how-to-build-and-run)
15. [Appendix — file map](#15-appendix--file-map)

---

## 1. Executive summary

| # | Lab | Core requirement | Status |
|---|---|---|---|
| 1 | **IntroLab** | JavaFX game with Core, Bullet, Player and a random enemy | ✅ Fulfilled |
| 2 | **GameLab** | Documented service contracts, collision, asteroid splitting | ✅ Fulfilled |
| 3 | **JavaLab** | `ServiceLoader`-driven component assembly (whiteboard model) | ✅ Fulfilled |
| 4 | **JPMSLab 1** | `requires` and `exports` in every `module-info.java` | ✅ Fulfilled |
| 5 | **JPMSLab 2** | `provides ... with` and `uses` service declarations | ✅ Fulfilled |
| 6 | **JPMSLab 3** | Split-package demo resolved with a `ModuleLayer` + `plugins/` | ✅ Fulfilled |
| 7 | **SpringLab** | Spring container and dependency injection in Core | ✅ Fulfilled |
| 8 | **MicroServiceLab** | Spring Boot scoring service integrated via `RestTemplate` | ✅ Fulfilled |
| 9 | **TestLab** | JUnit 5 unit tests, Mockito where stubbing is needed | ✅ Fulfilled |

**Result: 9 of 9 labs fulfilled.** The gaps found during this audit — one functional, three
documentation — have been closed and are recorded in
[section 13](#13-gaps-and-recommendations). Nothing is left outstanding.

**The through-line.** Each lab replaced a direct dependency with a discovered one. The end state is a
`Core` module whose `module-info.java` contains no `requires` for any gameplay module — it knows only
the interfaces in `Common`, and the five gameplay components are found at runtime.

---

## 2. Project inventory

Ten Maven modules, declared in the root `pom.xml`:

| Module | Kind | Role |
|---|---|---|
| `Core` | Application | JavaFX entry point, game loop, HUD, `ScoreClient` |
| `Common` | API + data | `Entity`, `GameData`, `World`, the three service interfaces, `ServiceLocator` |
| `CommonBullet` | API | `Bullet` entity and `BulletSPI` |
| `CommonAsteroids` | API | `Asteroid`, `AsteroidSize`, `IAsteroidSplitter` |
| `Player` | Plugin | Player ship: spawn, input, thrust, shooting |
| `Enemy` | Plugin | Enemy saucer: random movement and firing |
| `Bullet` | Plugin | Bullet lifetime; provides `BulletSPI` |
| `Asteroids` | Plugin | Asteroid spawn, wave movement, splitting |
| `Collision` | Plugin | Post-processing collision, scoring, split trigger |
| `Scoring` | Microservice | Standalone Spring Boot REST API on port 8081 |

**Counts:** 10 Maven modules · 9 `module-info.java` files (all but `Scoring`, which is a classpath
Spring Boot app) · 5 test classes · 21 test methods · 2 runtime processes.

---

## 3. System architecture

Three runtime tiers:

```
┌─────────────────────────────────────────────────────────┐
│  SCORING PROCESS  ·  port 8081                          │
│  Spring Boot — ScoreController — /api/score             │
└─────────────────────────────────────────────────────────┘
                          ▲  HTTP · RestTemplate
                          │
┌─────────────────────────────────────────────────────────┐
│  BOOT MODULE LAYER  ·  mods-mvn/                        │
│  Core · Common · CommonBullet · CommonAsteroids         │
└─────────────────────────────────────────────────────────┘
                          ▲  ServiceLocator
                          │  defineModulesWithOneLoader
┌─────────────────────────────────────────────────────────┐
│  CHILD MODULE LAYER  ·  plugins/                        │
│  Player · Enemy · Bullet · Asteroids · Collision        │
└─────────────────────────────────────────────────────────┘
```

**Design principles applied**

| Principle | How it appears in the code |
|---|---|
| Composition root | `Core` owns the loop but never `requires` a gameplay module |
| Interface segregation | Contracts live in `Common`, `CommonBullet`, `CommonAsteroids` |
| Plugin isolation | Gameplay jars resolve into a child `ModuleLayer` from `plugins/` |
| Ordered processing | Entity processors run first; post-processors run on final positions |
| Externalised state | Authoritative score is a separate HTTP process |

---

## 4. IntroLab — Introduction to an Asteroids Game

### 4.1 What the lab asked for

> Download and install the JDK · install an IDE · **fork the provided AsteroidsFX repository** ·
> build from the root folder with `mvn clean install` · run with `mvn exec:exec --non-recursive` ·
> create your own JPMS project setup · **implement the Core, Bullet and Player component** ·
> **implement an enemy spaceship. The enemy spaceship should shoot and move randomly.**

### 4.2 Requirement-by-requirement assessment

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | Fork the AsteroidsFX repository | ✅ | Forked to `github.com/Fard121/asteroid_game`; credited in `README.md` |
| 2 | Build with `mvn clean install` | ✅ | Root `pom.xml` (10 modules) + `mvnw` / `mvnw.cmd` wrapper |
| 3 | Run with `mvn exec:exec` | ✅ | `Core/pom.xml` exec configuration; `nbactions.xml` |
| 4 | Own JPMS project setup | ✅ | Multi-module Maven, 9 `module-info.java` files |
| 5 | Core component | ✅ | `Core/.../main/Main.java`, `Game.java`, `HUDRenderer.java` |
| 6 | Bullet component | ✅ | `Bullet/.../bulletsystem/BulletPlugin.java`, `BulletControlSystem.java` |
| 7 | Player component | ✅ | `Player/.../playersystem/PlayerPlugin.java`, `PlayerControlSystem.java` |
| 8 | Enemy **moves randomly** | ✅ | `Enemy/.../EnemyControlSystem.java` — `moveRandomly()` |
| 9 | Enemy **shoots** | ✅ | `Enemy/.../EnemyControlSystem.java` — `shoot()` via `BulletSPI` |

### 4.3 How it works

**Startup.** `Main.start(Stage)` creates an `AnnotationConfigApplicationContext` over
`ModuleConfig`, pulls the `Game` bean out of it, then calls `game.start(window)` followed by
`game.render()`. `render()` installs a JavaFX `AnimationTimer`, which is the game loop.

**The enemy is not scripted.** `EnemyControlSystem` keeps three frame counters as private fields:

| Counter | Constant | Effect |
|---|---|---|
| `framesUntilSpawn` | `SPAWN_DELAY_FRAMES = 180` | ~3 s before the first enemy, and again after each death |
| `framesUntilDirectionChange` | 60–150 frames | New random heading every 1–2.5 s |
| `shootCooldownFramesRemaining` | `SHOOT_COOLDOWN_FRAMES = 90` | One shot roughly every 1.5 s |

Each frame `moveRandomly()` decrements the direction counter; when it hits zero it calls
`enemy.setRotation(random.nextInt(360))` and re-arms with a fresh random interval. Movement is then
`cos`/`sin` of the rotation times `ENEMY_SPEED = 1.2`, and `wrap()` teleports the saucer across the
screen edges.

**Shooting without a dependency.** `shoot()` does *not* construct a `Bullet`. It calls
`getBulletSPIs()`, which does `ServiceLoader.load(ServiceLocator.INSTANCE.getLayer(), BulletSPI.class)`
and takes the first provider. The `Enemy` module therefore never imports anything from the `Bullet`
module — only the `BulletSPI` interface from `CommonBullet`.

**Player.** `PlayerControlSystem` handles `LEFT`/`RIGHT` (±5° rotation), `UP`
(`ACCELERATION = 0.12` along the facing vector, damped by `FRICTION = 0.98`, capped at
`MAX_SPEED = 3.5`), `SPACE` (fire, gated by a 15-frame cooldown and `Bullet.MAX_BULLETS`) and
`RESTART` once a run has ended.

### 4.4 Verdict

> **✅ FULLY FULFILLED.** All nine sub-requirements are implemented. The enemy satisfies both halves
> of the requirement — genuinely random movement and firing — and does so through a service
> interface rather than a direct class reference.

---

## 5. GameLab — Component and data-oriented game

### 5.1 What the lab asked for

> **Document** the provided `IGamePluginService`, `IEntityProcessorService` and
> `IPostEntityProcessorService` interfaces using JavaDoc. **Consider the pre- and post-conditions for
> each method signature.** · Implement the Player and Enemy as a separate project using those
> interfaces · Implement **randomly moving Asteroids** · Implement a **simple collision detection
> system based on Pythagoras** using `IPostEntityProcessorService`. Ships that collide with asteroids
> should be destroyed. When fired upon, Asteroids should **split into two smaller Asteroids** and when
> small enough they should be destroyed. The player ship and enemy-ships should be destroyed when hit
> by each other's bullets **a certain number of times** · Identify missing components · **Specify at
> contract level the required and provided interfaces** — pre- and post-conditions for each operation.

### 5.2 Requirement-by-requirement assessment

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | JavaDoc + contracts on `IGamePluginService` | ✅ | Pre/post-conditions on both `start()` and `stop()` |
| 2 | JavaDoc + contracts on `IEntityProcessingService` | ✅ | Ordering pre-condition documented on `process()` |
| 3 | JavaDoc + contracts on `IPostEntityProcessingService` | ✅ | Snapshot post-condition documented on `process()` |
| 4 | Player as a component | ✅ | `Player` module provides both plugin and processing services |
| 5 | Enemy as a component | ✅ | `Enemy` module provides both plugin and processing services |
| 6 | Randomly moving Asteroids | ✅ | `Asteroids/.../AsteroidProcessor.java` |
| 7 | Pythagorean collision detection | ✅ | `CollisionDetector.collides()` |
| 8 | Collision is a post-processor | ✅ | `Collision` provides `IPostEntityProcessingService` |
| 9 | Ships destroyed by asteroids | ✅ | `VALID_COLLISIONS` includes `PLAYER`↔`ASTEROID`, `PLAYER`↔`ENEMY` and `ENEMY`↔`ASTEROID` — see 5.3 |
| 10 | Asteroids split into two | ✅ | `AsteroidSplitterImpl.createSplitAsteroid()` — `for (i = 0; i < 2; i++)` |
| 11 | Small asteroids destroyed, not split | ✅ | `AsteroidSize.smaller()` returns `null` for `SMALL` |
| 12 | Destroyed after **a number of** hits | ✅ | `Entity.maxHealth`; enemy set to 3 in `EnemyControlSystem` |
| 13 | Identify missing components | ✅ | Dedicated `## Missing components / gaps identified` section in `docs/ARCHITECTURE.md` |
| 14 | Operation contracts specified | ✅ | All three interfaces carry explicit PRE/POST clauses |

### 5.3 How it works

**Why collision had to be a post-processor.** The JavaDoc on `IEntityProcessingService.process()`
states as a pre-condition that implementations run *"in no guaranteed order relative to other
`IEntityProcessingService` implementations"*. That single sentence is the design justification for
the whole two-phase loop: if collision ran as an ordinary entity processor it could observe some
entities already moved this frame and others not. So `Game.update()` runs the two phases strictly in
sequence:

```java
private void update() {
    for (IEntityProcessingService s : getEntityProcessingServices())      s.process(gameData, world);
    for (IPostEntityProcessingService s : getPostEntityProcessingServices()) s.process(gameData, world);
}
```

**The collision scan.** `CollisionDetector.process()` snapshots the world into an indexable
`ArrayList` and walks it with a nested loop where `j = i + 1`, so each unordered pair is tested
exactly once. Destroyed entities go into a `HashSet<Entity> toRemove` and are only removed **after
the full scan completes** — this is the snapshot post-condition the interface documents, and it
prevents removing an entity mid-scan from skipping or double-processing another pair.

**The Pythagoras test itself:**

```java
public Boolean collides(Entity entity1, Entity entity2) {
    float dx = (float) entity1.getX() - (float) entity2.getX();
    float dy = (float) entity1.getY() - (float) entity2.getY();
    float distance = (float) Math.sqrt(dx * dx + dy * dy);
    return distance < (entity1.getRadius() + entity2.getRadius());
}
```

**Which pairs may collide.** A `VALID_COLLISIONS` table filters pairs before any distance maths
runs, so asteroid-vs-asteroid and friendly fire are ignored:

| Pair | Meaning |
|---|---|
| `PLAYER_BULLET` ↔ `ASTEROID` | Player shoots a rock |
| `PLAYER_BULLET` ↔ `ENEMY` | Player shoots the saucer |
| `ENEMY_BULLET` ↔ `PLAYER` | Saucer shoots the player |
| `PLAYER` ↔ `ASTEROID` | Player rams a rock |
| `PLAYER` ↔ `ENEMY` | Player rams the saucer |
| `ENEMY` ↔ `ASTEROID` | Saucer flies into a rock |

> **Gap found and fixed during the 2026-08-08 audit.** The last row did not exist. The lab's wording
> is *"Ships that collide with asteroids should be destroyed"* — plural, and the enemy saucer is a
> ship. Previously an enemy could fly straight through a rock untouched. `ENEMY` ↔ `ASTEROID` was
> added to `VALID_COLLISIONS` with its own branch in `process()`: the saucer is destroyed outright
> (`enemy.damage(enemy.getHealth())`, not one point — a rock is not a bullet), the asteroid takes
> its normal one point of damage and splits as usual, and **no score is credited**, because the
> player did not make that kill. Covered by the new test
> `enemyShipIsDestroyedByAnAsteroidWithoutScoringForThePlayer`.

**Three destruction paths, deliberately different.** `process()` branches once a valid, overlapping
pair is found:

| Branch | Trigger | Outcome | Scores? |
|---|---|---|---|
| Player | either side is `PLAYER` | Player is never removed — `registerHit()` costs a life; the thing that hit it is damaged | No — a life was already paid |
| Ship vs rock | `ENEMY` ↔ `ASTEROID` | Saucer destroyed outright; asteroid splits | No — not the player's kill |
| Bullet | everything else | Both sides damaged; whoever runs out of health is removed | Yes |

**Multi-hit destruction.** `damage(Entity)` applies one point of damage and reports whether the
entity is now destroyed. Entities that never call `setMaxHealth()` default to 1, so bullets and
asteroids die on the first contact; the enemy is given `setMaxHealth(3)` and therefore survives two
bullets. The player is special-cased: it is never removed from the world — `PlayerState.registerHit()`
costs a life instead, and hits are ignored entirely while the respawn invulnerability window is open.

**Splitting.** When a destroyed entity is an asteroid, `splitIfAsteroid()` looks up
`IAsteroidSplitter` through the plugins layer and delegates. `AsteroidSplitterImpl` asks the
destroyed rock for `getSize().smaller()`; if that is `null` the rock simply disappears, otherwise it
adds **two** new asteroids of the smaller size at the same position with fresh random rotations.

| Size | Radius | Speed multiplier | Splits into | Points |
|---|---|---|---|---|
| `LARGE` | 14 | ×1.0 | 2 × `MEDIUM` | 20 |
| `MEDIUM` | 9 | ×1.4 | 2 × `SMALL` | 50 |
| `SMALL` | 5 | ×1.9 | *(destroyed)* | 100 |
| Enemy ship | — | — | — | 200 |

Smaller fragments move faster, so clearing a wave gets progressively harder — exactly the behaviour
the IntroLab brief describes.

### 5.4 Verdict

> **✅ FULLY FULFILLED — 14 of 14.** Requirement 9 ("ships destroyed by asteroids") was only partially
> met before this audit and is now complete for both ship types; requirement 13 ("identify missing
> components") has its own `## Missing components / gaps identified` section in
> `docs/ARCHITECTURE.md`. Every operation contract is stated as explicit PRE/POST JavaDoc on the
> interface itself.

---

## 6. JavaLab — Java ServiceLoader

### 6.1 What the lab asked for

> Follow *Creating Extensible Applications* and try the dictionary example **(optional, extra
> tutorial only)** · **Automate the Component Assembly in your own Asteroids game using the built-in
> `ServiceLoader` in Java (whiteboard component model).**

### 6.2 Requirement-by-requirement assessment

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | Component assembly automated via `ServiceLoader` | ✅ | `Common/.../util/ServiceLocator.java` |
| 2 | Whiteboard component model applied | ✅ | Core references no concrete plugin class anywhere |
| 3 | Understand why component interfaces exist | ✅ | Rationale documented in `ServiceLocator` JavaDoc and `docs/ARCHITECTURE.md` |
| 4 | Dictionary tutorial | ⚪ N/A | Explicitly optional in the lab text |

### 6.3 How it works

`ServiceLocator` is a singleton `enum` — one instance, initialised on first touch. Its constructor
builds the plugins layer (see [JPMSLab 3](#9-jpmslab-3--layers-and-split-packages)); `locateAll` is
the assembly entry point:

```java
public <T> List<T> locateAll(Class<T> service) {
    ServiceLoader<T> loader = loadermap.get(service);
    if (loader == null) {
        loader = ServiceLoader.load(layer, service);   // search the plugins layer
        loadermap.put(service, loader);
    }
    List<T> list = new ArrayList<T>();
    for (T instance : loader) list.add(instance);
    return list;
}
```

**The whiteboard model in one sentence.** A component joins the running game purely by declaring
`provides` in its `module-info.java`. Nothing registers it, nothing imports it, and `Core` holds no
reference to any concrete plugin class — it asks for an interface and takes whatever turns up.

**Why the `loadermap` cache matters.** `ServiceLoader` instantiates each provider lazily on first
iteration and then caches it internally. Re-using the same `ServiceLoader` per service type means the
`PlayerControlSystem` that `Game` calls every frame is the *same object* that was created at startup,
so its private cooldown counters persist. A fresh `ServiceLoader.load()` per frame would silently
reset them.

**Two lookup styles, and why both exist.** `locateAll` only works for the three service types that
`Common` itself declares `uses` for — `ServiceLoader.load(ModuleLayer, Class)` requires the *calling*
module to declare `uses` for that exact service. `Common` cannot declare `uses BulletSPI` without
requiring `CommonBullet`, which would be a dependency cycle (`CommonBullet` already requires
`Common`). So `Player`, `Enemy` and `Collision` call `ServiceLoader.load(ServiceLocator.INSTANCE.getLayer(), …)`
directly — their own `module-info.java` files already carry the matching `uses` clause.

### 6.4 Verdict

> **✅ FULLY FULFILLED.** One class performs the entire component assembly, and the optional tutorial
> was correctly skipped.

---

## 7. JPMSLab 1 — Java Platform Module System

### 7.1 What the lab asked for

> Continue to implement your own Asteroids game using JPMS · **Declare imports and exports in
> `module-info.java` files for each module in the Asteroids game.**

### 7.2 The nine module descriptors

| Module | `requires` | `exports` / `opens` |
|---|---|---|
| `Common` | `java.desktop` | exports `…common.services`, `…common.data`, `…common.util`, `…common.sound` |
| `CommonBullet` | `Common` | exports `…common.bullet` |
| `CommonAsteroids` | `Common` | exports `…common.asteroids` |
| `Core` | `Common`, `CommonBullet`, `CommonAsteroids`, `javafx.graphics`, `spring.context`, `spring.core`, `spring.beans`, `spring.web`, `micrometer.observation`, `com.fasterxml.jackson.databind` | exports `…cbse.main`; opens `…cbse.main` to `javafx.graphics`, `spring.core`, `jackson.databind` |
| `Player` | `Common`, `CommonBullet` | — (nothing exported) |
| `Enemy` | `Common`, `CommonBullet` | — |
| `Bullet` | `Common`, `CommonBullet` | — |
| `Asteroid` | `Common`, `CommonAsteroids` | — |
| `Collision` | `Common`, `CommonAsteroids` | — |

### 7.3 How it works

**The absence is the point.** `Core/module-info.java` contains no `requires Player`, no
`requires Enemy`, no `requires Bullet`, no `requires Asteroid`, no `requires Collision`. The compiler
therefore *physically cannot* let `Core` reach into a gameplay module. Strong encapsulation is what
makes the discovery mechanism necessary rather than decorative.

**No plugin exports anything.** All five gameplay modules keep every package sealed. Their classes
are only ever reached through the interfaces in `Common`/`CommonBullet`/`CommonAsteroids`, which is
exactly the intent of a plugin.

**`exports` versus `opens`.** `Core` *exports* `dk.sdu.mmmi.cbse.main` for compile-time access, but
JavaFX, Spring and Jackson all need **reflective** access to construct and populate objects at
runtime. `opens … to javafx.graphics, spring.core, com.fasterxml.jackson.databind` grants deep
reflection to exactly those three modules and nobody else — the narrow alternative to
`--add-opens ALL-UNNAMED`.

**Two `requires` that look unused, and why they are not.** `Core` requires `CommonAsteroids` even
though `Core` never names an asteroid type, and requires `micrometer.observation` even though no
Core class imports it:

- `CommonAsteroids` must be part of the boot layer's *configuration* because the child plugins layer
  is parented on that configuration; without it, `Collision`'s and `Asteroid`'s own
  `requires CommonAsteroids` cannot resolve.
- `micrometer.observation` is a transitive runtime dependency of `spring-web`'s `RestTemplate`. Unlike
  the plugins, `Core` is not bound via `ServiceLoader`, so everything it touches at runtime must be
  resolvable on its own module path.

Both are annotated with explanatory comments in the file itself.

### 7.4 Verdict

> **✅ FULLY FULFILLED.** Nine module descriptors, all with deliberate `requires` and minimal
> `exports`. The non-obvious declarations are documented in-place.

---

## 8. JPMSLab 2 — JPMS Services

### 8.1 What the lab asked for

> Continue to implement your Asteroids game using the Java Platform Module System **Services** ·
> **Declare `provides`, `with` and `uses` in `module-info.java` files for relevant modules.**

### 8.2 The complete service map

| Module | `provides … with` | `uses` |
|---|---|---|
| `Common` | — | `IGamePluginService`, `IEntityProcessingService`, `IPostEntityProcessingService` |
| `Player` | `IGamePluginService` → `PlayerPlugin`<br>`IEntityProcessingService` → `PlayerControlSystem` | `BulletSPI` |
| `Enemy` | `IGamePluginService` → `EnemyPlugin`<br>`IEntityProcessingService` → `EnemyControlSystem` | `BulletSPI` |
| `Bullet` | `IGamePluginService` → `BulletPlugin`<br>`IEntityProcessingService` → `BulletControlSystem`<br>**`BulletSPI` → `BulletControlSystem`** | — |
| `Asteroid` | `IGamePluginService` → `AsteroidPlugin`<br>`IEntityProcessingService` → `AsteroidProcessor`<br>**`IAsteroidSplitter` → `AsteroidSplitterImpl`** | — |
| `Collision` | `IPostEntityProcessingService` → `CollisionDetector` | `IAsteroidSplitter` |

Verbatim example — the producer/consumer pair:

```java
module Player {
    requires Common;
    requires CommonBullet;
    uses dk.sdu.mmmi.cbse.common.bullet.BulletSPI;                       // consumer
    provides IGamePluginService     with …playersystem.PlayerPlugin;
    provides IEntityProcessingService with …playersystem.PlayerControlSystem;
}

module Bullet {
    requires Common;
    requires CommonBullet;
    provides BulletSPI with …bulletsystem.BulletControlSystem;           // producer
    provides IGamePluginService     with …bulletsystem.BulletPlugin;
    provides IEntityProcessingService with …bulletsystem.BulletControlSystem;
}
```

### 8.3 How it works

**A service contract is not a dependency.** `Player` declares `uses BulletSPI` but never
`requires Bullet`. `Bullet` declares `provides BulletSPI` but never names `Player`. Neither module
knows the other exists; the module system connects them at runtime. Delete `Bullet.jar` from
`plugins/` and `Player` still compiles and still runs — it simply stops being able to shoot, because
`getBulletSPIs()` returns an empty list and `.findFirst().ifPresent(…)` does nothing.

**One class, three service roles.** `BulletControlSystem` is registered under both
`IEntityProcessingService` (so bullets advance every frame and expire) and `BulletSPI` (so other
components can ask it to manufacture a bullet). `ServiceLoader` treats these as separate services, so
two independent instances are created — which is correct here, since `BulletControlSystem` holds no
cross-role state.

**Why the three-way split of API modules.** `Common` cannot declare `uses BulletSPI` or
`uses IAsteroidSplitter` without requiring `CommonBullet`/`CommonAsteroids`, and both of those
already require `Common` — a cycle. Splitting the bullet and asteroid contracts into their own API
modules is what makes the `uses` declarations legal, and it is why `Player`/`Enemy`/`Collision` do
their own `ServiceLoader.load(layer, …)` calls rather than going through `ServiceLocator.locateAll`.

### 8.4 Verdict

> **✅ FULLY FULFILLED.** Every service in the system is declared with `provides … with` on the
> producing side and `uses` on the consuming side; no module names another gameplay module.

---

## 9. JPMSLab 3 — Layers and split packages

### 9.1 What the lab asked for

> **Try to rename two classes from two different modules to the same unique class name and see what
> happens** · **Resolve the issue of the introduced split packages by implementing a JPMS Module
> Layer** · **Create a `plugins` folder in your project root folder and move one of the split package
> modules to that folder**, using the `ModuleLayer` API with the JDK `ServiceLoader`.

### 9.2 Requirement-by-requirement assessment

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | Two classes with the same name in two modules | ✅ | `docs/jpms-lab3-demo/moduleA/shared/Greeter.java` and `moduleB/shared/Greeter.java` |
| 2 | Observe and record what happens | ✅ | `docs/JPMS_LAB3_SPLIT_PACKAGE.md` — captured real output |
| 3 | Resolve using a JPMS Module Layer | ✅ | `docs/jpms-lab3-demo/runner/demo/Main.java` — two separate layers |
| 4 | `plugins/` folder in the project root | ✅ | Populated by Maven; read by `ServiceLocator` |
| 5 | Modules moved to `plugins/` | ✅ | Player, Enemy, Bullet, Asteroids, Collision |
| 6 | `ModuleLayer` API + `ServiceLoader` | ✅ | `ServiceLocator` — `defineModulesWithOneLoader` + `ServiceLoader.load(layer, …)` |

### 9.3 How it works

**The failure, reproduced.** `moduleA` and `moduleB` each declare `exports shared;` and each contains
its own `shared.Greeter`. Loading both under one loader produces:

```
java.lang.LayerInstantiationException: Package shared in more than one module
```

**A subtlety worth presenting.** `Configuration.resolve(...)` alone does **not** reject the split
package — it only resolves the `requires` graph. The conflict surfaces only when the modules are
actually *defined* together under one loader via `defineModulesWithOneLoader`, which is precisely
what happens when every module sits on one flat module path. That is the normal launch case.

**The fix.** Not "rename the package" — resolve the two conflicting modules into **separate**
`ModuleLayer`s, each with its own class loader. `shared.Greeter` from `moduleA` and `shared.Greeter`
from `moduleB` then become two different classes that never coexist in one loader's namespace. The
demo proves it by loading each through `layer.findLoader(moduleName).loadClass(...)` — reflection is
required because a caller cannot statically `import shared.Greeter` when it is ambiguous which module
it would resolve from.

**The same API in the real game.** `ServiceLocator`'s constructor:

```java
Path pluginsDir = Paths.get("plugins");
ModuleFinder pluginsFinder = ModuleFinder.of(pluginsDir);

List<String> plugins = pluginsFinder.findAll().stream()
        .map(ModuleReference::descriptor)
        .map(ModuleDescriptor::name)
        .collect(Collectors.toList());

Configuration pluginsConfiguration = ModuleLayer.boot().configuration()
        .resolve(pluginsFinder, ModuleFinder.of(), plugins);

layer = ModuleLayer.boot()
        .defineModulesWithOneLoader(pluginsConfiguration, ClassLoader.getSystemClassLoader());
```

The game has no *actual* split package — every plugin's packages are unique. It uses the technique
for a different payoff: the plugin set resolves as its own isolated configuration, so it can be
swapped independently of `Core`, and a future package clash between two plugins fails loudly at
`ServiceLocator` construction (wrapped in an explanatory `RuntimeException`) instead of corrupting
`Core`'s module graph.

**Why one loader for all five plugins.** `defineModulesWithOneLoader` — singular — is deliberate. The
plugins must see each other's `ServiceLoader`-provided types (`Player` consumes `Bullet`'s
`BulletSPI`; `Collision` consumes `Asteroid`'s `IAsteroidSplitter`). Separate loaders per plugin would
break those hand-offs.

### 9.4 Verdict

> **✅ FULLY FULFILLED.** A standalone runnable reproduction *and* the same API applied in the real
> game, with the distinction between the two use cases documented.

---

## 10. SpringLab — Spring in the Asteroids game

### 10.1 What the lab asked for

> **Implement the Core component using the Spring container and the Dependency Injection Component
> Model** · You can combine JPMS with the Spring runtime container — use JPMS for reliable
> configuration and strong encapsulation · **Instantiate the `Game` class and use Spring for
> Dependency Injection of the `IEntityProcessors` and `IGamePluginServices`.**

### 10.2 Requirement-by-requirement assessment

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | Core implemented with the Spring container | ✅ | `Core/.../main/ModuleConfig.java` — `@Configuration` |
| 2 | `Game` instantiated by Spring | ✅ | `@Bean public Game game()` |
| 3 | `IGamePluginService` list injected | ✅ | `@Bean public List<IGamePluginService> gamePluginServices()` |
| 4 | `IEntityProcessingService` list injected | ✅ | `@Bean public List<IEntityProcessingService> entityProcessingServiceList()` |
| 5 | `IPostEntityProcessingService` list injected | ✅ | `@Bean public List<IPostEntityProcessingService> postEntityProcessingServices()` |
| 6 | JPMS combined with Spring | ✅ | `Core/module-info.java` requires `spring.context`/`core`/`beans`/`web` and `opens` its package |

### 10.3 How it works

**The composition root.**

```java
@Configuration
class ModuleConfig {
    @Bean public Game game() {
        return new Game(gamePluginServices(),
                        entityProcessingServiceList(),
                        postEntityProcessingServices(),
                        scoreClient());
    }
    @Bean public ScoreClient scoreClient() { return new ScoreClient(); }
    @Bean public List<IEntityProcessingService> entityProcessingServiceList() {
        return ServiceLocator.INSTANCE.locateAll(IEntityProcessingService.class);
    }
    @Bean public List<IGamePluginService> gamePluginServices() {
        return ServiceLocator.INSTANCE.locateAll(IGamePluginService.class);
    }
    @Bean public List<IPostEntityProcessingService> postEntityProcessingServices() {
        return ServiceLocator.INSTANCE.locateAll(IPostEntityProcessingService.class);
    }
}
```

**Two mechanisms, one hand-off, neither aware of the other.** JPMS finds the plugins through
`ServiceLocator`; Spring turns those plain `List`s into beans and passes them into `Game`'s
constructor. JPMS supplies reliable configuration and strong encapsulation; Spring supplies the
runtime wiring. `Game` receives everything through constructor injection, so it is trivially testable
with hand-built lists.

**Bootstrap order.** `Main.start(Stage)` creates the context, which triggers `ModuleConfig`, which on
first `@Bean` call touches `ServiceLocator.INSTANCE` — and *that* is what builds the plugins
`ModuleLayer`. So the module layer is constructed lazily, as a side effect of Spring asking for the
first plugin list. `Main` then pulls the `Game` bean, calls `start(window)` (which runs every
`IGamePluginService.start()` and builds the scene) and finally `render()` (which starts the
`AnimationTimer`).

**Why `opens` is required.** Spring must reflect into `dk.sdu.mmmi.cbse.main` to instantiate
`ModuleConfig` and invoke its `@Bean` methods. `exports` alone grants compile-time access, not deep
reflection — hence `opens dk.sdu.mmmi.cbse.main to javafx.graphics, spring.core, …`.

### 10.4 Verdict

> **✅ FULLY FULFILLED.** All three service lists plus `ScoreClient` are Spring-managed beans, `Game`
> is constructed by the container, and JPMS and Spring coexist through a single `opens` directive.

---

## 11. MicroServiceLab — MicroServices

### 11.1 What the lab asked for

> **Create a maven module for a scoring system** based on the Spring MicroService guide ·
> **Integrate the Scoring MicroService in the AsteroidsGame using the Spring `RestTemplate`.**

### 11.2 Requirement-by-requirement assessment

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | Separate Maven module for scoring | ✅ | `Scoring/pom.xml` — own module in the reactor |
| 2 | Spring Boot application | ✅ | `Scoring/.../ScoringApplication.java` |
| 3 | REST endpoints | ✅ | `Scoring/.../ScoreController.java` |
| 4 | Runs out of process | ✅ | Executable jar on port 8081 (`application.properties`) |
| 5 | Integrated via `RestTemplate` | ✅ | `Core/.../main/ScoreClient.java` |
| 6 | Documented | ✅ | `docs/MICROSERVICE.md` |

### 11.3 The API

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `GET` | `/api/score` | — | Read the current score |
| `POST` | `/api/score` | `{"score": N}` | Set the score |
| `POST` | `/api/score/reset` | — | Reset the score to zero |

```bash
curl http://localhost:8081/api/score
```

### 11.4 How it works

**Server side.** `ScoreController` is a `@RestController` mapped at `/api/score`. The score lives in
an `AtomicInteger`, so concurrent requests are safe without synchronisation. Request and response
bodies are Java `record`s (`ScoreUpdateRequest`, `ScoreResponse`), serialised by Jackson.

**Client side.** `ScoreClient.push(int)` is a thin wrapper — one `restTemplate.postForObject(...)`
call with no error handling by design; it propagates `RestClientException` like any other unreachable
HTTP call.

**Where the resilience actually lives.** The retry policy is in `Core/Game.java`, not in
`ScoreClient`. `pushScoreIfChanged()` is the **last statement** of the `AnimationTimer.handle()`
method, deliberately after rendering and key-state handling, and it does three things:

1. **Early-returns when the score has not changed.** Only a genuine delta triggers an HTTP call, so
   the service is not hammered at 60 requests per second.
2. **Catches `RestClientException`** and logs once rather than on every frame.
3. **Backs off by `SCORE_SYNC_RETRY_FRAMES = 180`** — about three seconds at 60 FPS — before trying
   again.

The net effect: if the scoring service is not running, the game still starts, still plays and still
shows the local HUD score. It just retries quietly in the background.

**Two sources of truth, deliberately.** `ScoreState` in `Common` is the local, authoritative-for-
gameplay score used by the HUD. The microservice holds the externally-visible copy. The game mirrors
local → remote; it never reads the remote value back into gameplay, which is why a service outage can
never corrupt a run.

### 11.5 Verdict

> **✅ FULLY FULFILLED.** A genuinely separate process with a three-endpoint REST API, integrated
> through `RestTemplate`, with sensible change-detection and back-off at the call site.

---

## 12. TestLab — Testing component-based software

### 12.1 What the lab asked for

> Read *JUnit Getting started*. If you have the need to stub or mock out dependencies, **Mockito** is
> recommended · **Write a unit test for one of the components. For example, write a test for moving
> the player ship or a test for collision detection.**

The lab asks for **one** test. Five test classes containing **21 test methods** were written.

### 12.2 Test inventory

| Test class | Module | Framework | Methods |
|---|---|---|---|
| `CollisionDetectorTest` | `Collision` | JUnit 5 + Mockito | 6 |
| `PlayerControlSystemTest` | `Player` | JUnit 5 + Mockito | 3 |
| `PlayerStateTest` | `Common` | JUnit 5 | 5 |
| `EntityTest` | `Common` | JUnit 5 | 4 |
| `AsteroidSizeTest` | `CommonAsteroids` | JUnit 5 | 3 |

**Both examples the lab names are covered** — collision detection *and* moving the player ship.

<details>
<summary><strong>All 21 test method names</strong></summary>

**`CollisionDetectorTest`**
- `collidesWhenDistanceIsLessThanCombinedRadii`
- `doesNotCollideWhenFarApart`
- `usesPythagoreanDistanceOnBothAxes`
- `enemyIsDestroyedOnlyAfterThreeBulletHits`
- `enemyShipIsDestroyedByAnAsteroidWithoutScoringForThePlayer`
- `bothSinglesHitEntitiesAreRemovedFromTheWorld`

**`PlayerControlSystemTest`**
- `rotatesLeftAndRightOnArrowKeys`
- `thrustAcceleratesInFacingDirectionAndFrictionSlowsItAfterwards`
- `wrapsAroundTheLeftScreenEdge`

**`PlayerStateTest`**
- `startsWithThreeLivesAndNotGameOver`
- `loseLifeDecrementsUntilGameOver`
- `hitsAreIgnoredWhileInvulnerable`
- `hitIsRegisteredOnceInvulnerabilityExpires`
- `resetRestoresInitialState`

**`EntityTest`**
- `defaultsToOneHitDestruction`
- `survivesUntilMaxHealthIsDepleted`
- `healthNeverGoesNegative`
- `setMaxHealthRefillsCurrentHealth`

**`AsteroidSizeTest`**
- `largeSplitsIntoMedium`
- `mediumSplitsIntoSmall`
- `smallHasNoSmallerSize`

</details>

### 12.3 How it works

`CollisionDetectorTest` deliberately demonstrates **both** testing styles the lab's reading material
describes.

**State-based — real objects, assert on resulting state.** Build a real `World`, fire three bullets
one frame at a time, and assert the enemy survives the first two and dies on the third:

```java
@Test
void enemyIsDestroyedOnlyAfterThreeBulletHits() {
    Entity enemy = entityAt(100, 100, 8, EntityCategory.ENEMY);
    enemy.setMaxHealth(3);
    world.addEntity(enemy);

    for (int hit = 1; hit <= 2; hit++) {
        world.addEntity(entityAt(100, 100, 1, EntityCategory.PLAYER_BULLET));
        detector.process(gameData, world);
        assertTrue(world.getEntities().contains(enemy), "enemy should survive hit " + hit);
    }

    world.addEntity(entityAt(100, 100, 1, EntityCategory.PLAYER_BULLET));
    detector.process(gameData, world);
    assertFalse(world.getEntities().contains(enemy), "enemy should be destroyed on the 3rd hit");
}
```

**Interaction-based — Mockito, assert on calls made.** Replace `World` with a mock and verify the
detector removed exactly the two entities involved and nothing else:

```java
World world = mock(World.class);
when(world.getEntities()).thenReturn(List.of(bullet, asteroid));

detector.process(gameData, world);

verify(world, times(1)).removeEntity(bullet);
verify(world, times(1)).removeEntity(asteroid);
verify(world, times(2)).removeEntity(any(Entity.class));   // and nothing else
```

That third `verify` is the important one — it proves the collision pass has no side effects beyond
the pair it matched.

**The maths is pinned to a known case.** `usesPythagoreanDistanceOnBothAxes` uses a 3-4-5 right
triangle: at `dx = 3, dy = 4` the distance is exactly 5, so combined radii of 4 must *not* collide
and combined radii of 6 must. This would catch a regression where someone compared only `dx` or
forgot the square root.

**Running them:**

```bash
mvn test
```

### 12.4 Verdict

> **✅ FULLY FULFILLED — substantially exceeded.** The lab asked for one test; the project has 21
> across 5 classes, covering both examples the lab names and both testing styles the reading
> material describes.

---

## 13. Gaps and recommendations

### 13.1 Findings from the 2026-08-08 audit — all closed

Every item below was found by re-reading `instructionlabs/labs.pdf` against the live code, then
fixed. The build was re-run clean afterwards: **BUILD SUCCESS, 21/21 tests passing.**

| # | Finding | Lab | Severity | What was done |
|---|---|---|---|---|
| 1 | **Enemy ships were not destroyed by asteroids.** `VALID_COLLISIONS` had no `ENEMY`↔`ASTEROID` pair, so the saucer flew through rocks untouched — but GameLab says *"Ships that collide with asteroids should be destroyed"* | GameLab | 🔴 Functional | Added the pair plus a dedicated branch in `CollisionDetector.process()`: saucer destroyed outright, asteroid splits normally, no score credited. New test `enemyShipIsDestroyedByAnAsteroidWithoutScoringForThePlayer` |
| 2 | **`IGamePluginService.stop()` documented as never called.** Its JavaDoc, and `docs/ARCHITECTURE.md` in two places, still said nothing invokes it — untrue since `ComponentRegistry` was added | — | 🟡 Stale doc | Corrected all three: `stop()` is called on runtime uninstall (keys `1`/`2`/`3`) and paired with `start()` on reinstall |
| 3 | **Contradictory comment on scoring.** `awardScoreForDestroyed`'s comment claimed ramming kills *do* score, while the branch above it said they don't. The code was right; the comment was wrong | — | 🟡 Stale doc | Comment rewritten to state the actual rule: score is credited only for kills the player shot |
| 4 | **README inconsistencies.** Tech Stack said *"Java 11+"* while Prerequisites said *"JDK 17+"*; the run command omitted `--non-recursive`; the `1`/`2`/`3` component-toggle keys were undocumented | IntroLab | 🟡 Doc | README standardised on **JDK 17+**, corrected to `mvn exec:exec --non-recursive`, and the toggle keys plus full step-by-step run/verify/troubleshoot instructions were added |

### 13.2 Deliberate design decisions — not gaps

Two things an examiner might flag on a quick read are intentional, and worth defending rather than
"fixing":

- **Plugin modules declare no `exports`.** JPMSLab 1 says *"declare imports and exports in
  `module-info.java` files for each module"*. `Player`, `Enemy`, `Bullet`, `Asteroids` and
  `Collision` declare `requires` and `provides` but export nothing — on purpose. Their classes are
  reached only through service interfaces; exporting them would hand other modules a compile-time
  path into plugin internals and undo exactly the encapsulation the lab is teaching. The modules
  that legitimately have an API to publish (`Common`, `CommonBullet`, `CommonAsteroids`, `Core`) all
  export it.
- **`Core` declares no `uses`.** The `uses` clauses sit in `Common/module-info.java` because
  `Common`'s `ServiceLocator` is the class that actually calls `ServiceLoader.load`. The result is
  the strongest possible reading of the brief: `Core/module-info.java` names no gameplay module and
  no gameplay service at all.

### 13.3 Genuinely out of scope

Not required by any of the nine labs, and left undone on purpose:

- **No hot *reload* of new plugin jars.** Components can be installed/uninstalled at runtime, but
  `ServiceLocator` builds its `ModuleLayer` once at startup, so a jar dropped into `plugins/`
  mid-session needs a restart.
- **No persistent high score.** The Scoring microservice holds the score in an `AtomicInteger`;
  MicroServiceLab asks for the service, not for a database behind it.
- **No dedicated wave/spawner component.** Spawn timing lives in `EnemyControlSystem` and
  `AsteroidProcessor`. Documented as a known gap in `docs/ARCHITECTURE.md`, which is what GameLab's
  "identify missing components" asks for.

---

## 14. How to build and run

**Prerequisites:** JDK 17+ and Maven 3.8+ (or the bundled `./mvnw` / `mvnw.cmd`). Run everything
**from the repository root** — `ServiceLocator` resolves `plugins/` relative to the working
directory.

**Step 1 — build every module.** Compiles, runs the 21 tests, and copies the jars into `mods-mvn/`
(boot layer) and `plugins/` (child layer). Both must be populated before launch, which is why this
is `install` and not `package`.

```bash
mvn clean install
```

**Step 2 — start the scoring microservice** in its own terminal, on port 8081:

```bash
java -jar Scoring/target/Scoring-1.0.1-SNAPSHOT.jar
```

**Step 3 — launch the game:**

```bash
mvn exec:exec --non-recursive
```

`--non-recursive` runs the `exec` plugin once for the root project only; without it Maven repeats
the launch for every child module. The underlying command is
`java --module-path=mods-mvn --module=Core/dk.sdu.mmmi.cbse.main.Main`.

**Step 4 — verify the full stack.** Shoot an asteroid, then from another terminal:

```bash
curl http://localhost:8081/api/score
```

The value should match the HUD score — proving the plugin layer, the game loop and the microservice
are all connected.

**Controls**

| Key | Action |
|---|---|
| ← → | Rotate |
| ↑ | Thrust |
| `Space` | Shoot / confirm menu |
| `P` | Pause |
| `M` | Mute |
| `H` | Help overlay |
| `R` | Restart (game over / victory) |
| `Q` | Quit (menus) |
| `1` / `2` / `3` | Install / uninstall the Player, Enemy and Weapon components at runtime |

Three lives, brief invulnerability on respawn; clear **wave 3** to win.

---

## 15. Appendix — file map

Every claim in this report traces to one of these files.

| Lab | Primary evidence |
|---|---|
| IntroLab | `Core/…/Main.java`, `Core/…/Game.java`, `Player/…/PlayerControlSystem.java`, `Enemy/…/EnemyControlSystem.java`, root `pom.xml` |
| GameLab | `Common/…/services/IGamePluginService.java`, `IEntityProcessingService.java`, `IPostEntityProcessingService.java`, `Collision/…/CollisionDetector.java`, `Asteroids/…/AsteroidSplitterImpl.java`, `Asteroids/…/AsteroidProcessor.java`, `CommonAsteroids/…/AsteroidSize.java` |
| JavaLab | `Common/…/util/ServiceLocator.java` |
| JPMSLab 1 | All nine `module-info.java` files |
| JPMSLab 2 | `Player/…/module-info.java`, `Bullet/…/module-info.java`, `Enemy/…/module-info.java`, `Asteroids/…/module-info.java`, `Collision/…/module-info.java`, `Common/…/module-info.java` |
| JPMSLab 3 | `docs/JPMS_LAB3_SPLIT_PACKAGE.md`, `docs/jpms-lab3-demo/**`, `Common/…/util/ServiceLocator.java` |
| SpringLab | `Core/…/main/ModuleConfig.java`, `Core/…/main/Main.java`, `Core/…/module-info.java` |
| MicroServiceLab | `Scoring/…/ScoreController.java`, `Scoring/…/ScoringApplication.java`, `Scoring/…/application.properties`, `Core/…/main/ScoreClient.java`, `Core/…/main/Game.java`, `docs/MICROSERVICE.md` |
| TestLab | `Collision/src/test/…/CollisionDetectorTest.java`, `Player/src/test/…/PlayerControlSystemTest.java`, `Common/src/test/…/PlayerStateTest.java`, `Common/src/test/…/EntityTest.java`, `CommonAsteroids/src/test/…/AsteroidSizeTest.java` |

**Supporting documentation:** `README.md` · `docs/ARCHITECTURE.md` · `docs/MICROSERVICE.md` ·
`docs/JPMS_LAB3_SPLIT_PACKAGE.md` · `AsteroidsFX-Presentation.pptx`

---

## Conclusion

All nine labs in `instructionlabs/labs.pdf` are fulfilled. Every requirement traces to working code,
and the architecture reflects the intent of the course rather than merely satisfying a checklist: the
`Core` module carries no compile-time knowledge of any gameplay component, the five plugins are
discovered at runtime from `plugins/` and resolved into their own `ModuleLayer`, Spring wires whatever
was found, and the score is mirrored to a genuinely separate process.

The 2026-08-08 audit closed the last outstanding item: one functional gap in GameLab's collision
rules and three stale documentation claims, all listed with their fixes in
[section 13](#13-gaps-and-recommendations). Nothing remains open.

*Verified 2026-08-08 with a clean `mvn clean install`: BUILD SUCCESS, 21 of 21 tests passing.
Source changes made as part of this audit are limited to the four fixes listed in section 13 — the
module structure, the service contracts and the plugin architecture are unchanged.*
