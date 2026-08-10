package dk.sdu.mmmi.cbse.common.util;

import dk.sdu.mmmi.cbse.common.services.IGamePluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the runtime plugin lifecycle end to end without depending on the
 * repository's own {@code plugins/} directory: each test compiles and jars a
 * throwaway plugin module on the fly, points {@code ServiceLocator} at it, and
 * drives it through {@code load → enable-able → unload → load again}.
 *
 * <p>That hermetic setup is what lets the strongest assertion in this class be
 * made at all — that after an unload the plugin's {@link ClassLoader} is
 * genuinely unreachable and collectable, which is the property that
 * distinguishes a real unload from merely unregistering a service.
 */
class ServiceLocatorTest {

    /**
     * {@code ServiceLocator} reads its directory freshly on every call rather
     * than caching it in a field, so a test can repoint it. The very first
     * touch of the enum still runs its constructor, so it is aimed at an empty
     * directory here before anything else can initialise it against the real
     * {@code plugins/} folder.
     */
    @BeforeAll
    static void isolateFromTheRealPluginsDirectory() throws IOException {
        Path empty = Files.createTempDirectory("asteroids-no-plugins");
        empty.toFile().deleteOnExit();
        System.setProperty("asteroids.plugins.dir", empty.toString());
        assertTrue(ServiceLocator.INSTANCE.loadedModuleNames().isEmpty(),
                "the locator must start with no plugins loaded for these tests to mean anything");
    }

    @AfterEach
    void unloadAnythingLeftBehind() {
        for (String moduleName : ServiceLocator.INSTANCE.loadedModuleNames()) {
            ServiceLocator.INSTANCE.unloadPlugin(moduleName);
        }
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    @Test
    void aMissingPluginsDirectoryYieldsNoModulesRatherThanAnError() {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.resolve("does-not-exist").toString());

        // Regression guard: this used to throw, because the directory was held
        // in a static field that an enum initialises only *after* its
        // constant - so it was still null while the constructor ran.
        assertTrue(ServiceLocator.INSTANCE.discoverModuleNames().isEmpty());
    }

    @Test
    void aJarDroppedInAfterStartUpIsDiscovered() throws Exception {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());
        assertTrue(ServiceLocator.INSTANCE.discoverModuleNames().isEmpty(),
                "nothing should be discovered before the jar is written");

        buildPluginJar(dir, "LateArrival");

        assertEquals(Set.of("LateArrival"), ServiceLocator.INSTANCE.discoverModuleNames(),
                "the directory must be re-scanned, not cached from start-up");
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    void loadMakesTheModulesServicesAvailableAndUnloadTakesThemAway() throws Exception {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());
        buildPluginJar(dir, "Demo");

        assertFalse(ServiceLocator.INSTANCE.isLoaded("Demo"));
        assertTrue(ServiceLocator.INSTANCE.locateAll(IGamePluginService.class).isEmpty());

        ServiceLocator.INSTANCE.loadPlugin("Demo");

        assertTrue(ServiceLocator.INSTANCE.isLoaded("Demo"));
        assertEquals(List.of("Demo"), ServiceLocator.INSTANCE.loadedModuleNames());
        List<IGamePluginService> services = ServiceLocator.INSTANCE.locateAll(IGamePluginService.class);
        assertEquals(1, services.size(), "the module provides exactly one IGamePluginService");
        assertEquals("Demo", services.get(0).getClass().getModule().getName());

        assertTrue(ServiceLocator.INSTANCE.unloadPlugin("Demo"));

