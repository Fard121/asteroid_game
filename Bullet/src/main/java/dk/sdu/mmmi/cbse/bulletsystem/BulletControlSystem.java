package dk.sdu.mmmi.cbse.bulletsystem;

import dk.sdu.mmmi.cbse.common.bullet.Bullet;
import dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.sound.SoundManager;

import java.util.Optional;

public class BulletControlSystem implements IEntityProcessingService, BulletSPI {

    // Shared across every instance (Player/Enemy each construct their own
    // via a fresh ServiceLoader.load(BulletSPI.class) call per shot, rather
    // than reusing Core's canonical IEntityProcessingService instance), so
    // this needs to be static, not a per-instance field, for
    // BulletPlugin.start()/stop() to actually gate createBullet() for all of
    // them.
    private static volatile boolean installed = true;

    static void setInstalled(boolean value) {
        installed = value;
    }

    @Override
    public void process(GameData gameData, World world) {

        for (Entity entity : world.getEntities(Bullet.class)) {
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
        if (!installed) {
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
        bullet.setCategory(shooter.getCategory() == EntityCategory.PLAYER
                ? EntityCategory.PLAYER_BULLET
                : EntityCategory.ENEMY_BULLET);
        SoundManager.playShoot();
        return Optional.of(bullet);
    }
}
