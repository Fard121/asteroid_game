package demo;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;

/**
 * JPMS Lab 3 demo: moduleA and moduleB both export the same package
 * ("shared", each with its own shared.Greeter class) - a split package.
 *
 * Run with the modules directory as the only argument, e.g.:
 *   java --module-path runner-out -m runner/demo.Main out
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Path modsDir = Path.of(args.length > 0 ? args[0] : "out");
        ModuleFinder finder = ModuleFinder.of(modsDir);

        System.out.println("=== 1) Loading moduleA + moduleB into ONE layer/loader (split package) ===");
        try {
            Configuration configAB = ModuleLayer.boot().configuration()
                    .resolve(finder, ModuleFinder.of(), List.of("moduleA", "moduleB"));
            // Configuration.resolve() only checks the requires graph - the
            // split-package check happens when the modules are actually
            // defined together under one loader, which is what a single
            // main module path (or one defineModulesWithOneLoader call)
            // does for every module it loads.
            ModuleLayer.boot().defineModulesWithOneLoader(configAB, ClassLoader.getSystemClassLoader());
            System.out.println("Loaded without error (NOT expected - split package should have failed)");
        } catch (Exception e) {
            System.out.println("FAILED as expected:");
            System.out.println("  " + e);
        }

        System.out.println();
        System.out.println("=== 2) Resolving moduleA and moduleB into SEPARATE ModuleLayers instead ===");

        Configuration configA = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), List.of("moduleA"));
        ModuleLayer layerA = ModuleLayer.boot().defineModulesWithOneLoader(configA, ClassLoader.getSystemClassLoader());

        Configuration configB = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), List.of("moduleB"));
        ModuleLayer layerB = ModuleLayer.boot().defineModulesWithOneLoader(configB, ClassLoader.getSystemClassLoader());

        Object greeterA = layerA.findLoader("moduleA").loadClass("shared.Greeter").getDeclaredConstructor().newInstance();
        Object greeterB = layerB.findLoader("moduleB").loadClass("shared.Greeter").getDeclaredConstructor().newInstance();

        String resultA = (String) greeterA.getClass().getMethod("greet").invoke(greeterA);
        String resultB = (String) greeterB.getClass().getMethod("greet").invoke(greeterB);

        System.out.println("  layerA -> " + resultA);
        System.out.println("  layerB -> " + resultB);
        System.out.println();
        System.out.println("Both 'shared.Greeter' classes coexist because each ModuleLayer");
        System.out.println("has its own loader - isolating the two modules resolves the conflict.");
    }
}
