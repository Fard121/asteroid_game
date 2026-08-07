package dk.sdu.mmmi.cbse.common.asteroids;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.World;

/**
 * Provider interface for splitting a destroyed asteroid into smaller
 * fragments. Implemented by the Asteroids module, required (via
 * {@code uses}) by Collision so it can trigger a split on impact without
 * depending on Asteroids' concrete classes.
 *
 * @author corfixen
 */
public interface IAsteroidSplitter {

    /**
     * Replaces a just-destroyed asteroid with its smaller fragments, if any.
     *
     * <p>Precondition: {@code e} and {@code w} are non-null; {@code e} is an
     * {@link Asteroid} that has already been (or is about to be) removed
     * from {@code w} by the caller - this method does not remove {@code e}
     * itself.
     *
     * <p>Postcondition: if {@code e}'s {@link Asteroid#getSize()} has a
     * smaller size to split into, new smaller-sized asteroid entities have
     * been added to {@code w} via {@link World#addEntity} at/near
     * {@code e}'s last position; if {@code e} was already the smallest
     * size, no entities are added (it is destroyed completely).
     *
     * @param e the destroyed asteroid to split
     * @param w the live entity collection for this game session
     */
    void createSplitAsteroid(Entity e, World w);
}
