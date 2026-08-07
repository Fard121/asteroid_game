package dk.sdu.mmmi.cbse.common.data;

import dk.sdu.mmmi.cbse.common.sound.SoundManager;

/**
 * Shared player life/invulnerability state, exposed via {@link GameData}.
 * Lets the (generic, category-only) collision system flag a hit on the
 * player without depending on the Player module, and lets the Player
 * module apply that hit (lose a life, respawn) without depending on
 * the Collision module.
 */
public class PlayerState {

    public static final int STARTING_LIVES = 3;
    public static final int INVULNERABILITY_FRAMES = 150; // ~2.5s at 60 FPS

    private int lives = STARTING_LIVES;
    private boolean gameOver = false;
    private int invulnerabilityFramesRemaining = INVULNERABILITY_FRAMES;
    private boolean hitPending = false;
    private boolean damagedThisFrame = false;

    public int getLives() {
        return lives;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isInvulnerable() {
        return invulnerabilityFramesRemaining > 0;
    }

    public void tickInvulnerability() {
        if (invulnerabilityFramesRemaining > 0) {
            invulnerabilityFramesRemaining--;
        }
    }

    /**
     * Called by the collision system when the player touches an asteroid,
     * enemy, or enemy bullet. Ignored while already invulnerable.
     */
    public void registerHit() {
        if (!isInvulnerable()) {
            hitPending = true;
        }
    }

    /**
     * Called once per frame by the player system to check for- and consume-
     * a hit registered by the collision system.
     */
    public boolean consumeHitPending() {
        if (hitPending) {
            hitPending = false;
            return true;
        }
        return false;
    }

    /**
     * Applies a hit: loses a life, then either ends the game or grants
     * fresh respawn invulnerability.
     */
    public void loseLife() {
        lives--;
        damagedThisFrame = true;
        SoundManager.playPlayerDeath();
        if (lives <= 0) {
            lives = 0;
            gameOver = true;
        } else {
            invulnerabilityFramesRemaining = INVULNERABILITY_FRAMES;
        }
    }

    /**
     * Called once per frame by the renderer to check for- and consume- a
     * screen-shake-worthy hit that happened this frame.
     */
    public boolean consumeDamagedThisFrame() {
        if (damagedThisFrame) {
            damagedThisFrame = false;
            return true;
        }
        return false;
    }

    public void reset() {
        lives = STARTING_LIVES;
        gameOver = false;
        invulnerabilityFramesRemaining = INVULNERABILITY_FRAMES;
        hitPending = false;
        damagedThisFrame = false;
    }
}
