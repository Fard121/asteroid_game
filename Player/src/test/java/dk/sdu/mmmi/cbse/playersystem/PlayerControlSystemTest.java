package dk.sdu.mmmi.cbse.playersystem;

import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.GameKeys;
import dk.sdu.mmmi.cbse.common.data.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerControlSystemTest {

    private static GameData newPlayingGameData() {
        GameData gameData = new GameData();
        gameData.getGameStateManager().startGame();
        return gameData;
    }

    @Test
    void rotatesLeftAndRightOnArrowKeys() {
        PlayerControlSystem system = new PlayerControlSystem();
        World world = new World();
        Player player = new Player();
        world.addEntity(player);
        GameData gameData = newPlayingGameData();

        gameData.getKeys().setKey(GameKeys.RIGHT, true);
        system.process(gameData, world);
        assertEquals(5, player.getRotation());

        gameData.getKeys().setKey(GameKeys.RIGHT, false);
        gameData.getKeys().setKey(GameKeys.LEFT, true);
        system.process(gameData, world);
        assertEquals(0, player.getRotation());
    }

    @Test
    void thrustAcceleratesInFacingDirectionAndFrictionSlowsItAfterwards() {
        PlayerControlSystem system = new PlayerControlSystem();
        World world = new World();
        Player player = new Player();
        world.addEntity(player);
        GameData gameData = newPlayingGameData();

        gameData.getKeys().setKey(GameKeys.UP, true);
        system.process(gameData, world);

        assertTrue(player.getVelocityX() > 0, "facing rotation 0 (right) should gain positive X velocity");
        double speedAfterThrust = Math.hypot(player.getVelocityX(), player.getVelocityY());

        gameData.getKeys().setKey(GameKeys.UP, false);
        system.process(gameData, world);
        double speedAfterFriction = Math.hypot(player.getVelocityX(), player.getVelocityY());

        assertTrue(speedAfterFriction < speedAfterThrust, "friction should reduce speed once thrust stops");
    }

    @Test
    void wrapsAroundTheLeftScreenEdge() {
        PlayerControlSystem system = new PlayerControlSystem();
        World world = new World();
        Player player = new Player();
        player.setX(-5);
        player.setY(0);
        world.addEntity(player);
        GameData gameData = newPlayingGameData();

        system.process(gameData, world);

        assertEquals(gameData.getDisplayWidth() - 5, player.getX(), 0.0001);
    }
}
