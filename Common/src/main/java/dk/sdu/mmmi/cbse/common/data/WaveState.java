package dk.sdu.mmmi.cbse.common.data;

/**
 * Shared wave/difficulty counter, exposed via {@link GameData}. Advanced by
 * the Asteroids module whenever it finds the world empty of asteroids; read
 * by the HUD.
 */
public class WaveState {

    public static final int STARTING_ASTEROIDS = 1;
    public static final double SPEED_INCREASE_PER_WAVE = 0.1; // +10% asteroid speed per wave
    public static final int VICTORY_WAVE = 3;

    private int waveNumber = 1;

    public int getWaveNumber() {
        return waveNumber;
    }

    public boolean hasReachedVictoryWave() {
        return waveNumber >= VICTORY_WAVE;
    }

    public int getAsteroidCountForCurrentWave() {
        return STARTING_ASTEROIDS + (waveNumber - 1);
    }

    public double getSpeedMultiplier() {
        return 1.0 + (waveNumber - 1) * SPEED_INCREASE_PER_WAVE;
    }

    public void nextWave() {
        waveNumber++;
    }

    public void reset() {
        waveNumber = 1;
    }
}
