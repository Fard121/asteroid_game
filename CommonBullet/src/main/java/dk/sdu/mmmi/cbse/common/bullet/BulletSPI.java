package dk.sdu.mmmi.cbse.common.bullet;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.GameData;

/**
 * Provider interface for spawning a bullet fired by a given shooter entity.
 * Implemented by the Bullet module, required (via {@code uses}) by Player
 * and Enemy so either can fire without depending on Bullet's concrete
 * classes.
 *
 * @author corfixen
 */
public interface BulletSPI {

    /**
     * Creates (but does not add to the world) a new bullet entity fired
     * from {@code e}'s current position/rotation.
     *
     * <p>Precondition: {@code e} and {@code gameData} are non-null; {@code e}
     * has valid x/y/rotation set (the bullet spawns at, and inherits the
     * heading of, the shooter).
     *
     * <p>Postcondition: a new, fully-initialized bullet {@link Entity} is
     * returned - category, polygon, radius, and initial position/velocity
     * are all set. The caller (not this method) is responsible for adding
     * it to the {@link dk.sdu.mmmi.cbse.common.data.World}.
     *
     * @param e the shooter (player or enemy) the bullet is fired from
     * @param gameData shared game-session state, e.g. for display bounds
     * @return a new bullet entity, not yet added to the world
     */
    Entity createBullet(Entity e, GameData gameData);
}
