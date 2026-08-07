# JPMS Lab 3 — Split Package & Module Layers

This is a minimal, standalone reproduction of the split-package conflict
JPMS Lab 3 asks for, kept separate from the real game (`docs/jpms-lab3-demo/`)
so it doesn't need to be wired into the main Maven build. It's plain
`javac`/`java`, no Maven needed.

## The setup

Two modules, `moduleA` and `moduleB`, each declare and export the **same**
package name (`shared`), each with its own `shared.Greeter` class:

```
docs/jpms-lab3-demo/
  moduleA/module-info.java   -> module moduleA { exports shared; }
  moduleA/shared/Greeter.java
  moduleB/module-info.java   -> module moduleB { exports shared; }
  moduleB/shared/Greeter.java
  runner/module-info.java    -> module runner { }
  runner/demo/Main.java
```

## Reproducing it

```
cd docs/jpms-lab3-demo
javac --module-source-path . -d out --module moduleA,moduleB,runner
java --module-path out -m runner/demo.Main out
```

## What actually happens (real captured output)

```
=== 1) Loading moduleA + moduleB into ONE layer/loader (split package) ===
FAILED as expected:
  java.lang.LayerInstantiationException: Package shared in more than one module

=== 2) Resolving moduleA and moduleB into SEPARATE ModuleLayers instead ===
  layerA -> Hello from moduleA's shared.Greeter
  layerB -> Hello from moduleB's shared.Greeter

Both 'shared.Greeter' classes coexist because each ModuleLayer
has its own loader - isolating the two modules resolves the conflict.
```

Two things worth noting from actually running this:

- `Configuration.resolve(...)` alone does **not** reject the split package -
  it only resolves the `requires` graph. The conflict only surfaces when the
  modules are actually *defined* together under one loader
  (`ModuleLayer.defineModulesWithOneLoader`) - which is exactly what happens
  when every module sits on one flat module path, i.e. what a normal launch
  does.
- The fix isn't "rename a package" (the usual advice for split packages
  between your *own* modules) - it's to load the two conflicting modules
  into **separate** `ModuleLayer`s, each with its own class loader, so
  `shared.Greeter` from `moduleA` and `shared.Greeter` from `moduleB` are
  simply two different classes that never have to coexist in one loader's
  namespace. `Main.java` demonstrates this via reflection (`layer.findLoader(
  moduleName).loadClass(...)`), since a caller can't statically `import
  shared.Greeter` when it's ambiguous which module it would resolve from.

## How this connects to the real game

The real Asteroids game doesn't have an *actual* split package (every plugin
module's packages are unique - see `docs/ARCHITECTURE.md`), but it uses the
exact same isolation technique for a different reason: Core's
`ServiceLocator` (`Common/.../util/ServiceLocator.java`) loads Player, Enemy,
Bullet, Asteroids, and Collision from the `plugins/` folder into their own
dedicated `ModuleLayer`, via `defineModulesWithOneLoader`, instead of putting
them on Core's own module path. That's the same API this demo exercises -
here it's used to prove two modules *can't* share a loader if they share a
package; in the real game it's used so the plugin set can be resolved,
loaded, and (in principle) swapped independently of Core, and so a future
package clash between two plugins would fail loudly and specifically at
`ServiceLocator`'s construction rather than corrupting Core's own module
graph.
