package dk.sdu.mmmi.cbse.collisionsystem;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollisionDetectorTest {

    private final CollisionDetector detector = new CollisionDetector();

    private static Entity entityAt(double x, double y, float radius, EntityCategory category) {
        Entity entity = new Entity();
        entity.setX(x);
        entity.setY(y);
        entity.setRadius(radius);
        entity.setCategory(category);
        return entity;
    }

    // --- collides(): the Pythagorean distance formula itself ---

    @Test
    void collidesWhenDistanceIsLessThanCombinedRadii() {
        Entity a = entityAt(0, 0, 5, EntityCategory.ASTEROID);
        Entity b = entityAt(6, 0, 5, EntityCategory.PLAYER_BULLET); // distance 6 < 5+5

        assertTrue(detector.collides(a, b));
    }

    @Test
    void doesNotCollideWhenFarApart() {
        Entity a = entityAt(0, 0, 5, EntityCategory.ASTEROID);
        Entity b = entityAt(100, 0, 5, EntityCategory.PLAYER_BULLET); // distance 100 > 10

        assertFalse(detector.collides(a, b));
    }

    @Test
    void usesPythagoreanDistanceOnBothAxes() {
        // Classic 3-4-5 right triangle: dx=3, dy=4 -> distance 5.
        Entity a = entityAt(0, 0, 2, EntityCategory.ASTEROID);
        Entity b = entityAt(3, 4, 2, EntityCategory.PLAYER_BULLET); // 2+2=4 < 5 -> no collision

        assertFalse(detector.collides(a, b));

        b.setRadius(4); // 2+4=6 > 5 -> now they do collide
        assertTrue(detector.collides(a, b));
    }

    // --- process(): multi-hit destruction, using real state-based objects ---

    @Test
    void enemyIsDestroyedOnlyAfterThreeBulletHits() {
        GameData gameData = new GameData();
        World world = new World();

        Entity enemy = entityAt(100, 100, 8, EntityCategory.ENEMY);
        enemy.setMaxHealth(3);
        world.addEntity(enemy);

        for (int hit = 1; hit <= 2; hit++) {
            Entity bullet = entityAt(100, 100, 1, EntityCategory.PLAYER_BULLET);
            world.addEntity(bullet);

            detector.process(gameData, world);

            assertTrue(world.getEntities().contains(enemy), "enemy should survive hit " + hit);
        }

        Entity finalBullet = entityAt(100, 100, 1, EntityCategory.PLAYER_BULLET);
        world.addEntity(finalBullet);

        detector.process(gameData, world);

        assertFalse(world.getEntities().contains(enemy), "enemy should be destroyed on the 3rd hit");
    }

    // GameLab: "Ships that collide with asteroids should be destroyed" -
    // the enemy saucer is a ship too, and a rock is not a player kill.
    @Test
    void enemyShipIsDestroyedByAnAsteroidWithoutScoringForThePlayer() {
        GameData gameData = new GameData();
        World world = new World();

        Entity enemy = entityAt(100, 100, 8, EntityCategory.ENEMY);
        enemy.setMaxHealth(3); // survives 3 bullets, but not one asteroid
        world.addEntity(enemy);
        world.addEntity(entityAt(100, 100, 14, EntityCategory.ASTEROID));

        detector.process(gameData, world);

        assertFalse(world.getEntities().contains(enemy), "enemy should be destroyed by the asteroid");
        assertEquals(0, gameData.getScoreState().getScore(), "a rock kill is not the player's kill");
    }

    // --- process(): interaction-based test with a mocked World ---

    @Test
    void bothSinglesHitEntitiesAreRemovedFromTheWorld() {
        GameData gameData = new GameData();
        World world = mock(World.class);

        Entity bullet = entityAt(0, 0, 2, EntityCategory.PLAYER_BULLET);
        Entity asteroid = entityAt(0, 0, 2, EntityCategory.ASTEROID);
        when(world.getEntities()).thenReturn(List.of(bullet, asteroid));

        detector.process(gameData, world);

        verify(world, times(1)).removeEntity(bullet);
        verify(world, times(1)).removeEntity(asteroid);
        verify(world, times(2)).removeEntity(any(Entity.class));
    }
}
