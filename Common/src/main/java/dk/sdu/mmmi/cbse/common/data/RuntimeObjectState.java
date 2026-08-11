package dk.sdu.mmmi.cbse.common.data;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether each {@link RuntimeObjectCategory} is currently active.
 *
 * <p>This is the single source of truth that producers consult before
 * creating anything: the Enemy component before spawning a saucer, the
 * Asteroids component before spawning a wave, and the Bullet component before
 * handing out a projectile. Because the check happens at the moment of
 * <em>creation</em>, deactivating a category does not merely clear what is
 * already on the field - it stops the next one from ever existing. That is the
 * difference between deleting objects and disabling the thing that makes them.
 *
 * <p>Every category is independent: deactivating enemy bullets says nothing
 * about enemies, player bullets or asteroids, and restoring one category never
 * restores another.
 *
 * <p>Reached from {@link GameData}, so every component already has it without
 * any new wiring. Backed by a concurrent set purely as a safety margin -
 * commands and the game loop both run on the JavaFX application thread, so
 * contention is not expected.
 */
public class RuntimeObjectState {

    /** Categories explicitly switched off; everything else is active. */
    private final Set<RuntimeObjectCategory> inactive = ConcurrentHashMap.newKeySet();

    /**
     * Whether producers may currently create objects of this category.
     * Everything is active by default, so a game that never issues a
     * lifecycle command behaves exactly as it always has.
     */
    public boolean isActive(RuntimeObjectCategory category) {
        return category != null && !inactive.contains(category);
    }

    /** Switches a category on or off. */
    public void setActive(RuntimeObjectCategory category, boolean active) {
        if (category == null) {
            return;
        }
        if (active) {
            inactive.remove(category);
        } else {
            inactive.add(category);
        }
    }

    /** The categories that are currently switched off. */
    public Set<RuntimeObjectCategory> getInactiveCategories() {
        return Collections.unmodifiableSet(inactive);
    }
}
