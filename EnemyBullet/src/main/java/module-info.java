import dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;

module EnemyBullet {
    requires Common;
    requires CommonBullet;
    provides IGamePluginService with dk.sdu.mmmi.cbse.enemybullet.EnemyBulletPlugin;
    provides BulletSPI with dk.sdu.mmmi.cbse.enemybullet.EnemyBulletControlSystem;
    provides IEntityProcessingService with dk.sdu.mmmi.cbse.enemybullet.EnemyBulletControlSystem;
}
