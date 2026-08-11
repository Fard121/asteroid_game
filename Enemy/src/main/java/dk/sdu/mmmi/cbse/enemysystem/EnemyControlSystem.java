package dk.sdu.mmmi.cbse.enemysystem;

import dk.sdu.mmmi.cbse.common.bullet.Bullet;
import dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.RuntimeObjectCategory;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.util.ServiceLocator;

import java.util.List;
import java.util.Random;
import java.util.ServiceLoader;

import static java.util.stream.Collectors.toList;

public class EnemyControlSystem implements IEntityProcessingService {

    private static final int SPAWN_DELAY_FRAMES = 180; // ~3s at 60 FPS, also used as the respawn delay
    private static final int MIN_DIRECTION_CHANGE_FRAMES = 60;
    private static final int MAX_DIRECTION_CHANGE_FRAMES = 150;
    private static final int SHOOT_COOLDOWN_FRAMES = 90;
    private static final double ENEMY_SPEED = 1.2;
    private static final int ENEMY_MAX_HEALTH = 3;

    private final Random random = new Random();

    private int framesUntilSpawn = SPAWN_DELAY_FRAMES;
    private int framesUntilDirectionChange = 0;
    private int shootCooldownFramesRemaining = 0;

    @Override
    public void process(GameData gameData, World world) {

        boolean enemiesActive = gameData.getRuntimeObjectState()
                .isActive(RuntimeObjectCategory.ENEMY);

        if (world.getEntities(Enemy.class).isEmpty()) {
            if (!enemiesActive) {
                // Deactivated: an empty field is a valid, stable state, so
                // the respawn timer is held at zero rather than counting
                // down. Restoring the category therefore brings an enemy
                // back on the very next frame instead of after another
                // three-second wait.
                framesUntilSpawn = 0;
                return;
            }
            // Covers both the initial delay before the first enemy appears
            // and the respawn delay after one is destroyed.
            if (framesUntilSpawn > 0) {
                framesUntilSpawn--;
            } else {
                spawnEnemy(gameData, world);
                framesUntilSpawn = SPAWN_DELAY_FRAMES;
            }
            return;
        }

        for (Entity enemy : world.getEntities(Enemy.class)) {
            moveRandomly(enemy);
            wrap(enemy, gameData);
            shoot(enemy, gameData, world);
        }
    }

    private void spawnEnemy(GameData gameData, World world) {
        Entity enemy = new Enemy();
        // Flattened hexagon "saucer" outline - deliberately not another
        // arrow/ship shape, so it doesn't read as a same-looking ship.
        enemy.setPolygonCoordinates(-10, 0, -5, -6, 5, -6, 10, 0, 5, 6, -5, 6);
        enemy.setRadius(8);
        enemy.setMaxHealth(ENEMY_MAX_HEALTH);
        enemy.setX(random.nextInt(gameData.getDisplayWidth()));
        enemy.setY(random.nextInt(gameData.getDisplayHeight()));
        enemy.setRotation(random.nextInt(360));
        world.addEntity(enemy);
        framesUntilDirectionChange = 0;
        shootCooldownFramesRemaining = SHOOT_COOLDOWN_FRAMES;
    }

    private void moveRandomly(Entity enemy) {
        if (framesUntilDirectionChange <= 0) {
            enemy.setRotation(random.nextInt(360));
            framesUntilDirectionChange = MIN_DIRECTION_CHANGE_FRAMES
                    + random.nextInt(MAX_DIRECTION_CHANGE_FRAMES - MIN_DIRECTION_CHANGE_FRAMES);
        } else {
            framesUntilDirectionChange--;
        }

        double changeX = Math.cos(Math.toRadians(enemy.getRotation()));
        double changeY = Math.sin(Math.toRadians(enemy.getRotation()));
        enemy.setX(enemy.getX() + changeX * ENEMY_SPEED);
        enemy.setY(enemy.getY() + changeY * ENEMY_SPEED);
    }

    private void wrap(Entity enemy, GameData gameData) {
        if (enemy.getX() < 0) {
            enemy.setX(enemy.getX() + gameData.getDisplayWidth());
        }
        if (enemy.getX() > gameData.getDisplayWidth()) {
            enemy.setX(enemy.getX() - gameData.getDisplayWidth());
        }
        if (enemy.getY() < 0) {
            enemy.setY(enemy.getY() + gameData.getDisplayHeight());
        }
        if (enemy.getY() > gameData.getDisplayHeight()) {
            enemy.setY(enemy.getY() - gameData.getDisplayHeight());
        }
    }

    private void shoot(Entity enemy, GameData gameData, World world) {
        if (shootCooldownFramesRemaining > 0) {
            shootCooldownFramesRemaining--;
            return;
        }
        if (world.getEntities(Bullet.class).size() < Bullet.MAX_BULLETS) {
            // See PlayerControlSystem: every BulletSPI provider is offered
            // the shot and only the enemy's own component answers, so
            // deleting the enemy weapon silences enemies without touching
            // the player's.
            for (BulletSPI spi : getBulletSPIs()) {
                java.util.Optional<Entity> created = spi.createBullet(enemy, gameData);
                if (created.isPresent()) {
                    world.addEntity(created.get());
                    break;
                }
            }
        }
        shootCooldownFramesRemaining = SHOOT_COOLDOWN_FRAMES;
    }

    // Player, Enemy, Bullet, Asteroids, and Collision are each loaded
    // dynamically into their own ModuleLayer at runtime (see
    // Common's ServiceLocator / docs/ARCHITECTURE.md) - a plain
    // ServiceLoader.load(BulletSPI.class) does not reliably discover a
    // sibling plugin's provider from within those layers, so this looks
    // providers up against the actual plugin layers explicitly. Enemy's own
    // module-info already declares `uses BulletSPI`, which is what
    // ServiceLoader.load(ModuleLayer, Class) requires of the caller.
    //
    // Iterating every currently-loaded layer (rather than one shared one)
    // is also what makes this degrade gracefully when Bullet is unloaded at
    // runtime: the list simply comes back empty and the enemy holds fire,
    // instead of touching a class that is no longer there.
    private List<BulletSPI> getBulletSPIs() {
        return ServiceLocator.INSTANCE.getLayers().stream()
                .flatMap(layer -> ServiceLoader.load(layer, BulletSPI.class).stream())
                .map(ServiceLoader.Provider::get).collect(toList());
    }
}
