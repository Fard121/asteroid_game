package dk.sdu.mmmi.cbse.bulletsystem;

import dk.sdu.mmmi.cbse.common.bullet.Bullet;
import dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.sound.SoundManager;

public class BulletControlSystem implements IEntityProcessingService, BulletSPI {

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
    public Entity createBullet(Entity shooter, GameData gameData) {
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
        return bullet;
    }
}
