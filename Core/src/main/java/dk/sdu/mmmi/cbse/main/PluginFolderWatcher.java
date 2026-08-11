package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.util.ServiceLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p><b>The folder is the instruction.</b> Deleting a jar removes that
 * plugin; putting it back restores it, loaded and enabled, within about a
 * second. Copying the file back is itself the deliberate act of restoring,
 * so nothing further has to be typed.
 *
 * <p>The watcher only ever undoes <em>its own</em> work. A plugin the user
 * unloaded on purpose with {@code plugin unload} is left alone no matter what
 * the folder contains, so an explicit decision is never overridden by a file
 * that merely happens to be present.
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
    private final Consumer<String> onJarRestored;
    private final Thread thread;
    private volatile boolean stopped;

    /**
     * Plugins this watcher unloaded because their jar disappeared - and only
     * those. Putting such a jar back is treated as the user restoring it, so
     * the plugin comes straight back.
     *
     * <p>A plugin the user unloaded deliberately with {@code plugin unload}
     * never lands in here, so it stays unloaded no matter what the folder
     * looks like. The rule is simply that the watcher only ever undoes its
     * own actions, never the user's.
     */
    private final Set<String> removedByWatcher = ConcurrentHashMap.newKeySet();

    private PluginFolderWatcher(Queue<Runnable> gameThreadActions,
            Consumer<String> onJarRemoved, Consumer<String> onJarRestored) {
        this.gameThreadActions = gameThreadActions;
        this.onJarRemoved = onJarRemoved;
        this.onJarRestored = onJarRestored;
        this.thread = new Thread(this::watch, "plugin-folder-watcher");
        this.thread.setDaemon(true);
    }

    static PluginFolderWatcher start(Queue<Runnable> gameThreadActions,
            Consumer<String> onJarRemoved, Consumer<String> onJarRestored) {
        PluginFolderWatcher watcher =
                new PluginFolderWatcher(gameThreadActions, onJarRemoved, onJarRestored);
        watcher.thread.start();
        System.out.println("[plugin] watching plugins/ - delete a jar to remove that plugin "
                + "from the running game, put it back to restore it");
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
                // Remember that *we* took this one out, so that putting the
                // jar back can bring it back. Hand the work to the game
                // thread rather than doing it here.
                removedByWatcher.add(moduleName);
                gameThreadActions.add(() -> onJarRemoved.accept(moduleName));
            }
        }

        for (String moduleName : new ArrayList<>(removedByWatcher)) {
            if (!onDisk.contains(moduleName)) {
                continue; // still gone
            }
            if (loaded.contains(moduleName)) {
                // Already back - the user loaded it by command before the
                // jar reappeared, or the restore below has been applied.
                removedByWatcher.remove(moduleName);
                continue;
            }
            removedByWatcher.remove(moduleName);
            gameThreadActions.add(() -> onJarRestored.accept(moduleName));
        }
    }

    void stop() {
        stopped = true;
        thread.interrupt();
    }
}
