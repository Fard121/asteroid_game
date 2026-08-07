package dk.sdu.mmmi.cbse.collisionsystem;

import dk.sdu.mmmi.cbse.common.asteroids.Asteroid;
import dk.sdu.mmmi.cbse.common.asteroids.IAsteroidSplitter;
import dk.sdu.mmmi.cbse.common.services.IPostEntityProcessingService;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.PlayerState;
import dk.sdu.mmmi.cbse.common.data.ScoreState;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.util.ServiceLocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static java.util.stream.Collectors.toList;

public class CollisionDetector implements IPostEntityProcessingService {

    // Unordered category pairs that are allowed to destroy each other.
    // Everything not listed here (e.g. ASTEROID-ASTEROID, PLAYER_BULLET-PLAYER,
    // bullet-vs-same-side-bullet) is ignored.
    private static final EntityCategory[][] VALID_COLLISIONS = {
        {EntityCategory.PLAYER_BULLET, EntityCategory.ASTEROID},
        {EntityCategory.PLAYER_BULLET, EntityCategory.ENEMY},
        {EntityCategory.ENEMY_BULLET, EntityCategory.PLAYER},
        {EntityCategory.PLAYER, EntityCategory.ASTEROID},
        {EntityCategory.PLAYER, EntityCategory.ENEMY}
    };

    private static final int POINTS_LARGE_ASTEROID = 20;
    private static final int POINTS_MEDIUM_ASTEROID = 50;
    private static final int POINTS_SMALL_ASTEROID = 100;
    private static final int POINTS_ENEMY = 200;

    public CollisionDetector() {
    }

    @Override
    public void process(GameData gameData, World world) {
        // Snapshot into an indexable list so each unordered pair is only
        // checked once and so we never mutate the collection we're scanning.
        List<Entity> entities = new ArrayList<>(world.getEntities());
        Set<Entity> toRemove = new HashSet<>();

        for (int i = 0; i < entities.size(); i++) {
            Entity entity1 = entities.get(i);
            for (int j = i + 1; j < entities.size(); j++) {
                Entity entity2 = entities.get(j);

                if (!isValidCollision(entity1.getCategory(), entity2.getCategory())) {
                    continue;
                }

                if (!this.collides(entity1, entity2)) {
                    continue;
                }

                Entity player = asPlayer(entity1, entity2);
                if (player != null) {
                    // The player entity itself is never removed on a hit
                    // (it has lives/respawn instead) - only the thing that
                    // hit it is damaged, and only while not invulnerable.
                    // Ramming kills don't score, since those already cost
                    // the player a life.
                    PlayerState playerState = gameData.getPlayerState();
                    if (playerState.isInvulnerable()) {
                        continue;
                    }
                    Entity other = (player == entity1) ? entity2 : entity1;
                    playerState.registerHit();
                    if (damage(other)) {
                        toRemove.add(other);
                        splitIfAsteroid(other, world);
                    }
                } else {
                    // Both sides take a hit - e.g. a bullet always has 1 max
                    // health so it is always consumed, while its target
                    // (asteroid/enemy) is only actually destroyed once its
                    // own hit points run out.
                    boolean entity1Destroyed = damage(entity1);
                    boolean entity2Destroyed = damage(entity2);
                    if (entity1Destroyed) {
                        toRemove.add(entity1);
                        awardScoreForDestroyed(entity1, gameData.getScoreState());
                        splitIfAsteroid(entity1, world);
                    }
                    if (entity2Destroyed) {
                        toRemove.add(entity2);
                        awardScoreForDestroyed(entity2, gameData.getScoreState());
                        splitIfAsteroid(entity2, world);
                    }
                }
            }
        }

        // Removal happens only after the full scan is complete.
        for (Entity entity : toRemove) {
            world.removeEntity(entity);
        }
    }

    // Called once an entity's hit points have actually run out. Bullets and
    // the player itself never score (their categories fall through both
    // ifs); ramming kills via the player branch DO still score here, same
    // as bullet kills - only the redundant per-hit life cost differs.
    private void awardScoreForDestroyed(Entity destroyed, ScoreState scoreState) {
        if (destroyed.getCategory() == EntityCategory.ASTEROID) {
            scoreState.addPoints(pointsForAsteroid(destroyed));
        } else if (destroyed.getCategory() == EntityCategory.ENEMY) {
            scoreState.addPoints(POINTS_ENEMY);
        }
    }

    // Applies one hit of damage and reports whether the entity is now
    // destroyed. Entities that never call setMaxHealth() default to 1 max
    // health, i.e. destroyed on the very first hit (bullets, asteroids).
    private boolean damage(Entity entity) {
        entity.damage(1);
        return entity.isDestroyed();
    }

    private int pointsForAsteroid(Entity entity) {
        if (!(entity instanceof Asteroid)) {
            return POINTS_SMALL_ASTEROID;
        }
        switch (((Asteroid) entity).getSize()) {
            case LARGE:
                return POINTS_LARGE_ASTEROID;
            case MEDIUM:
                return POINTS_MEDIUM_ASTEROID;
            default:
                return POINTS_SMALL_ASTEROID;
        }
    }

    private void splitIfAsteroid(Entity entity, World world) {
        if (entity.getCategory() != EntityCategory.ASTEROID) {
            return;
        }
        getAsteroidSplitters().stream().findFirst().ifPresent(
                splitter -> splitter.createSplitAsteroid(entity, world)
        );
    }

    // See EnemyControlSystem.getBulletSPIs() (Enemy module) for why this
    // looks IAsteroidSplitter up against ServiceLocator's plugins layer
    // explicitly rather than via a plain
    // ServiceLoader.load(IAsteroidSplitter.class). Collision's own
    // module-info already declares `uses IAsteroidSplitter`.
    private List<IAsteroidSplitter> getAsteroidSplitters() {
        return ServiceLoader.load(ServiceLocator.INSTANCE.getLayer(), IAsteroidSplitter.class)
                .stream().map(ServiceLoader.Provider::get).collect(toList());
    }

    private Entity asPlayer(Entity entity1, Entity entity2) {
        if (entity1.getCategory() == EntityCategory.PLAYER) {
            return entity1;
        }
        if (entity2.getCategory() == EntityCategory.PLAYER) {
            return entity2;
        }
        return null;
    }

    private boolean isValidCollision(EntityCategory category1, EntityCategory category2) {
        for (EntityCategory[] pair : VALID_COLLISIONS) {
            boolean matches = (category1 == pair[0] && category2 == pair[1])
                    || (category1 == pair[1] && category2 == pair[0]);
            if (matches) {
                return true;
            }
        }
        return false;
    }

    public Boolean collides(Entity entity1, Entity entity2) {
        float dx = (float) entity1.getX() - (float) entity2.getX();
        float dy = (float) entity1.getY() - (float) entity2.getY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return distance < (entity1.getRadius() + entity2.getRadius());
    }

}
