package dk.sdu.mmmi.cbse.common.services;

import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;

/**
 * Whiteboard-pattern service implemented by every game component that owns
 * one or more kinds of entity (Player, Enemy, Asteroids, Bullet). Core
 * discovers implementations via {@link java.util.ServiceLoader} and never
 * references a concrete plugin class directly - a component becomes part of
 * the running game purely by providing this service in its module-info.
 */
public interface IGamePluginService {

    /**
     * Called exactly once per plugin instance, during {@code Game.start()},
     * before the render/update loop begins and before any
     * {@link IEntityProcessingService#process} call.
     *
     * <p>Precondition: {@code gameData} and {@code world} are non-null and
     * represent a freshly-constructed game session; {@code world} may
     * already contain entities added by other plugins started earlier in
     * the same pass, but none of this plugin's own entities exist yet.
     *
     * <p>Postcondition: any entities this plugin is responsible for
     * creating up front (e.g. the player ship) have been added to
     * {@code world} via {@link World#addEntity}, and any plugin-local
     * state needed by a later {@code IEntityProcessingService} has been
     * initialized. Must not block or throw for a well-formed
     * {@code gameData}/{@code world}.
     *
     * @param gameData shared, mutable game-session state (score, lives, keys, ...)
     * @param world the live entity collection for this game session
     */
    void start(GameData gameData, World world);

    /**
     * Called when the plugin's entities/resources should be released -
     * invoked by Core's {@code ComponentRegistry} when the player
     * uninstalls that plugin's component at runtime (keys 1/2/3), and
     * paired with a later {@link #start} if the component is reinstalled.
     *
     * <p>Precondition: {@link #start} was previously called on this same
     * instance with the same {@code gameData}/{@code world}.
     *
     * <p>Postcondition: every entity exclusively owned by this plugin has
     * been removed from {@code world} via {@link World#removeEntity}, and
     * any non-entity resources the plugin allocated (timers, listeners)
     * have been released. Calling {@code stop} without a prior
     * {@code start} is a no-op, not an error.
     *
     * @param gameData shared, mutable game-session state
     * @param world the live entity collection for this game session
     */
    void stop(GameData gameData, World world);
}
