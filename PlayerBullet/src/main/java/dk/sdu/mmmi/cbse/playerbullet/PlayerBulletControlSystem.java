package dk.sdu.mmmi.cbse.playerbullet;

import dk.sdu.mmmi.cbse.common.bullet.Bullet;
import dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.RuntimeObjectCategory;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.sound.SoundManager;

import java.util.Optional;

/**
 * The player's weapon: creates and advances {@link EntityCategory#PLAYER_BULLET}
 * projectiles, and nothing else.
 *
 * <p>This is one half of what used to be a single Bullet component. Player and
 * enemy fire are two independently deployable plugins so that either can be
 * removed on its own - delete this jar and the player stops shooting while
 * enemies carry on, or the reverse. Both still produce the shared
 * {@link Bullet} entity type from CommonBullet, so collision and rendering are
 * unchanged and treat every projectile alike.
 *
 * <p>Both halves are offered through the same {@link BulletSPI} contract, so a
 * shooter does not choose a provider by name; it offers itself to each provider
 * in turn and uses whichever one answers. {@link #createBullet} declines any
 * shooter that is not the player, which is what keeps the two apart.
 */
public class PlayerBulletControlSystem implements IEntityProcessingService, BulletSPI {

    // Shared across every instance, because Player constructs its own copy
    // through ServiceLoader on each shot rather than reusing Core's canonical
    // processor instance. Static is what lets the plugin's start/stop gate
    // createBullet for all of them at once.
    private static volatile boolean installed = true;

    static void setInstalled(boolean value) {
        installed = value;
    }

    @Override
    public void process(GameData gameData, World world) {
        for (Entity entity : world.getEntities(Bullet.class)) {
            if (entity.getCategory() != EntityCategory.PLAYER_BULLET) {
                continue; // the enemy's own component advances its bullets
            }
            Bullet bullet = (Bullet) entity;

            double changeX = Math.cos(Math.toRadians(bullet.getRotation()));
            double changeY = Math.sin(Math.toRadians(bullet.getRotation()));
            bullet.setX(bullet.getX() + changeX * 3);
            bullet.setY(bullet.getY() + changeY * 3);

            bullet.setFramesRemaining(bullet.getFramesRemaining() - 1);

            boolean expired = bullet.getFramesRemaining() <= 0;
            boolean offScreen = bullet.getX() < 0 || bullet.getX() > gameData.getDisplayWidth()
                    || bullet.getY() < 0 || bullet.getY() > gameData.getDisplayHeight();

            if (expired || offScreen) {
                world.removeEntity(bullet);
            }
        }
    }

    @Override
    public Optional<Entity> createBullet(Entity shooter, GameData gameData) {
        if (shooter.getCategory() != EntityCategory.PLAYER) {
            // Not ours - let the enemy's bullet component answer instead.
            return Optional.empty();
        }
        if (!installed) {
            return Optional.empty();
        }
        if (!gameData.getRuntimeObjectState().isActive(RuntimeObjectCategory.PLAYER_BULLETS)) {
            return Optional.empty();
        }

        Entity bullet = new Bullet();
        bullet.setPolygonCoordinates(1, -1, 1, 1, -1, 1, -1, -1);
        double changeX = Math.cos(Math.toRadians(shooter.getRotation()));
        double changeY = Math.sin(Math.toRadians(shooter.getRotation()));
        bullet.setX(shooter.getX() + changeX * 10);
        bullet.setY(shooter.getY() + changeY * 10);
        bullet.setRotation(shooter.getRotation());
        bullet.setRadius(1);
        bullet.setCategory(EntityCategory.PLAYER_BULLET);
        SoundManager.playShoot();
        return Optional.of(bullet);
    }
}
