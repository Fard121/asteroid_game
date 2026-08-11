package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;
import dk.sdu.mmmi.cbse.common.services.IPostEntityProcessingService;
import dk.sdu.mmmi.cbse.common.util.ServiceLocator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the runtime lifecycle of every plugin the game is running:
 *
 * <pre>
 *   UNLOADED --load--&gt; DISABLED --enable--&gt; ENABLED
 *   ENABLED --disable--&gt; DISABLED --unload--&gt; UNLOADED
 * </pre>
 *
 * <p>{@code enable}/{@code disable} only add or remove a plugin's
 * already-constructed services from the lists the game loop iterates (this
 * is what the 1/2/3 keys have always done via {@link #toggle}).
 * {@code load}/{@code unload} go one step further and add or remove the
 * plugin's <em>classes</em>: unloading drops this registry's last
 * references to the plugin's service instances and then asks
 * {@link ServiceLocator} to drop its layer and class loader, so the plugin
 * genuinely leaves the JVM; loading resolves the jar from {@code plugins/}
 * again into a brand new loader and constructs fresh services from it.
 *
 * <p>Grouping is by JPMS module name, read off each service instance's own
 * {@code Class.getModule()}, so this stays a data-only mapping local to
 * Core rather than a compile-time dependency on Player/Enemy/Bullet.
 *
 * <p><b>Threading.</b> Every method here mutates game state and must be
 * called on the JavaFX application thread - {@code Game} funnels external
 * commands through a queue it drains at the top of a frame, so no command
 * ever runs concurrently with {@code update()}.
 */
class ComponentRegistry {

    /** Where a plugin is in its lifecycle. */
    enum State {
        /** Loaded and taking part in the game loop. */
        ENABLED,
        /** Classes still loaded, but not taking part in the game loop. */
        DISABLED,
        /** Classes released; only the jar on disk remains. */
        UNLOADED
    }

    /**
     * Names the game has always used for its components, mapped to the
     * JPMS module that actually implements them - so the existing
     * {@code toggle("Weapon")} key binding and a typed
     * {@code plugin unload Weapon} both resolve to module {@code Bullet}.
     */
    private static final String[][] NAME_ALIASES = {
        {"asteroids", "Asteroid"},
        {"playerbullets", "PlayerBullet"},
        {"enemybullets", "EnemyBullet"}
    };

    /**
     * Names that stand for more than one module. "Weapon" is what key 3 has
     * always toggled, and it used to mean the single Bullet component; now
     * that player and enemy fire are separate plugins it means both of them,
     * so the key keeps behaving exactly as it did.
     */
    private static final String[] WEAPON_MODULES = {"PlayerBullet", "EnemyBullet"};

    private static List<String> expand(String name) {
        String lowercase = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (lowercase.equals("weapon") || lowercase.equals("weapons")
                || lowercase.equals("bullet") || lowercase.equals("bullets")) {
            return Arrays.asList(WEAPON_MODULES);
        }
        return Collections.singletonList(name);
    }

    private static final class Component {

        final String moduleName;
        State state;
        List<IGamePluginService> plugins;
        List<IEntityProcessingService> processors;
        List<IPostEntityProcessingService> postProcessors;

        Component(String moduleName, State state,
                List<IGamePluginService> plugins,
                List<IEntityProcessingService> processors,
                List<IPostEntityProcessingService> postProcessors) {
            this.moduleName = moduleName;
            this.state = state;
            this.plugins = plugins;
            this.processors = processors;
            this.postProcessors = postProcessors;
        }
    }

    /** Keyed by module name, insertion-ordered for stable listings. */
    private final Map<String, Component> components = new LinkedHashMap<>();

    // Copy-on-write so the game loop can keep iterating these even if a
    // command mutates them, and so no removal can ever provoke a
    // ConcurrentModificationException mid-frame.
    private final List<IGamePluginService> activePlugins = new CopyOnWriteArrayList<>();
    private final List<IEntityProcessingService> activeProcessors = new CopyOnWriteArrayList<>();
    private final List<IPostEntityProcessingService> activePostProcessors = new CopyOnWriteArrayList<>();

    ComponentRegistry(List<IGamePluginService> allPlugins,
            List<IEntityProcessingService> allProcessors,
            List<IPostEntityProcessingService> allPostProcessors) {

        this.activePlugins.addAll(allPlugins);
        this.activeProcessors.addAll(allProcessors);
        this.activePostProcessors.addAll(allPostProcessors);

        // Everything discovered at startup starts out loaded and enabled -
        // exactly the state the game has always booted into.
        for (String moduleName : ServiceLocator.INSTANCE.loadedModuleNames()) {
            components.put(moduleName, new Component(moduleName, State.ENABLED,
                    ownedBy(moduleName, allPlugins),
                    ownedBy(moduleName, allProcessors),
                    ownedBy(moduleName, allPostProcessors)));
        }
    }

    private static <T> List<T> ownedBy(String moduleName, List<T> candidates) {
        List<T> owned = new ArrayList<>();
        for (T candidate : candidates) {
            Module module = candidate.getClass().getModule();
            if (module != null && moduleName.equals(module.getName())) {
                owned.add(candidate);
            }
        }
        return owned;
    }

    // ------------------------------------------------------------------
    // What the game loop iterates
    // ------------------------------------------------------------------

    List<IGamePluginService> getActivePlugins() {
        return activePlugins;
    }

    List<IEntityProcessingService> getActiveProcessors() {
        return activeProcessors;
    }

    List<IPostEntityProcessingService> getActivePostProcessors() {
        return activePostProcessors;
    }

    // ------------------------------------------------------------------
    // Lifecycle operations
    // ------------------------------------------------------------------

    /**
     * Installs the named component if it is currently uninstalled, or
     * uninstalls it if it is currently installed - the behaviour the 1/2/3
     * keys have always had. Unknown names are a no-op.
     */
    void toggle(String name, GameData gameData, World world) {
        for (String moduleName : expand(name)) {
            Component component = find(moduleName);
            if (component == null) {
                continue;
            }
            if (component.state == State.ENABLED) {
                disable(moduleName, gameData, world);
            } else {
                enable(moduleName, gameData, world);
            }
        }
    }

    /**
     * Stops the plugin participating in the game loop: its processors stop
     * being ticked, its {@code IGamePluginService.stop} runs so it can
     * retire its own entities, and any entity it still owns is swept out.
     * The plugin's classes stay loaded, so {@code enable} can bring it
     * straight back.
     */
    String disable(String name, GameData gameData, World world) {
        Component component = find(name);
        if (component == null) {
            return "unknown plugin '" + name + "'";
        }
        if (component.state == State.UNLOADED) {
            return component.moduleName + " is unloaded - 'load' it first";
        }
        if (component.state == State.DISABLED) {
            return component.moduleName + " is already disabled";
        }

        // Pulled out of the loop's lists first, so that even if a stop()
        // below misbehaves the plugin is already off the update path.
        activePlugins.removeAll(component.plugins);
        activeProcessors.removeAll(component.processors);
        activePostProcessors.removeAll(component.postProcessors);

        for (IGamePluginService plugin : component.plugins) {
            try {
                plugin.stop(gameData, world);
            } catch (RuntimeException e) {
                // A plugin that throws on the way out must not take the
                // game with it - it is already off the update path.
                System.err.println("[plugin] " + component.moduleName
                        + ": stop() failed, continuing anyway: " + e);
            }
        }

        int swept = removeEntitiesOwnedBy(component.moduleName, world);
        component.state = State.DISABLED;
        return component.moduleName + " disabled"
                + (swept > 0 ? " (" + swept + " leftover entit" + (swept == 1 ? "y" : "ies") + " removed)" : "");
    }

    /**
     * Puts a loaded-but-disabled plugin back into the game loop and runs
     * its {@code IGamePluginService.start} so it can seed its entities
     * again.
     */
    String enable(String name, GameData gameData, World world) {
        Component component = find(name);
        if (component == null) {
            return "unknown plugin '" + name + "'";
        }
        if (component.state == State.UNLOADED) {
            return component.moduleName + " is unloaded - 'load' it first";
        }
        if (component.state == State.ENABLED) {
            return component.moduleName + " is already enabled";
        }

        for (IGamePluginService plugin : component.plugins) {
            try {
                plugin.start(gameData, world);
            } catch (RuntimeException e) {
                System.err.println("[plugin] " + component.moduleName
                        + ": start() failed, leaving it disabled: " + e);
                // Roll back so a half-started plugin never reaches the loop.
                for (IGamePluginService started : component.plugins) {
                    try {
                        started.stop(gameData, world);
                    } catch (RuntimeException ignored) {
                        // best effort
                    }
                }
                removeEntitiesOwnedBy(component.moduleName, world);
                return component.moduleName + " could not be enabled: " + e;
            }
        }

        activePlugins.addAll(component.plugins);
        activeProcessors.addAll(component.processors);
        activePostProcessors.addAll(component.postProcessors);
        component.state = State.ENABLED;
        return component.moduleName + " enabled";
    }

    /**
     * Disables the plugin if needed, then releases it: this registry drops
     * its service instances and {@link ServiceLocator} drops the plugin's
     * module layer and class loader. Once no reference remains the loader
     * (and the plugin's classes, statics and jar handle) becomes
     * collectable, which is what makes a later {@link #load} a genuine
     * re-read from disk rather than a re-use of what was already in memory.
     */
    String unload(String name, GameData gameData, World world) {
        Component component = find(name);
        if (component == null) {
            return "unknown plugin '" + name + "'";
        }
        if (component.state == State.UNLOADED) {
            return component.moduleName + " is already unloaded";
        }

        StringBuilder result = new StringBuilder();
        if (component.state == State.ENABLED) {
            result.append(disable(component.moduleName, gameData, world)).append("; ");
        }

        // Belt and braces: even coming from DISABLED, sweep once more so no
        // entity of a class we are about to release can survive in the
        // world and be touched by the renderer or collision detection.
        removeEntitiesOwnedBy(component.moduleName, world);

        // Drop this registry's last references to the plugin's classes...
        component.plugins = new ArrayList<>();
        component.processors = new ArrayList<>();
        component.postProcessors = new ArrayList<>();

        // ...then Common's.
        try {
            ServiceLocator.INSTANCE.unloadPlugin(component.moduleName);
        } catch (RuntimeException e) {
            System.err.println("[plugin] " + component.moduleName + ": unload failed: " + e);
            return component.moduleName + " could not be unloaded: " + e;
        }

        component.state = State.UNLOADED;

        // Deliberately no System.gc() here: this runs on the game thread, and
        // forcing collections on it would stall the loop. Actually waiting for
        // the loader (and with it the jar file handle) to be released is done
        // off-thread by Game, which answers the shell only once it has
        // happened - see Game.completeWhenClassLoaderReleased.
        return result.append(component.moduleName).append(" unloaded").toString();
    }

    /**
     * Resolves the plugin's jar from {@code plugins/} into a fresh module
     * layer and class loader and constructs its services from it. The
     * plugin comes back {@code DISABLED}; {@code enable} then puts it into
     * the game loop.
     */
    String load(String name) {
        String moduleName = resolveModuleName(name);
        Component component = components.get(moduleName);
        if (component != null && component.state != State.UNLOADED) {
            return moduleName + " is already loaded";
        }

        try {
            ServiceLocator.INSTANCE.loadPlugin(moduleName);
        } catch (RuntimeException e) {
            System.err.println("[plugin] " + moduleName + ": load failed: " + e);
            return moduleName + " could not be loaded: " + e.getMessage();
        }

        List<IGamePluginService> plugins =
                ServiceLocator.INSTANCE.locateAll(moduleName, IGamePluginService.class);
        List<IEntityProcessingService> processors =
                ServiceLocator.INSTANCE.locateAll(moduleName, IEntityProcessingService.class);
        List<IPostEntityProcessingService> postProcessors =
                ServiceLocator.INSTANCE.locateAll(moduleName, IPostEntityProcessingService.class);

        if (component == null) {
            components.put(moduleName, new Component(moduleName, State.DISABLED,
                    plugins, processors, postProcessors));
        } else {
            component.plugins = plugins;
            component.processors = processors;
            component.postProcessors = postProcessors;
            component.state = State.DISABLED;
        }

        return moduleName + " loaded (disabled - run 'enable " + moduleName + "' to activate)";
    }

    /** One line per plugin, for {@code game plugin list}. */
    String list() {
        StringBuilder out = new StringBuilder();
        out.append(String.format("%-12s %-9s %s%n", "PLUGIN", "STATE", "DETAIL"));
        for (Component component : components.values()) {
            String detail;
            if (component.state == State.UNLOADED) {
                detail = ServiceLocator.INSTANCE.isClassLoaderReleased(component.moduleName)
                        ? "class loader released"
                        : "class loader not yet collected";
            } else {
                detail = component.plugins.size() + " plugin service(s), "
                        + component.processors.size() + " processor(s), "
                        + component.postProcessors.size() + " post-processor(s)";
            }
            out.append(String.format("%-12s %-9s %s%n",
                    component.moduleName, component.state, detail));
        }
        for (String available : ServiceLocator.INSTANCE.discoverModuleNames()) {
            if (!components.containsKey(available)) {
                out.append(String.format("%-12s %-9s %s%n",
                        available, "UNLOADED", "present in plugins/, never loaded"));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Removes every entity in the world whose class was loaded by the
     * plugin's own class loader. Plugins are expected to retire their own
     * entities in {@code stop()}, but this guarantees the invariant the
     * rest of the game relies on: after a plugin is disabled or unloaded,
     * no live entity can reference one of its classes. Entities built from
     * shared types (Bullet, Asteroid - both defined in the boot layer) are
     * deliberately untouched, since they outlive their producer.
     *
     * <p>Iterates a copy, and {@code World} is backed by a
     * {@code ConcurrentHashMap}, so removal here cannot corrupt the
     * collection.
     */
    private int removeEntitiesOwnedBy(String moduleName, World world) {
        ClassLoader loader = ServiceLocator.INSTANCE.getClassLoader(moduleName);
        if (loader == null) {
            return 0;
        }
        int removed = 0;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (entity.getClass().getClassLoader() == loader) {
                world.removeEntity(entity);
                removed++;
            }
        }
        return removed;
    }

    /**
     * The JPMS module name a user-typed name refers to, resolving the
     * historical aliases (so {@code Weapon} answers {@code Bullet}).
     */
    String moduleNameOf(String name) {
        return resolveModuleName(name);
    }

    /** Case-insensitive lookup by module name or historical alias. */
    private Component find(String name) {
        return components.get(resolveModuleName(name));
    }

    private String resolveModuleName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        String lowercase = trimmed.toLowerCase(Locale.ROOT);

        for (String[] alias : NAME_ALIASES) {
            if (alias[0].equals(lowercase)) {
                return alias[1];
            }
        }
        for (String known : components.keySet()) {
            if (known.toLowerCase(Locale.ROOT).equals(lowercase)) {
                return known;
            }
        }
        for (String available : ServiceLocator.INSTANCE.discoverModuleNames()) {
            if (available.toLowerCase(Locale.ROOT).equals(lowercase)) {
                return available;
            }
        }
        return trimmed;
    }
}
