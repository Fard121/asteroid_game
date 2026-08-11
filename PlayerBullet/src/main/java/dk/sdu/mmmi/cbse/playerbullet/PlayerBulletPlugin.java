package dk.sdu.mmmi.cbse.playerbullet;

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
public class PlayerBulletPlugin implements IGamePluginService {

    @Override
    public void start(GameData gameData, World world) {
        PlayerBulletControlSystem.setInstalled(true);
    }

    @Override
    public void stop(GameData gameData, World world) {
        PlayerBulletControlSystem.setInstalled(false);
        for (Entity entity : world.getEntities()) {
            if (entity.getCategory() == EntityCategory.PLAYER_BULLET) {
                world.removeEntity(entity);
            }
        }
    }
}
