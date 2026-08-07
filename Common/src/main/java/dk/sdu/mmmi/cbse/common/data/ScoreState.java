package dk.sdu.mmmi.cbse.common.data;

/**
 * Shared score counter, exposed via {@link GameData}. Written by the
 * (generic, category-only) collision system when a kill is credited to
 * the player, read by the HUD.
 */
public class ScoreState {

    private int score = 0;

    public int getScore() {
        return score;
    }

    public void addPoints(int points) {
        score += points;
    }

    public void reset() {
        score = 0;
    }
}
