package dk.sdu.mmmi.cbse.asteroid;

import dk.sdu.mmmi.cbse.common.asteroids.Asteroid;
import dk.sdu.mmmi.cbse.common.asteroids.AsteroidSize;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.sound.SoundManager;

import java.util.List;
import java.util.Random;

public class AsteroidProcessor implements IEntityProcessingService {

    private final Random random = new Random();

    @Override
    public void process(GameData gameData, World world) {

        List<Entity> asteroids = world.getEntities(Asteroid.class);
        gameData.setAsteroidsRemaining(asteroids.size());

        if (asteroids.isEmpty()) {
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
