package dk.sdu.mmmi.cbse.common.util;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads every plugin module found in the {@code plugins/} directory into
 * its own child {@link ModuleLayer} (parented on the boot layer, so plugins
 * can still {@code requires Common}/{@code CommonBullet}/
 * {@code CommonAsteroids}) and exposes {@link java.util.ServiceLoader}
 * lookups against that layer.
 *
 * <p>Core itself only ever {@code requires}/{@code uses} the shared
 * interfaces in Common - it never lists Player/Enemy/Bullet/Asteroids/
 * Collision as module dependencies. Those five are resolved and loaded
 * here, at runtime, from whatever jars happen to be sitting in
 * {@code plugins/}, which is what lets them be swapped or added without
 * recompiling/relinking Core.
 *
 * <p>Loading them into one dedicated layer (via
 * {@code defineModulesWithOneLoader}) rather than putting them on Core's own
 * module path also sidesteps split-package conflicts: two modules that
 * happen to export the same package name can never both resolve into the
 * SAME configuration (JPMS rejects that with a {@code ResolutionException}
 * regardless of which layer they'd end up in), but keeping the plugin set
 * resolved as its own isolated configuration/layer means a clash between a
 * plugin and something unrelated on the boot layer's module path cannot
 * happen, and a clash between two plugins is caught early and explicitly
 * here (constructor failure) instead of silently corrupting Core's own
 * module graph. See {@code docs/JPMS_LAB3_SPLIT_PACKAGE.md} for a minimal,
 * standalone reproduction of that failure mode and its ModuleLayer-based
 * fix.
 */
public enum ServiceLocator {

    INSTANCE;

    private static final Map<Class, ServiceLoader> loadermap = new HashMap<>();
    private final ModuleLayer layer;

    ServiceLocator() {
        try {
            Path pluginsDir = Paths.get("plugins"); // Directory with plugin JARs

            // Search for plugins in the plugins directory
            ModuleFinder pluginsFinder = ModuleFinder.of(pluginsDir);

            // Find the names of all discovered plugin modules
            List<String> plugins = pluginsFinder
                    .findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .collect(Collectors.toList());

            // Resolve the plugin modules against the boot layer's
            // configuration as parent (so requires Common/CommonBullet/
            // CommonAsteroids resolves) - this is also where a split
            // package between two plugin jars would surface, as a
            // java.lang.module.ResolutionException.
            Configuration pluginsConfiguration = ModuleLayer
                    .boot()
                    .configuration()
                    .resolve(pluginsFinder, ModuleFinder.of(), plugins);

            // Define a dedicated child layer for the resolved plugins, all
            // sharing one loader (they're meant to see each other's
            // ServiceLoader-provided types), separate from the boot layer.
            layer = ModuleLayer
                    .boot()
                    .defineModulesWithOneLoader(pluginsConfiguration, ClassLoader.getSystemClassLoader());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load plugins from the 'plugins' directory - "
                            + "make sure 'mvn clean install' has run so plugin jars "
                            + "were copied there, and that no two plugins export the "
                            + "same package (split package).", e);
        }

    }

    /**
     * The plugins {@link ModuleLayer} itself, for callers that need to
     * invoke {@link ServiceLoader#load(ModuleLayer, Class)} themselves
     * instead of going through {@link #locateAll} - necessary whenever the
     * caller's own module (not Common) is the one that legitimately
     * declares {@code uses} for the service type in question. {@code
     * ServiceLoader.load(ModuleLayer, Class)} checks that the calling
     * module declares {@code uses} for that exact service; {@link
     * #locateAll} only works for the three service types Common itself
     * defines and declares {@code uses} for (IGamePluginService,
     * IEntityProcessingService, IPostEntityProcessingService) - Common
     * cannot also declare {@code uses BulletSPI}/{@code
     * uses IAsteroidSplitter} without requiring CommonBullet/
     * CommonAsteroids, which would create a dependency cycle (both of
     * those already require Common). So Player/Enemy (for BulletSPI) and
     * Collision (for IAsteroidSplitter) call
     * {@code ServiceLoader.load(ServiceLocator.INSTANCE.getLayer(), ...)}
     * directly instead - their own modules already declare the matching
     * {@code uses} clause.
     */
    public ModuleLayer getLayer() {
        return layer;
    }

    /**
     * Returns every implementation of {@code service} found across all
     * plugin modules in the plugins layer, instantiating each provider's
     * public no-arg constructor via {@link ServiceLoader} the first time it
     * is requested, then reusing that {@code ServiceLoader} (and hence its
     * already-created instances) on subsequent calls.
     *
     * <p>Only usable for service types Common itself defines and declares
     * {@code uses} for - see {@link #getLayer()}.
     */
    public <T> List<T> locateAll(Class<T> service) {
        ServiceLoader<T> loader = loadermap.get(service);

        if (loader == null) {
            loader = ServiceLoader.load(layer, service);
            loadermap.put(service, loader);
        }

        List<T> list = new ArrayList<T>();

        if (loader != null) {
            try {
                for (T instance : loader) {
                    list.add(instance);
                }
            } catch (ServiceConfigurationError serviceError) {
                serviceError.printStackTrace();
            }
        }

        return list;
    }

}
