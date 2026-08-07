package dk.sdu.mmmi.cbse.common.bullet;

import dk.sdu.mmmi.cbse.common.data.Entity;

/**
 *
 * @author corfixen
 */
public class Bullet extends Entity {

    // Generous backstop, not the primary despawn mechanism - a bullet fired
    // from center toward a far corner needs to cross ~half the field
    // diagonal (~565 units at 800x800) before the off-screen check in
    // BulletControlSystem removes it; this only guards against it never
    // leaving the field for some reason.
    public static final int LIFETIME_FRAMES = 450; // ~7.5s at 60 FPS
    public static final int MAX_BULLETS = 20; // shared cap across player + enemy bullets

    private int framesRemaining = LIFETIME_FRAMES;

    public int getFramesRemaining() {
        return framesRemaining;
    }

    public void setFramesRemaining(int framesRemaining) {
        this.framesRemaining = framesRemaining;
    }
}
