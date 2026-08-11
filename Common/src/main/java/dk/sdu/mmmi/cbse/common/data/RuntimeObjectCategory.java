package dk.sdu.mmmi.cbse.common.data;

/**
 * The runtime object categories whose lifecycle can be controlled
 * independently while the game is running.
 *
 * <p>Each category maps onto the {@link EntityCategory} its entities carry,
 * which is what lets a category be emptied out of the world without anyone
 * having to know the concrete classes involved.
 *
 * <p><b>Enemy bullets and player bullets are deliberately separate
 * categories.</b> Both are produced by the same Bullet component and both are
 * {@code Bullet} instances, but they are two different runtime things: one is
 * fired by enemies, the other by the player. Keeping them apart here is what
 * allows the player to keep firing while enemies are silenced, or the reverse.
 * The distinction already exists on every entity as
 * {@link EntityCategory#PLAYER_BULLET} versus
 * {@link EntityCategory#ENEMY_BULLET}; this enum is what makes it
 * controllable.
 */
public enum RuntimeObjectCategory {

    /** Enemy ships. Independent of whether enemies may shoot. */
    ENEMY("Enemy", EntityCategory.ENEMY),

    /** Projectiles fired by enemies. */
    ENEMY_BULLETS("EnemyBullets", EntityCategory.ENEMY_BULLET),

    /** Projectiles fired by the player. */
    PLAYER_BULLETS("PlayerBullets", EntityCategory.PLAYER_BULLET),

    /** Asteroids, including fragments produced by splitting. */
    ASTEROIDS("Asteroids", EntityCategory.ASTEROID);

    private final String displayName;
    private final EntityCategory entityCategory;

    RuntimeObjectCategory(String displayName, EntityCategory entityCategory) {
        this.displayName = displayName;
        this.entityCategory = entityCategory;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The category carried by entities belonging to this runtime category. */
    public EntityCategory getEntityCategory() {
        return entityCategory;
    }

    /**
     * Resolves a user-typed name, case- and plural-insensitively, so
     * {@code enemybullet}, {@code EnemyBullets} and {@code ENEMY_BULLETS} all
     * mean the same thing.
     *
     * @return the category, or {@code null} if the name matches none
     */
    public static RuntimeObjectCategory fromName(String name) {
        if (name == null) {
            return null;
        }
        String normalised = name.trim().toLowerCase().replace("_", "").replace("-", "");
        if (normalised.endsWith("s")) {
            normalised = normalised.substring(0, normalised.length() - 1);
        }
        for (RuntimeObjectCategory category : values()) {
            String candidate = category.displayName.toLowerCase();
            if (candidate.endsWith("s")) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            if (candidate.equals(normalised)) {
                return category;
            }
        }
        return null;
    }
}
