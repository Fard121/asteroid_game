package dk.sdu.mmmi.cbse.common.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateTest {

    @Test
    void startsWithThreeLivesAndNotGameOver() {
        PlayerState state = new PlayerState();

        assertEquals(3, state.getLives());
        assertFalse(state.isGameOver());
    }

    @Test
    void loseLifeDecrementsUntilGameOver() {
        PlayerState state = new PlayerState();

        state.loseLife();
        assertEquals(2, state.getLives());
        assertFalse(state.isGameOver());

        state.loseLife();
        assertEquals(1, state.getLives());
        assertFalse(state.isGameOver());

        state.loseLife();
        assertEquals(0, state.getLives());
        assertTrue(state.isGameOver());
    }

    @Test
    void hitsAreIgnoredWhileInvulnerable() {
        PlayerState state = new PlayerState();
        // A freshly-created PlayerState grants spawn invulnerability.
        assertTrue(state.isInvulnerable());

        state.registerHit();

        assertFalse(state.consumeHitPending());
    }

    @Test
    void hitIsRegisteredOnceInvulnerabilityExpires() {
        PlayerState state = new PlayerState();
        for (int i = 0; i < PlayerState.INVULNERABILITY_FRAMES; i++) {
            state.tickInvulnerability();
        }
        assertFalse(state.isInvulnerable());

        state.registerHit();

        assertTrue(state.consumeHitPending());
        // consuming is one-shot
        assertFalse(state.consumeHitPending());
    }

    @Test
    void resetRestoresInitialState() {
        PlayerState state = new PlayerState();
        state.loseLife();
        state.loseLife();
        state.loseLife();
        assertTrue(state.isGameOver());

        state.reset();

        assertEquals(3, state.getLives());
        assertFalse(state.isGameOver());
    }
}
