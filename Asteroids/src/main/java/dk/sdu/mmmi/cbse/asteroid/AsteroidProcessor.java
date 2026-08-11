package dk.sdu.mmmi.cbse.asteroid;

import dk.sdu.mmmi.cbse.common.asteroids.Asteroid;
import dk.sdu.mmmi.cbse.common.asteroids.AsteroidSize;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.RuntimeObjectCategory;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.sound.SoundManager;

import java.util.List;
import java.util.Random;

public class AsteroidProcessor implements IEntityProcessingService {

    private final Random random = new Random();

    /**
     * Remembers that the field was emptied because the Asteroids category was
     * switched off, so that reactivating it restocks the current wave instead
     * of being mistaken for the player having cleared it.
     */
    private boolean waveSuppressedWhileInactive = false;

    @Override
    public void process(GameData gameData, World world) {

        List<Entity> asteroids = world.getEntities(Asteroid.class);
        gameData.setAsteroidsRemaining(asteroids.size());

        if (asteroids.isEmpty()) {
            if (!gameData.getRuntimeObjectState().isActive(RuntimeObjectCategory.ASTEROIDS)) {
                // An empty field normally means "wave cleared", but while the
                // category is deactivated it means "deliberately emptied".
                // Advancing the wave here would both spawn asteroids straight
                // back and march the run towards the victory wave, so the
                // whole branch is skipped and zero asteroids stays a stable
                // state for as long as the category is off.
                waveSuppressedWhileInactive = true;
                return;
            }
            if (waveSuppressedWhileInactive) {
                // Coming back from deactivation: restock the wave the player
                // was already on rather than promoting them to the next one,
                // so repeatedly deleting and restoring asteroids cannot
                // advance the game towards victory.
                waveSuppressedWhileInactive = false;
                respawnCurrentWave(gameData, world);
                return;
            }
            spawnNextWave(gameData, world);
            return;
        }

        double waveSpeedMultiplier = gameData.getWaveState().getSpeedMultiplier();

        for (Entity entity : asteroids) {
            Asteroid asteroid = (Asteroid) entity;
            double speedMultiplier = waveSpeedMultiplier * asteroid.getSize().getSpeedMultiplier();

            double changeX = Math.cos(Math.toRadians(asteroid.getRotation()));
            double changeY = Math.sin(Math.toRadians(asteroid.getRotation()));

            asteroid.setX(asteroid.getX() + changeX * 0.5 * speedMultiplier);
            asteroid.setY(asteroid.getY() + changeY * 0.5 * speedMultiplier);

            if (asteroid.getX() < 0) {
                asteroid.setX(asteroid.getX() + gameData.getDisplayWidth());
            }

            if (asteroid.getX() > gameData.getDisplayWidth()) {
                asteroid.setX(asteroid.getX() - gameData.getDisplayWidth());
            }

            if (asteroid.getY() < 0) {
                asteroid.setY(asteroid.getY() + gameData.getDisplayHeight());
            }

            if (asteroid.getY() > gameData.getDisplayHeight()) {
                asteroid.setY(asteroid.getY() - gameData.getDisplayHeight());
            }

        }

    }

    private void spawnNextWave(GameData gameData, World world) {
        gameData.getWaveState().nextWave();
        SoundManager.playWaveComplete();
        respawnCurrentWave(gameData, world);
    }

    /** Fills the field for whatever wave the player is already on. */
    private void respawnCurrentWave(GameData gameData, World world) {
        int count = gameData.getWaveState().getAsteroidCountForCurrentWave();
        for (int i = 0; i < count; i++) {
            world.addEntity(createRandomAsteroid(gameData));
        }
    }

    private Entity createRandomAsteroid(GameData gameData) {
        Asteroid asteroid = new Asteroid();
        asteroid.setSize(AsteroidSize.LARGE);
        asteroid.setPolygonCoordinates(AsteroidShapes.jaggedPolygon(AsteroidSize.LARGE.getRadius(), random));
        asteroid.setX(random.nextInt(gameData.getDisplayWidth()));
        asteroid.setY(random.nextInt(gameData.getDisplayHeight()));
        asteroid.setRadius(AsteroidSize.LARGE.getRadius());
        asteroid.setRotation(random.nextInt(360));
        return asteroid;
    }

}
