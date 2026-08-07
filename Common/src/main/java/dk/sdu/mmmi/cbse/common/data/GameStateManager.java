package dk.sdu.mmmi.cbse.common.data;

/**
 * Owns the overall game flow state, exposed via {@link GameData}. Other
 * per-domain state (PlayerState, ScoreState, WaveState) stays as-is and
 * this only tracks which "screen" the game is on.
 */
public class GameStateManager {

    private GameState state = GameState.START_MENU;

    public GameState getState() {
        return state;
    }

    public void startGame() {
        state = GameState.PLAYING;
    }

    public void togglePause() {
        if (state == GameState.PLAYING) {
            state = GameState.PAUSED;
        } else if (state == GameState.PAUSED) {
            state = GameState.PLAYING;
        }
    }

    public void gameOver() {
        state = GameState.GAME_OVER;
    }

    public void victory() {
        state = GameState.VICTORY;
    }
}
