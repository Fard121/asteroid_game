package dk.sdu.mmmi.cbse.playersystem;

import dk.sdu.mmmi.cbse.common.bullet.Bullet;
import dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.GameKeys;
import dk.sdu.mmmi.cbse.common.data.GameState;
import dk.sdu.mmmi.cbse.common.data.PlayerState;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.util.ServiceLocator;

import java.util.List;
import java.util.ServiceLoader;

import static java.util.stream.Collectors.toList;


public class PlayerControlSystem implements IEntityProcessingService {

    private static final double ACCELERATION = 0.12;
    private static final double FRICTION = 0.98; // slight drag, 2% velocity lost per frame
    private static final double MAX_SPEED = 3.5;
    private static final int SHOOT_COOLDOWN_FRAMES = 15; // ~0.25s at 60 FPS

    private int shootCooldownFramesRemaining = 0;

    @Override
    public void process(GameData gameData, World world) {

        PlayerState playerState = gameData.getPlayerState();

        for (Entity playerEntity : world.getEntities(Player.class)) {
            Player player = (Player) playerEntity;

            boolean runEnded = playerState.isGameOver()
                    || gameData.getGameStateManager().getState() == GameState.VICTORY;

            if (runEnded) {
                if (gameData.getKeys().isPressed(GameKeys.RESTART)) {
                    playerState.reset();
                    gameData.getScoreState().reset();
                    gameData.getWaveState().reset();
                    gameData.getGameStateManager().startGame();
                    respawnAtCenter(player, gameData);
                }
                continue;
            }

            playerState.tickInvulnerability();

            if (playerState.consumeHitPending()) {
                playerState.loseLife();
                if (!playerState.isGameOver()) {
                    respawnAtCenter(player, gameData);
                }
                continue;
            }

            if (gameData.getKeys().isDown(GameKeys.LEFT)) {
                player.setRotation(player.getRotation() - 5);
            }
            if (gameData.getKeys().isDown(GameKeys.RIGHT)) {
                player.setRotation(player.getRotation() + 5);
            }
            if (gameData.getKeys().isDown(GameKeys.UP)) {
                double accelX = Math.cos(Math.toRadians(player.getRotation())) * ACCELERATION;
                double accelY = Math.sin(Math.toRadians(player.getRotation())) * ACCELERATION;
                player.setVelocityX(player.getVelocityX() + accelX);
                player.setVelocityY(player.getVelocityY() + accelY);
            }

            player.setVelocityX(player.getVelocityX() * FRICTION);
            player.setVelocityY(player.getVelocityY() * FRICTION);

            double speed = Math.sqrt(player.getVelocityX() * player.getVelocityX()
                    + player.getVelocityY() * player.getVelocityY());
            if (speed > MAX_SPEED) {
                double scale = MAX_SPEED / speed;
                player.setVelocityX(player.getVelocityX() * scale);
                player.setVelocityY(player.getVelocityY() * scale);
            }

            player.setX(player.getX() + player.getVelocityX());
            player.setY(player.getY() + player.getVelocityY());

            if (shootCooldownFramesRemaining > 0) {
                shootCooldownFramesRemaining--;
            }
            if (gameData.getKeys().isDown(GameKeys.SPACE) && shootCooldownFramesRemaining <= 0
                    && world.getEntities(Bullet.class).size() < Bullet.MAX_BULLETS) {
                // Player and enemy fire are separate components, and both
                // publish BulletSPI. Taking the first provider would be a
                // coin toss, so every provider is offered the shot and the
                // one that recognises this shooter answers; the others
                // return empty. If the player's weapon component is absent
                // or deleted, nothing answers and no bullet is made.
                for (BulletSPI spi : getBulletSPIs()) {
                    java.util.Optional<Entity> created = spi.createBullet(player, gameData);
                    if (created.isPresent()) {
                        world.addEntity(created.get());
                        break;
                    }
                }
                shootCooldownFramesRemaining = SHOOT_COOLDOWN_FRAMES;
            }

        if (player.getX() < 0) {
            player.setX(player.getX() + gameData.getDisplayWidth());
        }

        if (player.getX() > gameData.getDisplayWidth()) {
            player.setX(player.getX() - gameData.getDisplayWidth());
        }

        if (player.getY() < 0) {
            player.setY(player.getY() + gameData.getDisplayHeight());
        }

        if (player.getY() > gameData.getDisplayHeight()) {
            player.setY(player.getY() - gameData.getDisplayHeight());
        }

        }
    }

    private void respawnAtCenter(Player player, GameData gameData) {
        player.setX(gameData.getDisplayWidth() / 2.0);
        player.setY(gameData.getDisplayHeight() / 2.0);
        player.setRotation(0);
        player.setVelocityX(0);
        player.setVelocityY(0);
    }

    // See EnemyControlSystem.getBulletSPIs() (Enemy module) for why this
    // looks BulletSPI up against ServiceLocator's plugins layer explicitly
    // rather than via a plain ServiceLoader.load(BulletSPI.class). Player's
    // own module-info already declares `uses BulletSPI`.
    private List<BulletSPI> getBulletSPIs() {
        return ServiceLocator.INSTANCE.getLayers().stream()
                .flatMap(layer -> ServiceLoader.load(layer, BulletSPI.class).stream())
                .map(ServiceLoader.Provider::get).collect(toList());
    }
}
