import dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;

module PlayerBullet {
    requires Common;
    requires CommonBullet;
    provides IGamePluginService with dk.sdu.mmmi.cbse.playerbullet.PlayerBulletPlugin;
    provides BulletSPI with dk.sdu.mmmi.cbse.playerbullet.PlayerBulletControlSystem;
    provides IEntityProcessingService with dk.sdu.mmmi.cbse.playerbullet.PlayerBulletControlSystem;
}