        assertFalse(ServiceLocator.INSTANCE.isLoaded("Demo"));
        assertTrue(ServiceLocator.INSTANCE.locateAll(IGamePluginService.class).isEmpty(),
                "an unloaded plugin must not still answer service lookups");
        assertTrue(ServiceLocator.INSTANCE.getLayers().isEmpty());
    }

    @Test
    void repeatedLookupsReturnTheSameInstanceWhileLoaded() throws Exception {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());
        buildPluginJar(dir, "Stable");
        ServiceLocator.INSTANCE.loadPlugin("Stable");

        IGamePluginService first = ServiceLocator.INSTANCE.locateAll(IGamePluginService.class).get(0);
        IGamePluginService second = ServiceLocator.INSTANCE.locateAll(IGamePluginService.class).get(0);

        assertSame(first, second, "the game must keep running the same service instance it was given");
    }

    @Test
    void reloadingProducesFreshClassesFromANewLoader() throws Exception {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());
        buildPluginJar(dir, "Cycle");

        ServiceLocator.INSTANCE.loadPlugin("Cycle");
        ClassLoader first = ServiceLocator.INSTANCE.getClassLoader("Cycle");
        Class<?> firstClass = ServiceLocator.INSTANCE.locateAll(IGamePluginService.class).get(0).getClass();
        ServiceLocator.INSTANCE.unloadPlugin("Cycle");

        ServiceLocator.INSTANCE.loadPlugin("Cycle");
        ClassLoader second = ServiceLocator.INSTANCE.getClassLoader("Cycle");
        Class<?> secondClass = ServiceLocator.INSTANCE.locateAll(IGamePluginService.class).get(0).getClass();

        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first == second, "a reload must not reuse the old class loader");
        assertFalse(firstClass == secondClass, "a reload must define genuinely new classes");
        assertEquals(firstClass.getName(), secondClass.getName(), "...of the same type, read again from disk");
    }

    @Test
    void theCycleCanBeRepeatedManyTimesWithoutDegrading() throws Exception {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());
        buildPluginJar(dir, "Repeat");

        for (int i = 0; i < 10; i++) {
            ServiceLocator.INSTANCE.loadPlugin("Repeat");
            assertEquals(1, ServiceLocator.INSTANCE.locateAll(IGamePluginService.class).size(),
                    "iteration " + i + " should expose exactly one service");
            assertTrue(ServiceLocator.INSTANCE.unloadPlugin("Repeat"), "iteration " + i + " should unload");
        }
    }

    /**
     * The claim that separates a real unload from a cosmetic one: once the
     * locator has released the plugin, nothing reachable keeps its loader
     * alive, so the collector can reclaim it along with the plugin's classes,
     * statics and jar file handle.
     */
    @Test
    void unloadingReleasesTheClassLoaderForCollection() throws Exception {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());
        buildPluginJar(dir, "Releasable");

        ServiceLocator.INSTANCE.loadPlugin("Releasable");
        WeakReference<ClassLoader> loader =
                new WeakReference<>(ServiceLocator.INSTANCE.getClassLoader("Releasable"));
        assertNotNull(loader.get());

        ServiceLocator.INSTANCE.unloadPlugin("Releasable");

        assertNull(ServiceLocator.INSTANCE.getClassLoader("Releasable"),
                "an unloaded plugin has no class loader to hand out");
        assertTrue(collect(loader), "the plugin's class loader was still reachable after unload");
        assertTrue(ServiceLocator.INSTANCE.isClassLoaderReleased("Releasable"));
    }

    // ------------------------------------------------------------------
    // Failure handling
    // ------------------------------------------------------------------

    @Test
    void loadingAModuleThatIsNotThereIsRejectedClearly() {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ServiceLocator.INSTANCE.loadPlugin("NoSuchModule"));
        assertTrue(e.getMessage().contains("NoSuchModule"));
    }

    @Test
    void unloadingSomethingThatWasNeverLoadedIsReportedNotThrown() {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());

        assertFalse(ServiceLocator.INSTANCE.unloadPlugin("NeverLoaded"));
        assertFalse(ServiceLocator.INSTANCE.isClassLoaderReleased("NeverLoaded"),
                "a plugin that was never loaded has not been 'released'");
    }

    @Test
    void loadingTwiceIsANoOpRatherThanASecondLoader() throws Exception {
        Path dir = newPluginsDir();
        System.setProperty("asteroids.plugins.dir", dir.toString());
        buildPluginJar(dir, "Once");

        ServiceLocator.INSTANCE.loadPlugin("Once");
        ClassLoader first = ServiceLocator.INSTANCE.getClassLoader("Once");
        ServiceLocator.INSTANCE.loadPlugin("Once");

        assertSame(first, ServiceLocator.INSTANCE.getClassLoader("Once"));
        assertEquals(1, ServiceLocator.INSTANCE.loadedModuleNames().size());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * A throwaway plugins directory per test.
     *
     * <p>Deliberately not JUnit's {@code @TempDir}: a loaded plugin's jar stays
     * open until its class loader is collected, and Windows refuses to delete
     * an open file, so automatic cleanup would fail the test for a reason that
     * is actually the behaviour under test. These live under the OS temp
     * directory and are cleaned up by the OS.
     */
    private static Path newPluginsDir() {
        try {
            Path dir = Files.createTempDirectory("asteroids-plugins");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("could not create a temporary plugins directory", e);
        }
    }

    /**
     * Encourages collection and reports whether the referent went away.
     * Allocation pressure plus repeated {@code System.gc()} is the standard
     * way to make a weak-reachability assertion deterministic enough to test.
     */
    private static boolean collect(WeakReference<?> reference) {
        for (int attempt = 0; attempt < 50 && reference.get() != null; attempt++) {
            System.gc();
            @SuppressWarnings("unused")
            byte[] pressure = new byte[1 << 20];
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return reference.get() == null;
    }

    /**
     * Compiles and jars a minimal plugin module that provides one
     * {@link IGamePluginService}, so the lifecycle is exercised against a real
     * JPMS module rather than a stand-in. Compiled against the same Common
     * classes this test is running on, so the service type is identical.
     */
    private static void buildPluginJar(Path pluginsDir, String moduleName) throws IOException {
        Path work = Files.createTempDirectory("asteroids-plugin-src");
        Path pkg = work.resolve("src").resolve("demo");
        Files.createDirectories(pkg);

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(pkg.resolve("DemoPlugin.java")))) {
            w.println("package demo;");
            w.println("import dk.sdu.mmmi.cbse.common.data.GameData;");
            w.println("import dk.sdu.mmmi.cbse.common.data.World;");
            w.println("import dk.sdu.mmmi.cbse.common.services.IGamePluginService;");
            w.println("public class DemoPlugin implements IGamePluginService {");
            w.println("    public void start(GameData gameData, World world) { }");
            w.println("    public void stop(GameData gameData, World world) { }");
            w.println("}");
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(work.resolve("src").resolve("module-info.java")))) {
            w.println("module " + moduleName + " {");
            w.println("    requires Common;");
            w.println("    provides dk.sdu.mmmi.cbse.common.services.IGamePluginService with demo.DemoPlugin;");
            w.println("}");
        }

        Path classes = work.resolve("classes");
        Files.createDirectories(classes);

        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath == null || modulePath.isBlank()) {
            modulePath = System.getProperty("java.class.path");
        }

        ToolProvider javac = ToolProvider.findFirst("javac").orElseThrow();
        int rc = javac.run(System.out, System.err,
                "--module-path", modulePath,
                "-d", classes.toString(),
                work.resolve("src").resolve("module-info.java").toString(),
                pkg.resolve("DemoPlugin.java").toString());
        if (rc != 0) {
            throw new IllegalStateException("could not compile the synthetic plugin module");
        }

        ToolProvider jar = ToolProvider.findFirst("jar").orElseThrow();
        rc = jar.run(System.out, System.err,
                "--create",
                "--file", pluginsDir.resolve(moduleName + ".jar").toString(),
                "-C", classes.toString(), ".");
        if (rc != 0) {
            throw new IllegalStateException("could not package the synthetic plugin module");
        }
    }
}
