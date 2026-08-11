package dk.sdu.mmmi.cbse.common.data;

public class GameData {

    private int displayWidth  = 800 ;
    private int displayHeight = 800;
    private final GameKeys keys = new GameKeys();
    private final PlayerState playerState = new PlayerState();
    private final ScoreState scoreState = new ScoreState();
    private final WaveState waveState = new WaveState();
    private final GameStateManager gameStateManager = new GameStateManager();
    private final RuntimeObjectState runtimeObjectState = new RuntimeObjectState();
    private int asteroidsRemaining = 0;


    public GameKeys getKeys() {
        return keys;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public ScoreState getScoreState() {
        return scoreState;
    }

    public WaveState getWaveState() {
        return waveState;
    }

    public GameStateManager getGameStateManager() {
        return gameStateManager;
    }

    /**
     * Which runtime object categories (enemies, enemy bullets, player
     * bullets, asteroids) may currently create objects. Producers must
     * consult this before spawning - see {@link RuntimeObjectState}.
     */
    public RuntimeObjectState getRuntimeObjectState() {
        return runtimeObjectState;
    }

    public void setAsteroidsRemaining(int asteroidsRemaining) {
        this.asteroidsRemaining = asteroidsRemaining;
    }

    public int getAsteroidsRemaining() {
        return asteroidsRemaining;
    }

    public void setDisplayWidth(int width) {
        this.displayWidth = width;
    }

    public int getDisplayWidth() {
        return displayWidth;
    }

    public void setDisplayHeight(int height) {
        this.displayHeight = height;
    }

    public int getDisplayHeight() {
        return displayHeight;
    }


}
