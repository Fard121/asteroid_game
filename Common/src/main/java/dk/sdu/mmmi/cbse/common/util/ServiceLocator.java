package dk.sdu.mmmi.cbse.common.util;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Loads every plugin module found in the {@code plugins/} directory and
 * exposes {@link java.util.ServiceLoader} lookups against them.
 *
 * <p>Core itself only ever {@code requires}/{@code uses} the shared
 * interfaces in Common - it never lists Player/Enemy/Bullet/Asteroids/
 * Collision as module dependencies. Those five are resolved and loaded
 * here, at runtime, from whatever jars happen to be sitting in
 * {@code plugins/}, which is what lets them be swapped or added without
 * recompiling/relinking Core.
 *
 * <p><b>One {@link ModuleLayer} per plugin.</b> Each plugin module is
 * resolved on its own against the boot layer's configuration and defined
 * into its own layer via {@code defineModulesWithOneLoader}, so every
 * plugin gets a private {@link ClassLoader}. That is what makes runtime
 * <em>unloading</em> possible at all: once the last reference to a
 * plugin's services, entities and layer is dropped, its loader - and with
 * it the plugin's classes, statics and jar file handle - becomes eligible
 * for garbage collection, and a subsequent {@link #loadPlugin} re-reads
 * the jar from disk into a brand new loader. A single shared layer for all
 * plugins could never do that: releasing one plugin would mean releasing
 * them all.
 *
 * <p>Per-plugin layers also keep the original split-package property
 * intact, and strengthen it: two plugins that export the same package can
 * no longer even meet in the same configuration.
 *
 * <p>Because each plugin now lives in a separate layer, a cross-plugin
 * service lookup (Enemy/Player asking for {@code BulletSPI}, Collision
 * asking for {@code IAsteroidSplitter}) has to iterate {@link #getLayers()}
 * rather than query one layer. See {@code getLayers()}.
 */
public enum ServiceLocator {

    INSTANCE;

    /**
     * Where plugin jars are looked up, relative to the working directory
     * the game was launched from. Overridable with
     * {@code -Dasteroids.plugins.dir=...} purely so tests/tooling can point
     * somewhere else; the shipped default is unchanged.
     */
    private static final Path PLUGINS_DIR =
            Paths.get(System.getProperty("asteroids.plugins.dir", "plugins"));

    /** A plugin module that is currently resolved into its own layer. */
    private static final class LoadedPlugin {

        final String moduleName;
        final ModuleLayer layer;
        final ClassLoader loader;
        /**
         * Provider instances per service type, so repeated
         * {@link #locateAll} calls hand back the same objects the game is
         * already running (matching the previous cached-ServiceLoader
         * behaviour) and so unloading has one definitive place to drop
         * them from.
         */
        final Map<Class<?>, List<?>> providerCache = new LinkedHashMap<>();

        LoadedPlugin(String moduleName, ModuleLayer layer) {
            this.moduleName = moduleName;
            this.layer = layer;
            this.loader = layer.findLoader(moduleName);
        }
    }

    /** Insertion-ordered so service processing order stays deterministic. */
    private final Map<String, LoadedPlugin> loadedPlugins = new LinkedHashMap<>();

    /**
     * Loaders of plugins that have been unloaded, kept only weakly - lets
     * {@link #isClassLoaderReleased} report honestly whether a plugin's
     * classes have actually gone away rather than merely been unregistered.
     */
    private final Map<String, WeakReference<ClassLoader>> unloadedLoaders = new LinkedHashMap<>();

    ServiceLocator() {
        // Startup behaviour is unchanged: every module sitting in
        // plugins/ is loaded up front, and a plugin that cannot be
        // resolved is a hard startup failure (same message as before).
        try {
            for (String moduleName : discoverModuleNames()) {
                loadedPlugins.put(moduleName, new LoadedPlugin(moduleName, defineLayerFor(moduleName)));
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load plugins from the 'plugins' directory - "
                            + "make sure 'mvn clean install' has run so plugin jars "
                            + "were copied there, and that no two plugins export the "
                            + "same package (split package).", e);
        }
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    /**
     * Every module name currently present in {@code plugins/}, re-scanned
     * on each call so a jar dropped in (or swapped) while the game is
     * running is picked up by the next {@code load}.
     */
    public Set<String> discoverModuleNames() {
        return ModuleFinder.of(PLUGINS_DIR)
                .findAll()
                .stream()
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static ModuleLayer defineLayerFor(String moduleName) {
        ModuleFinder finder = ModuleFinder.of(PLUGINS_DIR);

        // Resolve just this one module against the boot layer's
        // configuration as parent, so its `requires Common` /
        // `requires CommonBullet` / `requires CommonAsteroids` bind to the
        // boot layer's copies. Sharing those parent types is what lets a
        // plugin's entities and services stay type-compatible with the
        // rest of the running game across an unload/reload.
        Configuration configuration = ModuleLayer
                .boot()
                .configuration()
                .resolve(finder, ModuleFinder.of(), Set.of(moduleName));

        return ModuleLayer
                .boot()
                .defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public synchronized boolean isLoaded(String moduleName) {
        return loadedPlugins.containsKey(moduleName);
    }

    public synchronized List<String> loadedModuleNames() {
        return new ArrayList<>(loadedPlugins.keySet());
    }

    /**
     * Resolves {@code moduleName} out of {@code plugins/} into a fresh
     * layer with a fresh class loader, re-reading the jar from disk.
     * Loading an already-loaded plugin is a no-op.
     *
     * @throws IllegalArgumentException if no such module is in plugins/
     * @throws IllegalStateException if the module cannot be resolved
     */
    public synchronized void loadPlugin(String moduleName) {
        if (loadedPlugins.containsKey(moduleName)) {
            return;
        }
        if (!discoverModuleNames().contains(moduleName)) {
            throw new IllegalArgumentException(
                    "No module named '" + moduleName + "' found in " + PLUGINS_DIR.toAbsolutePath());
        }
        try {
            loadedPlugins.put(moduleName, new LoadedPlugin(moduleName, defineLayerFor(moduleName)));
            unloadedLoaders.remove(moduleName);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load plugin module '" + moduleName + "': " + e, e);
        }
    }

    /**
     * Drops every reference this class holds to the plugin: its cached
     * provider instances, its layer and its loader. After this returns,
     * nothing in Common can reach the plugin's classes any more - whether
     * the loader is actually collected additionally depends on the caller
     * having released the service/entity references it took out via
     * {@link #locateAll}, which is exactly what Core's plugin manager does
     * before calling here.
     *
     * @return {@code false} if the plugin was not loaded to begin with
     */
    public synchronized boolean unloadPlugin(String moduleName) {
        LoadedPlugin plugin = loadedPlugins.remove(moduleName);
        if (plugin == null) {
            return false;
        }
        plugin.providerCache.clear();
        unloadedLoaders.put(moduleName, new WeakReference<>(plugin.loader));
        return true;
    }

    /**
     * Whether an unloaded plugin's class loader has actually been
     * reclaimed. Reported by {@code plugin list} so an unload can be seen
     * to be real rather than merely bookkept; a {@code false} here only
     * means the GC has not run yet, not that a reference is necessarily
     * being held.
     */
    public synchronized boolean isClassLoaderReleased(String moduleName) {
        WeakReference<ClassLoader> reference = unloadedLoaders.get(moduleName);
        return reference != null && reference.get() == null;
    }

    /**
     * The class loader backing a loaded plugin, or {@code null} if the
     * plugin is not loaded. Used by Core to identify which live entities
     * a plugin owns.
     */
    public synchronized ClassLoader getClassLoader(String moduleName) {
        LoadedPlugin plugin = loadedPlugins.get(moduleName);
        return plugin == null ? null : plugin.loader;
    }

    // ------------------------------------------------------------------
    // Service lookup
    // ------------------------------------------------------------------

    /**
     * Every currently-loaded plugin's {@link ModuleLayer}, for callers that
     * need to run {@link ServiceLoader#load(ModuleLayer, Class)} themselves
     * instead of going through {@link #locateAll} - necessary whenever the
     * caller's own module (not Common) is the one that legitimately
     * declares {@code uses} for the service type in question.
     * {@code ServiceLoader.load(ModuleLayer, Class)} checks that the
     * <em>calling</em> module declares {@code uses} for that exact service;
     * {@link #locateAll} therefore only works for the three service types
     * Common itself defines and declares {@code uses} for
     * (IGamePluginService, IEntityProcessingService,
     * IPostEntityProcessingService). Common cannot also declare
     * {@code uses BulletSPI}/{@code uses IAsteroidSplitter} without
     * requiring CommonBullet/CommonAsteroids, which would create a
     * dependency cycle (both of those already require Common). So
     * Player/Enemy (for BulletSPI) and Collision (for IAsteroidSplitter)
     * iterate these layers themselves - their own modules already declare
     * the matching {@code uses} clause.
     *
     * <p>Returns a snapshot: a plugin unloaded after the call cannot be
     * reached through the returned list, and iterating it is safe while
     * other plugins are loaded or unloaded.
     */
    public synchronized List<ModuleLayer> getLayers() {
        List<ModuleLayer> layers = new ArrayList<>(loadedPlugins.size());
        for (LoadedPlugin plugin : loadedPlugins.values()) {
            layers.add(plugin.layer);
        }
        return Collections.unmodifiableList(layers);
    }

    /**
     * Returns every implementation of {@code service} found across all
     * currently-loaded plugin modules, instantiating each provider's public
     * no-arg constructor via {@link ServiceLoader} the first time that
     * plugin is asked for that service type, then reusing those instances.
     *
     * <p>Only usable for service types Common itself defines and declares
     * {@code uses} for - see {@link #getLayers()}.
     */
    public synchronized <T> List<T> locateAll(Class<T> service) {
        List<T> all = new ArrayList<>();
        for (LoadedPlugin plugin : loadedPlugins.values()) {
            all.addAll(providersOf(plugin, service));
        }
        return all;
    }

    /**
     * As {@link #locateAll(Class)} but restricted to a single plugin -
     * what Core uses after a {@code load} to pick up exactly the freshly
     * loaded plugin's services without disturbing the instances the game
     * is already running for every other plugin.
     */
    public synchronized <T> List<T> locateAll(String moduleName, Class<T> service) {
        LoadedPlugin plugin = loadedPlugins.get(moduleName);
        return plugin == null ? new ArrayList<>() : new ArrayList<>(providersOf(plugin, service));
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> providersOf(LoadedPlugin plugin, Class<T> service) {
        List<?> cached = plugin.providerCache.get(service);
        if (cached != null) {
            return (List<T>) cached;
        }

        List<T> instances = new ArrayList<>();
        try {
            for (T instance : ServiceLoader.load(plugin.layer, service)) {
                instances.add(instance);
            }
        } catch (ServiceConfigurationError serviceError) {
            // A single broken provider must not take the whole lookup (and
            // with it the running game) down.
            serviceError.printStackTrace();
        }
        plugin.providerCache.put(service, instances);
        return instances;
    }
}
