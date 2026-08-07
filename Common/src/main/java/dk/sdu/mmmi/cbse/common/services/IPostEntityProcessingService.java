package dk.sdu.mmmi.cbse.common.services;

import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;

/**
 * Whiteboard-pattern service for per-frame logic that must run only after
 * every {@link IEntityProcessingService} has already processed the current
 * frame - i.e. logic that depends on final, settled entity positions
 * (collision detection is the only current implementer, via the Collision
 * module's {@code CollisionDetector}). Discovered via
 * {@link java.util.ServiceLoader} and run, in Core's {@code Game.update()},
 * once per frame for every implementation found, after all
 * {@code IEntityProcessingService} implementations have run.
 */
public interface IPostEntityProcessingService {

    /**
     * Reacts to the final entity state for the current frame.
     *
     * <p>Precondition: {@code gameData} and {@code world} are non-null;
     * every {@link IEntityProcessingService#process} call for this frame
     * has already completed, so all entity positions/rotations in
     * {@code world} are final for this frame.
     *
     * <p>Postcondition: any cross-entity effects that depend on final
     * position (e.g. collision resolution: damaging/removing entities via
     * {@link World#removeEntity}, awarding score, splitting asteroids) have
     * been fully applied for this frame. Implementations must snapshot
     * {@code world}'s entities before mutating it, so that removing one
     * match doesn't skip or double-process another pair in the same pass.
     *
     * @param gameData shared, mutable game-session state (score, lives, keys, ...)
     * @param world the live entity collection for this game session
     */
    void process(GameData gameData, World world);
}
