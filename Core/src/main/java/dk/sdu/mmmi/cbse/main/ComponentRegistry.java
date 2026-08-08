package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Groups the already {@code ServiceLoader}-discovered
 * {@link IGamePluginService}/{@link IEntityProcessingService} instances by
 * component (Player, Enemy, Weapon) and lets {@code Game} install/uninstall
 * a component while the game loop keeps running - without recompiling or
 * rediscovering anything, since every instance here was already constructed
 * once at startup and is simply reused. Grouping is by the implementing
 * class's package name; this is a data-only mapping local to Core, not a
 * compile-time dependency on Player/Enemy/Bullet.
 */
class ComponentRegistry {

    private static final String[][] COMPONENT_PACKAGES = {
        {"Player", "dk.sdu.mmmi.cbse.playersystem"},
        {"Enemy", "dk.sdu.mmmi.cbse.enemysystem"},
        {"Weapon", "dk.sdu.mmmi.cbse.bulletsystem"}
    };

    private static final class Component {

        final String name;
        final List<IGamePluginService> plugins;
        final List<IEntityProcessingService> processors;
        boolean installed = true;

        Component(String name, List<IGamePluginService> plugins, List<IEntityProcessingService> processors) {
            this.name = name;
            this.plugins = plugins;
            this.processors = processors;
        }
    }

    private final List<Component> components = new ArrayList<>();
    private final List<IGamePluginService> activePlugins;
    private final List<IEntityProcessingService> activeProcessors;

    ComponentRegistry(List<IGamePluginService> allPlugins, List<IEntityProcessingService> allProcessors) {
        this.activePlugins = new CopyOnWriteArrayList<>(allPlugins);
        this.activeProcessors = new CopyOnWriteArrayList<>(allProcessors);

        for (String[] entry : COMPONENT_PACKAGES) {
            String name = entry[0];
            String packageName = entry[1];

            List<IGamePluginService> plugins = new ArrayList<>();
            for (IGamePluginService plugin : allPlugins) {
                if (plugin.getClass().getPackageName().equals(packageName)) {
                    plugins.add(plugin);
                }
            }

            List<IEntityProcessingService> processors = new ArrayList<>();
            for (IEntityProcessingService processor : allProcessors) {
                if (processor.getClass().getPackageName().equals(packageName)) {
                    processors.add(processor);
                }
            }

            components.add(new Component(name, plugins, processors));
        }
    }

    List<IGamePluginService> getActivePlugins() {
        return activePlugins;
    }

    List<IEntityProcessingService> getActiveProcessors() {
        return activeProcessors;
    }

    /**
     * Installs the named component if it is currently uninstalled, or
     * uninstalls it if it is currently installed. Unknown names are a
     * no-op.
     */
    void toggle(String name, GameData gameData, World world) {
        for (Component component : components) {
            if (!component.name.equals(name)) {
                continue;
            }
            if (component.installed) {
                for (IGamePluginService plugin : component.plugins) {
                    plugin.stop(gameData, world);
                }
                activePlugins.removeAll(component.plugins);
                activeProcessors.removeAll(component.processors);
            } else {
                for (IGamePluginService plugin : component.plugins) {
                    plugin.start(gameData, world);
                }
                activePlugins.addAll(component.plugins);
                activeProcessors.addAll(component.processors);
            }
            component.installed = !component.installed;
            return;
        }
    }
}
