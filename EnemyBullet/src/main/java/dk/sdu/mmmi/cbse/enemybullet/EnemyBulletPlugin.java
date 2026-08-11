package dk.sdu.mmmi.cbse.enemybullet;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;

/**
 * Lifecycle for the player's weapon. Removing it clears the player's bullets
 * that are in flight and stops new ones being made, without touching the
 * enemy's.
 */
public class EnemyBulletPlugin implements IGamePluginService {

    @Override
    public void start(GameData gameData, World world) {
        EnemyBulletControlSystem.setInstalled(true);
    }

    @Override
    public void stop(GameData gameData, World world) {
        EnemyBulletControlSystem.setInstalled(false);
        for (Entity entity : world.getEntities()) {
            if (entity.getCategory() == EntityCategory.ENEMY_BULLET) {
                world.removeEntity(entity);
            }
        }
    }
}
