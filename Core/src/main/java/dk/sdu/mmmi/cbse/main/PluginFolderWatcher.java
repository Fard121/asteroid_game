package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.util.ServiceLocator;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Watches the {@code plugins/} folder and reacts to a plugin jar being
 * deleted: the plugin is unloaded and its objects leave the game, while the
 * game keeps running.
 *
 * <p>The folder is the source of truth for what is installed. Deleting
 * {@code EnemyBullet-1.0.1-SNAPSHOT.jar} therefore silences enemies within a
 * second, with no command typed, and the plugin stays gone across restarts
 * because start-up loads whatever the folder holds.
 *
 * <p><b>Deliberately one-way.</b> Putting a jar back does <em>not</em> load
 * it again. Restoring is an explicit act - {@code game plugin load X} then
 * {@code enable X} - so a plugin never returns unless it is asked for.
 * Auto-restoring would mean a file copied back mid-game silently changes what
 * is running.
 *
 * <p>Polls rather than using a filesystem watch service: a poll is a few
 * directory reads a second, needs no platform-specific event plumbing, and
 * cannot miss a change that happened while the game was busy.
 *
 * <p>Runs on its own daemon thread and never touches game state directly. It
 * only parks an action on the queue that {@code Game} drains at the top of a
 * frame, which is what keeps unloading free of races with {@code update()}.
 */
final class PluginFolderWatcher {

    private static final long POLL_INTERVAL_MILLIS = 1000;

    private final Queue<Runnable> gameThreadActions;
    private final Consumer<String> onJarRemoved;
    private final Thread thread;
    private volatile boolean stopped;

    private PluginFolderWatcher(Queue<Runnable> gameThreadActions, Consumer<String> onJarRemoved) {
        this.gameThreadActions = gameThreadActions;
        this.onJarRemoved = onJarRemoved;
        this.thread = new Thread(this::watch, "plugin-folder-watcher");
        this.thread.setDaemon(true);
    }

    static PluginFolderWatcher start(Queue<Runnable> gameThreadActions, Consumer<String> onJarRemoved) {
        PluginFolderWatcher watcher = new PluginFolderWatcher(gameThreadActions, onJarRemoved);
        watcher.thread.start();
        System.out.println("[plugin] watching plugins/ - deleting a jar unloads that plugin; "
                + "put it back and run 'game plugin load <name>' to restore it");
        return watcher;
    }

    private void watch() {
        while (!stopped) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
                checkOnce();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // A transient filesystem hiccup must never kill the watcher
                // or the game; try again on the next tick.
                System.err.println("[plugin] folder watch failed, will retry: " + e);
            }
        }
    }

    private void checkOnce() {
        Set<String> onDisk = ServiceLocator.INSTANCE.discoverModuleNames();
        List<String> loaded = ServiceLocator.INSTANCE.loadedModuleNames();

        for (String moduleName : loaded) {
            if (!onDisk.contains(moduleName)) {
                // Hand the work to the game thread rather than doing it here.
                gameThreadActions.add(() -> onJarRemoved.accept(moduleName));
            }
        }
    }

    void stop() {
        stopped = true;
        thread.interrupt();
    }
}
