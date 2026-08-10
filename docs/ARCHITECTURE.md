# Component Analysis (Lab 2)

This document is the written analysis Lab 2 asks for: which components exist,
what each requires from / provides to the rest of the system, the operation
contract of every component, and what's still missing.

Full pre/postcondition contracts for each interface method live as Javadoc on
the interface itself (`Common/.../services/*.java`,
`CommonBullet/.../BulletSPI.java`, `CommonAsteroids/.../IAsteroidSplitter.java`)
— this document summarizes them and adds the cross-cutting picture.

## Components and their interfaces

| Module | Provides | Requires (`uses`) |
|---|---|---|
| Core | — (composition root, not a plugin) | `IGamePluginService`, `IEntityProcessingService`, `IPostEntityProcessingService` |
| Player | `IGamePluginService` (`PlayerPlugin`), `IEntityProcessingService` (`PlayerControlSystem`) | `BulletSPI` |
| Enemy | `IGamePluginService` (`EnemyPlugin`), `IEntityProcessingService` (`EnemyControlSystem`) | `BulletSPI` |
| Bullet | `IGamePluginService` (`BulletPlugin`), `IEntityProcessingService` (`BulletControlSystem`), `BulletSPI` (`BulletControlSystem`) | — |
| Asteroids | `IGamePluginService` (`AsteroidPlugin`), `IEntityProcessingService` (`AsteroidProcessor`), `IAsteroidSplitter` (`AsteroidSplitterImpl`) | — |
| Collision | `IPostEntityProcessingService` (`CollisionDetector`) | `IAsteroidSplitter` |
| Common | data model + service interfaces (`GameData`, `World`, `Entity`, `PlayerState`, `ScoreState`, `IGamePluginService`, `IEntityProcessingService`, `IPostEntityProcessingService`) | — |
| CommonBullet | `BulletSPI` interface + `Bullet` marker type | — |
| CommonAsteroids | `IAsteroidSplitter` interface + `Asteroid`/`AsteroidSize` types | — |

`CommonBullet` and `CommonAsteroids` are pure API/data modules — every other
module `requires` one or more of them to compile against the shared
interfaces and data types.

Two details the table above compresses:

- **Core consumes the three services but does not declare `uses` itself.**
  The `uses` clauses live in `Common/module-info.java`, because `Common`'s
  `ServiceLocator` is the class that actually calls `ServiceLoader.load`
  on Core's behalf. `Core/module-info.java` therefore names no gameplay
  module and no service — which is exactly the decoupling the labs are
  after.
- **`BulletSPI` and `IAsteroidSplitter` are looked up by their consumers.**
  `Common` cannot declare `uses` for those without requiring
  `CommonBullet`/`CommonAsteroids` (which already require `Common` — a
  cycle), so Player, Enemy and Collision declare `uses` in their own
  `module-info.java` and iterate
  `ServiceLoader.load(layer, ...)` over every layer returned by
  `ServiceLocator.INSTANCE.getLayers()` directly. (Each plugin now lives in
  its own layer — see `PLUGIN_LIFECYCLE.md` — so a cross-plugin lookup has
  to span layers rather than query a single shared one.)

## Operation contracts (summary)

- **`IGamePluginService.start`** — called once per plugin, before the game
  loop starts; adds the plugin's initial entities to `World`.
- **`IGamePluginService.stop`** — removes the plugin's entities; called by
  Core's `ComponentRegistry` whenever a component is disabled at runtime
  (keys `1`/`2`/`3`, or `game plugin disable <name>`), and followed by a
  fresh `start` when it is enabled again. It is also the first step of an
  `unload`. Core additionally sweeps any entity whose class belongs to the
  plugin's own class loader, so a `stop` that misses something cannot leave
  a dangling reference behind — see `PLUGIN_LIFECYCLE.md`.
- **`IEntityProcessingService.process`** — called once per frame, before any
  `IPostEntityProcessingService`; advances one component's own entities and
  must not assume other components have already run this frame.
- **`IPostEntityProcessingService.process`** — called once per frame, after
  every `IEntityProcessingService` has finished; the only place cross-entity
  effects (collision, destruction, scoring) are allowed to happen, since it's
  the first point at which every entity's position for the frame is final.
- **`BulletSPI.createBullet`** — pure factory, returns an unattached bullet
  entity; caller adds it to `World`.
- **`IAsteroidSplitter.createSplitAsteroid`** — given a destroyed asteroid,
  adds its smaller fragments to `World` (or none, if already smallest).

## Missing components / gaps identified

- **No dedicated Wave/Spawner component.** Enemy spawn timing lives inside
  `EnemyControlSystem` and asteroid wave logic inside `AsteroidProcessor` /
  `WaveState` rather than a standalone service — acceptable at this scale,
  but a growing project would want a `IWaveService` so spawn-rate tuning
  doesn't require touching each entity's own control system.
- **No persistent high-score storage.** `ScoreState` (Common) is in-memory
  only and resets on restart; there is no component responsible for
  persisting a high score across runs.
- ~~**No full plugin *reload* path.**~~ **Closed.** `ServiceLocator` no
  longer builds one `ModuleLayer` once; it builds **one layer per plugin**,
  each with its own class loader, and can drop and rebuild an individual
  layer while the game loop keeps running. `ComponentRegistry` drives the
  full `load → enable → disable → unload → load → enable` cycle, and
  `game plugin <action> <name>` exposes it to a shell. No restart, no
  recompilation, no relinking. See `PLUGIN_LIFECYCLE.md` for the mechanism,
  the cleanup contract, and the verification evidence.
- **Scoring was not a separate deployable component** until the Microservices
  Lab work — it lived entirely in-process inside `Common`/`Collision`. See
  `Scoring/` module and `ScoreClient` in Core for the extracted version.
- **No automated tests** existed prior to the Testing Lab work now added
  under each module's `src/test/java`.
