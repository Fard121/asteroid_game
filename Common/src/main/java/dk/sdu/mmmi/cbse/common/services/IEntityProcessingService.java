package dk.sdu.mmmi.cbse.common.services;

import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;

/**
 * Whiteboard-pattern service for per-frame gameplay logic that only needs
 * to see entity state as of the start of the current frame (movement,
 * shooting cooldowns, input handling). Discovered via
 * {@link java.util.ServiceLoader} and run, in Core's {@code Game.update()},
 * once per frame for every implementation found - before any
 * {@link IPostEntityProcessingService}, so no entity positions for this
 * frame are final yet when this runs.
 */
public interface IEntityProcessingService {

    /**
     * Advances this component's own entities by exactly one frame.
     *
     * <p>Precondition: {@code gameData} and {@code world} are non-null;
     * called once per frame, in no guaranteed order relative to other
     * {@code IEntityProcessingService} implementations, so this method must
     * not depend on another component's entities already having been
     * updated this frame (e.g. it may not assume collisions for this frame
     * have been resolved - that only happens afterwards, in
     * {@link IPostEntityProcessingService#process}).
     *
     * <p>Postcondition: every entity this component owns that is present
     * in {@code world} has had its position/rotation/internal cooldowns
     * updated for exactly one frame, and any entities it spawns this frame
     * (e.g. a bullet) have been added via {@link World#addEntity}. Must
     * never remove another component's entities.
     *
     * @param gameData shared, mutable game-session state (score, lives, keys, ...)
     * @param world the live entity collection for this game session
     */
    void process(GameData gameData, World world);
}
