package dk.sdu.mmmi.cbse.common.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Minimal synthesized-tone sound effects - no external audio assets needed.
 * Static/fire-and-forget by design so any module can call it directly
 * without threading an instance through GameData/constructors; mute is
 * the only piece of state involved.
 */
public final class SoundManager {

    private static volatile boolean muted = false;

    private SoundManager() {
    }

    public static void setMuted(boolean value) {
        muted = value;
    }

    public static boolean isMuted() {
        return muted;
    }

    public static void toggleMute() {
        muted = !muted;
    }

    public static void playShoot() {
        playAsync(() -> playTone(880, 50, 0.15));
    }

    public static void playExplosion() {
        playAsync(() -> playTone(110, 180, 0.3));
    }

    public static void playPlayerDeath() {
        playAsync(() -> {
            playTone(300, 150, 0.3);
            playTone(150, 300, 0.3);
        });
    }

    public static void playEnemyDeath() {
        playAsync(() -> playTone(660, 150, 0.25));
    }

    public static void playWaveComplete() {
        playAsync(() -> {
            playTone(440, 120, 0.2);
            playTone(660, 180, 0.2);
        });
    }

    public static void playMenuMove() {
        playAsync(() -> playTone(500, 40, 0.15));
    }

    private static void playAsync(Runnable toneSequence) {
        if (muted) {
            return;
        }
        Thread thread = new Thread(toneSequence);
        thread.setDaemon(true);
        thread.start();
    }

    private static void playTone(double frequencyHz, int durationMs, double volume) {
        try {
            float sampleRate = 44100;
            int numSamples = (int) (durationMs / 1000.0 * sampleRate);
            byte[] buffer = new byte[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double angle = 2.0 * Math.PI * i * frequencyHz / sampleRate;
                buffer[i] = (byte) (Math.sin(angle) * volume * 127);
            }
            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();
            line.write(buffer, 0, buffer.length);
            line.drain();
            line.close();
        } catch (LineUnavailableException e) {
            // Audio device unavailable - sound is non-critical, fail silently
        }
    }
}
