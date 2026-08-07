package dk.sdu.mmmi.cbse.asteroid;

import dk.sdu.mmmi.cbse.common.asteroids.Asteroid;
import dk.sdu.mmmi.cbse.common.asteroids.AsteroidSize;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;
import java.util.Random;

/**
 *
 * @author corfixen
 */
public class AsteroidPlugin implements IGamePluginService {

    @Override
    public void start(GameData gameData, World world) {
        Entity asteroid = createAsteroid(gameData);
        world.addEntity(asteroid);
    }

    @Override
    public void stop(GameData gameData, World world) {
        // Remove entities
        for (Entity asteroid : world.getEntities(Asteroid.class)) {
            world.removeEntity(asteroid);
        }
    }

    private Entity createAsteroid(GameData gameData) {
        Asteroid asteroid = new Asteroid();
        Random rnd = new Random();
        asteroid.setSize(AsteroidSize.LARGE);
        asteroid.setPolygonCoordinates(AsteroidShapes.jaggedPolygon(AsteroidSize.LARGE.getRadius(), rnd));
        asteroid.setX(0);
        asteroid.setY(0);
        asteroid.setRadius(AsteroidSize.LARGE.getRadius());
        asteroid.setRotation(rnd.nextInt(90));
        return asteroid;
    }
}
