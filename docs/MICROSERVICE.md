# Modular Microservices Lab — Scoring Service

## What this is

`Scoring/` is a standalone Spring Boot microservice, deliberately **not** a
JPMS module (no `module-info.java`) - it runs as its own separate process,
launched independently of the game.

- `ScoringApplication` — `@SpringBootApplication` entry point.
- `ScoreController` — `@RestController` at `/api/score`:
  - `GET /api/score` → `{"score": N}` (current score)
  - `POST /api/score` with body `{"score": N}` → sets and returns the score
  - `POST /api/score/reset` → resets to 0
- In-memory only (an `AtomicInteger`) - no database, matching the lab's
  scope ("build the scoring system as a Spring Microservice").

Core's `dk.sdu.mmmi.cbse.main.ScoreClient` talks to it over HTTP using
Spring's `RestTemplate`, and `Game` pushes the locally-tracked score
(`gameData.getScoreState().getScore()`) to it once per frame **whenever it
changes** (not every frame - only on an actual score delta).

## Running it

The microservice must be started separately, before (or independent of)
the game:

```
mvn -pl Scoring clean package
java -jar Scoring/target/Scoring-1.0.1-SNAPSHOT.jar
```

It listens on `http://localhost:8081`. Verified working:

```
curl http://localhost:8081/api/score              # {"score":0}
curl -X POST http://localhost:8081/api/score \
     -H "Content-Type: application/json" -d '{"score": 250}'   # {"score":250}
curl -X POST http://localhost:8081/api/score/reset # {"score":0}
```

Then run the game as usual (`mvn exec:exec` from the root, or the
`--module-path=mods-mvn` command in the root README).

## This is a hard dependency, by design

There is still **no local fallback score path** - `Game.pushScoreIfChanged()`
genuinely requires the Scoring microservice to be reachable to sync score,
and there's no code path that silently substitutes local-only state instead.
What it does do is fail *quietly* after the first failure, instead of
retrying (and logging) every single frame:

- **First failure**: logs once to stderr
  (`Scoring microservice unreachable (...) - will keep retrying quietly
  every 3s.`), then backs off - no network call, no log line, for the next
  ~180 frames (~3s).
- **While unreachable**: silently retries every ~3s instead of every frame.
  No console spam.
- **On reconnect**: logs once (`Scoring microservice reachable again -
  resuming score sync.`) and resumes syncing on every score change.

This was arrived at after finding a real bug during manual testing: an
earlier version called `pushScoreIfChanged()` *before* `draw()` and the
key-state update in the per-frame loop, so the uncaught `RestClientException`
aborted the rest of that frame - the screen never redrew and key state
never advanced, which looked exactly like the game had frozen or stopped
responding to input. The fix was two-fold: (1) moved the score-sync call to
run *last* in the frame, after rendering and input handling are already
done, so a sync failure can no longer block them; (2) added the
log-once/quiet-retry behavior described above so an unreachable
microservice doesn't flood the console. Both were verified by actually
running the game with and without the microservice up.

## Dependencies this pulled onto Core's module path

Getting a plain JPMS module (`Core`) to use `RestTemplate` required more
than just `spring-web` itself - two more `requires` and one more `opens`
were needed, all confirmed necessary by actually running it and fixing
each failure in turn:

- `requires spring.web;` (the client itself)
- `requires micrometer.observation;` - `RestTemplate`'s constructor in this
  Spring version references `io.micrometer.observation.ObservationConvention`
  at class-init time even if observability isn't otherwise used.
- `requires com.fasterxml.jackson.databind;` - `RestTemplate` only
  registers its JSON `HttpMessageConverter` when Jackson is present; without
  it, POSTing a plain object fails with "No HttpMessageConverter for ...".
- `opens dk.sdu.mmmi.cbse.main to ..., com.fasterxml.jackson.databind;` -
  Jackson needs reflective access to `ScoreClient`'s request/response
  classes to serialize/deserialize them.
